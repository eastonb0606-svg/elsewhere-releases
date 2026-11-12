package dev.elsewhere.gear2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Boss and chest sources without touching other modules: extra pools on the existing loot tables (Fabric's modify
 * event). Tempestra, Heliodor, Aphelion and Ossyra have no table JSON in their modules, so gear2 ships empty
 * placeholders ({@code "pools": []}, same as arsenal/summoner); a module's own file wins at merge if one appears.
 */
public final class Gear2Loot {
	private Gear2Loot() {}

	public record Drop(Item item, float chance) {}

	/** table id (elsewhere:...) -> extra drops, one pool each. */
	public static final Map<String, List<Drop>> DROPS = new LinkedHashMap<>();

	public static ResourceKey<LootTable> table(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Gear2Content.id(path));
	}

	public static void init() {
		DROPS.put("entities/galvanox", List.of(new Drop(Gear2Items.GALVANIC_TEMPLATE, Gear2Stats.PART_CHANCE)));
		DROPS.put("entities/thurgrim", List.of(new Drop(Gear2Items.IRONCLAD_CHAIN, Gear2Stats.PART_CHANCE),
			new Drop(Gear2Items.STEAM_AUGER_BIT, Gear2Stats.PART_CHANCE), new Drop(Gear2Items.PROSPECTORS_PENDULUM, Gear2Stats.PENDULUM_THURGRIM_CHANCE)));
		DROPS.put("entities/tempestra", List.of(new Drop(Gear2Items.GALE_RAPIER, Gear2Stats.RAPIER_CHANCE)));
		DROPS.put("entities/heliodor", List.of(new Drop(Gear2Items.HELIODOR_MIRROR_CORE, Gear2Stats.PART_CHANCE)));
		DROPS.put("entities/mother_creak", List.of(new Drop(Gear2Items.TERMITE_QUEEN, Gear2Stats.PART_CHANCE), new Drop(Gear2Items.AMBERSTILL_TEMPLATE, Gear2Stats.PART_CHANCE)));
		DROPS.put("entities/selenne", List.of(new Drop(Gear2Items.SEERS_HOURGLASS, Gear2Stats.HOURGLASS_CHANCE)));
		DROPS.put("entities/aphelion", List.of(new Drop(Gear2Items.VOID_TETHER_SPOOL, Gear2Stats.SPOOL_APHELION_CHANCE)));
		DROPS.put("entities/ossyra", List.of(new Drop(Gear2Items.VOID_TETHER_SPOOL, Gear2Stats.SPOOL_OSSYRA_CHANCE)));
		DROPS.put("chests/climbers_cairn", List.of(new Drop(Gear2Items.PROSPECTORS_PENDULUM, Gear2Stats.PENDULUM_CHEST_CHANCE)));
		DROPS.put("chests/clan_hold", List.of(new Drop(Gear2Items.PROSPECTORS_PENDULUM, Gear2Stats.PENDULUM_CHEST_CHANCE)));

		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!key.identifier().getNamespace().equals("elsewhere")) return;
			List<Drop> drops = DROPS.get(key.identifier().getPath());
			if (drops != null) for (Drop d : drops) builder.withPool(pool(d.item(), d.chance()));
		});
	}

	static LootPool.Builder pool(Item item, float chance) {
		return LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(chance)).add(LootItem.lootTableItem(item));
	}
}
