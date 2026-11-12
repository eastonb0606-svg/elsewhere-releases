package dev.elsewhere.kingdom.armies.routine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Hour-change hooks of the routine engine. {@link Listener}s (the townsfolk lane) are called once per kingdom per in-game hour
 * change with that kingdom's {@link Hour}: never per tick. {@link Condition}s gate events in the data ({@code "if": "raid_pending"}),
 * registered by the lane that knows the answer; an unregistered condition is false, except the built-in {@code treasury_low}
 * (a stable roll per kingdom and day, 1 in 3) so Mirewick's Pay Day brawl shows without the court economy.
 */
public final class RoutineEvents {
	private RoutineEvents() {}

	/** What one group does this hour: activity, layout anchor id, the share taking part, the pose on arrival and a style. */
	public record Assignment(String activity, String where, double share, RoutinePoses.Pose pose, String style) {}

	/**
	 * A kingdom's hour: the event in force (null for an ordinary day), the soldiers' main assignment and the townsfolk's
	 * assignments (shares of the live townsfolk; the rest keep to their homes).
	 */
	public record Hour(Identifier faction, long day, int hour, @Nullable String eventId, Assignment soldiers, List<Assignment> townsfolk,
		boolean watchChange) {}

	@FunctionalInterface
	public interface Listener {
		void onHour(ServerLevel level, Hour hour);
	}

	@FunctionalInterface
	public interface Condition {
		boolean test(ServerLevel level, Identifier faction, long day);
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static final Map<String, Condition> CONDITIONS = new HashMap<>();
	/** Listener calls made (self-test). */
	public static long fired;

	public static synchronized void register(Listener l) {
		if (!LISTENERS.contains(l)) LISTENERS.add(l);
	}

	public static synchronized void unregister(Listener l) {
		LISTENERS.remove(l);
	}

	public static synchronized void registerCondition(String id, Condition c) {
		CONDITIONS.put(id, c);
	}

	public static boolean test(ServerLevel level, Identifier faction, long day, @Nullable String id) {
		if (id == null || id.isEmpty()) return true;
		Condition c;
		synchronized (RoutineEvents.class) {
			c = CONDITIONS.get(id);
		}
		if (c != null) {
			try {
				return c.test(level, faction, day);
			} catch (RuntimeException e) {
				return false;
			}
		}
		if (id.equals("treasury_low")) return RoutinePlan.roll(faction.hashCode(), day, 77) < 1.0 / 3;
		return false;
	}

	static void fire(ServerLevel level, Hour h) {
		List<Listener> ls;
		synchronized (RoutineEvents.class) {
			if (LISTENERS.isEmpty()) return;
			ls = List.copyOf(LISTENERS);
		}
		for (Listener l : ls) {
			try {
				l.onHour(level, h);
				fired++;
			} catch (RuntimeException e) {
				dev.elsewhere.kingdom.KingdomContent.LOG.warn("routine hour listener failed: {}", e.toString());
			}
		}
	}
}
