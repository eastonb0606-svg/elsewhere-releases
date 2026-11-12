package dev.elsewhere.duelist.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.kit.Kit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

/**
 * The temporary-block service (K10). Every duel block is in a per-world ledger (saved with the world): it vanishes
 * when its lifetime ends (cracks from tick 500 of 600), when its owner dies or is removed ({@link #clearOwner}), when
 * its chunk unloads, and on the next server start. A block is placed only in air (never replaces a block, never in a
 * fluid), never inside an entity, and never so that a player nearby is left without an open side.
 */
public final class DuelBlocks {
	private DuelBlocks() {}

	public record Entry(long pos, String owner, long expires, long placed) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Entry::pos), Codec.STRING.fieldOf("owner").forGetter(Entry::owner),
			Codec.LONG.fieldOf("expires").forGetter(Entry::expires), Codec.LONG.fieldOf("placed").forGetter(Entry::placed)).apply(i, Entry::new));
	}

	/** The saved ledger of one world. */
	public static final class Ledger extends SavedData {
		static final Codec<Ledger> CODEC = Codec.list(Entry.CODEC).optionalFieldOf("entries", List.of()).xmap(l -> {
			Ledger g = new Ledger();
			for (Entry e : l) g.map.put(e.pos(), e);
			return g;
		}, g -> new ArrayList<>(g.map.values())).codec();
		static final SavedDataType<Ledger> TYPE = new SavedDataType<>(DuelistContent.id("duel_blocks"), Ledger::new, CODEC, net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		final Map<Long, Entry> map = new LinkedHashMap<>();
	}

	private static Ledger ledger(ServerLevel l) {
		return l.getDataStorage().computeIfAbsent(Ledger.TYPE);
	}

	/** Number of duel blocks this owner currently has in this world. */
	public static int count(ServerLevel level, UUID owner) {
		String o = owner.toString();
		int n = 0;
		for (Entry e : ledger(level).map.values()) if (e.owner().equals(o)) n++;
		return n;
	}

	/** A copy of the ledger entries (tests). */
	public static List<Entry> entries(ServerLevel level) {
		return new ArrayList<>(ledger(level).map.values());
	}

	public static int total(ServerLevel level) {
		return ledger(level).map.size();
	}

	public static boolean known(ServerLevel level, BlockPos pos) {
		return ledger(level).map.containsKey(pos.asLong());
	}

	/** True when placing at {@code pos} is allowed: air only, no entity inside, and no player within 4 blocks ends up fully enclosed. */
	public static boolean canPlace(ServerLevel level, BlockPos pos, Entity ignore) {
		if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir() || !level.hasChunkAt(pos)) return false;
		AABB box = new AABB(pos);
		for (Entity e : level.getEntities((Entity) null, box, x -> x != ignore && !x.isSpectator())) {
			if (e instanceof LivingEntity || e.getBoundingBox().intersects(box)) return false;
		}
		for (Player p : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(4.0))) {
			if (!p.isSpectator() && wouldEnclose(level, p, pos)) return false;
		}
		return true;
	}

	/** True when, with {@code pos} turned solid, every neighbour of the player's feet and head cells (and the cell above) is blocked. */
	static boolean wouldEnclose(ServerLevel level, Player p, BlockPos extra) {
		return wouldEncloseAt(level, p.blockPosition(), extra);
	}

	/** The enclosure rule for a player standing at {@code feet}. */
	public static boolean wouldEncloseAt(ServerLevel level, BlockPos feet, BlockPos extra) {
		int open = 0;
		BlockPos head = feet.above();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos f = feet.relative(d), hd = head.relative(d);
			boolean free = !f.equals(extra) && !hd.equals(extra)
				&& level.getBlockState(f).getCollisionShape(level, f).isEmpty() && level.getBlockState(hd).getCollisionShape(level, hd).isEmpty();
			if (free) open++;
		}
		return open == 0;
	}

	/** Places a duel block for {@code owner}. Returns false (and changes nothing) if the rules in the class comment forbid it. */
	public static boolean place(ServerLevel level, BlockPos pos, UUID owner, Entity ownerEntity, int lifetime) {
		if (!canPlace(level, pos, ownerEntity)) return false;
		level.setBlock(pos, DuelistContent.DUEL_BLOCK.defaultBlockState(), 3);
		long now = level.getGameTime();
		Ledger l = ledger(level);
		l.map.put(pos.asLong(), new Entry(pos.asLong(), owner.toString(), now + lifetime, now));
		l.setDirty();
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.HOSTILE, 0.6F, 1.4F);
		return true;
	}

	/** Removes every block this owner placed (boss death, reset, unload). */
	public static int clearOwner(ServerLevel level, UUID owner) {
		String o = owner.toString();
		return removeWhere(level, e -> e.owner().equals(o));
	}

	/** Removes every duel block in this world (server start, tests). */
	public static int clearAll(ServerLevel level) {
		return removeWhere(level, e -> true);
	}

	private static int removeWhere(ServerLevel level, java.util.function.Predicate<Entry> f) {
		Ledger l = ledger(level);
		int n = 0;
		for (Iterator<Entry> it = l.map.values().iterator(); it.hasNext();) {
			Entry e = it.next();
			if (!f.test(e)) continue;
			it.remove();
			if (vanish(level, BlockPos.of(e.pos()))) n++;
		}
		if (n > 0) l.setDirty();
		return n;
	}

	private static boolean vanish(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) return false;
		BlockState st = level.getBlockState(pos);
		level.destroyBlockProgress(pos.hashCode(), pos, -1);
		if (!st.is(DuelistContent.DUEL_BLOCK)) return false;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.02);
		level.removeBlock(pos, false);
		return true;
	}

	/** Called every server tick: expiry, cracks, and forgetting blocks a player broke. */
	static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			Ledger l = ledger(level);
			if (l.map.isEmpty()) continue;
			long now = level.getGameTime();
			boolean dirty = false;
			for (Iterator<Entry> it = l.map.values().iterator(); it.hasNext();) {
				Entry e = it.next();
				BlockPos pos = BlockPos.of(e.pos());
				if (!level.hasChunkAt(pos)) continue; // handled by the chunk load hook
				if (!level.getBlockState(pos).is(DuelistContent.DUEL_BLOCK)) {
					it.remove();
					dirty = true;
					continue;
				}
				long age = now - e.placed();
				if (now >= e.expires()) {
					it.remove();
					vanish(level, pos);
					dirty = true;
				} else if (age >= (e.expires() - e.placed()) * 5L / 6L && age % 5 == 0) {
					long crackFrom = (e.expires() - e.placed()) * 5L / 6L;
					int stage = (int) Math.min(9, (age - crackFrom) * 10L / Math.max(1, e.expires() - e.placed() - crackFrom));
					level.destroyBlockProgress(pos.hashCode(), pos, stage);
				}
			}
			if (dirty) l.setDirty();
		}
	}

	/** Removes the ledger blocks of one chunk (the unload hook). */
	public static void purgeChunk(ServerLevel level, ChunkPos cp) {
		Ledger l = ledger(level);
		if (l.map.isEmpty()) return;
		boolean dirty = false;
		for (Iterator<Entry> it = l.map.values().iterator(); it.hasNext();) {
			Entry e = it.next();
			BlockPos pos = BlockPos.of(e.pos());
			if ((pos.getX() >> 4) != cp.x() || (pos.getZ() >> 4) != cp.z()) continue;
			it.remove();
			vanish(level, pos);
			dirty = true;
		}
		if (dirty) l.setDirty();
	}

	private static boolean inited;

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerTickEvents.END_SERVER_TICK.register(DuelBlocks::tick);
		// vanish on unload and on load (a stale entry from a crash or an old save)
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> purgeChunk(level, chunk.getPos()));
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, gen) -> purgeChunk(level, chunk.getPos()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerLevel l : server.getAllLevels()) clearAll(l);
		});
	}
}
