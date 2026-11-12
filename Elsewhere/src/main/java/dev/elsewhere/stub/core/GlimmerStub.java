package dev.elsewhere.stub.core;

import net.minecraft.server.level.ServerPlayer;

/** Stand-in for the arsenal module's Glimmer pool (CONTRACT.md beta 2). The merge points this at the real one. */
public final class GlimmerStub {
	private GlimmerStub() {
	}

	/** Spends Glimmer; always succeeds until the arsenal's pool exists. */
	public static boolean trySpend(final ServerPlayer player, final int cost) {
		return true;
	}
}
