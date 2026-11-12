package dev.elsewhere.kingdom_court.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * s-layout-hraf (3.0.0): the land side of Hrafnholt's waterfront on new (fv 2) realms, built with the {@link Waterfront} piece. A shore road
 * from the castle (the south road's junction, or the north water gate) down to the jetty at the shoreline: 3 wide, every step at most 1,
 * folded into switchbacks when the slope is steeper than the run. Two nausts (stone-walled, turf-roofed boat sheds dug into the shore) with
 * log slips running into the water and a beached boat in each, at the moored longships' berths, only where dry shore stands right behind
 * the waterline. Deterministic (generator ground only); every write is clipped to the chunk. Kill switch: {@code hrafnholt_fort}.
 */
public final class HrafnholtShore {
	private HrafnholtShore() {}

	/** Planned road (cells {u, v} in the waterfront frame, standing heights relative to the floor) and the naust berths {vc, ub}. */
	public record Plan(int[][] cells, int[] h, int end, List<int[]> berths, String note) {}

	private static final Map<String, Plan> PLANS = new ConcurrentHashMap<>();
	static final int[] BERTHS = {-7, -14};
	/** s-asm-court: a naust is dug into the bank; its footprint's ground may stand up to this far above the sea (earth stays over the turf roof). */
	static final int NAUST_BANK = 16;

	/** Builds the shore road and nausts into the placer's chunk; the caller sets {@code pl.style} to the faction's style. */
	public static void build(CastleLayout.Placer pl, BlockPos o, int dir, int shore, int sea, CastleLayout.Ground g) {
		if (!HrafnholtFort.ENABLED || CastleKit.kitOf(pl) != CastleKit.Kit.HRAFNHOLT) return;
		CastleLayout.Ground prev = pl.ground;
		pl.ground = g;
		try {
			Plan p = plan(pl, o, dir, shore, sea);
			// s-asm-court: every road column is written once, at the height of its nearest road cell (ties: the higher). Stamping the 3-wide
			// cells in order let a hairpin's later, lower cells overwrite earlier centres 2+ below their planned height (a 2-high step)
			Map<Long, int[]> cols = new java.util.HashMap<>();
			for (int i = 0; i < p.cells.length; i++) for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
				long k = ((long) (p.cells[i][0] + a) << 32) ^ ((p.cells[i][1] + b) & 0xffffffffL);
				int d = Math.max(Math.abs(a), Math.abs(b));
				int[] c = cols.get(k);
				if (c == null || d < c[0] || d == c[0] && p.h[i] > c[1]) cols.put(k, new int[] {d, p.h[i]});
			}
			for (Map.Entry<Long, int[]> e : cols.entrySet()) {
				long k = e.getKey();
				path(pl, o, dir, (int) (k >> 32), (int) k, o.getY() + e.getValue()[1]);
			}
			for (int[] be : p.berths) naust(pl, o, dir, be[0], be[1], sea);
		} finally {
			pl.ground = prev;
		}
	}

	static int gw(CastleLayout.Ground g, BlockPos o, int dir, int u, int v) {
		int[] w = Waterfront.world(o, dir, u, v);
		return g.y(w[0], w[1]);
	}

	/** The plan (cached per realm); {@code pl.ground} must be the realm's generator ground. */
	public static Plan plan(CastleLayout.Placer pl, BlockPos o, int dir, int shore, int sea) {
		CastleLayout.Ground g = pl.ground;
		String key = o.asLong() + ":" + dir + ":" + shore + ":" + sea + ":" + gw(g, o, dir, shore, 0) + ":" + gw(g, o, dir, 40, 0);
		Plan c = PLANS.get(key);
		if (c != null) return c;
		// start: the water gate's lane (north) or the south road where it leaves the gate line
		int u0 = 37, h0 = 0;
		if (dir == 1) {
			CastleLayout.L l = new CastleLayout.L(o, pl);
			int bestZ = Integer.MIN_VALUE;
			for (int s = 1; s >= -1; s -= 2) {
				int tx = s * CastleLayout.TOWN_DX;
				int tf = g.floor(s < 0 ? CastleLayout.Part.TOWN_WEST : CastleLayout.Part.TOWN_EAST, g.y(o.getX() + tx, o.getZ() + CastleLayout.TOWN_DZ)) - o.getY();
				int[][] path = CastleLayout.route(l, tx);
				int n = path.length;
				int[] raw = new int[n];
				for (int i = 0; i < n; i++) raw[i] = l.gy(path[i][0], path[i][1]);
				int[] hh = CastleLayout.contour(raw, new int[] {0, n - 1}, new int[] {0, tf});
				int j = 0;
				while (j + 1 < n && Math.abs(path[j + 1][0]) <= 1) j++;
				if (path[j][1] > bestZ) {
					bestZ = path[j][1];
					h0 = hh[j];
				}
			}
			u0 = bestZ + 1;
		}
		int hEnd = sea + 1 - o.getY(), us = -1;
		for (int u = u0 + 1; u <= shore + 2 && us < 0; u++) if (gw(g, o, dir, u, 0) <= sea + 1) us = u;
		if (us < 0) us = shore;
		List<int[]> cells = new ArrayList<>();
		int[] h = new int[0];
		if (us - u0 >= 2) {
			int run = us - u0, drop = Math.abs(h0 - hEnd), extra = drop - run + 2, rows = 0, lead = dir == 1 ? 4 : 0;
			if (extra > 0) {
				rows = (extra + 7) / 8;
				if ((rows & 1) == 1) rows++;
				while (rows > 0 && u0 + lead + 4 * (rows - 1) > us - 8) rows -= 2;
			}
			int u = u0;
			// south: a short straight lead first, so the switchbacks stay clear of the town roads leaving the junction
			if (rows > 0) for (; u < u0 + lead; u++) cells.add(new int[] {u, 0});
			for (int r = 0; r < rows; r++) {
				boolean outw = (r & 1) == 0;
				for (int k = 0; k <= 8; k++) cells.add(new int[] {u, outw ? k : 8 - k});
				if (r < rows - 1) for (int k = 1; k <= 3; k++) cells.add(new int[] {u + k, outw ? 8 : 0});
				if (r < rows - 1) u += 4;
			}
			if (rows > 0) u++;
			for (; u <= us; u++) cells.add(new int[] {u, 0});
			int n = cells.size();
			int[] raw = new int[n];
			for (int i = 0; i < n; i++) raw[i] = gw(g, o, dir, cells.get(i)[0], cells.get(i)[1]) - o.getY();
			h = CastleLayout.contour(raw, new int[] {0, n - 1}, new int[] {h0, hEnd});
		}
		// naust berths: dry shore behind the waterline at the moored longships' lanes
		List<int[]> berths = new ArrayList<>();
		StringBuilder note = new StringBuilder(); // s-asm-court: per lane, why a naust stands or not (self-test detail)
		for (int vc : BERTHS) {
			// s-asm-court: the lane's own waterline, the first wet cell right after dry land, anywhere from clear of the ring (a 12-long shed
			// behind it starts at u >= 38) to just past the shore. It used to need dry land exactly 16 before the shore, which a flat or marshy
			// coast never has.
			int ub = -1;
			for (int u = 51; u <= shore + 2 && ub < 0; u++) if (gw(g, o, dir, u, vc) <= sea + 1 && gw(g, o, dir, u - 1, vc) > sea + 1) ub = u;
			if (note.length() > 0) note.append("; ");
			note.append("lane ").append(vc).append(": ");
			if (ub < 0 || ub - 13 < 38) {
				note.append("no waterline after dry land in u 51..").append(shore + 2);
				continue;
			}
			boolean ok = true;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int u = ub - 13; u <= ub - 2; u++) for (int v = vc - 3; v <= vc + 3; v++) {
				int y = gw(g, o, dir, u, v);
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
				if (y <= sea || y > sea + NAUST_BANK) ok = false;
			}
			note.append("waterline u ").append(ub).append(", bank ").append(lo - sea).append("..").append(hi - sea).append(ok ? " -> naust" : " -> none");
			if (ok) berths.add(new int[] {vc, ub});
		}
		Plan p = new Plan(cells.toArray(new int[0][]), h, hEnd, berths, note.toString());
		if (PLANS.size() > 64) PLANS.clear();
		PLANS.put(key, p);
		return p;
	}

	// ---- writes (waterfront frame u/v, world y) ----------------------------------------------------------------------

	static BlockPos at(BlockPos o, int dir, int u, int y, int v) {
		int[] w = Waterfront.world(o, dir, u, v);
		return new BlockPos(w[0], y, w[1]);
	}

	static void set(CastleLayout.Placer pl, BlockPos p, BlockState s) {
		pl.setRaw(p.getX(), p.getY(), p.getZ(), s);
	}

	static boolean free(CastleLayout.Placer pl, BlockPos p) {
		if (!pl.clip.isInside(p)) return false;
		BlockState c = pl.level.getBlockState(p);
		return c.isAir() || !c.getFluidState().isEmpty() || c.canBeReplaced();
	}

	static void carve(CastleLayout.Placer pl, BlockPos o, int dir, int u, int v, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			BlockPos p = at(o, dir, u, y, v);
			if (!pl.clip.isInside(p)) continue;
			BlockState c = pl.level.getBlockState(p);
			if (!c.isAir() && c.getFluidState().isEmpty()) set(pl, p, Blocks.AIR.defaultBlockState()); // never opens the sea
		}
	}

	static void down(CastleLayout.Placer pl, BlockPos o, int dir, int u, int v, int y, BlockState s) {
		for (int k = 0; k < 48; k++) {
			BlockPos p = at(o, dir, u, y - k, v);
			if (!free(pl, p)) return;
			set(pl, p, s);
		}
	}

	/** Road cell: dirt path under standing height s (world y), air above, earth down to solid ground. */
	static void path(CastleLayout.Placer pl, BlockPos o, int dir, int u, int v, int s) {
		int[] w = Waterfront.world(o, dir, u, v);
		if (w[0] < pl.clip.minX() || w[0] > pl.clip.maxX() || w[1] < pl.clip.minZ() || w[1] > pl.clip.maxZ()) return;
		carve(pl, o, dir, u, v, s, Math.max(s + 3, pl.ground.y(w[0], w[1]) + 1));
		set(pl, at(o, dir, u, s - 1, v), Blocks.DIRT_PATH.defaultBlockState());
		down(pl, o, dir, u, v, s - 2, Blocks.DIRT.defaultBlockState());
	}

	/** Naust at berth lane vc, waterline ub: stone side walls, turf gable roof along u, open to the water, a beached boat on a log slip. */
	static void naust(CastleLayout.Placer pl, BlockPos o, int dir, int vc, int ub, int sea) {
		Direction plusV = dir == 1 ? Direction.WEST : Direction.EAST;
		BlockState mc = Blocks.MOSSY_COBBLESTONE.defaultBlockState(), floor = Blocks.COARSE_DIRT.defaultBlockState(), planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
		BlockState rail = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
		int back = ub - 12, front = ub - 1;
		for (int u = back - 1; u <= front + 1; u++) for (int v = vc - 3; v <= vc + 3; v++) {
			int dv = Math.abs(v - vc);
			int[] w = Waterfront.world(o, dir, u, v);
			if (w[0] < pl.clip.minX() || w[0] > pl.clip.maxX() || w[1] < pl.clip.minZ() || w[1] > pl.clip.maxZ()) continue;
			int roofY = sea + 3 + (3 - dv);
			boolean inside = u >= back && u <= front;
			// s-asm-court: dug into the bank: the shed is cleared under its roof and the roof row itself; earth higher up stays over the turf
			carve(pl, o, dir, u, v, inside && dv <= 2 ? sea + 1 : roofY, roofY);
			if (inside && dv <= 2) {
				set(pl, at(o, dir, u, sea, v), floor);
				down(pl, o, dir, u, v, sea - 1, mc);
				boolean wall = dv == 2 || u == back;
				if (wall) for (int y = sea + 1; y <= sea + 3; y++) set(pl, at(o, dir, u, y, v), mc);
				if (u == back && dv <= 1) for (int y = sea + 4; y < roofY; y++) set(pl, at(o, dir, u, y, v), planks);
				if (u == front && dv <= 1) for (int y = sea + 5; y < roofY; y++) set(pl, at(o, dir, u, y, v), planks);
			}
			BlockState r;
			if (dv == 0) r = moss;
			else if (dv == 3) r = HrafnholtFort.st(Blocks.SPRUCE_STAIRS, v > vc ? plusV.getOpposite() : plusV);
			else r = moss;
			set(pl, at(o, dir, u, roofY, v), r);
		}
		// the slip: two log rails under the boat, stepping down into the water
		for (int s = -1; s <= 1; s += 2) {
			for (int u = back + 1; u <= front; u++) set(pl, at(o, dir, u, sea, vc + s), rail);
			for (int k = 0; k <= 2; k++) {
				BlockPos p = at(o, dir, ub + k, sea - k, vc + s);
				if (!pl.clip.isInside(p)) continue;
				set(pl, p, rail);
				down(pl, o, dir, ub + k, vc + s, sea - k - 1, Blocks.SPRUCE_LOG.defaultBlockState());
				carve(pl, o, dir, ub + k, vc + s, sea - k + 1, sea + 2);
			}
		}
		// the beached boat: keel, plank sides, slab trim, stems
		BlockState keel = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
		BlockState trim = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.BOTTOM);
		for (int i = 0; i < 11; i++) {
			int u = back + 1 + i;
			set(pl, at(o, dir, u, sea + 1, vc), keel);
			if (i == 0 || i == 10) {
				for (int y = sea + 2; y <= sea + 3; y++) set(pl, at(o, dir, u, y, vc), Blocks.DARK_OAK_LOG.defaultBlockState());
				continue;
			}
			for (int s = -1; s <= 1; s += 2) {
				set(pl, at(o, dir, u, sea + 1, vc + s), planks);
				if (i >= 2 && i <= 8) set(pl, at(o, dir, u, sea + 2, vc + s), i % 3 == 1 ? Blocks.SPRUCE_STAIRS.defaultBlockState()
					.setValue(net.minecraft.world.level.block.StairBlock.FACING, s > 0 ? plusV.getOpposite() : plusV).setValue(net.minecraft.world.level.block.StairBlock.HALF, Half.TOP) : trim);
			}
		}
		set(pl, at(o, dir, back + 1, sea + 4, vc), Blocks.DARK_OAK_FENCE.defaultBlockState());
		set(pl, at(o, dir, front + 1, sea + 7, vc), Blocks.DARK_OAK_FENCE.defaultBlockState());
		set(pl, at(o, dir, front + 1, sea + 8, vc), Blocks.BONE_BLOCK.defaultBlockState());
		set(pl, at(o, dir, back + 2, sea + 5, vc), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
	}
}
