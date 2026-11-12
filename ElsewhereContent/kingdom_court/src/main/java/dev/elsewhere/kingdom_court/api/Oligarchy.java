package dev.elsewhere.kingdom_court.api;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.ruler.CourtRulers;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The oligarchy (Mirewick's Lantern Council, RIVALS-DECISIONS 5). A faction whose file says {@code society.government = oligarchy}
 * has no King: its {@code society.leaders} are Magnates, and the living Magnate with the largest vault holds the Chair (First Lantern),
 * re-checked every {@code chair_days} (a week). The Chair gets the ruler bonus. When the Chair dies nobody is crowned: the Chair is
 * vacant for one in-game day, then the largest vault takes it. A dead seat is bought by a new family after {@code seat_refill_days}
 * (7): a new name, the same slot and kit. All leaders dead before any seat is refilled = Dark Lanterns, the faction falls for good.
 * Events are in {@link CourtEvents}: CHAIR_CHANGED, COUNCIL_VOTE, SEAT_REFILLED, DARK_LANTERNS.
 */
public final class Oligarchy {
	private Oligarchy() {}

	public static final int BRIBE_COST = 40;
	public static final long DAY = 24000L;

	public enum Topic { WAR, PEACE, ALLIANCE, HIRE }

	public enum Leaning { HAWK, DOVE, MISER }

	/** {@code yes}, {@code no}, {@code abstain} counts of living Magnates; {@code passes} = more yes than no; {@code delayDays} 1 (clear) or 2 (split) days before the decision takes effect; {@code byMagnate} slot key to -1/0/+1. */
	public record VoteResult(int yes, int no, int abstain, boolean passes, int delayDays, Map<String, Integer> byMagnate) {}

	public enum BribeResult { OK, NOT_FRIENDLY, NO_SUCH_MAGNATE, DEAD, ALREADY_THIS_WEEK, CANT_AFFORD, NOT_OLIGARCHY }

	static final Map<String, List<String>> FAMILY_NAMES = new ConcurrentHashMap<>();
	static final List<String> DEFAULT_NAMES = List.of("Pennick Dray", "Marta Quill", "Osbert Fenwarden", "Tamsin Saltmarsh", "Jory Oakes", "Bettina Vane",
		"Lambert Hollis", "Cecily Marrowby", "Rufus Tallow", "Ines Brightwater");

	/** Replaces the list of new-family names a faction's refilled seats draw from (in order, then wrapping). */
	public static void registerFamilyNames(Identifier faction, List<String> names) {
		FAMILY_NAMES.put(faction.toString(), List.copyOf(names));
	}

	public static String familyName(Identifier faction, int n) {
		List<String> l = FAMILY_NAMES.getOrDefault(faction.toString(), DEFAULT_NAMES);
		return l.get(Math.floorMod(n, l.size()));
	}

	/** Sets the default leanings of a faction's Magnates in slot order (before any {@link #setLeaning}); applied the first time they are read. */
	static final Map<String, List<Leaning>> DEFAULT_LEANINGS = new ConcurrentHashMap<>();

	public static void registerLeanings(Identifier faction, List<Leaning> inSlotOrder) {
		DEFAULT_LEANINGS.put(faction.toString(), List.copyOf(inSlotOrder));
	}

	// ---- vaults -------------------------------------------------------------------------------------------------------

	static void ensure(CourtState st) {
		List<Ruler> slots = st.slots();
		List<Leaning> def = DEFAULT_LEANINGS.get(st.faction.toString());
		for (int i = 0; i < slots.size(); i++) {
			Ruler r = slots.get(i);
			if (!st.vault.containsKey(r)) st.vault.put(r, 300L + Math.floorMod((long) st.faction.hashCode() * 31 + r.rank * 97L, 700L));
			if (!st.leaning.containsKey(r)) {
				Leaning l = def != null && i < def.size() ? def.get(i) : Leaning.values()[(i + 1) % 3];
				st.leaning.put(r, l.ordinal());
			}
		}
	}

	public static long vault(MinecraftServer server, Identifier faction, Ruler slot) {
		CourtState st = CourtState.get(server, faction);
		ensure(st);
		return st.vault.getOrDefault(slot, 0L);
	}

	public static void addVault(MinecraftServer server, Identifier faction, Ruler slot, long delta) {
		CourtState st = CourtState.get(server, faction);
		ensure(st);
		st.vault.merge(slot, delta, (a, b) -> Math.max(0L, a + b));
		st.touch();
	}

	public static void setLeaning(MinecraftServer server, Identifier faction, Ruler slot, Leaning l) {
		CourtState st = CourtState.get(server, faction);
		ensure(st);
		st.leaning.put(slot, l.ordinal());
		st.touch();
	}

	public static Leaning leaning(MinecraftServer server, Identifier faction, Ruler slot) {
		CourtState st = CourtState.get(server, faction);
		ensure(st);
		return Leaning.values()[st.leaning.getOrDefault(slot, 0)];
	}

	/** The living Magnate with the largest vault (ties: the higher rank), or null when none lives. */
	public static @Nullable Ruler largestVault(CourtState st) {
		ensure(st);
		Ruler best = null;
		for (Ruler r : st.slots()) {
			if (!st.alive(r)) continue;
			if (best == null || st.vault.getOrDefault(r, 0L) > st.vault.getOrDefault(best, 0L)) best = r;
		}
		return best;
	}

	/** The seated Chair's slot key, or null while the Chair is vacant (or the faction is not an oligarchy). */
	public static @Nullable String chair(MinecraftServer server, Identifier faction) {
		CourtState st = CourtState.peek(server, faction);
		if (st == null || !st.oligarchy() || st.chairVacantUntil >= 0 || !st.alive(st.crowned) || !st.slots().contains(st.crowned)) return null;
		return st.crowned.key;
	}

	// ---- votes and bribes ----------------------------------------------------------------------------------------------

	/** -1 no, 0 abstain, +1 yes for a leaning on a topic. */
	public static int stance(Leaning l, Topic t) {
		return switch (t) {
			case WAR -> l == Leaning.HAWK ? 1 : -1;
			case PEACE -> l == Leaning.HAWK ? -1 : 1;
			case ALLIANCE -> l == Leaning.MISER ? -1 : 1;
			case HIRE -> l == Leaning.DOVE ? -1 : 1;
		};
	}

	static long weekOf(ServerLevel level) {
		return level.getDefaultClockTime() / DAY / 7;
	}

	/** Pure tally of a vote now (does not fire the event); dead Magnates have no vote; this week's bribes shift one stance a step. */
	public static VoteResult tally(MinecraftServer server, Identifier faction, Topic topic) {
		CourtState st = CourtState.get(server, faction);
		ensure(st);
		long week = weekOf(server.overworld());
		int yes = 0, no = 0, ab = 0;
		Map<String, Integer> by = new LinkedHashMap<>();
		for (Ruler r : st.slots()) {
			if (!st.alive(r)) continue;
			int s = stance(Leaning.values()[st.leaning.getOrDefault(r, 0)], topic);
			String b = st.bribes.get(r);
			if (b != null) {
				String[] p = b.split(":");
				if (p.length == 3 && Long.parseLong(p[0]) == week && p[1].equals(topic.name())) s = Math.clamp(s + Integer.parseInt(p[2]), -1, 1);
			}
			by.put(r.key, s);
			if (s > 0) yes++;
			else if (s < 0) no++;
			else ab++;
		}
		boolean split = yes > 0 && no > 0;
		return new VoteResult(yes, no, ab, yes > no, split ? 2 : 1, by);
	}

	/** Holds a council vote (war, peace, alliance, hiring) and fires {@link CourtEvents#COUNCIL_VOTE}. Every war decision waits {@code delayDays}. */
	public static VoteResult vote(MinecraftServer server, Identifier faction, Topic topic) {
		VoteResult r = tally(server, faction, topic);
		CourtEvents.COUNCIL_VOTE.invoker().onVote(server, faction, topic, r);
		return r;
	}

	/**
	 * A Friendly-or-better player pays {@link #BRIBE_COST} of the faction's currency to move one living Magnate's vote on {@code topic} one step in
	 * {@code direction} (+1 towards yes, -1 towards no) for this week. One bribe per player per week.
	 */
	public static BribeResult bribe(ServerPlayer p, Identifier faction, Ruler slot, Topic topic, int direction) {
		ServerLevel level = (ServerLevel) p.level();
		CourtState st = CourtState.get(level, faction);
		if (!st.oligarchy()) return BribeResult.NOT_OLIGARCHY;
		if (!st.slots().contains(slot)) return BribeResult.NO_SUCH_MAGNATE;
		if (!st.alive(slot)) return BribeResult.DEAD;
		Standing.Band b = Standing.bandOf(level, p.getUUID(), faction);
		if (b != Standing.Band.FRIENDLY && b != Standing.Band.ALLIED) return BribeResult.NOT_FRIENDLY;
		String key = "bribe|" + p.getUUID();
		long week = weekOf(level);
		if (st.ledger.getOrDefault(key, -1L) == week) return BribeResult.ALREADY_THIS_WEEK;
		if (!Shops.take(p, faction, BRIBE_COST)) return BribeResult.CANT_AFFORD;
		st.ledger.put(key, week);
		st.bribes.put(slot, week + ":" + topic.name() + ":" + (direction >= 0 ? 1 : -1));
		addVault(level.getServer(), faction, slot, BRIBE_COST);
		st.touch();
		return BribeResult.OK;
	}

	// ---- the council's clock (called by the court once a minute) ---------------------------------------------------------

	public static void tick(ServerLevel level, CourtState st) {
		ensure(st);
		long now = level.getDefaultClockTime();
		long day = now / DAY;
		// daily income: each living Magnate's vault grows
		long last = st.ledger.getOrDefault("income_day", day);
		for (long d = last; d < day && d < last + 14; d++)
			for (Ruler r : st.slots()) if (st.alive(r)) st.vault.merge(r, 40L + Math.floorMod(st.faction.hashCode() + r.rank * 31L + d, 80L), Long::sum);
		if (last != day) {
			st.ledger.put("income_day", day);
			st.touch();
		}
		Succession.tick(level, st); // heirs of the same family (decision 10), never a new family
		if (st.chairVacantUntil >= 0) {
			if (now < st.chairVacantUntil) return;
			st.chairVacantUntil = -1;
			elect(level, st, CourtEvents.ChairReason.VACANCY_ENDED, true);
			return;
		}
		elect(level, st, CourtEvents.ChairReason.VAULT_WEEK, false);
	}

	/** Seats the living Magnate with the largest vault; {@code force} re-elects even within the term. */
	public static void elect(ServerLevel level, CourtState st, CourtEvents.ChairReason reason, boolean force) {
		Ruler best = largestVault(st);
		if (best == null) return;
		var dd = st.def();
		long days = dd == null ? 7 : Math.max(1, dd.society().chairDays());
		long term = level.getDefaultClockTime() / DAY / days;
		boolean seated = st.slots().contains(st.crowned) && st.alive(st.crowned);
		if (!force && seated && st.chairTerm == term) return;
		Ruler old = st.slots().contains(st.crowned) ? st.crowned : null;
		st.chairTerm = term;
		if (seated && !force && old == best) {
			st.touch();
			return;
		}
		st.crowned = best;
		st.touch();
		if (old != best || reason == CourtEvents.ChairReason.VACANCY_ENDED) {
			CourtLife.broadcast(level.getServer(), Component.translatable("message.elsewhere.court_succession", CourtRulers.displayName(st.faction, best)));
			CourtEvents.CHAIR_CHANGED.invoker().onChairChanged(level, st.faction, old == null ? null : old.key, best.key, old == null && reason == CourtEvents.ChairReason.VAULT_WEEK ? CourtEvents.ChairReason.STARTED : reason);
		}
		CourtLife.refreshAll(level, st);
	}

	/** The Chair died: nobody is crowned, the Chair is vacant for one day. */
	public static void chairDied(ServerLevel level, CourtState st, Ruler who) {
		st.chairVacantUntil = level.getDefaultClockTime() + DAY;
		st.touch();
		CourtEvents.CHAIR_CHANGED.invoker().onChairChanged(level, st.faction, who.key, null, CourtEvents.ChairReason.CHAIR_DIED);
	}

	public static List<String> livingMagnates(CourtState st) {
		List<String> out = new ArrayList<>();
		for (Ruler r : st.slots()) if (st.alive(r)) out.add(r.key);
		return out;
	}
}
