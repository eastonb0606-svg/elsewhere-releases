package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.boss.grunthar.GruntharArena;
import dev.elsewhere.boss.grunthar.GruntharContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * v2 Treasure Hall (STRUCTURES.md 4, "a façade on a netherrack cliff with the hall carved behind it; terrace bridges; open lava
 * seas rejected"). The fight room is unchanged (radius 13, 12 high, throne, lavafalls, hoard), so the boss plays the same.
 * Outside: the site prefers a cliff (FitSite), so the round hall sits buried in netherrack; a bastion gate façade with two
 * flanking towers faces the open cavern to the south, and a railed terrace bridge runs out from the gate on its own piers.
 * The roof dome and buttresses are written only into open space, so natural rock above a buried hall stays. No valuable
 * blocks: the hoard and trims are honeycomb, yellow and orange terracotta and chiseled blackstone.
 */
public final class GruntharHall implements FitArena {
	static final int R = GruntharArena.RADIUS, H = GruntharArena.HEIGHT;
	static final FitW.Ladder LADDER = new FitW.Ladder(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
		Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState());
	/** The terrace runs from the gate (z = R+6) to z = R+10 (inside the 3x3 chunks of the centre). */
	static final int TERRACE_Z0 = R + 6, TERRACE_Z1 = R + 10, TERRACE_W = 4;

	@Override
	public String id() {
		return "grunthar";
	}

	@Override
	public void build(FitW w, BlockPos o) {
		BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState chiseled = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState gold = Blocks.DYED_TERRACOTTA.yellow().defaultBlockState();
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// footing under the whole hall disc, the gate and the towers (solid 12, then piers)
		for (int dz = -R - 1; dz <= R + 1; dz++) for (int dx = -R - 1; dx <= R + 1; dx++)
			if (dx * dx + dz * dz <= (R + 1.5) * (R + 1.5)) w.footingOrPier(x + dx, z + dz, y - 3, LADDER, 12, 64, o);
		// shell: floor, walls (cracked by hash), ceiling; the room interior is the only air written
		w.disc(o, y - 2, R + 0.5, Blocks.BLACKSTONE.defaultBlockState());
		w.disc(o, y - 1, R + 0.5, bricks);
		for (int dy = 0; dy < H; dy++) {
			w.disc(o, y + dy, R - 0.5, air);
			int ir = R + 1;
			for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
				double d = dx * dx + dz * dz;
				if (d >= (R - 0.5) * (R - 0.5) && d <= (R + 0.5) * (R + 0.5))
					w.set(x + dx, y + dy, z + dz, w.hash(x + dx, y + dy, z + dz) < 18 ? cracked : bricks);
			}
		}
		w.disc(o, y + H, R + 0.5, Blocks.BLACKSTONE.defaultBlockState());
		// floor trims: chiseled ring, a honey-gold ring at the centre
		w.ring(o, y - 1, 5.5, 6.5, chiseled);
		w.ring(o, y - 1, 2.0, 2.6, gold);
		// pillars with chiseled bands (snout carvings) and hanging lanterns
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int px = x + (int) Math.round(Math.cos(a) * (R - 1)), pz = z + (int) Math.round(Math.sin(a) * (R - 1));
			for (int dy = 0; dy < H; dy++) w.set(px, y + dy, pz, dy % 4 == 3 ? chiseled : Blocks.POLISHED_BASALT.defaultBlockState());
		}
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			w.set(x + (int) Math.round(Math.cos(a) * 7), y + H - 1, z + (int) Math.round(Math.sin(a) * 7), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// four lavafalls behind iron bars on the diagonals (enclosed sources: brick behind, bars in front, floor and ceiling)
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			int lx = x + (int) Math.round(Math.cos(a) * R), lz = z + (int) Math.round(Math.sin(a) * R);
			int ix = x + (int) Math.round(Math.cos(a) * (R - 1)), iz = z + (int) Math.round(Math.sin(a) * (R - 1));
			int bx = x + (int) Math.round(Math.cos(a) * (R + 1)), bz = z + (int) Math.round(Math.sin(a) * (R + 1));
			for (int dy = 0; dy < H; dy++) {
				w.set(bx, y + dy, bz, bricks);
				w.set(lx, y + dy, lz, Blocks.LAVA.defaultBlockState());
				w.set(ix, y + dy, iz, dy < H - 1 ? Blocks.IRON_BARS.defaultBlockState() : bricks);
			}
		}
		// the hoard (north half), piled higher towards the wall: honeycomb, yellow and orange terracotta (no gold blocks)
		for (int dz = -R + 1; dz <= -4; dz++) for (int dx = -R + 1; dx <= R - 1; dx++) {
			if (dx * dx + dz * dz > (R - 1.5) * (R - 1.5)) continue;
			if (Math.abs(dx) <= 2 && dz >= -8) continue; // path to the throne
			int h = (int) Math.round((-dz - 4) / 3.0 + w.hash(x + dx, 0, z + dz) / 84.0);
			for (int k = 0; k < h; k++) {
				int r = w.hash(x + dx, y + k, z + dz);
				w.set(x + dx, y + k, z + dz, r < 45 ? Blocks.HONEYCOMB_BLOCK.defaultBlockState() : r < 75 ? gold : r < 90 ? Blocks.DYED_TERRACOTTA.orange().defaultBlockState() : chiseled);
			}
		}
		// dais and throne
		w.box(x - 2, y, z - 11, x + 2, y + 1, z - 7, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		w.box(x - 1, y, z - 6, x + 1, y, z - 6, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
		w.box(x - 1, y + 2, z - 10, x + 1, y + 4, z - 10, Blocks.HONEYCOMB_BLOCK.defaultBlockState());
		w.set(x, y + 5, z - 10, chiseled);
		w.set(x - 1, y + 2, z - 9, gold);
		w.set(x + 1, y + 2, z - 9, gold);
		BlockPos t = o.offset(GruntharArena.THRONE);
		w.set(t, GruntharContent.GILDED_THRONE.defaultBlockState());
		// gate passage (south): 5 wide, 4 high, from the wall to the façade
		for (int r = R - 1; r <= R + 5; r++) for (int dx = -2; dx <= 2; dx++) {
			w.set(x + dx, y - 1, z + r, bricks);
			w.set(x + dx, y + 4, z + r, bricks);
			for (int dy = 0; dy < 4; dy++) w.set(x + dx, y + dy, z + r, Math.abs(dx) == 2 ? (dy == 3 ? chiseled : bricks) : air);
			for (int dx2 = -3; dx2 <= 3; dx2 += 6) w.footingOrPier(x + dx2, z + r, y - 1, LADDER, 12, 64, o);
			w.footingOrPier(x + dx, z + r, y - 2, LADDER, 12, 64, o);
		}
		facade(w, o, bricks, chiseled, gold);
		terrace(w, o, bricks, chiseled);
		roof(w, o);
	}

	/** The bastion gate façade at z = R+5: a stepped gable over the gate, two square towers with crenels, chains and lanterns. */
	private static void facade(FitW w, BlockPos o, BlockState bricks, BlockState chiseled, BlockState gold) {
		int x = o.getX(), y = o.getY(), z = o.getZ() + R + 5;
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
		// front wall x -9..9, up to y+8, gate opening x -1..1 / y..y+3 (the passage)
		for (int dx = -9; dx <= 9; dx++) {
			int top = y + 8 - Math.max(0, Math.abs(dx) - 4) / 2;
			for (int yy = y - 1; yy <= top; yy++) {
				boolean gate = Math.abs(dx) <= 1 && yy >= y && yy <= y + 3;
				if (gate) continue;
				w.set(x + dx, yy, z, (yy == y + 5 && Math.abs(dx) <= 3) ? chiseled : bricks);
			}
			w.footingOrPier(x + dx, z, y - 2, LADDER, 12, 64, o);
		}
		w.set(x, y + 6, z, gold); // keystone medallion
		w.set(x, y + 4, z + 1, Blocks.IRON_CHAIN.defaultBlockState());
		w.set(x, y + 3, z + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		// two flanking towers, 5x5, to y+14, crenellated
		for (int side = -1; side <= 1; side += 2) {
			int cx = x + side * 7;
			for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				for (int yy = y - 1; yy <= y + 13; yy++) {
					if (edge) w.set(cx + dx, yy, z + dz, (yy - y) % 5 == 4 ? chiseled : bricks);
					else w.set(cx + dx, yy, z + dz, yy == y - 1 || yy == y + 13 ? bricks : Blocks.AIR.defaultBlockState());
				}
				if (edge && (dx + dz) % 2 == 0) w.set(cx + dx, y + 14, z + dz, wall);
				w.footingOrPier(cx + dx, z + dz, y - 2, LADDER, 12, 64, o);
			}
			// arrow slits towards the terrace
			for (int yy = y + 6; yy <= y + 8; yy++) w.set(cx, yy, z + 2, Blocks.IRON_BARS.defaultBlockState());
			w.set(cx, y + 12, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
	}

	/** A railed terrace bridge out from the gate (z R+6..R+10), on the hall's own footing and piers. */
	private static void terrace(FitW w, BlockPos o, BlockState bricks, BlockState chiseled) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
		for (int dz = TERRACE_Z0; dz <= TERRACE_Z1; dz++) for (int dx = -TERRACE_W; dx <= TERRACE_W; dx++) {
			w.set(x + dx, y - 1, z + dz, (dx + dz) % 4 == 0 ? chiseled : bricks);
			for (int dy = 0; dy < 3; dy++) {
				BlockState st = w.get(x + dx, y + dy, z + dz);
				if (FitW.ground(st)) w.set(x + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
			}
			if (Math.abs(dx) == TERRACE_W || dz == TERRACE_Z1) w.set(x + dx, y, z + dz, Math.abs(dx) == TERRACE_W && dz == TERRACE_Z1 ? bricks : wall);
			// abutments: the two side walls (2 wide) and the far end wall go down to the ground; between them a vaulted
			// underside (2 deep at the walls, 1 in the middle), filled to the ground only where the ground is close
			boolean leg = Math.abs(dx) >= TERRACE_W - 1 || dz == TERRACE_Z1;
			if (leg) w.footing(x + dx, z + dz, y - 2, LADDER, 96);
			else {
				int depth = Math.abs(dx) == 2 ? 2 : 1;
				for (int k = 1; k <= depth; k++) w.set(x + dx, y - 1 - k, z + dz, k == depth && Math.abs(dx) == 2 ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
					.setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, dx < 0 ? Direction.WEST : Direction.EAST) : bricks);
				w.footingIfNear(x + dx, z + dz, y - 2 - depth, LADDER, 4);
			}
		}
		// stair flights down both sides of the terrace into the cavern (2 wide, 6 steps), each step on its own footing
		for (int side = -1; side <= 1; side += 2) for (int dz = R + 7; dz <= R + 8; dz++) {
			w.set(x + side * TERRACE_W, y, z + dz, Blocks.AIR.defaultBlockState());
			for (int k = 0; k < 6; k++) {
				int sx = x + side * (TERRACE_W + 1 + k), sy = y - 1 - k;
				w.set(sx, sy, z + dz, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST));
				for (int c = 1; c <= 3; c++) if (FitW.ground(w.get(sx, sy + c, z + dz))) w.set(sx, sy + c, z + dz, Blocks.AIR.defaultBlockState());
				w.footingOrPier(sx, z + dz, sy - 1, LADDER, 12, 64, o);
			}
		}
		w.set(x - TERRACE_W, y + 1, z + TERRACE_Z1, Blocks.LANTERN.defaultBlockState());
		w.set(x + TERRACE_W, y + 1, z + TERRACE_Z1, Blocks.LANTERN.defaultBlockState());
	}

	/** A stepped blackstone dome and eight buttresses, written only where the hall stands in the open (a buried hall keeps its rock). */
	private static void roof(FitW w, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		BlockState bs = Blocks.BLACKSTONE.defaultBlockState(), pb = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		double[] rr = {R + 0.5, R - 1.5, R - 4.5, R - 8.5};
		for (int k = 0; k < rr.length; k++) {
			int ir = (int) Math.ceil(rr[k]);
			for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++)
				if (dx * dx + dz * dz <= rr[k] * rr[k]) w.soft(x + dx, y + H + 1 + k, z + dz, k == rr.length - 1 ? pb : bs);
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			if (Math.sin(a) > 0.9) continue; // the façade side
			for (int k = 0; k <= 2; k++) {
				int bx = x + (int) Math.round(Math.cos(a) * (R + 1 + k)), bz = z + (int) Math.round(Math.sin(a) * (R + 1 + k));
				int top = y + 8 - 3 * k;
				for (int yy = y - 1; yy <= top; yy++) w.soft(bx, yy, bz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				w.footingOrPier(bx, bz, y - 2, LADDER, 12, 64, o);
			}
		}
	}

	@Override
	public BlockPos anchor(BlockPos o) {
		return o.offset(GruntharArena.THRONE);
	}

	@Override
	public int floorRadius() {
		return R;
	}
}
