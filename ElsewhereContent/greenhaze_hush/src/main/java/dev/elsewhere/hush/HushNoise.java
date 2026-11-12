package dev.elsewhere.hush;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Hush's vibration bus. Every vanilla game event in the Hush becomes a {@link Noise} (vibrations already exist, the
 * dimension just listens harder); blind hunters (Echo Stalkers, Shrieker Swarms, Auris) read it, and the Resonance meter
 * is fed from the player's own events. Decoys (Tuning Fork, thrown items) are loud, repeated and take priority.
 */
public final class HushNoise {
	private HushNoise() {}

	public record Noise(Vec3 pos, long time, int loudness, boolean decoy, @Nullable Entity source) {}

	private static final Map<ServerLevel, Deque<Noise>> NOISES = new HashMap<>();
	private static final Map<ServerLevel, List<Noise>> DECOYS = new HashMap<>();
	public static int posted;

	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			Deque<Noise> q = NOISES.get(level);
			long now = level.getGameTime();
			if (q != null) while (!q.isEmpty() && now - q.peekFirst().time() > 120) q.pollFirst();
			List<Noise> d = DECOYS.get(level);
			if (d != null) {
				d.removeIf(n -> now - n.time() > GearStats.FORK_DECOY_TICKS);
				if (now % 20 == 0) for (Noise n : d) post(level, n.pos(), null, 3, true);
			}
		});
	}

	public static void post(ServerLevel level, Vec3 pos, @Nullable Entity source, int loudness) {
		post(level, pos, source, loudness, false);
	}

	private static void post(ServerLevel level, Vec3 pos, @Nullable Entity source, int loudness, boolean decoy) {
		posted++;
		NOISES.computeIfAbsent(level, l -> new ArrayDeque<>()).addLast(new Noise(pos, level.getGameTime(), loudness, decoy, source));
	}

	/** A Tuning Fork (or other decoy) rings here for 5 s. */
	public static void decoy(ServerLevel level, Vec3 pos, @Nullable Entity owner) {
		Noise n = new Noise(pos, level.getGameTime(), 3, true, owner);
		DECOYS.computeIfAbsent(level, l -> new ArrayList<>()).add(n);
		post(level, pos, null, 3, true);
	}

	/** Newest noise within radius and maxAge ticks; active decoys win. */
	public static @Nullable Noise latest(ServerLevel level, Vec3 near, double radius, int maxAge) {
		long now = level.getGameTime();
		List<Noise> decoys = DECOYS.get(level);
		if (decoys != null) for (Noise d : decoys) if (d.pos().distanceToSqr(near) <= radius * radius) return d;
		Deque<Noise> q = NOISES.get(level);
		if (q == null) return null;
		for (var it = q.descendingIterator(); it.hasNext(); ) {
			Noise n = it.next();
			if (now - n.time() > maxAge) break;
			if (n.pos().distanceToSqr(near) <= radius * radius) return n;
		}
		return null;
	}

	/** Noises posted at or after a game time (for Auris's echo scheduler). */
	public static List<Noise> since(ServerLevel level, long time) {
		List<Noise> out = new ArrayList<>();
		Deque<Noise> q = NOISES.get(level);
		if (q != null) for (Noise n : q) if (n.time() >= time) out.add(n);
		return out;
	}

	/** 0-3 loudness of recent noise near a point (Sculk Lanterns). */
	public static int loudnessNear(ServerLevel level, Vec3 near, double radius, int maxAge) {
		Deque<Noise> q = NOISES.get(level);
		if (q == null) return 0;
		long now = level.getGameTime();
		int sum = 0;
		for (Noise n : q) if (now - n.time() <= maxAge && n.pos().distanceToSqr(near) <= radius * radius) sum += n.loudness();
		return Math.min(3, sum);
	}

	static int loudness(Holder<GameEvent> e) {
		if (e.is(GameEvent.EXPLODE) || e.is(GameEvent.INSTRUMENT_PLAY) || e.is(GameEvent.LIGHTNING_STRIKE)) return 4;
		if (e.is(GameEvent.PROJECTILE_LAND) || e.is(GameEvent.SHRIEK) || e.is(GameEvent.NOTE_BLOCK_PLAY)) return 3;
		if (e.is(GameEvent.BLOCK_DESTROY) || e.is(GameEvent.ENTITY_DAMAGE) || e.is(GameEvent.CONTAINER_OPEN) || e.is(GameEvent.HIT_GROUND)
			|| e.is(GameEvent.BLOCK_PLACE)) return 2;
		if (e.is(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING) || e.is(GameEvent.RESONATE_1)) return 0;
		return 1;
	}

	/** Called from the ServerLevel.gameEvent mixin for every game event. */
	public static void onGameEvent(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context ctx) {
		Entity src = ctx.sourceEntity();
		if (event.is(GameEvent.SHRIEK) && src instanceof ServerPlayer sp) HushTrips.onShriek(sp);
		if (!Hush.isHush(level)) return;
		int loud = loudness(event);
		if (loud == 0) return;
		// mobs' own footsteps and flaps would flood the bus; only players, projectiles and world events are heard
		if (loud <= 1 && src != null && !(src instanceof ServerPlayer)) return;
		if (src instanceof ServerPlayer p) {
			if (p.isSpectator()) return;
			if (event.is(GameEvent.BLOCK_DESTROY) && HushItems.isEchoTool(p.getMainHandItem())) return; // silent mining
			// Hushweave boots: walking and landing make no vibrations
			if ((event.is(GameEvent.STEP) || event.is(GameEvent.HIT_GROUND) || event.is(GameEvent.SWIM)) && p.getItemBySlot(EquipmentSlot.FEET).is(HushItems.HUSHWEAVE_BOOTS)) return;
			// Unheard: while sneaking in the full set you make no vibrations at all
			if (p.isShiftKeyDown() && HushItems.unheard(p)) return;
			ItemStack censer = p.getItemBySlot(EquipmentSlot.OFFHAND);
			if (censer.is(HushItems.SCULK_CENSER) && pos.distanceToSqr(p.position()) <= 16) {
				censer.hurtAndBreak(1, p, EquipmentSlot.OFFHAND);
				return; // absorbed like wool
			}
			if (HushItems.noiseFactor(p) <= 0) return;
			Resonance.onEvent(p, event);
		}
		post(level, pos, src, loud);
	}

	/** fix-stability: the bus is keyed by ServerLevel objects; drop a level when it unloads and everything when the server stops. */
	public static void forgetLevel(ServerLevel level) {
		NOISES.remove(level);
		DECOYS.remove(level);
	}

	public static void clearAll() {
		NOISES.clear();
		DECOYS.clear();
		posted = 0;
	}

	public static int trackedLevels() { return NOISES.size() + DECOYS.size(); }
}
