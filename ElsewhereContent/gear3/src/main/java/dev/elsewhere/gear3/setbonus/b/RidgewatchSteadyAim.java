package dev.elsewhere.gear3.setbonus.b;

import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

/**
 * Ridgewatch set "Steady Aim" (Sniper, 4 pieces; GEAR.md A5): a full-draw bow shot made while sneaking, or after holding
 * still for 1 s (the whole draw, from the moment the bow was raised), flies with no spread and deals +30%.
 *
 * <p>Events only: the draw start (tick and position) is recorded when the wearer raises a bow (SetBonuses.STANCE: item use
 * start), and compared when the arrow is loosed. "No spread" is the bow's inaccuracy set to 0 for that shot
 * (SetBonusBMixins.Shoot); the +30% is an arrow tag applied when it hits. Never against players. Kill switch
 * {@code setbonus_steady_aim}.
 */
public final class RidgewatchSteadyAim {
	private RidgewatchSteadyAim() {}

	public static final float BONUS = 0.30F;
	/** Held still this long (the full draw of a vanilla bow) counts as steady. */
	public static final int STILL_TICKS = 20;
	/** "Still": no more than 0.1 block from where the draw began. */
	public static final double STILL_DIST_SQ = 0.1 * 0.1;
	public static final String TAG = "elsewhere_g3_steady:";
	public static SetBonus SET;
	/** Steady shots loosed with no spread (self-test reads it). */
	public static int steadyShots;

	private record Draw(long tick, Vec3 pos) {}

	private static final Map<UUID, Draw> DRAWS = new HashMap<>();

	static void init() {
		SET = SetBonuses.register(SetBonus.of("steady_aim")
			.piece(EquipmentSlot.HEAD, "ridgewatch_hood")
			.piece(EquipmentSlot.CHEST, "ridgewatch_coat")
			.piece(EquipmentSlot.LEGS, "ridgewatch_leggings")
			.piece(EquipmentSlot.FEET, "ridgewatch_boots")
			.details(2)
			.onChange((p, on) -> {
				if (!on) DRAWS.remove(p.getUUID());
			}));
		SetBonuses.STANCE.add(RidgewatchSteadyAim::stance);
		SetBonuses.DAMAGE.add(RidgewatchSteadyAim::damage);
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> DRAWS.remove(h.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> DRAWS.clear());
	}

	/** SetBonuses.STANCE: a wearer raised a bow (record where and when) or stopped using an item (forget it). */
	static void stance(ServerPlayer p) {
		if (!SetBonuses.active(p, SET)) return;
		if (p.isUsingItem() && p.getUseItem().getItem() instanceof BowItem) {
			if (p.getTicksUsingItem() == 0 || !DRAWS.containsKey(p.getUUID())) startDraw(p);
		} else {
			DRAWS.remove(p.getUUID());
		}
	}

	/** Records the start of a bow draw (public for the self-test). */
	public static void startDraw(ServerPlayer p) {
		DRAWS.put(p.getUUID(), new Draw(p.level().getGameTime(), p.position()));
	}

	/** Self-test: a draw that began {@code ticksAgo} ticks ago at the player's current position (game time does not move in the test). */
	public static void startDrawForTest(ServerPlayer p, long ticksAgo) {
		DRAWS.put(p.getUUID(), new Draw(p.level().getGameTime() - ticksAgo, p.position()));
	}

	/** Pure rule: drawn for at least 1 s without moving more than 0.1 block. */
	public static boolean stillSince(long startTick, Vec3 startPos, long now, Vec3 pos) {
		return now - startTick >= STILL_TICKS && pos.distanceToSqr(startPos) <= STILL_DIST_SQ;
	}

	/** A shot loosed now by {@code p} is steady: the set is on and {@code p} is sneaking or has held still through a 1 s draw. */
	public static boolean steady(ServerPlayer p) {
		if (!SetBonuses.active(p, SET)) return false;
		if (p.isShiftKeyDown()) return true;
		Draw d = DRAWS.get(p.getUUID());
		return d != null && stillSince(d.tick(), d.pos(), p.level().getGameTime(), p.position());
	}

	/** SetBonusBMixins.Shoot: the inaccuracy a bow's full-draw shot uses (0 when steady). Bows only; crossbows never. */
	public static float inaccuracy(ProjectileWeaponItem weapon, LivingEntity shooter, boolean fullDraw, float inaccuracy) {
		if (!fullDraw || !(weapon instanceof BowItem) || !(shooter instanceof ServerPlayer p) || !SetBonuses.anyone() || !steady(p)) return inaccuracy;
		steadyShots++;
		return 0F;
	}

	/** Multiplier at bonus strength {@code power}: 1 + 0.30 x power. */
	public static float mult(float power) {
		return 1F + BONUS * Math.max(0F, power);
	}

	/** An arrow loosed by {@code p}: a full-draw (critical) bow arrow from a steady wearer gets the +30% tag. */
	static void onLoose(ServerPlayer p, AbstractArrow arrow) {
		if (!arrow.isCritArrow() || !p.isUsingItem() || !(p.getUseItem().getItem() instanceof BowItem)) return;
		if (SetBonusesB.tagValue(arrow, TAG) > 0F || !steady(p)) return;
		arrow.addTag(TAG + mult(SetBonuses.power(p, SET)));
	}

	/** SetBonuses.DAMAGE: a tagged arrow hitting a non-player. */
	public static float damage(LivingEntity victim, DamageSource src, float amount) {
		if (victim instanceof Player || !(src.getDirectEntity() instanceof AbstractArrow arrow)) return amount;
		float m = SetBonusesB.tagValue(arrow, TAG);
		return m > 0F ? amount * m : amount;
	}
}
