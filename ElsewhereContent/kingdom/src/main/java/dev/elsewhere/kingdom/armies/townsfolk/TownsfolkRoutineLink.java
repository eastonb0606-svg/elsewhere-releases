package dev.elsewhere.kingdom.armies.townsfolk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The townsfolk's only link to the routine engine ({@code dev.elsewhere.kingdom.armies.routine}, s-routines, ROUTINES-API.md): the
 * kingdom-wide townsfolk assignments of an hour (market, temple, feast, festivals), place resolution and poses. Pure calls, cached per
 * kingdom and hour; any failure falls back to the role schedules alone (logged once), so the townsfolk never depend on it to work.
 */
final class TownsfolkRoutineLink {
	private TownsfolkRoutineLink() {}

	/** One kingdom-wide assignment for townsfolk: {@code share} of the live residents take part. */
	record Assignment(String activity, String where, double share, String pose) {}

	/** The assignments in force; {@code event} is true during an event of the day (festival, muster, procession). */
	record Plan(List<Assignment> assignments, boolean event) {
		double totalShare() {
			double s = 0;
			for (Assignment a : assignments) s += a.share;
			return Math.min(1.0, s);
		}
	}

	static final Plan NONE = new Plan(List.of(), false);
	private static final Map<String, Plan> CACHE = new HashMap<>();
	private static boolean warned;

	static void clearCache() {
		CACHE.clear();
	}

	static Plan plan(Identifier faction, long day, int hour) {
		return CACHE.computeIfAbsent(faction + "|" + day + "|" + hour, k -> lookup(faction, day, hour));
	}

	private static Plan lookup(Identifier faction, long day, int hour) {
		try {
			var h = dev.elsewhere.kingdom.armies.routine.RoutinePlan.hour(faction, day, hour);
			if (h == null) return NONE;
			List<Assignment> out = new ArrayList<>();
			for (var a : h.townsfolk()) {
				double share = ((Number) a.share()).doubleValue();
				if (share <= 0) continue;
				out.add(new Assignment(String.valueOf(a.activity()), String.valueOf(a.where()), Math.min(1.0, share), String.valueOf(a.pose())));
			}
			return new Plan(List.copyOf(out), h.eventId() != null);
		} catch (RuntimeException | LinkageError e) {
			warn(e);
			return NONE;
		}
	}

	/** The routine engine's place for {@code where} near {@code near}, or null (then the townsfolk's own fallback anchor is used). */
	static @Nullable BlockPos resolve(ServerLevel lv, Identifier faction, BlockPos near, String where, String activity) {
		try {
			Object r = dev.elsewhere.kingdom.armies.routine.RoutineAnchors.resolve(lv, faction, near, where, activity);
			if (r instanceof BlockPos p) return p;
			if (r instanceof java.util.Optional<?> o && o.orElse(null) instanceof BlockPos p2) return p2;
			return null;
		} catch (RuntimeException | LinkageError e) {
			warn(e);
			return null;
		}
	}

	/** Sets a routine pose by name ({@code NONE} clears it). True when the routine engine took it. */
	static boolean setPose(Mob mob, String name) {
		try {
			dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose p;
			try {
				p = dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose.valueOf(name);
			} catch (IllegalArgumentException bad) {
				p = dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose.NONE;
			}
			dev.elsewhere.kingdom.armies.routine.RoutinePoses.set(mob, p);
			return p != dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose.NONE;
		} catch (RuntimeException | LinkageError e) {
			warn(e);
			return false;
		}
	}

	private static void warn(Throwable e) {
		if (warned) return;
		warned = true;
		dev.elsewhere.kingdom.KingdomContent.LOG.warn("Townsfolk: routine engine call failed ({}); townsfolk follow their role schedules only", e.toString());
	}
}
