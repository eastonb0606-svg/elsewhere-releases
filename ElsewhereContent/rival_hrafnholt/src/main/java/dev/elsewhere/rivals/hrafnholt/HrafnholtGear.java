package dev.elsewhere.rivals.hrafnholt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;

/**
 * Effects of the unique drops, all server-side: Iron Crown Wolf-Rally, Hearth-Ward fire resistance, Ulfhednar Pelt-Rage,
 * Skald's Rune-Axe third hit, Hornbow Slowness arrows, Wolfskin snow speed, and the Raven Banner ledger.
 */
public final class HrafnholtGear {
	private HrafnholtGear() {}

	public static final float RALLY_BASE = 0.08F, RALLY_PER_SEAL = 0.01F, RALLY_MAX = 0.12F;
	public static final double RALLY_RADIUS = 16.0;
	public static final float FIRE_REDUCTION = 0.10F;
	public static final int PELT_COOLDOWN = 2400, PELT_TICKS = 120;
	public static final int RUNE_WINDOW = 100;
	public static final float RUNE_BONUS = HrafnholtItems.REBALANCE_RIVALS ? 5.0F : 3.0F; // 3.0: third hit +5
	public static final String HORNBOW_TAG = "elsewhere_hrafnholt_hornbow";
	public static final Identifier SNOW_SPEED = HrafnholtContent.id("wolfskin_snow_speed");
	public static final double SNOW_BONUS = 0.10;

	static final Map<UUID, Long> PELT_LAST = new HashMap<>();
	/** attacker -> (target -> hit times) for the Rune-Axe. */
	static final Map<UUID, Map<UUID, List<Long>>> RUNE_HITS = new HashMap<>();
	static boolean inBonus;
	public static int ralliesApplied, runeBonuses, peltRages, hornbowSlows;

	/** A planted Raven Banner. */
	public record Banner(UUID owner, ServerLevel level, BlockPos pos, long expires) {}
	public static final List<Banner> BANNERS = new ArrayList<>();

	public static boolean wearing(LivingEntity e, EquipmentSlot slot, Item item) {
		return e.getItemBySlot(slot).is(item);
	}

	public static int sealsCarried(Player p) {
		int n = 0;
		for (Item seal : HrafnholtItems.SEALS.values()) if (p.getInventory().contains(new ItemStack(seal))) n++;
		return n;
	}

	/** Wolf-Rally reduction for a crown wearer (0 when no tamed wolf is within 16 blocks). */
	public static float rallyFor(Player p) {
		if (!wearing(p, EquipmentSlot.HEAD, HrafnholtItems.IRON_CROWN)) return 0F;
		if (!(p.level() instanceof ServerLevel level)) return 0F;
		AABB box = new AABB(p.blockPosition()).inflate(RALLY_RADIUS);
		boolean wolf = !level.getEntitiesOfClass(Wolf.class, box, w -> w.isTame() && w.isAlive() && w.distanceToSqr(p) <= RALLY_RADIUS * RALLY_RADIUS).isEmpty();
		if (!wolf) return 0F;
		return Math.min(RALLY_MAX, RALLY_BASE + RALLY_PER_SEAL * sealsCarried(p));
	}

	/** Called from the hurt mixin before armor: scales incoming damage. */
	public static float scaleIncoming(LivingEntity e, DamageSource source, float amount) {
		if (e.level().isClientSide()) return amount;
		if (e instanceof Player p) {
			float r = rallyFor(p);
			if (r > 0F) { amount *= 1F - r; ralliesApplied++; }
		}
		if (source.is(DamageTypeTags.IS_FIRE) && wearing(e, EquipmentSlot.CHEST, HrafnholtItems.HEARTH_WARD_MAIL)) amount *= 1F - FIRE_REDUCTION;
		return amount;
	}

