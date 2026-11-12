package dev.elsewhere.arsenal.compat;

import dev.elsewhere.arsenal.Overhaul3;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Test Meter (mod id {@code testmeter}) compat for damage Elsewhere has already scaled itself (3.0, kill switch {@code testmeter_compat}).
 *
 * <p>Test Meter's {@code LivingEntity.hurtServer} hook multiplies every hit a player causes: the full melee multiplier for
 * {@code minecraft:player_attack}, half of it when the direct entity is any {@code Projectile}. Spells and minions get the half bonus
 * from Elsewhere instead ({@code Spells.damage}, {@code MinionApi.minionDamage}, ARSENAL.md section 3), because most of them have no
 * projectile. A spell that does fly as a projectile ({@code MagicBolt} and every bolt a 3.0 item launches) was therefore scaled twice:
 * x1.27 x 1.27 = x1.61 at a typical 650 ng/dL instead of x1.27.
 *
 * <p>{@link #preCompensate} divides by the exact factor Test Meter is about to apply to that source (asked from Test Meter itself,
 * {@code TestMeter.scaleOutgoingDamage(source, 1)}), so the hit lands at exactly the damage Elsewhere computed, whatever Test Meter's
 * config or version. Without Test Meter, or for a source it leaves alone (beams, minion bites), the factor is 1 and nothing changes.
 * One reflective call per hit; nothing ticks.
 */
public final class TestMeterCompat {
	private TestMeterCompat() {}

	/** {@code testmeter_compat} in {@code config/elsewhere-overhaul3.json} (default on). Off = the 2.1 behaviour (bolts double-scaled). */
	public static final boolean ENABLED = Overhaul3.on("testmeter_compat");

	private static Method scale;
	private static boolean looked;

	/** True when Test Meter is installed and its damage hook could be found. */
	public static boolean present() {
		return FabricLoader.getInstance().isModLoaded("testmeter") && method() != null;
	}

	private static Method method() {
		if (!looked) {
			looked = true;
			try {
				scale = Class.forName("dev.testmeter.TestMeter").getMethod("scaleOutgoingDamage", DamageSource.class, float.class);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				scale = null;
			}
		}
		return scale;
	}

	/** The factor Test Meter's hurtServer hook will apply to a hit from {@code source} (1 without Test Meter or when it leaves the source alone). */
	public static float outgoingFactor(DamageSource source) {
		if (source == null || !FabricLoader.getInstance().isModLoaded("testmeter")) return 1F;
		Method m = method();
		if (m == null) return 1F;
		try {
			float f = ((Number) m.invoke(null, source, 1F)).floatValue();
			return f > 0F && Float.isFinite(f) ? f : 1F;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			scale = null;
			return 1F;
		}
	}

	/**
	 * The amount to pass to {@code hurtServer} so that exactly {@code intended} lands (before armor) after Test Meter's hook.
	 * Use it for damage that already contains Elsewhere's own Test Meter bonus (spells, minions, their bolts).
	 */
	public static float preCompensate(DamageSource source, float intended) {
		if (!ENABLED || intended <= 0F) return intended;
		float f = outgoingFactor(source);
		return f == 1F ? intended : intended / f;
	}
}
