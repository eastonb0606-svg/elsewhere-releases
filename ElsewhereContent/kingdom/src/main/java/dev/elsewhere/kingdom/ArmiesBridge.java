package dev.elsewhere.kingdom;

/**
 * Package bridge for the 3.0 armies package ({@code dev.elsewhere.kingdom.armies}): exposes the one package-private hook it
 * needs without widening {@link KingdomSoldier}'s API. Removing the armies package leaves this class unused and harmless.
 */
public final class ArmiesBridge {
	private ArmiesBridge() {}

	/** Marks a soldier active (full AI) until {@code untilTick}; the same call {@link KingdomPerf} makes every 10 ticks. */
	public static void keepActive(KingdomSoldier s, long untilTick) {
		s.setActiveUntil(untilTick);
	}
}
