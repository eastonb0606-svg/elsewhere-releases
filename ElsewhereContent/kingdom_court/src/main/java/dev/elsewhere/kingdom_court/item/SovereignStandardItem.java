package dev.elsewhere.kingdom_court.item;

import dev.elsewhere.kingdom_court.CourtState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sovereign's Standard (King): use on the top of a block to plant the royal standard (a blue banner). Players and tamed
 * pets within 16 blocks get Regeneration I. One standard per player: planting again moves it. The item is kept.
 */
public class SovereignStandardItem extends Item {
	public static final int RADIUS = 16;

	public SovereignStandardItem(Item.Properties p) {
		super(p);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (ctx.getClickedFace() != Direction.UP) return InteractionResult.PASS;
		Player player = ctx.getPlayer();
		if (!(ctx.getLevel() instanceof ServerLevel level) || player == null) return InteractionResult.SUCCESS;
		BlockPos pos = ctx.getClickedPos().above();
		if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
		return plant(level, player, pos) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	public static boolean plant(ServerLevel level, Player player, BlockPos pos) {
		CourtState st = CourtState.get(level);
		Long old = st.standards.get(player.getUUID());
		if (old != null) {
			BlockPos op = BlockPos.of(old);
			if (level.getBlockState(op).is(Blocks.BANNER.blue())) level.removeBlock(op, false);
		}
		int rot = Math.floorMod((int) Math.floor((180.0F + player.getYRot()) * 16.0F / 360.0F + 0.5), 16);
		BlockState s = Blocks.BANNER.blue().defaultBlockState().setValue(BannerBlock.ROTATION, rot);
		if (!level.setBlock(pos, s, 3)) return false;
		st.standards.put(player.getUUID(), pos.asLong());
		st.touch();
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.elsewhere.court_standard_planted"));
		return true;
	}
}
