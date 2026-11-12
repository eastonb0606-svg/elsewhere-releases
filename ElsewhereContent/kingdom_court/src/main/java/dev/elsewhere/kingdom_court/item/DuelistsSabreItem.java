package dev.elsewhere.kingdom_court.item;

import dev.elsewhere.kingdom_court.CourtCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Duelist's Sabre (Master-at-Arms): 7 damage, 1.8 speed (netherite sword 8 / 1.6). Use opens an 8-tick parry window
 * (2.5 s cooldown): a melee hit landing inside it is negated, the attacker is pushed back and you gain Strength I for 3 s.
 */
public class DuelistsSabreItem extends Item {
	public static final int PARRY_TICKS = 8;
	public static final int COOLDOWN = 50;

	public DuelistsSabreItem(Item.Properties p) {
		super(p);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		if (level instanceof ServerLevel sl) {
			CourtCombat.openParry(player, sl.getGameTime() + PARRY_TICKS);
			sl.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		return InteractionResult.SUCCESS;
	}
}
