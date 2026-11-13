package dev.elsewhere.kingdom.armies.swarm;

import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.ai.FormationGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Follower steering (ARMIES.md 3.3, {@code SlotSteerGoal}): what {@link FormationGoal} runs while the swarm is on. Followers never search
 * a path to keep formation:
 * <ul>
 * <li><b>Steering:</b> the slot and speed are decided every 2 ticks in R1 (4 in R2), at 1.2x speed when more than 3 blocks behind. The
 * wanted position is re-issued every tick, because vanilla move control drops back to waiting after each tick it moves.</li>
 * <li><b>Columns:</b> in a COLUMN the slot is on the leader's walked trail ({@link SwarmSquads#ribbonSlot}), so the column passes gates and
 * bridges where the leader did.</li>
 * <li><b>Obstacles:</b> a follower on the ground that walks into a block jumps (2.1 rule, kept: waiting 10 ticks would stall it at every
 * step).</li>
 * <li><b>Stuck</b> (no progress for 40 ticks, or more than 12 blocks from the slot): it first joins the leader's current path; if that
 * already failed once, it asks for one real path of at most {@value #NODES} nodes through the per-tick budget; at most one recovery per 40
 * ticks. It leaves a path within 4 blocks of the slot, or as soon as vanilla replaced the path it was given.</li>
 * </ul>
 */
public final class SlotSteer {
	public static final int NODES = 400, RECOVER_EVERY = 40;
	public static final double STUCK_FAR = 12.0, LEAVE_PATH = 4.0;
	/** Decisions, ribbon slots used, recoveries by the leader's path and by a real path, jumps (self-test, bench, command). */
	public static long decisions, ribbonSlots, leaderPathJoins, realPaths, jumps;

	private final KingdomSoldier mob;
	private Vec3 slot;
	private double speed = 1.0;
	private int nextDecide, nextRecover, stuck, lastCheck;
	private Vec3 lastPos = Vec3.ZERO;
	private boolean triedLeaderPath;
	/** The path this steer installed (null when steering by move control). */
	private Path installed;

	public SlotSteer(KingdomSoldier mob) {
		this.mob = mob;
	}

	/** The slot this follower steers to (self-test). */
	public Vec3 slot() {
		return slot;
	}

	public void reset() {
		installed = null;
		stuck = 0;
		slot = null;
	}

	public void tick() {
		Vec3 target = mob.formationTarget;
		if (target == null || !(mob.level() instanceof ServerLevel level)) return;
		int ring = Swarm.ring(mob);
		if (slot == null || mob.tickCount >= nextDecide || mob.tickCount < nextDecide - 8) {
			decide(level, target, ring);
			nextDecide = mob.tickCount + (ring >= 2 ? 4 : 2);
		}
		PathNavigation nav = mob.getNavigation();
		if (installed != null) {
			if (nav.getPath() == installed && !nav.isDone()) return; // walking a path: navigation steers
			installed = null;
		}
		double dx = slot.x - mob.getX(), dz = slot.z - mob.getZ();
		double d2 = dx * dx + dz * dz;
		if (d2 < 0.6 * 0.6) {
			nav.stop();
			mob.faceYaw(mob.formationYaw);
			stuck = 0;
			triedLeaderPath = false;
			return;
		}
		if (!nav.isDone()) nav.stop(); // a leftover path (melee, orders) would fight the steering
		mob.getMoveControl().setWantedPosition(slot.x, slot.y, slot.z, speed);
		if (mob.horizontalCollision && mob.onGround()) {
			mob.getJumpControl().jump();
			jumps++;
		}
	}

	private void decide(ServerLevel level, Vec3 target, int ring) {
		decisions++;
		long now = level.getGameTime();
		Vec3 s = target;
		Squad sq = mob.squad();
		Mob lead = sq == null ? null : sq.leaderEntity(level);
		if (sq != null && lead != null) {
			SwarmSquads.SquadState st = SwarmSquads.of(sq, now);
			SwarmSquads.track(st, lead.position(), now);
			if (sq.formation() == Squad.Formation.COLUMN && !mob.isPassenger()) {
				int k = SwarmSquads.columnIndex(st, sq, level, mob, now);
				Vec3 r = k < 0 ? null : SwarmSquads.ribbonSlot(st.trail, lead.position(), k);
				if (r != null) {
					s = r;
					ribbonSlots++;
				}
			}
		}
		slot = s;
		double dx = s.x - mob.getX(), dz = s.z - mob.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		speed = d > 3 ? 1.2 : (d > 1.5 ? 1.0 : 0.7);
		PathNavigation nav = mob.getNavigation();
		if (installed != null && nav.getPath() == installed && !nav.isDone()) {
			if (d < LEAVE_PATH) {
				nav.stop();
				installed = null;
			} else return;
		}
		if (d < 0.6) {
			stuck = 0;
			return;
		}
		// progress check every 10 ticks
		if (mob.tickCount - lastCheck >= 10 || mob.tickCount < lastCheck) {
			// at most 10 per check: after a pause (combat, hold) the first check must not count the whole pause as being stuck
			stuck = mob.position().distanceToSqr(lastPos) < 0.01 ? stuck + Math.min(10, Math.max(0, mob.tickCount - lastCheck)) : 0;
			lastCheck = mob.tickCount;
			lastPos = mob.position();
		}
		if ((stuck >= 40 || d > STUCK_FAR) && mob.tickCount >= nextRecover) recover(level, lead, s);
	}

	private void recover(ServerLevel level, Mob lead, Vec3 s) {
		nextRecover = mob.tickCount + RECOVER_EVERY;
		PathNavigation nav = mob.getNavigation();
		if (!triedLeaderPath && lead != null && lead != mob && !mob.isPassenger()) {
			Path lp = lead.getNavigation().getPath();
			if (lp != null && !lp.isDone() && lp.getNodeCount() > 1) {
				Path p = SwarmPaths.join(mob, new SwarmPaths.Entry(SwarmPaths.nodesOf(lp), lp.getTarget(), lp.canReach(), level.getGameTime()), SwarmPaths.JOIN_MAX);
				if (p != null && SwarmPaths.recomputeSafe(nav, lp.getTarget(), 64) && nav.moveTo(p, speed)) {
					installed = nav.getPath();
					triedLeaderPath = true;
					stuck = 0;
					leaderPathJoins++;
					return;
				}
			}
		}
		if (!KingdomPerf.tryPath(level)) {
			nextRecover = mob.tickCount + 1; // budget used up this tick: ask again next tick
			return;
		}
		double range = Math.max(16.0, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
		nav.setMaxVisitedNodesMultiplier((float) Math.min(1.0, NODES / Math.max(1.0, Math.floor(range * 16.0))));
		boolean ok = nav.moveTo(s.x, s.y, s.z, speed);
		nav.resetMaxVisitedNodesMultiplier();
		FormationGoal.memberPaths++;
		realPaths++;
		triedLeaderPath = false;
		stuck = 0;
		installed = ok ? nav.getPath() : null;
	}
}
