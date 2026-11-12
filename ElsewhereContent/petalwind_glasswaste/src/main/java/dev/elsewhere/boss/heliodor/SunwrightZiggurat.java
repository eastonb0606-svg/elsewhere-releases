package dev.elsewhere.boss.heliodor;

import dev.elsewhere.glasswaste.GlasswasteBlocks;
import dev.elsewhere.glasswaste.SunMirrorBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Builds the Sunwright Ziggurat: five stepped tiers of Sunwright bricks, a summit with a glass lens ring and 4 sun mirrors. */
public final class SunwrightZiggurat {
	private SunwrightZiggurat() {}

	public static final int ARENA_RADIUS = 9;
	public static final int SUMMIT_Y = 15;

	public static List<BlockPos> mirrors(BlockPos summitCenter) {
		List<BlockPos> out = new ArrayList<>();
		out.add(summitCenter.offset(-6, 0, -6));
		out.add(summitCenter.offset(6, 0, -6));
		out.add(summitCenter.offset(6, 0, 6));
		out.add(summitCenter.offset(-6, 0, 6));
		return out;
	}

	/** Builds with the ground at {@code base}; returns the summit centre (standing position). */
	public static BlockPos build(WorldGenLevel level, BlockPos base) {
		BlockState brick = GlasswasteBlocks.SUNWRIGHT_BRICKS.defaultBlockState();
		BlockState gold = Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState(); // 3.0: no valuable blocks (gilt = yellow terracotta)
		int[] half = {15, 13, 11, 9, 7};
		int y = 0;
		for (int t = 0; t < half.length; t++) {
			int hw = half[t];
			for (int dy = 0; dy < 3; dy++) {
				for (int dx = -hw; dx <= hw; dx++) for (int dz = -hw; dz <= hw; dz++) {
					BlockState s = dy == 2 && (Math.abs(dx) == hw || Math.abs(dz) == hw) ? gold : brick;
					level.setBlock(base.offset(dx, y + dy, dz), s, 2);
				}
			}
			y += 3;
		}
		// stairs up the south face
		for (int i = 0; i < 15; i++) {
			for (int dx = -1; dx <= 1; dx++) {
				level.setBlock(base.offset(dx, i, 16 - i), GlasswasteBlocks.SUNWRIGHT_BRICK_STAIRS.defaultBlockState(), 2);
			}
		}
		BlockPos summit = base.above(SUMMIT_Y);
		for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) for (int dy = 0; dy < 8; dy++) {
			if (Math.abs(dx) <= 7 && Math.abs(dz) <= 7) level.setBlock(summit.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
		}
		// Rare Sunwright Upgrade template (BALANCE G9: Ziggurat chest): a chest in the summit's north-west corner.
		BlockPos chest = summit.offset(-6, 0, -6);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
		if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c) {
			c.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, dev.elsewhere.petalwind_glasswaste.Reg.id("chests/sunwright_ziggurat")), chest.asLong());
		}
		// glass lens ring (Glare!) and the central lens
		for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
			int d2 = dx * dx + dz * dz;
			if (d2 >= 9 && d2 <= 20) level.setBlock(summit.offset(dx, -1, dz), Blocks.GLASS.defaultBlockState(), 2);
		}
		level.setBlock(summit.below(), GlasswasteBlocks.CHISELED_SUNWRIGHT_BRICKS.defaultBlockState(), 2);
		for (BlockPos m : mirrors(summit)) {
			level.setBlock(m, GlasswasteBlocks.SUN_MIRROR.defaultBlockState().setValue(SunMirrorBlock.AIM, (m.getX() + m.getZ()) & 7), 2);
		}
		return summit;
	}
}
