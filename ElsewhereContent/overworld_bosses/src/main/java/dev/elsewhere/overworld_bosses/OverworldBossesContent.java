package dev.elsewhere.overworld_bosses;

import dev.elsewhere.boss.strawgrim.ScarecrowSummon;
import dev.elsewhere.overworld_bosses.world.OwbWorldgen;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import dev.elsewhere.stub.overworld_bosses.TripTrigger;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point of the overworld_bosses module (CONTRACT.md): the five Overworld bosses, their minions,
 * summon blocks and items, drops and world-gen sites. Called by the dev mod's initializer now and by the core's
 * entry point after the merge.
 */
public final class OverworldBossesContent {
	public static final Logger LOGGER = LoggerFactory.getLogger("elsewhere/overworld_bosses");
	/** No dimension in this module, so no accidental-trip triggers. */
	public static final List<TripTrigger> TRIPS = List.of();
	private static boolean done;

	private OverworldBossesContent() {}

	public static void init() {
		dev.elsewhere.boss.kit.BossKit.init();
		if (done) return;
		done = true;
		BossRegistry.init();
		OwbSounds.init();
		OwbBlocks.init();
		OwbItems.init();
		OwbEntities.init();
		OwbConfig.init();
		OwbWorldgen.init();
		ScarecrowSummon.init();
		OwbCreativeTab.init();
		LOGGER.info("[elsewhere/overworld_bosses] registered 5 bosses");
	}
}
