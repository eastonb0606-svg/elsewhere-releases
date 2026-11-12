package dev.elsewhere.greenhaze_hush.world.fit;

import static dev.elsewhere.greenhaze_hush.world.fit.GhFit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

/**
 * Site plans for the new looks (STRUCTURES.md 2.4-2.6). Each kind becomes several small pieces, each on its own ground, with a per-piece beard
 * where a floor should blend (beard_thin, footprint only, 4 high) or a buried room should be sealed (encapsulate).
 * Worldgen plans read generator columns only (memoised; 30-130 samples a start) and run inside the start's stub, so /locate never pays for them.
 */
public final class GhPlans {
	private GhPlans() {}

	/** Kinds with a new look (the dev-only return rift included). */
	public static boolean has(String kind) {
		return switch (kind) {
			case "great_hotbox", "bonfire_terrace", "fern_terrace_farms", "munchie_market", "sesh_hut", "lucid_spring", "greenhaze_return_rift",
				"listening_engine", "choir_geode", "silent_monastery", "hanging_undercity" -> true;
			default -> false;
		};
	}

	/** Memoised ground (each sample costs a noise column). */
	static final class G {
		final Ground g;
		final Map<Long, Integer> ys = new HashMap<>();

		G(Ground g) {
			this.g = g;
		}

