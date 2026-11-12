package dev.elsewhere.stub.greenhaze_hush;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * STUB of the arsenal module's Glimmer pool (ARSENAL.md section 1): max 20, regen 3/s starting 1 s after the last spend.
 * In-memory only (no attachment, so it can't clash with arsenal's {@code elsewhere:glimmer}). At merge, point
 * {@link #trySpend} at arsenal's API.
 */
public final class GlimmerStub {
	private GlimmerStub() {}

	public static final float MAX = 20F, REGEN_PER_SECOND = 3F;
	private static final Map<UUID, float[]> POOL = new HashMap<>(); // {glimmer, lastSpendGameTime}

	public static float get(ServerPlayer p) {
		return update(p)[0];
	}

	private static float[] update(ServerPlayer p) {
		long now = p.level().getGameTime();
		float[] s = POOL.computeIfAbsent(p.getUUID(), k -> new float[]{MAX, now, now});
		float since = now - s[2];
		if (now - s[1] > 20 && since > 0) s[0] = Math.min(MAX, s[0] + REGEN_PER_SECOND * since / 20F);
		s[2] = now;
		return s;
	}

	/** Spend cost Glimmer if the player has it; otherwise nothing happens and a soft amethyst clink plays. */
	public static boolean trySpend(ServerPlayer p, float cost) {
		if (p.isCreative()) return true;
		float[] s = update(p);
		if (s[0] < cost) {
			p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
			return false;
		}
		s[0] -= cost;
		s[1] = p.level().getGameTime();
		return true;
	}

	public static void forgetPlayer(UUID id) { POOL.remove(id); }

	public static void clearAll() { POOL.clear(); }
}
