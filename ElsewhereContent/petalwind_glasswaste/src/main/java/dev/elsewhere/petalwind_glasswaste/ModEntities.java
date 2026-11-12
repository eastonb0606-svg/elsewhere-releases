package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.kazehana.PetalMirage;
import dev.elsewhere.boss.tempestra.Tempestra;
import dev.elsewhere.glasswaste.GlassScarab;
import dev.elsewhere.glasswaste.Sandswimmer;
import dev.elsewhere.glasswaste.SunMote;
import dev.elsewhere.glasswaste.SunwrightRevenant;
import dev.elsewhere.petalwind.BlossomBreeze;
import dev.elsewhere.petalwind.Cumulus;
import dev.elsewhere.petalwind.GaleKite;
import dev.elsewhere.petalwind.Skywhale;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

/** Every entity type in this module (mobs, bosses, boss parts and projectiles). */
public final class ModEntities {
	private ModEntities() {}

	// Petalwind mobs
	public static EntityType<GaleKite> GALE_KITE;
	public static EntityType<BlossomBreeze> BLOSSOM_BREEZE;
	public static EntityType<Cumulus> CUMULUS;
	public static EntityType<Skywhale> SKYWHALE;
	// Glasswaste mobs
	public static EntityType<GlassScarab> GLASS_SCARAB;
	public static EntityType<Sandswimmer> SANDSWIMMER;
	public static EntityType<SunwrightRevenant> SUNWRIGHT_REVENANT;
	public static EntityType<SunMote> SUN_MOTE;
	public static EntityType<dev.elsewhere.petalwind.Windwright> WINDWRIGHT;
	public static EntityType<dev.elsewhere.glasswaste.DuneFactor> DUNE_FACTOR;
	// Bosses and their parts
	public static EntityType<Kazehana> KAZEHANA;
	public static EntityType<PetalMirage> PETAL_MIRAGE;
	public static EntityType<Tempestra> TEMPESTRA;
	public static EntityType<Heliodor> HELIODOR;
	public static EntityType<Ankhara> ANKHARA;

