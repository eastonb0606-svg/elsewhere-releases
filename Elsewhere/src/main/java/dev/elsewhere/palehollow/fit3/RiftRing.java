package dev.elsewhere.palehollow.fit3;

import dev.elsewhere.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Return Rift ruin, 3.0 look (STRUCTURES.md 4, core features): a 5x5 tuff dais underpinned to the ground, and a ring of eight standing stones,
 * each on its own ground, some tilted (a stair cap), some slipped outward, one fallen. No 9x9x5 air box: plants are removed above the dais and
 * natural blocks at most 2 above it on a slope. Feature placement: everything stays within 7 blocks of the anchor, inside the chunk being
 * decorated and its direct neighbours.
 */
public final class RiftRing {
	private RiftRing() {}

	public static final int RADIUS = 5;

	static boolean open(BlockState s) {
		return W.open(s);
	}

	public static void build(WorldGenLevel level, BlockPos c, RandomSource random) {
		BlockState bricks = Blocks.TUFF_BRICKS.defaultBlockState(), polished = Blocks.POLISHED_TUFF.defaultBlockState(),
			chiseled = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			int d = Math.max(Math.abs(dx), Math.abs(dz));
			if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && random.nextInt(2) == 0) continue;
			BlockPos p = c.offset(dx, -1, dz);
			level.setBlock(p, d <= 1 ? chiseled : random.nextInt(4) == 0 ? polished : bricks, Block.UPDATE_CLIENTS);
			for (int i = 1; i <= CoreFit.UNDERPIN_CAP; i++) {
				BlockPos q = p.below(i);
				if (!open(level.getBlockState(q))) break;
				level.setBlock(q, i <= 1 ? bricks : Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			for (int dy = 0; dy <= 3; dy++) {
				BlockPos q = c.offset(dx, dy, dz);
				BlockState s = level.getBlockState(q);
				if (s.isAir() || !s.getFluidState().isEmpty()) continue;
				if (W.soft(s) || dy <= 2) level.setBlock(q, air, Block.UPDATE_CLIENTS);
			}
		}
		double phase = random.nextDouble() * Math.PI / 4;
		int fallen = random.nextInt(8);
		for (int k = 0; k < 8; k++) {
			double a = phase + k * Math.PI / 4;
			int ox = (int) Math.round(Math.cos(a) * RADIUS), oz = (int) Math.round(Math.sin(a) * RADIUS);
			int x = c.getX() + ox, z = c.getZ() + oz;
			int g = W.ground(level, x, z);
			if (g == Integer.MIN_VALUE || g < c.getY() - 8 || g > c.getY() + 6) continue;
			Direction out = Direction.getApproximateNearest((float) ox, 0F, (float) oz);
			if (k == fallen) {
				// a fallen stone lying outward on its own ground, its broken end a stair
				for (int i = 0; i < 3; i++) {
					int fx = x + out.getStepX() * i, fz = z + out.getStepZ() * i;
					int fg = W.ground(level, fx, fz);
					if (fg == Integer.MIN_VALUE || Math.abs(fg - g) > 2) break;
					BlockState s = i == 2 ? Blocks.TUFF_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, out.getOpposite()) : i == 1 ? polished : bricks;
					level.setBlock(new BlockPos(fx, fg + 1, fz), s, Block.UPDATE_CLIENTS);
				}
				continue;
			}
			int h = 2 + random.nextInt(3);
			boolean slip = random.nextInt(4) == 0;
			for (int i = 1; i <= h; i++) {
				int sx = x, sz = z;
				if (slip && i == h) {
					sx += out.getStepX();
					sz += out.getStepZ();
				}
				BlockState s = i == h ? (random.nextBoolean()
					? Blocks.TUFF_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, random.nextBoolean() ? out : out.getOpposite()).setValue(StairBlock.HALF, random.nextBoolean() ? Half.TOP : Half.BOTTOM)
					: chiseled) : i == 1 ? Blocks.TUFF_BRICKS.defaultBlockState() : random.nextInt(3) == 0 ? polished : bricks;
				BlockPos q = new BlockPos(sx, g + i, sz);
				if (slip && i == h) {
					// a slipped cap rests on the ground or on a brace below it, never in the air
					for (int j = 1; j <= CoreFit.UNDERPIN_CAP && open(level.getBlockState(q.below(j))); j++) level.setBlock(q.below(j), bricks, Block.UPDATE_CLIENTS);
				}
				level.setBlock(q, s, Block.UPDATE_CLIENTS);
			}
		}
		level.setBlock(c, ModBlocks.RETURN_RIFT.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(c.above(), ModBlocks.RETURN_RIFT.defaultBlockState(), Block.UPDATE_CLIENTS);
	}
}
