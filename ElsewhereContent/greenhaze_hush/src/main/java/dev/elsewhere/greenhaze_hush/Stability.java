package dev.elsewhere.greenhaze_hush;

import dev.elsewhere.boss.auris.Auris;
import dev.elsewhere.boss.lithara.Lithara;
import dev.elsewhere.boss.percolax.Percolax;
import dev.elsewhere.greenhaze.Haze;
import dev.elsewhere.hush.HushItems;
import dev.elsewhere.hush.HushNoise;
import dev.elsewhere.hush.Resonance;
import dev.elsewhere.stub.greenhaze_hush.ArenaSeal;
import dev.elsewhere.stub.greenhaze_hush.GlimmerStub;
import dev.elsewhere.stub.greenhaze_hush.SetBonus;
import dev.elsewhere.stub.greenhaze_hush.SignatureGear;
import dev.elsewhere.stub.greenhaze_hush.Telegraph;
import dev.elsewhere.stub.greenhaze_hush.TripSystem;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * fix-stability. Every static collection in this module is either keyed by a ServerLevel / BlockPos (HushNoise, the Auris
 * engine list, arena seals, decals) or by a player UUID. In a long-lived JVM (an integrated server that opens many worlds,
 * a dedicated server that runs for weeks) those kept the old ServerLevel objects alive and grew forever. They are now
 * dropped when a level unloads, when a player leaves and when the server stops.
 */
public final class Stability {
	private Stability() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ServerLevelEvents.UNLOAD.register((server, level) -> {
			HushNoise.forgetLevel(level);
			dev.elsewhere.hush.HushSilence.forgetLevel(level);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetPlayer(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
	}

	public static void forgetPlayer(UUID id) {
		Resonance.forgetPlayer(id);
		HushItems.forgetPlayer(id);
		Percolax.forgetPlayer(id);
		Haze.forgetPlayer(id);
		TripSystem.forgetPlayer(id);
		SignatureGear.forgetPlayer(id);
		GlimmerStub.forgetPlayer(id);
		SetBonus.forgetPlayer(id);
		FxPrefs.forgetPlayer(id);
		DimTravel.forgetPlayer(id);
	}

	public static void clearAll() {
		HushNoise.clearAll();
		Auris.clearEngines();
		Resonance.clearAll();
		HushItems.clearAll();
		HushItems.clearVestment();
		Percolax.clearAll();
		Lithara.clearAll();
		Haze.clearAll();
		dev.elsewhere.greenhaze.GreenhazeItems.clearSlowed();
		TripSystem.clearAll();
		SignatureGear.clearAll();
		GlimmerStub.clearAll();
		SetBonus.clearAll();
		FxPrefs.clearAll();
		DimTravel.clearAll();
		Telegraph.clearAll();
		ArenaSeal.clearAll();
		dev.elsewhere.hush.HushSilence.clearAll();
	}
}
