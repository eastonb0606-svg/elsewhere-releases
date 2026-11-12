package dev.elsewhere.stub.petalwind_glasswaste;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import net.minecraft.server.level.ServerPlayer;

/** Beta merge: petalwind_glasswaste's Glimmer stub, pointed at the arsenal's real pool. */
public final class GlimmerStub {
	private GlimmerStub() {}

	/** Dev/self-test only: makes every spend fail (checks the "not enough Glimmer" path). */
	public static boolean denyForTest;
	public static int spentForTest;

	public static boolean trySpend(ServerPlayer player, int cost) {
		if (denyForTest) return false;
		boolean ok = Glimmer.trySpend(player, cost);
		if (ok) spentForTest += cost;
		return ok;
	}
}
