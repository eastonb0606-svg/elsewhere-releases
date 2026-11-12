package dev.elsewhere.kingdom_court;

import com.mojang.serialization.Codec;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import net.minecraft.resources.Identifier;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The court's world state (overworld data storage, {@code data/elsewhere/kingdom_court.dat}): the realm site, which
 * rulers are dead, who wears the crown, the roaming Lord, the garrison posts and whether the kingdom has fallen.
 */
public final class CourtState extends SavedData {
	public static final Codec<CourtState> CODEC = CompoundTag.CODEC.xmap(CourtState::load, CourtState::save);
	public static final SavedDataType<CourtState> TYPE = new SavedDataType<>(CourtContent.id("kingdom_court"), CourtState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Which faction this court belongs to (Aldermark's state is the 2.0 one, stored in its own data file; the others live in {@link CourtRealms}). */
	public Identifier faction = Factions.ALDERMARK;
	/** Game day (default clock / 24000) on which each dead ruler died, for the oligarchy's seat refill. */
	public final Map<Ruler, Long> deadSince = new EnumMap<>(Ruler.class);
	/** Seats refilled by new families so far (oligarchy). */
	public int refills;
	/** Container to dirty as well (non-Aldermark states). */
	@Nullable SavedData owner;
	public boolean siteChosen;
	/** Direction (radians, atan2(dz, dx)) from the castle towards the water for waterfront realms (Hrafnholt); NaN when none. Saved with the site. */
	public double waterBearing = Double.NaN;
	/** Blocks from the castle centre to the shore along {@link #waterBearing} (0 = no waterfront). */
	public int waterDist;
	public @Nullable BlockPos site;
	public boolean built;
	public boolean fallen;
	public final Set<Ruler> dead = new HashSet<>();
	public final Map<Ruler, UUID> rulerIds = new EnumMap<>(Ruler.class);
	/** The ruler on the throne (the King, then the highest living Lord). */
	public Ruler crowned = Ruler.KING;
	public @Nullable Ruler roaming;
	public long roamingSince;
	/** Post id -> squad id. */
	public final Map<String, UUID> postSquads = new HashMap<>();
	public final Map<String, Long> postDay = new HashMap<>();
	public boolean civiliansSpawned;
	public int sleepers;
	public int lastPhase = -1;
	/** Player -> planted Sovereign's Standard (packed BlockPos). */
	public final Map<UUID, Long> standards = new HashMap<>();

	// ---- 2.1 oligarchy and per-faction ledgers (new fields; absent in old saves and defaulted) ----------------------------
	/** Magnate vaults (slot -> coins in the faction's currency). The Chair is the living Magnate with the largest vault. */
	public final Map<Ruler, Long> vault = new EnumMap<>(Ruler.class);
	/** Game tick (default clock) until which the Chair is vacant after the Chair died; -1 = no vacancy. */
	public long chairVacantUntil = -1;
	/** Chair term index the current Chair was elected in. */
	public long chairTerm = -1;
	/** Council leaning per Magnate: 0 hawk, 1 dove, 2 miser. */
	public final Map<Ruler, Integer> leaning = new EnumMap<>(Ruler.class);
	/** Bribes: slot -> "week:step" (step -1/+1 along dove-miser-hawk). One per week per player is checked in the ledger; this is the effect. */
	public final Map<Ruler, String> bribes = new EnumMap<>(Ruler.class);
	/** Name of the family that bought a refilled seat (slot -> name). */
	public final Map<Ruler, String> holderName = new EnumMap<>(Ruler.class);
	/** True once the Republic ended in Dark Lanterns (all leaders dead inside the refill window). */
	public boolean darkLanterns;
	/** Players already paid a duel-right drop this week, and the tribute and trade ledgers: key "player|kind" -> value. */
	public final Map<String, Long> ledger = new HashMap<>();

	public CourtState() {}

	/** Aldermark's court (the 2.0 state). */
	public static CourtState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The court of {@code faction} (created empty on first use; use {@link #peek} to avoid creating one). */
	public static CourtState get(MinecraftServer server, Identifier faction) {
		if (Factions.ALDERMARK.equals(faction)) return get(server);
		return CourtRealms.get(server).state(faction, true);
	}

	public static @Nullable CourtState peek(MinecraftServer server, Identifier faction) {
		if (Factions.ALDERMARK.equals(faction)) return get(server);
		return CourtRealms.get(server).state(faction, false);
	}

	public static CourtState get(ServerLevel level, Identifier faction) {
		return get(level.getServer(), faction);
	}

	/** True when {@code faction} has a court that has fallen. */
	public static boolean isFallen(MinecraftServer server, Identifier faction) {
		CourtState st = peek(server, faction);
		return st != null && st.fallen;
	}

	public @Nullable FactionDef def() {
		return Factions.def(faction);
	}

	/** The leader slots of this faction in rank order (every slot when the faction has no data definition). */
	public List<Ruler> slots() {
		FactionDef d = def();
		List<Ruler> out = new ArrayList<>();
		if (d == null || d.rulers().isEmpty()) {
			for (Ruler r : Ruler.values()) out.add(r);
			return out;
		}
		for (String k : d.leaderSlots()) {
			Ruler r = Ruler.byKey(k);
			if (r != null && !out.contains(r)) out.add(r);
		}
		out.sort((a, b) -> Integer.compare(a.rank, b.rank));
		return out;
	}

	/** The Lords (or Magnates): every slot but the King, in rank order. */
	public List<Ruler> lords() {
		List<Ruler> out = new ArrayList<>(slots());
		out.remove(Ruler.KING);
		return out;
	}

	public boolean oligarchy() {
		FactionDef d = def();
		return d != null && d.society().government() == FactionDef.Government.OLIGARCHY;
	}

	public static CourtState get(ServerLevel level) {
		return get(level.getServer());
	}

	/** True when {@code r} holds the ruler bonus: the monarch (King slot) or, in an oligarchy, the seated Chair (none during the vacancy). */
	public boolean isHolder(Ruler r) {
		return oligarchy() ? crowned == r && chairVacantUntil < 0 && alive(r) : r == Ruler.KING;
	}

	public boolean alive(Ruler r) {
		return !dead.contains(r);
	}

	public int livingLords() {
		int n = 0;
		for (Ruler r : lords()) if (alive(r)) n++;
		return n;
	}

	public void touch() {
		setDirty();
		if (owner != null) owner.setDirty();
	}

	public static CourtState load(CompoundTag t) {
		CourtState s = new CourtState();
		s.siteChosen = t.getBooleanOr("site_chosen", false);
		s.waterBearing = t.getDoubleOr("water_bearing", Double.NaN);
		s.waterDist = t.getIntOr("water_dist", 0);
		long site = t.getLongOr("site", Long.MIN_VALUE);
		s.site = site == Long.MIN_VALUE ? null : BlockPos.of(site);
		s.built = t.getBooleanOr("built", false);
		s.fallen = t.getBooleanOr("fallen", false);
		for (String k : t.getStringOr("dead", "").split(",")) {
			Ruler r = Ruler.byKey(k);
			if (r != null) s.dead.add(r);
		}
		for (Ruler r : Ruler.values()) {
			String u = t.getStringOr("id_" + r.key, "");
			if (!u.isEmpty()) s.rulerIds.put(r, UUID.fromString(u));
		}
		Ruler c = Ruler.byKey(t.getStringOr("crowned", "king"));
		s.crowned = c == null ? Ruler.KING : c;
		s.roaming = Ruler.byKey(t.getStringOr("roaming", ""));
		s.roamingSince = t.getLongOr("roaming_since", 0L);
		for (String e : t.getStringOr("posts", "").split(";")) {
			String[] p = e.split("=");
			if (p.length == 3) {
				s.postSquads.put(p[0], UUID.fromString(p[1]));
				s.postDay.put(p[0], Long.parseLong(p[2]));
			}
		}
		s.civiliansSpawned = t.getBooleanOr("civilians", false);
		s.sleepers = t.getIntOr("sleepers", 0);
		s.lastPhase = t.getIntOr("last_phase", -1);
		Identifier f = Identifier.tryParse(t.getStringOr("faction", Factions.ALDERMARK.toString()));
		s.faction = f == null ? Factions.ALDERMARK : f;
		s.refills = t.getIntOr("refills", 0);
		for (String e : t.getStringOr("dead_since", "").split(",")) {
			String[] p = e.split("=");
			Ruler r = p.length == 2 ? Ruler.byKey(p[0]) : null;
			if (r != null) s.deadSince.put(r, Long.parseLong(p[1]));
		}
		for (String e : t.getStringOr("standards", "").split(";")) {
			String[] p = e.split("=");
			if (p.length == 2) s.standards.put(UUID.fromString(p[0]), Long.parseLong(p[1]));
		}
		s.chairVacantUntil = t.getLongOr("chair_vacant_until", -1L);
		s.chairTerm = t.getLongOr("chair_term", -1L);
		s.darkLanterns = t.getBooleanOr("dark_lanterns", false);
		readLedger(t.getStringOr("vault", ""), (k, v) -> { Ruler r = Ruler.byKey(k); if (r != null) s.vault.put(r, Long.parseLong(v)); });
		readLedger(t.getStringOr("leaning", ""), (k, v) -> { Ruler r = Ruler.byKey(k); if (r != null) s.leaning.put(r, Integer.parseInt(v)); });
		readLedger(t.getStringOr("bribes", ""), (k, v) -> { Ruler r = Ruler.byKey(k); if (r != null) s.bribes.put(r, v); });
		readLedger(t.getStringOr("holders", ""), (k, v) -> { Ruler r = Ruler.byKey(k); if (r != null) s.holderName.put(r, v.replace('\u00a6', ';')); });
		var lt = t.getCompoundOrEmpty("ledger_v2");
		for (String k : lt.keySet()) s.ledger.put(k, lt.getLongOr(k, 0L));
		return s;
	}

	static void readLedger(String raw, java.util.function.BiConsumer<String, String> each) {
		if (raw.isEmpty()) return;
		for (String e : raw.split(";")) {
			int i = e.lastIndexOf('=');
			if (i > 0) each.accept(e.substring(0, i), e.substring(i + 1));
		}
	}

	static <K, V> String writeLedger(Map<K, V> m, java.util.function.Function<K, String> key) {
		StringBuilder b = new StringBuilder();
		m.forEach((k, v) -> b.append(b.isEmpty() ? "" : ";").append(key.apply(k)).append('=').append(v.toString().replace(';', '\u00a6')));
		return b.toString();
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putBoolean("site_chosen", siteChosen);
		if (!Double.isNaN(waterBearing)) t.putDouble("water_bearing", waterBearing);
		if (waterDist > 0) t.putInt("water_dist", waterDist);
		if (site != null) t.putLong("site", site.asLong());
		t.putBoolean("built", built);
		t.putBoolean("fallen", fallen);
		StringBuilder d = new StringBuilder();
		for (Ruler r : dead) d.append(d.isEmpty() ? "" : ",").append(r.key);
		t.putString("dead", d.toString());
		rulerIds.forEach((r, u) -> t.putString("id_" + r.key, u.toString()));
		t.putString("crowned", crowned.key);
		t.putString("roaming", roaming == null ? "" : roaming.key);
		t.putLong("roaming_since", roamingSince);
		StringBuilder p = new StringBuilder();
		postSquads.forEach((k, v) -> p.append(p.isEmpty() ? "" : ";").append(k).append('=').append(v).append('=').append(postDay.getOrDefault(k, 0L)));
		t.putString("posts", p.toString());
		t.putBoolean("civilians", civiliansSpawned);
		t.putInt("sleepers", sleepers);
		t.putInt("last_phase", lastPhase);
		t.putString("faction", faction.toString());
		t.putInt("refills", refills);
		StringBuilder ds = new StringBuilder();
		deadSince.forEach((r, dd) -> ds.append(ds.isEmpty() ? "" : ",").append(r.key).append((char) 61).append(dd));
		t.putString("dead_since", ds.toString());
		StringBuilder st = new StringBuilder();
		standards.forEach((k, v) -> st.append(st.isEmpty() ? "" : ";").append(k).append('=').append(v));
		t.putString("standards", st.toString());
		t.putLong("chair_vacant_until", chairVacantUntil);
		t.putLong("chair_term", chairTerm);
		t.putBoolean("dark_lanterns", darkLanterns);
		t.putString("vault", writeLedger(vault, r -> r.key));
		t.putString("leaning", writeLedger(leaning, r -> r.key));
		t.putString("bribes", writeLedger(bribes, r -> r.key));
		t.putString("holders", writeLedger(holderName, r -> r.key));
		var lt = new CompoundTag();
		ledger.forEach(lt::putLong); // a compound, not one string: the per-player kill and shop ledgers outgrow the 65,535-byte NBT string limit
		t.put("ledger_v2", lt);
		return t;
	}

	/** Test hook: back to a fresh kingdom (site kept). */
	public void resetForTests() {
		dead.clear();
		deadSince.clear();
		refills = 0;
		rulerIds.clear();
		crowned = Ruler.KING;
		roaming = null;
		fallen = false;
		postSquads.clear();
		postDay.clear();
		civiliansSpawned = false;
		sleepers = 0;
		lastPhase = -1;
		vault.clear();
		leaning.clear();
		bribes.clear();
		holderName.clear();
		ledger.clear();
		chairVacantUntil = -1;
		chairTerm = -1;
		darkLanterns = false;
		setDirty();
	}
}
