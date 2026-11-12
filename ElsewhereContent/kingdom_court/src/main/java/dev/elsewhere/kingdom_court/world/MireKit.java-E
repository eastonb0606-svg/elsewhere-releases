package dev.elsewhere.kingdom_court.world;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * s-layout-mire: the KIT_CASTLE part of the Mirewick layout (runs after CASTLE and WALL in every chunk): the Counting-House's crow-stepped
 * gables with purple courses, gilt copings and clock, its slate roof and the lantern belfry; slate or crimson roofs (alternating) and stepped,
 * neck or bell front gables with gilt finials and hoist beams on the ten canal houses; stepped-gable roofs on the Guildhall, the sluice house,
 * barracks, arsenal and gatehouse; the stables' open gable; the polder windmill on the north bastion; lantern posts on the other bastion tips;
 * z-castle's terrain-safe quay canal ({@link CastleKit#canalFit}) and the lantern causeway over it. No valuable blocks: every write passes Valuables.safe (Placer).
 */
final class MireKit {
	private MireKit() {}

	static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	static final BlockState CALCITE = st(Blocks.CALCITE), QUARTZ = st(Blocks.QUARTZ_BRICKS), PURPLE = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE)),
		GOLD = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)), TILES = st(Blocks.DEEPSLATE_TILES), CRIMSON = st(Blocks.CRIMSON_PLANKS), AIR = st(Blocks.AIR);

	static void build(CastleLayout.L l) {
		CastleKit.W w = new CastleKit.W(l, false);
		MireLayout.Plan plan = MireLayout.plan(l.o, l.p.ground);
		countingHouse(w);
		for (int i = 0; i < MireCourt.HOUSES.length; i++) houseRoof(w, MireCourt.HOUSES[i], i);
		int[] g = MireCourt.GUILD, s = MireCourt.SLUICE, b = MireCourt.BARRACKS, a = MireCourt.ARSENAL, t = MireCourt.STABLES;
		CastleKit.gable(w, g[0], g[1], g[2], g[3], 8, 0, true, Blocks.DEEPSLATE_TILE_STAIRS, TILES, CALCITE, true, GOLD);
		w.s(g[2] + 1, 6, 0, Blocks.WALL_BANNER.pick(DyeColor.PURPLE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST));
		CastleKit.gable(w, s[0], s[1], s[2], s[3], 6, 0, true, Blocks.CRIMSON_STAIRS, CRIMSON, QUARTZ, true, GOLD);
		CastleKit.gable(w, b[0], b[1], b[2], b[3], 6, 0, true, Blocks.CRIMSON_STAIRS, CRIMSON, CALCITE, true, GOLD);
		CastleKit.gable(w, a[0], a[1], a[2], a[3], 6, 0, false, Blocks.DEEPSLATE_TILE_STAIRS, TILES, CALCITE, true, GOLD);
		CastleKit.gable(w, t[0], t[1], t[2], t[3], 4, 0, true, Blocks.DEEPSLATE_TILE_STAIRS, TILES, null, false, null);
		gatehouse(w);
		windmill(w, plan);
		int[][] tips = MireLayout.tipProbes();
		for (int i = 1; i < tips.length; i++) {
			int top = 8 + MireLayout.lift(plan, tips[i][0], tips[i][1]);
			w.dy = 0;
			if (!w.solid(tips[i][0], top, tips[i][1])) continue;
			w.s(tips[i][0], top + 1, tips[i][1], st(Blocks.DIORITE_WALL));
			w.s(tips[i][0], top + 2, tips[i][1], st(Blocks.LANTERN));
		}
		w.dy = 0;
		// z-castle's quay canal (sea-level water in a contained bed, only where the quay meets water) and its lantern causeway
		CastleKit.canalFit(w, QUARTZ, st(Blocks.STONE_BRICKS));
		causeway(w);
	}

	/** The Counting-House: crow-stepped calcite gables north and south with purple courses and gilt copings, a slate roof, clock, belfry. */
	static void countingHouse(CastleKit.W w) {
		w.dy = 0;
		CastleKit.clearCrenels(w, -11, -26, 11, -8, 19);
		CastleKit.gable(w, -11, -26, 11, -8, 19, 0, false, Blocks.DEEPSLATE_TILE_STAIRS, TILES, CALCITE, true, GOLD);
		for (int z : new int[] {-26, -8}) {
			for (int x = -9; x <= 9; x++) w.s(x, 21, z, PURPLE); // row 2 of the gable: half-width 9
			for (int x = -5; x <= 5; x++) w.s(x, 25, z, PURPLE); // row 6: half-width 5
		}
		// clock on the front gable
		for (int x = -1; x <= 1; x++) for (int y = 22; y <= 24; y++) w.s(x, y, -8, x == 0 && y == 23 ? st(Blocks.CHISELED_QUARTZ_BLOCK) : GOLD);
		// the lantern belfry on the ridge: purple body, open lantern stage, gilt cornice, crimson spire, gilt finial and lantern
		for (int y = 29; y <= 31; y++) CastleKit.ringFull(w, -1, -18, 1, -16, y, PURPLE, null);
		w.s(0, 31, -17, PURPLE);
		for (int y = 32; y <= 33; y++) for (int sx = -1; sx <= 1; sx += 2) for (int sz = -18; sz <= -16; sz += 2) w.s(sx, y, sz, st(Blocks.CHISELED_QUARTZ_BLOCK));
		w.s(0, 32, -17, st(Blocks.LANTERN));
		CastleKit.ringFull(w, -2, -19, 2, -15, 34, GOLD, null);
		for (int x = -1; x <= 1; x++) for (int z = -18; z <= -16; z++) w.s(x, 34, z, QUARTZ);
		int top = CastleKit.spire(w, 0, -17, 2, 35, Blocks.CRIMSON_STAIRS, CRIMSON, true);
		w.s(0, top, -17, GOLD);
		w.s(0, top + 1, -17, st(Blocks.LANTERN));
		for (int s = -1; s <= 1; s += 2)
			w.s(s * 5, 12, -7, Blocks.WALL_BANNER.pick(DyeColor.PURPLE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
	}

	/** A canal house roof (slate or crimson, alternating) between a fancy front gable (stepped, neck or bell) and a stepped back gable. */
	static void houseRoof(CastleKit.W w, int[] h, int i) {
		int x0 = h[0], z0 = h[1], x1 = h[2], z1 = h[3], front = h[4], y0 = 4 * h[5];
		int zf = front > 0 ? z1 : z0, zb = front > 0 ? z0 : z1;
		w.dy = 0;
		boolean crimson = (i & 1) == 0;
		CastleKit.gable(w, x0, z0, x1, z1, y0, 0, false, crimson ? Blocks.CRIMSON_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, crimson ? CRIMSON : TILES, null, true, null);
		int ft = facade(w, x0, zf, y0, h[6]);
		facade(w, x0, zb, y0, 0);
		w.s(x0 + 2, ft - 1, zf + front, st(Blocks.CRIMSON_FENCE)); // hoist beam
	}

	/**
	 * A 5-wide gable wall in the plane z = {@code z} from x0, rows from y0: 0 stepped (crow steps with gilt caps), 1 neck (shoulders, a 3-wide
	 * neck with a window, pediment, gilt finial), 2 bell (scrolled shoulders in gilt, a curved top, gilt bell cap). Returns the top row's y.
	 */
	static int facade(CastleKit.W w, int x0, int z, int y0, int type) {
		int xc = x0 + 2;
		BlockState up = CastleKit.stair(Blocks.QUARTZ_STAIRS, Direction.EAST, Half.BOTTOM), down = CastleKit.stair(Blocks.QUARTZ_STAIRS, Direction.WEST, Half.BOTTOM);
		BlockState window = st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PURPLE));
		switch (type) {
			case 1 -> {
				row(w, x0, x0 + 4, y0, z, CALCITE);
				w.s(x0, y0 + 1, z, up);
				row(w, x0 + 1, x0 + 3, y0 + 1, z, CALCITE);
				w.s(x0 + 4, y0 + 1, z, down);
				row(w, x0 + 1, x0 + 3, y0 + 2, z, CALCITE);
				w.s(xc, y0 + 2, z, window);
				row(w, x0 + 1, x0 + 3, y0 + 3, z, PURPLE);
				w.s(x0 + 1, y0 + 4, z, up);
				w.s(xc, y0 + 4, z, CALCITE);
				w.s(x0 + 3, y0 + 4, z, down);
				w.s(xc, y0 + 5, z, GOLD);
				return y0 + 5;
			}
			case 2 -> {
				row(w, x0, x0 + 4, y0, z, CALCITE);
				w.s(x0, y0 + 1, z, GOLD);
				row(w, x0 + 1, x0 + 3, y0 + 1, z, CALCITE);
				w.s(x0 + 4, y0 + 1, z, GOLD);
				row(w, x0 + 1, x0 + 3, y0 + 2, z, PURPLE);
				w.s(xc, y0 + 2, z, window);
				w.s(x0 + 1, y0 + 3, z, up);
				w.s(xc, y0 + 3, z, CALCITE);
				w.s(x0 + 3, y0 + 3, z, down);
				w.s(xc, y0 + 4, z, GOLD);
				return y0 + 4;
			}
			default -> {
				row(w, x0, x0 + 4, y0, z, CALCITE);
				row(w, x0, x0 + 4, y0 + 1, z, CALCITE);
				w.s(x0, y0 + 1, z, GOLD);
				w.s(x0 + 4, y0 + 1, z, GOLD);
				row(w, x0 + 1, x0 + 3, y0 + 2, z, PURPLE);
				row(w, x0 + 1, x0 + 3, y0 + 3, z, CALCITE);
				w.s(x0 + 1, y0 + 3, z, GOLD);
				w.s(x0 + 3, y0 + 3, z, GOLD);
				w.s(xc, y0 + 4, z, CALCITE);
				w.s(xc, y0 + 5, z, GOLD);
				return y0 + 5;
			}
		}
	}

	static void row(CastleKit.W w, int xa, int xb, int y, int z, BlockState b) {
		for (int x = xa; x <= xb; x++) w.s(x, y, z, b);
	}

	/** The water-gate roof: crimson between crow-stepped calcite gables, a gilt clock over the quay, a gilt lantern turret on the ridge. */
	static void gatehouse(CastleKit.W w) {
		w.dy = 0;
		int ridge = CastleKit.gable(w, -6, 25, 6, 30, 13, 0, false, Blocks.CRIMSON_STAIRS, CRIMSON, CALCITE, true, GOLD);
		for (int x = -4; x <= 4; x++) w.s(x, 15, 30, PURPLE);
		for (int x = -1; x <= 1; x++) for (int y = 16; y <= 18; y++) w.s(x, y, 30, x == 0 && y == 17 ? st(Blocks.CHISELED_QUARTZ_BLOCK) : GOLD);
		int ry = Math.min(ridge, 13 + 6);
		w.s(0, ry + 1, 27, GOLD);
		w.s(0, ry + 2, 27, st(Blocks.LANTERN));
	}

	/** Polder windmill on the north bastion: a calcite tower with a purple band, crimson cap, gilt finial, four sails on a shaft facing north (sail plane z -34, clear of the cap). */
	static void windmill(CastleKit.W w, MireLayout.Plan plan) {
		w.dy = MireLayout.lift(plan, 0, -31);
		for (int y = 9; y <= 15; y++) CastleKit.ringFull(w, -1, -32, 1, -30, y, y == 12 ? PURPLE : CALCITE, null);
		w.s(0, 9, -30, AIR);
		w.s(0, 10, -30, AIR);
		w.s(0, 9, -31, AIR);
		w.s(0, 10, -31, AIR);
		CastleKit.ring(w, -2, -33, 2, -29, 16, Blocks.CRIMSON_STAIRS, Half.BOTTOM, CRIMSON, null);
		for (int x = -1; x <= 1; x++) for (int z = -32; z <= -30; z++) w.s(x, 16, z, CRIMSON);
		CastleKit.ringFull(w, -1, -32, 1, -30, 17, CRIMSON, null);
		w.s(0, 17, -31, CRIMSON);
		w.s(0, 18, -31, GOLD);
		BlockState hub = Blocks.MANGROVE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), arm = st(Blocks.MANGROVE_FENCE),
			cloth = st(Blocks.WOOL.white());
		int hy = 14, z = -34;
		w.s(0, hy, z + 1, hub);
		w.s(0, hy, z, hub);
		for (int k = 1; k <= 5; k++) {
			w.s(0, hy + k, z, arm);
			w.s(0, hy - k, z, arm);
			w.s(k, hy, z, arm);
			w.s(-k, hy, z, arm);
			if (k >= 2) {
				w.s(1, hy + k, z, cloth);
				w.s(-1, hy - k, z, cloth);
				w.s(k, hy - 1, z, cloth);
				w.s(-k, hy + 1, z, cloth);
			}
		}
		w.dy = 0;
	}

	/** Lantern causeway where the gate road crosses between the two canal basins (only where the quay canal was built). */
	static void causeway(CastleKit.W w) {
		CastleKit.Canal c = CastleKit.canal(w.l.o, w.l.p.ground, w.l.p.style);
		if (c == null) return;
		int q = c.quay() - w.l.o.getY();
		w.dy = 0;
		for (int s = -1; s <= 1; s += 2) {
			if (c.at(s * 4) && w.solid(s * 3, q, 37) && !w.solid(s * 3, q + 1, 37)) {
				w.s(s * 3, q + 1, 37, st(Blocks.DIORITE_WALL));
				w.s(s * 3, q + 2, 37, st(Blocks.LANTERN));
			}
			for (int z = 35; z <= 36; z++)
				if (c.at(s * 4) && w.solid(s * 2, q, z) && !w.solid(s * 2, q + 1, z)) w.s(s * 2, q + 1, z, CastleKit.slab(Blocks.QUARTZ_SLAB, SlabType.BOTTOM));
		}
	}
}
