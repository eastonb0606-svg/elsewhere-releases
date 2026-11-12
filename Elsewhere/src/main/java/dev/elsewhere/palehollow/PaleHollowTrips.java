package dev.elsewhere.palehollow;

import dev.elsewhere.core.trip.AccidentalTrips;
import dev.elsewhere.core.trip.TripTrigger;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pale Hollow accidental-trip triggers (DIMENSIONS.md section 9 with the 2026-11-10 rules): any completed sleep (2%)
 * and a completed sleep within 32 blocks of pale oak or a creaking heart (15%). Both are LOCATION triggers, so home
 * beds and familiar regions damp them heavily.
 */
public final class PaleHollowTrips {
	public static final TripTrigger SLEEP = trigger("pale_hollow.sleep", 0.02);
	public static final TripTrigger SLEEP_NEAR_PALE_OAK = trigger("pale_hollow.sleep_near_pale_oak", 0.12);

	private PaleHollowTrips() {
	}

	private static TripTrigger trigger(final String id, final double base) {
		return new TripTrigger() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public ResourceKey<Level> target() {
				return PaleHollowDimension.LEVEL;
			}

			@Override
			public Kind kind() {
				return Kind.LOCATION;
			}

			@Override
			public double baseChance(final ServerPlayer player) {
				return base;
			}
		};
	}

	public static void init() {
		AccidentalTrips.register(SLEEP);
		AccidentalTrips.register(SLEEP_NEAR_PALE_OAK);
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player && player.getSleepTimer() >= 100) {
				onSlept(player, pos);
			}
		});
	}

	/** A completed night's sleep at {@code bed}. */
	public static void onSlept(final ServerPlayer player, final BlockPos bed) {
		// one roll per event: the near-pale-oak roll replaces the any-sleep roll
		AccidentalTrips.fire(player, nearPaleOak(player.level(), bed) ? SLEEP_NEAR_PALE_OAK : SLEEP);
	}

	/** Pale oak (log, wood, leaves) or a creaking heart within 32 blocks horizontally, 12 vertically. */
	public static boolean nearPaleOak(final ServerLevel level, final BlockPos center) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -12; dy <= 12; dy += 2) {
			for (int dx = -32; dx <= 32; dx++) {
				for (int dz = -32; dz <= 32; dz++) {
					p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState s = level.getBlockState(p);
					if (s.is(BlockTags.PALE_OAK_LOGS) || s.is(Blocks.PALE_OAK_LEAVES) || s.is(Blocks.CREAKING_HEART)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
