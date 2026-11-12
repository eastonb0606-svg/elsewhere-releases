package dev.elsewhere.overworld_bosses.world;

import dev.elsewhere.overworld_bosses.OwbBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The boss arenas as code, written through a {@link BlockSink} so the same layout is used by world generation
 * (structure pieces) and by the dev command / self-test. Every random choice is a positional hash of the origin,
 * so the layout is identical no matter which chunk is being generated.
 *
 * <p>Origins: the block where players stand at the arena centre. Layout constants below are shared with the summon
 * code (where the trigger block is relative to the centre).
 */
public final class Blueprints {
	private Blueprints() {}

	// Trigger offsets from the arena centre (origin).
	public static final BlockPos SHRINE_CAULDRON = new BlockPos(0, 1, -11);
	public static final BlockPos SUN_ALTAR = new BlockPos(0, 0, -8);
	public static final BlockPos FROZEN_HEART = new BlockPos(0, 0, 0);
	public static final BlockPos WRECK_CAPSTAN = new BlockPos(0, 0, -10);
	public static final int[][] SUN_IDOLS = {{-6, 10, -6}, {6, 10, -6}, {6, 10, 6}, {-6, 10, 6}}; // clockwise from NW
	public static final int[][] SARCOPHAGI = {{-9, 1, 0}, {9, 1, 0}};
	public static final int[][] CAMPFIRES = {{-9, 1, -9}, {9, 1, -9}, {9, 1, 9}, {-9, 1, 9}};

	/** Absolute position of sun idol i (0..3, clockwise from north-west) for a chamber with origin o. */
	public static BlockPos sunIdolPos(BlockPos o, int i) {
		return o.offset(SUN_IDOLS[i][0], SUN_IDOLS[i][1] - 1, SUN_IDOLS[i][2]);
	}

	static double hash(BlockPos o, int x, int y, int z, int salt) {
		long h = o.asLong() * 0x9E3779B97F4A7C15L + x * 73856093L + y * 19349663L + z * 83492791L + salt * 2654435761L;
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= (h >>> 33);
		return (h >>> 11) * 0x1.0p-53;
	}

