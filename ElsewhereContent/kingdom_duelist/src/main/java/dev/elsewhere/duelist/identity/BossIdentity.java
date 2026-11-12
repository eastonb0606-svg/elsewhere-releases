package dev.elsewhere.duelist.identity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.elsewhere.duelist.boss.TelegraphStyle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

/**
 * A boss's identity card, loaded from {@code data/elsewhere/boss_identity/<key>.json} (one file per boss), in exactly the
 * designer's format ({@code Ideas/kingdom/boss_identity}):
 *
 * <pre>
 * {
 *   "id": "elsewhere:azmar_amir", "kingdom": "azmar",
 *   "slots": { "weapons": "...", "movement": "...", "defence": "...", "recovery": "...", "reach": "...", "last_stand": "..." },
 *   "generic": ["drink"],                         // of circle_strafe, sprint_jump_crit, shield_block, eat, drink, pearl, temp_blocks
 *   "signature": ["camel_charge", "javelin_volley", "dismount_lunge"],   // 2 or 3
 *   "telegraph": { "particle": "dust_plume", "colour": "#B0362B", "sound": { "event": "entity.camel.dash", "pitch": 1.0 } },
 *   "last_stand": "final_charge", "idle": "...",
 *   "chair_last_stand": "lantern_rally",          // Mirewick Magnates only
 *   "risen_keeps": ["..."]                        // Hrafnholt champions only
 * }
 * </pre>
 *
 * {@code key} is the file name without ".json" ({@code azmar_amir}); {@link #get} accepts the key or the full id. The card
 * is the data the sameness audit reads ({@link BossIdentityAudit}); the behaviour lives in the boss's {@code DuelistProfile},
 * and {@link BossIdentityAudit#checkProfile} compares the two.
 */
