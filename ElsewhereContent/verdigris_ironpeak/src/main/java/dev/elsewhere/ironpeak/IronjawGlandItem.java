package dev.elsewhere.ironpeak;

import dev.elsewhere.verdigris_ironpeak.compat.TestMeterBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Ironjaw Gland: Test Meter consumable, a large adrenaline effect (double the Warden gland's duration). */
public class IronjawGlandItem extends Item {
	public IronjawGlandItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) TestMeterBridge.adrenaline(player, 2.0);
		return super.finishUsingItem(stack, level, entity);
	}
}
