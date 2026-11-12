package dev.elsewhere.greenhaze;

import dev.elsewhere.stub.greenhaze_hush.TripSystem;
import dev.elsewhere.stub.greenhaze_hush.TripTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** DIMENSIONS.md 9: each Green Leaf item use has a 1.5% chance (BALANCE.md) (ACTIVITY trigger, first 12 uses per hour roll). */
public final class GreenhazeTrips {
	private GreenhazeTrips() {}

	/** BALANCE.md (final): 1.5% per Green Leaf use, first 12 uses per hour roll. */
	public static final double BASE_CHANCE = 0.015;

	public static final TripTrigger GREEN_LEAF_USE = new TripTrigger() {
		@Override public String id() { return "greenhaze.green_leaf_use"; }
		@Override public ResourceKey<Level> target() { return Greenhaze.LEVEL; }
		@Override public Kind kind() { return Kind.ACTIVITY; }
		@Override public double baseChance(ServerPlayer player) { return GreenLeafBridge.loaded() ? BASE_CHANCE : 0.0; }
	};

	/** Called from the item-use hooks whenever a Green Leaf item use completes. */
	public static void onGreenLeafUse(ServerPlayer p) {
		TripSystem.fire(p, GREEN_LEAF_USE, p.getRandom());
	}

	/** The Everflow Bong is a Green Leaf bong too. */
	public static void onGreenLeafStyleUse(ServerPlayer p) {
		if (GreenLeafBridge.loaded()) onGreenLeafUse(p);
	}
}
