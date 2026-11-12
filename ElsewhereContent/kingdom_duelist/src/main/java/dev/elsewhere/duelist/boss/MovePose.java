package dev.elsewhere.duelist.boss;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 3.0 (P4/B4): the body-language key of a human boss's move, synced as ONE byte ({@code HumanBoss.DATA_POSE}).
 * The server picks the key when a wind-up starts (a signature move's own {@link SignatureMove#pose}, the plain swing by
 * the weapon in hand, otherwise this table), sets the strike bit when the move fires and clears the byte a few ticks
 * later. The byte changes three times per move and never per tick; the client plays the keyframes from game time.
 * <pre>bits 0-4 key (0..31), bit 5 STRIKE (the move fired), bit 6 SEQ (flips per wind-up so back-to-back moves always change it)</pre>
 * Pure functions, shared by the server, the client animation and the self-tests.
 */
public final class MovePose {
	private MovePose() {}

	public static final byte NONE = 0, OVERHEAD = 1, SWEEP = 2, THRUST = 3, SLAM = 4, LEAP = 5, BASH = 6, DRAW = 7, CAST = 8,
			HORN = 9, HOWL = 10, COMMAND = 11, INVOKE = 12, BRACE = 13, DRINK = 14, DRUM = 15, VEIL = 16;
	public static final String[] NAMES = {"none", "overhead", "sweep", "thrust", "slam", "leap", "bash", "draw", "cast",
			"horn", "howl", "command", "invoke", "brace", "drink", "drum", "veil"};

	public static final int KEY_MASK = 0x1F, STRIKE = 0x20, SEQ = 0x40;
	/** Ticks the strike bit stays on after a move fires (the client times its own snap and recovery from the change). */
	public static final int STRIKE_TICKS = 12;

	private static final Map<String, Byte> KNOWN = new HashMap<>();

	private static void put(byte k, String... ids) {
		for (String id : ids) KNOWN.put(id, k);
	}

	static {
		// base kit and test moves
		put(OVERHEAD, "swing", "look", "oath_cleave");
		put(SWEEP, "crescent", "three_beat_verse");
		put(THRUST, "crit", "counter_riposte", "camel_charge", "final_charge", "long_thrust", "raid_charge");
		put(SLAM, "test_slam", "hammer_anvil", "ninth_winter_slam", "mire_flood", "sluice_open", "mud_surge", "last_stand", "sluice_maul");
		put(LEAP, "pearl", "feigned_retreat", "dismount_lunge", "spear_vault", "pelt_leap", "pelt_dive", "heron_flight", "reed_dive", "furrow_run");
		put(BASH, "bash", "lamp_shove", "bash_chain", "door_breaker");
		put(DRAW, "throw", "javelin_volley", "crossbow_volley", "oil_flask", "scrip_scatter", "rune_throw", "pinning_arrow", "fowling_net");
		put(CAST, "pillar", "boat", "water", "falcon_azmar", "perch_post", "glyph_flare", "seal_of_ibis");
		// 3.0 B4: signature gestures that are not weapon swings
		put(HORN, "hearth_horn", "horn_of_three_calls", "dusk_bell");
		put(HOWL, "wolf_howl", "wolf_pack");
		put(COMMAND, "falcon_mark", "triple_stoop", "split_command", "hire_answer", "paid_passage", "double_debt", "call_the_debt", "pay_the_hands", "garnish");
		put(INVOKE, "halo_flare", "falcon_storm", "dusk_rite", "lantern_rally", "verse_of_winter");
		put(BRACE, "hearth_ward", "pike_brace", "scarab_shell", "dike_wall");
		put(DRINK, "counter_brew");
		put(DRUM, "bog_drum_call");
		put(VEIL, "veil_of_the_ibis", "screen_and_step", "duck_blind");
	}

