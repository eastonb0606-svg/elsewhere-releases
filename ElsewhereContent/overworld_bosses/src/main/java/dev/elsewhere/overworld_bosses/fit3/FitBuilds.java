package dev.elsewhere.overworld_bosses.fit3;

import dev.elsewhere.overworld_bosses.OwbBlocks;
import dev.elsewhere.overworld_bosses.world.BlockSink;
import dev.elsewhere.overworld_bosses.world.Blueprints;
import dev.elsewhere.overworld_bosses.world.OwbPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The 3.0 builders behind {@link OwbFitPiece} (STRUCTURES.md section 4 "owb", 2.9 foundations, 2.13 writer rules). Every arena keeps its
 * gameplay layout and trigger offsets ({@link Blueprints} constants), so the boss code is unchanged; what changes is how each part meets the
 * ground: piles, underpins in matching blocks (capped), soft clears instead of box clears, and no valuable blocks.
 */
public final class FitBuilds {
	private FitBuilds() {}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	// ---- boxes ---------------------------------------------------------------------------------------------------------------

	public static BoundingBox box(String kind, String part, BlockPos o, BlockPos at, int a, int b, int[] prof) {
		int x = o.getX(), z = o.getZ(), g = o.getY() - 1;
		return switch (kind + "/" + part) {
			case "croaking_shrine/walk" -> lineBox(o, a, 16, 16 + Math.max(1, b), min(prof, g) - 15, max(prof, g) + 4);
			// the shelf and the chamber carry a noise rule that reaches 12 past its box: their piece box includes that reach, so every chunk the
			// rule touches holds a reference to the start (vanilla inflates beard structures the same way)
			case "glacier_shelf/core" -> new BoundingBox(x - 30, g - 15, z - 29, x + 30, g + 13, z + 30);
			case "glacier_shelf/slope" -> new BoundingBox(x - 16, g - 30, z - 28, x + 16, g + 15, z - 18);
			case "glacier_shelf/buttress" -> {
				double ang = Math.PI * 2 * a / 8, c = Math.cos(ang), s = Math.sin(ang);
				int x0 = x + (int) Math.round(c * 17), z0 = z + (int) Math.round(s * 17);
				int x1 = x + (int) Math.round(c * (17 + b)), z1 = z + (int) Math.round(s * (17 + b));
				yield new BoundingBox(Math.min(x0, x1) - 2, g - b - 22, Math.min(z0, z1) - 2, Math.max(x0, x1) + 2, g + 3, Math.max(z0, z1) + 2);
			}
			case "glacier_shelf/stair" -> lineBox(o, a, 17, 19 + Math.max(1, b), min(prof, g) - 13, max(prof, g) + 5);
			case "sun_chamber/core" -> new BoundingBox(x - 25, g - 16, z - 25, x + 25, g + 26, z + 25);
			case "sun_chamber/shaft" -> new BoundingBox(x + 11, g - 1, z - 2, x + 18, Math.max(b, g + 8) + 7, z + 2);
			case "wreck_ring/core" -> new BoundingBox(x - 19, g - 1, z - 19, x + 19, g + 15, z + 19);
			case "wreck_ring/stack" -> new BoundingBox(at.getX() - 1, at.getY() - 6, at.getZ() - 1, at.getX() + 1, g - 1, at.getZ() + 1);
			case "wreck_ring/hulk" -> new BoundingBox(at.getX() - 6, at.getY() - 8, at.getZ() - 6, at.getX() + 6, g + 4, at.getZ() + 6);
			default -> new BoundingBox(x - 16, g - 16, z - 16, x + 16, g + 9, z + 16); // croaking_shrine/core
		};
	}

	/** The noise-level terrain rule's box: the shelf's plateau footprint (beard_thin), the chamber's shell (encapsulate). */
	public static BoundingBox beardBox(OwbFitPiece p) {
		int x = p.o.getX(), z = p.o.getZ(), g = p.o.getY() - 1;
		if (OwbPiece.SUN_CHAMBER.equals(p.kind)) return new BoundingBox(x - 13, g - 4, z - 13, x + 13, g + 14, z + 13);
		return new BoundingBox(x - 17, g, z - 17, x + 17, g, z + 17);
	}

	private static BoundingBox lineBox(BlockPos o, int dir, int r0, int r1, int y0, int y1) {
		int[] d = OwbFit.DIRS[dir & 3];
		int xa = o.getX() + d[0] * r0, za = o.getZ() + d[1] * r0, xb = o.getX() + d[0] * r1, zb = o.getZ() + d[1] * r1;
		return new BoundingBox(Math.min(xa, xb) - 2, y0, Math.min(za, zb) - 2, Math.max(xa, xb) + 2, y1, Math.max(za, zb) + 2);
	}

	private static int min(int[] v, int dflt) {
		int m = dflt;
		for (int i : v) m = Math.min(m, i);
		return m;
	}

	private static int max(int[] v, int dflt) {
		int m = dflt;
		for (int i : v) m = Math.max(m, i);
		return m;
	}

	// ---- shared writer helpers -------------------------------------------------------------------------------------------------

	static double hash(BlockPos o, int x, int y, int z, int salt) {
		long h = o.asLong() * 0x9E3779B97F4A7C15L + x * 73856093L + y * 19349663L + z * 83492791L + salt * 2654435761L;
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= (h >>> 33);
		return (h >>> 11) * 0x1.0p-53;
	}

