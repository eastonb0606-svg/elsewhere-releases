package dev.elsewhere.stub.nether_end_bosses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Stand-in for the arsenal module's Glimmer pool (ARSENAL.md section 1): max 20, regenerates 3 per second starting 1 s
 * after the last spend. At merge this points at the arsenal's real pool (attributes, hunger cost, HUD, sync).
 */
public final class GlimmerStub {
	public static final float MAX = 20.0F;
	public static final float REGEN_PER_TICK = 3.0F / 20.0F;
	public static final int REGEN_DELAY_TICKS = 20;

	private record Pool(float value, long lastSpend) {}

	private static final Map<UUID, Pool> POOLS = new HashMap<>();

	private GlimmerStub() {}

	public static float current(Player player) {
		Pool p = POOLS.get(player.getUUID());
		if (p == null) return MAX;
		long since = player.level().getGameTime() - p.lastSpend() - REGEN_DELAY_TICKS;
		return Math.min(MAX, p.value() + Math.max(0, since) * REGEN_PER_TICK);
	}

	/** Spends {@code cost} Glimmer if the player has it; otherwise plays the "not enough" clink and returns false. */
	public static boolean trySpend(Player player, float cost) {
		if (player.isCreative()) return true;
		float now = current(player);
		if (now < cost) {
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
			return false;
		}
		POOLS.put(player.getUUID(), new Pool(now - cost, player.level().getGameTime()));
		return true;
	}
}
