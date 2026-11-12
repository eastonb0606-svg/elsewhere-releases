package dev.elsewhere.gear3.s3.hurlbat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Hurlbat item (catalogue mechanic "hurlbat", stack 8): use throws one, then 0.6 s (12 ticks) before the next throw.
 * Kill switch off: use does nothing (PASS), the stack stays a plain item.
 */
public class HurlbatItem extends Item {
	public HurlbatItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!Hurlbats.enabled() || player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (level instanceof ServerLevel sl) Hurlbats.throwFrom(sl, player, stack);
		player.awardStat(Stats.ITEM_USED.get(this));
		player.getCooldowns().addCooldown(stack, Hurlbats.COOLDOWN_TICKS);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
