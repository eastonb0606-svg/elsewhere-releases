package dev.elsewhere.minion;

import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.summoner.GearStats;
import net.minecraft.server.level.ServerPlayer;

/**
 * The battle-call fervor timer (ARSENAL.md rev 2 §5a, always on) plus optional upkeep rules (all switched by {@link GearStats}; 0 = off): a per-summon lifetime, an ongoing Glimmer drain,
 * an ongoing hunger drain and dismiss-when-idle. The design picks which are on.
 */
public final class MinionUpkeep {
	private MinionUpkeep() {}

	/** Overridable at runtime by the self-test (and a future server config). */
	public static int durationTicks = GearStats.SUMMON_DURATION_TICKS;
	public static float glimmerPerSecond = GearStats.DRAIN_GLIMMER_PER_SECOND;
	public static float exhaustionPerSecond = GearStats.DRAIN_EXHAUSTION_PER_SECOND;
	public static int idleDismissTicks = GearStats.IDLE_DISMISS_TICKS;

	public static int fervorTicks = GearStats.FERVOR_TICKS;

	public static boolean expired(MinionEntity m) {
		if (m.fervorTicks <= 0) return true;
		if (durationTicks > 0 && m.lifeTicks >= durationTicks) return true;
		return idleDismissTicks > 0 && m.idleTicks >= idleDismissTicks;
	}

	/** Once per second per minion: pays the drains; false = could not pay (the minion is dismissed). */
	public static boolean paySecond(MinionEntity m, ServerPlayer owner) {
		if (exhaustionPerSecond > 0 && !owner.isCreative()) owner.causeFoodExhaustion(exhaustionPerSecond);
		float drain = glimmerPerSecond + MinionTuning.glimmerDrainPerSecond(owner);
		if (drain > 0 && !owner.isCreative()) return GlimmerStub.drain(owner, drain);
		return true;
	}
}
