package dev.elsewhere.boss_music;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * 3.0 fit-pass checks for the client self-test (s-music-fit), run on the merged sounds.json view the game itself uses:
 * <ul>
 * <li><b>fit: Aldermark realm ambient resolves</b>: {@code court_music.realm} has a sounds.json event and every file it lists
 * (Village Consort, Suonatore di Liuto) resolves. The merged jar drops kingdom_court's own sounds.json, so boss_music defines it.</li>
 * <li><b>fit: no orphan themes</b>: every {@code boss_music.theme.<slug>.*} event belongs to a theme some boss plays.</li>
 * </ul>
 * Returns the number of problems and appends one line per problem to {@code msg}.
 */
final class FitCheck {
	private FitCheck() {}

	static final String REALM_EVENT = "court_music.realm";
	private static final String THEME = "boss_music.theme.";

	static int run(Minecraft mc, JsonObject all, Set<Identifier> usedThemes, StringBuilder msg) {
		int bad = 0;
		if (!all.has(REALM_EVENT)) {
			bad++;
			msg.append("\n  fit: no sounds.json event ").append(REALM_EVENT).append(" (Aldermark realm ambient would be silent)");
		} else {
			int n = 0;
			for (var s : all.getAsJsonObject(REALM_EVENT).getAsJsonArray("sounds")) {
				String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
				Identifier file = Identifier.parse(name).withPrefix("sounds/").withSuffix(".ogg");
				if (mc.getResourceManager().getResource(file).isEmpty()) {
					bad++;
					msg.append("\n  fit: ").append(REALM_EVENT).append(" file missing ").append(file);
				}
				n++;
			}
			if (n == 0) {
				bad++;
				msg.append("\n  fit: ").append(REALM_EVENT).append(" lists no sounds");
			}
		}
		Set<String> orphans = new TreeSet<>();
		for (String key : all.keySet()) {
			if (!key.startsWith(THEME)) continue;
			String slug = key.substring(THEME.length()).split("\\.")[0];
			if (!usedThemes.contains(Identifier.fromNamespaceAndPath("elsewhere", THEME + slug))) orphans.add(slug);
		}
		if (!orphans.isEmpty()) {
			bad += orphans.size();
			msg.append("\n  fit: theme events no boss plays: ").append(orphans);
		}
		BossMusicService.LOGGER.info("SELFTEST boss_music fit: {} realm event {}, orphan themes {}; {} problem(s)", REALM_EVENT,
			all.has(REALM_EVENT) ? "defined" : "MISSING", orphans.size(), bad);
		return bad;
	}
}
