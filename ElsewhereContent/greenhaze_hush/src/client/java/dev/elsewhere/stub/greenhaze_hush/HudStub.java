package dev.elsewhere.stub.greenhaze_hush;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.greenhaze_hush.FxPrefs;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * STUB bridge to the shared client settings ({@code dev.elsewhere.hud.ElsewhereHud}, built in the arsenal module; at merge it
 * is always present, so this class can be replaced by direct calls). Meters: {@code haze} (config key {@code hazeMeter}) and
 * {@code resonance} ({@code resonanceMeter}), each Auto / Always / Hidden; plus {@code screenEffects} and {@code reduceMotion}.
 * Without ElsewhereHud (dev module alone) it reads the same keys from {@code config/elsewhere-client.json} (re-read when the
 * file changes). The two screen-effect flags are sent to the server ({@link FxPrefs}) on join and whenever they change.
 */
public final class HudStub {
	private HudStub() {}

	private static Method shouldShow, screenEffects, reduceMotion;
	private static boolean shared;
	// fallback state (no ElsewhereHud)
	private static String hazeMode = "auto", resonanceMode = "auto";
	private static boolean hudHidden, effects = true, reduce;
	private static long lastModified = -1;
	private static int tick;
	private static Boolean sentEffects, sentReduce;

	public static void init() {
		try {
			Class<?> hud = Class.forName("dev.elsewhere.hud.ElsewhereHud");
			Method register = hud.getMethod("registerMeter", String.class, String.class);
			shouldShow = hud.getMethod("shouldShow", String.class, boolean.class);
			screenEffects = hud.getMethod("screenEffects");
			reduceMotion = hud.getMethod("reduceMotion");
			register.invoke(null, "haze", "hazeMeter");
			register.invoke(null, "resonance", "resonanceMeter");
			shared = true;
		} catch (Throwable t) {
			shared = false;
			readFile();
		}
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			sentEffects = null;
			sentReduce = null;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (++tick % 20 != 0) return;
			if (!shared) readFile();
			if (mc.player == null || !ClientPlayNetworking.canSend(FxPrefs.Payload.TYPE)) return;
			boolean e = screenEffects(), r = reduceMotion();
			if (sentEffects == null || sentEffects != e || sentReduce != r) {
				ClientPlayNetworking.send(new FxPrefs.Payload(e, r));
				sentEffects = e;
				sentReduce = r;
			}
		});
	}

	public static boolean shouldShow(String id, boolean autoCondition) {
		if (shared) {
			try {
				return (Boolean) shouldShow.invoke(null, id, autoCondition);
			} catch (Throwable t) {
				shared = false;
			}
		}
		if (hudHidden) return false;
		String m = id.equals("haze") ? hazeMode : resonanceMode;
		return switch (m) {
			case "always" -> true;
			case "hidden" -> false;
			default -> autoCondition;
		};
	}

	public static boolean screenEffects() {
		if (shared) {
			try {
				return (Boolean) screenEffects.invoke(null);
			} catch (Throwable t) {
				shared = false;
			}
		}
		return effects;
	}

	public static boolean reduceMotion() {
		if (shared) {
			try {
				return (Boolean) reduceMotion.invoke(null);
			} catch (Throwable t) {
				shared = false;
			}
		}
		return reduce;
	}

	private static void readFile() {
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
			if (!Files.exists(f)) return;
			long m = Files.getLastModifiedTime(f).toMillis();
			if (m == lastModified) return;
			lastModified = m;
			JsonElement e = JsonParser.parseString(Files.readString(f));
			if (!e.isJsonObject()) return;
			JsonObject o = e.getAsJsonObject();
			if (o.has("hazeMeter")) hazeMode = o.get("hazeMeter").getAsString().toLowerCase(java.util.Locale.ROOT);
			if (o.has("resonanceMeter")) resonanceMode = o.get("resonanceMeter").getAsString().toLowerCase(java.util.Locale.ROOT);
			if (o.has("hudHidden")) hudHidden = o.get("hudHidden").getAsBoolean();
			if (o.has("screenEffects")) effects = o.get("screenEffects").getAsBoolean();
			if (o.has("reduceMotion")) reduce = o.get("reduceMotion").getAsBoolean();
		} catch (Throwable ignored) {
		}
	}
}
