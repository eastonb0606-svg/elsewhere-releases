package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.glasswaste.GlasswasteBlocks;
import dev.elsewhere.glasswaste.GlasswasteItems;
import dev.elsewhere.glasswaste.GlasswasteTrips;
import dev.elsewhere.glasswaste.Heat;
import dev.elsewhere.petalwind.PetalwindBlocks;
import dev.elsewhere.petalwind.PetalwindContentTypes;
import dev.elsewhere.petalwind.PetalwindItems;
import dev.elsewhere.petalwind.PetalwindRules;
import dev.elsewhere.petalwind.PetalwindTrips;
import dev.elsewhere.stub.petalwind_glasswaste.SetBonus;
import dev.elsewhere.stub.petalwind_glasswaste.SignatureGearStub;
import dev.elsewhere.stub.petalwind_glasswaste.Telegraph;
import dev.elsewhere.stub.petalwind_glasswaste.TripStub;
import dev.elsewhere.stub.petalwind_glasswaste.TripTrigger;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point for the Petalwind Isles + Glasswaste content. The core's initializer calls {@link #init()}. */
public final class PetalwindGlasswasteContent {
	private PetalwindGlasswasteContent() {}

	public static final Logger LOGGER = LoggerFactory.getLogger("Elsewhere/petalwind_glasswaste");
	/** This module's accidental-trip triggers (DIMENSIONS.md section 9). */
	public static final List<TripTrigger> TRIPS = List.of(PetalwindTrips.LONG_FALL, PetalwindTrips.WIND_LAUNCH, GlasswasteTrips.DESERT_NOON);
	private static boolean initialized;

	public static void init() {
		if (initialized) return;
		initialized = true;
		Sfx3.init();
		PetalwindBlocks.init();
		PetalwindContentTypes.init();
		GlasswasteBlocks.init();
		ModEntities.init();
		PetalwindItems.init();
		GlasswasteItems.init();
		ModEntities.initSpawnEggs();
		dev.elsewhere.petalwind_glasswaste.s3.groups.SpawnGroups3.init(); // ARMIES 6 spawn groups (s-spawngroups, key spawn_groups_petalwind_glasswaste)
		SignatureGearStub.init();
		ModWorldgen.init();
		CreativeTabs.init();
		Net.init();
		Tasks.init();
		Telegraph.init();
		SetBonus.init();
		TripStub.init();
		PetalwindRules.init();
		dev.elsewhere.glasswaste.GlasswasteRules.init();
		Bosses.init();
		dev.elsewhere.boss.kit.BossKit.init();
		ArsenalHooks.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Heat.tick(server);
			PGEvents.tick(server);
			dev.elsewhere.glasswaste.SunwrightPerks.serverTick(server);
			PetalwindRules.tick(server);
			GlasswasteTrips.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { Heat.forget(handler.player); dev.elsewhere.glasswaste.SunwrightPerks.forget(handler.player); });
		LOGGER.info("Petalwind Isles + Glasswaste content registered ({} items)", Reg.TAB_ITEMS.values().stream().mapToInt(List::size).sum());
	}
}
