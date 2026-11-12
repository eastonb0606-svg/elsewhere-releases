package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.heraldry.Heraldry;
import dev.elsewhere.kingdom.heraldry.Heraldry.Kingdom;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;

/**
 * 3.0 polish P10 (s-p10-tabard): kingdom emblems and soldier capes. The drawing is client-only, so this checks what
 * decides it: faction resolution, ranks from real soldiers' synced data, every texture's size and layout (emblem pixels
 * only on tabard cloth, lining matching the outer cut, each kingdom distinct and in its palette), the distance LOD, the
 * cape wave maths (bounded, never into the legs, no phase jump when the speed changes) and the kill-switch parsing.
 * Added to SelfTest's step list by {@code SelfTest.build()}.
 */
public final class SelfTestHeraldry {
	private SelfTestHeraldry() {}

	static void add() {
		STEPS.add((srv, l) -> {
			clean(l);
			resolve();
			soldiers(l);
			textures();
			lod();
			wave();
			switches();
			clean(l);
			return 2;
		});
	}

	static void resolve() {
		Set<Heraldry.Style> seen = new HashSet<>();
		boolean all = true;
		for (Kingdom k : Kingdom.values()) {
			Heraldry.Style s = Heraldry.resolve("elsewhere:" + k.path);
			all &= s != null && s.kingdom() == k && !s.remnant() && s.tint() == Heraldry.NO_TINT;
			seen.add(s);
		}
		check("heraldry: each of the four kingdoms resolves to its own heraldry", all && seen.size() == 4, seen);
		Heraldry.Style r = Heraldry.resolve("elsewhere:hrafnholt_remnant");
		check("heraldry: a remnant keeps its kingdom's heraldry, faded", r != null && r.kingdom() == Kingdom.HRAFNHOLT && r.remnant() && r.tint() == Heraldry.REMNANT_TINT,
			r);
		String[] none = {"elsewhere:rebels", "elsewhere:free_company", "", "minecraft:aldermark", "elsewhere:aldermarkish", "elsewhere:mirewick_remnantx", "aldermark"};
		boolean clear = Heraldry.resolve(null) == null;
		for (String f : none) clear &= Heraldry.resolve(f) == null;
		check("heraldry: other factions (rebels, free companies, unknown ids) get none", clear, null);
		Heraldry.Style a = Heraldry.resolve("elsewhere:aldermark");
		check("heraldry: capes by rank (none / officer short / captain long)",
			a != null && a.cape(0) == null && Kingdom.ALDERMARK.capeShort.equals(a.cape(1)) && Kingdom.ALDERMARK.cape.equals(a.cape(2)), null);
	}

