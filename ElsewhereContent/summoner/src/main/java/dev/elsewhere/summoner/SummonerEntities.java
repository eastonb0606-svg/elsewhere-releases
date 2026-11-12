package dev.elsewhere.summoner;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The three minion entity types (not saved with the world, no loot tables). */
public final class SummonerEntities {
	private SummonerEntities() {}

	public static EntityType<Rootling> ROOTLING;
	public static EntityType<PaperKite> PAPER_KITE;
	public static EntityType<Brassback> BRASSBACK;
	public static EntityType<Sporeling> SPORELING;

	@SuppressWarnings("unchecked")
	private static <T extends Entity> EntityType<T> reg(String id, EntityType.Builder<T> b) {
		var rid = SummonerContent.id(id);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(rid)) return (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(rid);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
	}

	public static void init() {
		ROOTLING = reg("rootling", EntityType.Builder.of(Rootling::new, MobCategory.MISC).sized(0.5F, 0.7F).eyeHeight(0.55F).clientTrackingRange(10).noSave().noLootTable());
		PAPER_KITE = reg("paper_kite", EntityType.Builder.of(PaperKite::new, MobCategory.MISC).sized(0.7F, 0.7F).eyeHeight(0.35F).clientTrackingRange(10).noSave().noLootTable());
		BRASSBACK = reg("brassback", EntityType.Builder.of(Brassback::new, MobCategory.MISC).sized(0.8F, 0.6F).eyeHeight(0.45F).clientTrackingRange(10).noSave().noLootTable().fireImmune());
		SPORELING = reg("sporeling", EntityType.Builder.of(Sporeling::new, MobCategory.MISC).sized(0.5F, 0.7F).eyeHeight(0.55F).clientTrackingRange(10).noSave().noLootTable());
		FabricDefaultAttributeRegistry.register(SPORELING, Sporeling.createAttributes());
		FabricDefaultAttributeRegistry.register(ROOTLING, Rootling.createAttributes());
		FabricDefaultAttributeRegistry.register(PAPER_KITE, PaperKite.createAttributes());
		FabricDefaultAttributeRegistry.register(BRASSBACK, Brassback.createAttributes());
	}
}
