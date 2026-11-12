package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/**
 * Ground navigation for kingdom soldiers that throttles the vanilla bow and crossbow goals, which call
 * {@code moveTo(target)} every tick they are out of range. While the old path is still running and the target
 * has moved less than 1.5 blocks (same target, path under 20 ticks old) the path is kept. Untouched vanilla: an archer
 * in range with a clear shot (shoots and strafes), and any archer whose path ran out (it repaths at once, so nothing
 * idles at the end of a stale path); no path-search budget is used, so nothing is ever denied. Kill switch: armies config {@code ranged_repath}.
 */
public final class RangedNav extends GroundPathNavigation {
	public static boolean throttle;
	/**
	 * Approach repaths made and kept (the calls the throttle decides on), and calls passed to vanilla untouched because the soldier was in
	 * range with a clear shot ({@code pathsInRange}: never throttled by design, so they are counted apart from the approach calls).
	 */
	public static long pathsMade, pathsSkipped, pathsInRange;
	static final double MOVED = 1.5;
	static final int MAX_AGE = 20;
	private int pathTick = -100, pathTarget = -1;
	private double tx = Double.NaN, ty, tz;

	public RangedNav(Mob mob, Level level) {
		super(mob, level);
	}

	/** Attack range the goals are built with in KingdomSoldier (bow 14, crossbow 8). */
	private static double range(UnitType.Role r) {
		return r == UnitType.Role.ARCHER ? 14.0 : r == UnitType.Role.CROSSBOW ? 8.0 : -1.0;
	}

	@Override
	public boolean moveTo(Entity target, double speed) {
		if (!throttle || !(mob instanceof KingdomSoldier s)) return super.moveTo(target, speed);
		double range = range(s.role());
		if (range < 0) return super.moveTo(target, speed);
		// in range with a clear shot: untouched vanilla (shoot, strafe); everything else is an approach or a re-acquire
		boolean shooting = mob.distanceToSqr(target) <= range * range && mob.getSensing().hasLineOfSight(target);
		boolean moved = Double.isNaN(tx) || target.getId() != pathTarget || target.distanceToSqr(tx, ty, tz) > MOVED * MOVED
			|| mob.tickCount - pathTick >= MAX_AGE;
		if (!shooting && !moved && !isDone()) {
			pathsSkipped++;
			setSpeedModifier(speed);
			return true;
		}
		boolean ok = super.moveTo(target, speed);
		pathTick = mob.tickCount;
		pathTarget = target.getId();
		tx = target.getX();
		ty = target.getY();
		tz = target.getZ();
		if (shooting) pathsInRange++;
		else pathsMade++;
		return ok;
	}
}
