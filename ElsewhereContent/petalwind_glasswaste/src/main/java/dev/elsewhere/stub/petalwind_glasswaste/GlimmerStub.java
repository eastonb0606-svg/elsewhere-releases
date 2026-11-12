package dev.elsewhere.stub.petalwind_glasswaste;

import net.minecraft.server.level.ServerPlayer;

/**
 * STUB of the arsenal module's Glimmer pool (ARSENAL.md). Until the merge points this at the real pool it always
 * succeeds (dev). Items call {@link #trySpend} instead of using cooldowns.
 */
public final class GlimmerStub {
	private GlimmerStub() {}

	/** Dev/self-test only: makes every spend fail (checks the "not enough Glimmer" path). */
	public static boolean denyForTest;
	public static int spentForTest;

	public static boolean trySpend(ServerPlayer player, int cost) {
		if (denyForTest) return false;
		spentForTest += cost;
		return true;
	}
}
