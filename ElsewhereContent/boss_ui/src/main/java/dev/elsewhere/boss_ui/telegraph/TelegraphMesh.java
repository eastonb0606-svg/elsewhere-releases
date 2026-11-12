package dev.elsewhere.boss_ui.telegraph;

import dev.elsewhere.boss.kit.TelegraphNet;
import net.minecraft.util.Mth;

/**
 * Geometry of one telegraph: flat translucent quads on the ground, camera-relative, in three layers (POLISH.md P2):
 * a faint fill, a brighter fill-up that grows from the boss side as the wind-up runs (the hit lands when it is full), and
 * a pulsing edge band. Repeat-call shapes ({@link TelegraphNet#OPEN}) don't know their end, so they "charge" towards
 * full instead. The pulse speeds up as the fill-up nears full; reduce-motion turns it off. Pure maths with a vertex sink,
 * so the self-test can count and bound the quads without a client.
 */
public final class TelegraphMesh {
	private TelegraphMesh() {}

	public interface Sink {
		void vertex(float x, float y, float z, int argb);
	}

	/** Distance LOD (POLISH.md rule 4): full detail under 24 blocks, reduced under 48, minimal under 64, nothing beyond. */
	public static final double FULL = 24, REDUCED = 48, MAX = 64;
	static final float EDGE = 0.32F;

	/** 0 full, 1 reduced, 2 minimal, -1 not drawn. */
	public static int lod(double distSqr) {
		if (distSqr < FULL * FULL) return 0;
		if (distSqr < REDUCED * REDUCED) return 1;
		if (distSqr < MAX * MAX) return 2;
		return -1;
	}

	/** Segments for an arc of this radius and span: about 0.6 / 1.2 / 2.4 blocks per segment by LOD, capped. */
	public static int segments(double radius, double spanDeg, int lod) {
		double frac = Math.min(1.0, Math.abs(spanDeg) / 360.0);
		double step = lod <= 0 ? 0.6 : lod == 1 ? 1.2 : 2.4;
		int min = Math.max(4, (int) Math.ceil((lod <= 0 ? 32 : lod == 1 ? 16 : 10) * frac));
		int max = Math.max(min, (int) Math.ceil((lod <= 0 ? 112 : lod == 1 ? 56 : 28) * frac));
		int n = (int) Math.ceil(radius * Math.toRadians(Math.abs(spanDeg)) / step);
		return Mth.clamp(n, min, max);
	}

	/** Fill-up progress 0..1 at {@code now} (client ticks with partial tick). */
	public static float progress(TelegraphStore.Slot s, double now) {
		double age = Math.max(0, now - s.start);
		if ((s.flags & TelegraphNet.OPEN) != 0) return (float) (1.0 - Math.exp(-age / 20.0));
		double dur = Math.max(1, s.end - s.start);
		return (float) Mth.clamp(age / dur, 0, 1);
	}

