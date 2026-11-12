package dev.elsewhere.stub.overworld_bosses;

import dev.elsewhere.boss.kit.TelegraphNet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Ground-decal telegraphs (BOSSES.md "Telegraphs and indicators"): yellow = dodge, red = big hit (over 25% HP),
 * cyan = interactable or weak point. Bosses may use their own colours for flavour (pink tongue line, green heal line).
 * 3.0 (POLISH.md P2): thin wrappers over the shared {@link TelegraphNet} decals, drawn by the client as one translucent
 * shape. Bosses call these every few ticks during a wind-up; TelegraphNet sends the shape once and only refreshes it.
 * Hints only for players with foresight gear (ForesightGear). Players whose client lacks the renderer, or everyone with
 * the {@code boss_telegraphs} kill switch off, get the old dust particles below.
 */
public final class Telegraph {
	public static final int YELLOW = 0xFFD21F;
	public static final int RED = 0xFF2A1A;
	public static final int CYAN = 0x2BE4FF;

	private Telegraph() {}

	public static DustParticleOptions dust(int color, float scale) {
		return new DustParticleOptions(color, scale);
	}

	private static void put(ServerLevel level, ServerPlayer to, ParticleOptions p, double x, double y, double z) {
		level.sendParticles(to, p, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	/** The decal colour of a particle: a dust's own colour, otherwise a pale tint matching the particle. */
	static int colorOf(ParticleOptions p) {
		if (p instanceof DustParticleOptions d) {
			Vector3f c = d.getColor();
			return (int) (c.x() * 255) << 16 | (int) (c.y() * 255) << 8 | (int) (c.z() * 255);
		}
		if (p == ParticleTypes.SNOWFLAKE) return 0xDDF2FF;
		if (p == ParticleTypes.CRIT) return 0xFFF0C8;
		return 0xFFFFFF;
	}

	/** Ring outline at height y (use the floor top + 0.1). */
	public static void ring(ServerLevel level, Vec3 c, double radius, int color) {
		ring(level, c, radius, dust(color, 1.6F));
	}

	public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions p) {
		TelegraphNet.pulse(level, TelegraphNet.RING, 0, c.x, c.y, c.z, (float) radius, 0.4F, 0, 0, colorOf(p), to -> ringDust(level, to, c, radius, p));
	}

	private static void ringDust(ServerLevel level, ServerPlayer to, Vec3 c, double radius, ParticleOptions p) {
		int n = Math.max(12, (int) (radius * Math.PI * 2 / 0.45));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			put(level, to, p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius);
		}
	}

	/** Filled disc made of concentric rings (shadow of a landing). */
	public static void disc(ServerLevel level, Vec3 c, double radius, int color) {
		TelegraphNet.pulse(level, TelegraphNet.DISC, 0, c.x, c.y, c.z, (float) radius, 0, 0, 0, color, to -> {
			DustParticleOptions p = dust(color, 1.3F);
			for (double r = radius; r > 0.3; r -= 1.0) ringDust(level, to, c, r, p);
			put(level, to, p, c.x, c.y, c.z);
		});
	}

	/** A straight ground line from a to b, `width` wide. */
	public static void line(ServerLevel level, Vec3 a, Vec3 b, double width, int color) {
		line(level, a, b, width, dust(color, 1.4F));
	}

	public static void line(ServerLevel level, Vec3 a, Vec3 b, double width, ParticleOptions p) {
		Vec3 d = b.subtract(a);
		if (d.length() < 1.0E-3) return;
		TelegraphNet.pulse(level, TelegraphNet.LINE, 0, a.x, a.y, a.z, (float) d.x, (float) d.y, (float) d.z, (float) width, colorOf(p), to -> lineDust(level, to, a, d, width, p));
	}

	private static void lineDust(ServerLevel level, ServerPlayer to, Vec3 a, Vec3 d, double width, ParticleOptions p) {
		double len = d.length();
		Vec3 dir = d.scale(1.0 / len);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		int rows = Math.max(1, (int) Math.round(width / 0.5));
		for (double t = 0; t <= len; t += 0.45) {
			for (int r = 0; r < rows; r++) {
				double o = rows == 1 ? 0 : (r / (double) (rows - 1) - 0.5) * width;
				put(level, to, p, a.x + dir.x * t + side.x * o, a.y + dir.y * t, a.z + dir.z * t + side.z * o);
			}
		}
	}

	/** An arc of the circle around c, from yaw a0 to a1 (degrees, Minecraft yaw): a cone decal of that span. */
	public static void arc(ServerLevel level, Vec3 c, double radius, float yawFrom, float yawTo, ParticleOptions p) {
		double span = yawTo - yawFrom;
		TelegraphNet.pulse(level, TelegraphNet.CONE, 0, c.x, c.y, c.z, (float) (yawFrom + span / 2), (float) Math.abs(span / 2), (float) radius, 0,
			colorOf(p), to -> {
				for (double r = 1.0; r <= radius + 0.01; r += 1.0) {
					int n = Math.max(6, (int) (Math.abs(span) / 360.0 * Math.PI * 2 * r / 0.5));
					for (int i = 0; i <= n; i++) {
						double yaw = Math.toRadians(yawFrom + span * i / n);
						put(level, to, p, c.x - Math.sin(yaw) * r, c.y, c.z + Math.cos(yaw) * r);
					}
				}
			});
	}

	/** A cone on the ground from origin along yaw, half-angle in degrees. */
	public static void cone(ServerLevel level, Vec3 origin, float yaw, float halfAngle, double length, int color) {
		arc(level, origin, length, yaw - halfAngle, yaw + halfAngle, dust(color, 1.2F));
	}
}
