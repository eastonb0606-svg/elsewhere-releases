package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * Soldier projectile hygiene (ARMIES.md 3.4 / 9.5): arrows and bolts shot by kingdom soldiers can't be picked up and are removed
 * once they have rested for {@link #STUCK_LIFE} ticks (vanilla keeps a stuck arrow 1,200 ticks). At most {@link #PER_AREA} soldier
 * arrows per 64-block area: a new one retires the oldest resting arrow nearby. Event-driven: arrows are tracked when they enter
 * the world and the tracked set (never the world) is swept every 20 ticks.
 */
public final class ArrowHygiene {
	private ArrowHygiene() {}

	public static final int STUCK_LIFE = 60, PER_AREA = 40, SWEEP = 20;
	private static final double AREA2 = 64.0 * 64.0;

	private static final class Track {
		Vec3 last;
		int restTicks;
		final long born;

		Track(Vec3 p, long born) {
			this.last = p;
			this.born = born;
		}
	}

	private static final Map<AbstractArrow, Track> TRACKED = new LinkedHashMap<>();
	/** Counters for /kingdom armies and the self-test. */
	public static long tracked, retired, retiredForCap, pickupBlocked, maxLive;
	/** Longest time (ticks) a resting soldier arrow was seen before removal. */
	public static int maxRestSeen;

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof AbstractArrow a && a.getOwner() instanceof KingdomSoldier) onSoldierArrow(a, level.getGameTime());
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof AbstractArrow a) TRACKED.remove(a);
		});
		ServerTickEvents.END_SERVER_TICK.register(ArrowHygiene::sweep);
	}

	static void onSoldierArrow(AbstractArrow a, long now) {
		if (a.pickup != AbstractArrow.Pickup.DISALLOWED) {
			a.pickup = AbstractArrow.Pickup.DISALLOWED;
			pickupBlocked++;
		}
		if (TRACKED.containsKey(a)) return;
		// area cap: count tracked arrows near this one, retire the oldest resting one if full
		int near = 0;
		AbstractArrow oldestResting = null;
		for (Map.Entry<AbstractArrow, Track> t : TRACKED.entrySet()) {
			AbstractArrow o = t.getKey();
			if (o.level() != a.level() || o.distanceToSqr(a) > AREA2) continue;
			near++;
			if (oldestResting == null && t.getValue().restTicks > 0) oldestResting = o;
		}
		if (near >= PER_AREA && oldestResting != null) {
			TRACKED.remove(oldestResting);
			oldestResting.discard();
			retiredForCap++;
		}
		TRACKED.put(a, new Track(a.position(), now));
		tracked++;
		maxLive = Math.max(maxLive, TRACKED.size());
	}

	static void sweep(MinecraftServer srv) {
		if (TRACKED.isEmpty() || srv.getTickCount() % SWEEP != 0) return;
		List<AbstractArrow> drop = new ArrayList<>();
		for (Iterator<Map.Entry<AbstractArrow, Track>> it = TRACKED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<AbstractArrow, Track> e = it.next();
			AbstractArrow a = e.getKey();
			if (a.isRemoved()) {
				it.remove();
				continue;
			}
			Track t = e.getValue();
			Vec3 p = a.position();
			if (p.distanceToSqr(t.last) < 1.0E-4) t.restTicks += SWEEP;
			else t.restTicks = 0;
			t.last = p;
			if (t.restTicks >= STUCK_LIFE) {
				maxRestSeen = Math.max(maxRestSeen, t.restTicks);
				drop.add(a);
				it.remove();
			}
		}
		for (AbstractArrow a : drop) {
			a.discard();
			retired++;
		}
	}

	public static int live() {
		return TRACKED.size();
	}

	/** Self-test: true when every tracked soldier arrow is unpickable. */
	public static boolean allUnpickable() {
		for (AbstractArrow a : TRACKED.keySet()) if (a.pickup != AbstractArrow.Pickup.DISALLOWED) return false;
		return true;
	}

	static void reset() {
		TRACKED.clear();
	}
}
