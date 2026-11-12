package dev.elsewhere.kingdom_court.world;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 realm terrain fit (GENERATION.md G4, generation lane) for NEW realm starts ("fv" 2 pieces): generator ground, per-part floors and the
 * per-piece beards. Saved 2.x starts (fv 1) never reach this class, so they finish exactly as they began.
 * Kill switch: {@code -Delsewhere.overhaul3.realm_fit=false} (new starts are fv 1 again: one beard_box start, every part at the castle floor).
 */
public final class RealmFit {
	private RealmFit() {}

	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.realm_fit", "true"));

	/** A town floor never differs from the castle floor by more than this (the road climbs at most 1 per block over ~50 blocks). */
	static final int TOWN_MAX_DY = 6;
	/** Half-size of the levelled castle yard that gets a thin beard (structures-build contract, 09:52). */
	static final int YARD = 28;

	/** Per realm (seed and site) memo of generator ground per column; a handful of realms per world. */
	static final Map<String, Map<Long, Integer>> MEMO = new ConcurrentHashMap<>();

	/**
	 * s-heapleak (3.0 heap pass): drops every realm's column memo. Called on SERVER_STOPPED ({@link CourtWorld#init}): the memo is keyed by
	 * seed, so without this a single-player client kept every world's realms (about 13k columns each) for as long as the game ran.
	 */
	public static void clearMemo() {
		MEMO.clear();
	}

	/** Memoized columns over all realms (self-test). */
	public static int memoColumns() {
		int n = 0;
		for (Map<Long, Integer> m : MEMO.values()) n += m.size();
		return n;
	}

	/** Realms with a memo (self-test). */
	public static int memoRealms() {
		return MEMO.size();
	}

	/**
	 * Generator-only ground: the first air above terrain or water ({@code WORLD_SURFACE_WG}, so roads and walls bridge a pond instead of
	 * sinking into it), exact per column, deterministic for every chunk (never reads the WorldGenLevel).
	 */
	public static final class HeightField implements CastleLayout.Ground {
		final ChunkGenerator gen;
		final RandomState rs;
		final LevelHeightAccessor h;
		final Map<Long, Integer> memo;
		final int castle, west, east;

		public HeightField(ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, long seed, BlockPos site, int west, int east) {
			this.gen = gen;
			this.rs = rs;
			this.h = h;
			this.memo = MEMO.computeIfAbsent(seed + "|" + site.asLong(), k -> new ConcurrentHashMap<>());
			this.castle = site.getY();
			this.west = west;
			this.east = east;
		}

		@Override
		public int y(int x, int z) {
			long k = ((long) x << 32) ^ (z & 0xffffffffL);
			Integer v = memo.get(k);
			if (v != null) return v;
			int g = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, h, rs);
			memo.putIfAbsent(k, g);
			return g;
		}

		@Override
		public int floor(CastleLayout.Part part, int fallback) {
			return switch (part) {
				case TOWN_WEST, KIT_TOWN_WEST -> west;
				case TOWN_EAST, KIT_TOWN_EAST -> east;
				default -> castle;
			};
		}
	}

	/**
	 * Floor of one town: the median generator ground over its pad (5x5 columns), kept within {@link #TOWN_MAX_DY} of the castle floor and
	 * equal to it when they differ by 1 or less.
	 */
	static int townFloor(HeightField hf, BlockPos site, boolean west) {
		int cx = site.getX() + (west ? -CastleLayout.TOWN_DX : CastleLayout.TOWN_DX), cz = site.getZ() + CastleLayout.TOWN_DZ;
		int[] g = new int[25];
		int i = 0;
		for (int dx = -20; dx <= 20; dx += 10) for (int dz = -20; dz <= 20; dz += 10) g[i++] = hf.y(cx + dx, cz + dz);
		Arrays.sort(g);
		int d = Math.clamp(g[12] - site.getY(), -TOWN_MAX_DY, TOWN_MAX_DY);
		return Math.abs(d) <= 1 ? site.getY() : site.getY() + d;
	}

	/**
	 * The beard of an fv 2 piece: the castle yard pad as a 1-high box at floor - 1 (beard_box on it = beard_thin); every other part none
	 * (structures-build 10:28: walls, roads and towns with per-house pads cut and fill their own ground; the dungeon is under the yard).
	 */
	static @Nullable BoundingBox beard(CastleLayout.Part part, BlockPos origin) {
		if (part != CastleLayout.Part.CASTLE) return null;
		int x = origin.getX(), y = origin.getY() - 1, z = origin.getZ();
		return new BoundingBox(x - YARD, y, z - YARD, x + YARD, y, z + YARD);
	}

	/** Whether {@code CastleLayout.town()} already follows {@link CourtWorld#townFloor}; without it towns stay at the castle floor. */
	static boolean anchorsFollowTownFloor() {
		BlockPos probe = new BlockPos(0x3ffff00, 100, 0x3ffff00);
		CourtWorld.TOWN_FLOORS.put(probe.asLong(), new int[] {103, 97});
		try {
			return CastleLayout.town(probe, true).getY() == 103 && CastleLayout.town(probe, false).getY() == 97;
		} finally {
			CourtWorld.TOWN_FLOORS.remove(probe.asLong());
		}
	}
}
