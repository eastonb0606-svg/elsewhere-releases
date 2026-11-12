package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.plate.Nameplate;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;

/**
 * 3.0 polish P3 (s-p3-nameplates): rank derivation from the roster slot, the ruler rank hook, level of detail, the plate
 * text per kingdom (titles, crests, ruler marks, tint), the glyph atlas and the lang keys. The synced fields themselves and
 * their save/load are covered by {@link SelfTestPolish}. Appended to SelfTest's steps from a DevEntry tick listener.
 */
public final class SelfTestNameplates {
	private SelfTestNameplates() {}

	private static boolean added;

	public static void tick(MinecraftServer srv) {
		if (added || !SelfTest.started) return;
		added = true;
		STEPS.add((s, l) -> {
			clean(l);
			// --- rank byte (server) ---
			check("nameplate P3: the sergeant slot is an officer even when the faction's unit has a captain ability (Azmar Jemadar)",
				Nameplate.rankOf(UnitTypes.SERGEANT, UnitTypes.get(UnitTypes.CAPTAIN)) == Nameplate.OFFICER, null);
			check("nameplate P3: the captain slot is a captain whatever unit fills it (Azmar Qaid, Hrafnholt Hird-Captain)",
				Nameplate.rankOf(UnitTypes.CAPTAIN, UnitTypes.get(UnitTypes.MAN_AT_ARMS)) == Nameplate.CAPTAIN, null);
			var fc = KingdomContent.id("free_company_captain");
			check("nameplate P3: a unit spawned by its own id falls back to its flags (captain ability = captain, man-at-arms = soldier)",
				Nameplate.rankOf(KingdomContent.id("p3_test_unit"), UnitTypes.get(UnitTypes.CAPTAIN)) == Nameplate.CAPTAIN
					&& Nameplate.rankOf(UnitTypes.MAN_AT_ARMS, UnitTypes.get(UnitTypes.MAN_AT_ARMS)) == Nameplate.SOLDIER, null);
			check("nameplate P3: the Free Company's captain (an officer unit without the captain ability) is its band's captain",
				UnitTypes.get(fc).officer() && Nameplate.rankOf(fc, UnitTypes.get(fc)) == Nameplate.CAPTAIN, Nameplate.rankOf(fc, UnitTypes.get(fc)));
			KingdomSoldier ruler = new KingdomSoldier(KingdomEntities.CAPTAIN, l) {
				@Override
				protected int nameplateRank() {
					return Nameplate.SOVEREIGN;
				}
			};
			ruler.setFaction(Factions.ALDERMARK);
			check("nameplate P3: a ruler subclass's nameplateRank() reaches the synced byte through setFaction (the court's crown resync path)", ruler.rank() == Nameplate.SOVEREIGN, ruler.rank());
			ruler.discard();
			KingdomSoldier sgt = KingdomApi.spawnSoldier(l, UnitTypes.SERGEANT, Factions.ALDERMARK, at(0, 64), null);
			check("nameplate P3: a spawned sergeant carries rank 1 and its faction from the first sync", sgt != null && sgt.rank() == Nameplate.OFFICER && sgt.syncedFaction().equals(Factions.ALDERMARK.toString()), sgt == null ? null : sgt.rank());

			// --- level of detail ---
			check("nameplate P3: LOD full under 8 blocks, icons 8-16, none from 16 (rank and file and officers)",
				Nameplate.lod(Nameplate.SOLDIER, 7.9 * 7.9) == Nameplate.FULL && Nameplate.lod(Nameplate.SOLDIER, 8.1 * 8.1) == Nameplate.COMPACT
					&& Nameplate.lod(Nameplate.CAPTAIN, 15.9 * 15.9) == Nameplate.COMPACT && Nameplate.lod(Nameplate.CAPTAIN, 16.0 * 16.0) == Nameplate.NONE, null);
			check("nameplate P3: rulers keep the full plate to 16 blocks and none beyond",
				Nameplate.lod(Nameplate.LORD, 15.9 * 15.9) == Nameplate.FULL && Nameplate.lod(Nameplate.SOVEREIGN, 16.1 * 16.1) == Nameplate.NONE, null);

			// --- text per kingdom ---
			int ald = 0xFF2E4C9E, hraf = 0xFF2E5A2C, azm = 0xFF1F3F8F, mire = 0xFF5B2A86;
			String cap = Nameplate.text(Nameplate.CAPTAIN, "elsewhere:aldermark", ald, null, Nameplate.FULL).getString();
			check("nameplate P3: Aldermark captain plate = star, Captain, crest, Aldermark", cap.equals(Nameplate.G_CAPTAIN + " Captain · " + Nameplate.C_ALDERMARK + " Aldermark"), cap);
			String pawn = Nameplate.text(Nameplate.SOLDIER, "elsewhere:aldermark", ald, null, Nameplate.FULL).getString();
			check("nameplate P3: rank and file show only the crest and faction", pawn.equals(Nameplate.C_ALDERMARK + " Aldermark"), pawn);
			check("nameplate P3: each kingdom has its own titles (Hird-Sergeant, Qaid, Reeve)",
				Nameplate.text(Nameplate.OFFICER, "elsewhere:hrafnholt", hraf, null, Nameplate.FULL).getString().contains("Hird-Sergeant")
					&& Nameplate.text(Nameplate.CAPTAIN, "elsewhere:azmar", azm, null, Nameplate.FULL).getString().contains("Qaid")
					&& Nameplate.text(Nameplate.OFFICER, "elsewhere:mirewick", mire, null, Nameplate.FULL).getString().contains("Reeve"), null);
			check("nameplate P3: each kingdom has its own crest glyph",
				Nameplate.kit("elsewhere:aldermark").crest() == Nameplate.C_ALDERMARK && Nameplate.kit("elsewhere:hrafnholt").crest() == Nameplate.C_HRAFNHOLT
					&& Nameplate.kit("elsewhere:azmar").crest() == Nameplate.C_AZMAR && Nameplate.kit("elsewhere:mirewick").crest() == Nameplate.C_MIREWICK, null);
			String compact = Nameplate.text(Nameplate.CAPTAIN, "elsewhere:hrafnholt", hraf, null, Nameplate.COMPACT).getString();
			String compactPawn = Nameplate.text(Nameplate.SOLDIER, "elsewhere:azmar", azm, null, Nameplate.COMPACT).getString();
			check("nameplate P3: compact plates are icons only (captain: star + crest; rank and file: crest)",
				compact.equals("" + Nameplate.G_CAPTAIN + Nameplate.C_HRAFNHOLT) && compactPawn.equals(String.valueOf(Nameplate.C_AZMAR)), compact + " / " + compactPawn);
			String king = Nameplate.text(Nameplate.SOVEREIGN, "elsewhere:aldermark", ald, Component.literal("King Osric Vael"), Nameplate.FULL).getString();
			check("nameplate P3: a sovereign's plate = crown, own name, crest", king.equals(Nameplate.G_CROWN + " King Osric Vael " + Nameplate.C_ALDERMARK), king);
			String lord = Nameplate.text(Nameplate.LORD, "elsewhere:aldermark", ald, Component.literal("Marshal Ysolde Varr"), Nameplate.FULL).getString();
			check("nameplate P3: a lord's plate = coronet, own name, crest", lord.equals(Nameplate.G_CORONET + " Marshal Ysolde Varr " + Nameplate.C_ALDERMARK), lord);
			String magnate = Nameplate.text(Nameplate.SOVEREIGN, "elsewhere:mirewick", mire, Component.literal("Fen-Marshal Garrick Thwaite"), Nameplate.FULL).getString();
			check("nameplate P3: Mirewick's councillors wear the chain of office, never a crown (a republic)", magnate.charAt(0) == Nameplate.G_CHAIN && magnate.indexOf(Nameplate.G_CROWN) < 0, magnate);
			String unknown = Nameplate.text(Nameplate.OFFICER, "elsewhere:test_band", 0xFF7A1F1F, null, Nameplate.FULL).getString();
			check("nameplate P3: an unknown faction gets the banner crest, a readable name and the generic title", unknown.equals(Nameplate.G_OFFICER + " Sergeant · " + Nameplate.C_BANNER + " Test Band"), unknown);
			String fcPlate = Nameplate.text(Nameplate.CAPTAIN, "elsewhere:free_company", 0xFF7A1F1F, null, Nameplate.FULL).getString();
			check("nameplate P3: Free Company sellswords: crossed swords and Company Captain", fcPlate.equals(Nameplate.G_CAPTAIN + " Company Captain · " + Nameplate.C_FREE + " Free Company"), fcPlate);
			check("nameplate P3: a remnant faction keeps its kingdom's crest (prefix match)", Nameplate.kit("elsewhere:hrafnholt_remnant").crest() == Nameplate.C_HRAFNHOLT, null);

			// glyph spans use the atlas font; crests keep their colours, the generic banner takes the faction tint
			Map<Character, Style> styles = new HashMap<>();
			Component fcC = Nameplate.text(Nameplate.CAPTAIN, "elsewhere:test_band", 0xFF7A1F1F, null, Nameplate.FULL);
			Component aldC = Nameplate.text(Nameplate.CAPTAIN, "elsewhere:aldermark", ald, null, Nameplate.FULL);
			for (Component c : new Component[] {fcC, aldC}) c.visit((st, txt) -> {
				for (char ch : txt.toCharArray()) if (ch >= '' && ch <= '') styles.put(ch, st);
				return java.util.Optional.empty();
			}, Style.EMPTY);
			Style star = styles.get(Nameplate.G_CAPTAIN), crest = styles.get(Nameplate.C_ALDERMARK), banner = styles.get(Nameplate.C_BANNER);
			check("nameplate P3: rank and crest glyphs are drawn with the nameplate atlas font",
				star != null && crest != null && banner != null && Nameplate.FONT.equals(star.getFont()) && Nameplate.FONT.equals(crest.getFont()) && Nameplate.FONT.equals(banner.getFont()), styles.keySet());
			check("nameplate P3: kingdom crests keep their own colours (white), the generic banner is tinted with the faction colour",
				crest != null && crest.getColor() != null && crest.getColor().getValue() == 0xFFFFFF
					&& banner != null && banner.getColor() != null && banner.getColor().getValue() == Nameplate.tint(0xFF7A1F1F), null);
			check("nameplate P3: dark tabard colours are lifted for readability (Hrafnholt green)", Nameplate.tint(hraf) != (hraf & 0xFFFFFF) && lum(Nameplate.tint(hraf)) >= 100, Integer.toHexString(Nameplate.tint(hraf)));

			// --- assets ---
			check("nameplate P3: every glyph the plates use is mapped in assets/elsewhere/font/nameplate.json", fontMapsAll(), null);
			BufferedImage atlas = null;
			try (InputStream in = SelfTestNameplates.class.getResourceAsStream("/assets/elsewhere/textures/font/nameplate.png")) {
				if (in != null) atlas = ImageIO.read(in);
			}
			check("nameplate P3: the glyph atlas exists (128x32: 8 x 2 cells of 16)", atlas != null && atlas.getWidth() == 128 && atlas.getHeight() == 32, atlas == null ? "missing" : atlas.getWidth() + "x" + atlas.getHeight());
			check("nameplate P3: every used glyph cell has pixels", atlas != null && glyphCellsPainted(atlas), null);
			List<String> missing = missingLang();
			check("nameplate P3: every kingdom's plate titles and short names are in en_us and match the code's fallbacks", missing.isEmpty(), missing);
			clean(l);
			return 2;
		});
	}