	/**
	 * Emits the shape's quads relative to the camera at {@code now}. Returns the number of quads (4 vertices each); 0 if
	 * nothing is visible (expired, wave past its end, or LOD -1).
	 */
	public static int emit(TelegraphStore.Slot s, double camX, double camY, double camZ, double now, int lod, boolean motion, Sink out) {
		if (!s.used || lod < 0) return 0;
		double age = now - s.start, left = s.end - now;
		if (left <= 0 || age < 0) return 0;
		float fade = (float) (Mth.clamp(age / 3.0, 0, 1) * Mth.clamp(left / 3.0, 0, 1));
		float p = progress(s, now);
		float pulse = motion ? 0.75F + 0.25F * Mth.sin((float) (age / 20.0 * Math.PI * 2 * (1 + 2 * p))) : 1F;
		int rgb = s.argb & 0xFFFFFF;
		int fill = color(rgb, 0.18F * fade);
		int up = color(rgb, (0.16F + 0.22F * p) * fade);
		int edge = color(rgb, (0.55F + 0.3F * p) * pulse * fade);
		double cx = s.x - camX, cy = s.y - camY + 0.02, cz = s.z - camZ;
		int q = 0;
		switch (s.kind) {
			case TelegraphNet.RING -> {
				double w = s.b > 0.05F ? s.b : 0.4;
				int n = segments(s.a, 360, lod);
				if (lod < 2) q += annulus(out, cx, cy, cz, s.a - w * 0.5 - 0.9 * p, s.a - w * 0.5, 0, 360, n, up);
				q += annulus(out, cx, cy + 0.01, cz, s.a - w * 0.5, s.a + w * 0.5, 0, 360, n, edge);
			}
			case TelegraphNet.DISC -> {
				int n = segments(s.a, 360, lod);
				q += annulus(out, cx, cy, cz, 0, s.a, 0, 360, n, fill);
				if (lod < 2 && p > 0.01F) q += annulus(out, cx, cy + 0.005, cz, 0, s.a * p, 0, 360, segments(s.a * p, 360, lod), up);
				q += annulus(out, cx, cy + 0.01, cz, s.a - EDGE, s.a, 0, 360, n, edge);
			}
			case TelegraphNet.CONE -> {
				double half = Math.min(180, Math.abs(s.b)), len = s.c;
				double y0 = s.a - half, y1 = s.a + half;
				int n = segments(len, half * 2, lod);
				q += annulus(out, cx, cy, cz, 0, len, y0, y1, n, fill);
				if (lod < 2 && p > 0.01F) q += annulus(out, cx, cy + 0.005, cz, 0, len * p, y0, y1, segments(len * p, half * 2, lod), up);
				q += annulus(out, cx, cy + 0.01, cz, len - EDGE, len, y0, y1, n, edge);
				if (half < 179) {
					q += ray(out, cx, cy + 0.01, cz, y0, len, edge);
					q += ray(out, cx, cy + 0.01, cz, y1, len, edge);
				}
			}
			case TelegraphNet.LINE -> q += line(out, s, cx, cy, cz, p, lod, fill, up, edge);
			case TelegraphNet.WAVE -> {
				double r = s.a + s.b * age;
				if (r > s.c) return 0;
				double w = s.d > 0.05F ? s.d : 0.8;
				float tail = (float) Mth.clamp(1.0 - r / Math.max(1, s.c), 0.25, 1.0);
				int n = segments(r, 360, lod);
				int wedge = color(rgb, 0.8F * fade * tail);
				if (lod < 2) q += annulus(out, cx, cy, cz, r - w * 2.5, r - w * 0.5, 0, 360, n, color(rgb, 0.14F * fade * tail));
				q += annulus(out, cx, cy + 0.01, cz, r - w * 0.5, r + w * 0.5, 0, 360, n, wedge);
			}
			default -> {
			}
		}
		return q;
	}

	private static int line(Sink out, TelegraphStore.Slot s, double cx, double cy, double cz, float p, int lod, int fill, int up, int edge) {
		double dx = s.a, dy = s.b, dz = s.c;
		double h = Math.sqrt(dx * dx + dz * dz);
		double len = Math.sqrt(h * h + dy * dy);
		if (len < 1.0E-3) return 0;
		double w = Math.max(0.3, s.d);
		if (Math.abs(dy) > h) {
			// a column (warning beam): two crossed vertical ribbons
			int q = 0;
			for (int i = 0; i < 2; i++) {
				double sx = i == 0 ? w * 0.5 : 0, sz = i == 0 ? 0 : w * 0.5;
				q += quad(out, cx - sx, cy, cz - sz, cx + sx, cy, cz + sz, cx + dx + sx, cy + dy, cz + dz + sz, cx + dx - sx, cy + dy, cz + dz - sz, edge);
				if (p > 0.01F && lod < 2) {
					double ex = dx * p, ey = dy * p, ez = dz * p;
					q += quad(out, cx - sx, cy, cz - sz, cx + sx, cy, cz + sz, cx + ex + sx, cy + ey, cz + ez + sz, cx + ex - sx, cy + ey, cz + ez - sz, up);
				}
			}
			return q;
		}
		double ux = dx / len, uy = dy / len, uz = dz / len;
		double vx = -dz / h, vz = dx / h;
		int q = strip(out, cx, cy, cz, ux, uy, uz, vx, vz, 0, len, -w / 2, w / 2, fill);
		if (lod < 2 && p > 0.01F) q += strip(out, cx, cy + 0.005, cz, ux, uy, uz, vx, vz, 0, len * p, -w / 2, w / 2, up);
		double e = Math.min(EDGE * 0.6, w * 0.3);
		q += strip(out, cx, cy + 0.01, cz, ux, uy, uz, vx, vz, 0, len, -w / 2, -w / 2 + e, edge);
		q += strip(out, cx, cy + 0.01, cz, ux, uy, uz, vx, vz, 0, len, w / 2 - e, w / 2, edge);
		q += strip(out, cx, cy + 0.01, cz, ux, uy, uz, vx, vz, len - e, len, -w / 2 + e, w / 2 - e, edge);
		return q;
	}

