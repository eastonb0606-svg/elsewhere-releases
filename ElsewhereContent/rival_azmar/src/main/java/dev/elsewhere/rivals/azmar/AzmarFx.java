package dev.elsewhere.rivals.azmar;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Azmar's fight particles. All server-side sends; purely cosmetic (never touches blocks). Palette: gold, lapis, faience turquoise. */
public final class AzmarFx {
	private AzmarFx() {}

	public static final int GOLD = 0xD4A537, LAPIS = 0x1F3F8F, TURQUOISE = 0x2BB3A3, CARNELIAN = 0xB0362B, LINEN = 0xEDE3CC, AMBER = 0xE8A317, GREEN = 0x4C9A2A;
	public static final DustParticleOptions GOLD_DUST = new DustParticleOptions(GOLD, 1.0F);

	/** Gold-dust and scarab-shell glitter on a khopesh swing or hit. */
	public static void goldDust(ServerLevel level, Vec3 p, int count) {
		level.sendParticles(GOLD_DUST, p.x, p.y, p.z, count, 0.25, 0.25, 0.25, 0.02);
		level.sendParticles(ParticleTypes.WAX_OFF, p.x, p.y, p.z, Math.max(1, count / 3), 0.2, 0.2, 0.2, 0.0);
	}

	/** The winged-sun halo behind the Sultana (her Halo Flare tell): two feathered arcs and a disc, in a plane facing {@code facingYawDeg}. */
	public static void wingedHalo(ServerLevel level, Vec3 center, float facingYawDeg, double scale) {
		double yaw = Math.toRadians(facingYawDeg);
		Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		for (int i = -8; i <= 8; i++) {
			double t = i / 8.0;
			double x = t * 2.6 * scale, y = (1.0 - t * t * 0.55) * 0.9 * scale;
			Vec3 q = center.add(right.scale(x)).add(0, y, 0);
			level.sendParticles(GOLD_DUST, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
			if (i % 2 == 0) level.sendParticles(new DustParticleOptions(TURQUOISE, 0.8F), q.x, q.y - 0.25, q.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		for (int i = 0; i < 12; i++) {
			double a = i / 12.0 * Math.PI * 2;
			Vec3 q = center.add(right.scale(Math.cos(a) * 0.45 * scale)).add(0, 1.0 * scale + Math.sin(a) * 0.45 * scale, 0);
			level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0.0);
		}
	}

	/** A glowing ibis-glyph circle drawn on the ground (the Wazir's telegraphs): ring, four glyph ticks and a centre mark, in {@code rgb}. */
	public static void glyphCircle(ServerLevel level, Vec3 center, double radius, int rgb) {
		DustParticleOptions d = new DustParticleOptions(rgb, 1.1F);
		int n = Math.max(16, (int) (radius * 10)); // P13: about 1.6 dust per block of ring, was 2.2
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			level.sendParticles(d, center.x + Math.cos(a) * radius, center.y + 0.08, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0.0);
		}
		for (int k = 0; k < 4; k++) {                        // glyph strokes pointing inward at the cardinal points
			double a = k * Math.PI / 2;
			for (double r = radius * 0.45; r < radius * 0.8; r += 0.22)
				level.sendParticles(d, center.x + Math.cos(a) * r, center.y + 0.08, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
			double a2 = a + 0.5;
			level.sendParticles(d, center.x + Math.cos(a2) * radius * 0.62, center.y + 0.08, center.z + Math.sin(a2) * radius * 0.62, 1, 0, 0, 0, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.1, center.z, 1, 0.05, 0.0, 0.05, 0.0);
	}

	/** The falcon's gold dive streak between two points. */
	public static void falconStreak(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int n = Math.max(4, (int) (d.length() * 3));
		for (int i = 0; i <= n; i++) {
			Vec3 q = from.add(d.scale(i / (double) n));
			level.sendParticles(GOLD_DUST, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
			if (i % 3 == 0) level.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** A linen trail (Sand-Dancers spinning, Mirage Step afterimage). */
	public static void linenTrail(ServerLevel level, Vec3 p) {
		level.sendParticles(new DustParticleOptions(LINEN, 1.2F), p.x, p.y + 1.0, p.z, 4, 0.25, 0.5, 0.25, 0.0);
	}

	/** Mirage Step flash: scarab-shell dust cloud (real step) or a plain poof (feint, no gold flash). */
	public static void mirage(ServerLevel level, Vec3 p, boolean realFlash) {
		if (realFlash) {
			level.sendParticles(GOLD_DUST, p.x, p.y + 1, p.z, 24, 0.4, 0.8, 0.4, 0.05);
			level.sendParticles(ParticleTypes.WAX_OFF, p.x, p.y + 1, p.z, 8, 0.3, 0.7, 0.3, 0.0);
		} else {
			level.sendParticles(ParticleTypes.POOF, p.x, p.y + 1, p.z, 5, 0.25, 0.5, 0.25, 0.01);
		}
	}
}
