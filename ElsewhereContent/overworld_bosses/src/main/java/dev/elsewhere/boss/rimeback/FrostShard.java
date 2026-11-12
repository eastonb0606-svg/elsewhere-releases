package dev.elsewhere.boss.rimeback;

import dev.elsewhere.overworld_bosses.OwbItems;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** {@code elsewhere:frost_shard}: Frost Breath and Revenant shards; small damage plus freeze ticks. */
public class FrostShard extends ThrowableItemProjectile {
	public FrostShard(EntityType<? extends FrostShard> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item getDefaultItem() {
		return OwbItems.FROST_SHARD;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity e = hit.getEntity();
		if (level() instanceof ServerLevel sl && e instanceof LivingEntity le && !(e instanceof RimebackEntity) && !(e instanceof FrostboundRevenant)) {
			le.hurtServer(sl, damageSources().thrown(this, getOwner()), BossDamage.scaled(sl, 2.0F));
			if (le.canFreeze()) le.setTicksFrozen(Math.min(le.getTicksFrozen() + 60, 300));
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.05);
			discard();
		}
	}
}
