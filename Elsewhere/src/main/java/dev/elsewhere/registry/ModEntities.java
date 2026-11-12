package dev.elsewhere.registry;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.boss.mother_creak.MotherCreak;
import dev.elsewhere.boss.mother_creak.RootSpike;
import dev.elsewhere.boss.selenne.Selenne;
import dev.elsewhere.core.travel.RiftEntity;
import dev.elsewhere.palehollow.entity.CreakingHusk;
import dev.elsewhere.palehollow.entity.EyeblossomWisp;
import dev.elsewhere.palehollow.entity.SapTapper;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ModEntities {
	public static final EntityType<RiftEntity> RIFT = register("rift",
		EntityType.Builder.<RiftEntity>of(RiftEntity::new, MobCategory.MISC).sized(1.4F, 2.6F).clientTrackingRange(10).updateInterval(10).noSummon());
	public static final EntityType<RootSpike> ROOT_SPIKE = register("root_spike",
		EntityType.Builder.<RootSpike>of(RootSpike::new, MobCategory.MISC).noLootTable().sized(0.9F, 1.4F).clientTrackingRange(8).updateInterval(2));

	public static final EntityType<CreakingHusk> CREAKING_HUSK = register("creaking_husk",
		FabricEntityType.Builder.createMob(CreakingHusk::new, MobCategory.MONSTER, b -> b
			.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules)
			.defaultAttributes(CreakingHusk::createAttributes))
			.sized(0.9F, 3.0F).eyeHeight(2.55F).clientTrackingRange(8).notInPeaceful());
	public static final EntityType<EyeblossomWisp> EYEBLOSSOM_WISP = register("eyeblossom_wisp",
		FabricEntityType.Builder.createMob(EyeblossomWisp::new, MobCategory.MONSTER, b -> b
			.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules)
			.defaultAttributes(EyeblossomWisp::createAttributes))
			.sized(0.6F, 0.9F).eyeHeight(0.6F).clientTrackingRange(8).notInPeaceful());

	public static final EntityType<MotherCreak> MOTHER_CREAK = register("mother_creak",
		FabricEntityType.Builder.createMob(MotherCreak::new, MobCategory.MONSTER, b -> b.defaultAttributes(MotherCreak::createAttributes))
			.sized(2.0F, 8.6F).eyeHeight(7.4F).clientTrackingRange(10).notInPeaceful().fireImmune());
	public static final EntityType<Selenne> SELENNE = register("selenne",
		FabricEntityType.Builder.createMob(Selenne::new, MobCategory.MONSTER, b -> b.defaultAttributes(Selenne::createAttributes))
			.sized(3.5F, 3.5F).eyeHeight(1.75F).clientTrackingRange(10).notInPeaceful().fireImmune());

	public static final EntityType<SapTapper> SAP_TAPPER = register("sap_tapper",
		FabricEntityType.Builder.createMob(SapTapper::new, MobCategory.MISC, b -> b.defaultAttributes(net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader::createMobAttributes))
			.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));


	// ---- 2.0: Pale Hollow mobs and companions (new-companions)
	public static final EntityType<dev.elsewhere.palehollow.entity.CompanionEntity> HOLLOW_STAG = register("hollow_stag",
		FabricEntityType.Builder.createMob(dev.elsewhere.palehollow.entity.CompanionEntity::new, MobCategory.CREATURE, b -> b
			.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.world.entity.Mob::checkMobSpawnRules)
			.defaultAttributes(dev.elsewhere.palehollow.entity.CompanionEntity::createAttributes))
			.sized(0.9F, 1.6F).eyeHeight(1.4F).clientTrackingRange(10));
	public static final EntityType<dev.elsewhere.palehollow.entity.CompanionEntity> DOCILE_SCARAB = register("docile_scarab",
		FabricEntityType.Builder.createMob(dev.elsewhere.palehollow.entity.CompanionEntity::new, MobCategory.CREATURE, b -> b
			.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.world.entity.Mob::checkMobSpawnRules)
			.defaultAttributes(dev.elsewhere.palehollow.entity.CompanionEntity::createAttributes))
			.sized(0.6F, 0.4F).eyeHeight(0.25F).clientTrackingRange(8));
	public static final EntityType<dev.elsewhere.palehollow.entity.CompanionEntity> HAZE_FERRET = register("haze_ferret",
		FabricEntityType.Builder.createMob(dev.elsewhere.palehollow.entity.CompanionEntity::new, MobCategory.CREATURE, b -> b
			.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.world.entity.Mob::checkMobSpawnRules)
			.defaultAttributes(dev.elsewhere.palehollow.entity.CompanionEntity::createAttributes))
			.sized(0.5F, 0.55F).eyeHeight(0.4F).clientTrackingRange(8));
	public static final EntityType<dev.elsewhere.palehollow.entity.BogWight> BOG_WIGHT = register("bog_wight",
		FabricEntityType.Builder.createMob(dev.elsewhere.palehollow.entity.BogWight::new, MobCategory.MONSTER, b -> b
			.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules)
			.defaultAttributes(dev.elsewhere.palehollow.entity.BogWight::createWightAttributes))
			.sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).notInPeaceful());

	private ModEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(final String name, final EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Elsewhere.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
	}
}
