package dev.elsewhere.rivals.mirewick;

import dev.elsewhere.rivals.mirewick.boss.MagnateBoss;
import dev.elsewhere.rivals.mirewick.item.MirewickItems;
import dev.elsewhere.rivals.mirewick.item.MirewickPassives;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the Mirewick module (Lantern Republic): items, unique soldiers, the five Magnates, temporary-block ledger,
 * drops and the sellsword policy. Faction data, units, skins, lang and tags are plain resources. The merged core calls
 * {@link #init()} from its own init and {@code MirewickClient.initClient()} on the client.
 */
public final class MirewickContent {
	private MirewickContent() {}

	public static final String NS = "elsewhere";
	public static final Logger LOG = LoggerFactory.getLogger("elsewhere-mirewick");
	public static final Identifier FACTION = id("mirewick");
	private static boolean done;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static void init() {
		if (done) return;
		done = true;
		Sfx3.init();
		MirewickItems.init();
		MirewickUnits.init();
		MagnateBoss.init();
		dev.elsewhere.rivals.mirewick.rewards.MirewickRewards.init(); // 3.0 (s-bossdrops): XP and drops on every death
		MirewickPassives.init();
		MirewickLoot.init();
		MirewickCommands.init();
		dev.elsewhere.rivals.mirewick.tactics.MirewickTactics.init();
		dev.elsewhere.rivals.mirewick.look.MirewickRulerLook.init(); // s-rulerarmor: B10 faction armor layer on Magnates saved before 3.0
		ServerLifecycleEvents.SERVER_STARTED.register(TempBlocks::purgeOnStart);
		ServerLifecycleEvents.SERVER_STOPPING.register(TempBlocks::purgeAll);
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			TempBlocks.tick(srv);
			dev.elsewhere.rivals.mirewick.boss.GroundPatches.tick(srv);
		});
		LOG.info("Elsewhere Mirewick loaded: {} items, {} unit types, {} Magnates", MirewickItems.ALL.size(), MirewickUnits.TYPES.size(), dev.elsewhere.rivals.mirewick.boss.Magnate.values().length);
	}
}
