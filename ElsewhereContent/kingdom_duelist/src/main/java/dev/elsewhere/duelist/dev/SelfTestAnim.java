package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.boss.MovePose;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TestDuelist;

/**
 * 3.0 (P4) move animations: the pose classifier covers every built-in move, and the synced move id (the only thing the
 * client animation reads) is set when a wind-up starts and cleared when the move fires.
 */
final class SelfTestAnim {
	private SelfTestAnim() {}

	/** Every move id the 15 rival bosses and the base kit use (2.1 and 3.0 sources). */
	static final String[] BUILT_IN = {"swing", "bash", "crit", "throw", "pearl", "last_stand",
			"crescent", "hammer_anvil", "feigned_retreat", "falcon_mark", "triple_stoop", "perch_post", "falcon_storm", "screen_and_step", "counter_riposte", "halo_flare",
			"scarab_shell", "camel_charge", "javelin_volley", "dismount_lunge", "final_charge", "counter_brew", "glyph_flare", "veil_of_the_ibis", "seal_of_ibis",
			"ninth_winter_slam", "oath_cleave", "hearth_horn", "raid_charge", "spear_vault", "horn_of_three_calls", "bash_chain", "hearth_ward", "door_breaker",
			"rune_throw", "three_beat_verse", "verse_of_winter", "furrow_run", "pelt_leap", "wolf_howl", "wolf_pack", "pinning_arrow", "pelt_dive",
			"paid_passage", "lamp_shove", "oil_flask", "lantern_rally", "double_debt", "mire_flood", "sluice_open", "duck_blind", "scrip_scatter", "call_the_debt",
			"garnish", "long_thrust", "pike_brace", "bog_drum_call", "dike_wall", "sluice_maul", "mud_surge", "fowling_net", "heron_flight", "reed_dive", "dusk_rite",
			"pay_the_hands", "dusk_bell", "split_command", "hire_answer", "crossbow_volley"};

	static void add(H h) {
		h.test("3.0 move animation keys").check("every built-in move id has a pose key (" + BUILT_IN.length + " ids)", () -> {
			for (String id : BUILT_IN) if (!MovePose.covered(id) || MovePose.of(id, false, false) == MovePose.NONE) {
				System.out.println("[duelist-selftest] INFO uncovered move id " + id);
				return false;
			}
			return true;
		}).check("every pose key is used by a built-in move (" + (MovePose.NAMES.length - 1) + " keys)", () -> {
			boolean[] seen = new boolean[MovePose.NAMES.length];
			for (String id : BUILT_IN) seen[MovePose.of(id, false, false)] = true;
			for (int k = 1; k < seen.length; k++) if (!seen[k]) return false;
			return true;
		}).check("unknown ids fall back on keywords, then on the cue", () -> MovePose.of("frost_quake", false, false) == MovePose.SLAM
				&& MovePose.of("zzz", true, false) == MovePose.THRUST && MovePose.of("zzz", false, true) == MovePose.OVERHEAD
				&& MovePose.of("zzz", false, false) == MovePose.SWEEP && MovePose.of("", false, true) == MovePose.NONE && MovePose.of(null, false, false) == MovePose.NONE);
		final TestDuelist[] b = new TestDuelist[1];
		final String[] seen = new String[2];
		h.test("3.0 move id sync (one change at start, one at fire)").run(() -> {
			b[0] = h.boss(H.base("anim").build(), 0, 0);
			b[0].passive = true;
			b[0].startTelegraph("oath_cleave", "Oath Cleave", 10, Telegraph.Weight.ORDINARY, 0F, b[0].style().cue(false, true), true, () -> {});
			seen[0] = b[0].action();
		}).wait(14).check("the id is set at wind-up start (" + "oath_cleave" + ")", () -> "oath_cleave".equals(seen[0]))
			.check("and cleared once the move has fired", () -> b[0].action().isEmpty());
	}
}
