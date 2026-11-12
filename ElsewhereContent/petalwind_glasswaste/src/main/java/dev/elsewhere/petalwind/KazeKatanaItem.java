package dev.elsewhere.petalwind;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Kaze Katana (Kazehana's drop): sneak + use dashes about 5 blocks along your look direction. 4 s cooldown. */
public class KazeKatanaItem extends Item {
	public static final int COOLDOWN_TICKS = dev.elsewhere.petalwind_glasswaste.GearStats.KAZE_KATANA_COOLDOWN;

	public KazeKatanaItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) return InteractionResult.PASS;
		if (level instanceof ServerLevel server) {
			Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
			player.setDeltaMovement(look.x * dev.elsewhere.petalwind_glasswaste.GearStats.KAZE_KATANA_DASH, 0.15, look.z * dev.elsewhere.petalwind_glasswaste.GearStats.KAZE_KATANA_DASH);
			player.needsSync = true;
			server.sendParticles(ParticleTypes.CHERRY_LEAVES, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.5, 0.4, 0.1);
			server.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look.x, player.getY() + 1, player.getZ() + look.z, 1, 0, 0, 0, 0);
			server.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
