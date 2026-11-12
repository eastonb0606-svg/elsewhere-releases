package dev.elsewhere.hush;

import dev.elsewhere.stub.greenhaze_hush.TripSystem;
import dev.elsewhere.stub.greenhaze_hush.TripTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

/** DIMENSIONS.md 9: post-dragon only. A Warden targeting you (15%, ACTIVITY), each shrieker you set off (2%, LOCATION). */
public final class HushTrips {
	private HushTrips() {}

	/** bal-trips-keep: the Warden trigger is 15% (it was 20%). */
	public static final double WARDEN_CHANCE = 0.15;
	public static final double SHRIEK_CHANCE = 0.02;

	/**
	 * A config/elsewhere.json written by 0.3.0 still holds the old Warden default 0.20; the core only fills missing keys, so it would
	 * override this 15%. Values that are exactly the old default are replaced (a deliberate custom value is kept). Reflection: no
	 * compile dependency on the core's config class; harmless when it is absent.
	 */
	@SuppressWarnings("unchecked")
	public static boolean migrateStaleConfig() {
		try {
			Class<?> c = Class.forName("dev.elsewhere.ElsewhereConfig");
			Object cfg = c.getMethod("get").invoke(null);
			Object trips = cfg.getClass().getField("accidentalTrips").get(cfg);
			java.util.Map<String, Double> m = (java.util.Map<String, Double>) trips.getClass().getField("triggers").get(trips);
			Double v = m.get("the_hush.warden");
			if (v != null && Math.abs(v - 0.20) < 1e-9) { m.put("the_hush.warden", WARDEN_CHANCE); return true; }
		} catch (Throwable ignored) { }
		return false;
	}

	public static boolean dragonKilled(ServerPlayer p) {
		ServerLevel end = p.level().getServer().getLevel(Level.END);
		EnderDragonFight fight = end == null ? null : end.getDragonFight();
		return fight != null && fight.hasPreviouslyKilledDragon();
	}

	public static final TripTrigger WARDEN = new TripTrigger() {
		@Override public String id() { return "the_hush.warden"; }
		@Override public ResourceKey<Level> target() { return Hush.LEVEL; }
		@Override public Kind kind() { return Kind.ACTIVITY; }
		@Override public double baseChance(ServerPlayer p) { return dragonKilled(p) ? WARDEN_CHANCE : 0.0; }
	};

	public static final TripTrigger SHRIEK = new TripTrigger() {
		@Override public String id() { return "the_hush.shriek"; }
		@Override public ResourceKey<Level> target() { return Hush.LEVEL; }
		@Override public Kind kind() { return Kind.LOCATION; }
		@Override public double baseChance(ServerPlayer p) { return dragonKilled(p) ? SHRIEK_CHANCE : 0.0; }
	};

	public static void onWardenTarget(ServerPlayer p) {
		TripSystem.fire(p, WARDEN, p.getRandom());
	}

	public static void onShriek(ServerPlayer p) {
		TripSystem.fire(p, SHRIEK, p.getRandom());
	}
}
