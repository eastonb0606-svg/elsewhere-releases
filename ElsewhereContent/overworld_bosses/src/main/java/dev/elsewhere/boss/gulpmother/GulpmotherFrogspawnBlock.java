package dev.elsewhere.boss.gulpmother;

import dev.elsewhere.overworld_bosses.OwbEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code elsewhere:gulpmother_frogspawn}: a clutch on the water that hatches two Mire Biters after 8 s unless
 * broken (one hit). Belongs to the nearest Gulpmother.
 */
public class GulpmotherFrogspawnBlock extends Block {
	public static final int HATCH_TICKS = 160;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1.5, 16);

	public GulpmotherFrogspawnBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) level.scheduleTick(pos, this, HATCH_TICKS);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		hatch(level, pos);
	}

	public static void hatch(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.HOSTILE, 1.5F, 0.8F);
		level.sendParticles(ParticleTypes.ITEM_SLIME, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 16, 0.4, 0.1, 0.4, 0.05);
		List<GulpmotherEntity> mothers = level.getEntitiesOfClass(GulpmotherEntity.class, new AABB(pos).inflate(32), e -> e.isAlive() && !e.isDormant());
		for (int i = 0; i < 2; i++) {
			MireBiter biter = OwbEntities.MIRE_BITER.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (biter == null) continue;
			biter.setPos(pos.getX() + 0.5 + (i - 0.5) * 0.6, pos.getY() + 0.2, pos.getZ() + 0.5);
			biter.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
			if (!mothers.isEmpty()) mothers.get(0).addMinion(level, biter);
			else level.addFreshEntity(biter);
		}
	}
}
