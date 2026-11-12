package dev.elsewhere.duelist.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code elsewhere:duel_block}: the soft temporary block of the Human Duelist kit (bridges, pillars, walls). Breaks in
 * about 14 ticks with any tool, drops nothing. It only ever exists while {@link DuelBlocks}' ledger says so: a block the
 * ledger does not know (a crash lost it) removes itself on its next random tick.
 */
public class DuelBlock extends Block {
	public DuelBlock(Properties p) {
		super(p);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!DuelBlocks.known(level, pos)) level.removeBlock(pos, false);
	}
}
