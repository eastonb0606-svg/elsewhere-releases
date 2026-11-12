package dev.elsewhere.registry;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.boss.ArenaSealBlock;
import dev.elsewhere.core.riftruin.ReturnRiftBlock;
import dev.elsewhere.core.riftruin.ReturnRiftBlockEntity;
import dev.elsewhere.palehollow.block.MoonBloomBlock;
import dev.elsewhere.palehollow.block.ResinHeartBlock;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	// ---- core
	public static final Block ARENA_SEAL = register("arena_seal", ArenaSealBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().sound(SoundType.GLASS)
		.lightLevel(s -> 7).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, box) -> false).pushReaction(PushReaction.IMMOVEABLE), false);
	public static final Block RETURN_RIFT = register("return_rift", ReturnRiftBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noLootTable().noCollision().noOcclusion().sound(SoundType.AMETHYST)
		.lightLevel(s -> 15).pushReaction(PushReaction.IMMOVEABLE), true);

	// ---- Pale Hollow
	public static final Block RESIN_HEART = register("resin_heart", ResinHeartBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_ORANGE).strength(4.0F, 6.0F).sound(SoundType.CREAKING_HEART).lightLevel(s -> 10)
		.emissiveRendering(s -> true), true);
	public static final Block HEARTWOOD_LOG = register("heartwood_log", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.QUARTZ).strength(2.0F).sound(SoundType.WOOD).lightLevel(s -> 4).ignitedByLava(), true);
	public static final Block MOONRING_STONE = register("moonring_stone", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.TERRACOTTA_GRAY).strength(1.5F, 6.0F).sound(SoundType.TUFF).requiresCorrectToolForDrops(), true);
	public static final Block ASHEN_MOSS = register("ashen_moss", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.1F).sound(SoundType.MOSS), true);
	public static final Block MOON_BLOOM = register("moon_bloom", MoonBloomBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_GRAY).noCollision().instabreak().sound(SoundType.GRASS).lightLevel(s -> 5)
		.offsetType(BlockBehaviour.OffsetType.NONE).pushReaction(PushReaction.POPPED), true);

	public static final BlockEntityType<ReturnRiftBlockEntity> RETURN_RIFT_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, Elsewhere.id("return_rift"),
		FabricBlockEntityTypeBuilder.create(ReturnRiftBlockEntity::new, RETURN_RIFT).build()
	);


	// ---- 2.0
	/** polish-pale-hollow: Amber ore (tuff below Y 30 in the Pale Hollow, about diamond rarity). Drops 1 Amber (Fortune applies). */
	public static final Block AMBER_ORE = register("amber_ore", p -> new net.minecraft.world.level.block.DropExperienceBlock(
		net.minecraft.util.valueproviders.UniformInt.of(3, 7), p), BlockBehaviour.Properties.of()
		.mapColor(MapColor.TERRACOTTA_ORANGE).strength(3.0F, 3.0F).sound(SoundType.TUFF).requiresCorrectToolForDrops(), true);
	/** new-rift-anchor. */
	public static final Block RIFT_ANCHOR = register("rift_anchor", dev.elsewhere.core.travel.RiftAnchorBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_PURPLE).strength(3.5F, 1200.0F).sound(SoundType.LODESTONE).lightLevel(s -> 9).requiresCorrectToolForDrops(), true);
	/** new-boss-waystones (placed by the game at arenas; unbreakable). */
	public static final Block BOSS_WAYSTONE = register("boss_waystone", dev.elsewhere.core.travel.BossWaystoneBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.LODESTONE).lightLevel(s -> 12)
		.pushReaction(PushReaction.IMMOVEABLE), false);
	/** new-boss-trophies. */
	public static final Block BOSS_TROPHY = register("boss_trophy", dev.elsewhere.core.trophy.BossTrophyBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.GOLD).strength(2.0F, 6.0F).noLootTable().noOcclusion().sound(SoundType.METAL).lightLevel(s -> 6), true);

	public static final BlockEntityType<dev.elsewhere.core.trophy.BossTrophyBlockEntity> BOSS_TROPHY_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, Elsewhere.id("boss_trophy"),
		FabricBlockEntityTypeBuilder.create(dev.elsewhere.core.trophy.BossTrophyBlockEntity::new, BOSS_TROPHY).build()
	);

	private ModBlocks() {
	}

	private static <B extends Block> B register(final String name, final Function<BlockBehaviour.Properties, B> factory,
		final BlockBehaviour.Properties props, final boolean withItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Elsewhere.id(name));
		B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		if (withItem) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Elsewhere.id(name));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		}
		return block;
	}

	public static void init() {
	}
}
