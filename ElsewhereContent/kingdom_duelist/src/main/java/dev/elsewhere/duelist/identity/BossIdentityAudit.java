package dev.elsewhere.duelist.identity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.elsewhere.duelist.boss.DuelistProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The sameness audit (RIVALS-DECISIONS section 11): a port of {@code _tools/boss_audit.py}, rule for rule, so both give
 * the same verdict on the same files. An empty list means the set passes. It enforces:
 * <ul>
 *   <li>exactly the expected card files exist ({@link #EXPECTED}, the 15 rival bosses) and each has the required fields;</li>
 *   <li>any two bosses share at most {@link #MAX_SHARED_SLOTS} of the six slot tokens;</li>
 *   <li>each generic behaviour is used by at most {@link #MAX_GENERIC} bosses, ender pearls by at most {@link #MAX_PEARL};</li>
 *   <li>no Totem anywhere in a card; every last stand is unique, and the Mirewick Chair's is one shared token that no boss uses as its own;</li>
 *   <li>movement, signature moves and telegraph looks (sound event, particle, colour) are unique;</li>
 *   <li>at most {@link #MAX_BUILDERS_MIREWICK} of the Mirewick Magnates builds with temporary blocks.</li>
 * </ul>
 * Kingdom self-tests call {@link #checkPresent} with their own cards (limits only) and {@link #checkProfile} per boss; the
 * merged build calls {@link #check} with all 15.
 */
public final class BossIdentityAudit {
	private BossIdentityAudit() {}

	public static final List<String> EXPECTED = List.of(
		"hrafnholt_jarl", "hrafnholt_hersir", "hrafnholt_hallwarden", "hrafnholt_skald", "hrafnholt_chief", "hrafnholt_huntmaster",
		"azmar_sultana", "azmar_wazir", "azmar_falconer", "azmar_amir",
		"mirewick_marrow", "mirewick_thwaite", "mirewick_sorrel", "mirewick_tolliver", "mirewick_ames");
	public static final int MAX_SHARED_SLOTS = 2, MAX_GENERIC = 3, MAX_PEARL = 1, MAX_BUILDERS_MIREWICK = 1;
	private static final Pattern COLOUR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

	/** The full audit of loaded cards: all 15 expected bosses must be there and every limit must hold. */
	public static List<String> check(Collection<BossIdentity> cards) {
		return audit(rawOf(cards), EXPECTED);
	}

	/** The limits only, over whatever cards are given (a kingdom lane's own 4 to 6): no "missing file" problems. */
	public static List<String> checkPresent(Collection<BossIdentity> cards) {
		return audit(rawOf(cards), null);
	}

	/** The full audit of raw card files (key = file name without ".json", value = its text), like the Python tool reads a directory. */
	public static List<String> checkRaw(Map<String, String> rawByKey) {
		return checkRaw(rawByKey, EXPECTED);
	}

	/** As {@link #checkRaw(Map)}, with your own list of expected keys ({@code null} = accept whatever is there). */
	public static List<String> checkRaw(Map<String, String> rawByKey, @Nullable Collection<String> expected) {
		List<String> errors = new ArrayList<>();
		Map<String, JsonElement> cards = new LinkedHashMap<>();
		for (String key : expected != null ? expected : new TreeSet<>(rawByKey.keySet())) {
			String text = rawByKey.get(key);
			if (text == null) {
				errors.add("missing file: " + key + ".json");
				continue;
			}
			try {
				cards.put(key, BossIdentity.strictJson(text));
			} catch (IOException e) {
				errors.add(key + ": invalid JSON (" + e.getMessage() + ")");
			}
		}
		if (expected != null) for (String key : new TreeSet<>(rawByKey.keySet())) {
			if (!expected.contains(key)) errors.add("unexpected identity file: " + key + ".json (expected exactly the " + expected.size() + " ids)");
		}
		errors.addAll(limits(cards));
		return errors;
	}

	/** The full audit of a directory of card files (the designer's {@code Ideas/kingdom/boss_identity}). */
	public static List<String> checkDir(Path dir) throws IOException {
		return checkRaw(BossIdentity.readDir(dir));
	}

	private static Map<String, JsonElement> rawOf(Collection<BossIdentity> cards) {
		Map<String, JsonElement> m = new LinkedHashMap<>();
		for (BossIdentity b : cards) m.put(b.key(), b.raw());
		return m;
	}

	private static List<String> audit(Map<String, JsonElement> have, @Nullable List<String> expected) {
		List<String> errors = new ArrayList<>();
		Map<String, JsonElement> cards = new LinkedHashMap<>();
		if (expected == null) cards.putAll(new TreeMap<>(have));
		else {
			for (String key : expected) {
				if (have.containsKey(key)) cards.put(key, have.get(key));
				else errors.add("missing file: " + key + ".json");
			}
			for (String key : new TreeSet<>(have.keySet())) if (!expected.contains(key)) errors.add("unexpected identity file: " + key + ".json (expected exactly the " + expected.size() + " ids)");
		}
		errors.addAll(limits(cards));
		return errors;
	}

	// ---- schema ---------------------------------------------------------------------------------------------------

	/** Python truthiness of a JSON value. */
	private static boolean truthy(@Nullable JsonElement e) {
		if (e == null || e.isJsonNull()) return false;
		if (e.isJsonArray()) return !e.getAsJsonArray().isEmpty();
		if (e.isJsonObject()) return !e.getAsJsonObject().isEmpty();
		var p = e.getAsJsonPrimitive();
		if (p.isBoolean()) return p.getAsBoolean();
		if (p.isNumber()) return p.getAsDouble() != 0.0;
		return !p.getAsString().isEmpty();
	}

	private static boolean isString(@Nullable JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
	}

	private static boolean isMirewick(String key) {
		return key.startsWith("mirewick_");
	}

	/** Python's str() of a JSON scalar, close enough for the keys this audit compares. */
	private static String str(JsonElement e) {
		return e.isJsonPrimitive() ? e.getAsString() : e.toString();
	}

	/** Schema problems of one card ({@code chairRequired}: check the Magnates' {@code chair_last_stand}; always on for {@code mirewick_} keys). */
	public static List<String> schemaProblems(String key, JsonElement card, boolean chairRequired) {
		List<String> out = new ArrayList<>();
		if (!card.isJsonObject()) {
			out.add(key + ": the card must be a JSON object");
			return out;
		}
		JsonObject c = card.getAsJsonObject();
		JsonElement slots = c.get("slots");
		if (slots == null || !slots.isJsonObject() || !slots.getAsJsonObject().keySet().equals(new HashSet<>(BossIdentity.SLOTS))) out.add(key + ": slots must have exactly " + BossIdentity.SLOTS);
		else for (String s : BossIdentity.SLOTS) {
			JsonElement v = slots.getAsJsonObject().get(s);
			if (!isString(v) || v.getAsString().strip().isEmpty()) out.add(key + ": slot '" + s + "' must be a non-empty token");
		}
		JsonElement g = c.get("generic");
		boolean gOk = g != null && g.isJsonArray();
		if (gOk) {
			Set<String> seen = new HashSet<>();
			for (JsonElement x : g.getAsJsonArray()) if (!isString(x) || !BossIdentity.GENERICS.contains(x.getAsString()) || !seen.add(x.getAsString())) gOk = false;
		}
		if (!gOk) out.add(key + ": generic must be a list (no repeats) drawn from " + BossIdentity.GENERICS);
		JsonElement sig = c.get("signature");
		boolean sOk = sig != null && sig.isJsonArray() && sig.getAsJsonArray().size() >= 2 && sig.getAsJsonArray().size() <= 3;
		if (sOk) {
			Set<JsonElement> seen = new HashSet<>();
			for (JsonElement x : sig.getAsJsonArray()) if (!seen.add(x)) sOk = false;
		}
		if (!sOk) out.add(key + ": signature must list 2 or 3 distinct move ids");
		JsonElement t = c.get("telegraph");
		if (t == null || !t.isJsonObject() || !truthy(t.getAsJsonObject().get("particle")) || !truthy(t.getAsJsonObject().get("colour")) || !truthy(t.getAsJsonObject().get("sound")))
			out.add(key + ": telegraph needs particle, colour and sound");
		else {
			JsonObject to = t.getAsJsonObject();
			if (to.get("colour").isJsonPrimitive() ? !COLOUR.matcher(to.get("colour").getAsString()).matches() : true) out.add(key + ": telegraph colour must be #RRGGBB");
			JsonElement snd = to.get("sound");
			if (snd.isJsonObject()) {
				JsonElement ev = snd.getAsJsonObject().get("event"), pitch = snd.getAsJsonObject().get("pitch");
				boolean num = pitch != null && pitch.isJsonPrimitive() && (pitch.getAsJsonPrimitive().isNumber() || pitch.getAsJsonPrimitive().isBoolean());
				if (!truthy(ev) || !num) out.add(key + ": telegraph sound needs event and numeric pitch");
			} else if (!isString(snd)) out.add(key + ": telegraph sound must be an event string or {event, pitch}");
		}
		if (!truthy(c.get("last_stand"))) out.add(key + ": last_stand missing");
		if ((chairRequired || isMirewick(key)) && !truthy(c.get("chair_last_stand"))) out.add(key + ": Mirewick Magnates need chair_last_stand (the Chair's own last stand)");
		return out;
	}

	// ---- the limits -----------------------------------------------------------------------------------------------

	private static List<String> limits(Map<String, JsonElement> in) {
		List<String> errors = new ArrayList<>();
		TreeMap<String, JsonObject> cards = new TreeMap<>();
		for (var e : in.entrySet()) {
			List<String> p = schemaProblems(e.getKey(), e.getValue(), false);
			if (p.isEmpty()) cards.put(e.getKey(), e.getValue().getAsJsonObject());
			else errors.addAll(p);
		}
		List<String> ids = new ArrayList<>(cards.keySet());
		Function<String, JsonObject> slots = k -> cards.get(k).getAsJsonObject("slots");
		// any two bosses share at most MAX_SHARED_SLOTS of 6
		for (int i = 0; i < ids.size(); i++) for (int j = i + 1; j < ids.size(); j++) {
			String a = ids.get(i), b = ids.get(j);
			List<String> shared = new ArrayList<>();
			for (String s : BossIdentity.SLOTS) if (slots.apply(a).get(s).getAsString().equals(slots.apply(b).get(s).getAsString())) shared.add(s + "=" + slots.apply(a).get(s).getAsString());
			if (shared.size() > MAX_SHARED_SLOTS) errors.add("slot overlap: " + a + " and " + b + " share " + shared.size() + " of 6 slots (max " + MAX_SHARED_SLOTS + "): " + String.join(", ", shared));
		}
		// generic behaviour usage
		for (String g : BossIdentity.GENERICS) {
			List<String> users = new ArrayList<>();
			for (String k : ids) for (JsonElement x : cards.get(k).getAsJsonArray("generic")) if (x.getAsString().equals(g)) users.add(k);
			int cap = g.equals("pearl") ? MAX_PEARL : MAX_GENERIC;
			if (users.size() > cap) errors.add("generic '" + g + "' used by " + users.size() + " bosses (max " + cap + "): " + String.join(", ", users));
		}
		// no Totem; unique last stands
		for (String k : ids) if (cards.get(k).toString().toLowerCase(Locale.ROOT).contains("totem")) errors.add(k + ": mentions a Totem; no boss may use the vanilla Totem");
		TreeMap<String, List<String>> seen = new TreeMap<>();
		for (String k : ids) seen.computeIfAbsent(str(cards.get(k).get("last_stand")), x -> new ArrayList<>()).add(k);
		for (var e : seen.entrySet()) pairs(e.getValue(), (a, b) -> errors.add("last stand '" + e.getKey() + "' shared by " + a + " and " + b));
		TreeSet<String> chair = new TreeSet<>();
		for (String k : ids) if (isMirewick(k)) chair.add(str(cards.get(k).get("chair_last_stand")));
		if (chair.size() > 1) errors.add("Mirewick Magnates disagree on the Chair's last stand: " + chair);
		for (String tok : chair) if (seen.containsKey(tok)) errors.add("the Chair's last stand '" + tok + "' must differ from every boss's own last stand (used by " + String.join(", ", seen.get(tok)) + ")");
		// movement, signatures and tells are unique
		unique(errors, cards, "movement", c -> List.of(c.getAsJsonObject("slots").get("movement").getAsString()));
		unique(errors, cards, "signature move", c -> {
			List<String> l = new ArrayList<>();
			for (JsonElement x : c.getAsJsonArray("signature")) l.add(str(x));
			return l;
		});
		unique(errors, cards, "telegraph sound event", c -> {
			JsonElement s = c.getAsJsonObject("telegraph").get("sound");
			return List.of(str(s.isJsonObject() ? s.getAsJsonObject().get("event") : s));
		});
		unique(errors, cards, "telegraph particle", c -> List.of(str(c.getAsJsonObject("telegraph").get("particle"))));
		unique(errors, cards, "telegraph colour", c -> List.of(c.getAsJsonObject("telegraph").get("colour").getAsString().toLowerCase(Locale.ROOT)));
		// Mirewick: at most one builder
		List<String> builders = new ArrayList<>();
		for (String k : ids) if (isMirewick(k)) for (JsonElement x : cards.get(k).getAsJsonArray("generic")) if (x.getAsString().equals("temp_blocks")) builders.add(k);
		if (builders.size() > MAX_BUILDERS_MIREWICK) errors.add("Mirewick has " + builders.size() + " builders (max " + MAX_BUILDERS_MIREWICK + "): " + String.join(", ", builders));
		return errors;
	}

	private interface Pair {
		void of(String a, String b);
	}

	private static void pairs(List<String> who, Pair f) {
		for (int i = 0; i < who.size(); i++) for (int j = i + 1; j < who.size(); j++) f.of(who.get(i), who.get(j));
	}

	private static void unique(List<String> errors, TreeMap<String, JsonObject> cards, String label, Function<JsonObject, List<String>> keys) {
		TreeMap<String, List<String>> by = new TreeMap<>();
		for (var e : cards.entrySet()) for (String k : keys.apply(e.getValue())) by.computeIfAbsent(k, x -> new ArrayList<>()).add(e.getKey());
		for (var e : by.entrySet()) pairs(e.getValue(), (a, b) -> errors.add(label + " '" + e.getKey() + "' shared by " + a + " and " + b));
	}

	// ---- card against profile -------------------------------------------------------------------------------------

	/**
	 * Checks that a boss's card and its profile agree: the card's generic list must equal the audited behaviours the profile
	 * opted into ({@code profile.genericIds()}), the profile's signature move ids must be the card's, and the profile's telegraph
	 * colour, sound and pitch must be the card's. Call it from the lane's own self-test for each boss.
	 */
	public static List<String> checkProfile(BossIdentity card, DuelistProfile profile) {
		List<String> problems = new ArrayList<>();
		TreeSet<String> want = new TreeSet<>(card.generic()), have = new TreeSet<>(profile.genericIds());
		if (!want.equals(have)) problems.add(card.key() + ": card generic " + want + " does not match the profile's opted-in behaviours " + have);
		TreeSet<String> sigWant = new TreeSet<>(card.signature()), sigHave = new TreeSet<>();
		for (var m : profile.signatures) sigHave.add(m.id());
		if (!sigWant.equals(sigHave)) problems.add(card.key() + ": card signature moves " + sigWant + " do not match the profile's " + sigHave);
		if ((profile.style.colour() & 0xFFFFFF) != card.telegraph().rgb()) problems.add(card.key() + ": profile telegraph colour " + String.format("#%06X", profile.style.colour() & 0xFFFFFF) + " is not the card's " + card.telegraph().colour());
		String ev = profile.style.sound().location().toString(), cardEv = card.telegraph().sound().contains(":") ? card.telegraph().sound() : "minecraft:" + card.telegraph().sound();
		if (!ev.equals(cardEv)) problems.add(card.key() + ": profile telegraph sound " + ev + " is not the card's " + cardEv);
		if (Math.abs(profile.style.pitch() - card.telegraph().pitch()) > 1e-4) problems.add(card.key() + ": profile telegraph pitch " + profile.style.pitch() + " is not the card's " + card.telegraph().pitch());
		return problems;
	}
}
