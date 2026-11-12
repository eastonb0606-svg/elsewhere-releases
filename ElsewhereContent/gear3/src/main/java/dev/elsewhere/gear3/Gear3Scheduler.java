package dev.elsewhere.gear3;

import java.util.PriorityQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * One-shot delayed tasks (frost damage over time, Starfall stars, Glimmer Lantern expiry and stale-light clean-up). A min-heap keyed on the server tick:
 * each tick looks at the head only (O(1) when nothing is due), so this is a timer, not a scan.
 */
public final class Gear3Scheduler {
	private Gear3Scheduler() {}

	private record Task(long due, long seq, Runnable run) implements Comparable<Task> {
		@Override
		public int compareTo(Task o) {
			return due != o.due ? Long.compare(due, o.due) : Long.compare(seq, o.seq);
		}
	}

	private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>();
	private static long seq;
	private static long now;
	private static boolean hooked;

	public static void init() {
		if (hooked) return;
		hooked = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			now = server.getTickCount();
			while (!QUEUE.isEmpty() && QUEUE.peek().due <= now) QUEUE.poll().run.run();
		});
		// Glimmer Lantern lights clear themselves on stop (Gear3LightBlocks.init) and heal on chunk load after a crash.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> QUEUE.clear());
	}

	/** Queues {@code r} to run {@code ticks} ticks from now; the returned handle can be passed to {@link #cancel}. */
	public static Object after(MinecraftServer server, int ticks, Runnable r) {
		now = server.getTickCount();
		Task t = new Task(now + ticks, seq++, r);
		QUEUE.add(t);
		return t;
	}

	/** Drops a task queued by {@link #after} that has not run yet; false when it already ran or was never queued. */
	public static boolean cancel(Object handle) {
		return handle instanceof Task t && QUEUE.remove(t);
	}

	public static int pending() {
		return QUEUE.size();
	}

	/** Self-test: run every queued task, now. */
	public static void flush(MinecraftServer server) {
		while (!QUEUE.isEmpty()) QUEUE.poll().run.run();
	}

	/** Self-test: run, now, the tasks that are due within {@code ticks} ticks (later ones stay queued). */
	public static int runDueWithin(MinecraftServer server, int ticks) {
		long until = server.getTickCount() + ticks;
		int n = 0;
		while (!QUEUE.isEmpty() && QUEUE.peek().due <= until) {
			QUEUE.poll().run.run();
			n++;
		}
		return n;
	}
}
