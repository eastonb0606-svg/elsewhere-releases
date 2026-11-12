package dev.elsewhere.kingdom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the kingdom base module: registers the soldier entity types, spawn eggs, creative tab,
 * the unit-type data loader, the squad/perf tick hooks and loads {@code config/elsewhere-kingdom.json}.
 * Call {@link #init()} once from the mod initializer (the merged core calls it from its own init).
 */
public final class KingdomContent {
	private KingdomContent() {}

	public static final String NS = "elsewhere";
	public static final Logger LOG = LoggerFactory.getLogger("elsewhere-kingdom");
	private static boolean done;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static void init() {
		if (done) return;
		done = true;
		KingdomConfig.load();
		Factions.bootstrap();
		FreeCompanies.register();
		FactionDefs.init();
		dev.elsewhere.kingdom.tactics.TacticsConfig.init();
		UnitTypes.init();
		dev.elsewhere.kingdom.item.KingdomItems.init();
		KingdomEntities.init();
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof KingdomSoldier s) KingdomPerf.onLoad(s);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof KingdomSoldier s) KingdomPerf.onUnload(s);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
			if (e.level() instanceof net.minecraft.server.level.ServerLevel sl) KingdomState.get(sl).onDeath(sl, e, src);
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			KingdomPerf.tickLevel(level);
			KingdomState.get(level).tick(level);
		});
		KingdomEvents.SOLDIER_DIED.register(Standing::onSoldierDied);
		KingdomEvents.SOLDIER_DIED.register(FreeCompanies::onSoldierDied);
		dev.elsewhere.kingdom.war.WarLayer.init();
		KingdomCommands.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(srv -> {
			KingdomPerf.reset();
			Factions.clearStanceOverrides();
		});
		dev.elsewhere.kingdom.armies.Armies.init(); // 3.0 epic armies (kill switch: config/elsewhere_armies.json)
		dev.elsewhere.kingdom.idle.IdlePoses.init(); // 3.0 polish P9 idle/work poses (kill switch: idle_poses in config/elsewhere_armies.json)
		LOG.info("Elsewhere kingdom base loaded: {} soldier types", KingdomEntities.TYPES.size());
	}
}
