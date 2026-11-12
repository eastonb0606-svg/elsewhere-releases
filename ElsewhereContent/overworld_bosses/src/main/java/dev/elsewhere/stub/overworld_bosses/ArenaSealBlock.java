package dev.elsewhere.stub.overworld_bosses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code elsewhere:arena_seal}: the translucent force-field wall that closes a boss arena. Players can walk through
 * it (leaving the seal for 30 s drops them from the fight), mobs, minions and the boss cannot. Unbreakable; removed
 * by the arena restore. STUB: the core owns the real block.
 */
public class ArenaSealBlock extends Block {
	public ArenaSealBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext ec) {
			Entity e = ec.getEntity();
			if (e == null || e instanceof Player) return Shapes.empty();
		}
		return Shapes.block();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState neighbor, Direction direction) {
		return neighbor.is(this) || super.skipRendering(state, neighbor, direction);
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}
}
