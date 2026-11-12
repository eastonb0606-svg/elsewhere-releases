package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/** Eel-Tooth Spear: an iron spear with +3 damage (attribute) and a further bonus against burrowed or burrowing mobs. */
public class EelToothSpearItem extends Item {
	public EelToothSpearItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
		return GearStats.EEL_SPEAR_BONUS_DAMAGE + (victim instanceof LivingEntity l && Burrowing.isBurrowing(l) ? GearStats.EEL_SPEAR_BURROW_BONUS : 0.0F);
	}
}
