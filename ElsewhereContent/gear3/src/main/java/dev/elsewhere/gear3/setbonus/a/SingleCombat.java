package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.minion.MinionEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Galeplate set, <b>Single Combat</b> (GEAR.md A5, Duelist): while exactly one hostile mob targets you, +10% damage dealt
 * and -10% damage taken. "Hostile" = any mob whose attack target is you. Mob hits only (PvP neutral): the bonus is dealt
 * to non-players and taken from non-players. The count is one bounded entity query on a hit, cached for 10 ticks per
 * player (several hits in one swing or tick share it); nothing ticks.
 */
public final class SingleCombat {
	private SingleCombat() {}

	public static final float DEALT = 0.10F, TAKEN = 0.10F;
	/** Search radius for mobs targeting the wearer (most mobs' follow range is 16-35). */
	public static final double RANGE = 32.0;
	public static final int CACHE_TICKS = 10;
	public static SetBonus BONUS;
	private static final Map<UUID, long[]> CACHE = new HashMap<>();

	static void init() {
		BONUS = SetBonuses.register(SetBonus.of("single_combat")
			.piece(EquipmentSlot.HEAD, "galeplate_helmet").piece(EquipmentSlot.CHEST, "galeplate_chestplate")
			.piece(EquipmentSlot.LEGS, "galeplate_leggings").piece(EquipmentSlot.FEET, "galeplate_boots")
			.details(2).onChange((p, on) -> CACHE.remove(p.getUUID())));
		SetBonuses.DAMAGE.add(SingleCombat::damage);
	}

	public static float damage(LivingEntity victim, DamageSource src, float amount) {
		Entity by = src.getEntity();
		if (by instanceof ServerPlayer p && victim != p && !(victim instanceof Player) && !(src.getDirectEntity() instanceof MinionEntity)) {
			float pw = SetBonuses.power(p, BONUS);
			if (pw > 0F && hostilesTargeting(p) == 1) amount *= 1F + DEALT * pw;
		}
		if (victim instanceof ServerPlayer p && by != null && by != p && !(by instanceof Player)) {
			float pw = SetBonuses.power(p, BONUS);
			if (pw > 0F && hostilesTargeting(p) == 1) amount *= Math.max(0F, 1F - TAKEN * pw);
		}
		return amount;
	}

	/** Mobs within {@link #RANGE} whose target is {@code p} (cached {@link #CACHE_TICKS} ticks). */
	public static int hostilesTargeting(ServerPlayer p) {
		long now = p.level().getGameTime();
		long[] c = CACHE.get(p.getUUID());
		if (c != null && now >= c[0] && now - c[0] < CACHE_TICKS) return (int) c[1];
		int n = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RANGE), m -> m.isAlive() && m.getTarget() == p).size();
		CACHE.put(p.getUUID(), new long[] {now, n});
		return n;
	}

	/** Self-test: forget the cached counts. */
	public static void clearCache() {
		CACHE.clear();
	}
}
