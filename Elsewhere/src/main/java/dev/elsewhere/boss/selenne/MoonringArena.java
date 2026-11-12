package dev.elsewhere.boss.selenne;

import dev.elsewhere.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Moonring: an eyeblossom meadow inside 12 tuff monoliths that block line of sight, each holding a water-filled
 * cauldron on its inner face (the phase 3 "reflections"). One closed Moon Bloom grows at the centre.
 */
public final class MoonringArena {
	public static final int RADIUS = 15;
	public static final int MONOLITH_RING = 12;
	public static final int MONOLITHS = 12;

	private MoonringArena() {
	}

	public static BlockPos monolithBase(final BlockPos center, final int i) {
		double a = i * Math.PI * 2.0 / MONOLITHS;
		return center.offset((int)Math.round(Math.cos(a) * MONOLITH_RING), 0, (int)Math.round(Math.sin(a) * MONOLITH_RING));
	}

	/** The cauldron of monolith {@code i} sits on its side facing the centre. */
	public static BlockPos cauldronPos(final BlockPos center, final int i) {
		double a = i * Math.PI * 2.0 / MONOLITHS;
		return center.offset((int)Math.round(Math.cos(a) * (MONOLITH_RING - 2)), 0, (int)Math.round(Math.sin(a) * (MONOLITH_RING - 2)));
	}

	public static List<BlockPos> cauldrons(final BlockPos center) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < MONOLITHS; i++) {
			out.add(cauldronPos(center, i));
		}
		return out;
	}

	/** Builds the ring around {@code center} (ground level) and plants the Moon Bloom there. */
	public static void build(final ServerLevel level, final BlockPos center) {
		RandomSource random = level.getRandom();
		int r = RADIUS + 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				BlockPos g = center.offset(dx, -1, dz);
				for (int dy = -3; dy < -1; dy++) {
					level.setBlock(center.offset(dx, dy, dz), Blocks.DIRT.defaultBlockState(), 2);
				}
				level.setBlock(g, random.nextInt(5) == 0 ? ModBlocks.ASHEN_MOSS.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				for (int dy = 0; dy < 24; dy++) {
					level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
				}
				int pick = random.nextInt(14);
				if (dx * dx + dz * dz > 9 && pick < 3) {
					level.setBlock(center.offset(dx, 0, dz), pick == 0 ? Blocks.CLOSED_EYEBLOSSOM.defaultBlockState() : Blocks.OPEN_EYEBLOSSOM.defaultBlockState(), 2);
				} else if (pick == 3) {
					level.setBlock(center.offset(dx, 0, dz), Blocks.SHORT_GRASS.defaultBlockState(), 2);
				}
			}
		}
		BlockState tuff = Blocks.TUFF.defaultBlockState();
		BlockState polished = Blocks.POLISHED_TUFF.defaultBlockState();
		BlockState cap = ModBlocks.MOONRING_STONE.defaultBlockState();
		for (int i = 0; i < MONOLITHS; i++) {
			BlockPos base = monolithBase(center, i);
			int h = 7 + random.nextInt(3);
			for (int ox = 0; ox <= 1; ox++) {
				for (int oz = 0; oz <= 1; oz++) {
					for (int y = -1; y < h; y++) {
						BlockState s = y == h - 1 ? cap : (y % 3 == 1 ? polished : tuff);
						level.setBlock(base.offset(ox, y, oz), s, 2);
					}
				}
			}
			BlockPos c = cauldronPos(center, i);
			level.setBlock(c, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 2);
		}
		level.setBlock(center, ModBlocks.MOON_BLOOM.defaultBlockState(), 3);
	}
}
