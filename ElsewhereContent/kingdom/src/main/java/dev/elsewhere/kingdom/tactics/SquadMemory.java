package dev.elsewhere.kingdom.tactics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Short, bounded memory of one squad (at most 6 players, 12 counters, 12 recent events): FIELD-TACTICS 1.2. */
public final class SquadMemory {
	public static final int MAX_PLAYERS = 6, MAX_COUNTERS = 12, MAX_EVENTS = 12;

	public static final class PlayerFile {
		public Perception.LoadoutSummary loadout = Perception.LoadoutSummary.UNKNOWN;
		public long lastSeen;
		public int fightsWon, fightsLost;
		public final Map<String, Integer> tacticsSeen = new HashMap<>();
	}

	public final Map<UUID, PlayerFile> players = new LinkedHashMap<>();
	/** "pillar", "water", "boat", "pearl", "kite", "door"... to times seen in this encounter. */
	public final Map<String, Integer> tacticUses = new LinkedHashMap<>();
	public final Deque<Perception.ThreatEvent> recent = new ArrayDeque<>();
	public @Nullable Vec3 rallyPoint;
	public @Nullable BlockPos lastBadSpot;
	/** Last tick each tactic counter was incremented (so a long pillar stay counts once). */
	final Map<String, Long> lastCount = new HashMap<>();
	/** Contact id to the tick it was last seen and where (ghost handling). */
	final Map<UUID, Perception.Contact> lastSeen = new HashMap<>();
	/** Intelligence noise is sampled once per encounter per contact. */
	final Map<UUID, Double> noise = new HashMap<>();
	public long encounterStart = -1;

	public int uses(String tactic) {
		return tacticUses.getOrDefault(tactic, 0);
	}

	/** Counts a tactic seen now (at most once every 100 ticks per tactic). */
	public void saw(String tactic, long now) {
		Long last = lastCount.get(tactic);
		if (last != null && now - last < 100) return;
		if (!tacticUses.containsKey(tactic) && tacticUses.size() >= MAX_COUNTERS) return;
		lastCount.put(tactic, now);
		tacticUses.merge(tactic, 1, Integer::sum);
	}

	public void addEvent(Perception.ThreatEvent e) {
		recent.addLast(e);
		while (recent.size() > MAX_EVENTS) recent.removeFirst();
	}

	public PlayerFile player(UUID id) {
		PlayerFile f = players.get(id);
		if (f == null) {
			if (players.size() >= MAX_PLAYERS) players.remove(players.keySet().iterator().next());
			f = new PlayerFile();
			players.put(id, f);
		}
		return f;
	}

	/** Updates counters from a fresh perception: the cheese patterns of FIELD-TACTICS 1.5.2 (pillar, boat, water) are counted once per 100 ticks. */
	public void observe(Perception p, long now) {
		for (Perception.Contact c : p.contacts()) {
			if (!c.visibleNow() || c.kind() != Types.ContactKind.PLAYER) continue;
			if (c.lastSeen().y > p.squadCentre().y + 3 && c.onHighGround()) saw("pillar", now);
			if (c.inBoat()) saw("boat", now);
			else if (c.inWater()) saw("water", now);
			if (c.loadout() != null && c.loadout().pearlInHand()) saw("pearl", now);
		}
	}

	/** Forgets everything about the encounter when the squad has seen nobody for a while. */
	public void endEncounter() {
		tacticUses.clear();
		lastCount.clear();
		noise.clear();
		lastSeen.clear();
		encounterStart = -1;
	}
}
