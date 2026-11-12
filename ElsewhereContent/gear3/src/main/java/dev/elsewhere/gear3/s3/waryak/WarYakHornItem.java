package dev.elsewhere.gear3.s3.waryak;

import dev.elsewhere.gear3.minion.MinionSlots;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionStaffItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * War-Yak Horn: a summoner staff (MinionStaffItem rules: hold use for the 1.0 s call, 10 Glimmer, 1 durability; sneak +
 * use sends this horn's War-Yaks home) blown like a goat horn. The yak takes 2 minion slots: older minions make room as
 * usual, and with fewer than 2 slots the call fails with a message (nothing spent).
 */
public class WarYakHornItem extends MinionStaffItem {
	public WarYakHornItem(Item.Properties props) {
		super(() -> WarYak.TYPE, props);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown() && player instanceof ServerPlayer sp && WarYak.ENABLED) WarYak.hornBlast(sp);
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer sp && !canCall(sp)) return stack;
		if (entity instanceof ServerPlayer sp) MinionSlots.track(sp, WarYak.TYPE);
		return super.finishUsingItem(stack, level, entity);
	}

	@Override
	public MinionEntity summonNow(ServerPlayer sp, ItemStack stack) {
		if (!canCall(sp)) return null;
		MinionSlots.track(sp, WarYak.TYPE);
		return super.summonNow(sp, stack);
	}

	/** Switched on, and the owner has the 2 slots a War-Yak needs (else an action-bar line says why). */
	public static boolean canCall(ServerPlayer sp) {
		if (!WarYak.ENABLED) {
			sp.sendOverlayMessage(Component.translatable("message.elsewhere.war_yak.off"));
			return false;
		}
		if (!MinionSlots.fits(sp, WarYak.TYPE)) {
			sp.sendOverlayMessage(Component.translatable("message.elsewhere.war_yak.slots", WarYak.SLOTS, MinionApi.maxSlots(sp)));
			return false;
		}
		return true;
	}
}
