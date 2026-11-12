package dev.elsewhere.core.boss;

import dev.elsewhere.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A cylindrical boss arena: snapshot of its blocks (for reset), the seal ring, and a containment test.
 * Snapshots live in memory only; a server restart mid-fight keeps the arena as it is.
 */
public final class ArenaSeal {
	public final BlockPos center;
	public final int radius;
	public final int minY;
	public final int maxY;
	private final Map<BlockPos, BlockState> snapshot = new HashMap<>();
	private final List<BlockPos> sealBlocks = new ArrayList<>();

	public ArenaSeal(final BlockPos center, final int radius, final int below, final int above) {
		this.center = center;
		this.radius = radius;
		this.minY = center.getY() - below;
		this.maxY = center.getY() + above;
	}

	public boolean contains(final Vec3 pos) {
		double dx = pos.x - (this.center.getX() + 0.5);
		double dz = pos.z - (this.center.getZ() + 0.5);
		return dx * dx + dz * dz <= (this.radius + 0.5) * (this.radius + 0.5) && pos.y >= this.minY - 1 && pos.y <= this.maxY + 2;
	}

	/** Records every block inside the cylinder so {@link #restore} can undo arena damage. */
	public void snapshot(final ServerLevel level) {
		this.snapshot.clear();
		int r = this.radius + 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int y = this.minY; y <= this.maxY; y++) {
					BlockPos p = new BlockPos(this.center.getX() + dx, y, this.center.getZ() + dz);
					this.snapshot.put(p, level.getBlockState(p));
				}
			}
		}
	}

	public int snapshotSize() {
		return this.snapshot.size();
	}

	/** Puts back every block that changed since the snapshot. Returns how many blocks were restored. */
	public int restore(final ServerLevel level) {
		int n = 0;
		for (Map.Entry<BlockPos, BlockState> e : this.snapshot.entrySet()) {
			BlockState cur = level.getBlockState(e.getKey());
			if (cur != e.getValue() && !cur.is(ModBlocks.BOSS_WAYSTONE)) {
				level.setBlock(e.getKey(), e.getValue(), 2);
				n++;
			}
		}
		return n;
	}

	/** Places the seal ring (one block outside the radius) wherever it is open. */
	public void raise(final ServerLevel level) {
		int r = this.radius + 1;
		BlockState seal = ModBlocks.ARENA_SEAL.defaultBlockState();
		for (int i = 0; i < 360; i++) {
			double a = Math.toRadians(i);
			int x = this.center.getX() + (int)Math.round(Math.cos(a) * r);
			int z = this.center.getZ() + (int)Math.round(Math.sin(a) * r);
			for (int y = this.minY; y <= this.maxY; y++) {
				BlockPos p = new BlockPos(x, y, z);
				BlockState s = level.getBlockState(p);
				if ((s.isAir() || s.canBeReplaced()) && !s.is(ModBlocks.ARENA_SEAL)) {
					level.setBlock(p, seal, 2);
					this.sealBlocks.add(p);
				}
			}
		}
	}

	public void lower(final ServerLevel level) {
		for (BlockPos p : this.sealBlocks) {
			if (level.getBlockState(p).is(ModBlocks.ARENA_SEAL)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		this.sealBlocks.clear();
	}

	public boolean isRaised() {
		return !this.sealBlocks.isEmpty();
	}
}
