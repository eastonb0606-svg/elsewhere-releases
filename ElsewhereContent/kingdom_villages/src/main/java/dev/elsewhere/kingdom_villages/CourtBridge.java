package dev.elsewhere.kingdom_villages;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Proxy;

/**
 * The court lane's {@code CourtEvents.FACTION_FALLEN} by reflection (the court module may be absent): when a kingdom falls (all its
 * leaders dead; for Mirewick that is Dark Lanterns) every village it held goes free (contested, "not annexed": the normal slow
 * rules may besiege it again), its outposts fall silent, and its soldiers' sieges end.
 */
public final class CourtBridge {
	private CourtBridge() {}

	private static boolean listening;

	static synchronized void listen() {
		if (listening) return;
		try {
			Class<?> events = Class.forName("dev.elsewhere.kingdom_court.CourtEvents");
			Class<?> iface = Class.forName("dev.elsewhere.kingdom_court.CourtEvents$FactionFallen");
			Object event = events.getField("FACTION_FALLEN").get(null);
			Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, (p, m, a) -> {
				if (m.getName().equals("onFallen")) factionFallen((MinecraftServer) a[0], (Identifier) a[1]);
				return null;
			});
			Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class).invoke(event, proxy);
			listening = true;
		} catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | LinkageError e) {
			// court module not present
		} catch (ReflectiveOperationException | RuntimeException e) {
			VillagesContent.LOG.warn("CourtEvents.FACTION_FALLEN subscription failed: {}", e.toString());
		}
	}

	/** Public so the self-test (and the court lane's own tests) can drive it. */
	public static void factionFallen(MinecraftServer server, Identifier faction) {
		ServerLevel level = server.overworld();
		VillageLedger ledger = VillageLedger.get(level);
		String f = faction.toString();
		for (VillageRecord r : new java.util.ArrayList<>(ledger.villages.values())) {
			if (r.kind == VillageRecord.Kind.VILLAGE && r.owner.equals(f) && (r.isOccupiedLike() || r.state == VillageRecord.State.SIEGE)) {
				VillageManager.onVillageChanged(level, WarBridge.key(r), r.center, null, faction, "fallen");
			}
		}
		for (VillageRecord s : ledger.sites.values()) {
			if (s.kind == VillageRecord.Kind.OUTPOST && s.owner.equals(f) && s.state == VillageRecord.State.OCCUPIED) {
				for (var ks : VillageManager.soldiers(level, s)) ks.discard();
				s.state = VillageRecord.State.LIBERATED;
			}
		}
		ledger.strikeUntil.remove(f);
		ledger.dirty();
	}
}
