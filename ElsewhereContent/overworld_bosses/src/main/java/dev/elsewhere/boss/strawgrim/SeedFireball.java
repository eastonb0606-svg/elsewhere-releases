package dev.elsewhere.boss.strawgrim;

import dev.elsewhere.overworld_bosses.OwbItems;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** {@code elsewhere:seed_fireball}: a burning seed from the pumpkin mouth; 5 damage and sets you alight. */
public class SeedFireball extends ThrowableItemProjectile {
	public SeedFireball(EntityType<? extends SeedFireball> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item getDefaultItem() {
		return OwbItems.SEED_FIREBALL;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public boolean isOnFire() {
		return true;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity e = hit.getEntity();
		if (level() instanceof ServerLevel sl && e instanceof LivingEntity le && !(e instanceof StrawgrimEntity) && !(e instanceof StrawgrimHead)) {
			le.igniteForSeconds(4.0F);
			le.hurtServer(sl, damageSources().thrown(this, getOwner()), BossDamage.scaled(sl, 5.0F));
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!level().isClientSide()) discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.tickCount > 100 && !level().isClientSide()) discard();
		if (level() instanceof ServerLevel sl && this.tickCount % 3 == 0) {
			sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}
}
