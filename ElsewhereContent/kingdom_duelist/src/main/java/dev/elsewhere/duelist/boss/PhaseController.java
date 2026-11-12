package dev.elsewhere.duelist.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.Nullable;

/**
 * HP thresholds (X10) and the last-stand hook. Phase 0 is full health; each threshold crossed moves to the next phase. A
 * phase change resets every move cooldown (HumanBoss does that) and fires the one-shot events registered with {@link #onEnter}.
 * {@link #onLethalDamage} lets each ruler have its own last stand: the hook runs when a hit would kill the boss and may
 * keep it alive (there is no shared Totem).
 */
public final class PhaseController {
	/**
	 * Runs when damage would kill the boss. Return true to keep it alive: the hook must leave HP above zero (for example with
	 * {@link HumanBoss#lastStand}). In duel mode it runs when a hit would take the boss to the 10 percent yield line, before the boss
	 * can yield; once its uses are spent the boss yields. Never called while the boss is yielding.
	 */
	@FunctionalInterface
	public interface LethalHook {
		boolean onLethal(HumanBoss boss, ServerLevel level, DamageSource source, float amount);
	}

	private final double[] thresholds;
	private final List<IntConsumer> listeners = new ArrayList<>();
	private @Nullable LethalHook lethal;
	private int lethalMax = 1, lethalUses;
	private int phase;

	/** @param thresholds descending HP fractions, e.g. {0.66, 0.33}; phase = number of thresholds the HP has fallen to. */
	public PhaseController(double... thresholds) {
		this.thresholds = thresholds;
	}

	public int phase() {
		return phase;
	}

	public int phases() {
		return thresholds.length + 1;
	}

	/** Runs {@code r} (once) each time the boss enters {@code phase}. */
	public void onEnter(int phase, Runnable r) {
		listeners.add(p -> {
			if (p == phase) r.run();
		});
	}

	/** Sets this boss's last stand: {@code hook} may run at most {@code maxUses} times per fight (counted when it returns true). */
	public void onLethalDamage(LethalHook hook, int maxUses) {
		this.lethal = hook;
		this.lethalMax = maxUses;
	}

	public void onLethalDamage(LethalHook hook) {
		onLethalDamage(hook, 1);
	}

	public boolean hasLastStand() {
		return lethal != null;
	}

	/** How many times a last stand has kept the boss alive. */
	public int lastStandUses() {
		return lethalUses;
	}

	/** Called for lethal damage. True = the boss survives. */
	boolean lethal(HumanBoss boss, ServerLevel level, DamageSource src, float amount) {
		if (lethal == null || lethalUses >= lethalMax) return false;
		if (!lethal.onLethal(boss, level, src, amount)) return false;
		lethalUses++;
		return true;
	}

	void restore(int p, int lethalUses) {
		this.phase = p;
		this.lethalUses = lethalUses;
	}

	/** Returns the new phase if it changed, else -1. */
	int update(double hpFrac) {
		int p = 0;
		for (double t : thresholds) if (hpFrac <= t) p++;
		if (p > phase) {
			phase = p;
			for (IntConsumer c : List.copyOf(listeners)) c.accept(p);
			return p;
		}
		return -1;
	}

	/** Forces the last phase (a last stand may do this). */
	int forceLast() {
		if (phase >= thresholds.length) return -1;
		phase = thresholds.length;
		for (IntConsumer c : List.copyOf(listeners)) c.accept(phase);
		return phase;
	}
}
