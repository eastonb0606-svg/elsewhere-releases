package dev.elsewhere.kingdom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Faction registry (live state). A faction has a tabard colour, a "hostile to players" flag, an optional data
 * definition ({@link FactionDef}: roster, rulers, biomes...) and relations to every other faction.
 * <p><b>Relations:</b> each pair of factions is at {@link Stance#WAR} (soldiers fight each other) or at TRUCE, TENSION, PEACE
 * or ALLIED (soldiers ignore each other; TENSION and TRUCE are the states the rival-kingdom simulation will drive). The default is war (config {@code factions.defaultStance}),
 * a faction's JSON can list {@code allies} and {@code peace}, and {@link KingdomApi#setStance} changes a pair at runtime
 * (saved with the world).
 * <p>{@code elsewhere:aldermark} (blue) is defined by {@code data/elsewhere/kingdom_faction/aldermark.json}; {@code elsewhere:rebels}
 * (red, used by tests as a ready-made enemy) exists only in code. Both are hostile to players by default.
 * <p>Runtime changes made with {@link #setHostileToPlayers} are saved with the overworld's {@link KingdomState},
 * so a conquest/diplomacy system can flip them and they survive restarts.
 */
public final class Factions {
	private Factions() {}

	public static final Identifier ALDERMARK = KingdomContent.id("aldermark");
	public static final Identifier REBELS = KingdomContent.id("rebels");

	/** Relation between two factions. */
	public enum Stance {
		WAR, TRUCE, TENSION, PEACE, ALLIED;

		public static Stance parse(String s, Stance fallback) {
			if (s == null) return fallback;
			try {
				return valueOf(s.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return fallback;
			}
		}
	}

	/** One faction. {@code hostileToPlayers} is mutable through {@link Factions#setHostileToPlayers}. */
	public static final class Faction {
		public final Identifier id;
		public int tabardColor;
		boolean hostileToPlayers;
		final Set<Identifier> allies = new HashSet<>();
		/** From the definition's {@code allies} / {@code peace} lists. */
		final Set<Identifier> defAllies = new HashSet<>(), defPeace = new HashSet<>();
		@Nullable FactionDef def;
		/** Set for remnant factions: whose aggression ledger and unit roster this faction shares. */
		@Nullable Identifier parent;
		final Map<Identifier, UnitType> cache = new HashMap<>();

		Faction(Identifier id, int tabardColor, boolean hostileToPlayers) {
			this.id = id;
			this.tabardColor = tabardColor;
			this.hostileToPlayers = hostileToPlayers;
		}

		public boolean hostileToPlayers() {
			return hostileToPlayers;
		}

		/** The kingdom this remnant faction belongs to, or null. */
		public @Nullable Identifier parent() {
			return parent;
		}

		public @Nullable FactionDef def() {
			return def;
		}

		public Component displayName() {
			if (def == null) return Component.literal(id.getPath());
			return def.nameKey().<Component>map(Component::translatable).orElseGet(() -> Component.literal(def.name()));
		}
	}

	private static final Map<Identifier, Faction> FACTIONS = new LinkedHashMap<>();
	/** Defaults as registered in code or data, so saved overrides can be told apart. */
	static final Map<Identifier, Boolean> DEFAULT_HOSTILE = new LinkedHashMap<>();
	/** Runtime (saved) stance overrides, stored in both directions. */
	private static final Map<Identifier, Map<Identifier, Stance>> OVERRIDES = new HashMap<>();

	static void bootstrap() {
		register(ALDERMARK, 0x2E4C9E, true);
		register(REBELS, 0x9E2E2E, true);
	}

	/**
	 * Registers (or replaces) a faction. Call during mod init. {@code tabardColor} is 0xRRGGBB and tints the soldier's
	 * tabard; {@code hostileToPlayers} is the default player stance (a saved runtime override wins).
	 */
	public static Faction register(Identifier id, int tabardColor, boolean hostileToPlayers) {
		Faction f = new Faction(id, tabardColor, hostileToPlayers);
		Faction old = FACTIONS.put(id, f);
		if (old != null) {
			f.allies.addAll(old.allies);
			f.def = old.def;
			f.defAllies.addAll(old.defAllies);
			f.defPeace.addAll(old.defPeace);
			f.parent = old.parent;
		}
		DEFAULT_HOSTILE.put(id, hostileToPlayers);
		return f;
	}

	/**
	 * Registers a neutral remnant faction of {@code parent} (what is left of a fallen kingdom): same roster and the same
	 * aggression ledger as the parent, its own tabard colour, never hostile to players.
	 */
	public static Faction registerRemnant(Identifier id, Identifier parent, int tabardColor) {
		Faction f = FACTIONS.get(id);
		if (f == null) f = register(id, tabardColor, false);
		f.parent = parent;
		Faction p = FACTIONS.get(parent);
		f.def = p == null ? null : p.def;
		f.cache.clear();
		return f;
	}

	/** Removes a faction (tests that registered a throw-away faction). */
	static void unregister(Identifier id) {
		FACTIONS.remove(id);
		DEFAULT_HOSTILE.remove(id);
		OVERRIDES.remove(id);
		OVERRIDES.values().forEach(m -> m.remove(id));
	}

	/** Applies every loaded definition: creates missing factions, updates tabards, rosters and diplomacy lists. */
	static void syncDefs(Map<Identifier, FactionDef> defs) {
		for (Map.Entry<Identifier, FactionDef> e : defs.entrySet()) define(e.getKey(), e.getValue());
		for (Faction f : FACTIONS.values()) {
			if (f.parent != null) {
				Faction p = FACTIONS.get(f.parent);
				f.def = p == null ? null : p.def;
			}
			f.cache.clear();
		}
	}

	private static void define(Identifier id, FactionDef def) {
		Faction f = FACTIONS.get(id);
		if (f == null) f = register(id, def.tabard().color(), def.hostileToPlayers());
		else {
			Boolean oldDefault = DEFAULT_HOSTILE.get(id);
			if (oldDefault == null || oldDefault == f.hostileToPlayers) f.hostileToPlayers = def.hostileToPlayers();
			DEFAULT_HOSTILE.put(id, def.hostileToPlayers());
		}
		f.tabardColor = def.tabard().color();
		f.def = def;
		f.defAllies.clear();
		f.defAllies.addAll(def.allies());
		f.defPeace.clear();
		f.defPeace.addAll(def.peace());
		f.cache.clear();
	}

	/** The faction, or Aldermark when the id is unknown. */
	public static Faction get(Identifier id) {
		Faction f = FACTIONS.get(id);
		return f != null ? f : FACTIONS.get(ALDERMARK);
	}

	public static boolean exists(Identifier id) {
		return FACTIONS.containsKey(id);
	}

	public static Iterable<Faction> all() {
		return FACTIONS.values();
	}

	/** The data definition of a faction, or null. */
	public static @Nullable FactionDef def(Identifier id) {
		Faction f = FACTIONS.get(id);
		return f == null ? null : f.def;
	}

	/** The faction whose aggression ledger a soldier of {@code id} feeds (a remnant's parent, else itself). */
	public static Identifier ledgerOf(Identifier id) {
		Faction f = FACTIONS.get(id);
		return f != null && f.parent != null ? f.parent : id;
	}

	/**
	 * The unit type as this faction fields it: the base unit with the faction's roster entry (skin, armor, dye, hands,
	 * mount, trim, health scale, or a whole other unit) applied. Factions without a roster get the base unit itself.
	 */
	public static UnitType unit(Identifier faction, Identifier unitId) {
		Faction f = FACTIONS.get(faction);
		if (f == null || f.def == null || !f.def.reskins()) return UnitTypes.get(unitId);
		UnitType c = f.cache.get(unitId);
		if (c == null) {
			c = resolve(f.def, unitId);
			f.cache.put(unitId, c);
		}
		return c;
	}

	/**
	 * The unit id this faction actually fields for a requested base unit: the roster entry's {@code unit} redirect (a unique soldier such as a Berserker) or the unit itself.
	 * The entity type for a spawn is chosen from this id ({@link KingdomEntities#typeFor}), so a unit with its own entity class gets that class.
	 */
	public static Identifier effectiveUnitId(Identifier faction, Identifier unitId) {
		Faction f = FACTIONS.get(faction);
		if (f == null || f.def == null) return unitId;
		FactionDef.RosterEntry e = f.def.roster().get(unitId);
		return e != null && e.unit().isPresent() ? e.unit().get() : unitId;
	}

	static void clearUnitCaches() {
		for (Faction f : FACTIONS.values()) f.cache.clear();
	}

	private static UnitType resolve(FactionDef def, Identifier unitId) {
		FactionDef.RosterEntry e = def.roster().get(unitId);
		UnitType base = UnitTypes.get(e != null && e.unit().isPresent() ? e.unit().get() : unitId);
		Optional<UnitType.Trim> trim = base.trim();
		if (e != null && e.trim().isPresent()) trim = e.trim();
		else if (def.trim().isPresent() && base.trim().isPresent()) trim = def.trim();
		double hm = def.army().healthMultiplier(), sm = def.army().speedMultiplier(), dm = def.army().damageMultiplier();
		if (e == null && trim.equals(base.trim()) && hm == 1.0 && sm == 1.0 && dm == 1.0) return base;
		UnitType.Armor armor = base.armor();
		Identifier skin = base.skin();
		Optional<Integer> dye = base.armorDye();
		Optional<Identifier> main = base.mainHand(), off = base.offHand(), mount = base.mount(), mountArmor = base.mountArmor();
		UnitType.Stats stats = base.stats();
		if (e != null) {
			if (e.armor().isPresent()) {
				UnitType.Armor a = e.armor().get();
				armor = new UnitType.Armor(a.head().or(armor::head), a.chest().or(armor::chest), a.legs().or(armor::legs), a.feet().or(armor::feet));
			}
			skin = e.skin().orElse(skin);
			dye = e.armorDye().or(() -> base.armorDye());
			main = e.mainHand().or(() -> base.mainHand());
			off = e.offHand().or(() -> base.offHand());
			mount = e.mount().or(() -> base.mount());
			mountArmor = e.mountArmor().or(() -> base.mountArmor());
			if (e.healthScale().isPresent()) {
				UnitType.Stats s = base.stats();
				stats = new UnitType.Stats(s.health() * e.healthScale().get(), s.speed(), s.attackDamage(), s.followRange(), s.knockbackResistance(), s.armor(), s.armorToughness());
			}
		}
		if (hm != 1.0 || sm != 1.0 || dm != 1.0) stats = new UnitType.Stats(stats.health() * hm, stats.speed() * sm, stats.attackDamage() * dm, stats.followRange(), stats.knockbackResistance(), stats.armor(), stats.armorToughness());
		return new UnitType(base.role(), base.officer(), skin, armor, dye, main, off, mount, mountArmor, stats, base.combat(), base.ability(), trim, base.aggression());
	}

	/**
	 * Sets whether soldiers of this faction attack players on sight. When false they only fight players who attacked
	 * them (or their squad). Persisted when a server is running ({@link KingdomApi#setFactionHostileToPlayers}).
	 */
	public static void setHostileToPlayers(Identifier id, boolean hostile) {
		Faction f = FACTIONS.get(id);
		if (f != null) f.hostileToPlayers = hostile;
	}

	/** Makes two factions allies (or enemies again). Not persisted: call it from init code. */
	public static void setAllied(Identifier a, Identifier b, boolean allied) {
		Faction fa = FACTIONS.get(a), fb = FACTIONS.get(b);
		if (fa == null || fb == null) return;
		if (allied) {
			fa.allies.add(b);
			fb.allies.add(a);
		} else {
			fa.allies.remove(b);
			fb.allies.remove(a);
		}
	}

	// ---- relations -------------------------------------------------------------------------------------------------

	/** The relation between two factions (same faction: allied). Runtime override, then init-time allies, then the JSON lists, then the default (war). */
	public static Stance stance(Identifier a, Identifier b) {
		if (a.equals(b)) return Stance.ALLIED;
		if (!OVERRIDES.isEmpty()) {
			Map<Identifier, Stance> m = OVERRIDES.get(a);
			Stance o = m == null ? null : m.get(b);
			if (o != null) return o;
		}
		Faction fa = FACTIONS.get(a), fb = FACTIONS.get(b);
		if (fa == null) return KingdomConfig.defaultStance();
		if (fa.allies.contains(b)) return Stance.ALLIED;
		if (fa.defAllies.contains(b) || (fb != null && fb.defAllies.contains(a))) return Stance.ALLIED;
		if (fa.defPeace.contains(b) || (fb != null && fb.defPeace.contains(a))) return Stance.PEACE;
		return KingdomConfig.defaultStance();
	}

	/** Runtime stance for a pair; null removes the override. Not saved: use {@link KingdomApi#setStance}. */
	public static void setStance(Identifier a, Identifier b, @Nullable Stance s) {
		if (a.equals(b)) return;
		if (s == null) {
			Map<Identifier, Stance> m = OVERRIDES.get(a);
			if (m != null) m.remove(b);
			m = OVERRIDES.get(b);
			if (m != null) m.remove(a);
			return;
		}
		OVERRIDES.computeIfAbsent(a, k -> new HashMap<>()).put(b, s);
		OVERRIDES.computeIfAbsent(b, k -> new HashMap<>()).put(a, s);
	}

	/** Drops every runtime stance override (server stop). */
	static void clearStanceOverrides() {
		OVERRIDES.clear();
	}

	/** True when soldiers of {@code a} should fight soldiers of {@code b}. */
	public static boolean hostile(Identifier a, Identifier b) {
		if (a.equals(b)) return false;
		return stance(a, b) == Stance.WAR;
	}
}
