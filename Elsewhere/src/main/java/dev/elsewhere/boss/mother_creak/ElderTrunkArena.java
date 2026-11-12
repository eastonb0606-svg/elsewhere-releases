package dev.elsewhere.boss.mother_creak;

import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Elder Trunk: a hollow heartwood column sunk into the ground, a spiral root ramp down to the heart chamber,
 * four resin hearts set in the walls, Mother Creak asleep at the bottom. She wakes when a player reaches the floor.
 */
public final class ElderTrunkArena {
	public static final int RADIUS = 13;
	public static final int DEPTH = 12;
	public static final int RIM = 6;

	private ElderTrunkArena() {
	}

	/** Builds the arena with its rim at {@code top} and spawns a dormant Mother Creak. Returns her. */
	public static @Nullable MotherCreak buildAndSpawn(final ServerLevel level, final BlockPos top, final @Nullable Player player) {
		BlockPos floor = top.below(DEPTH);
		List<BlockPos> hearts = build(level, floor, level.getRandom());
		MotherCreak boss = ModEntities.MOTHER_CREAK.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) {
			return null;
		}
		boss.snapTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, 180.0F, 0.0F);
		boss.setArena(floor, RADIUS, 1, DEPTH + RIM);
		boss.setHearts(hearts);
		level.addFreshEntity(boss);
		if (player instanceof ServerPlayer sp) {
			// stand on the start of the ramp, at the rim
			BlockPos start = rampPos(floor, 0);
			sp.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
		}
		return boss;
	}

	static BlockPos rampPos(final BlockPos floor, final int step) {
		// ramp goes from the rim (step 0) down to the floor (step DEPTH * 3)
		double a = step * (Math.PI * 2.0 * 1.3) / (DEPTH * 3);
		double r = RADIUS - 1.5;
		int y = floor.getY() + DEPTH - step / 3;
		return new BlockPos(floor.getX() + (int)Math.round(Math.cos(a) * r), y, floor.getZ() + (int)Math.round(Math.sin(a) * r));
	}

	/** Builds the trunk around {@code floor} (centre of the chamber floor). Returns the four heart positions. */
	public static List<BlockPos> build(final ServerLevel level, final BlockPos floor, final RandomSource random) {
		BlockState bark = Blocks.PALE_OAK_WOOD.defaultBlockState();
		BlockState heart = ModBlocks.HEARTWOOD_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		int outer = RADIUS + 2;
		for (int dx = -outer - 1; dx <= outer + 1; dx++) {
			for (int dz = -outer - 1; dz <= outer + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > outer + 1.0) {
					continue;
				}
				for (int y = -3; y <= DEPTH + RIM; y++) {
					BlockPos p = floor.offset(dx, y, dz);
					BlockState s;
					if (d > RADIUS + 0.5) {
						// the trunk wall, ragged at the top
						int wallTop = DEPTH + RIM - (int)((Math.sin(Math.atan2(dz, dx) * 7.0) + 1.0) * 1.5);
						s = y <= wallTop ? ((dx * 3 + dz * 5 + y) % 9 == 0 ? heart : bark) : Blocks.AIR.defaultBlockState();
						if (d > outer + 0.5 && y > DEPTH) {
							s = Blocks.AIR.defaultBlockState();
						}
					} else if (y < 0) {
						s = Blocks.MUD.defaultBlockState();
					} else if (y == 0) {
						s = Blocks.AIR.defaultBlockState();
					} else {
						s = Blocks.AIR.defaultBlockState();
					}
					level.setBlock(p, s, 2);
				}
				if (d <= RADIUS + 0.5) {
					BlockPos f = floor.offset(dx, -1, dz);
					int pick = random.nextInt(10);
					level.setBlock(f, pick < 4 ? Blocks.PALE_MOSS_BLOCK.defaultBlockState()
						: pick < 6 ? Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState() : Blocks.MUD.defaultBlockState(), 2);
					if (pick == 0) {
						level.setBlock(floor.offset(dx, 0, dz), Blocks.PALE_MOSS_CARPET.defaultBlockState(), 2);
					}
				}
			}
		}
		// spiral resin-root ramp, two blocks wide
		for (int step = 0; step <= DEPTH * 3; step++) {
			BlockPos p = rampPos(floor, step);
			for (BlockPos q : new BlockPos[] {p, p.offset(1, 0, 0), p.offset(0, 0, 1), p.offset(-1, 0, 0), p.offset(0, 0, -1)}) {
				double d = Math.sqrt(Math.pow(q.getX() - floor.getX(), 2) + Math.pow(q.getZ() - floor.getZ(), 2));
				if (d >= RADIUS - 3.0 && d <= RADIUS + 0.5) {
					level.setBlock(q.below(), step % 7 == 0 ? Blocks.RESIN_BRICKS.defaultBlockState() : Blocks.PALE_OAK_PLANKS.defaultBlockState(), 2);
				}
			}
		}
		// hanging moss and resin from the rim
		for (int i = 0; i < 40; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			BlockPos p = floor.offset((int)Math.round(Math.cos(a) * RADIUS), DEPTH + 1 - random.nextInt(4), (int)Math.round(Math.sin(a) * RADIUS));
			if (level.getBlockState(p).isAir() && !level.getBlockState(p.above()).isAir()) {
				level.setBlock(p, Blocks.PALE_HANGING_MOSS.defaultBlockState(), 2);
			}
		}
		// four hearts set in the wall at the cardinal points, three blocks up
		List<BlockPos> hearts = new ArrayList<>();
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] dir : dirs) {
			BlockPos h = floor.offset(dir[0] * (RADIUS + 1), 3, dir[1] * (RADIUS + 1));
			level.setBlock(h, ModBlocks.RESIN_HEART.defaultBlockState(), 3);
			level.setBlock(h.above(), Blocks.RESIN_BLOCK.defaultBlockState(), 2);
			level.setBlock(h.below(), Blocks.RESIN_BLOCK.defaultBlockState(), 2);
			hearts.add(h);
		}
		// a few glowing resin knots for light
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6.0 + 0.2;
			BlockPos p = floor.offset((int)Math.round(Math.cos(a) * (RADIUS + 1)), 6 + (i % 3) * 2, (int)Math.round(Math.sin(a) * (RADIUS + 1)));
			level.setBlock(p, Blocks.OCHRE_FROGLIGHT.defaultBlockState(), 2);
		}
		return hearts;
	}
}
