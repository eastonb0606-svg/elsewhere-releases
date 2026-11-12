package dev.elsewhere.palehollow.fit3;

import dev.elsewhere.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lantern Procession road (STRUCTURES.md 2.10 and 4, core): a contour road 40-80 blocks long. The route is the cheapest path over a ground
 * lattice (5 blocks along, 5 lanes of 4 blocks across; cost = distance + slope^2 x 6 + water x 40 + lane drift; the lattice only moves forward,
 * so the search is exact shortest path on a DAG), smoothed twice (Chaikin) and rasterised 3 wide. Per cell the walking height is the ground,
 * with valleys raised so neighbours differ by at most one block: half-block steps are slabs, full-block steps are stairs. Dips of at most 3
 * get a rubble causeway, deeper dips and water a timber trestle on log piles with rails. Hanging-lantern posts stand on the outside of bends
 * and every 10 cells on straights; a few candles along the edge; every third road carries a Return Rift pad at its middle. No origin-y
 * fallback, no air box: only plants are removed, and natural blocks at most 2 above the deck where the ground rises between lattice points.
 */
public final class RoadKit {
	private RoadKit() {}

	static final int STEP = 5, LANES = 2, LANE_W = 4;

	public static void plan(CoreFit.Ground g, BlockPos o, long seed, boolean rift, List<StructurePiece> out) {
		double ang = Math.floorMod(seed >>> 13, 3600L) / 3600.0 * Math.PI * 2.0;
		int len = 40 + (int) Math.floorMod(seed >>> 23, 41L);
		double ux = Math.cos(ang), uz = Math.sin(ang), vx = -uz, vz = ux;
		int n = (int) Math.ceil(len / (double) STEP);
		double ax = o.getX() + 0.5 - ux * len / 2.0, az = o.getZ() + 0.5 - uz * len / 2.0;
		int lanes = 2 * LANES + 1;
		double[][] px = new double[n + 1][lanes], pz = new double[n + 1][lanes];
		int[][] gy = new int[n + 1][lanes];
		boolean[][] wet = new boolean[n + 1][lanes];
		for (int i = 0; i <= n; i++) for (int j = 0; j < lanes; j++) {
			double s = Math.min(len, i * STEP), t = (j - LANES) * LANE_W;
			px[i][j] = ax + ux * s + vx * t;
			pz[i][j] = az + uz * s + vz * t;
			int x = (int) Math.floor(px[i][j]), z = (int) Math.floor(pz[i][j]);
			gy[i][j] = g.ground(x, z);
			wet[i][j] = g.surface(x, z) > gy[i][j];
		}
		double[][] cost = new double[n + 1][lanes];
		int[][] from = new int[n + 1][lanes];
		for (double[] r : cost) java.util.Arrays.fill(r, Double.MAX_VALUE);
		cost[0][LANES] = 0;
		for (int i = 1; i <= n; i++) for (int j = 0; j < lanes; j++) for (int dj = -1; dj <= 1; dj++) {
			int k = j + dj;
			if (k < 0 || k >= lanes || cost[i - 1][k] == Double.MAX_VALUE) continue;
			double dist = Math.hypot(STEP, dj * LANE_W);
			int dh = Math.abs(gy[i][j] - gy[i - 1][k]);
			double c = cost[i - 1][k] + dist + dh * dh * 6.0 + (wet[i][j] ? 40 : 0) + Math.abs(j - LANES) * 2.0;
			if (c < cost[i][j]) {
				cost[i][j] = c;
				from[i][j] = k;
			}
		}
		int end = LANES;
		for (int j = 0; j < lanes; j++) if (cost[n][j] < cost[n][end]) end = j;
		List<double[]> pts = new ArrayList<>();
		for (int i = n, j = end; i >= 0; j = from[i][j], i--) pts.add(0, new double[] {px[i][j], pz[i][j], gy[i][j]});
		for (int pass = 0; pass < 2; pass++) pts = chaikin(pts);
		// rasterise the centreline (4-connected), height in half blocks of the walking surface
		List<int[]> cells = new ArrayList<>();
		for (int s = 0; s + 1 < pts.size(); s++) {
			double[] a = pts.get(s), b = pts.get(s + 1);
			int steps = Math.max(1, (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) * 3));
			for (int t = 0; t <= steps; t++) {
				double f = t / (double) steps;
				int x = (int) Math.floor(a[0] + (b[0] - a[0]) * f), z = (int) Math.floor(a[1] + (b[1] - a[1]) * f);
				double y = a[2] + (b[2] - a[2]) * f;
				int sh = (int) Math.round(2 * (y + 1));
				if (!cells.isEmpty()) {
					int[] last = cells.get(cells.size() - 1);
					if (last[0] == x && last[1] == z) continue;
					if (last[0] != x && last[1] != z) cells.add(new int[] {last[0], z, (last[2] + sh) / 2});
				}
				cells.add(new int[] {x, z, sh});
			}
		}
		clamp(cells);
		if (rift) {
			// the Return Rift pad is flat and whole-block: every centreline cell within 1 of the middle walks at one even height
			int[] m = cells.get(cells.size() / 2);
			int pad = Integer.MIN_VALUE;
			for (int[] q : cells) if (Math.abs(q[0] - m[0]) <= 1 && Math.abs(q[1] - m[1]) <= 1) pad = Math.max(pad, q[2]);
			pad = (pad + 1) & ~1;
			for (int[] q : cells) if (Math.abs(q[0] - m[0]) <= 1 && Math.abs(q[1] - m[1]) <= 1) q[2] = pad;
			clamp(cells);
		}
		int[] data = new int[2 + 3 * cells.size()];
		data[0] = rift ? 1 : 0;
		data[1] = cells.size();
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, minS = Integer.MAX_VALUE, maxS = Integer.MIN_VALUE;
		for (int k = 0; k < cells.size(); k++) {
			int[] c = cells.get(k);
			int dx = c[0] - o.getX(), dz = c[1] - o.getZ();
			data[2 + 3 * k] = dx;
			data[3 + 3 * k] = dz;
			data[4 + 3 * k] = c[2];
			minX = Math.min(minX, dx);
			maxX = Math.max(maxX, dx);
			minZ = Math.min(minZ, dz);
			maxZ = Math.max(maxZ, dz);
			minS = Math.min(minS, c[2]);
			maxS = Math.max(maxS, c[2]);
		}
		int mid = cells.get(cells.size() / 2)[2] / 2;
		out.add(new FitPiece("road", o, 0, mid, new int[] {minX - 3, minZ - 3, maxX + 3, maxZ + 3}, false, seed, data, minS / 2 - 16, maxS / 2 + 6));
	}

	/** Raises valleys so neighbouring cells differ by at most 2 half-blocks (one block). Only ever raises. */
	static void clamp(List<int[]> cells) {
		for (int k = 1; k < cells.size(); k++) cells.get(k)[2] = Math.max(cells.get(k)[2], cells.get(k - 1)[2] - 2);
		for (int k = cells.size() - 2; k >= 0; k--) cells.get(k)[2] = Math.max(cells.get(k)[2], cells.get(k + 1)[2] - 2);
	}

	static List<double[]> chaikin(List<double[]> p) {
		List<double[]> q = new ArrayList<>();
		q.add(p.get(0));
		for (int i = 0; i + 1 < p.size(); i++) {
			double[] a = p.get(i), b = p.get(i + 1);
			q.add(new double[] {a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25, a[2] * 0.75 + b[2] * 0.25});
			q.add(new double[] {a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75, a[2] * 0.25 + b[2] * 0.75});
		}
		q.add(p.get(p.size() - 1));
		return q;
	}

	/** Centreline cells {x, z, walkHalfBlocks} in world coordinates. */
	public static int[][] cells(FitPiece p) {
		int n = p.data.length >= 2 ? p.data[1] : 0;
		int[][] c = new int[n][];
		for (int k = 0; k < n; k++) c[k] = new int[] {p.origin.getX() + p.data[2 + 3 * k], p.origin.getZ() + p.data[3 + 3 * k], p.data[4 + 3 * k]};
		return c;
	}

	/** y of the top deck block for a walking height in half blocks. */
	public static int deckY(int s) {
		return Math.floorDiv(s, 2) - (Math.floorMod(s, 2) == 0 ? 1 : 0);
	}

	static double[] dir(int[][] c, int k) {
		int a = Math.max(0, k - 2), b = Math.min(c.length - 1, k + 2);
		double dx = c[b][0] - c[a][0], dz = c[b][1] - c[a][1], l = Math.max(1e-6, Math.hypot(dx, dz));
		return new double[] {dx / l, dz / l};
	}

	/** Lantern post cells {centreline index, side (+1 left, -1 right)}: outside of bends, and every 10 cells on straights. */
	public static List<int[]> posts(int[][] c) {
		List<int[]> out = new ArrayList<>();
		int last = -100, side = 1;
		for (int k = 4; k < c.length - 4; k++) {
			double h1 = Math.atan2(c[k][1] - c[k - 4][1], c[k][0] - c[k - 4][0]), h2 = Math.atan2(c[k + 4][1] - c[k][1], c[k + 4][0] - c[k][0]);
			double d = h2 - h1;
			while (d > Math.PI) d -= 2 * Math.PI;
			while (d < -Math.PI) d += 2 * Math.PI;
			if (Math.abs(d) > 0.25 && k - last >= 6) {
				// heading angle growing turns from +x toward +z: a right turn seen from above, so the outside is the left
				out.add(new int[] {k, d > 0 ? 1 : -1});
				last = k;
			} else if (k - last >= 10) {
				out.add(new int[] {k, side});
				side = -side;
				last = k;
			}
		}
		return out;
	}

	public static void build(FitPiece p, W w) {
		int[][] c = cells(p);
		if (c.length < 2) return;
		BlockState air = Blocks.AIR.defaultBlockState();
		// deck cells: a 3x3 brush around each centreline cell; each deck cell takes the height of its NEAREST centreline cell (ties: the
		// earlier one), so every centreline cell carries its own height and steps run across the road. {@code all} is the whole road's
		// deck (not clipped to this chunk), for rails and posts at chunk borders.
		java.util.Set<Long> all = new java.util.HashSet<>();
		Map<Long, int[]> deck = new HashMap<>();
		for (int k = 0; k < c.length; k++) for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
			int x = c[k][0] + ox, z = c[k][1] + oz, d2 = ox * ox + oz * oz;
			all.add(BlockPos.asLong(x, 0, z));
			if (x < w.clip.minX() || x > w.clip.maxX() || z < w.clip.minZ() || z > w.clip.maxZ()) continue;
			long key = BlockPos.asLong(x, 0, z);
			int[] had = deck.get(key);
			if (had == null || d2 < had[5]) deck.put(key, new int[] {x, z, c[k][2], k, d2 == 0 ? 1 : 0, d2});
		}
		int mid = c.length / 2;
		boolean rift = p.data[0] == 1;
		for (int[] d : deck.values()) {
			int x = d[0], z = d[1], s = d[2], k = d[3];
			boolean centre = d[4] == 1;
			if (rift && Math.abs(x - c[mid][0]) <= 1 && Math.abs(z - c[mid][1]) <= 1) s = c[mid][2] & ~1;
			int dy = deckY(s);
			boolean slab = (s & 1) == 1;
			int support = slab ? dy - 1 : dy;
			// gap under the support decides causeway or trestle (reads only this chunk)
			int gap = 0;
			boolean water = false;
			for (int y = support - 1; y > support - 16; y--) {
				BlockState b = w.getW(x, y, z);
				if (!W.open(b)) break;
				if (!b.getFluidState().isEmpty()) water = true;
				gap++;
			}
			boolean trestle = gap > 3 || water;
			int h = w.hash(x - w.f.ox(), s, z - w.f.oz()) % 100;
			BlockState top;
			if (trestle) top = slab ? W.slab(Blocks.PALE_OAK_SLAB, false) : Blocks.PALE_OAK_PLANKS.defaultBlockState();
			else if (centre || h < 30) top = slab ? W.slab(h % 3 == 0 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB, false) : h % 4 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
			else top = slab ? W.slab(Blocks.MOSSY_COBBLESTONE_SLAB, false) : h < 60 ? Blocks.DIRT_PATH.defaultBlockState() : h < 80 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			w.setW(x, dy, z, top);
			if (slab) w.setW(x, support, z, trestle ? Blocks.PALE_OAK_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
			if (!trestle) {
				for (int y = support - 1, i = 0; i < gap; y--, i++) w.setW(x, y, z, W.RUBBLE[Math.min(i, 1)]);
			} else if (!centre && k % 3 == 0) {
				for (int y = support - 1, i = 0; i < 14; y--, i++) {
					if (!W.open(w.getW(x, y, z))) break;
					w.setW(x, y, z, Blocks.PALE_OAK_LOG.defaultBlockState());
				}
			}
			// headroom: plants and leaves up to 3, natural blocks up to 2 above the deck (ground rising between lattice points)
			for (int y = dy + 1; y <= dy + 3; y++) {
				BlockState b = w.getW(x, y, z);
				if (b.isAir() || !b.getFluidState().isEmpty()) continue;
				if (W.soft(b) || y <= dy + 2) w.setW(x, y, z, air);
			}
			if (trestle && !centre) {
				boolean edge = !all.contains(BlockPos.asLong(x + 1, 0, z)) || !all.contains(BlockPos.asLong(x - 1, 0, z))
					|| !all.contains(BlockPos.asLong(x, 0, z + 1)) || !all.contains(BlockPos.asLong(x, 0, z - 1));
				if (edge && inner(c, x, z) > 0.9) w.setW(x, dy + 1, z, Blocks.PALE_OAK_FENCE.defaultBlockState());
			}
		}
		// full-block steps become stairs on the lower centreline cell, facing the higher one
		for (int k = 0; k + 1 < c.length; k++) {
			int a = c[k][2], b = c[k + 1][2];
			if (Math.abs(a - b) < 2) continue;
			int[] lo = a < b ? c[k] : c[k + 1], hi = a < b ? c[k + 1] : c[k];
			Direction face = Direction.getApproximateNearest(hi[0] - lo[0], 0, hi[1] - lo[1]);
			if (rift && lo[0] == c[mid][0] && lo[1] == c[mid][1]) continue;
			BlockState st = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, face);
			w.setW(lo[0], Math.floorDiv(lo[2], 2), lo[1], st);
		}
		// hanging-lantern posts on the outside of bends and along straights
		for (int[] post : posts(c)) {
			int k = post[0];
			double[] t = dir(c, k);
			double ox = post[1] > 0 ? t[1] : -t[1], oz = post[1] > 0 ? -t[0] : t[0];
			int x = c[k][0] + (int) Math.round(ox * 2), z = c[k][1] + (int) Math.round(oz * 2);
			int ax = c[k][0] + (int) Math.round(ox), az = c[k][1] + (int) Math.round(oz);
			if (all.contains(BlockPos.asLong(x, 0, z)) || inner(c, x, z) < 1.5) {
				x = c[k][0] + (int) Math.round(ox * 3);
				z = c[k][1] + (int) Math.round(oz * 3);
				if (all.contains(BlockPos.asLong(x, 0, z)) || inner(c, x, z) < 1.5) continue;
			}
			int base = deckY(c[k][2]) + 1;
			// the post's foot goes down to its own ground in logs (no floating post beside a trestle)
			for (int y = base - 1, i = 0; i < 14; y--, i++) {
				if (!W.open(w.getW(x, y, z))) break;
				w.setW(x, y, z, Blocks.PALE_OAK_LOG.defaultBlockState());
			}
			w.setW(x, base, z, Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState());
			for (int y = base + 1; y <= base + 3; y++) w.setW(x, y, z, Blocks.PALE_OAK_FENCE.defaultBlockState());
			int armX = x + Integer.signum(ax - x), armZ = z + Integer.signum(az - z);
			if (armX == x && armZ == z) armX = ax;
			w.setW(armX, base + 3, armZ, Blocks.PALE_OAK_FENCE.defaultBlockState());
			w.setW(armX, base + 2, armZ, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// a few candles along the edge (lit candles stay under 12 per road)
		int candles = 0;
		for (int k = 3; k < c.length - 3 && candles < 10; k += 7) {
			if ((c[k][2] & 1) == 1) continue;
			double[] t = dir(c, k);
			int side = (k / 7) % 2 == 0 ? 1 : -1;
			int x = c[k][0] + (int) Math.round(side * t[1]), z = c[k][1] + (int) Math.round(-side * t[0]);
			int y = deckY(c[k][2]) + 1;
			if (!w.clip.isInside(x, y, z)) continue;
			BlockState below = w.getW(x, y - 1, z);
			if (!below.isFaceSturdy(w.l, new BlockPos(x, y - 1, z), Direction.UP) || !w.getW(x, y, z).isAir()) continue;
			w.setW(x, y, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 1 + k % 3));
			candles++;
		}
		if (rift) {
			int walk = (c[mid][2] & ~1) / 2;
			for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++)
				w.setW(c[mid][0] + ox, walk - 1, c[mid][1] + oz, ox == 0 && oz == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
			w.setW(c[mid][0], walk, c[mid][1], ModBlocks.RETURN_RIFT.defaultBlockState());
			w.setW(c[mid][0], walk + 1, c[mid][1], ModBlocks.RETURN_RIFT.defaultBlockState());
		}
	}

	/** Distance from (x, z) to the nearest centreline cell. */
	static double inner(int[][] c, int x, int z) {
		double best = Double.MAX_VALUE;
		for (int[] q : c) best = Math.min(best, Math.hypot(q[0] - x, q[1] - z));
		return best;
	}

	/** The road whose start is in or next to the chunk holding (x, z), or null. */
	public static @Nullable FitPiece find(ServerLevel level, int x, int z) {
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			ChunkAccess ch = level.getChunk((x >> 4) + dx, (z >> 4) + dz);
			for (StructureStart st : ch.getAllStarts().values()) for (StructurePiece sp : st.getPieces()) {
				if (sp instanceof FitPiece fp && fp.kind.equals("road")) return fp;
			}
		}
		return null;
	}

	/** Walking points along the road (cell centres, 1.2 above the deck) for the Lantern Procession. */
	public static List<Vec3> walk(FitPiece p) {
		List<Vec3> out = new ArrayList<>();
		for (int[] q : cells(p)) out.add(new Vec3(q[0] + 0.5, q[2] / 2.0 + 1.2, q[1] + 0.5));
		return out;
	}

	/** Where the procession leaves its chest: two blocks past the road's end, at the end's walking height. */
	public static BlockPos chest(FitPiece p) {
		int[][] c = cells(p);
		int[] e = c[c.length - 1];
		double[] t = dir(c, c.length - 1);
		return new BlockPos(e[0] + (int) Math.round(t[0] * 2), (e[2] + 1) / 2, e[1] + (int) Math.round(t[1] * 2));
	}
}
