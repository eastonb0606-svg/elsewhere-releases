package dev.elsewhere.gear3.s3.mothlight;

import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.minion.MinionStaffItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Mothlight Staff: a summoner minion staff (the 1.0 s call at bow-draw speed, 10 Glimmer and 1 durability per call, damage
 * interrupts the call, sneak-use dismisses its swarm) that calls a {@link MothlightSwarm}. Repaired with Moonsilk Thread
 * ({@code #elsewhere:repairs_mothlight_staff}). Catalogue mechanic {@code "mothlight_staff"} (Gear3Mechanics).
 */
public class MothlightStaffItem extends MinionStaffItem {
	public MothlightStaffItem(Properties props) {
		super(() -> Mothlight.SWARM, props.repairable(Gear3Items.repairTag(Mothlight.STAFF_ID)));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!Mothlight.enabled && !player.isShiftKeyDown()) {
			if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.elsewhere.mothlight_off"));
			return InteractionResult.FAIL;
		}
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!Mothlight.enabled) return stack;
		return super.finishUsingItem(stack, level, entity);
	}
}
