package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/**
 * Glassfang (Ankhara's drop): +4 damage against burrowing or armored targets, and it pierces 4 armor (applied as the
 * damage that 4 armor points would have absorbed).
 */
public class GlassfangItem extends Item {
	public GlassfangItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
		float bonus = 0;
		if (victim instanceof LivingEntity living) {
			if (living instanceof net.minecraft.world.entity.player.Player) return 0; // pierce applies to mobs and bosses only
			double armor = Math.min(living.getArmorValue(), GearStats.GLASSFANG_ARMOR_PIERCE);
			bonus += (float) (damage * armor * 0.04); // vanilla: each armor point absorbs ~4% at low damage
		}
		return bonus;
	}
}
