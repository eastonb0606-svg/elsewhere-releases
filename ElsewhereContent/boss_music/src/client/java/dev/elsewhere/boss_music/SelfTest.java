package dev.elsewhere.boss_music;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Dev check ({@code -Dboss_music.selftest=true}, {@code -Dboss_music.selftest.exit=true}): every theme (its intro and its loop event) and stinger in the map
 * has a sounds.json event whose OGG files resolve through the resource manager; every boss id in the map exists in the
 * entity registry (skipped when no other Elsewhere module is loaded). 3.0: every theme has its timing plan and its surge /
 * ending files when the plan names them, a theme is shared only where {@code shared_themes} documents it, and
 * {@link ConductorSim} runs every theme through the transition scenarios. Prints one SELFTEST line.
 */
final class SelfTest {
	private SelfTest() {}

	static void run(Minecraft mc) {
		int bad = 0, files = 0;
		StringBuilder msg = new StringBuilder();
		try {
			BossMusicMap m = BossMusicMap.load(mc);
			Set<Identifier> events = new HashSet<>();
			int simChecks = 0, surges = 0, endings = 0;
			java.util.List<String> sim = new java.util.ArrayList<>();
			for (Identifier theme : new HashSet<>(m.themes.values())) {
				events.add(BossMusicMap.introOf(theme));
				events.add(BossMusicMap.loopOf(theme));
				if (!m.introTicks.containsKey(theme)) {
					bad++;
					msg.append("\n  no intro_ticks for ").append(theme);
				}
				Conductor.Plan plan = m.plans.get(theme);
				if (plan == null) {
					bad++;
					msg.append("\n  no 3.0 'themes' timing for ").append(theme);
					continue;
				}
				if (plan.hasSurge()) {
					events.add(theme.withSuffix(".surge"));
					surges++;
				}
				if (plan.hasEnd()) {
					events.add(theme.withSuffix(".end"));
					endings++;
				}
				if (plan.beatTicks() <= 0 || plan.loopTicks() <= 0) {
					bad++;
					msg.append("\n  no beat/loop length for ").append(theme);
				}
				simChecks += ConductorSim.check(theme.getPath(), plan, sim);
			}
			// two bosses on one theme only where boss_music.json says it is one fight
			java.util.Map<Identifier, java.util.List<Identifier>> users = new java.util.HashMap<>();
			for (var e : m.themes.entrySet()) users.computeIfAbsent(e.getValue(), k -> new java.util.ArrayList<>()).add(e.getKey());
			for (var e : users.entrySet()) {
				if (e.getValue().size() < 2) continue;
				var ok = m.shared.get(e.getKey());
				if (ok == null || !new HashSet<>(ok).equals(new HashSet<>(e.getValue()))) {
					bad++;
					msg.append("\n  undocumented shared theme ").append(e.getKey()).append(" ").append(e.getValue());
				}
			}
			bad += sim.size();
			for (String line : sim.subList(0, Math.min(20, sim.size()))) msg.append("\n  transition: ").append(line);
			events.addAll(m.stingers.values());
			var sounds = mc.getResourceManager().getResourceStack(Identifier.fromNamespaceAndPath("elsewhere", "sounds.json"));
			com.google.gson.JsonObject all = new com.google.gson.JsonObject();
			for (Resource r : sounds) {
				try (var in = r.openAsReader()) {
					for (var e : new com.google.gson.Gson().fromJson(in, com.google.gson.JsonObject.class).entrySet()) all.add(e.getKey(), e.getValue());
				}
			}
			for (Identifier ev : events) {
				if (!all.has(ev.getPath())) {
					bad++;
					msg.append("\n  no sounds.json event ").append(ev);
					continue;
				}
				for (var s : all.getAsJsonObject(ev.getPath()).getAsJsonArray("sounds")) {
					String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
					Identifier file = Identifier.parse(name).withPrefix("sounds/").withSuffix(".ogg");
					var res = mc.getResourceManager().getResource(file);
					if (res.isEmpty()) {
						bad++;
						msg.append("\n  missing file ").append(file);
						continue;
					}
					try (InputStream in = res.get().open()) {
						byte[] head = in.readNBytes(4);
						if (head.length < 4 || head[0] != 'O' || head[1] != 'g' || head[2] != 'g' || head[3] != 'S') {
							bad++;
							msg.append("\n  not an OGG ").append(file);
						}
					}
					files++;
				}
			}
			boolean otherModules = BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.fromNamespaceAndPath("elsewhere", "mother_creak"));
			int missing = 0;
			if (otherModules) {
				for (Identifier boss : m.themes.keySet()) {
					if (!BuiltInRegistries.ENTITY_TYPE.containsKey(boss)) {
						missing++;
						msg.append("\n  unknown entity ").append(boss);
					}
				}
			}
			BossMusicService.LOGGER.info("SELFTEST boss_music: {} bosses, {} themes ({} surge, {} endings), {} events, {} sound files checked, {} transition ticks simulated, entity registry {}; {} problem(s){}",
				m.themes.size(), new HashSet<>(m.themes.values()).size(), surges, endings, events.size(), files, simChecks,
				otherModules ? "checked (" + missing + " unknown)" : "skipped (standalone)", bad + missing, msg);
			if (bad + missing > 0) System.err.println("SELFTEST boss_music FAILED");
		} catch (Exception e) {
			BossMusicService.LOGGER.error("SELFTEST boss_music crashed", e);
		}
		if (Boolean.getBoolean("boss_music.selftest.exit")) mc.stop();
	}
}
