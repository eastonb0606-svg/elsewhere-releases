package dev.elsewhere.kingdom.war;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The longship's coarse water route (RIVALS-DECISIONS 18): computed once per raid, A* over water-surface columns on a 4-block grid, from where the water
 * enters the loaded area (a ring about 96 blocks out from the landing, entity-ticking chunks only, preferring the side the raid comes from) to the landing.
 * A node is open water at least 2 deep and about 5 wide at the landing's water level; a step also needs water at its midpoint. The work is a {@link Job}
 * stepped from the war tick with a node and time budget per tick and a hard node cap; it never loads chunks. Results are cached per realm and target.
 */
public final class WaterPath {
	private WaterPath() {}

	public static final int GRID = 4, ENTRY = 96, ENTRY_MIN = 40, MAX_NODES = 2000, NODES_PER_TICK = 200, RING_SAMPLES = 48;
	/** Time budget per tick slice (0.5 ms). */
	public static final long SLICE_NANOS = 500_000L;
	static final int NO = Integer.MIN_VALUE;
	/** Linked once at class load, so the first search's slices do not pay for class loading and lambda linkage. */
	static final java.util.Comparator<Open> BY_F = (p, q) -> Double.compare(p.f(), q.f());
	static final java.util.Comparator<long[]> BY_PREF = (p, q) -> Long.compare(p[1], q[1]);

	/** A budgeted search. {@link #step()} does one tick's share and returns true once finished ({@link #path} is then the route or null). */
	public static final class Job {
		final ServerLevel lv;
		final BlockPos goal, finish;
		final int waterY;
		final double bx, bz; // preferred direction the ship comes from (unit vector from the landing)
		final Long2IntOpenHashMap surface = new Long2IntOpenHashMap();
		final List<long[]> entries = new ArrayList<>();
		int ring = ENTRY, entryIdx, sample;
		final List<long[]> ringFound = new ArrayList<>();
		/** Debug: rings scanned and entries found. */
		public final StringBuilder log = new StringBuilder();
		boolean scanned, done;
		// A* state of the current entry
		PriorityQueue<Open> open;
		Long2DoubleOpenHashMap g;
		Long2LongOpenHashMap parent;
		LongOpenHashSet closed;
		long startKey, goalKey;
		public int expanded, slices;
		/** Wall-clock time spent (total, worst slice) and the worst slice in thread CPU time (excludes GC pauses and the OS preempting the server thread). */
		public long nanos, maxSlice, maxSliceCpu;
		public @Nullable List<Vec3> path;

		Job(ServerLevel lv, BlockPos goal, BlockPos finish, int waterY, double bx, double bz) {
			this.lv = lv;
			this.goal = goal;
			this.finish = finish;
			this.waterY = waterY;
			double n = Math.sqrt(bx * bx + bz * bz);
			this.bx = n < 1e-6 ? 0 : bx / n;
			this.bz = n < 1e-6 ? 0 : bz / n;
			surface.defaultReturnValue(Integer.MAX_VALUE);
		}

		public boolean done() {
			return done;
		}

		/** One tick's share of the work. */
		public boolean step() {
			if (done) return true;
			long t0 = System.nanoTime(), c0 = cpu();
			int budget = NODES_PER_TICK;
			slices++;
			try {
				while (!done && budget > 0 && System.nanoTime() - t0 < SLICE_NANOS) {
					if (!scanned) {
						scanRing();
						budget -= 4;
						continue;
					}
					if (open == null) {
						if (entryIdx >= entries.size() || entryIdx >= 4 || expanded >= MAX_NODES) {
							done = true;
							break;
						}
						begin(entries.get(entryIdx++));
						continue;
					}
					expand(); // one node per check, so a slice never overruns the time budget by more than one expansion
					budget--;
				}
			} finally {
				long dt = System.nanoTime() - t0;
				nanos += dt;
				maxSlice = Math.max(maxSlice, dt);
				maxSliceCpu = Math.max(maxSliceCpu, cpu() - c0);
			}
			return done;
		}

