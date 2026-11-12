package dev.elsewhere.kingdom_court.api;

import dev.elsewhere.kingdom_court.CourtContent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * RIVALS-DECISIONS 13: a human kingdom boss never has more than {@link #CAP} max health, after every modifier (King's gating, succession, Risen +15 percent, Regents, heirs,
 * phases, the Test Meter multiplier). {@link #clamp} adds a negative {@code elsewhere:human_hp_cap} multiplier (300 / uncapped - 1) that brings the total down to the cap; it re-computes on every call, so
 * calling it again after any other change is always right. The court calls it on spawn, on every gating refresh, on Risen, succession and entity load, and wherever court code changes a max-health modifier. There is no periodic re-clamp (RIVALS-DECISIONS 17); vanilla setHealth already stops overhealing.
 */
public final class HpCap {
	private HpCap() {}

	public static final double CAP = 300.0;
	public static final Identifier ID = CourtContent.id("human_hp_cap");

	/** Max health the entity would have without the cap modifier. */
	public static double uncapped(LivingEntity e) {
		AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
		if (hp == null) return 0;
		AttributeModifier old = hp.getModifier(ID);
		if (old == null) return hp.getValue();
		hp.removeModifier(ID);
		double v = hp.getValue();
		hp.addPermanentModifier(old);
		return v;
	}

	/** Clamps max health to {@link #CAP}; health follows. Returns the max health after. */
	public static double clamp(LivingEntity e) {
		AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
		if (hp == null) return 0;
		double raw = uncapped(e);
		AttributeModifier cur = hp.getModifier(ID);
		// a multiplier on the total, so it is right whatever other modifiers there are (added values, multiplied bases and totals) and in whatever order
		double want = raw > CAP + 1e-6 ? CAP / raw - 1.0 : 0.0;
		double have = cur == null ? 0.0 : cur.amount();
		if (Math.abs(want - have) > 1e-9) {
			hp.removeModifier(ID);
			if (want < 0) hp.addPermanentModifier(new AttributeModifier(ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
		return e.getMaxHealth();
	}
}
