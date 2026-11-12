package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitType;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Mounted swordsman (role {@code cavalry}): charge the target at a gallop, slash when in reach (sprint-knockback hit),
 * ride on past it for ~10 blocks, turn, and charge again. The rider steers the horse through its navigation (vanilla:
 * a mob rider controls its mob vehicle).
 */
public final class MountedChargeGoal extends Goal {
	private final KingdomSoldier mob;
	private boolean passing;
	private Vec3 passPoint;
	private int phaseTicks, repath;

	public MountedChargeGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity t = mob.getTarget();
		return mob.role() == UnitType.Role.CAVALRY && mob.isPassenger() && mob.getVehicle() instanceof Mob && t != null && t.isAlive() && !mob.isRouted() && mob.isActive();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		passing = false;
		repath = 0;
		mob.setAggressive(true);
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity t = mob.getTarget();
		if (t == null || !(mob.level() instanceof ServerLevel level)) return;
		mob.getLookControl().setLookAt(t, 30F, 30F);
		phaseTicks++;
		if (passing) {
			if (passPoint == null || phaseTicks > 50 || mob.position().distanceToSqr(passPoint) < 4) {
				passing = false;
				phaseTicks = 0;
				repath = 0;
			} else if (--repath <= 0) {
				repath = 10;
				if (KingdomPerf.tryPath(level)) mob.getNavigation().moveTo(passPoint.x, passPoint.y, passPoint.z, 1.4);
			}
			return;
		}
		if (mob.isWithinMeleeAttackRange(t) && mob.attackReady()) {
			mob.setSprinting(true);
			mob.strike(level, t);
			Vec3 dir = t.position().subtract(mob.position()).multiply(1, 0, 1);
			if (dir.lengthSqr() < 1.0E-4) dir = mob.getLookAngle().multiply(1, 0, 1);
			passPoint = t.position().add(dir.normalize().scale(10));
			passing = true;
			phaseTicks = 0;
			repath = 0;
			return;
		}
		if (--repath <= 0 || mob.getNavigation().isDone()) {
			repath = 10;
			if (KingdomPerf.tryPath(level)) mob.getNavigation().moveTo(t, 1.5);
		}
		if (phaseTicks > 160) { // circled too long without reaching: break off and come round again
			passPoint = mob.position().add(mob.getLookAngle().multiply(1, 0, 1).normalize().scale(-8));
			passing = true;
			phaseTicks = 0;
		}
	}
}
