package dev.elsewhere.petalwind_glasswaste;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Tiny delayed-task queue on the server thread (used for temporary blocks such as Gale Heart updrafts). */
public final class Tasks {
	private Tasks() {}

	private record Task(long due, Runnable action) {}

	private static final List<Task> TASKS = new ArrayList<>();
	private static long now;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			now = server.getTickCount();
			if (TASKS.isEmpty()) return;
			List<Task> due = new ArrayList<>();
			for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
				Task t = it.next();
				if (t.due <= now) {
					due.add(t);
					it.remove();
				}
			}
			for (Task t : due) t.action.run();
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			List<Task> all = new ArrayList<>(TASKS);
			TASKS.clear();
			for (Task t : all) t.action.run(); // restore temporary blocks before the world saves
		});
	}

	public static void later(MinecraftServer server, int ticks, Runnable action) {
		TASKS.add(new Task(server.getTickCount() + ticks, action));
	}
}
