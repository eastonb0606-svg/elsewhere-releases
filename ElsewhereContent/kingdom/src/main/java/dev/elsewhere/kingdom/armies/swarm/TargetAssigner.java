package dev.elsewhere.kingdom.armies.swarm;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.armies.ArmyScale;
import dev.elsewhere.kingdom.tactics.Perception;
import dev.elsewhere.kingdom.tactics.Perceiver;
import dev.elsewhere.kingdom.tactics.SquadBrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Squad-level target selection (ARMIES.md 3.4, {@code tactics/TargetAssigner}). Once per squad per {@value #DECIDE} ticks one pass over the
 * squad's shared enemy list gives every soldier a target and, for melee, an approach angle:
 * <ul>
 * <li><b>Shared perception:</b> visibility comes from the squad brain's fresh {@link Perception} when it has one (no new rays); otherwise
 * one ray per enemy from the nearest soldier, at most {@value #RAYS_R1} per decision in R1 and {@value #RAYS_R2} from the leader in R2.
 * Soldiers stop casting their own target rays.</li>
 * <li><b>Spread:</b> at most {@value #MELEE_PER_ENEMY} melee soldiers of a squad on one enemy soldier or mob; on a player, the kingdom's
 * attack tokens ({@code army.scale.attack_tokens}). Archers and crossbowmen have no cap on mobs and soldiers.</li>
 * <li><b>Keep, then fill:</b> soldiers keep a valid current target while its cap allows; the others take the nearest visible enemy under
 * its cap; a melee soldier with nothing under the cap keeps its old target, or holds formation.</li>
 * </ul>
 * Only soldiers in R1-R2 use the plan. In R0 (a player within 24 blocks, or fighting a player) and for 3 s after being hurt (retaliation is
 * immediate, {@code KingdomSoldier.setLastHurtByMob}) the soldier's own target goal runs as before. Targets are always re-checked with
 * {@link KingdomTargeting#canAttack}.
 */
public final class TargetAssigner {
	private TargetAssigner() {}

	public static final int DECIDE = 10, MELEE_PER_ENEMY = 3, RAYS_R1 = 16, RAYS_R2 = 8, PERCEPTION_FRESH = 20, RETALIATE_TICKS = 60;
	public static final double KEEP_RANGE = 32.0;
	/** Plans made, rays cast, decisions that reused the brain's perception, melee/ranged assignments, melee held back by the cap. */
	public static long plans, rays, perceptionReuse, assignedMelee, assignedRanged, heldBack;

	/** One squad's decision. */
	public static final class Plan {
		long tick;
		final Map<UUID, LivingEntity> target = new HashMap<>();
		final Map<UUID, Double> angle = new HashMap<>();
		/** Melee attackers per enemy (self-test). */
		final Map<LivingEntity, Integer> melee = new IdentityHashMap<>();
		int raysUsed;
		boolean reused;

		public @Nullable LivingEntity targetOf(UUID soldier) {
			return target.get(soldier);
		}

		public int maxMeleeOnOneSoldier() {
			int m = 0;
			for (var e : melee.entrySet()) if (!(e.getKey() instanceof Player)) m = Math.max(m, e.getValue());
			return m;
		}

		public int assigned() {
			return target.size();
		}

		public int raysUsed() {
			return raysUsed;
		}

		public boolean reusedPerception() {
			return reused;
		}
	}

	private static final Map<UUID, Plan> PLANS = new HashMap<>();

	static void clear() {
		PLANS.clear();
	}

	/** True when this soldier's target comes from its squad's plan (swarm on, in a squad, R1-R2, not retaliating). */
	public static boolean managed(KingdomSoldier mob) {
		if (!Swarm.on() || mob.squadId() == null || Swarm.ring(mob) == 0) return false;
		LivingEntity hurtBy = mob.getLastHurtByMob();
		return !(hurtBy != null && hurtBy == mob.getTarget() && mob.tickCount - mob.getLastHurtByMobTimestamp() < RETALIATE_TICKS);
	}

	/** The plan's target for a managed soldier (null = no target: keep the current one if valid, else hold formation). */
	public static @Nullable LivingEntity assigned(KingdomSoldier mob) {
		Squad sq = mob.squad();
		if (sq == null || !(mob.level() instanceof ServerLevel level)) return null;
		LivingEntity t = plan(sq, level).target.get(mob.getUUID());
		return t != null && t.isAlive() && !t.isRemoved() && KingdomTargeting.canAttack(mob, t) ? t : null;
	}

	/**
	 * Where a melee soldier should close in on {@code t} from: its own angle around the target, 1.6 blocks out, so a squad's attackers
	 * surround an enemy instead of queueing behind each other. Null when the plan gave it no angle for this target.
	 */
	public static @Nullable Vec3 approachSlot(KingdomSoldier mob, LivingEntity t) {
		if (!managed(mob)) return null;
		Squad sq = mob.squad();
		if (sq == null) return null;
		Plan p = PLANS.get(sq.id());
		if (p == null || p.target.get(mob.getUUID()) != t) return null;
		Double a = p.angle.get(mob.getUUID());
		if (a == null) return null;
		return new Vec3(t.getX() + Math.cos(a) * 1.6, t.getY(), t.getZ() + Math.sin(a) * 1.6);
	}

	/** The squad's current plan, made at most every {@value #DECIDE} ticks. */
	public static Plan plan(Squad sq, ServerLevel level) {
		long now = level.getGameTime();
		Plan p = PLANS.get(sq.id());
		if (p != null && now - p.tick < DECIDE && now >= p.tick) return p;
		if (p == null && PLANS.size() > 128) PLANS.values().removeIf(x -> now - x.tick > 200 || now < x.tick);
		p = decide(sq, level, now);
		PLANS.put(sq.id(), p);
		return p;
	}

	/** A fresh plan now, replacing the cached one (self-test). */
	public static Plan planNow(Squad sq, ServerLevel level) {
		Plan p = decide(sq, level, level.getGameTime());
		PLANS.put(sq.id(), p);
		return p;
	}

	static Plan decide(Squad sq, ServerLevel level, long now) {
		plans++;
		Plan plan = new Plan();
		plan.tick = now;
		List<KingdomSoldier> bodies = new ArrayList<>();
		Mob lead = sq.leaderEntity(level);
		if (lead instanceof KingdomSoldier ls && ls.isAlive()) bodies.add(ls);
		bodies.addAll(sq.memberEntities(level));
		if (bodies.isEmpty()) return plan;
		List<LivingEntity> enemies = new ArrayList<>();
		for (LivingEntity e : sq.enemies()) if (e.isAlive() && !e.isRemoved()) enemies.add(e);
		Set<LivingEntity> visible = visible(sq, level, now, bodies, lead, enemies, plan);
		ArmyScale scale = ArmyScale.of(sq.faction());
		Map<LivingEntity, int[]> load = new IdentityHashMap<>();
		// melee already committed on one enemy by the OTHER squads' fresh plans counts against the same cap, so two or three lances do not
		// pile on a lone enemy (s-swarm1 L308.1: 15 knights on one soldier)
		UUID own = sq.id();
		for (var en : PLANS.entrySet()) {
			Plan op = en.getValue();
			if (own.equals(en.getKey()) || now - op.tick > 3 * DECIDE || now < op.tick) continue;
			for (var m : op.melee.entrySet()) if (m.getKey().isAlive()) load.computeIfAbsent(m.getKey(), k -> new int[2])[0] += m.getValue();
		}
		List<KingdomSoldier> open = new ArrayList<>();
		// pass 1: keep a valid current target while its cap allows
		for (KingdomSoldier b : bodies) {
			LivingEntity cur = b.getTarget();
			if (valid(b, cur) && room(load, cur, b, scale)) take(plan, load, b, cur);
			else open.add(b);
		}
		// pass 2: the rest take the nearest visible enemy under its cap (nearest soldiers choose first)
		Map<KingdomSoldier, Double> nearest = new IdentityHashMap<>();
		for (KingdomSoldier b : open) {
			double best = Double.MAX_VALUE;
			for (LivingEntity e : visible) best = Math.min(best, e.distanceToSqr(b));
			nearest.put(b, best);
		}
		open.sort((x, y) -> Double.compare(nearest.get(x), nearest.get(y)));
		for (KingdomSoldier b : open) {
			double range = Math.min(24.0, b.getAttributeValue(Attributes.FOLLOW_RANGE));
			LivingEntity pick = null;
			double best = range * range;
			for (LivingEntity e : enemies) {
				if (!visible.contains(e)) continue;
				double d = e.distanceToSqr(b);
				if (d < best && room(load, e, b, scale) && KingdomTargeting.canAttack(b, e)) {
					best = d;
					pick = e;
				}
			}
			if (pick != null) take(plan, load, b, pick);
			else if ((b.role().ranged() || b.getTarget() instanceof Player) && valid(b, b.getTarget())) take(plan, load, b, b.getTarget()); // melee over the cap holds its slot instead of piling on
			else if (!b.role().ranged() && !visible.isEmpty()) heldBack++;
		}
		// approach angles: each enemy's melee attackers spread around it, starting from the side the squad comes from
		Map<LivingEntity, Integer> nth = new IdentityHashMap<>();
		for (KingdomSoldier b : bodies) {
			LivingEntity t = plan.target.get(b.getUUID());
			if (t == null || b.role().ranged()) continue;
			int n = nth.merge(t, 1, Integer::sum) - 1;
			double base = Math.atan2(b.getZ() - t.getZ(), b.getX() - t.getX());
			double off = n == 0 ? 0 : ((n + 1) / 2) * (Math.PI / 2.5) * (n % 2 == 1 ? 1 : -1);
			plan.angle.put(b.getUUID(), base + off);
		}
		return plan;
	}

	private static boolean valid(KingdomSoldier b, @Nullable LivingEntity t) {
		return t != null && t.isAlive() && !t.isRemoved() && t.distanceToSqr(b) < KEEP_RANGE * KEEP_RANGE && KingdomTargeting.canAttack(b, t);
	}

	private static int cap(LivingEntity e, KingdomSoldier b, @Nullable ArmyScale scale) {
		boolean ranged = b.role().ranged();
		if (e instanceof Player) {
			if (scale == null) return Integer.MAX_VALUE; // kingdoms without army.scale are unchanged (as ArmyLod's tokens)
			return Math.max(1, ranged ? scale.rangedTokens() : scale.meleeTokens());
		}
		return ranged ? Integer.MAX_VALUE : MELEE_PER_ENEMY;
	}

	private static boolean room(Map<LivingEntity, int[]> load, LivingEntity e, KingdomSoldier b, @Nullable ArmyScale scale) {
		int[] l = load.get(e);
		int used = l == null ? 0 : l[b.role().ranged() ? 1 : 0];
		return used < cap(e, b, scale);
	}

	private static void take(Plan plan, Map<LivingEntity, int[]> load, KingdomSoldier b, LivingEntity e) {
		boolean ranged = b.role().ranged();
		load.computeIfAbsent(e, k -> new int[2])[ranged ? 1 : 0]++;
		plan.target.put(b.getUUID(), e);
		if (ranged) assignedRanged++;
		else {
			assignedMelee++;
			plan.melee.merge(e, 1, Integer::sum);
		}
	}

	/** Enemies the squad can see: the brain's fresh perception, else one ray per enemy from the nearest soldier (budgeted). */
	private static Set<LivingEntity> visible(Squad sq, ServerLevel level, long now, List<KingdomSoldier> bodies, @Nullable Mob lead, List<LivingEntity> enemies, Plan plan) {
		Set<LivingEntity> out = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		if (enemies.isEmpty()) return out;
		SquadBrain brain = sq.brain;
		Perception per = brain == null ? null : brain.last;
		Set<UUID> seen = null;
		if (per != null && now - brain.lastPerceptionTick <= PERCEPTION_FRESH && now >= brain.lastPerceptionTick) {
			seen = new HashSet<>();
			for (Perception.Contact c : per.contacts()) if (c.visibleNow()) seen.add(c.id());
			plan.reused = true;
			perceptionReuse++;
		}
		int ring = Swarm.ring(bodies.get(0));
		int budget = ring >= 2 ? RAYS_R2 : RAYS_R1;
		for (LivingEntity e : enemies) {
			if (seen != null && seen.contains(e.getUUID())) {
				out.add(e);
				continue;
			}
			if (plan.raysUsed >= budget) continue;
			KingdomSoldier from = null;
			if (ring >= 2 && lead instanceof KingdomSoldier ls && ls.isAlive()) from = ls; // R2: the squad sees through its leader
			else {
				double best = Double.MAX_VALUE;
				for (KingdomSoldier b : bodies) {
					double d = b.distanceToSqr(e);
					if (d < best) {
						best = d;
						from = b;
					}
				}
			}
			if (from == null) continue;
			double sight = from.role().ranged() ? Perceiver.SIGHT_ARCHER : Perceiver.SIGHT;
			if (from.distanceToSqr(e) > sight * sight) continue;
			plan.raysUsed++;
			rays++;
			if (from.getSensing().hasLineOfSight(e)) out.add(e);
		}
		return out;
	}

	/** The last plan of a squad, or null (self-test, command). */
	public static @Nullable Plan last(Squad sq) {
		return PLANS.get(sq.id());
	}
}
