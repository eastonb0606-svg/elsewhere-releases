package dev.elsewhere.overworld_bosses;

import dev.elsewhere.boss.gulpmother.GulpmotherFrogspawnBlock;
import dev.elsewhere.boss.gulpmother.GulpmotherShrineCauldronBlock;
import dev.elsewhere.boss.neferkhet.NeferkhetSunAltarBlock;
import dev.elsewhere.boss.rimeback.FrozenHeartBlock;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Summon and arena blocks. */
public final class OwbBlocks {
	public static Block GULPMOTHER_SHRINE_CAULDRON, GULPMOTHER_FROGSPAWN, NEFERKHET_SUN_ALTAR, NEFERKHET_SUN_IDOL, FROZEN_HEART, VORLAGH_CAPSTAN;

	private OwbBlocks() {}

	public static void init() {
		GULPMOTHER_SHRINE_CAULDRON = withItem("gulpmother_shrine_cauldron", GulpmotherShrineCauldronBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noOcclusion().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE).noLootTable());
		GULPMOTHER_FROGSPAWN = BossRegistry.block("gulpmother_frogspawn", GulpmotherFrogspawnBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WATER).instabreak().noOcclusion().noCollision().sound(SoundType.FROGSPAWN).pushReaction(PushReaction.POPPED).noLootTable());
		NEFERKHET_SUN_ALTAR = withItem("neferkhet_sun_altar", NeferkhetSunAltarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).lightLevel(s -> 9).pushReaction(PushReaction.IMMOVEABLE).noLootTable());
		NEFERKHET_SUN_IDOL = withItem("neferkhet_sun_idol", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).lightLevel(s -> 12).pushReaction(PushReaction.IMMOVEABLE).noLootTable());
		FROZEN_HEART = withItem("frozen_heart", FrozenHeartBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.ICE).strength(2.0F, 6.0F).lightLevel(s -> 10).sound(SoundType.GLASS).friction(0.98F).noLootTable());
		VORLAGH_CAPSTAN = withItem("vorlagh_capstan", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).pushReaction(PushReaction.IMMOVEABLE).noLootTable());
	}

	private static Block withItem(String id, java.util.function.Function<BlockBehaviour.Properties, Block> f, BlockBehaviour.Properties p) {
		Block b = BossRegistry.block(id, f, p);
		BossRegistry.item(id, props -> new BlockItem(b, props), new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));
		return b;
	}
}