	private static int lum(int rgb) {
		return (int) (0.299 * (rgb >> 16 & 0xFF) + 0.587 * (rgb >> 8 & 0xFF) + 0.114 * (rgb & 0xFF));
	}

	private static boolean fontMapsAll() {
		try (InputStream in = SelfTestNameplates.class.getResourceAsStream("/assets/elsewhere/font/nameplate.json")) {
			if (in == null) return false;
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			StringBuilder all = new StringBuilder();
			for (var p : o.getAsJsonArray("providers")) {
				var po = p.getAsJsonObject();
				if (!"bitmap".equals(po.get("type").getAsString()) || !"elsewhere:font/nameplate.png".equals(po.get("file").getAsString())) continue;
				for (var row : po.getAsJsonArray("chars")) all.append(row.getAsString());
			}
			for (char g : Nameplate.GLYPHS) if (all.indexOf(String.valueOf(g)) < 0) return false;
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/** Each glyph's 16x16 cell (row = high nibble of the low byte, column = low nibble) has opaque pixels. */
	private static boolean glyphCellsPainted(BufferedImage img) {
		for (char g : Nameplate.GLYPHS) {
			int col = g & 0x0F, row = (g >> 4) & 0x0F;
			boolean any = false;
			for (int y = row * 16; y < row * 16 + 16 && !any; y++)
				for (int x = col * 16; x < col * 16 + 16 && !any; x++) any = (img.getRGB(x, y) >>> 24) > 0;
			if (!any) return false;
		}
		return true;
	}

	/** Reads every en_us copy on the classpath (first copy wins per key, as in the merged jar). */
	private static List<String> missingLang() {
		Map<String, String> lang = new HashMap<>();
		try {
			Enumeration<URL> urls = SelfTestNameplates.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
			while (urls.hasMoreElements()) {
				try (InputStream in = urls.nextElement().openStream()) {
					JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (var e : o.entrySet()) lang.putIfAbsent(e.getKey(), e.getValue().getAsString());
				}
			}
		} catch (Exception e) {
			return List.of("read failed: " + e);
		}
		List<String> missing = new ArrayList<>();
		for (String f : new String[] {"aldermark", "hrafnholt", "azmar", "mirewick", "rebels", "free_company"}) {
			Nameplate.Kit k = Nameplate.kit("elsewhere:" + f);
			String[][] want = {{"faction." + f, k.name()}, {"title." + f + ".officer", k.officer()}, {"title." + f + ".captain", k.captain()},
				{"title." + f + ".lord", k.lord()}, {"title." + f + ".sovereign", k.sovereign()}};
			for (String[] w : want) {
				String key = "elsewhere.nameplate." + w[0];
				if (!w[1].equals(lang.get(key))) missing.add(key + "=" + lang.get(key) + " (code: " + w[1] + ")");
			}
		}
		return missing;
	}
}
