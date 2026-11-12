package dev.elsewhere.core;

import dev.elsewhere.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Small persistent per-player key/value store (attachment {@code elsewhere:progress}). Writes always replace the map. */
public final class Progress {
	private Progress() {}

	public static Map<String, String> view(final Player p) {
		Map<String, String> m = p.getAttached(ModAttachments.PROGRESS);
		return m == null ? Map.of() : m;
	}

	public static @Nullable String get(final Player p, final String key) {
		return view(p).get(key);
	}

	public static boolean has(final Player p, final String key) {
		return view(p).containsKey(key);
	}

	public static void set(final Player p, final String key, final @Nullable String value) {
		Map<String, String> m = new HashMap<>(view(p));
		if (value == null) {
			m.remove(key);
		} else {
			m.put(key, value);
		}
		p.setAttached(ModAttachments.PROGRESS, m);
	}

	public static long getLong(final Player p, final String key, final long def) {
		String v = get(p, key);
		if (v == null) {
			return def;
		}
		try {
			return Long.parseLong(v);
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
