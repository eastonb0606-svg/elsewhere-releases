package dev.elsewhere.kingdom_court.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * s-layout-mire (3.0.0): Mirewick's own castle LAYOUT for new (fv 2) realm starts, "the Lantern Water-Castle" of STRUCTURES.md 3.4 / 7.1.
 * Not the Aldermark square re-skinned: a five-bastion pentagonal star trace ({@link MireFort}) with a wet moat cut only on flat ground near the
 * floor (a dry ditch elsewhere, contained beds, dams at every run end), the quay canal in front of the gate (z-castle's terrain-safe
 * {@link CastleKit#canal}), a stilted Guildhall and a stilted sluice house over an inner canal with quays and lantern bridges, the
 * Counting-House with its lantern belfry, ten narrow canal houses with stepped, neck and bell gables and hoist beams, the arsenal, barracks and
 * stables ({@link MireCourt}, roofs and signatures in {@link MireKit}); outside, a ravelin before the gate and the dike road with drainage
 * ditches ({@link MireOutworks}). Royal purple, crimson and gold are balanced (purple panels, bands, glass and banners; crimson roofs, shutters
 * and timber on half the houses, slate on the rest; yellow-terracotta gilt).
 * <p>
 * Runs only for fv 2 pieces of a Mirewick style ({@code Placer.ground != null}); saved fv 1 starts and every other kingdom are untouched. All
 * the gameplay anchors of {@link CastleLayout} (throne, hall, gate at z 30, yard, barracks, stables, bell, braziers, wall walk at (+-12, 9, 29),
 * yard loop) stay where the court AI expects them. Deterministic: only the generator ground ({@code Ground.y}) and the chunk being written are
 * read. Kill switch: {@code -Delsewhere.overhaul3.mire_layout=false} (Mirewick falls back to the shared layout plus CastleKit).
 */
public final class MireLayout {
	private MireLayout() {}

	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.mire_layout", "true"));

	/** Hook for {@link CastleLayout#build}: true when this part was built by the Mirewick layout. */
	public static boolean build(CastleLayout.Part part, BlockPos origin, CastleLayout.Placer pl) {
		if (!ENABLED || pl.ground == null || CastleKit.kitOf(pl) != CastleKit.Kit.MIREWICK) return false;
		CastleLayout.L l = new CastleLayout.L(origin, pl);
		switch (part) {
			case CASTLE -> MireCourt.build(l);
			case WALL -> MireFort.build(l);
			case KIT_CASTLE -> MireKit.build(l);
			case ROAD -> MireOutworks.build(l);
			default -> {
				return false;
			}
		}
		return true;
	}

	// ---- the trace (constant geometry, computed once) ----------------------------------------------------------------

	/** Core pentagon (bastion centres): north, east, south-east, south-west, west. The south curtain (gate front) lies at z 30.5. */
	static final double[][] CORE = {{0, -29}, {30, -22}, {24, 30.5}, {-24, 30.5}, {-30, -22}};
	static final double GORGE = 6, FLANK = 3, SALIENT = 6;
	/** Closed outline: per bastion flank base, shoulder, salient, shoulder, flank base. */
	static final double[][] TRACE = new double[25][];
	static final double[][][] BASTIONS = new double[5][][];
	/** Salient points (bastion tips). */
	static final double[][] TIPS = new double[5][];
	/** Outline samples at unit spacing: {x, z, outward nx, nz}. */
	static final double[][] SAMPLES;
	static final int R = 40, GW = 2 * R + 1;
	static final float[] DELTA = new float[GW * GW];
	static final short[] NEAR = new short[GW * GW];
	static final boolean[] BAST = new boolean[GW * GW];

	static {
		int n = CORE.length;
		double cx = 0, cz = 0;
		for (double[] c : CORE) {
			cx += c[0] / n;
			cz += c[1] / n;
		}
		int k = 0;
		for (int i = 0; i < n; i++) {
			double[] v = CORE[i], p = CORE[(i + n - 1) % n], q = CORE[(i + 1) % n];
			double[] a = norm(p[0] - v[0], p[1] - v[1]), b = norm(q[0] - v[0], q[1] - v[1]);
			double[] f1 = {v[0] + a[0] * GORGE, v[1] + a[1] * GORGE}, f2 = {v[0] + b[0] * GORGE, v[1] + b[1] * GORGE};
			double[] na = away(a, f1, cx, cz), nb = away(b, f2, cx, cz);
			double[] s1 = {f1[0] + na[0] * FLANK, f1[1] + na[1] * FLANK}, s2 = {f2[0] + nb[0] * FLANK, f2[1] + nb[1] * FLANK};
			double[] d = norm(-(a[0] + b[0]), -(a[1] + b[1]));
			double[] t = {v[0] + d[0] * SALIENT, v[1] + d[1] * SALIENT};
			TRACE[k++] = f1;
			TRACE[k++] = s1;
			TRACE[k++] = t;
			TRACE[k++] = s2;
			TRACE[k++] = f2;
			BASTIONS[i] = new double[][] {f1, s1, t, s2, f2};
			TIPS[i] = t;
		}
		java.util.List<double[]> s = new java.util.ArrayList<>();
		for (int i = 0; i < TRACE.length; i++) {
			double[] p = TRACE[i], q = TRACE[(i + 1) % TRACE.length];
			double dx = q[0] - p[0], dz = q[1] - p[1], len = Math.hypot(dx, dz);
			double ux = dx / len, uz = dz / len, nx = uz, nz = -ux;
			double mx = (p[0] + q[0]) / 2, mz = (p[1] + q[1]) / 2;
			if (inside(TRACE, mx + nx * 0.5, mz + nz * 0.5)) {
				nx = -nx;
				nz = -nz;
			}
			int steps = Math.max(1, (int) Math.round(len));
			for (int j = 0; j < steps; j++) s.add(new double[] {p[0] + dx * j / steps, p[1] + dz * j / steps, nx, nz});
		}
		SAMPLES = s.toArray(new double[0][]);
		for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
			int i = (x + R) * GW + z + R;
			double best = Double.MAX_VALUE;
			for (int j = 0; j < TRACE.length; j++) best = Math.min(best, segDist(x, z, TRACE[j], TRACE[(j + 1) % TRACE.length]));
			DELTA[i] = (float) (inside(TRACE, x, z) ? best : -best);
			double bs = Double.MAX_VALUE;
			int bj = 0;
			for (int j = 0; j < SAMPLES.length; j++) {
				double e = Math.hypot(SAMPLES[j][0] - x, SAMPLES[j][1] - z);
				if (e < bs) {
					bs = e;
					bj = j;
				}
			}
			NEAR[i] = (short) bj;
			if (DELTA[i] > 0) for (double[][] bp : BASTIONS) if (inside(bp, x, z)) BAST[i] = true;
		}
	}

	static double[] norm(double x, double z) {
		double l = Math.hypot(x, z);
		return new double[] {x / l, z / l};
	}

	/** The perpendicular of edge direction {@code a} at point {@code f} that points away from the centre. */
	static double[] away(double[] a, double[] f, double cx, double cz) {
		double[] n = {-a[1], a[0]};
		return n[0] * (f[0] - cx) + n[1] * (f[1] - cz) >= 0 ? n : new double[] {-n[0], -n[1]};
	}

	static boolean inside(double[][] poly, double x, double z) {
		boolean in = false;
		for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			double xi = poly[i][0], zi = poly[i][1], xj = poly[j][0], zj = poly[j][1];
			if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
		}
		return in;
	}

	static double segDist(double x, double z, double[] p, double[] q) {
		double dx = q[0] - p[0], dz = q[1] - p[1];
		double t = Math.clamp(((x - p[0]) * dx + (z - p[1]) * dz) / (dx * dx + dz * dz), 0.0, 1.0);
		return Math.hypot(x - p[0] - t * dx, z - p[1] - t * dz);
	}

	/** Signed distance of local column (x, z) to the trace: > 0 inside the fort, < 0 outside (-99 off the grid). */
	public static float delta(int x, int z) {
		if (Math.abs(x) > R || Math.abs(z) > R) return -99F;
		return DELTA[(x + R) * GW + z + R];
	}

	static int near(int x, int z) {
		return NEAR[(Math.clamp(x, -R, R) + R) * GW + Math.clamp(z, -R, R) + R];
	}

	/** Inside a bastion (beyond its gorge): the solid earth platform at rampart height. */
	public static boolean bastion(int x, int z) {
		return Math.abs(x) <= R && Math.abs(z) <= R && BAST[(x + R) * GW + z + R];
	}

	/** The Counting-House (keep) footprint, which the rampart never overwrites. */
	static boolean keepBox(int x, int z) {
		return x >= -11 && x <= 11 && z >= -26 && z <= -8;
	}

	/** Local cells one block inside each of the five bastion tips (self-test probes). */
	public static int[][] tipProbes() {
		int[][] r = new int[5][];
		for (int i = 0; i < 5; i++) {
			double[] t = TIPS[i], v = CORE[i];
			double[] d = norm(v[0] - t[0], v[1] - t[1]);
			r[i] = new int[] {(int) Math.round(t[0] + d[0] * 2), (int) Math.round(t[1] + d[1] * 2)};
		}
		return r;
	}

	/** Rampart band: inside within 3 of the trace, or a bastion platform. */
	public static boolean rampart(int x, int z) {
		float d = delta(x, z);
		return d > 0 && (d <= 3 || bastion(x, z)) && !keepBox(x, z);
	}

	/** The south quay strip (style quay, z-castle canal): no batter, no moat, no ditch. */
	static boolean quayZone(int x, int z) {
		return Math.abs(x) <= 25 && z >= 26;
	}

	// ---- per realm plan (ground only) --------------------------------------------------------------------------------

	/** Per outline sample: rampart lift and moat mode (0 none, 1 dry ditch, 2 wet). */
	public record Plan(int[] lift, byte[] mode) {}

	static final Map<String, Plan> PLANS = new ConcurrentHashMap<>();

	public static Plan plan(BlockPos o, CastleLayout.Ground g) {
		String key = o.asLong() + ":" + g.y(o.getX(), o.getZ()) + ":" + g.y(o.getX() + 33, o.getZ() - 20) + ":" + g.y(o.getX() - 33, o.getZ() + 9) + ":"
			+ g.y(o.getX(), o.getZ() - 34);
		Plan p = PLANS.get(key);
		if (p != null) return p;
		int n = SAMPLES.length;
		int[] s = new int[n];
		for (int i = 0; i < n; i++) s[i] = Math.max(0, g.y(o.getX() + (int) Math.round(SAMPLES[i][0]), o.getZ() + (int) Math.round(SAMPLES[i][1])) - o.getY());
		for (int rep = 0; rep < 2; rep++) {
			for (int i = 0; i < n; i++) s[i] = Math.max(s[i], s[(i - 1 + n) % n] - 1);
			for (int i = n - 1; i >= 0; i--) s[i] = Math.max(s[i], s[(i + 1) % n] - 1);
		}
		// the gate front (quay strip) stays on the yard floor, so the court's wall-top anchors (+-12, 9, 29) and the gate stairs hold on any
		// ground; the neighbours then step down to it at most 1 per sample (both directions, cyclic)
		for (int i = 0; i < n; i++) if (quayZone((int) Math.round(SAMPLES[i][0]), (int) Math.round(SAMPLES[i][1]))) s[i] = 0;
		for (int rep = 0; rep < 2; rep++) {
			for (int i = 0; i < n; i++) s[i] = Math.min(s[i], s[(i - 1 + n) % n] + 1);
			for (int i = n - 1; i >= 0; i--) s[i] = Math.min(s[i], s[(i + 1) % n] + 1);
		}
		byte[] wet = new byte[n], ditch = new byte[n];
		for (int i = 0; i < n; i++) {
			boolean w = true, d = true;
			for (int k = 1; k <= 7; k++) {
				int x = (int) Math.round(SAMPLES[i][0] + SAMPLES[i][2] * k), z = (int) Math.round(SAMPLES[i][1] + SAMPLES[i][3] * k);
				int gy = g.y(o.getX() + x, o.getZ() + z) - o.getY();
				boolean natWater = CastleLayout.wet(g, o.getX() + x, o.getZ() + z);
				if (quayZone(x, z) || natWater) {
					w = false;
					d = false;
				}
				if (Math.abs(x) > 34 || Math.abs(z) > 34 || gy < -1 || gy > 2) w = false;
				if (Math.abs(x) > 35 || Math.abs(z) > 35 || gy < -1 || gy > 3) d = false;
			}
			wet[i] = (byte) (w ? 1 : 0);
			ditch[i] = (byte) (d ? 1 : 0);
		}
		dropShortRuns(wet, 6);
		dropShortRuns(ditch, 4);
		byte[] mode = new byte[n];
		for (int i = 0; i < n; i++) mode[i] = (byte) (wet[i] == 1 ? 2 : ditch[i] == 1 ? 1 : 0);
		p = new Plan(s, mode);
		if (PLANS.size() > 32) PLANS.clear();
		PLANS.put(key, p);
		return p;
	}

	/** Clears cyclic runs of 1 shorter than {@code min}. */
	static void dropShortRuns(byte[] a, int min) {
		int n = a.length, start = -1;
		for (int i = 0; i < n; i++) if (a[i] == 0) {
			start = i;
			break;
		}
		if (start < 0) return;
		int i = 0;
		while (i < n) {
			int j = (start + i) % n;
			if (a[j] == 0) {
				i++;
				continue;
			}
			int len = 0;
			while (len < n && a[(j + len) % n] == 1) len++;
			if (len < min) for (int k = 0; k < len; k++) a[(j + k) % n] = 0;
			i += len;
		}
	}

	public static int lift(Plan p, int x, int z) {
		return p.lift()[near(x, z)];
	}

	/** Moat band (outside the trace, 1.5 to 5.5 out). */
	static boolean moatBand(int x, int z) {
		float d = delta(x, z);
		return d <= -1.5F && d > -5.5F;
	}

	/** A wet moat cell: water at floor-3 and floor-2 over a bed at floor-4. */
	public static boolean wetMoat(Plan p, int x, int z) {
		return moatBand(x, z) && Math.abs(x) <= 34 && Math.abs(z) <= 34 && !quayZone(x, z) && p.mode()[near(x, z)] == 2;
	}

	/** A dry ditch cell (floor at floor-2). */
	static boolean dryDitch(Plan p, int x, int z) {
		return moatBand(x, z) && Math.abs(x) <= 35 && Math.abs(z) <= 35 && !quayZone(x, z) && !wetMoat(p, x, z) && p.mode()[near(x, z)] >= 1;
	}

	static boolean nextToWet(Plan p, int x, int z) {
		return wetMoat(p, x + 1, z) || wetMoat(p, x - 1, z) || wetMoat(p, x, z + 1) || wetMoat(p, x, z - 1);
	}

	// ---- the inner canal (constant) ------------------------------------------------------------------------------------

	/** Inner canal water: x -21..21, z -2..0, y -3..-2 (local), walls z -3 and 1, ends x +-22, bed y -4. */
	public static boolean innerCanal(int x, int y, int z) {
		return x >= -21 && x <= 21 && z >= -2 && z <= 0 && (y == -3 || y == -2) && !MireCourt.stilt(x, z) && x != MireCourt.SLUICE_GATE_X;
	}

	/**
	 * Every water block the Mirewick layout places (self-test): the inner canal, the wet moat, z-castle's quay canal and the road ditches
	 * ({@link MireOutworks#ditchWater}). Local coordinates relative to the realm origin.
	 */
	public static boolean plannedWater(BlockPos o, CastleLayout.Ground g, CastleStyles.@Nullable Style style, int x, int y, int z) {
		if (innerCanal(x, y, z)) return true;
		if ((y == -3 || y == -2) && wetMoat(plan(o, g), x, z)) return true;
		CastleKit.Canal c = CastleKit.canal(o, g, style);
		if (c != null && c.at(x) && (z == 35 || z == 36) && (y == c.water() - o.getY() || y == c.water() - o.getY() - 1)) return true;
		return MireOutworks.ditchWater(o, g, x, y, z);
	}

	/** Self-test: the ravelin's base level (local), or Integer.MIN_VALUE when the ground skips it (water, or a spread over 5). */
	public static int ravelinBase(BlockPos o, CastleLayout.Ground g) {
		CastleLayout.Placer pl = new CastleLayout.Placer(null, new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 0, 0, 0, 0, 0));
		pl.ground = g;
		return MireOutworks.ravelinBase(new CastleLayout.L(o, pl));
	}

	/** Self-test: the south quay strip (no batter, moat or ditch there). */
	public static boolean quay(int x, int z) {
		return quayZone(x, z);
	}
}
