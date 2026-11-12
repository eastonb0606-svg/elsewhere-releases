package dev.elsewhere.kingdom_villages.dev;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dev only (fx-siegeperf): a low-rate stack sampler of the server thread during the full-siege bench window, so the
 * self-test log names where the tick goes (info lines only, never a check). One daemon thread, one stack every few ms,
 * only while the server thread is inside a measured tick.
 */
final class SiegeSampler {
	private SiegeSampler() {}

	static volatile Thread server;
	static volatile boolean inTick, running;
	static final Map<String, Integer> INCL = new HashMap<>(), SELF = new HashMap<>();
	static int samples;
	static volatile int tickId;
	static final Map<Integer, List<Set<String>>> PER_TICK = new HashMap<>();

	static synchronized void start(Thread serverThread) {
		server = serverThread;
		INCL.clear();
		SELF.clear();
		samples = 0;
		PER_TICK.clear();
		if (running) return;
		running = true;
		Thread t = new Thread(SiegeSampler::loop, "elsewhere-siege-sampler");
		t.setDaemon(true);
		t.start();
	}

	static void stop() {
		running = false;
	}

	private static void loop() {
		while (running) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				return;
			}
			Thread s = server;
			if (s == null || !inTick) continue;
			StackTraceElement[] st = s.getStackTrace();
			if (!inTick || st.length == 0) continue;
			synchronized (SiegeSampler.class) {
				samples++;
				Set<String> seen = new HashSet<>();
				for (int i = 0; i < st.length; i++) {
					String k = name(st[i]);
					if (seen.add(k)) INCL.merge(k, 1, Integer::sum);
				}
				PER_TICK.computeIfAbsent(tickId, k -> new ArrayList<>()).add(seen);
				for (StackTraceElement e : st) {
					String c = e.getClassName();
					if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.")) continue;
					SELF.merge(name(e), 1, Integer::sum);
					break;
				}
			}
		}
	}

	private static String name(StackTraceElement e) {
		String c = e.getClassName();
		return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName();
	}

	/** The report: inclusive shares between 2% and 95% (the tick roots are 100%), then the top self frames. */
	static synchronized List<String> report() {
		List<String> out = new ArrayList<>();
		int n = Math.max(1, samples);
		StringBuilder b = new StringBuilder("SAMPLER " + samples + " samples; inclusive:");
		INCL.entrySet().stream().filter(e -> e.getValue() * 100 / n >= 2 && e.getValue() * 100 / n <= 95)
			.sorted((a, c) -> c.getValue() - a.getValue()).limit(60)
			.forEach(e -> b.append(' ').append(e.getKey()).append('=').append(e.getValue() * 100 / n).append('%'));
		out.add(b.toString());
		StringBuilder s = new StringBuilder("SAMPLER self:");
		SELF.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(25)
			.forEach(e -> s.append(' ').append(e.getKey()).append('=').append(e.getValue() * 100 / n).append('%'));
		out.add(s.toString());
		// top 5% ticks by sample count: where do the spikes go
		List<Map.Entry<Integer, List<Set<String>>>> ticks = new ArrayList<>(PER_TICK.entrySet());
		ticks.sort((a, c) -> c.getValue().size() - a.getValue().size());
		int top = Math.max(1, ticks.size() / 20), tot = 0;
		Map<String, Integer> hot = new HashMap<>();
		for (int i = 0; i < top && i < ticks.size(); i++) for (Set<String> ss : ticks.get(i).getValue()) {
			tot++;
			for (String k : ss) hot.merge(k, 1, Integer::sum);
		}
		StringBuilder h = new StringBuilder("SAMPLER top-5% ticks (" + top + " of " + ticks.size() + ", " + tot + " samples, sizes " + ticks.get(0).getValue().size() + ".." + ticks.get(Math.min(top, ticks.size()) - 1).getValue().size() + "); inclusive:");
		final int tt = Math.max(1, tot);
		hot.entrySet().stream().filter(e -> e.getValue() * 100 / tt >= 3 && e.getValue() * 100 / tt <= 95).sorted((a, c) -> c.getValue() - a.getValue()).limit(60)
			.forEach(e -> h.append(' ').append(e.getKey()).append('=').append(e.getValue() * 100 / tt).append('%'));
		out.add(h.toString());
		return out;
	}
}
