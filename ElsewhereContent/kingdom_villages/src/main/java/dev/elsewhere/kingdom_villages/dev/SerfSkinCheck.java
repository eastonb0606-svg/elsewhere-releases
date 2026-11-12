package dev.elsewhere.kingdom_villages.dev;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 3.0 QA fix (s-townsfolk-art): the four Serf-tier skins (serf, thrall, porter, peat_cutter) used to be recolours of one
 * skin and looked identical. Each is now its own outfit; this checks they exist, are usable 64x64 player skins and differ
 * clearly pairwise (the old recolours scored 0.15 on this measure). Pure resource reads (small PNG decoder, no AWT, no
 * world): {@code SerfSkinCheck.run()} once from DevEntry's villages.selftest block.
 */
public final class SerfSkinCheck {
	private SerfSkinCheck() {}

	static final String[] VARIANTS = {"serf", "thrall", "porter", "peat_cutter"};
	/** Share of visible pixels that must differ clearly between any two Serf-tier skins. */
	static final double MIN_APART = 0.40;

	public static void run() {
		List<String> missing = new ArrayList<>(), badFormat = new ArrayList<>();
		int[][] seen = new int[VARIANTS.length][];
		for (int i = 0; i < VARIANTS.length; i++) {
			String file = VARIANTS[i].equals("serf") ? "serf" : "serf_" + VARIANTS[i];
			try {
				Png png = Png.resource("assets/elsewhere/textures/entity/village/" + file + ".png");
				if (png == null) missing.add(file);
				else if (png.skinProblem() != null) badFormat.add(file + " (" + png.skinProblem() + ")");
				else seen[i] = png.visible();
			} catch (IOException e) {
				badFormat.add(file + " (" + e.getMessage() + ")");
			}
		}
		SelfTest.check("serf variant skins present (serf, thrall, porter, peat_cutter)", missing.isEmpty(), missing.isEmpty() ? null : missing);
		SelfTest.check("serf variant skins: 64x64 player layout, opaque base layer", badFormat.isEmpty(), badFormat.isEmpty() ? null : badFormat);
		double worst = 1;
		String worstAt = "none";
		for (int a = 0; a < VARIANTS.length; a++) {
			for (int b = a + 1; b < VARIANTS.length; b++) {
				if (seen[a] == null || seen[b] == null) continue;
				double d = Png.differs(seen[a], seen[b]);
				if (d < worst) {
					worst = d;
					worstAt = VARIANTS[a] + "-" + VARIANTS[b];
				}
			}
		}
		SelfTest.check("serf variant skins clearly distinct (>=" + (int) (MIN_APART * 100) + "% of pixels per pair)",
			missing.isEmpty() && badFormat.isEmpty() && worst >= MIN_APART, String.format("closest %s %.2f", worstAt, worst));
	}

	/** Minimal 8-bit, non-interlaced PNG reader (RGBA, RGB or palette) plus the player-skin layout. */
	record Png(int w, int h, int[] argb) {
		/** Box (u, v, w, h, d) of head, body, right arm, left arm, right leg, left leg, then their overlay layers. */
		static final int[][] BASE = {{0, 0, 8, 8, 8}, {16, 16, 8, 12, 4}, {40, 16, 4, 12, 4}, {32, 48, 4, 12, 4}, {0, 16, 4, 12, 4}, {16, 48, 4, 12, 4}};
		static final int[][] OVER = {{32, 0, 8, 8, 8}, {16, 32, 8, 12, 4}, {40, 32, 4, 12, 4}, {48, 48, 4, 12, 4}, {0, 32, 4, 12, 4}, {0, 48, 4, 12, 4}};

		static Png resource(String path) throws IOException {
			ClassLoader cl = SerfSkinCheck.class.getClassLoader();
			try (InputStream in = cl.getResourceAsStream(path)) {
				return in == null ? null : decode(in.readAllBytes());
			}
		}

		/** The six faces of a box as {x, y, w, h}: top, bottom, right, front, left, back. */
		static int[][] faces(int[] b) {
			int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4];
			return new int[][] {{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + 2 * d + w, v + d, w, h}};
		}

		int at(int x, int y) {
			return argb[y * w + x];
		}

		/** Null when this is a usable skin, else what is wrong. */
		String skinProblem() {
			if (w != 64 || h != 64) return "size " + w + "x" + h;
			int holes = 0, semi = 0;
			for (int[] box : BASE) for (int[] f : faces(box)) for (int y = f[1]; y < f[1] + f[3]; y++) for (int x = f[0]; x < f[0] + f[2]; x++) if (at(x, y) >>> 24 != 255) holes++;
			for (int[] box : OVER) for (int[] f : faces(box)) for (int y = f[1]; y < f[1] + f[3]; y++) for (int x = f[0]; x < f[0] + f[2]; x++) {
				int a = at(x, y) >>> 24;
				if (a != 0 && a != 255) semi++;
			}
			if (holes > 0) return holes + " see-through base pixels";
			if (semi > 0) return semi + " half-transparent overlay pixels";
			return null;
		}

