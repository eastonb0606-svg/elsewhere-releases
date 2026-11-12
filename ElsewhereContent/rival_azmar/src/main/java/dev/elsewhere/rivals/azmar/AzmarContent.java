package dev.elsewhere.rivals.azmar;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry of the Azmar rival module (lane azmar): the Sultanate of Azmar, the smart ancient-Egyptian mythic kingdom.
 * Faction/units/castle palette are data; this class registers the items (khopesh family, headpieces, drops, currency),
 * the khopesh hook, fire pots, particles and the worn-item abilities. {@link #init()} is idempotent.
 */
public final class AzmarContent {
	private AzmarContent() {}

	public static final Logger LOG = LoggerFactory.getLogger("elsewhere-azmar");
	/** The faction id, {@code elsewhere:azmar} (data/elsewhere/kingdom_faction/azmar.json). */
	public static final Identifier FACTION = id("azmar");
	private static boolean done;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("elsewhere", path);
	}

	public static void init() {
		if (done) return;
		done = true;
		Sfx3.init();
		dev.elsewhere.kingdom.KingdomContent.init();
		dev.elsewhere.duelist.DuelistContent.init();
		dev.elsewhere.duelist.kit.ShieldHook.params = new dev.elsewhere.duelist.kit.ShieldHook.Params(60, 120, 40);   // the khopesh hook: 60 ticks down, once per 120 per attacker, once per 40 per squad
		AzmarItems.init();
		AzmarSoldiers.init();
		FirePots.init();
		dev.elsewhere.rivals.azmar.boss.AzmarBosses.init();
		AzmarWear.init();
		AzmarTactics.init();
		dev.elsewhere.rivals.azmar.look.AzmarRulerLook.init(); // s-rulerarmor: B10 Egyptian armor layer and K29 caparison on bosses saved before 3.0
		LOG.info("Elsewhere Azmar loaded: {} items", AzmarItems.ALL.size());
	}
}
