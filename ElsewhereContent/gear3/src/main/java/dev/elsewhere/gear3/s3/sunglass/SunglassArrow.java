package dev.elsewhere.gear3.s3.sunglass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Sunglass Arrow in flight (see {@link SunglassArrows} for the rules). The glass pass-through and the undead bonus are applied
 * by {@code dev.elsewhere.gear3.mixin.sunglass.SunglassArrowMixin} inside the vanilla arrow code (its flight ray, its inside-a-block check and its damage call);
 * the fire is {@link #doPostHurtEffects}. Server extras: one soft amethyst chime when the arrow really crosses glass in a tick.
 * Client extra: a sparse small-flame trail while it flies (client-side particles, culled by the particle engine).
 */
public class SunglassArrow extends AbstractArrow {
	/** The sunglass effects are on for this arrow (the kill switch where it was made); synced so the client predicts the same flight. */
	private static final EntityDataAccessor<Boolean> SUNLIT = SynchedEntityData.defineId(SunglassArrow.class, EntityDataSerializers.BOOLEAN);
	/** Client trail: one small flame every this many ticks of flight. */
	public static final int TRAIL_EVERY = 3;

	/** Where this tick's flight ray first crossed glass, or null (set by {@link GlassPassClip}, read once after the move). */
	@Nullable Vec3 glassAt;

	public SunglassArrow(EntityType<? extends SunglassArrow> type, Level level) {
		super(type, level);
	}

	public SunglassArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
		super(SunglassArrows.TYPE, owner, level, pickup, weapon);
	}

	public SunglassArrow(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
		super(SunglassArrows.TYPE, x, y, z, level, pickup, weapon);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SUNLIT, SunglassArrows.ENABLED);
	}

	/** True while this arrow's sunglass effects are on. */
	public boolean sunlit() {
		return entityData.get(SUNLIT);
	}

	public void setSunlit(boolean on) {
		entityData.set(SUNLIT, on);
	}

	/** True if this arrow flies through {@code state} (glass, while sunlit). */
	public boolean passes(BlockState state) {
		return sunlit() && state.is(SunglassArrows.PASSES);
	}

	/** Stuck in a block (vanilla "in ground"). */
	public boolean stuck() {
		return isInGround();
	}

	@Override
	public void tick() {
		Vec3 from = position();
		glassAt = null;
		super.tick();
		if (!sunlit()) return;
		Level level = level();
		if (!level.isClientSide()) {
			Vec3 crossed = glassAt;
			glassAt = null;
			// only if the arrow really got past the glass this tick (an entity in front of the pane stops it first)
			if (crossed != null && from.distanceToSqr(position()) + 1.0E-4 >= from.distanceToSqr(crossed))
				level.playSound(null, crossed.x, crossed.y, crossed.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6F, 1.5F + random.nextFloat() * 0.3F);
		} else if (!isRemoved() && !isInGround() && tickCount % TRAIL_EVERY == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
	}

	/** GEAR.md "fire 3 s": a hit that dealt damage sets the target alight (fire-immune mobs don't burn, as with Flame). */
	@Override
	protected void doPostHurtEffects(LivingEntity target) {
		super.doPostHurtEffects(target);
		if (sunlit()) target.igniteForSeconds(SunglassArrows.FIRE_SECONDS);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return SunglassArrows.stack();
	}

	/**
	 * The arrow's per-tick flight ray (same block/fluid mode and entity as vanilla's): blocks the arrow {@link #passes} have no
	 * shape, so the ray runs on to the next solid block. The first glass shape the ray really crosses is remembered for the chime.
	 */
	public static final class GlassPassClip extends ClipContext {
		private final SunglassArrow arrow;

		public GlassPassClip(ClipContext base, SunglassArrow arrow) {
			super(base.getFrom(), base.getTo(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow);
			this.arrow = arrow;
		}

		@Override
		public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
			VoxelShape shape = super.getBlockShape(state, level, pos);
			if (shape.isEmpty() || !arrow.passes(state)) return shape;
			if (arrow.glassAt == null) {
				BlockHitResult crossed = shape.clip(getFrom(), getTo(), pos);
				if (crossed != null) arrow.glassAt = crossed.getLocation();
			}
			return Shapes.empty();
		}
	}
}
