package dev.elsewhere.kingdom_court.world;

import dev.elsewhere.kingdom_court.Overhaul3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 faction architecture kit (Ideas/overhaul/STRUCTURES.md sections 7-8). Runs as the parts {@code KIT_CASTLE}, {@code KIT_TOWN_WEST} and
 * {@code KIT_TOWN_EAST}, after the base layout in every chunk, and dresses it per kingdom so no two realms share a skyline:
 * <ul>
 * <li>Aldermark: steep deepslate spires on every tower, a pitched keep roof inside the parapet with four bartizans, a chapel steeple, corbelled
 * and battered curtain walls with a string course;</li>
 * <li>Hrafnholt: very steep spruce roofs with overhang 2, a Jarl's-hall roof over the keep with crossed dragon finials, timber hoardings on the
 * walls and towers, a tiered stave tower on the chapel;</li>
 * <li>Azmar: flat roofs with cavetto cornices and lapis faience bands, a battered pylon pair in front of the keep, an obelisk pair, roof kiosks,
 * palms in the court and an undulating parapet;</li>
 * <li>Mirewick: crimson spires and roofs behind crow-stepped calcite gables with yellow-terracotta gilt, a lantern belfry on the Counting-House keep
 * and purple bands.</li>
 * </ul>
 * Only new realm starts contain these parts, so saved 2.0/2.1 castles finish unchanged. Writes are clipped to the chunk like the base layout. No
 * containers, no ticking blocks, no entities; at most 4 banners per realm part, a few lanterns.
 */
public final class CastleKit {
	private CastleKit() {}

	public enum Kit { ALDERMARK, HRAFNHOLT, AZMAR, MIREWICK }

	/** The kit for the placer's faction (from its castle style; no style = Aldermark). */
	static Kit kitOf(CastleLayout.Placer pl) {
		if (pl.style == null) return Kit.ALDERMARK;
		for (Map.Entry<String, CastleStyles.Style> e : CastleStyles.STYLES.entrySet()) {
			if (e.getValue() != pl.style) continue;
			String k = e.getKey();
			if (k.contains("hrafnholt")) return Kit.HRAFNHOLT;
			if (k.contains("azmar")) return Kit.AZMAR;
			if (k.contains("mirewick")) return Kit.MIREWICK;
		}
		return Kit.ALDERMARK;
	}

	// ---- writer ------------------------------------------------------------------------------------------------------

	/** Local-frame writer; Aldermark (and unknown factions) go through the style map, the rival kits name their blocks. */
	static final class W {
		final CastleLayout.L l;
		final boolean mapped;
		/** Vertical shift of the next writes: 0 on the yard; on fv 2 walls and towers the contour lift of that column (see CastleLayout.wallFit). */
		int dy;
		private int[] grid;

		W(CastleLayout.L l, boolean mapped) {
			this.l = l;
			this.mapped = mapped;
		}

		/** Wall-top lift at a curtain column (0 on the 2.1 path), identical to what WALL built. */
		int lift(int x, int z) {
			if (l.p.ground == null) return 0;
			if (grid == null) grid = CastleLayout.ringLift(l, 30);
			return CastleLayout.liftAt(grid, 30, x, z);
		}

		int gate() {
			if (l.p.ground == null) return 0;
			lift(0, 30);
			return CastleLayout.gateLift(grid);
		}

		/** Ground under an outside column relative to the floor (0 on the 2.1 path). */
		int ground(int x, int z) {
			return l.gy(x, z);
		}

		void s(int x, int y, int z, BlockState st) {
			int wx = l.o.getX() + x, wy = l.o.getY() + y + dy, wz = l.o.getZ() + z;
			if (mapped) l.p.set(wx, wy, wz, st);
			else l.p.setRaw(wx, wy, wz, st);
		}

		void s(int x, int y, int z, Block b) {
			s(x, y, z, b.defaultBlockState());
		}

		boolean solid(int x, int y, int z) {
			int wx = l.o.getX() + x, wy = l.o.getY() + y + dy, wz = l.o.getZ() + z;
			if (!l.p.clip.isInside(wx, wy, wz)) return false;
			BlockState c = l.p.level.getBlockState(new BlockPos(wx, wy, wz));
			return !c.isAir() && c.getFluidState().isEmpty();
		}

		void ifSolid(int x, int y, int z, BlockState st) {
			if (solid(x, y, z)) s(x, y, z, st);
		}

		/** fv 2 underpinning: fills air, fluid and replaceable blocks under (x, y, z) down to solid ground (at most 48), so a batter never has air under it. */
		void under(int x, int y, int z, BlockState st) {
			for (int k = 1; k <= 48; k++) {
				int wx = l.o.getX() + x, wy = l.o.getY() + y + dy - k, wz = l.o.getZ() + z;
				if (!l.p.clip.isInside(wx, wy, wz)) return;
				BlockState c = l.p.level.getBlockState(new BlockPos(wx, wy, wz));
				if (!c.isAir() && c.getFluidState().isEmpty() && !c.canBeReplaced()) return;
				s(x, y - k, z, st);
			}
		}

		/** True when (x, y0..y1, z) in this chunk holds only air or replaceable non-fluid blocks (no land, no water): carving it trenches nothing. */
		boolean open(int x, int y0, int y1, int z) {
			for (int y = y0; y <= y1; y++) {
				int wx = l.o.getX() + x, wy = l.o.getY() + y + dy, wz = l.o.getZ() + z;
				if (!l.p.clip.isInside(wx, wy, wz)) return false;
				BlockState c = l.p.level.getBlockState(new BlockPos(wx, wy, wz));
				if (!c.getFluidState().isEmpty() || !c.isAir() && !c.canBeReplaced()) return false;
			}
			return true;
		}
	}

