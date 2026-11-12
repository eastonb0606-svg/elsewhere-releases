package dev.elsewhere.stub.verdigris_ironpeak;

import dev.elsewhere.boss.kit.TelegraphNet;
import java.util.Collection;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Telegraph decals: yellow = dodge, red = big hit (over 25% HP), cyan = interactable or weak point. The attack name shows
 * on the action bar. 3.0 (POLISH.md P2): thin wrappers over the shared {@link TelegraphNet} decals, drawn by the client as
 * one translucent shape. Bosses call these every few ticks during a wind-up; TelegraphNet sends the shape once and only
 * refreshes it. Hints only for players with foresight gear. Players whose client lacks the renderer, or everyone with the
 * {@code boss_telegraphs} kill switch off, get the old dust particles.
 */
public final class Telegraph {
	public static final int YELLOW = 0xFFE040, RED = 0xFF2A1A, CYAN = 0x30F0FF;

	private Telegraph() {}

	private static DustParticleOptions dust(int color) {
		return new DustParticleOptions(color, 1.6F);
	}

	private static void put(ServerLevel level, ServerPlayer to, DustParticleOptions p, double x, double y, double z) {
		level.sendParticles(to, p, true, true, x, y, z, 1, 0, 0, 0, 0);
	}

	private static void circleDust(ServerLevel level, ServerPlayer to, Vec3 c, double r, int color) {
		int n = Math.max(12, (int) (r * 10));
		DustParticleOptions p = dust(color);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			put(level, to, p, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r);
		}
	}

	/** Ring outline. */
	public static void circle(ServerLevel level, Vec3 c, double r, int color) {
		TelegraphNet.pulse(level, TelegraphNet.RING, 0, c.x, c.y + 0.06, c.z, (float) r, 0.4F, 0, 0, color, to -> circleDust(level, to, c, r, color));
	}

	public static void filledCircle(ServerLevel level, Vec3 c, double r, int color) {
		TelegraphNet.pulse(level, TelegraphNet.DISC, 0, c.x, c.y + 0.06, c.z, (float) r, 0, 0, 0, color, to -> {
			circleDust(level, to, c, r, color);
			int n = (int) (r * r * 3);
			DustParticleOptions p = dust(color);
			for (int i = 0; i < n; i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2, rr = Math.sqrt(level.getRandom().nextDouble()) * r;
				put(level, to, p, c.x + Math.cos(a) * rr, c.y + 0.1, c.z + Math.sin(a) * rr);
			}
		});
	}

	public static void ring(ServerLevel level, Vec3 c, double inner, double outer, int color) {
		TelegraphNet.pulse(level, TelegraphNet.RING, 0, c.x, c.y + 0.06, c.z, (float) ((inner + outer) / 2), (float) Math.abs(outer - inner), 0, 0, color, to -> {
			circleDust(level, to, c, inner, color);
			circleDust(level, to, c, outer, color);
		});
	}

	/**
	 * A travelling shockwave front: the attack itself, so everyone in range sees it (not a foresight hint). {@code start} =
	 * the wave's first tick: one {@link TelegraphNet#WAVE} shape is sent then, owned by the boss, and the client grows it by
	 * {@code growPerTick} up to {@code maxRadius}. Every tick, players without the client renderer get the dust ring.
	 */
	public static void wave(ServerLevel level, Entity owner, Vec3 c, double radius, double growPerTick, double maxRadius, double width, int color, boolean start) {
		if (start) {
			TelegraphNet.clear(level, owner);
			int ticks = (int) Math.ceil((maxRadius - radius) / Math.max(0.01, growPerTick)) + 2;
			TelegraphNet.show(level, owner, TelegraphNet.WAVE, TelegraphNet.ATTACK, c.x, c.y + 0.06, c.z, (float) radius, (float) growPerTick, (float) maxRadius,
				(float) width, color, ticks, null);
		}
		TelegraphNet.fallback(level, c.x, c.y, c.z, true, to -> {
			circleDust(level, to, c, radius - width / 2, color);
			circleDust(level, to, c, radius + width / 2, color);
		});
	}

	public static void line(ServerLevel level, Vec3 a, Vec3 b, double halfWidth, int color) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 0.01) return;
		TelegraphNet.pulse(level, TelegraphNet.LINE, 0, a.x, a.y + 0.06, a.z, (float) d.x, (float) d.y, (float) d.z, (float) (halfWidth * 2), color, to -> {
			Vec3 dir = d.scale(1 / len), side = new Vec3(-dir.z, 0, dir.x);
			DustParticleOptions p = dust(color);
			for (double t = 0; t <= len; t += 0.5) {
				for (double w = -halfWidth; w <= halfWidth + 0.01; w += Math.max(0.5, halfWidth)) {
					Vec3 q = a.add(dir.scale(t)).add(side.scale(w));
					put(level, to, p, q.x, q.y + 0.1, q.z);
				}
			}
		});
	}

	public static void cone(ServerLevel level, Vec3 apex, Vec3 dir, double length, double halfAngleDeg, int color) {
		TelegraphNet.pulse(level, TelegraphNet.CONE, 0, apex.x, apex.y + 0.06, apex.z, TelegraphNet.yawOf(dir.x, dir.z), (float) halfAngleDeg, (float) length, 0, color, to -> {
			double base = Math.atan2(dir.z, dir.x), half = Math.toRadians(halfAngleDeg);
			DustParticleOptions p = dust(color);
			for (double r = 1; r <= length; r += 1) {
				for (double a = -half; a <= half; a += 0.6 / r) {
					put(level, to, p, apex.x + Math.cos(base + a) * r, apex.y + 0.1, apex.z + Math.sin(base + a) * r);
				}
			}
		});
	}

	/** Flashes the attack name for everyone in the fight (the core shows it under the boss bar). */
	public static void announce(Collection<ServerPlayer> players, Component name) {
		for (ServerPlayer p : players) p.sendOverlayMessage(name);
	}
}
