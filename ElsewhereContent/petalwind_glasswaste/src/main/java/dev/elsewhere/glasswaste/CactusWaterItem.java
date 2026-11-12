package dev.elsewhere.glasswaste;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cactus Water: drinking it resets your Heat (Fire Resistance comes from the consumable component). */
public class CactusWaterItem extends Item {
	public CactusWaterItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) Heat.set(player, 0);
		return super.finishUsingItem(stack, level, entity);
	}
}
