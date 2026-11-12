package dev.elsewhere.kingdom_villages.s3.ald;

import dev.elsewhere.kingdom_villages.Conquest;
import dev.elsewhere.kingdom_villages.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 3.0 Aldermark outpost (STRUCTURES.md 3.1 "Fort (outpost): motte-and-bailey"): a stone-footed timber tower with a roofed
 * hoarding on the highest point within 24 of the chosen spot, a log palisade that follows the ground around a small bailey (every
 * column on its own ground, so the ring steps with the slope), the gate facing the castle under a timber lintel, a plinth under the
 * tower pad with a stair batter where the ground falls away, a beaten track from gate to tower and an unlit mess fire in the yard.
 * Budgets (STRUCTURES.md 5): at most {@value #MAX_OPS} ops, 2 block entities (flag, fire), 3 lights; one blue banner (the flag and
 * the border hint). The old {@code flatSpot} 13x13 flat test becomes {@link #site}: a pad-only test plus a natural ring.
 *
 * <p>Local frame: {@code u} runs from the tower toward the gate (the castle side), {@code v} across it. The tower stands at u = 0,
 * the bailey's yard centre (the record's centre, where the garrison musters) at u = {@value #YARD}.
 */
public final class AldFort {
	private AldFort() {}

	public static final int MAX_OPS = 900, YARD = 6, RING = 7, PAD_SPREAD = 3;

	/** The plan of one fort. {@code flag} is the blue banner (hint and capture banner), {@code yard} the record centre. */
	public record Plan(List<VillageRecord.Op> ops, BlockPos flag, BoundingBox box, BlockPos yard, BlockPos roadStart, BlockPos tower, int floor) {}

	/** Self-test readouts. */
	public static int lastRingCells, lastRingLevels, lastPlinth, lastBatter;

	/** Direction from {@code c} toward the castle (the gate side). */
	public static Direction gateDir(BlockPos c, BlockPos castle) {
		return Direction.getApproximateNearest((double) (castle.getX() - c.getX()), 0.0, (double) (castle.getZ() - c.getZ()));
	}

	/**
	 * The motte for a fort near {@code p}: the highest pad within 24 whose whole fort fits (pad spread at most {@value #PAD_SPREAD},
	 * natural ground with no water or trees on the ring and pad, foundations within the cap) on fresh chunks. Null when none fits.
	 */
	public static @Nullable BlockPos site(ServerLevel level, BlockPos p, BlockPos castle) {
		record Cand(int x, int z, int g) {}
		List<Cand> cands = new ArrayList<>();
		for (int dx = -24; dx <= 24; dx += 3) for (int dz = -24; dz <= 24; dz += 3) {
			if (dx * dx + dz * dz > 24 * 24) continue;
			int x = p.getX() + dx, z = p.getZ() + dz, g = AldLook.ground(level, x, z);
			if (g == Integer.MIN_VALUE || !AldLook.natural(level.getBlockState(new BlockPos(x, g, z)))) continue;
			cands.add(new Cand(x, z, g));
		}
		cands.sort(Comparator.comparingInt((Cand c) -> -c.g()));
		int tried = 0;
		for (Cand c : cands) {
			if (tried++ >= 16) break;
			BlockPos t = new BlockPos(c.x(), c.g() + 1, c.z());
			Plan plan = plan(level, t, castle);
			if (plan == null) continue;
			if (!Conquest.freshChunkPublic(level, plan.tower()) || !Conquest.freshChunkPublic(level, plan.yard())) continue;
			return t;
		}
		return null;
	}

	static BlockPos at(BlockPos t, Direction u, int du, int dv) {
		Direction v = u.getClockWise();
		return new BlockPos(t.getX() + u.getStepX() * du + v.getStepX() * dv, 0, t.getZ() + u.getStepZ() * du + v.getStepZ() * dv);
	}

	/** Distance from local (du, dv) to the tower-yard segment (u in 0..YARD, v = 0). */
	static double seg(int du, int dv) {
		double cu = Math.max(0, Math.min(YARD, du));
		return Math.hypot(du - cu, dv);
	}

	/** The fort around the motte at {@code motte} (any y; the ground is read per column), or null when it does not fit there. */
	public static @Nullable Plan plan(ServerLevel level, BlockPos motte, BlockPos castle) {
		Direction gd = gateDir(motte, castle), vd = gd.getClockWise();
		BlockPos t = new BlockPos(motte.getX(), 0, motte.getZ());
		// the tower pad: 5x5, natural, spread at most PAD_SPREAD, clear above to the flag
		int f = Integer.MIN_VALUE, lo = Integer.MAX_VALUE;
		for (int du = -2; du <= 2; du++) for (int dv = -2; dv <= 2; dv++) {
			BlockPos c = at(t, gd, du, dv);
			int g = AldLook.ground(level, c.getX(), c.getZ());
			if (g == Integer.MIN_VALUE) return null;
			BlockState gs = level.getBlockState(c.atY(g));
			if (!AldLook.natural(gs) || !gs.isFaceSturdy(level, c.atY(g), Direction.UP)) return null;
			f = Math.max(f, g);
			lo = Math.min(lo, g);
		}
		if (f - lo > PAD_SPREAD) return null;
		for (int du = -3; du <= 3; du++) for (int dv = -3; dv <= 3; dv++) {
			BlockPos c = at(t, gd, du, dv);
			int g = AldLook.ground(level, c.getX(), c.getZ());
			if (g == Integer.MIN_VALUE || g > f + 1) return null; // the motte top: nothing in the hoarding ring stands above the pad
			if (!AldLook.clear(level, c.getX(), Math.min(g + 1, f + 1), f + 20, c.getZ())) return null;
		}
		AldOps o = new AldOps();
		int ringLevels = 0, plinth = 0, batter = 0;
		// 1. plinth: the pad's columns are filled up to the floor with the foundation ladder (a mound under the tower, not a cut)
		for (int du = -2; du <= 2; du++) for (int dv = -2; dv <= 2; dv++) {
			BlockPos c = at(t, gd, du, dv);
			int g = AldLook.ground(level, c.getX(), c.getZ());
			if (g >= f) continue;
			if (f - g > AldLook.FOUNDATION_CAP) return null;
			AldLook.clearPlantTop(level, o, c.getX(), g + 1, c.getZ());
			AldLook.underpin(level, o, c.getX(), f, c.getZ());
			plinth += f - g;
		}
		// 2. batter: a stone-brick stair skirt round the pad where the ground falls away, high side toward the tower
		for (int du = -3; du <= 3; du++) for (int dv = -3; dv <= 3; dv++) {
			if (Math.abs(du) != 3 && Math.abs(dv) != 3) continue;
			BlockPos c = at(t, gd, du, dv);
			int g = AldLook.ground(level, c.getX(), c.getZ());
			if (g >= f) continue;
			boolean corner = Math.abs(du) == 3 && Math.abs(dv) == 3;
			Direction in = Math.abs(du) == 3 ? (du > 0 ? gd.getOpposite() : gd) : (dv > 0 ? vd.getOpposite() : vd);
			AldLook.clearPlantTop(level, o, c.getX(), g + 1, c.getZ());
			o.set(c.getX(), f, c.getZ(), corner ? AldLook.bricks(c.getX(), f, c.getZ()) : AldLook.stair(Blocks.STONE_BRICK_STAIRS, in, Half.BOTTOM));
			if (f - 1 > g) AldLook.underpin(level, o, c.getX(), f - 1, c.getZ());
			batter++;
		}
		// 3. the tower: stone base f+1..f+3 (doorway on the yard side), timber storey f+4..f+7 with slits, hoarding at f+8 on corbels,
		//    a fence rail, corner posts and a steep deepslate roof, the flag on its peak
		for (int du = -2; du <= 2; du++) for (int dv = -2; dv <= 2; dv++) {
			if (Math.abs(du) != 2 && Math.abs(dv) != 2) continue;
			BlockPos c = at(t, gd, du, dv);
			boolean corner = Math.abs(du) == 2 && Math.abs(dv) == 2;
			int g = AldLook.ground(level, c.getX(), c.getZ());
			if (g >= f) AldLook.clearPlantTop(level, o, c.getX(), f + 1, c.getZ());
			for (int y = f + 1; y <= f + 7; y++) {
				boolean door = du == 2 && dv == 0 && y <= f + 2;
				boolean slit = !corner && (du == 0 || dv == 0) && y == f + 6;
				if (door || slit) continue;
				BlockState s = y <= f + 3 ? AldLook.bricks(c.getX(), y, c.getZ())
					: corner ? Blocks.SPRUCE_LOG.defaultBlockState() : y == f + 4 ? Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
				o.set(c.getX(), y, c.getZ(), s);
			}
		}
		for (int du = -3; du <= 3; du++) for (int dv = -3; dv <= 3; dv++) {
			BlockPos c = at(t, gd, du, dv);
			boolean ring = Math.abs(du) == 3 || Math.abs(dv) == 3, corner = Math.abs(du) == 3 && Math.abs(dv) == 3;
			if (!ring) o.set(c.getX(), f + 8, c.getZ(), Blocks.SPRUCE_PLANKS.defaultBlockState());
			else {
				o.set(c.getX(), f + 8, c.getZ(), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
				o.set(c.getX(), f + 9, c.getZ(), Blocks.SPRUCE_FENCE.defaultBlockState());
				if (!corner) {
					Direction in = Math.abs(du) == 3 ? (du > 0 ? gd.getOpposite() : gd) : (dv > 0 ? vd.getOpposite() : vd);
					o.set(c.getX(), f + 7, c.getZ(), AldLook.stair(Blocks.SPRUCE_STAIRS, in, Half.TOP));
				}
			}
		}
		for (int du = -2; du <= 2; du += 4) for (int dv = -2; dv <= 2; dv += 4) {
			BlockPos c = at(t, gd, du, dv);
			o.set(c.getX(), f + 9, c.getZ(), Blocks.SPRUCE_LOG.defaultBlockState());
			o.set(c.getX(), f + 10, c.getZ(), Blocks.SPRUCE_LOG.defaultBlockState());
		}
		int top = AldRebuild.spire(o, t.getX(), t.getZ(), 3, f + 11, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES.defaultBlockState());
		o.set(t.getX(), top, t.getZ(), Blocks.SPRUCE_FENCE.defaultBlockState());
		BlockPos flag = new BlockPos(t.getX(), top + 1, t.getZ());
		o.set(flag, Blocks.BANNER.blue().defaultBlockState().setValue(BannerBlock.ROTATION, AldLook.rotationToward(flag, castle)));
		o.hang(new BlockPos(t.getX(), f + 7, t.getZ()), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		// 4. the palisade: a ring RING round the tower-yard segment; every column on its own ground, logs 3 high, alternating tips
		BlockPos yardCol = at(t, gd, YARD, 0);
		Map<Long, int[]> ring = new LinkedHashMap<>(); // col -> {du, dv, g}
		for (int du = -RING - 1; du <= YARD + RING + 1; du++) for (int dv = -RING - 1; dv <= RING + 1; dv++) {
			if (seg(du, dv) > RING) continue;
			boolean edge = false;
			for (int a = -1; a <= 1 && !edge; a++) for (int b = -1; b <= 1 && !edge; b++) if (seg(du + a, dv + b) > RING) edge = true;
			if (!edge) continue;
			BlockPos c = at(t, gd, du, dv);
			int g = AldLook.ground(level, c.getX(), c.getZ());
			if (g == Integer.MIN_VALUE) return null;
			BlockState gs = level.getBlockState(c.atY(g));
			if (!AldLook.natural(gs) || !gs.isFaceSturdy(level, c.atY(g), Direction.UP) || !AldLook.clear(level, c.getX(), g + 1, g + 5, c.getZ())) return null;
			ring.put(AldRebuild.col(c.getX(), c.getZ()), new int[] {du, dv, g});
		}
		// the gate: ring cells on the castle side within one of the axis; posts beside it
		int gateH = Integer.MIN_VALUE, gateU = Integer.MIN_VALUE;
		List<int[]> gate = new ArrayList<>(), posts = new ArrayList<>();
		for (int[] c : ring.values()) {
			if (c[0] <= YARD) continue;
			if (Math.abs(c[1]) <= 1) gate.add(c);
			else if (Math.abs(c[1]) == 2) posts.add(c);
		}
		for (int[] c : gate) {
			gateH = Math.max(gateH, c[2]);
			gateU = Math.max(gateU, c[0]);
		}
		for (int[] c : posts) gateH = Math.max(gateH, c[2]);
		gateH += 4;
		java.util.Set<Long> gateCols = new java.util.HashSet<>();
		for (int[] c : gate) {
			BlockPos p = at(t, gd, c[0], c[1]);
			gateCols.add(AldRebuild.col(p.getX(), p.getZ()));
			if (!AldLook.clear(level, p.getX(), c[2] + 1, gateH + 1, p.getZ())) return null;
			o.set(p.getX(), gateH, p.getZ(), Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
			o.set(p.getX(), gateH + 1, p.getZ(), Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
		}
		for (int[] c : posts) {
			BlockPos p = at(t, gd, c[0], c[1]);
			gateCols.add(AldRebuild.col(p.getX(), p.getZ()));
			AldLook.clearPlantTop(level, o, p.getX(), c[2] + 1, p.getZ());
			o.set(p.getX(), c[2] + 1, p.getZ(), AldLook.bricks(p.getX(), c[2] + 1, p.getZ()));
			for (int y = c[2] + 2; y <= gateH; y++) o.set(p.getX(), y, p.getZ(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
			o.set(p.getX(), gateH + 1, p.getZ(), Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
		}
		BlockPos lampCol = at(t, gd, gateU, 0);
		o.hang(lampCol.atY(gateH - 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		java.util.Set<Integer> levels = new java.util.HashSet<>();
		for (var e : ring.entrySet()) {
			if (gateCols.contains(e.getKey())) continue;
			int[] c = e.getValue();
			BlockPos p = at(t, gd, c[0], c[1]);
			int g = c[2];
			AldLook.clearPlantTop(level, o, p.getX(), g + 1, p.getZ());
			for (int h = 1; h <= 3; h++) o.set(p.getX(), g + h, p.getZ(), Blocks.SPRUCE_LOG.defaultBlockState());
			o.loose(p.atY(g + 4), ((p.getX() + p.getZ()) & 1) == 0 ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState());
			levels.add(g);
		}
		ringLevels = levels.size();
		// 5. the beaten track from the gate to the tower door (grass only, nothing on top)
		for (int du = gateU; du >= 3; du--) {
			BlockPos p = at(t, gd, du, 0);
			int g = AldLook.ground(level, p.getX(), p.getZ());
			if (g == Integer.MIN_VALUE || o.has(p.atY(g)) || o.has(p.atY(g + 1))) continue;
			if (level.getBlockState(p.atY(g)).is(Blocks.GRASS_BLOCK) && level.getBlockState(p.atY(g + 1)).isAir()) o.set(p.atY(g), Blocks.DIRT_PATH.defaultBlockState());
		}
		// 6. the mess: an unlit fire and a lantern post across the yard (clear of the garrison's muster at yard + (2, 0, -2))
		int yg = AldLook.ground(level, yardCol.getX(), yardCol.getZ());
		BlockPos yard = yardCol.atY(yg + 1);
		BlockPos muster = yard.offset(2, 0, -2);
		int side = 1;
		BlockPos fireCol = at(t, gd, YARD + 1, 3 * side);
		if (Math.abs(fireCol.getX() - muster.getX()) <= 2 && Math.abs(fireCol.getZ() - muster.getZ()) <= 2) {
			side = -1;
			fireCol = at(t, gd, YARD + 1, 3 * side);
		}
		if (AldLook.buildable(level, fireCol.getX(), fireCol.getZ(), 2)) {
			int g = AldLook.ground(level, fireCol.getX(), fireCol.getZ());
			AldLook.clearPlantTop(level, o, fireCol.getX(), g + 1, fireCol.getZ());
			o.set(fireCol.atY(g + 1), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
			BlockPos bench = at(t, gd, YARD + 1, 3 * side + side);
			if (AldLook.buildable(level, bench.getX(), bench.getZ(), 1)) {
				int bg = AldLook.ground(level, bench.getX(), bench.getZ());
				AldLook.clearPlantTop(level, o, bench.getX(), bg + 1, bench.getZ());
				o.set(bench.atY(bg + 1), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, gd.getAxis()));
			}
		}
		BlockPos lamp = at(t, gd, YARD - 1, -3 * side);
		if (AldLook.buildable(level, lamp.getX(), lamp.getZ(), 2)) {
			int g = AldLook.ground(level, lamp.getX(), lamp.getZ());
			AldLook.clearPlantTop(level, o, lamp.getX(), g + 1, lamp.getZ());
			o.set(lamp.atY(g + 1), Blocks.SPRUCE_FENCE.defaultBlockState());
			o.set(lamp.atY(g + 2), Blocks.LANTERN.defaultBlockState());
		}
		List<VillageRecord.Op> ops = o.ops(level);
		if (ops.size() > MAX_OPS) return null;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (VillageRecord.Op op : ops) {
			BlockPos p = BlockPos.of(op.pos());
			minX = Math.min(minX, p.getX());
			minY = Math.min(minY, p.getY());
			minZ = Math.min(minZ, p.getZ());
			maxX = Math.max(maxX, p.getX());
			maxY = Math.max(maxY, p.getY());
			maxZ = Math.max(maxZ, p.getZ());
		}
		lastRingCells = ring.size();
		lastRingLevels = ringLevels;
		lastPlinth = plinth;
		lastBatter = batter;
		BlockPos road = at(t, gd, gateU + 1, 0);
		int rg = AldLook.ground(level, road.getX(), road.getZ());
		return new Plan(ops, flag, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), yard, road.atY(rg == Integer.MIN_VALUE ? yard.getY() : rg + 1),
			new BlockPos(t.getX(), f + 1, t.getZ()), f);
	}
}