	static BlockState stair(Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	static BlockState slab(Block b, SlabType t) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, t);
	}

	interface Skip {
		boolean skip(int x, int z);
	}

	/** Stairs on the ring x1..x2 / z1..z2, each facing the inside (inward = back against the inside), corners {@code corner} (or a stair). */
	static void ring(W w, int x1, int z1, int x2, int z2, int y, Block st, Half half, BlockState corner, Skip skip) {
		for (int x = x1; x <= x2; x++) {
			put(w, x, y, z1, x == x1 || x == x2 ? corner : null, stair(st, Direction.SOUTH, half), skip);
			put(w, x, y, z2, x == x1 || x == x2 ? corner : null, stair(st, Direction.NORTH, half), skip);
		}
		for (int z = z1 + 1; z <= z2 - 1; z++) {
			put(w, x1, y, z, null, stair(st, Direction.EAST, half), skip);
			put(w, x2, y, z, null, stair(st, Direction.WEST, half), skip);
		}
	}

	private static void put(W w, int x, int y, int z, BlockState corner, BlockState st, Skip skip) {
		if (skip != null && skip.skip(x, z)) return;
		w.s(x, y, z, corner != null ? corner : st);
	}

	/** A full-block ring. */
	static void ringFull(W w, int x1, int z1, int x2, int z2, int y, BlockState b, Skip skip) {
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
			if (x != x1 && x != x2 && z != z1 && z != z2) continue;
			if (skip != null && skip.skip(x, z)) continue;
			w.s(x, y, z, b);
		}
	}

	/** Removes the alternating crenels of a flat tower/keep top (the spire or roof replaces them). */
	static void clearCrenels(W w, int x1, int z1, int x2, int z2, int y) {
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
			if (x != x1 && x != x2 && z != z1 && z != z2) continue;
			if (((x + z) & 1) == 0) w.s(x, y, z, Blocks.AIR.defaultBlockState());
		}
	}

	/**
	 * Square spire over a tower: eave ring of stairs at half-size {@code hs}, then rings stepping in. Steep spires rise 2 per step (a full ring then
	 * a stair ring), like real medieval and stave roofs. Returns the y above the apex block.
	 */
	static int spire(W w, int cx, int cz, int hs, int y0, Block st, BlockState full, boolean steep) {
		int y = y0;
		ring(w, cx - hs, cz - hs, cx + hs, cz + hs, y++, st, Half.BOTTOM, full, null);
		for (int s = hs - 1; s >= 1; s--) {
			if (steep) ringFull(w, cx - s, cz - s, cx + s, cz + s, y++, full, null);
			ring(w, cx - s, cz - s, cx + s, cz + s, y++, st, Half.BOTTOM, full, null);
		}
		if (steep) w.s(cx, y++, cz, full);
		w.s(cx, y++, cz, full);
		return y;
	}

	/**
	 * Gable roof over the rectangle x1..x2 / z1..z2 (outer wall lines), eaves at {@code y0}, ridge along x ({@code ridgeX}) or z. With {@code stepped}
	 * the end walls rise as crow-stepped gables (Dutch) above the roof and the roof stays between them; otherwise the ends are filled with
	 * {@code gableFill} (null = open) and the roof overhangs by {@code over}. Returns the ridge y.
	 */
	static int gable(W w, int x1, int z1, int x2, int z2, int y0, int over, boolean ridgeX, Block st, BlockState ridge, BlockState gableFill,
					 boolean stepped, BlockState stepCap) {
		int u1 = ridgeX ? x1 : z1, u2 = ridgeX ? x2 : z2, v1 = ridgeX ? z1 : x1, v2 = ridgeX ? z2 : x2;
		int ua = stepped ? u1 + 1 : u1 - over, ub = stepped ? u2 - 1 : u2 + over, ov = stepped ? 0 : over;
		Direction upLo = ridgeX ? Direction.SOUTH : Direction.EAST, upHi = ridgeX ? Direction.NORTH : Direction.WEST;
		int top = y0;
		for (int k = 0; k < 40; k++) {
			int lo = v1 - ov + k, hi = v2 + ov - k, y = y0 + k;
			if (lo > hi) break;
			top = y;
			for (int u = ua; u <= ub; u++) {
				if (lo == hi) uv(w, ridgeX, u, y, lo, ridge);
				else {
					uv(w, ridgeX, u, y, lo, stair(st, upLo, Half.BOTTOM));
					uv(w, ridgeX, u, y, hi, stair(st, upHi, Half.BOTTOM));
				}
			}
			if (!stepped && gableFill != null) for (int v = Math.max(lo + 1, v1); v <= Math.min(hi - 1, v2); v++) {
				uv(w, ridgeX, u1, y, v, gableFill);
				uv(w, ridgeX, u2, y, v, gableFill);
			}
			if (lo >= hi) break;
		}
		if (stepped && gableFill != null) {
			int hw0 = (v2 - v1) / 2, vc = v1 + hw0, y = y0;
			for (int r = 0; r < 40; r++) {
				int hw = hw0 - 2 * (r / 2);
				if (hw < 0) break;
				y = y0 + r;
				for (int v = vc - hw; v <= vc + hw; v++) {
					BlockState b = r % 2 == 1 && Math.abs(v - vc) == hw && stepCap != null ? stepCap : gableFill;
					uv(w, ridgeX, u1, y, v, b);
					uv(w, ridgeX, u2, y, v, b);
				}
			}
			if (stepCap != null) {
				uv(w, ridgeX, u1, y + 1, vc, stepCap);
				uv(w, ridgeX, u2, y + 1, vc, stepCap);
			}
			top = Math.max(top, y + 1);
		}
		return top;
	}

	private static void uv(W w, boolean ridgeX, int u, int y, int v, BlockState s) {
		if (ridgeX) w.s(u, y, v, s);
		else w.s(v, y, u, s);
	}

	// ---- the castle --------------------------------------------------------------------------------------------------

	static final int[][] CORNERS = {{-30, -30}, {30, -30}, {-30, 30}, {30, 30}};

	/** Curtain-wall line at distance m from the centre, without the corner towers and the gatehouse. */
	static boolean offWall(int x, int z) {
		return Math.abs(x) >= 26 && Math.abs(z) >= 26 || z >= 29 && Math.abs(x) <= 7;
	}

	public static void castle(CastleLayout.L l) {
		// no valuable blocks: every Placer write already goes through Valuables.safe (s-throne B9), so the old per-chunk gold scan is gone
		if (!Overhaul3.CASTLE_KIT) return;
		Kit k = kitOf(l.p);
		W w = new W(l, k == Kit.ALDERMARK);
		switch (k) {
			case ALDERMARK -> aldermark(w);
			case HRAFNHOLT -> hrafnholt(w);
			case AZMAR -> azmar(w);
			case MIREWICK -> mirewick(w);
		}
	}

	/** Visits the outer line (m) of the curtain wall: (x, z, inward direction, position along the ring). */
	interface WallCell {
		void at(int x, int z, Direction in, int t);
	}

	/** Like wallLine, with the writer raised to the contour lift of each column (fv 2) and reset afterwards. */
	static void wallTop(W w, int m, WallCell c) {
		wallLine(m, (x, z, in, t) -> {
			w.dy = w.lift(x, z);
			c.at(x, z, in, t);
		});
		w.dy = 0;
	}

	/** Batter cells sit on the outside ground of their own column (fv 2), underpinned with {@code under} so no batter has air under it. */
	static void wallFoot(W w, int m, BlockState under, WallCell c) {
		wallLine(m, (x, z, in, t) -> {
			w.dy = w.ground(x, z);
			c.at(x, z, in, t);
			if (w.l.p.ground != null) w.under(x, 0, z, under);
		});
		w.dy = 0;
	}

	/**
	 * fv 2 Azmar tower batter (z-castle): the ring at 5 around each corner tower as local cells {x, y (floor-relative), z, corner 1/0, stair
	 * facing (3D data value)}. Like the curtain batter, every cell sits on the ground of its own column; yard cells sit on the yard floor and
	 * cells on the curtain itself are skipped. Deterministic (ground only); the self-test checks the same cells.
	 */
	public static List<int[]> towerBatter(BlockPos o, CastleLayout.Ground g) {
		List<int[]> out = new ArrayList<>();
		for (int[] c : CORNERS) for (int x = c[0] - 5; x <= c[0] + 5; x++) for (int z = c[1] - 5; z <= c[1] + 5; z++) {
			int ax = Math.abs(x - c[0]), az = Math.abs(z - c[1]);
			if (Math.max(ax, az) != 5) continue;
			int m = Math.max(Math.abs(x), Math.abs(z));
			if (m == 29 || m == 30) continue;
			int y = m <= 28 ? 0 : g.y(o.getX() + x, o.getZ() + z) - o.getY();
			Direction in = ax == 5 ? (x < c[0] ? Direction.EAST : Direction.WEST) : z < c[1] ? Direction.SOUTH : Direction.NORTH;
			out.add(new int[] {x, y, z, ax == 5 && az == 5 ? 1 : 0, in.get3DDataValue()});
		}
		return out;
	}

	static void towerBatterFit(W w, Block st, BlockState corner) {
		for (int[] b : towerBatter(w.l.o, w.l.p.ground)) {
			w.dy = b[1];
			w.s(b[0], 0, b[2], b[3] == 1 ? corner : stair(st, Direction.from3DDataValue(b[4]), Half.BOTTOM));
			w.under(b[0], 0, b[2], corner);
		}
		w.dy = 0;
	}

	/** fv 2 Mirewick canal plan: world y of the top water block (sea level - 1), world y of the quay stone, water per column x (-16..16). */
	public record Canal(int water, int quay, boolean[] wet) {
		public boolean at(int x) {
			return x >= -16 && x <= 16 && wet[x + 16];
		}

		public int columns() {
			int n = 0;
			for (boolean b : wet) if (b) n++;
			return n;
		}
	}

	/**
	 * z-castle: where the fv 2 Mirewick canal goes, from generator ground only (deterministic for every chunk). The quay must have been placed
	 * (fitPieces did not skip it) with its stone 0 to 2 above sea-level water. A canal column (x, both z 35 and 36) holds water only where
	 * the natural ground there is at or below the water level (first air at most sea level), so the canal never trenches land above the
	 * water; runs shorter than 3 columns stay land. Null = no canal.
	 */
	public static @Nullable Canal canal(BlockPos o, CastleLayout.@Nullable Ground g, CastleStyles.@Nullable Style style) {
		if (!(g instanceof RealmFit.HeightField hf) || style == null) return null;
		int dy = CastleLayout.pieceLift(style, o, g, 0, 32);
		if (dy == Integer.MIN_VALUE) return null;
		int sea = hf.gen.getSeaLevel(), water = sea - 1, quay = o.getY() + dy;
		if (quay < water || quay > water + 2) return null;
		boolean[] wet = new boolean[33];
		for (int x = 4; x <= 16; x++) for (int s = -1; s <= 1; s += 2)
			wet[s * x + 16] = g.y(o.getX() + s * x, o.getZ() + 35) <= sea && g.y(o.getX() + s * x, o.getZ() + 36) <= sea;
		for (int s = -1; s <= 1; s += 2) {
			int x = 4;
			while (x <= 16) {
				if (!wet[s * x + 16]) {
					x++;
					continue;
				}
				int e = x;
				while (e < 16 && wet[s * (e + 1) + 16]) e++;
				if (e - x + 1 < 3) for (int k = x; k <= e; k++) wet[s * k + 16] = false;
				x = e + 1;
			}
		}
		Canal c = new Canal(water, quay, wet);
		return c.columns() > 0 ? c : null;
	}

	/**
	 * Builds the fv 2 canal of {@link #canal}: a contained bed in front of the quay. Water columns get a stone-brick bed at water - 2, water at
	 * water - 1 and water, and air above up to the quay + 1; the quay side (z 34), the outer side (z 37) and both run ends are quartz from the
	 * bed to the water level, so every water block touches only canal water or its own walls. Nothing above the water level is removed: a
	 * column whose blocks above the water are not open air in this chunk (say, land the yard beard raised) is walled instead of carved.
	 */
	static void canalFit(W w, BlockState quartz, BlockState bricks) {
		Canal c = canal(w.l.o, w.l.p.ground, w.l.p.style);
		if (c == null) return;
		int wt = c.water() - w.l.o.getY(), q = c.quay() - w.l.o.getY();
		BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		w.dy = 0;
		for (int x = -17; x <= 17; x++) {
			if (Math.abs(x) <= 2) continue;
			boolean wet = c.at(x);
			if (!wet && !c.at(x - 1) && !c.at(x + 1)) continue;
			for (int y = wt - 2; y <= wt; y++) {
				w.s(x, y, 34, quartz);
				w.s(x, y, 37, quartz);
			}
			for (int y = wt + 1; y <= q; y++) if (w.open(x, y, y, 37)) w.s(x, y, 37, quartz);
			for (int z = 35; z <= 36; z++) {
				boolean open = wet && w.open(x, wt + 1, q + 1, z);
				w.s(x, wt - 2, z, bricks);
				w.s(x, wt - 1, z, open ? water : quartz);
				w.s(x, wt, z, open ? water : quartz);
				if (open) for (int y = wt + 1; y <= q + 1; y++) w.s(x, y, z, air);
			}
		}
	}

	static void wallLine(int m, WallCell c) {
		for (int t = -m; t <= m; t++) {
			if (!offWall(t, -m)) c.at(t, -m, Direction.SOUTH, t + 100);
			if (!offWall(t, m)) c.at(t, m, Direction.NORTH, t + 300);
			if (!offWall(-m, t)) c.at(-m, t, Direction.EAST, t + 500);
			if (!offWall(m, t)) c.at(m, t, Direction.WEST, t + 700);
		}
	}

	// Aldermark: the baseline crown. Norman curtain wall dressed with corbels, string course and batter; spired towers; a pitched keep roof
	// inside the parapet with four corbelled bartizans; a chapel steeple; pitched timber roofs on the yard buildings.
	static void aldermark(W w) {
		BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState(), bricks = Blocks.STONE_BRICKS.defaultBlockState();
		Block tileStair = Blocks.DEEPSLATE_TILE_STAIRS, brickStair = Blocks.STONE_BRICK_STAIRS;
		for (int[] c : CORNERS) {
			w.dy = w.lift(c[0], c[1]);
			clearCrenels(w, c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, 14);
			ring(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 13, brickStair, Half.TOP, bricks, null);
			int top = spire(w, c[0], c[1], 5, 14, tileStair, tiles, true);
			w.s(c[0], top, c[1], Blocks.SPRUCE_FENCE);
			w.s(c[0], top + 1, c[1], Blocks.BANNER.pick(DyeColor.BLUE));
		}
		w.dy = w.gate();
		for (int s = -1; s <= 1; s += 2) {
			clearCrenels(w, s * 5 - 2, 27, s * 5 + 2, 31, 13);
			ring(w, s * 5 - 3, 26, s * 5 + 3, 32, 12, brickStair, Half.TOP, bricks, null);
			int top = spire(w, s * 5, 29, 3, 13, tileStair, tiles, true);
			w.s(s * 5, top, 29, Blocks.LANTERN);
		}
		w.dy = 0;
		// curtain: batter at the foot, string course, corbelled projecting parapet
		wallFoot(w, 31, bricks, (x, z, in, t) -> w.s(x, 0, z, stair(brickStair, in, Half.BOTTOM)));
		wallTop(w, 31, (x, z, in, t) -> {
			w.s(x, 8, z, stair(brickStair, in, Half.TOP));
			w.s(x, 9, z, CastleLayout.wallBlock(x, 9, z));
			if (((x + z) & 1) == 0) w.s(x, 10, z, CastleLayout.wallBlock(x, 10, z));
		});
		BlockState course = Blocks.POLISHED_ANDESITE.defaultBlockState();
		wallTop(w, 30, (x, z, in, t) -> w.ifSolid(x, 5, z, course));
		// keep: pitched deepslate roof inside the parapet, gable ends in brick, four bartizans
		gable(w, -10, -25, 10, -9, 19, 0, true, tileStair, tiles, bricks, false, null);
		for (int sx = -1; sx <= 1; sx += 2) for (int sz : new int[] {-27, -7}) {
			int cx = sx * 12;
			w.s(cx, 15, sz, bricks);
			ring(w, cx - 1, sz - 1, cx + 1, sz + 1, 15, brickStair, Half.TOP, bricks, null);
			for (int y = 16; y <= 20; y++) ringFull(w, cx - 1, sz - 1, cx + 1, sz + 1, y, bricks, null);
			w.s(cx + sx, 18, sz, Blocks.AIR.defaultBlockState());
			int top = spire(w, cx, sz, 2, 21, tileStair, tiles, true);
			w.s(cx, top, sz, Blocks.SPRUCE_FENCE);
		}
		// yard buildings: pitched roofs (timber gables over stone)
		BlockState spruce = Blocks.SPRUCE_PLANKS.defaultBlockState();
		gable(w, -27, -4, -17, 12, 5, 1, false, Blocks.SPRUCE_STAIRS, spruce, spruce, false, null);
		gable(w, 17, -4, 27, 8, 5, 1, false, tileStair, tiles, bricks, false, null);
		gable(w, -27, 16, -17, 26, 4, 1, false, Blocks.SPRUCE_STAIRS, spruce, null, false, null);
		// chapel: steep nave roof and a west steeple
		BlockState calcite = Blocks.CALCITE.defaultBlockState();
		gable(w, 15, -27, 27, -15, 8, 1, true, tileStair, tiles, calcite, false, null);
		for (int y = 8; y <= 17; y++) ringFull(w, 15, -22, 17, -20, y, calcite, null);
		for (int y = 15; y <= 16; y++) {
			w.s(16, y, -22, Blocks.AIR.defaultBlockState());
			w.s(16, y, -20, Blocks.AIR.defaultBlockState());
			w.s(15, y, -21, Blocks.AIR.defaultBlockState());
			w.s(17, y, -21, Blocks.AIR.defaultBlockState());
		}
		int top = spire(w, 16, -21, 2, 18, tileStair, tiles, true);
		w.s(16, top, -21, Blocks.SPRUCE_FENCE);
		w.s(16, top + 1, -21, Blocks.LANTERN);
	}

	// Hrafnholt: Viking timber. Hoardings, very steep spruce roofs with overhang 2, the Jarl's-hall roof over the keep with crossed dragon finials,
	// a tiered stave tower on the hof (chapel).
	static void hrafnholt(W w) {
		Block st = Blocks.SPRUCE_STAIRS;
		BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState(), plank = Blocks.SPRUCE_PLANKS.defaultBlockState(),
			fence = Blocks.SPRUCE_FENCE.defaultBlockState(), dfence = Blocks.DARK_OAK_FENCE.defaultBlockState(), log = Blocks.DARK_OAK_LOG.defaultBlockState();
		int i = 0;
		for (int[] c : CORNERS) {
			w.dy = w.lift(c[0], c[1]);
			clearCrenels(w, c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, 14);
			ring(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 11, st, Half.TOP, dark, null);
			ringFull(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 12, plank, null);
			ringFull(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 13, fence, null);
			int top = spire(w, c[0], c[1], 6, 14, st, dark, true);
			w.s(c[0], top, c[1], dfence);
			w.s(c[0], top + 1, c[1], Blocks.BANNER.pick((i++ & 1) == 0 ? DyeColor.GREEN : DyeColor.BROWN));
		}
		w.dy = w.gate();
		for (int s = -1; s <= 1; s += 2) {
			clearCrenels(w, s * 5 - 2, 27, s * 5 + 2, 31, 13);
			int top = spire(w, s * 5, 29, 4, 13, st, dark, true);
			w.s(s * 5, top, 29, dfence);
		}
		w.dy = 0;
		// timber hoardings on the curtain: corbels, a plank gallery, fence posts and a slab roof
		BlockState slabB = slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM);
		wallTop(w, 31, (x, z, in, t) -> {
			w.s(x, 7, z, stair(st, in, Half.TOP));
			w.s(x, 8, z, plank);
			w.s(x, 9, z, (t & 1) == 0 ? log : fence);
			w.s(x, 10, z, (t & 1) == 0 ? log : fence);
			w.s(x, 11, z, slabB);
		});
		wallTop(w, 30, (x, z, in, t) -> w.s(x, 11, z, slabB));
		// the Jarl's hall: one steep roof over the whole keep, overhang 2, ridge along the hall, crossed finials at both ends
		clearCrenels(w, -11, -26, 11, -8, 19);
		int ridge = gable(w, -11, -26, 11, -8, 18, 2, false, st, dark, dark, false, null);
		for (int z : new int[] {-28, -6}) {
			w.s(-1, ridge + 1, z, stair(st, Direction.EAST, Half.BOTTOM));
			w.s(1, ridge + 1, z, stair(st, Direction.WEST, Half.BOTTOM));
			w.s(0, ridge + 1, z, dfence);
			w.s(0, ridge + 2, z, dfence);
			w.s(0, ridge + 3, z, Blocks.LANTERN);
		}
		// longhouse roofs on the yard buildings
		gable(w, -27, -4, -17, 12, 5, 2, false, st, dark, dark, false, null);
		gable(w, 17, -4, 27, 8, 5, 2, false, st, dark, dark, false, null);
		gable(w, -27, 16, -17, 26, 4, 1, false, st, dark, null, false, null);
		// the hof: nave roof, a skirt roof and a stave tower above the crossing
		int r = gable(w, 15, -27, 27, -15, 8, 2, true, st, dark, dark, false, null);
		for (int y = 8; y <= r + 3; y++) ringFull(w, 20, -22, 22, -20, y, dark, null);
		ring(w, 19, -23, 23, -19, r + 1, st, Half.BOTTOM, dark, null);
		int top = spire(w, 21, -21, 2, r + 4, st, dark, true);
		w.s(21, top, -21, dfence);
		w.s(21, top + 1, -21, Blocks.LANTERN);
	}

	// Azmar: ancient-Egyptian mythic. Flat roofs with cavetto cornices and lapis faience, a battered pylon pair before the keep, obelisks, kiosks,
	// palms and an undulating parapet. No pitched roofs, no glass, no gold blocks (gold is yellow terracotta).
	static void azmar(W w) {
		BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState(), sand = Blocks.SANDSTONE.defaultBlockState(), smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
			chis = Blocks.CHISELED_SANDSTONE.defaultBlockState(), lapis = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE).defaultBlockState(),
			gold = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState(), turq = Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN).defaultBlockState();
		Block st = Blocks.SANDSTONE_STAIRS;
		BlockState slabTop = slab(Blocks.SMOOTH_SANDSTONE_SLAB, SlabType.BOTTOM);
		for (int[] c : CORNERS) {
			w.dy = w.lift(c[0], c[1]);
			clearCrenels(w, c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, 14);
			for (int x = c[0] - 4; x <= c[0] + 4; x++) for (int z = c[1] - 4; z <= c[1] + 4; z++)
				if (Math.max(Math.abs(x - c[0]), Math.abs(z - c[1])) == 4) w.ifSolid(x, 11, z, lapis);
			ring(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 13, st, Half.TOP, cut, null);
			ringFull(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 14, cut, null);
			ringFull(w, c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, 14, cut, null);
			if (w.l.p.ground == null) ring(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 0, st, Half.BOTTOM, sand, null); // fv 2: towerBatterFit below
			kiosk(w, c[0], c[1], 14, cut, slabTop, gold);
		}
		w.dy = 0;
		if (w.l.p.ground != null) towerBatterFit(w, st, sand); // fv 2: each batter cell on its own ground, underpinned
		// pylon gate before the keep: two battered trapezoid towers, cavetto cornice, faience band, lapis banners
		for (int s = -1; s <= 1; s += 2) {
			for (int y = 0; y <= 18; y++) {
				int outer = 11 - y / 5;
				for (int a = 4; a <= outer; a++) for (int z = -7; z <= -4; z++) {
					int x = s * a;
					boolean face = z == -4 || a == outer;
					w.s(x, y, z, y == 14 && face ? lapis : face && ((a + y) % 5 == 0) ? cut : sand);
				}
			}
			for (int a = 4; a <= 8; a++) {
				w.s(s * a, 18, -3, stair(st, Direction.NORTH, Half.TOP));
				w.s(s * a, 19, -3, cut);
				for (int z = -7; z <= -4; z++) w.s(s * a, 19, z, cut);
			}
			w.s(s * 7, 11, -3, Blocks.WALL_BANNER.pick(DyeColor.BLUE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
			obelisk(w, s * 13, -2, smooth, chis, gold, cut);
		}
		// keep: faience band, cavetto cornice, solid parapet, a roof kiosk
		for (int x = -11; x <= 11; x++) for (int z = -26; z <= -8; z++)
			if (x == -11 || x == 11 || z == -26 || z == -8) w.ifSolid(x, 15, z, lapis);
		ring(w, -12, -27, 12, -7, 17, st, Half.TOP, cut, null);
		ringFull(w, -12, -27, 12, -7, 18, cut, null);
		ringFull(w, -11, -26, 11, -8, 19, cut, null);
		kiosk(w, 0, -17, 19, cut, slabTop, gold);
		// walls: faience band and an undulating parapet (pan-bedded courses)
		wallTop(w, 30, (x, z, in, t) -> {
			w.ifSolid(x, 6, z, lapis);
			w.s(x, 9, z, cut);
			if (((t % 8) + 8) % 8 >= 2 && ((t % 8) + 8) % 8 <= 5) w.s(x, 10, z, cut);
		});
		wallFoot(w, 31, sand, (x, z, in, t) -> w.s(x, 0, z, stair(st, in, Half.BOTTOM)));
		wallTop(w, 31, (x, z, in, t) -> w.s(x, 8, z, stair(st, in, Half.TOP)));
		// yard buildings: cavetto cornices and parapets on the flat roofs, a turquoise band
		cornice(w, -27, -4, -17, 12, 5, st, cut, turq);
		cornice(w, 17, -4, 27, 8, 5, st, cut, turq);
		cornice(w, 15, -27, 27, -15, 8, st, cut, lapis);
		// palm court
		for (int[] p : new int[][] {{-12, 22}, {12, 22}, {-20, -10}, {20, -10}, {-12, 2}, {12, 2}}) palm(w, p[0], p[1]);
	}

	static void kiosk(W w, int cx, int cz, int y0, BlockState pillar, BlockState roof, BlockState finial) {
		for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) for (int y = y0; y <= y0 + 2; y++) w.s(cx + sx, y, cz + sz, pillar);
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) w.s(cx + x, y0 + 3, cz + z, roof);
		w.s(cx, y0 + 4, cz, finial);
	}

	static void cornice(W w, int x1, int z1, int x2, int z2, int y, Block st, BlockState lip, BlockState band) {
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
			if (x == x1 || x == x2 || z == z1 || z == z2) w.ifSolid(x, y - 2, z, band);
		ring(w, x1 - 1, z1 - 1, x2 + 1, z2 + 1, y, st, Half.TOP, lip, null);
		ringFull(w, x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 1, lip, null);
		ringFull(w, x1, z1, x2, z2, y + 1, lip, null);
	}

	/** Obelisk: plinth, a rounded 3-wide shaft with a glyph band, a 1-wide tip and a yellow-terracotta pyramidion. */
	static void obelisk(W w, int cx, int cz, BlockState shaft, BlockState glyph, BlockState tip, BlockState plinth) {
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) w.s(cx + x, 0, cz + z, plinth);
		for (int y = 1; y <= 13; y++) {
			w.s(cx, y, cz, shaft);
			if (y <= 9) for (Direction d : Direction.Plane.HORIZONTAL) w.s(cx + d.getStepX(), y, cz + d.getStepZ(), y == 4 ? glyph : shaft);
		}
		w.s(cx, 14, cz, tip);
	}

	/** Natural terrain (earth, stone, sand, terracotta, snow, foliage): the only blocks a slope skirt may clear or cover, so it never eats a wall. */
	static boolean natural(BlockState c) {
		return c.isAir() || c.canBeReplaced() || c.is(BlockTags.DIRT) || c.is(BlockTags.LEAVES) || c.is(BlockTags.LOGS) || c.is(BlockTags.SAND)
			|| c.is(BlockTags.TERRACOTTA) || c.is(BlockTags.BASE_STONE_OVERWORLD) || c.is(BlockTags.SNOW) || c.is(Blocks.SANDSTONE) || c.is(Blocks.RED_SANDSTONE);
	}

	/**
	 * Slope skirt (3.0 terrain fit): around a levelled pad (floor surface at y -1) the hillside is cut back on a 1:1 slope instead of a sheer box
	 * wall, a low side is raised on the same slope, and natural terrain, leaves and overhanging grass above the skirt are cleared up to {@code clearTo}.
	 * {@code dist} is the distance of a column outside the pad (<= 0 = on the pad, skipped); columns up to {@code reach} out are fitted. Per column
	 * and from the generator ground only, so every chunk agrees; man-made blocks (walls, towers) are never touched.
	 */
	static void skirt(CastleLayout.L l, int ext, java.util.function.IntBinaryOperator dist, int reach, Block top, Block body, int clearTo) {
		var c = l.p.clip;
		int x0 = Math.max(-ext, c.minX() - l.o.getX()), x1 = Math.min(ext, c.maxX() - l.o.getX());
		int z0 = Math.max(-ext, c.minZ() - l.o.getZ()), z1 = Math.min(ext, c.maxZ() - l.o.getZ());
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			int d = dist.applyAsInt(x, z);
			if (d <= 0 || d > reach) continue;
			int g = l.gy(x, z) - 1, tgt = g > -1 ? -1 + d : -1 - d;
			int eff = g > -1 ? Math.min(g, tgt) : Math.max(g, tgt);
			// clear natural blocks above the target surface (hillside, trees, overhanging grass)
			for (int y = eff + 1; y <= Math.max(clearTo, g + 12); y++) {
				BlockPos bp = new BlockPos(l.o.getX() + x, l.o.getY() + y, l.o.getZ() + z);
				if (!c.isInside(bp.getX(), bp.getY(), bp.getZ())) continue;
				BlockState cur = l.p.level.getBlockState(bp);
				if (!cur.isAir() && natural(cur) && cur.getFluidState().isEmpty()) l.p.setRaw(bp.getX(), bp.getY(), bp.getZ(), Blocks.AIR.defaultBlockState());
			}
			if (g > -1 && g > tgt) l.p.setSkirt(l.o.getX() + x, l.o.getY() + tgt, l.o.getZ() + z, top.defaultBlockState());
			else if (g < -1 && g < tgt) {
				for (int y = g + 1; y <= tgt; y++) l.p.setSkirt(l.o.getX() + x, l.o.getY() + y, l.o.getZ() + z, (y == tgt ? top : body).defaultBlockState());
				l.foundation(x, z, x, z, g, body);
			}
		}
	}

	/** Date palm: a leaning jungle trunk and a crown of persistent fronds (never decay). */
	static void palm(W w, int x, int z) {
		BlockState trunk = Blocks.JUNGLE_LOG.defaultBlockState(), leaf = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		int lean = ((x * 31 + z * 17) & 1) == 0 ? 1 : -1;
		for (int y = 0; y <= 6; y++) w.s(x + (y >= 4 ? lean : 0), y, z, trunk);
		int tx = x + lean, ty = 7;
		w.s(tx, ty, z, leaf);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			w.s(tx + d.getStepX(), ty, z + d.getStepZ(), leaf);
			w.s(tx + 2 * d.getStepX(), ty, z + 2 * d.getStepZ(), leaf);
			w.s(tx + 3 * d.getStepX(), ty - 1, z + 3 * d.getStepZ(), leaf);
		}
	}

	// Mirewick: the Lantern Republic. Crimson spires with gilt finials and lanterns, crow-stepped calcite gables over crimson roofs, a lantern belfry
	// on the Counting-House keep, purple bands and banners.
	static void mirewick(W w) {
		Block st = Blocks.CRIMSON_STAIRS;
		BlockState crimson = Blocks.CRIMSON_PLANKS.defaultBlockState(), calcite = Blocks.CALCITE.defaultBlockState(),
			purple = Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE).defaultBlockState(), gold = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState(),
			quartz = Blocks.QUARTZ_BRICKS.defaultBlockState();
		for (int[] c : CORNERS) {
			w.dy = w.lift(c[0], c[1]);
			clearCrenels(w, c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, 14);
			for (int x = c[0] - 4; x <= c[0] + 4; x++) for (int z = c[1] - 4; z <= c[1] + 4; z++)
				if (Math.max(Math.abs(x - c[0]), Math.abs(z - c[1])) == 4) w.ifSolid(x, 11, z, purple);
			ring(w, c[0] - 5, c[1] - 5, c[0] + 5, c[1] + 5, 13, Blocks.QUARTZ_STAIRS, Half.TOP, quartz, null);
			int top = spire(w, c[0], c[1], 5, 14, st, crimson, true);
			w.s(c[0], top, c[1], gold);
			w.s(c[0], top + 1, c[1], Blocks.LANTERN);
		}
		w.dy = w.gate();
		for (int s = -1; s <= 1; s += 2) {
			clearCrenels(w, s * 5 - 2, 27, s * 5 + 2, 31, 13);
			int top = spire(w, s * 5, 29, 3, 13, st, crimson, true);
			w.s(s * 5, top, 29, gold);
		}
		w.dy = 0;
		// the Counting-House: crow-stepped gables north and south, crimson roof between, a lantern belfry, purple banners
		clearCrenels(w, -11, -26, 11, -8, 19);
		int ridge = gable(w, -11, -26, 11, -8, 19, 0, false, st, crimson, calcite, true, gold);
		w.s(0, 24, -26, Blocks.STAINED_GLASS.pick(DyeColor.PURPLE));
		w.s(0, 24, -8, Blocks.STAINED_GLASS.pick(DyeColor.PURPLE));
		int by = Math.min(ridge, 30) - 2;
		for (int y = by; y <= by + 5; y++) {
			ringFull(w, -1, -18, 1, -16, y, calcite, null);
			if (y == by + 3 || y == by + 4) {
				w.s(0, y, -18, Blocks.AIR.defaultBlockState());
				w.s(0, y, -16, Blocks.AIR.defaultBlockState());
				w.s(-1, y, -17, Blocks.AIR.defaultBlockState());
				w.s(1, y, -17, Blocks.AIR.defaultBlockState());
			}
		}
		w.s(0, by + 2, -17, gold);
		w.s(0, by + 3, -17, Blocks.LANTERN);
		ring(w, -2, -19, 2, -15, by + 6, st, Half.BOTTOM, crimson, null);
		int top = spire(w, 0, -17, 1, by + 7, st, crimson, true);
		w.s(0, top, -17, gold);
		w.s(0, top + 1, -17, Blocks.LANTERN);
		for (int s = -1; s <= 1; s += 2)
			w.s(s * 5, 12, -7, Blocks.WALL_BANNER.pick(DyeColor.PURPLE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
		// walls: purple band, gilt merlons, quartz corbels
		wallTop(w, 30, (x, z, in, t) -> {
			w.ifSolid(x, 6, z, purple);
			if (t % 6 == 0 && w.solid(x, 9, z)) w.s(x, 9, z, gold);
		});
		wallTop(w, 31, (x, z, in, t) -> w.s(x, 8, z, stair(Blocks.QUARTZ_STAIRS, in, Half.TOP)));
		// the quay (style pieces: smooth-stone quay, quartz parapet and lantern posts at z 31..34) gets its canal: the parapet is underpinned and
		// water lies in front of it in two basins either side of the gate road. fv 2: canalFit (sea-level water, contained bed, no trenching).
		if (w.l.p.ground != null) canalFit(w, quartz, Blocks.STONE_BRICKS.defaultBlockState());
		else {
			BlockState water = Blocks.WATER.defaultBlockState(), bricks = Blocks.STONE_BRICKS.defaultBlockState();
			for (int x = -17; x <= 17; x++) {
				if (Math.abs(x) <= 2) continue;
				w.s(x, 0, 34, quartz);
				boolean end = Math.abs(x) == 3 || Math.abs(x) == 17;
				for (int z = 35; z <= 36; z++) {
					w.s(x, -2, z, bricks);
					w.s(x, -1, z, end ? quartz : water);
					w.s(x, 0, z, end ? quartz : water);
					w.s(x, 1, z, Blocks.AIR.defaultBlockState());
				}
				for (int y = -2; y <= 0; y++) w.s(x, y, 37, quartz);
			}
		}
		// yard buildings: stepped gables, crimson roofs
		gable(w, -27, -4, -17, 12, 5, 0, false, st, crimson, calcite, true, gold);
		gable(w, 17, -4, 27, 8, 5, 0, false, st, crimson, calcite, true, gold);
		gable(w, 15, -27, 27, -15, 8, 0, true, st, crimson, calcite, true, gold);
		gable(w, -27, 16, -17, 26, 4, 1, false, st, crimson, null, false, null);
	}

	// ---- the two market towns ----------------------------------------------------------------------------------------

	public static void town(CastleLayout.L l) {
		if (!Overhaul3.TOWN_KIT) return;
		Kit k = kitOf(l.p);
		W w = new W(l, k == Kit.ALDERMARK);
		int i = 0;
		for (int[] h : CastleLayout.HOUSES) {
			w.dy = CastleLayout.pad(l, h[0], h[1], h[0] + 6, h[1] + 6); // fv 2: the house's own pad (0 on the 2.1 path)
			house(w, k, h[0], h[1], h[0] + 6, h[1] + 6, i++);
		}
		w.dy = CastleLayout.pad(l, 8, 9, 18, 17);
		house(w, k, 8, 9, 18, 17, i);
		w.dy = 0;
	}

	static void house(W w, Kit k, int x0, int z0, int x1, int z1, int i) {
		boolean doorSouth = z0 >= 0; // the base layout puts the door on the side toward the square
		int dz = doorSouth ? z0 : z1, dx = x0 + (x1 - x0 + 1) / 2;
		switch (k) {
			case ALDERMARK -> {
				BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
				gable(w, x0, z0, x1, z1, 4, 1, (i & 1) == 0, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS.defaultBlockState(), planks, false, null);
				for (int y = 4; y <= 9; y++) w.s(x0 + 1, y, z0 + 1, Blocks.COBBLESTONE);
				w.s(x0 + 1, 10, z0 + 1, Blocks.COBBLESTONE_WALL);
			}
			case HRAFNHOLT -> {
				BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
				boolean rx = (i & 1) == 0;
				int ridge = gable(w, x0, z0, x1, z1, 4, 1, rx, Blocks.SPRUCE_STAIRS, dark, dark, false, null);
				int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
				if (rx) {
					w.s(x0 - 1, ridge + 1, cz, Blocks.DARK_OAK_FENCE);
					w.s(x1 + 1, ridge + 1, cz, Blocks.DARK_OAK_FENCE);
				} else {
					w.s(cx, ridge + 1, z0 - 1, Blocks.DARK_OAK_FENCE);
					w.s(cx, ridge + 1, z1 + 1, Blocks.DARK_OAK_FENCE);
				}
			}
			case AZMAR -> {
				BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
				for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = z0 + 1; z <= z1 - 1; z++) w.s(x, 5, z, air);
				ring(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, 4, Blocks.SANDSTONE_STAIRS, Half.TOP, cut, null);
				ringFull(w, x0, z0, x1, z1, 5, cut, null);
				for (int y = 5; y <= 7; y++) w.s(x1 - 1, y, z0 + 1, cut);
				w.s(x1 - 1, 8, z0 + 1, slab(Blocks.SMOOTH_SANDSTONE_SLAB, SlabType.BOTTOM));
				int out = doorSouth ? z0 - 1 : z1 + 1;
				for (int x = dx - 1; x <= dx + 1; x++) w.s(x, 3, out, Blocks.WOOL.pick((x & 1) == 0 ? DyeColor.BLUE : DyeColor.WHITE));
			}
			case MIREWICK -> {
				BlockState calcite = Blocks.CALCITE.defaultBlockState(), gold = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState();
				int ridge = gable(w, x0, z0, x1, z1, 4, 0, false, Blocks.CRIMSON_STAIRS, Blocks.CRIMSON_PLANKS.defaultBlockState(), calcite, true, gold);
				int out = doorSouth ? z0 - 1 : z1 + 1;
				w.s(dx, ridge - 1, out, Blocks.CRIMSON_FENCE); // hoist beam
			}
		}
	}
}
