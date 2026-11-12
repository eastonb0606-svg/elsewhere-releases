package dev.elsewhere.boss.grunthar;

import dev.elsewhere.boss.solaxis.SolaxisArena;
import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.Build;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Treasure Hall: a round bastion-style hall (radius 13, 12 high) of blackstone with four lavafalls behind bars,
 * a gold hoard filling the north half and the Gilded Throne on a dais. Place a gold block on the throne to summon.
 */
public final class GruntharArena implements ArenaBuilder {
	public static final GruntharArena INSTANCE = new GruntharArena();
	public static final int RADIUS = 13;
	public static final int HEIGHT = 12;
	/** Throne offset from the origin (floor centre). */
	public static final BlockPos THRONE = new BlockPos(0, 2, -9);

	public static BossArena arena(BlockPos origin) {
		return new BossArena(origin, RADIUS, origin.getY() - 1, origin.getY() + HEIGHT, false, Vec3.atBottomCenterOf(origin.south(8)));
	}

	@Override
	public void build(WorldGenLevel level, BlockPos o, RandomSource random) {
		BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int y = o.getY();
		Build.disc(level, o, y - 2, RADIUS + 0.5, Blocks.BLACKSTONE.defaultBlockState());
		Build.disc(level, o, y - 1, RADIUS + 0.5, bricks);
		for (int dy = 0; dy < HEIGHT; dy++) {
			Build.disc(level, o, y + dy, RADIUS - 0.5, air);
			Build.ring(level, o, y + dy, RADIUS - 0.5, RADIUS + 0.5, random.nextInt(5) == 0 ? cracked : bricks);
		}
		Build.disc(level, o, y + HEIGHT, RADIUS + 0.5, Blocks.BLACKSTONE.defaultBlockState());
		Build.ring(level, o, y - 1, 5.5, 6.5, gilded);
		Build.ring(level, o, y - 1, 2.0, 2.6, Blocks.GOLD_BLOCK.defaultBlockState());
		// basalt pillars and gilded bands
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int px = o.getX() + (int) Math.round(Math.cos(a) * (RADIUS - 1)), pz = o.getZ() + (int) Math.round(Math.sin(a) * (RADIUS - 1));
			for (int dy = 0; dy < HEIGHT; dy++) Build.set(level, px, y + dy, pz, dy % 4 == 3 ? gilded : Blocks.POLISHED_BASALT.defaultBlockState());
		}
		// hanging lanterns
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			BlockPos l = new BlockPos(o.getX() + (int) Math.round(Math.cos(a) * 7), y + HEIGHT - 1, o.getZ() + (int) Math.round(Math.sin(a) * 7));
			Build.set(level, l, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// four lavafalls behind iron bars on the diagonals
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			int lx = o.getX() + (int) Math.round(Math.cos(a) * RADIUS), lz = o.getZ() + (int) Math.round(Math.sin(a) * RADIUS);
			int ix = o.getX() + (int) Math.round(Math.cos(a) * (RADIUS - 1)), iz = o.getZ() + (int) Math.round(Math.sin(a) * (RADIUS - 1));
			int bx = o.getX() + (int) Math.round(Math.cos(a) * (RADIUS + 1)), bz = o.getZ() + (int) Math.round(Math.sin(a) * (RADIUS + 1));
			for (int dy = 0; dy < HEIGHT; dy++) {
				Build.set(level, bx, y + dy, bz, bricks);
				Build.set(level, lx, y + dy, lz, Blocks.LAVA.defaultBlockState());
				Build.set(level, ix, y + dy, iz, dy < HEIGHT - 1 ? Blocks.IRON_BARS.defaultBlockState() : bricks);
			}
		}
		// the hoard: north half, piled higher towards the wall
		for (int dz = -RADIUS + 1; dz <= -4; dz++) {
			for (int dx = -RADIUS + 1; dx <= RADIUS - 1; dx++) {
				if (dx * dx + dz * dz > (RADIUS - 1.5) * (RADIUS - 1.5)) continue;
				if (Math.abs(dx) <= 2 && dz >= -8) continue; // path to the throne
				int h = (int) Math.round((-dz - 4) / 3.0 + random.nextDouble() * 1.2);
				for (int k = 0; k < h; k++) {
					double r = random.nextDouble();
					BlockState s = r < 0.45 ? Blocks.GOLD_BLOCK.defaultBlockState() : r < 0.7 ? Blocks.RAW_GOLD_BLOCK.defaultBlockState() : r < 0.9 ? gilded : Blocks.GOLD_BLOCK.defaultBlockState();
					Build.set(level, o.getX() + dx, y + k, o.getZ() + dz, s);
				}
			}
		}
		// dais and throne
		Build.box(level, o.offset(-2, 0, -11), o.offset(2, 1, -7), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		Build.box(level, o.offset(-1, 0, -6), o.offset(1, 0, -6), Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
		Build.box(level, o.offset(-1, 2, -10), o.offset(1, 4, -10), Blocks.GOLD_BLOCK.defaultBlockState());
		Build.set(level, o.offset(0, 5, -10), gilded);
		Build.set(level, o.offset(-1, 2, -9), Blocks.GOLD_BLOCK.defaultBlockState());
		Build.set(level, o.offset(1, 2, -9), Blocks.GOLD_BLOCK.defaultBlockState());
		Build.set(level, o.offset(THRONE.getX(), THRONE.getY(), THRONE.getZ()), GruntharContent.GILDED_THRONE.defaultBlockState());
		// entrance (south) with a short corridor
		for (int r = RADIUS - 1; r <= RADIUS + 5; r++) {
			int cz = o.getZ() + r;
			for (int dx = -2; dx <= 2; dx++) {
				Build.set(level, o.getX() + dx, y - 1, cz, bricks);
				Build.set(level, o.getX() + dx, y + 4, cz, bricks);
				for (int dy = 0; dy < 4; dy++) Build.set(level, o.getX() + dx, y + dy, cz, Math.abs(dx) == 2 ? gilded : air);
			}
		}
	}

	@Override
	public @Nullable BlockPos findOrigin(WorldGenLevel level, BlockPos center, RandomSource random) {
		return SolaxisArena.INSTANCE.findOrigin(level, center, random);
	}

	@Override
	public BlockPos anchor(BlockPos origin) {
		return origin.offset(THRONE);
	}
}
