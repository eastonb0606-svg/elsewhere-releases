package dev.elsewhere.kingdom.armies.impostor;

/**
 * Where the figures of a rank impostor stand (ARMIES.md 3.3 / 3.6): pure, deterministic maths shared by the server (break-out spots,
 * hit tests, bounding box) and the client renderer, so both sides agree to the pixel with nothing but the synced shape, slot count,
 * width, spacing, jitter and the entity's own seed.
 * <p>Offsets are local: {@code across} to the rank's right, {@code along} forward (the heading). The formation is centred on the entity.
 * Kingdom looks come from {@code army.scale.formation}: Aldermark 5 wide by 2 at 1.25 (neat lines), Hrafnholt 8 wide by 2 at 1.1 with a
 * little jitter (shield wall; a boar's-snout wedge when it surges), Azmar 4 by 4 at 1.1 (the rank block), Mirewick 6 wide at 1.3 with
 * heavy jitter and a gap in the middle (two loose watches).
 */
public final class RankLayout {
	private RankLayout() {}

	/** Most figures one rank entity draws. */
	public static final int MAX = 16;

	public enum Shape {
		/** Rows of {@code width} (the kingdom's battle line). */
		LINE,
		/** A square block (Azmar's rank block; any kingdom's tight reserve). */
		BLOCK,
		/** Two abreast, marching. */
		COLUMN,
		/** Hrafnholt's boar's snout: rows of 1, 3, 5, 7. */
		WEDGE;

		public static Shape of(int i) {
			Shape[] v = values();
			return v[Math.floorMod(i, v.length)];
		}
	}

	/**
	 * Fills {@code out} with {@code 2 * slots} floats: (across, along) of each slot. {@code jitter} at 0.5 or more also opens a one-spacing
	 * gap in the middle of every row (Mirewick's two watches).
	 */
	public static void offsets(Shape shape, int slots, int width, float spacing, float jitter, long seed, float[] out) {
		slots = Math.max(0, Math.min(MAX, slots));
		if (slots == 0) return;
		float sp = Math.max(0.8F, spacing);
		int w = switch (shape) {
			case LINE -> Math.max(1, Math.min(width, slots));
			case BLOCK -> Math.max(1, (int) Math.ceil(Math.sqrt(slots)));
			case COLUMN -> Math.min(2, slots);
			case WEDGE -> 0;
		};
		boolean gap = jitter >= 0.5F && shape == Shape.LINE && w >= 4;
		if (shape == Shape.WEDGE) {
			// rows of 1, 3, 5, 7: the point leads
			int row = 0, inRow = 0, rowLen = 1;
			int rows = 0, left = slots, len = 1;
			while (left > 0) {
				left -= len;
				len += 2;
				rows++;
			}
			float back = (rows - 1) * sp * 0.5F;
			for (int i = 0; i < slots; i++) {
				int n = Math.min(rowLen, slots - (i - inRow));
				out[2 * i] = (inRow - (n - 1) * 0.5F) * sp;
				out[2 * i + 1] = back - row * sp;
				if (++inRow >= rowLen) {
					inRow = 0;
					row++;
					rowLen += 2;
				}
			}
		} else {
			int rows = (slots + w - 1) / w;
			float back = (rows - 1) * sp * 0.5F;
			for (int i = 0; i < slots; i++) {
				int r = i / w, c = i % w;
				int n = r == rows - 1 ? slots - r * w : w; // the last row is centred
				float a = (c - (n - 1) * 0.5F) * sp;
				if (gap) a += (c < n / 2 ? -0.5F : 0.5F) * sp;
				out[2 * i] = a;
				out[2 * i + 1] = back - r * sp;
			}
		}
		if (jitter > 0) {
			float j = Math.min(jitter, 1.0F) * 0.5F;
			for (int i = 0; i < slots; i++) {
				out[2 * i] += (hash01(seed, i * 2) - 0.5F) * 2 * j;
				out[2 * i + 1] += (hash01(seed, i * 2 + 1) - 0.5F) * 2 * j;
			}
		}
	}

	/** World x offset of a local (across, along) offset for heading {@code yawDeg} (entity yRot: 0 faces +z). */
	public static double worldX(float across, float along, float yawDeg) {
		double a = Math.toRadians(yawDeg);
		return -across * Math.cos(a) - along * Math.sin(a);
	}

	/** World z offset of a local (across, along) offset for heading {@code yawDeg}. */
	public static double worldZ(float across, float along, float yawDeg) {
		double a = Math.toRadians(yawDeg);
		return -across * Math.sin(a) + along * Math.cos(a);
	}

	/** A stable pseudo-random value in [0, 1) for (seed, i): figure jitter, walk phase, swing timing. */
	public static float hash01(long seed, int i) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (float) (1L << 24);
	}
}
