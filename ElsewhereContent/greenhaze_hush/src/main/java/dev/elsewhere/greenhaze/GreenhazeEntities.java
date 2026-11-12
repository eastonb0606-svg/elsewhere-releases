package dev.elsewhere.greenhaze;

import dev.elsewhere.boss.grumblegut.Grumblegut;
import dev.elsewhere.boss.percolax.Percolax;
import dev.elsewhere.greenhaze_hush.GHProjectile;
import dev.elsewhere.greenhaze_hush.Reg;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

public final class GreenhazeEntities {
	private GreenhazeEntities() {}

	public static EntityType<GreenhazeMobs.LazyGorger> LAZY_GORGER;
	public static EntityType<GreenhazeMobs.MunchieBoar> MUNCHIE_BOAR;
	public static EntityType<GreenhazeMobs.TokeToad> TOKE_TOAD;
	public static EntityType<GreenhazeMobs.HotboxSmog> HOTBOX_SMOG;
	public static EntityType<GreenhazeMobs.ParanoiaShade> PARANOIA_SHADE;
	public static EntityType<GreenhazeMobs.BudBomb> BUD_BOMB;
	public static EntityType<GreenhazeMobs.Budtender> BUDTENDER;
	public static EntityType<Percolax> PERCOLAX;
	public static EntityType<Grumblegut> GRUMBLEGUT;
	public static EntityType<GHProjectile> SMOKE_RING, EMBER_FLICK, SPIT_SEED, CLOUD_JAR;

	static void init() {
		LAZY_GORGER = Reg.entity("lazy_gorger", EntityType.Builder.of(GreenhazeMobs.LazyGorger::new, MobCategory.CREATURE).sized(1.7F, 1.5F).clientTrackingRange(10));
		MUNCHIE_BOAR = Reg.entity("munchie_boar", EntityType.Builder.of(GreenhazeMobs.MunchieBoar::new, MobCategory.MONSTER).sized(1.4F, 1.2F).clientTrackingRange(10));
		TOKE_TOAD = Reg.entity("toke_toad", EntityType.Builder.of(GreenhazeMobs.TokeToad::new, MobCategory.CREATURE).sized(1.2F, 1.0F).clientTrackingRange(10));
		HOTBOX_SMOG = Reg.entity("hotbox_smog", EntityType.Builder.of(GreenhazeMobs.HotboxSmog::new, MobCategory.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).clientTrackingRange(10));
		PARANOIA_SHADE = Reg.entity("paranoia_shade", EntityType.Builder.of(GreenhazeMobs.ParanoiaShade::new, MobCategory.MONSTER).sized(0.5F, 0.8F).fireImmune().clientTrackingRange(8));
		BUD_BOMB = Reg.entity("bud_bomb", EntityType.Builder.of(GreenhazeMobs.BudBomb::new, MobCategory.MONSTER).sized(0.8F, 1.1F).clientTrackingRange(8));
		BUDTENDER = Reg.entity("budtender", EntityType.Builder.<GreenhazeMobs.Budtender>of(GreenhazeMobs.Budtender::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10));
		PERCOLAX = Reg.entity("percolax", EntityType.Builder.of(Percolax::new, MobCategory.MONSTER).sized(3.0F, 7.5F).fireImmune().clientTrackingRange(16));
		GRUMBLEGUT = Reg.entity("grumblegut", EntityType.Builder.of(Grumblegut::new, MobCategory.MONSTER).sized(3.0F, 3.6F).clientTrackingRange(16));
		SMOKE_RING = projectile("smoke_ring", 0.6F);
		EMBER_FLICK = projectile("ember_flick", 0.3F);
		SPIT_SEED = projectile("spit_seed", 0.3F);
		CLOUD_JAR = projectile("cloud_jar", 0.3F);

		FabricDefaultAttributeRegistry.register(LAZY_GORGER, GreenhazeMobs.LazyGorger.attributes());
		FabricDefaultAttributeRegistry.register(MUNCHIE_BOAR, GreenhazeMobs.MunchieBoar.attributes());
		FabricDefaultAttributeRegistry.register(TOKE_TOAD, GreenhazeMobs.TokeToad.attributes());
		FabricDefaultAttributeRegistry.register(HOTBOX_SMOG, GreenhazeMobs.HotboxSmog.attributes());
		FabricDefaultAttributeRegistry.register(PARANOIA_SHADE, GreenhazeMobs.ParanoiaShade.attributes());
		FabricDefaultAttributeRegistry.register(BUD_BOMB, GreenhazeMobs.BudBomb.attributes());
		FabricDefaultAttributeRegistry.register(BUDTENDER, Mob.createMobAttributes());
		FabricDefaultAttributeRegistry.register(PERCOLAX, Percolax.attributes());
		FabricDefaultAttributeRegistry.register(GRUMBLEGUT, Grumblegut.attributes());

		SpawnPlacements.register(LAZY_GORGER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(TOKE_TOAD, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(MUNCHIE_BOAR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);
		SpawnPlacements.register(HOTBOX_SMOG, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(PARANOIA_SHADE, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules);

		egg("lazy_gorger", LAZY_GORGER);
		egg("munchie_boar", MUNCHIE_BOAR);
		egg("toke_toad", TOKE_TOAD);
		egg("hotbox_smog", HOTBOX_SMOG);
		egg("paranoia_shade", PARANOIA_SHADE);
		egg("bud_bomb", BUD_BOMB);
		egg("budtender", BUDTENDER);
	}

	private static EntityType<GHProjectile> projectile(String name, float size) {
		return Reg.entity(name, EntityType.Builder.<GHProjectile>of(GHProjectile::new, MobCategory.MISC).sized(size, size)
			.clientTrackingRange(6).updateInterval(10));
	}

	private static void egg(String name, EntityType<?> type) {
		Reg.item(name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}
}
