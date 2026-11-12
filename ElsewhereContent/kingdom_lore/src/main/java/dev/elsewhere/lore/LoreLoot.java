package dev.elsewhere.lore;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/** Kingdom loot: the courier's pouch, and a rare Survey Fragment in village cartographers' chests. Chest tables are data files. */
public final class LoreLoot {
	private LoreLoot() {}

	public static final String[] CHEST_TABLES = {"kingdom_cache", "kingdom_armory", "kingdom_dungeon", "kingdom_market_stall", "kingdom_courier",
		"hrafnholt_courier", "azmar_courier", "mirewick_courier"};

	public static ResourceKey<LootTable> table(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, LoreContent.id(path));
	}

	public static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Identifier id = key.identifier();
			if (id.getNamespace().equals("minecraft") && id.getPath().equals("chests/village/village_cartographer"))
				builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(0.12F)).add(LootItem.lootTableItem(LoreItems.SURVEY_FRAGMENT)));
		});
	}

	/** Rolls {@code chests/kingdom_courier} at the courier's feet. */
	public static void dropCourierPouch(ServerLevel level, Entity courier) {
		dropCourierPouch(level, courier, LoreFactions.of(null));
	}

	public static void dropCourierPouch(ServerLevel level, Entity courier, LoreFactions.Lore faction) {
		String table = faction.rival() ? "chests/" + faction.key + "_courier" : "chests/kingdom_courier";
		for (ItemStack s : roll(level, table)) courier.spawnAtLocation(level, s);
	}

	public static Iterable<ItemStack> roll(ServerLevel level, String path) {
		LootTable t = level.getServer().reloadableRegistries().getLootTable(table(path));
		LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
		return t.getRandomItems(params);
	}
}
