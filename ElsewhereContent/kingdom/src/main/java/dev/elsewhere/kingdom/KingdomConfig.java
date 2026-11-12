package dev.elsewhere.kingdom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server config {@code config/elsewhere-kingdom.json} (flat dotted keys, written with defaults on first start).
 * Keys follow Ideas/kingdom/KINGDOM.md section 2.6. All fields are public and may be changed at runtime
 * (the self-test does); call {@link #save()} to persist.
 */
public final class KingdomConfig {
	private KingdomConfig() {}

	/**
	 * Real soldiers per player (0.2.0 default 15 = 2 squads with 2 sergeants + 1 captain): the nearest 15 within
	 * {@link #activeRadius} run full AI, and spawns/materializations near a player stop at 15 per player there.
	 */
	public static int maxRealSoldiersPerPlayer = 15;
	/** Spawn cap: soldiers inside one 48-block area (0.2.0 default 30 = 2 captains with their 4 squads). */
	public static int maxRealSoldiersPerArea = 30;
	/** Spawn cap: loaded soldiers world-wide (all levels). */
	public static int maxRealSoldiersGlobal = 120;
	/** Spawn cap: mounted soldiers loaded at once. */
	public static int maxMounted = 8;
	/** Squad size cap (leader included). */
	public static int maxSquadSize = 16;
	/** Global A* path searches started per tick by soldiers. */
	public static int pathSearchesPerTick = 6;
	/** Leader repath interval, min/max ticks. */
	public static int leaderRepathMin = 10, leaderRepathMax = 20;
	/** A player within this many blocks makes soldiers "active" (full AI). */
	public static double activeRadius = 48.0;
	public static boolean useShields = true, useCrits = true, useFormations = true, useMorale = true;
	/** Multiplies all soldier melee damage. */
	public static double soldierDamageScale = 1.0;
	/** Chance that each member routs when its squad's officer dies. */
	public static double routChanceOnOfficerDeath = 0.5;
	/** Rout duration in ticks. */
	public static int routTicks = 200;
	/**
	 * When true and the vanilla {@code pvp} gamerule is false, soldiers never pick players as targets on their own
	 * (they still fight back when a player attacks them). The mod has no PvP config of its own; this is the hook.
	 */
	public static boolean respectPvpGamerule = false;
	/** Relation between two factions that have no explicit stance: "war" (default), "peace" or "allied". */
	public static String factionDefaultStance = "war";
	/** When true, squads of hostile factions that meet while no player is near resolve the fight as a seeded roll (no entities). */
	public static boolean abstractConflicts = true;
	/** 3.0 kill switch (RIVALS-DECISIONS 18): Hrafnholt raids near water arrive by a sailing longship. Off: the 2.1 beached-prop landing (16a) only. */
	public static boolean sailingLongships = true;

	public static Factions.Stance defaultStance() {
		return Factions.Stance.parse(factionDefaultStance, Factions.Stance.WAR);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("elsewhere-kingdom.json");
	}

	public static Map<String, Object> asMap() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("perf.maxRealSoldiersPerPlayer", maxRealSoldiersPerPlayer);
		m.put("perf.maxRealSoldiersPerArea", maxRealSoldiersPerArea);
		m.put("perf.maxRealSoldiersGlobal", maxRealSoldiersGlobal);
		m.put("perf.maxMounted", maxMounted);
		m.put("perf.maxSquadSize", maxSquadSize);
		m.put("perf.pathSearchesPerTick", pathSearchesPerTick);
		m.put("perf.leaderRepathMin", leaderRepathMin);
		m.put("perf.leaderRepathMax", leaderRepathMax);
		m.put("perf.activeRadius", activeRadius);
		m.put("combat.useShields", useShields);
		m.put("combat.useCrits", useCrits);
		m.put("combat.useFormations", useFormations);
		m.put("combat.useMorale", useMorale);
		m.put("combat.soldierDamageScale", soldierDamageScale);
		m.put("combat.respectPvpGamerule", respectPvpGamerule);
		m.put("morale.routChanceOnOfficerDeath", routChanceOnOfficerDeath);
		m.put("morale.routTicks", routTicks);
		m.put("factions.defaultStance", factionDefaultStance);
		m.put("factions.abstractConflicts", abstractConflicts);
		m.put("war.sailingLongships", sailingLongships);
		m.put("configVersion", 2);
		return m;
	}

	public static void load() {
		Path p = path();
		try {
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p)) {
					JsonObject o = GSON.fromJson(r, JsonObject.class);
					if (o != null) {
						apply(o);
						// 0.1.0 files carry the old 24/40 defaults: move them to the 0.2.0 caps (15 per player, 30 per area)
						if (!o.has("configVersion") && maxRealSoldiersPerPlayer == 24 && maxRealSoldiersPerArea == 40) {
							maxRealSoldiersPerPlayer = 15;
							maxRealSoldiersPerArea = 30;
						}
					}
				}
			}
			save();
		} catch (Exception e) {
			KingdomContent.LOG.warn("Could not read {} ({}); using defaults", p, e.toString());
		}
	}

	static void apply(JsonObject o) {
		maxRealSoldiersPerPlayer = i(o, "perf.maxRealSoldiersPerPlayer", maxRealSoldiersPerPlayer, 1, 500);
		maxRealSoldiersPerArea = i(o, "perf.maxRealSoldiersPerArea", maxRealSoldiersPerArea, 1, 1000);
		maxRealSoldiersGlobal = i(o, "perf.maxRealSoldiersGlobal", maxRealSoldiersGlobal, 1, 5000);
		maxMounted = i(o, "perf.maxMounted", maxMounted, 0, 500);
		maxSquadSize = i(o, "perf.maxSquadSize", maxSquadSize, 2, 64);
		pathSearchesPerTick = i(o, "perf.pathSearchesPerTick", pathSearchesPerTick, 1, 100);
		leaderRepathMin = i(o, "perf.leaderRepathMin", leaderRepathMin, 1, 200);
		leaderRepathMax = Math.max(leaderRepathMin, i(o, "perf.leaderRepathMax", leaderRepathMax, 1, 400));
		activeRadius = d(o, "perf.activeRadius", activeRadius, 8, 256);
		useShields = b(o, "combat.useShields", useShields);
		useCrits = b(o, "combat.useCrits", useCrits);
		useFormations = b(o, "combat.useFormations", useFormations);
		useMorale = b(o, "combat.useMorale", useMorale);
		soldierDamageScale = d(o, "combat.soldierDamageScale", soldierDamageScale, 0, 100);
		respectPvpGamerule = b(o, "combat.respectPvpGamerule", respectPvpGamerule);
		routChanceOnOfficerDeath = d(o, "morale.routChanceOnOfficerDeath", routChanceOnOfficerDeath, 0, 1);
		routTicks = i(o, "morale.routTicks", routTicks, 1, 6000);
		try {
			if (o.has("factions.defaultStance")) factionDefaultStance = o.get("factions.defaultStance").getAsString();
		} catch (Exception ignored) {
		}
		abstractConflicts = b(o, "factions.abstractConflicts", abstractConflicts);
		sailingLongships = b(o, "war.sailingLongships", sailingLongships);
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path())) {
				GSON.toJson(asMap(), w);
			}
		} catch (Exception e) {
			KingdomContent.LOG.warn("Could not write {}: {}", path(), e.toString());
		}
	}

	private static int i(JsonObject o, String k, int def, int lo, int hi) {
		try {
			return o.has(k) ? Math.clamp(o.get(k).getAsInt(), lo, hi) : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static double d(JsonObject o, String k, double def, double lo, double hi) {
		try {
			return o.has(k) ? Math.clamp(o.get(k).getAsDouble(), lo, hi) : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static boolean b(JsonObject o, String k, boolean def) {
		try {
			return o.has(k) ? o.get(k).getAsBoolean() : def;
		} catch (Exception e) {
			return def;
		}
	}
}
