package dev.elsewhere.ironpeak;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Yak Butter Tea: food 6/8, Resistance I for 60 s (via its consumable) and it clears freezing. */
public class ButterTeaItem extends Item {
	public ButterTeaItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		entity.setTicksFrozen(0);
		entity.clearFire();
		return super.finishUsingItem(stack, level, entity);
	}
}
