package dev.elsewhere.rivals.azmar;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Faience fire pot: right-click throws it (see {@link FirePots}). */
public class FirePotItem extends Item {
	public FirePotItem(Item.Properties p) {
		super(p);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
		FirePots.throwPot(sl, player, player.getEyePosition().add(0, -0.2, 0), player.getLookAngle(), 1.0);
		if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
		return InteractionResult.SUCCESS_SERVER;
	}
}
