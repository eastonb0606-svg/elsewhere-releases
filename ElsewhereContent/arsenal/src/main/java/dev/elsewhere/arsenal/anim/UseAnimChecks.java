package dev.elsewhere.arsenal.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.arsenal.anim.SwingCurves.Style;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Self-test checks for P6 weapon use animations (server side: the curves, the trail math, the item model state files
 * and their textures). The client hooks themselves are covered by the SHOTS-3.0 client shots.
 */
public final class UseAnimChecks {
	private UseAnimChecks() {}

	@FunctionalInterface
	public interface Out {
		void check(String name, boolean ok, Object detail);
	}

	/** Arsenal weapons: item id path, the property its item model must dispatch on, and its state models. */
	static final String[][] ARSENAL = {
		{"amethyst_wand", "elsewhere:cast_cooldown", "amethyst_wand", "amethyst_wand_spent"},
		{"sunlance_staff", "elsewhere:cast_cooldown", "sunlance_staff", "sunlance_staff_spent"},
		{"stormcoil_staff", "elsewhere:cast_cooldown", "stormcoil_staff", "stormcoil_staff_spent"},
		{"sculk_resonator", "elsewhere:channel_time", "sculk_resonator", "sculk_resonator_charging_0", "sculk_resonator_charging_1", "sculk_resonator_charged"},
		{"mirrorglass_prism", "elsewhere:channeling", "mirrorglass_prism", "mirrorglass_prism_channel"},
		{"hushward_bell", "elsewhere:channeling", "hushward_bell", "hushward_bell_ringing"}};

	public static void run(Out out) {
		out.check("useanim: kill switch use_anims defaults on", dev.elsewhere.arsenal.Overhaul3.on("use_anims"), null);
		out.check("useanim: styles (flail, rapier, 3 casters; others vanilla)", SwingCurves.styleOf("elsewhere", "ironclad_flail") == Style.FLAIL
			&& SwingCurves.styleOf("elsewhere", "gale_rapier") == Style.RAPIER && SwingCurves.styleOf("elsewhere", "amethyst_wand") == Style.CAST
			&& SwingCurves.styleOf("elsewhere", "sunlance_staff") == Style.CAST && SwingCurves.styleOf("elsewhere", "stormcoil_staff") == Style.CAST
			&& SwingCurves.styleOf("elsewhere", "sculk_resonator") == Style.NONE && SwingCurves.styleOf("minecraft", "iron_sword") == Style.NONE, null);
		curves(out);
		trails(out);
		for (String[] w : ARSENAL) itemModel(out, w);
	}

	static void curves(Out out) {
		float[] v = new float[7];
		boolean rest = true, smooth = true;
		float worst = 0;
		for (Style s : new Style[] {Style.FLAIL, Style.RAPIER, Style.CAST}) {
			for (float[][] t : new float[][][] {SwingCurves.firstPersonTable(s), SwingCurves.thirdPersonTable(s)}) {
				for (float p : new float[] {0F, 1F}) {
					SwingCurves.sample(t, p, v);
					for (int c = 0; c < t[0].length - 1; c++) if (Math.abs(v[c]) > 1.0E-4F) rest = false;
				}
				float[] prev = new float[7];
				SwingCurves.sample(t, 0F, prev);
				for (int i = 1; i <= 100; i++) {
					SwingCurves.sample(t, i / 100F, v);
					for (int c = 0; c < t[0].length - 1; c++) {
						// first person: degrees (and blocks); third person: radians. Bound a single 1% step of the swing.
						float bound = t[0].length == 7 ? (c < 3 ? 0.06F : 12F) : 0.25F;
						float d = Math.abs(v[c] - prev[c]);
						worst = Math.max(worst, d / bound);
						if (d > bound) smooth = false;
					}
					System.arraycopy(v, 0, prev, 0, 7);
				}
			}
		}
		out.check("useanim: every swing curve starts and ends at the vanilla rest pose", rest, null);
		out.check("useanim: swing curves are continuous (no jump over a 1% step)", smooth, String.format("worst %.2f of bound", worst));
		float minRx = 0, maxRx = 0, minTz = 0, minTzAt = 1;
		for (int i = 0; i <= 100; i++) {
			SwingCurves.sample(SwingCurves.firstPersonTable(Style.FLAIL), i / 100F, v);
			minRx = Math.min(minRx, v[3]);
			maxRx = Math.max(maxRx, v[3]);
			SwingCurves.sample(SwingCurves.firstPersonTable(Style.RAPIER), i / 100F, v);
			if (v[2] < minTz) {
				minTz = v[2];
				minTzAt = i / 100F;
			}
		}
		out.check("useanim: flail swings a wide overhead arc (>= 140 degrees)", maxRx - minRx >= 140F, (maxRx - minRx) + " deg");
		float tz = minTz, at = minTzAt;
		out.check("useanim: rapier lunge thrusts >= 0.45 block forward in the first half of the swing", tz <= -0.45F && at < 0.5F, tz + " at " + at);
		float[] a = new float[7];
		SwingCurves.thirdPerson(Style.FLAIL, 0.2F, 0F, a);
		boolean overhead = a[0] < -2.5F;
		SwingCurves.thirdPerson(Style.RAPIER, 0.32F, 0F, a);
		boolean lunge = a[0] < -1.4F && a[4] < -0.4F && a[5] > 0.3F;
		out.check("useanim: third person flail winds up overhead, rapier lunges (arm level, front leg forward, back leg back)", overhead && lunge, null);
	}

