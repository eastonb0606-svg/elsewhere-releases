package dev.elsewhere.arsenal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 3.0 overhaul kill switches for gear, balance and Callings (GEAR.md A3, BUILD-3.0 "Kill switches"). Every key defaults on.
 * Turn one off with {@code "key": false} in {@code config/elsewhere-overhaul3.json} or the JVM flag
 * {@code -Delsewhere.overhaul3.<key>=false}. Read once, at class load; nothing polls.
 */
public final class Overhaul3 {
	private Overhaul3() {}

	private static final JsonObject FILE = read();

	/** {@code callings}: Primary / Secondary / Open Callings (Calling v3). Off = the 2.1 piece-count rule and class-armor cons. */
	public static final boolean CALLINGS = on("callings");
	/** {@code calling_weapons}: Vanguard and Marksman multipliers on calling-tagged weapons. */
	public static final boolean CALLING_WEAPONS = CALLINGS && on("calling_weapons");
	/** {@code rebalance_arsenal}: 3.0 numbers for arsenal items (Sunlance, Stormcoil, Resonator, Prism, Repeater). */
	public static final boolean REBALANCE_ARSENAL = on("rebalance_arsenal");
	/** {@code spell_hooks}: the 3.0 spell API hooks for other modules (Spells.DAMAGE_MULT / CC_MULT / withPowerScale, CasterItem.castFree). */
	public static final boolean SPELL_HOOKS = on("spell_hooks");
	/** {@code balance_calc}: the balance-calculator self-test section (test 7). */
	public static final boolean BALANCE_CALC = on("balance_calc");

	public static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			if (FILE != null && FILE.has(key)) return FILE.get(key).getAsBoolean();
		} catch (RuntimeException e) {
			// a malformed value keeps the default
		}
		return true;
	}

	private static JsonObject read() {
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return null;
			return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
