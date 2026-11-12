package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom_court.ruler.CourtRulers;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry of the kingdom court module (lane K-B): realm worldgen, the Lords and the King, succession and the end state,
 * Lord/King items, kingdom music, Calling/Glimmer tie-ins. {@link #init()} is idempotent; the merged core calls it.
 */
public final class CourtContent {
	private CourtContent() {}

	public static final Logger LOG = LoggerFactory.getLogger("elsewhere-kingdom-court");
	/** Neutral faction of the remnants after the fall. */
	public static final Identifier REMNANT = id("aldermark_remnant");
	private static boolean done;

	/** The neutral remnant faction of {@code faction}'s fallen kingdom (Aldermark's is {@link #REMNANT}). Registered by the court at start. */
	public static Identifier remnantOf(Identifier faction) {
		return Factions.ALDERMARK.equals(faction) ? REMNANT : id(faction.getPath() + "_remnant");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("elsewhere", path);
	}

	public static void init() {
		if (done) return;
		done = true;
		KingdomContent.init();
		Factions.registerRemnant(REMNANT, Factions.ALDERMARK, 0x77706A);
		CourtItems.init();
		CourtMusic.init();
		Sfx3.init();
		CourtWorld.init();
		CourtRulers.init();
		dev.elsewhere.kingdom_court.npc.Npcs.init();
		dev.elsewhere.kingdom_court.api.Duel.init();
		dev.elsewhere.kingdom_court.trade.TradeEvents.init();
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> DiplomacyCommand.register(d));
		CourtCombat.init();
		CourtLife.init();
		CourtRoutineAnchors.init(); // s-routines: routine where ids -> castle layout places (ARMIES.md 2.6)
		dev.elsewhere.kingdom_court.compat.TestMeterOrder.init(); // s-compat-testmeter: 300 HP cap after Test Meter's mob-health bonus
		dev.elsewhere.kingdom_court.look.AldermarkRulerLook.init(); // s-rulerarmor: B10 Aldermark plate and barding on rulers saved before 3.0
		dev.elsewhere.kingdom_court.standing.StandingNet.init(); // s-p7-standing: POLISH P7 trader dialogue + Standing chip payloads
		LOG.info("Elsewhere kingdom court loaded: 6 rulers, {} items", CourtItems.ALL.size());
	}
}
