package dev.elsewhere.glasswaste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Looks like sand. Living things sink slowly through it; sneaking (or jumping) lets you climb back out. */
public class QuicksandBlock extends Block {
	public QuicksandBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext ec && ec.getEntity() instanceof LivingEntity) return Shapes.empty();
		return Shapes.block();
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return Shapes.block();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!(entity instanceof LivingEntity) || entity instanceof FallingBlockEntity) return;
		entity.makeStuckInBlock(state, new Vec3(0.5, 0.12, 0.5));
		if (entity.isShiftKeyDown()) {
			Vec3 v = entity.getDeltaMovement();
			entity.setDeltaMovement(v.x, 0.16, v.z);
		}
		entity.resetFallDistance();
		if (level instanceof ServerLevel server && level.getGameTime() % 8 == 0) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), entity.getX(), pos.getY() + 1, entity.getZ(), 3, 0.3, 0, 0.3, 0.05);
			if (entity.getEyeY() < pos.getY() + 1 && level.getGameTime() % 20 == 0) entity.hurt(level.damageSources().inWall(), 1.0F);
		}
	}
}
