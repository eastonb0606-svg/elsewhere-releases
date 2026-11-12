package dev.elsewhere.kingdom_villages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the block plans: siege burn, fortified rebuild. Plans only list candidate ops; {@link BlockWriter} decides. */
public final class Plans {
	private Plans() {}

	public static final int MAX_BURN = 420, MAX_FORTIFY = 2600;

	static boolean burnable(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
			|| s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.WOOL) || s.is(Blocks.HAY_BLOCK) || s.is(BlockTags.LEAVES);
	}

	static boolean skip(BlockState s) {
		return s.isAir() || (s.hasBlockEntity() && !s.is(Blocks.BELL)) || (PoiTypes.hasPoi(s) && !s.is(Blocks.BELL)) || s.is(BlockTags.BEDS)
			|| s.is(BlockTags.DOORS) || BlockWriter.isPlayerBlock(s) || !s.getFluidState().isEmpty();
	}

	// ---- siege burn ---------------------------------------------------------------------------------------------------

	/** Fire on top of roofs and walls, charred planks, collapsed parts, trampled farms, the bell torn down. */
	public static List<VillageRecord.Op> burn(ServerLevel level, VillageRecord r, RandomSource rnd) {
		List<VillageRecord.Piece> houses = new ArrayList<>();
		for (VillageRecord.Piece p : r.pieces) if (p.type() == VillageRecord.HOUSE || p.type() == VillageRecord.CENTER) houses.add(p);
		Collections.shuffle(houses, new java.util.Random(rnd.nextLong()));
		Set<VillageRecord.Piece> collapse = new HashSet<>();
		for (int i = 0; i < houses.size() && collapse.size() < 2; i++) if (houses.get(i).type() == VillageRecord.HOUSE && rnd.nextInt(3) == 0) collapse.add(houses.get(i));
		List<VillageRecord.Op> ops = new ArrayList<>();
		BlockState fire = VillagesContent.SIEGE_FIRE.defaultBlockState(), charred = VillagesContent.CHARRED_PLANKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		Set<Long> fired = new HashSet<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (VillageRecord.Piece p : houses) {
			BoundingBox b = p.box();
			boolean col = collapse.contains(p);
			List<VillageRecord.Op> pieceOps = new ArrayList<>();
			for (int y = b.maxY(); y >= b.minY(); y--) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				m.set(x, y, z);
				if (!level.isLoaded(m)) continue;
				BlockState s = level.getBlockState(m);
				if (s.is(Blocks.BELL)) { pieceOps.add(new VillageRecord.Op(m.asLong(), air)); continue; }
				if (s.is(Blocks.FARMLAND)) { pieceOps.add(new VillageRecord.Op(m.asLong(), Blocks.DIRT.defaultBlockState())); continue; }
				if (s.is(BlockTags.CROPS)) { pieceOps.add(new VillageRecord.Op(m.asLong(), air)); continue; }
				if (skip(s) || !burnable(s)) continue;
				int roll = rnd.nextInt(100);
				BlockState target;
				if (col) target = rnd.nextInt(4) == 0 ? charred : air;
				else if (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS)) target = roll < 60 ? charred : null;
				else target = roll < 45 ? air : roll < 65 ? charred : null;
				if (target == null) continue;
				BlockPos above = m.above();
				if (rnd.nextInt(3) == 0 && level.getBlockState(above).isAir() && fired.add(above.asLong()) && r.insideFootprint(above, BlockWriter.UP))
					pieceOps.add(new VillageRecord.Op(above.asLong(), fire));
				pieceOps.add(new VillageRecord.Op(m.asLong(), target));
			}
			ops.addAll(pieceOps);
			if (ops.size() >= MAX_BURN) break;
		}
		return ops.size() > MAX_BURN ? new ArrayList<>(ops.subList(0, MAX_BURN)) : ops;
	}

	// ---- fortified rebuild --------------------------------------------------------------------------------------------

	/** The Aldermark style for an original village block, or null to leave it. */
	static @Nullable BlockState style(BlockState orig, int y, BoundingBox box) {
		if (orig.is(BlockTags.PLANKS)) return y < box.minY() + 2 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
		if (orig.is(BlockTags.LOGS)) return Blocks.STRIPPED_SPRUCE_LOG.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_STAIRS)) return Blocks.STONE_BRICK_STAIRS.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_SLABS)) return Blocks.STONE_BRICK_SLAB.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_FENCES)) return Blocks.SPRUCE_FENCE.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE)) return Blocks.STONE_BRICKS.defaultBlockState();
		if (orig.is(Blocks.MOSSY_COBBLESTONE)) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (orig.is(Blocks.COBBLESTONE_STAIRS)) return Blocks.STONE_BRICK_STAIRS.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_SLAB)) return Blocks.STONE_BRICK_SLAB.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_WALL)) return Blocks.STONE_BRICK_WALL.withPropertiesOf(orig);
		if (orig.is(BlockTags.CROPS)) return orig.getBlock().defaultBlockState();
		return null;
	}

	public record Fortify(List<VillageRecord.Op> ops, @Nullable BlockPos coffer, @Nullable BlockPos towerTop, @Nullable BlockPos taxPost) {}

	public static Fortify fortify(ServerLevel level, VillageRecord r, RandomSource rnd) {
		List<VillageRecord.Op> first = new ArrayList<>(), ops = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		Set<Long> used = new HashSet<>();
		BlockPos bell = null;
		// 1. restyle (and repair what the siege burned) house by house
		for (VillageRecord.Piece p : r.pieces) {
			if (p.type() != VillageRecord.HOUSE && p.type() != VillageRecord.CENTER) continue;
			BoundingBox b = p.box();
			for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				m.set(x, y, z);
				if (!level.isLoaded(m)) continue;
				BlockState cur = level.getBlockState(m);
				VillageRecord.Change ch = r.journal.get(m.asLong());
				BlockState orig = ch != null ? ch.original() : cur;
				if (orig.is(Blocks.BELL)) bell = m.immutable();
				if (ch == null && skip(cur)) continue;
				BlockState target = style(orig, y, b);
				if (target == null && ch != null) target = orig; // repair (bell, farmland, wool, hay, leaves)
				if (target == null || target == cur) continue;
				if (used.add(m.asLong())) ops.add(new VillageRecord.Op(m.asLong(), target));
			}
		}
		if (bell == null) bell = surface(level, r.center);
		BlockPos taxPost = bell;
		// 2. the tax office coffer (sealed) next to the bell, first, so a liberated town gets its Steward early
		BlockPos coffer = freeFloor(level, r, bell, 6, used);
		if (coffer != null) {
			first.add(new VillageRecord.Op(coffer.asLong(), VillagesContent.ROYAL_COFFER.defaultBlockState().setValue(RoyalCofferBlock.SEALED, true)));
			used.add(coffer.asLong());
		}
		// 3. watchtower, mess fire, tents
		BlockPos towerTop = null;
		BlockPos t = freeArea(level, r, bell, 24, 3, 7, used, rnd);
		if (t != null) {
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				boolean corner = dx != 0 && dz != 0;
				for (int dy = 0; dy < 5; dy++) if (corner) add(ops, used, t.offset(dx, dy, dz), Blocks.STONE_BRICKS.defaultBlockState());
				add(ops, used, t.offset(dx, 5, dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
				if (dx != 0 || dz != 0) add(ops, used, t.offset(dx, 6, dz), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
			add(ops, used, t.offset(1, 7, 1), Blocks.BANNER.blue().defaultBlockState());
			towerTop = t.above(6);
		}
		BlockPos fireAt = freeFloor(level, r, bell.offset(3, 0, 3), 8, used);
		if (fireAt != null) add(ops, used, fireAt, Blocks.CAMPFIRE.defaultBlockState());
		for (int i = 0; i < 2; i++) {
			BlockPos tent = freeArea(level, r, bell, 30, 3, 3, used, rnd);
			if (tent == null) break;
			for (int dz = -1; dz <= 1; dz++) {
				add(ops, used, tent.offset(-1, 0, dz), Blocks.WOOL.white().defaultBlockState());
				add(ops, used, tent.offset(1, 0, dz), Blocks.WOOL.white().defaultBlockState());
				add(ops, used, tent.offset(0, 1, dz), Blocks.WOOL.white().defaultBlockState());
				used.add(tent.offset(0, 0, dz).asLong());
			}
		}
		// 4. palisade along the village's outer edge on open ground; path cells are gates (stone posts + banners)
		Set<Long> cols = new HashSet<>();
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				if (x != b.minX() && x != b.maxX() && z != b.minZ() && z != b.maxZ()) continue;
				if (!cols.add(BlockPos.asLong(x, 0, z))) continue;
				boolean outer = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (!inAnyColumn(r, x + d.getStepX(), z + d.getStepZ())) outer = true;
				if (!outer || !level.isLoaded(m.set(x, b.minY(), z))) continue;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos ground = new BlockPos(x, top - 1, z);
				BlockState g = level.getBlockState(ground);
				if (!(g.is(Blocks.GRASS_BLOCK) || g.is(BlockTags.DIRT) || g.is(Blocks.SAND) || g.is(Blocks.SNOW_BLOCK) || g.is(Blocks.DIRT_PATH) || g.is(Blocks.GRAVEL))) continue;
				if (g.is(Blocks.DIRT_PATH)) continue; // gate
				boolean nearGate = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(ground.relative(d)).is(Blocks.DIRT_PATH)) nearGate = true;
				if (nearGate) {
					add(ops, used, ground.above(), Blocks.STONE_BRICK_WALL.defaultBlockState());
					add(ops, used, ground.above(2), Blocks.STONE_BRICK_WALL.defaultBlockState());
					add(ops, used, ground.above(3), Blocks.BANNER.blue().defaultBlockState());
				} else add(ops, used, ground.above(), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		// 5. Aldermark banners on the houses
		for (VillageRecord.Piece p : r.pieces) {
			if (p.type() != VillageRecord.HOUSE) continue;
			BlockPos c = p.box().getCenter();
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
			if (top <= p.box().maxY() + 2) add(ops, used, new BlockPos(c.getX(), top, c.getZ()), Blocks.BANNER.blue().defaultBlockState());
		}
		first.addAll(ops);
		first.removeIf(o -> !r.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP));
		if (first.size() > MAX_FORTIFY) first = new ArrayList<>(first.subList(0, MAX_FORTIFY));
		return new Fortify(first, coffer, towerTop, taxPost);
	}

	static void add(List<VillageRecord.Op> ops, Set<Long> used, BlockPos p, BlockState s) {
		if (used.add(p.asLong())) ops.add(new VillageRecord.Op(p.asLong(), s));
	}

	static boolean inAnyColumn(VillageRecord r, int x, int z) {
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) return true;
		}
		return false;
	}

	public static BlockPos surface(ServerLevel level, BlockPos p) {
		return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
	}

	/** A floor cell (air with a sturdy block under it and air above) inside the village pieces near {@code from}. */
	static @Nullable BlockPos freeFloor(ServerLevel level, VillageRecord r, BlockPos from, int radius, Set<Long> used) {
		for (int d = 1; d <= radius; d++) for (int dx = -d; dx <= d; dx++) for (int dz = -d; dz <= d; dz++) {
			if (Math.abs(dx) != d && Math.abs(dz) != d) continue;
			BlockPos s = surface(level, from.offset(dx, 0, dz));
			if (used.contains(s.asLong()) || !r.insidePieces(s) || !level.isLoaded(s)) continue;
			BlockState below = level.getBlockState(s.below());
			if (!level.getBlockState(s).isAir() || !level.getBlockState(s.above()).isAir()) continue;
			if (below.is(Blocks.DIRT_PATH) || below.is(Blocks.FARMLAND) || !below.isFaceSturdy(level, s.below(), Direction.UP)) continue;
			return s;
		}
		return null;
	}

	/** Centre of a flat {@code size}x{@code size} open area (air {@code height} high), in the pieces, away from paths. */
	static @Nullable BlockPos freeArea(ServerLevel level, VillageRecord r, BlockPos from, int radius, int size, int height, Set<Long> used, RandomSource rnd) {
		int h = size / 2;
		for (int tries = 0; tries < 80; tries++) {
			BlockPos c = surface(level, from.offset(rnd.nextInt(radius * 2 + 1) - radius, 0, rnd.nextInt(radius * 2 + 1) - radius));
			if (!level.isLoaded(c)) continue;
			boolean ok = true;
			for (int dx = -h; dx <= h && ok; dx++) for (int dz = -h; dz <= h && ok; dz++) {
				BlockPos q = c.offset(dx, 0, dz);
				if (used.contains(q.asLong()) || !r.insideFootprint(q, BlockWriter.UP) || !r.insideFootprint(q.above(height - 1), BlockWriter.UP)) { ok = false; break; }
				BlockState below = level.getBlockState(q.below());
				if (below.is(Blocks.DIRT_PATH) || below.is(Blocks.FARMLAND) || !below.isFaceSturdy(level, q.below(), Direction.UP)) { ok = false; break; }
				for (int dy = 0; dy < height; dy++) if (!level.getBlockState(q.above(dy)).isAir()) { ok = false; break; }
			}
			if (ok) return c;
		}
		return null;
	}

	// ---- outposts and hamlets (never-visited chunks) ------------------------------------------------------------------

	/** Palisade ring, 3x3 tower, banner and mess fire on flat open ground around {@code c} (the surface air block). */
	public static List<VillageRecord.Op> outpost(BlockPos c, Direction gate) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
			if (Math.abs(dx) != 5 && Math.abs(dz) != 5) continue;
			boolean gap = (gate.getStepX() != 0 ? dx == 5 * gate.getStepX() && Math.abs(dz) <= 1 : dz == 5 * gate.getStepZ() && Math.abs(dx) <= 1);
			if (!gap) add(ops, used, c.offset(dx, 0, dz), Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (dx != 0 && dz != 0) for (int dy = 0; dy < 4; dy++) add(ops, used, c.offset(dx, dy, dz), Blocks.COBBLESTONE.defaultBlockState());
			add(ops, used, c.offset(dx, 4, dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
			if (dx != 0 || dz != 0) add(ops, used, c.offset(dx, 5, dz), Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		add(ops, used, c.offset(-1, 6, -1), Blocks.BANNER.blue().defaultBlockState());
		add(ops, used, c.offset(3, 0, 3), Blocks.CAMPFIRE.defaultBlockState());
		return ops;
	}

	/** A small burned hamlet: charred corner posts and broken walls, ash, and a composter (a refugee's job site). */
	public static List<VillageRecord.Op> hamlet(BlockPos c, RandomSource rnd) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		BlockState charred = VillagesContent.CHARRED_PLANKS.defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
			if (corner) for (int dy = 0; dy < 2 + rnd.nextInt(2); dy++) add(ops, used, c.offset(dx, dy, dz), charred);
			else if (edge && rnd.nextInt(3) == 0) add(ops, used, c.offset(dx, 0, dz), charred);
			else if (!edge && rnd.nextInt(3) == 0) add(ops, used, c.offset(dx, 0, dz), VillagesContent.ASH.defaultBlockState());
		}
		add(ops, used, c.offset(4, 0, 1), Blocks.COMPOSTER.defaultBlockState());
		return ops;
	}

	static Block[] nothing() {
		return new Block[0];
	}
}
