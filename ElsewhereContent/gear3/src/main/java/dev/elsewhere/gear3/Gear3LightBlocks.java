package dev.elsewhere.gear3;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Glimmer Lantern light blocks: invisible level-14 light in air only, 5 minutes, at most 8 per player (oldest goes first).
 *
 * <p>Self-healing: every placed light is also recorded persistently in its own chunk ({@link #RECORD}, a persistent Fabric
 * chunk attachment). The record is written in the same chunk NBT as the light block, so a save can never keep one without
 * the other (a level SavedData file is saved separately from the chunks, so a crash after a chunk was saved on unload but
 * before the next SavedData save would leave an untracked light). A recorded light that is not live in this server session
 * (left by a crash, or whose 5 minutes ran out while its chunk was unloaded) is cleared when its chunk loads; every chunk that
 * loads at server start goes through the same hook. Event-driven only: chunk load, the expiry timer and server stop; no
 * per-tick scan, and a chunk is never loaded just to remove a light.
 */
public final class Gear3LightBlocks {
	private Gear3LightBlocks() {}

	public static final int MAX_PER_PLAYER = 8, LIFETIME_TICKS = 5 * 60 * 20, LEVEL = 14;

	/** Block positions ({@link BlockPos#asLong()}) of Glimmer Lantern lights in a chunk; saved with the chunk. */
	public static final AttachmentType<List<Long>> RECORD = AttachmentRegistry.create(Gear3Content.id("gear3_lantern_lights"),
		b -> b.persistent(Codec.LONG.listOf()));

	private record Placed(ServerLevel level, BlockPos pos) {}

	private static final Map<UUID, ArrayDeque<Placed>> BY_PLAYER = new HashMap<>();
	/** Lights placed in this server session whose timer has not run out. */
	private static final Set<Placed> LIVE = new HashSet<>();
	private static boolean hooked;

	/** Registers the attachment type (before any chunk loads) and the event hooks. Idempotent. */
	public static void init() {
		if (hooked) return;
		hooked = true;
		Gear3Scheduler.init();
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (!generated) onChunkLoad(level, chunk);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdown());
	}

	public static int count(UUID p) {
		ArrayDeque<Placed> q = BY_PLAYER.get(p);
		return q == null ? 0 : q.size();
	}

	/** Places a light at {@code pos} if it is air; false when it is not. */
	public static boolean place(ServerPlayer p, ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return false;
		ArrayDeque<Placed> q = BY_PLAYER.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
		while (q.size() >= MAX_PER_PLAYER) expire(q.pollFirst());
		BlockPos at = pos.immutable();
		level.setBlockAndUpdate(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LEVEL));
		Placed pl = new Placed(level, at);
		q.addLast(pl);
		LIVE.add(pl);
		record(level.getChunkAt(at), at, true);
		Gear3Scheduler.after(level.getServer(), LIFETIME_TICKS, () -> {
			if (q.remove(pl)) expire(pl);
		});
		return true;
	}

	/** The light's time is up (timer or per-player cap): cleared now if its chunk is loaded, else when the chunk next loads. */
	private static void expire(Placed pl) {
		if (pl == null) return;
		LIVE.remove(pl);
		clear(pl.level, pl.pos);
	}

	/** Removes our light at {@code pos} (only a level-14 light block) and its record; does nothing if the chunk isn't loaded. */
	private static boolean clear(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) return false;
		BlockState st = level.getBlockState(pos);
		if (st.is(Blocks.LIGHT) && st.getValue(LightBlock.LEVEL) == LEVEL) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		record(level.getChunkAt(pos), pos, false);
		return true;
	}

	private static void record(LevelChunk chunk, BlockPos pos, boolean add) {
		AttachmentTarget t = (AttachmentTarget) chunk;
		List<Long> cur = t.getAttached(RECORD);
		long key = pos.asLong();
		boolean has = cur != null && cur.contains(key);
		if (add == has) return;
		List<Long> next = cur == null ? new ArrayList<>() : new ArrayList<>(cur);
		if (add) next.add(key);
		else next.remove(Long.valueOf(key));
		if (next.isEmpty()) t.removeAttached(RECORD);
		else t.setAttached(RECORD, List.copyOf(next));
		chunk.markUnsaved();
	}

	/**
	 * Chunk-load hook: every recorded light in this chunk that is not live in this session is stale and is cleared on the next
	 * server tick (the chunk is fully loaded by then). Returns how many were queued. O(1) for a chunk without a record.
	 */
	public static int onChunkLoad(ServerLevel level, LevelChunk chunk) {
		List<Long> rec = ((AttachmentTarget) chunk).getAttached(RECORD);
		if (rec == null || rec.isEmpty()) return 0;
		MinecraftServer server = level.getServer();
		if (!server.isSameThread()) {
			server.execute(() -> onChunkLoad(level, chunk));
			return 0;
		}
		int n = 0;
		for (long key : rec) {
			BlockPos pos = BlockPos.of(key);
			if (LIVE.contains(new Placed(level, pos))) continue;
			n++;
			Gear3Scheduler.after(server, 1, () -> {
				if (!LIVE.contains(new Placed(level, pos))) clear(level, pos);
			});
		}
		return n;
	}

	/** Recorded light positions in the chunk holding {@code pos} (self-test). */
	public static List<Long> recorded(ServerLevel level, BlockPos pos) {
		List<Long> rec = ((AttachmentTarget) level.getChunkAt(pos)).getAttached(RECORD);
		return rec == null ? List.of() : rec;
	}

	/** Self-test: forget this session's lights the way a crash does (blocks and chunk records stay, timers find nothing). */
	public static void forgetSessionForTest() {
		forget();
	}

	private static void forget() {
		for (ArrayDeque<Placed> q : BY_PLAYER.values()) q.clear();
		BY_PLAYER.clear();
		LIVE.clear();
	}

	/** Server stop: clear every live light whose chunk is loaded; the rest stay recorded and are cleared when their chunk loads. */
	static void shutdown() {
		for (Placed pl : new ArrayList<>(LIVE)) clear(pl.level, pl.pos);
		forget();
	}
}
