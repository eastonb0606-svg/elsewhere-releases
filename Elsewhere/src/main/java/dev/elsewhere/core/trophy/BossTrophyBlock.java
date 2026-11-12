package dev.elsewhere.core.trophy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Boss trophy pedestal (new-boss-trophies). The block entity remembers the boss; breaking it gives the same trophy back. */
public class BossTrophyBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0);

	public BossTrophyBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new BossTrophyBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state, final @Nullable LivingEntity placer, final ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BossTrophyBlockEntity be) {
			be.setBoss(Trophies.bossOf(stack));
		}
	}

	@Override
	public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state, final Player player) {
		if (level instanceof ServerLevel sl && !player.isCreative() && level.getBlockEntity(pos) instanceof BossTrophyBlockEntity be && !be.boss().isEmpty()) {
			Block.popResource(sl, pos, Trophies.stack(be.boss()));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
