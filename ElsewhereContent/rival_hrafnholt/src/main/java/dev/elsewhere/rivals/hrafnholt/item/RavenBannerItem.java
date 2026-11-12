package dev.elsewhere.rivals.hrafnholt.item;

import dev.elsewhere.rivals.hrafnholt.HrafnholtGear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Raven Banner (High Jarl): use it on top of a block to plant a black banner. You and any player or tamed pet within 16
 * blocks get Speed I; it burns down after 30 s. One banner per player (planting again moves it). The item is kept.
 * A plant-on-air-only tool: it never replaces a block and never lasts (HrafnholtGear keeps the ledger and clears it on stop).
 */
public class RavenBannerItem extends Item {
	public static final int RADIUS = 16, LIFETIME_TICKS = 600, COOLDOWN_TICKS = 100;

	public RavenBannerItem(Item.Properties p) {
		super(p);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (ctx.getClickedFace() != Direction.UP) return InteractionResult.PASS;
		Player player = ctx.getPlayer();
		if (!(ctx.getLevel() instanceof ServerLevel level) || player == null) return InteractionResult.SUCCESS;
		if (player.getCooldowns().isOnCooldown(ctx.getItemInHand())) {
			player.sendOverlayMessage(Component.translatable("message.elsewhere.hrafnholt_banner_cooldown"));
			return InteractionResult.FAIL;
		}
		BlockPos pos = ctx.getClickedPos().above();
		if (!HrafnholtGear.plantBanner(level, player, pos)) return InteractionResult.FAIL;
		player.getCooldowns().addCooldown(ctx.getItemInHand(), COOLDOWN_TICKS);
		player.sendOverlayMessage(Component.translatable("message.elsewhere.hrafnholt_banner_planted"));
		return InteractionResult.SUCCESS_SERVER;
	}
}
