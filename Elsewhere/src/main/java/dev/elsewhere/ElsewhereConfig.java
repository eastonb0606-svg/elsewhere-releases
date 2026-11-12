package dev.elsewhere;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/elsewhere.json. Missing keys fall back to the defaults below; the file is rewritten with every key on load
 * so server owners can see what exists.
 */
public final class ElsewhereConfig {
	public static final class AccidentalTrips {
		public boolean enabled = true;
		/** Multiplies every trigger chance. 0 turns accidental trips off, 2 doubles them. */
		public double scale = 1.0;
		public double cooldownHours = 2.0;
		public double minPlaytimeMinutes = 60.0;
		public double pityHours = 8.0;
		public double homeRadius = 128.0;
		/** Familiarity damping: chance x max(floor, 0.5^(hoursInRegion / halfLifeHours)) per 512x512 region. */
		public double familiarityHalfLifeHours = 0.5;
		public double familiarityFloor = 0.03;
		public int regionSize = 512;
		/** Seconds of warning ("the pull") before an accidental trip; damage cancels it. */
		public double pullSeconds = 3.0;
		public double pityMultiplier = 2.0;
		/** BALANCE.md T1: the place multiplier also applies to ACTIVITY triggers (on top of their budgets). */
		public boolean dampActivityTriggers = true;
		/** Base chance per trigger id. Content modules add their own ids; any id can be set to 0. */
		public Map<String, Double> triggers = new LinkedHashMap<>();
		/** Activity triggers: how many events per hour of playtime may roll at all. */
		public Map<String, Integer> hourlyBudgets = new LinkedHashMap<>();
	}

	public static final class Wayfinders {
		public double cooldownSeconds = 60.0;
		public double riftOpenSeconds = 3.0;
		public double riftLingerSeconds = 10.0;
	}

	public static final class Bosses {
		/** Extra max health per additional player inside the arena when the fight starts. */
		public double partyHealthPerPlayer = 0.5;
		public double overpowerThreshold = 24.0;
		public double lethalHitCap = 0.6;
		public int arenaLeaveSeconds = 30;
	}

	/**
	 * Vanilla-friendly server switch (0.4.0, was a beta overlay): false turns off the Overworld boss sites (and their
	 * site-only mobs; overworld_bosses reads this field) and every accidental trip. Wayfinders and the dimensions still work.
	 */
	public boolean overworldContent = true;
	public AccidentalTrips accidentalTrips = new AccidentalTrips();
	public Wayfinders wayfinders = new Wayfinders();
	public Bosses bosses = new Bosses();

	private static ElsewhereConfig instance = new ElsewhereConfig();

	public static ElsewhereConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("elsewhere.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ElsewhereConfig loaded = null;
		if (Files.exists(path)) {
			try {
				loaded = gson.fromJson(Files.readString(path), ElsewhereConfig.class);
			} catch (Exception e) {
				Elsewhere.LOG.warn("Could not read {}, using defaults: {}", path, e.toString());
			}
		}
		instance = loaded == null ? new ElsewhereConfig() : loaded;
		instance.fillDefaults();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException e) {
			Elsewhere.LOG.warn("Could not write {}: {}", path, e.toString());
		}
	}

	private void fillDefaults() {
		if (accidentalTrips == null) {
			accidentalTrips = new AccidentalTrips();
		}
		if (wayfinders == null) {
			wayfinders = new Wayfinders();
		}
		if (bosses == null) {
			bosses = new Bosses();
		}
		if (accidentalTrips.triggers == null) {
			accidentalTrips.triggers = new LinkedHashMap<>();
		}
		if (accidentalTrips.hourlyBudgets == null) {
			accidentalTrips.hourlyBudgets = new LinkedHashMap<>();
		}
		// Documented base chances (DIMENSIONS.md section 9 with the 2026-11-10 user decisions).
		Map<String, Double> t = accidentalTrips.triggers;
		t.putIfAbsent("pale_hollow.sleep", 0.02);
		t.putIfAbsent("pale_hollow.sleep_near_pale_oak", 0.12);
		t.putIfAbsent("petalwind.long_fall", 0.15);
		t.putIfAbsent("petalwind.wind_launch", 0.08);
		t.putIfAbsent("glasswaste.desert_noon", 0.15);
		t.putIfAbsent("verdigris_sea.storm_minute", 0.20);
		t.putIfAbsent("ironpeak.peak", 0.15);
		t.putIfAbsent("the_hush.warden", 0.15);
		t.putIfAbsent("the_hush.shriek", 0.02);
		t.putIfAbsent("greenhaze.green_leaf_use", 0.015);
		Map<String, Integer> b = accidentalTrips.hourlyBudgets;
		b.putIfAbsent("petalwind.long_fall", 3);
		b.putIfAbsent("petalwind.wind_launch", 4);
		b.putIfAbsent("greenhaze.green_leaf_use", 12);
		b.putIfAbsent("the_hush.warden", 2);
	}
}
