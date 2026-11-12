package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.boss.aphelion.AphelionArena;
import dev.elsewhere.boss.aphelion.AphelionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v2 Shattered Halo (STRUCTURES.md 4, "the centre disc on real ground; halo fragments are small natural islands with tapered
 * undersides"). The fight layout is unchanged (spire, altar, four crystal pillars, seven islands at the same centres), so
 * Aphelion's island dives and void returns work as before. The centre disc now stands on an end-stone footing down to the
 * island it was sited on and blends out with a sloped apron; each halo fragment is an irregular island whose underside tapers
 * to a jagged point, some carrying a broken end-stone-brick column with an end rod. The fragments float by design.
 */
public final class AphelionHalo implements FitArena {
	static final BlockState STONE = Blocks.END_STONE.defaultBlockState();
	static final FitW.Ladder LADDER = new FitW.Ladder(STONE, STONE, STONE, STONE, Blocks.END_STONE_BRICKS.defaultBlockState());

	@Override
	public String id() {
		return "aphelion";
	}

	@Override
	public void build(FitW w, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// centre disc on real ground: footing under every column, a sloped apron out to 13.5
		for (int dz = -14; dz <= 14; dz++) for (int dx = -14; dx <= 14; dx++) {
			double d = Math.sqrt(dx * dx + dz * dz);
			int cx = x + dx, cz = z + dz;
			if (d <= 10.5) {
				w.set(cx, y - 1, cz, STONE);
				w.set(cx, y - 2, cz, STONE);
				w.footingOrPier(cx, cz, y - 3, LADDER, 24, 48, o);
			} else if (d <= 13.5) {
				int top = y - 1 - (int) Math.round((d - 10.5) * 1.5);
				if (FitW.ground(w.get(cx, top - 1, cz)) || reachesGround(w, cx, cz, top - 1, 24)) {
					for (int yy = top; yy >= top - 1; yy--) w.soft(cx, yy, cz, STONE);
					w.footing(cx, cz, top - 2, LADDER, 24);
				}
			}
		}
		w.ring(o, y - 1, 3.5, 4.5, Blocks.END_STONE_BRICKS.defaultBlockState());
		for (int dy = 0; dy < 14; dy++) {
			double r = dy < 10 ? 1.5 : 0.9;
			w.disc(o, y + dy, r, dy % 4 == 3 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
		}
		w.set(x, y + 14, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
		for (int[] c : AphelionArena.CRYSTALS) {
			for (int dy = 0; dy < 4; dy++) w.set(x + c[0], y + dy, z + c[1], Blocks.OBSIDIAN.defaultBlockState());
			w.set(x + c[0], y + 4, z + c[1], Blocks.BEDROCK.defaultBlockState());
		}
		for (int i = 0; i < AphelionArena.ISLANDS; i++) fragment(w, AphelionArena.islandCenter(o, i), 3.5 + (i % 2), i);
		w.set(o.offset(AphelionArena.ALTAR), AphelionContent.HALO_ALTAR.defaultBlockState());
	}

	/** Only an apron column whose ground is near gets the apron (no ledge over the void). Reads this column only. */
	private static boolean reachesGround(FitW w, int x, int z, int top, int max) {
		for (int k = 0; k <= max; k++) if (FitW.ground(w.get(x, top - k, z))) return true;
		return false;
	}

	/** A small natural island: ragged outline, flat top, underside tapering to a jagged point. Floats by design. */
	private static void fragment(FitW w, BlockPos top, double r, int i) {
		int ir = (int) Math.ceil(r + 0.6);
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
			int cx = top.getX() + dx, cz = top.getZ() + dz;
			double d = Math.sqrt(dx * dx + dz * dz);
			double edge = r + (w.hash(cx, 7, cz) - 50) / 85.0;
			if (d > edge) continue;
			int depth = 1 + (int) Math.round(Math.pow(1.0 - d / (edge + 0.01), 0.8) * (3 + r)) + (w.hash(cx, 3, cz) < 25 ? 1 : 0);
			for (int k = 1; k <= depth; k++) w.set(cx, top.getY() - k, cz, STONE);
		}
		// a broken column from the halo that once stood here, on alternate fragments
		if (i % 2 == 0) {
			int h = 2 + w.hash(top.getX(), 1, top.getZ()) % 3;
			int px = top.getX() + 1, pz = top.getZ() - 1;
			for (int dy = 0; dy < h; dy++) w.set(px, top.getY() + dy, pz, Blocks.END_STONE_BRICKS.defaultBlockState());
			w.set(px, top.getY() + h, pz, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
		} else {
			w.set(top.getX() - 1, top.getY(), top.getZ(), Blocks.END_STONE_BRICK_SLAB.defaultBlockState());
		}
	}

	@Override
	public BlockPos anchor(BlockPos o) {
		return o.offset(AphelionArena.ALTAR);
	}

	@Override
	public int floorRadius() {
		return 10;
	}
}
