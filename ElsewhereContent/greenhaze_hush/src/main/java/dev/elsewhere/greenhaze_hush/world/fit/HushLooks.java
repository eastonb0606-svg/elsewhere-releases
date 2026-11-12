package dev.elsewhere.greenhaze_hush.world.fit;

import static dev.elsewhere.greenhaze_hush.world.fit.GhFit.*;

import dev.elsewhere.hush.HushBlocks;
import dev.elsewhere.hush.HushEntities;
import dev.elsewhere.hush.HushStructures;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hush looks (STRUCTURES.md 4): the Listening Engine gets vault ribs, a sculk cochlea and an access tunnel that climbs to the nearest cave
 * shelf; the Choir Geode keeps its shape (encapsulated, with crystal stalactites); the Silent Monastery becomes an octagonal vaulted chapel
 * and rock cells on the cave's own shelves joined by galleries on piers; the Hanging Undercity hangs from the real ceiling by anchor plates
 * and chains, octagonal inverted-spire towers joined by chain-railed bridges.
 */
public final class HushLooks {
	private HushLooks() {}

	static void register() {
		BUILDERS.put("engine", HushLooks::engine);
		BUILDERS.put("engine_tunnel", HushLooks::tunnel);
		BUILDERS.put("geode", HushLooks::geode);
		BUILDERS.put("chapel", HushLooks::chapel);
		BUILDERS.put("cell", HushLooks::cell);
		BUILDERS.put("gallery", HushLooks::gallery);
		BUILDERS.put("tower", HushLooks::tower);
		BUILDERS.put("bridge", HushLooks::bridge);
	}

	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	static boolean oct(int lx, int lz, double r) {
		return Math.abs(lx) <= r && Math.abs(lz) <= r && Math.abs(lx) + Math.abs(lz) <= r * 1.5;
	}

	// ============================================================================================ The Listening Engine

