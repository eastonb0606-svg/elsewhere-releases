package dev.elsewhere.petalwind;

import dev.elsewhere.stub.petalwind_glasswaste.TripTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Petalwind's accidental-trip triggers (DIMENSIONS.md section 9). Base chances only; the framework damps them. */
public final class PetalwindTrips {
	private PetalwindTrips() {}

	/** A fall of 40+ blocks, rolled once mid-fall (you land safe in the sky). ACTIVITY, budget 3/h. */
	public static final TripTrigger LONG_FALL = new TripTrigger() {
		@Override
		public String id() {
			return "petalwind.long_fall";
		}

		@Override
		public ResourceKey<Level> target() {
			return PetalwindDimension.LEVEL;
		}

		@Override
		public Kind kind() {
			return Kind.ACTIVITY;
		}

		@Override
		public double baseChance(ServerPlayer player) {
			return 0.15; // BALANCE.md (final)
		}
	};

	/** Launched by a breeze or a wind charge. ACTIVITY, budget 4/h. */
	public static final TripTrigger WIND_LAUNCH = new TripTrigger() {
		@Override
		public String id() {
			return "petalwind.wind_launch";
		}

		@Override
		public ResourceKey<Level> target() {
			return PetalwindDimension.LEVEL;
		}

		@Override
		public Kind kind() {
			return Kind.ACTIVITY;
		}

		@Override
		public double baseChance(ServerPlayer player) {
			return 0.08; // BALANCE.md (final)
		}
	};
}
