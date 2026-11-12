package dev.elsewhere.boss.gulpmother;

import dev.elsewhere.overworld_bosses.OwbItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** {@code elsewhere:frogspawn_glob}: spat by Gulpmother in phase 2; lands as a frogspawn clutch on the water. */
public class FrogspawnGlob extends ThrowableItemProjectile {
	public FrogspawnGlob(EntityType<? extends FrogspawnGlob> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item getDefaultItem() {
		return OwbItems.FROGSPAWN_GLOB;
	}

	/** Lob toward a block position in a fixed-time arc (about 1 s). */
	public void launchTo(Vec3 target) {
		Vec3 d = target.subtract(position());
		int t = 20;
		double g = getGravity();
		setDeltaMovement(d.x / t, (d.y + 0.5 * g * t * t) / t, d.z / t);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.05;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		// Harmless on contact; it is the clutch that matters.
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel sl && !isRemoved()) {
			BlockPos at = hit instanceof BlockHitResult bh ? bh.getBlockPos().relative(bh.getDirection()) : blockPosition();
			GulpmotherEntity.placeFrogspawn(sl, at);
			sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 12, 0.3, 0.2, 0.3, 0.05);
			discard();
		}
	}
}
