package dev.elsewhere.stub.overworld_bosses;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Accidental-trip trigger interface from CONTRACT.md. This module has no dimension, so it has no triggers
 * ({@code OverworldBossesContent.TRIPS} is empty); the interface is here so the module matches the contract. STUB.
 */
public interface TripTrigger {
	String id();

	ResourceKey<Level> target();

	Kind kind();

	double baseChance(ServerPlayer player);

	enum Kind { LOCATION, ACTIVITY }
}
