package dev.elsewhere.glasswaste;

import dev.elsewhere.stub.petalwind_glasswaste.TripStub;
import dev.elsewhere.stub.petalwind_glasswaste.TripTrigger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

/** Glasswaste's accidental-trip trigger (DIMENSIONS.md section 9). */
public final class GlasswasteTrips {
	private GlasswasteTrips() {}

	public static final int NEEDED_SECONDS = 30;

	/** 30 s under open sky in a desert or badlands at midday, rolled once per in-game day. LOCATION (familiarity-damped). */
	public static final TripTrigger DESERT_NOON = new TripTrigger() {
		@Override
		public String id() {
			return "glasswaste.desert_noon";
		}

		@Override
		public ResourceKey<Level> target() {
			return GlasswasteDimension.LEVEL;
		}

		@Override
		public Kind kind() {
			return Kind.LOCATION;
		}

		@Override
		public double baseChance(ServerPlayer player) {
			return 0.15; // BALANCE.md (final)
		}
	};

	private static final class State {
		int seconds;
		long rolledDay = -1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	public static boolean qualifies(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD) return false;
		long time = level.getOverworldClockTime() % 24000L;
		if (time < 5000 || time > 7000) return false;
		BlockPos pos = player.blockPosition();
		var biome = level.getBiome(pos);
		if (!(biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS))) return false;
		return level.canSeeSky(pos.above());
	}

	/** Once per second. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State s = STATES.computeIfAbsent(player.getUUID(), u -> new State());
			long day = player.level().getOverworldClockTime() / 24000L;
			if (s.rolledDay == day || !qualifies(player)) {
				s.seconds = 0;
				continue;
			}
			if (++s.seconds >= NEEDED_SECONDS) {
				s.rolledDay = day;
				s.seconds = 0;
				TripStub.fire(DESERT_NOON, player);
			}
		}
	}
}