	static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && s.isSolid() && !soft(s);
	}

	/** Plants, leaves, snow layers, vines and other replaceables: the only things a fit piece removes outside a room. */
	static boolean soft(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && (s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(Blocks.SNOW) || s.is(Blocks.VINE)
			|| s.is(Blocks.MOSS_CARPET) || s.is(Blocks.LILY_PAD) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS));
	}

	static void softClear(BlockSink s, int x, int y0, int y1, int z) {
		for (int y = y0; y <= y1; y++) if (soft(s.get(x, y, z))) s.set(x, y, z, AIR);
	}

	/** Room interior only: everything above the floor goes, except water (an arena never drains the sea). */
	static void interior(BlockSink s, int x, int y0, int y1, int z) {
		for (int y = y0; y <= y1; y++) {
			BlockState st = s.get(x, y, z);
			if (!st.isAir() && st.getFluidState().isEmpty()) s.set(x, y, z, AIR);
		}
	}

	/** Fills down from yTop with the material ladder until solid ground, at most cap blocks. True if it reached the ground. */
	static boolean underpin(BlockSink s, int x, int yTop, int z, BlockState[] ladder, int cap) {
		for (int i = 0; i < cap; i++) {
			if (solid(s.get(x, yTop - i, z))) return true;
			s.set(x, yTop - i, z, ladder[Math.min(i, ladder.length - 1)]);
		}
		return solid(s.get(x, yTop - cap, z));
	}

	static BlockState stair(net.minecraft.world.level.block.Block b, Direction facing, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState log(net.minecraft.world.level.block.Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	static Direction dirOf(int dir) {
		return switch (dir & 3) {
			case 0 -> Direction.EAST;
			case 1 -> Direction.SOUTH;
			case 2 -> Direction.WEST;
			default -> Direction.NORTH;
		};
	}

	public static void build(OwbFitPiece p, BlockSink s) {
		switch (p.kind + "/" + p.part) {
			case "croaking_shrine/core" -> shrineDeck(p, s);
			case "croaking_shrine/walk" -> shrineWalk(p, s);
			case "glacier_shelf/core" -> glacierShelf(p, s);
			case "glacier_shelf/slope" -> glacierSlope(p, s);
			case "glacier_shelf/buttress" -> glacierButtress(p, s);
			case "glacier_shelf/stair" -> glacierStair(p, s);
			case "sun_chamber/core" -> sunChamber(p, s);
			case "sun_chamber/shaft" -> sunShaft(p, s);
			case "wreck_ring/core" -> wreckDeck(p, s);
			case "wreck_ring/stack" -> wreckStack(p, s);
			case "wreck_ring/hulk" -> wreckHulk(p, s);
			default -> {}
		}
	}

	// =================================================================================================================
	// Croaking Shrine: a mangrove stilt shrine. Deck ring on piles one above the water line, the frog pool enclosed beneath
	// it, a mud-brick dais under a gabled mangrove shrine roof to the north, boardwalks east, south and west to dry land.
	// =================================================================================================================

	static void shrineDeck(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int g = o.getY() - 1;
		BlockState water = Blocks.WATER.defaultBlockState(), mud = Blocks.MUD.defaultBlockState();
		BlockState[] bed = {Blocks.MUD.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState()};
		BlockState[] rimLadder = {Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(), Blocks.MUD.defaultBlockState()};
		BlockState planks = Blocks.MANGROVE_PLANKS.defaultBlockState(), pile = Blocks.MANGROVE_LOG.defaultBlockState();
		for (int dx = -15; dx <= 15; dx++) {
			for (int dz = -15; dz <= 15; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 15.5) continue;
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				boolean dais = Math.abs(dx) <= 4 && dz >= -14 && dz <= -10;
				if (d <= 9.5 && !dais) {
					// the pool: open water one below the deck, enclosed by the rim, on a mud bed
					int depth = d <= 3.5 ? 3 : 2;
					interior(s, x, g, g + 6, z);
					for (int y = g - 1; y >= g - depth; y--) s.set(x, y, z, water);
					s.set(x, g - depth - 1, z, mud);
					underpin(s, x, g - depth - 2, z, bed, 6);
					if (d > 4.0 && hash(o, dx, 0, dz, 1) < 0.24) s.set(x, g, z, Blocks.LILY_PAD.defaultBlockState());
					else if (hash(o, dx, 1, dz, 2) < 0.14) s.set(x, g - depth, z, Blocks.SEAGRASS.defaultBlockState());
					continue;
				}
				interior(s, x, g + 1, g + 5, z);
				if (dais) continue; // written below
				if (d <= 10.6) {
					// rim wall under the deck edge: keeps the pool's water in, down to the ground
					for (int y = g - 1; y >= g - 4; y--) s.set(x, y, z, hash(o, dx, y, dz, 3) < 0.7 ? Blocks.MUD_BRICKS.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState());
					underpin(s, x, g - 5, z, rimLadder, OwbFit.CAP);
					s.set(x, g, z, log(Blocks.STRIPPED_MANGROVE_LOG, Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X));
					continue;
				}
				boolean beam = dx == 0 || dz == 0 || dx == dz || dx == -dz;
				s.set(x, g, z, beam ? log(Blocks.STRIPPED_MANGROVE_LOG, Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z) : planks);
				if ((dx & 3) == 0 && (dz & 3) == 0 || d > 14.6 && (dx + dz & 3) == 0) underpin(s, x, g - 1, z, new BlockState[] {pile}, 18);
				boolean gap = (Math.abs(dz) <= 1 && Math.abs(dx) > 8) || (Math.abs(dx) <= 1 && dz > 8);
				if (d > 14.5 && !gap) s.set(x, g + 1, z, Blocks.MANGROVE_FENCE.defaultBlockState());
				else if (hash(o, dx, 2, dz, 4) < 0.10) s.set(x, g + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
			}
		}
		// lantern posts on the ring (the old pillar spots), none in front of the shrine
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8 + Math.PI / 8;
			int px = (int) Math.round(Math.cos(a) * 12.8), pz = (int) Math.round(Math.sin(a) * 12.8);
			if (pz < -10) continue;
			for (int y = 1; y <= 3; y++) s.set(o.getX() + px, g + y, o.getZ() + pz, pile);
			s.set(o.getX() + px, g + 4, o.getZ() + pz, Blocks.LANTERN.defaultBlockState());
			if (hash(o, i, 9, 0, 5) < 0.6) s.set(o.getX() + px + (px > 0 ? -1 : 1), g + 3, o.getZ() + pz,
				Blocks.VINE.defaultBlockState().setValue(px > 0 ? VineBlock.EAST : VineBlock.WEST, true));
		}
		shrineDais(p, s, g);
	}

	private static void shrineDais(OwbFitPiece p, BlockSink s, int g) {
		BlockPos o = p.o;
		BlockState[] plinth = {Blocks.MUD_BRICKS.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState()};
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -14; dz <= -10; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				s.set(x, g, z, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
				s.set(x, g + 1, z, dz == -10 ? Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState() : ruinStone(o, dx, 0, dz));
				underpin(s, x, g - 1, z, plinth, OwbFit.CAP);
			}
		}
		// mangrove posts and a gabled roof over the cauldron (ridge east-west, overhang 1)
		BlockState post = Blocks.MANGROVE_LOG.defaultBlockState(), planks = Blocks.MANGROVE_PLANKS.defaultBlockState();
		for (int px : new int[] {-4, 4}) for (int pz : new int[] {-14, -10}) for (int y = 2; y <= 4; y++) s.set(o.getX() + px, g + y, o.getZ() + pz, post);
		for (int dz = -15; dz <= -9; dz++) {
			int off = Math.abs(dz + 12);
			int ry = g + 8 - off;
			for (int dx = -5; dx <= 5; dx++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (off == 0) s.set(x, ry, z, Blocks.MANGROVE_SLAB.defaultBlockState());
				else s.set(x, ry, z, stair(Blocks.MANGROVE_STAIRS, dz > -12 ? Direction.NORTH : Direction.SOUTH, false));
				// gable ends filled under the roof
				if (Math.abs(dx) == 4 && off <= 2) for (int y = g + 5; y < ry; y++) s.set(x, y, z, planks);
			}
		}
		for (int dx = -3; dx <= 3; dx++) for (int y = g + 2; y <= g + 5; y++) s.set(o.getX() + dx, y, o.getZ() - 14, dx == 0 && y == g + 4 ? Blocks.MANGROVE_TRAPDOOR.defaultBlockState() : planks);
		s.set(o.offset(Blueprints.SHRINE_CAULDRON), OwbBlocks.GULPMOTHER_SHRINE_CAULDRON.defaultBlockState());
		s.set(o.getX() - 2, g + 2, o.getZ() - 11, Blocks.LANTERN.defaultBlockState());
		s.set(o.getX() + 2, g + 2, o.getZ() - 11, Blocks.LANTERN.defaultBlockState());
		for (int sx : new int[] {-2, 2}) {
			for (int y = g + 5; y <= g + 7; y++) s.set(o.getX() + sx, y, o.getZ() - 12, Blocks.IRON_CHAIN.defaultBlockState());
			s.set(o.getX() + sx, g + 4, o.getZ() - 12, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		for (int y = 2; y <= 4; y++) {
			if (hash(o, 0, y, 0, 11) < 0.7) s.set(o.getX() - 5, g + y, o.getZ() - 14, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true));
		}
	}

	private static BlockState ruinStone(BlockPos o, int x, int y, int z) {
		double n = hash(o, x, y, z, 12);
		if (n < 0.45) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (n < 0.65) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		if (n < 0.85) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	static void shrineWalk(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int g = o.getY() - 1;
		int[] d = OwbFit.DIRS[p.a & 3];
		Direction out = dirOf(p.a);
		int lx = -d[1], lz = d[0];
		Direction.Axis along = d[0] != 0 ? Direction.Axis.X : Direction.Axis.Z;
		int prev = g;
		for (int i = 0; i < p.prof.length; i++) {
			int y = p.prof[i], r = 16 + i;
			int cx = o.getX() + d[0] * r, cz = o.getZ() + d[1] * r;
			for (int k = -1; k <= 1; k++) {
				int x = cx + lx * k, z = cz + lz * k;
				if (!s.writes(x, z)) continue;
				softClear(s, x, y + 1, y + 3, z);
				BlockState plank = y > prev ? stair(Blocks.MANGROVE_STAIRS, out, false)
					: y < prev ? stair(Blocks.MANGROVE_STAIRS, out.getOpposite(), false)
					: k == 0 ? log(Blocks.STRIPPED_MANGROVE_LOG, along) : Blocks.MANGROVE_PLANKS.defaultBlockState();
				s.set(x, y, z, plank);
				if (k != 0 && i % 3 == 0) underpin(s, x, y - 1, z, new BlockState[] {Blocks.MANGROVE_LOG.defaultBlockState()}, 16);
				if (k != 0 && i % 4 != 3 && i > 0 && y == prev) s.set(x, y + 1, z, Blocks.MANGROVE_FENCE.defaultBlockState());
			}
			prev = y;
		}
		// the far end: a lantern post on the right-hand side (on land, or on its own pile over water)
		int last = p.prof.length - 1, ly = p.prof[last];
		int ex = o.getX() + d[0] * (16 + last) + lx * 2, ez = o.getZ() + d[1] * (16 + last) + lz * 2;
		if (s.writes(ex, ez)) {
			underpin(s, ex, ly, ez, new BlockState[] {Blocks.MANGROVE_LOG.defaultBlockState()}, 16);
			s.set(ex, ly + 1, ez, Blocks.MANGROVE_FENCE.defaultBlockState());
			s.set(ex, ly + 2, ez, Blocks.MANGROVE_FENCE.defaultBlockState());
			s.set(ex, ly + 3, ez, Blocks.LANTERN.defaultBlockState());
		}
	}

	// =================================================================================================================
	// Glacier Shelf: the snow plateau on its own floor (the ground meets it through a thin noise beard), the avalanche slope
	// anchored into the uphill ground, packed-ice buttresses where the ground drops, the entrance stair facing downhill.
	// =================================================================================================================

	static void glacierShelf(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int g = o.getY() - 1;
		BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
		BlockState[] ladder = {Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
		for (int dx = -18; dx <= 18; dx++) {
			for (int dz = -17; dz <= 18; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 18.0) continue;
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				interior(s, x, g + 1, g + 12, z);
				boolean powder = d > 5 && d < 14 && hash(o, dx >> 1, 0, dz >> 1, 31) < 0.08;
				s.set(x, g, z, powder ? Blocks.POWDER_SNOW.defaultBlockState() : snow);
				s.set(x, g - 1, z, d > 16 ? Blocks.STONE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
				s.set(x, g - 2, z, Blocks.STONE.defaultBlockState());
				underpin(s, x, g - 3, z, ladder, OwbFit.CAP);
				// a low packed-ice kerb on the rim, broken, so the edge reads from below (open at the entrance)
				if (d > 17.3 && dz > -12 && !inGate(p.a, dx, dz) && hash(o, dx, 3, dz, 33) < 0.45) s.set(x, g + 1, z, Blocks.PACKED_ICE.defaultBlockState());
			}
		}
		// the 4 braziers stay LIT campfires: they are Rimeback's mechanic (phase 3 blizzard shelter, Campfire Stomp puts them out); the
		// only lit campfires in any boss site. Other light comes from rim lanterns.
		for (int[] c : Blueprints.CAMPFIRES) {
			s.set(o.getX() + c[0], g + 1, o.getZ() + c[2], Blocks.STONE_BRICKS.defaultBlockState());
			s.set(o.getX() + c[0], g + 2, o.getZ() + c[2], Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6 + Math.PI / 6;
			int px = (int) Math.round(Math.cos(a) * 16), pz = (int) Math.round(Math.sin(a) * 16);
			if (pz < -12 || inGate(p.a, px, pz)) continue;
			s.set(o.getX() + px, g + 1, o.getZ() + pz, Blocks.PACKED_ICE.defaultBlockState());
			s.set(o.getX() + px, g + 2, o.getZ() + pz, Blocks.BLUE_ICE.defaultBlockState());
			s.set(o.getX() + px, g + 3, o.getZ() + pz, Blocks.LANTERN.defaultBlockState());
		}
		// ice outcrops (cover from the avalanche, which comes from the north) and the frozen heart
		int[][] outcrops = {{-7, -3}, {6, -5}, {0, 7}};
		for (int[] oc : outcrops) {
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = 0; az <= 1; az++) {
					int hh = 3 - (az == 1 ? 1 : 0) - (Math.abs(ax) == 1 && az == 1 ? 1 : 0);
					for (int y = 1; y <= hh; y++) {
						s.set(o.getX() + oc[0] + ax, g + y, o.getZ() + oc[1] + az, y == hh ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
					}
				}
			}
		}
		s.set(o.getX(), g, o.getZ(), Blocks.BLUE_ICE.defaultBlockState());
		s.set(o.offset(Blueprints.FROZEN_HEART), OwbBlocks.FROZEN_HEART.defaultBlockState());
	}

	/** Whether (dx, dz) on the plateau rim lies in front of the entrance stair (direction dir), which must stay open. */
	static boolean inGate(int dir, int dx, int dz) {
		int[] d = OwbFit.DIRS[dir & 3];
		int along = dx * d[0] + dz * d[1], across = Math.abs(dx * d[1] - dz * d[0]);
		return along > 12 && across <= 2;
	}

	static void glacierSlope(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int g = o.getY() - 1;
		BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
		BlockState[] core = {Blocks.PACKED_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -28; dz <= -18; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				// the ramp rises 1.25 per block northwards; where the hillside is higher it stays (the ramp runs into it)
				int top = g + Math.min(13, (int) ((-dz - 17) * 1.25 + 1)) - (Math.abs(dx) >= 15 ? 1 : 0);
				softClear(s, x, top + 1, top + 4, z);
				if (solid(s.get(x, top + 1, z))) continue; // anchored: the uphill ground is above the ramp here
				s.set(x, top, z, snow);
				s.set(x, top - 1, z, snow);
				underpin(s, x, top - 2, z, core, 28);
				if (hash(o, dx, 1, dz, 32) < 0.5) s.set(x, top + 1, z, Blocks.SNOW.defaultBlockState());
			}
		}
	}

	static void glacierButtress(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int g = o.getY() - 1;
		double ang = Math.PI * 2 * p.a / 8, c = Math.cos(ang), sn = Math.sin(ang);
		BlockState[] ice = {Blocks.PACKED_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), Blocks.STONE.defaultBlockState()};
		for (int k = 0; k <= p.b; k++) {
			for (int w = -1; w <= 1; w++) {
				int x = o.getX() + (int) Math.round(c * (17 + k) - sn * w), z = o.getZ() + (int) Math.round(sn * (17 + k) + c * w);
				if (!s.writes(x, z)) continue;
				int top = g - k - (w != 0 ? 1 : 0);
				if (solid(s.get(x, top, z))) continue; // the ground is already this high: nothing to hold up
				s.set(x, top, z, w == 0 && k % 4 == 1 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
				underpin(s, x, top - 1, z, ice, 18);
				if (w == 0 && hash(o, k, p.a, 0, 34) < 0.5) softClear(s, x, top + 1, top + 1, z);
			}
		}
	}

	static void glacierStair(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int g = o.getY() - 1;
		int[] d = OwbFit.DIRS[p.a & 3];
		Direction out = dirOf(p.a);
		int lx = -d[1], lz = d[0];
		BlockState[] ladder = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState()};
		// gate obelisks of blue ice either side of the top step, on the plateau rim
		for (int k : new int[] {-2, 2}) {
			int x = o.getX() + d[0] * 17 + lx * k, z = o.getZ() + d[1] * 17 + lz * k;
			if (!s.writes(x, z)) continue;
			s.set(x, g + 1, z, Blocks.PACKED_ICE.defaultBlockState());
			s.set(x, g + 2, z, Blocks.BLUE_ICE.defaultBlockState());
			s.set(x, g + 3, z, Blocks.BLUE_ICE.defaultBlockState());
			s.set(x, g + 4, z, Blocks.LANTERN.defaultBlockState());
		}
		// the rim in front of the stair stays open, at the plateau floor
		for (int r = 17; r <= 18; r++) {
			for (int k = -1; k <= 1; k++) {
				int x = o.getX() + d[0] * r + lx * k, z = o.getZ() + d[1] * r + lz * k;
				if (!s.writes(x, z)) continue;
				interior(s, x, g + 1, g + 3, z);
				s.set(x, g, z, Blocks.SNOW_BLOCK.defaultBlockState());
				underpin(s, x, g - 1, z, ladder, OwbFit.CAP);
			}
		}
		int prev = g;
		for (int i = 0; i < p.prof.length; i++) {
			int y = p.prof[i], r = 19 + i;
			for (int k = -1; k <= 1; k++) {
				int x = o.getX() + d[0] * r + lx * k, z = o.getZ() + d[1] * r + lz * k;
				if (!s.writes(x, z)) continue;
				interior(s, x, y + 1, y + 3, z); // the stair is cut into the slope where the ground stands above it
				BlockState tread = y < prev ? stair(Blocks.STONE_BRICK_STAIRS, out.getOpposite(), false)
					: y > prev ? stair(Blocks.STONE_BRICK_STAIRS, out, false)
					: k == 0 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
				s.set(x, y, z, tread);
				underpin(s, x, y - 1, z, ladder, OwbFit.CAP);
			}
			prev = y;
		}
		int last = p.prof.length - 1, ly = p.prof[last];
		int ex = o.getX() + d[0] * (19 + last) + lx * 2, ez = o.getZ() + d[1] * (19 + last) + lz * 2;
		if (s.writes(ex, ez)) {
			underpin(s, ex, ly, ez, ladder, OwbFit.CAP);
			s.set(ex, ly + 1, ez, Blocks.PACKED_ICE.defaultBlockState());
			s.set(ex, ly + 2, ez, Blocks.LANTERN.defaultBlockState());
		}
	}

	// =================================================================================================================
	// Sun Chamber: the same hall (encapsulated at noise level), faience bands and a cavetto cornice, gilt in yellow terracotta
	// and lapis in blue terracotta (no valuable blocks); the shaft rises beside the pyramid into a pyramid-capped kiosk.
	// =================================================================================================================

	static void sunChamber(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int f = o.getY() - 1;
		BlockState ss = Blocks.SANDSTONE.defaultBlockState(), cut = Blocks.CUT_SANDSTONE.defaultBlockState();
		BlockState smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState(), chis = Blocks.CHISELED_SANDSTONE.defaultBlockState();
		BlockState orange = Blocks.DYED_TERRACOTTA.orange().defaultBlockState(), blue = Blocks.DYED_TERRACOTTA.blue().defaultBlockState();
		for (int dx = -13; dx <= 13; dx++) {
			for (int dz = -13; dz <= 13; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				boolean inside = Math.abs(dx) <= 10 && Math.abs(dz) <= 10;
				boolean wall = Math.abs(dx) == 11 || Math.abs(dz) == 11;
				for (int y = f - 4; y <= f + 14; y++) {
					int ry = y - f;
					BlockState st;
					if (y <= f - 3) st = cut;
					else if (y < f) st = ss;
					else if (y == f) st = inside ? floorTile(dx, dz) : cut;
					else if (inside && ry <= 11) st = AIR;
					else if (inside && ry == 12) st = (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) ? AIR : ((dx + dz) % 4 == 0 ? chis : cut);
					else if (wall) {
						boolean pilaster = (Math.abs(dx) == 11 ? Math.floorMod(dz, 4) == 0 : Math.floorMod(dx, 4) == 0);
						st = ry == 1 || ry == 11 ? chis : pilaster ? cut : ry == 4 || ry == 8 ? blue : ry % 4 == 2 ? orange
							: ry >= 5 && ry <= 7 && hash(o, dx, ry, dz, 51) < 0.3 ? chis : cut;
					} else st = ss;
					s.set(x, y, z, st);
				}
				// cavetto cornice: upside-down stairs along the top of the walls, leaning out over the hall
				if (inside && (Math.abs(dx) == 10 || Math.abs(dz) == 10) && !(Math.abs(dx) == 10 && Math.abs(dz) == 10)) {
					Direction toWall = Math.abs(dx) == 10 ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
					s.set(x, f + 11, z, stair(Blocks.SANDSTONE_STAIRS, toWall, true));
				}
			}
		}
		// sunbeam shaft (closed by the eclipse in phase 3)
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = f + 13; y <= f + 16; y++) s.set(o.getX() + dx, y, o.getZ() + dz, AIR);
				s.set(o.getX() + dx, f + 17, o.getZ() + dz, dx == 0 && dz == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GLOWSTONE.defaultBlockState());
			}
		}
		for (int y = f + 3; y <= f + 15; y += 3) s.set(o.getX(), y, o.getZ(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
		// idol pillars (papyrus columns: chiselled base and capital, a faience ring)
		for (int[] idol : Blueprints.SUN_IDOLS) {
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					for (int y = 1; y <= 9; y++) {
						BlockState st = (y == 1 || y == 9) ? chis : y == 5 && (ax != 0 || az != 0) ? blue : (ax == 0 && az == 0 ? ss : cut);
						s.set(o.getX() + idol[0] + ax, f + y, o.getZ() + idol[2] + az, st);
					}
				}
			}
			s.set(o.getX() + idol[0], f + idol[1], o.getZ() + idol[2], OwbBlocks.NEFERKHET_SUN_IDOL.defaultBlockState());
		}
		// sarcophagi: smooth base, gilt (yellow terracotta) and lapis-faience (blue terracotta) lid, removed when they open
		for (int[] sc : Blueprints.SARCOPHAGI) {
			int sx = sc[0] < 0 ? -1 : 1;
			for (int ax = 0; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					int x = o.getX() + sc[0] + sx * ax, z = o.getZ() + az;
					s.set(x, f + 1, z, smooth);
					s.set(x, f + 2, z, az == 0 ? Blocks.DYED_TERRACOTTA.yellow().defaultBlockState() : blue);
				}
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -10; dz <= -7; dz++) {
				if (Math.abs(dx) == 2 && dz == -7) continue;
				s.set(o.getX() + dx, f + 1, o.getZ() + dz, dz == -7 ? Blocks.SANDSTONE_SLAB.defaultBlockState() : smooth);
			}
		}
		s.set(o.getX() + Blueprints.SUN_ALTAR.getX(), f + 1, o.getZ() + Blueprints.SUN_ALTAR.getZ(), OwbBlocks.NEFERKHET_SUN_ALTAR.defaultBlockState());
		s.set(o.getX() - 2, f + 2, o.getZ() - 9, Blocks.LANTERN.defaultBlockState());
		s.set(o.getX() + 2, f + 2, o.getZ() - 9, Blocks.LANTERN.defaultBlockState());
		for (int i = -8; i <= 8; i += 4) {
			s.set(o.getX() + i, f + 6, o.getZ() - 10, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9));
			s.set(o.getX() + i, f + 6, o.getZ() + 10, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9));
		}
	}

	private static BlockState floorTile(int dx, int dz) {
		int r = Math.max(Math.abs(dx), Math.abs(dz));
		if (r <= 1) return Blocks.CHISELED_SANDSTONE.defaultBlockState();
		if (r == 3 || r == 7) return Blocks.DYED_TERRACOTTA.blue().defaultBlockState();
		if (r == 4 && (dx == 0 || dz == 0)) return Blocks.DYED_TERRACOTTA.yellow().defaultBlockState();
		return (dx + dz & 1) == 0 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
	}

	static void sunShaft(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int f = o.getY() - 1;
		BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState(), smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		BlockState chis = Blocks.CHISELED_SANDSTONE.defaultBlockState(), orange = Blocks.DYED_TERRACOTTA.orange().defaultBlockState();
		// corridor east from the hall
		for (int dx = 11; dx <= 17; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				for (int y = f - 1; y <= f + 5; y++) {
					boolean air = Math.abs(dz) <= 1 && y >= f + 1 && y <= f + 4 && dx <= 16;
					s.set(x, y, z, air ? AIR : (y == f ? smooth : cut));
				}
			}
		}
		// ladder shaft to the surface
		int shaftTop = Math.max(p.b, f + 8);
		for (int dx = 14; dx <= 18; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				boolean wall = dx == 14 || dx == 18 || Math.abs(dz) == 2;
				for (int y = f + 1; y < shaftTop; y++) {
					boolean corridorOpen = dx == 14 && Math.abs(dz) <= 1 && y <= f + 4;
					s.set(x, y, z, wall && !corridorOpen ? cut : AIR);
				}
				s.set(x, shaftTop, z, wall ? cut : (dx == 17 && dz == 0 ? AIR : smooth));
				// the kiosk: a little pyramid-capped shrine in the pyramid's own banding, door to the south
				boolean corner = (dx == 14 || dx == 18) && Math.abs(dz) == 2;
				for (int y = shaftTop + 1; y <= shaftTop + 3; y++) {
					int ry = y - shaftTop;
					boolean door = dz == 2 && dx == 16 && ry <= 2;
					BlockState st = !wall || door ? AIR : corner ? chis : ry == 2 ? orange : cut;
					if (dz == 2 && dx == 16 && ry == 3) st = Blocks.DYED_TERRACOTTA.blue().defaultBlockState();
					s.set(x, y, z, st);
				}
				// pyramid cap: two stepped courses of outward-sloping stairs and a gilt (yellow terracotta) pyramidion
				boolean edge1 = wall;
				s.set(x, shaftTop + 4, z, edge1 ? capStair(dx - 16, dz) : cut);
				if (dx >= 15 && dx <= 17 && Math.abs(dz) <= 1) {
					boolean edge2 = dx == 15 || dx == 17 || Math.abs(dz) == 1;
					s.set(x, shaftTop + 5, z, edge2 ? capStair(dx - 16, dz) : chis);
				}
			}
		}
		if (s.writes(o.getX() + 16, o.getZ())) s.set(o.getX() + 16, shaftTop + 6, o.getZ(), Blocks.DYED_TERRACOTTA.yellow().defaultBlockState());
		for (int y = f + 1; y <= shaftTop + 1; y++) {
			if (s.writes(o.getX() + 17, o.getZ())) s.set(o.getX() + 17, y, o.getZ(), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		if (s.writes(o.getX() + 15, o.getZ() - 1)) s.set(o.getX() + 15, shaftTop + 1, o.getZ() - 1, Blocks.LANTERN.defaultBlockState());
		// sand drifts around the kiosk foot are left as they are; plants on its roof line go
		for (int dx = 14; dx <= 18; dx++) for (int dz = -2; dz <= 2; dz++) {
			if (s.writes(o.getX() + dx, o.getZ() + dz)) softClear(s, o.getX() + dx, shaftTop + 6, shaftTop + 7, o.getZ() + dz);
		}
	}

	/** A stair on the kiosk's pyramid cap: high side towards the centre (16, 0). */
	private static BlockState capStair(int rx, int rz) {
		Direction toCentre = Math.abs(rx) >= Math.abs(rz) ? (rx > 0 ? Direction.WEST : Direction.EAST) : (rz > 0 ? Direction.NORTH : Direction.SOUTH);
		return stair(Blocks.SANDSTONE_STAIRS, toCentre, false);
	}

	// =================================================================================================================
	// Wreck Ring: the deck ring of lashed wrecks at sea level, carried on 8 hull stacks down to the sea bed; 3 sunken hulks
	// rest on sand shoals between the deck hulls, their masts breaking the surface.
	// =================================================================================================================

	static void wreckDeck(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int deck = o.getY() - 1;
		for (int dx = -19; dx <= 19; dx++) {
			for (int dz = -19; dz <= 19; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				double d = Math.sqrt(dx * dx + dz * dz);
				double edge = 17.0 + hash(o, (int) Math.round(Math.atan2(dz, dx) * 6), 0, 0, 41) * 2.0;
				if (d < 5.5 || d > edge) continue;
				softClear(s, x, deck, deck + 4, z);
				double n = hash(o, dx, 0, dz, 42);
				BlockState plank = n < 0.55 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : n < 0.85 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
				boolean beam = (dx == 0 || dz == 0 || dx == dz || dx == -dz);
				if (n > 0.975 && d > 7 && d < 10) continue; // a few gaps between planks, never under the hulls, rails or lanterns
				s.set(x, deck, z, beam ? log(Blocks.STRIPPED_SPRUCE_LOG, Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z) : plank);
				if (d > edge - 1.0 && hash(o, dx, 1, dz, 43) < 0.6) s.set(x, deck + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
				if (d < 6.5 && hash(o, dx, 2, dz, 44) < 0.35) s.set(x, deck + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		for (int i = 0; i < 3; i++) {
			double a = Math.PI * 2 * i / 3 + Math.PI / 6;
			double cx = Math.cos(a), cz = Math.sin(a);
			int hx = o.getX() + (int) Math.round(cx * 12.5), hz = o.getZ() + (int) Math.round(cz * 12.5);
			int mastH = 7 + (int) (hash(o, i, 5, 0, 45) * 6);
			for (int y = 1; y <= mastH; y++) s.set(hx, deck + y, hz, Blocks.SPRUCE_LOG.defaultBlockState());
			for (int k = -3; k <= 3; k++) {
				int sx = hx + (int) Math.round(-cz * k), sz = hz + (int) Math.round(cx * k);
				s.set(sx, deck + mastH - 1, sz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
				for (int y = 2; y <= mastH - 2; y++) {
					if (Math.abs(k) <= 2 && hash(o, k, y, i, 46) < 0.55) s.set(sx, deck + y, sz, Blocks.WOOL.white().defaultBlockState());
				}
			}
			// a raised bow and gunwale along the outer edge of this hull
			for (int k = -6; k <= 6; k++) {
				int gx = o.getX() + (int) Math.round(cx * 17 - cz * k), gz = o.getZ() + (int) Math.round(cz * 17 + cx * k);
				s.set(gx, deck + 1, gz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				if (Math.abs(k) >= 5) s.set(gx, deck + 2, gz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				else if (hash(o, k, 7, i, 47) < 0.7) s.set(gx, deck + 2, gz, Blocks.DARK_OAK_FENCE.defaultBlockState());
			}
			s.set(hx + (int) Math.round(-cz * 2), deck + 1, hz + (int) Math.round(cx * 2), Blocks.BARREL.defaultBlockState());
			s.set(hx + (int) Math.round(cz * 2), deck + 1, hz + (int) Math.round(-cx * 2), Blocks.BARREL.defaultBlockState());
			s.set(hx, deck + mastH + 1, hz, Blocks.LANTERN.defaultBlockState());
		}
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6;
			int lx = o.getX() + (int) Math.round(Math.cos(a) * 6.5), lz = o.getZ() + (int) Math.round(Math.sin(a) * 6.5);
			s.set(lx, deck + 1, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
			s.set(lx, deck + 2, lz, Blocks.LANTERN.defaultBlockState());
		}
		s.set(o.getX() + Blueprints.WRECK_CAPSTAN.getX(), deck + 1, o.getZ() + Blueprints.WRECK_CAPSTAN.getZ(), OwbBlocks.VORLAGH_CAPSTAN.defaultBlockState());
	}

	static void wreckStack(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int deck = o.getY() - 1, bed = p.at.getY();
		for (int ax = -1; ax <= 1; ax++) {
			for (int az = -1; az <= 1; az++) {
				int x = p.at.getX() + ax, z = p.at.getZ() + az;
				if (!s.writes(x, z)) continue;
				boolean centre = ax == 0 && az == 0, corner = ax != 0 && az != 0;
				// keel-first hull sections stacked up: a log course every 4, planks between, a few stove-in corners (never the centre post)
				for (int y = bed - 1; y < deck; y++) {
					int ry = y - bed;
					if (corner && ry > 2 && hash(o, x, y, z, 61) < 0.18) continue;
					BlockState st = centre ? Blocks.DARK_OAK_LOG.defaultBlockState()
						: Math.floorMod(ry, 4) == 0 ? log(Blocks.STRIPPED_DARK_OAK_LOG, (p.a & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z)
						: hash(o, x, y, z, 62) < 0.6 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
					s.set(x, y, z, st);
				}
				underpin(s, x, bed - 2, z, new BlockState[] {Blocks.GRAVEL.defaultBlockState()}, 4);
			}
		}
	}

	static void wreckHulk(OwbFitPiece p, BlockSink s) {
		BlockPos o = p.o;
		int sea = o.getY() - 1, bed = p.at.getY();
		int hx = p.at.getX(), hz = p.at.getZ();
		// the shoal: a low sand and gravel mound the hulk rests on
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 5.2) continue;
				int x = hx + dx, z = hz + dz;
				if (!s.writes(x, z)) continue;
				if (d < 3.6) s.set(x, bed, z, hash(o, dx, p.a, dz, 71) < 0.7 ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
				underpin(s, x, bed - 1, z, new BlockState[] {Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()}, 6);
			}
		}
		// the hull: 9 long along the tangent axis, listing (one side taller), stove-in planks
		double a = Math.PI * 2 * p.a / 3 + Math.PI / 2;
		boolean alongX = Math.abs(Math.sin(a)) > Math.abs(Math.cos(a));
		int keel = bed + 1;
		for (int k = -4; k <= 4; k++) {
			int half = Math.abs(k) == 4 ? 0 : Math.abs(k) == 3 ? 1 : 2;
			for (int w = -half; w <= half; w++) {
				int x = hx + (alongX ? k : w), z = hz + (alongX ? w : k);
				if (!s.writes(x, z)) continue;
				s.set(x, keel, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				underpin(s, x, keel - 1, z, new BlockState[] {Blocks.GRAVEL.defaultBlockState()}, 6);
				if (Math.abs(w) == half) {
					int hgt = w > 0 ? 3 : 2;
					for (int y = 1; y <= hgt; y++) {
						if (y < hgt && hash(o, k, y, w + p.a * 7, 72) < 0.15) continue;
						s.set(x, keel + y, z, y == hgt ? log(Blocks.STRIPPED_DARK_OAK_LOG, alongX ? Direction.Axis.X : Direction.Axis.Z) : Blocks.DARK_OAK_PLANKS.defaultBlockState());
					}
				}
			}
		}
		// the mast breaks the surface with its broken yard
		for (int y = keel + 1; y <= sea + 2; y++) if (s.writes(hx, hz)) s.set(hx, y, hz, Blocks.SPRUCE_LOG.defaultBlockState());
		// a patch of broken deck planks floats at the waterline around the mast, so the mast and yard read as the wreck's, never adrift
		for (int fx = -1; fx <= 1; fx++) for (int fz = -1; fz <= 1; fz++) {
			if ((fx != 0 || fz != 0) && s.writes(hx + fx, hz + fz) && hash(o, fx + p.a * 5, fz, 0, 74) < 0.75) {
				s.set(hx + fx, sea, hz + fz, hash(o, fx, fz, p.a, 75) < 0.5 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
		}
		for (int k = -1; k <= 1; k += 2) {
			int x = hx + (alongX ? 0 : k), z = hz + (alongX ? k : 0);
			if (s.writes(x, z) && hash(o, p.a, k, 0, 73) < 0.7) s.set(x, sea + 1, z, log(Blocks.STRIPPED_SPRUCE_LOG, alongX ? Direction.Axis.Z : Direction.Axis.X));
		}
	}
}
