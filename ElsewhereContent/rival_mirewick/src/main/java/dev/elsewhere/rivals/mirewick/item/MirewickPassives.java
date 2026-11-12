package dev.elsewhere.rivals.mirewick.item;

import dev.elsewhere.rivals.mirewick.MirewickContent;
import dev.elsewhere.rivals.mirewick.MirewickState;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Server-side passives of the Mirewick items: wet Brigandine, plank speed, Coronet, Cowl, planted Standards, Marshbow arrows. */
public final class MirewickPassives {
	private MirewickPassives() {}

	public static final Identifier WET_ARMOR = MirewickContent.id("brigandine_wet_armor");
	public static final Identifier WET_TOUGH = MirewickContent.id("brigandine_wet_toughness");
	public static final Identifier PLANK_SPEED = MirewickContent.id("debt_collector_planks");
	public static final double COWL_BONUS = 1.10;
	private static final List<WeakReference<AbstractArrow>> ARROWS = new ArrayList<>();
	private static final Map<UUID, Map<Holder<MobEffect>, Integer>> COWL = new HashMap<>();

	public static void track(AbstractArrow a) {
		ARROWS.add(new WeakReference<>(a));
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(MirewickPassives::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || taken <= 0 || !(entity instanceof ServerPlayer p) || !p.isAlive()) return;
			if (!p.getItemBySlot(EquipmentSlot.HEAD).is(MirewickItems.LANTERN_CORONET) || !p.isInWaterOrRain()) return;
			p.heal((float) (taken * coronetReduction(p)));
		});
	}

	/** The Coronet's damage reduction while wet: 10% plus 1% per Lord Seal carried (the seal part capped at 12%). */
	public static double coronetReduction(ServerPlayer p) {
		int seals = 0;
		for (ItemStack s : p.getInventory()) if (!s.isEmpty() && s.is(MirewickItems.LORE_SEALS)) seals += 1;
		return 0.10 + Math.min(0.12, seals * 0.01);
	}

	static boolean wearing(LivingEntity e, EquipmentSlot slot, Item item) {
		return item != null && e.getItemBySlot(slot).is(item);
	}

	static void setMod(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op, boolean on) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst == null) return;
		if (on) {
			AttributeModifier cur = inst.getModifier(id);
			if (cur == null || cur.amount() != amount) {
				inst.removeModifier(id);
				inst.addTransientModifier(new AttributeModifier(id, amount, op));
			}
		} else if (inst.hasModifier(id)) inst.removeModifier(id);
	}

	/** True on mud-brick and planks, the surfaces the Debt-Collector's Leggings speed up. */
	public static boolean fastGround(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(Blocks.MUD_BRICKS) || s.is(Blocks.MUD_BRICK_STAIRS) || s.is(Blocks.MUD_BRICK_SLAB);
	}

	static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 5 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				boolean wet = wearing(p, EquipmentSlot.CHEST, MirewickItems.DIKEWARDENS_BRIGANDINE) && p.isInWaterOrRain();
				setMod(p, Attributes.ARMOR, WET_ARMOR, MirewickItems.REBALANCE_RIVALS ? 1.0 : 2.0, AttributeModifier.Operation.ADD_VALUE, wet); // 3.0: dry 7/2.5, wet still 8/3
				setMod(p, Attributes.ARMOR_TOUGHNESS, WET_TOUGH, MirewickItems.REBALANCE_RIVALS ? 0.5 : 1.0, AttributeModifier.Operation.ADD_VALUE, wet);
				boolean fast = wearing(p, EquipmentSlot.LEGS, MirewickItems.DEBT_COLLECTORS_LEGGINGS) && p.onGround() && fastGround(p.level().getBlockState(p.blockPosition().below()));
				setMod(p, Attributes.MOVEMENT_SPEED, PLANK_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, fast);
				cowl(p);
			}
		}
		if (t % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) coronet(p);
			standards(server);
		}
		for (Iterator<WeakReference<AbstractArrow>> it = ARROWS.iterator(); it.hasNext(); ) {
			AbstractArrow a = it.next().get();
			if (a == null || a.isRemoved()) {
				it.remove();
				continue;
			}
			if (a.isInWater()) {
				a.setNoGravity(true);
				a.setDeltaMovement(a.getDeltaMovement().scale(1.0 / 0.6 * 0.995));
			} else if (a.isNoGravity()) a.setNoGravity(false);
		}
	}

	static void coronet(ServerPlayer p) {
		if (!wearing(p, EquipmentSlot.HEAD, MirewickItems.LANTERN_CORONET)) return;
		var biome = p.level().getBiome(p.blockPosition());
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || p.isUnderWater())
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
	}

	/** Calling: a beneficial effect that appears (or is renewed) while the Cowl is worn lasts 10% longer. Beacon effects are ambient and skipped. */
	static void cowl(ServerPlayer p) {
		if (!wearing(p, EquipmentSlot.HEAD, MirewickItems.ALMONERS_COWL)) {
			COWL.remove(p.getUUID());
			return;
		}
		Map<Holder<MobEffect>, Integer> last = COWL.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		Map<Holder<MobEffect>, Integer> now = new HashMap<>();
		for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects())) {
			if (e.isAmbient() || e.isInfiniteDuration() || !e.getEffect().value().isBeneficial()) continue;
			int d = e.getDuration();
			Integer old = last.get(e.getEffect());
			if (old == null || d > old + 10) {
				int nd = (int) Math.round(d * COWL_BONUS);
				p.addEffect(new MobEffectInstance(e.getEffect(), nd, e.getAmplifier(), false, e.isVisible(), e.showIcon()));
				d = nd;
			}
			now.put(e.getEffect(), d);
		}
		last.clear();
		last.putAll(now);
	}

	static void standards(MinecraftServer server) {
		MirewickState st = MirewickState.get(server);
		if (st.standards.isEmpty()) return;
		ServerLevel level = server.overworld();
		boolean changed = false;
		for (Iterator<Map.Entry<UUID, long[]>> it = st.standards.entrySet().iterator(); it.hasNext(); ) {
			BlockPos pos = BlockPos.of(it.next().getValue()[0]);
			if (!level.hasChunkAt(pos)) continue;
			if (!level.getBlockState(pos).is(Blocks.BANNER.green())) {
				it.remove();
				changed = true;
				continue;
			}
			AABB box = new AABB(pos).inflate(MarshStandardItem.RADIUS);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x instanceof ServerPlayer || (x instanceof TamableAnimal ta && ta.isTame()))) {
				if (e.position().distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 256) continue;
				e.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false, true));
				e.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100, 0, true, false, true));
			}
		}
		if (changed) st.setDirty();
	}
}
