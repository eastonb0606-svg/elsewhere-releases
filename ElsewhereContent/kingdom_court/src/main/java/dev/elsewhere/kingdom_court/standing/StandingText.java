package dev.elsewhere.kingdom_court.standing;

import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * POLISH P7: every translation key the trader dialogue and the Standing chip use, built in one place so the client and the self-test agree
 * (the self-test checks each key exists in en_us.json). Each kingdom names the five Standing bands its own way
 * ({@code gui.elsewhere.standing.title.<kingdom>.<band>}), each trader greets you by band ({@code gui.elsewhere.trader.greet.<role>.<band>}).
 */
public final class StandingText {
	private StandingText() {}

	public static final String[] BANDS = {"hostile", "wary", "neutral", "friendly", "allied"};
	public static final String[] KINGDOMS = {"aldermark", "hrafnholt", "azmar", "mirewick"};
	public static final String[] BUTTONS = {"wares", "lots", "treasury", "tribute", "haggle", "claim", "repair", "pickup", "offer", "farewell"};
	public static final String[] TIPS = {"wares", "lots", "treasury", "tribute", "haggle", "claim", "repair", "pickup", "offer", "hostile", "locked", "haggled"};
	public static final String[] PRICES = {"pays", "pays_list", "discount", "no_discount", "ring", "ring_hint", "thing_open", "thing_closed", "posted", "tiers_0", "tiers_1",
		"tiers_2", "tiers_3", "floating", "haggled", "locked", "chance", "miser", "war", "owes", "late", "hostile", "auction", "envoy", "offer", "pact", "oath", "legacy"};
	public static final String[] CHIP = {"attacks", "oath", "capped", "asking"};

	/** The band key ("hostile".."allied") of a Standing value, by the kingdom module's own thresholds. */
	public static String band(int standing) {
		return BANDS[Standing.band(standing).ordinal()];
	}

	/** The kingdom ledger of a faction id string: the path, without a remnant's "_remnant" suffix ("elsewhere:aldermark_remnant" is "aldermark"). */
	public static String ledgerPath(String factionId) {
		int c = factionId.indexOf(':');
		String path = c < 0 ? factionId : factionId.substring(c + 1);
		return path.endsWith("_remnant") ? path.substring(0, path.length() - "_remnant".length()) : path;
	}

	/** One of {@link #KINGDOMS} for this faction id, or null for any other faction (test and data-pack factions). */
	public static @Nullable String kingdom(String factionId) {
		String p = ledgerPath(factionId).toLowerCase(Locale.ROOT);
		for (String k : KINGDOMS) if (k.equals(p)) return k;
		return null;
	}

	/** "lawspeaker", "royal_envoy", "clerk", "smith", "market_inspector", "auctioneer", "caravan_master". */
	public static String role(Diplomacy.Role r) {
		return r.path.startsWith("kingdom_") ? r.path.substring("kingdom_".length()) : r.path;
	}

	public static String bandKey(String band) {
		return "gui.elsewhere.standing.band." + band;
	}

	public static String titleKey(String kingdom, String band) {
		return "gui.elsewhere.standing.title." + kingdom + "." + band;
	}

	public static String mottoKey(String kingdom) {
		return "gui.elsewhere.standing.motto." + kingdom;
	}

	public static String kingdomKey(String kingdom) {
		return "gui.elsewhere.standing.kingdom." + kingdom;
	}

	public static String chipKey(String what) {
		return "gui.elsewhere.standing.chip." + what;
	}

	public static String greetKey(Diplomacy.Role r, String band) {
		return "gui.elsewhere.trader.greet." + role(r) + "." + band;
	}

	public static String buttonKey(String what) {
		return "gui.elsewhere.trader.button." + what;
	}

	public static String tipKey(String what) {
		return "gui.elsewhere.trader.tip." + what;
	}

	public static String priceKey(String what) {
		return "gui.elsewhere.trader.price." + what;
	}

	public static final String TITLE = "gui.elsewhere.trader.title";
	public static final String STANDING = "gui.elsewhere.standing.label";
	public static final String VALUE = "gui.elsewhere.standing.value";

	/** Every key above, for the lang check. */
	public static List<String> allKeys() {
		List<String> out = new ArrayList<>(List.of(TITLE, STANDING, VALUE));
		for (String b : BANDS) out.add(bandKey(b));
		for (String k : KINGDOMS) {
			out.add(mottoKey(k));
			out.add(kingdomKey(k));
			for (String b : BANDS) out.add(titleKey(k, b));
		}
		for (Diplomacy.Role r : Diplomacy.Role.values()) for (String b : BANDS) out.add(greetKey(r, b));
		for (String s : BUTTONS) out.add(buttonKey(s));
		for (String s : TIPS) out.add(tipKey(s));
		for (String s : PRICES) out.add(priceKey(s));
		for (String s : CHIP) out.add(chipKey(s));
		return out;
	}
}
