package dev.elsewhere.overworld_bosses.fit3;

import dev.elsewhere.overworld_bosses.world.OwbPiece;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * 3.0 terrain fit for the four Overworld boss sites (STRUCTURES.md section 4 "owb", 2.5-2.11): plans each NEW start as several
 * {@link OwbFitPiece}s from the generator's own heights (never the live world, so the plan depends only on the seed):
 * <ul>
 * <li>croaking_shrine: a mangrove stilt shrine on piles above the water line, pool enclosed under the deck, boardwalks to dry land;</li>
 * <li>glacier_shelf: the plateau at the lattice median (cut and fill each at most 12), a thin beard so the ground meets it, the
 * avalanche slope anchored on the uphill side, ice buttresses where the ground drops, the entrance stair facing downhill;</li>
 * <li>sun_chamber: encapsulated (noise-level) chamber, rejected where a cave void crosses it, the shaft kiosk styled after the pyramid;</li>
 * <li>wreck_ring: the deck carried on hull stacks down to the sea bed, sunken hulks on shoals with masts to the surface.</li>
 * </ul>
 * Kill switch: {@code -Delsewhere.overhaul3.owb_fit=false} (or {@link #ENABLED}): new starts use the frozen 2.x {@code OwbPiece} again.
 * Saved 2.x starts always finish with {@code OwbPiece}.
 */
public final class OwbFit {
	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.owb_fit", "true"));

	public static final String CORE = "core", WALK = "walk", SLOPE = "slope", BUTTRESS = "buttress", STAIR = "stair", SHAFT = "shaft",
		STACK = "stack", HULK = "hulk";
	/** E, S, W, N as (dx, dz). */
	public static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
	static final int WALK_MAX = 20, STAIR_MAX = 16, CAP = 12;

	private static StructurePieceType type;

	private OwbFit() {}

	public static void init() {
		if (type != null) return;
		StructurePieceType t = (ctx, tag) -> new OwbFitPiece(tag);
		type = BuiltInRegistries.STRUCTURE_PIECE.containsKey(BossRegistry.id("owb_fit"))
			? BuiltInRegistries.STRUCTURE_PIECE.getValue(BossRegistry.id("owb_fit"))
			: Registry.register(BuiltInRegistries.STRUCTURE_PIECE, BossRegistry.id("owb_fit"), t);
	}

	public static StructurePieceType pieceType() {
		if (type == null) init();
		return type;
	}

	/** Generator heights: {@code ground} = first free y above solid ground (ocean floor), {@code surface} = first free y above ground or water. */
	public interface Heights {
		int ground(int x, int z);

		int surface(int x, int z);
	}

	/** Whether a block of the noise column is open (air or fluid): a cave or aquifer. */
	public interface Probe {
		boolean open(int x, int y, int z);
	}

	/** Noise samples (height queries and base columns) taken on this thread: the planner's cost, read by the self-test. */
	public static final ThreadLocal<int[]> SAMPLES = ThreadLocal.withInitial(() -> new int[1]);

	/** One exact, counted generator height (the site rules' own probes). */
	public static int exact(Structure.GenerationContext ctx, int x, int z, Heightmap.Types type) {
		SAMPLES.get()[0]++;
		return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, type, ctx.heightAccessor(), ctx.randomState());
	}

	/**
	 * STRUCTURES.md 2.3 HeightField: ground and surface sampled lazily on a 4-block lattice, memoised per start (one instance per plan, never
	 * shared between threads), bilinear between lattice points. The noise itself is interpolated over 4-block cells, so this keeps the shape
	 * while a 20-block boardwalk costs 5 samples instead of 20.
	 */
	public static Heights heights(Structure.GenerationContext ctx) {
		return new Field(ctx);
	}

	static final class Field implements Heights {
		private final Structure.GenerationContext ctx;
		private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap ground = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(),
			surface = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

		Field(Structure.GenerationContext ctx) {
			this.ctx = ctx;
			this.ground.defaultReturnValue(Integer.MIN_VALUE);
			this.surface.defaultReturnValue(Integer.MIN_VALUE);
		}

		private int sample(it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap m, Heightmap.Types type, int x, int z) {
			long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
			int v = m.get(k);
			if (v == Integer.MIN_VALUE) {
				v = exact(this.ctx, x, z, type);
				m.put(k, v);
			}
			return v;
		}

		private int at(it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap m, Heightmap.Types type, int x, int z) {
			int x0 = x & ~3, z0 = z & ~3, fx = x & 3, fz = z & 3;
			double v00 = sample(m, type, x0, z0);
			if (fx == 0 && fz == 0) return (int) v00;
			if (fz == 0) return (int) Math.floor(lerp(v00, sample(m, type, x0 + 4, z0), fx / 4.0) + 0.5);
			double v01 = sample(m, type, x0, z0 + 4);
			if (fx == 0) return (int) Math.floor(lerp(v00, v01, fz / 4.0) + 0.5);
			double v10 = sample(m, type, x0 + 4, z0), v11 = sample(m, type, x0 + 4, z0 + 4);
			return (int) Math.floor(lerp(lerp(v00, v10, fx / 4.0), lerp(v01, v11, fx / 4.0), fz / 4.0) + 0.5);
		}

		private static double lerp(double a, double b, double t) {
			return a + (b - a) * t;
		}

		@Override
		public int ground(int x, int z) {
			return at(this.ground, Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		}

		@Override
		public int surface(int x, int z) {
			return at(this.surface, Heightmap.Types.WORLD_SURFACE_WG, x, z);
		}
	}

	/** One base column per probed (x, z), memoised for the plan (the chamber check reads 5 columns at up to 4 depths). */
	public static Probe probe(Structure.GenerationContext ctx) {
		var gen = ctx.chunkGenerator();
		return new Probe() {
			final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.level.NoiseColumn> cols =
				new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

			@Override
			public boolean open(int x, int y, int z) {
				var col = this.cols.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> {
					SAMPLES.get()[0]++;
					return gen.getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
				});
				var s = col.getBlock(y);
				return s.isAir() || !s.getFluidState().isEmpty();
			}
		};
	}

	/** Nearest multiple of 4 (a HeightField lattice line). */
	static int snap4(double v) {
		return (int) Math.round(v / 4.0) * 4;
	}

	// ---- planning -------------------------------------------------------------------------------------------------------------

	/**
	 * 13-column lattice over a disc of radius r: centre, 4 on the axes at r, 4 diagonals at r*0.7, 4 half-way on the axes; offsets snapped
	 * to the 4-block HeightField lattice, so each point is one sample.
	 */
	static int[] lattice(int x, int z, int r, Heights h) {
		int d = snap4(r * 0.7), m = snap4(r / 2.0);
		r = snap4(r);
		int[][] pts = {{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}, {d, d}, {-d, d}, {d, -d}, {-d, -d}, {m, 0}, {-m, 0}, {0, m}, {0, -m}};
		int[] out = new int[pts.length];
		for (int i = 0; i < pts.length; i++) out[i] = h.ground(x + pts[i][0], z + pts[i][1]);
		return out;
	}

	static int median(int[] v) {
		int[] c = v.clone();
		Arrays.sort(c);
		return c[c.length / 2];
	}

	/** Croaking Shrine: deck one above the water line (or one above the median ground on a dry site), three boardwalks to dry land. */
	public static List<StructurePiece> shrine(int x, int z, int sea, Heights h) {
		int deck = Math.max(sea, median(lattice(x, z, 15, h)) + 1);
		BlockPos o = new BlockPos(x, deck + 1, z);
		List<StructurePiece> out = new ArrayList<>();
		out.add(new OwbFitPiece(OwbPiece.CROAKING_SHRINE, CORE, o, o, 0, 0, new int[0], TerrainAdjustment.NONE));
		for (int dir = 0; dir < 3; dir++) {
			int[] prof = walkProfile(x, z, dir, deck, sea, h);
			out.add(new OwbFitPiece(OwbPiece.CROAKING_SHRINE, WALK, o, o.offset(DIRS[dir][0] * 16, -1, DIRS[dir][1] * 16), dir, prof.length, prof,
				TerrainAdjustment.NONE));
		}
		return out;
	}

	/** Plank heights along a boardwalk from the deck edge (r 16): over water one above it, over land on the ground, steps of at most 1. */
	static int[] walkProfile(int x, int z, int dir, int deck, int sea, Heights h) {
		int[] prof = new int[WALK_MAX];
		int prev = deck, n = WALK_MAX;
		for (int s = 0; s < WALK_MAX; s++) {
			int cx = x + DIRS[dir][0] * (16 + s), cz = z + DIRS[dir][1] * (16 + s);
			int ground = h.ground(cx, cz);
			boolean wet = ground < sea; // below sea level in a swamp: water (or a hole the walk bridges on piles)
			int want = wet ? sea : ground;
			if (!wet && want > prev + 1 && s >= 2) { n = s; break; } // a bank too steep to climb: the walk ends at its foot
			int y = Math.max(prev - 1, Math.min(prev + 1, want));
			prof[s] = y;
			prev = y;
			if (!wet && y == ground && s >= 2) { n = s + 1; break; } // landed: the plank rests on dry ground
		}
		return Arrays.copyOf(prof, Math.max(1, n));
	}

	/**
	 * Glacier Shelf at a site already chosen by the 3.0 site rule (spread at most 24 over its box): floor at the lattice median, clamped so
	 * the cut and the fill over the plateau are each at most 12.
	 */
	public static List<StructurePiece> glacier(int x, int z, Heights h) {
		int[] lat = lattice(x, z, 16, h);
		int lo = Arrays.stream(lat).min().orElse(64), hi = Arrays.stream(lat).max().orElse(64);
		int floor = hi - lo > 24 ? (hi + lo) / 2 : Math.max(hi - CAP, Math.min(lo + CAP, median(lat)));
		BlockPos o = new BlockPos(x, floor, z);
		int g = floor - 1;
		// the entrance faces downhill: E, S or W, whichever side's ground is lowest (ties go south); read just past the rim, on the lattice
		int best = 1, bestY = h.ground(x, z + 20);
		for (int dir : new int[] {0, 2}) {
			int y = h.ground(x + DIRS[dir][0] * 20, z + DIRS[dir][1] * 20);
			if (y < bestY) { best = dir; bestY = y; }
		}
		List<StructurePiece> out = new ArrayList<>();
		out.add(new OwbFitPiece(OwbPiece.GLACIER_SHELF, CORE, o, o, best, 0, new int[0], TerrainAdjustment.BEARD_THIN));
		out.add(new OwbFitPiece(OwbPiece.GLACIER_SHELF, SLOPE, o, o.offset(0, 0, -18), 0, 0, new int[0], TerrainAdjustment.NONE));
		// ice buttresses on the rim where the ground drops away (not on the north side, where the slope is)
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			int rx = (int) Math.round(Math.cos(a) * 19), rz = (int) Math.round(Math.sin(a) * 19);
			if (rz < -12 || i == best * 2) continue; // not on the north slope, not across the entrance stair
			int drop = floor - h.ground(x + snap4(rx), z + snap4(rz)); // the rim's nearest lattice point
			if (drop > 2) out.add(new OwbFitPiece(OwbPiece.GLACIER_SHELF, BUTTRESS, o, o.offset(rx, 0, rz), i, Math.min(drop, 14), new int[0], TerrainAdjustment.NONE));
		}
		int[] prof = stairProfile(x, z, best, g, h);
		out.add(new OwbFitPiece(OwbPiece.GLACIER_SHELF, STAIR, o, o.offset(DIRS[best][0] * 19, -1, DIRS[best][1] * 19), best, prof.length, prof, TerrainAdjustment.NONE));
		return out;
	}

	/** Tread heights from the plateau rim (r 19) down (or up) to the ground, one block per step. */
	static int[] stairProfile(int x, int z, int dir, int g, Heights h) {
		int[] prof = new int[STAIR_MAX];
		int prev = g, n = STAIR_MAX;
		for (int s = 0; s < STAIR_MAX; s++) {
			int want = h.ground(x + DIRS[dir][0] * (19 + s), z + DIRS[dir][1] * (19 + s)) - 1;
			int y = Math.max(prev - 1, Math.min(prev + 1, want));
			prof[s] = y;
			prev = y;
			if (y == want && s >= 1) { n = s + 1; break; }
		}
		return Arrays.copyOf(prof, n);
	}

	/** Sun Chamber: the encapsulated chamber and the shaft with its kiosk at the surface beside the pyramid. */
	public static List<StructurePiece> sun(BlockPos origin, int top) {
		List<StructurePiece> out = new ArrayList<>();
		out.add(new OwbFitPiece(OwbPiece.SUN_CHAMBER, CORE, origin, origin, 0, top, new int[0], TerrainAdjustment.ENCAPSULATE));
		out.add(new OwbFitPiece(OwbPiece.SUN_CHAMBER, SHAFT, origin, new BlockPos(origin.getX() + 16, top, origin.getZ()), 0, top, new int[0], TerrainAdjustment.NONE));
		return out;
	}

	/** Chamber floors tried under one pyramid: the 2.1 depth, then 3 deeper, so a cave at one depth does not lose the site. */
	public static int[] sunFloors(int floor) {
		return new int[] {floor, floor - 7, floor - 14, floor - 21};
	}

	/** True when a cave or aquifer void crosses the chamber's hall (5 columns, floor to roof). */
	public static boolean chamberHitsCave(int x, int z, int floor, Probe p) {
		int[][] cols = {{0, 0}, {8, 8}, {-8, 8}, {8, -8}, {-8, -8}};
		for (int[] c : cols) {
			for (int y = floor - 2; y <= floor + 14; y += 2) if (p.open(x + c[0], y, z + c[1])) return true;
		}
		return false;
	}

	/** Wreck Ring: the deck ring at sea level, 8 hull stacks down to the bed, 3 sunken hulks on shoals between the deck hulls. */
	public static List<StructurePiece> wreck(int x, int z, int sea, Heights h) {
		BlockPos o = new BlockPos(x, sea + 1, z);
		List<StructurePiece> out = new ArrayList<>();
		out.add(new OwbFitPiece(OwbPiece.WRECK_RING, CORE, o, o, 0, 0, new int[0], TerrainAdjustment.NONE));
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8 + Math.PI / 8;
			int sx = x + (int) Math.round(Math.cos(a) * 11.5), sz = z + (int) Math.round(Math.sin(a) * 11.5);
			out.add(new OwbFitPiece(OwbPiece.WRECK_RING, STACK, o, new BlockPos(sx, Math.min(h.ground(sx, sz), sea - 2), sz), i, 0, new int[0], TerrainAdjustment.NONE));
		}
		for (int i = 0; i < 3; i++) {
			double a = Math.PI * 2 * i / 3 + Math.PI / 2;
			int hx = x + (int) Math.round(Math.cos(a) * 21), hz = z + (int) Math.round(Math.sin(a) * 21);
			out.add(new OwbFitPiece(OwbPiece.WRECK_RING, HULK, o, new BlockPos(hx, Math.min(h.ground(hx, hz), sea - 6), hz), i, 0, new int[0], TerrainAdjustment.NONE));
		}
		return out;
	}
}
