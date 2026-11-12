package dev.elsewhere.kingdom.armies.routine;

import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Routine poses (ARMIES.md 2.4 / POLISH P9). The routine engine sets a pose when a soldier or townsperson <b>arrives</b> at its
 * activity spot and clears it ({@link Pose#NONE}) when the activity ends, the squad fights or is hurt: a change, never a tick.
 * <p>The synced pose and its client animation belong to the P9 idle lane: it registers one {@link Sink} with {@link #register},
 * which maps these ids onto its own synced pose int (loops such as WORK, SPAR, CHEER and DRINK are animated client-side from the
 * pose). Until a sink handles a mob, a vanilla fallback keeps every pose visible: SIT, KNEEL, PRAY and LOUNGE crouch, the others
 * stand with one arm swing. The current pose is kept as the tag {@code elsewhere_rpose:<NAME>} so it survives restarts.
 */
public final class RoutinePoses {
	private RoutinePoses() {}

	/** Stable ids (the P9 sink maps them). */
	public enum Pose {
		NONE(0), SIT(1), KNEEL(2), SALUTE(3), WORK(4), SPAR(5), CHEER(6), TORCH(7), ATTENTION(8), CARRY(9), DRINK(10), LOUNGE(11), PRAY(12);

		public final int id;

		Pose(int id) {
			this.id = id;
		}

		public boolean low() {
			return this == SIT || this == KNEEL || this == PRAY || this == LOUNGE;
		}

		public static Pose byName(@Nullable String s, Pose def) {
			if (s == null || s.isEmpty()) return def;
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return def;
			}
		}

		public static Pose byId(int id) {
			for (Pose p : values()) if (p.id == id) return p;
			return NONE;
		}
	}

	/** The P9 lane's pose setter. Return true when it handled the mob (the vanilla fallback is then skipped). */
	@FunctionalInterface
	public interface Sink {
		boolean apply(Mob mob, Pose pose);
	}

	public static final String TAG = "elsewhere_rpose:";
	private static final List<Sink> SINKS = new ArrayList<>();
	/** Pose changes made (self-test and /kingdom armies). */
	public static long changes;

	public static synchronized void register(Sink s) {
		if (!SINKS.contains(s)) SINKS.add(s);
	}

	public static Pose get(Mob mob) {
		for (String t : mob.entityTags()) if (t.startsWith(TAG)) return Pose.byName(t.substring(TAG.length()), Pose.NONE);
		return Pose.NONE;
	}

	/** Sets {@code pose} on {@code mob} if it differs from the current one. */
	public static void set(Mob mob, Pose pose) {
		Pose old = get(mob);
		if (old == pose) return;
		if (old != Pose.NONE) mob.removeTag(TAG + old.name());
		if (pose != Pose.NONE) mob.addTag(TAG + pose.name());
		changes++;
		boolean handled = false;
		for (Sink s : SINKS) {
			try {
				handled |= s.apply(mob, pose);
			} catch (RuntimeException e) {
				dev.elsewhere.kingdom.KingdomContent.LOG.warn("routine pose sink failed for {}: {}", mob, e.toString());
			}
		}
		if (handled) return;
		// vanilla fallback: a resting soldier keeps its own crouch (RestGoal), everyone else crouches for the low poses
		boolean resting = mob instanceof KingdomSoldier ks && ks.isResting();
		if (!resting) {
			if (pose.low()) mob.setPose(net.minecraft.world.entity.Pose.CROUCHING);
			else if (old.low() || mob.getPose() == net.minecraft.world.entity.Pose.CROUCHING) mob.setPose(net.minecraft.world.entity.Pose.STANDING);
		}
		if (pose == Pose.SALUTE || pose == Pose.WORK || pose == Pose.SPAR || pose == Pose.CHEER || pose == Pose.TORCH) mob.swingForAttack(InteractionHand.MAIN_HAND);
	}

	public static void clear(Mob mob) {
		set(mob, Pose.NONE);
	}

	/** The pose an activity uses when its data names none. */
	public static Pose defaultFor(String activity, @Nullable String style) {
		if (style != null) switch (style) {
			case "butts", "quintain" -> {
				return Pose.WORK;
			}
			case "vigil", "linkboys" -> {
				return Pose.TORCH;
			}
			case "dawn_rite", "matins" -> {
				return Pose.PRAY;
			}
			case "crowd", "minstrels", "holmgang" -> {
				return Pose.CHEER;
			}
			case "queue" -> {
				return Pose.ATTENTION;
			}
			default -> {
			}
		}
		return switch (activity) {
			case "drill", "muster", "procession", "court" -> Pose.ATTENTION;
			case "market", "watch" -> Pose.NONE;
			case "work" -> Pose.WORK;
			case "temple" -> Pose.KNEEL;
			case "feast" -> Pose.SIT;
			case "spar" -> Pose.SPAR;
			case "tourney" -> Pose.CHEER;
			case "caravan" -> Pose.CARRY;
			case "lamplight" -> Pose.TORCH;
			case "siesta" -> Pose.LOUNGE;
			default -> Pose.NONE;
		};
	}
}
