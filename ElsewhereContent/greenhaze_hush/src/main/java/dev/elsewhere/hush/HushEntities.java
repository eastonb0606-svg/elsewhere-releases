package dev.elsewhere.hush;

import dev.elsewhere.boss.auris.Auris;
import dev.elsewhere.boss.lithara.Lithara;
import dev.elsewhere.greenhaze_hush.GHProjectile;
import dev.elsewhere.greenhaze_hush.Reg;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

public final class HushEntities {
	private HushEntities() {}

	public static EntityType<HushMobs.EchoStalker> ECHO_STALKER;
	public static EntityType<HushMobs.ShriekerSwarm> SHRIEKER_SWARM;
	public static EntityType<HushMobs.OssuaryChorister> OSSUARY_CHORISTER;
	public static EntityType<HushMobs.CatalystMimic> CATALYST_MIMIC;
	public static EntityType<HushMobs.MuteArchivist> MUTE_ARCHIVIST;
	public static EntityType<Auris> AURIS;
	public static EntityType<Lithara> LITHARA;
	public static EntityType<GHProjectile> CRYSTAL_SHARD, TUNING_FORK;

	static void init() {
		ECHO_STALKER = Reg.entity("echo_stalker", EntityType.Builder.of(HushMobs.EchoStalker::new, MobCategory.MONSTER).sized(0.7F, 3.6F).clientTrackingRange(10));
		SHRIEKER_SWARM = Reg.entity("shrieker_swarm", EntityType.Builder.of(HushMobs.ShriekerSwarm::new, MobCategory.MONSTER).sized(0.7F, 0.6F).clientTrackingRange(8));
		OSSUARY_CHORISTER = Reg.entity("ossuary_chorister", EntityType.Builder.of(HushMobs.OssuaryChorister::new, MobCategory.MONSTER).sized(0.7F, 2.4F).clientTrackingRange(8));
		CATALYST_MIMIC = Reg.entity("catalyst_mimic", EntityType.Builder.of(HushMobs.CatalystMimic::new, MobCategory.MONSTER).sized(1.0F, 1.0F).clientTrackingRange(10));
		MUTE_ARCHIVIST = Reg.entity("mute_archivist", EntityType.Builder.<HushMobs.MuteArchivist>of(HushMobs.MuteArchivist::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10));
		AURIS = Reg.entity("auris", EntityType.Builder.of(Auris::new, MobCategory.MONSTER).sized(2.6F, 5.0F).fireImmune().clientTrackingRange(16));
		LITHARA = Reg.entity("lithara", EntityType.Builder.of(Lithara::new, MobCategory.MONSTER).sized(1.8F, 6.0F).fireImmune().clientTrackingRange(16));
		CRYSTAL_SHARD = Reg.entity("crystal_shard", EntityType.Builder.<GHProjectile>of(GHProjectile::new, MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(10));
		TUNING_FORK = Reg.entity("tuning_fork_of_auris", EntityType.Builder.<GHProjectile>of(GHProjectile::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(10));

		FabricDefaultAttributeRegistry.register(ECHO_STALKER, HushMobs.EchoStalker.attributes());
		FabricDefaultAttributeRegistry.register(SHRIEKER_SWARM, HushMobs.ShriekerSwarm.attributes());
		FabricDefaultAttributeRegistry.register(OSSUARY_CHORISTER, HushMobs.OssuaryChorister.attributes());
		FabricDefaultAttributeRegistry.register(CATALYST_MIMIC, HushMobs.CatalystMimic.attributes());
		FabricDefaultAttributeRegistry.register(MUTE_ARCHIVIST, Mob.createMobAttributes());
		FabricDefaultAttributeRegistry.register(AURIS, Auris.attributes());
		FabricDefaultAttributeRegistry.register(LITHARA, Lithara.attributes());

		SpawnPlacements.register(ECHO_STALKER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(OSSUARY_CHORISTER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(CATALYST_MIMIC, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(SHRIEKER_SWARM, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);

		egg("echo_stalker", ECHO_STALKER);
		egg("shrieker_swarm", SHRIEKER_SWARM);
		egg("ossuary_chorister", OSSUARY_CHORISTER);
		egg("catalyst_mimic", CATALYST_MIMIC);
		egg("mute_archivist", MUTE_ARCHIVIST);
	}

	private static void egg(String name, EntityType<?> type) {
		Reg.item(name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}
}
