package dev.elsewhere.stub.greenhaze_hush;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import net.minecraft.server.level.ServerPlayer;

/** Beta merge: greenhaze_hush's Glimmer stub, pointed at the arsenal's real pool (same public API as the stub). */
public final class GlimmerStub {
	private GlimmerStub() {}

	public static final float MAX = 20F, REGEN_PER_SECOND = 3F;

	public static float get(ServerPlayer p) {
		return Glimmer.current(p);
	}

	public static boolean trySpend(ServerPlayer p, float cost) {
		return Glimmer.trySpend(p, Math.max(0, (int) Math.ceil(cost - 1.0E-4F)));
	}

	/** 2.0 (dims-gh Stability clean-up): the stub's own pool is gone in the merge; the arsenal pool cleans up itself. */
	public static void forgetPlayer(java.util.UUID id) {
	}

	public static void clearAll() {
	}
}
