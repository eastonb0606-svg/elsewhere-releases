package dev.elsewhere.stub.nether_end_bosses;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Beta merge: nether_end_bosses' Glimmer stub, pointed at the arsenal's real pool (same public API as the stub). */
public final class GlimmerStub {
	public static final float MAX = 20.0F;
	public static final float REGEN_PER_TICK = 3.0F / 20.0F;
	public static final int REGEN_DELAY_TICKS = 20;

	private GlimmerStub() {}

	public static float current(Player player) {
		return Glimmer.current(player);
	}

	public static boolean trySpend(Player player, float cost) {
		if (!(player instanceof ServerPlayer sp)) return true;
		return Glimmer.trySpend(sp, Math.max(0, (int) Math.ceil(cost - 1.0E-4F)));
	}
}
