package dev.elsewhere.kingdom.client;

import dev.elsewhere.kingdom.armies.routine.RoutineFxPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Festival lights of the kingdom routines, drawn by the client only (ARMIES.md 2.4): Mirewick's floating canal lanterns at the
 * Lantern Fair, Azmar's lamp boats at the Festival of the Flood and the sparks of the Hrafnholt Blót bonfire. The server sends one
 * {@link RoutineFxPayload} per hour to nearby players; the client keeps it for that hour and spawns at most 3 particles every 2
 * ticks for the nearest festival within 96 blocks (about 110 alive at once, under the 120 budget). No packets per tick, nothing
 * when no festival is on.
 */
public final class RoutineFxClient {
	private RoutineFxClient() {}

	record Fx(int kind, double x, double y, double z, int radius, long until) {}

	private static final List<Fx> ACTIVE = new ArrayList<>();
	private static long ticks;
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		try {
			ClientPlayNetworking.registerGlobalReceiver(RoutineFxPayload.TYPE, (p, ctx) -> receive(p));
		} catch (RuntimeException e) { // the armies overhaul is off on this client: no festival lights
			dev.elsewhere.kingdom.KingdomContent.LOG.info("Routine festival lights off: {}", e.toString());
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(RoutineFxClient::tick);
	}

	static void receive(RoutineFxPayload p) {
		ACTIVE.removeIf(f -> f.kind() == p.kind() && Math.abs(f.x() - p.x()) < 8 && Math.abs(f.z() - p.z()) < 8);
		ACTIVE.add(new Fx(p.kind(), p.x() + 0.5, p.y(), p.z() + 0.5, Math.max(4, Math.min(64, p.radius())), ticks + p.ticks()));
	}

	static void tick(Minecraft mc) {
		ticks++;
		if (ACTIVE.isEmpty()) return;
		ACTIVE.removeIf(f -> f.until() < ticks);
		ClientLevel lv = mc.level;
		if (lv == null || mc.player == null) {
			ACTIVE.clear();
			return;
		}
		if (mc.isPaused() || ACTIVE.isEmpty()) return;
		Fx near = null;
		double best = 96 * 96;
		for (Fx f : ACTIVE) {
			double dx = mc.player.getX() - f.x(), dz = mc.player.getZ() - f.z(), d = dx * dx + dz * dz;
			if (d < best) {
				best = d;
				near = f;
			}
		}
		if (near == null) return;
		RandomSource r = lv.getRandom();
		int n = (ticks & 1) == 0 ? 2 : 1;
		for (int i = 0; i < n; i++) {
			if (near.kind() == 2) { // Blót: sparks rising from the bonfire
				lv.addParticle(ParticleTypes.FLAME, near.x() + r.nextGaussian() * 0.8, near.y() + 1.2, near.z() + r.nextGaussian() * 0.8, 0, 0.06 + r.nextDouble() * 0.05, 0);
				continue;
			}
			double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * near.radius();
			double px = near.x() + Math.cos(a) * d, pz = near.z() + Math.sin(a) * d;
			int top = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(px), (int) Math.floor(pz));
			if (Math.abs(top - near.y()) > 12) continue; // only near the festival's own level (canal or river surface)
			if (near.kind() == 0) lv.addParticle(ParticleTypes.END_ROD, px, top + 0.3, pz, 0, 0.015, 0); // a lantern drifting up
			else lv.addParticle(ParticleTypes.SMALL_FLAME, px, top + 0.15, pz, 0.02, 0, 0.01); // a lamp boat on the water
		}
	}
}
