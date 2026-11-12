package dev.elsewhere.kingdom.tactics;

import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomTargeting;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Executes a soldier's {@link Order} (priority 3: above melee, below Rout and Retreat). It moves with plain move control inside 12 blocks and paths farther away at most once
 * per 10 ticks (through the global path budget); it never scans. {@code FOCUS} sets a target only if {@link KingdomTargeting#canAttack} still allows it at execution time
 * (FIELD-TACTICS 1.9.8).
 */
public final class OrderGoal extends Goal {
	private final KingdomSoldier mob;
	private long lastPath;

	public OrderGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	private Order order() {
		return mob.tacticsOrder;
	}

	@Override
	public boolean canUse() {
		Order o = order();
		if (o == null || mob.isRouted() || !mob.isActive() || mob.level().isClientSide()) return false;
		long now = mob.level().getGameTime();
		if (now >= o.expiresTick()) {
			mob.tacticsOrder = null;
			return false;
		}
		return o.ready(now);
	}

	@Override
	public boolean canContinueToUse() {
		Order o = order();
		return o != null && !mob.isRouted() && o.live(mob.level().getGameTime());
	}

	@Override
	public boolean isInterruptable() {
		return true;
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
	}

	private void moveTo(Vec3 p, double speed) {
		double d2 = mob.distanceToSqr(p);
		if (d2 < 1.5 * 1.5) {
			mob.getNavigation().stop();
			return;
		}
		if (d2 <= 12 * 12) {
			mob.getMoveControl().setWantedPosition(p.x, p.y, p.z, speed);
			return;
		}
		long now = mob.level().getGameTime();
		if (now - lastPath >= 10 && mob.level() instanceof ServerLevel sl && KingdomPerf.tryPath(sl)) {
			lastPath = now;
			mob.getNavigation().moveTo(p.x, p.y, p.z, speed);
		}
	}

	@Override
	public void tick() {
		Order o = order();
		if (o == null) return;
		ServerLevel level = (ServerLevel) mob.level();
		switch (o.kind()) {
			case HOLD -> {
				mob.getNavigation().stop();
				if (o.pos() != null) mob.getLookControl().setLookAt(o.pos().x, o.pos().y, o.pos().z);
			}
			case SHIELD_UP -> {
				mob.raiseShield(10);
				if (o.pos() != null) moveTo(o.pos(), 0.9);
			}
			case MOVE_TO_SLOT, ADVANCE, PICKET, FLANK, LURE, RALLY, GUARD_ALLY -> {
				if (o.pos() != null) moveTo(o.pos(), 1.0);
			}
			case FALL_BACK, WITHDRAW -> {
				if (o.pos() != null) moveTo(o.pos(), 1.1);
			}
			case KEEP_RANGE, SKIRMISH -> {
				if (o.pos() != null) {
					double stand = o.kind() == Types.OrderKind.SKIRMISH ? 12 : TacticsRuntime.standoff(mob);
					Vec3 away = mob.position().subtract(o.pos());
					away = new Vec3(away.x, 0, away.z);
					double d = away.length();
					if (d < stand - 1 && d > 1e-3) {
						Vec3 to = mob.position().add(away.normalize().scale(Math.min(6, stand - d + 2)));
						mob.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
					} else mob.getNavigation().stop();
					mob.getLookControl().setLookAt(o.pos().x, o.pos().y, o.pos().z);
				}
				focus(level, o);
			}
			case FOCUS -> {
				focus(level, o);
			}
			case CHARGE -> {
				focus(level, o);
				if (o.pos() != null) moveTo(o.pos(), 1.2);
			}
		}
	}

	private void focus(ServerLevel level, Order o) {
		if (o.target() == null) return;
		if (level.getEntity(o.target()) instanceof LivingEntity t && t.isAlive() && KingdomTargeting.canAttack(mob, t) && mob.getTarget() != t) mob.setTarget(t);
	}
}
