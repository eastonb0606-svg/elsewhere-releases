package dev.elsewhere.kingdom_court;

/**
 * 3.0 overhaul kill switches for the court (BUILD-3.0 "Kill switches"). Each key defaults on; turn one off with the JVM flag
 * {@code -Delsewhere.overhaul3.<key>=false} (or flip the constant) and that feature is gone without touching other code.
 */
public final class Overhaul3 {
	private Overhaul3() {}

	/** {@code castle_kit}: faction architecture dressing of new realm castles (roofs, spires, keep signatures, wall detail; see CastleKit). */
	public static volatile boolean CASTLE_KIT = on("castle_kit");
	/** {@code town_kit}: faction roofs and details of the two market towns of new realms (see CastleKit). */
	public static volatile boolean TOWN_KIT = on("town_kit");
	// volatile, not final (like RealmFit.ENABLED), only so SelfTestValuables can sweep every switch combination; the JVM flag sets them at start
	/** {@code trader_screen}: the trader dialogue screen (POLISH P7, s-p7-standing; standing/TraderDialogue). Off: the 2.1 chat menu alone. */
	public static volatile boolean TRADER_SCREEN = on("trader_screen");
	/** {@code standing_chip}: the Standing chip when looking at a ruler, and its query answer (POLISH P7, s-p7-standing; standing/StandingQuery). */
	public static volatile boolean STANDING_CHIP = on("standing_chip");
	// these two are volatile so SelfTestStanding can prove each switch removes its feature

	static boolean on(String key) {
		return !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3." + key, "true"));
	}
}
