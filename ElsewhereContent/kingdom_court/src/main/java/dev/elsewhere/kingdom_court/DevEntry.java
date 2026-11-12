package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom_court.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Dev mod initializer (elsewhere_kingdom_court): the module content plus the tick-driven self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		CourtContent.init();
		// Dev only: K-D's items (referenced by the chest loot tables and the Lord drops) do not exist on this dev classpath.
		for (String id : new String[] {"lore_survey_fragment", "lore_royal_proclamation", "lore_seal_marshal", "lore_seal_castellan", "lore_seal_master_at_arms", "lore_seal_huntsman", "lore_seal_chancellor"}) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CourtContent.id(id));
			if (!BuiltInRegistries.ITEM.containsKey(key.identifier())) Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		}
		// The rival-court test factions pay in vanilla items (dev/TestCoins), never in items registered here: DevEntry is dropped at merge,
		// so anything the self-test needs must exist in the merged jar too.
		if (Boolean.getBoolean("court.worldgen")) {
			ServerTickEvents.END_SERVER_TICK.register(dev.elsewhere.kingdom_court.dev.WorldgenCheck::endTick);
		}
		if (Boolean.getBoolean("court.selftest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::serverStarted);
			ServerTickEvents.START_SERVER_TICK.register(SelfTest::startTick);
			ServerTickEvents.END_SERVER_TICK.register(SelfTest::endTick);
		}
	}
}
