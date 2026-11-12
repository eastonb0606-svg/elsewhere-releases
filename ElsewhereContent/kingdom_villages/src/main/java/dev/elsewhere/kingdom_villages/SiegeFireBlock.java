package dev.elsewhere.kingdom_villages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code elsewhere:village_siege_fire}: controlled siege fire. Looks like fire, but it is NOT a fire block: it never
 * random-ticks, never spreads, never ignites or burns neighbours and never hurts villagers or golems. It only sets players
 * standing in it alight, and goes out on its own after 30-60 s (scheduled tick).
 */
public class SiegeFireBlock extends Block {
	public static final int MIN_LIFE = 600, MAX_LIFE = 1200;
	/** Tests only: shorter life so the burn-out can be observed. */
	public static int testLife = 0;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

	public SiegeFireBlock(BlockBehaviour.Properties p) {
		super(p);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return Shapes.empty();
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return false;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (!old.is(this) && !level.isClientSide()) level.scheduleTick(pos, this, testLife > 0 ? testLife : MIN_LIFE + level.getRandom().nextInt(MAX_LIFE - MIN_LIFE));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return !level.getBlockState(pos.below()).isAir();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos npos, BlockState nstate, RandomSource random) {
		return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean b) {
		// Players only: villagers are captured, not killed, and golems fight soldiers, not flames.
		if (entity instanceof Player p && !p.isCreative() && !p.isSpectator() && p.getRemainingFireTicks() < 40) p.igniteForSeconds(3.0F);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(24) == 0) level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
		for (int i = 0; i < 2; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.6 + random.nextDouble() * 0.5, pos.getZ() + random.nextDouble(), 0, 0.07, 0);
		if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 0, 0.06, 0);
	}
}