		/** Scans the next 8 samples of the current ring; at the end of a ring keeps its best entries (spread out) and moves inwards if fewer than 2. */
		private void scanRing() {
			for (int n = 0; n < 8 && sample < RING_SAMPLES; n++, sample++) {
				double a = sample * Math.PI * 2 / RING_SAMPLES;
				double cx = Math.cos(a), cz = Math.sin(a);
				int x = goal.getX() + snap(cx * ring), z = goal.getZ() + snap(cz * ring);
				if (!lv.isPositionEntityTicking(new BlockPos(x, waterY, z)) || !node(x, z)) continue;
				double pref = bx == 0 && bz == 0 ? 0 : cx * bx + cz * bz; // 1 = straight from the preferred side
				ringFound.add(new long[] {key(x, z), Math.round(-pref * 1000)});
			}
			if (sample < RING_SAMPLES) return;
			ringFound.sort(BY_PREF);
			// keep entries a good way apart (at most 3 are tried), so a blocked one does not repeat almost the same search
			for (long[] f : ringFound) {
				boolean near = false;
				for (long[] e : entries) if (dist(e[0], f[0]) < 4 * GRID) near = true; // close entries only; one in a separate pond must not hide the real one
				if (!near) entries.add(f);
			}
			log.append(ring).append(':').append(ringFound.size()).append(' ');
			ringFound.clear();
			sample = 0;
			if (entries.size() >= 3 || entries.size() >= 2 && ring < ENTRY || ring <= ENTRY_MIN) scanned = true;
			else ring -= 8;
		}

		/** Debug: the entries found, as x,z. */
		public String entries() {
			StringBuilder b = new StringBuilder(log);
			for (long[] e : entries) b.append('(').append(kx(e[0])).append(',').append(kz(e[0])).append(')');
			return b.toString();
		}

		private int snap(double v) {
			return (int) (Math.round(v / GRID) * GRID);
		}

		private void begin(long[] e) {
			open = new PriorityQueue<>(BY_F);
			g = new Long2DoubleOpenHashMap();
			parent = new Long2LongOpenHashMap();
			closed = new LongOpenHashSet();
			startKey = e[0];
			goalKey = key(goal.getX(), goal.getZ());
			g.put(startKey, 0);
			open.add(new Open(h(startKey), startKey));
		}

		private void expand() {
			Open top = open.poll();
			if (top == null) {
				open = null;
				return;
			}
			long k = top.key();
			if (!closed.add(k)) return;
			if (++expanded > MAX_NODES) {
				open = null;
				done = true;
				return;
			}
			if (k == goalKey) {
				build(k);
				open = null;
				done = true;
				return;
			}
			int x = kx(k), z = kz(k);
			double gk = g.get(k);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				int nx = x + dx * GRID, nz = z + dz * GRID;
				long nk = key(nx, nz);
				if (closed.contains(nk)) continue;
				boolean isGoal = nk == goalKey;
				if (!(isGoal ? column(nx, nz) != NO : node(nx, nz)) || column(x + dx * GRID / 2, z + dz * GRID / 2) == NO) continue;
				double ng = gk + (dx != 0 && dz != 0 ? 1.4142 : 1.0);
				if (g.containsKey(nk) && g.get(nk) <= ng) continue;
				g.put(nk, ng);
				parent.put(nk, k);
				open.add(new Open(ng + h(nk), nk));
			}
		}

		private double h(long k) {
			double dx = Math.abs(kx(k) - goal.getX()) / (double) GRID, dz = Math.abs(kz(k) - goal.getZ()) / (double) GRID;
			return Math.max(dx, dz) + 0.4142 * Math.min(dx, dz);
		}

		private void build(long end) {
			List<Long> keys = new ArrayList<>();
			for (long k = end;; k = parent.get(k)) {
				keys.add(k);
				if (k == startKey || !parent.containsKey(k)) break;
			}
			java.util.Collections.reverse(keys);
			// string pulling: keep a node only when the straight line past it would leave open water (the final run-in stays as computed)
			List<Long> pulled = new ArrayList<>();
			int i = 0;
			pulled.add(keys.get(0));
			while (i < keys.size() - 1) {
				int j = keys.size() - 1;
				while (j > i + 1 && !clear(keys.get(i), keys.get(j))) j--;
				pulled.add(keys.get(j));
				i = j;
			}
			List<Vec3> out = new ArrayList<>();
			for (long k : pulled) out.add(new Vec3(kx(k) + 0.5, waterY, kz(k) + 0.5));
			if (pulled.get(pulled.size() - 1) != key(finish.getX(), finish.getZ())) out.add(new Vec3(finish.getX() + 0.5, waterY, finish.getZ() + 0.5));
			path = out;
		}

