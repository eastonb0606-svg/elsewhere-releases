package dev.elsewhere.stub.overworld_bosses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Level-wide ledger of every {@code elsewhere:arena_seal} block an overworld boss arena placed: the block that was
 * there before (so a restore never writes a seal back, even when two arenas overlap) and which arenas still need it.
 * A seal is only lifted when no live arena holds it any more. A once-a-second sweep lifts seals whose owners are all
 * gone (boss discarded, unloaded, changed dimension, or removed by anything that skipped the normal restore).
 * STUB: the core's framework owns the real arena seal; this copy keeps the same behaviour for the module.
 */
public final class ArenaSeals {
	private ArenaSeals() {}

	private static final class Claim {
		BlockState original;
		final List<BossArena> owners = new ArrayList<>(2);

		Claim(BlockState original) {
			this.original = original;
		}
	}

	private static final Map<ResourceKey<Level>, Map<Long, Claim>> CLAIMS = new HashMap<>();
	private static boolean registered;

	/** Registers the sweep (idempotent). */
	public static void init() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 7 || CLAIMS.isEmpty()) return;
			for (ServerLevel level : server.getAllLevels()) sweep(level);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CLAIMS.clear());
	}

	private static Map<Long, Claim> of(ServerLevel level) {
		return CLAIMS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
	}

	private static boolean isSeal(BlockState s) {
		return BossRegistry.ARENA_SEAL != null && s.is(BossRegistry.ARENA_SEAL);
	}

	/**
	 * Places a seal at {@code pos} for {@code arena} (or joins an existing seal there). Only air / replaceable blocks
	 * (water, grass, snow layers...) take a seal. Returns true when the arena now holds a seal at pos.
	 */
	public static boolean place(ServerLevel level, BlockPos pos, BossArena arena, BlockState seal) {
		Map<Long, Claim> m = of(level);
		long key = pos.asLong();
		BlockState cur = level.getBlockState(pos);
		Claim c = m.get(key);
		if (isSeal(cur)) {
			if (c == null) {
				// A seal nobody tracks (left over from an older build / crash): adopt it, lift it to air later.
				c = new Claim(Blocks.AIR.defaultBlockState());
				m.put(key, c);
			}
			if (!c.owners.contains(arena)) c.owners.add(arena);
			arena.sealed.add(key);
			return true;
		}
		if (!(cur.isAir() || cur.canBeReplaced())) return false;
		c = new Claim(cur);
		c.owners.add(arena);
		m.put(key, c);
		arena.sealed.add(key);
		level.setBlock(pos, seal, Block.UPDATE_CLIENTS);
		return true;
	}

	/** The block that stood at pos before any arena sealed it, or null when no tracked seal is there. */
	public static @Nullable BlockState original(ServerLevel level, BlockPos pos) {
		Map<Long, Claim> m = CLAIMS.get(level.dimension());
		Claim c = m == null ? null : m.get(pos.asLong());
		return c == null ? null : c.original;
	}

	/** True when a live arena other than {@code except} still holds the seal at pos. */
	public static boolean heldByOther(ServerLevel level, BlockPos pos, BossArena except) {
		Map<Long, Claim> m = CLAIMS.get(level.dimension());
		Claim c = m == null ? null : m.get(pos.asLong());
		if (c == null) return false;
		for (BossArena a : c.owners) if (a != except && a.isLive()) return true;
		return false;
	}

	/** Drops all of the arena's seal claims; seals no other live arena holds go back to their pre-seal block. */
	public static int release(ServerLevel level, BossArena arena) {
		Map<Long, Claim> m = of(level);
		int n = 0;
		for (long key : arena.sealed) {
			Claim c = m.get(key);
			if (c == null) continue;
			c.owners.remove(arena);
			c.owners.removeIf(a -> !a.isLive());
			if (!c.owners.isEmpty()) continue;
			m.remove(key);
			BlockPos p = BlockPos.of(key);
			if (isSeal(level.getBlockState(p))) {
				level.setBlock(p, c.original, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				n++;
			}
		}
		arena.sealed.clear();
		return n;
	}

	/** Lifts every tracked seal whose owners are all gone. Returns how many blocks were reverted. */
	public static int sweep(ServerLevel level) {
		Map<Long, Claim> m = CLAIMS.get(level.dimension());
		if (m == null || m.isEmpty()) return 0;
		int n = 0;
		Iterator<Map.Entry<Long, Claim>> it = m.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Claim> e = it.next();
			Claim c = e.getValue();
			c.owners.removeIf(a -> !a.isLive());
			if (!c.owners.isEmpty()) continue;
			BlockPos p = BlockPos.of(e.getKey());
			if (!level.isLoaded(p)) continue; // retry once the chunk is back
			if (isSeal(level.getBlockState(p))) {
				level.setBlock(p, c.original, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				n++;
			}
			it.remove();
		}
		return n;
	}

	/** Tracked seal positions in this level (for tests). */
	public static int tracked(ServerLevel level) {
		Map<Long, Claim> m = CLAIMS.get(level.dimension());
		return m == null ? 0 : m.size();
	}
}
