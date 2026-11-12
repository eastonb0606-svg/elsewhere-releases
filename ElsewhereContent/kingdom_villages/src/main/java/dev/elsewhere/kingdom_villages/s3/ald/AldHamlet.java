package dev.elsewhere.kingdom_villages.s3.ald;

import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.VillagesContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The 3.0 refugee hamlet (STRUCTURES.md 4 "kingdom_villages hamlets: burnt farmsteads placed per column"): the burned shell of a
 * small farmhouse (charred corner posts and broken walls, ash on a scorched floor), a burned field of coarse dirt with dead bushes,
 * a broken fence pen with a fallen rail and the farm's composter (the refugees' job site). Every block stands on its own column's
 * ground (the old plan put everything on the centre's plane, floating or buried by the slope); nothing is cleared, only grass and
 * soft plants are replaced. The refugees appear on the spot the plan keeps free.
 */
public final class AldHamlet {
	private AldHamlet() {}

	public record Plan(List<VillageRecord.Op> ops, BoundingBox box, BlockPos refuge) {}

	/** A spot near {@code p} with a 9x9 of natural ground (spread at most 3, no water or trees), or null. */
	public static @Nullable BlockPos site(ServerLevel level, BlockPos p) {
		for (int d = 0; d <= 12; d += 3) for (int dx = -d; dx <= d; dx += 3) for (int dz = -d; dz <= d; dz += 3) {
			if (Math.abs(dx) != d && Math.abs(dz) != d) continue;
			int x = p.getX() + dx, z = p.getZ() + dz;
			if (fits(level, x, z)) return new BlockPos(x, AldLook.ground(level, x, z) + 1, z);
		}
		return null;
	}

	static boolean fits(ServerLevel level, int x, int z) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
			if (!AldLook.buildable(level, x + dx, z + dz, 3)) return false;
			int g = AldLook.ground(level, x + dx, z + dz);
			lo = Math.min(lo, g);
			hi = Math.max(hi, g);
		}
		return hi - lo <= 3;
	}

	/** The farmstead around {@code c} (the house's centre; any y, the ground is read per column). */
	public static Plan plan(ServerLevel level, BlockPos c, RandomSource rnd) {
		AldOps o = new AldOps();
		BlockState charred = VillagesContent.CHARRED_PLANKS.defaultBlockState(), ash = VillagesContent.ASH.defaultBlockState();
		int cx = c.getX(), cz = c.getZ();
		int cg = AldLook.ground(level, cx, cz);
		BlockPos refuge = new BlockPos(cx, cg == Integer.MIN_VALUE ? c.getY() : cg + 1, cz);
		// 1. the burned house: 5x5 shell, corner posts 2-3 high, broken walls, a scorched floor with ash (the refuge cells stay free)
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			int x = cx + dx, z = cz + dz;
			if (!AldLook.buildable(level, x, z, 3)) continue;
			int g = AldLook.ground(level, x, z);
			boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
			boolean refugeCell = dz == 0 && (dx == 0 || dx == 1);
			AldLook.clearPlantTop(level, o, x, g + 1, z);
			if (level.getBlockState(new BlockPos(x, g, z)).is(Blocks.GRASS_BLOCK)) o.set(x, g, z, Blocks.COARSE_DIRT.defaultBlockState());
			if (corner) {
				int h = 2 + rnd.nextInt(2);
				for (int y = 1; y <= h; y++) o.set(x, g + y, z, y == h && rnd.nextBoolean() ? charred : Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
			} else if (edge) {
				int r = rnd.nextInt(4);
				if (r == 0) {
					o.set(x, g + 1, z, charred);
					o.set(x, g + 2, z, charred);
				} else if (r == 1) o.set(x, g + 1, z, charred);
				else if (r == 2) o.set(x, g + 1, z, ash);
			} else if (!refugeCell && rnd.nextInt(3) != 0) o.set(x, g + 1, z, ash);
			if (!level.getBlockState(new BlockPos(x, g + 1, z)).isAir()) o.set(x, g + 1, z, Blocks.AIR.defaultBlockState()); // no grass grows on a burned floor
		}
		// a fallen roof beam across the shell
		boolean beamX = rnd.nextBoolean();
		for (int i = -1; i <= 1; i++) {
			int x = cx + (beamX ? i : -1), z = cz + (beamX ? -1 : i);
			if (!AldLook.buildable(level, x, z, 2)) continue;
			int g = AldLook.ground(level, x, z);
			BlockPos p = new BlockPos(x, g + 1, z);
			if (o.has(p)) o.map.put(p.asLong(), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, beamX ? Direction.Axis.X : Direction.Axis.Z));
			else o.set(p, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, beamX ? Direction.Axis.X : Direction.Axis.Z));
		}
		// 2. the burned field: coarse dirt rows with dead bushes, beside the house
		int side = rnd.nextBoolean() ? 1 : -1;
		for (int dx = -2; dx <= 2; dx++) for (int dz = 4; dz <= 7; dz++) {
			int x = cx + dx, z = cz + dz * side;
			if (!AldLook.buildable(level, x, z, 2)) continue;
			int g = AldLook.ground(level, x, z);
			BlockState gs = level.getBlockState(new BlockPos(x, g, z));
			if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT))) continue;
			AldLook.clearPlantTop(level, o, x, g + 1, z);
			o.set(x, g, z, (dx & 1) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PODZOL.defaultBlockState());
			if ((dx & 1) == 0 && AldLook.hash(x, g, z) < 30) o.set(x, g + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
		}
		// 3. the broken pen: a 5x5 fence ring on the other side, each post on its own ground, with gaps and a fallen rail
		for (int dx = -2; dx <= 2; dx++) for (int dz = 4; dz <= 8; dz++) {
			if (Math.abs(dx) != 2 && dz != 4 && dz != 8) continue;
			int x = cx + dx, z = cz - dz * side;
			if (AldLook.hash(x, 0, z) < 25 || !AldLook.buildable(level, x, z, 2)) continue;
			int g = AldLook.ground(level, x, z);
			AldLook.clearPlantTop(level, o, x, g + 1, z);
			o.set(x, g + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		int fx = cx + 3, fz = cz - 6 * side;
		if (AldLook.buildable(level, fx, fz, 1)) o.set(fx, AldLook.ground(level, fx, fz) + 1, fz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
		// 4. the composter by the door (the refugees' job site)
		int kx = cx + 3, kz = cz + 1;
		if (AldLook.buildable(level, kx, kz, 1)) {
			int g = AldLook.ground(level, kx, kz);
			AldLook.clearPlantTop(level, o, kx, g + 1, kz);
			o.set(kx, g + 1, kz, Blocks.COMPOSTER.defaultBlockState());
		}
		List<VillageRecord.Op> ops = o.ops(level);
		int minX = cx - 1, minY = refuge.getY() - 1, minZ = cz - 1, maxX = cx + 1, maxY = refuge.getY() + 2, maxZ = cz + 1;
		for (VillageRecord.Op op : ops) {
			BlockPos p = BlockPos.of(op.pos());
			minX = Math.min(minX, p.getX());
			minY = Math.min(minY, p.getY());
			minZ = Math.min(minZ, p.getZ());
			maxX = Math.max(maxX, p.getX());
			maxY = Math.max(maxY, p.getY());
			maxZ = Math.max(maxZ, p.getZ());
		}
		return new Plan(ops, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), refuge);
	}
}
