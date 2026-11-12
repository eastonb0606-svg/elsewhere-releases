package dev.elsewhere.boss.mournmaw;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.Build;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Weeping Basin: a soul-sand bowl (radius 18) open to the cavern above, with a soul-water basin in the middle and
 * five bone arches for cover from the tear rain. Mournmaw weeps dormant above it.
 */
public final class MournmawArena implements ArenaBuilder {
	public static final MournmawArena INSTANCE = new MournmawArena();
	public static final int RADIUS = 18;

	@Override
	public void build(WorldGenLevel level, BlockPos o, RandomSource random) {
		int y = o.getY();
		BlockState air = Blocks.AIR.defaultBlockState();
		Build.disc(level, o, y - 2, RADIUS + 0.5, Blocks.SOUL_SOIL.defaultBlockState());
		Build.disc(level, o, y - 1, RADIUS + 0.5, Blocks.SOUL_SAND.defaultBlockState());
		for (int dy = 0; dy < 24; dy++) Build.disc(level, o, y + dy, RADIUS + 0.5, air);
		for (int i = 0; i < 60; i++) {
			int dx = random.nextInt(2 * RADIUS) - RADIUS, dz = random.nextInt(2 * RADIUS) - RADIUS;
			if (dx * dx + dz * dz < RADIUS * RADIUS) Build.set(level, o.getX() + dx, y - 1, o.getZ() + dz, Blocks.SOUL_SOIL.defaultBlockState());
		}
		// rim of basalt so the bowl reads as an arena
		Build.ring(level, o, y, RADIUS - 0.5, RADIUS + 0.5, Blocks.BASALT.defaultBlockState());
		// the basin: soul-soil rim, water inside (sunken one block), soul campfires at the edge
		Build.disc(level, o, y - 2, 3.5, Blocks.SOUL_SOIL.defaultBlockState());
		Build.disc(level, o, y - 1, 3.2, Blocks.WATER.defaultBlockState());
		Build.ring(level, o, y - 1, 3.2, 4.2, Blocks.POLISHED_BASALT.defaultBlockState());
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			Build.set(level, o.getX() + (int) Math.round(Math.cos(a) * 4.5), y, o.getZ() + (int) Math.round(Math.sin(a) * 4.5), Blocks.SOUL_CAMPFIRE.defaultBlockState());
		}
		// five bone arches (cover)
		BlockState boneUp = Blocks.BONE_BLOCK.defaultBlockState();
		for (int i = 0; i < 5; i++) {
			double a = i * Math.PI * 2 / 5;
			int cx = o.getX() + (int) Math.round(Math.cos(a) * 11), cz = o.getZ() + (int) Math.round(Math.sin(a) * 11);
			boolean alongX = Math.abs(Math.sin(a)) > 0.7;
			Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
			for (int s = -2; s <= 2; s += 4) {
				int lx = cx + (alongX ? s : 0), lz = cz + (alongX ? 0 : s);
				for (int dy = 0; dy < 5; dy++) Build.set(level, lx, y + dy, lz, boneUp);
			}
			for (int s = -2; s <= 2; s++) {
				Build.set(level, cx + (alongX ? s : 0), y + 5, cz + (alongX ? 0 : s), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
				for (int w = -1; w <= 1; w++) {
					Build.set(level, cx + (alongX ? s : w), y + 5, cz + (alongX ? w : s), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
				}
			}
		}
	}

	@Override
	public @Nullable BlockPos findOrigin(WorldGenLevel level, BlockPos center, RandomSource random) {
		return null; // world generation disabled for the beta; the dev command builds it
	}

	@Override
	public BlockPos anchor(BlockPos origin) {
		return origin;
	}
}
