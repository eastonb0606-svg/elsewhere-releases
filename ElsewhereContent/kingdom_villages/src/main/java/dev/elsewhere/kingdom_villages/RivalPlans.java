package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom_villages.FactionVillages.Profile;
import dev.elsewhere.kingdom_villages.FactionVillages.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-faction rebuild styles (RIVALS-DECISIONS: Hrafnholt longhall and palisade, Azmar obelisks and palm-shaded walls,
 * Mirewick canal quays, dikes and sluice houses) plus border hints (raven post, obelisk, lantern post). Like {@link Plans}, these only list
 * candidate ops; {@link BlockWriter} decides, and it never writes over fluids, block entities, job sites, player blocks or
 * anything outside the village's own pieces and the air above them. No style places water, lava or fire, so nothing floods
 * or burns. Aldermark's own plan stays in {@link Plans}.
 */
public final class RivalPlans {
	private RivalPlans() {}

	public static final int MAX_FORTIFY = 4200;
	/** The border hint position of the last {@link #fortify} (set right after planning; read by the manager). */
	public static @Nullable BlockPos lastHint;

	static BlockState state(Block b) {
		return b.defaultBlockState();
	}

	static BlockState banner(Profile p, int rot) {
		return Blocks.BANNER.pick(p.banner()).defaultBlockState().setValue(BannerBlock.ROTATION, rot & 15);
	}

	// ---- restyle ------------------------------------------------------------------------------------------------------

