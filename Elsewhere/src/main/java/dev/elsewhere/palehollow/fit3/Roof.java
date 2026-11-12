package dev.elsewhere.palehollow.fit3;

import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pitched gable roof in local plan coordinates (STRUCTURES.md 7: eaves are stairs, overhang 1, steep pitch, ridge a full block).
 * {@code alongZ}: the ridge runs along z (the roof slopes across x). {@code rise} is blocks of rise per block across (2 = steep).
 */
public record Roof(boolean alongZ, int center, int halfSpan, int eaveY, int rise, int from, int to) {
	public static final int NONE = Integer.MIN_VALUE;

	public int y(int lx, int lz) {
		int a = alongZ ? lx : lz, b = alongZ ? lz : lx;
		int d = Math.abs(a - center);
		if (b < from || b > to || d > halfSpan) return NONE;
		return eaveY + rise * (halfSpan - d);
	}

	/** Signed distance across the ridge. */
	public int across(int lx, int lz) {
		return (alongZ ? lx : lz) - center;
	}

	/** The highest roof over a column, or null. */
	public static Roof top(List<Roof> roofs, int lx, int lz) {
		Roof best = null;
		int by = NONE;
		for (Roof r : roofs) {
			int y = r.y(lx, lz);
			if (y > by) {
				by = y;
				best = r;
			}
		}
		return best;
	}

	/**
	 * Places the roof over one column: the topmost of {@code roofs} wins, so crossing roofs meet in clean valleys. Skips columns for which
	 * {@code hole} is true (a fallen-in section). Plants and leaves under the roof inside {@code underTo} are soft-cleared.
	 */
	public static void column(W w, List<Roof> roofs, int lx, int lz, Block stairs, BlockState full, BiPredicate<Integer, Integer> hole) {
		Roof r = top(roofs, lx, lz);
		if (r == null) return;
		int y = r.y(lx, lz);
		if (hole != null && hole.test(lx, lz)) return;
		int d = r.across(lx, lz);
		if (d == 0) {
			w.set(lx, y, lz, full);
			if (r.rise == 2) w.set(lx, y - 1, lz, full);
			return;
		}
		Direction face = r.alongZ ? (d > 0 ? Direction.WEST : Direction.EAST) : (d > 0 ? Direction.NORTH : Direction.SOUTH);
		w.set(lx, y, lz, w.stair(stairs, face, false));
		if (r.rise == 2 && Math.abs(d) < r.halfSpan) w.set(lx, y - 1, lz, full);
	}

	/** Fills a gable end (the triangle between the wall top and the roof) at along-coordinate {@code b}. */
	public void gable(W w, int b, int wallTop, java.util.function.IntFunction<BlockState> block) {
		for (int a = center - halfSpan + 1; a <= center + halfSpan - 1; a++) {
			int lx = alongZ ? a : b, lz = alongZ ? b : a;
			int ry = y(lx, lz);
			if (ry == NONE) continue;
			int topFill = ry - (rise == 2 ? 2 : 1);
			for (int y = wallTop + 1; y <= topFill; y++) w.set(lx, y, lz, block.apply(y * 64 + a));
		}
	}
}
