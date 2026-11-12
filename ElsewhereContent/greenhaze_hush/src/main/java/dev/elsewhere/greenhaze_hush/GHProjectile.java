package dev.elsewhere.greenhaze_hush;

import dev.elsewhere.greenhaze.GearStats;
import dev.elsewhere.greenhaze.GreenhazeEntities;
import dev.elsewhere.greenhaze.GreenhazeItems;
import dev.elsewhere.greenhaze.Haze;
import dev.elsewhere.hush.HushEntities;
import dev.elsewhere.hush.HushItems;
import dev.elsewhere.hush.HushNoise;
import dev.elsewhere.stub.greenhaze_hush.BossDamage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every thrown thing in this module, rendered as its item: boss projectiles {@code elsewhere:smoke_ring},
 * {@code elsewhere:ember_flick}, {@code elsewhere:spit_seed}, {@code elsewhere:crystal_shard} and the thrown items
 * Cloud Jar and Tuning Fork of Auris. Behaviour is picked by entity type.
 */
public class GHProjectile extends ThrowableItemProjectile {
	/** Raw (Normal difficulty) damage for boss projectiles; scaled by the boss framework. */
	public float damage;
	private int bounces;

	public GHProjectile(EntityType<? extends GHProjectile> type, Level level) {
		super(type, level);
	}

	public GHProjectile(EntityType<? extends GHProjectile> type, Level level, LivingEntity owner, ItemStack stack, float damage) {
		super(type, owner, level, stack);
		this.damage = damage;
	}

	@Override
	protected Item getDefaultItem() {
		EntityType<?> t = getType();
		if (t == GreenhazeEntities.SMOKE_RING) return GreenhazeItems.SMOKE_RING;
		if (t == GreenhazeEntities.EMBER_FLICK) return GreenhazeItems.EMBER;
		if (t == GreenhazeEntities.SPIT_SEED) return GreenhazeItems.SPIT_SEED;
		if (t == GreenhazeEntities.CLOUD_JAR) return GreenhazeItems.CLOUD_JAR;
		if (t == HushEntities.CRYSTAL_SHARD) return HushItems.CRYSTAL_SHARD;
		if (t == HushEntities.TUNING_FORK) return HushItems.TUNING_FORK_OF_AURIS;
		return GreenhazeItems.SMOKE_RING;
	}

	@Override
	protected double getDefaultGravity() {
		EntityType<?> t = getType();
		return t == GreenhazeEntities.SMOKE_RING || t == HushEntities.CRYSTAL_SHARD ? 0.0 : 0.03;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl) {
			if (getType() == GreenhazeEntities.SMOKE_RING && tickCount % 3 == 0)
				sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0);
			if (getType() == GreenhazeEntities.EMBER_FLICK && tickCount % 2 == 0) sl.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
			if (getType() == HushEntities.CRYSTAL_SHARD && tickCount % 3 == 0) sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
			if (tickCount > 200) discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!(level() instanceof ServerLevel sl) || !(hit.getEntity() instanceof LivingEntity target)) return;
		Entity owner = getOwner();
		if (owner != null && target == owner) return;
		EntityType<?> t = getType();
		if (damage > 0) {
			if (owner != null) BossDamage.hit(sl, owner, target, damage);
			else target.hurtServer(sl, damageSources().thrown(this, null), damage);
		}
		if (t == GreenhazeEntities.EMBER_FLICK) target.igniteForSeconds(4);
		if (t == GreenhazeEntities.SMOKE_RING && target instanceof ServerPlayer p) Haze.add(p, 3, false);
		if (t == GreenhazeEntities.SPIT_SEED) target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 2));
	}

	@Override
	protected void onHit(HitResult hit) {
		if (getType() == GreenhazeEntities.SMOKE_RING && hit instanceof BlockHitResult bhr && bounces < 2 && level() instanceof ServerLevel) {
			// Sativa rings bounce
			bounces++;
			Vec3 v = getDeltaMovement();
			switch (bhr.getDirection().getAxis()) {
				case X -> setDeltaMovement(-v.x, v.y, v.z);
				case Y -> setDeltaMovement(v.x, -v.y, v.z);
				case Z -> setDeltaMovement(v.x, v.y, -v.z);
			}
			return;
		}
		super.onHit(hit);
		if (!(level() instanceof ServerLevel sl)) return;
		EntityType<?> t = getType();
		Vec3 at = hit.getLocation();
		if (t == GreenhazeEntities.CLOUD_JAR) {
			double r = GearStats.CLOUD_JAR_RADIUS;
			sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 60, r / 2, 1, r / 2, 0.01);
			sl.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, 0.7F);
			for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(r))) {
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, GearStats.CLOUD_JAR_INVIS_TICKS, 0));
			}
		} else if (t == HushEntities.TUNING_FORK) {
			HushNoise.decoy(sl, at, getOwner());
			sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2F, 1.2F);
			spawnAtLocation(sl, new ItemStack(HushItems.TUNING_FORK_OF_AURIS));
		}
		discard();
	}
}
