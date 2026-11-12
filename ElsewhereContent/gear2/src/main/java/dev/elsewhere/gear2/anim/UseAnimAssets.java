package dev.elsewhere.gear2.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Self-test checks for the P6 gear2 weapon states (the animation code itself lives in arsenal's client,
 * {@code dev.elsewhere.arsenal.client.anim}, keyed by item id; its curves are checked in the arsenal self-test).
 * Ironclad Flail: charged glint on {@code elsewhere:attack_charge}; Gale Rapier: lunge streak on {@code elsewhere:lunge_ready}.
 */
public final class UseAnimAssets {
	private UseAnimAssets() {}

	@FunctionalInterface
	public interface Out {
		void check(String name, boolean ok, Object detail);
	}

	static final String[][] GEAR2 = {
		{"ironclad_flail", "elsewhere:attack_charge", "ironclad_flail", "ironclad_flail_charged"},
		{"gale_rapier", "elsewhere:lunge_ready", "gale_rapier", "gale_rapier_lunge"}};

	public static void run(Out out) {
		for (String[] w : GEAR2) itemModel(out, w);
	}

	/** One weapon's item model: dispatches on its state property and every state model and 16x16 texture exists. */
	public static void itemModel(Out out, String[] w) {
		String name = "useanim: " + w[0] + " item model switches on " + w[1] + " (" + (w.length - 2) + " states, models + 16x16 textures)";
		try {
			JsonObject item = json("assets/elsewhere/items/" + w[0] + ".json");
			Set<String> props = new LinkedHashSet<>();
			List<String> models = new ArrayList<>();
			walk(item, props, models);
			List<String> missing = new ArrayList<>();
			if (!props.contains(w[1])) missing.add("property " + w[1] + " (has " + props + ")");
			for (int i = 2; i < w.length; i++) if (!models.contains("elsewhere:item/" + w[i])) missing.add("state " + w[i]);
			for (String m : models) {
				String path = m.substring(m.indexOf(':') + 1);
				JsonObject model = json("assets/elsewhere/models/" + path + ".json");
				if (model == null) {
					missing.add("model " + m);
					continue;
				}
				String tex = model.has("textures") ? model.getAsJsonObject("textures").get("layer0").getAsString() : null;
				if (tex == null || !tex.startsWith("elsewhere:")) continue; // vanilla texture
				int[] size = pngSize("assets/elsewhere/textures/" + tex.substring(10) + ".png");
				if (size == null || size[0] != 16 || size[1] != 16) missing.add("texture " + tex + (size == null ? " missing" : " " + size[0] + "x" + size[1]));
			}
			out.check(name, item != null && missing.isEmpty(), missing.isEmpty() ? null : missing);
		} catch (Exception e) {
			out.check(name, false, e);
		}
	}

	static void walk(JsonElement e, Set<String> props, List<String> models) {
		if (e == null) return;
		if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) walk(x, props, models);
		} else if (e.isJsonObject()) {
			JsonObject o = e.getAsJsonObject();
			if (o.has("property")) props.add(o.get("property").getAsString());
			if (o.has("type") && "minecraft:model".equals(o.get("type").getAsString()) && o.has("model")) models.add(o.get("model").getAsString());
			for (var en : o.entrySet()) if (!en.getKey().equals("model") || !en.getValue().isJsonPrimitive()) walk(en.getValue(), props, models);
		}
	}

	/** The first copy on the classpath (what the game's resource stack and the merged jar use). */
	static JsonObject json(String path) throws Exception {
		try (InputStream in = UseAnimAssets.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	static int[] pngSize(String path) throws Exception {
		try (InputStream in = UseAnimAssets.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) return null;
			byte[] h = in.readNBytes(24);
			if (h.length < 24) return null;
			int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
			int hh = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
			return new int[] {w, hh};
		}
	}
}
