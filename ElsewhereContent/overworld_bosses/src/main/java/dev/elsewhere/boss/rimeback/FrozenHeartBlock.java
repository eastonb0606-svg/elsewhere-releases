package dev.elsewhere.boss.rimeback;

import dev.elsewhere.overworld_bosses.Summons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** {@code elsewhere:frozen_heart}: breaking it on the Glacier Shelf wakes Rimeback. Drops nothing. */
public class FrozenHeartBlock extends Block {
	public FrozenHeartBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel sl && !player.isCreative()) Summons.summonRimeback(sl, pos);
		return super.playerWillDestroy(level, pos, state, player);
	}
}
