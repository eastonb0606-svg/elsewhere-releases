package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom.war.WarApi;
import dev.elsewhere.kingdom.war.WarEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The villages lane's link to the war layer ({@code dev.elsewhere.kingdom.war}, WAR-AI.md): every village is registered with its
 * occupier (null = free), and {@code WarEvents.VILLAGE_CHANGED} (conquest or revolt) drives the physical flips. A {@link Sink} stands
 * in for the war layer in self-tests. Failures of the war layer never break the villages.
 */
public final class WarBridge {
	private WarBridge() {}

	/** Receives {@code WarEvents.VILLAGE_CHANGED}. */
	public interface Listener {
		void onVillageChanged(ServerLevel level, String key, BlockPos pos, @Nullable Identifier newOwner, @Nullable Identifier oldOwner, String reason);
	}

	/** Test observer of the calls made to the war layer (set by the self-test); null in production. */
	public interface Sink {
		void registerVillage(MinecraftServer server, String key, BlockPos pos, @Nullable Identifier owner, boolean contestable, boolean force);

		void removeVillage(MinecraftServer server, String key);
	}

	public static @Nullable Sink testSink;
	private static boolean listening;
	private static final java.util.Set<Long> synced = new java.util.HashSet<>();

	/** A world with only Aldermark never creates war-layer data (2.0 saves stay untouched): the real calls start once a second realm kingdom exists. */
	static boolean warActive() {
		return dev.elsewhere.kingdom.FactionDefs.realmFactions().size() >= 2;
	}

	public static void reset() {
		synced.clear();
	}

	/** Registers every known village with the war layer once per session (and once the war layer becomes active). */
	static void ensure(MinecraftServer server, VillageLedger ledger) {
		if (!warActive()) return;
		for (VillageRecord r : ledger.villages.values()) {
			if (r.kind != VillageRecord.Kind.VILLAGE || !synced.add(r.id)) continue;
			boolean contestable = r.state != VillageRecord.State.PROTECTED && r.state != VillageRecord.State.RESTORED;
			register(server, r, VillageManager.warOwner(r), contestable, r.isOccupiedLike());
		}
	}

	public static String key(VillageRecord r) {
		return Long.toString(r.id);
	}

	/** Registers or updates a village with the war layer. {@code owner} null = free. */
	public static void register(MinecraftServer server, VillageRecord r, @Nullable Identifier owner, boolean contestable, boolean force) {
		if (r.kind != VillageRecord.Kind.VILLAGE) return;
		if (testSink != null) testSink.registerVillage(server, key(r), r.center, owner, contestable, force);
		if (!warActive()) return;
		synced.add(r.id);
		try {
			WarApi.registerVillage(server, key(r), r.center, owner, contestable, force);
		} catch (RuntimeException e) {
			VillagesContent.LOG.warn("WarApi.registerVillage failed: {}", e.toString());
		}
	}

	public static void forget(MinecraftServer server, VillageRecord r) {
		if (testSink != null) testSink.removeVillage(server, key(r));
		if (!warActive()) return;
		try {
			WarApi.removeVillage(server, key(r));
		} catch (RuntimeException e) {
			VillagesContent.LOG.warn("WarApi.removeVillage failed: {}", e.toString());
		}
	}

	/** One effective brain number (boss-death degradation included), or {@code fallback} when the war layer does not know it. */
	public static double brainField(MinecraftServer server, Identifier faction, String key, double fallback) {
		try {
			double v = WarApi.brainField(server, faction, key);
			return Double.isNaN(v) ? fallback : v;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	/** Subscribes to {@code WarEvents.VILLAGE_CHANGED} (once). */
	public static synchronized void listen(Listener l) {
		if (listening) return;
		listening = true;
		WarEvents.VILLAGE_CHANGED.register(l::onVillageChanged);
	}
}
