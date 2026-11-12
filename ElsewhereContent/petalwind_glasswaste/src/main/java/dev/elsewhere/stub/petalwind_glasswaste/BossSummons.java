package dev.elsewhere.stub.petalwind_glasswaste;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Everything a boss spawns during its fight (minions, petal mirages, wind charges, effect clouds) is bound to it here, so
 * nothing outlives the fight: the boss clears its summons on death, arena reset and any removal (discard, unload), every
 * bound summon is discarded within half a second once its owner is gone, dead or out of combat, and none lives longer than
 * {@link #MAX_LIFETIME} ticks. Summons saved to disk carry {@link #TAG}; when one loads again without a live binding (server
 * restart, chunk reload after the fight) it is discarded on the next tick.
 */
public final class BossSummons {
	private BossSummons() {}

	public static final String TAG = "elsewhere.boss_summon";
	/** 3 minutes: longer than any summon's job in a fight, short enough that a stray one can never pile up. */
	public static final int MAX_LIFETIME = 3600;

	private record Bound(Entity summon, UUID owner, int born) {}

	private static final List<Bound> LIVE = new ArrayList<>();
	private static final List<Entity> ORPHANS = new ArrayList<>();
	/** Total summons each owner has spawned (self-test / QA counters). */
	private static final Map<UUID, Integer> SPAWNED = new HashMap<>();
	private static int serverTicks;
	private static boolean initialized;

	public static void init() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !isBound(entity)) ORPHANS.add(entity);
		});
	}

	/** Binds {@code summon} to {@code owner} and adds it to the level. Bind before adding, so the load hook sees the binding. */
	public static <T extends Entity> T spawn(Entity owner, ServerLevel level, T summon) {
		bind(owner, summon);
		level.addFreshEntity(summon);
		return summon;
	}

	public static void bind(Entity owner, Entity summon) {
		summon.addTag(TAG);
		LIVE.add(new Bound(summon, owner.getUUID(), serverTicks));
		SPAWNED.merge(owner.getUUID(), 1, Integer::sum);
	}

	/** Discards every live summon bound to {@code owner}; returns how many were removed. */
	public static int clear(Entity owner) {
		UUID id = owner.getUUID();
		int n = 0;
		for (Iterator<Bound> it = LIVE.iterator(); it.hasNext(); ) {
			Bound b = it.next();
			if (!b.owner.equals(id)) continue;
			it.remove();
			if (!b.summon.isRemoved()) {
				b.summon.discard();
				n++;
			}
		}
		return n;
	}

	/** Live (not removed) summons bound to {@code owner}. */
	public static int alive(Entity owner) {
		UUID id = owner.getUUID();
		int n = 0;
		for (Bound b : LIVE) if (b.owner.equals(id) && !b.summon.isRemoved()) n++;
		return n;
	}

	public static int spawnedBy(Entity owner) {
		return SPAWNED.getOrDefault(owner.getUUID(), 0);
	}

	private static boolean isBound(Entity e) {
		for (Bound b : LIVE) if (b.summon == e) return true;
		return false;
	}

	private static void tick() {
		serverTicks++;
		if (!ORPHANS.isEmpty()) {
			List<Entity> copy = new ArrayList<>(ORPHANS);
			ORPHANS.clear();
			for (Entity e : copy) if (!e.isRemoved() && !isBound(e)) e.discard();
		}
		if (serverTicks % 10 != 0 || LIVE.isEmpty()) return;
		List<Entity> drop = new ArrayList<>();
		for (Iterator<Bound> it = LIVE.iterator(); it.hasNext(); ) {
			Bound b = it.next();
			Entity s = b.summon;
			if (s.isRemoved()) {
				it.remove();
				continue;
			}
			Entity owner = s.level() instanceof ServerLevel level ? level.getEntity(b.owner) : null;
			boolean ownerGone = owner == null || owner.isRemoved() || !owner.isAlive()
				|| (owner instanceof BossBase boss && !boss.isFighting());
			if (ownerGone || serverTicks - b.born > MAX_LIFETIME) {
				it.remove();
				drop.add(s);
			}
		}
		for (Entity e : drop) e.discard();
	}
}
