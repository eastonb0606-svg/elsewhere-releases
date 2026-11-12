package dev.elsewhere.stub.verdigris_ironpeak;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** STUB of the core's accidental-trip trigger interface (CONTRACT.md). Triggers report only their base chance. */
public interface TripTrigger {
	String id();

	ResourceKey<Level> target();

	Kind kind();

	double baseChance(ServerPlayer player);

	enum Kind { LOCATION, ACTIVITY }
}
