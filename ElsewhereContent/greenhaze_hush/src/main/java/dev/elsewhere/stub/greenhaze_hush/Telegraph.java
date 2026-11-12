package dev.elsewhere.stub.greenhaze_hush;

import dev.elsewhere.boss.kit.TelegraphNet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Ground-decal telegraphs: circles, rings, lines and cones that stay for a duration.
 * Yellow = dodge, red = a big hit (>25% HP), cyan = interactable / weak point (shared framework colours).
 * 3.0 (POLISH.md P2): each call sends ONE {@link TelegraphNet} shape that the client draws and animates for its duration;
 * hints only for players with foresight gear. The old dust decal (re-drawn every 4 ticks) is kept only for players whose
 * client lacks the renderer, or for everyone with the {@code boss_telegraphs} kill switch off.
 */
public final class Telegraph {
	private Telegraph() {}

	public static final int YELLOW = 0xFFD83A, RED = 0xFF2A2A, CYAN = 0x3AE8FF, GREEN = 0x7CFF5A, VIOLET = 0xC27BFF;

	private interface Shape { void draw(ServerLevel level, ServerPlayer to, DustParticleOptions dust); }

	private static final class Decal {
		final ServerLevel level; final Shape shape; final DustParticleOptions dust; final List<ServerPlayer> to; int ticks;
		Decal(ServerLevel level, Shape shape, int color, int ticks, List<ServerPlayer> to) {
			this.level = level; this.shape = shape; this.dust = new DustParticleOptions(color, 1.6F); this.ticks = ticks; this.to = to;
		}
	}

	private static final List<Decal> ACTIVE = new ArrayList<>();
	/** Total decals ever created (the self-test checks attacks telegraph). */
	public static int created;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ACTIVE.isEmpty()) return;
			for (Iterator<Decal> it = ACTIVE.iterator(); it.hasNext(); ) {
				Decal d = it.next();
				if (d.ticks % 4 == 0) {
					for (ServerPlayer p : d.to) if (!p.isRemoved()) d.shape.draw(d.level, p, d.dust);
				}
				if (--d.ticks <= 0) it.remove();
			}
		});
	}

	public static void clear(ServerLevel level) {
		ACTIVE.removeIf(d -> d.level == level);
		TelegraphNet.clear(level, null);
	}

	private static void add(ServerLevel level, byte kind, Vec3 o, float a, float b, float c, float d, Shape shape, int color, int ticks) {
		created++;
		List<ServerPlayer> dust = new ArrayList<>(0);
		TelegraphNet.show(level, null, kind, 0, o.x, o.y + 0.06, o.z, a, b, c, d, color, ticks, dust::add);
		if (!dust.isEmpty()) ACTIVE.add(new Decal(level, shape, color, ticks, dust));
	}

	/** Filled circle (several concentric rings). */
	public static void circle(ServerLevel level, Vec3 c, double radius, int color, int ticks) {
		add(level, TelegraphNet.DISC, c, (float) radius, 0, 0, 0, (l, to, dust) -> {
			for (double r = radius; r > 0.4; r -= 1.2) ring(l, to, dust, c, r);
		}, color, ticks);
	}

	/** Thin ring (e.g. a shockwave to jump over). */
	public static void ring(ServerLevel level, Vec3 c, double radius, int color, int ticks) {
		add(level, TelegraphNet.RING, c, (float) radius, 0.45F, 0, 0, (l, to, dust) -> ring(l, to, dust, c, radius), color, ticks);
	}

	public static void line(ServerLevel level, Vec3 from, Vec3 to, double halfWidth, int color, int ticks) {
		Vec3 dv = to.subtract(from);
		if (dv.horizontalDistance() < 0.01) return;
		add(level, TelegraphNet.LINE, from, (float) dv.x, (float) dv.y, (float) dv.z, (float) (halfWidth * 2), (l, pl, dust) -> {
			double len = dv.horizontalDistance();
			Vec3 side = new Vec3(-dv.z / len, 0, dv.x / len);
			for (double s = 0; s <= len; s += 0.7) {
				Vec3 p = from.add(dv.scale(s / len));
				for (double w = -halfWidth; w <= halfWidth + 0.01; w += 0.8) {
					l.sendParticles(pl, dust, true, true, p.x + side.x * w, p.y + 0.1, p.z + side.z * w, 1, 0, 0, 0, 0);
				}
			}
		}, color, ticks);
	}

	/** Cone from origin along yaw (degrees, Minecraft convention), half-angle in degrees. */
	public static void cone(ServerLevel level, Vec3 origin, float yawDeg, float halfAngleDeg, double range, int color, int ticks) {
		add(level, TelegraphNet.CONE, origin, yawDeg, halfAngleDeg, (float) range, 0, (l, pl, dust) -> {
			for (double r = 1.5; r <= range; r += 1.2) {
				int steps = Math.max(3, (int) (r * Math.toRadians(halfAngleDeg * 2) / 0.8));
				for (int i = 0; i <= steps; i++) {
					double a = Math.toRadians(yawDeg - halfAngleDeg + (2.0 * halfAngleDeg * i / steps));
					double x = origin.x - Math.sin(a) * r, z = origin.z + Math.cos(a) * r;
					l.sendParticles(pl, dust, true, true, x, origin.y + 0.1, z, 1, 0, 0, 0, 0);
				}
			}
		}, color, ticks);
	}

	private static void ring(ServerLevel l, ServerPlayer to, DustParticleOptions dust, Vec3 c, double r) {
		int n = Math.max(8, (int) (r * 2 * Math.PI / 0.7));
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			l.sendParticles(to, dust, true, true, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	public static void clearAll() {
		ACTIVE.clear();
		TelegraphNet.reset();
	}
}
