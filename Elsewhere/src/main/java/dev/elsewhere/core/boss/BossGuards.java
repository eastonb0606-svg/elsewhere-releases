package dev.elsewhere.core.boss;

import dev.elsewhere.ElsewhereConfig;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The Difficulty framework's two fairness guards (BOSSES.md). */
public final class BossGuards {
	private BossGuards() {
	}

	/** Overpower guard: per-hit damage dealt to a boss above 24 is halved past 24 (before armor). */
	public static float overpower(final float amount) {
		float t = (float)ElsewhereConfig.get().bosses.overpowerThreshold;
		return amount <= t ? amount : t + (amount - t) * 0.5F;
	}

	/** Damage left after the victim's armor and toughness (enchantments ignored, so the cap is conservative). */
	public static float afterArmor(final LivingEntity victim, final float raw, final DamageSource source) {
		float armor = (float)victim.getAttributeValue(Attributes.ARMOR);
		float toughness = (float)victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		return CombatRules.getDamageAfterAbsorb(victim, raw, source, armor, toughness);
	}

	/**
	 * Lethal-hit guard: lowers a boss hit so that, after armor, it takes at most 60% of the victim's max health.
	 * Returns the raw damage to deal.
	 */
	public static float lethalCap(final LivingEntity victim, final float raw, final DamageSource source) {
		float cap = victim.getMaxHealth() * (float)ElsewhereConfig.get().bosses.lethalHitCap;
		if (afterArmor(victim, raw, source) <= cap) {
			return raw;
		}
		float lo = 0.0F;
		float hi = raw;
		for (int i = 0; i < 24; i++) {
			float mid = (lo + hi) * 0.5F;
			if (afterArmor(victim, mid, source) > cap) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return lo;
	}
}