		int y(int x, int z) {
			return ys.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> g.y(x, z));
		}
	}

	/**
	 * Plans one start. {@code o} is the walk level at the site centre (surface: site floor + 1; fixed: the JSON y; cave: the found floor).
	 * The same plan serves worldgen (generator ground) and dev builds (live ground).
	 */
	public static List<Piece> plan(String kind, BlockPos o, long seed, Ground ground, Columns cols) {
		G g = new G(ground);
		return switch (kind) {
			case "great_hotbox" -> hotbox(o, seed, g);
			case "bonfire_terrace" -> bonfire(o, seed, g);
			case "fern_terrace_farms" -> farms(o, seed, g);
			case "munchie_market" -> market(o, seed, g);
			case "sesh_hut" -> hut(o, seed, g);
			case "lucid_spring" -> spring(o, seed, g);
			case "greenhaze_return_rift" -> List.of(new Piece("rift", o, seed, 0, new int[0],
				box(o.getX() - 6, o.getY() - 16, o.getZ() - 6, o.getX() + 6, o.getY() + 8, o.getZ() + 6), null, null));
			case "listening_engine" -> engine(o, seed, cols);
			case "choir_geode" -> geode(o, seed, cols);
			case "silent_monastery" -> monastery(o, seed, cols);
			case "hanging_undercity" -> undercity(o, seed, cols);
			default -> List.of();
		};
	}

	// ------------------------------------------------------------------------------------------------ helpers

	static final Direction[] ROT = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

	static int rotOf(Direction d) {
		for (int i = 0; i < 4; i++) if (ROT[i] == d) return i;
		return 0;
	}

	/** The rot whose front faces the lowest of 4 cardinal ground samples at distance d (ties in seed order). */
	static int downhill(G g, BlockPos o, int d, long seed) {
		int start = (int) Math.floorMod(seed, 4L), best = start, bestY = Integer.MAX_VALUE;
		for (int k = 0; k < 4; k++) {
			int i = (start + k) & 3;
			int y = g.y(o.getX() + ROT[i].getStepX() * d, o.getZ() + ROT[i].getStepZ() * d);
			if (y < bestY) { bestY = y; best = i; }
		}
		return best;
	}

	/** World box of a local rectangle of a frame. */
	static BoundingBox fbox(Frame f, int lx0, int lz0, int lx1, int lz1, int y0, int y1) {
		return box(f.x(lx0, lz0), y0, f.z(lx0, lz0), f.x(lx1, lz1), y1, f.z(lx1, lz1));
	}

	static int median(int... v) {
		int[] s = v.clone();
		Arrays.sort(s);
		return s.length % 2 == 1 ? s[s.length / 2] : Math.floorDiv(s[s.length / 2 - 1] + s[s.length / 2], 2);
	}

	// ------------------------------------------------------------------------------------------------ Greenhaze

	/** Main dome facing downhill, a store annex and a lounge annex in front, each on its own floor, joined by covered stair walks. */
	static List<Piece> hotbox(BlockPos o, long seed, G g) {
		int x = o.getX(), y0 = o.getY(), z = o.getZ(), R = 15;
		int rot = downhill(g, o, 22, seed);
		Frame f = new Frame(x, y0, z, rot);
		List<Piece> out = new ArrayList<>(), walks = new ArrayList<>();
		out.add(new Piece("hotbox", o, seed, rot, new int[0], box(x - 19, y0 - 12, z - 19, x + 19, y0 + 18, z + 19),
			box(x - R, y0, z - R, x + R, y0 + 3, z + R), TerrainAdjustment.BEARD_THIN));
		int[][] annexes = {{31, 6, 1, 0}, {50, 5, 0, 1}}; // centre lz, radius, front door, role
		int prev = y0, start = R + 4;
		for (int k = 0; k < annexes.length; k++) {
			int lz = annexes[k][0], ra = annexes[k][1], end = lz - ra - 1, n = end - start + 1;
			int gm = median(g.y(f.x(0, lz), f.z(0, lz)), g.y(f.x(ra, lz), f.z(ra, lz)), g.y(f.x(-ra, lz), f.z(-ra, lz)),
				g.y(f.x(0, lz + ra), f.z(0, lz + ra)), g.y(f.x(0, lz - ra), f.z(0, lz - ra)));
			int ya = Mth.clamp(gm + 1, Math.max(y0 - 6, prev - (n - 1)), Math.min(y0 + 2, prev + (n - 1)));
			BlockPos c = new BlockPos(f.x(0, lz), ya, f.z(0, lz));
			out.add(new Piece("hotbox_annex", c, seed ^ (0xA11L * (k + 1)), rot, new int[] {ra, annexes[k][3], annexes[k][2]},
				box(c.getX() - ra - 3, ya - 12, c.getZ() - ra - 3, c.getX() + ra + 3, ya + ra + 3, c.getZ() + ra + 3),
				box(c.getX() - ra, ya, c.getZ() - ra, c.getX() + ra, ya + 3, c.getZ() + ra), TerrainAdjustment.BEARD_THIN));
			int[] p = new int[1 + n];
			p[0] = n;
			for (int i = 0; i < n; i++) p[1 + i] = prev + (int) Math.round((ya - prev) * (double) i / Math.max(1, n - 1));
			walks.add(new Piece("hotbox_walk", new BlockPos(f.x(0, start), prev, f.z(0, start)), seed ^ (0x3A1L * (k + 1)), rot, p,
				fbox(f, -3, start, 3, end, Math.min(prev, ya) - 14, Math.max(prev, ya) + 5), null, null));
			prev = ya;
			start = lz + ra + 1;
		}
		// walks after the domes: each walk's last cell meets its dome's door over that dome's skirt
		out.addAll(walks);
		return out;
	}

	/** Cavea: 16 sectors x 4 seat rings; each ring follows the ground where it rises, else is built up one step per ring. */
	static List<Piece> bonfire(BlockPos o, long seed, G g) {
		int x = o.getX(), y0 = o.getY(), z = o.getZ();
		int[][] L = new int[16][5];
		for (int s = 0; s < 16; s++) {
			double th = -Math.PI + (s + 0.5) * Math.PI / 8;
			for (int k = 1; k <= 4; k++) {
				double rr = 9.25 + 2.5 * (k - 1);
				int gy = g.y(x + (int) Math.round(Math.cos(th) * rr), z + (int) Math.round(Math.sin(th) * rr)) + 1 - y0;
				L[s][k] = Mth.clamp(Math.max(gy, k - 1), 0, k);
			}
		}
		// lowering-only relaxation: rings never drop outward, rise at most 1 per ring, differ by at most 1 between sectors
		boolean changed = true;
		for (int it = 0; changed && it < 64; it++) {
			changed = false;
			for (int s = 0; s < 16; s++) for (int k = 1; k <= 4; k++) {
				int v = Math.min(L[s][k], L[s][k - 1] + 1);
				v = Math.min(v, Math.min(L[(s + 1) & 15][k], L[(s + 15) & 15][k]) + 1);
				if (k < 4) v = Math.min(v, L[s][k + 1]);
				if (v != L[s][k]) { L[s][k] = v; changed = true; }
			}
		}
		int[] p = new int[64];
		for (int s = 0; s < 16; s++) for (int k = 1; k <= 4; k++) p[s * 4 + k - 1] = L[s][k];
		return List.of(new Piece("bonfire", o, seed, 0, p, box(x - 21, y0 - 14, z - 21, x + 21, y0 + 14, z + 21),
			box(x - 8, y0, z - 8, x + 8, y0 + 3, z + 8), TerrainAdjustment.BEARD_THIN));
	}

	/** Rice terraces from a 9x9 ground lattice (step 4); the stair path climbs uphill, a gentle tilt gives flat sites their steps. */
	static List<Piece> farms(BlockPos o, long seed, G g) {
		int x = o.getX(), y0 = o.getY(), z = o.getZ(), n = 9, step = 4, x0 = x - 16, z0 = z - 16;
		int[] p = new int[4 + n * n + 3];
		p[0] = x0;
		p[1] = z0;
		p[2] = step;
		p[3] = n;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		long gx = 0, gz = 0;
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
			int v = g.y(x0 + i * step, z0 + j * step);
			p[4 + i * n + j] = v;
			lo = Math.min(lo, v);
			hi = Math.max(hi, v);
			gx += (long) v * (i - n / 2);
			gz += (long) v * (j - n / 2);
		}
		int spread = hi - lo, ux, uz;
		if (spread <= 2) {
			Direction d = ROT[(int) Math.floorMod(seed, 4L)];
			ux = d.getStepX();
			uz = d.getStepZ();
		} else if (Math.abs(gx) >= Math.abs(gz)) {
			ux = gx >= 0 ? 1 : -1;
			uz = 0;
		} else {
			ux = 0;
			uz = gz >= 0 ? 1 : -1;
		}
		p[4 + n * n] = spread <= 2 ? 100 : spread <= 4 ? 50 : 0;
		p[5 + n * n] = ux;
		p[6 + n * n] = uz;
		return List.of(new Piece("farms", o, seed, 0, p, box(x - 17, y0 - 14, z - 17, x + 17, y0 + 9, z + 17), null, null));
	}

	static int spread(G g, int x, int z, int dx, int dz) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int s = -15; s <= 15; s += 5) {
			int v = g.y(x + dx * s, z + dz * s);
			lo = Math.min(lo, v);
			hi = Math.max(hi, v);
		}
		return hi - lo;
	}

	/** Souk street along the flatter axis (31 cells, steps of at most 1), six stalls on their own pads facing it. */
	static List<Piece> market(BlockPos o, long seed, G g) {
		int x = o.getX(), y0 = o.getY(), z = o.getZ(), n = 31, half = 15;
		int rot = spread(g, x, z, 0, 1) <= spread(g, x, z, 1, 0) ? 0 : 3;
		Frame f = new Frame(x, y0, z, rot);
		int[] raw = new int[n], w = new int[n];
		for (int i = 0; i < n; i++) raw[i] = Mth.clamp(g.y(f.x(0, i - half), f.z(0, i - half)) + 1, y0 - 5, y0 + 5);
		// the mean of the lower and upper 1-Lipschitz envelopes: follows the ground, never steps more than 1
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int i = 0; i < n; i++) {
			int up = Integer.MAX_VALUE, dn = Integer.MIN_VALUE;
			for (int j = 0; j < n; j++) {
				up = Math.min(up, raw[j] + Math.abs(i - j));
				dn = Math.max(dn, raw[j] - Math.abs(i - j));
			}
			w[i] = Math.floorDiv(up + dn, 2);
			lo = Math.min(lo, w[i]);
			hi = Math.max(hi, w[i]);
		}
		int[] sp = new int[1 + n];
		sp[0] = n;
		System.arraycopy(w, 0, sp, 1, n);
		List<Piece> out = new ArrayList<>();
		out.add(new Piece("market_street", new BlockPos(x, w[half], z), seed, rot, sp, fbox(f, -5, -half, 5, half, lo - 10, hi + 6), null, null));
		int booth = (int) Math.floorMod(seed, 6L), chest = (booth + 3) % 6, k = 0;
		for (int s : new int[] {-10, 0, 10}) for (int side = -1; side <= 1; side += 2) {
			int role = k == booth ? 1 : k == chest ? 2 : 0, hw = role == 1 ? 3 : 2, sw = w[s + half];
			int srot = rotOf(f.dir(side > 0 ? Direction.WEST : Direction.EAST));
			int cx = f.x(6 * side, s), cz = f.z(6 * side, s);
			Frame sf = new Frame(cx, 0, cz, srot);
			int gm = median(g.y(sf.x(-hw, -2), sf.z(-hw, -2)), g.y(sf.x(hw, -2), sf.z(hw, -2)), g.y(sf.x(-hw, 2), sf.z(-hw, 2)), g.y(sf.x(hw, 2), sf.z(hw, 2)));
			int yp = Mth.clamp(gm + 1, sw - 2, sw + 2);
			Frame pf = new Frame(cx, yp, cz, srot);
			out.add(new Piece("market_stall", new BlockPos(cx, yp, cz), seed ^ (0x57A1L * (k + 1)), srot, new int[] {role, (int) Math.floorMod(seed + k, 6L), sw},
				fbox(pf, -hw - 1, -3, hw + 1, 4, yp - 8, yp + 6), fbox(pf, -hw, -2, hw, 2, yp, yp + 3), TerrainAdjustment.BEARD_THIN));
			k++;
		}
		return out;
	}

	/** Stilt hut: the deck 3 blocks over the highest post footing, the ladder on the downhill side. */
	static List<Piece> hut(BlockPos o, long seed, G g) {
		int x = o.getX(), z = o.getZ();
		int rot = downhill(g, o, 7, seed);
		Frame f = new Frame(x, 0, z, rot);
		int top = Integer.MIN_VALUE;
		for (int[] q : new int[][] {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) top = Math.max(top, g.y(f.x(q[0], q[1]), f.z(q[0], q[1])));
		int d = top + 4;
		return List.of(new Piece("hut", new BlockPos(x, d, z), seed, rot, new int[0], box(x - 7, d - 18, z - 7, x + 7, d + 10, z + 7), null, null));
	}

	/** Spring: the lowest point of a 5x5 lattice round the site; the pool fills its hollow, the rill runs to the lowest side. */
	static List<Piece> spring(BlockPos o, long seed, G g) {
		int mx = o.getX(), mz = o.getZ(), bx = mx, bz = mz, best = Integer.MAX_VALUE;
		for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) {
			int score = g.y(mx + i * 4, mz + j * 4) * 16 + Math.abs(i) + Math.abs(j);
			if (score < best) { best = score; bx = mx + i * 4; bz = mz + j * 4; }
		}
		int gc = g.y(bx, bz), spill = Integer.MAX_VALUE;
		for (int a = 0; a < 12; a++) {
			double t = a * Math.PI / 6;
			spill = Math.min(spill, g.y(bx + (int) Math.round(Math.cos(t) * 7), bz + (int) Math.round(Math.sin(t) * 7)));
		}
		int wy = Math.max(gc, Math.min(spill, gc + 1));
		int rd = 0, rdy = Integer.MAX_VALUE;
		for (int k = 0; k < 4; k++) {
			Direction d = Direction.from2DDataValue(k);
			int v = g.y(bx + d.getStepX() * 9, bz + d.getStepZ() * 9);
			if (v < rdy) { rdy = v; rd = k; }
		}
		int rillLen = 8, n = 7, off = 2 + rillLen;
		int[] p = new int[off + 4 + n * n];
		p[0] = rd;
		p[1] = rillLen;
		p[off] = bx - 6;
		p[off + 1] = bz - 6;
		p[off + 2] = 2;
		p[off + 3] = n;
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) p[off + 4 + i * n + j] = g.y(bx - 6 + 2 * i, bz - 6 + 2 * j);
		BlockPos c = new BlockPos(bx, wy, bz);
		BoundingBox bb = box(bx - 17, wy - 10, bz - 17, bx + 17, wy + 6, bz + 17);
		Piece shape = new Piece("spring", c, seed, 0, p, bb, null, null);
		Direction dir = Direction.from2DDataValue(rd);
		int sx = bx, sz = bz;
		for (int i = 0; i < 12 && GreenhazeLooks.inPool(shape, sx, sz); i++) { sx += dir.getStepX(); sz += dir.getStepZ(); }
		int prev = wy;
		for (int i = 0; i < rillLen; i++) {
			int lv = Math.max(prev - 2, Math.min(prev, g.y(sx + dir.getStepX() * i, sz + dir.getStepZ() * i)));
			p[2 + i] = lv;
			prev = lv;
		}
		return List.of(new Piece("spring", c, seed, 0, p, bb, null, null));
	}

	// ------------------------------------------------------------------------------------------------ the Hush

	/**
	 * Access tunnel from a buried room: 1 step per 2 blocks toward the nearest cave floor, searched from {@code from} cells out (beyond the
	 * encapsulating beard) to {@code to}; with none, a {@code fallback}-cell tunnel like 2.x.
	 */
	static Piece tunnel(Columns c, BlockPos o, int rot, long seed, int from, int to, int fallback, boolean cache) {
		Frame f = new Frame(o.getX(), o.getY(), o.getZ(), rot);
		int base = o.getY(), n = fallback, target = base;
		for (int i = from; i <= to; i++) {
			int s = shelf(c, f.x(0, i), f.z(0, i), base, base - i / 2, base + i / 2);
			if (s != Integer.MIN_VALUE) { n = i + 1; target = s; break; }
		}
		int[] p = new int[2 + n];
		p[0] = n;
		for (int i = 0; i < n; i++) p[1 + i] = base + Integer.signum(target - base) * Math.min(i / 2, Math.abs(target - base));
		p[1 + n] = cache ? 1 : 0;
		return new Piece("engine_tunnel", o, seed, rot, p, fbox(f, -3, -1, 3, n, Math.min(base, target) - 10, Math.max(base, target) + 6), null, null);
	}

	/** The Engine (encapsulated, so caves never cut its vault) and its east access tunnel. */
	static List<Piece> engine(BlockPos o, long seed, Columns c) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		return List.of(
			new Piece("engine", o, seed, 0, new int[0], box(x - 24, y - 10, z - 24, x + 24, y + 24, z + 24), box(x - 23, y - 8, z - 23, x + 23, y + 23, z + 23), TerrainAdjustment.ENCAPSULATE),
			tunnel(c, new BlockPos(x + 20, y + 1, z), 3, seed ^ 0x7E1L, 18, 36, 12, true));
	}

	/** The geode (encapsulated) and a tunnel from the end of its north entrance to the nearest cave. */
	static List<Piece> geode(BlockPos o, long seed, Columns c) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		return List.of(
			new Piece("geode", o, seed, 0, new int[0], box(x - 27, y - 24, z - 33, x + 27, y + 30, z + 27), box(x - 27, y - 22, z - 27, x + 27, y + 28, z + 27), TerrainAdjustment.ENCAPSULATE),
			tunnel(c, new BlockPos(x, y, z - 33), 2, seed ^ 0x6E0L, 8, 30, 4, false));
	}

	/** Chapel on the found floor; three cells on the cave's own shelves (else carved at chapel level); galleries on piers between them. */
	static List<Piece> monastery(BlockPos o, long seed, Columns c) {
		int x = o.getX(), y0 = o.getY(), z = o.getZ(), R = 6;
		int rot = (int) Math.floorMod(seed, 4L);
		Frame f = new Frame(x, y0, z, rot);
		List<Piece> out = new ArrayList<>(), galleries = new ArrayList<>();
		out.add(new Piece("chapel", o, seed, rot, new int[] {15}, fbox(f, -8, -8, 8, 8, y0 - 10, y0 + 8), fbox(f, -R, -R, R, R, y0, y0 + 3), TerrainAdjustment.BEARD_THIN));
		Direction[] local = {Direction.NORTH, Direction.WEST, Direction.EAST};
		for (int k = 0; k < local.length; k++) {
			int gx = local[k].getStepX(), gz = local[k].getStepZ(), n = 5;
			int wx = f.x(gx * 15, gz * 15), wz = f.z(gx * 15, gz * 15);
			int s = shelf(c, wx, wz, y0, y0 - (n - 1), y0 + (n - 1));
			int yc = s == Integer.MIN_VALUE ? y0 : s;
			Direction wd = f.dir(local[k]);
			out.add(new Piece("cell", new BlockPos(wx, yc, wz), seed ^ (0xCE11L * (k + 1)), rotOf(wd.getOpposite()), new int[0],
				box(wx - 5, yc - 10, wz - 5, wx + 5, yc + 6, wz + 5), box(wx - 3, yc, wz - 3, wx + 3, yc + 3, wz + 3), TerrainAdjustment.BEARD_THIN));
			int[] p = new int[1 + n];
			p[0] = n;
			for (int i = 0; i < n; i++) p[1 + i] = y0 + (int) Math.round((yc - y0) * (double) i / (n - 1));
			int sx = f.x(gx * (R + 1), gz * (R + 1)), sz = f.z(gx * (R + 1), gz * (R + 1));
			Frame gf = new Frame(sx, y0, sz, rotOf(wd));
			galleries.add(new Piece("gallery", new BlockPos(sx, y0, sz), seed ^ (0x6A1L * (k + 1)), rotOf(wd), p,
				fbox(gf, -3, 0, 3, n, Math.min(y0, yc) - 14, Math.max(y0, yc) + 4), null, null));
		}
		out.addAll(galleries);
		return out;
	}

	/** Up to 7 towers, each hung from its own ceiling (the centre one always, it keeps the Ancients' door); chain bridges from the centre. */
	static List<Piece> undercity(BlockPos o, long seed, Columns c) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		RandomSource r = RandomSource.create(seed);
		List<Piece> towers = new ArrayList<>(), bridges = new ArrayList<>();
		List<int[]> placed = new ArrayList<>(); // tx, top, tz, w
		for (int i = 0; i < 7; i++) {
			double a = i * 2 * Math.PI / 7 + r.nextDouble() * 0.4;
			double rad = i == 0 ? 0 : 9 + r.nextInt(7);
			int tx = x + (int) Math.round(Math.cos(a) * rad), tz = z + (int) Math.round(Math.sin(a) * rad);
			int h = 12 + r.nextInt(14), w = i == 0 ? 4 : 3, chain = 2 + r.nextInt(3);
			int ceil = ceiling(c, tx, tz, y - 8, 24);
			if (i == 0 && ceil == Integer.MIN_VALUE) ceil = y + 1;
			if (ceil == Integer.MIN_VALUE) continue;
			boolean room = true;
			for (int yy = ceil - 1; yy >= ceil - 4 && room; yy--) room = c.at(tx, yy, tz).isAir();
			boolean apart = true;
			for (int[] q : placed) apart &= Math.max(Math.abs(q[0] - tx), Math.abs(q[2] - tz)) > q[3] + w + 2;
			if (i != 0 && (!room || !apart)) continue;
			int top = ceil - 2 - chain;
			towers.add(new Piece("tower", new BlockPos(tx, top, tz), seed ^ (0x70E1L * (i + 1)), 0, new int[] {ceil, h, w, i == 0 ? 1 : 0},
				box(tx - w - 2, top - h - 2 * w - 3, tz - w - 2, tx + w + 2, ceil + 6, tz + w + 2), null, null));
			placed.add(new int[] {tx, top, tz, w});
		}
		int[] hub = placed.get(0);
		for (int k = 1; k < placed.size(); k++) {
			int[] t = placed.get(k);
			Direction da = toward(t[0] - hub[0], t[2] - hub[2]), db = da.getOpposite();
			int ax = hub[0] + da.getStepX() * hub[3], az = hub[2] + da.getStepZ() * hub[3];
			int bx = t[0] + db.getStepX() * t[3], bz = t[2] + db.getStepZ() * t[3];
			int len = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
			if (len < 2 || Math.abs(t[1] - hub[1]) * 2 > len) continue;
			boolean clear = true;
			for (int s = 1; s < len && clear; s++) {
				int cx = ax + (int) Math.round((bx - ax) * s / (double) len), cz = az + (int) Math.round((bz - az) * s / (double) len);
				int cy = hub[1] + (int) Math.round((t[1] - hub[1]) * (s / (double) len));
				for (int yy = cy - 1; yy <= cy + 2 && clear; yy++) clear = !solid(c.at(cx, yy, cz));
			}
			if (!clear) continue;
			bridges.add(new Piece("bridge", new BlockPos(ax, hub[1], az), seed ^ (0xB41L * k), 0, new int[] {bx, t[1], bz},
				box(Math.min(ax, bx) - 3, Math.min(hub[1], t[1]) - 2, Math.min(az, bz) - 3, Math.max(ax, bx) + 3, Math.max(hub[1], t[1]) + 2, Math.max(az, bz) + 3), null, null));
		}
		List<Piece> out = new ArrayList<>(towers);
		out.addAll(bridges);
		return out;
	}
}
