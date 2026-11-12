package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.boss.mournmaw.MournmawArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v2 Weeping Basin (STRUCTURES.md 4, "set into the valley floor (concave), with basalt pillars to the floor"). The fight space is
 * the same radius-18 bowl with the soul-water basin and five bone arches for cover. Now the floor is a real bowl (it rises 2
 * towards the rim and blends into the valley with a soul-soil berm), the open space above is a dome shaped to the cavern
 * instead of a 24-high cylinder cut, the rim is a ring of standing basalt pillars on their own footings, and the basin is lit
 * by soul lanterns on basalt posts (no lit campfires).
 */
public final class MournmawBasin implements FitArena {
	static final int R = MournmawArena.RADIUS;
	static final FitW.Ladder LADDER = new FitW.Ladder(Blocks.SOUL_SOIL.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(),
		Blocks.SOUL_SOIL.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.BASALT.defaultBlockState());

	@Override
	public String id() {
		return "mournmaw";
	}

	/** Bowl floor top (the standing block) at horizontal distance d. */
	static int floorAt(int y, double d) {
		return y - 1 + (int) Math.round(2.0 * Math.min(1.0, (d / R) * (d / R)));
	}

	@Override
	public void build(FitW w, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		BlockState air = Blocks.AIR.defaultBlockState();
		int ir = R + 3;
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
			double d = Math.sqrt(dx * dx + dz * dz);
			int cx = x + dx, cz = z + dz;
			if (d <= R + 0.5) {
				int f = floorAt(y, d);
				w.footingOrPier(cx, cz, y - 3, LADDER, 12, 64, o);
				for (int yy = y - 2; yy < f; yy++) w.set(cx, yy, cz, Blocks.SOUL_SOIL.defaultBlockState());
				w.set(cx, f, cz, w.hash(cx, f, cz) < 18 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.SOUL_SAND.defaultBlockState());
				// the dome: open space shaped to the cavern, 24 over the basin, about 9 at the rim
				int top = y + 8 + (int) Math.round(16 * Math.sqrt(Math.max(0, 1 - (d / (R + 1)) * (d / (R + 1)))));
				for (int yy = f + 1; yy <= top; yy++) if (!w.get(cx, yy, cz).isAir()) w.set(cx, yy, cz, air);
			} else if (d <= R + 3) {
				// soul-soil berm that steps down from the rim into the valley floor (written only into open space)
				int top = floorAt(y, R) - (int) Math.round(d - R);
				for (int yy = top; yy >= y - 3; yy--) w.soft(cx, yy, cz, Blocks.SOUL_SOIL.defaultBlockState());
				w.footingOrPier(cx, cz, y - 4, LADDER, 12, 64, o);
			}
		}
		// the basin: soul-soil bed, enclosed water sunk one block, polished basalt lip
		w.disc(o, y - 2, 3.5, Blocks.SOUL_SOIL.defaultBlockState());
		w.disc(o, y - 1, 3.2, Blocks.WATER.defaultBlockState());
		w.ring(o, y - 1, 3.2, 4.2, Blocks.POLISHED_BASALT.defaultBlockState());
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			int px = x + (int) Math.round(Math.cos(a) * 4.5), pz = z + (int) Math.round(Math.sin(a) * 4.5);
			w.set(px, y, pz, Blocks.POLISHED_BASALT.defaultBlockState());
			w.set(px, y + 1, pz, Blocks.SOUL_LANTERN.defaultBlockState());
		}
		// five bone arches (cover), standing on the bowl
		for (int i = 0; i < 5; i++) {
			double a = i * Math.PI * 2 / 5;
			int cx = x + (int) Math.round(Math.cos(a) * 11), cz = z + (int) Math.round(Math.sin(a) * 11);
			boolean alongX = Math.abs(Math.sin(a)) > 0.7;
			Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
			int base = floorAt(y, 11) + 1;
			for (int s = -2; s <= 2; s += 4) {
				int lx = cx + (alongX ? s : 0), lz = cz + (alongX ? 0 : s);
				for (int dy = 0; dy < 5; dy++) w.set(lx, base + dy, lz, Blocks.BONE_BLOCK.defaultBlockState());
			}
			for (int s = -2; s <= 2; s++) for (int t = -1; t <= 1; t++)
				w.set(cx + (alongX ? s : t), base + 5, cz + (alongX ? t : s), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
		}
		// rim: 14 standing basalt pillars (4-9 high) at the bowl's edge, each on its own footing
		BlockState pillar = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int i = 0; i < 14; i++) {
			double a = i * Math.PI * 2 / 14 + 0.11;
			int px = x + (int) Math.round(Math.cos(a) * (R + 1)), pz = z + (int) Math.round(Math.sin(a) * (R + 1));
			int base = floorAt(y, R) + 1, h = 4 + w.hash(px, 0, pz) % 6;
			// the pillar stands from inside the berm (y - 2) up, so it is rooted in the floor, not set on top of it
			for (int yy = y - 2; yy < base + h; yy++) w.set(px, yy, pz, yy == base + h - 1 ? Blocks.POLISHED_BASALT.defaultBlockState() : pillar);
			w.footingOrPier(px, pz, y - 3, LADDER, 12, 64, o);
			if (i % 2 == 0) w.set(px, base + h, pz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		}
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
