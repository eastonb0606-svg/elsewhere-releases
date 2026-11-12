package dev.elsewhere.verdigris_ironpeak.compat;

import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakContent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Soft tie-in with Test Meter (mod id {@code testmeter}). No compile dependency: everything goes through reflection on
 * Test Meter's per-player {@code TestData} and is a no-op when the mod is absent or its internals changed.
 * All nudges are in ng/dL and respect Test Meter's own smoothing (they decay naturally when they stop).
 */
public final class TestMeterBridge {
	private static Boolean ok;
	private static Method data, total;
	private static Field target, acute, adrenalineSeconds, pendingDamage, configField, cfgAdrenaline, cfgFoods;

	private TestMeterBridge() {}

	public static boolean available() {
		if (ok == null) ok = init();
		return ok;
	}

	private static boolean init() {
		if (!FabricLoader.getInstance().isModLoaded("testmeter")) return false;
		try {
			Class<?> tm = Class.forName("dev.testmeter.TestMeter");
			Class<?> td = Class.forName("dev.testmeter.TestData");
			data = tm.getMethod("data", ServerPlayer.class);
			total = td.getMethod("total");
			target = field(td, "target");
			acute = field(td, "acute");
			adrenalineSeconds = field(td, "adrenalineSeconds");
			pendingDamage = field(td, "pendingDamage");
			configField = field(tm, "config");
			Class<?> cfg = configField.getType();
			cfgAdrenaline = field(cfg, "adrenalineSeconds");
			cfgFoods = field(cfg, "foodQuality");
			VerdigrisIronpeakContent.LOG.info("Test Meter found: tie-ins on");
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			VerdigrisIronpeakContent.LOG.warn("Test Meter is loaded but its internals changed; tie-ins off ({})", e.toString());
			return false;
		}
	}

	private static Field field(Class<?> c, String name) throws NoSuchFieldException {
		Field f = c.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	private static Object d(ServerPlayer p) throws ReflectiveOperationException {
		return data.invoke(null, p);
	}

	/** Current total testosterone in ng/dL, or -1 without Test Meter. */
	public static double total(ServerPlayer p) {
		if (!available()) return -1;
		try {
			return (double) total.invoke(d(p));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1;
		}
	}

	/** A quick, decaying change (Thurgrim's roar: -80). */
	public static void acute(ServerPlayer p, double ngdl) {
		if (!available()) return;
		try {
			Object o = d(p);
			acute.setDouble(o, acute.getDouble(o) + ngdl);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	/**
	 * A lifestyle factor: call once per second while it applies. Test Meter's lifestyle target approaches its own value at
	 * 1/60 per second, so adding {@code steady/60} each second holds the target {@code steady} ng/dL higher.
	 */
	public static void lifestyle(ServerPlayer p, double steady) {
		if (!available()) return;
		try {
			Object o = d(p);
			target.setDouble(o, target.getDouble(o) + steady / 60.0);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	/** Holds the lifestyle target a fraction higher (Summit Crown: 0.10). Call once per second. */
	public static void lifestyleFraction(ServerPlayer p, double fraction) {
		if (!available()) return;
		try {
			Object o = d(p);
			double t = target.getDouble(o);
			target.setDouble(o, t + t * fraction / 60.0);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	/** Gives back part of the damage that counts toward damage stress (Summit Crown halves it). */
	public static void forgiveDamageStress(ServerPlayer p, float amount) {
		if (!available()) return;
		try {
			Object o = d(p);
			pendingDamage.setFloat(o, Math.max(0, pendingDamage.getFloat(o) - amount));
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	/** Adrenaline like Test Meter's Warden Adrenal Gland, with a duration multiplier. */
	public static void adrenaline(ServerPlayer p, double durationMultiplier) {
		if (!available()) return;
		try {
			Object o = d(p);
			int seconds = cfgAdrenaline.getInt(configField.get(null));
			adrenalineSeconds.setInt(o, (int) Math.round(seconds * durationMultiplier));
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	/** Yak meat counts as quality red meat. Called on server start. */
	@SuppressWarnings("unchecked")
	public static void registerFoods() {
		if (!available()) return;
		try {
			Map<String, Double> foods = (Map<String, Double>) cfgFoods.get(configField.get(null));
			foods.putIfAbsent("elsewhere:cooked_yak_meat", 1.0);
			foods.putIfAbsent("elsewhere:yak_meat", -0.2);
			foods.putIfAbsent("elsewhere:yak_butter_tea", 0.6);
			foods.putIfAbsent("elsewhere:brine_lung", 0.5);
			foods.putIfAbsent("elsewhere:ironjaw_gland", 0.0);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}
}
