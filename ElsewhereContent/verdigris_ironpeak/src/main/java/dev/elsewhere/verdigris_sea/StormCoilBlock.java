package dev.elsewhere.verdigris_sea;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Storm Coil: soaks up lightning that lands within 8 blocks and stores it as a redstone charge (0-15)
 * that bleeds off one level every 4 seconds. Crackles while charged.
 */
public class StormCoilBlock extends Block {
	public static final IntegerProperty CHARGE = BlockStateProperties.POWER;
	public static final int RANGE = 8;

	public StormCoilBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CHARGE);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(CHARGE);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return state.getValue(CHARGE);
	}

	/** Called by the lightning hook for every coil near a strike. */
	public static void charge(ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof StormCoilBlock coil)) return;
		level.setBlockAndUpdate(pos, state.setValue(CHARGE, 15));
		level.scheduleTick(pos, coil, 80);
		level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.8F);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int charge = state.getValue(CHARGE);
		if (charge > 0) {
			level.setBlockAndUpdate(pos, state.setValue(CHARGE, charge - 1));
			if (charge - 1 > 0) level.scheduleTick(pos, this, 80);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int charge = state.getValue(CHARGE);
		if (charge == 0 || random.nextInt(16) >= charge) return;
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 0.2 + random.nextDouble() * 0.9,
				pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.4);
		}
	}
}
