package dev.elsewhere.petalwind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A calcite grate that blows a column of wind upward (up to {@link UpdraftVentBlockEntity#RANGE} blocks of open air). */
public class UpdraftVentBlock extends BaseEntityBlock {
	public UpdraftVentBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UpdraftVentBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, PetalwindContentTypes.UPDRAFT_VENT, UpdraftVentBlockEntity::tick);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.SMALL_GUST, pos.getX() + random.nextDouble(), pos.getY() + 1.1 + random.nextDouble() * 6, pos.getZ() + random.nextDouble(),
				0, 0.15, 0);
		}
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0, 0.25, 0);
		}
	}
}
