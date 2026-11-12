package dev.elsewhere.boss_music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-player toggle. Stored as {@code "bossMusic"} in {@code config/elsewhere-client.json} (the same file the other
 * Elsewhere display switches use; other keys are kept). Also honours {@code ElsewhereHud.flag("bossMusic")} when the
 * arsenal HUD class is present, so the switch shows up wherever the shared display toggles are listed.
 */
public final class BossMusicConfig {
	private BossMusicConfig() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean enabled = true;
	/** 3.0 kill switch {@code "bossMusic3"}: false = the 2.x behaviour (generic fades and stingers, no beat sync, surge or endings). */
	private static boolean overhaul = true;
	private static Method hudFlag;
	private static boolean hudLooked;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
	}

	public static void load() {
		try {
			Path p = file();
			if (Files.exists(p)) {
				JsonObject o = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), JsonObject.class);
				if (o != null && o.has("bossMusic")) enabled = o.get("bossMusic").getAsBoolean();
				if (o != null && o.has("bossMusic3")) overhaul = o.get("bossMusic3").getAsBoolean();
			}
		} catch (Exception e) {
			enabled = true;
		}
	}

	public static boolean overhaul() {
		return overhaul && !"false".equals(System.getProperty("boss_music.overhaul3"));
	}

	public static boolean enabled() {
		if (!enabled) return false;
		if (!hudLooked) {
			hudLooked = true;
			try {
				hudFlag = Class.forName("dev.elsewhere.hud.ElsewhereHud").getMethod("flag", String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				hudFlag = null;
			}
		}
		if (hudFlag != null) {
			try {
				return (Boolean) hudFlag.invoke(null, "bossMusic");
			} catch (ReflectiveOperationException | RuntimeException e) {
				return true;
			}
		}
		return true;
	}

	public static void setEnabled(boolean on) {
		enabled = on;
		try {
			Path p = file();
			JsonObject o = Files.exists(p) ? GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), JsonObject.class) : null;
			if (o == null) o = new JsonObject();
			o.addProperty("bossMusic", on);
			Files.createDirectories(p.getParent());
			Files.writeString(p, GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			// the switch still applies for this session
		}
	}
}
