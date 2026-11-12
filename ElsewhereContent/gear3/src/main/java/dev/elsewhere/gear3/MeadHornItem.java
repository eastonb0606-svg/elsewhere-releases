package dev.elsewhere.gear3;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Mead-Horn of the Hall: drink to heal 6 over 3 s (Regeneration III for 3 s = 6 HP) and gain 3 Wolf-Fury stacks for 4 s
 * (s-setbonus-b, {@code UlfhednarWolfFury.grant}; with or without the Ulfhednar set), 30 s cooldown.
 */
public class MeadHornItem extends Item {
	public static final int COOLDOWN_TICKS = 600;

	public MeadHornItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) return InteractionResult.PASS;
		if (!level.isClientSide()) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 3));
			if (player instanceof ServerPlayer sp) dev.elsewhere.gear3.setbonus.b.UlfhednarWolfFury.grant(sp, dev.elsewhere.gear3.setbonus.b.UlfhednarWolfFury.HORN_STACKS);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
