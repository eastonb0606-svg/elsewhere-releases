package dev.elsewhere.kingdom.armies.townsfolk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.elsewhere.kingdom.armies.ArmyScale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Per-kingdom townsfolk data, {@code data/<ns>/kingdom_townsfolk/<faction>.json} (the file id is the faction id): live caps near a
 * player, the roles with their share of the residents, held item and day schedule. The logical resident counts come from the faction's
 * {@code army.scale.residents} ({@link ArmyScale}); {@link #FALLBACK_RESIDENTS} only when a kingdom has none. Reloaded with /reload.
 * <p>A role's {@code day} lists hour blocks {@code {"from", "to", "activity", "where", "pose"}}; hours outside every block, and the
 * activities {@code home}, {@code sleep} and {@code siesta}, are spent indoors (not live). Where ids are the routine layout anchors
 * (ROUTINES-API.md); poses are routine pose names.
 */
public final class TownsfolkRoles {
	private TownsfolkRoles() {}

	/** Skin variants per role (TOWNSFOLK-IDS.md). */
	public static final int VARIANTS = 3;
	public static final Set<String> INDOORS = Set.of("home", "sleep", "siesta");
	/** Logical residents {capital, town} for a kingdom without {@code army.scale.residents}. */
	public static final int[] FALLBACK_RESIDENTS = {160, 50};

	public record Slot(int from, int to, String activity, String where, String pose) {}

	public record Role(String id, double weight, ItemStack held, List<Slot> day) {
		/** The role's slot at {@code hour}, or null (indoors). */
		public @Nullable Slot at(int hour) {
			for (Slot s : day) if (hour >= s.from && hour < s.to) return INDOORS.contains(s.activity) ? null : s;
			return null;
		}
	}

	/** One kingdom: live caps near a player (capital, town) and its roles. */
	public record Kingdom(Identifier faction, int liveCapital, int liveTown, List<Role> roles) {
		/** Share of the residents outdoors at {@code hour} (0-1), from the role schedules. */
		public double outdoorShare(int hour) {
			double s = 0, w = 0;
			for (Role r : roles) {
				w += r.weight;
				if (r.at(hour) != null) s += r.weight;
			}
			return w <= 0 ? 0 : s / w;
		}

		/** A role picked by weight from a uniform roll in [0, 1). */
		public Role pick(double roll) {
			double w = 0;
			for (Role r : roles) w += r.weight;
			double acc = 0;
			for (Role r : roles) {
				acc += r.weight / w;
				if (roll < acc) return r;
			}
			return roles.get(roles.size() - 1);
		}

		/** A role picked by weight among those outdoors at {@code hour} (all roles when nobody is). */
		public Role pickOutdoors(double roll, int hour) {
			List<Role> out = new ArrayList<>();
			for (Role r : roles) if (r.at(hour) != null) out.add(r);
			if (out.isEmpty()) return pick(roll);
			double w = 0;
			for (Role r : out) w += r.weight;
			double acc = 0;
			for (Role r : out) {
				acc += r.weight / w;
				if (roll < acc) return r;
			}
			return out.get(out.size() - 1);
		}

		public @Nullable Role role(String id) {
			for (Role r : roles) if (r.id.equals(id)) return r;
			return null;
		}
	}

	private static Map<Identifier, Kingdom> loaded = Map.of();
	private static List<String> parseErrors = List.of();

	static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath("elsewhere", "kingdom_townsfolk"),
			new SimpleJsonResourceReloadListener<JsonElement>(ExtraCodecs.JSON, FileToIdConverter.json("kingdom_townsfolk")) {
				@Override
				protected void apply(final Map<Identifier, JsonElement> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					Map<Identifier, Kingdom> out = new LinkedHashMap<>();
					List<String> errs = new ArrayList<>();
					for (var e : prepared.entrySet()) {
						try {
							if (!e.getValue().isJsonObject()) continue;
							Kingdom k = parse(e.getKey(), e.getValue().getAsJsonObject());
							if (k.roles.isEmpty()) throw new IllegalArgumentException("no roles");
							out.put(e.getKey(), k);
						} catch (RuntimeException ex) {
							errs.add(e.getKey() + ": " + ex);
							dev.elsewhere.kingdom.KingdomContent.LOG.warn("bad kingdom_townsfolk file {}: {}", e.getKey(), ex.toString());
						}
					}
					loaded = Collections.unmodifiableMap(out);
					parseErrors = List.copyOf(errs);
					dev.elsewhere.kingdom.KingdomContent.LOG.info("Townsfolk roles loaded for {} kingdoms: {}", out.size(), out.keySet());
				}
			});
	}

	public static Map<Identifier, Kingdom> all() {
		return loaded;
	}

	public static List<String> parseErrors() {
		return parseErrors;
	}

	/** The kingdom's townsfolk (its ledger kingdom for a sub-faction), or null when it has none. */
	public static @Nullable Kingdom of(Identifier faction) {
		Kingdom k = loaded.get(faction);
		if (k == null) k = loaded.get(dev.elsewhere.kingdom.Factions.ledgerOf(faction));
		return k;
	}

	/** Logical residents of a capital ({@code capital} true) or a town of this kingdom: {@code army.scale.residents}, else the fallback. */
	public static int residents(Identifier faction, boolean capital) {
		ArmyScale s = ArmyScale.of(faction);
		if (s == null) s = ArmyScale.of(dev.elsewhere.kingdom.Factions.ledgerOf(faction));
		int v = s == null ? 0 : capital ? s.residentsCapital() : s.residentsTown();
		return v > 0 ? v : FALLBACK_RESIDENTS[capital ? 0 : 1];
	}

	/** Problems with one kingdom's data (empty when valid): used by the self-test and /kingdom townsfolk. */
	public static List<String> problems(Kingdom k) {
		List<String> p = new ArrayList<>();
		if (k.liveCapital < 1 || k.liveCapital > TownsfolkManager.AREA_CAP) p.add("live.capital " + k.liveCapital + " outside 1-" + TownsfolkManager.AREA_CAP);
		if (k.liveTown < 1 || k.liveTown > k.liveCapital) p.add("live.town " + k.liveTown + " outside 1-" + k.liveCapital);
		double w = 0;
		for (Role r : k.roles) {
			w += r.weight;
			int last = 0;
			for (Slot s : r.day) {
				if (s.from < 0 || s.to > 24 || s.from >= s.to) p.add(r.id + " block " + s.from + "-" + s.to + " outside 0-24");
				if (s.from < last) p.add(r.id + " block " + s.from + "-" + s.to + " overlaps the previous one");
				last = Math.max(last, s.to);
			}
			if (r.day.isEmpty()) p.add(r.id + " has no day schedule");
		}
		if (Math.abs(w - 1.0) > 0.01) p.add("role weights sum to " + w);
		return p;
	}

	static Kingdom parse(Identifier id, JsonObject o) {
		JsonObject live = o.has("live") && o.get("live").isJsonObject() ? o.getAsJsonObject("live") : new JsonObject();
		List<Role> roles = new ArrayList<>();
		if (o.has("roles")) for (JsonElement re : o.getAsJsonArray("roles")) {
			JsonObject r = re.getAsJsonObject();
			ItemStack held = ItemStack.EMPTY;
			if (r.has("held")) {
				Identifier hid = Identifier.tryParse(r.get("held").getAsString());
				Item item = hid == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(hid);
				if (item != Items.AIR) held = new ItemStack(item);
			}
			List<Slot> day = new ArrayList<>();
			if (r.has("day")) for (JsonElement se : r.getAsJsonArray("day")) {
				JsonObject s = se.getAsJsonObject();
				day.add(new Slot(i(s, "from", 0), i(s, "to", 0), str(s, "activity", "work"), str(s, "where", "centre"), str(s, "pose", "NONE")));
			}
			roles.add(new Role(str(r, "id", "resident"), d(r, "weight", 1.0), held, List.copyOf(day)));
		}
		return new Kingdom(id, i(live, "capital", 40), i(live, "town", 18), List.copyOf(roles));
	}

	private static int i(JsonObject o, String k, int def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
	}

	private static double d(JsonObject o, String k, double def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
	}

	private static String str(JsonObject o, String k, String def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
	}
}