	static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (!(target.level() instanceof ServerLevel level)) return;
		// Ulfhednar Pelt-Rage
		if (target instanceof Player p && wearing(p, EquipmentSlot.CHEST, HrafnholtItems.ULFHEDNAR_PELT) && p.isAlive() && p.getHealth() < p.getMaxHealth() * 0.30F) {
			long now = level.getGameTime();
			Long last = PELT_LAST.get(p.getUUID());
			if (last == null || now - last >= PELT_COOLDOWN) {
				PELT_LAST.put(p.getUUID(), now);
				p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, PELT_TICKS, 0));
				peltRages++;
				level.playSound(null, p.blockPosition(), Sfx3.pick("hrafnholt", "pelt_rage", SoundEvents.ENDER_DRAGON_GROWL), SoundSource.PLAYERS, Sfx3.enabled() ? 0.9F : 0.5F, Sfx3.enabled() ? 1.0F : 1.4F);
			}
		}
		// Skald's Rune-Axe: the third hit on one target within 5 s
		if (!inBonus && source.getEntity() instanceof LivingEntity atk && source.getDirectEntity() == atk && atk.getMainHandItem().is(HrafnholtItems.RUNE_AXE)) {
			long now = level.getGameTime();
			List<Long> hits = RUNE_HITS.computeIfAbsent(atk.getUUID(), k -> new HashMap<>()).computeIfAbsent(target.getUUID(), k -> new ArrayList<>());
			hits.removeIf(t -> now - t > RUNE_WINDOW);
			hits.add(now);
			if (hits.size() >= 3) {
				hits.clear();
				inBonus = true;
				try {
					target.hurtServer(level, source, RUNE_BONUS);
				} finally {
					inBonus = false;
				}
				Vec3 d = target.position().subtract(atk.position()).multiply(1, 0, 1).normalize().scale(0.6);
				target.push(d.x, 0.2, d.z);
				target.needsSync = true;
				runeBonuses++;
				level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5F, 1.6F);
			}
		}
		// Hornbow arrows slow
		if (source.getDirectEntity() instanceof AbstractArrow a && a.entityTags().contains(HORNBOW_TAG)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
			hornbowSlows++;
		}
	}

	static void tagArrow(net.minecraft.world.entity.Entity e, net.minecraft.server.level.ServerLevel level) {
		if (e instanceof AbstractArrow a && a.getOwner() instanceof LivingEntity o
			&& (o.getMainHandItem().is(HrafnholtItems.HORNBOW) || o.getOffhandItem().is(HrafnholtItems.HORNBOW))) a.addTag(HORNBOW_TAG);
	}

	/** Plants a black banner at pos (air or replaceable only). One per player: an older one is removed. */
	public static boolean plantBanner(ServerLevel level, Player player, BlockPos pos) {
		if (!level.getBlockState(pos).canBeReplaced() && !isOwnBanner(player.getUUID(), level, pos)) return false;
		clearBanner(player.getUUID());
		int rot = Math.floorMod((int) Math.floor((180.0F + player.getYRot()) * 16.0F / 360.0F + 0.5), 16);
		BlockState s = Blocks.BANNER.black().defaultBlockState().setValue(BannerBlock.ROTATION, rot);
		if (!level.setBlock(pos, s, 3)) return false;
		BANNERS.add(new Banner(player.getUUID(), level, pos.immutable(), level.getGameTime() + dev.elsewhere.rivals.hrafnholt.item.RavenBannerItem.LIFETIME_TICKS));
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		applySpeed(level, pos);
		return true;
	}

	static boolean isOwnBanner(UUID owner, ServerLevel level, BlockPos pos) {
		for (Banner b : BANNERS) if (b.owner.equals(owner) && b.level == level && b.pos.equals(pos)) return true;
		return false;
	}

	static void clearBanner(UUID owner) {
		for (Iterator<Banner> it = BANNERS.iterator(); it.hasNext();) {
			Banner b = it.next();
			if (b.owner.equals(owner)) { removeBlock(b); it.remove(); }
		}
	}

	static void removeBlock(Banner b) {
		if (b.level.getBlockState(b.pos).is(Blocks.BANNER.black())) b.level.removeBlock(b.pos, false);
	}

	static void applySpeed(ServerLevel level, BlockPos pos) {
		double r2 = (double) dev.elsewhere.rivals.hrafnholt.item.RavenBannerItem.RADIUS * dev.elsewhere.rivals.hrafnholt.item.RavenBannerItem.RADIUS;
		Vec3 c = Vec3.atCenterOf(pos);
		AABB box = new AABB(pos).inflate(dev.elsewhere.rivals.hrafnholt.item.RavenBannerItem.RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && x.distanceToSqr(c) <= r2)) {
			boolean ally = e instanceof Player || (e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame());
			if (ally) e.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, true));
		}
	}

	static void tick(MinecraftServer srv) {
		long now = srv.overworld().getGameTime();
		if (!BANNERS.isEmpty()) {
			for (Iterator<Banner> it = BANNERS.iterator(); it.hasNext();) {
				Banner b = it.next();
				if (now >= b.expires || !b.level.getBlockState(b.pos).is(Blocks.BANNER.black())) { removeBlock(b); it.remove(); continue; }
				if ((now & 19) == 0) applySpeed(b.level, b.pos);
			}
		}
		if (srv.getTickCount() % 10 != 0) return;
		for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
			AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed == null) continue;
			boolean on = wearing(p, EquipmentSlot.FEET, HrafnholtItems.WOLFSKIN_BOOTS) && onSnow(p);
			boolean has = speed.getModifier(SNOW_SPEED) != null;
			if (on && !has) speed.addTransientModifier(new AttributeModifier(SNOW_SPEED, SNOW_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			else if (!on && has) speed.removeModifier(SNOW_SPEED);
		}
	}

	/** Wolfskin Boots: friction above plain ground (ice 0.98, packed and blue ice) is replaced by 0.6, so the wearer never slips. Called from LivingEntityIceMixin on both sides. */
	public static float iceGrip(LivingEntity e, float friction) {
		return friction > 0.62F && e.getItemBySlot(EquipmentSlot.FEET).is(HrafnholtItems.WOLFSKIN_BOOTS) ? 0.6F : friction;
	}

	static boolean onSnow(Player p) {
		BlockPos feet = p.blockPosition();
		for (BlockPos q : new BlockPos[] {feet, feet.below()}) {
			BlockState s = p.level().getBlockState(q);
			if (s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SNOW_BLOCK)) return true;
		}
		return false;
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HrafnholtGear::afterDamage);
		ServerEntityEvents.ENTITY_LOAD.register(HrafnholtGear::tagArrow);
		ServerTickEvents.END_SERVER_TICK.register(HrafnholtGear::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			for (Banner b : BANNERS) removeBlock(b);
			BANNERS.clear();
		});
	}
}
