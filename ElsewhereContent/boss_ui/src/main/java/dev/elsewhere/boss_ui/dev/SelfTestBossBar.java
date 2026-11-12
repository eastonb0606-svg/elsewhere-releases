package dev.elsewhere.boss_ui.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.boss_ui.BossBarData;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;

/**
 * P1 boss bar v2 checks (s-p1-bossbar): real per-boss phase notches, the state chips, every family's title format, cast
 * bars, and the label pipeline (a raw move id or untranslated key is never shown; every label is a lang name whose
 * characters all have glyphs in the default font, and proper labels pass through char for char). Pure logic, no client.
 */
public final class SelfTestBossBar {
	private SelfTestBossBar() {}

	/** Each boss's phaseThresholds() (or its phase rule) as read from its source; boss_bars.json must agree. */
	private static final Map<String, float[]> CODE_PHASES = new HashMap<>();

	static {
		float[] p6030 = {0.6F, 0.3F}, p6530 = {0.65F, 0.3F};
		for (String b : new String[] {"gulpmother", "neferkhet", "rimeback", "vorlagh", "mournmaw", "ankhara", "heliodor", "kazehana", "auris", "grumblegut", "galvanox", "selenne"}) CODE_PHASES.put(b, p6030);
		for (String b : new String[] {"strawgrim", "ossyra", "tempestra", "lithara", "percolax", "verdigrim"}) CODE_PHASES.put(b, p6530);
		CODE_PHASES.put("solaxis", new float[] {0.66F, 0.33F});
		CODE_PHASES.put("aphelion", new float[] {0.70F, 0.35F});
		CODE_PHASES.put("hrolf", new float[] {0.50F, 0.20F});
		CODE_PHASES.put("thurgrim", new float[] {0.65F, 0.35F});
		CODE_PHASES.put("grunthar", new float[] {0.30F}); // phase 2 is Ashtusk's death (not an HP line), phase 3 below 30%
		CODE_PHASES.put("mother_creak", new float[0]); // phases come from her hearts, not HP
	}

	/** Every rival signature move id in the 15 boss_identity files (kingdom_duelist, rival_azmar, rival_hrafnholt, rival_mirewick). */
	private static final List<String> SIGNATURES = List.of("bash_chain", "bog_drum_call", "call_the_debt", "camel_charge", "counter_brew", "counter_riposte",
		"dike_wall", "dismount_lunge", "door_breaker", "dusk_bell", "dusk_rite", "falcon_mark", "fowling_net", "furrow_run", "garnish", "glyph_flare", "halo_flare",
		"hearth_horn", "hearth_ward", "heron_flight", "horn_of_three_calls", "javelin_volley", "long_thrust", "mud_surge", "ninth_winter_slam", "oath_cleave",
		"pay_the_hands", "pelt_dive", "pelt_leap", "perch_post", "pike_brace", "pinning_arrow", "raid_charge", "reed_dive", "rune_throw", "screen_and_step",
		"sluice_maul", "spear_vault", "three_beat_verse", "triple_stoop", "veil_of_the_ibis", "verse_of_winter", "wolf_howl", "wolf_pack");