	/** The rival's block for an original village block, or null to leave it. */
	static @Nullable BlockState style(Shape shape, BlockState orig, int y, BoundingBox box, RandomSource rnd) {
		boolean low = y < box.minY() + 2;
		switch (shape) {
			case HRAFNHOLT -> {
				if (orig.is(BlockTags.PLANKS)) return low ? state(Blocks.MOSSY_COBBLESTONE) : state(Blocks.SPRUCE_PLANKS);
				if (orig.is(BlockTags.LOGS)) return Blocks.SPRUCE_LOG.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_STAIRS)) return Blocks.SPRUCE_STAIRS.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_SLABS)) return Blocks.SPRUCE_SLAB.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_FENCES)) return Blocks.SPRUCE_FENCE.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE)) return state(Blocks.MOSSY_COBBLESTONE);
				if (orig.is(Blocks.COBBLESTONE_STAIRS)) return Blocks.MOSSY_COBBLESTONE_STAIRS.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE_SLAB)) return Blocks.MOSSY_COBBLESTONE_SLAB.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE_WALL)) return Blocks.MOSSY_COBBLESTONE_WALL.withPropertiesOf(orig);
				if (HrafnLooks.ON && (orig.is(Blocks.GLASS_PANE) || orig.is(Blocks.GLASS))) return state(Blocks.SPRUCE_FENCE); // no glass in Hrafnholt: wind-eyes
			}
			case AZMAR -> {
				if (orig.is(BlockTags.PLANKS)) return low ? state(Blocks.SANDSTONE) : state(Blocks.CUT_SANDSTONE);
				if (orig.is(BlockTags.LOGS)) return Blocks.STRIPPED_JUNGLE_LOG.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_STAIRS)) return Blocks.SANDSTONE_STAIRS.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_SLABS)) return Blocks.SANDSTONE_SLAB.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_FENCES)) return Blocks.SANDSTONE_WALL.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE) || orig.is(Blocks.MOSSY_COBBLESTONE)) return state(Blocks.SANDSTONE);
				if (orig.is(Blocks.COBBLESTONE_STAIRS)) return Blocks.SANDSTONE_STAIRS.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE_SLAB)) return Blocks.SANDSTONE_SLAB.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE_WALL)) return Blocks.SANDSTONE_WALL.withPropertiesOf(orig);
			}
			case MIREWICK -> {
				// the polder republic: pale stone with purple and crimson, gold accents (no valuable blocks)
				if (orig.is(BlockTags.PLANKS)) return low ? (rnd.nextInt(8) == 0 ? state(Blocks.CALCITE) : state(Blocks.QUARTZ_BRICKS)) : (rnd.nextInt(6) == 0 ? state(Blocks.DYED_TERRACOTTA.purple()) : state(Blocks.MANGROVE_PLANKS));
				if (orig.is(BlockTags.LOGS)) return Blocks.MANGROVE_LOG.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_STAIRS)) return Blocks.MANGROVE_STAIRS.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_SLABS)) return Blocks.MANGROVE_SLAB.withPropertiesOf(orig);
				if (orig.is(BlockTags.WOODEN_FENCES)) return Blocks.MANGROVE_FENCE.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE) || orig.is(Blocks.MOSSY_COBBLESTONE)) return state(Blocks.STONE_BRICKS);
				if (orig.is(Blocks.COBBLESTONE_STAIRS)) return Blocks.STONE_BRICK_STAIRS.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE_SLAB)) return Blocks.STONE_BRICK_SLAB.withPropertiesOf(orig);
				if (orig.is(Blocks.COBBLESTONE_WALL)) return Blocks.STONE_BRICK_WALL.withPropertiesOf(orig);
			}
			default -> {}
		}
		if (orig.is(BlockTags.CROPS)) return orig.getBlock().defaultBlockState();
		return null;
	}

	// ---- the plan -----------------------------------------------------------------------------------------------------

	/** Working state shared by the builders. */
	static final class Ctx {
		final ServerLevel level;
		final VillageRecord r;
		final RandomSource rnd;
		final Profile p;
		final List<VillageRecord.Op> ops = new ArrayList<>();
		final Set<Long> used = new HashSet<>();
		BlockPos bell;
		@Nullable BlockPos towerTop, hint;

		Ctx(ServerLevel level, VillageRecord r, RandomSource rnd, Profile p) {
			this.level = level;
			this.r = r;
			this.rnd = rnd;
			this.p = p;
		}

		void add(BlockPos pos, BlockState s) {
			Plans.add(ops, used, pos, s);
		}

		void fill(BlockPos a, BlockPos b, BlockState s) {
			for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++)
				for (int y = Math.min(a.getY(), b.getY()); y <= Math.max(a.getY(), b.getY()); y++)
					for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) add(new BlockPos(x, y, z), s);
		}
	}

	public static Plans.Fortify fortify(ServerLevel level, VillageRecord r, RandomSource rnd, Profile p) {
		Ctx c = new Ctx(level, r, rnd, p);
		List<VillageRecord.Op> first = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		BlockPos bell = null;
		// 1. restyle (and repair what the siege burned) house by house
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() != VillageRecord.HOUSE && pc.type() != VillageRecord.CENTER) continue;
			BoundingBox b = pc.box();
			for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				m.set(x, y, z);
				if (!level.isLoaded(m)) continue;
				BlockState cur = level.getBlockState(m);
				VillageRecord.Change ch = r.journal.get(m.asLong());
				BlockState orig = ch != null ? ch.original() : cur;
				if (orig.is(Blocks.BELL)) bell = m.immutable();
				if (ch == null && Plans.skip(cur)) continue;
				BlockState target = style(p.shape(), orig, y, b, rnd);
				if (target == null && ch != null) target = orig;
				if (target == null || target == cur) continue;
				if (c.used.add(m.asLong())) c.ops.add(new VillageRecord.Op(m.asLong(), target));
			}
		}
		if (bell == null) bell = Plans.surface(level, r.center);
		c.bell = bell;
		// 2. no coffer: a rival's shop is the court's NPC (Shops.offers/buy), not a Steward; the tax collector just surrenders on liberation
		BlockPos coffer = null;
		// 3. the faction's signature
		lastHint = null;
		switch (p.shape()) {
			case HRAFNHOLT -> hrafnholt(c);
			case AZMAR -> azmar(c);
			case MIREWICK -> mirewick(c);
			default -> {}
		}
		lastHint = c.hint;
		first.addAll(c.ops);
		first.removeIf(o -> !r.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP));
		int cap = p.shape() == Shape.HRAFNHOLT && HrafnLooks.ON ? HrafnLooks.MAX_OPS : MAX_FORTIFY;
		if (first.size() > cap) first = new ArrayList<>(first.subList(0, cap));
		return new Plans.Fortify(first, coffer, c.towerTop, bell);
	}

	/** The outer-edge column nearest the realm's castle (inside the footprint, so the writer accepts it): where the border hint stands. */
	static @Nullable BlockPos hintSpot(Ctx c) {
		BlockPos[] best = {null};
		double[] bd = {Double.MAX_VALUE};
		BlockPos target = c.r.castle != null ? c.r.castle : c.r.center;
		perimeter(c, (ground, near) -> {
			if (near) return;
			double d = ground.distSqr(target);
			if (d < bd[0]) {
				bd[0] = d;
				best[0] = ground.above();
			}
		});
		return best[0];
	}

	// ---- Hrafnholt: longhall, palisade, raven post ----------------------------------------------------------------------

	static void hrafnholt(Ctx c) {
		if (HrafnLooks.ON) { // 3.0 look (STRUCTURES 3.2 towns); the 2.1 rebuild below stays as the kill-switch path
			HrafnLooks.build(c);
			return;
		}
		ServerLevel level = c.level;
		// the longhall (13x7, else 9x5): mossy-cobblestone footing, spruce timber, gable roof, a hearth fire inside
		BlockPos o = freeRect(c, 13, 7, 7);
		int len = 13, wid = 7;
		if (o == null) {
			o = freeRect(c, 9, 5, 6);
			len = 9;
			wid = 5;
		}
		if (o != null) longhall(c, o, len, wid);
		// the watch post (raven tower): a spruce-and-deepslate tower with a black banner and a platform for the archer
		BlockPos t = Plans.freeArea(level, c.r, c.bell, 24, 3, 7, c.used, c.rnd);
		if (t != null) {
			tower(c, t, state(Blocks.MOSSY_COBBLESTONE), state(Blocks.SPRUCE_LOG), state(Blocks.SPRUCE_PLANKS), state(Blocks.SPRUCE_FENCE));
			c.add(t.offset(1, 7, 1), banner(c.p, 4));
		}
		BlockPos fireAt = Plans.freeFloor(level, c.r, c.bell.offset(3, 0, 3), 8, c.used);
		if (fireAt != null) c.add(fireAt, state(Blocks.CAMPFIRE));
		// raven post at the edge toward the realm (the border hint), banners on the houses, then the palisade (3 spruce logs and a
		// sharpened fence-post top on open ground, gates at the paths), the longest list last
		BlockPos hs = hintSpot(c);
		if (hs != null) {
			ravenPost(c, hs);
			c.hint = hs.above(4);
		}
		houseBanners(c);
		waterfront(c);
		palisade(c, state(Blocks.SPRUCE_LOG), state(Blocks.SPRUCE_FENCE), 3, state(Blocks.MOSSY_COBBLESTONE_WALL), (pos, k) -> banner(c.p, 8));
	}

	/** VillageRecord piece type of a waterfront (pier and boathouse) box. */
	public static final int PIER = 9;

	/** The y of the water source block at the column's surface, or Integer.MIN_VALUE when the column is not open water. */
	static int waterTop(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		BlockState st = level.getBlockState(new BlockPos(x, y, z));
		return st.is(Blocks.WATER) && st.getFluidState().isSource() ? y : Integer.MIN_VALUE;
	}

	/**
	 * Hrafnholt's seafaring side (RIVALS-DECISIONS 16a): when open water lies within 48 blocks of the village, a spruce pier of up to 8
	 * slabs runs out over it on log pilings (a log replaces one water block; no water is ever placed or removed otherwise, so nothing
	 * floods), with a lantern at the end and a three-high spruce boathouse open toward the water on the shore. One per village.
	 */
	static void waterfront(Ctx c) {
		for (VillageRecord.Piece pc : c.r.pieces) if (pc.type() == PIER) return;
		ServerLevel level = c.level;
		BlockPos ctr = c.r.center;
		BlockPos shore = null;
		Direction dir = null;
		int wy = 0;
		search:
		for (int rad = 8; rad <= 48; rad += 2) for (Direction d : Direction.Plane.HORIZONTAL) for (int lat = -12; lat <= 12; lat += 12) {
			BlockPos base = ctr.relative(d, rad).relative(d.getClockWise(), lat);
			if (!level.isLoaded(base) || !level.isLoaded(base.relative(d, 5))) continue;
			int w0 = waterTop(level, base.getX(), base.getZ());
			if (w0 == Integer.MIN_VALUE) continue;
			BlockPos land = base.relative(d, -1);
			if (waterTop(level, land.getX(), land.getZ()) != Integer.MIN_VALUE) continue;
			boolean open = true;
			for (int i = 1; i <= 4 && open; i++) open = waterTop(level, base.getX() + d.getStepX() * i, base.getZ() + d.getStepZ() * i) == w0;
			if (!open) continue;
			BlockPos stand = Plans.surface(level, land);
			if (Math.abs(stand.getY() - (w0 + 1)) > 1) continue;
			shore = new BlockPos(land.getX(), w0 + 1, land.getZ());
			dir = d;
			wy = w0;
			break search;
		}
		if (shore == null) return;
		int[] mm = {shore.getX(), shore.getY(), shore.getZ(), shore.getX(), shore.getY(), shore.getZ()};
		Direction side = dir.getClockWise();
		int placed = 0;
		BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState(), log = state(Blocks.SPRUCE_LOG);
		for (int i = 1; i <= 8; i++) {
			BlockPos p = shore.relative(dir, i);
			if (waterTop(level, p.getX(), p.getZ()) != wy) break;
			c.add(p, slab);
			placed++;
			grow(mm, p);
			if (i % 3 == 0) for (int dy = 0; dy <= 1; dy++) {
				BlockPos pile = p.below(1 + dy);
				if (level.getBlockState(pile).is(Blocks.WATER)) {
					c.add(pile, log);
					grow(mm, pile);
				}
			}
			if (i == 8 || i == 4) {
				c.add(p.above(), state(Blocks.SPRUCE_FENCE));
				c.add(p.above(2), state(Blocks.LANTERN));
				grow(mm, p.above(2));
			}
		}
		if (placed < 3) return; // too short to be a pier: leave the shore alone
		// the boathouse: 5 deep, 5 wide, 3 high on dry level ground behind the pier head, open toward the water
		boolean ground = true;
		for (int u = -5; u <= -1 && ground; u++) for (int v = -2; v <= 2 && ground; v++) {
			BlockPos q = shore.relative(dir, u).relative(side, v);
			BlockState g = level.getBlockState(q.below());
			ground = level.isLoaded(q) && !g.getFluidState().isEmpty() == false && g.isFaceSturdy(level, q.below(), Direction.UP) && level.getBlockState(q).isAir() && Math.abs(Plans.surface(level, q).getY() - shore.getY()) <= 1;
		}
		if (ground) {
			BlockState plank = state(Blocks.SPRUCE_PLANKS);
			for (int u = -5; u <= -1; u++) for (int v = -2; v <= 2; v++) {
				BlockPos q = shore.relative(dir, u).relative(side, v);
				boolean edge = u == -5 || u == -1 || v == -2 || v == 2;
				boolean corner = (u == -5 || u == -1) && (v == -2 || v == 2);
				boolean opening = u == -1 && v >= -1 && v <= 1;
				if (edge) for (int h = 0; h < 3; h++) {
					if (opening) continue;
					c.add(q.above(h), corner ? log : plank);
					grow(mm, q.above(h));
				}
				c.add(q.above(3), Blocks.SPRUCE_SLAB.defaultBlockState());
				grow(mm, q.above(3));
			}
			BlockPos lamp = shore.relative(dir, -4);
			c.add(lamp, state(Blocks.LANTERN));
			c.add(shore.relative(dir, -1).relative(side, 2).above(3), banner(c.p, 4));
		}
		BoundingBox box = new BoundingBox(mm[0] - 1, wy - 3, mm[2] - 1, mm[3] + 1, wy + 8, mm[5] + 1);
		box = BoundingBox.fromCorners(new BlockPos(Math.min(mm[0], mm[3]) - 1, wy - 3, Math.min(mm[2], mm[5]) - 1), new BlockPos(Math.max(mm[0], mm[3]) + 1, wy + 8, Math.max(mm[2], mm[5]) + 1));
		c.r.pieces.add(new VillageRecord.Piece(box, PIER));
	}

	private static void grow(int[] mm, BlockPos p) {
		mm[0] = Math.min(mm[0], p.getX());
		mm[1] = Math.min(mm[1], p.getY());
		mm[2] = Math.min(mm[2], p.getZ());
		mm[3] = Math.max(mm[3], p.getX());
		mm[4] = Math.max(mm[4], p.getY());
		mm[5] = Math.max(mm[5], p.getZ());
	}


	static void longhall(Ctx c, BlockPos o, int len, int wid) {
		int g = o.getY();
		BlockState footing = state(Blocks.MOSSY_COBBLESTONE), plank = state(Blocks.SPRUCE_PLANKS), log = state(Blocks.SPRUCE_LOG);
		for (int x = 0; x < len; x++) for (int z = 0; z < wid; z++) {
			boolean edgeX = x == 0 || x == len - 1, edgeZ = z == 0 || z == wid - 1;
			if (!edgeX && !edgeZ) continue;
			boolean corner = edgeX && edgeZ;
			boolean door = z == 0 && x == len / 2 || z == wid - 1 && x == len / 2;
			boolean window = !corner && !door && (edgeZ ? x % 3 == 1 : z == wid / 2);
			for (int h = 0; h < 3; h++) {
				BlockPos q = o.offset(x, h, z);
				if (door && h < 2) continue;
				if (corner) c.add(q, log);
				else if (h == 0) c.add(q, footing);
				else if (window && h == 1) c.add(q, state(Blocks.IRON_BARS));
				else c.add(q, plank);
			}
		}
		// gable roof running along x
		int half = wid / 2;
		for (int k = 0; k <= half; k++) for (int x = -1; x <= len; x++) {
			int y = g + 3 + k;
			if (k < half) {
				BlockState sa = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
				BlockState sb = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
				c.add(new BlockPos(o.getX() + x, y, o.getZ() + k), sa);
				c.add(new BlockPos(o.getX() + x, y, o.getZ() + wid - 1 - k), sb);
			} else c.add(new BlockPos(o.getX() + x, y, o.getZ() + half), state(Blocks.SPRUCE_PLANKS));
		}
		for (int k = 0; k < half; k++) for (int z = k + 1; z <= wid - 2 - k; z++) {
			c.add(o.offset(0, 3 + k, z), plank);
			c.add(o.offset(len - 1, 3 + k, z), plank);
		}
		// the Hearth-Fire in the middle of the hall, a raven banner over the far gable
		c.add(o.offset(len / 2, 0, half), state(Blocks.CAMPFIRE));
		c.add(o.offset(len / 2 - 2, 0, half), state(Blocks.SPRUCE_FENCE));
		c.add(o.offset(len / 2 + 2, 0, half), state(Blocks.SPRUCE_FENCE));
		c.add(o.offset(len / 2 - 2, 1, half), state(Blocks.LANTERN));
		c.add(o.offset(len / 2 + 2, 1, half), state(Blocks.LANTERN));
		c.add(new BlockPos(o.getX() + len, g + 3 + half + 1, o.getZ() + half), banner(c.p, 12));
	}

	/** A raven post: a 5-high spruce post with a black banner and a black candle perched on top (the border hint). */
	static void ravenPost(Ctx c, BlockPos g) {
		for (VillageRecord.Op op : ravenPostOps(g, c.p)) c.add(BlockPos.of(op.pos()), op.state());
	}

	public static List<VillageRecord.Op> ravenPostOps(BlockPos g, Profile p) {
		List<VillageRecord.Op> out = new ArrayList<>();
		for (int h = 0; h < 5; h++) out.add(new VillageRecord.Op(g.above(h).asLong(), state(Blocks.SPRUCE_LOG)));
		out.add(new VillageRecord.Op(g.above(5).asLong(), Blocks.DYED_CANDLE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState()));
		out.add(new VillageRecord.Op(g.offset(1, 4, 0).asLong(), banner(p, 4)));
		return out;
	}

	// ---- Azmar: obelisks and palm-shaded walls ------------------------------------------------------------------------

	static void azmar(Ctx c) {
		ServerLevel level = c.level;
		BlockPos ob = Plans.freeArea(level, c.r, c.bell, 14, 3, 8, c.used, c.rnd);
		if (ob != null) obelisk(c, ob, 7);
		BlockPos t = Plans.freeArea(level, c.r, c.bell, 24, 3, 7, c.used, c.rnd);
		if (t != null) {
			tower(c, t, state(Blocks.CUT_SANDSTONE), state(Blocks.STRIPPED_JUNGLE_LOG), state(Blocks.SMOOTH_SANDSTONE), state(Blocks.SANDSTONE_WALL));
			c.add(t.offset(1, 7, 1), banner(c.p, 4));
		}
		// pavilion: shaded awning of yellow wool on 4 sandstone posts (replaces the tents)
		BlockPos pav = Plans.freeArea(level, c.r, c.bell, 30, 3, 4, c.used, c.rnd);
		if (pav != null) {
			for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) for (int h = 0; h < 2; h++) c.add(pav.offset(dx, h, dz), state(Blocks.CUT_SANDSTONE));
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.add(pav.offset(dx, 2, dz), Blocks.WOOL.yellow().defaultBlockState());
		}
		// the border obelisk at the edge toward the realm, banners, then the wall: 2 high sandstone with a slab parapet, palms every
		// 7th column, gates at the paths
		BlockPos hs = hintSpot(c);
		if (hs != null) {
			obelisk(c, hs, 6);
			c.hint = hs.above(6);
		}
		houseBanners(c);
		palmWall(c, 1);
	}

	/** Small obelisk: 3x3 base, a sandstone shaft with a chiselled glyph band and a lapis band, yellow-terracotta pyramidion. */
	static void obelisk(Ctx c, BlockPos center, int height) {
		for (VillageRecord.Op op : obeliskOps(center, height)) c.add(BlockPos.of(op.pos()), op.state());
	}

	public static List<VillageRecord.Op> obeliskOps(BlockPos g, int height) {
		List<VillageRecord.Op> out = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(new VillageRecord.Op(g.offset(dx, 0, dz).asLong(), state(Blocks.CUT_SANDSTONE)));
		for (int h = 1; h < height; h++) {
			BlockState s = h == height / 2 ? state(Blocks.CHISELED_SANDSTONE) : h == height / 2 + 2 ? state(Blocks.LAPIS_BLOCK) : state(Blocks.SANDSTONE);
			out.add(new VillageRecord.Op(g.above(h).asLong(), s));
		}
		out.add(new VillageRecord.Op(g.above(height).asLong(), state(Blocks.GLAZED_TERRACOTTA.yellow())));
		return out;
	}

	/** Palm: jungle-log trunk, persistent jungle leaves (they never decay). */
	public static List<VillageRecord.Op> palmOps(BlockPos g, int trunk) {
		List<VillageRecord.Op> out = new ArrayList<>();
		BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int h = 0; h < trunk; h++) out.add(new VillageRecord.Op(g.above(h).asLong(), state(Blocks.JUNGLE_LOG)));
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			int md = Math.abs(dx) + Math.abs(dz);
			if (md > 3 || md == 0) continue;
			if (md <= 2) out.add(new VillageRecord.Op(g.offset(dx, trunk, dz).asLong(), leaves));
			if (md == 1) out.add(new VillageRecord.Op(g.offset(dx, trunk - 1, dz).asLong(), leaves));
			if (md == 3) out.add(new VillageRecord.Op(g.offset(dx, trunk - 1, dz).asLong(), leaves));
		}
		out.add(new VillageRecord.Op(g.above(trunk + 1).asLong(), leaves));
		out.add(new VillageRecord.Op(g.above(trunk).asLong(), leaves));
		return out;
	}

	/** The Azmar ring wall of height {@code level+1} (growth steps add courses); a palm every 7th column. */
	static void palmWall(Ctx c, int courses) {
		int[] n = {0};
		perimeter(c, (ground, nearGate) -> {
			if (nearGate) {
				c.add(ground.above(), state(Blocks.CHISELED_SANDSTONE));
				c.add(ground.above(2), state(Blocks.CHISELED_SANDSTONE));
				c.add(ground.above(3), banner(c.p, 8));
				return;
			}
			n[0]++;
			if (n[0] % 7 == 0) {
				for (VillageRecord.Op op : palmOps(ground.above(), 4)) c.add(BlockPos.of(op.pos()), op.state());
				return;
			}
			for (int h = 1; h <= 1 + courses; h++) c.add(ground.above(h), state(Blocks.SANDSTONE));
			c.add(ground.above(2 + courses), Blocks.CUT_SANDSTONE_SLAB.defaultBlockState());
		});
	}

	// ---- Mirewick: canal quays and dikes (never water) ------------------------------------------------------------------------------

	static void mirewick(Ctx c) {
		ServerLevel level = c.level;
		// the sluice house (the watch post): a 3x3 deck 3 above the ground on four pillars, purple walls, a crimson slab roof and a lantern
		BlockPos t = Plans.freeArea(level, c.r, c.bell, 24, 3, 8, c.used, c.rnd);
		if (t != null) stiltHut(c, t);
		// the canal quays: raised pale-stone promenades with rails and lanterns from the bell to the gates, on pillars (dry: no water is ever placed)
		boardwalk(c);
		BlockPos fireAt = Plans.freeFloor(level, c.r, c.bell.offset(3, 0, 3), 8, c.used);
		if (fireAt != null) c.add(fireAt, state(Blocks.CAMPFIRE));
		BlockPos hs = hintSpot(c);
		if (hs != null) {
			for (VillageRecord.Op op : lanternPostOps(hs)) c.add(BlockPos.of(op.pos()), op.state());
			c.hint = hs.above(5);
		}
		houseBanners(c);
		// a low dike (a grass bank faced with stone brick) with lantern posts at the outer edge; no gates needed, the quay is the way in
		int[] n = {0};
		perimeter(c, (ground, nearGate) -> {
			if (nearGate) return;
			n[0]++;
			if (n[0] % 6 == 0) {
				for (VillageRecord.Op op : lanternPostOps(ground.above())) c.add(BlockPos.of(op.pos()), op.state());
				return;
			}
			c.add(ground.above(), state(Blocks.GRASS_BLOCK));
			if (c.rnd.nextInt(5) == 0) c.add(ground.above(2), Blocks.CARPET.purple().defaultBlockState());
		});
	}

	static void stiltHut(Ctx c, BlockPos t) {
		int g = t.getY();
		BlockState post = state(Blocks.STRIPPED_MANGROVE_LOG), deck = state(Blocks.SMOOTH_STONE), mud = state(Blocks.DYED_TERRACOTTA.yellow());
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			c.add(new BlockPos(t.getX() + dx, g + 3, t.getZ() + dz), deck);
			boolean corner = dx != 0 && dz != 0;
			if (corner) for (int h = 0; h < 3; h++) c.add(new BlockPos(t.getX() + dx, g + h, t.getZ() + dz), post);
			if (corner) for (int h = 4; h <= 6; h++) c.add(new BlockPos(t.getX() + dx, g + h, t.getZ() + dz), post);
			else if (dx != 0 || dz != 0) {
				if (!(dx == 1 && dz == 0)) for (int h = 4; h <= 5; h++) c.add(new BlockPos(t.getX() + dx, g + h, t.getZ() + dz), h == 4 ? mud : state(Blocks.MANGROVE_FENCE));
			}
			c.add(new BlockPos(t.getX() + dx, g + 7, t.getZ() + dz), Blocks.RED_NETHER_BRICK_SLAB.defaultBlockState());
		}
		// steps up on the open side (+x): stairs climbing toward the hut
		for (int i = 0; i < 3; i++) {
			c.add(new BlockPos(t.getX() + 4 - i, g + i, t.getZ()), Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
		}
		c.add(new BlockPos(t.getX() + 2, g + 3, t.getZ()), deck); // landing
		c.add(new BlockPos(t.getX(), g + 8, t.getZ()), state(Blocks.LANTERN));
		c.add(new BlockPos(t.getX() - 1, g + 8, t.getZ() - 1), banner(c.p, 4));
		c.towerTop = new BlockPos(t.getX(), g + 4, t.getZ());
	}

	/** Quay deck at ground+1 on pillars: legs from the bell toward each gate (path cell at the village edge), rails, lanterns, steps at the ends. */
	static void boardwalk(Ctx c) {
		ServerLevel level = c.level;
		VillageRecord r = c.r;
		List<BlockPos> gates = new ArrayList<>();
		for (VillageRecord.Piece pc : r.pieces) if (pc.type() == VillageRecord.STREET && gates.size() < 3) gates.add(pc.box().getCenter());
		if (gates.isEmpty()) gates.add(r.center.offset(8, 0, 0));
		int legs = 0;
		for (BlockPos gate : gates) {
			if (legs++ >= 3) break;
			BlockPos from = c.bell, to = gate;
			int sx = Integer.signum(to.getX() - from.getX()), sz = Integer.signum(to.getZ() - from.getZ());
			List<BlockPos> cells = new ArrayList<>();
			int x = from.getX(), z = from.getZ();
			int guard = 0;
			while (x != to.getX() && guard++ < 40) {
				x += sx;
				cells.add(new BlockPos(x, 0, z));
			}
			while (z != to.getZ() && guard++ < 80) {
				z += sz;
				cells.add(new BlockPos(x, 0, z));
			}
			int deckY = from.getY() + 1;
			int i = 0;
			for (BlockPos cell : cells) {
				i++;
				BlockPos surf = Plans.surface(level, cell);
				if (!level.isLoaded(surf) || Math.abs(surf.getY() - from.getY()) > 2) continue;
				BlockPos ground = surf.below();
				BlockState gs = level.getBlockState(ground);
				if (!gs.getFluidState().isEmpty() || gs.is(Blocks.FARMLAND)) continue;
				BlockPos deck = new BlockPos(cell.getX(), deckY, cell.getZ());
				c.add(deck, state(Blocks.SMOOTH_STONE));
				// pillars: a post under every third plank, down to the ground (only through air)
				if (i % 3 == 0) for (int y = deckY - 1; y >= ground.getY() + 1; y--) c.add(new BlockPos(cell.getX(), y, cell.getZ()), state(Blocks.STRIPPED_MANGROVE_LOG));
				boolean alongX = sx != 0 && (cell.getZ() == from.getZ());
				int rx = alongX ? 0 : 1, rz = alongX ? 1 : 0;
				if (i % 2 == 0) {
					c.add(deck.offset(rx, 1, rz), state(Blocks.MANGROVE_FENCE));
					c.add(deck.offset(-rx, 1, -rz), state(Blocks.MANGROVE_FENCE));
				}
				if (i % 4 == 0) c.add(deck.offset(rx, 2, rz), state(Blocks.LANTERN));
			}
		}
	}

	/** A lantern post (the Mirewick border hint): a 4-high stone-brick post, a lantern on top, a stone-brick footing (set along the canals). */
	public static List<VillageRecord.Op> lanternPostOps(BlockPos g) {
		List<VillageRecord.Op> out = new ArrayList<>();
		out.add(new VillageRecord.Op(g.asLong(), state(Blocks.STONE_BRICKS)));
		for (int h = 1; h <= 4; h++) out.add(new VillageRecord.Op(g.above(h).asLong(), state(Blocks.STRIPPED_MANGROVE_LOG)));
		out.add(new VillageRecord.Op(g.above(5).asLong(), state(Blocks.LANTERN)));
		return out;
	}

	// ---- shared pieces ------------------------------------------------------------------------------------------------

	interface ColumnVisitor {
		void visit(BlockPos ground, boolean nearGate);
	}

	/** Visits every outer-edge column on open natural ground (path cells are gates; columns beside a path are "near a gate"). */
	static void perimeter(Ctx c, ColumnVisitor v) {
		perimeter(c, v, true);
	}

	static void perimeter(Ctx c, ColumnVisitor v, boolean requireAir) {
		ServerLevel level = c.level;
		VillageRecord r = c.r;
		Set<Long> cols = new HashSet<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (VillageRecord.Piece pc : r.pieces) {
			BoundingBox b = pc.box();
			for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				if (x != b.minX() && x != b.maxX() && z != b.minZ() && z != b.maxZ()) continue;
				if (!cols.add(BlockPos.asLong(x, 0, z))) continue;
				boolean outer = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (!Plans.inAnyColumn(r, x + d.getStepX(), z + d.getStepZ())) outer = true;
				if (!outer || !level.isLoaded(m.set(x, b.minY(), z))) continue;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos ground = new BlockPos(x, top - 1, z);
				if (!requireAir) { // growth: our own wall stands on the ground, so look down for it
					for (int d = 0; d < 10 && !level.getBlockState(ground).is(Blocks.GRASS_BLOCK) && !level.getBlockState(ground).is(BlockTags.DIRT) && !level.getBlockState(ground).is(Blocks.SAND) && !level.getBlockState(ground).is(Blocks.RED_SAND) && !level.getBlockState(ground).is(Blocks.SNOW_BLOCK) && !level.getBlockState(ground).is(Blocks.GRAVEL); d++) ground = ground.below();
				}
				BlockState g = level.getBlockState(ground);
				if (!(g.is(Blocks.GRASS_BLOCK) || g.is(BlockTags.DIRT) || g.is(Blocks.SAND) || g.is(Blocks.RED_SAND) || g.is(Blocks.SNOW_BLOCK) || g.is(Blocks.DIRT_PATH) || g.is(Blocks.GRAVEL) || g.is(Blocks.COARSE_DIRT))) continue;
				if (g.is(Blocks.DIRT_PATH)) continue;
				boolean nearGate = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(ground.relative(d)).is(Blocks.DIRT_PATH)) nearGate = true;
				if (requireAir && !level.getBlockState(ground.above()).isAir()) continue;
				v.visit(ground, nearGate);
			}
		}
	}

	interface GateBanner {
		BlockState at(BlockPos pos, int k);
	}

	static void palisade(Ctx c, BlockState log, BlockState top, int logs, BlockState gatePost, GateBanner gate) {
		perimeter(c, (ground, nearGate) -> {
			if (nearGate) {
				c.add(ground.above(), gatePost);
				c.add(ground.above(2), gatePost);
				c.add(ground.above(3), gate.at(ground.above(3), 0));
				return;
			}
			for (int h = 1; h <= logs; h++) c.add(ground.above(h), log);
			c.add(ground.above(logs + 1), top);
		});
	}

	/** A watch tower: stone corner pillars 5 high, a plank platform, a fence rail around the top. */
	static void tower(Ctx c, BlockPos t, BlockState stone, BlockState pillar, BlockState platform, BlockState rail) {
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			boolean corner = dx != 0 && dz != 0;
			for (int dy = 0; dy < 5; dy++) if (corner) c.add(t.offset(dx, dy, dz), dy < 2 ? stone : pillar);
			c.add(t.offset(dx, 5, dz), platform);
			if (dx != 0 || dz != 0) c.add(t.offset(dx, 6, dz), rail);
		}
		c.towerTop = t.above(6);
	}

	static void houseBanners(Ctx c) {
		for (VillageRecord.Piece pc : c.r.pieces) {
			if (pc.type() != VillageRecord.HOUSE) continue;
			BlockPos ctr = pc.box().getCenter();
			int top = c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ctr.getX(), ctr.getZ());
			if (top <= pc.box().maxY() + 2) c.add(new BlockPos(ctr.getX(), top, ctr.getZ()), banner(c.p, 0));
		}
	}

	/** A flat free rectangle {@code w x d} (x by z) of air {@code height} high inside the pieces; origin = its min corner at the surface. */
	static @Nullable BlockPos freeRect(Ctx c, int w, int d, int height) {
		ServerLevel level = c.level;
		for (int tries = 0; tries < 120; tries++) {
			BlockPos o = Plans.surface(level, c.bell.offset(c.rnd.nextInt(57) - 28, 0, c.rnd.nextInt(57) - 28));
			if (!level.isLoaded(o)) continue;
			boolean ok = true;
			for (int dx = -1; dx <= w && ok; dx++) for (int dz = -1; dz <= d && ok; dz++) {
				BlockPos q = o.offset(dx, 0, dz);
				if (c.used.contains(q.asLong()) || !c.r.insideFootprint(q, BlockWriter.UP) || !c.r.insideFootprint(q.above(height - 1), BlockWriter.UP)) { ok = false; break; }
				if (Plans.surface(level, q).getY() != o.getY()) { ok = false; break; }
				BlockState below = level.getBlockState(q.below());
				if (below.is(Blocks.DIRT_PATH) || below.is(Blocks.FARMLAND) || !below.isFaceSturdy(level, q.below(), Direction.UP)) { ok = false; break; }
				for (int dy = 0; dy < height; dy++) if (!level.getBlockState(q.above(dy)).isAir()) { ok = false; break; }
			}
			if (ok) return o;
		}
		return null;
	}

	/** Where a rebuilt rival village's hint stands: the edge toward its realm (the plan puts the marker there). */
	public static BlockPos hintFor(ServerLevel level, VillageRecord r, Profile p) {
		BlockPos edge = Plans.surface(level, VillageManager.edgeToward(level, r));
		return switch (p.shape()) {
			case HRAFNHOLT -> edge.above(4);
			case AZMAR -> edge.above(6);
			case MIREWICK -> edge.above(5);
			default -> edge;
		};
	}

	// ---- outposts and border hints -----------------------------------------------------------------------------------

	/** The hint marker position for an outpost at {@code c} (the same cell {@link #outpost} builds it on). */
	public static BlockPos hintPos(Profile p, BlockPos c) {
		return switch (p.shape()) {
			case HRAFNHOLT -> c.offset(3, 4, 3);
			case AZMAR -> c.offset(3, 7, 3);
			case MIREWICK -> c.offset(3, 5, 3);
			default -> c.offset(-1, 6, -1);
		};
	}

	/** Rival outpost: a ring of the faction's fence, a tower platform on its footing, a banner and the faction's border hint. */
	public static List<VillageRecord.Op> outpost(Profile p, BlockPos c, Direction gate) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		BlockState fence = switch (p.shape()) {
			case HRAFNHOLT -> state(Blocks.SPRUCE_FENCE);
			case AZMAR -> state(Blocks.SANDSTONE);
			default -> state(Blocks.DARK_OAK_FENCE);
		};
		for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
			if (Math.abs(dx) != 5 && Math.abs(dz) != 5) continue;
			boolean gap = gate.getStepX() != 0 ? dx == 5 * gate.getStepX() && Math.abs(dz) <= 1 : dz == 5 * gate.getStepZ() && Math.abs(dx) <= 1;
			if (!gap) Plans.add(ops, used, c.offset(dx, 0, dz), fence);
		}
		BlockState foot = switch (p.shape()) {
			case HRAFNHOLT -> state(Blocks.MOSSY_COBBLESTONE);
			case AZMAR -> state(Blocks.CUT_SANDSTONE);
			default -> state(Blocks.MUD_BRICKS);
		}, deck = switch (p.shape()) {
			case HRAFNHOLT -> state(Blocks.SPRUCE_PLANKS);
			case AZMAR -> state(Blocks.SMOOTH_SANDSTONE);
			default -> state(Blocks.DARK_OAK_PLANKS);
		};
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (dx != 0 && dz != 0) for (int dy = 0; dy < 4; dy++) Plans.add(ops, used, c.offset(dx, dy, dz), foot);
			Plans.add(ops, used, c.offset(dx, 4, dz), deck);
			if (dx != 0 || dz != 0) Plans.add(ops, used, c.offset(dx, 5, dz), fence);
		}
		Plans.add(ops, used, c.offset(-1, 6, -1), banner(p, 4));
		Plans.add(ops, used, c.offset(-3, 0, -3), state(Blocks.CAMPFIRE));
		BlockPos h = c.offset(3, 0, 3);
		switch (p.shape()) {
			case HRAFNHOLT -> ravenPostOps(h, p).forEach(o -> Plans.add(ops, used, BlockPos.of(o.pos()), o.state()));
			case AZMAR -> obeliskOps(h, 7).forEach(o -> Plans.add(ops, used, BlockPos.of(o.pos()), o.state()));
			case MIREWICK -> lanternPostOps(h).forEach(o -> Plans.add(ops, used, BlockPos.of(o.pos()), o.state()));
			default -> {}
		}
		return ops;
	}

	// ---- cosmetic owner swap (a village changed hands: only blocks our own ledger placed) --------------------------------

	public static final int MAX_SWAP = 400;

	/** Banners to the new owner's colour, plus the new faction's small signature (obelisk / raven post / lantern post, palms). Never houses, walls or chests. */
	public static List<VillageRecord.Op> swap(ServerLevel level, VillageRecord r, Profile now, RandomSource rnd) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		for (var e : r.journal.long2ObjectEntrySet()) {
			BlockState w = e.getValue().written();
			BlockPos pos = BlockPos.of(e.getLongKey());
			if (!(w.getBlock() instanceof BannerBlock)) continue;
			if (!level.isLoaded(pos)) continue;
			BlockState cur = level.getBlockState(pos);
			if (!(cur.getBlock() instanceof BannerBlock)) continue;
			ops.add(new VillageRecord.Op(pos.asLong(), Blocks.BANNER.pick(HrafnLooks.tint(now, pos, now.banner())).defaultBlockState().setValue(BannerBlock.ROTATION, cur.getValue(BannerBlock.ROTATION))));
			if (ops.size() >= MAX_SWAP) return ops;
		}
		BlockPos bell = Plans.surface(level, r.center);
		Set<Long> used = new HashSet<>();
		BlockPos spot = Plans.freeArea(level, r, bell, 18, 3, 9, used, rnd);
		if (spot != null) {
			switch (now.shape()) {
				case AZMAR -> ops.addAll(obeliskOps(spot, 5));
				case HRAFNHOLT -> ops.addAll(ravenPostOps(spot, now));
				case MIREWICK -> ops.addAll(lanternPostOps(spot));
				default -> ops.add(new VillageRecord.Op(spot.above().asLong(), Blocks.BANNER.pick(now.banner()).defaultBlockState()));
			}
		}
		if (now.shape() == Shape.AZMAR) {
			BlockPos palm = Plans.freeArea(level, r, bell, 26, 3, 8, used, rnd);
			if (palm != null) ops.addAll(palmOps(palm, 4));
		}
		ops.removeIf(o -> !r.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP));
		return ops.size() > MAX_SWAP ? new ArrayList<>(ops.subList(0, MAX_SWAP)) : ops;
	}

	/** Re-dyes every banner our ledger placed (a village showing unrest, or calm again). */
	public static List<VillageRecord.Op> recolor(ServerLevel level, VillageRecord r, net.minecraft.world.item.DyeColor color) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		for (var e : r.journal.long2ObjectEntrySet()) {
			if (!(e.getValue().written().getBlock() instanceof BannerBlock)) continue;
			BlockPos pos = BlockPos.of(e.getLongKey());
			if (!level.isLoaded(pos)) continue;
			BlockState cur = level.getBlockState(pos);
			if (!(cur.getBlock() instanceof BannerBlock)) continue;
			ops.add(new VillageRecord.Op(pos.asLong(), Blocks.BANNER.pick(HrafnLooks.tint(r, pos, color)).defaultBlockState().setValue(BannerBlock.ROTATION, cur.getValue(BannerBlock.ROTATION))));
		}
		return ops;
	}

	// ---- border-wall growth (Azmar) -----------------------------------------------------------------------------------

	/** Ops for growth step {@code step} (1..9): the ring wall gains a course (1-3), then a palm and lantern column each (4-6), then an obelisk and gate pillars (7-9). */
	public static List<VillageRecord.Op> growth(ServerLevel level, VillageRecord r, Profile p, int step, RandomSource rnd) {
		Ctx c = new Ctx(level, r, rnd, p);
		c.bell = Plans.surface(level, r.center);
		if (step <= 3) {
			// one more course on every plain wall column: swap the parapet slab for a block and put the slab on top
			perimeter(c, (ground, nearGate) -> {
				if (nearGate) return;
				BlockPos top = ground.above(2 + step);
				if (level.getBlockState(top).is(Blocks.CUT_SANDSTONE_SLAB)) {
					c.add(top, state(Blocks.SANDSTONE));
					c.add(top.above(), Blocks.CUT_SANDSTONE_SLAB.defaultBlockState());
				}
			}, false);
		} else if (step <= 6) {
			// lamp pillars on the wall: a sandstone column with a lantern on top, every ninth column
			int[] n = {0};
			perimeter(c, (ground, nearGate) -> {
				if (nearGate) return;
				n[0]++;
				if (n[0] % 9 == step % 9) {
					for (int h = 1; h <= 3 + step - 3; h++) c.add(ground.above(h), state(h % 2 == 0 ? Blocks.CHISELED_SANDSTONE : Blocks.CUT_SANDSTONE));
					c.add(ground.above(4 + step - 3), state(Blocks.LANTERN));
				}
			}, false);
		} else {
			BlockPos spot = Plans.freeArea(level, r, c.bell, 26, 3, 9, c.used, rnd);
			if (spot != null) obelisk(c, spot, 6 + step - 6);
		}
		return c.ops;
	}

	static boolean isHalf(BlockState s, Half h) {
		return s.hasProperty(BlockStateProperties.HALF) && s.getValue(BlockStateProperties.HALF) == h;
	}
}
