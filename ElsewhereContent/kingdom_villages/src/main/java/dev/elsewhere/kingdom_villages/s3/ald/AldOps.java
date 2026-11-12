package dev.elsewhere.kingdom_villages.s3.ald;

import dev.elsewhere.kingdom_villages.VillageRecord;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One section of a runtime plan: first write per position wins (like {@code Plans.add}). {@link #ops} returns the section
 * bottom-up (every block after the block it stands on, so the manager's {@code canSurvive} test passes for banners and lanterns
 * on posts), hanging pieces after the beam they hang from, and fences, walls and bars with their side connections set (the
 * kingdom's writer sends no shape updates, so a plain default state would stand as a lone post: STRUCTURES.md R7).
 */
public final class AldOps {
	final Long2ObjectLinkedOpenHashMap<BlockState> map = new Long2ObjectLinkedOpenHashMap<>();
	/** Posts that stay unconnected (the sharpened palisade tips). */
	final LongOpenHashSet loose = new LongOpenHashSet();
	/** Pieces that hang from the block above (written after everything else in the section). */
	final LongOpenHashSet hanging = new LongOpenHashSet();

	public boolean set(BlockPos p, BlockState s) {
		long k = p.asLong();
		if (map.containsKey(k)) return false;
		map.put(k, s);
		return true;
	}

	public boolean set(int x, int y, int z, BlockState s) {
		return set(new BlockPos(x, y, z), s);
	}

	public void loose(BlockPos p, BlockState s) {
		if (set(p, s)) loose.add(p.asLong());
	}

	public void hang(BlockPos p, BlockState s) {
		if (set(p, s)) hanging.add(p.asLong());
	}

	public boolean has(BlockPos p) {
		return map.containsKey(p.asLong());
	}

	public BlockState get(BlockPos p) {
		return map.get(p.asLong());
	}

	public int size() {
		return map.size();
	}

	/** The section's ops: bottom-up, hanging pieces last, connections set. */
	public List<VillageRecord.Op> ops(ServerLevel level) {
		connect(level);
		List<VillageRecord.Op> main = new ArrayList<>(), late = new ArrayList<>();
		for (var e : map.long2ObjectEntrySet()) (hanging.contains(e.getLongKey()) ? late : main).add(new VillageRecord.Op(e.getLongKey(), e.getValue()));
		main.sort(Comparator.comparingInt(o -> BlockPos.getY(o.pos()))); // stable: same-height ops keep their order
		main.addAll(late);
		return main;
	}

	/** Sets fence, bar and wall sides toward planned neighbours that connect (same family, or a planned full block). */
	void connect(ServerLevel level) {
		for (var e : map.long2ObjectEntrySet()) {
			long k = e.getLongKey();
			BlockState s = e.getValue();
			if (loose.contains(k)) continue;
			boolean cross = s.getBlock() instanceof FenceBlock || s.getBlock() instanceof IronBarsBlock, wall = s.getBlock() instanceof WallBlock;
			if (!cross && !wall) continue;
			BlockPos p = BlockPos.of(k);
			boolean[] side = new boolean[4];
			int i = 0;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos q = p.relative(d);
				BlockState n = map.get(q.asLong());
				boolean c = n != null && !loose.contains(q.asLong()) && (n.getBlock() == s.getBlock() || n.isCollisionShapeFullBlock(level, q)
					|| wall && n.getBlock() instanceof WallBlock || cross && s.getBlock() instanceof IronBarsBlock && n.getBlock() instanceof IronBarsBlock);
				side[i++] = c;
				if (cross) s = s.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d), c);
				else s = s.setValue(WallBlock.PROPERTY_BY_DIRECTION.get(d), c ? WallSide.LOW : WallSide.NONE);
			}
			if (wall) {
				// Direction.Plane.HORIZONTAL order: north, east, south, west. A straight run (N+S or E+W only) has no post.
				boolean straight = side[0] && side[2] && !side[1] && !side[3] || side[1] && side[3] && !side[0] && !side[2];
				s = s.setValue(WallBlock.UP, !straight);
			}
			e.setValue(s);
		}
	}
}
