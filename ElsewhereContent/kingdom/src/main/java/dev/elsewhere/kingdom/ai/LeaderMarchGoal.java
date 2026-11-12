package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The squad leader marches to the squad objective at a pace the formation can keep (0.8), repathing every
 * {@code perf.leaderRepathMin..Max} ticks through the global path budget. Combat goals (higher priority) interrupt it.
 */
public final class LeaderMarchGoal extends Goal {
	private final KingdomSoldier mob;
	private int repath;
	public static long leaderPaths;

	public LeaderMarchGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private BlockPos objective() {
		Squad sq = mob.squad();
		return sq != null && mob.isSquadLeader() ? sq.objective() : null;
	}

	@Override
	public boolean canUse() {
		BlockPos o = objective();
		return o != null && !mob.isRouted() && mob.getTarget() == null && o.distToCenterSqr(mob.position()) > 2.5 * 2.5;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		repath = 0;
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
		BlockPos o = objective();
		if (o == null || !(mob.level() instanceof ServerLevel level)) return;
		if (--repath <= 0) {
			if (dev.elsewhere.kingdom.armies.swarm.SwarmPaths.keepMarch(mob, o)) {
				// 3.0 swarm: still on a live path into this goal cell; keep walking it (swarm off: vanilla moveTo keeps a path to the same target)
				repath = KingdomConfig.leaderRepathMin + mob.getRandom().nextInt(Math.max(1, KingdomConfig.leaderRepathMax - KingdomConfig.leaderRepathMin + 1));
			} else if (dev.elsewhere.kingdom.armies.swarm.SwarmPaths.joinMarch(mob, o, 0.8)) {
				// 3.0 swarm: another squad's (or our own) fresh path from this section into this goal cell; no search
				repath = KingdomConfig.leaderRepathMin + mob.getRandom().nextInt(Math.max(1, KingdomConfig.leaderRepathMax - KingdomConfig.leaderRepathMin + 1));
			} else if (KingdomPerf.tryPath(level)) {
				mob.getNavigation().moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0.8);
				dev.elsewhere.kingdom.armies.swarm.SwarmPaths.offerMarch(mob, o);
				leaderPaths++;
				repath = KingdomConfig.leaderRepathMin + mob.getRandom().nextInt(Math.max(1, KingdomConfig.leaderRepathMax - KingdomConfig.leaderRepathMin + 1));
			} else {
				repath = 1;
			}
		}
	}
}