	static void trails(Out out) {
		SwingCurves.Scratch sc = new SwingCurves.Scratch();
		float[] pts = new float[SwingCurves.TRAIL_MAX * 6];
		int fl = SwingCurves.firstPersonTrail(Style.FLAIL, 0.45F, 1, 5, sc, pts);
		float span = dist(pts, 3, (fl - 1) * 6 + 3);
		out.check("useanim: flail first-person trail has 5 samples mid-strike and sweeps >= 0.2 block", fl == 5 && span >= 0.2F, fl + " samples, " + span);
		int rp = SwingCurves.firstPersonTrail(Style.RAPIER, 0.3F, 1, SwingCurves.TRAIL_MAX, sc, pts);
		out.check("useanim: rapier trail capped at 6 samples (POLISH ribbon budget)", rp >= 2 && rp <= SwingCurves.TRAIL_MAX, rp);
		boolean none = SwingCurves.firstPersonTrail(Style.FLAIL, 0.05F, 1, 5, sc, pts) == 0 && SwingCurves.firstPersonTrail(Style.CAST, 0.4F, 1, 5, sc, pts) == 0
			&& SwingCurves.firstPersonTrail(Style.FLAIL, 0.45F, 1, SwingCurves.trailSamples(0), sc, pts) == 0
			&& SwingCurves.firstPersonTrail(Style.RAPIER, 0.99F, 1, 5, sc, pts) == 0;
		out.check("useanim: no trail during wind-up, after the swing, for casters, or on the low level", none, null);
		int r = SwingCurves.thirdPersonTrail(Style.FLAIL, 0.45F, false, 0F, false, 5, sc, pts);
		boolean finite = true;
		float rx = 0, lx = 0;
		for (int i = 0; i < r * 6; i++) if (!Float.isFinite(pts[i]) || Math.abs(pts[i]) > 3F) finite = false;
		for (int i = 0; i < r; i++) rx += pts[i * 6 + 3] / r;
		int l = SwingCurves.thirdPersonTrail(Style.FLAIL, 0.45F, true, 0F, false, 5, sc, pts);
		for (int i = 0; i < l * 6; i++) if (!Float.isFinite(pts[i]) || Math.abs(pts[i]) > 3F) finite = false;
		for (int i = 0; i < l; i++) lx += pts[i * 6 + 3] / l;
		// model space: the right arm is on -x, the left on +x
		out.check("useanim: third-person trail stays at the weapon (within 3 blocks) on the swinging arm's side", r == 5 && l == 5 && finite && lx > rx,
			r + "/" + l + " mean x " + rx + "/" + lx);
		out.check("useanim: effects levels low/normal/high = 0/5/6 samples within 0/24/32 blocks", SwingCurves.trailSamples(0) == 0
			&& SwingCurves.trailSamples(1) == 5 && SwingCurves.trailSamples(2) == 6 && SwingCurves.trailRange(0) == 0 && SwingCurves.trailRange(1) == 24
			&& SwingCurves.trailRange(2) == 32, null);
	}

	static float dist(float[] p, int a, int b) {
		float dx = p[a] - p[b], dy = p[a + 1] - p[b + 1], dz = p[a + 2] - p[b + 2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
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
		try (InputStream in = UseAnimChecks.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) return null;
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	static int[] pngSize(String path) throws Exception {
		try (InputStream in = UseAnimChecks.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) return null;
			byte[] h = in.readNBytes(24);
			if (h.length < 24) return null;
			int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
			int hh = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
			return new int[] {w, hh};
		}
	}
}
