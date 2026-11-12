package dev.elsewhere.verdigris_sea.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tidewing Ray: a glowing-spotted manta that skims 1-3 blocks above the waves in wide loops and dives at swimmers
 * (and at anyone it is told to attack, e.g. by Galvanox). Never burns in daylight.
 */
public class TidewingRay extends PathfinderMob implements Enemy {
	private Vec3 cruiseCenter;
	private double angle;
	private int diveCooldown = 60;
	private int diveTicks;

	public TidewingRay(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setNoGravity(true);
		cruiseCenter = position();
		angle = level.getRandom().nextDouble() * Math.PI * 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 28).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.FLYING_SPEED, 0.6)
			.add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32);
	}

	@Override
	protected void registerGoals() {}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (tickCount == 1 || cruiseCenter.distanceToSqr(position()) > 64 * 64) cruiseCenter = position();
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || target.distanceToSqr(this) > 40 * 40) {
			target = null;
			setTarget(null);
			Player p = level.getNearestPlayer(this, 24);
			if (p != null && !p.isCreative() && !p.isSpectator() && (p.isInWater() || p.getY() < level.getSeaLevel() + 3)) setTarget(p);
		}
		Vec3 goal;
		if (target != null && diveCooldown <= 0) {
			goal = target.position().add(0, target.getBbHeight() * 0.5, 0);
			diveTicks++;
			if (distanceToSqr(target) < 2.5 * 2.5) {
				doHurtTarget(level, target);
				diveCooldown = 50 + random.nextInt(40);
				diveTicks = 0;
			} else if (diveTicks > 100) {
				diveCooldown = 40;
				diveTicks = 0;
			}
		} else {
			if (diveCooldown > 0) diveCooldown--;
			angle += 0.035;
			double surface = surfaceY(level);
			goal = new Vec3(cruiseCenter.x + Math.cos(angle) * 12, surface + 2 + Math.sin(tickCount * 0.05), cruiseCenter.z + Math.sin(angle) * 12);
		}
		Vec3 d = goal.subtract(position());
		double speed = target != null && diveCooldown <= 0 ? 0.55 : 0.32;
		Vec3 v = getDeltaMovement().scale(0.85).add(d.normalize().scale(speed * 0.15));
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
	public void travel(Vec3 input) {
		move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
	}

	private double surfaceY(ServerLevel level) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPosition());
		return Math.max(top.getY(), level.getSeaLevel());
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target);
		if (target != null) diveCooldown = Math.min(diveCooldown, 20);
	}

	public float flap(float partial) {
		return (float) Math.sin((tickCount + partial) * 0.25);
	}
}
