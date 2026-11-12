package dev.elsewhere.kingdom_court.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * s-layout-mire: the WALL part of the Mirewick layout. Low angular ramparts on the five-bastion trace of {@link MireLayout}: each column on its
 * own ground (rubble-free calcite underpinning), its top on the smoothed contour of the trace (steps of at most 1), calcite plinth, quartz-brick
 * face with a purple-terracotta band, a parapet with gilt merlons, a grassy earthen walk; the bastions are solid earth platforms at walk
 * height. Outside: a quartz batter on the ground of each column, then the moat: wet (water at floor-3..-2 over a smooth-stone bed, quartz
 * counterscarp) only where the plan found flat ground near the floor along a run of 6+, a dry ditch elsewhere on gentle ground, nothing on
 * steep or wet ground; every column next to moat water is made solid at the water levels (dams at run ends), so no water block ever has an open
 * side. The water-gate (gatehouse) keeps the court's gate anchors: fence gates at z 30, x -2..2; wall-walk stairs reach (+-12, 8, 28..29).
 */
final class MireFort {
	private MireFort() {}

	static BlockState st(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState();
	}

	static void build(CastleLayout.L l) {
		CastleKit.W w = new CastleKit.W(l, false);
		MireLayout.Plan plan = MireLayout.plan(l.o, l.p.ground);
		var c = l.p.clip;
		int x0 = Math.max(-36, c.minX() - l.o.getX()), x1 = Math.min(36, c.maxX() - l.o.getX());
		int z0 = Math.max(-36, c.minZ() - l.o.getZ()), z1 = Math.min(36, c.maxZ() - l.o.getZ());
		BlockState calcite = st(Blocks.CALCITE), quartz = st(Blocks.QUARTZ_BRICKS), purple = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE)),
			gold = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)), mud = st(Blocks.PACKED_MUD), grass = st(Blocks.GRASS_BLOCK), smooth = st(Blocks.SMOOTH_STONE),
			slab = CastleKit.slab(Blocks.QUARTZ_SLAB, SlabType.BOTTOM), water = st(Blocks.WATER), air = st(Blocks.AIR), dirt = st(Blocks.DIRT);
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			float d = MireLayout.delta(x, z);
			int gy = l.gy(x, z);
			w.dy = 0;
			if (MireLayout.rampart(x, z)) {
				int top = 8 + MireLayout.lift(plan, x, z), foot = Math.min(0, gy);
				boolean face = d <= 1F, inner = d > 2F && !MireLayout.bastion(x, z);
				for (int y = foot; y <= top; y++) {
					BlockState b;
					if (face) b = y <= foot + 1 ? calcite : y == 5 ? purple : y == top ? quartz : CastleLayout.hash(x, y, z) < 30 ? calcite : quartz;
					else if (inner) b = y == top ? smooth : y == 5 ? purple : calcite;
					else b = y == top ? grass : mud;
					w.s(x, y, z, b);
				}
				for (int y = top + 1; y <= top + 4; y++) w.s(x, y, z, air);
				if (face) w.s(x, top + 1, z, ((x + z) & 1) == 0 ? (CastleLayout.hash(x, 3, z) < 15 ? gold : quartz) : slab);
				l.foundation(x, z, x, z, foot - 1, Blocks.CALCITE);
				continue;
			}
			if (d > 0) continue;
			boolean nextWet = MireLayout.nextToWet(plan, x, z);
			if (d > -1.5F) {
				// batter on this column's own ground, underpinned
				if (MireLayout.quayZone(x, z) && !nextWet) continue;
				double[] s = MireLayout.SAMPLES[MireLayout.near(x, z)];
				w.dy = gy;
				w.s(x, 0, z, CastleKit.stair(Blocks.QUARTZ_STAIRS, inward(s[2], s[3]), Half.BOTTOM));
				w.under(x, 0, z, calcite);
				w.dy = 0;
				if (nextWet) solidAt(w, x, z, -4, -2, quartz);
				continue;
			}
			if (MireLayout.wetMoat(plan, x, z)) {
				w.s(x, -4, z, smooth);
				w.s(x, -3, z, water);
				w.s(x, -2, z, water);
				for (int y = -1; y <= Math.max(gy, 0) + 2; y++) w.s(x, y, z, air);
				l.foundation(x, z, x, z, -5, Blocks.CALCITE);
				continue;
			}
			if (MireLayout.dryDitch(plan, x, z)) {
				if (nextWet) solidAt(w, x, z, -4, -2, quartz);
				else {
					solidAt(w, x, z, -4, -3, dirt);
					w.s(x, -2, z, grass);
				}
				for (int y = -1; y <= Math.max(gy, 0) + 2; y++) w.s(x, y, z, air);
				l.foundation(x, z, x, z, -5, Blocks.DIRT);
				continue;
			}
			if (nextWet) {
				// counterscarp, or a dam at the end of a wet run
				for (int y = -4; y <= Math.max(-2, gy - 1); y++) w.s(x, y, z, quartz);
				l.foundation(x, z, x, z, -5, Blocks.CALCITE);
				continue;
			}
			if (d > -6.5F && (MireLayout.moatBand(x + 1, z) || MireLayout.moatBand(x - 1, z) || MireLayout.moatBand(x, z + 1) || MireLayout.moatBand(x, z - 1))
				&& (MireLayout.dryDitch(plan, x + 1, z) || MireLayout.dryDitch(plan, x - 1, z) || MireLayout.dryDitch(plan, x, z + 1) || MireLayout.dryDitch(plan, x, z - 1))) {
				// dry-ditch counterscarp: a quartz revetment from the ditch floor to the ground
				for (int y = -3; y <= gy - 1; y++) w.s(x, y, z, y == gy - 1 ? calcite : quartz);
			}
		}
		w.dy = 0;
		gatehouse(l, w, plan);
	}

	/** Makes (x, y0..y1, z) solid (fills air, fluid and replaceable blocks only). */
	static void solidAt(CastleKit.W w, int x, int z, int y0, int y1, BlockState b) {
		for (int y = y0; y <= y1; y++) if (!w.solid(x, y, z) || w.open(x, y, y, z)) w.s(x, y, z, b);
	}

	static Direction inward(double nx, double nz) {
		if (Math.abs(nx) >= Math.abs(nz)) return nx > 0 ? Direction.WEST : Direction.EAST;
		return nz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	/**
	 * The water-gate: a 13-wide gate building over the passage (x -2..2, three high, iron-bar portcullis), guard rooms either side, a hall
	 * above; its crow-stepped gables and roof come from {@link MireKit}. Fence gates at z 30 (CastleLayout.gates) and the wall-walk stairs at z 27
	 * up to the walk at (+-12, 8, 28..29).
	 */
	static void gatehouse(CastleLayout.L l, CastleKit.W w, MireLayout.Plan plan) {
		BlockState quartz = st(Blocks.QUARTZ_BRICKS), calcite = st(Blocks.CALCITE), purple = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE)),
			gold = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)), air = st(Blocks.AIR), planks = st(Blocks.MANGROVE_PLANKS),
			glass = st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PURPLE));
		w.dy = 0;
		for (int x = -6; x <= 6; x++) for (int z = 25; z <= 30; z++) {
			boolean edge = x == -6 || x == 6 || z == 25 || z == 30, corner = (x == -6 || x == 6) && (z == 25 || z == 30);
			for (int y = 0; y <= 12; y++) {
				BlockState b = !edge ? air : corner ? calcite : y == 5 || y == 11 ? purple : y == 12 ? gold : quartz;
				w.s(x, y, z, b);
			}
			w.s(x, -1, z, st(Blocks.SMOOTH_STONE));
			l.foundation(x, z, x, z, -2, Blocks.CALCITE);
		}
		// floor of the gate hall, the passage lining and guard rooms
		for (int x = -5; x <= 5; x++) for (int z = 26; z <= 29; z++) w.s(x, 5, z, planks);
		for (int z = 25; z <= 30; z++) for (int y = 0; y <= 4; y++) {
			w.s(-3, y, z, quartz);
			w.s(3, y, z, quartz);
		}
		for (int x = -2; x <= 2; x++) for (int z = 25; z <= 30; z++) for (int y = 0; y <= 4; y++) w.s(x, y, z, y == 4 ? quartz : air);
		for (int x = -2; x <= 2; x++) w.s(x, 3, 28, st(Blocks.IRON_BARS));
		for (int s = -1; s <= 1; s += 2) {
			w.s(s * 2, 3, 25, CastleKit.stair(Blocks.QUARTZ_STAIRS, s < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
			w.s(s * 2, 3, 30, CastleKit.stair(Blocks.QUARTZ_STAIRS, s < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
			// guard-room doors from the yard, windows to the quay
			w.s(s * 4, 0, 25, air);
			w.s(s * 4, 1, 25, air);
			// s-asm-court: a front window only where the ground outside is below it (an uphill gate keeps its wall: no glass against earth)
			w.s(s * 4, 2, 30, l.gy(s * 4, 31) > 2 ? quartz : glass);
			for (int y = 7; y <= 9; y++) w.s(s * 3, y, 30, l.gy(s * 3, 31) > y ? quartz : glass);
			for (int y = 7; y <= 9; y++) w.s(s * 3, y, 25, glass);
			w.s(s * 4, 7, 31, Blocks.WALL_BANNER.pick(DyeColor.PURPLE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
		}
		// ladder from the west guard room to the gate hall
		for (int y = 0; y <= 5; y++) w.s(-5, y, 29, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
		for (int x = -2; x <= 2; x++)
			w.s(x, 0, 30, Blocks.MANGROVE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH).setValue(FenceGateBlock.OPEN, true));
		w.s(0, 4, 31, gold); // keystone over the arch
		// stairs up to the rampart walk on both sides, along z 27 (the walk is at y 8 + lift; the south curtain sits on the yard floor)
		for (int s = -1; s <= 1; s += 2) {
			int top = 8 + MireLayout.lift(plan, s * 17, 29);
			for (int i = 0; i <= top; i++) {
				int x = s * (9 + i);
				if (Math.abs(x) > 20) break;
				w.s(x, i, 27, CastleKit.stair(Blocks.QUARTZ_STAIRS, s < 0 ? Direction.WEST : Direction.EAST, Half.BOTTOM));
				for (int y = 0; y < i; y++) w.s(x, y, 27, calcite);
				for (int y = i + 1; y <= i + 3; y++) w.s(x, y, 27, air);
			}
		}
	}

	/** Local probe cells of the gate anchors (self-test). */
	static BlockPos gateProbe(int x) {
		return new BlockPos(x, 0, 30);
	}
}
