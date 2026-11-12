package dev.elsewhere.stub.petalwind_glasswaste;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * STUB of the core's accidental-trip trigger (CONTRACT.md). A trigger only reports its base chance when its event
 * happens; the trip framework ({@link TripStub} until the core exists) applies every damping rule.
 */
public interface TripTrigger {
	String id();

	ResourceKey<Level> target();

	Kind kind();

	double baseChance(ServerPlayer player);

	enum Kind { LOCATION, ACTIVITY }
}
