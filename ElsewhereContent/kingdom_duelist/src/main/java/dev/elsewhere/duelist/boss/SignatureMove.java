package dev.elsewhere.duelist.boss;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * A boss's own move (two or three per boss), as data plus one method. HumanBoss schedules it: it is chosen when the
 * target is within {@code [minRange, maxRange]}, its cooldown is over and {@link #canUse} agrees, then wound up for
 * {@link #windup()} ticks (the telegraph rule is checked when the boss registers it) and {@link #fire} runs once.
 * Damage must go through {@link HumanBoss#hurtRaw} so the caps hold.
 */
public interface SignatureMove {
	String id();

	/** Shown on the boss bar after the name ("Name · Label"). */
	String label();

	Telegraph.Weight weight();

	int windup();

	int cooldown();

	/** The largest raw damage this move can deal (used for the telegraph rule and the cap check). */
	float raw();

	default double minRange() {
		return 0;
	}

	default double maxRange() {
		return 6;
	}

	/** Scheduling weight relative to other usable signatures. */
	default int priority() {
		return 10;
	}

	/** This move's own telegraph look, or null to use the boss's style. */
	default @Nullable TelegraphStyle style() {
		return null;
	}

	/** Pose and look of the wind-up: {@link #style()} or the boss's own style; the arm is raised unless this is a crouch move. */
	default Telegraph.Cue cue(HumanBoss boss) {
		TelegraphStyle s = style() != null ? style() : boss.style();
		return s.cue(false, true);
	}

	/**
	 * 3.0 P4/B4: the body-language key of this move ({@link MovePose}), synced as one byte when the wind-up starts. Default: the
	 * {@link MovePose} table for the id. Override to give a new move its own key without a table edit.
	 */
	default byte pose(HumanBoss boss) {
		Telegraph.Cue c = cue(boss);
		return MovePose.of(id(), c != null && c.crouch(), c != null && c.raised());
	}

	/** True: the boss stands still during the wind-up. */
	default boolean freeze() {
		return true;
	}

	/** Phase gate: usable when the boss phase is at least this. */
	default int minPhase() {
		return 0;
	}

	default boolean canUse(HumanBoss boss, LivingEntity target) {
		return true;
	}

	/** Called on the first tick of the wind-up (set a pose, lock a direction, mark a landing spot). */
	default void begin(HumanBoss boss, LivingEntity target) {}

	/** The move lands. The target may have dodged: re-check range and position here. */
	void fire(HumanBoss boss, LivingEntity target);

	/** Quick data-driven move. */
	static SignatureMove of(String id, String label, Telegraph.Weight w, int windup, int cooldown, float raw, double minRange, double maxRange, FireFn fn) {
		return new SignatureMove() {
			public String id() { return id; }
			public String label() { return label; }
			public Telegraph.Weight weight() { return w; }
			public int windup() { return windup; }
			public int cooldown() { return cooldown; }
			public float raw() { return raw; }
			public double minRange() { return minRange; }
			public double maxRange() { return maxRange; }
			public void fire(HumanBoss b, LivingEntity t) { fn.fire(b, t); }
		};
	}

	@FunctionalInterface
	interface FireFn {
		void fire(HumanBoss boss, LivingEntity target);
	}
}