	public static void init() {
		GALE_KITE = Reg.living("gale_kite", EntityType.Builder.of(GaleKite::new, MobCategory.MONSTER).sized(0.9F, 0.5F).clientTrackingRange(8),
			GaleKite.createAttributes());
		BLOSSOM_BREEZE = Reg.living("blossom_breeze", EntityType.Builder.of(BlossomBreeze::new, MobCategory.MONSTER).sized(0.72F, 2.1F)
			.eyeHeight(1.6F).clientTrackingRange(10), BlossomBreeze.createBlossomAttributes());
		CUMULUS = Reg.living("cumulus", EntityType.Builder.of(Cumulus::new, MobCategory.CREATURE).sized(0.52F, 0.52F).eyeHeight(0.325F)
			.clientTrackingRange(10), Cumulus.createAttributes());
		SKYWHALE = Reg.living("skywhale", EntityType.Builder.of(Skywhale::new, MobCategory.CREATURE).sized(4.0F, 3.0F).eyeHeight(2.0F)
			.clientTrackingRange(10), Skywhale.createAttributes());

		GLASS_SCARAB = Reg.living("glass_scarab", EntityType.Builder.of(GlassScarab::new, MobCategory.MONSTER).sized(0.72F, 0.54F)
			.clientTrackingRange(8), GlassScarab.createAttributes());
		SANDSWIMMER = Reg.living("sandswimmer", EntityType.Builder.of(Sandswimmer::new, MobCategory.MONSTER).sized(0.8F, 0.6F)
			.clientTrackingRange(10), Sandswimmer.createAttributes());
		SUNWRIGHT_REVENANT = Reg.living("sunwright_revenant", EntityType.Builder.of(SunwrightRevenant::new, MobCategory.MONSTER).sized(0.7F, 2.25F)
			.eyeHeight(1.95F).clientTrackingRange(8), SunwrightRevenant.createRevenantAttributes());
		SUN_MOTE = Reg.living("sun_mote", EntityType.Builder.of(SunMote::new, MobCategory.MONSTER).sized(0.6F, 0.8F).fireImmune()
			.clientTrackingRange(8), SunMote.createMoteAttributes());

		WINDWRIGHT = Reg.living("windwright", EntityType.Builder.of(dev.elsewhere.petalwind.Windwright::new, MobCategory.CREATURE).sized(0.6F, 1.95F)
			.eyeHeight(1.62F).clientTrackingRange(10), net.minecraft.world.entity.Mob.createMobAttributes());
		DUNE_FACTOR = Reg.living("dune_factor", EntityType.Builder.of(dev.elsewhere.glasswaste.DuneFactor::new, MobCategory.CREATURE).sized(0.6F, 1.95F)
			.eyeHeight(1.62F).clientTrackingRange(10), net.minecraft.world.entity.Mob.createMobAttributes());

		KAZEHANA = Reg.living("kazehana", EntityType.Builder.of(Kazehana::new, MobCategory.MONSTER).sized(1.1F, 3.2F).eyeHeight(2.8F)
			.clientTrackingRange(12), Kazehana.createAttributes());
		PETAL_MIRAGE = Reg.living("petal_mirage", EntityType.Builder.of(PetalMirage::new, MobCategory.MONSTER).sized(1.1F, 3.2F).eyeHeight(2.8F)
			.clientTrackingRange(12).noSave(), PetalMirage.createAttributes());
		TEMPESTRA = Reg.living("tempestra", EntityType.Builder.of(Tempestra::new, MobCategory.MONSTER).sized(4.0F, 8.0F).eyeHeight(6.0F)
			.fireImmune().clientTrackingRange(16), Tempestra.createAttributes());
		HELIODOR = Reg.living("heliodor", EntityType.Builder.of(Heliodor::new, MobCategory.MONSTER).sized(2.6F, 6.0F).eyeHeight(5.2F).fireImmune()
			.clientTrackingRange(16), Heliodor.createAttributes());
		ANKHARA = Reg.living("ankhara", EntityType.Builder.of(Ankhara::new, MobCategory.MONSTER).sized(3.0F, 3.0F).eyeHeight(1.8F).fireImmune()
			.clientTrackingRange(16).updateInterval(1), Ankhara.createAttributes());

		SpawnPlacements.register(WINDWRIGHT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			net.minecraft.world.entity.Mob::checkMobSpawnRules);
		SpawnPlacements.register(DUNE_FACTOR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			net.minecraft.world.entity.Mob::checkMobSpawnRules);
		SpawnPlacements.register(GALE_KITE, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING, GaleKite::checkKiteSpawnRules);
		SpawnPlacements.register(BLOSSOM_BREEZE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(CUMULUS, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Cumulus::checkCumulusSpawnRules);
		SpawnPlacements.register(SKYWHALE, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING, Skywhale::checkWhaleSpawnRules);
		SpawnPlacements.register(GLASS_SCARAB, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(SANDSWIMMER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(SUNWRIGHT_REVENANT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(SUN_MOTE, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			Monster::checkAnyLightMonsterSpawnRules);
	}

	/** Spawn eggs are registered with the items so they land in the right creative tab. */
	public static void initSpawnEggs() {
		Reg.currentTab = "petalwind";
		Reg.spawnEgg("gale_kite", GALE_KITE);
		Reg.spawnEgg("blossom_breeze", BLOSSOM_BREEZE);
		Reg.spawnEgg("cumulus", CUMULUS);
		Reg.spawnEgg("skywhale", SKYWHALE);
		Reg.spawnEgg("windwright", WINDWRIGHT);
		Reg.currentTab = "glasswaste";
		Reg.spawnEgg("glass_scarab", GLASS_SCARAB);
		Reg.spawnEgg("sandswimmer", SANDSWIMMER);
		Reg.spawnEgg("sunwright_revenant", SUNWRIGHT_REVENANT);
		Reg.spawnEgg("sun_mote", SUN_MOTE);
		Reg.spawnEgg("dune_factor", DUNE_FACTOR);
	}
}
