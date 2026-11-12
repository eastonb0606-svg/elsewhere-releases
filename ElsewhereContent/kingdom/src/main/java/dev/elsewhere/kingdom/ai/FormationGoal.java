package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Squad members walk to their formation slot with plain move control (no A* while within 16 blocks of the slot);
 * only when farther, or stuck, they ask the global path budget for a path (at most every 40 ticks).
 * At the slot they face the squad heading.
 */
public final class FormationGoal extends Goal {
	private final KingdomSoldier mob;
	private int nextPath, stuck;
	private Vec3 lastPos = Vec3.ZERO;
	/** Path searches this member started (tests check members stay near zero). */
	public static long memberPaths;

	/** 3.0 swarm-as-one follower steering (armies key {@code swarm}); off = the code below. */
	private final dev.elsewhere.kingdom.armies.swarm.SlotSteer steer;

	public FormationGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.steer = new dev.elsewhere.kingdom.armies.swarm.SlotSteer(mob);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return KingdomConfig.useFormations && mob.formationTarget != null && !mob.isRouted() && mob.squadId() != null && !mob.isSquadLeader()
			&& (mob.isActive() || (mob.tickCount + mob.phase) % 4 == 0);
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
	public void stop() {
		mob.getNavigation().stop();
		steer.reset();
	}

	@Override
	public void tick() {
		if (dev.elsewhere.kingdom.armies.swarm.Swarm.on()) {
			steer.tick();
			return;
		}
		Vec3 slot = mob.formationTarget;
		if (slot == null) return;
		double dx = slot.x - mob.getX(), dz = slot.z - mob.getZ();
		double d2 = dx * dx + dz * dz;
		if (d2 < 0.6 * 0.6) {
			mob.getNavigation().stop();
			mob.setYRot(mob.formationYaw);
			mob.yBodyRot = mob.formationYaw;
			mob.yHeadRot = mob.formationYaw;
			stuck = 0;
			return;
		}
		double speed = d2 > 16 ? 1.25 : (d2 > 4 ? 1.0 : 0.7);
		if (d2 < 16 * 16 && stuck < 30) {
			if (!mob.getNavigation().isDone() && d2 < 8 * 8) mob.getNavigation().stop();
			if (mob.getNavigation().isDone()) mob.getMoveControl().setWantedPosition(slot.x, slot.y, slot.z, speed);
			if (mob.horizontalCollision && mob.onGround()) mob.getJumpControl().jump();
			if (mob.tickCount % 10 == 0) {
				stuck = mob.position().distanceToSqr(lastPos) < 0.01 ? stuck + 10 : 0;
				lastPos = mob.position();
			}
		} else if (mob.tickCount >= nextPath && mob.level() instanceof ServerLevel level && KingdomPerf.tryPath(level)) {
			mob.getNavigation().moveTo(slot.x, slot.y, slot.z, speed);
			memberPaths++;
			nextPath = mob.tickCount + 40;
			stuck = 0;
		}
	}
}
