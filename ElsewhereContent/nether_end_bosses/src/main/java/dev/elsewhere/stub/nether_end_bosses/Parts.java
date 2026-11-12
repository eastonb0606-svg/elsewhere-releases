package dev.elsewhere.stub.nether_end_bosses;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

/** Per-level index of live boss parts (server and client), read by the multipart mixins. */
public final class Parts {
	private static final Map<Level, Int2ObjectMap<BossPart>> BY_LEVEL = new WeakHashMap<>();

	private Parts() {}

	public static synchronized void add(BossPart part) {
		BY_LEVEL.computeIfAbsent(part.level(), l -> new Int2ObjectOpenHashMap<>()).put(part.getId(), part);
	}

	public static synchronized void remove(BossPart part) {
		Int2ObjectMap<BossPart> map = BY_LEVEL.get(part.level());
		if (map != null) map.remove(part.getId());
	}

	public static synchronized BossPart get(Level level, int id) {
		Int2ObjectMap<BossPart> map = BY_LEVEL.get(level);
		return map == null ? null : map.get(id);
	}

	public static synchronized Collection<BossPart> all(Level level) {
		Int2ObjectMap<BossPart> map = BY_LEVEL.get(level);
		return map == null ? java.util.List.of() : java.util.List.copyOf(map.values());
	}
}
