package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.armies.routine.RoutineDirector;
import dev.elsewhere.kingdom.armies.routine.RoutinePlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Kingdom routines (ARMIES.md 2.4 / 2.6 / 9.8, kill switch {@code routine}): the hour detector and the soldier-tag helpers. The
 * engine itself is {@link RoutineDirector} (package {@code armies.routine}): every kingdom's full day from {@code army.routine}
 * (watch changes, drill, market, work, court, temple, feast, spar, tourney, caravan, lamplight, muster, procession, siesta and
 * the festivals), carried out at the layout anchors with poses, and announced to the townsfolk.
 * <p>Event-driven: the only per-tick work is one compare of the overworld clock's hour index; the engine acts when it changes,
 * and its timer queue runs every 20 ticks only while it has entries.
 * <p>Who moves: squads whose soldiers carry {@code elsewhere_court_garrison} (the court posts) or the opt-in tag
 * {@code elsewhere_armies_routine}, of a kingdom with an {@code army.routine}, not virtual, not fighting, hunting or following a
 * commander, and whose objective is unset or was set by the routine (another lane's order always wins; the court's drill loop
 * keeps its squad). The post is a soldier tag and saved data, so it survives restarts and LOD.
 */
public final class ArmyRoutine {
	private ArmyRoutine() {}

	public static final String OPT_IN = "elsewhere_armies_routine", COURT = "elsewhere_court_garrison", POST = "elsewhere_post:", FORM = "elsewhere_form:";
	public static long passes, moved, returned, teleported, skippedBusy;
	private static long lastHour = Long.MIN_VALUE;

	static void init() {
		RoutineDirector.init();
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			ServerLevel lv = srv.overworld();
			long t = lv.getOverworldClockTime();
			long h = Math.floorDiv(t, 1000L);
			if (h != lastHour) {
				boolean first = lastHour == Long.MIN_VALUE;
				lastHour = h;
				if (!first) onHour(lv, Math.floorDiv(t, 24000L), (int) Math.floorMod(h + 6, 24L));
			}
			if (srv.getTickCount() % 20 == 11 && RoutineDirector.busy()) RoutineDirector.tick20(lv);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(srv -> lastHour = Long.MIN_VALUE);
	}

	/** True when kingdom {@code s} changes something for its squads at {@code hour} of {@code day}. */
	static boolean trigger(ArmyScale s, long day, int hour) {
		RoutinePlan.Plan p = RoutinePlan.of(s.faction());
		return p != null && RoutinePlan.trigger(p, day, hour);
	}

	/** One pass over the kingdoms and squads at an hour change (also called by the self-tests). */
	public static int onHour(ServerLevel lv, long day, int hour) {
		return RoutineDirector.onHour(lv, day, hour);
	}

	/** Gives one squad its order for {@code hour} of {@code day} now (no ward offset). True when it was given one. */
	public static boolean apply(ServerLevel lv, Squad q, long day, int hour) {
		RoutinePlan.Plan p = RoutinePlan.of(q.faction());
		return p != null && RoutineDirector.apply(lv, q, p, day, hour, new HashMap<>());
	}

	/** Our anchors are within 15 blocks of the post on the horizontal (3.0 v1 orders; newer ones are in RoutineState). */
	public static boolean isOurAnchor(BlockPos post, BlockPos obj) {
		int dx = obj.getX() - post.getX(), dz = obj.getZ() - post.getZ();
		return dx * dx + dz * dz <= 15 * 15 && Math.abs(obj.getY() - post.getY()) <= 4;
	}

	public static List<Mob> all(@Nullable Mob lead, List<KingdomSoldier> mem) {
		List<Mob> l = new ArrayList<>(mem);
		if (lead != null && !l.contains(lead)) l.add(0, lead);
		return l;
	}

	public static boolean hasTag(@Nullable Mob lead, List<KingdomSoldier> mem, String tag) {
		if (lead != null && lead.entityTags().contains(tag)) return true;
		for (KingdomSoldier s : mem) if (s.entityTags().contains(tag)) return true;
		return false;
	}

	public static @Nullable String readTag(@Nullable Mob lead, List<KingdomSoldier> mem, String prefix) {
		for (Mob m : all(lead, mem)) for (String t : m.entityTags()) if (t.startsWith(prefix)) return t.substring(prefix.length());
		return null;
	}

	public static @Nullable BlockPos readPos(@Nullable Mob lead, List<KingdomSoldier> mem) {
		String v = readTag(lead, mem, POST);
		if (v == null) return null;
		String[] p = v.split(",");
		try {
			return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Squads the routine is currently steering (for /kingdom armies). */
	public static int active(ServerLevel lv) {
		int n = 0;
		for (Squad q : KingdomState.get(lv).squads()) if (q.activitySpots != null) n++;
		return n;
	}
}
