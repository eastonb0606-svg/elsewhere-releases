package dev.elsewhere.kingdom_court;

/**
 * 3.0 overhaul kill switches for the court (BUILD-3.0 "Kill switches"). Each key defaults on; turn one off with the JVM flag
 * {@code -Delsewhere.overhaul3.<key>=false} (or flip the constant) and that feature is gone without touching other code.
 */
public final class Overhaul3 {
	private Overhaul3() {}

	/** {@code castle_kit}: faction architecture dressing of new realm castles (roofs, spires, keep signatures, wall detail; see CastleKit). */
	public static final boolean CASTLE_KIT = on("castle_kit");
	/** {@code town_kit}: faction roofs and details of the two market towns of new realms (see CastleKit). */
	public static final boolean TOWN_KIT = on("town_kit");

	static boolean on(String key) {
		return !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3." + key, "true"));
	}
}
