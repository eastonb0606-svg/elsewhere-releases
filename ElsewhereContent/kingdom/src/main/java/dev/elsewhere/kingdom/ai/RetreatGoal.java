package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Low health (below the unit's {@code retreat_health}) and the once-rolled {@code retreat_chance} said so: back away from
 * the target with the shield up for up to 3 s, then rejoin (cooldown 13 s). Officers hold their ground.
 */
public final class RetreatGoal extends Goal {
	private final KingdomSoldier mob;
	private int ticks;
	private Vec3 away;

	public RetreatGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity t = mob.getTarget();
		return t != null && !mob.isOfficer() && !mob.isPassenger() && mob.retreatCooldown <= 0 && mob.isActive() && mob.wantsRetreat()
			&& mob.getHealth() < mob.getMaxHealth() * mob.unit().combat().retreatHealth() && mob.distanceToSqr(t) < 8 * 8;
	}

	@Override
	public boolean canContinueToUse() {
		return ticks > 0 && mob.getTarget() != null && !mob.isRouted();
	}

	@Override
	public void start() {
		ticks = 60;
		mob.retreatCooldown = 260;
		LivingEntity t = mob.getTarget();
		away = t != null ? LandRandomPos.getPosAway(mob, 8, 4, t.position()) : null;
		if (away != null && mob.level() instanceof ServerLevel l && KingdomPerf.tryPath(l)) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks--;
		LivingEntity t = mob.getTarget();
		if (t == null) return;
		mob.getLookControl().setLookAt(t, 30F, 30F);
		mob.raiseShield(2);
		if (mob.getNavigation().isDone()) mob.getMoveControl().strafe(-0.6F, 0F);
	}
}
