package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import net.minecraft.world.damagesource.DamageSource;

/** Numbers and checks for the boss guard mixin (kept outside the mixin package). */
public final class BossGuards {
	private BossGuards() {}

	public static final float OVERPOWER_THRESHOLD = 24.0F;
	public static final float LETHAL_CAP = 0.6F;

	public static float overpower(float damage) {
		return damage <= OVERPOWER_THRESHOLD ? damage : OVERPOWER_THRESHOLD + (damage - OVERPOWER_THRESHOLD) / 2.0F;
	}

	public static boolean fromBoss(DamageSource source) {
		return source.getEntity() instanceof BossBase || source.getDirectEntity() instanceof BossBase;
	}
}
