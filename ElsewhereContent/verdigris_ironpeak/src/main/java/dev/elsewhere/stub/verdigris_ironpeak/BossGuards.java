package dev.elsewhere.stub.verdigris_ironpeak;

import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** STUB of the core's Difficulty framework numbers: overpower guard, lethal-hit guard, difficulty scaling. */
public final class BossGuards {
	public static final float OVERPOWER_KNEE = 24.0F;
	public static final float LETHAL_FRACTION = 0.60F;

	private BossGuards() {}

	/** Per-hit damage to a boss above 24 is halved past 24, before armor. */
	public static float overpower(float damage) {
		return damage > OVERPOWER_KNEE ? OVERPOWER_KNEE + (damage - OVERPOWER_KNEE) / 2 : damage;
	}

	/** Raw values are for Normal; scripted attacks are x1.25 on Hard and x0.6 on Easy. */
	public static float scaled(Level level, float raw) {
		Difficulty d = level.getDifficulty();
		return d == Difficulty.HARD ? raw * 1.25F : d == Difficulty.EASY ? raw * 0.6F : d == Difficulty.PEACEFUL ? 0 : raw;
	}

	/** True when a hit comes from a boss (its body, or a projectile it owns). */
	public static boolean fromBoss(DamageSource source) {
		Entity e = source.getEntity();
		return e instanceof StubBoss || source.getDirectEntity() instanceof StubBoss;
	}

	/** No single boss hit takes more than 60% of a player's max health after armor. */
	public static float lethalCap(float afterArmor, float maxHealth) {
		return Math.min(afterArmor, maxHealth * LETHAL_FRACTION);
	}
}
