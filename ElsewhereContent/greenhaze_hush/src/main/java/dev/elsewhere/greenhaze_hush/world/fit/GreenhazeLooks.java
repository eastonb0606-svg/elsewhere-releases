package dev.elsewhere.greenhaze_hush.world.fit;

import static dev.elsewhere.greenhaze_hush.world.fit.GhFit.*;

import dev.elsewhere.greenhaze.GreenhazeBlocks;
import dev.elsewhere.greenhaze.GreenhazeEntities;
import dev.elsewhere.greenhaze.GreenhazeMarket;
import dev.elsewhere.greenhaze.GreenhazeStructures;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Greenhaze looks (STRUCTURES.md 4 and 7; references: Moroccan/Andalusian glass-and-rib domes and Kew-style palm houses for the Hotbox,
 * Greek theatre cavea seating for the Bonfire Terrace, Banaue rice terraces for the farms, a souk street for the market, Southeast Asian
 * stilt huts for the sesh hut). Each builder writes one planned piece and only the chunk being generated.
 */
public final class GreenhazeLooks {
	private GreenhazeLooks() {}

	static void register() {
		BUILDERS.put("hotbox", GreenhazeLooks::hotbox);
		BUILDERS.put("hotbox_annex", GreenhazeLooks::annex);
		BUILDERS.put("hotbox_walk", GreenhazeLooks::walk);
		BUILDERS.put("bonfire", GreenhazeLooks::bonfire);
		BUILDERS.put("farms", GreenhazeLooks::farms);
		BUILDERS.put("market_street", GreenhazeLooks::street);
		BUILDERS.put("market_stall", GreenhazeLooks::stall);
		BUILDERS.put("hut", GreenhazeLooks::hut);
		BUILDERS.put("spring", GreenhazeLooks::spring);
		BUILDERS.put("rift", GreenhazeLooks::rift);
	}

	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	static BlockState glass(DyeColor c) {
		return Blocks.STAINED_GLASS.pick(c).defaultBlockState();
	}

	static BlockState wool(DyeColor c) {
		return Blocks.WOOL.pick(c).defaultBlockState();
	}

	static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	/** Angular distance (radians) from {@code a} to the nearest multiple of {@code step}. */
	static double ang(double a, double step) {
		double m = Math.floorMod((long) Math.floor(a / step * 1e6), (long) 1e6) / 1e6 * step;
		return Math.min(m, step - m);
	}

	// ============================================================================================ The Great Hotbox

