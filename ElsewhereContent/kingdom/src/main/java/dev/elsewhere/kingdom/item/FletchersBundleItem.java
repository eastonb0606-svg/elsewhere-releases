package dev.elsewhere.kingdom.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Fletcher's Bundle: use to unpack 8 arrows and 1 spectral arrow. */
public class FletchersBundleItem extends Item {
	public static final int ARROWS = 8, SPECTRAL = 1;

	public FletchersBundleItem(Properties p) {
		super(p);
	}

	/** Gives the bundle's contents to the player (dropped if the inventory is full). */
	public static void unpack(Player player) {
		for (ItemStack s : new ItemStack[] {new ItemStack(Items.ARROW, ARROWS), new ItemStack(Items.SPECTRAL_ARROW, SPECTRAL)})
			player.getInventory().placeItemBackInInventory(s, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack self = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			unpack(player);
			self.consume(1, player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, java.util.function.Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
	}
}
