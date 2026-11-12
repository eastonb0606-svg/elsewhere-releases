package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Routed soldiers run from the nearest threat (no attacking) until the rout ends, then rejoin their squad. */
public final class RoutGoal extends Goal {
	private final KingdomSoldier mob;
	private int next;

	public RoutGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return mob.isRouted();
	}

	@Override
	public void start() {
		next = 0;
		mob.setTarget(null);
		mob.setSprinting(true);
	}

	@Override
	public void stop() {
		mob.setSprinting(false);
		mob.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (--next > 0 && !mob.getNavigation().isDone()) return;
		next = 20;
		if (!(mob.level() instanceof ServerLevel level)) return;
		Vec3 from = mob.getLastHurtByMob() != null ? mob.getLastHurtByMob().position() : null;
		if (from == null) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(mob.blockPosition()).inflate(16), x -> x != mob && x.isAlive()
				&& dev.elsewhere.kingdom.KingdomTargeting.canAttack(mob.factionId(), null, null, x))) {
				from = e.position();
				break;
			}
		}
		if (from == null) from = mob.position().add(mob.getLookAngle());
		Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 6, from);
		if (away != null && KingdomPerf.tryPath(level)) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
	}
}
