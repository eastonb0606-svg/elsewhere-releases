package dev.elsewhere.duelist.kit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Tiny server-tick scheduler for afterimages, patches and delayed effects. Cleared when the server stops. */
public final class Timers {
	private Timers() {}

	private record Task(long at, Runnable r) {}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> ADDED = new ArrayList<>();
	private static long tick;
	private static boolean inited;

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			tick = s.getTickCount();
			if (!ADDED.isEmpty()) {
				TASKS.addAll(ADDED);
				ADDED.clear();
			}
			for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
				Task t = it.next();
				if (tick >= t.at()) {
					it.remove();
					try {
						t.r().run();
					} catch (RuntimeException e) {
						// a failed effect must not stop the others
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			TASKS.clear();
			ADDED.clear();
		});
	}

	/** Runs {@code r} after {@code ticks} server ticks. */
	public static void after(net.minecraft.server.MinecraftServer server, int ticks, Runnable r) {
		ADDED.add(new Task(server.getTickCount() + ticks, r));
	}

	public static int pending() {
		return TASKS.size() + ADDED.size();
	}
}
