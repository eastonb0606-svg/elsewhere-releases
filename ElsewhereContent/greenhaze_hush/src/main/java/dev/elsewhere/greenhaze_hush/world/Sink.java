package dev.elsewhere.greenhaze_hush.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Where a blueprint writes blocks: straight into a live level (dev summon) or clipped to one chunk (worldgen). */
public interface Sink {
	void set(BlockPos pos, BlockState state);

	BlockState get(BlockPos pos);

	/** Called for things that only make sense in a live level (spawning entities, block entity data). */
	default ServerLevel live() {
		return null;
	}

	/** Give the container at pos a loot table (it rolls when first opened). */
	void loot(BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table, long seed);

	/** Spawn a persistent mob at pos (only if pos is in this sink's area). */
	<T extends net.minecraft.world.entity.Mob> T spawn(net.minecraft.world.entity.EntityType<T> type, BlockPos pos);

	default void set(int x, int y, int z, BlockState s) {
		set(new BlockPos(x, y, z), s);
	}

	default void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
	}

	/** Fill a horizontal disc. */
	default void disc(int cx, int y, int cz, double r, BlockState s) {
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) if (dx * dx + dz * dz <= r * r) set(cx + dx, y, cz + dz, s);
	}

	/** Columns of filler from y-1 down until solid ground (max depth), so platforms never float. */
	default void foundation(int cx, int y, int cz, double r, BlockState filler, int maxDepth) {
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
			if (dx * dx + dz * dz > r * r) continue;
			for (int d = 1; d <= maxDepth; d++) {
				BlockPos p = new BlockPos(cx + dx, y - d, cz + dz);
				BlockState here = get(p);
				if (!here.isAir() && here.getFluidState().isEmpty() && !here.canBeReplaced()) break;
				set(p, filler);
			}
		}
	}

	default void clearAbove(int cx, int y, int cz, double r, int height) {
		int ir = (int) Math.ceil(r);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
			if (dx * dx + dz * dz > r * r) continue;
			for (int dy = 0; dy < height; dy++) set(cx + dx, y + dy, cz + dz, air);
		}
	}

	static Sink live(ServerLevel level) {
		return new Sink() {
			@Override public void set(BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_CLIENTS); }
			@Override public BlockState get(BlockPos pos) { return level.getBlockState(pos); }
			@Override public ServerLevel live() { return level; }
			@Override public void loot(BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table, long seed) {
				net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, net.minecraft.util.RandomSource.create(seed), pos, table);
			}
			@Override public <T extends net.minecraft.world.entity.Mob> T spawn(net.minecraft.world.entity.EntityType<T> type, BlockPos pos) {
				T mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
				if (mob == null) return null;
				mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
				mob.setPersistenceRequired();
				level.addFreshEntity(mob);
				return mob;
			}
		};
	}

	static Sink clipped(WorldGenLevel level, BoundingBox box) {
		return new Sink() {
			@Override public void set(BlockPos pos, BlockState state) { if (box.isInside(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS); }
			@Override public BlockState get(BlockPos pos) { return box.isInside(pos) ? level.getBlockState(pos) : Blocks.STONE.defaultBlockState(); }
			@Override public void loot(BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table, long seed) {
				if (box.isInside(pos)) net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, net.minecraft.util.RandomSource.create(seed), pos, table);
			}
			@Override public <T extends net.minecraft.world.entity.Mob> T spawn(net.minecraft.world.entity.EntityType<T> type, BlockPos pos) {
				if (!box.isInside(pos)) return null;
				T mob = type.create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
				if (mob == null) return null;
				mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
				mob.setPersistenceRequired();
				level.addFreshEntityWithPassengers(mob);
				return mob;
			}
		};
	}
}
