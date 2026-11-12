package dev.elsewhere.palehollow.block;

import dev.elsewhere.boss.selenne.SelenneSummon;
import dev.elsewhere.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The single closed eyeblossom at the centre of a Moonring. Picking it (use or break) wakes Selenne. */
public class MoonBloomBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 12.0, 11.0);

	public MoonBloomBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			level.removeBlock(pos, false);
			SelenneSummon.summon(server, pos, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state, final Player player) {
		if (level instanceof ServerLevel server) {
			SelenneSummon.summon(server, pos, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(2) == 0) {
			level.addParticle(ModParticles.MOON_MOTE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.7, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
				0.0, 0.03, 0.0);
		}
	}
}
