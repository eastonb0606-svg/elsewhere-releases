package dev.elsewhere.kingdom_court.api;

import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Succession per faction (RIVALS-DECISIONS section 10; there is no generic seat refill). Rules, all keyed by faction:
 * <ul>
 * <li><b>Lord to ruler</b> (monarchies: Aldermark, Hrafnholt, Azmar): the highest living Lord takes the throne with a slight boost. A faction may name its new ruler
 * ({@link #registerRulerTitle}: Azmar's is the "Regent"). Dead Lords are never re-created.</li>
 * <li><b>Risen</b> (Hrafnholt, {@link #registerRisen}): after {@link #RISEN_DAYS} in-game days the realm's living Hird-Captain is promoted into the empty champion seat, once per
 * seat; no captain, no promotion. Stats: Hird-Captain plus 15 percent health and damage; drops at repeat-kill odds; a {@link Leaders.LeaderBoss} is told {@code onRisen(2)}
 * (only 2 of its signature moves).</li>
 * <li><b>Heirs</b> (Mirewick oligarchy): an heir of the same family (same surname, new first name, same slot and kit, inherits the vote leaning) sits after the file's
 * {@code seat_refill_days} (7); 2 heirs per family, so at most 3 holders per seat, then the seat is empty for good. Heirs count as repeat kills.</li>
 * <li>Azmar: a dead official's seat is never filled; the faction falls when all are dead.</li>
 * </ul>
 * Every succession fires {@link CourtEvents#SUCCESSION} (seat, kind).
 */
public final class Succession {
	private Succession() {}

	public static final int RISEN_DAYS = 10, MAX_HEIRS = 2;
	static final Identifier RISEN_HP = CourtContent.id("court_risen_hp"), RISEN_DMG = CourtContent.id("court_risen_dmg");
	static final Map<String, String> RISEN_SEATS = new ConcurrentHashMap<>(Map.of("elsewhere:hrafnholt", "chancellor"));
	static final Map<String, String> TITLES = new ConcurrentHashMap<>(Map.of("elsewhere:azmar", "Regent"));
	static final Map<String, List<String>> HEIR_NAMES = new ConcurrentHashMap<>();
	static final List<String> DEFAULT_FIRST = List.of("Pennick", "Marta", "Osbert", "Tamsin", "Jory", "Bettina", "Lambert", "Cecily", "Rufus", "Ines");

	/** The seat (slot key) a faction's Risen champion fills, or null for none. */
	public static void registerRisen(Identifier faction, @Nullable String seat) {
		if (seat == null) RISEN_SEATS.remove(faction.toString());
		else RISEN_SEATS.put(faction.toString(), seat);
	}

	public static void registerRulerTitle(Identifier faction, @Nullable String title) {
		if (title == null) TITLES.remove(faction.toString());
		else TITLES.put(faction.toString(), title);
	}

	public static @Nullable String rulerTitle(Identifier faction) {
		return TITLES.get(faction.toString());
	}

	/** First names an oligarchy's heirs draw from. */
	public static void registerHeirNames(Identifier faction, List<String> firstNames) {
		HEIR_NAMES.put(faction.toString(), List.copyOf(firstNames));
	}

	public static int generation(CourtState st, Ruler seat) {
		return (int) (long) st.ledger.getOrDefault("gen|" + seat.key, 0L);
	}

	public static boolean risen(CourtState st, Ruler seat) {
		return st.ledger.getOrDefault("risen|" + seat.key, 0L) == 1L;
	}

	public static boolean vacantForever(CourtState st, Ruler seat) {
		return st.ledger.getOrDefault("forever|" + seat.key, 0L) == 1L;
	}

	public static void fire(MinecraftServer server, Identifier faction, String seat, CourtEvents.SuccessionKind kind) {
		CourtEvents.SUCCESSION.invoker().onSuccession(server, faction, seat, kind);
		dev.elsewhere.kingdom.KingdomApi.successionEvent(server, faction, seat, kind.name().toLowerCase());
	}

	/** Surname of a holder name: the last word. */
	static String surname(String name) {
		int i = name.lastIndexOf(' ');
		return i < 0 ? name : name.substring(i + 1);
	}

	/** Called once a minute for every court that is not fallen. */
	public static void tick(ServerLevel level, CourtState st) {
		long day = level.getDefaultClockTime() / 24000L;
		if (st.oligarchy()) {
			var d = st.def();
			int wait = d == null ? 7 : Math.max(1, d.society().seatRefillDays());
			for (Ruler r : List.copyOf(st.dead)) {
				Long since = st.deadSince.get(r);
				if (since == null || day - since < wait || vacantForever(st, r)) continue;
				if (generation(st, r) >= MAX_HEIRS) {
					st.ledger.put("forever|" + r.key, 1L);
					st.touch();
					CourtLife.broadcast(level.getServer(), Component.literal("The " + dev.elsewhere.kingdom.Factions.get(st.faction).displayName().getString() + " seat of " + r.key + " stays empty for good: no heir is left."));
					fire(level.getServer(), st.faction, r.key, CourtEvents.SuccessionKind.VACANT_FOREVER);
					continue;
				}
				heir(level, st, r);
			}
		} else {
			String seat = RISEN_SEATS.get(st.faction.toString());
			Ruler r = seat == null ? null : Ruler.byKey(seat);
			if (r != null && st.slots().contains(r) && !st.alive(r) && !risen(st, r)) {
				Long since = st.deadSince.get(r);
				if (since != null && day - since >= RISEN_DAYS) promote(level, st, r);
			}
		}
	}

	/** An heir of the dead holder's family takes the seat (same surname, new first name; the leaning stays on the seat). */
	public static void heir(ServerLevel level, CourtState st, Ruler seat) {
		int gen = generation(st, seat) + 1;
		String old = st.holderName.get(seat);
		if (old == null) {
			var d = dev.elsewhere.kingdom_court.ruler.CourtRulers.def(st.faction, seat);
			old = d == null ? seat.key : d.name();
		}
		List<String> firsts = HEIR_NAMES.getOrDefault(st.faction.toString(), DEFAULT_FIRST);
		String name = firsts.get(Math.floorMod(seat.rank * 3 + gen, firsts.size())) + " " + surname(old);
		st.dead.remove(seat);
		st.deadSince.remove(seat);
		st.rulerIds.remove(seat);
		st.holderName.put(seat, name);
		st.ledger.put("gen|" + seat.key, (long) gen);
		st.refills++;
		st.vault.put(seat, 200L);
		st.touch();
		CourtLife.broadcast(level.getServer(), Component.literal(name + " takes the seat of the family on the council."));
		CourtEvents.SEAT_REFILLED.invoker().onSeatRefilled(level, st.faction, seat.key, name);
		CourtLife.brainRole(level.getServer(), st, seat, true);
		fire(level.getServer(), st.faction, seat.key, CourtEvents.SuccessionKind.HEIR);
	}

	/** The generic captain unit or a faction's own Hird-Captain ({@code elsewhere:hrafnholt_hird_captain}). */
	public static boolean isCaptainUnit(net.minecraft.resources.Identifier unit) {
		return unit != null && (UnitTypes.CAPTAIN.equals(unit) || unit.getPath().endsWith("hird_captain"));
	}

	/** Promotes the senior living Hird-Captain into the empty seat (once per seat). Returns the new leader or null. */
	public static @Nullable Mob promote(ServerLevel level, CourtState st, Ruler seat) {
		KingdomSoldier cap = null;
		for (KingdomSoldier s : KingdomPerf.loaded(level))
			if (s.isAlive() && st.faction.equals(s.factionId()) && isCaptainUnit(s.unitId()) && (cap == null || s.getHealth() > cap.getHealth())) cap = s;
		if (cap == null) return null;
		Vec3 at = cap.position();
		double capHp = cap.getMaxHealth();   // RIVALS-DECISIONS 14: a Risen champion is the Hird-Captain's health (from the unit data) plus 15 percent
		cap.discard();
		st.dead.remove(seat);
		st.deadSince.remove(seat);
		st.rulerIds.remove(seat);
		st.ledger.put("risen|" + seat.key, 1L);
		st.touch();
		Mob m = CourtLife.spawnLeader(level, st, seat, at, null);
		if (m == null) return null;
		var hp = m.getAttribute(Attributes.MAX_HEALTH);
		var dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (hp != null) {
			hp.addPermanentModifier(new AttributeModifier(RISEN_HP, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			HpCap.clamp(m);
			m.setHealth(m.getMaxHealth());
		}
		if (dmg != null) dmg.addPermanentModifier(new AttributeModifier(RISEN_DMG, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		m.addTag("court_risen");
		if (m instanceof Leaders.LeaderBoss lb) lb.onRisen(2);
		if (hp != null && capHp > 0) {
			hp.setBaseValue(capHp);
			HpCap.clamp(m);
			m.setHealth(m.getMaxHealth());
		}
		CourtLife.broadcast(level.getServer(), Component.literal("A Hird-Captain has risen to the empty champion's seat."));
		CourtLife.brainRole(level.getServer(), st, seat, true);
		fire(level.getServer(), st.faction, seat.key, CourtEvents.SuccessionKind.RISEN);
		return m;
	}

	/** A Lord took the throne: names the Regent-style title and fires the event. */
	public static void rulerSucceeded(ServerLevel level, CourtState st, Ruler newRuler) {
		String title = rulerTitle(st.faction);
		UUID_BLOCK:
		{
			var id = st.rulerIds.get(newRuler);
			Entity e = id == null ? null : level.getEntity(id);
			if (title != null && e != null) {
				var d = dev.elsewhere.kingdom_court.ruler.CourtRulers.def(st.faction, newRuler);
				e.setCustomName(Component.literal(title + " " + (d == null ? newRuler.key : surname(d.name()))));
			}
		}
		fire(level.getServer(), st.faction, newRuler.key, CourtEvents.SuccessionKind.RULER_SUCCESSION);
	}
}
