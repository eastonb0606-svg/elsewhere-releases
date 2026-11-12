package dev.elsewhere.stub.greenhaze_hush;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * STUB of the core's accidental-trip trigger (CONTRACT.md). A trigger only reports its base chance when its
 * event happens; {@link TripSystem} (the core's framework at merge) applies familiarity damping, hourly budgets,
 * cooldowns, the pity timer and the cancellable warning.
 */
public interface TripTrigger {
	String id();

	ResourceKey<Level> target();

	Kind kind();

	double baseChance(ServerPlayer player);

	enum Kind { LOCATION, ACTIVITY }
}
