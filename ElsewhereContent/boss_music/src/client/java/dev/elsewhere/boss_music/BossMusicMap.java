package dev.elsewhere.boss_music;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** The data-driven boss map: {@code assets/<ns>/boss_music.json} (merged across every pack that ships one). */
public final class BossMusicMap {
	public static final Identifier FILE = Identifier.fromNamespaceAndPath("elsewhere", "boss_music.json");

	public final Map<Identifier, Identifier> themes = new HashMap<>();
	public final Map<String, Identifier> stingers = new HashMap<>();
	/** theme event -> length of its {@code .intro} file in ticks (the loop starts then). */
	public final Map<Identifier, Integer> introTicks = new HashMap<>();
	public int rampTicks = 8;
	public double range = 48;
	public int fadeIn = 40, fadeOut = 60;
	public float[] phases = {0.66F, 0.33F};
	/** 3.0: per-theme timing ({@code themes}): intro style, beat, loop/surge lengths and phases, ending length. */
	public final Map<Identifier, Conductor.Plan> plans = new HashMap<>();
	/** Theme events that more than one boss id may share on purpose (one fight, two entities). */
	public final Map<Identifier, List<Identifier>> shared = new HashMap<>();
	public boolean summonStinger = false;
	public int fleeTicks = 50, defeatTicks = 80;

	/** The one-shot intro event of a theme. */
	public static Identifier introOf(Identifier theme) {
		return theme.withSuffix(".intro");
	}

	/** The looping body event of a theme. */
	public static Identifier loopOf(Identifier theme) {
		return theme.withSuffix(".loop");
	}

	public static BossMusicMap load(Minecraft mc) {
		BossMusicMap m = new BossMusicMap();
		List<Resource> stack = mc.getResourceManager().getResourceStack(FILE);
		for (Resource r : stack) { // lowest pack first; later packs override per key
			try (BufferedReader in = r.openAsReader()) {
				JsonObject o = new Gson().fromJson(in, JsonObject.class);
				if (o.has("range")) m.range = o.get("range").getAsDouble();
				if (o.has("fade_in_ticks")) m.fadeIn = o.get("fade_in_ticks").getAsInt();
				if (o.has("fade_out_ticks")) m.fadeOut = o.get("fade_out_ticks").getAsInt();
				if (o.has("phase_thresholds")) {
					JsonArray a = o.getAsJsonArray("phase_thresholds");
					m.phases = new float[a.size()];
					for (int i = 0; i < a.size(); i++) m.phases[i] = a.get(i).getAsFloat();
				}
				if (o.has("stingers")) {
					for (var e : o.getAsJsonObject("stingers").entrySet()) m.stingers.put(e.getKey(), Identifier.parse(e.getValue().getAsString()));
				}
				if (o.has("handoff_ramp_ticks")) m.rampTicks = o.get("handoff_ramp_ticks").getAsInt();
				if (o.has("intro_ticks")) {
					for (var e : o.getAsJsonObject("intro_ticks").entrySet()) m.introTicks.put(Identifier.parse(e.getKey()), e.getValue().getAsInt());
				}
				if (o.has("summon_stinger")) m.summonStinger = o.get("summon_stinger").getAsBoolean();
				if (o.has("flee_fade_ticks")) m.fleeTicks = o.get("flee_fade_ticks").getAsInt();
				if (o.has("defeat_fade_ticks")) m.defeatTicks = o.get("defeat_fade_ticks").getAsInt();
				if (o.has("themes")) {
					for (var e : o.getAsJsonObject("themes").entrySet()) {
						JsonObject t = e.getValue().getAsJsonObject();
						m.plans.put(Identifier.parse(e.getKey()), new Conductor.Plan(t.get("intro_ticks").getAsInt(), "cold".equals(t.get("intro").getAsString()),
							t.get("beat_ticks").getAsFloat(), t.get("loop_ticks").getAsFloat(), t.get("surge_ticks").getAsFloat(), t.get("end_ticks").getAsInt(),
							t.has("loop_phase_ticks") ? t.get("loop_phase_ticks").getAsFloat() : 0F, t.has("surge_phase_ticks") ? t.get("surge_phase_ticks").getAsFloat() : 0F));
					}
				}
				if (o.has("shared_themes")) {
					for (var e : o.getAsJsonObject("shared_themes").entrySet()) {
						if (!e.getValue().isJsonArray()) continue;
						List<Identifier> ids = new java.util.ArrayList<>();
						for (var x : e.getValue().getAsJsonArray()) ids.add(Identifier.parse(x.getAsString()));
						m.shared.put(Identifier.parse(e.getKey()), ids);
					}
				}
				if (o.has("bosses")) {
					for (var e : o.getAsJsonObject("bosses").entrySet()) m.themes.put(Identifier.parse(e.getKey()), Identifier.parse(e.getValue().getAsString()));
				}
			} catch (Exception ex) {
				BossMusicService.LOGGER.warn("boss_music.json unreadable in one pack: {}", ex.toString());
			}
		}
		return m;
	}
}
