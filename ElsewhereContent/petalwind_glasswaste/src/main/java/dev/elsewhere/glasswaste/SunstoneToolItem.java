package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Sunstone tools: hits set the target alight while the wielder stands under the open sun. */
public class SunstoneToolItem extends Item {
	public SunstoneToolItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (attacker.level() instanceof ServerLevel level && Heat.inDirectSun(level, attacker)) target.igniteForSeconds(GearStats.SUNSTONE_IGNITE_SECONDS);
	}
}
