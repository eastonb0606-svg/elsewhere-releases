package dev.elsewhere.stub.petalwind_glasswaste;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** STUB of the core's arena snapshot: records every block state in the arena box and puts them back on reset. */
public final class ArenaSnapshot {
	private final Map<BlockPos, BlockState> states = new HashMap<>();

	public static ArenaSnapshot capture(ServerLevel level, AABB box) {
		ArenaSnapshot s = new ArenaSnapshot();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			if (!level.isInWorldBounds(p)) continue;
			s.states.put(p.immutable(), level.getBlockState(p));
		}
		return s;
	}

	/** Puts back every changed block; returns how many changed. */
	public int restore(ServerLevel level) {
		int changed = 0;
		for (var e : states.entrySet()) {
			if (differs(level.getBlockState(e.getKey()), e.getValue())) {
				level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				changed++;
			}
		}
		return changed;
	}

	public int differences(ServerLevel level) {
		int n = 0;
		for (var e : states.entrySet()) if (differs(level.getBlockState(e.getKey()), e.getValue())) n++;
		return n;
	}

	public int size() {
		return states.size();
	}

	/** Core's Boss Waystone (placed near a slain boss, often inside the arena) is neither arena damage nor rolled back. */
	private static boolean differs(BlockState cur, BlockState want) {
		return cur != want && !"elsewhere:boss_waystone".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString());
	}
}
