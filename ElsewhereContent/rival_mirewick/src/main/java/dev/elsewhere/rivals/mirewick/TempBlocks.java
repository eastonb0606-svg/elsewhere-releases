package dev.elsewhere.rivals.mirewick;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The temporary-block ledger (BOSSES K10 / X6): walls, bridges, webs and the Mire-Trapper's webs are placed only in AIR,
 * never replace a block, live a fixed time and are saved in {@link MirewickState} so they vanish on unload, crash or
 * server stop. A block is only removed if the same block is still there, so a player's own build is never touched.
 */
public final class TempBlocks {
	private TempBlocks() {}

	public static final int MAX_PER_OWNER = 8;

	/** Places {@code state} at {@code pos} only if the cell is air. Returns true when placed. */
	public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ttlTicks) {
		if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir()) return false;
		if (!level.setBlock(pos, state, 3)) return false;
		MirewickState s = MirewickState.get(level.getServer());
		Identifier bid = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		s.temp.add(new MirewickState.Temp(level.dimension().identifier().toString(), pos.asLong(), bid.toString(), level.getGameTime() + ttlTicks));
		s.setDirty();
		return true;
	}

	public static int count(MinecraftServer server) {
		return MirewickState.get(server).temp.size();
	}

	/** Removes ledger entries whose time is up (or all of them when {@code all}); a changed cell is left alone. */
	static void sweep(MinecraftServer server, boolean all) {
		MirewickState s = MirewickState.get(server);
		if (s.temp.isEmpty()) return;
		long now = server.overworld().getGameTime();
		List<MirewickState.Temp> gone = new ArrayList<>();
		for (MirewickState.Temp t : s.temp) if (all || t.expiry() <= now) gone.add(t);
		if (gone.isEmpty()) return;
		for (MirewickState.Temp t : gone) {
			for (ServerLevel l : server.getAllLevels()) {
				if (!l.dimension().identifier().toString().equals(t.dim())) continue;
				BlockPos p = BlockPos.of(t.pos());
				if (!l.hasChunkAt(p)) continue;
				BlockState bs = l.getBlockState(p);
				Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(t.block()));
				if (bs.is(b)) l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			}
		}
		Iterator<MirewickState.Temp> it = s.temp.iterator();
		while (it.hasNext()) {
			MirewickState.Temp t = it.next();
			if (gone.contains(t)) {
				// keep entries whose chunk is not loaded so the cell is cleaned when it loads again
				boolean loaded = false;
				for (ServerLevel l : server.getAllLevels()) if (l.dimension().identifier().toString().equals(t.dim()) && l.hasChunkAt(BlockPos.of(t.pos()))) loaded = true;
				if (loaded) it.remove();
			}
		}
		s.setDirty();
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 == 0) sweep(server, false);
	}

	/** Crash recovery: every ledger entry left from a previous run is stale. */
	public static void purgeOnStart(MinecraftServer server) {
		MirewickState s = MirewickState.get(server);
		long now = server.overworld().getGameTime();
		for (int i = 0; i < s.temp.size(); i++) {
			MirewickState.Temp t = s.temp.get(i);
			s.temp.set(i, new MirewickState.Temp(t.dim(), t.pos(), t.block(), Math.min(t.expiry(), now)));
		}
		sweep(server, false);
	}

	public static void purgeAll(MinecraftServer server) {
		sweep(server, true);
	}
}