	/** The ear: ellipsoid vault (tile shell) with 12 brick ribs and a crown, the terraced funnel with a sculk cochlea spiral, throne and membranes. */
	static void engine(Piece pc, Out o) {
		int x = pc.o.getX(), y = pc.o.getY(), z = pc.o.getZ();
		long sd = pc.seed;
		RandomSource r = RandomSource.create(sd);
		double rx = 22, ry = 14, rz = 22;
		int cy = y + 8;
		for (int dx = -23; dx <= 23; dx++) for (int dz = -23; dz <= 23; dz++) {
			if (!o.has(x + dx, z + dz)) continue;
			double rh = Math.sqrt(dx * dx + dz * dz), th = Math.atan2(dz, dx);
			for (int dy = -15; dy <= 15; dy++) {
				double d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
				BlockPos p = new BlockPos(x + dx, cy + dy, z + dz);
				if (d <= 1) {
					boolean rib = d > 0.80 && dy > -6 && GreenhazeLooks.ang(th, Math.PI / 6) * rh < 0.8;
					boolean crown = d > 0.8 && dy >= 11 && rh < 6.5;
					o.set(p, rib ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : crown ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : AIR);
				} else if (d <= 1.25) {
					o.set(p, hash(sd, p.getX(), p.getY(), p.getZ()) < 0.12 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
				}
			}
		}
		// the funnel terraces (as in 2.x, so Auris's arena reads the same) with the cochlea: a sculk spiral winding down to the throne
		for (int dx = -21; dx <= 21; dx++) for (int dz = -21; dz <= 21; dz++) {
			if (!o.has(x + dx, z + dz)) continue;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 21) continue;
			int step = (int) Math.min(4, Math.max(0, (d - 5) / 4));
			int top = y - 4 + step;
			double th = Math.atan2(dz, dx) + Math.PI;
			double spiral = 5 + (th / (2 * Math.PI)) * 5.5;
			boolean cochlea = d > 5 && (Math.abs(d - spiral) < 0.7 || Math.abs(d - spiral - 5.5) < 0.7 || Math.abs(d - spiral - 11) < 0.7);
			for (int yy = y - 8; yy <= top; yy++) {
				BlockState b = yy == top ? (cochlea ? Blocks.SCULK.defaultBlockState() : step % 2 == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState())
					: Blocks.DEEPSLATE.defaultBlockState();
				o.set(new BlockPos(x + dx, yy, z + dz), b);
			}
			// terrace lips: a polished stair edge where each terrace drops to the next
			int inStep = (int) Math.min(4, Math.max(0, (d - 1 - 5) / 4));
			if (inStep < step && !cochlea) o.set(new BlockPos(x + dx, top, z + dz), stair(Blocks.POLISHED_DEEPSLATE_STAIRS, toward(dx, dz), false));
		}
		int floor = y - 4;
		o.fill(x - 2, floor, z - 2, x + 2, floor, z + 2, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
		o.fill(x - 1, floor + 1, z - 1, x + 1, floor + 3, z + 1, Blocks.SCULK_CATALYST.defaultBlockState());
		for (int[] c : new int[][] {{12, 0}, {-12, 0}, {0, 12}, {0, -12}}) {
			int px = x + c[0], pz = z + c[1], base = floor + 2;
			for (int yy = base; yy < base + 5; yy++) o.set(new BlockPos(px, yy, pz), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
			o.fill(px - 1, base + 5, pz - 1, px + 1, base + 7, pz + 1, HushBlocks.TYMPANUM_MEMBRANE.defaultBlockState());
			o.set(new BlockPos(px, base + 8, pz), Blocks.SCULK_SENSOR.defaultBlockState());
			// a ring of chiseled corbels under each membrane
			for (Direction dir : Direction.Plane.HORIZONTAL) o.set(new BlockPos(px + dir.getStepX() * 2, base + 5, pz + dir.getStepZ() * 2), stair(Blocks.DEEPSLATE_BRICK_STAIRS, dir.getOpposite(), true));
		}
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + Math.PI / 6;
			int px = x + (int) Math.round(Math.cos(a) * 9), pz = z + (int) Math.round(Math.sin(a) * 9);
			o.fill(px - 1, floor + 1, pz - 1, px + 1, floor + 1, pz + 1, (i % 2 == 0 ? Blocks.WOOL.pick(DyeColor.BLACK) : Blocks.WOOL.pick(DyeColor.GRAY)).defaultBlockState());
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			int px = x + (int) Math.round(Math.cos(a) * 15), pz = z + (int) Math.round(Math.sin(a) * 15);
			o.set(new BlockPos(px, y + 17, pz), Blocks.DEEPSLATE_TILES.defaultBlockState());
			o.set(new BlockPos(px, y + 16, pz), Blocks.IRON_CHAIN.defaultBlockState());
			o.set(new BlockPos(px, y + 15, pz), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		r.nextInt();
	}

	/** Access tunnel (local +z runs away from the room): arched 3x3, 1 step per 2 blocks to the nearest cave floor. p = [n, levels..., cache]; cache 1 = the Engine surveyor's chest. */
	static void tunnel(Piece pc, Out o) {
		Frame f = pc.frame();
		int n = pc.p(0), base = f.oy();
		long sd = pc.seed;
		for (int i = 0; i < n; i++) {
			int w = pc.p(1 + i), dy = w - base;
			int prev = i > 0 ? pc.p(i) - base : dy;
			for (int lx = -2; lx <= 2; lx++) for (int h = -1; h <= 4; h++) {
				BlockPos q = f.at(lx, dy + h, i);
				if (!o.has(q.getX(), q.getZ())) continue;
				boolean inside = Math.abs(lx) <= 1 && h >= 0 && h <= 2 && !(Math.abs(lx) == 1 && h == 2);
				if (inside) { o.set(q, AIR); continue; }
				if (Math.abs(lx) == 1 && h == 2) { f.set(o, lx, dy + h, i, stair(Blocks.DEEPSLATE_TILE_STAIRS, lx < 0 ? Direction.WEST : Direction.EAST, true)); continue; }
				if (h == -1 && Math.abs(lx) <= 1) { o.set(q, lx == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState()); underpin(o, q.getX(), q.getY() - 1, q.getZ(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 8); continue; }
				// lining only where the rock is open (a cave crossing), never as a box
				if (o.get(q).isAir() && (h <= 3)) o.set(q, hash(sd, q.getX(), q.getY(), q.getZ()) < 0.2 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
			}
			if (w > prev) for (int lx = -1; lx <= 1; lx++) f.set(o, lx, dy - 1, i - 1 < 0 ? 0 : i - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, false));
			if (w < prev) for (int lx = -1; lx <= 1; lx++) f.set(o, lx, dy, i, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
			if (i % 6 == 3) f.set(o, 0, dy + 2, i, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		if (pc.p(1 + n) != 1) return;
		int ci = Math.min(n - 1, 8), cdy = pc.p(1 + ci) - base;
		f.set(o, 1, cdy, ci, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST));
		o.loot(f.at(1, cdy, ci), HushStructures.ENGINE_LOOT, sd ^ 0xE1L);
	}

	// ============================================================================================ The Choir Geode

	/** The 2.x geode, unchanged in shape, plus crystal stalactites hanging from the vault. Encapsulated by its beard. */
	static void geode(Piece pc, Out o) {
		HushStructures.CHOIR_GEODE.build(o, pc.o, RandomSource.create(pc.seed));
		int x = pc.o.getX(), y = pc.o.getY(), z = pc.o.getZ(), R = 24;
		for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
			if (!o.has(x + dx, z + dz)) continue;
			for (int dy = 6; dy <= R + 2; dy++) {
				double d = Math.sqrt(dx * dx + dz * dz + (dy * 0.9) * (dy * 0.9));
				double below = Math.sqrt(dx * dx + dz * dz + ((dy - 1) * 0.9) * ((dy - 1) * 0.9));
				if (d > R - 1 && d <= R && below <= R - 1 && hash(pc.seed, x + dx, y + dy, z + dz) < 0.03) {
					o.set(new BlockPos(x + dx, y + dy - 1, z + dz), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
				}
			}
		}
	}

	// ============================================================================================ The Silent Monastery

	/** Octagonal chapel: hushstone shell lined with black wool, a stepped vault, arched doors toward its galleries, pews, lectern, loot barrel. */
	static void chapel(Piece pc, Out o) {
		Frame f = pc.frame();
		int R = 6, y0 = f.oy(), doors = pc.p(0);
		long sd = pc.seed;
		BlockState brick = HushBlocks.HUSHSTONE_BRICKS.defaultBlockState(), wool = Blocks.WOOL.pick(DyeColor.BLACK).defaultBlockState();
		for (int lx = -R - 1; lx <= R + 1; lx++) for (int lz = -R - 1; lz <= R + 1; lz++) {
			BlockPos fp = f.at(lx, -1, lz);
			if (!o.has(fp.getX(), fp.getZ())) continue;
			if (oct(lx, lz, R)) {
				o.set(fp, oct(lx, lz, R - 1) ? HushBlocks.HUSHWEAVE.defaultBlockState() : brick);
				underpin(o, fp.getX(), y0 - 2, fp.getZ(), brick, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 8);
				for (int dy = 0; dy <= 7; dy++) {
					BlockState b;
					boolean ringR = !oct(lx, lz, R - 1), ring1 = oct(lx, lz, R - 1) && !oct(lx, lz, R - 2), ring2 = oct(lx, lz, R - 2) && !oct(lx, lz, R - 3);
					if (dy <= 4) b = ringR ? (hash(sd, fp.getX(), dy, fp.getZ()) < 0.1 ? HushBlocks.POLISHED_HUSHSTONE.defaultBlockState() : brick) : ring1 && dy <= 3 ? wool : AIR;
					else if (dy == 5) b = ringR ? brick : ring1 ? stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, f.dir(toward(lx, lz)), true) : AIR;
					else if (dy == 6) b = ringR ? AIR : ring1 ? brick : ring2 ? stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, f.dir(toward(lx, lz)), true) : AIR;
					else b = ringR || ring1 ? AIR : brick;
					BlockPos q = f.at(lx, dy, lz);
					if (b.isAir()) { if (!ringR && !ring1 || dy <= 4) { if (!o.get(q).isAir()) o.set(q, AIR); } }
					else o.set(q, b);
				}
				if (oct(lx, lz, R - 2)) f.set(o, lx, 0, lz, HushBlocks.HUSHWEAVE_CARPET.defaultBlockState());
			}
		}
		// buttresses at the 8 octagon faces
		int[][] bt = {{R + 1, 3}, {R + 1, -3}, {-R - 1, 3}, {-R - 1, -3}, {3, R + 1}, {-3, R + 1}, {3, -R - 1}, {-3, -R - 1}};
		for (int[] b : bt) {
			BlockPos bp = f.at(b[0], -1, b[1]);
			underpin(o, bp.getX(), y0 - 1, bp.getZ(), brick, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 8);
			for (int dy = 0; dy <= 2; dy++) f.set(o, b[0], dy, b[1], brick);
			f.set(o, b[0], 3, b[1], stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, toward(-b[0], -b[1]), false));
		}
		// arched doors: bit 0 front (+z, the entrance), 1 back, 2 west, 3 east
		for (int i = 0; i < 4; i++) {
			if ((doors & (1 << i)) == 0) continue;
			for (int t = R - 2; t <= R; t++) for (int k = -1; k <= 1; k++) for (int dy = 0; dy <= 2; dy++) {
				int lx = i < 2 ? k : (i == 2 ? -t : t), lz = i < 2 ? (i == 0 ? t : -t) : k;
				if (Math.abs(k) == 1 && dy == 2) f.set(o, lx, dy, lz, stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, i < 2 ? (k < 0 ? Direction.WEST : Direction.EAST) : (k < 0 ? Direction.NORTH : Direction.SOUTH), true));
				else f.set(o, lx, dy, lz, AIR);
			}
		}
		// furnishings: lectern and candles at the back, the loot barrel, pews facing the lectern, hanging soul lanterns
		f.set(o, 0, 0, -(R - 3), Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.SOUTH));
		f.set(o, -1, 0, -(R - 3), Blocks.DYED_CANDLE.pick(DyeColor.WHITE).defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
		f.set(o, 1, 0, -(R - 3), Blocks.DYED_CANDLE.pick(DyeColor.WHITE).defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
		f.set(o, R - 3, 0, -(R - 3), Blocks.BARREL.defaultBlockState());
		o.loot(f.at(R - 3, 0, -(R - 3)), HushStructures.MONASTERY_LOOT, sd ^ 0x30L);
		f.set(o, -(R - 3), 0, -(R - 3), Blocks.BOOKSHELF.defaultBlockState());
		for (int lz = 1; lz <= 3; lz += 2) for (int lx = -3; lx <= 3; lx++) if (lx != 0) f.set(o, lx, 0, lz, stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, Direction.SOUTH, false));
		for (int[] c : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) f.set(o, c[0], 5, c[1], Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		o.spawn(HushEntities.MUTE_ARCHIVIST, f.at(0, 0, -1));
	}

	/** A round rock cell on its own shelf: wool-lined, a domed cap, a sleeping mat, a shelf, an unlit candle and a barrel; its door faces local +z. */
	static void cell(Piece pc, Out o) {
		Frame f = pc.frame();
		int y0 = f.oy();
		long sd = pc.seed;
		BlockState brick = HushBlocks.HUSHSTONE_BRICKS.defaultBlockState(), wool = Blocks.WOOL.pick(DyeColor.GRAY).defaultBlockState();
		for (int lx = -4; lx <= 4; lx++) for (int lz = -4; lz <= 4; lz++) {
			double r = Math.sqrt(lx * lx + lz * lz);
			if (r > 3.5) continue;
			BlockPos fp = f.at(lx, -1, lz);
			if (!o.has(fp.getX(), fp.getZ())) continue;
			o.set(fp, brick);
			underpin(o, fp.getX(), y0 - 2, fp.getZ(), brick, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 8);
			for (int dy = 0; dy <= 4; dy++) {
				BlockState b;
				if (dy <= 2) b = r > 2.5 ? brick : r > 1.8 ? wool : AIR;
				else if (dy == 3) b = r > 1.6 ? brick : AIR;
				else b = r <= 1.6 ? brick : AIR;
				if (b.isAir()) { if (dy <= 3 && r <= 2.5) f.set(o, lx, dy, lz, AIR); }
				else f.set(o, lx, dy, lz, b);
			}
		}
		for (int dy = 0; dy <= 1; dy++) for (int t = 2; t <= 3; t++) f.set(o, 0, dy, t, AIR);
		f.set(o, -1, 0, -1, HushBlocks.HUSHWEAVE_CARPET.defaultBlockState());
		f.set(o, 0, 0, -1, HushBlocks.HUSHWEAVE_CARPET.defaultBlockState());
		f.set(o, 1, 0, -1, Blocks.BOOKSHELF.defaultBlockState());
		f.set(o, -1, 0, 1, Blocks.BARREL.defaultBlockState());
		f.set(o, 1, 0, 1, Blocks.DYED_CANDLE.pick(DyeColor.WHITE).defaultBlockState().setValue(CandleBlock.CANDLES, 1).setValue(CandleBlock.LIT, false));
		f.set(o, 0, 3, 0, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
	}

	/** Gallery on piers (local +z from one door to the next): 3-wide deck, carpet runner, parapets; a pier every 4 blocks with corbelled arches. */
	static void gallery(Piece pc, Out o) {
		Frame f = pc.frame();
		int n = pc.p(0), base = f.oy();
		BlockState brick = HushBlocks.HUSHSTONE_BRICKS.defaultBlockState();
		for (int i = 0; i < n; i++) {
			int w = pc.p(1 + i), dy = w - base;
			int prev = i > 0 ? pc.p(i) - base : dy;
			boolean pier = i % 4 == 0 || i == n - 1;
			for (int lx = -2; lx <= 2; lx++) {
				BlockPos fp = f.at(lx, dy - 1, i);
				if (!o.has(fp.getX(), fp.getZ())) continue;
				o.set(fp, brick);
				if (Math.abs(lx) <= 1 && pier) underpin(o, fp.getX(), fp.getY() - 1, fp.getZ(), brick, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 12);
				else if (Math.abs(lx) <= 1 && !pier) {
					// corbel arch springing from the piers
					int dp = Math.min(i % 4, 4 - i % 4);
					if (dp == 1) f.set(o, lx, dy - 2, i, stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, i % 4 == 1 ? Direction.NORTH : Direction.SOUTH, true));
				}
				for (int h = 0; h <= 2; h++) {
					BlockPos q = f.at(lx, dy + h, i);
					if (Math.abs(lx) <= 1 && !o.get(q).isAir()) o.set(q, AIR);
				}
				if (Math.abs(lx) == 2) f.set(o, lx, dy, i, HushBlocks.HUSHSTONE_BRICK_WALL.defaultBlockState());
			}
			f.set(o, 0, dy, i, HushBlocks.HUSHWEAVE_CARPET.defaultBlockState());
			if (prev > dy) for (int lx = -1; lx <= 1; lx++) f.set(o, lx, dy, i, stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, Direction.NORTH, false));
			if (i > 0 && prev < dy) for (int lx = -1; lx <= 1; lx++) f.set(o, lx, prev, i - 1, stair(HushBlocks.HUSHSTONE_BRICK_STAIRS, Direction.SOUTH, false));
		}
	}

	// ============================================================================================ The Hanging Undercity

	/** An octagonal tower hanging from an anchor plate on the real ceiling by chains, tapering to an inverted spire. p = [ceilY, h, w, centre]. */
	static void tower(Piece pc, Out o) {
		int tx = pc.o.getX(), top = pc.o.getY(), tz = pc.o.getZ();
		int ceil = pc.p(0), h = pc.p(1), w = pc.p(2);
		boolean centre = pc.p(3) == 1;
		long sd = pc.seed;
		BlockState bricks = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
		// anchor plate fused to the rock, and up into any pocket (cap 6)
		for (int dx = -w - 1; dx <= w + 1; dx++) for (int dz = -w - 1; dz <= w + 1; dz++) {
			if (!oct(dx, dz, w + 1) || !o.has(tx + dx, tz + dz)) continue;
			for (int y = ceil - 1; y < ceil + 6; y++) {
				BlockPos p = new BlockPos(tx + dx, y, tz + dz);
				if (y > ceil - 1 && ground(o.get(p))) break;
				o.set(p, y == ceil - 1 ? (hash(sd, p.getX(), y, p.getZ()) < 0.3 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : tiles) : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
			}
		}
		// chains from the plate to the parapet
		for (int[] c : new int[][] {{w, w / 2}, {-w, -w / 2}, {w / 2, -w}, {-w / 2, w}}) {
			for (int y = top + 1; y <= ceil - 2; y++) if (o.has(tx + c[0], tz + c[1])) o.set(new BlockPos(tx + c[0], y, tz + c[1]), Blocks.IRON_CHAIN.defaultBlockState());
		}
		int bottomRoom = top - h + 5;
		for (int dx = -w; dx <= w; dx++) for (int dz = -w; dz <= w; dz++) {
			if (!o.has(tx + dx, tz + dz) || !oct(dx, dz, w)) continue;
			boolean ring = !oct(dx, dz, w - 1);
			// parapet at the top floor, openings at the cardinals for the bridges
			boolean cardinal = (dx == 0 || dz == 0);
			if (ring) o.set(new BlockPos(tx + dx, top, tz + dz), cardinal ? AIR : Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
			else o.set(new BlockPos(tx + dx, top, tz + dz), AIR);
			o.set(new BlockPos(tx + dx, top - 1, tz + dz), ring ? tiles : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			for (int dy = 2; dy <= h - 4; dy++) {
				int y = top - dy;
				BlockState b;
				if (ring) {
					boolean window = dy % 6 == 3 && (dx == 0 || dz == 0) && !(dx == 0 && dz < 0); // never behind the ladder
					b = window ? AIR : dy % 6 == 0 ? tiles : hash(sd, tx + dx, y, tz + dz) < 0.15 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : bricks;
				} else b = y == bottomRoom - 1 ? (centre && Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Blocks.REINFORCED_DEEPSLATE.defaultBlockState() : tiles) : AIR;
				o.set(new BlockPos(tx + dx, y, tz + dz), b);
			}
		}
		// ladder shaft up the inside of the north wall, from the bottom room through the top floor
		for (int y = bottomRoom; y <= top - 1; y++) {
			if (!o.has(tx, tz - w + 1)) break;
			o.set(new BlockPos(tx, y, tz - w + 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		// inverted spire below the bottom room
		int tipTop = top - h + 5 - 2;
		for (int k = 0; k <= 2 * w + 1; k++) {
			int rr = w - k / 2, y = tipTop - k;
			for (int dx = -w; dx <= w; dx++) for (int dz = -w; dz <= w; dz++) {
				if (!o.has(tx + dx, tz + dz)) continue;
				if (rr >= 0 && oct(dx, dz, rr)) o.set(new BlockPos(tx + dx, y, tz + dz), rr == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : tiles);
			}
		}
		int tipY = tipTop - (2 * w + 1) - 1;
		if (o.has(tx, tz)) {
			o.set(new BlockPos(tx, tipY, tz), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			o.set(new BlockPos(tx, bottomRoom, tz), Blocks.CHEST.defaultBlockState());
			o.loot(new BlockPos(tx, bottomRoom, tz), HushStructures.UNDERCITY_LOOT, sd ^ 0x7EL);
			o.set(new BlockPos(tx, bottomRoom + 2, tz), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			o.set(new BlockPos(tx, bottomRoom + 3, tz), Blocks.IRON_CHAIN.defaultBlockState());
			if (h > 12) o.set(new BlockPos(tx + w, bottomRoom + 6, tz), Blocks.SCULK_SENSOR.defaultBlockState());
		}
		if (centre && o.has(tx + 1, tz)) {
			o.set(new BlockPos(tx + 1, bottomRoom, tz), StubBlocks.RETURN_RIFT.defaultBlockState());
			o.set(new BlockPos(tx + 1, bottomRoom + 1, tz), StubBlocks.RETURN_RIFT.defaultBlockState());
		}
	}

	/** Chain-railed bridge between two tower tops: 3-wide slab deck, chain posts every other block. p = [bx, by, bz] (from o). */
	static void bridge(Piece pc, Out o) {
		int ax = pc.o.getX(), ay = pc.o.getY(), az = pc.o.getZ(), bx = pc.p(0), by = pc.p(1), bz = pc.p(2);
		int len = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
		if (len == 0) return;
		double ux = (bx - ax) / (double) len, uz = (bz - az) / (double) len, nl = Math.sqrt(ux * ux + uz * uz);
		double px = -uz / nl, pz = ux / nl;
		for (int t = 0; t <= len; t++) {
			double cx = ax + ux * t, cz = az + uz * t;
			int y = ay + (int) Math.round((by - ay) * (t / (double) len));
			for (int k = -1; k <= 1; k++) {
				int x = (int) Math.round(cx + px * k), z = (int) Math.round(cz + pz * k);
				if (!o.has(x, z)) continue;
				BlockPos d = new BlockPos(x, y - 1, z);
				if (!o.get(d).isAir() && ground(o.get(d)) && !o.get(d).is(Blocks.DEEPSLATE_TILE_SLAB)) continue;
				o.set(d, k == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
				if (o.get(d.above()).isAir() || k != 0) o.set(d.above(), AIR);
				if (k != 0 && t % 2 == 0) {
					int xx = (int) Math.round(cx + px * 2 * k), zz = (int) Math.round(cz + pz * 2 * k);
					if (o.has(xx, zz) && o.get(new BlockPos(xx, y, zz)).isAir()) {
						o.set(new BlockPos(xx, y - 1, zz), Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
						o.set(new BlockPos(xx, y, zz), Blocks.IRON_CHAIN.defaultBlockState());
					}
				}
			}
		}
	}
}
