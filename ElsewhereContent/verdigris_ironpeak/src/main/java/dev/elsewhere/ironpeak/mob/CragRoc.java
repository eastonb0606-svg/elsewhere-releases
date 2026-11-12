package dev.elsewhere.ironpeak.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Crag Roc: a giant red-gold eagle. Circles high over the slopes, swoops, snatches a player as its passenger, climbs
 * 20 blocks and lets go. Hitting it while carried makes it drop you early.
 */
public class CragRoc extends PathfinderMob implements Enemy {
	private enum State { CIRCLE, SWOOP, CARRY }

	private State state = State.CIRCLE;
	private Vec3 anchor;
	private double angle;
	private int stateTicks, cooldown = 100;
	private double carryStartY;

	public CragRoc(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setNoGravity(true);
		anchor = position();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.FOLLOW_RANGE, 48)
			.add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected void registerGoals() {}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public void travel(Vec3 input) {
		move(MoverType.SELF, getDeltaMovement());
	}

	public boolean isCarrying() {
		return state == State.CARRY && isVehicle();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty() && passenger instanceof Player;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		stateTicks++;
		if (tickCount == 1 || anchor.distanceToSqr(position()) > 96 * 96) anchor = position();
		Vec3 goal;
		double speed;
		switch (state) {
			case SWOOP -> {
				LivingEntity t = getTarget();
				if (t == null || !t.isAlive() || stateTicks > 120) {
					state = State.CIRCLE;
					cooldown = 100;
					return;
				}
				goal = t.position().add(0, 1.0, 0);
				speed = 0.7;
				if (distanceToSqr(t) < 2.2 * 2.2) {
					doHurtTarget(level, t);
					if (t instanceof Player p && !p.isPassenger() && !p.isShiftKeyDown()) {
						p.startRiding(this, true, true);
						carryStartY = getY();
						level.playSound(null, blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.2F, 0.6F);
					}
					state = State.CARRY;
					stateTicks = 0;
				}
			}
			case CARRY -> {
				goal = position().add(Math.cos(angle) * 4, 6, Math.sin(angle) * 4);
				speed = 0.35;
				if (!isVehicle() || getY() > carryStartY + 20 || stateTicks > 100) {
					ejectPassengers();
					state = State.CIRCLE;
					cooldown = 160;
				}
			}
			default -> {
				if (cooldown > 0) cooldown--;
				angle += 0.025;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) anchor.x, (int) anchor.z);
				goal = new Vec3(anchor.x + Math.cos(angle) * 18, Math.max(ground, anchor.y - 10) + 18, anchor.z + Math.sin(angle) * 18);
				speed = 0.4;
				Player p = level.getNearestPlayer(this, 40);
				if (cooldown <= 0 && p != null && !p.isCreative() && !p.isSpectator() && hasLineOfSight(p)) {
					setTarget(p);
					state = State.SWOOP;
					stateTicks = 0;
					level.playSound(null, blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0F, 0.6F);
				}
			}
		}
		Vec3 d = goal.subtract(position());
		Vec3 v = getDeltaMovement().scale(0.88).add(d.normalize().scale(speed * 0.14));
		if (v.length() > speed) v = v.normalize().scale(speed);
		setDeltaMovement(v);
		if (v.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = (float) (Math.atan2(v.z, v.x) * 180 / Math.PI) - 90;
			setYRot(yaw);
			yBodyRot = yaw;
			yHeadRot = yaw;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && isVehicle() && source.getEntity() instanceof Player) {
			ejectPassengers();
			state = State.CIRCLE;
			cooldown = 120;
		}
		return hurt;
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		return position().add(0, -passenger.getBbHeight() + 0.2, 0);
	}
}
