package dev.elsewhere.kingdom_villages;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;

/** Hooks for the other lanes (e.g. K-D drops a Survey Map fragment on liberation, K-B counts dead Lords). */
public final class VillageEvents {
	private VillageEvents() {}

	public interface Liberated {
		void onLiberated(ServerLevel level, VillageRecord village);
	}

	public interface StateChanged {
		void onStateChanged(ServerLevel level, VillageRecord village, VillageRecord.State from, VillageRecord.State to);
	}

	public interface OwnerChanged {
		/** {@code newOwner} null = the village went free. {@code reason}: conquest, revolt, api or the village rules. */
		void onOwnerChanged(ServerLevel level, VillageRecord village, @org.jspecify.annotations.Nullable String newOwner, String oldOwner, String reason);
	}

	public interface Strike {
		void onStrike(ServerLevel level, String faction, long until);
	}

	public static final Event<OwnerChanged> OWNER_CHANGED = EventFactory.createArrayBacked(OwnerChanged.class, ls -> (level, v, n, o, r) -> {
		for (OwnerChanged l : ls) l.onOwnerChanged(level, v, n, o, r);
	});

	public static final Event<Strike> STRIKE = EventFactory.createArrayBacked(Strike.class, ls -> (level, f, u) -> {
		for (Strike l : ls) l.onStrike(level, f, u);
	});

	public static final Event<Liberated> LIBERATED = EventFactory.createArrayBacked(Liberated.class, ls -> (level, v) -> {
		for (Liberated l : ls) l.onLiberated(level, v);
	});

	public static final Event<StateChanged> STATE_CHANGED = EventFactory.createArrayBacked(StateChanged.class, ls -> (level, v, a, b) -> {
		for (StateChanged l : ls) l.onStateChanged(level, v, a, b);
	});
}