		/** What the player sees on every base face: the overlay pixel where it is opaque, else the base pixel (RGB). */
		int[] visible() {
			int n = 0;
			for (int[] box : BASE) for (int[] f : faces(box)) n += f[2] * f[3];
			int[] out = new int[n];
			int i = 0;
			for (int p = 0; p < BASE.length; p++) {
				int[][] bf = faces(BASE[p]), of = faces(OVER[p]);
				for (int k = 0; k < 6; k++) {
					for (int y = 0; y < bf[k][3]; y++) {
						for (int x = 0; x < bf[k][2]; x++) {
							int o = at(of[k][0] + x, of[k][1] + y);
							out[i++] = (o >>> 24 == 255 ? o : at(bf[k][0] + x, bf[k][1] + y)) & 0xFFFFFF;
						}
					}
				}
			}
			return out;
		}

		/** Share of pixels whose colours differ clearly (sum of channel differences above 60). */
		static double differs(int[] a, int[] b) {
			int n = 0;
			for (int i = 0; i < a.length; i++) {
				int d = Math.abs((a[i] >> 16 & 255) - (b[i] >> 16 & 255)) + Math.abs((a[i] >> 8 & 255) - (b[i] >> 8 & 255)) + Math.abs((a[i] & 255) - (b[i] & 255));
				if (d > 60) n++;
			}
			return n / (double) a.length;
		}

		static int be32(byte[] d, int p) {
			return (d[p] & 255) << 24 | (d[p + 1] & 255) << 16 | (d[p + 2] & 255) << 8 | d[p + 3] & 255;
		}

		static int paeth(int a, int b, int c) {
			int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
			return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
		}

		static Png decode(byte[] d) throws IOException {
			if (d.length < 8 || (d[0] & 255) != 0x89 || d[1] != 'P' || d[2] != 'N' || d[3] != 'G') throw new IOException("not a PNG");
			int pos = 8, w = 0, h = 0, depth = 0, type = 0, interlace = 0;
			byte[] plte = null, trns = null;
			ByteArrayOutputStream idat = new ByteArrayOutputStream();
			while (pos + 8 <= d.length) {
				int len = be32(d, pos), s = pos + 8;
				String t = new String(d, pos + 4, 4, StandardCharsets.US_ASCII);
				if (len < 0 || s + len > d.length) throw new IOException("truncated " + t);
				switch (t) {
					case "IHDR" -> {
						w = be32(d, s);
						h = be32(d, s + 4);
						depth = d[s + 8] & 255;
						type = d[s + 9] & 255;
						interlace = d[s + 12] & 255;
					}
					case "PLTE" -> plte = Arrays.copyOfRange(d, s, s + len);
					case "tRNS" -> trns = Arrays.copyOfRange(d, s, s + len);
					case "IDAT" -> idat.write(d, s, len);
					default -> {
					}
				}
				if (t.equals("IEND")) break;
				pos = s + len + 4;
			}
			if (w <= 0 || h <= 0 || w > 4096 || h > 4096) throw new IOException("bad size " + w + "x" + h);
			if (depth != 8 || interlace != 0 || (type != 6 && type != 2 && type != 3)) throw new IOException("unsupported PNG (depth " + depth + ", type " + type + ", interlace " + interlace + ")");
			int bpp = type == 6 ? 4 : type == 2 ? 3 : 1, stride = w * bpp;
			byte[] raw = new byte[(stride + 1) * h];
			Inflater inf = new Inflater();
			int n = 0;
			try {
				inf.setInput(idat.toByteArray());
				while (n < raw.length && !inf.finished()) {
					int k = inf.inflate(raw, n, raw.length - n);
					if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
					n += k;
				}
			} catch (DataFormatException e) {
				throw new IOException("bad image data: " + e.getMessage());
			} finally {
				inf.end();
			}
			if (n < raw.length) throw new IOException("short image data " + n + "/" + raw.length);
			byte[] cur = new byte[stride], prev = new byte[stride];
			int[] argb = new int[w * h];
			for (int y = 0; y < h; y++) {
				int row = y * (stride + 1), f = raw[row] & 255;
				for (int i = 0; i < stride; i++) {
					int x = raw[row + 1 + i] & 255, a = i >= bpp ? cur[i - bpp] & 255 : 0, b = prev[i] & 255, c = i >= bpp ? prev[i - bpp] & 255 : 0;
					int v = switch (f) {
						case 0 -> x;
						case 1 -> x + a;
						case 2 -> x + b;
						case 3 -> x + ((a + b) >> 1);
						case 4 -> x + paeth(a, b, c);
						default -> throw new IOException("bad filter " + f + " in row " + y);
					};
					cur[i] = (byte) v;
				}
				for (int px = 0; px < w; px++) {
					int o = px * bpp, r, g, bl, al;
					if (type == 6) {
						r = cur[o] & 255;
						g = cur[o + 1] & 255;
						bl = cur[o + 2] & 255;
						al = cur[o + 3] & 255;
					} else if (type == 2) {
						r = cur[o] & 255;
						g = cur[o + 1] & 255;
						bl = cur[o + 2] & 255;
						al = 255;
					} else {
						int idx = cur[o] & 255;
						if (plte == null || idx * 3 + 2 >= plte.length) throw new IOException("palette index " + idx + " out of range");
						r = plte[idx * 3] & 255;
						g = plte[idx * 3 + 1] & 255;
						bl = plte[idx * 3 + 2] & 255;
						al = trns != null && idx < trns.length ? trns[idx] & 255 : 255;
					}
					argb[y * w + px] = al << 24 | r << 16 | g << 8 | bl;
				}
				byte[] tmp = prev;
				prev = cur;
				cur = tmp;
			}
			return new Png(w, h, argb);
		}
	}
}
