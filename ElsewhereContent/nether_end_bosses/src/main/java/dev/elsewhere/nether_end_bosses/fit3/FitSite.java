package dev.elsewhere.nether_end_bosses.fit3;

import java.util.function.BiFunction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v2 site choice for the five arenas (STRUCTURES.md 2.4 and 4). Reads only noise columns (never the live world), so a site
 * depends on the seed alone and the structure start and its {@code /place} twin agree. Nether: every open shelf between y 100
 * and the bottom is scored instead of taking the first from a random top; the ground-within-16 rule of 3.0 stays.
 * <ul>
 * <li>Grunthar: open lava seas are rejected (lava right under 3+ footprint samples); a shelf whose ring is buried in rock at
 * mid-height (a cliff) with an open front to the south scores highest.</li>
 * <li>Solaxis: wet cores are rejected (lava within 4 under the core); a mesa (open ring, ground close under it) scores.</li>
 * <li>Mournmaw: a concave floor scores (ground at the rim higher than the centre).</li>
 * </ul>
 * End: as 3.0 (real end stone under every sample, spread at most 12); Ossyra is limited to highlands and midlands by its JSON.
 */
public final class FitSite {
	private FitSite() {}

	/** Deepest footing a Nether arena may need under its rim before a sample counts as "deep" (as 3.0). */
	public static final int FILL_CAP = 16;
	public static final int NONE = Integer.MIN_VALUE;

	public static int footRadius(String boss) {
		return switch (boss) {
			case "mournmaw" -> 18;
			case "aphelion" -> 9;
			case "ossyra" -> 8;
			default -> 13;
		};
	}

	static boolean solid(BlockState st) {
		return !st.isAir() && st.getFluidState().isEmpty();
	}

	/** Floor y (first air above the floor) for an arena centred at x/z, or {@link #NONE}. */
	public static int pick(String boss, int x, int z, BiFunction<Integer, Integer, NoiseColumn> column, int minY, int maxY, RandomSource rnd) {
		boolean end = boss.equals("ossyra") || boss.equals("aphelion");
		int r = footRadius(boss);
		int n = 17;
		NoiseColumn[] cols = new NoiseColumn[n + 2];
		for (int i = 0; i < n; i++) {
			int sx = x, sz = z;
			if (i > 0) {
				double a = ((i - 1) % 8) * Math.PI / 4;
				double rr = i <= 8 ? r : r * 0.5;
				sx = x + (int) Math.round(Math.cos(a) * rr);
				sz = z + (int) Math.round(Math.sin(a) * rr);
			}
			cols[i] = column.apply(sx, sz);
		}
		if (end) return endFloor(boss, cols, n, maxY);
		// extra samples: the gate front (Grunthar, south) or the two bridge ends (Solaxis)
		cols[n] = column.apply(x, z + r + 8);
		cols[n + 1] = column.apply(x, z - r - 8);
		int tie = rnd.nextInt(4096);
		int top = Math.min(100, maxY - 30), bottom = boss.equals("mournmaw") ? 33 : 40;
		int best = NONE;
		long bestScore = Long.MIN_VALUE;
		for (int yy = top; yy >= bottom; yy--) {
			BlockState floor = cols[0].getBlock(yy - 1);
			if (!(cols[0].getBlock(yy).isAir() && cols[0].getBlock(yy + 1).isAir() && cols[0].getBlock(yy + 2).isAir() && solid(floor))) continue;
			int deep = 0, lavaUnder = 0, wetCore = 0;
			for (int i = 1; i < n; i++) {
				// as 3.0: ground (fluid does not count) within FILL_CAP below the foundation; v2 also counts lava on the way
				int d = 0;
				boolean ground = false, lava = false;
				for (int py = yy - 3; py >= minY && d <= FILL_CAP; py--, d++) {
					BlockState st = cols[i].getBlock(py);
					if (!st.getFluidState().isEmpty()) lava = true;
					else if (!st.isAir()) { ground = true; break; }
				}
				if (lava) lavaUnder++;
				if (!ground) deep++;
			}
			for (int i : new int[] {0, 9, 10, 11, 12, 13, 14, 15, 16}) {
				for (int py = yy - 1; py >= yy - 5; py--) {
					BlockState st = cols[i].getBlock(py);
					if (!st.getFluidState().isEmpty()) { wetCore++; break; }
					if (solid(st) && py < yy - 2) break;
				}
			}
			if (deep > (boss.equals("mournmaw") ? 1 : 2)) continue; // Mournmaw's wide basin: at most one ring sample without ground in reach
			if (boss.equals("solaxis") && wetCore > 0) continue; // "reject wet cores"
			if (boss.equals("grunthar") && (lavaUnder >= 3 || wetCore > 0)) continue; // "open lava seas rejected"
			int score = 0;
			switch (boss) {
				case "grunthar" -> {
					for (int i = 1; i <= 8; i++) if (solid(cols[i].getBlock(yy + 6))) score += 3; // buried ring: a cliff
					if (cols[n].getBlock(yy).isAir() && cols[n].getBlock(yy + 1).isAir()) score += 4; // open front
				}
				case "solaxis" -> {
					for (int i = 1; i <= 8; i++) if (cols[i].getBlock(yy + 1).isAir() && cols[i].getBlock(yy + 4).isAir()) score += 2; // open drum
					for (int k = n; k <= n + 1; k++) if (solid(cols[k].getBlock(yy - 2)) || solid(cols[k].getBlock(yy - 4))) score += 3; // bridges land
				}
				case "mournmaw" -> {
					for (int i = 1; i <= 8; i++) if (solid(cols[i].getBlock(yy + 1))) score += 2; // concave: the rim is higher
					for (int i = 9; i <= 16; i++) if (cols[i].getBlock(yy + 1).isAir()) score += 1; // open floor inside
				}
				default -> { }
			}
			score -= deep * 4 + lavaUnder * 2;
			long s = ((long) score << 13) + ((yy * 131L + tie) & 4095);
			if (s > bestScore) {
				bestScore = s;
				best = yy;
			}
		}
		return best;
	}

	private static int endFloor(String boss, NoiseColumn[] cols, int n, int maxY) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, voids = 0;
		for (int i = 0; i < n; i++) {
			int top = -1;
			for (int yy = 120; yy >= 20; yy--) {
				if (solid(cols[i].getBlock(yy))) { top = yy; break; }
			}
			if (top < 0) { voids++; continue; }
			lo = Math.min(lo, top);
			hi = Math.max(hi, top);
		}
		// Aphelion's halo floats over the island edge with even a few void samples (6-8% of floor columns over void): none allowed
		if (voids > (boss.equals("aphelion") ? 0 : 3) || hi == Integer.MIN_VALUE || hi - lo > 12) return NONE;
		int y = boss.equals("ossyra") ? hi + 27 : hi + 1;
		return y + 24 > maxY ? NONE : y;
	}
}