	/** A rectangle from t0 to t1 along u and from w0 to w1 along the horizontal side vector v. */
	private static int strip(Sink out, double cx, double cy, double cz, double ux, double uy, double uz, double vx, double vz,
			double t0, double t1, double w0, double w1, int argb) {
		if (t1 <= t0) return 0;
		return quad(out,
			cx + ux * t0 + vx * w0, cy + uy * t0, cz + uz * t0 + vz * w0,
			cx + ux * t0 + vx * w1, cy + uy * t0, cz + uz * t0 + vz * w1,
			cx + ux * t1 + vx * w1, cy + uy * t1, cz + uz * t1 + vz * w1,
			cx + ux * t1 + vx * w0, cy + uy * t1, cz + uz * t1 + vz * w0, argb);
	}

	/** A thin band along one cone side, from the apex out to {@code len}. */
	private static int ray(Sink out, double cx, double cy, double cz, double yawDeg, double len, int argb) {
		float yaw = (float) Math.toRadians(yawDeg);
		double ux = -Mth.sin(yaw), uz = Mth.cos(yaw);
		return strip(out, cx, cy, cz, ux, 0, uz, -uz, ux, 0, len, -EDGE / 2, EDGE / 2, argb);
	}

	/** Ring band (r0 = 0 gives a disc) between two Minecraft yaws, in {@code segs} quads. */
	static int annulus(Sink out, double cx, double cy, double cz, double r0, double r1, double yaw0, double yaw1, int segs, int argb) {
		r0 = Math.max(0, r0);
		if (r1 <= r0 || segs <= 0) return 0;
		float y = (float) cy;
		double step = (yaw1 - yaw0) / segs;
		float a = (float) Math.toRadians(yaw0);
		double ps = -Mth.sin(a), pc = Mth.cos(a);
		for (int i = 1; i <= segs; i++) {
			float b = (float) Math.toRadians(yaw0 + step * i);
			double s = -Mth.sin(b), c = Mth.cos(b);
			out.vertex((float) (cx + ps * r0), y, (float) (cz + pc * r0), argb);
			out.vertex((float) (cx + ps * r1), y, (float) (cz + pc * r1), argb);
			out.vertex((float) (cx + s * r1), y, (float) (cz + c * r1), argb);
			out.vertex((float) (cx + s * r0), y, (float) (cz + c * r0), argb);
			ps = s;
			pc = c;
		}
		return segs;
	}

	private static int quad(Sink out, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
			double x3, double y3, double z3, int argb) {
		out.vertex((float) x0, (float) y0, (float) z0, argb);
		out.vertex((float) x1, (float) y1, (float) z1, argb);
		out.vertex((float) x2, (float) y2, (float) z2, argb);
		out.vertex((float) x3, (float) y3, (float) z3, argb);
		return 1;
	}

	static int color(int rgb, float alpha) {
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		return a << 24 | rgb;
	}
}
