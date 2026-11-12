package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitType;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Player-like melee: approach (sprinting for a knockback hit), hold reach and strafe while the weapon recharges, raise
 * the shield against the target's wind-up, drop it to strike at full strength, and jump-crit on a cooldown.
 * Squad members only engage targets within 12 blocks (else they keep formation); they path locally to the target.
 */
public final class SoldierMeleeGoal extends Goal {
	/** Engage distance for squad members: shield-bearers press in from farther, the rest join closer. */
	private static final double ENGAGE = 12.0, ENGAGE_SHIELD = 16.0;
	private final KingdomSoldier mob;
	private int strafeDir = 1, strafeSwitch, repath, holdTicks;
	/** 3.0 repath throttle: where the target stood at the last path, and when (a path is redone only after it moved or the old one ran out). */
	private double pathTx = Double.NaN, pathTy, pathTz;
	private int pathTick = -100;
	public static long pathsMade, pathsSkipped;
	/** Set by the armies overhaul (kill switch {@code enabled}); off = the 2.1 repath rule. */
	public static boolean throttle;
	static final double REPATH_MOVED = 2.0;
	static final int REPATH_COOLDOWN = 10, REPATH_DONE_COOLDOWN = 4;
	private boolean strafeOn = true;

	public SoldierMeleeGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private boolean roleOk() {
		UnitType.Role r = mob.role();
		return r == UnitType.Role.MELEE || (r == UnitType.Role.CAVALRY && !mob.isPassenger());
	}

	private boolean engageable(LivingEntity t) {
		if (t == null || !t.isAlive()) return false;
		Squad sq = mob.squad();
		if (sq == null || mob.isSquadLeader() || !KingdomConfig.useFormations) return true;
		double e = mob.hasShield() ? ENGAGE_SHIELD : ENGAGE;
		return mob.distanceToSqr(t) < e * e || mob.getLastHurtByMob() == t;
	}

	@Override
	public boolean canUse() {
		return roleOk() && !mob.isRouted() && (mob.isActive() || mob.getLastHurtByMob() != null) && engageable(mob.getTarget());
	}

	@Override
	public boolean canContinueToUse() {
		return roleOk() && !mob.isRouted() && engageable(mob.getTarget());
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		repath = 0;
		pathTx = Double.NaN;
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.setSprinting(false);
		mob.meleeThreat = false;
		mob.getNavigation().stop();
		mob.getMoveControl().strafe(0, 0);
	}

