package dev.elsewhere.core.atlas;

import dev.elsewhere.core.Progress;
import dev.elsewhere.core.trip.TripState;
import dev.elsewhere.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** polish-atlas-a, server half: the base Wayfinder opens the full-screen Atlas with what this player has discovered. */
public final class Atlas {
	private Atlas() {}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(AtlasPayload.TYPE, AtlasPayload.CODEC);
	}

	public static AtlasPayload snapshot(final ServerPlayer p) {
		TreeSet<String> dims = new TreeSet<>();
		TripState state = p.getAttached(ModAttachments.TRIP_STATE);
		if (state != null) {
			dims.addAll(state.visited);
		}
		List<String> bosses = new ArrayList<>();
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, String> e : Progress.view(p).entrySet()) {
			if (e.getKey().startsWith("boss:")) {
				bosses.add(e.getKey().substring(5));
			} else if (e.getKey().startsWith("found:")) {
				found.add(e.getKey().substring(6));
			}
		}
		bosses.sort(null);
		found.sort(null);
		return new AtlasPayload(List.copyOf(dims), bosses, found);
	}

	/** Opens the Atlas on the client (if the client has the mod's receiver). Returns the payload sent. */
	public static AtlasPayload open(final ServerPlayer p) {
		AtlasPayload payload = snapshot(p);
		if (ServerPlayNetworking.canSend(p, AtlasPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
		return payload;
	}

	/** Records a discovery shown in the Atlas ("item:elsewhere:amber", "companion:hollow_stag", "event:lantern"). */
	public static void discover(final ServerPlayer p, final String what) {
		if (!Progress.has(p, "found:" + what)) {
			Progress.set(p, "found:" + what, "1");
		}
	}
}
