package dev.elsewhere.gear3.s3.mothlight;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.summoner.SummonerPerks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Mothlight Swarm: three pale moths that share one minion slot (one summoner minion; see {@link Mothlight}).
 * <ul>
 * <li>Health is the moths' pooled 3 x 6 HP, with moth rules on top: one blow fells at most the moth in front ({@link #hurtServer}),
 * healing never raises a fallen moth ({@link #setHealth} caps at the living moths' share), and the synced moth count drives
 * both the flurry damage and the client model (fallen moths vanish with a puff and a snuffed-candle sound).</li>
 * <li>A flurry every 0.75 s: each living moth strikes for 1.5 (x minion_damage, Summoner Calling, Well Fed, Ribbon), dealt as one
 * hit so the target's hurt cooldown cannot swallow the second and third moth; the hit dusts a non-boss target (Mothdust 3 s).</li>
 * <li>Flight: hovers round the owner's head like moths round a lamp (summoner flyer AI) and, in a fight, flutters round the
 * target's chest in a small circle instead of the Paper Kite's ranged hover.</li>
 * </ul>
 * Client: two entity events (flurry, moth falls) drive the lunge animation and the puff; nothing is sent per tick.
 */
public class MothlightSwarm extends MinionEntity {
	private static final EntityDataAccessor<Integer> DATA_MOTHS = SynchedEntityData.defineId(MothlightSwarm.class, EntityDataSerializers.INT);
	/** Entity events (not used by LivingEntity / Mob / Entity: those use 1-4, 46-55, 60, 65, 67, 68). */
	public static final byte EVENT_FLURRY = 62, EVENT_MOTH_FALLS = 63;
	/** Strike reach (block centre distance from the swarm's feet; the base adds half the target's width). */
	public static final double REACH = 2.0;
	public static final double FLY_SPEED = 0.7;
	/** Radius (blocks) of the little circle the swarm flutters round its target. */
	public static final double STRIKE_CIRCLE = 0.6;

	/** Client only: ticks of the last flurry / fallen moth (animation clocks for the renderer). */
	public int flurryTick = -1000, fallTick = -1000;

	public MothlightSwarm(EntityType<? extends MothlightSwarm> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, Mothlight.MOTHS * Mothlight.MOTH_HP)
			.add(Attributes.ARMOR, 0.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, FLY_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_MOTHS, Mothlight.MOTHS);
	}

	/** Living moths (0..3), synced to the client. */
	public int moths() {
		return this.entityData.get(DATA_MOTHS);
	}

	/** One moth's share of the max health (6 HP unless a perk raised the swarm's max health). */
	public float mothHp() {
		float m = getMaxHealth() / Mothlight.MOTHS;
		return m > 0F ? m : (float) Mothlight.MOTH_HP;
	}

	// ---------------------------------------------------------------- moths: health

	/** A fallen moth never comes back: health is capped at the living moths' share; a drop below a moth's share fells it. */
	@Override
	public void setHealth(float health) {
		int alive = moths();
		float per = mothHp();
		if (health > alive * per) health = alive * per;
		super.setHealth(health);
		if (level().isClientSide()) return;
		int now = health <= 0F ? 0 : Math.min(alive, Mth.ceil(health / per - 1.0E-4F));
		if (now < alive) {
			this.entityData.set(DATA_MOTHS, now);
			if (now > 0) mothFell(alive - now);
		}
	}

	/** One blow fells one moth at most (the others scatter): damage is capped at the HP left on the moth in front. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		int alive = moths();
		if (alive > 1 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			float front = getHealth() - (alive - 1) * mothHp();
			if (front > 0F && damage > front) damage = front;
		}
		return super.hurtServer(level, source, damage);
	}

	private void mothFell(int count) {
		if (!(level() instanceof ServerLevel sl)) return;
		sl.broadcastEntityEvent(this, EVENT_MOTH_FALLS);
		sl.playSound(null, getX(), getY() + 0.3, getZ(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.NEUTRAL, 0.9F, 1.5F + 0.1F * count);
	}

	// ---------------------------------------------------------------- minion API

	@Override
	public boolean isFlyingMinion() {
		return true;
	}

	/** One moth's strike; a flurry is this times the living moths. */
	@Override
	public double baseDamage() {
		return Mothlight.MOTH_DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return Mothlight.ATTACK_TICKS;
	}

	@Override
	public double attackReach() {
		return REACH;
	}

	@Override
	protected double flyingSpeed() {
		return FLY_SPEED;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		flurry(level, owner, target);
	}

	/**
	 * One flurry: every living moth strikes for {@link Mothlight#MOTH_DAMAGE}, dealt as one minion hit of moths x 1.5 (the
	 * target's hurt cooldown would otherwise swallow the second and third moth). A landed hit dusts a non-boss target.
	 */
	public boolean flurry(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		int n = moths();
		if (n <= 0 || !Mothlight.enabled) return false;
		level.broadcastEntityEvent(this, EVENT_FLURRY);
		level.playSound(null, getX(), getY() + 0.3, getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.35F, 1.8F + this.random.nextFloat() * 0.3F);
		boolean hit = hitTarget(level, owner, target, baseDamage() * n);
		if (hit) Mothlight.dust(target, owner);
		return hit;
	}

	/** Where the swarm flutters while fighting: a small circle at the target's chest (capped at 1.2 blocks up for tall mobs). */
	public static Vec3 strikePoint(LivingEntity target, double angle) {
		double up = Math.min(target.getBbHeight() * 0.6, 1.2);
		return target.position().add(Math.cos(angle) * STRIKE_CIRCLE, up, Math.sin(angle) * STRIKE_CIRCLE);
	}

	@Override
	protected void moveTowardTarget(ServerPlayer owner, LivingEntity target) {
		double speed = SummonerPerks.speedMultiplier(owner);
		double angle = (this.tickCount + this.getId() * 7) * 0.25;
		flyTo(strikePoint(target, angle), flyingSpeed() * speed);
		faceToward(target.position());
	}

	// ---------------------------------------------------------------- sounds (moths: wings, not voices)

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PHANTOM_FLAP;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CANDLE_EXTINGUISH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.5F;
	}

	// ---------------------------------------------------------------- client events

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_FLURRY) {
			flurryTick = this.tickCount;
			Vec3 f = Vec3.directionFromRotation(0F, this.getYRot()).scale(0.4);
			for (int i = 0; i < 3; i++)
				level().addParticle(ParticleTypes.WHITE_ASH, getX() + f.x + (random.nextDouble() - 0.5) * 0.4, getY() + 0.3 + (random.nextDouble() - 0.5) * 0.3,
					getZ() + f.z + (random.nextDouble() - 0.5) * 0.4, 0.0, -0.02, 0.0);
			return;
		}
		if (id == EVENT_MOTH_FALLS) {
			fallTick = this.tickCount;
			for (int i = 0; i < 10; i++)
				level().addParticle(ParticleTypes.WHITE_ASH, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.2 + random.nextDouble() * 0.4,
					getZ() + (random.nextDouble() - 0.5) * 0.6, 0.0, -0.04, 0.0);
			for (int i = 0; i < 3; i++)
				level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.3, getZ() + (random.nextDouble() - 0.5) * 0.4,
					0.0, -0.03, 0.0);
			return;
		}
		super.handleEntityEvent(id);
	}
}