public record BossIdentity(String key, String id, String kingdom, Map<String, String> slots, List<String> generic, List<String> signature,
		Look telegraph, String lastStand, @Nullable String chairLastStand, String idle, List<String> risenKeeps, JsonObject raw) {
	/** The six slots, in order. */
	public static final List<String> SLOTS = List.of("weapons", "movement", "defence", "recovery", "reach", "last_stand");
	/** The generic behaviours a card may list. */
	public static final List<String> GENERICS = List.of("circle_strafe", "sprint_jump_crit", "shield_block", "eat", "drink", "pearl", "temp_blocks");

	/** A telegraph look as written in the card: a particle name, "#RRGGBB", a sound event id and its pitch. */
	public record Look(String particle, String colour, String sound, float pitch) {
		public int rgb() {
			return TelegraphStyle.parseColour(colour);
		}
	}

	/**
	 * The card's telegraph as a render style, with the particle the boss's lane made for the card's particle name (most of the
	 * designer's names are that boss's own particle, e.g. {@code dust_plume}) and the shape it wants. Colour, sound and pitch are the card's.
	 */
	public TelegraphStyle style(net.minecraft.core.particles.ParticleOptions particle, TelegraphStyle.Shape shape) {
		return TelegraphStyle.of("dust", telegraph.rgb(), telegraph.sound(), shape, telegraph.pitch()).withParticle(particle);
	}

	/** The card's telegraph as a render style when its particle name is a vanilla particle id; throws if it is not (no default look). */
	public TelegraphStyle style(TelegraphStyle.Shape shape) {
		return TelegraphStyle.of(telegraph.particle(), telegraph.rgb(), telegraph.sound(), shape, telegraph.pitch());
	}

	// ---- parsing --------------------------------------------------------------------------------------------------

	/** Strict JSON (what Python's json module accepts): no comments, no trailing commas, nothing after the value. */
	public static JsonElement strictJson(String json) throws IOException {
		JsonReader r = new JsonReader(new StringReader(json));
		r.setStrictness(Strictness.STRICT);
		JsonElement e;
		try {
			e = JsonParser.parseReader(r);
			if (r.peek() != JsonToken.END_DOCUMENT) throw new IOException("data after the JSON value");
		} catch (RuntimeException ex) {
			throw new IOException(ex.getMessage(), ex);
		}
		return e;
	}

	/** Parses one card. Throws {@link IllegalArgumentException} naming every schema problem ({@link BossIdentityAudit#schemaProblems}). */
	public static BossIdentity parse(String key, String json) {
		JsonElement e;
		try {
			e = strictJson(json);
		} catch (IOException ex) {
			throw new IllegalArgumentException(key + ": invalid JSON (" + ex.getMessage() + ")");
		}
		List<String> problems = BossIdentityAudit.schemaProblems(key, e, false);
		if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
		JsonObject o = e.getAsJsonObject();
		Map<String, String> slots = new LinkedHashMap<>();
		for (String s : SLOTS) slots.put(s, o.getAsJsonObject("slots").get(s).getAsString());
		List<String> generic = strings(o.get("generic")), signature = strings(o.get("signature"));
		JsonObject t = o.getAsJsonObject("telegraph");
		JsonElement snd = t.get("sound");
		String event = snd.isJsonObject() ? snd.getAsJsonObject().get("event").getAsString() : snd.getAsString();
		float pitch = snd.isJsonObject() ? snd.getAsJsonObject().get("pitch").getAsFloat() : 1.0F;
		Look look = new Look(t.get("particle").getAsString(), t.get("colour").getAsString(), event, pitch);
		return new BossIdentity(key, str(o, "id", "elsewhere:" + key), str(o, "kingdom", key.contains("_") ? key.substring(0, key.indexOf('_')) : ""),
			java.util.Collections.unmodifiableMap(slots), List.copyOf(generic), List.copyOf(signature), look, text(o.get("last_stand")),
			o.has("chair_last_stand") ? text(o.get("chair_last_stand")) : null, str(o, "idle", ""), o.has("risen_keeps") ? List.copyOf(strings(o.get("risen_keeps"))) : List.of(), o);
	}

	private static String str(JsonObject o, String k, String dflt) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : dflt;
	}

	static String text(JsonElement e) {
		return e.isJsonPrimitive() ? e.getAsString() : e.toString();
	}

	private static List<String> strings(JsonElement e) {
		List<String> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(text(x));
		return out;
	}

	// ---- loading --------------------------------------------------------------------------------------------------

	/** The raw text of every {@code *.json} in a directory, by file name without ".json" (sorted). Feed it to {@link BossIdentityAudit#checkRaw}. */
	public static Map<String, String> readDir(Path dir) throws IOException {
		Map<String, String> out = new TreeMap<>();
		if (!Files.isDirectory(dir)) return out;
		try (Stream<Path> files = Files.list(dir)) {
			for (Path f : files.filter(x -> x.getFileName().toString().endsWith(".json")).toList()) {
				String name = f.getFileName().toString();
				out.put(name.substring(0, name.length() - 5), Files.readString(f));
			}
		}
		return out;
	}

	/** The raw text of every {@code data/elsewhere/boss_identity/*.json} in the loaded data packs (every kingdom mod's cards together), by key. */
	public static Map<String, String> readAll(ResourceManager rm) {
		Map<String, String> out = new TreeMap<>();
		Map<Identifier, Resource> found = rm.listResources("boss_identity", id -> id.getNamespace().equals("elsewhere") && id.getPath().endsWith(".json"));
		for (var e : found.entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
			try (BufferedReader r = e.getValue().openAsReader()) {
				StringBuilder sb = new StringBuilder();
				char[] buf = new char[4096];
				int n;
				while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
				out.put(name, sb.toString());
			} catch (IOException ex) {
				out.put(name, "");
			}
		}
		return out;
	}

	/** Parses every card of a raw map; a card that does not parse is reported in {@code problems} (if not null) and skipped. */
	public static List<BossIdentity> parseAll(Map<String, String> raw, @Nullable List<String> problems) {
		List<BossIdentity> out = new ArrayList<>();
		for (var e : new TreeMap<>(raw).entrySet()) {
			try {
				out.add(parse(e.getKey(), e.getValue()));
			} catch (IllegalArgumentException ex) {
				if (problems != null) problems.add(ex.getMessage());
			}
		}
		return out;
	}

	// ---- cache ----------------------------------------------------------------------------------------------------

	private static volatile Map<String, BossIdentity> cache = Map.of();
	private static volatile Map<String, String> rawCache = Map.of();
	private static boolean inited;

	/** Registers the reload hooks (called by {@code DuelistContent.init()}). */
	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerLifecycleEvents.SERVER_STARTED.register(BossIdentity::reload);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, rm, ok) -> reload(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			cache = Map.of();
			rawCache = Map.of();
		});
	}

	/** Re-reads every card from the server's data packs. A card that does not parse is logged and left out. */
	public static void reload(MinecraftServer server) {
		Map<String, String> raw = readAll(server.getResourceManager());
		List<String> problems = new ArrayList<>();
		Map<String, BossIdentity> m = new LinkedHashMap<>();
		for (BossIdentity b : parseAll(raw, problems)) m.put(b.key(), b);
		for (String p : problems) System.err.println("[duelist] boss_identity: " + p);
		rawCache = raw;
		cache = m;
	}

	/** The loaded card for a boss: the key ({@code azmar_amir}, a profile's {@code identity}) or the full id ({@code elsewhere:azmar_amir}). Null if there is none. */
	public static @Nullable BossIdentity get(String keyOrId) {
		return cache.get(keyOrId.startsWith("elsewhere:") ? keyOrId.substring("elsewhere:".length()) : keyOrId);
	}

	/** Every loaded card. */
	public static Collection<BossIdentity> all() {
		return cache.values();
	}

	/** The raw text of every loaded card file (including any that failed to parse), for {@link BossIdentityAudit#checkRaw}. */
	public static Map<String, String> allRaw() {
		return rawCache;
	}
}
