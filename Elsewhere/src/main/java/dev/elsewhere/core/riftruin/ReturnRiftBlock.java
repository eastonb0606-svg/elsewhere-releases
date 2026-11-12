package dev.elsewhere.core.riftruin;

import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A permanent one-way rift home. Walk into it. The block entity draws the light beam. */
public class ReturnRiftBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

	public ReturnRiftBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new ReturnRiftBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void entityInside(final BlockState state, final Level level, final BlockPos pos, final Entity entity,
		final InsideBlockEffectApplier effectApplier, final boolean isPrecise) {
		if (entity instanceof ServerPlayer player && !player.isSpectator()) {
			ReturnRifts.use(player);
		}
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		int color = ElsewhereDimensions.colorOf(level.dimension());
		for (int i = 0; i < 2; i++) {
			level.addParticle(ModParticles.spark(color), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + random.nextDouble(),
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.04, 0.0);
		}
	}
}
