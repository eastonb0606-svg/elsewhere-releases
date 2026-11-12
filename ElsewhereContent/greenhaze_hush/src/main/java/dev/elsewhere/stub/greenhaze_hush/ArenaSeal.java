package dev.elsewhere.stub.greenhaze_hush;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * STUB of the core's arena lock: snapshots the arena's blocks on summon, closes a translucent seal
 * ({@code elsewhere:arena_seal}, solid for mobs, passable for players) and restores everything on reset or win.
 * Nothing outside the box is touched.
 */
public final class ArenaSeal {
	public static final List<ArenaSeal> ACTIVE = new ArrayList<>();

	public final ServerLevel level;
	public final BlockPos center;
	public final int radius, below, above;
	private final Map<BlockPos, BlockState> snapshot = new HashMap<>();
	private final List<BlockPos> sealBlocks = new ArrayList<>();
	private boolean sealed;

	private ArenaSeal(ServerLevel level, BlockPos center, int radius, int below, int above) {
		this.level = level;
		this.center = center.immutable();
		this.radius = radius;
		this.below = below;
		this.above = above;
	}

	/** Snapshot the arena box now (call right after the arena is built and before the fight). */
	public static ArenaSeal create(ServerLevel level, BlockPos center, int radius, int below, int above) {
		ArenaSeal a = new ArenaSeal(level, center, radius, below, above);
		int r = radius + 1;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -below, -r), center.offset(r, above, r))) {
			a.snapshot.put(p.immutable(), level.getBlockState(p));
		}
		ACTIVE.add(a);
		return a;
	}

	/** Re-link after a reload, without a snapshot (restore only removes the seal, which is found again by scanning). */
	public static ArenaSeal relink(ServerLevel level, BlockPos center, int radius, int below, int above) {
		ArenaSeal a = new ArenaSeal(level, center, radius, below, above);
		int r = radius + 1;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, Math.min(above, 12), r))) {
			if (level.getBlockState(p).is(StubBlocks.ARENA_SEAL)) a.sealBlocks.add(p.immutable());
		}
		a.sealed = !a.sealBlocks.isEmpty();
		ACTIVE.add(a);
		return a;
	}

	public boolean inside(Vec3 pos) {
		double dx = pos.x - (center.getX() + 0.5), dz = pos.z - (center.getZ() + 0.5);
		return dx * dx + dz * dz <= (double) radius * radius && pos.y >= center.getY() - below - 1 && pos.y <= center.getY() + above + 1;
	}

	public boolean inside(ServerPlayer p) {
		return p.level() == level && p.isAlive() && !p.isSpectator() && inside(p.position());
	}

	public List<ServerPlayer> playersInside() {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) if (inside(p)) out.add(p);
		return out;
	}

	/** Close the seal: a ring of seal blocks at the arena edge, wherever there is air. */
	public void seal(Block sealBlock) {
		if (sealed) return;
		sealed = true;
		int n = (int) Math.ceil(2 * Math.PI * radius * 1.3);
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			int x = center.getX() + (int) Math.round(Math.cos(a) * radius);
			int z = center.getZ() + (int) Math.round(Math.sin(a) * radius);
			for (int y = center.getY() - 1; y <= center.getY() + Math.min(above, 12); y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (level.getBlockState(p).isAir() && !sealBlocks.contains(p)) {
					level.setBlock(p, sealBlock.defaultBlockState(), Block.UPDATE_CLIENTS);
					sealBlocks.add(p);
				}
			}
		}
	}

	public boolean isSealed() {
		return sealed;
	}

	public int sealBlockCount() {
		return sealBlocks.size();
	}

	public void unseal() {
		for (BlockPos p : sealBlocks) {
			if (!level.getBlockState(p).isAir()) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		sealBlocks.clear();
		sealed = false;
	}

	/** Put every snapshotted block back. Returns how many blocks were changed. */
	public int restore() {
		unseal();
		int changed = 0;
		for (Map.Entry<BlockPos, BlockState> e : snapshot.entrySet()) {
			if (differs(level.getBlockState(e.getKey()), e.getValue())) {
				level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				changed++;
			}
		}
		return changed;
	}

	public void release() {
		unseal();
		ACTIVE.remove(this);
	}

	public static boolean inAnyFight(ServerPlayer p) {
		for (ArenaSeal a : ACTIVE) if (a.sealed && a.inside(p)) return true;
		return false;
	}

	public static void clearAll() { ACTIVE.clear(); }

	/** Core's Boss Waystone (placed near a slain boss, often inside the arena) is neither arena damage nor rolled back. */
	private static boolean differs(BlockState cur, BlockState want) {
		return cur != want && !"elsewhere:boss_waystone".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString());
	}
}
