package dev.elsewhere.verdigris_ironpeak;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** A horn with a server-side effect and a cooldown (Hymnal Horn, Avalanche Horn). */
public class HornItem extends Item {
	public interface Effect {
		/** Returns false if the horn did nothing (no cooldown is applied). */
		boolean blow(ServerLevel level, ServerPlayer player, ItemStack stack);
	}

	private final Holder<SoundEvent> sound;
	private final int cooldownTicks;
	private final Effect effect;

	public HornItem(Holder<SoundEvent> sound, int cooldownTicks, Effect effect, Properties properties) {
		super(properties.stacksTo(1));
		this.sound = sound;
		this.cooldownTicks = cooldownTicks;
		this.effect = effect;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			level.playSound(null, player, sound.value(), SoundSource.RECORDS, 16.0F, 1.0F);
			level.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
			if (effect.blow(server, sp, stack)) player.getCooldowns().addCooldown(stack, cooldownTicks);
		}
		return InteractionResult.SUCCESS;
	}
}
