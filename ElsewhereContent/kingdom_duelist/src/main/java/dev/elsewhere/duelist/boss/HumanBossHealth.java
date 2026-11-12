package dev.elsewhere.duelist.boss;

import dev.elsewhere.duelist.DuelistContent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * RIVALS-DECISIONS section 13: no human kingdom boss ever has more than {@link #MAX} max health, whatever stacks on it
 * (succession boosts, Risen +15 percent, ruler or court bonuses, phases, the Test Meter's enemy multiplier).
 * {@code HumanBoss} calls {@link #enforce} on spawn, on load from disk and right after it changes a max-health modifier itself (court bonus); there is no per-tick check.
 * Whoever else adds or changes a max-health modifier on a human boss (succession, Risen, Test Meter) calls it right after. It keeps one transient modifier
 * {@code elsewhere:human_hp_cap} (ADD_MULTIPLIED_TOTAL, negative, scaled so the final value is exactly the cap) and
 * keeps current health at or below the max. Other lanes may call it for a human boss that is not a HumanBoss.
 */
public final class HumanBossHealth {
	private HumanBossHealth() {}

	public static final double MAX = 300.0;
	public static final Identifier CAP_ID = DuelistContent.id("human_hp_cap");

	public static void enforce(LivingEntity mob) {
		AttributeInstance a = mob.getAttribute(Attributes.MAX_HEALTH);
		if (a == null) return;
		AttributeModifier cap = a.getModifier(CAP_ID);
		// the true value without the cap, computed by hand: vanilla clamps getValue() at 1024, which would hide a larger stack
		double base = a.getBaseValue(), add = 0, multBase = 0, multTotal = 1;
		for (AttributeModifier m : a.getModifiers()) {
			if (m.id().equals(CAP_ID)) continue;
			switch (m.operation()) {
				case ADD_VALUE -> add += m.amount();
				case ADD_MULTIPLIED_BASE -> multBase += m.amount();
				case ADD_MULTIPLIED_TOTAL -> multTotal *= 1.0 + m.amount();
			}
		}
		double uncapped = (base + add) * (1.0 + multBase) * multTotal;
		if (uncapped > MAX + 1e-6) {
			double amount = MAX / uncapped - 1.0;
			if (cap == null || Math.abs(cap.amount() - amount) > 1e-9) a.addOrUpdateTransientModifier(new AttributeModifier(CAP_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (cap != null) {
			a.removeModifier(CAP_ID);
		}
		if (mob.getHealth() > mob.getMaxHealth()) mob.setHealth(mob.getMaxHealth());
	}
}
