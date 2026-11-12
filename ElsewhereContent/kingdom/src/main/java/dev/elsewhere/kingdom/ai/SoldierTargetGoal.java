package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * Target selection, staggered: every 10 ticks on the soldier's phase. Squad members pick the nearest enemy from the
 * squad's shared scan; free soldiers do their own scan every 20 ticks. Retaliation is immediate (see
 * {@link KingdomSoldier#setLastHurtByMob}).
 */
public final class SoldierTargetGoal extends Goal {
	private final KingdomSoldier mob;
	private LivingEntity pick;

	public SoldierTargetGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (mob.isRouted() || !mob.isActive() || mob.isResting()) return false;
		if ((mob.tickCount + mob.phase) % 10 != 0) return false;
		if (dev.elsewhere.kingdom.armies.swarm.TargetAssigner.managed(mob)) {
			// 3.0 swarm: the squad picks (one pass per squad, shared sight, at most 3 melee per enemy); R0 soldiers fall through to their own scan
			LivingEntity a = dev.elsewhere.kingdom.armies.swarm.TargetAssigner.assigned(mob);
			LivingEntity now = mob.getTarget();
			if (a == null) {
				pick = null;
				return now != null && !now.isAlive(); // clear a dead target; else keep a live one or hold formation
			}
			pick = a;
			return a != now;
		}
		LivingEntity cur = mob.getTarget();
		if (cur != null && cur.isAlive() && KingdomTargeting.canAttack(mob, cur)) return false;
		double range = Math.min(24.0, mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
		Squad sq = mob.squad();
		List<LivingEntity> list;
		if (sq != null) list = sq.enemies();
		else {
			if ((mob.tickCount + mob.phase) % 20 != 0) return false;
			list = mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(mob.position(), mob.position()).inflate(range, 8, range),
				e -> KingdomTargeting.canAttack(mob, e));
		}
		pick = null;
		double best = range * range;
		for (LivingEntity e : list) {
			if (!e.isAlive()) continue;
			double d = e.distanceToSqr(mob);
			if (d < best && KingdomTargeting.canAttack(mob, e) && mob.getSensing().hasLineOfSight(e)) {
				best = d;
				pick = e;
			}
		}
		return pick != null;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		mob.setTarget(pick);
		pick = null;
	}
}