	public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) throws Exception {
		Map<String, BossBarData.Entry> m;
		try (InputStream in = SelfTestBossBar.class.getResourceAsStream("/assets/elsewhere/boss_bars.json")) {
			m = BossBarData.parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
		}
		Map<String, String> lang = readLang();
		Function<String, String> tr = lang::get;

		// ---- phases: notches are the boss's own thresholds ----
		StringBuilder wrong = new StringBuilder();
		for (BossBarData.Entry e : m.values()) {
			float[] want = CODE_PHASES.get(e.path());
			if (want == null || !java.util.Arrays.equals(want, e.phases())) wrong.append(' ').append(e.path());
		}
		check.accept(wrong.isEmpty() && CODE_PHASES.size() == m.size(), "bossbar: every boss's phase notches match its phaseThresholds()" + wrong);
		boolean desc = true;
		for (BossBarData.Entry e : m.values()) for (int i = 0; i < e.phases().length; i++) {
			float p = e.phases()[i];
			if (p <= 0F || p >= 1F || i > 0 && p >= e.phases()[i - 1]) desc = false;
		}
		check.accept(desc, "bossbar: notches are strictly descending inside (0, 1)");
		check.accept(BossBarData.phasesFor(null).length == 0 && BossBarData.phasesFor(m.get("elsewhere:hrolf")).length == 2, "bossbar: unlisted bars (vanilla, rivals in Always mode) get no invented notches");
		check.accept(!m.get("elsewhere:grunthar").counter() && m.values().stream().filter(e -> !e.counter()).count() == 1 && m.values().stream().allMatch(e -> e.transition() == 40),
			"bossbar: phase counter hidden only for Grunthar; every boss has the 40-tick invulnerable transition");
		check.accept(BossBarData.phaseOf(1F, new float[] {0.6F, 0.3F}) == 0 && BossBarData.phaseOf(0.6F, new float[] {0.6F, 0.3F}) == 1 && BossBarData.phaseOf(0.29F, new float[] {0.6F, 0.3F}) == 2,
			"bossbar: phase index flips at the threshold (<=, like the bosses)");

		// ---- labels: never a raw id ----
		check.accept(BossBarData.label("glyph_flare", tr).equals("Glyph Flare") && BossBarData.label("ninth_winter_slam", tr).equals("Ninth-Winter Slam")
			&& BossBarData.label("oath_cleave", tr).equals("Cleave of the Oath") && BossBarData.label("bash_chain", tr).equals("Bash Chain"),
			"bossbar: the anim shot's raw ids (glyph_flare, ninth_winter_slam, oath_cleave, bash_chain) show their lang names");
		List<String> missing = new ArrayList<>();
		for (String id : SIGNATURES) if (!lang.containsKey(BossBarData.moveKey(id))) missing.add(id);
		Set<String> onPath = identitySignatures();
		for (String id : onPath) if (!lang.containsKey(BossBarData.moveKey(id))) missing.add(id);
		check.accept(missing.isEmpty(), "bossbar: all " + SIGNATURES.size() + " rival signature ids (+" + onPath.size() + " identity files on the classpath) have a lang name " + missing);
		check.accept(BossBarData.label("crescent", tr).equals("Crescent") && BossBarData.label("some_new_move", tr).equals("Some New Move")
			&& BossBarData.label("entity.elsewhere.test_duelist_ruler", tr).equals("Test Duelist Ruler"),
			"bossbar: unknown ids and untranslated keys are humanized, never shown raw");
		String[] proper = {"Seal of the Ibis", "Greed!", "Phase 2: Burning Effigy", "❤❤❤", "Stunned! Gizzards exposed", "Gudrun Ulfhednar", "Crafting"};
		boolean same = true;
		for (String s : proper) same &= BossBarData.label(s, tr).equals(s);
		check.accept(same, "bossbar: proper labels pass through char for char (no letter dropped, 'f' included)");
		int moves = 0, withF = 0;
		List<String> badLabels = new ArrayList<>();
		for (var e : lang.entrySet()) {
			if (!e.getKey().startsWith("elsewhere.move.") && !e.getKey().startsWith("elsewhere.bossbar.")) continue;
			String v = e.getValue();
			if (e.getKey().startsWith("elsewhere.move.")) moves++;
			if (v.indexOf('f') >= 0) withF++;
			boolean ok = !v.isBlank() && !BossBarData.isRaw(v) && Character.isUpperCase(v.charAt(0)) && BossBarData.label(v, tr).equals(v);
			for (int i = 0; i < v.length(); i++) if (v.charAt(i) < 0x20 || v.charAt(i) > 0x7E) ok = false; // glyphs of the default font's ascii page
			if (!ok) badLabels.add(e.getKey());
		}
		check.accept(moves >= SIGNATURES.size() && badLabels.isEmpty() && withF > 0,
			"bossbar: every label string (" + moves + " moves, " + withF + " with an 'f') is a capitalised name drawn only with default-font glyphs " + badLabels);
		boolean states = true;
		for (String s : BossBarData.STATES) states &= lang.containsKey("elsewhere.bossbar.state." + s);
		check.accept(states, "bossbar: the 4 state chips have lang names");

		// ---- every family's title format ----
		var nether = BossBarData.splitFor("Grunthar, the Gilded  ·  Gold Bombs", "Grunthar, the Gilded");
		var green = BossBarData.splitFor("Percolax  »  Coughing fit! Core exposed", "Percolax");
		var petal = BossBarData.splitFor("Tempestra, Eye of the Gale — Gust Volley", "Tempestra, Eye of the Gale");
		var creak = BossBarData.splitFor("Mother Creak  ❤❤❤", "Mother Creak");
		var rival = BossBarData.split("Test Duelist Ruler · glyph_flare");
		check.accept(nether.attack().equals("Gold Bombs") && green.attack().equals("Coughing fit! Core exposed") && petal.attack().equals("Gust Volley")
			&& petal.name().equals("Tempestra, Eye of the Gale") && creak.attack().equals("❤❤❤") && rival.name().equals("Test Duelist Ruler") && rival.attack().equals("glyph_flare")
			&& BossBarData.split("Tempestra — Phase 2").attack().equals("Phase 2") && BossBarData.split("Ender Dragon").attack().isEmpty(),
			"bossbar: name and attack split for every family (· core/nether/rivals, » greenhaze, — petalwind, Mother Creak hearts)");

		// ---- state chips ----
		BossBarData.Entry creakE = m.get("elsewhere:mother_creak");
		check.accept(BossBarData.VULNERABLE.equals(BossBarData.stateOf("Stunned! Gizzards exposed", null)) && BossBarData.VULNERABLE.equals(BossBarData.stateOf("Crystal armor shattered!", null))
			&& BossBarData.VULNERABLE.equals(BossBarData.stateOf("Armor cracked (4)", null)) && BossBarData.ENRAGED.equals(BossBarData.stateOf("Last Stand", null))
			&& BossBarData.ENRAGED.equals(BossBarData.stateOf("Hrolf berserks!", null)) && BossBarData.WARDED.equals(BossBarData.stateOf("❤❤", creakE))
			&& BossBarData.INVULNERABLE.equals(BossBarData.stateOf("Invulnerable", null)),
			"bossbar: state chips read from the title words already sent (vulnerable, enraged, warded, invulnerable)");
		check.accept(BossBarData.stateOf("Gold Bombs", null) == null && BossBarData.stateOf("Glyph Flare", null) == null && BossBarData.stateOf("", null) == null
			&& BossBarData.stateOf("❤❤", null) == null && BossBarData.stateOf(BossBarData.splitFor("Wolf-Coat, Chief of the Berserkers · Pelt Leap", "Wolf-Coat, Chief of the Berserkers").attack(), null) == null,
			"bossbar: plain attacks and words inside a boss's own name give no chip");

		// ---- overworld cast bars ----
		List<String> owners = BossBarData.subOwners(m, "⚠ Reaping Sweep").stream().map(BossBarData.Entry::path).toList();
		check.accept(BossBarData.isCastBar("⚠ Reaping Sweep") && owners.equals(List.of("gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh")) && !BossBarData.isCastBar("Strawgrim"),
			"bossbar: the overworld '⚠' cast bar attaches to whichever overworld boss is on screen " + owners);
		check.accept(BossBarData.subOwners(m, "Tentacles (3 of 4 standing)").get(0).path().equals("vorlagh") && BossBarData.subOwners(m, "Ender Dragon").isEmpty(),
			"bossbar: tentacle sub-bar still attaches to Vorlagh; vanilla titles have no owner");
		var noPhases = BossBarData.parse(JsonParser.parseString("{\"bosses\":{\"elsewhere:y\":{\"color\":\"#FFFFFF\",\"states\":{\"Glow\":\"warded\",\"x\":\"bogus\"}}}}").getAsJsonObject()).get("elsewhere:y");
		check.accept(noPhases.phases().length == 2 && noPhases.phases()[0] == 0.66F && noPhases.states().size() == 1 && BossBarData.WARDED.equals(noPhases.states().get("glow")) && noPhases.counter(),
			"bossbar: JSON defaults (66/33 notches, counter on) and unknown states are dropped");
	}

	/** Every en_us.json on the classpath merged, the first copy winning per key (as the merged jar and the resource stack do). */
	private static Map<String, String> readLang() throws Exception {
		Map<String, String> out = new HashMap<>();
		Enumeration<URL> urls = SelfTestBossBar.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
		while (urls.hasMoreElements()) {
			try (InputStream in = urls.nextElement().openStream()) {
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (var e : o.entrySet()) out.putIfAbsent(e.getKey(), e.getValue().getAsString());
			}
		}
		return out;
	}

	/** Signature ids from any boss_identity files on the classpath (the merged jar has all 15; the boss_ui dev run has none). */
	private static Set<String> identitySignatures() {
		Set<String> ids = new LinkedHashSet<>();
		String[] files = {"azmar_amir", "azmar_falconer", "azmar_sultana", "azmar_wazir", "hrafnholt_chief", "hrafnholt_hallwarden", "hrafnholt_hersir",
			"hrafnholt_huntmaster", "hrafnholt_jarl", "hrafnholt_skald", "mirewick_ames", "mirewick_marrow", "mirewick_sorrel", "mirewick_thwaite", "mirewick_tolliver"};
		for (String f : files) {
			try (InputStream in = SelfTestBossBar.class.getResourceAsStream("/data/elsewhere/boss_identity/" + f + ".json")) {
				if (in == null) continue;
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				if (o.has("signature")) o.getAsJsonArray("signature").forEach(s -> ids.add(s.getAsString()));
			} catch (Exception ignored) {}
		}
		return ids;
	}
}
