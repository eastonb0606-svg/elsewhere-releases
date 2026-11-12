package dev.elsewhere.stub.core;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import net.minecraft.server.level.ServerPlayer;

/** Beta merge: the core's Glimmer stub, pointed at the arsenal's real pool. */
public final class GlimmerStub {
	private GlimmerStub() {
	}

	public static boolean trySpend(final ServerPlayer player, final int cost) {
		return Glimmer.trySpend(player, cost);
	}
}
