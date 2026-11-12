package dev.elsewhere.kingdom.armies.lod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client render LOD for armies (ARMIES.md 3.7), the rules only: no client classes here, so the server self-test can check them
 * ({@link LodSelfTest}). The client side ({@code dev.elsewhere.kingdom.client.lod}) applies them to soldier render states, particles and
 * sounds.
 * <ul>
 * <li><b>Distance tiers</b> (horizontal and vertical distance to the camera): {@link #FULL} up to 24 blocks (armour with trims, tabard,
 * cape, held items), {@link #NEAR} 24-48 (no trims, cape static), {@link #FAR} 48-96 (one baked LOD skin: skin, armour, tabard and trim
 * colour in one texture; only banners and spears held), {@link #BEYOND} past 96 (not drawn: the server folds battle squads into rank
 * impostors there). A soldier has to clear a boundary by {@link #HYSTERESIS} blocks before it changes tier, so nothing flickers.</li>
 * <li><b>Count cap</b>: at most 24 / 48 / 72 soldiers at full detail per frame (soldier detail Low / Medium / High), the nearest ones
 * ({@link Budget}); the rest drop to {@link #NEAR}.</li>
 * <li><b>Particles</b>: while a battle is in view ({@link #BATTLE_MIN_SOLDIERS} soldiers tracked), at most {@link #PARTICLE_BUDGET} combat
 * particles per second ({@link Window}) and crit/sweep/hit particles only within {@link #HIT_PARTICLE_RANGE} blocks.</li>
 * <li><b>Sounds</b>: at most {@link #SOUND_SLOTS} battle sounds playing at once ({@link Slots}); beyond {@link #DIN_RANGE} blocks single
 * hits are replaced by one rate-limited battle din ({@link Din}).</li>
 * </ul>
 * Kill switch: {@code "client_lod": false} in {@code config/elsewhere_armies.json} (or {@code -Delsewhere.armies.client_lod=false});
 * {@code "enabled": false} there turns it off too. Off = exactly the 2.1 "soldier detail" behaviour.
 */
public final class RenderLod {
	private RenderLod() {}

	public static final int FULL = 0, NEAR = 1, FAR = 2, BEYOND = 3;
	/** Outer edge (blocks) of FULL, NEAR and FAR. */
	public static final double[] EDGE = {24.0, 48.0, 96.0};
	public static final double HYSTERESIS = 2.0;
	public static final int PARTICLE_BUDGET = 400, HIT_PARTICLE_RANGE = 24, SOUND_SLOTS = 12, DIN_RANGE = 32, BATTLE_MIN_SOLDIERS = 16;
	/** Sounds and particles this close to the camera are the player's own fight: never limited. */
	public static final double OWN_RANGE = 6.0;
	/** The din plays at most once per this many ms. */
	public static final long DIN_GAP_MS = 350;

	// ---- kill switch ---------------------------------------------------------------------------------------------------------
	private static Boolean enabled;

	/** True when the client render LOD is on ({@code client_lod} and the armies {@code enabled} key, both default true). */
	public static boolean enabled() {
		if (enabled == null) load();
		return enabled;
	}

	/** For tests: force the switch. */
	public static void setEnabled(boolean on) {
		enabled = on;
	}

	/** Reads {@code client_lod} from {@code config/elsewhere_armies.json}, adding it (default true) when missing. */
	public static synchronized void load() {
		boolean on = true, armies = true;
		Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere_armies.json");
		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			JsonObject o = Files.exists(p) ? gson.fromJson(Files.readString(p), JsonObject.class) : null;
			if (o == null) o = new JsonObject();
			if (o.has("enabled")) armies = o.get("enabled").getAsBoolean();
			if (o.has("client_lod")) on = o.get("client_lod").getAsBoolean();
			else {
				o.addProperty("client_lod", true);
				Files.createDirectories(p.getParent());
				Files.writeString(p, gson.toJson(o));
			}
		} catch (Exception e) {
			dev.elsewhere.kingdom.KingdomContent.LOG.warn("elsewhere_armies.json unreadable for client_lod ({}); client LOD stays on", e.toString());
		}
		if (System.getProperty("elsewhere.armies.enabled") != null) armies = Boolean.getBoolean("elsewhere.armies.enabled");
		if (System.getProperty("elsewhere.armies.client_lod") != null) on = Boolean.getBoolean("elsewhere.armies.client_lod");
		enabled = on && armies;
	}

	// ---- tiers ---------------------------------------------------------------------------------------------------------------
	/** Tier by distance alone. */
	public static int rawTier(double d) {
		return d <= EDGE[0] ? FULL : d <= EDGE[1] ? NEAR : d <= EDGE[2] ? FAR : BEYOND;
	}

	/** Tier with hysteresis: leaving {@code previous} (or -1 for none) needs {@link #HYSTERESIS} blocks past its edge. */
	public static int tier(double distSq, int previous) {
		double d = Math.sqrt(distSq);
		int t = rawTier(d);
		if (previous < FULL || previous > BEYOND || t == previous) return t;
		if (t > previous) return d > EDGE[previous] + HYSTERESIS ? rawTier(d - HYSTERESIS) : previous;
		return d < EDGE[previous - 1] - HYSTERESIS ? rawTier(d + HYSTERESIS) : previous;
	}

	/** Full-detail soldiers per frame for soldier detail 0 = Low, 1 = Medium, 2 = High. */
	public static int fullDetailCap(int detail) {
		return detail <= 0 ? 24 : detail == 1 ? 48 : 72;
	}

	/** Cut-off (blocks) past which live soldiers are not drawn, scaled by the vanilla entity-distance slider. */
	public static double drawDistance(double entityDistanceScaling) {
		return EDGE[2] * Math.max(0.5, entityDistanceScaling);
	}

	// ---- the nearest-N full-detail budget ------------------------------------------------------------------------------------
	/**
	 * The count cap. During a frame every soldier that wants full detail {@link #offer}s its squared distance and asks {@link #grant};
	 * grants use the threshold from the previous frame (the cap-th nearest distance) plus a hard counter, so at most {@code cap} are
	 * granted and they are the nearest. A soldier already at full detail keeps it up to 10% past the threshold (no flicker at the edge).
	 */
	public static final class Budget {
		private float[] buf = new float[128];
		private int n, granted, cap = 48;
		private float threshold = Float.MAX_VALUE;

		public void offer(float distSq) {
			if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
			buf[n++] = distSq;
		}

		public boolean grant(float distSq, boolean wasFull) {
			float lim = wasFull && threshold != Float.MAX_VALUE ? threshold * 1.21F : threshold;
			if (distSq <= lim && granted < cap) {
				granted++;
				return true;
			}
			return false;
		}

		/** Ends a frame: the next frame's threshold is the {@code cap}-th smallest distance offered in this one. */
		public void endFrame(int newCap) {
			cap = Math.max(1, newCap);
			threshold = n <= cap ? Float.MAX_VALUE : kth(buf, n, cap - 1);
			n = 0;
			granted = 0;
		}

		public float threshold() {
			return threshold;
		}

		public int cap() {
			return cap;
		}
	}

	/** k-th smallest (0-based) of a[0..n), in place (quickselect). */
	public static float kth(float[] a, int n, int k) {
		int lo = 0, hi = n - 1;
		while (lo < hi) {
			float pivot = a[(lo + hi) >>> 1];
			int i = lo, j = hi;
			while (i <= j) {
				while (a[i] < pivot) i++;
				while (a[j] > pivot) j--;
				if (i <= j) {
					float t = a[i];
					a[i] = a[j];
					a[j] = t;
					i++;
					j--;
				}
			}
			if (k <= j) hi = j;
			else if (k >= i) lo = i;
			else return a[k];
		}
		return a[k];
	}

	// ---- particle budget: a sliding one-second window ------------------------------------------------------------------------
	/** At most {@code limit} acquisitions in the last {@code buckets * bucketMs} ms. O(1) per call, no allocation. */
	public static final class Window {
		private final int[] buckets;
		private final long bucketMs;
		private final int limit;
		private long head = Long.MIN_VALUE;
		private int sum;
		public long refused;

		public Window(int limit, int buckets, long bucketMs) {
			this.limit = limit;
			this.buckets = new int[buckets];
			this.bucketMs = bucketMs;
		}

		private void advance(long nowMs) {
			long b = Math.floorDiv(nowMs, bucketMs);
			if (head == Long.MIN_VALUE) {
				head = b;
				return;
			}
			if (b <= head) return;
			long steps = Math.min(b - head, buckets.length);
			for (long s = 1; s <= steps; s++) {
				int i = (int) Math.floorMod(head + s, (long) buckets.length);
				sum -= buckets[i];
				buckets[i] = 0;
			}
			head = b;
		}

		public boolean tryAcquire(long nowMs) {
			advance(nowMs);
			if (sum >= limit) {
				refused++;
				return false;
			}
			buckets[(int) Math.floorMod(head, (long) buckets.length)]++;
			sum++;
			return true;
		}

		public int inWindow(long nowMs) {
			advance(nowMs);
			return sum;
		}
	}

	// ---- concurrent sound slots ----------------------------------------------------------------------------------------------
	/** At most {@code size} things playing at once; a slot frees when its occupant stops being active. */
	public static final class Slots<T> {
		private final Object[] held;

		public Slots(int size) {
			held = new Object[size];
		}

		@SuppressWarnings("unchecked")
		public boolean tryTake(T x, Predicate<T> active) {
			int free = -1;
			for (int i = 0; i < held.length; i++) {
				if (held[i] != null && !active.test((T) held[i])) held[i] = null;
				if (held[i] == null && free < 0) free = i;
			}
			if (free < 0) return false;
			held[free] = x;
			return true;
		}

		@SuppressWarnings("unchecked")
		public int busy(Predicate<T> active) {
			int n = 0;
			for (Object o : held) if (o != null && active.test((T) o)) n++;
			return n;
		}

		public void clear() {
			Arrays.fill(held, null);
		}
	}

	// ---- battle din ----------------------------------------------------------------------------------------------------------
	/**
	 * Far battle sounds (past {@link #DIN_RANGE}) are not played one by one; they feed this accumulator, which asks for one din sound at
	 * most every {@link #DIN_GAP_MS} ms at the running centre of the far fighting, louder when more is going on.
	 */
	public static final class Din {
		public double x, y, z;
		private int pending;
		private long last = Long.MIN_VALUE / 2;
		public long played, absorbed;

		/** Records one suppressed far sound; returns true when a din should play now (then read {@link #x}/{@link #volume}). */
		public boolean feed(double sx, double sy, double sz, long nowMs) {
			absorbed++;
			if (pending == 0) {
				x = sx;
				y = sy;
				z = sz;
			} else {
				x += (sx - x) * 0.25;
				y += (sy - y) * 0.25;
				z += (sz - z) * 0.25;
			}
			pending++;
			if (nowMs - last < DIN_GAP_MS) return false;
			last = nowMs;
			played++;
			return true;
		}

		/** Volume 0.35-1.0 by how many far hits were absorbed since the last din; resets the count. */
		public float takeVolume() {
			float v = (float) Math.min(1.0, 0.35 + 0.08 * pending);
			pending = 0;
			return v;
		}
	}
}
