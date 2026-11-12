package dev.elsewhere.kingdom.tactics;

import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.tactics.Types.Bucket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One brain per real squad (FIELD-TACTICS 1.1-1.4): every {@code decisionInterval} ticks (10-20, jittered, at most {@value TacticsRuntime#MAX_DECISIONS_PER_TICK} squads per tick) it builds a
 * {@link Perception}, updates the {@link SquadMemory}, picks a {@link Behaviour} by bucket (SURVIVE, REACT, ENGAGE, POSITION, IDLE) with 20 percent hysteresis and a minimum commit time, and
 * writes {@link Order}s onto the soldiers. A thrown exception disables the brain of that squad for 600 ticks (the 2.0 goals keep running).
 */
public final class SquadBrain {
	public final UUID squadId;
	public final Squad squad;
	public final TacticsProfile profile;
	public final SquadMemory memory = new SquadMemory();
	public final List<Perception.IntelSource> intel = new ArrayList<>();
	public @Nullable Perception last;
	public @Nullable Behaviour active;
	public long activeSince, nextDecisionTick, disabledUntil, lastPerceptionTick;
	public double activeScore;
	/** Tests and the command read these. */
	public long decisions, failures, ordersIssued;
	public int lastPathRequests;
	final Map<String, Long> cooldownUntil = new HashMap<>();
	final Map<UUID, Types.TacticalRole> roles = new HashMap<>();
	final long[] ring = new long[100];
	int ringIdx, ringCount;
	long lastSignal = -1000, lastShout = -1000, lastRoleKey;
	Perception.@Nullable TerrainRead terrain;
	long terrainAt = -1000;
	final RandomSource rng = RandomSource.create();
	boolean loggedFailure;
	/** The squad's last decision as text (for the command and tests). */
	public String lastChoice = "";

	SquadBrain(Squad squad, TacticsProfile profile) {
		this.squad = squad;
		this.squadId = squad.id();
		this.profile = profile;
		this.rng.setSeed(squadId.getLeastSignificantBits());
		profile.onSquadCreated(this);
		TacticsRuntime.register(this);
	}

	// ---- config view (the faction file's tactics block overrides the profile) ----------------------------------------------

	TacticsConfig.@Nullable Cfg cfg() {
		return TacticsConfig.of(profile.faction());
	}

	public int interval() {
		var c = cfg();
		return Math.clamp(c != null && c.interval != null ? c.interval : profile.decisionInterval(), 10, 20);
	}

	public double minOdds() {
		var c = cfg();
		return c != null && c.minOdds != null ? c.minOdds : profile.minLocalOdds();
	}

	public double retreatOdds() {
		var c = cfg();
		return c != null && c.retreatOdds != null ? c.retreatOdds : profile.retreatOdds();
	}

	public double blunder() {
		var c = cfg();
		return c != null && c.blunder != null ? c.blunder : profile.blunderRate();
	}

	public int reactMin() {
		var c = cfg();
		return c != null && c.reactMin != null ? c.reactMin : profile.reactionTicksMin();
	}

	public int reactMax() {
		var c = cfg();
		return c != null && c.reactMax != null ? c.reactMax : profile.reactionTicksMax();
	}

	public String mobPolicy() {
		var c = cfg();
		return c != null && c.mobPolicy != null ? c.mobPolicy : profile.mobPolicy();
	}

	public double weight(String behaviourId) {
		var c = cfg();
		return c != null ? c.weight.getOrDefault(behaviourId, 1.0) : 1.0;
	}

	public double minRatio(String behaviourId) {
		var c = cfg();
		return c != null ? c.minRatio.getOrDefault(behaviourId, 0.0) : 0.0;
	}

	/** WAR-AI intelligence noise: the profile's, else the faction's brain tuning (effective, so a dead Falconer raises it). */
	public double intelNoise() {
		if (profile.intelNoise() != null) return profile.intelNoise();
		var t = dev.elsewhere.kingdom.war.Tuning.of(squad.faction(), dev.elsewhere.kingdom.Factions.def(squad.faction()));
		return t.noise;
	}

	public Types.TacticalRole roleOf(UUID id) {
		return roles.getOrDefault(id, Types.TacticalRole.ANVIL);
	}

	/** The soldiers this brain commands (leader first), alive and loaded. */
	public List<KingdomSoldier> bodies(ServerLevel level) {
		List<KingdomSoldier> out = new ArrayList<>();
		if (squad.leaderEntity(level) instanceof KingdomSoldier ls && ls.isAlive()) out.add(ls);
		out.addAll(squad.memberEntities(level));
		return out;
	}

	Perception.TerrainRead terrainRead(ServerLevel level, Vec3 c, long now) {
		if (terrain == null || now - terrainAt > 100) {
			terrain = Perceiver.scan(level, c);
			terrainAt = now;
		}
		return terrain;
	}

	/** Assigns tactical roles from unit role and ability; reassigned when the member set changes. */
	void assignRoles(ServerLevel level, List<KingdomSoldier> bodies) {
		long key = 0;
		for (KingdomSoldier s : bodies) key = key * 31 + s.getUUID().hashCode();
		if (key == lastRoleKey && roles.size() == bodies.size()) return;
		lastRoleKey = key;
		roles.clear();
		int shields = 0;
		for (KingdomSoldier s : bodies) {
			var u = s.unit();
			Types.TacticalRole r;
			if (squad.leaderEntity(level) == s) r = Types.TacticalRole.LEADER;
			else if (u.ability() == dev.elsewhere.kingdom.UnitType.Ability.STANDARD) r = Types.TacticalRole.BANNER;
			else if (u.role().ranged()) r = Types.TacticalRole.SKIRMISHER;
			else if (u.role() == dev.elsewhere.kingdom.UnitType.Role.LANCER || u.role() == dev.elsewhere.kingdom.UnitType.Role.CAVALRY) r = Types.TacticalRole.HAMMER;
			else if (Perceiver.path(s.getOffhandItem()).equals("shield") && shields++ % 2 == 0) r = Types.TacticalRole.SHIELD;
			else r = Types.TacticalRole.ANVIL;
			roles.put(s.getUUID(), r);
		}
	}

	// ---- the decision ---------------------------------------------------------------------------------------------------------

	/** Called from the squad tick; returns at once unless a decision is due. */
	void tick(ServerLevel level, long now, List<KingdomSoldier> bodies) {
		if (now < nextDecisionTick || now < disabledUntil) return;
		nextDecisionTick = now + interval() + rng.nextInt(7) - 3;
		long t0 = System.nanoTime();
		try {
			decide(level, now, bodies);
		} catch (RuntimeException e) {
			failures++;
			disabledUntil = now + 600;
			active = null;
			for (KingdomSoldier s : bodies) s.tacticsOrder = null;
			if (!loggedFailure) {
				loggedFailure = true;
				KingdomContent.LOG.warn("squad tactics: decision failed for squad {} ({}), brain off for 600 ticks", squadId, profile.name(), e);
			}
		}
		long dt = System.nanoTime() - t0;
		ring[ringIdx] = dt;
		ringIdx = (ringIdx + 1) % ring.length;
		ringCount = Math.min(ring.length, ringCount + 1);
		decisions++;
	}

	/** Average decision time in milliseconds over the last 100 decisions. */
	public double avgDecisionMs() {
		if (ringCount == 0) return 0;
		long s = 0;
		for (int i = 0; i < ringCount; i++) s += ring[i];
		return s / (double) ringCount / 1e6;
	}

	void decide(ServerLevel level, long now, List<KingdomSoldier> bodies) {
		if (bodies.isEmpty()) return;
		assignRoles(level, bodies);
		Perception p = Perceiver.build(level, this, bodies, now);
		lastPerceptionTick = now;
		last = p;
		memory.observe(p, now);
		if (active != null && rng.nextDouble() < blunder()) {
			// a human mistake: hold the previous orders for another interval
			lastChoice = active.id() + " (blunder: held)";
			return;
		}
		List<Behaviour> all = new ArrayList<>(profile.behaviours());
		Behaviour best = null;
		double bestScore = 0, budget = 0;
		Bucket bestBucket = null;
		for (Bucket bucket : Bucket.values()) {
			for (Behaviour b : all) {
				if (b.bucket() != bucket) continue;
				Long cd = cooldownUntil.get(b.id());
				if (cd != null && cd > now && b != active) continue;
				budget += b.cost();
				if (budget > 1.0 && best != null) break;
				if (minRatio(b.id()) > 0 && p.odds().ratio() < minRatio(b.id()) && p.odds().theirs() > 0) continue;
				double sc = b.score(p, memory, this) * weight(b.id());
				if (sc > bestScore) {
					best = b;
					bestScore = sc;
					bestBucket = bucket;
				}
			}
			if (best != null) break;
		}
		Behaviour keep = active;
		if (keep != null && best != keep) {
			boolean preempt = bestBucket == Bucket.SURVIVE && keep.bucket() != Bucket.SURVIVE;
			boolean committed = now - activeSince < keep.minCommitTicks();
			if (!preempt && committed && keep.score(p, memory, this) > 0) best = keep;
			else if (!preempt && best != null && bestScore < activeScore * 1.2 && keep.score(p, memory, this) > 0) best = keep;
		}
		if (best == null) {
			if (active != null) for (KingdomSoldier s : bodies) s.tacticsOrder = null;
			active = null;
			lastChoice = "none";
			return;
		}
		if (best != active) {
			active = best;
			activeSince = now;
			if (best.cooldownTicks() > 0) cooldownUntil.put(best.id(), now + best.cooldownTicks());
		}
		activeScore = bestScore;
		lastChoice = best.id();
		apply(level, now, bodies, best.plan(p, memory, this), best);
	}

	private void apply(ServerLevel level, long now, List<KingdomSoldier> bodies, Order.Orders o, Behaviour b) {
		int delay = 0;
		if (o.signal() != null) {
			delay = o.signal().delay();
			emit(level, bodies, o.signal(), now);
		}
		int moves = 0;
		Map<UUID, KingdomSoldier> byId = new HashMap<>();
		for (KingdomSoldier s : bodies) byId.put(s.getUUID(), s);
		for (KingdomSoldier s : bodies) s.tacticsOrder = null;
		for (var e : o.perSoldier().entrySet()) {
			KingdomSoldier s = byId.get(e.getKey());
			if (s == null) continue;
			Order raw = e.getValue();
			long react = now + delay + profileReact();
			long exp = raw.expiresTick() > now ? raw.expiresTick() : now + b.orderTicks();
			if (raw.pos() != null) moves++;
			s.tacticsOrder = new Order(raw.kind(), raw.pos(), raw.target(), exp, now, react);
			ordersIssued++;
		}
		lastPathRequests = Math.min(moves, 4);
	}

	int profileReact() {
		int lo = reactMin(), hi = Math.max(lo, reactMax());
		return lo + rng.nextInt(hi - lo + 1);
	}

	/** Plays a signal at the squad leader: sound, particles, and (rate limited) a shout. */
	public void emit(ServerLevel level, List<KingdomSoldier> bodies, Signal s, long now) {
		var sig = profile.signals();
		if (bodies.isEmpty() || now - lastSignal < 20) return;
		boolean horn = s.kind().name().startsWith("HORN");
		if (horn && !sig.horn()) return;
		if (s.kind() == Types.SignalKind.BANNER_RAISE && !sig.banner()) return;
		lastSignal = now;
		Vec3 at = bodies.get(0).position();
		level.playSound(null, at.x, at.y, at.z, s.sound(), SoundSource.HOSTILE, 4.0F, 1.0F);
		if (s.particle() != null) level.sendParticles(s.particle(), at.x, at.y + 2.2, at.z, 6, 0.4, 0.3, 0.4, 0.02);
		TacticsRuntime.signalsEmitted++;
	}

	/** Gives an allied squad's brain a threat event (call_help). */
	public void tell(Perception.ThreatEvent e) {
		memory.addEvent(e);
		nextDecisionTick = Math.min(nextDecisionTick, e.tick() + 4);
	}
}
