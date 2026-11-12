package dev.elsewhere.stub.nether_end_bosses;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Contract interface for accidental trips. This module adds no dimensions, so it lists none. */
public interface TripTrigger {
	String id();

	ResourceKey<Level> target();

	Kind kind();

	double baseChance(ServerPlayer player);

	enum Kind { LOCATION, ACTIVITY }
}
