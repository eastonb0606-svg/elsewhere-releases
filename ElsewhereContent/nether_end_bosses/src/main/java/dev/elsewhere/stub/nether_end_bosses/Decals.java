package dev.elsewhere.stub.nether_end_bosses;

import dev.elsewhere.boss.kit.TelegraphNet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Ground decals: yellow = dodge, red = big hit (over 25% HP), cyan = interactable or weak point.
 * 3.0 (POLISH.md P2): thin wrappers over the shared {@link TelegraphNet} decals, drawn by the client as one translucent
 * shape. Bosses call these every few ticks during a wind-up; TelegraphNet sends the shape once and only refreshes it.
 * Hints only for players with foresight gear. Players whose client lacks the renderer, or everyone with the
 * {@code boss_telegraphs} kill switch off, get the old coloured dust.
 */
public final class Decals {
	public static final int YELLOW = 0xFFE23B;
	public static final int RED = 0xFF2A1F;
	public static final int CYAN = 0x3BF2FF;
	public static final int PURPLE = 0xB34DFF;

	private Decals() {}

	public static ParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(rgb, size);
	}

	private static void put(ServerLevel level, ServerPlayer to, ParticleOptions p, double x, double y, double z, int count, double dx, double dz) {
		level.sendParticles(to, p, true, true, x, y, z, count, dx, 0, dz, 0);
	}

	private static void circleDust(ServerLevel level, ServerPlayer to, Vec3 center, double radius, int rgb) {
		int points = Math.max(12, (int) (radius * 7));
		ParticleOptions p = dust(rgb, 1.6F);
		for (int i = 0; i < points; i++) {
			double a = i * Mth.TWO_PI / points;
			put(level, to, p, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0, 0);
		}
	}

	private static void lineDust(ServerLevel level, ServerPlayer to, Vec3 from, Vec3 dest, int rgb) {
		double len = from.distanceTo(dest);
		int points = Math.max(2, (int) (len * 2.5));
		ParticleOptions p = dust(rgb, 1.5F);
		for (int i = 0; i <= points; i++) {
			Vec3 at = from.lerp(dest, i / (double) points);
			put(level, to, p, at.x, at.y + 0.1, at.z, 1, 0, 0);
		}
	}

	public static void circle(ServerLevel level, Vec3 center, double radius, int rgb) {
		TelegraphNet.pulse(level, TelegraphNet.RING, 0, center.x, center.y + 0.06, center.z, (float) radius, 0.45F, 0, 0, rgb,
			to -> circleDust(level, to, center, radius, rgb));
	}

	/** A filled disc, for "something lands here" marks. */
	public static void disc(ServerLevel level, Vec3 center, double radius, int rgb) {
		TelegraphNet.pulse(level, TelegraphNet.DISC, 0, center.x, center.y + 0.06, center.z, (float) radius, 0, 0, 0, rgb, to -> {
			circleDust(level, to, center, radius, rgb);
			circleDust(level, to, center, radius * 0.5, rgb);
			put(level, to, dust(rgb, 2.0F), center.x, center.y + 0.1, center.z, 2, 0.1, 0.1);
		});
	}

	public static void line(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
		Vec3 d = to.subtract(from);
		if (d.lengthSqr() < 1.0E-6) return;
		TelegraphNet.pulse(level, TelegraphNet.LINE, 0, from.x, from.y + 0.06, from.z, (float) d.x, (float) d.y, (float) d.z, 0.7F, rgb,
			pl -> lineDust(level, pl, from, to, rgb));
	}

	/** Cone on the ground from origin towards yaw (degrees), half-angle in degrees. */
	public static void cone(ServerLevel level, Vec3 origin, float yawDeg, float halfAngleDeg, double length, int rgb) {
		TelegraphNet.pulse(level, TelegraphNet.CONE, 0, origin.x, origin.y + 0.06, origin.z, yawDeg, halfAngleDeg, (float) length, 0, rgb, to -> {
			float yaw = yawDeg * Mth.DEG_TO_RAD;
			float half = halfAngleDeg * Mth.DEG_TO_RAD;
			for (float a = -half; a <= half + 1e-3; a += half / 3) {
				Vec3 dir = new Vec3(-Mth.sin(yaw + a), 0, Mth.cos(yaw + a));
				lineDust(level, to, origin, origin.add(dir.scale(length)), rgb);
			}
		});
	}

	/** A vertical column (warning beam). */
	public static void column(ServerLevel level, Vec3 base, double height, int rgb) {
		TelegraphNet.pulse(level, TelegraphNet.LINE, 0, base.x, base.y, base.z, 0, (float) height, 0, 0.6F, rgb, to -> {
			ParticleOptions p = dust(rgb, 2.0F);
			for (double y = 0; y < height; y += 0.5) put(level, to, p, base.x, base.y + y, base.z, 1, 0.05, 0.05);
		});
	}
}
