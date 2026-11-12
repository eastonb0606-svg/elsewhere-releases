package dev.elsewhere.gear3.s3.willo;

import com.mojang.serialization.Codec;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Scheduler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The Marsh Wisp's light: one invisible level-10 light block (a soul lantern's glow) at the wisp, moved at most every 10 ticks
 * and only when the wisp changed block. Placed only in air, never over a block or fluid; the wisp keeps its last light when the
 * new spot is not air.
 *
 * <p>Crash-safe, the same way as z-items' Glimmer Lantern lights ({@code Gear3LightBlocks}), with its own record so neither
 * system ever touches the other's lights: each light is also recorded in its own chunk ({@link #RECORD}, a persistent Fabric
 * chunk attachment written in the same chunk NBT as the block, so no save order can keep one without the other). A recorded
 * light that is not live in this server session (left by a crash, or by a wisp whose chunk unloaded) is cleared when its chunk
 * loads. A wisp that is discarded or killed clears its light at once; server stop clears every live light in a loaded chunk. The
 * clean-up only ever removes a level-10 light block. Placing and clearing skip neighbour and shape updates, so a passing wisp
 * never trips an observer. Event-driven: the wisp's own move check, chunk load (O(1) without a record), server stop.
 */
public final class WispLights {
	private WispLights() {}

	public static final int LEVEL = 10, INTERVAL = 10;
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** Block positions ({@link BlockPos#asLong()}) of wisp lights in a chunk; saved with the chunk. */
	public static final AttachmentType<List<Long>> RECORD = AttachmentRegistry.create(Gear3Content.id("gear3_wisp_lights"),
		b -> b.persistent(Codec.LONG.listOf()));

	private record Key(ServerLevel level, long pos) {}

	/** Lights placed in this server session by a wisp that is still out. */
	private static final Set<Key> LIVE = new HashSet<>();
	private static boolean hooked;

	/** Registers the chunk-load and server-stop hooks (the attachment type registers when this class loads, at mod init). Idempotent. */
	static void init() {
		if (hooked) return;
		hooked = true;
		Gear3Scheduler.init();
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (!generated) onChunkLoad(level, chunk);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdown());
	}

	public static boolean isOurs(BlockState st) {
		return st.is(Blocks.LIGHT) && st.getValue(LightBlock.LEVEL) == LEVEL;
	}

	public static int live() {
		return LIVE.size();
	}

	/** The wisp's move check (every {@link #INTERVAL} ticks): moves its light to the block it is in now. */
	static void follow(MarshWisp w, ServerLevel level) {
		BlockPos cur = w.lightPos;
		if (!WillOLantern.LIGHT) {
			if (cur != null) {
				w.lightPos = null;
				clear(level, cur);
			}
			return;
		}
		BlockPos want = BlockPos.containing(w.getX(), w.getY() + w.getBbHeight() * 0.5, w.getZ());
		if (want.equals(cur) && level.hasChunkAt(cur) && isOurs(level.getBlockState(cur))) return;
		if (!want.equals(cur) && !placeable(level, want)) return; // keep the old light until there is air to move into
		if (cur != null) {
			w.lightPos = null;
			clear(level, cur);
		}
		if (place(level, want)) w.lightPos = want.immutable();
	}

	/** The wisp leaves the level: killed or dismissed clears its light now; unloaded with its chunk leaves it for that chunk's next load. */
	static void release(MarshWisp w, ServerLevel level, Entity.RemovalReason reason) {
		BlockPos at = w.lightPos;
		if (at == null) return;
		w.lightPos = null;
		if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) LIVE.remove(new Key(level, at.asLong()));
		else clear(level, at);
	}

	static boolean placeable(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos) && level.getBlockState(pos).isAir();
	}

	/** Places a wisp light at {@code pos} if it is air in a loaded chunk; false otherwise. */
	public static boolean place(ServerLevel level, BlockPos pos) {
		if (!placeable(level, pos)) return false;
		BlockPos at = pos.immutable();
		level.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LEVEL), FLAGS);
		LIVE.add(new Key(level, at.asLong()));
		record(level.getChunkAt(at), at, true);
		return true;
	}

	/** Removes a wisp light (only a level-10 light block; a waterlogged one turns back into water) and its record. False if its chunk isn't loaded (it heals on load). */
	public static boolean clear(ServerLevel level, BlockPos pos) {
		LIVE.remove(new Key(level, pos.asLong()));
		if (!level.hasChunkAt(pos)) return false;
		BlockState st = level.getBlockState(pos);
		if (isOurs(st)) {
			if (st.getValue(LightBlock.WATERLOGGED)) level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
			else level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
		}
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
			if (LIVE.contains(new Key(level, key))) continue;
			BlockPos pos = BlockPos.of(key);
			n++;
			Gear3Scheduler.after(server, 1, () -> {
				if (!LIVE.contains(new Key(level, pos.asLong()))) clear(level, pos);
			});
		}
		return n;
	}

	/** Recorded wisp-light positions in the chunk holding {@code pos} (self-test). */
	public static List<Long> recorded(ServerLevel level, BlockPos pos) {
		List<Long> rec = ((AttachmentTarget) level.getChunkAt(pos)).getAttached(RECORD);
		return rec == null ? List.of() : rec;
	}

	/** Self-test: forget this session's lights the way a crash does (blocks and chunk records stay). */
	public static void forgetSessionForTest() {
		LIVE.clear();
	}

	/** Server stop: clear every live light whose chunk is loaded; the rest stay recorded and are cleared when their chunk loads. */
	static void shutdown() {
		for (Key k : new ArrayList<>(LIVE)) clear(k.level(), BlockPos.of(k.pos()));
		LIVE.clear();
	}
}
