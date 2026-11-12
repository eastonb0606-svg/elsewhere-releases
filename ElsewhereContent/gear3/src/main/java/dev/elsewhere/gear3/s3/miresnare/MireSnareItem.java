package dev.elsewhere.gear3.s3.miresnare;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** The Mire Snare item (stack 8, catalogue mechanic "mire_snare"): places a {@link MireSnareBlock}; refuses while the kill switch is off. */
public class MireSnareItem extends BlockItem {
	public MireSnareItem(Block block, Properties props) {
		super(block, props);
		registerBlocks(Item.BY_BLOCK, this); // Gear3Items registers straight into the registry, so block.asItem() (pick-block) needs this
	}

	@Override
	public InteractionResult place(BlockPlaceContext ctx) {
		if (!MireSnare.ENABLED) return InteractionResult.FAIL;
		return super.place(ctx);
	}
}
