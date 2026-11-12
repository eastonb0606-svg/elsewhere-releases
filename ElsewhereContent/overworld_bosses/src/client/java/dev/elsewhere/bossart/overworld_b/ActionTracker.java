package dev.elsewhere.bossart.overworld_b;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Client-side follow-through tracking: a boss attack's strike happens on the tick its synced action id drops back to
 * 0, so the wind-up animation (driven by the entity's AnimationState) is followed by a strike animation started here.
 * Also tracks a value's change time (tentacle states). Keyed weakly by entity, nothing to clean up.
 */
public final class ActionTracker {
	private final Map<Entity, Track> tracks = new WeakHashMap<>();

	public static final class Track {
		public int last = Integer.MIN_VALUE;
		public float lastStart;
		public int prev = Integer.MIN_VALUE;
		public float start;
	}

	/** Records value {@code v} at time {@code age}; returns the track (prev = the value before the latest change). */
	public Track update(Entity e, int v, float age) {
		Track t = this.tracks.computeIfAbsent(e, k -> new Track());
		if (t.last != v) {
			t.prev = t.last;
			t.last = v;
			t.start = (t.prev == Integer.MIN_VALUE) ? age - 1000.0F : age;
		}
		return t;
	}

	public static long millis(float age, float start) {
		return (long) Math.max(0.0F, (age - start) * 50.0F);
	}
}
