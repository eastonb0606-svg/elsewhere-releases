package dev.elsewhere.palehollow;

import dev.elsewhere.palehollow.item.AntlerBowItem;
import dev.elsewhere.registry.ModItems;
import dev.elsewhere.tiergear.TierGear;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Pale Hollow gear perks (BALANCE.md M-core): Heartwood tools root Stillness mobs (Creaking, Creaking Husk; not bosses) 2 s,
 * once per 6 s per target; Antler Bow arrows root them 2 s, once per 4 s per target; each Paleweave piece cuts the range at
 * which Hollow mobs and Creakings notice you by 12.5%.
 */
public final class PaleHollowGear {
	private PaleHollowGear() {}

	public static final int ROOT_TICKS = 40;
	public static final int HEARTWOOD_ROOT_COOLDOWN = 120;
	public static final int ANTLER_ROOT_COOLDOWN = 80;
	public static final double NOTICE_CUT_PER_PIECE = 0.125;
	private static final Map<UUID, Long> ROOTED = new HashMap<>();

	public static boolean isHeartwoodTool(final ItemStack s) {
		return s.is(ModItems.HEARTWOOD_SWORD) || s.is(ModItems.HEARTWOOD_PICKAXE) || s.is(ModItems.HEARTWOOD_AXE) || s.is(ModItems.HEARTWOOD_SHOVEL)
			|| s.is(ModItems.HEARTWOOD_HOE);
	}

	public static boolean isStillnessMob(final Entity e) {
		return (e instanceof StillnessMob || e instanceof Creaking) && !TierGear.isBoss(e);
	}

	public static int paleweavePieces(final LivingEntity e) {
		int n = 0;
		Item[] set = {ModItems.PALEWEAVE_HELMET, ModItems.PALEWEAVE_MANTLE, ModItems.PALEWEAVE_LEGGINGS, ModItems.PALEWEAVE_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			if (e.getItemBySlot(slots[i]).is(set[i])) {
				n++;
			}
		}
		return n;
	}

	/** Visibility multiplier for {@code target} as seen by {@code looker} (1.0 = unchanged). */
	public static double noticeFactor(final LivingEntity target, final Entity looker) {
		if (!(target instanceof Player) || looker == null) {
			return 1.0;
		}
		boolean hollowMob = looker instanceof Creaking || looker instanceof StillnessMob
			|| looker.level().dimension().equals(PaleHollowDimension.LEVEL) && !(looker instanceof Player);
		if (!hollowMob) {
			return 1.0;
		}
		return 1.0 - NOTICE_CUT_PER_PIECE * paleweavePieces(target);
	}

	/** Roots a Stillness mob (Slowness 10 for 2 s) unless it was rooted within {@code cooldown} ticks. Returns true if rooted. */
	public static boolean root(final LivingEntity target, final int cooldown) {
		long now = target.level().getGameTime();
		Long last = ROOTED.get(target.getUUID());
		if (last != null && now - last < cooldown) {
			return false;
		}
		ROOTED.put(target.getUUID(), now);
		if (ROOTED.size() > 1024) {
			ROOTED.clear();
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROOT_TICKS, 9));
		if (target instanceof StillnessMob s) {
			s.stillnessFreeze(ROOT_TICKS);
		}
		return true;
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0F || !isStillnessMob(entity)) {
				return;
			}
			if (source.getDirectEntity() instanceof Player p && isHeartwoodTool(p.getMainHandItem())) {
				root(entity, HEARTWOOD_ROOT_COOLDOWN);
			} else if (source.getDirectEntity() instanceof AbstractArrow a && a.entityTags().contains(AntlerBowItem.ROOT_TAG)) {
				root(entity, ANTLER_ROOT_COOLDOWN);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ROOTED.clear());
	}
}