	/** Keywords for ids that are not in the table (new signature moves keep working without a table edit). Checked in order. */
	private static final String[][] WORDS = {
			{"horn", "bell"},
			{"howl"},
			{"drum"},
			{"veil", "blind", "screen"},
			{"brew", "drink", "quaff"},
			{"brace", "ward", "wall", "shell"},
			{"slam", "smash", "quake", "stomp", "flood", "surge", "maul"},
			{"bash", "shove", "shield"},
			{"thrust", "lunge", "stab", "riposte", "charge", "pierce", "skewer"},
			{"throw", "volley", "arrow", "javelin", "flask", "shot", "toss", "net", "bolt", "scatter"},
			{"leap", "vault", "dive", "hop", "jump", "pearl", "flight", "pounce", "retreat", "run"},
			{"sweep", "crescent", "arc", "spin", "whirl", "reap"},
			{"cleave", "chop", "strike", "breaker", "swing", "hew"},
			{"command", "mark", "debt", "order", "pay", "hire", "passage", "stoop"},
			{"rite", "rally", "storm", "halo", "verse"},
			{"call", "glyph", "flare", "seal", "curse", "summon"}};
	private static final byte[] WORD_KEYS = {HORN, HOWL, DRUM, VEIL, DRINK, BRACE, SLAM, BASH, THRUST, DRAW, LEAP, SWEEP, OVERHEAD, COMMAND, INVOKE, CAST};

	/** The pose key of a move id; unknown ids fall back on keywords, then on the wind-up cue (crouch: thrust, raised arm: overhead). */
	public static byte of(String id, boolean crouchCue, boolean raisedCue) {
		if (id == null || id.isEmpty()) return NONE;
		Byte k = KNOWN.get(id);
		if (k != null) return k;
		for (int i = 0; i < WORDS.length; i++) for (String w : WORDS[i]) if (id.contains(w)) return WORD_KEYS[i];
		if (crouchCue) return THRUST;
		return raisedCue ? OVERHEAD : SWEEP;
	}

	/** True when the id is covered by the table or a keyword. */
	public static boolean covered(String id) {
		if (KNOWN.containsKey(id)) return true;
		for (String[] ws : WORDS) for (String w : ws) if (id.contains(w)) return true;
		return false;
	}

	/** True when the id has its own table entry (the B4 audit asks this of every boss move: no guessing). */
	public static boolean explicit(String id) {
		return id != null && KNOWN.containsKey(id);
	}

	/**
	 * The plain swing by the weapon in hand, so each boss's basic attack reads as its weapon: axes chop (overhead, overhead, sweep),
	 * spears and pikes stab (thrust, thrust, sweep with the shaft), maces and mauls come down hard (overhead, slam), khopeshes hook
	 * (sweep, overhead, sweep), swords and everything else cycle overhead, sweep, thrust. {@code n} counts this boss's swings.
	 */
	public static byte swing(ItemStack weapon, int n) {
		String p = weapon == null || weapon.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
		int i = Math.floorMod(n, 3);
		if (p.endsWith("_axe") || p.equals("axe")) return i == 2 ? SWEEP : OVERHEAD;
		if (p.contains("spear") || p.contains("pike") || p.contains("trident") || p.contains("halberd")) return i == 2 ? SWEEP : THRUST;
		if (p.contains("mace") || p.contains("maul") || p.contains("hammer")) return i == 1 ? SLAM : OVERHEAD;
		if (p.contains("khopesh")) return i == 1 ? OVERHEAD : SWEEP;
		if (p.contains("shovel")) return i == 1 ? OVERHEAD : SWEEP;
		return i == 0 ? OVERHEAD : i == 1 ? SWEEP : THRUST;
	}

	// ---- the synced byte ------------------------------------------------------------------------------------------------

	/** The wind-up byte of a key; {@code seq} alternates per wind-up. */
	public static byte pack(byte key, boolean seq) {
		return (byte) ((key & KEY_MASK) | (seq ? SEQ : 0));
	}

	public static byte key(byte b) {
		return (byte) (b & KEY_MASK);
	}

	public static boolean struck(byte b) {
		return (b & STRIKE) != 0;
	}

	/**
	 * What the boss syncs this tick: the running wind-up's byte; after it fired, the same byte with the strike bit for
	 * {@link #STRIKE_TICKS}; otherwise 0 (also after a stagger cancelled the wind-up, so no strike plays).
	 */
	public static byte synced(boolean windupActive, byte telegraphPose, int ticksSinceStrike) {
		if (windupActive) return telegraphPose;
		if (struck(telegraphPose) && ticksSinceStrike >= 0 && ticksSinceStrike <= STRIKE_TICKS) return telegraphPose;
		return 0;
	}

	public static String name(byte k) {
		return k >= 0 && k < NAMES.length ? NAMES[k] : "none";
	}
}
