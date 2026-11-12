package dev.elsewhere.stub.nether_end_bosses;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** The Difficulty framework's two guards. */
public final class Guards {
	/** Per-hit damage dealt to a boss above this is halved past it, before armor. */
	public static final float OVERPOWER_THRESHOLD = 24.0F;
	/** No single hit from a boss takes more than this share of a player's max health after armor. */
	public static final float LETHAL_FRACTION = 0.6F;
	public static final String MINION_TAG = "elsewhere_boss_minion";

	private Guards() {}

	public static float overpower(float damage) {
		return damage > OVERPOWER_THRESHOLD ? OVERPOWER_THRESHOLD + (damage - OVERPOWER_THRESHOLD) / 2.0F : damage;
	}

	public static boolean isBossSource(DamageSource source) {
		return isBossEntity(source.getEntity()) || isBossEntity(source.getDirectEntity());
	}

	private static boolean isBossEntity(Entity e) {
		return e instanceof ElsewhereBoss || e instanceof BossPart || (e != null && e.entityTags().contains(MINION_TAG));
	}

	/** Called (via mixin) with a player's final damage after armor and enchantments. */
	public static float lethal(LivingEntity victim, DamageSource source, float damage) {
		if (victim instanceof Player && isBossSource(source)) {
			return Math.min(damage, victim.getMaxHealth() * LETHAL_FRACTION);
		}
		return damage;
	}
}
