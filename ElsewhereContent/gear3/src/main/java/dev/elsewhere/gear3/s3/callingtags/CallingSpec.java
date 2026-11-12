package dev.elsewhere.gear3.s3.callingtags;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The calling tags every GEAR.md A5 item must be in, and no others (s-callingtags). This is the self-test's own copy of
 * {@code gear3/tools/calling_tags.py} (which writes the tag files through build_catalogue.py and gen_resources.py), so a drift on
 * either side fails {@link CallingTagChecks}. Tag paths are in the {@code elsewhere} namespace:
 * <ul>
 * <li>{@code calling/<path>}: Leaning armor ({@code Calling.Path.leans}); Caster and Summoner use the 3.0 {@code calling/caster} and
 * {@code calling/summoner}, never the 2.1 {@code caster_armor} / {@code summoner_armor} (those keep the 2.1 cons with callings off);</li>
 * <li>{@code calling_weapon/<path>}: calling items in the main hand or off hand ({@code Calling.itemPath}; Vanguard multiplies
 * main-hand melee, Marksman multiplies arrows while in either hand);</li>
 * <li>{@code caster_trinket}: a trinket whose active path is Caster.</li>
 * </ul>
 * An empty list means "in no calling tag", on purpose: shields (no damage), the Mead-Horn (a drink), Vanguard and Marksman trinkets
 * (an off-hand trinket in calling_weapon/marksman would scale a vanilla bow; the Oath Ring would satisfy its own "holding a Vanguard
 * weapon"), ammo and the Mire Snare (an off-hand arrow stack would scale a vanilla bow, like kingdom's Bodkin Bolt which is untagged),
 * and the shared materials and tools.
 */
public final class CallingSpec {
	private CallingSpec() {}

	/** Every calling-related item tag the checks look at (the last two must never hold a gear3 item). */
	public static final List<String> TAGS = List.of("calling/vanguard", "calling/marksman", "calling/caster", "calling/summoner", "calling_weapon/vanguard",
		"calling_weapon/marksman", "calling_weapon/caster", "calling_weapon/summoner", "caster_trinket", "caster_armor", "summoner_armor");

	/** GEAR.md A5 id -> expected calling tags (paths in {@link #TAGS}). 82 entries. */
	public static final Map<String, List<String>> SPEC;

	private static final String V = "vanguard", M = "marksman", C = "caster", S = "summoner";

	private static List<String> lean(String... paths) {
		return prefixed("calling/", paths);
	}

	private static List<String> held(String... paths) {
		return prefixed("calling_weapon/", paths);
	}

	private static List<String> prefixed(String prefix, String... paths) {
		String[] out = new String[paths.length];
		for (int i = 0; i < paths.length; i++) out[i] = prefix + paths[i];
		return List.of(out);
	}

	private static final List<String> NONE = List.of();

	static {
		Map<String, List<String>> m = new LinkedHashMap<>();
		// Vanguard (melee)
		for (String id : new String[] {"galeplate_helmet", "galeplate_chestplate", "galeplate_leggings", "galeplate_boots"}) m.put(id, lean(V));
		m.put("mirewick_lanternfang_estoc", held(V));
		m.put("aldermark_parrying_buckler", NONE);
		for (String id : new String[] {"hrafnholt_hearthguard_helm", "hrafnholt_hearthguard_greaves", "hrafnholt_hearthguard_boots"}) m.put(id, lean(V));
		m.put("hrafnholt_skjaldborg_shield", NONE);
		m.put("bellbreaker_maul", held(V));
		m.put("hrafnholt_ulfhednar_mask", lean(V));
		m.put("hrafnholt_ulfhednar_breeches", lean(V));
		m.put("hrafnholt_huginns_bite", held(V));
		m.put("hrafnholt_mead_horn", NONE);
		m.put("hrafnholt_runeseax", held(V, C)); // GEAR.md: in both calling_weapon/vanguard and the caster focus tag
		m.put("azmar_glyphmail_hood", lean(V, C));
		m.put("azmar_glyphmail_hauberk", lean(V, C));
		m.put("aldermark_outrider_chaps", lean(V, M));
		m.put("aldermark_outrider_boots", lean(V, M));
		m.put("aldermark_oath_ring", NONE);
		// Marksman (ranged)
		for (String id : new String[] {"ridgewatch_hood", "ridgewatch_coat", "ridgewatch_leggings", "ridgewatch_boots"}) m.put(id, lean(M));
		m.put("aphelion_starpiercer", held(M));
		m.put("rangefinder_lens", NONE);
		m.put("hrafnholt_hurlbat", held(M));
		m.put("gale_sling", held(M));
		m.put("aldermark_quickdraw_quiver", NONE);
		m.put("sunglass_arrow", NONE);
		m.put("echo_arrow", NONE);
		m.put("tidebrass_harpoon_bolt", NONE);
		m.put("mirewick_mire_snare", NONE);
		m.put("mirewick_fowlers_bandolier", NONE);
		m.put("prism_bow", held(M, C));
		m.put("glasswaste_starsight_visor", lean(M, C));
		m.put("glasswaste_prism_mantle", lean(M, C));
		m.put("azmar_falconer_kilt", lean(M, S));
		// Caster (spells; every CasterItem is a Caster calling item, as in arsenal and gear2)
		for (String id : new String[] {"sunscribe_hood", "sunscribe_robe", "sunscribe_leggings", "sunscribe_boots"}) m.put(id, lean(C));
		for (String id : new String[] {"emberfall_rod", "starfall_orrery", "amber_grimoire", "frostwake_scepter"}) m.put(id, held(C));
		m.put("hush_binding_sigil", List.of("caster_trinket"));
		for (String id : new String[] {"choirlight_censer", "mirewick_almoners_chalice", "azmar_ka_sigil"}) m.put(id, held(C));
		m.put("mirewick_will_o_lantern", held(C, S));
		m.put("mirewick_lamplighter_hood", lean(C, S));
		m.put("mirewick_lamplighter_coat", lean(C, S));
		m.put("glimmer_lantern", held(C));
		m.put("blinkstone", held(C));
		// Summoner (minions, whips, crooks, totems, standards)
		for (String id : new String[] {"thornlash", "tidechain_lash", "azmar_sunscourge", "echo_chord", "mothlight_staff", "bramble_totem", "herdcaller_pennant",
			"ironpeak_war_yak_horn", "echo_reliquary"}) m.put(id, held(S));
		for (String id : new String[] {"elderbark_mantle", "elderbark_leggings", "elderbark_boots", "court_marshals_helm", "court_marshals_tabard", "court_marshals_tassets"})
			m.put(id, lean(S));
		m.put("mirewick_lantern_crook", held(S));
		m.put("clockwork_sentry", held(S));
		m.put("azmar_sunglass_obelisk", held(S));
		// V+S hybrid from the Summoner section: Summoner only (calling_weapon/vanguard would win Calling.itemPath and make it a Vanguard melee weapon)
		m.put("hrafnholt_wolfcall_horn", held(S));
		m.put("hrafnholt_hird_leggings", lean(V, S));
		m.put("hrafnholt_hird_boots", lean(V, S));
		// Shared materials and tools: no Calling
		for (String id : new String[] {"gear3_forging_template", "gear3_ascension_shard", "aldermark_masons_chisel", "tidebrass_dredge", "mirewick_lantern_sickle"})
			m.put(id, NONE);
		SPEC = Collections.unmodifiableMap(m);
	}

	/** The path {@code Calling.itemPath} must read for a held item (it checks Vanguard, Marksman, Caster or caster_trinket, Summoner in that order), or null. */
	public static String heldPath(List<String> tags) {
		for (String p : new String[] {V, M, C, S}) {
			if (tags.contains("calling_weapon/" + p)) return p;
			if (p.equals(C) && tags.contains("caster_trinket")) return p;
		}
		return null;
	}
}
