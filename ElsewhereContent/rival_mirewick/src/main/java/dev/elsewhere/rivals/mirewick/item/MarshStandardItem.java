package dev.elsewhere.rivals.mirewick.item;

import dev.elsewhere.rivals.mirewick.MirewickState;
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
 * Marsh-Lantern Standard (the Chair): use on the top of a block to plant a green banner. Players and tamed pets within 16
 * blocks get Water Breathing and Dolphin's Grace (MirewickPassives). One standard per player: planting again moves it.
 */
public class MarshStandardItem extends Item {
	public static final int RADIUS = 16;

	public MarshStandardItem(Item.Properties p) {
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
		MirewickState st = MirewickState.get(level.getServer());
		long[] old = st.standards.get(player.getUUID());
		if (old != null) {
			BlockPos op = BlockPos.of(old[0]);
			if (level.hasChunkAt(op) && level.getBlockState(op).is(Blocks.BANNER.green())) level.removeBlock(op, false);
		}
		int rot = Math.floorMod((int) Math.floor((180.0F + player.getYRot()) * 16.0F / 360.0F + 0.5), 16);
		BlockState s = Blocks.BANNER.green().defaultBlockState().setValue(BannerBlock.ROTATION, rot);
		if (!level.setBlock(pos, s, 3)) return false;
		st.standards.put(player.getUUID(), new long[] {pos.asLong(), level.getGameTime()});
		st.setDirty();
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.elsewhere.mirewick_standard_planted"));
		return true;
	}
}
