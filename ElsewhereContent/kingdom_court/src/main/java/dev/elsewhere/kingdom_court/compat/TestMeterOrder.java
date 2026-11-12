package dev.elsewhere.kingdom_court.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.api.HpCap;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * The court's 300 HP cap ({@link HpCap}, RIVALS-DECISIONS 13) holds with Test Meter installed (3.0, s-compat-testmeter; kill switch
 * {@code testmeter_compat}). Rulers and court-spawned leaders are {@code Enemy} mobs, so Test Meter adds a saved +53% max health from its
 * own ENTITY_LOAD listener. The court's clamp on load ran in the same default phase, before or after Test Meter's by mod load order;
 * a freshly crowned 300 HP ruler could come out at 459. This listener runs in a phase ordered after the default one, so the clamp
 * always sees Test Meter's bonus. One idempotent clamp per ruler or leader load; nothing ticks.
 */
public final class TestMeterOrder {
	private TestMeterOrder() {}

	public static final Identifier LATE = CourtContent.id("hp_cap_after_mods");
	public static final boolean ENABLED = on("testmeter_compat");
	private static boolean done;

	public static void init() {
		if (done || !ENABLED) return;
		done = true;
		ServerEntityEvents.ENTITY_LOAD.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ServerEntityEvents.ENTITY_LOAD.register(LATE, (entity, level) -> {
			if (entity instanceof LivingEntity e && (entity instanceof CourtRuler || Leaders.of(entity) != null)) HpCap.clamp(e);
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