	@Override
	public void tick() {
		LivingEntity t = mob.getTarget();
		if (t == null || !(mob.level() instanceof ServerLevel level)) return;
		mob.getLookControl().setLookAt(t, 30F, 30F);
		double d2 = mob.distanceToSqr(t);
		boolean inReach = mob.isWithinMeleeAttackRange(t);
		boolean ready = mob.attackReady();
		// threat: an enemy close enough to hit us while we can't hit back yet
		boolean windup = t instanceof Player p ? p.getAttackStrengthScale(0.5F) > 0.7F : (t instanceof Mob m && (m.isAggressive() || m.getTarget() == mob));
		mob.meleeThreat = d2 < 4.5 * 4.5 && windup;
		boolean blocking = mob.isBlocking();

		// jump crit: rolled once per attack (rare for rank and file), on cooldown, when the hit is ready and the target is close
		if (KingdomConfig.useCrits && ready && !mob.critRolled && !mob.pendingCrit && mob.critCooldown <= 0 && mob.onGround() && d2 < 3.2 * 3.2) {
			mob.critRolled = true;
			if (mob.getRandom().nextFloat() < mob.unit().combat().critChance()) {
				mob.pendingCrit = true;
				mob.setSprinting(false);
				mob.getJumpControl().jump();
				mob.critCooldown = 20; // re-armed with the unit's cooldown when the crit lands
			}
		}
		// knight shield bash: shove the enemy back while blocking
		if (blocking && mob.bashCooldown <= 0 && d2 < 2.4 * 2.4 && mob.unit().ability() == dev.elsewhere.kingdom.UnitType.Ability.BASH && windup) {
			mob.bashCooldown = 100;
			double dx = mob.getX() - t.getX(), dz = mob.getZ() - t.getZ();
			var bash = mob.damageSources().mobAttack(mob);
			if (t.hurtServer(level, bash, 1.5F)) t.knockback(0.8, dx, dz, bash, 1.5F);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), mob.getSoundSource(), 1F, 0.8F);
			KingdomSoldier.totalBashes++;
		}
		if (mob.pendingCrit) {
			if (inReach && mob.canCritNow(t) && mob.getDeltaMovement().y < 0) mob.strike(level, t);
			else if (!inReach) mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
			return;
		}

		if (inReach) {
			// like a player: keep the shield up until the enemy's swing is blocked (or ~0.75 s passed), then riposte
			boolean justBlocked = mob.tickCount - mob.lastBlockTick <= 4;
			if (ready && (!blocking || justBlocked || holdTicks >= 15)) {
				holdTicks = 0;
				mob.strike(level, t);
				return;
			}
			if (ready) holdTicks++;
			mob.getNavigation().stop();
			float strafe = mob.unit().combat().strafe();
			if (--strafeSwitch <= 0) {
				strafeSwitch = 20 + mob.getRandom().nextInt(25);
				if (mob.getRandom().nextFloat() < 0.4F) strafeDir = -strafeDir;
				// light strafing: rank and file sidestep only part of the time
				strafeOn = mob.getRandom().nextFloat() < 0.35F + 0.65F * strafe;
			}
			float fwd = mob.hasShield() ? (d2 < 1.3 * 1.3 ? -0.3F : 0.3F) : (d2 < 1.6 * 1.6 ? -0.5F : 0.15F);
			float side = strafeOn ? strafeDir * (blocking ? 0.25F : 0.45F) * (0.4F + 0.6F * strafe) : 0F;
			mob.getMoveControl().strafe(blocking ? fwd * 0.5F : fwd, side);
			mob.setSprinting(false);
			return;
		}

		// approach; sprint in for a knockback hit when the attack will be ready on arrival (rolled once per attack)
		if (!ready) mob.critRolled = false;
		if (ready && !mob.sprintRolled) {
			mob.sprintRolled = true;
			mob.sprintIntent = mob.getRandom().nextFloat() < mob.unit().combat().sprintChance();
		}
		boolean sprint = ready && mob.sprintIntent && d2 > 2.5 * 2.5 && d2 < 8 * 8 && !blocking;
		mob.setSprinting(sprint);
		double speed = blocking ? 0.6 : (sprint ? 1.3 : 1.0);
		if (d2 < 6 * 6) {
			mob.getNavigation().stop();
			// 3.0 swarm: close in from the squad plan's angle (attackers surround instead of queueing); straight in for the last 2.5 blocks
			net.minecraft.world.phys.Vec3 ap = d2 > 2.5 * 2.5 ? dev.elsewhere.kingdom.armies.swarm.TargetAssigner.approachSlot(mob, t) : null;
			if (ap != null) mob.getMoveControl().setWantedPosition(ap.x, ap.y, ap.z, speed);
			else mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), speed);
		} else {
			repath--;
			boolean done = mob.getNavigation().isDone();
			boolean moved = Double.isNaN(pathTx) || t.distanceToSqr(pathTx, pathTy, pathTz) > REPATH_MOVED * REPATH_MOVED;
			int since = mob.tickCount - pathTick;
			// repath only when the target moved since the last path (on the usual timer), or the old path ran out; never twice within the cooldown
			boolean want = !throttle ? (repath <= 0 || done) : done ? since >= REPATH_DONE_COOLDOWN : (repath <= 0 && moved && since >= REPATH_COOLDOWN);
			if (want) {
				boolean shared = dev.elsewhere.kingdom.armies.swarm.SwarmPaths.joinApproach(mob, t, speed); // 3.0 swarm: a squad mate's fresh path to t
				if (shared || KingdomPerf.tryPath(level)) {
					if (!shared) {
						mob.getNavigation().moveTo(t, speed);
						dev.elsewhere.kingdom.armies.swarm.SwarmPaths.offerApproach(mob, t);
						pathsMade++;
					}
					pathTx = t.getX();
					pathTy = t.getY();
					pathTz = t.getZ();
					pathTick = mob.tickCount;
					repath = KingdomConfig.leaderRepathMin + mob.getRandom().nextInt(Math.max(1, KingdomConfig.leaderRepathMax - KingdomConfig.leaderRepathMin + 1));
				} else {
					repath = 1;
				}
			} else if (repath <= 0 && !done) {
				mob.getNavigation().setSpeedModifier(speed); // keep the current path, follow the sprint/block speed
				pathsSkipped++;
				repath = 2;
			}
		}
	}
}
