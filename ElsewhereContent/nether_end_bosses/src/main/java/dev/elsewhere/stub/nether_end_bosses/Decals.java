package dev.elsewhere.stub.nether_end_bosses;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Stub of the core's ground decals: shapes drawn with coloured dust. Yellow = dodge, red = big hit (over 25% HP),
 * cyan = interactable or weak point. The core replaces this with real decal particles plus the colourblind palette.
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

	public static void circle(ServerLevel level, Vec3 center, double radius, int rgb) {
		int points = Math.max(12, (int) (radius * 7));
		ParticleOptions p = dust(rgb, 1.6F);
		for (int i = 0; i < points; i++) {
			double a = i * Mth.TWO_PI / points;
			dev.elsewhere.boss.kit.ForesightGear.hint(level, p, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	/** A filled disc (sparse), for "something lands here" marks. */
	public static void disc(ServerLevel level, Vec3 center, double radius, int rgb) {
		circle(level, center, radius, rgb);
		circle(level, center, radius * 0.5, rgb);
		dev.elsewhere.boss.kit.ForesightGear.hint(level, dust(rgb, 2.0F), center.x, center.y + 0.1, center.z, 2, 0.1, 0, 0.1, 0);
	}

	public static void line(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
		double len = from.distanceTo(to);
		int points = Math.max(2, (int) (len * 2.5));
		ParticleOptions p = dust(rgb, 1.5F);
		for (int i = 0; i <= points; i++) {
			Vec3 at = from.lerp(to, i / (double) points);
			dev.elsewhere.boss.kit.ForesightGear.hint(level, p, at.x, at.y + 0.1, at.z, 1, 0, 0, 0, 0);
		}
	}

	/** Cone on the ground from origin towards yaw (degrees), half-angle in degrees. */
	public static void cone(ServerLevel level, Vec3 origin, float yawDeg, float halfAngleDeg, double length, int rgb) {
		float yaw = yawDeg * Mth.DEG_TO_RAD;
		float half = halfAngleDeg * Mth.DEG_TO_RAD;
		for (float a = -half; a <= half + 1e-3; a += half / 3) {
			Vec3 dir = new Vec3(-Mth.sin(yaw + a), 0, Mth.cos(yaw + a));
			line(level, origin, origin.add(dir.scale(length)), rgb);
		}
	}

	/** A vertical column of particles (warning beams). */
	public static void column(ServerLevel level, Vec3 base, double height, int rgb) {
		ParticleOptions p = dust(rgb, 2.0F);
		for (double y = 0; y < height; y += 0.5) {
			dev.elsewhere.boss.kit.ForesightGear.hint(level, p, base.x, base.y + y, base.z, 1, 0.05, 0, 0.05, 0);
		}
	}
}
