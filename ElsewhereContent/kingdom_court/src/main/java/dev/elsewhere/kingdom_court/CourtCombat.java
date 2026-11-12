package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom_court.item.AshgroveLongbowItem;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;

/**
 * Player-side effects of the court items and the Lords' decrees: Duelist parry, Crown Rally (Glimmer-fuelled, stronger
 * per Lord Seal carried), Warmarshal's Spurs, Hunter's Mark / Ashgrove Longbow, Chancellor's Chaperon XP tithe,
 * Sovereign's Standard regeneration, and the Chancellor's Silence of Writ and Tithe decrees.
 */
public final class CourtCombat {
	private CourtCombat() {}

	public static final Identifier SPURS_SPEED = CourtContent.id("court_spurs_speed");
	public static final double SPURS_MOUNT_SPEED = 0.25;
	public static final float SPURS_MOUNT_GUARD = 0.20F;
	public static final float RALLY_BASE = 0.10F, RALLY_PER_SEAL = 0.01F;
	public static final double RALLY_RADIUS = 10.0;
	public static final float RALLY_GLIMMER_PER_SECOND = 0.5F;
	public static final float MARK_BONUS_PLAYER = 0.15F, MARK_BONUS_HUNTSMAN = 0.30F;
	public static final float CHAPERON_XP = 0.25F;

