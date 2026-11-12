package dev.elsewhere.palehollow.item;

import dev.elsewhere.boss.selenne.MoonringArena;
import dev.elsewhere.palehollow.PaleHollowDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Planted in the Pale Hollow, it raises a Moonring; pick the closed bloom at its centre to wake Selenne. */
public class MoonringSeedItem extends Item {
	public MoonringSeedItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(final UseOnContext context) {
		Player player = context.getPlayer();
		if (!(context.getLevel() instanceof ServerLevel level) || player == null) {
			return InteractionResult.SUCCESS;
		}
		if (!level.dimension().equals(PaleHollowDimension.LEVEL) && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.elsewhere.effigy.wrong_place").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		MoonringArena.build(level, context.getClickedPos().above());
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
