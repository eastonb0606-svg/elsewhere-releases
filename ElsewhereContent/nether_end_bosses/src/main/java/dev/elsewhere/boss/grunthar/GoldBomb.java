package dev.elsewhere.boss.grunthar;

import dev.elsewhere.stub.nether_end_bosses.BossPart;
import dev.elsewhere.stub.nether_end_bosses.Decals;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.Guards;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code elsewhere:gold_bomb}: Grunthar's thrown bomb. It lands, shows a 2 s fuse (sparks, hiss, red circle), then a
 * power-3 blast: 34 raw at the centre, falling off to 0 at 4 blocks. Never breaks blocks.
 */
public class GoldBomb extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(GoldBomb.class, EntityDataSerializers.INT);
	public static final int FUSE_TICKS = 40;
	public static final double RADIUS = 4.0;
	private float damage = 34.0F;
	private boolean landed;

	public GoldBomb(EntityType<? extends GoldBomb> type, Level level) {
		super(type, level);
	}

	public GoldBomb(EntityType<? extends GoldBomb> type, LivingEntity owner, Level level) {
		super(type, level);
		this.setOwner(owner);
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FUSE, -1);
	}

	/** Ticks left on the fuse, or -1 while flying (synced for the renderer's flashing). */
	public int fuse() {
		return this.entityData.get(DATA_FUSE);
	}

	@Override
	protected Item getDefaultItem() {
		return GruntharContent.GOLD_BOMB_ITEM;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof ElsewhereBoss || entity instanceof BossPart || entity.entityTags().contains(Guards.MINION_TAG));
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		land();
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		land();
	}

	private void land() {
		if (landed) return;
		landed = true;
		this.setDeltaMovement(Vec3.ZERO);
		this.setNoGravity(true);
		if (!this.level().isClientSide()) this.entityData.set(DATA_FUSE, FUSE_TICKS);
	}

	@Override
	public void tick() {
		if (!landed) {
			super.tick();
			return;
		}
		this.baseTick();
		if (!(this.level() instanceof ServerLevel level)) return;
		int fuse = fuse() - 1;
		this.entityData.set(DATA_FUSE, fuse);
		if (fuse % 8 == 0) {
			Decals.circle(level, this.position(), RADIUS, Decals.RED);
			this.playSound(SoundEvents.TNT_PRIMED, 1.0F, 1.2F + (FUSE_TICKS - fuse) * 0.02F);
		}
		if (fuse % 2 == 0) { // P13: fuse sparks every 2nd tick, was every tick
			level.sendParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.05, 0.05, 0.05, 0.02);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.6, this.getZ(), 1, 0.05, 0.05, 0.05, 0.01);
		}
		if (fuse <= 0) explode(level);
	}

	private void explode(ServerLevel level) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.WAX_ON, this.getX(), this.getY() + 0.5, this.getZ(), 30, 1.5, 1.0, 1.5, 0.3);
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.9F);
		ElsewhereBoss boss = this.getOwner() instanceof ElsewhereBoss b ? b : null;
		for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(RADIUS))) {
			double d = p.position().add(0, 0.9, 0).distanceTo(this.position());
			if (d > RADIUS) continue;
			float raw = (float) (damage * (1 - d / RADIUS));
			if (raw <= 0.5F) continue;
			if (boss != null) p.hurtServer(level, boss.source(StubFramework.BOSS_BLAST, this), ElsewhereBoss.scaled(level, raw));
			else p.hurtServer(level, this.damageSources().explosion(this, this.getOwner()), raw);
			Vec3 push = p.position().subtract(this.position()).normalize().scale(0.8 * (1 - d / RADIUS));
			p.push(push.x, 0.35, push.z);
			p.syncVelocity = true;
		}
		this.discard();
	}
}
