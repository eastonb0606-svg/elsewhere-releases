package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.boss.solaxis.SolaxisArena;
import dev.elsewhere.boss.solaxis.SolaxisContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * v2 Pyre Cradle (STRUCTURES.md 4, "on a basalt mesa shelf, with flared basalt supports and arched bridges to land; reject wet
 * cores"). The chamber is unchanged inside (radius 12, 14 high, the core, four blaze spawners, lavafalls east and west), so the
 * fight is the same. Outside: the drum stands on a matching footing (nether brick, red nether brick, basalt rubble) with piers
 * past 12 deep, eight polished-basalt supports that flare out towards the ground, a corbelled cornice, and the two corridor
 * stubs became fortress bridges (railed, arched between legs) running out to z +-23, each leg down to the ground.
 */
public final class SolaxisCradle implements FitArena {
	static final int R = SolaxisArena.RADIUS, H = SolaxisArena.HEIGHT;
	static final FitW.Ladder LADDER = new FitW.Ladder(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(),
		Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState());
	/** Bridge legs (distance from the centre along z); the deck ends at 23. */
	static final int[] LEGS = {R + 5, R + 11};

	@Override
	public String id() {
		return "solaxis";
	}

	@Override
	public void build(FitW w, BlockPos o) {
		BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
		BlockState red = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int x = o.getX(), y = o.getY(), z = o.getZ();
		for (int dz = -R - 1; dz <= R + 1; dz++) for (int dx = -R - 1; dx <= R + 1; dx++)
			if (dx * dx + dz * dz <= (R + 1.5) * (R + 1.5)) w.footingOrPier(x + dx, z + dz, y - 3, LADDER, 12, 64, o);
		// shell (as 2.x)
		w.disc(o, y - 2, R + 0.5, brick);
		w.disc(o, y - 1, R + 0.5, brick);
		for (int dy = 0; dy < H; dy++) {
			w.disc(o, y + dy, R - 0.5, air);
			w.ring(o, y + dy, R - 0.5, R + 0.5, dy % 5 == 4 ? red : brick);
		}
		w.disc(o, y + H, R + 0.5, brick);
		w.ring(o, y - 1, 8.5, 9.5, red);
		w.ring(o, y - 1, 3.5, 4.2, Blocks.CHISELED_NETHER_BRICKS.defaultBlockState());
		w.ring(o, y - 1, 10.5, 11.5, Blocks.MAGMA_BLOCK.defaultBlockState());
		for (int dz = -10; dz <= 10; dz++) for (int dx = -10; dx <= 10; dx++)
			if (dx * dx + dz * dz < 80 && w.hash(x + dx, y - 1, z + dz) < 9) w.set(x + dx, y - 1, z + dz, Blocks.CRACKED_NETHER_BRICKS.defaultBlockState());
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			w.set(x + (int) Math.round(Math.cos(a) * 7), y + H, z + (int) Math.round(Math.sin(a) * 7), Blocks.SHROOMLIGHT.defaultBlockState());
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int wx = x + (int) Math.round(Math.cos(a) * R), wz = z + (int) Math.round(Math.sin(a) * R);
			for (int dy = 4; dy <= 6; dy++) w.set(wx, y + dy, wz, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
		}
		for (int side = -1; side <= 1; side += 2) {
			int lx = x + side * R;
			for (int dy = 0; dy < H; dy++) for (int dz = -1; dz <= 1; dz++) {
				w.set(lx + side, y + dy, z + dz, brick);
				w.set(lx, y + dy, z + dz, Blocks.LAVA.defaultBlockState());
				w.set(lx - side, y + dy, z + dz, dy < 12 ? Blocks.IRON_BARS.defaultBlockState() : brick);
			}
		}
		// doors north and south with the corridor stubs (as 2.x), then the bridges
		for (int side = -1; side <= 1; side += 2) {
			for (int r = R - 1; r <= R + 5; r++) {
				int cz = z + side * r;
				for (int dx = -2; dx <= 2; dx++) {
					w.set(x + dx, y - 1, cz, brick);
					w.set(x + dx, y + 4, cz, brick);
					for (int dy = 0; dy < 4; dy++) w.set(x + dx, y + dy, cz, Math.abs(dx) == 2 ? brick : air);
					w.footingOrPier(x + dx, cz, y - 2, LADDER, 12, 64, o);
				}
			}
			bridge(w, o, side);
		}
		for (int[] s : SolaxisArena.SPAWNERS) {
			for (int dy = 0; dy < s[1]; dy++) w.set(x + s[0], y + dy, z + s[2], red);
			BlockPos sp = o.offset(s[0], s[1], s[2]);
			w.set(sp, Blocks.SPAWNER.defaultBlockState());
			if (w.in(sp.getX(), sp.getY(), sp.getZ()) && w.level.getBlockEntity(sp) instanceof SpawnerBlockEntity spawner)
				spawner.setEntityId(EntityTypes.BLAZE, RandomSource.create(w.hash(sp.getX(), sp.getY(), sp.getZ())));
		}
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
			if (dx != 0 || dz != 0) w.set(x + dx, y - 1, z + dz, Blocks.MAGMA_BLOCK.defaultBlockState());
		w.set(o, SolaxisContent.PYRE_CRADLE_CORE.defaultBlockState());
		supports(w, o);
		cornice(w, o);
	}

	/** Eight polished-basalt supports around the drum that flare outwards towards the ground (soft: rock is kept). */
	private static void supports(FitW w, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		BlockState pillar = Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			for (int k = 0; k <= 2; k++) {
				int sx = x + (int) Math.round(Math.cos(a) * (R + 1 + k)), sz = z + (int) Math.round(Math.sin(a) * (R + 1 + k));
				int top = y + 6 - 4 * k; // flare: the outer layers start lower
				for (int yy = y - 2; yy <= top; yy++) w.soft(sx, yy, sz, pillar);
				w.footingOrPier(sx, sz, y - 3, LADDER, 12, 64, o);
			}
		}
	}

	/** A corbelled cornice of upside-down stairs under a red nether brick crown, only where the drum is in the open. */
	private static void cornice(FitW w, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		int ir = R + 1;
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < R + 0.5 || d > R + 1.5) continue;
			Direction face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
			w.soft(x + dx, y + H - 1, z + dz, Blocks.NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face).setValue(StairBlock.HALF, Half.TOP));
			w.soft(x + dx, y + H, z + dz, Blocks.RED_NETHER_BRICKS.defaultBlockState());
			if (((dx + dz) & 1) == 0) w.soft(x + dx, y + H + 1, z + dz, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
		}
	}

	/** A railed nether-brick bridge from the corridor stub (R+6) to 23 blocks out, arched between its two legs. */
	private static void bridge(FitW w, BlockPos o, int side) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
		for (int r = R + 6; r <= R + 11; r++) {
			int cz = z + side * r;
			// arch: deepest at the legs, one block under the deck at mid-span
			int toLeg = Math.min(Math.abs(r - LEGS[0]), Math.abs(r - LEGS[1]));
			boolean leg = toLeg == 0;
			int depth = leg ? 0 : Math.max(1, 4 - toLeg);
			for (int dx = -2; dx <= 2; dx++) {
				w.set(x + dx, y - 1, cz, (dx + r) % 3 == 0 ? Blocks.RED_NETHER_BRICKS.defaultBlockState() : brick);
				for (int dy = 0; dy < 3; dy++) if (FitW.ground(w.get(x + dx, y + dy, cz))) w.set(x + dx, y + dy, cz, Blocks.AIR.defaultBlockState());
				if (Math.abs(dx) == 2) w.set(x + dx, y, cz, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
				if (leg) w.footingOrPier(x + dx, cz, y - 2, LADDER, 64, 64, o);
				else {
					boolean nearInner = Math.abs(r - LEGS[0]) < Math.abs(r - LEGS[1]);
					Direction toLegDir = (side > 0) == nearInner ? Direction.NORTH : Direction.SOUTH;
					for (int k = 1; k <= depth; k++) w.set(x + dx, y - 1 - k, cz, k == depth ? Blocks.NETHER_BRICK_STAIRS.defaultBlockState()
						.setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, toLegDir) : brick);
					// where land rises to just under the arch, the arch is filled down to it (the bridge lands)
					w.footingIfNear(x + dx, cz, y - 2 - depth, LADDER, 3);
				}
			}
		}
		// lantern posts on the far leg
		for (int dx = -2; dx <= 2; dx += 4) w.set(x + dx, y + 1, z + side * (R + 11), Blocks.LANTERN.defaultBlockState());
	}

	@Override
	public BlockPos anchor(BlockPos o) {
		return o;
	}

	@Override
	public int floorRadius() {
		return R;
	}
}
