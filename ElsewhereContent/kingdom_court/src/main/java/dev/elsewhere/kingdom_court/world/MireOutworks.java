package dev.elsewhere.kingdom_court.world;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * s-layout-mire: the ROAD part of the Mirewick layout. The shared fv 2 road ({@link CastleLayout#roadFit}: A* over the terrain, step-limited
 * contour meeting both town gates) becomes a polder dike road: grass shoulders where the road runs above the land, and a drainage ditch
 * beside it (water one block deep in the natural soil, only where the ground there is flat or rising on all four sides and those blocks
 * are solid in this chunk, never on natural water, never next to the castle or towns). Before the gate, south of the road junction, a
 * ravelin: a triangular quartz-faced earthwork on its own ground with a purple band, gilt-capped parapet, a stair ramp from the road, a lantern
 * and a purple banner; skipped over water or steep ground.
 */
final class MireOutworks {
	private MireOutworks() {}

	static final int[][] RAVELIN = {{0, 55}, {13, 42}, {-13, 42}};

	static void build(CastleLayout.L l) {
		CastleLayout.roadFit(l);
		CastleKit.W w = new CastleKit.W(l, false);
		Map<Long, int[]> info = info(l);
		var c = l.p.clip;
		int x0 = Math.max(-CastleLayout.RX - 3, c.minX() - l.o.getX()), x1 = Math.min(CastleLayout.RX + 3, c.maxX() - l.o.getX());
		int z0 = Math.max(CastleLayout.RZ0 - 1, c.minZ() - l.o.getZ()), z1 = Math.min(CastleLayout.RZ1 + 3, c.maxZ() - l.o.getZ());
		BlockState dirt = Blocks.DIRT.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), water = Blocks.WATER.defaultBlockState(),
			air = Blocks.AIR.defaultBlockState();
		// dike shoulders first (they only add ground), then the ditches
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			int[] r = info.get(key(x, z));
			if (r == null || r[0] != 2 || excluded(x, z)) continue;
			int gy = l.gy(x, z), top = r[1] - 1; // flush with the path block (h - 1), so roadFit's lantern posts at h stand on the dike
			if (gy > top || CastleLayout.wet(l.p.ground, l.o.getX() + x, l.o.getZ() + z)) continue;
			for (int y = gy; y <= top; y++) w.s(x, y, z, y == top ? grass : dirt);
			l.foundation(x, z, x, z, gy - 1, Blocks.DIRT);
		}
		// two passes: every cell is judged on the blocks as they were before any ditch water (a neighbour that passes too becomes water at the
		// same level, a rejected one stays the solid block it was), so a ditch is a continuous run, never a dotted line
		java.util.List<int[]> ditch = new java.util.ArrayList<>();
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (!ditchCandidate(l, info, x, z)) continue;
			int y = l.gy(x, z) - 1;
			boolean ok = w.solid(x, y, z) && w.solid(x, y - 1, z) && w.open(x, y + 1, y + 1, z);
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) ok &= w.solid(x + d[0], y, z + d[1]);
			if (ok) ditch.add(new int[] {x, y, z});
		}
		for (int[] c2 : ditch) {
			w.s(c2[0], c2[1], c2[2], water);
			w.s(c2[0], c2[1] + 1, c2[2], air);
		}
		ravelin(l, w, info);
	}

	static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	static boolean excluded(int x, int z) {
		if (Math.abs(x) <= 40 && z <= 40) return true;
		for (int s = -1; s <= 1; s += 2) if (Math.abs(x - s * CastleLayout.TOWN_DX) <= 26 && z <= 34) return true;
		return inRavelin(x, z, 3);
	}

	/** Road cells near the route: {Chebyshev distance to the path (0..3), road surface y (local)}. Cached per realm and ground. */
	static final Map<String, Map<Long, int[]>> INFO = new ConcurrentHashMap<>();

	static Map<Long, int[]> info(CastleLayout.L l) {
		CastleLayout.Ground g = l.p.ground;
		BlockPos o = l.o;
		String k = o.asLong() + ":" + g.y(o.getX(), o.getZ() + 60) + ":" + g.y(o.getX() + 60, o.getZ() + 40) + ":" + g.y(o.getX() - 60, o.getZ() + 70);
		Map<Long, int[]> m = INFO.get(k);
		if (m != null) return m;
		m = new HashMap<>();
		for (int s = -1; s <= 1; s += 2) {
			int tx = s * CastleLayout.TOWN_DX;
			int tf = g.floor(s < 0 ? CastleLayout.Part.TOWN_WEST : CastleLayout.Part.TOWN_EAST, g.y(o.getX() + tx, o.getZ() + CastleLayout.TOWN_DZ)) - o.getY();
			int[][] path = CastleLayout.route(l, tx);
			int n = path.length;
			int[] raw = new int[n];
			for (int i = 0; i < n; i++) raw[i] = l.gy(path[i][0], path[i][1]);
			int[] h = CastleLayout.contour(raw, new int[] {0, n - 1}, new int[] {0, tf});
			for (int i = 0; i < n; i++) for (int a = -3; a <= 3; a++) for (int b = -3; b <= 3; b++) {
				int d = Math.max(Math.abs(a), Math.abs(b));
				long kk = key(path[i][0] + a, path[i][1] + b);
				int[] cur = m.get(kk);
				if (cur == null || d < cur[0] || d == cur[0] && h[i] > cur[1]) m.put(kk, new int[] {d, h[i]});
			}
		}
		if (INFO.size() > 16) INFO.clear();
		INFO.put(k, m);
		return m;
	}

	/** Ground-only rule for a ditch cell (the build also checks the real blocks in its chunk). */
	static boolean ditchCandidate(CastleLayout.L l, Map<Long, int[]> info, int x, int z) {
		int[] r = info.get(key(x, z));
		if (r == null || r[0] != 3 || excluded(x, z)) return false;
		if (x < -CastleLayout.RX - 2 || x > CastleLayout.RX + 2 || z < CastleLayout.RZ0 || z > CastleLayout.RZ1 + 2) return false;
		CastleLayout.Ground g = l.p.ground;
		if (CastleLayout.wet(g, l.o.getX() + x, l.o.getZ() + z)) return false;
		int gy = l.gy(x, z);
		if (gy > r[1] - 1) return false;
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			int[] rn = info.get(key(x + d[0], z + d[1]));
			if (rn != null && rn[0] <= 1) return false;
			if (l.gy(x + d[0], z + d[1]) < gy || CastleLayout.wet(g, l.o.getX() + x + d[0], l.o.getZ() + z + d[1])) return false;
		}
		return true;
	}

	/** Self-test: could the layout have put road-ditch water at local (x, y, z)? */
	static boolean ditchWater(BlockPos o, CastleLayout.Ground g, int x, int y, int z) {
		CastleLayout.Placer pl = new CastleLayout.Placer(null, new BoundingBox(0, 0, 0, 0, 0, 0));
		pl.ground = g;
		CastleLayout.L l = new CastleLayout.L(o, pl);
		return ditchCandidate(l, info(l), x, z) && y == l.gy(x, z) - 1;
	}

	static boolean inRavelin(int x, int z, int margin) {
		double d = triDist(x, z);
		return d > -margin;
	}

	/** Signed distance to the ravelin triangle (> 0 inside). */
	static double triDist(double x, double z) {
		double best = Double.MAX_VALUE;
		double[][] t = new double[3][];
		for (int i = 0; i < 3; i++) t[i] = new double[] {RAVELIN[i][0], RAVELIN[i][1]};
		for (int i = 0; i < 3; i++) best = Math.min(best, MireLayout.segDist(x, z, t[i], t[(i + 1) % 3]));
		return MireLayout.inside(t, x, z) ? best : -best;
	}

	/** The ravelin's base level (local), or Integer.MIN_VALUE when it is skipped (water, or ground spread over 5). */
	public static int ravelinBase(CastleLayout.L l) {
		int[] v = new int[9];
		int i = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
			int x = -8 + 8 * a, z = 44 + 4 * b;
			if (CastleLayout.wet(l.p.ground, l.o.getX() + x, l.o.getZ() + z)) return Integer.MIN_VALUE;
			v[i++] = l.gy(x, z);
			lo = Math.min(lo, v[i - 1]);
			hi = Math.max(hi, v[i - 1]);
		}
		if (hi - lo > 5) return Integer.MIN_VALUE;
		java.util.Arrays.sort(v);
		return v[4];
	}

	static void ravelin(CastleLayout.L l, CastleKit.W w, Map<Long, int[]> info) {
		int rb = ravelinBase(l);
		if (rb == Integer.MIN_VALUE) return;
		BlockState calcite = Blocks.CALCITE.defaultBlockState(), quartz = Blocks.QUARTZ_BRICKS.defaultBlockState(),
			purple = Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE).defaultBlockState(), gold = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState(),
			mud = Blocks.PACKED_MUD.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState(),
			slab = CastleKit.slab(Blocks.QUARTZ_SLAB, SlabType.BOTTOM);
		int top = rb + 4;
		w.dy = 0;
		for (int x = -14; x <= 14; x++) for (int z = 41; z <= 56; z++) {
			double d = triDist(x, z);
			if (d <= 0) continue;
			int[] r = info.get(key(x, z));
			if (r != null && r[0] <= 2) continue; // the road and its shoulders stay clear
			boolean ramp = Math.abs(x) <= 1 && z <= 46;
			int gy = l.gy(x, z), foot = Math.min(gy, rb), t = ramp ? rb + (z - 42) : top;
			boolean face = d <= 1 && z > 42;
			for (int y = foot; y <= t; y++) {
				BlockState b = face ? (y <= foot + 1 ? calcite : y == rb + 2 ? purple : quartz) : y == t ? grass : mud;
				w.s(x, y, z, b);
			}
			if (ramp) w.s(x, t, z, CastleKit.stair(Blocks.QUARTZ_STAIRS, net.minecraft.core.Direction.SOUTH, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
			for (int y = t + 1; y <= Math.max(t, gy) + 2; y++) w.s(x, y, z, air);
			if (face && !ramp) w.s(x, t + 1, z, ((x + z) & 1) == 0 ? (CastleLayout.hash(x, 5, z) < 20 ? gold : quartz) : slab);
			l.foundation(x, z, x, z, foot - 1, Blocks.CALCITE);
		}
		if (w.solid(0, top, 52)) {
			w.s(0, top + 1, 52, Blocks.DIORITE_WALL.defaultBlockState());
			w.s(0, top + 2, 52, Blocks.LANTERN.defaultBlockState());
		}
		if (w.solid(0, top, 49)) w.s(0, top + 1, 49, Blocks.BANNER.pick(DyeColor.PURPLE).defaultBlockState());
	}
}
