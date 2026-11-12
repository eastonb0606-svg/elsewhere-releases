package dev.elsewhere.gear2.item;

import dev.elsewhere.gear2.Gear2Items;
import dev.elsewhere.gear2.Termites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** gear-areaminer-b Termite Hive Lantern: mining an edible block sends termites over the 3x3x1 face around it within 2 s. */
public class TermiteHiveLantern extends Item {
	public TermiteHiveLantern(Properties p) {
		super(p);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		if (level instanceof ServerLevel sl && miner instanceof ServerPlayer p && !Termites.chewing() && state.is(Gear2Items.TERMITE_EDIBLE)) {
			Termites.spread(sl, p, pos);
		}
		return super.mineBlock(stack, level, state, pos, miner);
	}
}
