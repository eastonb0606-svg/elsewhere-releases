package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomContent;

/**
 * 3.0 soldier caps (ARMIES.md 9.4). A cap is raised only while the server's kingdom config still holds the 2.1 default, so an
 * owner's custom value always wins. The kingdom self-test ({@code -Dkingdom.selftest=true}) pins the 2.1 caps because its cap
 * checks assert those exact numbers; the armies self-test verifies these.
 */
public final class ArmyCaps {
	private ArmyCaps() {}

	public static final int OLD_PER_PLAYER = 15, OLD_PER_AREA = 30, OLD_GLOBAL = 120, OLD_SQUAD = 16;
	/** Preset "epic" (default): full-AI soldiers near one player, live soldiers per area, per server, squad size. */
	public static final int PER_PLAYER = 32, PER_AREA = 64, GLOBAL = 192, SQUAD = 20;
	/** Preset "fallback" ({@code "caps": "fallback"} in elsewhere_armies.json): 24 per player, 48 per area, 160 per server. */
	public static final int FB_PER_PLAYER = 24, FB_PER_AREA = 48, FB_GLOBAL = 160;

	static int perPlayer() {
		return ArmiesConfig.fallbackCaps ? FB_PER_PLAYER : PER_PLAYER;
	}

	static int perArea() {
		return ArmiesConfig.fallbackCaps ? FB_PER_AREA : PER_AREA;
	}

	static int global() {
		return ArmiesConfig.fallbackCaps ? FB_GLOBAL : GLOBAL;
	}

	public static String state = "not applied";

	static void apply() {
		if (ArmiesConfig.otherSelfTest()) {
			state = "pinned to the 2.1 caps for another module's self-test";
			return;
		}
		StringBuilder kept = new StringBuilder();
		if (KingdomConfig.maxRealSoldiersPerPlayer == OLD_PER_PLAYER) KingdomConfig.maxRealSoldiersPerPlayer = perPlayer();
		else kept.append(" perPlayer=").append(KingdomConfig.maxRealSoldiersPerPlayer);
		if (KingdomConfig.maxRealSoldiersPerArea == OLD_PER_AREA) KingdomConfig.maxRealSoldiersPerArea = perArea();
		else kept.append(" perArea=").append(KingdomConfig.maxRealSoldiersPerArea);
		if (KingdomConfig.maxRealSoldiersGlobal == OLD_GLOBAL) KingdomConfig.maxRealSoldiersGlobal = global();
		else kept.append(" global=").append(KingdomConfig.maxRealSoldiersGlobal);
		if (KingdomConfig.maxSquadSize == OLD_SQUAD) KingdomConfig.maxSquadSize = SQUAD;
		else kept.append(" squad=").append(KingdomConfig.maxSquadSize);
		state = "3.0 caps (" + (ArmiesConfig.fallbackCaps ? "fallback" : "epic") + " preset): " + KingdomConfig.maxRealSoldiersPerPlayer + " per player, " + KingdomConfig.maxRealSoldiersPerArea + " per area, "
			+ KingdomConfig.maxRealSoldiersGlobal + " per server, squads of " + KingdomConfig.maxSquadSize + (kept.isEmpty() ? "" : " (custom cap kept:" + kept + ")");
		KingdomContent.LOG.info("Armies: {}", state);
	}

	/** Kill switch off: if a saved kingdom config carries exactly our 3.0 values, put the 2.1 defaults back. */
	static void restoreIfOurs() {
		if (KingdomConfig.maxRealSoldiersPerPlayer == PER_PLAYER || KingdomConfig.maxRealSoldiersPerPlayer == FB_PER_PLAYER) KingdomConfig.maxRealSoldiersPerPlayer = OLD_PER_PLAYER;
		if (KingdomConfig.maxRealSoldiersPerArea == PER_AREA || KingdomConfig.maxRealSoldiersPerArea == FB_PER_AREA) KingdomConfig.maxRealSoldiersPerArea = OLD_PER_AREA;
		if (KingdomConfig.maxRealSoldiersGlobal == GLOBAL || KingdomConfig.maxRealSoldiersGlobal == FB_GLOBAL) KingdomConfig.maxRealSoldiersGlobal = OLD_GLOBAL;
		if (KingdomConfig.maxSquadSize == SQUAD) KingdomConfig.maxSquadSize = OLD_SQUAD;
		state = "armies overhaul off: 2.1 caps";
	}
}
