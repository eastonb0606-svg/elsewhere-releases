package dev.elsewhere.kingdom_court.world;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * s-layout-mire: the CASTLE part of the Mirewick layout (inside the trace). The yard pad (only inside the trace), the Counting-House (the
 * court keep with throne, hall, solar and dungeon hatch, in the Mirewick palette), the inner canal (x -21..21, z -2..0; water at floor-3..-2
 * in a quartz-walled bed, two quays, a lantern bridge on the gate road and two crimson footbridges), the stilted Guildhall over the canal's
 * west end and the stilted sluice house over its east end, ten narrow canal houses (bodies here, gables in {@link MireKit}), barracks,
 * arsenal, stables, the bell and the braziers at the court's anchors, and the style's quay (z 31..34, without the post that stood in the gate
 * road).
 */
final class MireCourt {
	private MireCourt() {}

	static final int SLUICE_GATE_X = 19;
	/** Canal houses: x0, z0, x1, z1, front (1 south, -1 north), storeys, gable (0 stepped, 1 neck, 2 bell). */
	static final int[][] HOUSES = {
		{-22, -11, -18, -5, 1, 3, 0}, {-17, -11, -13, -5, 1, 3, 1}, {-22, -19, -18, -13, 1, 4, 2}, {-17, -19, -13, -13, 1, 4, 0},
		{13, -11, 17, -5, 1, 3, 2}, {18, -11, 22, -5, 1, 3, 0}, {13, -19, 17, -13, 1, 4, 1}, {18, -19, 22, -13, 1, 4, 2},
		{12, 5, 16, 11, -1, 3, 1}, {17, 5, 21, 11, -1, 3, 0}};
	/** Stilted halls over the canal: Guildhall (west) and sluice house (east): x0, z0, x1, z1. */
	static final int[] GUILD = {-23, -3, -15, 3}, SLUICE = {17, -3, 21, 3};
	static final int[] BARRACKS = {-23, 6, -13, 14}, ARSENAL = {11, 13, 21, 21}, STABLES = {-21, 16, -11, 24};

	/** Stilt columns standing in the canal (they replace water). */
	static boolean stilt(int x, int z) {
		return z == 0 && (x == -19 || x == -15 || x == 17 || x == 21);
	}

