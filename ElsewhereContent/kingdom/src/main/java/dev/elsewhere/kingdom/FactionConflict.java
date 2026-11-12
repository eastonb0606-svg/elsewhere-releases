package dev.elsewhere.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Conflict between factions.
 * <ul>
 * <li><b>Near a player</b> there is nothing special: soldiers of factions at {@link Factions.Stance#WAR} target each other
 * through {@link KingdomTargeting}, so two patrols that meet fight as real entities (the caps apply to all factions together).</li>
 * <li><b>Far from every player</b> squads are virtual (data only, {@link KingdomLod}). Two virtual squads of hostile factions
 * that come within {@link #MEET_RADIUS} blocks of each other, with no player within the materialize radius plus 16,
 * resolve the fight <b>abstractly</b>: no entities, one seeded roll ({@link #roll}) decides the winner and the casualties.
 * The seed is derived from the world seed, the two squad ids and a saved clash counter, and every outcome is stored in
 * {@link KingdomState} ({@link Record}, newest {@link #MAX_RECORDS}), so results are reproducible and survive restarts.</li>
 * </ul>
 * Disabled by {@code factions.abstractConflicts = false}. Fallen factions and factions at peace or allied never clash.
 */
public final class FactionConflict {
	private FactionConflict() {}

	public static final double MEET_RADIUS = 24;
	public static final int CHECK_TICKS = 100, MAX_RECORDS = 64, COOLDOWN_TICKS = 600;
	/** Tests: clashes resolved since start. */
	public static long resolved;

	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	/** One resolved clash. {@code lossesA/B} are soldiers killed, {@code sizeA/B} the squad sizes before the fight. */
	public record Record(long time, Identifier factionA, Identifier factionB, Identifier winner, int sizeA, int sizeB, int lossesA, int lossesB, long seed, BlockPos at) {
		public static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("time").forGetter(Record::time),
			Identifier.CODEC.fieldOf("a").forGetter(Record::factionA),
			Identifier.CODEC.fieldOf("b").forGetter(Record::factionB),
			Identifier.CODEC.fieldOf("winner").forGetter(Record::winner),
			Codec.INT.fieldOf("size_a").forGetter(Record::sizeA),
			Codec.INT.fieldOf("size_b").forGetter(Record::sizeB),
			Codec.INT.fieldOf("losses_a").forGetter(Record::lossesA),
			Codec.INT.fieldOf("losses_b").forGetter(Record::lossesB),
			Codec.LONG.fieldOf("seed").forGetter(Record::seed),
			BlockPos.CODEC.fieldOf("at").forGetter(Record::at)
		).apply(i, Record::new));
	}

	/** The pure outcome of one roll: who wins and how many soldiers each side loses. */
	public record Outcome(boolean aWins, int lossesA, int lossesB) {}

	/** Fighting strength of a virtual squad (health weighted by role; uses the faction's own roster). */
	public static double power(Squad q) {
		double p = 0;
		for (Squad.VirtualSoldier v : q.virtualMembers) {
			UnitType u = Factions.unit(q.faction, v.unit());
			double w = u.officer() ? 1.25 : u.role().ranged() ? 0.9 : (u.role() == UnitType.Role.LANCER || u.role() == UnitType.Role.CAVALRY) ? 1.3 : 1.0;
			p += Math.max(1.0, v.health()) * w * (1.0 + 0.15 * u.stats().attackDamage());
		}
		return p;
	}

	/**
	 * The seeded roll. {@code A} wins with probability pa^2 / (pa^2 + pb^2); the loser loses 60-100% of its soldiers, the
	 * winner 5-50% scaled by how strong the loser was. Same inputs and seed give the same outcome.
	 */
	public static Outcome roll(long seed, double powerA, double powerB, int sizeA, int sizeB) {
		RandomSource r = RandomSource.create(mix(seed));
		double a2 = powerA * powerA, b2 = powerB * powerB;
		boolean aWins = r.nextDouble() < (a2 + b2 <= 0 ? 0.5 : a2 / (a2 + b2));
		double pw = aWins ? powerA : powerB, pl = aWins ? powerB : powerA;
		int nw = aWins ? sizeA : sizeB, nl = aWins ? sizeB : sizeA;
		int lostL = Math.min(nl, (int) Math.round(nl * (0.6 + 0.4 * r.nextDouble())));
		double wf = Math.clamp(0.5 * (pw <= 0 ? 1 : pl / pw) * (0.5 + r.nextDouble()), 0.05, 0.5);
		int lostW = Math.min(Math.max(0, nw - 1), (int) Math.round(nw * wf));
		return aWins ? new Outcome(true, lostW, lostL) : new Outcome(false, lostL, lostW);
	}

	/** SplitMix64 finalizer: java's LCG gives correlated first values for nearby seeds. */
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	static long seedFor(ServerLevel level, KingdomState st, Squad a, Squad b) {
		long h = level.getSeed() * 0x9E3779B97F4A7C15L;
		h ^= a.id.getMostSignificantBits() ^ b.id.getLeastSignificantBits();
		h ^= Long.rotateLeft(a.id.getLeastSignificantBits() ^ b.id.getMostSignificantBits(), 17);
		return h + st.clashCount * 0xBF58476D1CE4E5B9L;
	}

	static boolean eligible(ServerLevel level, KingdomState st, Squad q) {
		return q.virtual && q.anchor != null && !q.dismissed && !q.virtualMembers.isEmpty() && !st.isFallen(q.faction)
			&& COOLDOWN.getOrDefault(q.id, 0L) <= level.getGameTime()
			&& !KingdomLod.observerWithin(level, q.anchor, KingdomLod.MATERIALIZE_RADIUS + 16);
	}

	/** Every {@link #CHECK_TICKS}: resolve at most one clash per squad among the virtual squads of this level. */
	static void tick(ServerLevel level, KingdomState st, long now) {
		if (!KingdomConfig.abstractConflicts || now % CHECK_TICKS != 0) return;
		List<Squad> v = null;
		for (Squad q : st.squads()) {
			if (!q.virtual) continue;
			if (v == null) v = new ArrayList<>();
			v.add(q);
		}
		if (v == null || v.size() < 2) return;
		for (int i = 0; i < v.size(); i++) {
			Squad a = v.get(i);
			if (!eligible(level, st, a)) continue;
			for (int j = i + 1; j < v.size(); j++) {
				Squad b = v.get(j);
				if (!eligible(level, st, b) || !Factions.hostile(a.faction, b.faction)) continue;
				if (a.anchor.distanceToSqr(b.anchor) > MEET_RADIUS * MEET_RADIUS) continue;
				resolve(level, st, a, b, now);
				break;
			}
		}
	}

	/** Resolves a clash between two virtual squads now (also used by tests and commands). Returns the stored record. */
	public static @Nullable Record resolve(ServerLevel level, KingdomState st, Squad a, Squad b, long now) {
		if (!a.virtual || !b.virtual || a.virtualMembers.isEmpty() || b.virtualMembers.isEmpty()) return null;
		long seed = seedFor(level, st, a, b);
		int na = a.virtualMembers.size(), nb = b.virtualMembers.size();
		Outcome o = roll(seed, power(a), power(b), na, nb);
		kill(a, o.lossesA(), seed ^ 0x51ED2701L);
		kill(b, o.lossesB(), seed ^ 0x2545F491L);
		Vec3 mid = a.anchor == null || b.anchor == null ? Vec3.ZERO : a.anchor.add(b.anchor).scale(0.5);
		Record rec = new Record(now, a.faction, b.faction, o.aWins() ? a.faction : b.faction, na, nb, o.lossesA(), o.lossesB(), seed, BlockPos.containing(mid));
		st.clashes.add(rec);
		while (st.clashes.size() > MAX_RECORDS) st.clashes.remove(0);
		st.clashCount++;
		COOLDOWN.put(a.id, now + COOLDOWN_TICKS);
		COOLDOWN.put(b.id, now + COOLDOWN_TICKS);
		for (Squad q : new Squad[] {a, b}) if (q.virtualMembers.isEmpty()) st.removeSquad(q);
		st.setDirty();
		resolved++;
		KingdomEvents.FACTION_CLASH.invoker().onClash(level, rec);
		return rec;
	}

	/** Removes {@code n} virtual soldiers (seeded choice) and keeps the squad consistent: a new leader if the leader fell. */
	private static void kill(Squad q, int n, long seed) {
		RandomSource r = RandomSource.create(mix(seed));
		for (int k = 0; k < n && !q.virtualMembers.isEmpty(); k++) {
			Squad.VirtualSoldier dead = q.virtualMembers.remove(r.nextInt(q.virtualMembers.size()));
			q.members.remove(dead.uuid());
			if (dead.uuid().equals(q.leader)) q.leader = null;
		}
		if (q.leader == null && !q.virtualMembers.isEmpty()) {
			Squad.VirtualSoldier next = q.virtualMembers.get(0);
			for (Squad.VirtualSoldier m : q.virtualMembers) if (Factions.unit(q.faction, m.unit()).officer()) {
				next = m;
				break;
			}
			q.leader = next.uuid();
			q.members.remove(next.uuid());
		}
	}
}
