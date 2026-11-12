package dev.elsewhere.v2;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** 2.0 content that spans dimensions: companions' and Hollow mobs' spawns, fishing in every dimension, bounties, the Lantern Procession. */
public final class V2Content {
	private V2Content() {}

	/** new-dimension-fishing: per dimension, its fish (common) and one rare treasure. */
	public record Catch(String dim, Item fish, Item treasure) {}

	public static final Map<String, Catch> CATCHES = new LinkedHashMap<>();

	private static ResourceKey<Biome> biome(final String path) {
		return ResourceKey.create(Registries.BIOME, Elsewhere.id(path));
	}

	public static void init() {
		// ---- spawns (Fabric BiomeModifications: no other module's biome files are edited)
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biome("hollow_thicket"), biome("heartwood_grove"), biome("silent_clearing")),
			MobCategory.CREATURE, ModEntities.HOLLOW_STAG, 8, 1, 3);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biome("resin_bog"), biome("weeping_deeps")),
			MobCategory.MONSTER, ModEntities.BOG_WIGHT, 40, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biome("glass_flats"), biome("ember_dunes"), biome("spirefields"), biome("bonebed_hollows")),
			MobCategory.CREATURE, ModEntities.DOCILE_SCARAB, 6, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biome("kush_canopy"), biome("indica_hollows"), biome("sativa_highlands"), biome("munchie_groves"),
			biome("hazebottom_swamp")), MobCategory.CREATURE, ModEntities.HAZE_FERRET, 6, 1, 2);
		dev.elsewhere.core.s3.groups.SpawnGroups3.init(); // ARMIES 6: 3.0 group sizes + herds (s-spawngroups, key spawn_groups_core)

		// ---- fishing
		CATCHES.put("pale_hollow", new Catch("pale_hollow", ModItems.GHOSTFIN, ModItems.MUSIC_DISC_PALE_LIGHT));
		CATCHES.put("petalwind", new Catch("petalwind", ModItems.PETALSCALE_KOI, Items.HEART_OF_THE_SEA));
		CATCHES.put("glasswaste", new Catch("glasswaste", ModItems.GLASSFIN_DARTER, Items.DIAMOND));
		CATCHES.put("verdigris_sea", new Catch("verdigris_sea", ModItems.VERDIGRIS_EEL, Items.NAUTILUS_SHELL));
		CATCHES.put("ironpeak", new Catch("ironpeak", ModItems.FROSTJAW_TROUT, Items.EMERALD_BLOCK));
		CATCHES.put("greenhaze", new Catch("greenhaze", ModItems.HAZE_GUPPY, Items.GOLDEN_APPLE));
		CATCHES.put("the_hush", new Catch("the_hush", ModItems.HUSH_LANTERNFISH, Items.ECHO_SHARD));
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!key.equals(BuiltInLootTables.FISHING_FISH)) {
				return;
			}
			List<LootItemCondition.Builder> anyElsewhere = new ArrayList<>();
			for (Catch c : CATCHES.values()) {
				anyElsewhere.add(inDim(c.dim()));
			}
			LootItemCondition.Builder notElsewhere = InvertedLootItemCondition.invert(AnyOfCondition.anyOf(anyElsewhere.toArray(new LootItemCondition.Builder[0])));
			builder.modifyPools(pool -> pool.when(notElsewhere));
			for (Catch c : CATCHES.values()) {
				builder.pool(LootPool.lootPool()
					.add(LootItem.lootTableItem(c.fish()).setWeight(70))
					.add(LootItem.lootTableItem(Items.COD).setWeight(20))
					.add(LootItem.lootTableItem(Items.SALMON).setWeight(7))
					.add(LootItem.lootTableItem(c.treasure()).setWeight(3))
					.when(inDim(c.dim())).build());
			}
		});

		Bounties.init();
		// TODO (HANDOFF.md): LanternEvent.init(); PHSites.init();
	}

	static LootItemCondition.Builder inDim(final String path) {
		return LocationCheck.checkLocation(LocationPredicate.Builder.inDimension(ResourceKey.create(Registries.DIMENSION, Elsewhere.id(path))));
	}

	static {
		BuiltInRegistries.class.getName();
		Identifier.class.getName();
		Level.class.getName();
	}
}
