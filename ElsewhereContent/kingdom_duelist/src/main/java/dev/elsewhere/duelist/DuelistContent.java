package dev.elsewhere.duelist;

import dev.elsewhere.duelist.blocks.DuelBlock;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import dev.elsewhere.duelist.kit.ShieldHook;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Registry and init for the duelist module (shared ids start with {@code duelist_}). */
public final class DuelistContent {
	private DuelistContent() {}

	public static final String NS = "elsewhere";
	public static Block DUEL_BLOCK;
	public static Item DUEL_BLOCK_ITEM;
	public static EntityType<DuelistProjectile> PROJECTILE;
	public static EntityType<TestDuelist> TEST_DUELIST;
	public static EntityType<TestDuelist.Ruler> TEST_RULER;
	private static boolean inited;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ResourceKey<Block> bk = ResourceKey.create(Registries.BLOCK, id("duel_block"));
		DUEL_BLOCK = Registry.register(BuiltInRegistries.BLOCK, bk, new DuelBlock(BlockBehaviour.Properties.of().setId(bk)
			.mapColor(MapColor.GOLD).strength(0.45F).sound(SoundType.AMETHYST).noLootTable().pushReaction(PushReaction.POPPED).noOcclusion().randomTicks()));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, id("duel_block"));
		DUEL_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(DUEL_BLOCK, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		ResourceKey<EntityType<?>> pk = ResourceKey.create(Registries.ENTITY_TYPE, id("duelist_projectile"));
		PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, pk, EntityType.Builder.<DuelistProjectile>of(DuelistProjectile::new, MobCategory.MISC)
			.sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(5).noLootTable().build(pk));
		TEST_DUELIST = registerBoss("duelist_test", TestDuelist::new, TestDuelist.PROFILE);
		TEST_RULER = registerBoss("duelist_test_ruler", TestDuelist.Ruler::new, TestDuelist.RULER_PROFILE);
		DuelBlocks.init();
		ShieldHook.init();
		dev.elsewhere.duelist.kit.Timers.init();
		dev.elsewhere.duelist.kit.GroundPatch.init();
		dev.elsewhere.duelist.adapt.PlayerTrace.init();
		// each ruler's own last stand: PhaseController#onLethalDamage (there is no shared Totem)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof HumanBoss b) || !b.onLethal(source, amount));
		dev.elsewhere.duelist.identity.BossIdentity.init();
		dev.elsewhere.duelist.compat.TestMeterOrder.init(); // s-compat-testmeter: 300 HP cap after Test Meter's mob-health bonus
		dev.elsewhere.duelist.foresight.Foresight.init(); // s-foresight B5: attack hints only for foresight gear
	}

	/**
	 * Registers a boss entity type built on {@link HumanBoss}: player-sized, MobCategory.MISC (never uses the natural mob
	 * cap), attributes from the profile. {@code path} is the registry path (e.g. {@code azmar_sultana}). The client side
	 * calls {@code DuelistClient.registerRenderer(type)}. Idempotent per path.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends HumanBoss> EntityType<T> registerBoss(String path, EntityType.EntityFactory<T> factory, DuelistProfile profile) {
		Identifier rid = id(path);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(rid)) return (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(rid);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.of(factory, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(type, HumanBoss.attributes(profile));
		BOSS_TYPES.add(type);
		return type;
	}

	/** Every boss type registered through {@link #registerBoss} (the client registers a renderer for each). */
	public static final java.util.List<EntityType<? extends HumanBoss>> BOSS_TYPES = new java.util.ArrayList<>();
}