	/** Main dome (the Percolax arena): R 15 ribbed glass dome on a battered mud-brick drum, tuff string course, cupola, 6 vents, arched door. */
	static void hotbox(Piece pc, Out o) {
		Frame f = pc.frame();
		int R = 15, y0 = f.oy();
		long sd = pc.seed;
		BlockState mudB = st(Blocks.MUD_BRICKS), packed = st(Blocks.PACKED_MUD), tuffB = st(Blocks.TUFF_BRICKS), chis = st(Blocks.CHISELED_TUFF_BRICKS),
			ptuff = st(Blocks.POLISHED_TUFF), moss = st(Blocks.MOSS_BLOCK), rib = pillar(Blocks.STRIPPED_BAMBOO_BLOCK, Direction.Axis.Y),
			lime = glass(DyeColor.LIME), green = glass(DyeColor.GREEN);
		// 1. floor, drum footing and batter, each column on its own foundation
		for (int lx = -R - 3; lx <= R + 3; lx++) for (int lz = -R - 3; lz <= R + 3; lz++) {
			double r = Math.sqrt(lx * lx + lz * lz);
			if (r > R + 2.0) continue;
			int wx = f.x(lx, lz), wz = f.z(lx, lz);
			if (!o.has(wx, wz)) continue;
			if (r <= R + 1.0) {
				BlockState fl;
				if (r > R - 0.5) fl = mudB;
				else if (r <= 2.5) fl = chis;
				else if (r > 5.5 && r < 8.5) fl = hash(sd, wx, 0, wz) < 0.2 ? st(Blocks.MOSSY_COBBLESTONE) : moss;
				else if (Math.abs(r - 3.2) < 0.6 || Math.abs(r - 11.5) < 0.5) fl = ptuff;
				else fl = (Math.floorMod(lx + lz, 2) == 0) ? mudB : packed;
				o.set(new BlockPos(wx, y0 - 1, wz), fl);
				underpin(o, wx, y0 - 2, wz, mudB, packed, 10);
			} else {
				// battered plinth: a mud-brick stair skirt leaning on the drum (STRUCTURES.md 7 rule 3)
				Direction in = f.dir(toward(-lx, -lz));
				underpin(o, wx, y0 - 1, wz, mudB, packed, 10);
				o.set(new BlockPos(wx, y0, wz), stair(Blocks.MUD_BRICK_STAIRS, in, false));
				soft(o, wx, y0 + 1, y0 + 4, wz);
			}
		}
		// 2. shell: drum (dy 0-3), tuff string course (dy 4), ribbed glass dome above; interior air only inside the room
		for (int lx = -R - 1; lx <= R + 1; lx++) for (int lz = -R - 1; lz <= R + 1; lz++) {
			int wx = f.x(lx, lz), wz = f.z(lx, lz);
			if (!o.has(wx, wz)) continue;
			double r2 = lx * lx + lz * lz, rr = Math.sqrt(r2);
			if (rr > R + 1) continue;
			double theta = Math.atan2(lz, lx);
			for (int dy = 0; dy <= R + 2; dy++) {
				double d = Math.sqrt(r2 + (dy * 1.15) * (dy * 1.15));
				BlockPos p = new BlockPos(wx, y0 + dy, wz);
				if (d <= R) {
					if (!o.get(p).isAir()) o.set(p, AIR);
				} else if (d <= R + 1) {
					BlockState b;
					if (dy <= 3) b = dy == 3 && Math.floorMod(Math.round(theta / (Math.PI / 12)), 2) == 0 ? tuffB : mudB;
					else if (dy == 4) b = tuffB;
					else if (rr < 2.6 || ang(theta, Math.PI / 6) * rr < 0.75) b = rib;
					else if (dy == 9 && ang(theta, Math.PI / 6) * rr < 1.9) b = rib; // a second ring of ribs: the "girdle"
					else b = hash(sd, wx, y0 + dy, wz) < 0.18 ? green : lime;
					o.set(p, b);
				}
			}
			// cornice: inverted stairs on the outside of the drum top
			if (rr > R + 0.6 && rr <= R + 1.6) o.set(new BlockPos(wx, y0 + 4, wz), stair(Blocks.TUFF_BRICK_STAIRS, f.dir(toward(-lx, -lz)), true));
		}
		// 3. the cupola over the oculus: bamboo posts, a mosaic cap and a shroomlight glow
		for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
			if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
			if (Math.abs(lx) == 1 && Math.abs(lz) == 1) f.set(o, lx, 14, lz, st(Blocks.BAMBOO_FENCE));
			f.set(o, lx, 15, lz, lx == 0 && lz == 0 ? st(Blocks.SHROOMLIGHT) : slab(Blocks.BAMBOO_MOSAIC_SLAB, false));
		}
		f.set(o, 0, 16, 0, st(Blocks.BAMBOO_FENCE));
		f.set(o, 0, 17, 0, st(Blocks.LANTERN));
		// 4. six smoke vents in the dome (Percolax opens them by their levers); the door is the local front (+z), vents skip it
		for (int i = 0; i < 6; i++) {
			double a = Math.PI / 2 + Math.PI / 6 + i * Math.PI / 3;
			double c = Math.cos(a), s = Math.sin(a);
			double rs = Math.sqrt((R + 0.5) * (R + 0.5) - (7 * 1.15) * (7 * 1.15));
			int vx = (int) Math.round(c * rs), vz = (int) Math.round(s * rs);
			for (int k = -1; k <= 1; k++) for (int dy = 6; dy <= 8; dy++) {
				int px = vx + (Math.abs(s) > 0.5 ? k : 0), pz = vz + (Math.abs(s) > 0.5 ? 0 : k);
				for (int t = -1; t <= 2; t++) if (t != 0) f.set(o, px + (int) Math.round(c * t), dy, pz + (int) Math.round(s * t), AIR);
				f.set(o, px, dy, pz, st(GreenhazeBlocks.HOTBOX_VENT));
			}
			for (int k = -2; k <= 2; k += 4) for (int dy = 5; dy <= 9; dy++) {
				int px = vx + (Math.abs(s) > 0.5 ? k : 0), pz = vz + (Math.abs(s) > 0.5 ? 0 : k);
				BlockState here = f.get(o, px, dy, pz);
				if (!here.isAir()) f.set(o, px, dy, pz, tuffB);
			}
			int lx = (int) Math.round(c * (rs - 2)), lz = (int) Math.round(s * (rs - 2));
			f.set(o, lx, 0, lz, st(GreenhazeBlocks.VENT_LEVER));
			f.set(o, lx, -1, lz, chis);
		}
		// 5. moss couches between the vents (seat + backrest), hanging lanterns and rib shroomlights
		for (int i = 1; i < 6; i++) {
			double a = Math.PI / 2 + i * Math.PI / 3;
			double c = Math.cos(a), s = Math.sin(a);
			Direction out = toward(c, s);
			for (int k = -1; k <= 1; k++) {
				int sx = (int) Math.round(c * 9.5 - s * k), sz = (int) Math.round(s * 9.5 + c * k);
				int bx = (int) Math.round(c * 10.6 - s * k), bz = (int) Math.round(s * 10.6 + c * k);
				f.set(o, sx, 0, sz, st(Blocks.MOSS_BLOCK));
				f.set(o, sx, 1, sz, st(Blocks.MOSS_CARPET));
				f.set(o, bx, 0, bz, stair(Blocks.MUD_BRICK_STAIRS, out, false));
			}
		}
		for (int i = 0; i < 6; i++) {
			double a = Math.PI / 2 + i * Math.PI / 3;
			int hx = (int) Math.round(Math.cos(a) * 8), hz = (int) Math.round(Math.sin(a) * 8);
			f.set(o, hx, 11, hz, st(Blocks.IRON_CHAIN));
			f.set(o, hx, 10, hz, st(Blocks.IRON_CHAIN));
			f.set(o, hx, 9, hz, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
			double b = i * Math.PI / 3 + Math.PI / 6;
			f.set(o, (int) Math.round(Math.cos(b) * 11.1), 10, (int) Math.round(Math.sin(b) * 11.1), st(Blocks.SHROOMLIGHT));
		}
		// 6. the arched south door with a bamboo porch
		for (int lz = R - 1; lz <= R + 2; lz++) {
			for (int lx = -1; lx <= 1; lx++) for (int dy = 0; dy <= 2; dy++) f.set(o, lx, dy, lz, AIR);
			f.set(o, 0, 3, lz, AIR);
			f.set(o, -1, 3, lz, stair(Blocks.MUD_BRICK_STAIRS, Direction.WEST, true));
			f.set(o, 1, 3, lz, stair(Blocks.MUD_BRICK_STAIRS, Direction.EAST, true));
		}
		for (int lz = R + 1; lz <= R + 3; lz++) {
			for (int lx = -2; lx <= 2; lx++) {
				BlockPos fp = f.at(lx, -1, lz);
				o.set(fp, Math.abs(lx) == 2 ? tuffB : mudB);
				underpin(o, fp.getX(), y0 - 2, fp.getZ(), mudB, packed, 10);
				if (Math.abs(lx) < 2) for (int dy = 0; dy <= 3; dy++) f.set(o, lx, dy, lz, AIR);
				f.set(o, lx, 4, lz, slab(Blocks.BAMBOO_MOSAIC_SLAB, false));
			}
			for (int dy = 0; dy <= 3; dy++) {
				f.set(o, -2, dy, lz, lz == R + 2 ? st(Blocks.BAMBOO_FENCE) : pillar(Blocks.STRIPPED_BAMBOO_BLOCK, Direction.Axis.Y));
				f.set(o, 2, dy, lz, lz == R + 2 ? st(Blocks.BAMBOO_FENCE) : pillar(Blocks.STRIPPED_BAMBOO_BLOCK, Direction.Axis.Y));
			}
		}
		f.set(o, 0, 3, R + 3, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		// the doorman's stocked chest at the porch steps (outside the Percolax seal, where 2.x kept it)
		f.set(o, 1, 0, R + 3, st(Blocks.CHEST).setValue(ChestBlock.FACING, Direction.WEST));
		o.loot(f.at(1, 0, R + 3), GreenhazeStructures.HOTBOX_LOOT, sd ^ 0x5EEDL);
		// 7. the altar on its chiseled dais
		f.set(o, 0, 0, 0, st(GreenhazeBlocks.HOTBOX_ALTAR));
	}

	/** A small ribbed glass annex dome on its own floor: role 0 = the doorman's store, 1 = the chill-out lounge. */
	static void annex(Piece pc, Out o) {
		Frame f = pc.frame();
		int ra = pc.p(0), role = pc.p(1), y0 = f.oy();
		long sd = pc.seed;
		BlockState mudB = st(Blocks.MUD_BRICKS), packed = st(Blocks.PACKED_MUD), tuffB = st(Blocks.TUFF_BRICKS), rib = pillar(Blocks.STRIPPED_BAMBOO_BLOCK, Direction.Axis.Y);
		for (int lx = -ra - 3; lx <= ra + 3; lx++) for (int lz = -ra - 3; lz <= ra + 3; lz++) {
			double r = Math.sqrt(lx * lx + lz * lz);
			if (r > ra + 2.0) continue;
			int wx = f.x(lx, lz), wz = f.z(lx, lz);
			if (!o.has(wx, wz)) continue;
			double theta = Math.atan2(lz, lx);
			if (r <= ra + 1.0) {
				o.set(new BlockPos(wx, y0 - 1, wz), r > ra - 0.5 ? mudB : (r < 2.5 ? st(Blocks.MOSS_BLOCK) : (Math.floorMod(lx + lz, 2) == 0 ? mudB : packed)));
				underpin(o, wx, y0 - 2, wz, mudB, packed, 10);
				for (int dy = 0; dy <= ra + 2; dy++) {
					double d = Math.sqrt(r * r + dy * dy);
					BlockPos p = new BlockPos(wx, y0 + dy, wz);
					if (d <= ra) { if (!o.get(p).isAir()) o.set(p, AIR); }
					else if (d <= ra + 1) {
						BlockState b = dy <= 1 ? mudB : dy == 2 ? tuffB : (r < 1.6 || ang(theta, Math.PI / 4) * r < 0.75) ? rib
							: (hash(sd, wx, y0 + dy, wz) < 0.2 ? glass(DyeColor.GREEN) : glass(DyeColor.LIME));
						o.set(p, b);
					}
				}
			} else {
				underpin(o, wx, y0 - 1, wz, mudB, packed, 10);
				o.set(new BlockPos(wx, y0, wz), stair(Blocks.MUD_BRICK_STAIRS, f.dir(toward(-lx, -lz)), false));
				soft(o, wx, y0 + 1, y0 + 3, wz);
			}
		}
		// apex light and doors: back door (local -z toward the main dome), front door (+z) when another annex follows
		f.set(o, 0, ra, 0, st(Blocks.SHROOMLIGHT));
		for (int side = -1; side <= 1; side += 2) {
			if (side == 1 && pc.p(2) == 0) continue;
			for (int t = ra - 1; t <= ra + 1; t++) {
				int lz = side * t;
				for (int lx = -1; lx <= 1; lx++) for (int dy = 0; dy <= 1; dy++) f.set(o, lx, dy, lz, AIR);
				f.set(o, 0, 2, lz, AIR);
				f.set(o, -1, 2, lz, stair(Blocks.MUD_BRICK_STAIRS, Direction.WEST, true));
				f.set(o, 1, 2, lz, stair(Blocks.MUD_BRICK_STAIRS, Direction.EAST, true));
			}
		}
		if (role == 0) {
			// the doorman's store: barrels of supplies, lantern on a hay bale, a moss bench (the stocked chest stays by the main door, as in 2.x)
			f.set(o, 2, 0, -1, st(Blocks.BARREL));
			f.set(o, 2, 0, 1, st(Blocks.BARREL));
			f.set(o, -2, 0, -1, st(Blocks.HAY_BLOCK));
			f.set(o, -2, 1, -1, st(Blocks.LANTERN));
			f.set(o, -2, 0, 1, st(Blocks.MOSS_BLOCK));
			f.set(o, -2, 1, 1, st(Blocks.MOSS_CARPET));
		} else {
			// the lounge: a ring of moss seats round a cauldron "bong", ferns in pots
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3 + Math.PI / 6;
				int sx = (int) Math.round(Math.cos(a) * (ra - 1.5)), sz = (int) Math.round(Math.sin(a) * (ra - 1.5));
				f.set(o, sx, 0, sz, i % 2 == 0 ? st(Blocks.MOSS_BLOCK) : st(Blocks.POTTED_FERN));
				if (i % 2 == 0) f.set(o, sx, 1, sz, st(Blocks.MOSS_CARPET));
			}
			f.set(o, 0, 0, 0, st(Blocks.CAULDRON));
			f.set(o, 0, ra - 1, 0, st(Blocks.IRON_CHAIN));
			f.set(o, 0, ra - 2, 0, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		}
	}

	/** Covered stair walk between domes: 3 wide, steps of at most 1 per block, bamboo pergola posts and a mosaic roof that follow the steps. */
	static void walk(Piece pc, Out o) {
		Frame f = pc.frame();
		int n = pc.p(0), base = f.oy();
		BlockState mudB = st(Blocks.MUD_BRICKS), packed = st(Blocks.PACKED_MUD), tuffB = st(Blocks.TUFF_BRICKS);
		for (int i = 0; i < n; i++) {
			int w = pc.p(1 + i), dy = w - base;
			int prev = i > 0 ? pc.p(i) - base : dy, next = i < n - 1 ? pc.p(2 + i) - base : dy;
			for (int lx = -2; lx <= 2; lx++) {
				BlockPos fp = f.at(lx, dy - 1, i);
				o.set(fp, Math.abs(lx) == 2 ? tuffB : (lx == 0 ? st(Blocks.PACKED_MUD) : mudB));
				underpin(o, fp.getX(), fp.getY() - 1, fp.getZ(), mudB, packed, 12);
				for (int h = 0; h <= 3; h++) {
					BlockPos q = f.at(lx, dy + h, i);
					if (!o.get(q).isAir()) o.set(q, AIR);
				}
				if (Math.abs(lx) < 2) {
					if (prev > dy) f.set(o, lx, dy, i, stair(Blocks.MUD_BRICK_STAIRS, Direction.NORTH, false));
					else if (next > dy) f.set(o, lx, dy, i, stair(Blocks.MUD_BRICK_STAIRS, Direction.SOUTH, false));
				}
			}
			boolean post = i % 3 == 0 || i == n - 1;
			for (int side = -2; side <= 2; side += 4) {
				if (post) {
					for (int h = 0; h <= 2; h++) f.set(o, side, dy + h, i, pillar(Blocks.STRIPPED_BAMBOO_BLOCK, Direction.Axis.Y));
				} else {
					f.set(o, side, dy, i, st(Blocks.BAMBOO_FENCE));
				}
			}
			for (int lx = -2; lx <= 2; lx++) f.set(o, lx, dy + 3, i, Math.abs(lx) == 2 ? slab(Blocks.BAMBOO_MOSAIC_SLAB, false) : st(Blocks.BAMBOO_MOSAIC));
			if (i % 4 == 2) f.set(o, 0, dy + 2, i, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		}
	}

	// ============================================================================================ The Bonfire Terrace

	/** Ring bands: inner floor to 8, seat rings 1..4 to 18 (the Grumblegut arena edge), promenade to 20. */
	static int ring(double r) {
		if (r <= 8) return 0;
		if (r <= 18) return 1 + (int) Math.min(3, Math.floor((r - 8) / 2.5));
		return 5;
	}

	static int sector(double theta) {
		return Math.floorMod((int) Math.floor((theta + Math.PI) / (2 * Math.PI) * 16), 16);
	}

	/** Amphitheatre (Greek cavea): stepped seat rings on their own contours round a bowl-bottom bonfire; aisles at the cardinals. */
	static void bonfire(Piece pc, Out o) {
		Frame f = pc.frame();
		int y0 = f.oy();
		long sd = pc.seed;
		BlockState mudB = st(Blocks.MUD_BRICKS), packed = st(Blocks.PACKED_MUD), mud = st(Blocks.MUD);
		for (int lx = -21; lx <= 21; lx++) for (int lz = -21; lz <= 21; lz++) {
			double r = Math.sqrt(lx * lx + lz * lz);
			if (r > 21) continue;
			int wx = f.x(lx, lz), wz = f.z(lx, lz);
			if (!o.has(wx, wz)) continue;
			double theta = Math.atan2(lz, lx);
			int sec = sector(theta), k = ring(r);
			int lvl = level(pc, sec, Math.min(k, 4));
			boolean aisle = r > 3.3 && ang(theta, Math.PI / 2) * r < 1.6;
			int wy = y0 + lvl;
			if (r > 20) {
				// rim retaining wall where the hillside stands above the promenade (no cut slope left raw)
				int g = topSolid(o, wx, wz, wy + 7, 8);
				for (int y = wy - 1; y <= Math.min(wy + 5, g); y++) o.set(new BlockPos(wx, y, wz), hash(sd, wx, y, wz) < 0.25 ? st(Blocks.MOSSY_COBBLESTONE) : mudB);
				if (g >= wy - 1) o.set(new BlockPos(wx, Math.min(wy + 5, g) + 1, wz), stair(Blocks.MUD_BRICK_STAIRS, f.dir(toward(lx, lz)), false));
				continue;
			}
			// surface block
			BlockState surf;
			if (k == 0) surf = r <= 3.3 ? mudB : (aisle || Math.floorMod(sec, 2) == 0 ? packed : mudB);
			else if (aisle) surf = st(Blocks.PACKED_MUD);
			else surf = hash(sd, wx, 1, wz) < 0.3 ? st(Blocks.MOSS_BLOCK) : st(Blocks.GRASS_BLOCK);
			o.set(new BlockPos(wx, wy - 1, wz), surf);
			underpin(o, wx, wy - 2, wz, mudB, packed, 10);
			// the cut: clear the hillside above the terrace (only real blocks, never a box), at most 8 up
			int g = topSolid(o, wx, wz, wy + 8, 9);
			if (g >= wy) clear(o, wx, wy, g, wz);
			soft(o, wx, wy, wy + 4, wz);
			// seat riser: the first block of a raised ring is a mud-brick stair facing out (sit facing the fire); aisles get plain steps
			if (k >= 1 && k <= 4) {
				int inner = level(pc, sec, k - 1);
				double rIn = 8 + 2.5 * (k - 1);
				if (lvl > inner && r - rIn <= 1.0) o.set(new BlockPos(wx, wy - 1, wz), stair(Blocks.MUD_BRICK_STAIRS, f.dir(toward(lx, lz)), false));
			}
		}
		// the bonfire: a mud-brick ring wall, criss-crossed jungle-log pyre round a magma heart, one flame on top (one lit campfire only)
		for (int lx = -3; lx <= 3; lx++) for (int lz = -3; lz <= 3; lz++) {
			double r = Math.sqrt(lx * lx + lz * lz);
			if (r > 3.3) continue;
			if (r > 2.3) f.set(o, lx, 0, lz, st(Blocks.MUD_BRICK_WALL));
			else f.set(o, lx, -1, lz, st(Blocks.MAGMA_BLOCK));
		}
		for (int t = -1; t <= 1; t++) {
			f.set(o, t, 0, -1, pillar(Blocks.STRIPPED_JUNGLE_LOG, Direction.Axis.X));
			f.set(o, t, 0, 1, pillar(Blocks.STRIPPED_JUNGLE_LOG, Direction.Axis.X));
			f.set(o, -1, 1, t, pillar(Blocks.JUNGLE_LOG, Direction.Axis.Z));
			f.set(o, 1, 1, t, pillar(Blocks.JUNGLE_LOG, Direction.Axis.Z));
		}
		f.set(o, 0, 0, 0, st(Blocks.MAGMA_BLOCK));
		f.set(o, 0, 1, 0, st(Blocks.MAGMA_BLOCK));
		f.set(o, 0, 2, 0, st(Blocks.CAMPFIRE).setValue(CampfireBlock.SIGNAL_FIRE, true));
		f.set(o, 0, 0, 5, st(GreenhazeBlocks.SNACK_ALTAR));
		// moss couches on the first ring (6), facing the fire
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + Math.PI / 6;
			double c = Math.cos(a), s = Math.sin(a);
			for (int t = -1; t <= 1; t++) {
				int sx = (int) Math.round(c * 9.6 - s * t), sz = (int) Math.round(s * 9.6 + c * t);
				int lv = level(pc, sector(Math.atan2(sz, sx)), 1);
				f.set(o, sx, lv, sz, st(Blocks.MOSS_CARPET));
			}
		}
		// four fruit trees on ring 3 (Grumblegut's snack sniff targets), lantern posts on the promenade, the stocked chest
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			int tx = (int) Math.round(Math.cos(a) * 14.5), tz = (int) Math.round(Math.sin(a) * 14.5);
			int lv = level(pc, sector(Math.atan2(tz, tx)), 3);
			BlockPos b = f.at(tx, lv, tz);
			GreenhazeStructures.fruitTree(o, b.getX(), b.getY(), b.getZ());
		}
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6 + Math.PI / 12;
			int px = (int) Math.round(Math.cos(a) * 19.2), pz = (int) Math.round(Math.sin(a) * 19.2);
			int lv = level(pc, sector(Math.atan2(pz, px)), 4);
			f.set(o, px, lv, pz, st(Blocks.MUD_BRICK_WALL));
			f.set(o, px, lv + 1, pz, st(Blocks.LANTERN));
		}
		int cx = 16, cz = -4, clv = level(pc, sector(Math.atan2(cz, cx)), 4);
		f.set(o, cx, clv, cz, st(Blocks.CHEST).setValue(ChestBlock.FACING, Direction.WEST));
		o.loot(f.at(cx, clv, cz), GreenhazeStructures.TERRACE_LOOT, sd ^ 0xB0BL);
	}

	/** Planned walk level (relative to the floor) of seat ring k in sector s; ring 0 is the floor. */
	static int level(Piece pc, int sec, int k) {
		if (k <= 0) return 0;
		return pc.p((sec & 15) * 4 + (Math.min(k, 4) - 1));
	}

	// ============================================================================================ Fern Terrace Farms

	/** Bilinear ground of a stored lattice: p = [x0, z0, step, n, g...]. */
	static double lat(Piece pc, int off, double x, double z) {
		int x0 = pc.p(off), z0 = pc.p(off + 1), step = Math.max(1, pc.p(off + 2)), n = pc.p(off + 3);
		double fx = Math.max(0, Math.min(n - 1.0001, (x - x0) / (double) step)), fz = Math.max(0, Math.min(n - 1.0001, (z - z0) / (double) step));
		int i = (int) fx, j = (int) fz;
		double tx = fx - i, tz = fz - j;
		double a = pc.p(off + 4 + i * n + j), b = pc.p(off + 4 + (i + 1) * n + j), c = pc.p(off + 4 + i * n + j + 1), d = pc.p(off + 4 + (i + 1) * n + j + 1);
		return a * (1 - tx) * (1 - tz) + b * tx * (1 - tz) + c * (1 - tx) * tz + d * tx * tz;
	}

	/** Terrace walk level of a world column (rice terraces: ground quantised along its isolines, plus a gentle tilt on flat sites). */
	static int terrace(Piece pc, int x, int z) {
		int y0 = pc.o.getY();
		int n = pc.p(3), off = 4 + n * n;
		double tilt = pc.p(off) / 100.0, ux = pc.p(off + 1), uz = pc.p(off + 2);
		double h = lat(pc, 0, x, z) + 1 + tilt * ((x - pc.o.getX()) * ux + (z - pc.o.getZ()) * uz) / 5.0;
		return Math.max(y0 - 4, Math.min(y0 + 4, (int) Math.round(h)));
	}

	static boolean inFarm(Piece pc, int x, int z) {
		double dx = x - pc.o.getX(), dz = z - pc.o.getZ();
		double r = Math.sqrt(dx * dx + dz * dz);
		return r <= 13.0 + 2.0 * hash(pc.seed, (int) Math.round(Math.atan2(dz, dx) * 4), 0, 0);
	}

	static void farms(Piece pc, Out o) {
		long sd = pc.seed;
		int ox = pc.o.getX(), oz = pc.o.getZ();
		int n = pc.p(3), off = 4 + n * n;
		int ux = pc.p(off + 1), uz = pc.p(off + 2);
		BlockState mudB = st(Blocks.MUD_BRICKS), packed = st(Blocks.PACKED_MUD);
		CropBlock[] crops = {(CropBlock) Blocks.WHEAT, (CropBlock) Blocks.CARROTS, (CropBlock) Blocks.POTATOES};
		for (int x = ox - 16; x <= ox + 16; x++) for (int z = oz - 16; z <= oz + 16; z++) {
			if (!o.has(x, z) || !inFarm(pc, x, z)) continue;
			int t = terrace(pc, x, z);
			int lo = t;
			boolean edge = false, flat8 = true;
			for (int ddx = -1; ddx <= 1; ddx++) for (int ddz = -1; ddz <= 1; ddz++) {
				if (ddx == 0 && ddz == 0) continue;
				boolean in = inFarm(pc, x + ddx, z + ddz);
				int tn = in ? terrace(pc, x + ddx, z + ddz) : Integer.MIN_VALUE;
				if (!in || tn != t) flat8 = false;
				if ((ddx == 0 || ddz == 0) && (!in || tn < t)) { edge = true; if (in) lo = Math.min(lo, tn); }
			}
			// the stair path climbs straight up the slope
			int along = (x - ox) * ux + (z - oz) * uz, across = (x - ox) * -uz + (z - oz) * ux;
			boolean path = Math.abs(across) <= 1;
			clear(o, x, t, t + 4, z);
			if (path) {
				o.set(new BlockPos(x, t - 1, z), Math.abs(across) == 1 ? mudB : st(Blocks.COARSE_DIRT));
				underpin(o, x, t - 2, z, mudB, packed, 8);
				int up = terrace(pc, x + ux, z + uz);
				if (up > t && inFarm(pc, x + ux, z + uz)) o.set(new BlockPos(x, t, z), stair(Blocks.MUD_BRICK_STAIRS, toward(ux, uz), false));
				continue;
			}
			if (edge) {
				// the bund: a mud-brick retaining wall down to the lower terrace (riser visible, mossy in places)
				o.set(new BlockPos(x, t - 1, z), hash(sd, x, t, z) < 0.15 ? st(Blocks.MOSSY_COBBLESTONE) : mudB);
				for (int y = t - 2; y >= lo - 1; y--) o.set(new BlockPos(x, y, z), hash(sd, x, y, z) < 0.12 ? st(Blocks.MOSSY_COBBLESTONE) : mudB);
				underpin(o, x, Math.min(t - 2, lo - 2), z, mudB, packed, 8);
				if (Math.floorMod(along * 7 + across * 3, 23) == 0) { o.set(new BlockPos(x, t, z), st(Blocks.MUD_BRICK_WALL)); o.set(new BlockPos(x, t + 1, z), st(Blocks.LANTERN)); }
				continue;
			}
			underpin(o, x, t - 2, z, packed, packed, 8);
			if (flat8 && Math.floorMod(along * 5 + across * 11 + (int) (sd & 7), 9) == 0) {
				o.set(new BlockPos(x, t - 1, z), st(Blocks.WATER));
				o.set(new BlockPos(x, t - 2, z), mudB);
				continue;
			}
			double hz = hash(sd, x, 7, z);
			int tier = Math.floorMod(t - pc.o.getY() + 4, 3);
			if (hz < 0.06) {
				o.set(new BlockPos(x, t - 1, z), st(Blocks.GRASS_BLOCK));
				o.set(new BlockPos(x, t, z), st(GreenhazeBlocks.MUNCHFRUIT_BUSH).setValue(SweetBerryBushBlock.AGE, 3));
			} else if (hz < 0.16) {
				o.set(new BlockPos(x, t - 1, z), st(Blocks.GRASS_BLOCK));
				o.set(new BlockPos(x, t, z), st(Blocks.LARGE_FERN).setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
				o.set(new BlockPos(x, t + 1, z), st(Blocks.LARGE_FERN).setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
			} else {
				o.set(new BlockPos(x, t - 1, z), st(Blocks.FARMLAND).setValue(FarmlandBlock.MOISTURE, 7));
				o.set(new BlockPos(x, t, z), crops[tier].getStateForAge(crops[tier].getMaxAge()));
			}
		}
		// the field shed on the top of the slope: kushwood posts, a lean-to roof, scarecrow, composter and the tool chest
		int sx = ox + ux * 10 + (-uz) * -5, sz = oz + uz * 10 + ux * -5;
		int st0 = Integer.MIN_VALUE;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) st0 = Math.max(st0, terrace(pc, sx + dx, sz + dz));
		Direction back = toward(ux, uz);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			int x = sx + dx, z = sz + dz;
			o.set(new BlockPos(x, st0 - 1, z), st(GreenhazeBlocks.KUSHWOOD_PLANKS));
			underpin(o, x, st0 - 2, z, mudB, packed, 8);
			clear(o, x, st0, st0 + 3, z);
			boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
			int behind = dx * ux + dz * uz;
			if (corner) for (int h = 0; h <= (behind > 0 ? 2 : 1); h++) o.set(new BlockPos(x, st0 + h, z), pillar(GreenhazeBlocks.KUSHWOOD_LOG, Direction.Axis.Y));
			o.set(new BlockPos(x, st0 + (behind > 0 ? 3 : behind == 0 ? 3 : 2), z), behind == 0 ? slab(GreenhazeBlocks.KUSHWOOD_SLAB, false) : stair(GreenhazeBlocks.KUSHWOOD_STAIRS, back, false));
		}
		o.set(new BlockPos(sx, st0, sz), st(Blocks.CHEST).setValue(ChestBlock.FACING, back.getOpposite()));
		o.loot(new BlockPos(sx, st0, sz), GreenhazeMarket.FARM_LOOT, sd ^ 0xFA4L);
		BlockPos comp = new BlockPos(sx + (-uz), st0, sz + ux);
		o.set(comp, st(Blocks.COMPOSTER));
		int cx = ox + ux * 6 + (-uz) * 6, cz = oz + uz * 6 + ux * 6, ct = terrace(pc, cx, cz);
		if (inFarm(pc, cx, cz)) {
			o.set(new BlockPos(cx, ct, cz), st(Blocks.SPRUCE_FENCE));
			o.set(new BlockPos(cx, ct + 1, cz), st(Blocks.HAY_BLOCK));
			o.set(new BlockPos(cx, ct + 2, cz), st(Blocks.CARVED_PUMPKIN).setValue(net.minecraft.world.level.block.CarvedPumpkinBlock.FACING, toward(-ux, -uz)));
			o.set(new BlockPos(cx + uz, ct + 1, cz - ux), st(Blocks.HAY_BLOCK));
			o.set(new BlockPos(cx - uz, ct + 1, cz + ux), st(Blocks.HAY_BLOCK));
		}
	}

	// ============================================================================================ Munchie Market

	/** The souk street: 5 wide (path between mud-brick curbs) stepping with the ground, lamp posts, a fire-pit bulge, fern verges. */
	static void street(Piece pc, Out o) {
		Frame f = pc.frame();
		int n = pc.p(0), half = n / 2, base = f.oy();
		long sd = pc.seed;
		BlockState mudB = st(Blocks.MUD_BRICKS), packed = st(Blocks.PACKED_MUD);
		for (int i = 0; i < n; i++) {
			int s = i - half, w = pc.p(1 + i), dy = w - base;
			int prev = i > 0 ? pc.p(i) : w, next = i < n - 1 ? pc.p(2 + i) : w;
			int wide = (s >= -6 && s <= -4) ? 3 : 2;
			for (int t = -wide - 1; t <= wide + 1; t++) {
				BlockPos fp = f.at(t, dy - 1, s);
				if (!o.has(fp.getX(), fp.getZ())) continue;
				boolean verge = Math.abs(t) == wide + 1;
				BlockState surf = verge ? st(Blocks.GRASS_BLOCK) : Math.abs(t) == wide ? mudB
					: (hash(sd, fp.getX(), 0, fp.getZ()) < 0.45 ? st(Blocks.DIRT_PATH) : hash(sd, fp.getX(), 1, fp.getZ()) < 0.5 ? st(Blocks.GRAVEL) : packed);
				o.set(fp, surf);
				underpin(o, fp.getX(), fp.getY() - 1, fp.getZ(), mudB, packed, 8);
				int g = topSolid(o, fp.getX(), fp.getZ(), w + 5, 6);
				if (g >= w) clear(o, fp.getX(), w, g, fp.getZ());
				soft(o, fp.getX(), w, w + 4, fp.getZ());
				if (!verge && Math.abs(t) < wide) {
					if (prev > w) f.set(o, t, dy, s, stair(Blocks.MUD_BRICK_STAIRS, Direction.NORTH, false));
					else if (next > w) f.set(o, t, dy, s, stair(Blocks.MUD_BRICK_STAIRS, Direction.SOUTH, false));
				}
				if (verge && hash(sd, fp.getX(), 2, fp.getZ()) < 0.35) f.set(o, t, dy, s, st(Blocks.FERN));
			}
			if (Math.floorMod(s, 7) == 0 && s != 0) for (int side = -1; side <= 1; side += 2) {
				f.set(o, side * 2, dy, s, st(Blocks.BAMBOO_FENCE));
				f.set(o, side * 2, dy + 1, s, st(Blocks.BAMBOO_FENCE));
				f.set(o, side * 2, dy + 2, s, st(Blocks.LANTERN));
			}
		}
		// the fire pit in the street bulge (unlit: light comes from the lanterns) with hay-bale benches
		int pi = half - 5, pdy = pc.p(1 + pi) - base;
		f.set(o, 0, pdy - 1, -5, st(Blocks.STONE_BRICKS));
		f.set(o, 0, pdy, -5, st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
		for (int t = -1; t <= 1; t += 2) f.set(o, t * 2, pdy, -5, st(Blocks.HAY_BLOCK));
	}

	static final DyeColor[][] STRIPES = {{DyeColor.LIME, DyeColor.WHITE}, {DyeColor.ORANGE, DyeColor.WHITE}, {DyeColor.PINK, DyeColor.WHITE},
		{DyeColor.YELLOW, DyeColor.WHITE}, {DyeColor.PURPLE, DyeColor.LIME}, {DyeColor.GREEN, DyeColor.YELLOW}};

	/** A stall on its own pad facing the street: bamboo posts, counter, a striped awning sloping to the street. Role 1 = the Budtender's booth, 2 = with chest. */
	static void stall(Piece pc, Out o) {
		Frame f = pc.frame();
		int role = pc.p(0), col = Math.floorMod(pc.p(1), STRIPES.length), y0 = f.oy();
		long sd = pc.seed;
		int hw = role == 1 ? 3 : 2;
		BlockState mudB = st(Blocks.MUD_BRICKS), packed = st(Blocks.PACKED_MUD);
		// local +z faces the street: the counter is at lz = +2, the back at lz = -2
		for (int lx = -hw; lx <= hw; lx++) for (int lz = -2; lz <= 2; lz++) {
			BlockPos fp = f.at(lx, -1, lz);
			if (!o.has(fp.getX(), fp.getZ())) continue;
			o.set(fp, lz == 2 ? mudB : st(Blocks.SPRUCE_PLANKS));
			underpin(o, fp.getX(), y0 - 2, fp.getZ(), mudB, packed, 6);
			clear(o, fp.getX(), y0, y0 + 2, fp.getZ());
			soft(o, fp.getX(), y0 + 3, y0 + 6, fp.getZ());
		}
		// a step down (or up) to the street when the pad is off the street level
		int sw = pc.p(2), d = sw - y0;
		if (d != 0) for (int lx = -1; lx <= 1; lx++) {
			if (d < 0) f.set(o, lx, -1, 3, stair(Blocks.MUD_BRICK_STAIRS, Direction.NORTH, false));
			else f.set(o, lx, 0, 3, stair(Blocks.MUD_BRICK_STAIRS, Direction.SOUTH, false));
		}
		// posts: front ones 3 high, back ones 4 high (the awning slopes to the street)
		for (int lx = -hw; lx <= hw; lx += 2 * hw) {
			for (int h = 0; h <= 2; h++) f.set(o, lx, h, 2, st(Blocks.BAMBOO_FENCE));
			for (int h = 0; h <= 3; h++) f.set(o, lx, h, -2, pillar(Blocks.STRIPPED_BAMBOO_BLOCK, Direction.Axis.Y));
		}
		DyeColor[] c = STRIPES[col];
		for (int lx = -hw; lx <= hw; lx++) {
			BlockState stripe = wool(c[Math.floorMod(lx, 2)]);
			f.set(o, lx, 4, -2, stripe);
			f.set(o, lx, 4, -1, stripe);
			f.set(o, lx, 3, 0, stripe);
			f.set(o, lx, 3, 1, stripe);
			f.set(o, lx, 3, 2, stripe);
		}
		// counter (upside-down spruce stairs) with goods, shelves behind
		for (int lx = -hw + 1; lx <= hw - 1; lx++) f.set(o, lx, 0, 2, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, true));
		BlockState[] goods = {st(Blocks.CAKE), st(Blocks.POTTED_FERN), st(Blocks.MELON), st(Blocks.PUMPKIN), st(Blocks.HAY_BLOCK)};
		f.set(o, -1, 1, 2, goods[Math.floorMod((int) sd, goods.length)]);
		f.set(o, 1, 1, 2, goods[Math.floorMod((int) (sd >> 8), goods.length)]);
		f.set(o, -hw + 1, 0, -1, role == 2 ? st(Blocks.CHEST).setValue(ChestBlock.FACING, Direction.SOUTH) : st(Blocks.BARREL));
		if (role == 2) o.loot(f.at(-hw + 1, 0, -1), GreenhazeMarket.MARKET_LOOT, sd ^ 0x3A2L);
		f.set(o, hw - 1, 0, -1, Math.floorMod(sd, 2) == 0 ? st(Blocks.CAULDRON) : st(Blocks.COMPOSTER));
		f.set(o, 0, 0, -2, st(Blocks.HAY_BLOCK));
		f.set(o, 0, 2, 0, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		if (role == 1) {
			// the Budtender's booth: a kushwood gable over the awning and the Budtender himself
			for (int lx = -hw - 1; lx <= hw + 1; lx++) {
				f.set(o, lx, 5, -1, stair(GreenhazeBlocks.KUSHWOOD_STAIRS, Direction.NORTH, false));
				f.set(o, lx, 5, -2, stair(GreenhazeBlocks.KUSHWOOD_STAIRS, Direction.SOUTH, false));
				f.set(o, lx, 4, 0, stair(GreenhazeBlocks.KUSHWOOD_STAIRS, Direction.NORTH, false));
				f.set(o, lx, 4, -3, stair(GreenhazeBlocks.KUSHWOOD_STAIRS, Direction.SOUTH, false));
			}
			o.spawn(GreenhazeEntities.BUDTENDER, f.at(0, 0, 0));
		}
	}

	// ============================================================================================ Sesh Hut

	/** Round stilt hut: 9 kushwood posts each down to its own ground (stilts by design), plank deck with a railing, conical hay roof. */
	static void hut(Piece pc, Out o) {
		Frame f = pc.frame();
		int d = f.oy();
		long sd = pc.seed;
		BlockState log = pillar(GreenhazeBlocks.KUSHWOOD_LOG, Direction.Axis.Y), planks = st(GreenhazeBlocks.KUSHWOOD_PLANKS), bamboo = st(Blocks.BAMBOO_PLANKS);
		int[][] posts = {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
		for (int[] q : posts) {
			BlockPos b = f.at(q[0], -1, q[1]);
			for (int dy = 2; dy <= 16; dy++) {
				BlockPos p = b.below(dy - 1);
				if (!o.has(p.getX(), p.getZ())) break;
				if (dy > 2 && ground(o.get(p))) break;
				o.set(p, log);
			}
		}
		// knee braces under the deck edge
		for (int i = 0; i < 4; i++) {
			Direction dir = Direction.from2DDataValue(i);
			f.set(o, dir.getStepX() * 4, -2, dir.getStepZ() * 4, stair(GreenhazeBlocks.KUSHWOOD_STAIRS, dir.getOpposite(), true));
		}
		for (int lx = -5; lx <= 5; lx++) for (int lz = -5; lz <= 5; lz++) {
			double r = Math.sqrt(lx * lx + lz * lz);
			if (r > 5.3) continue;
			if (r <= 4.5) f.set(o, lx, -1, lz, Math.floorMod(lx, 2) == 0 ? planks : st(GreenhazeBlocks.STRIPPED_KUSHWOOD_LOG));
			else if (!(lx == 0 && lz == 5)) f.set(o, lx, 0, lz, st(GreenhazeBlocks.KUSHWOOD_FENCE));
			for (int h = 1; h <= 8; h++) {
				BlockPos q = f.at(lx, h, lz);
				if (!o.get(q).isAir() && !o.get(q).is(GreenhazeBlocks.KUSHWOOD_FENCE)) o.set(q, AIR);
			}
			if (r <= 4.5) f.set(o, lx, 0, lz, AIR);
		}
		// round wall with log posts and window gaps, door at the front
		for (int lx = -4; lx <= 4; lx++) for (int lz = -4; lz <= 4; lz++) {
			double r = Math.sqrt(lx * lx + lz * lz);
			if (r <= 2.5 || r > 3.5) continue;
			double th = Math.atan2(lz, lx);
			boolean post = ang(th, Math.PI / 4) * r < 0.5;
			for (int h = 0; h <= 2; h++) {
				boolean window = h == 1 && !post && ang(th + Math.PI / 4, Math.PI / 2) * r < 0.6;
				boolean door = lx == 0 && lz >= 3 && h <= 1;
				f.set(o, lx, h, lz, door || window ? AIR : post ? log : bamboo);
			}
		}
		// conical hay roof (overhang 1), finial
		for (int h = 0; h <= 4; h++) {
			double outer = 4.6 - h, inner = outer - 1.2;
			for (int lx = -5; lx <= 5; lx++) for (int lz = -5; lz <= 5; lz++) {
				double r = Math.sqrt(lx * lx + lz * lz);
				if (r <= outer && (r > inner || h == 4)) f.set(o, lx, 3 + h, lz, h == 0 && hash(sd, lx, h, lz) < 0.3 ? st(Blocks.DRIED_KELP_BLOCK) : st(Blocks.HAY_BLOCK));
			}
		}
		f.set(o, 0, 8, 0, st(GreenhazeBlocks.KUSHWOOD_FENCE));
		f.set(o, 0, 9, 0, st(Blocks.LANTERN));
		f.set(o, 0, 6, 0, st(Blocks.IRON_CHAIN));
		f.set(o, 0, 5, 0, st(Blocks.IRON_CHAIN));
		f.set(o, 0, 4, 0, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		// inside: rug, chest, cauldron, Budtender
		for (int lx = -1; lx <= 1; lx++) for (int lz = -1; lz <= 1; lz++) f.set(o, lx, 0, lz, st(Blocks.MOSS_CARPET));
		f.set(o, 1, 0, -2, st(Blocks.CHEST).setValue(ChestBlock.FACING, Direction.SOUTH));
		o.loot(f.at(1, 0, -2), GreenhazeStructures.SESH_HUT_LOOT, sd ^ 0x5E5L);
		f.set(o, -2, 0, 0, st(Blocks.CAULDRON));
		f.set(o, -1, 0, -2, st(Blocks.POTTED_FERN));
		// ladder post and ladder at the deck gap (front)
		BlockPos lp = f.at(0, -1, 5);
		Direction front = f.dir(Direction.SOUTH);
		for (int dy = 0; dy <= 16; dy++) {
			BlockPos p = lp.below(dy);
			if (!o.has(p.getX(), p.getZ())) break;
			if (dy > 0 && ground(o.get(p))) break;
			o.set(p, log);
			BlockPos l = p.relative(front);
			if (o.get(l).isAir() || o.get(l).canBeReplaced()) o.set(l, st(Blocks.LADDER).setValue(LadderBlock.FACING, front));
		}
		BlockPos topLadder = lp.above().relative(front);
		o.set(topLadder, st(Blocks.LADDER).setValue(LadderBlock.FACING, front));
		o.spawn(GreenhazeEntities.BUDTENDER, f.at(0, 0, 1));
	}

	// ============================================================================================ Lucid Spring

	/** Spring in a hollow: the pool takes the hollow's shape, its rim is solid to the water line, and a rill of weired, enclosed pools runs downhill. */
	static void spring(Piece pc, Out o) {
		int ox = pc.o.getX(), oz = pc.o.getZ(), wy = pc.o.getY();
		long sd = pc.seed;
		int rillDir = pc.p(0), rillLen = pc.p(1);
		java.util.function.BiPredicate<Integer, Integer> pool = (x, z) -> inPool(pc, x, z);
		for (int x = ox - 9; x <= ox + 9; x++) for (int z = oz - 9; z <= oz + 9; z++) {
			if (!o.has(x, z)) continue;
			int dx = x - ox, dz = z - oz;
			if (pool.test(x, z)) {
				double r = Math.sqrt(dx * dx + dz * dz);
				int depth = r < 1.5 ? 4 : r < 3.5 ? 3 : 2;
				int bed = wy - depth;
				BlockState bedB = hash(sd, x, 0, z) < 0.5 ? st(Blocks.CLAY) : hash(sd, x, 1, z) < 0.5 ? st(Blocks.MOSS_BLOCK) : st(Blocks.SAND);
				if ((dx == 2 && dz == 1) || (dx == -2 && dz == -1) || (dx == 1 && dz == -3)) bedB = st(Blocks.SEA_LANTERN);
				o.set(new BlockPos(x, bed, z), bedB);
				underpin(o, x, bed - 1, z, st(Blocks.CLAY), st(Blocks.DIRT), 6);
				for (int y = bed + 1; y <= wy; y++) o.set(new BlockPos(x, y, z), st(Blocks.WATER));
				clear(o, x, wy + 1, wy + 4, z);
				if (hash(sd, x, 2, z) < 0.15 && r > 1.5) o.set(new BlockPos(x, wy + 1, z), st(GreenhazeBlocks.LUCID_LILY));
			} else {
				boolean rim = pool.test(x + 1, z) || pool.test(x - 1, z) || pool.test(x, z + 1) || pool.test(x, z - 1);
				if (rim) {
					// solid to the water line (never an open side), mossy stones, and nothing standing above the rim
					BlockState rb = hash(sd, x, 3, z) < 0.35 ? st(Blocks.MOSSY_COBBLESTONE) : st(Blocks.MOSS_BLOCK);
					if (!ground(o.get(new BlockPos(x, wy, z)))) o.set(new BlockPos(x, wy, z), rb);
					underpin(o, x, wy - 1, z, st(Blocks.MOSSY_COBBLESTONE), st(Blocks.COBBLESTONE), 8);
					soft(o, x, wy + 1, wy + 3, z);
				}
			}
		}
		// the rill: a 1-wide channel down the slope, stepping by weirs (a solid lip at each drop), banks solid to the water line
		Direction dir = Direction.from2DDataValue(rillDir);
		int sx = ox, sz = oz;
		for (int i = 0; i < 12 && pool.test(sx, sz); i++) { sx += dir.getStepX(); sz += dir.getStepZ(); }
		Direction side = dir.getClockWise();
		for (int i = 0; i < rillLen; i++) {
			int x = sx + dir.getStepX() * i, z = sz + dir.getStepZ() * i;
			int lv = pc.p(2 + i);
			int prev = i == 0 ? wy : pc.p(1 + i);
			boolean last = i == rillLen - 1;
			if (o.has(x, z)) {
				if (lv < prev || last) {
					// weir or the soak pit at the end
					o.set(new BlockPos(x, prev, z), last ? st(Blocks.GRAVEL) : st(Blocks.MOSSY_COBBLESTONE));
					for (int y = lv; y < prev; y++) o.set(new BlockPos(x, y, z), st(Blocks.MOSSY_COBBLESTONE));
					underpin(o, x, Math.min(lv, prev) - 1, z, st(Blocks.MOSSY_COBBLESTONE), st(Blocks.COBBLESTONE), 8);
					soft(o, x, prev + 1, prev + 3, z);
				} else {
					o.set(new BlockPos(x, lv, z), st(Blocks.WATER));
					o.set(new BlockPos(x, lv - 1, z), st(Blocks.MOSSY_COBBLESTONE));
					underpin(o, x, lv - 2, z, st(Blocks.COBBLESTONE), st(Blocks.COBBLESTONE), 8);
					clear(o, x, lv + 1, lv + 3, z);
				}
			}
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				int bx = x + side.getStepX() * sgn, bz = z + side.getStepZ() * sgn;
				if (!o.has(bx, bz) || pool.test(bx, bz)) continue;
				BlockPos b = new BlockPos(bx, Math.max(lv, prev), bz);
				if (!ground(o.get(b))) o.set(b, hash(sd, bx, 4, bz) < 0.5 ? st(Blocks.MOSS_BLOCK) : st(Blocks.MOSSY_COBBLESTONE));
				underpin(o, bx, b.getY() - 1, bz, st(Blocks.MOSSY_COBBLESTONE), st(Blocks.COBBLESTONE), 8);
				if (hash(sd, bx, 5, bz) < 0.3) o.set(b.above(), st(Blocks.FERN));
			}
		}
		// mossy boulders and a kushwood bench facing the water, then the offering chest on the bed
		for (int i = 0; i < 3; i++) {
			double a = i * 2.1 + (sd & 15) * 0.3;
			int bx = ox + (int) Math.round(Math.cos(a) * 8), bz = oz + (int) Math.round(Math.sin(a) * 8);
			if (!o.has(bx, bz) || pool.test(bx, bz)) continue;
			int g = topSolid(o, bx, bz, wy + 6, 12);
			if (g == Integer.MIN_VALUE) continue;
			o.set(new BlockPos(bx, g + 1, bz), st(Blocks.MOSSY_COBBLESTONE));
			if (i != 1) o.set(new BlockPos(bx, g + 2, bz), st(Blocks.MOSSY_COBBLESTONE_SLAB));
			o.set(new BlockPos(bx, g + 1, bz).relative(Direction.from2DDataValue(i)), st(Blocks.MOSS_BLOCK));
		}
		if (o.has(ox, oz)) {
			BlockPos c = new BlockPos(ox, wy - 3, oz);
			o.set(c, st(Blocks.CHEST).setValue(ChestBlock.WATERLOGGED, true));
			o.loot(c, GreenhazeStructures.SPRING_LOOT, sd ^ 0x1C1DL);
		}
	}

	/** Pool cell: always the inner r 2.5; out to r 6.5 where the stored ground lattice, plus a bowl slope, stays under the water line. */
	static boolean inPool(Piece pc, int x, int z) {
		int dx = x - pc.o.getX(), dz = z - pc.o.getZ();
		double r = Math.sqrt(dx * dx + dz * dz);
		if (r <= 2.5) return true;
		if (r > 6.5) return false;
		int n = pc.p(2 + pc.p(1) + 3);
		if (n <= 0) return r <= 4;
		// a bowl: the edge sits near r 4.5 on level ground, wider in a hollow, tighter on a rise; ragged by a stable hash
		return lat(pc, 2 + pc.p(1), x, z) + 0.5 * (r - 4.5) <= pc.o.getY() + 0.6 * (hash(pc.seed, x, 9, z) - 0.5);
	}

	// ============================================================================================ Return rift (dev module only)

	/** Ruin ring: 8 broken menhirs, each standing on its own ground with a tilted cap; beacon beam and the rift at the centre. */
	static void rift(Piece pc, Out o) {
		int x = pc.o.getX(), y = pc.o.getY(), z = pc.o.getZ();
		long sd = pc.seed;
		BlockState wall = st(Blocks.MOSSY_STONE_BRICKS);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + 0.2;
			int px = x + (int) Math.round(Math.cos(a) * 4.5), pz = z + (int) Math.round(Math.sin(a) * 4.5);
			int g = topSolid(o, px, pz, y + 6, 14);
			if (g == Integer.MIN_VALUE) continue;
			int h = 1 + (int) (hash(sd, px, 0, pz) * 4);
			o.set(new BlockPos(px, g, pz), st(Blocks.MOSSY_COBBLESTONE));
			for (int dy = 1; dy <= h; dy++) o.set(new BlockPos(px, g + dy, pz), hash(sd, px, dy, pz) < 0.3 ? st(Blocks.CRACKED_STONE_BRICKS) : wall);
			o.set(new BlockPos(px, g + h + 1, pz), stair(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.from2DDataValue((int) (hash(sd, px, 9, pz) * 4)), false));
		}
		for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
			if (dx * dx + dz * dz > 20 || !o.has(x + dx, z + dz)) continue;
			int g = topSolid(o, x + dx, z + dz, y + 4, 10);
			if (g == Integer.MIN_VALUE) continue;
			if (hash(sd, x + dx, 1, z + dz) < 0.55) o.set(new BlockPos(x + dx, g, z + dz), hash(sd, x + dx, 2, z + dz) < 0.5 ? st(Blocks.MOSS_BLOCK) : st(Blocks.MOSSY_COBBLESTONE));
			soft(o, x + dx, g + 1, g + 3, z + dz);
		}
		if (o.has(x, z)) {
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) o.set(new BlockPos(x + dx, y - 2, z + dz), st(Blocks.IRON_BLOCK));
			o.set(new BlockPos(x, y - 1, z), st(Blocks.BEACON));
			o.set(new BlockPos(x, y, z), glass(DyeColor.LIME));
			clear(o, x, y + 1, y + 4, z);
		}
		if (o.has(x + 2, z)) {
			o.set(new BlockPos(x + 2, y - 1, z), st(Blocks.CRYING_OBSIDIAN));
			o.set(new BlockPos(x + 2, y, z), st(StubBlocks.RETURN_RIFT));
			o.set(new BlockPos(x + 2, y + 1, z), st(StubBlocks.RETURN_RIFT));
		}
	}
}
