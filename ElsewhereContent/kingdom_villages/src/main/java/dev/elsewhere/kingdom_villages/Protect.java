package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * Bystander protection (FIELD-TACTICS 1.8, 3.2): soldiers never hurt villagers (stray arrows and area effects included), and
 * Azmar's soldiers never hurt the iron golems of a village they hold or are taking unless the golem is fighting them. The block
 * and crop rules sit with the writer (soldiers break no blocks) and {@code VillageFarmlandMixin} (no trampling).
 */
public final class Protect {
	private Protect() {}

	public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		return !shielded(victim, source.getEntity());
	}

	/** True when {@code attacker}'s damage to {@code victim} must be cancelled. */
	public static boolean shielded(LivingEntity victim, Entity attacker) {
		if (!(attacker instanceof KingdomSoldier ks)) return false;
		if (victim instanceof AbstractVillager) return true;
		if (victim instanceof IronGolem g && !g.isPlayerCreated() && FactionVillages.of(ks.factionId()).protectsGolems()) return g.getTarget() != ks;
		return false;
	}
}