	/** Real soldiers: the synced faction and rank the client reads pick the heraldry. */
	static void soldiers(net.minecraft.server.level.ServerLevel l) {
		Identifier a = Factions.ALDERMARK;
		KingdomSoldier pawn = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, a, at(0, 64), null);
		KingdomSoldier sgt = KingdomApi.spawnSoldier(l, UnitTypes.SERGEANT, a, at(2, 64), null);
		KingdomSoldier cap = KingdomApi.spawnSoldier(l, UnitTypes.CAPTAIN, a, at(4, 64), null);
		boolean ok = pawn != null && sgt != null && cap != null;
		if (ok) {
			Heraldry.Style s = Heraldry.resolve(pawn.syncedFaction());
			ok = s != null && s.kingdom() == Kingdom.ALDERMARK && s.cape(pawn.rank()) == null && Kingdom.ALDERMARK.capeShort.equals(s.cape(sgt.rank()))
				&& Kingdom.ALDERMARK.cape.equals(s.cape(cap.rank()));
		}
		check("heraldry: Aldermark man-at-arms / sergeant / captain get emblem, emblem + short cape, emblem + long cape", ok,
			ok ? null : pawn == null ? "no spawn" : pawn.syncedFaction() + " ranks " + pawn.rank() + "/" + (sgt == null ? -1 : sgt.rank()) + "/" + (cap == null ? -1 : cap.rank()));
		if (pawn != null) {
			pawn.setFaction(Factions.REBELS);
			check("heraldry: a soldier that changes to a faction without heraldry drops it", Heraldry.resolve(pawn.syncedFaction()) == null, pawn.syncedFaction());
		}
	}

	static BufferedImage png(Identifier id) {
		try (InputStream in = SelfTestHeraldry.class.getResourceAsStream("/assets/" + id.getNamespace() + "/" + id.getPath())) {
			return in == null ? null : ImageIO.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	static boolean opaque(BufferedImage im, int x, int y) {
		return (im.getRGB(x, y) >>> 24) != 0;
	}

	static void textures() {
		BufferedImage base = png(Heraldry.BASE_TABARD), plain = png(Heraldry.PLAIN_TABARD);
		List<String> bad = new ArrayList<>();
		List<BufferedImage> emblems = new ArrayList<>();
		for (Kingdom k : Kingdom.values()) {
			BufferedImage e = png(k.emblem), c = png(k.cape), s = png(k.capeShort);
			if (e == null || e.getWidth() != 64 || e.getHeight() != 32) bad.add(k.emblem.getPath());
			else emblems.add(e);
			for (BufferedImage im : new BufferedImage[] {c, s})
				if (im == null || im.getWidth() != Heraldry.CAPE_TEX_W || im.getHeight() != Heraldry.CAPE_TEX_H) bad.add(k.path + " cape");
		}
		boolean basesOk = base != null && plain != null && base.getWidth() == 64 && plain.getWidth() == 64 && base.getHeight() == 32 && plain.getHeight() == 32;
		check("heraldry: 13 textures ship (4 emblems 64x32, 8 capes 32x16, plain tabard)", bad.isEmpty() && basesOk && emblems.size() == 4, bad);
		if (!bad.isEmpty() || !basesOk) return;

		// the plain tabard is the tabard cut minus the grey tower motif
		boolean sameCut = true, motif = false;
		for (int x = 0; x < 64; x++)
			for (int y = 0; y < 32; y++) {
				sameCut &= opaque(base, x, y) == opaque(plain, x, y);
				motif |= opaque(plain, x, y) && (plain.getRGB(x, y) & 0xFF) == 150;
			}
		check("heraldry: plain tabard has the tabard's cut and no tower motif", sameCut && !motif, null);

		// every emblem pixel sits on tabard cloth; chest and back both carry a charge
		List<String> stray = new ArrayList<>(), thin = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			BufferedImage e = emblems.get(i);
			int front = 0, back = 0;
			for (int x = 0; x < 64; x++)
				for (int y = 0; y < 32; y++) {
					if (!opaque(e, x, y)) continue;
					if (!opaque(base, x, y)) stray.add(Kingdom.values()[i].path + "@" + x + "," + y);
					if (x >= 20 && x < 28 && y >= 20) front++;
					if (x >= 32 && x < 40 && y >= 20) back++;
				}
			if (front < 24 || back < 24) thin.add(Kingdom.values()[i].path + " " + front + "/" + back);
		}
		check("heraldry: emblem pixels only on tabard cloth", stray.isEmpty(), stray.size() > 6 ? stray.subList(0, 6) + "..." : stray);
		check("heraldry: every emblem marks the chest and the back", thin.isEmpty(), thin);

		// the four chest emblems differ clearly
		int minDiff = Integer.MAX_VALUE;
		for (int i = 0; i < 4; i++)
			for (int j = i + 1; j < 4; j++) {
				int d = 0;
				for (int x = 20; x < 28; x++)
					for (int y = 20; y < 31; y++)
						if (differs(emblems.get(i).getRGB(x, y), emblems.get(j).getRGB(x, y))) d++;
				minDiff = Math.min(minDiff, d);
			}
		check("heraldry: the four chest emblems are distinct (>= 30 of 88 pixels differ per pair)", minDiff >= 30, minDiff);

		// lore palettes: Aldermark red/gold/cream, Hrafnholt browns (on green), Azmar gold + turquoise, Mirewick gold + crimson
		check("heraldry: Aldermark emblem has its red chief, gold crown and cream tower", has(emblems.get(0), 0xA3302E) && has(emblems.get(0), 0xF0C94A) && has(emblems.get(0), 0xECE3C2), null);
		check("heraldry: Hrafnholt emblem is brown (raven, hem) with an amber eye", has(emblems.get(1), 0x6B4A2B) && has(emblems.get(1), 0xD9A23A) && brownShare(emblems.get(1)) >= 0.75,
			brownShare(emblems.get(1)));
		check("heraldry: Azmar emblem has Egyptian gold, turquoise and lapis", has(emblems.get(2), 0xE2B240) && has(emblems.get(2), 0x2BB3A3) && has(emblems.get(2), 0x16245C), null);
		check("heraldry: Mirewick emblem has gold and crimson", has(emblems.get(3), 0xD6A832) && has(emblems.get(3), 0x9E1B32), null);

		// capes: the lining (u0-9) has the outer face's (u10-19) cut, mirrored; captains' capes are longer than officers'
		List<String> cut = new ArrayList<>(), len = new ArrayList<>();
		for (Kingdom k : Kingdom.values()) {
			BufferedImage c = png(k.cape), s = png(k.capeShort);
			for (BufferedImage im : new BufferedImage[] {c, s})
				for (int y = 0; y < Heraldry.CAPE_TEX_H; y++)
					for (int x = 0; x < Heraldry.CAPE_WIDTH; x++)
						if (opaque(im, 10 + x, y) != opaque(im, 9 - x, y)) cut.add(k.path + "@" + x + "," + y);
			int lc = lastRow(c), ls = lastRow(s);
			if (!(lc >= 12 && ls >= 6 && ls < lc - 3 && opaque(c, 14, 0) && opaque(s, 14, 0))) len.add(k.path + " " + ls + "/" + lc);
		}
		check("heraldry: cape lining matches the outer cut on every cape", cut.isEmpty(), cut.size() > 6 ? cut.subList(0, 6) + "..." : cut);
		check("heraldry: captain capes hang longer than officer capes and start at the shoulders", len.isEmpty(), len);
	}

	static boolean differs(int a, int b) {
		boolean oa = (a >>> 24) != 0, ob = (b >>> 24) != 0;
		if (oa != ob) return true;
		if (!oa) return false;
		int dr = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)), dg = Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)), db = Math.abs((a & 0xFF) - (b & 0xFF));
		return dr + dg + db > 60;
	}

	/** A pixel within a little noise of {@code rgb}. */
	static boolean has(BufferedImage im, int rgb) {
		for (int x = 0; x < im.getWidth(); x++)
			for (int y = 0; y < im.getHeight(); y++) {
				int p = im.getRGB(x, y);
				if ((p >>> 24) == 0) continue;
				if (Math.abs((p >> 16 & 0xFF) - (rgb >> 16 & 0xFF)) + Math.abs((p >> 8 & 0xFF) - (rgb >> 8 & 0xFF)) + Math.abs((p & 0xFF) - (rgb & 0xFF)) <= 18) return true;
			}
		return false;
	}

	/** Share of opaque pixels that are browns (red >= green >= blue) or the dark grey beak and iron buckle. */
	static double brownShare(BufferedImage im) {
		int n = 0, brown = 0;
		for (int x = 0; x < im.getWidth(); x++)
			for (int y = 0; y < im.getHeight(); y++) {
				int p = im.getRGB(x, y);
				if ((p >>> 24) == 0) continue;
				n++;
				int r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
				if ((r >= g && g >= b) || Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 16) brown++;
			}
		return n == 0 ? 0 : (double) brown / n;
	}

	static int lastRow(BufferedImage im) {
		for (int y = im.getHeight() - 1; y >= 0; y--)
			for (int x = 10; x < 20; x++)
				if (opaque(im, x, y)) return y;
		return -1;
	}

	static void lod() {
		boolean emblem = Heraldry.emblemVisible(10 * 10) && Heraldry.emblemVisible(63.9 * 63.9) && !Heraldry.emblemVisible(64 * 64) && !Heraldry.emblemVisible(200 * 200);
		check("heraldry: emblem drawn within 64 blocks only", emblem, null);
		boolean cape = Heraldry.capeLod(5 * 5, false) == Heraldry.CAPE_WAVE && Heraldry.capeLod(23.9 * 23.9, false) == Heraldry.CAPE_WAVE
			&& Heraldry.capeLod(24 * 24, false) == Heraldry.CAPE_FLAT && Heraldry.capeLod(63 * 63, false) == Heraldry.CAPE_FLAT
			&& Heraldry.capeLod(64 * 64, false) == Heraldry.CAPE_NONE && Heraldry.capeLod(4, true) == Heraldry.CAPE_NONE;
		check("heraldry: cape waves within 24 blocks, one piece to 64, none beyond or on LOW detail", cape, null);
		check("heraldry: cape height splits evenly into the wave panels", Heraldry.CAPE_HEIGHT % Heraldry.CAPE_SEGMENTS == 0 && Heraldry.CAPE_TEX_H == Heraldry.CAPE_HEIGHT, null);
	}

	/** Hem and every hinge, in body pixels: z of the cape at each panel joint (positive = behind the attach line). */
	static float minZ(float root, float t, float walk, float speed) {
		int n = Heraldry.CAPE_SEGMENTS;
		float h = Heraldry.CAPE_HEIGHT / (float) n, ang = root, z = 0F, min = 0F;
		for (int j = 0; j < n; j++) {
			if (j > 0) ang += Heraldry.jointDeg(j, n, t, walk, speed);
			z += h * (float) Math.sin(Math.toRadians(ang));
			min = Math.min(min, z);
		}
		return min;
	}

	static void wave() {
		int n = Heraldry.CAPE_SEGMENTS;
		float maxJoint = 0F, worstZ = Float.MAX_VALUE;
		for (float s = 0F; s <= 1.0001F; s += 0.125F) {
			float root = Heraldry.rootDeg(Heraldry.lean(s, 1F), 0F, false);
			for (float t = 0F; t < 400F; t += 0.37F) {
				float walk = t * s;
				for (int j = 1; j < n; j++) maxJoint = Math.max(maxJoint, Math.abs(Heraldry.jointDeg(j, n, t, walk, s)));
				worstZ = Math.min(worstZ, minZ(root, t, walk, s));
				// walking backwards (lean from a backward step): the hem still never swings into the legs
				worstZ = Math.min(worstZ, minZ(Heraldry.rootDeg(Heraldry.lean(s, -1F), 0F, false), t, walk, s));
			}
		}
		check("heraldry: wave bends stay small (<= 12 degrees a hinge)", maxJoint <= 12F, maxJoint);
		check("heraldry: the waving cape never swings forward into the legs (min z >= -0.5 px)", worstZ >= -0.5F, worstZ);

		// continuity: in time, and in speed at a large age (a speed change must not jump the wave's phase)
		float dt = 0F, ds = 0F;
		for (int j = 1; j < n; j++)
			for (float t = 0F; t < 300F; t += 1.3F) {
				dt = Math.max(dt, Math.abs(Heraldry.jointDeg(j, n, t + 0.05F, 10F, 0.6F) - Heraldry.jointDeg(j, n, t, 10F, 0.6F)));
				float big = 120000F + t;
				ds = Math.max(ds, Math.abs(Heraldry.jointDeg(j, n, big, 500F, 0.52F) - Heraldry.jointDeg(j, n, big, 500F, 0.50F)));
			}
		check("heraldry: wave is smooth in time (<= 0.5 degrees per 1/20 tick)", dt <= 0.5F, dt);
		check("heraldry: a speed change never jumps the wave (<= 0.5 degrees per 0.02 speed at age 120000)", ds <= 0.5F, ds);

		float rest = Heraldry.rootDeg(Heraldry.lean(0F, 1F), 0F, false), run = Heraldry.rootDeg(Heraldry.lean(1F, 1F), 0F, false);
		float back = Heraldry.rootDeg(Heraldry.lean(1F, -1F), 0F, false), ride = Heraldry.rootDeg(Heraldry.lean(0.5F, 1F), 0F, true);
		float fall = Heraldry.rootDeg(0F, Heraldry.flap(-0.4F), false);
		check("heraldry: moving lifts the cape (rest < backwards < forwards; riding and falling lift it)",
			rest == Heraldry.REST_DEG && back > rest && run > back + 10F && ride > Heraldry.rootDeg(Heraldry.lean(0.5F, 1F), 0F, false) && fall > rest + 10F,
			rest + "/" + back + "/" + run + "/" + ride + "/" + fall);
		boolean side = Heraldry.sideLean(1F, 1F) > 0F && Heraldry.sideLean(1F, -1F) < 0F && Heraldry.sideLean(0F, 1F) == 0F && Math.abs(Heraldry.sideLean(1F, 5F)) <= 20F;
		check("heraldry: a sideways step swings the hem the other way, bounded", side, null);
		float flat = Heraldry.flatDeg(run, 1F);
		check("heraldry: the far one-piece cape leans at least as far as the near cape's top", flat >= run && Heraldry.flatDeg(rest, 0F) == rest, flat);
	}

	static void switches() {
		boolean[] none = Heraldry.switches(null), empty = Heraldry.switches("{}"), off = Heraldry.switches("{\"polishEmblems\": false}");
		boolean[] capes = Heraldry.switches("{\"polishCapes\": false, \"polishNameplates\": true}"), junk = Heraldry.switches("not json {");
		boolean ok = none[0] && none[1] && empty[0] && empty[1] && !off[0] && off[1] && capes[0] && !capes[1] && junk[0] && junk[1];
		check("heraldry: kill switches polishEmblems / polishCapes (default on, each turns off alone)", ok, null);
	}
}
