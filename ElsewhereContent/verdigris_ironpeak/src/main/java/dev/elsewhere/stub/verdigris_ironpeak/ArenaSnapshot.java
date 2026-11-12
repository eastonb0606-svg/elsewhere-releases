package dev.elsewhere.stub.verdigris_ironpeak;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** STUB of the core's arena snapshot: block states in a box, restored on reset or victory. Block entities are not kept. */
public final class ArenaSnapshot {
	private final BoundingBox box;
	private final Map<BlockPos, BlockState> states = new HashMap<>();

	private ArenaSnapshot(BoundingBox box) {
		this.box = box;
	}

	public static ArenaSnapshot take(ServerLevel level, BoundingBox box) {
		ArenaSnapshot s = new ArenaSnapshot(box);
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			s.states.put(p.immutable(), level.getBlockState(p));
		}
		return s;
	}

	/** Puts back every block that changed. Returns how many were restored. */
	public int restore(ServerLevel level) {
		int changed = 0;
		for (Map.Entry<BlockPos, BlockState> e : states.entrySet()) {
			if (differs(level.getBlockState(e.getKey()), e.getValue())) {
				level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				changed++;
			}
		}
		return changed;
	}

	public int differences(ServerLevel level) {
		int n = 0;
		for (Map.Entry<BlockPos, BlockState> e : states.entrySet()) if (differs(level.getBlockState(e.getKey()), e.getValue())) n++;
		return n;
	}

	public BoundingBox box() {
		return box;
	}

	/** Core's Boss Waystone (placed near a slain boss, often inside the arena) is neither arena damage nor rolled back. */
	private static boolean differs(BlockState cur, BlockState want) {
		return cur != want && !"elsewhere:boss_waystone".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString());
	}
}
