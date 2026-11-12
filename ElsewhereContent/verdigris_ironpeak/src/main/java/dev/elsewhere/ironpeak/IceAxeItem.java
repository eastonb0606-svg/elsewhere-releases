package dev.elsewhere.ironpeak;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Ice Axe: use it on a wall to cling there for 3 seconds (no falling, no fall damage). 4 s cooldown. */
public class IceAxeItem extends Item {
	public static final int CLING_TICKS = 60;
	/** Players currently clinging, with ticks left. Ticked by {@link ThinAir}. */
	public static final Map<UUID, Integer> CLINGING = new ConcurrentHashMap<>();

	public IceAxeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || context.getClickedFace().getAxis() == Direction.Axis.Y) return InteractionResult.PASS;
		if (player.onGround() || player.getCooldowns().isOnCooldown(context.getItemInHand())) return InteractionResult.PASS;
		if (player instanceof ServerPlayer sp) {
			CLINGING.put(sp.getUUID(), CLING_TICKS);
			sp.setDeltaMovement(0, 0, 0);
			sp.needsSync = true;
			sp.resetFallDistance();
			sp.level().playSound(null, sp.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4F, 1.9F);
			sp.getCooldowns().addCooldown(context.getItemInHand(), dev.elsewhere.verdigris_ironpeak.GearStats.ICE_AXE_COOLDOWN);
		}
		return InteractionResult.SUCCESS;
	}

	/** Called every tick for clinging players: hold them in place until the timer runs out or they jump off. */
	public static void tickCling(ServerPlayer player) {
		Integer left = CLINGING.get(player.getUUID());
		if (left == null) return;
		if (left <= 0 || player.onGround() || player.isShiftKeyDown()) {
			CLINGING.remove(player.getUUID());
			return;
		}
		CLINGING.put(player.getUUID(), left - 1);
		player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 0, 0.2));
		player.needsSync = true;
		player.resetFallDistance();
	}
}
