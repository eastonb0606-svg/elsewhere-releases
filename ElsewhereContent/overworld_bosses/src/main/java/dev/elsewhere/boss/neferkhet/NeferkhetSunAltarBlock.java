package dev.elsewhere.boss.neferkhet;

import dev.elsewhere.overworld_bosses.Summons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:neferkhet_sun_altar}: the Sun Chamber altar. Place a gold block on it at noon (or use a gold block
 * on it) and Neferkhet wakes in the sunbeam. Unbreakable.
 */
public class NeferkhetSunAltarBlock extends Block {
	public NeferkhetSunAltarBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	public static boolean isNoon(Level level) {
		long t = level.getOverworldClockTime() % 24000L;
		return t >= 4500L && t <= 7500L;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.GOLD_BLOCK)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level instanceof ServerLevel sl) {
			if (!isNoon(sl)) {
				player.sendOverlayMessage(Component.translatable("message.elsewhere.neferkhet.not_noon"));
				return InteractionResult.FAIL;
			}
			stack.consume(1, player);
			Summons.summonNeferkhet(sl, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level instanceof ServerLevel sl && sl.getBlockState(pos.above()).is(Blocks.GOLD_BLOCK)) {
			if (isNoon(sl)) {
				sl.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
				Summons.summonNeferkhet(sl, pos);
			}
		}
	}
}
