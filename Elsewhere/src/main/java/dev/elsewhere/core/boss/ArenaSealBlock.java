package dev.elsewhere.core.boss;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The translucent force-field wall that closes a boss arena while the fight runs. Unbreakable; removed on win or reset. */
public class ArenaSealBlock extends TransparentBlock {
	public ArenaSealBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected boolean skipRendering(final BlockState state, final BlockState neighbor, final Direction direction) {
		return neighbor.is(this) || super.skipRendering(state, neighbor, direction);
	}
}
