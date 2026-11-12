package dev.elsewhere.overworld_bosses;

import dev.elsewhere.boss.gulpmother.FrogspawnGlob;
import dev.elsewhere.boss.gulpmother.GulpmotherEntity;
import dev.elsewhere.boss.gulpmother.MireBiter;
import dev.elsewhere.boss.neferkhet.NeferkhetEntity;
import dev.elsewhere.boss.neferkhet.SunLance;
import dev.elsewhere.boss.neferkhet.WrappedSentinel;
import dev.elsewhere.boss.rimeback.FrostShard;
import dev.elsewhere.boss.rimeback.FrostboundRevenant;
import dev.elsewhere.boss.rimeback.GlacierBoulder;
import dev.elsewhere.boss.rimeback.RimebackEntity;
import dev.elsewhere.boss.strawgrim.MurderCrow;
import dev.elsewhere.boss.strawgrim.SeedFireball;
import dev.elsewhere.boss.strawgrim.StrawgrimEntity;
import dev.elsewhere.boss.strawgrim.StrawgrimHead;
import dev.elsewhere.boss.vorlagh.InkGlob;
import dev.elsewhere.boss.vorlagh.TidewrackDrowned;
import dev.elsewhere.boss.vorlagh.VorlaghEntity;
import dev.elsewhere.boss.vorlagh.VorlaghTentacle;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.SpawnPlacements;

/** Every entity type of this module (bosses, parts, minions, projectiles). */
public final class OwbEntities {
	public static EntityType<GulpmotherEntity> GULPMOTHER;
	public static EntityType<MireBiter> MIRE_BITER;
	public static EntityType<FrogspawnGlob> FROGSPAWN_GLOB;
	public static EntityType<StrawgrimEntity> STRAWGRIM;
	public static EntityType<StrawgrimHead> STRAWGRIM_HEAD;
	public static EntityType<MurderCrow> MURDER_CROW;
	public static EntityType<SeedFireball> SEED_FIREBALL;
	public static EntityType<NeferkhetEntity> NEFERKHET;
	public static EntityType<SunLance> SUN_LANCE;
	public static EntityType<WrappedSentinel> WRAPPED_SENTINEL;
	public static EntityType<RimebackEntity> RIMEBACK;
	public static EntityType<GlacierBoulder> GLACIER_BOULDER;
	public static EntityType<FrostShard> FROST_SHARD;
	public static EntityType<FrostboundRevenant> FROSTBOUND_REVENANT;
	public static EntityType<VorlaghEntity> VORLAGH;
	public static EntityType<VorlaghTentacle> VORLAGH_TENTACLE;
	public static EntityType<InkGlob> INK_GLOB;
	public static EntityType<TidewrackDrowned> TIDEWRACK_DROWNED;

	private OwbEntities() {}

	private static <T extends Entity> EntityType<T> reg(String id, EntityType.Builder<T> b) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, BossRegistry.id(id));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
	}

	public static void init() {
		GULPMOTHER = reg("gulpmother", EntityType.Builder.of(GulpmotherEntity::new, MobCategory.MONSTER).sized(4.5F, 3.2F).clientTrackingRange(10).fireImmune().noLootTable());
		MIRE_BITER = reg("mire_biter", EntityType.Builder.of(MireBiter::new, MobCategory.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).clientTrackingRange(10));
		FROGSPAWN_GLOB = reg("frogspawn_glob", EntityType.Builder.<FrogspawnGlob>of(FrogspawnGlob::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(5).noLootTable());
		STRAWGRIM = reg("strawgrim", EntityType.Builder.of(StrawgrimEntity::new, MobCategory.MONSTER).sized(1.6F, 4.4F).clientTrackingRange(10).fireImmune().noLootTable());
		STRAWGRIM_HEAD = reg("strawgrim_head", EntityType.Builder.of(StrawgrimHead::new, MobCategory.MONSTER).sized(1.0F, 1.0F).clientTrackingRange(10).fireImmune().noLootTable());
		MURDER_CROW = reg("murder_crow", EntityType.Builder.of(MurderCrow::new, MobCategory.MONSTER).sized(0.5F, 0.3F).clientTrackingRange(8));
		SEED_FIREBALL = reg("seed_fireball", EntityType.Builder.<SeedFireball>of(SeedFireball::new, MobCategory.MISC).sized(0.3125F, 0.3125F).clientTrackingRange(6).updateInterval(2).noLootTable());
		NEFERKHET = reg("neferkhet", EntityType.Builder.of(NeferkhetEntity::new, MobCategory.MONSTER).sized(1.8F, 6.0F).clientTrackingRange(10).noLootTable());
		SUN_LANCE = reg("sun_lance", EntityType.Builder.<SunLance>of(SunLance::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).noLootTable().noSave());
		WRAPPED_SENTINEL = reg("wrapped_sentinel", EntityType.Builder.of(WrappedSentinel::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8));
		RIMEBACK = reg("rimeback", EntityType.Builder.of(RimebackEntity::new, MobCategory.MONSTER).sized(3.2F, 7.0F).clientTrackingRange(10).noLootTable());
		GLACIER_BOULDER = reg("glacier_boulder", EntityType.Builder.<GlacierBoulder>of(GlacierBoulder::new, MobCategory.MISC).sized(2.0F, 2.0F).clientTrackingRange(10).updateInterval(1).noLootTable().noSave());
		FROST_SHARD = reg("frost_shard", EntityType.Builder.<FrostShard>of(FrostShard::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(5).noLootTable());
		FROSTBOUND_REVENANT = reg("frostbound_revenant", EntityType.Builder.of(FrostboundRevenant::new, MobCategory.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).clientTrackingRange(8));
		VORLAGH = reg("vorlagh", EntityType.Builder.of(VorlaghEntity::new, MobCategory.MONSTER).sized(6.0F, 9.0F).clientTrackingRange(12).noLootTable());
		VORLAGH_TENTACLE = reg("vorlagh_tentacle", EntityType.Builder.of(VorlaghTentacle::new, MobCategory.MONSTER).sized(1.8F, 8.0F).clientTrackingRange(12).noLootTable());
		INK_GLOB = reg("ink_glob", EntityType.Builder.<InkGlob>of(InkGlob::new, MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(5).noLootTable());
		TIDEWRACK_DROWNED = reg("tidewrack_drowned", EntityType.Builder.of(TidewrackDrowned::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8));

		FabricDefaultAttributeRegistry.register(GULPMOTHER, GulpmotherEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MIRE_BITER, MireBiter.createAttributes());
		FabricDefaultAttributeRegistry.register(STRAWGRIM, StrawgrimEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(STRAWGRIM_HEAD, StrawgrimHead.createAttributes());
		FabricDefaultAttributeRegistry.register(MURDER_CROW, MurderCrow.createAttributes());
		FabricDefaultAttributeRegistry.register(NEFERKHET, NeferkhetEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WRAPPED_SENTINEL, WrappedSentinel.createAttributes());
		FabricDefaultAttributeRegistry.register(RIMEBACK, RimebackEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FROSTBOUND_REVENANT, FrostboundRevenant.createAttributes());
		FabricDefaultAttributeRegistry.register(VORLAGH, VorlaghEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(VORLAGH_TENTACLE, VorlaghTentacle.createAttributes());
		FabricDefaultAttributeRegistry.register(TIDEWRACK_DROWNED, TidewrackDrowned.createAttributes());
	}
}
