package dev.elsewhere.nether_end_bosses;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/** A tiny tick-driven script runner for the self-test: run steps, wait ticks, wait for conditions with a timeout. */
public final class TickScript {
	private interface Step {
		/** Returns true when finished. */
		boolean tick();
	}

	private final Deque<Step> steps = new ArrayDeque<>();
	private Step current;
	private final java.util.function.Consumer<String> onTimeout;
	private final TickScript root;

	public TickScript(java.util.function.Consumer<String> onTimeout) {
		this.onTimeout = onTimeout;
		this.root = this;
	}

	private TickScript(TickScript root) {
		this.onTimeout = root.onTimeout;
		this.root = root;
	}

	public TickScript run(Runnable r) {
		steps.add(() -> {
			r.run();
			return true;
		});
		return this;
	}

	public TickScript waitTicks(int ticks) {
		int[] left = {ticks};
		steps.add(() -> --left[0] <= 0);
		return this;
	}

	public TickScript waitUntil(String what, BooleanSupplier cond, int timeout) {
		int[] left = {timeout};
		steps.add(() -> {
			if (cond.getAsBoolean()) return true;
			if (--left[0] <= 0) {
				onTimeout.accept(what);
				return true;
			}
			return false;
		});
		return this;
	}

	/** Inserts steps produced at run time right after the current one. */
	private void runNow(java.util.function.Consumer<TickScript> builder) {
		TickScript sub = new TickScript(root);
		builder.accept(sub);
		Deque<Step> rest = new ArrayDeque<>(root.steps);
		root.steps.clear();
		root.steps.addAll(sub.steps);
		root.steps.addAll(rest);
	}

	/** Builds more steps when this point is reached (so they can use values set by earlier steps). */
	public TickScript then(java.util.function.Consumer<TickScript> builder) {
		steps.add(() -> {
			root.runNow(builder);
			return true;
		});
		return this;
	}

	public boolean done() {
		return current == null && steps.isEmpty();
	}

	public void tick() {
		for (int guard = 0; guard < 64; guard++) {
			if (current == null) current = steps.poll();
			if (current == null) return;
			boolean finished;
			try {
				finished = current.tick();
			} catch (Throwable t) {
				onTimeout.accept("step crashed: " + t);
				NetherEndBossesContent.LOGGER.error("[selftest] step crashed", t);
				finished = true;
			}
			if (!finished) return;
			current = null;
		}
	}
}
