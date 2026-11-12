package dev.elsewhere.kingdom.armies.townsfolk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A town's street graph (ARMIES.md 2.5 / 3.9): at most {@link #MAX_NODES} standable outdoor points around the town centre (grid
 * candidates, never on roofs, in water or on fences) joined by edges whose straight line is walkable (height steps of at most 1, dry).
 * Built once, lazily on the first approach of a player, by {@link Builder} spread over ticks under a time budget, then stored in
 * {@link TownsfolkState} (rebuilt when older than {@link #MAX_AGE_DAYS}, so fortify and rebuild steps are picked up). Shortest paths
 * are single-source Dijkstra, cached per source node.
 */
public final class StreetGraph {
	public static final int MAX_NODES = 64, MAX_AGE_DAYS = 7;
	public static final byte DOOR = 1;

	final int[] x, y, z;
	final byte[] flags;
	final int[][] adj;
	final int[] comp;
	final int[] compSize;
	final long builtDay;
	private final int[][] prev; // Dijkstra predecessor per source (lazily)
	/** Diagnostics: Dijkstra runs and cached path answers. */
	public int dijkstraRuns, cacheHits;

	StreetGraph(int[] x, int[] y, int[] z, byte[] flags, int[][] adj, long builtDay) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.flags = flags;
		this.adj = adj;
		this.builtDay = builtDay;
		int n = x.length;
		this.prev = new int[n][];
		this.comp = new int[n];
		Arrays.fill(comp, -1);
		List<Integer> sizes = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (comp[i] >= 0) continue;
			int c = sizes.size(), size = 0;
			ArrayDeque<Integer> q = new ArrayDeque<>();
			q.add(i);
			comp[i] = c;
			while (!q.isEmpty()) {
				int a = q.poll();
				size++;
				for (int b : adj[a]) if (comp[b] < 0) {
					comp[b] = c;
					q.add(b);
				}
			}
			sizes.add(size);
		}
		compSize = sizes.stream().mapToInt(Integer::intValue).toArray();
	}

	public int size() {
		return x.length;
	}

	public BlockPos pos(int i) {
		return new BlockPos(x[i], y[i], z[i]);
	}

	public boolean door(int i) {
		return (flags[i] & DOOR) != 0;
	}

	/** True when node {@code i} sits in a connected part of at least 3 nodes (spawn and target nodes come from those). */
	public boolean usable(int i) {
		return compSize[comp[i]] >= 3;
	}

	public int edges() {
		int e = 0;
		for (int[] a : adj) e += a.length;
		return e / 2;
	}

	/** Nearest usable node to {@code p} (horizontal distance, height difference counted double), optionally within component {@code c} (-1 any). */
	public int nearest(BlockPos p, int c) {
		int best = -1;
		long bd = Long.MAX_VALUE;
		for (int i = 0; i < x.length; i++) {
			if (!usable(i) || (c >= 0 && comp[i] != c)) continue;
			long dx = x[i] - p.getX(), dz = z[i] - p.getZ(), dy = 2L * (y[i] - p.getY());
			long d = dx * dx + dz * dz + dy * dy;
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return best;
	}

	/** Node positions from {@code from} to {@code to} (both included), or null when they are not connected. Cached per source. */
	public @Nullable BlockPos[] path(int from, int to) {
		if (from < 0 || to < 0 || comp[from] != comp[to]) return null;
		if (from == to) return new BlockPos[] {pos(to)};
		int[] pr = prev[from];
		if (pr == null) {
			pr = dijkstra(from);
			prev[from] = pr;
			dijkstraRuns++;
		} else cacheHits++;
		ArrayDeque<BlockPos> out = new ArrayDeque<>();
		int c = to, guard = 0;
		while (c != from && c >= 0 && guard++ <= x.length) {
			out.addFirst(pos(c));
			c = pr[c];
		}
		if (c != from) return null;
		out.addFirst(pos(from));
		return out.toArray(new BlockPos[0]);
	}

	private int[] dijkstra(int s) {
		int n = x.length;
		double[] dist = new double[n];
		int[] pr = new int[n];
		boolean[] done = new boolean[n];
		Arrays.fill(dist, Double.MAX_VALUE);
		Arrays.fill(pr, -1);
		dist[s] = 0;
		for (int k = 0; k < n; k++) { // n <= 64: the O(n^2) form is the fastest here
			int u = -1;
			for (int i = 0; i < n; i++) if (!done[i] && (u < 0 || dist[i] < dist[u])) u = i;
			if (u < 0 || dist[u] == Double.MAX_VALUE) break;
			done[u] = true;
			for (int v : adj[u]) {
				double dx = x[u] - x[v], dy = y[u] - y[v], dz = z[u] - z[v];
				double nd = dist[u] + Math.sqrt(dx * dx + dy * dy * 4 + dz * dz);
				if (nd < dist[v]) {
					dist[v] = nd;
					pr[v] = u;
				}
			}
		}
		return pr;
	}

	// ---- building ---------------------------------------------------------------------------------------------------------------

	/** Resumable build: candidates first (nearest the centre first), then edges; {@link #step} works until its time budget is spent. */
	static final class Builder {
		final String key;
		final BlockPos center;
		final int radius, spacing;
		final long day;
		private final List<int[]> cand = new ArrayList<>();
		private int ci, ei, ej;
		private final List<int[]> nodes = new ArrayList<>(); // x, y, z, flags
		private List<List<Integer>> links;
		int unloaded;
		long nanos, maxStepNanos;

		Builder(String key, BlockPos center, int radius, long day) {
			this.key = key;
			this.center = center;
			this.radius = radius;
			this.spacing = radius >= 48 ? 10 : 8;
			this.day = day;
			for (int dx = -radius; dx <= radius; dx += spacing)
				for (int dz = -radius; dz <= radius; dz += spacing)
					if (dx * dx + dz * dz <= radius * radius) cand.add(new int[] {center.getX() + dx, center.getZ() + dz, dx * dx + dz * dz});
			cand.sort((a, b) -> Integer.compare(a[2], b[2]));
		}

		/** Works for at most {@code budgetNanos}; returns the finished graph, or null while work remains. */
		@Nullable StreetGraph step(ServerLevel lv, long budgetNanos) {
			long s0 = System.nanoTime();
			StreetGraph g = stepInner(lv, budgetNanos);
			maxStepNanos = Math.max(maxStepNanos, System.nanoTime() - s0);
			return g;
		}

		private @Nullable StreetGraph stepInner(ServerLevel lv, long budgetNanos) {
			long t0 = System.nanoTime();
			while (ci < cand.size() && nodes.size() < MAX_NODES) {
				int[] c = cand.get(ci++);
				int[] n = standable(lv, c[0], c[1]);
				if (n != null) nodes.add(n);
				if (System.nanoTime() - t0 > budgetNanos) {
					nanos += System.nanoTime() - t0;
					return null;
				}
			}
			if (links == null) {
				links = new ArrayList<>();
				for (int i = 0; i < nodes.size(); i++) links.add(new ArrayList<>());
			}
			double link = spacing * 1.5 + 0.01;
			while (ei < nodes.size()) {
				int[] a = nodes.get(ei);
				if (ej <= ei) ej = ei + 1;
				while (ej < nodes.size()) {
					int j = ej++;
					int[] b = nodes.get(j);
					double dx = a[0] - b[0], dz = a[2] - b[2];
					if (dx * dx + dz * dz > link * link || Math.abs(a[1] - b[1]) > 3) continue;
					if (walkable(lv, a, b)) {
						links.get(ei).add(j);
						links.get(j).add(ei);
					}
					if (System.nanoTime() - t0 > budgetNanos) { // resumes at the next pair: no tick does more than one segment past the budget
						nanos += System.nanoTime() - t0;
						return null;
					}
				}
				ei++;
			}
			nanos += System.nanoTime() - t0;
			int n = nodes.size();
			int[] gx = new int[n], gy = new int[n], gz = new int[n];
			byte[] gf = new byte[n];
			int[][] adj = new int[n][];
			for (int i = 0; i < n; i++) {
				int[] p = nodes.get(i);
				gx[i] = p[0];
				gy[i] = p[1];
				gz[i] = p[2];
				gf[i] = (byte) p[3];
				adj[i] = links.get(i).stream().mapToInt(Integer::intValue).toArray();
			}
			return new StreetGraph(gx, gy, gz, gf, adj, day);
		}

		/** {x, feetY, z, flags} when a person can stand outdoors at the top of column x/z, else null. */
		private int @Nullable [] standable(ServerLevel lv, int x, int z) {
			BlockPos probe = new BlockPos(x, center.getY(), z);
			if (!lv.isLoaded(probe)) {
				unloaded++;
				return null;
			}
			int fy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, fy, z), ground = feet.below();
			BlockState g = lv.getBlockState(ground);
			if (g.isAir() || !g.getFluidState().isEmpty() || g.is(BlockTags.LEAVES)) return null;
			VoxelShape gs = g.getCollisionShape(lv, ground);
			if (gs.isEmpty() || gs.max(Direction.Axis.Y) > 1.0) return null; // fences, walls
			for (BlockPos p : new BlockPos[] {feet, feet.above()}) {
				BlockState s = lv.getBlockState(p);
				if (!s.getCollisionShape(lv, p).isEmpty() || !s.getFluidState().isEmpty() || s.is(BlockTags.FIRE)) return null;
			}
			// a roof or an upper floor has air under it: not a street
			for (int k = 1; k <= 4; k++) {
				BlockPos u = ground.below(k);
				if (lv.isOutsideBuildHeight(u)) break; // thin superflat floors end at the world's bottom, which is not a roof
				if (lv.getBlockState(u).isAir()) return null;
			}
			int flags = 0;
			for (Direction d : Direction.Plane.HORIZONTAL)
				for (int r = 1; r <= 2 && flags == 0; r++)
					if (lv.isLoaded(feet.relative(d, r)) && lv.getBlockState(feet.relative(d, r)).is(BlockTags.DOORS)) flags |= DOOR; // never load a neighbour chunk (s-review-kingdom)
			return new int[] {x, fy, z, flags};
		}

		/** The straight line from a to b: every block column dry, height steps of at most 1. */
		private static boolean walkable(ServerLevel lv, int[] a, int[] b) {
			double dx = b[0] - a[0], dz = b[2] - a[2];
			int steps = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
			int prevY = a[1];
			for (int s = 1; s <= steps; s++) {
				int px = (int) Math.floor(a[0] + 0.5 + dx * s / steps), pz = (int) Math.floor(a[2] + 0.5 + dz * s / steps);
				int py = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
				if (Math.abs(py - prevY) > 1) return false;
				if (!lv.getFluidState(new BlockPos(px, py - 1, pz)).isEmpty() || !lv.getFluidState(new BlockPos(px, py, pz)).isEmpty()) return false;
				prevY = py;
			}
			return Math.abs(prevY - b[1]) <= 1;
		}
	}
}
