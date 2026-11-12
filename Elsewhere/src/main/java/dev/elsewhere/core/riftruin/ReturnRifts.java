package dev.elsewhere.core.riftruin;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.core.travel.RiftTravel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Return Rifts: one ruin with a permanent one-way rift home per 600x600-block cell of every Elsewhere dimension that
 * opts in ({@link ElsewhereDimensions.Info#returnRifts()}). Positions are a pure function of seed, dimension and cell,
 * so landings and the Frayed Wayfinder can find the nearest one without loading chunks.
 */
public final class ReturnRifts {
	public static final int CELL = 600;
	/** Wayfinder and accidental arrivals land within this many blocks of a Return Rift. */
	public static final int LANDING_RADIUS = 250;
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();

	private ReturnRifts() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Elsewhere.id("return_rift"), ReturnRiftFeature.CODEC);
	}

	/** The rift position (x, z; y is a placeholder 64) of one cell. */
	public static BlockPos anchor(final long seed, final ResourceKey<Level> dim, final int cellX, final int cellZ) {
		long h = seed ^ (dim.identifier().toString().hashCode() * 0x9E3779B97F4A7C15L) ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L);
		h = mixBits(h);
		int ox = 96 + (int)Math.floorMod(h, CELL - 192);
		int oz = 96 + (int)Math.floorMod(h >>> 24, CELL - 192);
		return new BlockPos(cellX * CELL + ox, 64, cellZ * CELL + oz);
	}

	private static long mixBits(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static BlockPos nearestRift(final long seed, final ResourceKey<Level> dim, final int x, final int z) {
		int cx = Mth.floorDiv(x, CELL);
		int cz = Mth.floorDiv(z, CELL);
		BlockPos best = null;
		long bestD = Long.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos a = anchor(seed, dim, cx + dx, cz + dz);
				long d = (long)(a.getX() - x) * (a.getX() - x) + (long)(a.getZ() - z) * (a.getZ() - z);
				if (d < bestD) {
					bestD = d;
					best = a;
				}
			}
		}
		return best;
	}

	public static BlockPos nearestRift(final ServerLevel level, final int x, final int z) {
		return nearestRift(level.getSeed(), level.dimension(), x, z);
	}

	/** Called when a player touches a Return Rift block. */
	public static void use(final ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_USE.get(player.getUUID());
		if (last != null && now - last < 60) {
			return;
		}
		LAST_USE.put(player.getUUID(), now);
		player.sendSystemMessage(Component.translatable("message.elsewhere.return_rift"));
		RiftTravel.Destination dest = player.isShiftKeyDown() ? RiftTravel.respawnDestination(player) : RiftTravel.homeDestination(player);
		RiftTravel.teleport(player, dest.level(), dest.pos(), player.getYRot());
	}

	public static boolean enabledIn(final ResourceKey<Level> dim) {
		ElsewhereDimensions.Info info = ElsewhereDimensions.get(dim);
		return info != null && info.returnRifts();
	}
}
