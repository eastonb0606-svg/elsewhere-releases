package dev.elsewhere.petalwind_glasswaste;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Self-test only (s-struct-nether, final review B7): counts vanilla's world-generation reach errors ("Detected setBlock in a far chunk", "unsafe terrain read") while
 * the self-test generates its sites, per structure or feature being generated. Installed once; nothing runs per tick.
 */
public final class FarChunkWatch {
	private FarChunkWatch() {}

	private static final AtomicInteger COUNT = new AtomicInteger();
	private static final Map<String, Integer> BY_SOURCE = new TreeMap<>();
	private static boolean installed;

	public static synchronized void install() {
		if (installed) return;
		installed = true;
		try {
			LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
			AbstractAppender app = new AbstractAppender("elsewhere-petalwind-farchunk-watch", null, null, true, Property.EMPTY_ARRAY) {
				@Override
				public void append(LogEvent e) {
					String m = e.getMessage() == null ? "" : e.getMessage().getFormattedMessage();
					if (m == null || m.contains("[selftest]") || !(m.contains("far chunk") || m.contains("unsafe terrain read"))) return; // not our own check lines
					COUNT.incrementAndGet();
					int i = m.indexOf("currently generating:");
					String src = i < 0 ? "?" : m.substring(i + 21).trim();
					synchronized (BY_SOURCE) {
						BY_SOURCE.merge(src.length() > 120 ? src.substring(0, 120) : src, 1, Integer::sum);
					}
				}
			};
			app.start();
			ctx.getConfiguration().addAppender(app);
			ctx.getRootLogger().addAppender(app);
		} catch (Throwable t) {
			installed = false;
		}
	}

	public static boolean installed() {
		return installed;
	}

	public static int count() {
		return COUNT.get();
	}

	/** Errors logged while generating one structure or feature (its id appears in vanilla's "currently generating" text). */
	public static int countFor(String id) {
		synchronized (BY_SOURCE) {
			int n = 0;
			for (var e : BY_SOURCE.entrySet()) if (e.getKey().contains(id + "]") || e.getKey().endsWith(id)) n += e.getValue();
			return n;
		}
	}

	public static String sources() {
		synchronized (BY_SOURCE) {
			return BY_SOURCE.toString();
		}
	}
}
