package dev.elsewhere.kingdom_court.client.standing;

import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.standing.StandingText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * POLISH P7: each kingdom's look for the trader dialogue and the Standing chip. The frame, chip and crest are GUI atlas sprites in
 * {@code textures/gui/sprites/standing/} (nine-slice frames); the colours below are the text and inset colours that sit on them.
 * <ul>
 * <li>Aldermark (the baseline): royal blue damask in a stone border with a gold rule, a gold crown on a blue heater shield.</li>
 * <li>Hrafnholt (seafaring vikings): tarred spruce planks bound in riveted iron, a black raven on a round green-and-brown shield.</li>
 * <li>Azmar (Egyptian, gold and lapis): lapis field in sandstone with gold and turquoise bands and stepped corners, a winged sun disc.</li>
 * <li>Mirewick (purple, crimson and gold republic): ledger-ruled purple in crimson and gold, gilt coin corners, a lit lantern.</li>
 * </ul>
 */
public enum KingdomStyle {
	ALDERMARK("aldermark", 0xFFF2D98C, 0xFFE4E1D6, 0xFF9BA6C4, 0xFFE8C060, 0xFF101833, 0xFF56689E, 0xFFF7DA78, 0xFF080C1A),
	HRAFNHOLT("hrafnholt", 0xFFE0CCA2, 0xFFE4DAC6, 0xFFA08E72, 0xFF7DB377, 0xFF160F0A, 0xFF656B73, 0xFFD3DBE2, 0xFF0B0805),
	AZMAR("azmar", 0xFFF4CA5E, 0xFFF3E8CE, 0xFFC0AC80, 0xFF39C2B1, 0xFF0B1A40, 0xFFD8BC7E, 0xFF6BE8D8, 0xFF050C22),
	MIREWICK("mirewick", 0xFFEAC24E, 0xFFEEE1F4, 0xFFAD94BE, 0xFFD8455A, 0xFF1A0C28, 0xFFD6A832, 0xFFFFDC6E, 0xFF0E0616);

	public final String key;
	/** Name line (ruler or trader title). */
	public final int title;
	/** Body text. */
	public final int text;
	/** Secondary text (motto, band name). */
	public final int dim;
	/** The kingdom's own band title ("Shield-friend") and highlights. */
	public final int accent;
	/** Portrait box fill. */
	public final int inset;
	/** Portrait box edge and the header rule. */
	public final int insetEdge;
	/** The Standing marker on the bar. */
	public final int marker;
	/** The bar's outline. */
	public final int barEdge;
	public final Identifier frame, chip, crest;

	KingdomStyle(String key, int title, int text, int dim, int accent, int inset, int insetEdge, int marker, int barEdge) {
		this.key = key;
		this.title = title;
		this.text = text;
		this.dim = dim;
		this.accent = accent;
		this.inset = inset;
		this.insetEdge = insetEdge;
		this.marker = marker;
		this.barEdge = barEdge;
		this.frame = CourtContent.id("standing/frame_" + key);
		this.chip = CourtContent.id("standing/chip_" + key);
		this.crest = CourtContent.id("standing/crest_" + key);
	}

	/** The style of a faction id (remnants take their kingdom's), or null for a faction that is none of the four. */
	public static KingdomStyle ofOrNull(String factionId) {
		String k = StandingText.kingdom(factionId);
		if (k == null) return null;
		for (KingdomStyle s : values()) if (s.key.equals(k)) return s;
		return null;
	}

	/** The style of a faction id; any other faction gets Aldermark's, the baseline. */
	public static KingdomStyle of(String factionId) {
		KingdomStyle s = ofOrNull(factionId);
		return s != null ? s : ALDERMARK;
	}

	/** For a trader: its faction's style, else the style of the culture its role belongs to (a test or data-pack faction binding the role). */
	public static KingdomStyle of(String factionId, Diplomacy.Role role) {
		KingdomStyle s = ofOrNull(factionId);
		if (s != null) return s;
		return switch (role) {
			case LAWSPEAKER, SMITH -> HRAFNHOLT;
			case ENVOY, INSPECTOR, CARAVAN_MASTER -> AZMAR;
			case CLERK, AUCTIONEER -> MIREWICK;
		};
	}

	/** The kingdom's display name for a faction id ("Jarldom of Hrafnholt"); a faction that is none of the four shows its own path. */
	public static MutableComponent name(String factionId) {
		String k = StandingText.kingdom(factionId);
		if (k != null) return Component.translatable(StandingText.kingdomKey(k));
		String p = StandingText.ledgerPath(factionId).replace('_', ' ');
		return Component.literal(p.isEmpty() ? "?" : Character.toUpperCase(p.charAt(0)) + p.substring(1));
	}

	/** This kingdom's name for a Standing band ("Shield-friend"). */
	public MutableComponent bandTitle(String band) {
		return Component.translatable(StandingText.titleKey(key, band));
	}

	public MutableComponent motto() {
		return Component.translatable(StandingText.mottoKey(key));
	}

	/** {@code argb} with its alpha multiplied by {@code a} (0..1). */
	public static int fade(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(a, 0F, 1F));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}
}
