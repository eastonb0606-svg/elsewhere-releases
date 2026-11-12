package dev.elsewhere.palehollow.world;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Elsewhere 3.0 generation rules (Ideas/overhaul/GENERATION.md, G2/G3): seeded multi-offset site choice for surface structures.
 * Reads only the chunk generator (never the live world), so a site depends on the seed alone. Changes only where NEW starts appear;
 * piece code is untouched, so starts saved by 2.x finish exactly as they began.
 * Kill switch: {@code -Delsewhere.overhaul3.gen_sites=false} (or set {@link #ENABLED} to false) restores the 2.1 site code.
 * Identical copies live in each module that places structures (core, greenhaze_hush, verdigris_ironpeak, petalwind_glasswaste).
 */
public final class GenFit {
	private GenFit() {}

	/** Overhaul3 kill switch for the site rules of this module. */
	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.gen_sites", "true"));

	/**
	 * Limits for one structure family.
	 * @param radius    sampling radius of the core footprint (blocks)
	 * @param maxCut    most terrain the floor may sit below the highest sampled ground
	 * @param maxFill   most the floor may sit above the lowest sampled ground (keep it under the builder's foundation cap)
	 * @param maxWetPct most lattice points that may be under water (surface sites); ignored when {@code underwater}
	 * @param underwater the site must be on a sea or lake bed (at least 80% of the lattice under water)
	 */
	public record Limits(int radius, int maxCut, int maxFill, int maxWetPct, int minY, int maxY, boolean underwater) {
		public static Limits surface(int radius, int maxCut, int maxFill, int maxWetPct, int minY, int maxY) {
			return new Limits(radius, maxCut, maxFill, maxWetPct, minY, maxY, false);
		}
	}

	/** A chosen site. {@code floor} uses the "first occupied" convention (y of the top ground block), like the 2.1 code. */
	public record Site(int x, int z, int floor, int cut, int fill, int range, int wetPct, int score) {}

	/** Centre first, then +-8 on the axes, then +-16 on the diagonals (STRUCTURES.md 2.4). */
	static final int[][] OFFSETS = {{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}, {16, 16}, {-16, 16}, {16, -16}, {-16, -16}};

	/** Whole-chunk offsets only: for builders that write a 49-wide site from the chunk at its centre (they rely on the chunk middle). */
	static final int[][] ALIGNED = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {-16, 16}, {16, -16}, {-16, -16}};

	/** Best site in this start chunk, or empty when no candidate passes (then this grid cell has no start, like vanilla). */
	public static Optional<Site> pick(Structure.GenerationContext ctx, Limits lim) {
		return pick(ctx, lim, OFFSETS, lim.radius());
	}

	/**
	 * As {@link #pick(Structure.GenerationContext, Limits)} but every candidate stays at a chunk middle, so a builder that writes its whole
	 * site from the origin chunk never writes outside the 3x3 chunks that chunk may touch.
	 */
	public static Optional<Site> pickAligned(Structure.GenerationContext ctx, Limits lim, int pieceRadius) {
		return pick(ctx, lim, ALIGNED, pieceRadius);
	}

	static Optional<Site> pick(Structure.GenerationContext ctx, Limits lim, int[][] offsets, int pieceRadius) {
		ChunkPos cp = ctx.chunkPos();
		int mx = cp.getMiddleBlockX(), mz = cp.getMiddleBlockZ();
		// rarity stays as in 2.1: the offsets only look for better ground inside a start whose chunk middle is in a valid biome
		int g0 = ctx.chunkGenerator().getFirstOccupiedHeight(mx, mz, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
		if (!ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(mx >> 2, g0 >> 2, mz >> 2))) return Optional.empty();
		Site best = null;
		for (int[] o : offsets) {
			// the footprint must still cover the start chunk's middle: /locate, maps, traders and references look there
			if (Math.max(Math.abs(o[0]), Math.abs(o[1])) > pieceRadius - 2) continue;
			Site s = evaluate(ctx, mx + o[0], mz + o[1], lim, Math.abs(o[0]) + Math.abs(o[1]));
			if (s == null) continue;
			if (best == null || s.score() < best.score()) best = s;
			// good enough: stop sampling. Half the cut+fill budget keeps /locate and map trades cheap (each candidate is 13-26 noise columns)
			if (best.range() <= Math.max(3, (lim.maxCut() + lim.maxFill()) / 2) && (lim.underwater() || best.wetPct() == 0)) break;
		}
		return Optional.ofNullable(best);
	}

	/** Scores one candidate centre; null when it breaks a limit. */
	public static Site evaluate(Structure.GenerationContext ctx, int cx, int cz, Limits lim, int offset) {
		ChunkGenerator gen = ctx.chunkGenerator();
		int r = Math.max(2, Math.min(lim.radius(), 20)), h = Math.max(1, r / 2);
		// 3x3 lattice at the rim plus 4 points half-way in: 13 columns
		int[][] pts = {{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, r}, {r, -r}, {-r, -r}, {h, h}, {-h, h}, {h, -h}, {-h, -h}};
		int[] ground = new int[pts.length];
		int wet = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		// pass 1: ground only, with early outs (most rejected candidates stop after a few columns)
		for (int i = 0; i < pts.length; i++) {
			int x = cx + pts[i][0], z = cz + pts[i][1];
			int g = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
			ground[i] = g;
			// cheap first test (the 2.1 rule): the centre column's biome; skips the other 12 columns in a wrong biome
			if (i == 0 && !ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(x >> 2, g >> 2, z >> 2))) return null;
			lo = Math.min(lo, g);
			hi = Math.max(hi, g);
			// early out: the spread already exceeds what cut + fill can absorb
			if (hi - lo > lim.maxCut() + lim.maxFill()) return null;
		}
		// pass 2: water surface, only for candidates whose ground already fits
		for (int i = 0; i < pts.length; i++) {
			int s = gen.getFirstOccupiedHeight(cx + pts[i][0], cz + pts[i][1], Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
			if (s > ground[i]) {
				wet++;
				// interiors stay dry: the centre and the 4 half-way columns may never be under water on a dry-land site
				if (!lim.underwater() && lim.maxWetPct() < 50 && (i == 0 || i >= 9)) return null;
			}
		}
		if (lo <= ctx.heightAccessor().getMinY() + 1) return null; // no ground at all (void)
		int wetPct = wet * 100 / pts.length;
		if (lim.underwater() ? wetPct < 80 : wetPct > lim.maxWetPct()) return null;
		int[] sorted = ground.clone();
		Arrays.sort(sorted);
		int median = sorted[sorted.length / 2];
		int floorLo = hi - lim.maxCut(), floorHi = lo + lim.maxFill();
		if (floorLo > floorHi) return null;
		int floor = Math.max(floorLo, Math.min(floorHi, median));
		if (floor < lim.minY() || floor > lim.maxY()) return null;
		// biome: centre must be valid, and 3 of 4 rim points
		if (!ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(cx >> 2, floor >> 2, cz >> 2))) return null;
		int rimOk = 0;
		for (int i = 1; i <= 4; i++) {
			if (ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome((cx + pts[i][0]) >> 2, floor >> 2, (cz + pts[i][1]) >> 2))) rimOk++;
		}
		if (rimOk < 3) return null;
		int range = hi - lo;
		int score = 3 * range + (lim.underwater() ? 0 : wetPct / 10) + offset / 8;
		return new Site(cx, cz, floor, hi - floor, floor - lo, range, wetPct, score);
	}
}
