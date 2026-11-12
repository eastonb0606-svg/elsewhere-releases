package dev.elsewhere.kingdom.dev;

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
 * 3.0 townsfolk art (s-townsfolk-art): the 36 skins TownsfolkRenderer asks for
 * ({@code textures/entity/townsfolk/<kingdom>/<role>_<0|1|2>.png}, ids from _swarm/kingdom/TOWNSFOLK-IDS.md) exist, are
 * 64x64 wide-arm player skins with an opaque base layer, the three people of each role differ, and no two roles look
 * alike. Pure resource reads (a small PNG decoder, no AWT, no world), so it can run once at self-test start:
 * {@code SelfTestTownsfolkSkins.run()} inside DevEntry's kingdom.selftest block, or {@code run((n, ok, d) -> check(n, ok, d))}
 * from TownsfolkSelfTest.
 */
public final class SelfTestTownsfolkSkins {
	private SelfTestTownsfolkSkins() {}

	/** Kingdom and role ids, as in TOWNSFOLK-IDS.md. */
	static final String[][] ROLES = {
		{"aldermark", "peasant"}, {"aldermark", "craftsman"},
		{"hrafnholt", "thrall"}, {"hrafnholt", "fisherfolk"},
		{"azmar", "priest"}, {"azmar", "scribe"}, {"azmar", "porter"}, {"azmar", "merchant"},
		{"mirewick", "clerk"}, {"mirewick", "bargeman"}, {"mirewick", "lamplighter"}, {"mirewick", "peat_cutter"}};
	static final int PEOPLE = 3;
	/** Share of visible pixels that must differ clearly: between the people of one role, and between any two roles. */
	static final double MIN_PEOPLE = 0.12, MIN_ROLES = 0.30;

	/** Where the check lines go: the kingdom self-test by default, or another harness such as TownsfolkSelfTest's check. */
	public interface Sink {
		void check(String name, boolean ok, Object detail);
	}

	/** Reports into the kingdom self-test ({@code -Dkingdom.selftest=true}). */
	public static void run() {
		run(SelfTest::check);
	}

	public static void run(Sink sink) {
		List<String> missing = new ArrayList<>(), badFormat = new ArrayList<>();
		int[][][] seen = new int[ROLES.length][PEOPLE][];
		for (int r = 0; r < ROLES.length; r++) {
			for (int v = 0; v < PEOPLE; v++) {
				String name = ROLES[r][0] + "/" + ROLES[r][1] + "_" + v;
				Png png;
				try {
					png = Png.resource("assets/elsewhere/textures/entity/townsfolk/" + name + ".png");
				} catch (IOException e) {
					badFormat.add(name + " (" + e.getMessage() + ")");
					continue;
				}
				if (png == null) {
					missing.add(name);
					continue;
				}
				String why = png.skinProblem();
				if (why != null) badFormat.add(name + " (" + why + ")");
				else seen[r][v] = png.visible();
			}
		}
		sink.check("townsfolk skins: all " + ROLES.length * PEOPLE + " role textures present", missing.isEmpty(), missing.isEmpty() ? null : missing);
		sink.check("townsfolk skins: 64x64 player layout, opaque base layer, overlay alpha 0/255", badFormat.isEmpty(), badFormat.isEmpty() ? null : badFormat);

		double worstPeople = 1;
		String worstPeopleAt = "none";
		for (int r = 0; r < ROLES.length; r++) {
			for (int a = 0; a < PEOPLE; a++) {
				for (int b = a + 1; b < PEOPLE; b++) {
					if (seen[r][a] == null || seen[r][b] == null) continue;
					double d = Png.differs(seen[r][a], seen[r][b]);
					if (d < worstPeople) {
						worstPeople = d;
						worstPeopleAt = ROLES[r][0] + "/" + ROLES[r][1] + " " + a + "-" + b;
					}
				}
			}
		}
		sink.check("townsfolk skins: the 3 people of each role look different (>=" + (int) (MIN_PEOPLE * 100) + "% of pixels)",
			missing.isEmpty() && badFormat.isEmpty() && worstPeople >= MIN_PEOPLE, String.format("closest %s %.2f", worstPeopleAt, worstPeople));

		double worstRoles = 1;
		String worstRolesAt = "none";
		for (int r = 0; r < ROLES.length; r++) {
			for (int q = r + 1; q < ROLES.length; q++) {
				for (int a = 0; a < PEOPLE; a++) {
					for (int b = 0; b < PEOPLE; b++) {
						if (seen[r][a] == null || seen[q][b] == null) continue;
						double d = Png.differs(seen[r][a], seen[q][b]);
						if (d < worstRoles) {
							worstRoles = d;
							worstRolesAt = ROLES[r][0] + "/" + ROLES[r][1] + "_" + a + " vs " + ROLES[q][0] + "/" + ROLES[q][1] + "_" + b;
						}
					}
				}
			}
		}
		sink.check("townsfolk skins: no two roles look alike (>=" + (int) (MIN_ROLES * 100) + "% of pixels)",
			missing.isEmpty() && badFormat.isEmpty() && worstRoles >= MIN_ROLES, String.format("closest %s %.2f", worstRolesAt, worstRoles));
	}

	/** Minimal 8-bit, non-interlaced PNG reader (RGBA, RGB or palette) plus the player-skin layout. */
	record Png(int w, int h, int[] argb) {
		/** Box (u, v, w, h, d) of head, body, right arm, left arm, right leg, left leg, then their overlay layers. */
		static final int[][] BASE = {{0, 0, 8, 8, 8}, {16, 16, 8, 12, 4}, {40, 16, 4, 12, 4}, {32, 48, 4, 12, 4}, {0, 16, 4, 12, 4}, {16, 48, 4, 12, 4}};
		static final int[][] OVER = {{32, 0, 8, 8, 8}, {16, 32, 8, 12, 4}, {40, 32, 4, 12, 4}, {48, 48, 4, 12, 4}, {0, 32, 4, 12, 4}, {0, 48, 4, 12, 4}};

		static Png resource(String path) throws IOException {
			ClassLoader cl = SelfTestTownsfolkSkins.class.getClassLoader();
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
