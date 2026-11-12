package dev.elsewhere.palehollow.item;

import dev.elsewhere.boss.mother_creak.ElderTrunkArena;
import dev.elsewhere.palehollow.PaleHollowDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Planted in the Pale Hollow, it raises the Elder Trunk around you with Mother Creak asleep at the bottom. */
public class HeartwoodEffigyItem extends Item {
	public HeartwoodEffigyItem(final Properties properties) {
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
		BlockPos at = context.getClickedPos().above();
		ElderTrunkArena.buildAndSpawn(level, at, player);
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
