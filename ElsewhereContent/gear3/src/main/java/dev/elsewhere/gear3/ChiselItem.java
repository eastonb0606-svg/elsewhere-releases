package dev.elsewhere.gear3;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Mason's Chisel: use on a stone-family block to cycle plain -> bricks -> chiseled -> cracked -> plain. One use, no scan. */
public class ChiselItem extends Item {
	/** block -> next block in its family cycle. */
	public static final Map<Block, Block> NEXT = Map.ofEntries(
		Map.entry(Blocks.STONE, Blocks.STONE_BRICKS), Map.entry(Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS),
		Map.entry(Blocks.CHISELED_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS), Map.entry(Blocks.CRACKED_STONE_BRICKS, Blocks.STONE),
		Map.entry(Blocks.DEEPSLATE, Blocks.DEEPSLATE_BRICKS),
		Map.entry(Blocks.DEEPSLATE_BRICKS, Blocks.CHISELED_DEEPSLATE), Map.entry(Blocks.CHISELED_DEEPSLATE, Blocks.CRACKED_DEEPSLATE_BRICKS),
		Map.entry(Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE),
		Map.entry(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE), Map.entry(Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE), Map.entry(Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE),
		Map.entry(Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE), Map.entry(Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE), Map.entry(Blocks.CHISELED_RED_SANDSTONE, Blocks.RED_SANDSTONE),
		Map.entry(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE), Map.entry(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS),
		Map.entry(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE), Map.entry(Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
		Map.entry(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE),
		Map.entry(Blocks.TUFF, Blocks.POLISHED_TUFF), Map.entry(Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS), Map.entry(Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF_BRICKS),
		Map.entry(Blocks.CHISELED_TUFF_BRICKS, Blocks.CHISELED_TUFF), Map.entry(Blocks.CHISELED_TUFF, Blocks.TUFF));

	public ChiselItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Block next = NEXT.get(state.getBlock());
		if (next == null) return InteractionResult.PASS;
		if (!level.isClientSide()) {
			level.setBlockAndUpdate(pos, next.defaultBlockState());
			level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
			Player p = ctx.getPlayer();
			if (p != null) ctx.getItemInHand().hurtAndBreak(1, p, ctx.getHand());
		}
		return InteractionResult.SUCCESS;
	}
}
