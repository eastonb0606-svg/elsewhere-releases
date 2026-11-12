package dev.elsewhere.verdigris_ironpeak.world.fit;

import java.util.function.IntBinaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 3.0 per-piece ground model (STRUCTURES.md 2.3): the generator's ground and water surface sampled on a 4-block lattice around a
 * start when its pieces are created, then interpolated per block. It is saved in the piece NBT, so every chunk of the structure
 * reads the same ground no matter which chunk generates first, and nothing reads the live world outside the chunk being written.
 * Heights use the "first occupied" convention: the y of the top ground block ({@code OCEAN_FLOOR_WG}) and of the top water block
 * ({@code WORLD_SURFACE_WG}, equal to the ground on dry land).
 */
public final class Terrain {
	public static final int STEP = 4;
	/** World x/z of lattice point (0, 0), and the lattice size per axis. */
	final int x0, z0, n;
	final int[] ground, water;

	Terrain(int x0, int z0, int n, int[] ground, int[] water) {
		this.x0 = x0;
		this.z0 = z0;
		this.n = n;
		this.ground = ground;
		this.water = water;
	}

	/** Lattice centred on (cx, cz) covering at least {@code radius} blocks each way. */
	public static Terrain sample(Structure.GenerationContext ctx, int cx, int cz, int radius, boolean withWater) {
		int h = (radius + STEP - 1) / STEP, n = 2 * h + 1;
		int x0 = cx - h * STEP, z0 = cz - h * STEP;
		int[] g = new int[n * n], w = new int[n * n];
		var gen = ctx.chunkGenerator();
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
			int x = x0 + i * STEP, z = z0 + j * STEP;
			g[i * n + j] = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
			w[i * n + j] = withWater ? Math.max(g[i * n + j], gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState())) : g[i * n + j];
		}
		return new Terrain(x0, z0, n, g, w);
	}

	/** A synthetic ground model (self-tests and dev builds): {@code fn(x, z)} gives the top ground block, {@code sea} the water top. */
	public static Terrain synthetic(int cx, int cz, int radius, IntBinaryOperator fn, int sea) {
		int h = (radius + STEP - 1) / STEP, n = 2 * h + 1;
		int x0 = cx - h * STEP, z0 = cz - h * STEP;
		int[] g = new int[n * n], w = new int[n * n];
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
			g[i * n + j] = fn.applyAsInt(x0 + i * STEP, z0 + j * STEP);
			w[i * n + j] = Math.max(g[i * n + j], sea);
		}
		return new Terrain(x0, z0, n, g, w);
	}

	/** The live world's ground (dev builds and summons outside world generation), read once before anything is built. */
	public static Terrain live(net.minecraft.world.level.LevelReader level, int cx, int cz, int radius) {
		int h = (radius + STEP - 1) / STEP, n = 2 * h + 1;
		int x0 = cx - h * STEP, z0 = cz - h * STEP;
		int[] g = new int[n * n], w = new int[n * n];
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
			int x = x0 + i * STEP, z = z0 + j * STEP;
			g[i * n + j] = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
			w[i * n + j] = Math.max(g[i * n + j], level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
		}
		return new Terrain(x0, z0, n, g, w);
	}

	public int ground(int x, int z) {
		return lerp(ground, x, z);
	}

	public int water(int x, int z) {
		return Math.max(ground(x, z), lerp(water, x, z));
	}

	public boolean wet(int x, int z) {
		return water(x, z) > ground(x, z);
	}

	public int maxGround() {
		int m = Integer.MIN_VALUE;
		for (int v : ground) m = Math.max(m, v);
		return m;
	}

	public int minGround() {
		int m = Integer.MAX_VALUE;
		for (int v : ground) m = Math.min(m, v);
		return m;
	}

	/** Highest lattice point within {@code r} blocks of (cx, cz), as {x, z, y}. */
	public int[] highest(int cx, int cz, int r) {
		int[] best = {cx, cz, Integer.MIN_VALUE};
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
			int x = x0 + i * STEP, z = z0 + j * STEP;
			if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r) continue;
			if (ground[i * n + j] > best[2]) best = new int[] {x, z, ground[i * n + j]};
		}
		return best;
	}

	private int lerp(int[] a, int x, int z) {
		if (n < 2) return a[0];
		double fx = Math.max(0, Math.min(n - 1, (x - x0) / (double) STEP)), fz = Math.max(0, Math.min(n - 1, (z - z0) / (double) STEP));
		int i = Math.min(n - 2, (int) Math.floor(fx)), j = Math.min(n - 2, (int) Math.floor(fz));
		double tx = fx - i, tz = fz - j;
		double v = a[i * n + j] * (1 - tx) * (1 - tz) + a[(i + 1) * n + j] * tx * (1 - tz) + a[i * n + j + 1] * (1 - tx) * tz + a[(i + 1) * n + j + 1] * tx * tz;
		return (int) Math.round(v);
	}

	public void save(CompoundTag tag) {
		tag.putIntArray("hfInfo", new int[] {x0, z0, n});
		tag.putIntArray("hfGround", ground);
		tag.putIntArray("hfWater", water);
	}

	public static Terrain load(CompoundTag tag) {
		int[] info = tag.getIntArray("hfInfo").orElse(new int[0]);
		int[] g = tag.getIntArray("hfGround").orElse(new int[0]);
		int[] w = tag.getIntArray("hfWater").orElse(new int[0]);
		if (info.length != 3 || info[2] < 1 || g.length != info[2] * info[2] || w.length != g.length) return null;
		return new Terrain(info[0], info[1], info[2], g, w);
	}
}
