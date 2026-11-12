package dev.elsewhere.petalwind_glasswaste;

import java.util.Optional;
import dev.elsewhere.petalwind.PetalwindItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Breeze Temple (Petalwind): a small tuff-brick temple with two vanilla vaults that open with a Zephyr Key
 * (dropped by Blossom Breezes), once per player each. Trial-chamber styling; built from its centre chunk like the boss sites.
 */
public final class BreezeTemple {
	private BreezeTemple() {}

	public static final int RADIUS = 7;
	/** Vault positions relative to the build origin (the surface block). */
	public static final int[][] VAULTS = {{-2, 1, -6}, {2, 1, -6}};

	public static ResourceKey<LootTable> loot() {
		return ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/breeze_temple_vault"));
	}

	public static void vault(WorldGenLevel level, BlockPos p, Direction facing, Item key, ResourceKey<LootTable> loot) {
		level.setBlock(p, Blocks.VAULT.defaultBlockState().setValue(VaultBlock.FACING, facing), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(p) instanceof VaultBlockEntity v) v.setConfig(new VaultConfig(loot, 4.0, 4.5, new ItemStack(key), Optional.empty()));
	}

	public static void build(WorldGenLevel level, BlockPos base) {
		BlockState brick = Blocks.TUFF_BRICKS.defaultBlockState(), polished = Blocks.POLISHED_TUFF.defaultBlockState(), chiseled = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState(), lamp = Blocks.SEA_LANTERN.defaultBlockState(), slab = Blocks.TUFF_BRICK_SLAB.defaultBlockState();
		for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
			for (int y = -1; y >= -8; y--) { // foundation down to the ground
				BlockPos q = base.offset(x, y, z);
				if (!level.getBlockState(q).isAir() && !level.getBlockState(q).canBeReplaced()) break;
				level.setBlock(q, brick, Block.UPDATE_CLIENTS);
			}
			boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
			boolean corner = Math.abs(x) == RADIUS && Math.abs(z) == RADIUS;
			level.setBlock(base.offset(x, 0, z), (x + z) % 2 == 0 ? polished : brick, Block.UPDATE_CLIENTS);
			for (int y = 1; y <= 4; y++) {
				boolean door = z == RADIUS && Math.abs(x) <= 1 && y <= 3;
				level.setBlock(base.offset(x, y, z), corner ? chiseled : edge && !door ? (y == 3 && (x + z) % 3 == 0 ? Blocks.TUFF_BRICK_WALL.defaultBlockState() : brick) : air, Block.UPDATE_CLIENTS);
			}
			level.setBlock(base.offset(x, 5, z), slab, Block.UPDATE_CLIENTS);
		}
		for (int[] l : new int[][] {{-5, 4, -5}, {5, 4, -5}, {-5, 4, 5}, {5, 4, 5}, {0, 4, -6}}) level.setBlock(base.offset(l[0], l[1], l[2]), lamp, Block.UPDATE_CLIENTS);
		for (int[] v : VAULTS) vault(level, base.offset(v[0], v[1], v[2]), Direction.SOUTH, PetalwindItems.ZEPHYR_KEY, loot());
		// a plinth of chiseled tuff between the vaults, steps at the door
		level.setBlock(base.offset(0, 1, -6), chiseled, Block.UPDATE_CLIENTS);
	}
}