	static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	static void build(CastleLayout.L l) {
		CastleKit.W w = new CastleKit.W(l, false);
		var c = l.p.clip;
		// slope skirt outside the trace (retaining slope, no grass or leaf overhang above the footprint)
		CastleKit.skirt(l, MireLayout.R, (x, z) -> { float dl = MireLayout.delta(x, z); return dl <= -90F || dl > 0F ? 0 : (int) Math.ceil(-dl); }, 8, Blocks.GRASS_BLOCK, Blocks.DIRT, 30, -1, true);
		int x0 = Math.max(-36, c.minX() - l.o.getX()), x1 = Math.min(36, c.maxX() - l.o.getX());
		int z0 = Math.max(-36, c.minZ() - l.o.getZ()), z1 = Math.min(36, c.maxZ() - l.o.getZ());
		// pad: only inside the trace (the rampart and bastions are filled by WALL); the beard_thin of the CASTLE piece blends the edge
		BlockState air = st(Blocks.AIR);
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (MireLayout.delta(x, z) <= 0) continue;
			for (int y = 0; y <= 30; y++) w.s(x, y, z, air);
			for (int y = -4; y <= -2; y++) if (!w.solid(x, y, z) || w.open(x, y, y, z)) w.s(x, y, z, st(Blocks.STONE));
			l.foundation(x, z, x, z, -5, Blocks.STONE);
			w.s(x, -1, z, paving(x, z));
		}
		CastleLayout.keep(l); // the Counting-House body: throne hall, dais, balconies, solar, ladder, dungeon hatch (Mirewick palette)
		counting(w);
		canal(l, w);
		for (int[] h : HOUSES) house(w, h);
		stilted(w, GUILD, true);
		stilted(w, SLUICE, false);
		barracks(l, w);
		arsenal(l, w);
		stables(l, w);
		yard(w);
		// the style's quay (z 31..34) through the fv 2 piece fit, without the lantern post that stood in the gate road (0, 32)
		CastleStyles.Style style = l.p.style;
		if (style != null && !style.pieces.isEmpty()) {
			CastleStyles.Style q = new CastleStyles.Style();
			q.palette.putAll(style.palette);
			for (CastleStyles.Piece p : style.pieces) if (!(p.type().equals("column") && p.a()[0] == 0 && p.a()[2] == 32)) q.pieces.add(p);
			CastleLayout.fitPieces(q, l.o, l.p);
			CastleKit.quayDeck(w); // w6-mirewick: deck over natural ground, no foliage inside the quay
		}
	}

	static BlockState paving(int x, int z) {
		int h = CastleLayout.hash(x, 11, z);
		return st(h < 50 ? Blocks.SMOOTH_STONE : h < 80 ? Blocks.POLISHED_DIORITE : Blocks.CALCITE);
	}

	/** Counting-House dressing at floor level: purple glass, purple banners in the hall, a gilt cornice line, tall front windows. */
	static void counting(CastleKit.W w) {
		BlockState glass = st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PURPLE)), gold = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW));
		for (int z = -24; z <= -12; z += 4) for (int s = -1; s <= 1; s += 2) {
			for (int y = 3; y <= 5; y++) w.s(s * 11, y, z, glass);
			for (int y = 11; y <= 12; y++) w.s(s * 11, y, z, glass);
		}
		for (int s = -1; s <= 1; s += 2) for (int x : new int[] {4, 8}) {
			for (int y = 2; y <= 6; y++) w.s(s * x, y, -8, glass);
			for (int y = 11; y <= 14; y++) w.s(s * x, y, -8, glass);
			for (int y = 11; y <= 14; y++) w.s(s * x, y, -26, glass);
		}
		for (int x = -5; x <= 5; x += 2) if (x != -1 && x != 1)
			w.s(x, 4, -25, Blocks.WALL_BANNER.pick(DyeColor.PURPLE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
		for (int x = -11; x <= 11; x++) {
			w.s(x, 18, -8, gold);
			w.s(x, 18, -26, gold);
		}
		w.s(0, 4, -8, gold); // lintel over the hall door
	}

	/** The inner canal with its quays, the lantern bridge on the gate road and two footbridges. */
	static void canal(CastleLayout.L l, CastleKit.W w) {
		BlockState quartz = st(Blocks.QUARTZ_BRICKS), water = st(Blocks.WATER), air = st(Blocks.AIR), bed = st(Blocks.SMOOTH_STONE),
			deck = st(Blocks.POLISHED_DIORITE), wall = st(Blocks.DIORITE_WALL), lantern = st(Blocks.LANTERN);
		for (int x = -22; x <= 22; x++) for (int z = -3; z <= 1; z++) {
			boolean edge = z == -3 || z == 1 || Math.abs(x) == 22;
			w.s(x, -4, z, edge ? quartz : bed);
			for (int y = -3; y <= -1; y++) w.s(x, y, z, edge ? quartz : y == -1 ? air : MireLayout.innerCanal(x, y, z) ? water : quartz);
		}
		// lantern bridge on the gate road (deck at floor-1 over the water, railings, four lantern posts)
		for (int x = -3; x <= 3; x++) for (int z = -2; z <= 0; z++) w.s(x, -1, z, deck);
		for (int z = -2; z <= 0; z++) for (int s = -1; s <= 1; s += 2) w.s(s * 3, 0, z, wall);
		for (int s = -1; s <= 1; s += 2) for (int z : new int[] {-3, 1}) {
			w.s(s * 3, 0, z, wall);
			w.s(s * 3, 1, z, wall);
			w.s(s * 3, 2, z, lantern);
		}
		// crimson footbridges
		for (int s = -1; s <= 1; s += 2) {
			int bx = s * 8;
			for (int z = -2; z <= 0; z++) {
				w.s(bx, -1, z, CastleKit.slab(Blocks.CRIMSON_SLAB, SlabType.TOP));
				w.s(bx - 1, -1, z, CastleKit.slab(Blocks.CRIMSON_SLAB, SlabType.TOP));
				w.s(bx + 1, -1, z, CastleKit.slab(Blocks.CRIMSON_SLAB, SlabType.TOP));
				w.s(bx - 1, 0, z, st(Blocks.CRIMSON_FENCE));
				w.s(bx + 1, 0, z, st(Blocks.CRIMSON_FENCE));
			}
		}
		// mooring posts on the quays
		for (int x = -12; x <= 12; x += 24) for (int z : new int[] {-3, 1}) w.s(x, 0, z, st(Blocks.MANGROVE_FENCE));
	}

	/** A narrow canal house body: calcite front, quartz sides with purple string courses at each floor, floors, a ladder, purple glass. */
	static void house(CastleKit.W w, int[] h) {
		int x0 = h[0], z0 = h[1], x1 = h[2], z1 = h[3], front = h[4], storeys = h[5];
		int zf = front > 0 ? z1 : z0, zb = front > 0 ? z0 : z1, xc = (x0 + x1) / 2, eave = 4 * storeys - 1;
		BlockState calcite = st(Blocks.CALCITE), quartz = st(Blocks.QUARTZ_BRICKS), purple = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE)),
			gold = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)), air = st(Blocks.AIR), floor = st(Blocks.MANGROVE_PLANKS),
			glass = st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PURPLE)), post = Blocks.CRIMSON_STEM.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		Direction out = front > 0 ? Direction.SOUTH : Direction.NORTH;
		// crimson-stem quoins only under the slate roofs (odd houses); the crimson-roofed ones get purple quoins, so purple leads crimson
		BlockState quoin = (java.util.Arrays.asList(HOUSES).indexOf(h) & 1) == 1 ? post : purple;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			boolean edge = x == x0 || x == x1 || z == z0 || z == z1, corner = (x == x0 || x == x1) && (z == z0 || z == z1);
			w.s(x, -1, z, edge ? calcite : floor);
			for (int y = 0; y <= eave; y++) {
				BlockState b;
				if (!edge) b = y % 4 == 3 ? floor : air;
				else if (corner) b = z == zf ? quoin : quartz;
				else if (z == zf) b = y % 4 == 3 ? purple : calcite;
				else b = y % 4 == 3 ? purple : quartz;
				w.s(x, y, z, b);
			}
		}
		// front: door with a gilt lintel, windows on every floor with crimson shutters (open trapdoors against the wall), purple spandrels
		w.s(xc, 0, zf, air);
		w.s(xc, 1, zf, air);
		w.s(xc, 2, zf, gold);
		for (int k = 0; k < storeys; k++) {
			int y = 4 * k + 1;
			for (int x : k == 0 ? new int[] {x0 + 1, x1 - 1} : new int[] {x0 + 1, xc, x1 - 1}) {
				w.s(x, y, zf, glass);
				w.s(x, y + 1, zf, glass);
			}
			if (k > 0) {
				w.s(x0, y, zf + front, shutter(out));
				w.s(x1, y, zf + front, shutter(out));
			}
			for (int z = Math.min(z0, z1) + 2; z <= Math.max(z0, z1) - 2; z += 2) {
				w.s(x0, y + 1, z, glass);
				w.s(x1, y + 1, z, glass);
			}
		}
		// ladder against the back wall, through every floor
		int lx = x1 - 1, lz = zb + front;
		for (int y = 0; y <= eave; y++) w.s(lx, y, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, out));
	}

	static BlockState shutter(Direction out) {
		return Blocks.CRIMSON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, out).setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.BOTTOM);
	}

	/**
	 * A hall on mangrove stilts over the canal (floor block at y 1, a crawl space with the canal under it): the Guildhall (west, entered from the
	 * south quay by two steps; lecterns, a cartography table, purple carpet and banners) or the sluice house (east, its sluice gate across the
	 * canal at x 19, a grindstone winch).
	 */
	static void stilted(CastleKit.W w, int[] b, boolean guild) {
		int x0 = b[0], z0 = b[1], x1 = b[2], z1 = b[3], fy = guild ? 1 : 0, top = fy + (guild ? 6 : 5);
		BlockState log = Blocks.MANGROVE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), planks = st(Blocks.MANGROVE_PLANKS),
			calcite = st(Blocks.CALCITE), quartz = st(Blocks.QUARTZ_BRICKS), purple = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE)), air = st(Blocks.AIR),
			glass = st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PURPLE)), crimson = st(Blocks.CRIMSON_PLANKS);
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			boolean edge = x == x0 || x == x1 || z == z0 || z == z1, corner = (x == x0 || x == x1) && (z == z0 || z == z1);
			boolean post = (x == x0 || x == x1 || (guild && x == (x0 + x1) / 2)) && (z == z0 || z == z1 || z == 0);
			boolean inCanal = z >= -2 && z <= 0 && Math.abs(x) <= 21;
			if (inCanal) {
				if (post) for (int y = -4; y < fy; y++) w.s(x, y, z, log);
			} else for (int y = 0; y < fy; y++) w.s(x, y, z, post ? log : air);
			w.s(x, fy, z, planks);
			for (int y = fy + 1; y <= top; y++) {
				BlockState s = !edge ? air : corner ? log : y == fy + 1 || y == top ? purple : (x + y) % 4 == 0 ? purple : guild ? calcite : quartz;
				w.s(x, y, z, s);
			}
		}
		int xc = (x0 + x1) / 2;
		for (int y = fy + 2; y <= top - 1; y += 2) for (int z = z0 + 2; z <= z1 - 2; z += 2) {
			w.s(x0, y, z, glass);
			w.s(x1, y, z, glass);
		}
		if (guild) {
			// door on the south quay side, two steps up from the yard (floor surface y 2)
			w.s(xc, fy + 1, z1, air);
			w.s(xc, fy + 2, z1, air);
			w.s(xc, 1, z1 + 1, CastleKit.stair(Blocks.MANGROVE_STAIRS, Direction.NORTH, Half.BOTTOM));
			w.s(xc, 0, z1 + 1, planks);
			w.s(xc, 0, z1 + 2, CastleKit.stair(Blocks.MANGROVE_STAIRS, Direction.NORTH, Half.BOTTOM));
			for (int x = x0 + 1; x <= x1 - 1; x++) w.s(x, fy + 1, 0, st(Blocks.CARPET.pick(DyeColor.PURPLE)));
			w.s(xc - 2, fy + 1, -2, st(Blocks.LECTERN));
			w.s(xc + 2, fy + 1, -2, st(Blocks.LECTERN));
			w.s(xc, fy + 1, -2, st(Blocks.CARTOGRAPHY_TABLE));
			w.s(x0 + 1, fy + 4, 0, Blocks.WALL_BANNER.pick(DyeColor.PURPLE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST));
			w.s(x1, fy + 4, 0, st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)));
		} else {
			// sluice gate across the canal under the house, its winch inside; door to the north quay, one step up (floor surface y 1)
			for (int z = -2; z <= 0; z++) for (int y = -4; y <= -1; y++) w.s(SLUICE_GATE_X, y, z, crimson);
			w.s(SLUICE_GATE_X, fy + 1, -1, st(Blocks.GRINDSTONE));
			w.s(SLUICE_GATE_X, fy + 1, z0, air);
			w.s(SLUICE_GATE_X, fy + 2, z0, air);
			w.s(SLUICE_GATE_X, 0, z0 - 1, CastleKit.slab(Blocks.MANGROVE_SLAB, SlabType.BOTTOM));
		}
	}

	static void barracks(CastleLayout.L l, CastleKit.W w) {
		int[] b = BARRACKS;
		hall(w, b, 5);
		w.s(-18, 0, b[1], st(Blocks.AIR));
		w.s(-18, 1, b[1], st(Blocks.AIR));
		w.s(-18, 2, b[1], st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)));
		for (int x : new int[] {-22, -20, -16, -14}) CastleLayout.bed(l, x, b[3] - 1, Direction.SOUTH, Blocks.BED.pick(DyeColor.PURPLE));
		l.b(-18, 0, b[3] - 1, Blocks.CHEST);
		w.s(-22, 0, b[1] + 1, st(Blocks.CRAFTING_TABLE));
	}

	static void arsenal(CastleLayout.L l, CastleKit.W w) {
		int[] b = ARSENAL;
		hall(w, b, 5);
		w.s(b[0], 0, 17, st(Blocks.AIR));
		w.s(b[0], 1, 17, st(Blocks.AIR));
		w.s(b[0], 2, 17, st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)));
		w.s(14, 0, 14, st(Blocks.ANVIL));
		w.s(16, 0, 14, st(Blocks.SMITHING_TABLE));
		w.s(18, 0, 14, st(Blocks.GRINDSTONE));
		w.s(20, 0, 19, st(Blocks.BARREL));
		w.s(20, 0, 18, st(Blocks.BARREL));
		l.chest(20, 0, 16, Direction.WEST, CastleLayout.LOOT_ARMORY);
	}

	/** A stone hall: quartz and calcite walls, purple band, crimson posts, purple glass, mangrove floor; roof from MireKit. */
	static void hall(CastleKit.W w, int[] b, int h) {
		BlockState calcite = st(Blocks.CALCITE), quartz = st(Blocks.QUARTZ_BRICKS), purple = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE)),
			air = st(Blocks.AIR), floor = st(Blocks.MANGROVE_PLANKS), glass = st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PURPLE)),
			post = Blocks.CRIMSON_STEM.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++) {
			boolean edge = x == b[0] || x == b[2] || z == b[1] || z == b[3], corner = (x == b[0] || x == b[2]) && (z == b[1] || z == b[3]);
			w.s(x, -1, z, edge ? calcite : floor);
			for (int y = 0; y <= h; y++) w.s(x, y, z, !edge ? air : corner ? post : y == 3 ? purple : y == 0 ? calcite : quartz);
		}
		for (int x = b[0] + 2; x <= b[2] - 2; x += 3) {
			w.s(x, 1, b[1], glass);
			w.s(x, 1, b[3], glass);
			w.s(x, 2, b[1], glass);
			w.s(x, 2, b[3], glass);
		}
	}

	static void stables(CastleLayout.L l, CastleKit.W w) {
		int[] b = STABLES;
		BlockState fence = st(Blocks.MANGROVE_FENCE), log = Blocks.MANGROVE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int x = b[0]; x <= b[2]; x++) for (int z = b[1]; z <= b[3]; z++) {
			boolean edge = x == b[0] || x == b[2] || z == b[1] || z == b[3];
			if (edge && !(x == b[2] && z >= 19 && z <= 21)) w.s(x, 0, z, fence);
		}
		for (int x = b[0]; x <= b[2]; x += 5) for (int z = b[1]; z <= b[3]; z += b[3] - b[1]) for (int y = 0; y <= 3; y++) w.s(x, y, z, log);
		for (int y = 0; y <= 3; y++) w.s(b[0], y, (b[1] + b[3]) / 2, log);
		w.s(-19, 0, 23, st(Blocks.HAY_BLOCK));
		w.s(-18, 0, 23, st(Blocks.HAY_BLOCK));
		w.s(-19, 1, 23, st(Blocks.HAY_BLOCK));
		l.chest(-20, 0, 22, Direction.EAST, CastleLayout.LOOT_CACHE);
	}

	/** The court's yard anchors: bell, braziers (campfires, lit at night by CourtLife), training targets. */
	static void yard(CastleKit.W w) {
		BlockState post = Blocks.MANGROVE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		w.s(6, 0, 14, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
		for (int y = 0; y <= 2; y++) {
			w.s(4, y, 14, post);
			w.s(8, y, 14, post);
		}
		for (int x = 4; x <= 8; x++) w.s(x, 3, 14, CastleKit.slab(Blocks.CRIMSON_SLAB, SlabType.BOTTOM));
		w.s(6, 4, 14, st(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW)));
		for (var b : CastleLayout.braziers(net.minecraft.core.BlockPos.ZERO))
			w.s(b.getX(), b.getY(), b.getZ(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		for (int x : new int[] {-10, -5}) {
			w.s(x, 0, 12, st(Blocks.HAY_BLOCK));
			w.s(x, 1, 12, st(Blocks.TARGET));
		}
	}
}