	static final Map<UUID, Long> PARRY = new HashMap<>();
	static final Map<UUID, Long> MARKED = new HashMap<>();
	static final Map<UUID, Long> SILENCED = new HashMap<>();
	static final Map<UUID, Long> TITHED = new HashMap<>();
	static final Map<UUID, UUID> SPUR_MOUNTS = new HashMap<>();
	/** Active Rally sources this second: wearer, its level and the reduction. */
	public record Rally(UUID wearer, ServerLevel level, double x, double y, double z, float pct) {}
	public static final List<Rally> RALLIES = new ArrayList<>();
	public static int parries, decreesBlocked, tithes;

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CourtCombat::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(CourtCombat::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack s = player.getItemInHand(hand);
			if (isSilenced(player, level.getGameTime()) && (s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem || s.getItem() instanceof TridentItem)) {
				decreesBlocked++;
				player.sendOverlayMessage(Component.translatable("message.elsewhere.court_decree_silence_active"));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && isTithed(player, level.getGameTime())) applyTithe(player);
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, src) -> {
			if (dead instanceof Mob && !(dead instanceof Player) && src.getEntity() instanceof ServerPlayer p
				&& p.getItemBySlot(EquipmentSlot.HEAD).is(CourtItems.CHANCELLORS_CHAPERON) && dead.level() instanceof ServerLevel sl) {
				int xp = dead.getExperienceReward(sl, p);
				int extra = (int) Math.ceil(xp * CHAPERON_XP);
				if (extra > 0) ExperienceOrb.award(sl, dead.position(), extra);
			}
		});
	}

	// ---- parry (Duelist's Sabre) ------------------------------------------------------------------------------------

	public static void openParry(Player p, long until) {
		PARRY.put(p.getUUID(), until);
	}

	static boolean allowDamage(LivingEntity victim, DamageSource src, float amount) {
		if (!(victim instanceof Player p) || !(victim.level() instanceof ServerLevel sl)) return true;
		Long until = PARRY.get(p.getUUID());
		if (until == null || sl.getGameTime() > until) return true;
		Entity att = src.getEntity();
		if (!(att instanceof LivingEntity la) || src.getDirectEntity() != att || src.isDirect() == false) return true;
		PARRY.remove(p.getUUID());
		parries++;
		double dx = la.getX() - p.getX(), dz = la.getZ() - p.getZ();
		la.knockback(0.6, -dx, -dz, la.damageSources().generic(), 0.0F);
		p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0));
		sl.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
		return false;
	}

	// ---- decrees --------------------------------------------------------------------------------------------------

	public static void silence(Player p, long until) {
		SILENCED.put(p.getUUID(), until);
	}

	public static boolean isSilenced(Player p, long now) {
		Long u = SILENCED.get(p.getUUID());
		return u != null && now <= u;
	}

	public static void tithe(Player p, long until) {
		TITHED.put(p.getUUID(), until);
	}

	public static boolean isTithed(Player p, long now) {
		Long u = TITHED.get(p.getUUID());
		return u != null && now <= u;
	}

	public static void applyTithe(Player player) {
		ItemStack w = player.getMainHandItem();
		if (w.isDamageableItem()) {
			w.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			tithes++;
		}
	}

	// ---- Hunter's Mark ----------------------------------------------------------------------------------------------

	public static void mark(LivingEntity e, long until) {
		MARKED.put(e.getUUID(), until);
		e.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Math.max(20, until - e.level().getGameTime()), 0));
	}

	public static boolean isMarked(LivingEntity e) {
		Long u = MARKED.get(e.getUUID());
		return u != null && e.level().getGameTime() <= u;
	}

	// ---- damage scaling (mixin) -------------------------------------------------------------------------------------

	public static float scaleIncoming(LivingEntity victim, DamageSource src, float amount) {
		if (victim.level().isClientSide() || amount <= 0) return amount;
		float m = 1.0F;
		Entity direct = src.getDirectEntity();
		if (direct != null && direct.entityTags().contains(AshgroveLongbowItem.TAG)) {
			if (isMarked(victim)) m *= 1.0F + MARK_BONUS_PLAYER;
			if (direct.entityTags().contains(AshgroveLongbowItem.TAG_FULL)) mark(victim, victim.level().getGameTime() + 120);
		}
		if (src.getEntity() instanceof CourtRuler r && r.ruler == Ruler.HUNTSMAN && isMarked(victim)) m *= 1.0F + MARK_BONUS_HUNTSMAN;
		for (Entity pass : victim.getPassengers())
			if (pass instanceof Player p && p.getItemBySlot(EquipmentSlot.FEET).is(CourtItems.WARMARSHAL_SPURS)) {
				m *= 1.0F - SPURS_MOUNT_GUARD;
				break;
			}
		if (!RALLIES.isEmpty() && (victim instanceof Player || victim instanceof TamableAnimal t && t.isTame())) {
			float best = 0;
			for (Rally r : RALLIES)
				if (r.level == victim.level() && victim.distanceToSqr(r.x, r.y, r.z) <= RALLY_RADIUS * RALLY_RADIUS) best = Math.max(best, r.pct);
			m *= 1.0F - best;
		}
		return amount * m;
	}

	// ---- per-tick upkeep ----------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		if (t % 10 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) tickSpurs(p);
		}
		if (t % 20 == 0) {
			RALLIES.clear();
			for (ServerLevel level : server.getAllLevels()) for (Player p : level.players()) {
				Rally r = rallyOf(p, true);
				if (r != null) RALLIES.add(r);
			}
			long now = server.overworld().getGameTime();
			PARRY.values().removeIf(v -> v < now - 200);
			MARKED.values().removeIf(v -> v < now - 200);
		}
		if (t % 40 == 0) tickStandards(server);
	}

	public static void tickSpurs(Player p) {
		boolean wearing = p.getItemBySlot(EquipmentSlot.FEET).is(CourtItems.WARMARSHAL_SPURS);
		Entity v = p.getVehicle();
		UUID prev = SPUR_MOUNTS.get(p.getUUID());
		if (prev != null && (v == null || !v.getUUID().equals(prev) || !wearing)) {
			Entity old = p.level() instanceof ServerLevel sl ? sl.getEntity(prev) : null;
			if (old instanceof LivingEntity le) {
				AttributeInstance a = le.getAttribute(Attributes.MOVEMENT_SPEED);
				if (a != null) a.removeModifier(SPURS_SPEED);
			}
			SPUR_MOUNTS.remove(p.getUUID());
		}
		if (wearing && v instanceof LivingEntity mount) {
			AttributeInstance a = mount.getAttribute(Attributes.MOVEMENT_SPEED);
			if (a != null && !a.hasModifier(SPURS_SPEED))
				a.addTransientModifier(new AttributeModifier(SPURS_SPEED, SPURS_MOUNT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			SPUR_MOUNTS.put(p.getUUID(), mount.getUUID());
		}
	}

	/** The Rally this player gives, or null (no Crown, or no Glimmer left when {@code pay}). */
	public static Rally rallyOf(Player p, boolean pay) {
		if (!p.getItemBySlot(EquipmentSlot.HEAD).is(CourtItems.CROWN_OF_ALDERMARK) || !(p.level() instanceof ServerLevel sl)) return null;
		if (pay && p instanceof ServerPlayer sp && !glimmerDrain(sp, RALLY_GLIMMER_PER_SECOND)) return null;
		Set<Item> seals = new HashSet<>();
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.isEmpty() && s.is(CourtItems.LORE_SEALS)) seals.add(s.getItem());
		}
		float pct = RALLY_BASE + RALLY_PER_SEAL * Math.min(5, seals.size());
		return new Rally(p.getUUID(), sl, p.getX(), p.getY(), p.getZ(), pct);
	}

	// ---- Glimmer (arsenal) by reflection: no-op success when the arsenal module is absent ------------------------

	private static Method glimmerDrain;
	private static boolean glimmerLooked;
	public static boolean glimmerPresent;

	public static boolean glimmerDrain(ServerPlayer p, float amount) {
		if (!glimmerLooked) {
			glimmerLooked = true;
			try {
				glimmerDrain = Class.forName("dev.elsewhere.arsenal.glimmer.Glimmer").getMethod("drain", ServerPlayer.class, float.class);
				glimmerPresent = true;
			} catch (ReflectiveOperationException | LinkageError e) {
				glimmerDrain = null;
			}
		}
		if (glimmerDrain == null) return true;
		try {
			return (Boolean) glimmerDrain.invoke(null, p, amount);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true;
		}
	}

	// ---- Sovereign's Standard --------------------------------------------------------------------------------------

	public static int standardBuffs;

	static void tickStandards(MinecraftServer server) {
		CourtState st = CourtState.get(server);
		if (st.standards.isEmpty()) return;
		List<UUID> gone = new ArrayList<>();
		for (Map.Entry<UUID, Long> e : st.standards.entrySet()) {
			BlockPos pos = BlockPos.of(e.getValue());
			boolean found = false;
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.BANNER.blue())) continue;
				found = true;
				for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(16),
					x -> x instanceof Player || x instanceof TamableAnimal ta && ta.isTame())) {
					le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
					standardBuffs++;
				}
			}
			if (!found && server.overworld().isLoaded(pos)) gone.add(e.getKey());
		}
		if (!gone.isEmpty()) {
			gone.forEach(st.standards::remove);
			st.touch();
		}
	}
}