		private boolean clear(long a, long b) {
			double ax = kx(a), az = kz(a), bx2 = kx(b), bz2 = kz(b);
			double len = Math.sqrt((bx2 - ax) * (bx2 - ax) + (bz2 - az) * (bz2 - az));
			int n = (int) Math.ceil(len / 2);
			for (int s = 1; s < n; s++) {
				int x = (int) Math.round(ax + (bx2 - ax) * s / n), z = (int) Math.round(az + (bz2 - az) * s / n);
				if (!node(x, z)) return false;
			}
			return true;
		}

		/** Open water about 5 wide (the column and the columns 2 blocks away on both axes). */
		boolean node(int x, int z) {
			return column(x, z) != NO && column(x + 2, z) != NO && column(x - 2, z) != NO && column(x, z + 2) != NO && column(x, z - 2) != NO;
		}

		/** The water level of a column when it is water at least 2 deep at the landing's level (loaded chunks only), else {@link #NO}. */
		int column(int x, int z) {
			long k = key(x, z);
			int c = surface.get(k);
			if (c != Integer.MAX_VALUE) return c;
			int r = NO;
			BlockPos p = new BlockPos(x, waterY, z);
			if (lv.isLoaded(p)) {
				int top = lv.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, p).getY() - 1;
				if (Math.abs(top - waterY) <= 1 && lv.getFluidState(new BlockPos(x, top, z)).is(FluidTags.WATER) && lv.getFluidState(new BlockPos(x, top - 1, z)).is(FluidTags.WATER)
					&& lv.getBlockState(new BlockPos(x, top, z)).getCollisionShape(lv, new BlockPos(x, top, z)).isEmpty()) r = top;
			}
			surface.put(k, r);
			return r;
		}
	}

	/**
	 * Starts a search for a ship that must end at {@code finish} (its beached centre) coming in along (dx, dz); {@code from} is the preferred side the ship
	 * comes from (a direction away from the landing), or zero for any. Returns null when the run-in itself is not open water.
	 */
	public static @Nullable Job start(ServerLevel lv, BlockPos finish, int dx, int dz, int waterY, double fromX, double fromZ) {
		Job probe = new Job(lv, finish, finish, waterY, fromX, fromZ);
		if (probe.column(finish.getX(), finish.getZ()) == NO) return null;
		BlockPos runIn = finish.offset(-dx * GRID, 0, -dz * GRID);
		BlockPos goal = probe.column(runIn.getX(), runIn.getZ()) != NO && probe.column(runIn.getX() - dx * GRID / 2, runIn.getZ() - dz * GRID / 2) != NO ? runIn : finish;
		Job j = new Job(lv, goal, finish, waterY, fromX, fromZ);
		j.surface.putAll(probe.surface);
		return j;
	}

	record Open(double f, long key) {}

	/** Server start: loads and links the search classes once (a few ms of class loading that would otherwise land in the first raid's first slice). */
	public static void warm() {
		PriorityQueue<Open> q = new PriorityQueue<>(BY_F);
		q.add(new Open(1, key(1, 1)));
		q.add(new Open(0, key(0, 0)));
		q.poll();
		List<long[]> l = new ArrayList<>(List.of(new long[] {1, 2}, new long[] {0, 1}));
		l.sort(BY_PREF);
		new Long2DoubleOpenHashMap().put(1L, 1.0);
		new Long2LongOpenHashMap().put(1L, 1L);
		new LongOpenHashSet().add(1L);
		new Long2IntOpenHashMap().put(1L, 1);
		cpu();
	}

	static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	static int kx(long k) {
		return (int) (k >> 32);
	}

	static int kz(long k) {
		return (int) k;
	}

	static double dist(long a, long b) {
		double dx = kx(a) - kx(b), dz = kz(a) - kz(b);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Length of a route in blocks. */
	public static double length(List<Vec3> p) {
		double l = 0;
		for (int i = 1; i < p.size(); i++) l += p.get(i).distanceTo(p.get(i - 1));
		return l;
	}

	private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

	/** Thread CPU time when the JVM offers it, else wall time. */
	static long cpu() {
		try {
			if (THREADS.isCurrentThreadCpuTimeSupported()) return THREADS.getCurrentThreadCpuTime();
		} catch (RuntimeException | Error e) {
			// fall through
		}
		return System.nanoTime();
	}
}
