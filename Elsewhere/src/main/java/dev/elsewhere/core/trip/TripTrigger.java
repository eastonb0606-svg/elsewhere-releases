package dev.elsewhere.core.trip;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * One accidental-trip trigger (CONTRACT.md). A trigger only reports its base chance when its event happens; it calls
 * {@link AccidentalTrips#fire(ServerPlayer, TripTrigger)} from its own hook (sleep, fall, launch, item use, timed check)
 * and the framework applies every damping rule. The config value {@code accidentalTrips.triggers[id]} overrides
 * {@link #baseChance} when present.
 */
public interface TripTrigger {
	String id();

	ResourceKey<Level> target();

	Kind kind();

	double baseChance(ServerPlayer player);

	enum Kind {
		/** Sleeping somewhere, being in a biome/altitude, storms near water: damped by familiarity and home radius. */
		LOCATION,
		/** Falls, launches, item uses, a Warden targeting you: limited by an hourly budget. */
		ACTIVITY
	}
}
