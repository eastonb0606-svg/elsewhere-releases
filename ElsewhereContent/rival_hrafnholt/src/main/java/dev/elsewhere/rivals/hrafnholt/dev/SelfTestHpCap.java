package dev.elsewhere.rivals.hrafnholt.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * 3.0 final review B8 (s-dataclean): the shipped faction data itself keeps every human boss at or under 300 health
 * (RIVALS-DECISIONS 13) and every Serf tier at 18 (section 14). The runtime clamps (HumanBossHealth, HpCap, CourtRulers) hid a
 * {@code "health_cap": 560} in hrafnholt.json, so this reads every {@code data/<ns>/kingdom_faction/*.json} the server loaded (the
 * winning copy, as the game sees it) and the parsed FactionDef behind it. In this module's run that is Aldermark and Hrafnholt;
 * in the merged jar it is all four kingdoms. Runtime caps per boss stay in SelfTestBosses ("HP cap: ...").
 */
final class SelfTestHpCap {
	private SelfTestHpCap() {}

	static final double HUMAN_BOSS_CAP = 300.0, SERF_HP = 18.0;

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			Map<Identifier, Resource> files = new LinkedHashMap<>();
			srv.getResourceManager().listResources("kingdom_faction", id -> id.getPath().endsWith(".json")).entrySet().stream()
				.sorted(Comparator.comparing(en -> en.getKey().toString())).forEach(en -> files.put(en.getKey(), en.getValue()));
			List<String> names = new ArrayList<>();
			for (Identifier f : files.keySet()) names.add(f.getNamespace() + ":" + f.getPath().substring("kingdom_faction/".length(), f.getPath().length() - 5));
			Identifier hraf = Identifier.fromNamespaceAndPath(HrafnholtContent.FACTION.getNamespace(), "kingdom_faction/" + HrafnholtContent.FACTION.getPath() + ".json");
			SelfTest.check("B8 human-boss HP data: kingdom_faction files found, Hrafnholt among them", files.containsKey(hraf), names);
			for (Map.Entry<Identifier, Resource> e : files.entrySet()) {
				Identifier file = e.getKey();
				String path = file.getPath();
				Identifier fid = Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring("kingdom_faction/".length(), path.length() - 5));
				JsonObject root;
				try (BufferedReader r = e.getValue().openAsReader()) {
					root = JsonParser.parseReader(r).getAsJsonObject();
				} catch (Exception ex) {
					SelfTest.check("B8 human-boss HP data: " + fid + " faction file parses", false, ex);
					continue;
				}
				// raw file: every ruler's health and health_cap
				List<String> bad = new ArrayList<>(), seen = new ArrayList<>();
				JsonElement rulers = root.get("rulers");
				if (rulers != null && rulers.isJsonObject()) {
					for (Map.Entry<String, JsonElement> re : rulers.getAsJsonObject().entrySet()) {
						if (!re.getValue().isJsonObject()) continue;
						JsonObject ro = re.getValue().getAsJsonObject();
						double hp = num(ro, "health"), cap = num(ro, "health_cap");
						seen.add(re.getKey() + "=" + fmt(hp) + (Double.isNaN(cap) ? "" : "/" + fmt(cap)));
						if (hp > HUMAN_BOSS_CAP || cap > HUMAN_BOSS_CAP) bad.add(re.getKey() + " " + fmt(hp) + (Double.isNaN(cap) ? "" : " cap " + fmt(cap)));
					}
				}
				SelfTest.check("B8 human-boss HP data: " + fid + " file: every ruler's health and health_cap at most 300", bad.isEmpty(), bad.isEmpty() ? String.join(" ", seen) : "over 300: " + bad);
				// the same faction as the kingdom module parsed it (a JSON file wins over a code-defined test faction with that id)
				FactionDef def = FactionDefs.get(fid);
				if (def == null) {
					SelfTest.check("B8 human-boss HP data: " + fid + " is loaded by FactionDefs", false, null);
					continue;
				}
				List<String> badDef = new ArrayList<>();
				for (Map.Entry<String, FactionDef.RulerDef> rd : def.rulers().entrySet()) {
					FactionDef.RulerDef d = rd.getValue();
					if (d.health() > HUMAN_BOSS_CAP || d.healthCap().orElse(0.0) > HUMAN_BOSS_CAP) badDef.add(rd.getKey() + " " + fmt(d.health()) + d.healthCap().map(c -> " cap " + fmt(c)).orElse(""));
				}
				SelfTest.check("B8 human-boss HP data: " + fid + " parsed rulers (" + def.rulers().size() + ") at most 300 health and cap", badDef.isEmpty(), badDef.isEmpty() ? null : badDef);
				// RIVALS-DECISIONS 14: Serf tier 18 in every kingdom (a missing serfs block would fall back to the codec default)
				double serf = def.society().serfs().health();
				JsonElement soc = root.get("society");
				boolean inFile = soc != null && soc.isJsonObject() && soc.getAsJsonObject().has("serfs");
				SelfTest.check("B8 serf tier data: " + fid + " Serf-tier health is 18" + (inFile ? "" : " (no serfs block: codec default)"), serf == SERF_HP, serf);
			}
			// the 2.x Jarl number is gone from the shipped file, not only clamped at runtime
			FactionDef h = FactionDefs.get(HrafnholtContent.FACTION);
			FactionDef.RulerDef jarl = h == null ? null : h.rulers().get("king");
			SelfTest.check("B8 human-boss HP data: High Jarl health 300 and health_cap 300 in hrafnholt.json (2.x file had 560)",
				jarl != null && jarl.health() == 300.0 && jarl.healthCap().orElse(-1.0) == 300.0, jarl == null ? null : jarl.health() + " / " + jarl.healthCap());
			return 0;
		});
	}

	static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : Double.NaN;
	}

	static String fmt(double d) {
		return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
	}
}
