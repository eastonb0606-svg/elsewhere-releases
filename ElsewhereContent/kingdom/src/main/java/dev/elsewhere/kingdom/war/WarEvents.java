package dev.elsewhere.kingdom.war;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Fabric events of the war layer. All fire on the server thread, inside the daily war step (overworld tick) or a real battle's end. */
public final class WarEvents {
	private WarEvents() {}

	public enum Type {
		DECLARED, TRUCE, SKIRMISH, ASSAULT_WON, ASSAULT_FAILED, BROKE_OFF, GARRISON_WITHDREW, VILLAGE_CAPTURED, VILLAGE_REVOLTED,
		COALITION, ALLIANCE, BROKERED, HIRED, DESERTED, BRAIN_KILLED
	}

	/**
	 * One war event. {@code faction} is the actor (attacker, declarer, winner), {@code other} the other party (null for none or a free village), {@code village} the
	 * village key registered with {@link WarApi#registerVillage} (null if none), {@code pos} its x/z (y is 0), {@code odds} the attacker:defender odds for assaults.
	 */
	public record WarEvent(Type type, long day, Identifier faction, @Nullable Identifier other, @Nullable String village, @Nullable BlockPos pos, double odds, String text) {}

	public interface Listener {
		void onWarEvent(ServerLevel level, WarEvent e);
	}

	public interface VillageChanged {
		/** {@code newOwner} null = the village went free. {@code reason}: "conquest" (siege won), "revolt" or "api". The villages lane starts occupation or rebuilding here. */
		void onVillageChanged(ServerLevel level, String villageKey, BlockPos pos, @Nullable Identifier newOwner, @Nullable Identifier oldOwner, String reason);
	}

	/** Every war event (declarations, assaults, alliances, hiring, brain kills...). */
	public static final Event<Listener> WAR_EVENT = EventFactory.createArrayBacked(Listener.class, ls -> (level, e) -> {
		for (Listener l : ls) l.onWarEvent(level, e);
	});

	/** A village changed owner by conquest or revolt. */
	public static final Event<VillageChanged> VILLAGE_CHANGED = EventFactory.createArrayBacked(VillageChanged.class, ls -> (level, k, p, n, o, r) -> {
		for (VillageChanged l : ls) l.onVillageChanged(level, k, p, n, o, r);
	});
}
