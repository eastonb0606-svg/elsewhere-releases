package dev.elsewhere.kingdom_court.client.standing;

import dev.elsewhere.kingdom_court.standing.StandingNet;
import dev.elsewhere.kingdom_court.standing.StandingText;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;

/**
 * POLISH P7: the client's copy of its Standing per kingdom (keyed by the kingdom ledger path, so a remnant's soldiers read their kingdom's).
 * Filled by the trader dialogue and by query replies. A query goes out only when the chip needs a kingdom whose copy is older than
 * {@link #FRESH_MS} and none is already on its way (at most one per {@link #RETRY_MS}); nothing here runs on a timer.
 */
public final class StandingCache {
	private StandingCache() {}

	public static final long FRESH_MS = 5000L, RETRY_MS = 1000L;

	public record Entry(int standing, int flags, long at) {}

	private static final Map<String, Entry> MAP = new HashMap<>();
	private static final Map<String, Long> PENDING = new HashMap<>();

	private static String lastId = "", lastKey = "";

	/** The ledger key of a faction id; the last one is remembered so the per-frame chip lookup allocates nothing. */
	static String key(String factionId) {
		if (!factionId.equals(lastId)) {
			lastKey = StandingText.ledgerPath(factionId);
			lastId = factionId;
		}
		return lastKey;
	}

	public static Entry get(String factionId) {
		return MAP.get(key(factionId));
	}

	/** Asks the server for this ruler's kingdom unless a fresh copy or an open request already exists. */
	public static void request(int entityId, String factionId) {
		String k = key(factionId);
		long now = Util.getMillis();
		Entry e = MAP.get(k);
		if (e != null && now - e.at() < FRESH_MS) return;
		Long sent = PENDING.get(k);
		if (sent != null && now - sent < RETRY_MS) return;
		if (!ClientPlayNetworking.canSend(StandingNet.Query.TYPE)) return;
		PENDING.put(k, now);
		ClientPlayNetworking.send(new StandingNet.Query(entityId));
	}

	static void put(String factionId, int standing, int flags) {
		String k = StandingText.ledgerPath(factionId);
		MAP.put(k, new Entry(standing, flags, Util.getMillis()));
		PENDING.remove(k);
	}

	public static void accept(StandingNet.Reply r) {
		put(r.faction(), r.standing(), r.flags());
	}

	/** The trader dialogue carries the same Standing; its Hostile and Oathbreaker bits map onto the chip's flags. */
	public static void accept(StandingNet.TraderOpen t) {
		int flags = (t.has(StandingNet.HOSTILE) ? StandingNet.R_ATTACKS : 0) | (t.has(StandingNet.OATH) ? StandingNet.R_OATH : 0);
		Entry old = MAP.get(StandingText.ledgerPath(t.faction()));
		if (old != null) flags |= old.flags() & StandingNet.R_CAPPED;
		put(t.faction(), t.standing(), flags);
	}

	public static void clear() {
		MAP.clear();
		PENDING.clear();
	}
}