	private static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && s.isSolid();
	}

	/** Fills downward from y (inclusive) until reaching solid ground, at most maxDepth blocks. */
	static void foundation(BlockSink s, int x, int y, int z, BlockState fill, int maxDepth) {
		for (int i = 0; i < maxDepth; i++) {
			BlockPos p = new BlockPos(x, y - i, z);
			if (solid(s.get(p))) return;
			s.set(p, fill);
		}
	}

	static void clear(BlockSink s, int x, int y0, int y1, int z) {
		for (int y = y0; y <= y1; y++) s.set(x, y, z, Blocks.AIR.defaultBlockState());
	}

	// =================================================================================================================
	// Croaking Shrine (Old Gulpmother): a mossy ruin around a lily-pad pool, cauldron on the north platform.
	// =================================================================================================================

	public static void croakingShrine(BlockSink s, BlockPos o) {
		int g = o.getY() - 1;
		BlockState water = Blocks.WATER.defaultBlockState();
		BlockState mud = Blocks.MUD.defaultBlockState();
		for (int dx = -15; dx <= 15; dx++) {
			for (int dz = -15; dz <= 15; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 15.0) continue;
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				clear(s, x, g + 1, g + 14, z);
				if (d <= 9.5) {
					int depth = d <= 3.5 ? 2 : 1;
					for (int y = g; y > g - depth; y--) s.set(x, y, z, water);
					s.set(x, g - depth, z, mud);
					foundation(s, x, g - depth - 1, z, mud, 10);
					if (d > 4.0 && hash(o, dx, 0, dz, 1) < 0.30) s.set(x, g + 1, z, Blocks.LILY_PAD.defaultBlockState());
					else if (d <= 9.0 && hash(o, dx, 1, dz, 2) < 0.12) s.set(x, g - depth + 1, z, Blocks.SEAGRASS.defaultBlockState());
				} else {
					double n = hash(o, dx, 0, dz, 3);
					s.set(x, g, z, n < 0.45 ? mud : n < 0.8 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());
					s.set(x, g - 1, z, mud);
					foundation(s, x, g - 2, z, mud, 12);
					if (d < 10.6 && hash(o, dx, 2, dz, 4) < 0.35) s.set(x, g + 1, z, Blocks.MANGROVE_ROOTS.defaultBlockState());
					else if (hash(o, dx, 3, dz, 5) < 0.3) s.set(x, g + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
				}
				// Broken ring wall with gaps at east, south and west.
				if (d >= 11.2 && d <= 12.4 && dz > -9) {
					boolean gap = (Math.abs(dz) <= 1 && Math.abs(dx) > 8) || (Math.abs(dx) <= 1 && dz > 8);
					if (!gap) {
						int h = (int) (hash(o, dx, 4, dz, 6) * 4.2);
						for (int y = 1; y <= h; y++) s.set(x, g + y, z, ruinStone(o, dx, y, dz));
					}
				}
			}
		}
		// Pillars around the ring, some with vines and lanterns.
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8 + Math.PI / 8;
			int px = o.getX() + (int) Math.round(Math.cos(a) * 12.8), pz = o.getZ() + (int) Math.round(Math.sin(a) * 12.8);
			if (pz - o.getZ() < -10) continue;
			int h = 3 + (int) (hash(o, i, 9, 0, 7) * 4);
			for (int y = 1; y <= h; y++) s.set(px, g + y, pz, ruinStone(o, px, y, pz));
			if (hash(o, i, 10, 0, 8) < 0.6) s.set(px, g + h + 1, pz, Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState());
		}
		// North platform with the shrine cauldron, an arch and lanterns.
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -14; dz <= -10; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				s.set(x, g, z, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
				s.set(x, g + 1, z, dz == -10 ? Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState() : ruinStone(o, dx, 0, dz));
				foundation(s, x, g - 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 12);
			}
		}
		for (int y = 2; y <= 6; y++) {
			s.set(o.getX() - 3, g + y, o.getZ() - 13, ruinStone(o, -3, y, -13));
			s.set(o.getX() + 3, g + y, o.getZ() - 13, ruinStone(o, 3, y, -13));
		}
		for (int dx = -3; dx <= 3; dx++) s.set(o.getX() + dx, g + 7, o.getZ() - 13, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
		s.set(o.getX() - 2, g + 6, o.getZ() - 13, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		s.set(o.getX() + 2, g + 6, o.getZ() - 13, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		s.set(o.getX() - 2, g + 2, o.getZ() - 11, Blocks.LANTERN.defaultBlockState());
		s.set(o.getX() + 2, g + 2, o.getZ() - 11, Blocks.LANTERN.defaultBlockState());
		s.set(o.offset(SHRINE_CAULDRON), OwbBlocks.GULPMOTHER_SHRINE_CAULDRON.defaultBlockState());
		for (int y = 1; y <= 4; y++) {
			if (hash(o, 0, y, 0, 11) < 0.7) s.set(o.getX() - 3, g + y + 2, o.getZ() - 12, Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.NORTH, true));
		}
	}

	private static BlockState ruinStone(BlockPos o, int x, int y, int z) {
		double n = hash(o, x, y, z, 12);
		if (n < 0.45) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (n < 0.65) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		if (n < 0.85) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	// =================================================================================================================
	// Farm field (Strawgrim, dev / test only: the real summon works on any farm a player builds).
	// =================================================================================================================

	public static void farmField(BlockSink s, BlockPos o, boolean withScarecrow) {
		int g = o.getY() - 1;
		for (int dx = -13; dx <= 13; dx++) {
			for (int dz = -13; dz <= 13; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				clear(s, x, g + 1, g + 12, z);
				foundation(s, x, g - 1, z, Blocks.DIRT.defaultBlockState(), 12);
				boolean edge = Math.abs(dx) == 13 || Math.abs(dz) == 13;
				if (edge) {
					s.set(x, g, z, Blocks.GRASS_BLOCK.defaultBlockState());
					if ((dx + dz) % 4 == 0) s.set(x, g + 1, z, Blocks.OAK_FENCE.defaultBlockState());
				} else if (dx % 5 == 0 && Math.abs(dz) > 1 && dx != 0) {
					s.set(x, g, z, Blocks.WATER.defaultBlockState());
				} else if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					s.set(x, g, z, Blocks.COARSE_DIRT.defaultBlockState());
				} else {
					s.set(x, g, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
					int age = 4 + (int) (hash(o, dx, 0, dz, 21) * 4);
					s.set(x, g + 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, Math.min(7, age)));
				}
			}
		}
		if (withScarecrow) {
			s.set(o, Blocks.OAK_FENCE.defaultBlockState());
			s.set(o.above(1), Blocks.HAY_BLOCK.defaultBlockState());
			s.set(o.above(2), Blocks.HAY_BLOCK.defaultBlockState());
			s.set(o.above(3), Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
		}
	}

	// =================================================================================================================
	// Sun Chamber (Neferkhet): a hall dug under a desert pyramid; 4 idol pillars, 2 sarcophagi, a roof sunbeam.
	// =================================================================================================================

	/** @param top the surface Y at the ladder shaft (x+15..17), where the entrance kiosk goes. */
	public static void sunChamber(BlockSink s, BlockPos o, int top) {
		int f = o.getY() - 1;
		BlockState ss = Blocks.SANDSTONE.defaultBlockState();
		BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState();
		BlockState smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		BlockState chis = Blocks.CHISELED_SANDSTONE.defaultBlockState();
		for (int dx = -13; dx <= 13; dx++) {
			for (int dz = -13; dz <= 13; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				boolean inside = Math.abs(dx) <= 10 && Math.abs(dz) <= 10;
				for (int y = f - 4; y <= f + 14; y++) {
					BlockState st;
					if (y <= f - 3) st = cut;
					else if (y < f) st = ss;
					else if (y == f) st = inside ? floorTile(dx, dz) : cut;
					else if (inside && y <= f + 11) st = Blocks.AIR.defaultBlockState();
					else if (inside && y == f + 12) st = (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) ? Blocks.AIR.defaultBlockState() : ((dx + dz) % 4 == 0 ? chis : cut);
					else if (Math.abs(dx) == 11 || Math.abs(dz) == 11) st = (y - f) % 4 == 0 ? chis : (y - f) % 4 == 2 ? Blocks.DYED_TERRACOTTA.orange().defaultBlockState() : cut;
					else st = ss;
					s.set(x, y, z, st);
				}
			}
		}
		// Sunbeam shaft (closed by the eclipse in phase 3): light all the way down the centre column.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = f + 13; y <= f + 16; y++) s.set(o.getX() + dx, y, o.getZ() + dz, Blocks.AIR.defaultBlockState());
				s.set(o.getX() + dx, f + 17, o.getZ() + dz, dx == 0 && dz == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GLOWSTONE.defaultBlockState());
			}
		}
		for (int y = f + 3; y <= f + 15; y += 3) s.set(o.getX(), y, o.getZ(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
		// Pillars (3x3) with sun idols on top.
		for (int[] idol : SUN_IDOLS) {
			for (int ax = -1; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					for (int y = 1; y <= 9; y++) {
						BlockState st = (y == 1 || y == 9) ? chis : (ax == 0 && az == 0 ? ss : cut);
						s.set(o.getX() + idol[0] + ax, f + y, o.getZ() + idol[2] + az, st);
					}
				}
			}
			s.set(o.getX() + idol[0], f + idol[1], o.getZ() + idol[2], OwbBlocks.NEFERKHET_SUN_IDOL.defaultBlockState());
		}
		// Sarcophagi along the east and west walls: base + gilt (yellow terracotta) and lapis-faience (blue terracotta) lid, removed when they
		// open. No valuable blocks in structures (s-throne B9; same blocks as the fit3 sun chamber).
		for (int[] sc : SARCOPHAGI) {
			int sx = sc[0] < 0 ? -1 : 1;
			for (int ax = 0; ax <= 1; ax++) {
				for (int az = -1; az <= 1; az++) {
					int x = o.getX() + sc[0] + sx * ax, z = o.getZ() + az;
					s.set(x, f + 1, z, smooth);
					s.set(x, f + 2, z, az == 0 ? Blocks.DYED_TERRACOTTA.yellow().defaultBlockState() : Blocks.DYED_TERRACOTTA.blue().defaultBlockState());
				}
			}
		}
		// Altar on a dais in front of the north wall.
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -10; dz <= -7; dz++) {
				if (Math.abs(dx) == 2 && dz == -7) continue;
				s.set(o.getX() + dx, f + 1, o.getZ() + dz, dz == -7 ? Blocks.SANDSTONE_SLAB.defaultBlockState() : smooth);
			}
		}
		s.set(o.getX() + SUN_ALTAR.getX(), f + 1, o.getZ() + SUN_ALTAR.getZ(), OwbBlocks.NEFERKHET_SUN_ALTAR.defaultBlockState());
		s.set(o.getX() - 2, f + 2, o.getZ() - 9, Blocks.LANTERN.defaultBlockState());
		s.set(o.getX() + 2, f + 2, o.getZ() - 9, Blocks.LANTERN.defaultBlockState());
		// Torches of light on the walls.
		for (int i = -8; i <= 8; i += 4) {
			s.set(o.getX() + i, f + 6, o.getZ() - 10, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9));
			s.set(o.getX() + i, f + 6, o.getZ() + 10, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9));
		}
		// Entrance: corridor east (z -1..1) to a ladder shaft at x+15..17 that climbs to the surface kiosk.
		for (int dx = 11; dx <= 17; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				for (int y = f - 1; y <= f + 5; y++) {
					boolean air = Math.abs(dz) <= 1 && y >= f + 1 && y <= f + 4 && dx <= 16;
					s.set(x, y, z, air ? Blocks.AIR.defaultBlockState() : (y == f ? smooth : cut));
				}
			}
		}
		int shaftTop = Math.max(top, f + 8);
		for (int dx = 14; dx <= 18; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				boolean wall = dx == 14 || dx == 18 || Math.abs(dz) == 2;
				for (int y = f + 1; y <= shaftTop + 4; y++) {
					if (y > shaftTop) {
						// Kiosk on the surface: walls with a doorway facing south, a chiselled roof.
						boolean kioskWall = wall && !(dz == 2 && dx == 16 && y <= shaftTop + 2);
						s.set(x, y, z, y == shaftTop + 4 ? chis : kioskWall ? cut : Blocks.AIR.defaultBlockState());
					} else {
						boolean corridorOpen = dx == 14 && Math.abs(dz) <= 1 && y <= f + 4;
						s.set(x, y, z, wall && !corridorOpen ? cut : Blocks.AIR.defaultBlockState());
					}
				}
				s.set(x, shaftTop, z, wall ? cut : (dx == 17 && dz == 0 ? Blocks.AIR.defaultBlockState() : smooth));
			}
		}
		// Ladder on the east wall of the shaft, through a hole in the kiosk floor.
		for (int y = f + 1; y <= shaftTop + 1; y++) {
			s.set(o.getX() + 17, y, o.getZ(), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		s.set(o.getX() + 17, shaftTop, o.getZ(), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		s.set(o.getX() + 15, shaftTop + 2, o.getZ() - 1, Blocks.LANTERN.defaultBlockState());
	}

	private static BlockState floorTile(int dx, int dz) {
		int r = Math.max(Math.abs(dx), Math.abs(dz));
		if (r <= 1) return Blocks.CHISELED_SANDSTONE.defaultBlockState();
		if (r == 3 || r == 7) return Blocks.DYED_TERRACOTTA.blue().defaultBlockState();
		if (r == 4 && (dx == 0 || dz == 0)) return Blocks.DYED_TERRACOTTA.yellow().defaultBlockState(); // gilt inlay, not gold (s-throne B9)
		return (dx + dz & 1) == 0 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
	}

	/** Phase 3 eclipse: the roof seals and the sunbeam goes dark (restored with the arena). */
	public static void sunChamberEclipse(BlockSink s, BlockPos o) {
		int f = o.getY() - 1;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) s.set(o.getX() + dx, f + 12, o.getZ() + dz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
		for (int y = f + 1; y <= f + 11; y++) {
			if (s.get(o.getX(), y, o.getZ()).is(Blocks.LIGHT)) s.set(o.getX(), y, o.getZ(), Blocks.AIR.defaultBlockState());
		}
	}

	// =================================================================================================================
	// Glacier Shelf (Rimeback): snow plateau, slope to the north, 4 campfire braziers, 3 ice outcrops, frozen heart.
	// =================================================================================================================

	public static void glacierShelf(BlockSink s, BlockPos o) {
		int g = o.getY() - 1;
		BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
		for (int dx = -18; dx <= 18; dx++) {
			for (int dz = -28; dz <= 18; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (dz >= -17 && d <= 18.0) {
					clear(s, x, g + 1, g + 22, z);
					boolean powder = d > 5 && d < 14 && hash(o, dx >> 1, 0, dz >> 1, 31) < 0.08;
					s.set(x, g, z, powder ? Blocks.POWDER_SNOW.defaultBlockState() : snow);
					s.set(x, g - 1, z, d > 16 ? Blocks.STONE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
					s.set(x, g - 2, z, Blocks.STONE.defaultBlockState());
					foundation(s, x, g - 3, z, Blocks.STONE.defaultBlockState(), 40);
				} else if (dz < -17 && Math.abs(dx) <= 16) {
					// The slope the avalanche comes down: rises 1.25 blocks per block northwards.
					int h = (int) Math.min(13, (-dz - 17) * 1.25 + 1);
					for (int y = g - 2; y <= g + h; y++) s.set(x, y, z, y >= g + h - 1 ? snow : Blocks.STONE.defaultBlockState());
					foundation(s, x, g - 3, z, Blocks.STONE.defaultBlockState(), 40);
					clear(s, x, g + h + 1, g + 22, z);
					if (hash(o, dx, 1, dz, 32) < 0.5) s.set(x, g + h + 1, z, Blocks.SNOW.defaultBlockState());
				}
			}
		}
		// Campfire braziers on stone-brick pedestals.
		for (int[] c : CAMPFIRES) {
			s.set(o.getX() + c[0], g + 1, o.getZ() + c[2], Blocks.STONE_BRICKS.defaultBlockState());
			s.set(o.getX() + c[0], g + 2, o.getZ() + c[2], Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}
		// Ice outcrops (cover from the avalanche, which comes from the north).
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
		// The frozen heart on a blue-ice pedestal.
		s.set(o.getX(), g, o.getZ(), Blocks.BLUE_ICE.defaultBlockState());
		s.set(o.getX() + FROZEN_HEART.getX(), g + 1, o.getZ() + FROZEN_HEART.getZ(), OwbBlocks.FROZEN_HEART.defaultBlockState());
	}

	// =================================================================================================================
	// Wreck Ring (Vorlagh): three shipwrecks lashed into a deck ring around open water, in a deep ocean.
	// =================================================================================================================

	/** @param fillWater true for the dev/test build (adds an ocean basin under and around the deck). */
	public static void wreckRing(BlockSink s, BlockPos o, boolean fillWater) {
		int deck = o.getY() - 1;
		BlockState water = Blocks.WATER.defaultBlockState();
		for (int dx = -24; dx <= 24; dx++) {
			for (int dz = -24; dz <= 24; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				if (!s.writes(x, z)) continue;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 24.0) continue;
				clear(s, x, deck, deck + 14, z);
				if (fillWater) {
					for (int y = deck - 1; y >= deck - 14; y--) s.set(x, y, z, water);
					s.set(x, deck - 15, z, Blocks.SAND.defaultBlockState());
					s.set(x, deck - 16, z, Blocks.SANDSTONE.defaultBlockState());
				}
				double edge = 17.0 + hash(o, (int) Math.round(Math.atan2(dz, dx) * 6), 0, 0, 41) * 2.0;
				if (d >= 5.5 && d <= edge) {
					double n = hash(o, dx, 0, dz, 42);
					BlockState plank = n < 0.55 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : n < 0.85 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
					boolean beam = (dx == 0 || dz == 0 || dx == dz || dx == -dz);
					if (n > 0.975 && d > 7) continue; // a few gaps between planks
					s.set(x, deck, z, beam ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : plank);
					if (d > edge - 1.0 && hash(o, dx, 1, dz, 43) < 0.6) s.set(x, deck + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
					if (d < 6.5 && hash(o, dx, 2, dz, 44) < 0.35) s.set(x, deck + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
				}
			}
		}
		// Three hulls: raised gunwales, a broken mast with sail scraps, barrels and lanterns.
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
			// Gunwale along the outer edge of this hull.
			for (int k = -6; k <= 6; k++) {
				int gx = o.getX() + (int) Math.round(cx * 17 - cz * k), gz = o.getZ() + (int) Math.round(cz * 17 + cx * k);
				s.set(gx, deck + 1, gz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				if (hash(o, k, 7, i, 47) < 0.7) s.set(gx, deck + 2, gz, Blocks.DARK_OAK_FENCE.defaultBlockState());
			}
			s.set(hx + (int) Math.round(-cz * 2), deck + 1, hz + (int) Math.round(cx * 2), Blocks.BARREL.defaultBlockState());
			s.set(hx + (int) Math.round(cz * 2), deck + 1, hz + (int) Math.round(-cx * 2), Blocks.BARREL.defaultBlockState());
			s.set(hx, deck + mastH + 1, hz, Blocks.LANTERN.defaultBlockState());
		}
		// Lashing chains between the masts (decor) and lanterns on the inner rail.
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6;
			int lx = o.getX() + (int) Math.round(Math.cos(a) * 6.5), lz = o.getZ() + (int) Math.round(Math.sin(a) * 6.5);
			s.set(lx, deck + 1, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
			s.set(lx, deck + 2, lz, Blocks.LANTERN.defaultBlockState());
		}
		s.set(o.getX() + WRECK_CAPSTAN.getX(), deck + 1, o.getZ() + WRECK_CAPSTAN.getZ(), OwbBlocks.VORLAGH_CAPSTAN.defaultBlockState());
	}

	public static Block[] decorBlocks() {
		return new Block[] {Blocks.LILY_PAD, Blocks.CAMPFIRE};
	}
}
