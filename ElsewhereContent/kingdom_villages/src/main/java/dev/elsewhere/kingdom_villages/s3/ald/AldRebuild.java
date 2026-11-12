package dev.elsewhere.kingdom_villages.s3.ald;

import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.Plans;
import dev.elsewhere.kingdom_villages.RoyalCofferBlock;
import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.VillagesContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 3.0 Aldermark fortified rebuild of a held village (STRUCTURES.md 1.3, 3.1, 4 and 7.1; the baseline crown style, improved):
 * <ul>
 *   <li>houses restyled in place: hashed stone-brick footings (mossy and cracked), dark spruce timber frames, spruce plank upper
 *   storeys and deepslate-tile roofs; burned blocks repaired first;</li>
 *   <li>a watchtower on the village's high point (the highest 3x3 of natural ground among every candidate, not any flat spot): stone-footed
 *   corner posts that each go down to their own ground, a projecting timber hoarding on corbels, a steep deepslate spire (never a flat
 *   top) and the blue tower flag (the border hint);</li>
 *   <li>a contour palisade on the village's outer edge: contiguous spruce logs on every column's own ground (it steps with the slope),
 *   alternating fence and wall tips, gaps at doors, and two timber gatehouses (posts, lintel, slate cap, lantern, banner) on the main
 *   paths out; other paths get stone-footed posts;</li>
 *   <li>a camp near the bell: an unlit mess fire with log benches and a lantern post, two A-frame tents on level ground;</li>
 *   <li>chimneys and flag poles on the ridges of the bigger houses.</li>
 * </ul>
 * Like {@link Plans}, this only lists candidate ops; {@link BlockWriter} decides. Nothing is carved: every new block goes into air or a
 * soft block (grass, flowers, snow), so no box is ever cleared. Budgets (STRUCTURES.md 5): at most {@value #MAX_BANNERS} banners, a
 * handful of lanterns, no lit fire, no valuable block.
 */
public final class AldRebuild {
	private AldRebuild() {}

	public static final int MAX_OPS = 4200, MAX_BANNERS = 12, MAX_HOUSE_FLAGS = 6, MAX_CHIMNEYS = 8;
	/** VillageRecord piece type of the watchtower's own box (its spire rises above the village's piece boxes). */
	public static final int TOWER_PIECE = 41;
	/** Height of the tower top above its floor (flag included). */
	public static final int TOWER_HEIGHT = 15;

	/** Self-test readouts of the last plan. */
	public static int lastExits, lastGatehouses, lastWallCells, lastChimneys, lastFlags, lastTents, lastTowerGround = Integer.MIN_VALUE, lastBestGround = Integer.MIN_VALUE;
	public static @Nullable BlockPos lastTower;
	public static final List<BlockPos> lastGates = new ArrayList<>();

	// ---- restyle ------------------------------------------------------------------------------------------------------

	static boolean skip(BlockState s) {
		return s.isAir() || (s.hasBlockEntity() && !s.is(Blocks.BELL)) || (PoiTypes.hasPoi(s) && !s.is(Blocks.BELL)) || s.is(BlockTags.BEDS)
			|| s.is(BlockTags.DOORS) || BlockWriter.isPlayerBlock(s) || !s.getFluidState().isEmpty();
	}

	/** Aldermark's block for an original village block, or null to leave it. */
	public static @Nullable BlockState style(BlockState orig, int x, int y, int z, BoundingBox box) {
		boolean low = y < box.minY() + 2;
		int h = AldLook.hash(x, y, z);
		if (orig.is(BlockTags.PLANKS)) return low ? AldLook.bricks(x, y, z) : Blocks.SPRUCE_PLANKS.defaultBlockState();
		if (orig.is(BlockTags.LOGS)) return Blocks.SPRUCE_LOG.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_STAIRS)) return Blocks.DEEPSLATE_TILE_STAIRS.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_SLABS)) return Blocks.DEEPSLATE_TILE_SLAB.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_FENCES)) return Blocks.SPRUCE_FENCE.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE)) return AldLook.bricks(x, y, z);
		if (orig.is(Blocks.MOSSY_COBBLESTONE)) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (orig.is(Blocks.COBBLESTONE_STAIRS)) return (h < 15 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS).withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_SLAB)) return (h < 15 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB).withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_WALL)) return Blocks.STONE_BRICK_WALL.withPropertiesOf(orig);
		if (orig.is(BlockTags.CROPS)) return orig.getBlock().defaultBlockState();
		return null;
	}

	// ---- the plan -----------------------------------------------------------------------------------------------------

	public static Plans.Fortify fortify(ServerLevel level, VillageRecord r, RandomSource rnd) {
		Set<Long> used = new HashSet<>();
		AldOps repairs = new AldOps();
		List<AldOps> houses = new ArrayList<>();
		BlockPos bell = null;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		// 1. restyle (and repair what the siege burned) house by house
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() != VillageRecord.HOUSE && pc.type() != VillageRecord.CENTER) continue;
			BoundingBox b = pc.box();
			AldOps h = new AldOps();
			for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				m.set(x, y, z);
				if (!level.isLoaded(m)) continue;
				BlockState cur = level.getBlockState(m);
				VillageRecord.Change ch = r.journal.get(m.asLong());
				BlockState orig = ch != null ? ch.original() : cur;
				if (orig.is(Blocks.BELL)) bell = m.immutable();
				if (ch == null && skip(cur)) continue;
				BlockState target = style(orig, x, y, z, b);
				if (target == null && ch != null) target = orig; // repair (bell, farmland, wool, hay, leaves)
				if (target == null || target == cur) continue;
				if (!used.add(m.asLong())) continue;
				(ch != null ? repairs : h).set(m.immutable(), target);
			}
			houses.add(h);
		}
		if (bell == null) bell = Plans.surface(level, r.center);
		// 2. the tax office coffer (sealed) next to the bell, first, so a liberated town gets its Steward early
		BlockPos coffer = freeFloor(level, r, bell, 6, used);
		if (coffer != null) used.add(coffer.asLong());
		// 3. the watchtower on the high point, 4. the palisade and gatehouses, 5. the camp, 6. chimneys and flags
		AldOps tower = new AldOps(), walls = new AldOps(), camp = new AldOps(), roofs = new AldOps();
		int[] banners = {0};
		BlockPos towerTop = tower(level, r, bell, used, tower, banners);
		palisade(level, r, used, walls, banners);
		camp(level, r, bell, used, camp, rnd);
		roofs(level, r, bell, used, roofs, banners);
		// order: coffer, repairs, tower, walls, camp, whole houses while the budget lasts, roofs
		List<VillageRecord.Op> out = new ArrayList<>();
		if (coffer != null) out.add(new VillageRecord.Op(coffer.asLong(), VillagesContent.ROYAL_COFFER.defaultBlockState().setValue(RoyalCofferBlock.SEALED, true)));
		out.addAll(repairs.ops(level));
		out.addAll(tower.ops(level));
		out.addAll(walls.ops(level));
		out.addAll(camp.ops(level));
		for (AldOps h : houses) {
			List<VillageRecord.Op> l = h.ops(level);
			if (out.size() + l.size() <= MAX_OPS - roofs.size()) out.addAll(l);
		}
		out.addAll(roofs.ops(level));
		out.removeIf(o -> !r.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP));
		if (out.size() > MAX_OPS) out = new ArrayList<>(out.subList(0, MAX_OPS));
		return new Plans.Fortify(out, coffer, towerTop, bell);
	}

	/**
	 * True when the village's writer will accept a new block at {@code p}: inside a piece box, or air within the footprint at most
	 * {@link BlockWriter#UP} above a piece. Every column is checked whole before it is planned, so a refused bottom block can never
	 * leave the blocks above it floating.
	 */
	static boolean writable(ServerLevel level, VillageRecord r, BlockPos p) {
		if (!level.isLoaded(p)) return false;
		if (r.insidePieces(p)) return true;
		return level.getBlockState(p).isAir() && r.insideFootprint(p, BlockWriter.UP);
	}

	static boolean writable(ServerLevel level, VillageRecord r, int x, int y0, int y1, int z) {
		for (int y = y0; y <= y1; y++) if (!writable(level, r, new BlockPos(x, y, z))) return false;
		return true;
	}

	static boolean inAnyColumn(VillageRecord r, int x, int z) {
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) return true;
		}
		return false;
	}

	/** A floor cell (air with a sturdy block under it and air above) inside the village pieces near {@code from}. */
	static @Nullable BlockPos freeFloor(ServerLevel level, VillageRecord r, BlockPos from, int radius, Set<Long> used) {
		for (int d = 1; d <= radius; d++) for (int dx = -d; dx <= d; dx++) for (int dz = -d; dz <= d; dz++) {
			if (Math.abs(dx) != d && Math.abs(dz) != d) continue;
			BlockPos s = Plans.surface(level, from.offset(dx, 0, dz));
			if (used.contains(s.asLong()) || !r.insidePieces(s) || !level.isLoaded(s)) continue;
			BlockState below = level.getBlockState(s.below());
			if (!level.getBlockState(s).isAir() || !level.getBlockState(s.above()).isAir()) continue;
			if (below.is(Blocks.DIRT_PATH) || below.is(Blocks.FARMLAND) || !below.isFaceSturdy(level, s.below(), Direction.UP)) continue;
			return s;
		}
		return null;
	}

	/**
	 * The new owner's signature after a cosmetic swap (Aldermark and the default shapes): a flag pole on a stone footing, the
	 * banner on the pole (the 2.1 swap put a lone banner one block above the ground, which could not stand). {@code spot} is the
	 * free air cell on the ground.
	 */
	public static List<VillageRecord.Op> flagPostOps(BlockPos spot, net.minecraft.world.item.DyeColor color) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		ops.add(new VillageRecord.Op(spot.asLong(), AldLook.bricks(spot.getX(), spot.getY(), spot.getZ())));
		ops.add(new VillageRecord.Op(spot.above().asLong(), Blocks.SPRUCE_FENCE.defaultBlockState()));
		ops.add(new VillageRecord.Op(spot.above(2).asLong(), Blocks.SPRUCE_FENCE.defaultBlockState()));
		ops.add(new VillageRecord.Op(spot.above(3).asLong(), Blocks.BANNER.pick(color).defaultBlockState()));
		return ops;
	}

	// ---- watchtower ---------------------------------------------------------------------------------------------------

	record Spot(int x, int z, int floor, int low, int dist) {}

	/** The highest 3x3 of natural ground (spread at most 2) whose 5x5 roof fits; the tower; returns the archer's stand (towerTop). */
	static @Nullable BlockPos tower(ServerLevel level, VillageRecord r, BlockPos bell, Set<Long> used, AldOps o, int[] banners) {
		lastTower = null;
		lastTowerGround = lastBestGround = Integer.MIN_VALUE;
		r.pieces.removeIf(p -> p.type() == TOWER_PIECE); // a re-fortified town plans its tower afresh
		List<Spot> cands = new ArrayList<>();
		for (int x = bell.getX() - 28; x <= bell.getX() + 28; x += 2) for (int z = bell.getZ() - 28; z <= bell.getZ() + 28; z += 2) {
			boolean ok = true;
			for (int dx = -1; dx <= 1 && ok; dx++) for (int dz = -1; dz <= 1 && ok; dz++) ok = inAnyColumn(r, x + dx, z + dz);
			if (!ok) continue;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int dx = -1; dx <= 1 && ok; dx++) for (int dz = -1; dz <= 1 && ok; dz++) {
				int g = AldLook.ground(level, x + dx, z + dz);
				if (g == Integer.MIN_VALUE) { ok = false; break; }
				BlockPos gp = new BlockPos(x + dx, g, z + dz);
				BlockState gs = level.getBlockState(gp);
				if (!AldLook.natural(gs) || !gs.isFaceSturdy(level, gp, Direction.UP)) ok = false;
				lo = Math.min(lo, g);
				hi = Math.max(hi, g);
			}
			if (!ok || hi - lo > 2) continue;
			cands.add(new Spot(x, z, hi, lo, Math.abs(x - bell.getX()) + Math.abs(z - bell.getZ())));
			lastBestGround = Math.max(lastBestGround, hi);
		}
		cands.sort(Comparator.comparingInt((Spot s) -> -s.floor()).thenComparingInt(s -> s.floor() - s.low()).thenComparingInt(Spot::dist));
		Spot pick = null;
		for (int i = 0; i < cands.size() && i < 32 && pick == null; i++) if (fits(level, r, cands.get(i), used)) pick = cands.get(i);
		if (pick == null) return null;
		int cx = pick.x(), cz = pick.z(), f = pick.floor();
		lastTower = new BlockPos(cx, f, cz);
		lastTowerGround = f;
		// stone-footed corner posts, each from its own ground; stone up to 2 above the floor, spruce timber above
		for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) {
			int g = AldLook.ground(level, cx + dx, cz + dz);
			AldLook.clearPlantTop(level, o, cx + dx, g + 1, cz + dz);
			for (int y = g + 1; y <= f + 9; y++) o.set(cx + dx, y, cz + dz, y <= f + 2 ? AldLook.bricks(cx + dx, y, cz + dz) : Blocks.SPRUCE_LOG.defaultBlockState());
		}
		// the archers' floor and the hoarding gallery on upside-down-stair corbels, fence rail around it
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
			if (!ring) o.set(cx + dx, f + 6, cz + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
			else {
				o.set(cx + dx, f + 6, cz + dz, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
				o.set(cx + dx, f + 7, cz + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
				if (!corner) {
					Direction in = Math.abs(dx) == 2 ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
					o.set(cx + dx, f + 5, cz + dz, AldLook.stair(Blocks.SPRUCE_STAIRS, in, Half.TOP));
				}
			}
		}
		o.hang(new BlockPos(cx, f + 5, cz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		// the steep deepslate spire (2 rows per step) and the tower flag
		int top = spire(o, cx, cz, 2, f + 9, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES.defaultBlockState());
		o.set(cx, top, cz, Blocks.SPRUCE_FENCE.defaultBlockState());
		BlockPos toward = r.castle != null ? r.castle : bell;
		o.set(cx, top + 1, cz, Blocks.BANNER.blue().defaultBlockState().setValue(BannerBlock.ROTATION, AldLook.rotationToward(new BlockPos(cx, top, cz), toward)));
		banners[0]++;
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) used.add(BlockPos.asLong(cx + dx, AldLook.ground(level, cx + dx, cz + dz) + 1, cz + dz));
		// the tower's own box: its spire rises above the village's pieces (same columns, so the village's outer edge is unchanged)
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(cx - 2, pick.low() + 1, cz - 2, cx + 2, top + 2, cz + 2), TOWER_PIECE));
		return new BlockPos(cx, f + 7, cz);
	}

	static boolean fits(ServerLevel level, VillageRecord r, Spot s, Set<Long> used) {
		int f = s.floor();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			int x = s.x() + dx, z = s.z() + dz;
			boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
			int g = AldLook.ground(level, x, z);
			if (inner) {
				if (!AldLook.clear(level, x, g + 1, f + TOWER_HEIGHT + 1, z) || AldLook.nearDoor(level, x, g, z)) return false;
				if (used.contains(BlockPos.asLong(x, g + 1, z))) return false;
			} else if (g > f + 3 || !AldLook.clear(level, x, f + 4, f + TOWER_HEIGHT + 1, z)) return false;
		}
		return true;
	}

	/** A steep square spire (CastleKit's Aldermark spire): stair rings with full rings between, two full blocks on top. Returns the y above it. */
	public static int spire(AldOps o, int cx, int cz, int hs, int y0, Block stairs, BlockState full) {
		int y = y0;
		ring(o, cx, cz, hs, y, stairs, full);
		under(o, cx, cz, hs - 1, y++, full);
		for (int s = hs - 1; s >= 1; s--) {
			for (int dx = -s; dx <= s; dx++) for (int dz = -s; dz <= s; dz++) if (Math.abs(dx) == s || Math.abs(dz) == s) o.set(cx + dx, y, cz + dz, full);
			y++;
			ring(o, cx, cz, s, y, stairs, full);
			under(o, cx, cz, s - 1, y++, full);
		}
		o.set(cx, y++, cz, full);
		o.set(cx, y++, cz, full);
		return y;
	}

	/**
	 * The hidden course inside a stair ring: the next step up (half-size {@code s}, or the peak when {@code s} is 0) gets a full block
	 * under every cell, so each step stands on the one below instead of touching it only at an edge (s-asm-vill: spire tops floated).
	 */
	static void under(AldOps o, int cx, int cz, int s, int y, BlockState full) {
		if (s < 0) return;
		for (int dx = -s; dx <= s; dx++) for (int dz = -s; dz <= s; dz++) if (Math.abs(dx) == s || Math.abs(dz) == s) o.set(cx + dx, y, cz + dz, full);
	}

	/** A ring of stairs at half-size {@code s} whose high side faces the centre, full blocks on the corners. */
	public static void ring(AldOps o, int cx, int cz, int s, int y, Block stairs, BlockState corner) {
		for (int dx = -s; dx <= s; dx++) for (int dz = -s; dz <= s; dz++) {
			boolean ex = Math.abs(dx) == s, ez = Math.abs(dz) == s;
			if (!ex && !ez) continue;
			if (ex && ez) o.set(cx + dx, y, cz + dz, corner);
			else {
				Direction in = ex ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
				o.set(cx + dx, y, cz + dz, AldLook.stair(stairs, in, Half.BOTTOM));
			}
		}
	}

	// ---- palisade and gatehouses --------------------------------------------------------------------------------------

	static final class Cell {
		final int x, z, g;
		final boolean path;
		boolean post;
		int comp = -1;

		Cell(int x, int z, int g, boolean path) {
			this.x = x;
			this.z = z;
			this.g = g;
			this.path = path;
		}
	}

	static long col(int x, int z) {
		return BlockPos.asLong(x, 0, z);
	}

	/** The outer-edge columns of the village, classified: open natural ground (wall), a path out (opening), or nothing to build. */
	static Map<Long, Cell> edge(ServerLevel level, VillageRecord r) {
		Map<Long, Cell> cells = new LinkedHashMap<>();
		Set<Long> seen = new HashSet<>();
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() == TOWER_PIECE) continue;
			BoundingBox b = pc.box();
			for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				if (x != b.minX() && x != b.maxX() && z != b.minZ() && z != b.maxZ()) continue;
				if (!seen.add(col(x, z))) continue;
				boolean outer = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (!inAnyColumn(r, x + d.getStepX(), z + d.getStepZ())) outer = true;
				if (!outer) continue;
				int g = AldLook.ground(level, x, z);
				if (g == Integer.MIN_VALUE) continue;
				BlockPos gp = new BlockPos(x, g, z);
				BlockState gs = level.getBlockState(gp);
				if (gs.is(Blocks.DIRT_PATH)) {
					if (AldLook.clear(level, x, g + 1, g + 3, z)) cells.put(col(x, z), new Cell(x, z, g, true));
				} else if (AldLook.natural(gs) && gs.isFaceSturdy(level, gp, Direction.UP) && AldLook.clear(level, x, g + 1, g + 4, z) && writable(level, r, x, g + 1, g + 4, z)) {
					cells.put(col(x, z), new Cell(x, z, g, AldLook.nearDoor(level, x, g, z)));
				}
			}
		}
		return cells;
	}

	static void palisade(ServerLevel level, VillageRecord r, Set<Long> used, AldOps o, int[] banners) {
		lastGates.clear();
		Map<Long, Cell> cells = edge(level, r);
		// openings: 8-connected runs of path (or door) cells; their wall neighbours become posts
		List<List<Cell>> comps = new ArrayList<>();
		for (Cell c : cells.values()) {
			if (!c.path || c.comp >= 0) continue;
			List<Cell> comp = new ArrayList<>();
			ArrayDeque<Cell> q = new ArrayDeque<>();
			c.comp = comps.size();
			q.add(c);
			while (!q.isEmpty()) {
				Cell a = q.poll();
				comp.add(a);
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
					Cell n = cells.get(col(a.x + dx, a.z + dz));
					if (n == null) continue;
					if (n.path && n.comp < 0) {
						n.comp = c.comp;
						q.add(n);
					} else if (!n.path) n.post = true;
				}
			}
			comps.add(comp);
		}
		lastExits = comps.size();
		// the two gatehouses: the opening facing the castle (or the biggest), then the one farthest from it
		List<List<Cell>> gateable = new ArrayList<>();
		for (List<Cell> comp : comps) if (comp.size() >= 2 && comp.size() <= 7) gateable.add(comp);
		List<List<Cell>> gates = new ArrayList<>();
		if (!gateable.isEmpty()) {
			BlockPos ctr = r.center;
			List<Cell> main = gateable.get(0);
			double best = -1e9;
			for (List<Cell> comp : gateable) {
				double[] c = centroid(comp);
				double score = comp.size();
				if (r.castle != null) {
					double ax = c[0] - ctr.getX(), az = c[1] - ctr.getZ(), bx = r.castle.getX() - ctr.getX(), bz = r.castle.getZ() - ctr.getZ();
					score = (ax * bx + az * bz) / (Math.hypot(ax, az) * Math.hypot(bx, bz) + 1e-6) * 10 + comp.size() * 0.1;
				}
				if (score > best) {
					best = score;
					main = comp;
				}
			}
			gates.add(main);
			double[] mc = centroid(main);
			List<Cell> second = null;
			double far = 16 * 16;
			for (List<Cell> comp : gateable) {
				if (comp == main) continue;
				double[] c = centroid(comp);
				double d = (c[0] - mc[0]) * (c[0] - mc[0]) + (c[1] - mc[1]) * (c[1] - mc[1]);
				if (d > far) {
					far = d;
					second = comp;
				}
			}
			if (second != null) gates.add(second);
		}
		Set<Long> gatePosts = new HashSet<>();
		lastGatehouses = 0;
		for (List<Cell> comp : gates) if (gatehouse(level, r, cells, comp, o, banners, gatePosts, gates.indexOf(comp) == 0)) lastGatehouses++;
		// the wall: 3 spruce logs on every column's own ground, a fence or wall tip, posts beside every opening
		int n = 0;
		for (Cell c : cells.values()) {
			if (c.path || gatePosts.contains(col(c.x, c.z))) continue;
			if (used.contains(BlockPos.asLong(c.x, c.g + 1, c.z))) continue;
			AldLook.clearPlantTop(level, o, c.x, c.g + 1, c.z);
			if (c.post) {
				o.set(c.x, c.g + 1, c.z, AldLook.bricks(c.x, c.g + 1, c.z));
				o.set(c.x, c.g + 2, c.z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
				o.set(c.x, c.g + 3, c.z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
				o.loose(new BlockPos(c.x, c.g + 4, c.z), Blocks.STONE_BRICK_WALL.defaultBlockState());
			} else {
				for (int h = 1; h <= 3; h++) o.set(c.x, c.g + h, c.z, Blocks.SPRUCE_LOG.defaultBlockState());
				o.loose(new BlockPos(c.x, c.g + 4, c.z), ((c.x + c.z) & 1) == 0 ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState());
				n++;
			}
		}
		lastWallCells = n;
	}

	static double[] centroid(List<Cell> comp) {
		double x = 0, z = 0;
		for (Cell c : comp) {
			x += c.x;
			z += c.z;
		}
		return new double[] {x / comp.size(), z / comp.size()};
	}

	/** Timber gatehouse over an opening: stone-footed stripped-spruce posts, a lintel 3 clear above the path, a slate cap, a lantern under it. */
	static boolean gatehouse(ServerLevel level, VillageRecord r, Map<Long, Cell> cells, List<Cell> comp, AldOps o, int[] banners, Set<Long> gatePosts, boolean main) {
		if (comp.get(0).g == Integer.MIN_VALUE || !comp.get(0).path) return false;
		List<Cell> posts = new ArrayList<>();
		for (Cell a : comp) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			Cell n = cells.get(col(a.x + dx, a.z + dz));
			if (n != null && !n.path && !posts.contains(n)) posts.add(n);
		}
		if (posts.size() < 2) return false;
		int h = Integer.MIN_VALUE;
		for (Cell c : comp) h = Math.max(h, c.g);
		for (Cell c : posts) h = Math.max(h, c.g);
		h += 4;
		// everything must fit: clear above the path up to the cap, inside the village's footprint
		for (Cell c : comp) if (!AldLook.clear(level, c.x, c.g + 1, h + 1, c.z) || !writable(level, r, c.x, h - 1, h + 1, c.z)) return false;
		for (Cell c : posts) if (!AldLook.clear(level, c.x, c.g + 1, h + 2, c.z) || !writable(level, r, c.x, c.g + 1, h + 2, c.z)) return false;
		for (Cell c : posts) {
			gatePosts.add(col(c.x, c.z));
			AldLook.clearPlantTop(level, o, c.x, c.g + 1, c.z);
			o.set(c.x, c.g + 1, c.z, AldLook.bricks(c.x, c.g + 1, c.z));
			for (int y = c.g + 2; y <= h; y++) o.set(c.x, y, c.z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
			o.set(c.x, h + 1, c.z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
		}
		for (Cell c : comp) {
			o.set(c.x, h, c.z, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
			o.set(c.x, h + 1, c.z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
		}
		double[] mid = centroid(comp);
		Cell lamp = comp.get(0);
		for (Cell c : comp) if (Math.hypot(c.x - mid[0], c.z - mid[1]) < Math.hypot(lamp.x - mid[0], lamp.z - mid[1])) lamp = c;
		o.hang(new BlockPos(lamp.x, h - 1, lamp.z), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		if (banners[0] < MAX_BANNERS) {
			Cell p = posts.get(0);
			BlockPos at = new BlockPos(p.x, h + 2, p.z);
			o.set(at, Blocks.BANNER.blue().defaultBlockState().setValue(BannerBlock.ROTATION, AldLook.rotationToward(r.center, at)));
			banners[0]++;
		}
		lastGates.add(new BlockPos(lamp.x, h, lamp.z));
		return true;
	}

	// ---- camp ---------------------------------------------------------------------------------------------------------

	/** An unlit mess fire with two log benches and a lantern post near the bell, and two A-frame tents on level ground. */
	static void camp(ServerLevel level, VillageRecord r, BlockPos bell, Set<Long> used, AldOps o, RandomSource rnd) {
		BlockPos fire = null;
		for (int d = 3; d <= 10 && fire == null; d++) for (int dx = -d; dx <= d && fire == null; dx++) for (int dz = -d; dz <= d && fire == null; dz++) {
			if (Math.abs(dx) != d && Math.abs(dz) != d) continue;
			int x = bell.getX() + dx, z = bell.getZ() + dz;
			if (!spotOk(level, r, used, x, z, 2)) continue;
			fire = new BlockPos(x, AldLook.ground(level, x, z) + 1, z);
		}
		if (fire != null) {
			AldLook.clearPlantTop(level, o, fire.getX(), fire.getY(), fire.getZ());
			o.set(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
			used.add(fire.asLong());
			for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
				int x = fire.getX() + d.getStepX() * 2, z = fire.getZ() + d.getStepZ() * 2;
				if (!spotOk(level, r, used, x, z, 1)) continue;
				int y = AldLook.ground(level, x, z) + 1;
				AldLook.clearPlantTop(level, o, x, y, z);
				o.set(x, y, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
				used.add(BlockPos.asLong(x, y, z));
			}
			int lx = fire.getX() + 2, lz = fire.getZ();
			if (spotOk(level, r, used, lx, lz, 2)) {
				int y = AldLook.ground(level, lx, lz) + 1;
				AldLook.clearPlantTop(level, o, lx, y, lz);
				o.set(lx, y, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
				o.set(lx, y + 1, lz, Blocks.LANTERN.defaultBlockState());
				used.add(BlockPos.asLong(lx, y, lz));
			}
		}
		int tents = 0;
		for (int tries = 0; tries < 160 && tents < 2; tries++) {
			int x = bell.getX() + rnd.nextInt(61) - 30, z = bell.getZ() + rnd.nextInt(61) - 30;
			boolean alongX = rnd.nextBoolean();
			if (tent(level, r, used, o, x, z, alongX)) tents++;
		}
		lastTents = tents;
	}

	static boolean spotOk(ServerLevel level, VillageRecord r, Set<Long> used, int x, int z, int height) {
		if (!AldLook.buildable(level, x, z, height)) return false;
		int g = AldLook.ground(level, x, z);
		if (used.contains(BlockPos.asLong(x, g + 1, z)) || AldLook.nearDoor(level, x, g, z)) return false;
		if (!writable(level, r, x, g + 1, g + height, z)) return false;
		return r.insidePieces(new BlockPos(x, g + 1, z)) || r.insidePieces(new BlockPos(x, g, z));
	}

	/** A white A-frame tent (3 long, 3 wide, poles at both ends) on level natural ground; false when the spot does not fit. */
	static boolean tent(ServerLevel level, VillageRecord r, Set<Long> used, AldOps o, int x, int z, boolean alongX) {
		int g = AldLook.ground(level, x, z);
		if (g == Integer.MIN_VALUE) return false;
		for (int u = -2; u <= 2; u++) for (int v = -1; v <= 1; v++) {
			int cx = alongX ? x + u : x + v, cz = alongX ? z + v : z + u;
			if (!spotOk(level, r, used, cx, cz, 3) || AldLook.ground(level, cx, cz) != g) return false;
		}
		BlockState wool = Blocks.WOOL.white().defaultBlockState();
		BlockState sa, sb, ridge;
		BlockState ws = Blocks.WOOL_STAIRS.white().defaultBlockState(), wl = Blocks.WOOL_SLAB.white().defaultBlockState();
		Direction pv = alongX ? Direction.SOUTH : Direction.EAST, nv = pv.getOpposite();
		if (ws.hasProperty(StairBlock.FACING) && ws.hasProperty(StairBlock.HALF)) {
			sa = ws.setValue(StairBlock.FACING, pv).setValue(StairBlock.HALF, Half.BOTTOM); // v = -1 side, high side toward the ridge
			sb = ws.setValue(StairBlock.FACING, nv).setValue(StairBlock.HALF, Half.BOTTOM);
		} else sa = sb = wool;
		ridge = wl.hasProperty(SlabBlock.TYPE) ? wl.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : wool;
		for (int u = -2; u <= 2; u++) for (int v = -1; v <= 1; v++) {
			int cx = alongX ? x + u : x + v, cz = alongX ? z + v : z + u;
			used.add(BlockPos.asLong(cx, g + 1, cz));
			AldLook.clearPlantTop(level, o, cx, g + 1, cz);
			if (Math.abs(u) == 2) {
				if (v == 0) {
					o.set(cx, g + 1, cz, Blocks.SPRUCE_FENCE.defaultBlockState());
					o.set(cx, g + 2, cz, Blocks.SPRUCE_FENCE.defaultBlockState());
				}
				continue;
			}
			if (v == -1) o.set(cx, g + 1, cz, sa);
			else if (v == 1) o.set(cx, g + 1, cz, sb);
			else o.set(cx, g + 2, cz, ridge);
		}
		return true;
	}

	// ---- chimneys and flags -------------------------------------------------------------------------------------------

	/** On the bigger houses' pitched roofs: a stone chimney at one end of the ridge, a flag pole at the other. */
	static void roofs(ServerLevel level, VillageRecord r, BlockPos bell, Set<Long> used, AldOps o, int[] banners) {
		List<VillageRecord.Piece> hs = new ArrayList<>();
		for (VillageRecord.Piece pc : r.pieces) if (pc.type() == VillageRecord.HOUSE && pc.box().getXSpan() >= 5 && pc.box().getZSpan() >= 5) hs.add(pc);
		hs.sort(Comparator.comparingInt((VillageRecord.Piece p) -> -p.box().getXSpan() * p.box().getZSpan()));
		int chimneys = 0, flags = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (VillageRecord.Piece pc : hs) {
			if (chimneys >= MAX_CHIMNEYS && flags >= MAX_HOUSE_FLAGS) break;
			BoundingBox b = pc.box();
			boolean alongX = b.getXSpan() >= b.getZSpan();
			int best = Integer.MIN_VALUE;
			List<int[]> ridge = new ArrayList<>();
			for (int x = b.minX() + 1; x <= b.maxX() - 1; x++) for (int z = b.minZ() + 1; z <= b.maxZ() - 1; z++) {
				if (!level.isLoaded(m.set(x, b.minY(), z))) continue;
				// the original roof (journal first: the siege may have burned it, the repair puts it back)
				int top = Integer.MIN_VALUE;
				BlockState ts = null;
				for (int y = b.maxY() + 2; y >= b.minY(); y--) {
					m.set(x, y, z);
					VillageRecord.Change ch = r.journal.get(m.asLong());
					BlockState s = ch != null ? ch.original() : level.getBlockState(m);
					if (s.isAir()) continue;
					top = y;
					ts = s;
					break;
				}
				if (ts == null || !(ts.is(BlockTags.WOODEN_STAIRS) || ts.is(BlockTags.WOODEN_SLABS))) continue;
				if (top > best) {
					best = top;
					ridge.clear();
				}
				if (top == best) ridge.add(new int[] {x, z});
			}
			if (ridge.size() < 2) continue;
			ridge.sort(Comparator.comparingInt(a -> alongX ? a[0] : a[1]));
			int[] a = ridge.get(0), e = ridge.get(ridge.size() - 1);
			if (chimneys < MAX_CHIMNEYS && freeAbove(level, r, a[0], best, a[1], 3)) {
				o.set(a[0], best, a[1], AldLook.bricks(a[0], best, a[1])); // through the ridge
				o.set(a[0], best + 1, a[1], AldLook.bricks(a[0], best + 1, a[1]));
				o.set(a[0], best + 2, a[1], AldLook.bricks(a[0], best + 2, a[1]));
				o.loose(new BlockPos(a[0], best + 3, a[1]), Blocks.STONE_BRICK_WALL.defaultBlockState());
				chimneys++;
			}
			if (flags < MAX_HOUSE_FLAGS && banners[0] < MAX_BANNERS && (e[0] != a[0] || e[1] != a[1]) && freeAbove(level, r, e[0], best, e[1], 2)) {
				o.set(e[0], best + 1, e[1], Blocks.SPRUCE_FENCE.defaultBlockState());
				BlockPos at = new BlockPos(e[0], best + 2, e[1]);
				o.set(at, Blocks.BANNER.blue().defaultBlockState().setValue(BannerBlock.ROTATION, AldLook.rotationToward(at, bell)));
				flags++;
				banners[0]++;
			}
		}
		lastChimneys = chimneys;
		lastFlags = flags;
	}

	static boolean freeAbove(ServerLevel level, VillageRecord r, int x, int top, int z, int n) {
		for (int y = top + 1; y <= top + n; y++) {
			BlockPos p = new BlockPos(x, y, z);
			if (!level.getBlockState(p).isAir() || r.journal.containsKey(p.asLong()) || !r.insideFootprint(p, BlockWriter.UP)) return false;
		}
		return true;
	}
}
