package dev.elsewhere.duelist.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.HumanBossHealth;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/**
 * The 300 HP human-boss cap holds with Test Meter installed (3.0, s-compat-testmeter; kill switch {@code testmeter_compat}).
 *
 * <p>Test Meter gives every hostile mob (every {@code Enemy}, so every {@link HumanBoss}) a saved +53% max-health modifier from its own
 * {@code ServerEntityEvents.ENTITY_LOAD} listener. {@code HumanBoss} enforces the cap in its constructor and on load from disk, both of
 * which run before that listener, so a freshly spawned 300 HP ruler came out at 459 HP with Test Meter, and the order of the two mods'
 * listeners was left to mod load order. This listener sits in its own phase, ordered after the default phase every other mod uses,
 * so the cap is applied once, after Test Meter's bonus, on every spawn and load (Hrafnholt, Azmar, Mirewick and Aldermark bosses alike).
 * Event-driven: one idempotent call per boss load, nothing ticks.
 */
public final class TestMeterOrder {
	private TestMeterOrder() {}

	/** The ENTITY_LOAD phase that runs after {@link Event#DEFAULT_PHASE}. */
	public static final Identifier LATE = DuelistContent.id("hp_cap_after_mods");
	public static final boolean ENABLED = on("testmeter_compat");
	private static boolean done;

	public static void init() {
		if (done || !ENABLED) return;
		done = true;
		ServerEntityEvents.ENTITY_LOAD.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ServerEntityEvents.ENTITY_LOAD.register(LATE, (entity, level) -> {
			if (entity instanceof HumanBoss boss) HumanBossHealth.enforce(boss);
		});
	}

	/** {@code config/elsewhere-overhaul3.json} key (or {@code -Delsewhere.overhaul3.<key>=false}), default on; read once. */
	static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return true;
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			return !o.has(key) || o.get(key).getAsBoolean();
		} catch (Exception e) {
			return true;
		}
	}
}
