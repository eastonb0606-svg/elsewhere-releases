package dev.elsewhere.beta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;

/**
 * Beta 2 fix (QA): boss bars must not outlive their boss. Whenever an Elsewhere entity is removed for any reason
 * (death, discard, arena reset, chunk unload, dimension change), every {@link ServerBossEvent} it owns is cleared:
 * its own fields, and bars kept one level down in helper objects of the mod (e.g. nether_end_bosses' BossFight list,
 * Grunthar's mount bar). Living bosses re-add nearby players on their next tick, so nothing visible is lost.
 */
public final class BossBarSweep {
	private BossBarSweep() {}

	private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

	private static List<Field> fields(Class<?> c) {
		return FIELDS.computeIfAbsent(c, k -> {
			List<Field> out = new ArrayList<>();
			for (Class<?> x = k; x != null && x != Object.class && x != Entity.class; x = x.getSuperclass()) {
				if (!x.getName().startsWith("dev.elsewhere.")) continue;
				for (Field f : x.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
					Class<?> t = f.getType();
					if (ServerBossEvent.class.isAssignableFrom(t) || Collection.class.isAssignableFrom(t)
						|| (t.getName().startsWith("dev.elsewhere.") && !Entity.class.isAssignableFrom(t))) {
						try {
							f.setAccessible(true);
							out.add(f);
						} catch (RuntimeException e) {
							// skip
						}
					}
				}
			}
			return out;
		});
	}

	/** Every bar the entity owns right now (for the smoke test). */
	public static List<ServerBossEvent> bars(Object o) {
		List<ServerBossEvent> out = new ArrayList<>();
		collect(o, 0, out);
		return out;
	}

	private static void collect(Object o, int depth, List<ServerBossEvent> out) {
		try {
			for (Field f : fields(o.getClass())) {
				Object v = f.get(o);
				if (v instanceof ServerBossEvent bar) out.add(bar);
				else if (v instanceof Collection<?> col) { for (Object x : col.toArray()) if (x instanceof ServerBossEvent bar) out.add(bar); }
				else if (v != null && depth == 0 && !(v instanceof Entity) && v.getClass().getName().startsWith("dev.elsewhere.")) collect(v, 1, out);
			}
		} catch (IllegalAccessException e) {
			// ignore
		}
	}

	/** 2.0 smoke diagnostics: who discarded an Elsewhere entity (Elsewhere frames of the call stack), by entity UUID. */
	public static final Map<java.util.UUID, String> DISCARDED_BY = new ConcurrentHashMap<>();

	public static void onRemoved(Entity e) {
		if (e.level().isClientSide() || !e.getClass().getName().startsWith("dev.elsewhere.")) return;
		if (Boolean.getBoolean("elsewhere.betatest") && e.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
			StringBuilder sb = new StringBuilder();
			for (StackTraceElement st : new Throwable().getStackTrace()) {
				String c = st.getClassName();
				if (!c.startsWith("dev.elsewhere.") || c.startsWith("dev.elsewhere.beta.")) continue;
				if (sb.length() > 0) sb.append(" <- ");
				sb.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(st.getMethodName());
				if (sb.length() > 320) break;
			}
			DISCARDED_BY.put(e.getUUID(), sb.length() == 0 ? "(no Elsewhere frame)" : sb.toString());
		}
		try {
			sweep(e, 0);
		} catch (Throwable t) {
			BetaSmokeTest.LOG.debug("boss bar sweep failed for {}", e, t);
		}
	}

	private static void sweep(Object o, int depth) throws IllegalAccessException {
		for (Field f : fields(o.getClass())) {
			Object v = f.get(o);
			if (v == null) continue;
			if (v instanceof ServerBossEvent bar) {
				bar.removeAllPlayers();
			} else if (v instanceof Collection<?> col) {
				for (Object x : col.toArray()) if (x instanceof ServerBossEvent bar) bar.removeAllPlayers();
			} else if (depth == 0 && !(v instanceof Entity) && v.getClass().getName().startsWith("dev.elsewhere.")) {
				sweep(v, 1);
			}
		}
	}
}
