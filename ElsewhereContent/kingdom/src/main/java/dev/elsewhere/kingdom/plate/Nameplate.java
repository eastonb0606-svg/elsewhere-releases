package dev.elsewhere.kingdom.plate;

import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 polish P3 (POLISH.md): rank and faction nameplates over kingdom soldiers and court rulers.
 * <p>Shared and pure, so the server self-test covers it. The server derives one rank byte per soldier ({@link #rankOf}),
 * synced with the faction id on spawn and on a unit, faction or crown change only (never per tick). The client builds the
 * plate from that byte, the synced faction id and, for rulers, the synced custom name ({@link #text}), at the level of
 * detail {@link #lod} picks by distance. Rank and crest icons are glyphs from one tiny bitmap font atlas
 * ({@code assets/elsewhere/font/nameplate.json}, 16x16 cells drawn at text height).
 * <p>Each kingdom has its own crest, rank titles and ruler mark ({@link Kit}): Aldermark's heater shield (red chief, crown,
 * tower), Hrafnholt's raven on a round shield, Azmar's winged scarab, Mirewick's lantern (all as on the P10 tabard emblems) (and a chain of office instead of a coronet: the Lantern
 * Republic crowns nobody), the rebels' torch and the Free Company's crossed swords. Unknown factions get a banner in their tabard colour and the generic titles.
 */
public final class Nameplate {
	private Nameplate() {}

	/** Rank byte values. */
	public static final int SOLDIER = 0, OFFICER = 1, CAPTAIN = 2, LORD = 3, SOVEREIGN = 4;
	/** Levels of detail. */
	public static final int NONE = 0, COMPACT = 1, FULL = 2;
	/** Full plates (title and faction name) within this many blocks; icons only beyond. */
	public static final double FULL_RANGE = 8.0;
	/** No plate beyond this (POLISH.md budget rule 6: text only within 16 blocks). */
	public static final double RANGE = 16.0;

	public static final Identifier FONT_ID = Identifier.fromNamespaceAndPath("elsewhere", "nameplate");
	public static final FontDescription FONT = new FontDescription.Resource(FONT_ID);

	// glyphs in the atlas (private use area); row 0 = rank marks, row 1 = crests
	public static final char G_OFFICER = '', G_CAPTAIN = '', G_CORONET = '', G_CROWN = '', G_CHAIN = '';
	public static final char C_ALDERMARK = '', C_HRAFNHOLT = '', C_AZMAR = '', C_MIREWICK = '', C_REBELS = '', C_BANNER = '', C_FREE = '';
	/** Every glyph the plates use (the self-test checks each one is mapped in the font file). */
	public static final char[] GLYPHS = {G_OFFICER, G_CAPTAIN, G_CORONET, G_CROWN, G_CHAIN, C_ALDERMARK, C_HRAFNHOLT, C_AZMAR, C_MIREWICK, C_REBELS, C_BANNER, C_FREE};

	/**
	 * One kingdom's plate look. {@code crest} is drawn in its own colours, except {@link #C_BANNER}, which takes the tabard
	 * tint. Titles are English fallbacks for the lang keys {@code elsewhere.nameplate.title.<faction>.<officer|captain|lord|sovereign>}.
	 */
	public record Kit(String key, char crest, String name, String officer, String captain, String lord, String sovereign, char lordMark, char sovereignMark) {}

	public static final Kit DEFAULT = new Kit("", C_BANNER, "", "Sergeant", "Captain", "Lord", "Sovereign", G_CORONET, G_CROWN);
	private static final Map<String, Kit> KITS = Map.of(
		"aldermark", new Kit("aldermark", C_ALDERMARK, "Aldermark", "Sergeant", "Captain", "Lord", "King", G_CORONET, G_CROWN),
		"hrafnholt", new Kit("hrafnholt", C_HRAFNHOLT, "Hrafnholt", "Hird-Sergeant", "Hird-Captain", "Thane", "High Jarl", G_CORONET, G_CROWN),
		"azmar", new Kit("azmar", C_AZMAR, "Azmar", "Jemadar", "Qaid", "Amir", "Sultana", G_CORONET, G_CROWN),
		"mirewick", new Kit("mirewick", C_MIREWICK, "Mirewick", "Reeve", "Ward-Captain", "Magnate", "First Magnate", G_CHAIN, G_CHAIN),
		"rebels", new Kit("rebels", C_REBELS, "Rebels", "Ringleader", "Rebel Chief", "Rebel Lord", "Rebel King", G_CORONET, G_CROWN),
		// neutral sellsword bands: their only officer is the company's captain (an officer unit without the captain ability)
		"free_company", new Kit("free_company", C_FREE, "Free Company", "Company Captain", "Company Captain", "Company Lord", "Company Lord", G_CORONET, G_CORONET));

	private static final int GOLD = 0xFFD75A, WHITE = 0xFFFFFF, GREY = 0xAAAAAA;
	private static final int NIGHT_TITLE = 0xA8A8B4, NIGHT_DOT = 0x80808A;
	private static final Style GLYPH = Style.EMPTY.withFont(FONT).withColor(WHITE);
	private static final Style TITLE = Style.EMPTY.withColor(WHITE);
	private static final Style DOT = Style.EMPTY.withColor(GREY);

	// ---- server: the synced rank byte --------------------------------------------------------------------------------

	/**
	 * The rank a unit shows. A faction fields its own unit for the base sergeant and captain slots (Azmar's Jemadar and Qaid,
	 * Hrafnholt's Hird-Sergeant and Hird-Captain), and the soldier keeps the base id, so the slot decides first; a unit spawned
	 * by its own id falls back to its flags (a captain ability, then the officer flag). Lords and sovereigns come from the
	 * ruler's own override of {@code KingdomSoldier.nameplateRank()}.
	 */
	public static int rankOf(@Nullable Identifier unitId, UnitType u) {
		if (UnitTypes.CAPTAIN.equals(unitId)) return CAPTAIN;
		if (UnitTypes.SERGEANT.equals(unitId)) return OFFICER;
		if (u.ability() == UnitType.Ability.CAPTAIN) return CAPTAIN;
		if (!u.officer()) return SOLDIER;
		// an officer unit named as a captain leads its band (the Free Company's free_company_captain has no captain ability)
		return unitId != null && unitId.getPath().endsWith("captain") ? CAPTAIN : OFFICER;
	}

	// ---- client: level of detail and text ----------------------------------------------------------------------------

	/** Detail for a plate at this squared distance: rulers get the full plate out to {@link #RANGE}, everyone else only to {@link #FULL_RANGE}. */
	public static int lod(int rank, double distSq) {
		if (distSq >= RANGE * RANGE) return NONE;
		if (rank >= LORD || distSq < FULL_RANGE * FULL_RANGE) return FULL;
		return COMPACT;
	}

	/** The kit for a faction id: exact path, then the longest known prefix (remnants such as {@code aldermark_loyalists}), else {@link #DEFAULT}. */
	public static Kit kit(String factionId) {
		String path = path(factionId);
		Kit k = KITS.get(path);
		if (k != null) return k;
		Kit best = null;
		for (Kit c : KITS.values()) if (path.startsWith(c.key() + "_") && (best == null || c.key().length() > best.key().length())) best = c;
		return best != null ? best : DEFAULT;
	}

	/**
	 * The plate text. FULL: rank mark and title (officers up), then the crest and the faction's short name; rulers show their
	 * mark, their own name and the crest. COMPACT: the marks only (rank mark if any, then the crest). The faction parts are
	 * tinted with the tabard colour, lifted toward white when dark ({@link #tint}); glyphs keep their own colours.
	 *
	 * @param name the ruler's synced custom name (rank {@link #LORD} and up); null falls back to the kingdom's title
	 */
	public static Component text(int rank, String factionId, int argb, @Nullable Component name, int lod) {
		return text(rank, factionId, argb, name, lod, false);
	}

	/**
	 * Same, for night: the rank text (title and dot) is dimmed toward grey (sneaking-tag style, L257.3) while the faction name is
	 * lifted toward white so it stays legible on the dark plate (it was nearly invisible at night).
	 */
	public static Component text(int rank, String factionId, int argb, @Nullable Component name, int lod, boolean night) {
		Kit k = kit(factionId);
		Style faction = Style.EMPTY.withColor(night ? lift(tint(argb)) : tint(argb));
		Style TITLE = night ? Style.EMPTY.withColor(NIGHT_TITLE) : Nameplate.TITLE;
		Style DOT = night ? Style.EMPTY.withColor(NIGHT_DOT) : Nameplate.DOT;
		Style crest = k.crest() == C_BANNER ? GLYPH.withColor(night ? lift(tint(argb)) : tint(argb)) : GLYPH;
		MutableComponent out = Component.empty();
		if (rank >= LORD) {
			char mark = rank >= SOVEREIGN ? k.sovereignMark() : k.lordMark();
			out.append(Component.literal(String.valueOf(mark)).withStyle(GLYPH));
			out.append(Component.literal(" "));
			out.append(name != null ? name.copy().withStyle(faction) : title(k, factionId, rank).withStyle(faction));
			out.append(Component.literal(" "));
			out.append(Component.literal(String.valueOf(k.crest())).withStyle(crest));
			return out;
		}
		if (rank > SOLDIER) out.append(Component.literal(String.valueOf(rank >= CAPTAIN ? G_CAPTAIN : G_OFFICER)).withStyle(GLYPH));
		if (lod <= COMPACT) {
			out.append(Component.literal(String.valueOf(k.crest())).withStyle(crest));
			return out;
		}
		if (rank > SOLDIER) {
			out.append(Component.literal(" "));
			out.append(title(k, factionId, rank).withStyle(TITLE));
			out.append(Component.literal(" · ").withStyle(DOT));
		}
		out.append(Component.literal(String.valueOf(k.crest())).withStyle(crest));
		out.append(Component.literal(" "));
		out.append(factionName(k, factionId).withStyle(faction));
		return out;
	}

	/** The rank title, translatable ({@code elsewhere.nameplate.title.<faction>.<rank>}) with the kit's English fallback. */
	public static MutableComponent title(Kit k, String factionId, int rank) {
		String r = switch (rank) {
			case OFFICER -> "officer";
			case CAPTAIN -> "captain";
			case LORD -> "lord";
			default -> "sovereign";
		};
		String fb = switch (rank) {
			case OFFICER -> k.officer();
			case CAPTAIN -> k.captain();
			case LORD -> k.lord();
			default -> k.sovereign();
		};
		return Component.translatableWithFallback("elsewhere.nameplate.title." + path(factionId) + "." + r, fb);
	}

	/** The short faction name ({@code elsewhere.nameplate.faction.<path>}): "Azmar" rather than "Sultanate of Azmar". */
	public static MutableComponent factionName(Kit k, String factionId) {
		String path = path(factionId);
		String fb = k != DEFAULT && k.key().equals(path) ? k.name() : pretty(path);
		return Component.translatableWithFallback("elsewhere.nameplate.faction." + path, fb);
	}

	/** Lifts dark tabard colours toward white so the plate stays readable on the dark plate background. */
	public static int tint(int argb) {
		int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
		double lum = 0.299 * r + 0.587 * g + 0.114 * b;
		if (lum < 140) {
			double t = (140 - lum) / 140 * 0.6;
			r += (int) ((255 - r) * t);
			g += (int) ((255 - g) * t);
			b += (int) ((255 - b) * t);
		}
		// L255.1: every faction colour reads as a light tint of itself on the dark plate (a fixed 30% lift toward white)
		r += (int) ((255 - r) * 0.30);
		g += (int) ((255 - g) * 0.30);
		b += (int) ((255 - b) * 0.30);
		return r << 16 | g << 8 | b;
	}

	/** Lifts a colour 45% toward white: the night faction name, so it never reads darker than the plate behind it. */
	static int lift(int rgb) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		r += (int) ((255 - r) * 0.45);
		g += (int) ((255 - g) * 0.45);
		b += (int) ((255 - b) * 0.45);
		return r << 16 | g << 8 | b;
	}

	static String path(String factionId) {
		int c = factionId.indexOf(':');
		return c < 0 ? factionId : factionId.substring(c + 1);
	}

	/** "aldermark_loyalists" -> "Aldermark Loyalists". */
	static String pretty(String path) {
		StringBuilder sb = new StringBuilder();
		for (String w : path.split("_")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
		}
		return sb.toString();
	}
}
