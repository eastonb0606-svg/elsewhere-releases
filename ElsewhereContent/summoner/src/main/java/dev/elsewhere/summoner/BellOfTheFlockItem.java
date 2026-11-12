package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Bell of the Flock: trinket (+1 minion slot while active, see SummonerPerks); ringing it calls every minion to you. */
public class BellOfTheFlockItem extends Item {
	public BellOfTheFlockItem(Item.Properties p) {
		super(p);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		level.playSound(player, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.5F);
		if (player instanceof ServerPlayer sp) {
			level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.5F);
			MinionManager.recall(sp);
		}
		return InteractionResult.SUCCESS;
	}
}
