package dev.elsewhere.stub.nether_end_bosses;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stub of the core's arena: a vertical cylinder (centre, radius, minY..maxY) that is snapshotted on summon,
 * sealed with {@code elsewhere:arena_seal} on its rim, and restored on win or reset.
 * Void arenas send players who fall out back to the ledge instead of killing them.
 */
public final class BossArena {
	public final BlockPos center;
	public final int radius;
	public final int minY;
	public final int maxY;
	public final boolean voidArena;
	/** Where players are sent back to on a void fall. */
	public final Vec3 ledge;

	private BlockState[] snapshot;
	private int sealed;

	public BossArena(BlockPos center, int radius, int minY, int maxY, boolean voidArena, Vec3 ledge) {
		this.center = center;
		this.radius = radius;
		this.minY = minY;
		this.maxY = maxY;
		this.voidArena = voidArena;
		this.ledge = ledge;
	}

	public AABB box() {
		return new AABB(center.getX() - radius, minY, center.getZ() - radius, center.getX() + radius + 1, maxY + 1, center.getZ() + radius + 1);
	}

	public boolean contains(Vec3 pos) {
		double dx = pos.x - (center.getX() + 0.5);
		double dz = pos.z - (center.getZ() + 0.5);
		return dx * dx + dz * dz <= (radius + 0.5) * (radius + 0.5) && pos.y >= minY - 1 && pos.y <= maxY + 2;
	}

	public boolean contains(Entity entity) {
		return contains(entity.position());
	}

	/** True once a player is clearly out of a void arena (below the floor, or above the ceiling when gravity is flipped). */
	public boolean fellIntoVoid(Entity entity) {
		return voidArena && (entity.getY() < minY - 6 || entity.getY() > maxY + 10);
	}

	private int sizeX() { return radius * 2 + 1; }

	private int sizeY() { return maxY - minY + 1; }

	private int index(int x, int y, int z) {
		return ((y - minY) * sizeX() + (z - center.getZ() + radius)) * sizeX() + (x - center.getX() + radius);
	}

	public boolean hasSnapshot() {
		return snapshot != null;
	}

	public void snapshot(ServerLevel level) {
		snapshot = new BlockState[sizeX() * sizeX() * sizeY()];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = minY; y <= maxY; y++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
					snapshot[index(x, y, z)] = level.getBlockState(pos.set(x, y, z));
				}
			}
		}
	}

	/** Puts every block back the way it was at summon time. Returns the number of blocks changed. */
	public int restore(ServerLevel level) {
		if (snapshot == null) return 0;
		int changed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int pass = 0; pass < 2; pass++) { // solids first, then fluids, so nothing flows while restoring
			for (int y = minY; y <= maxY; y++) {
				for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
					for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
						BlockState want = snapshot[index(x, y, z)];
						if (want.getFluidState().isEmpty() == (pass == 1)) continue;
						pos.set(x, y, z);
						if (differs(level.getBlockState(pos), want)) {
							level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
							changed++;
						}
					}
				}
			}
		}
		sealed = 0;
		return changed;
	}

	/** Counts blocks that differ from the snapshot (used by the self-test). */
	public int diff(ServerLevel level) {
		if (snapshot == null) return -1;
		int n = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = minY; y <= maxY; y++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
					if (differs(level.getBlockState(pos.set(x, y, z)), snapshot[index(x, y, z)])) n++;
				}
			}
		}
		return n;
	}

	/** Fills the air on the arena's rim with the translucent seal. Returns how many seal blocks were placed. */
	public int seal(ServerLevel level, int maxBlocks) {
		if (voidArena) return 0; // void arenas rely on the return-to-the-ledge rule instead of a wall
		BlockState seal = StubFramework.ARENA_SEAL.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int placed = 0;
		double inner = (radius - 0.5) * (radius - 0.5);
		double outer = (radius + 0.5) * (radius + 0.5);
		for (int z = -radius; z <= radius; z++) {
			for (int x = -radius; x <= radius; x++) {
				double d = x * x + z * z;
				if (d < inner || d > outer) continue;
				for (int y = minY; y <= maxY && placed < maxBlocks; y++) {
					pos.set(center.getX() + x, y, center.getZ() + z);
					if (level.getBlockState(pos).isAir()) {
						level.setBlock(pos, seal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						placed++;
					}
				}
			}
		}
		sealed = placed;
		return placed;
	}

	public int sealedBlocks() {
		return sealed;
	}

	public static boolean isSeal(BlockState state) {
		return state.is(StubFramework.ARENA_SEAL);
	}

	public static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	/** Core's Boss Waystone (placed near a slain boss, often inside the arena) is neither arena damage nor rolled back. */
	private static boolean differs(BlockState cur, BlockState want) {
		return cur != want && !"elsewhere:boss_waystone".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString());
	}
}
