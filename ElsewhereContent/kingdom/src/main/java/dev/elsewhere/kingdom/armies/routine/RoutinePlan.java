package dev.elsewhere.kingdom.armies.routine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.elsewhere.kingdom.armies.ArmyScale;
import dev.elsewhere.kingdom.armies.routine.RoutineEvents.Assignment;
import dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/**
 * The full day schedule of each kingdom (ARMIES.md 2.4), read from the same {@code army.routine} block as {@link ArmyScale} plus
 * the 3.0 detail keys it ignores: per block or event {@code pose}, {@code style}, {@code groups} (what mounted, ranged or melee
 * squads do instead), {@code rest} (what the squads that don't take part do), {@code townsfolk} (assignments for the town lane),
 * {@code sound} / {@code sound_hourly}, {@code lights} (lantern/brazier ids switched on or off), {@code fx} (client festival lights),
 * {@code if} (a {@link RoutineEvents.Condition}), event {@code share} and {@code phases} (a new assignment from an hour on).
 * Pure lookups: no world access except the event conditions.
 */
public final class RoutinePlan {
	private RoutinePlan() {}

	public record Light(String id, boolean on) {}

	public record Group(String unit, Assignment a) {}

	public record Phase(int at, Assignment a, List<Group> groups, @Nullable String sound) {}

	public record Step(String id, int from, int to, int everyDays, boolean event, Assignment a, List<Group> groups, @Nullable Assignment rest,
		List<Assignment> townsfolk, @Nullable String sound, boolean hourlySound, List<Light> lights, @Nullable String fx, @Nullable String cond,
		List<Phase> phases) {

		public boolean on(long day) {
			return Math.floorMod(day, (long) everyDays) == 0;
		}

		public boolean covers(int hour) {
			return hour >= from && hour < to;
		}
	}

	public record Plan(Identifier faction, List<Integer> watch, int jitter, List<Step> blocks, List<Step> events, @Nullable String watchSound) {}

	/** What one squad does this hour, and the block or event that gave it (null for the plain watch). */
	public record Choice(@Nullable Step step, Assignment a) {
		public boolean atPost() {
			return a.activity().equals("watch") || a.activity().equals("sleep");
		}
	}

	private static Map<Identifier, Plan> loaded = Map.of();
	private static List<String> parseErrors = List.of();

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath("elsewhere", "kingdom_routine_plan"),
			new SimpleJsonResourceReloadListener<JsonElement>(ExtraCodecs.JSON, FileToIdConverter.json("kingdom_faction")) {
				@Override
				protected void apply(final Map<Identifier, JsonElement> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					Map<Identifier, Plan> out = new LinkedHashMap<>();
					List<String> errs = new ArrayList<>();
					for (var e : prepared.entrySet()) {
						try {
							if (!e.getValue().isJsonObject()) continue;
							JsonObject f = e.getValue().getAsJsonObject();
							if (!f.has("army") || !f.get("army").isJsonObject()) continue;
							JsonObject army = f.getAsJsonObject("army");
							if (!army.has("routine") || !army.get("routine").isJsonObject()) continue;
							out.put(e.getKey(), parse(e.getKey(), army.getAsJsonObject("routine")));
						} catch (RuntimeException ex) {
							errs.add(e.getKey() + ": " + ex);
							dev.elsewhere.kingdom.KingdomContent.LOG.warn("bad army.routine detail in {}: {}", e.getKey(), ex.toString());
						}
					}
					loaded = Collections.unmodifiableMap(out);
					parseErrors = List.copyOf(errs);
				}
			});
	}

	public static @Nullable Plan of(Identifier faction) {
		return loaded.get(faction);
	}

	public static Map<Identifier, Plan> all() {
		return loaded;
	}

	public static List<String> parseErrors() {
		return parseErrors;
	}

	// ---- lookups --------------------------------------------------------------------------------------------------------------

	public static boolean isWatchChange(Plan p, int hour) {
		return p.watch.contains(hour);
	}

	public static @Nullable Step block(Plan p, int hour) {
		for (Step b : p.blocks) if (b.covers(hour)) return b;
		return null;
	}

	/** Events on today's calendar in force at {@code hour} whose condition holds ({@code lv} null: conditions other than built-ins fail). */
	public static List<Step> events(Plan p, @Nullable ServerLevel lv, long day, int hour) {
		List<Step> out = new ArrayList<>(2);
		for (Step e : p.events) {
			if (!e.on(day) || !e.covers(hour)) continue;
			if (e.cond != null && !(lv != null ? RoutineEvents.test(lv, p.faction, day, e.cond) : "treasury_low".equals(e.cond)
				&& roll(p.faction.hashCode(), day, 77) < 1.0 / 3)) continue;
			out.add(e);
		}
		return out;
	}

	/** True when something changes for squads of this kingdom at {@code hour}: a watch change, a block or event start, end or phase. */
	public static boolean trigger(Plan p, long day, int hour) {
		if (p.watch.contains(hour)) return true;
		for (Step b : p.blocks) {
			if (b.from == hour || b.to == hour) return true;
			for (Phase ph : b.phases) if (ph.at == hour) return true;
		}
		for (Step e : p.events) {
			if (!e.on(day) || e.a.share() <= 0 && e.groups.isEmpty() && e.phases.isEmpty()) continue; // lights-only events move nobody
			if (e.from == hour || e.to == hour) return true;
			for (Phase ph : e.phases) if (ph.at == hour) return true;
		}
		return false;
	}

	/** The assignment of a step for a squad of {@code kind} (mounted, ranged or melee) at {@code hour} (phases and groups applied). */
	public static Assignment assignment(Step s, String kind, int hour) {
		Assignment a = s.a;
		List<Group> groups = s.groups;
		for (Phase ph : s.phases) if (ph.at <= hour) {
			a = ph.a;
			if (!ph.groups.isEmpty()) groups = ph.groups;
		}
		for (Group g : groups) if (g.unit.equals(kind)) return g.a;
		return a;
	}

	/** The plain watch: at the post, a torch by night. */
	public static Assignment watch(int hour) {
		return new Assignment("watch", "post", 1.0, hour >= 19 || hour < 6 ? Pose.TORCH : Pose.NONE, "post");
	}

	/**
	 * What squad {@code squad} (of {@code kind}) does at {@code hour}: an event of the day first (it takes part with the event's
	 * share, a stable roll per squad, day and event), else the block (share rolled per squad, day and hour, so the watch is
	 * relieved at each change), else the block's {@code rest}, else the plain watch.
	 */
	public static Choice choose(Plan p, @Nullable ServerLevel lv, UUID squad, String kind, long day, int hour) {
		long sid = squad.getLeastSignificantBits() ^ squad.getMostSignificantBits();
		for (Step e : events(p, lv, day, hour)) {
			Assignment a = assignment(e, kind, hour);
			if (roll(sid ^ e.id.hashCode(), day, e.from) < a.share()) return new Choice(e, a);
		}
		Step b = block(p, hour);
		if (b != null) {
			Assignment a = assignment(b, kind, hour);
			// one roll per block, re-rolled at each watch change inside it (the relief), so other triggers never reshuffle a block
			int rh = b.from;
			for (int w : p.watch) if (w > b.from && w <= hour) rh = Math.max(rh, w);
			if (roll(sid, day, rh) < a.share()) return new Choice(b, a.activity().equals("watch") ? watch(hour) : a);
			if (b.rest != null) return new Choice(b, b.rest.activity().equals("watch") && b.rest.pose() == Pose.NONE ? watch(hour) : b.rest);
		}
		return new Choice(null, watch(hour));
	}

	/** The kingdom's hour for listeners (townsfolk): the event of the day if any, else the block. */
	public static RoutineEvents.Hour hour(Plan p, @Nullable ServerLevel lv, long day, int hour) {
		List<Step> ev = events(p, lv, day, hour);
		Step s = ev.isEmpty() ? block(p, hour) : ev.get(0);
		Assignment main = s == null ? watch(hour) : assignment(s, "melee", hour);
		List<Assignment> tf = new ArrayList<>();
		if (s != null) tf.addAll(s.townsfolk);
		if (!ev.isEmpty()) {
			Step b = block(p, hour); // townsfolk of the ordinary block keep going where the event leaves room
			if (b != null) for (Assignment a : b.townsfolk) if (tf.stream().noneMatch(x -> x.where().equals(a.where()))) tf.add(a);
		}
		return new RoutineEvents.Hour(p.faction, day, hour, ev.isEmpty() ? null : ev.get(0).id, main, List.copyOf(tf), p.watch.contains(hour));
	}

	public static RoutineEvents.@Nullable Hour hour(Identifier faction, long day, int hour) {
		Plan p = of(faction);
		return p == null ? null : hour(p, null, day, hour);
	}

	/**
	 * A ward's watch offset in in-game minutes, stable per squad, in [-jitter, +jitter] (Mirewick: the wards change about 30 minutes
	 * apart around 07:00 and 19:00, which leaves gaps on purpose). 0 when the kingdom has no jitter.
	 */
	public static int wardOffsetMinutes(Plan p, UUID squad) {
		if (p.jitter <= 0) return 0;
		return (int) Math.floorMod(squad.getMostSignificantBits() * 31 + squad.getLeastSignificantBits(), 2L * p.jitter + 1) - p.jitter;
	}

	public static double roll(long a, long day, int hour) {
		long h = a ^ (day * 0x9E3779B97F4A7C15L) ^ (hour * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) / (double) (1L << 53);
	}

	/** Problems in a kingdom's detail data (self-test). */
	public static List<String> problems(Plan p) {
		List<String> out = new ArrayList<>();
		List<Step> all = new ArrayList<>(p.blocks);
		all.addAll(p.events);
		for (Step s : all) {
			if (s.from < 0 || s.to > 24 || s.from >= s.to) out.add(s.id + ": hours " + s.from + "-" + s.to);
			check(out, s.id, s.a);
			if (s.rest != null) check(out, s.id + " rest", s.rest);
			for (Group g : s.groups) {
				if (!List.of("mounted", "ranged", "melee").contains(g.unit)) out.add(s.id + ": unknown group unit " + g.unit);
				check(out, s.id + " " + g.unit, g.a);
			}
			for (Assignment a : s.townsfolk) check(out, s.id + " townsfolk", a);
			for (Phase ph : s.phases) {
				if (ph.at <= s.from || ph.at >= s.to) out.add(s.id + ": phase at " + ph.at + " outside the event");
				check(out, s.id + " phase", ph.a);
			}
		}
		return out;
	}

	private static void check(List<String> out, String id, Assignment a) {
		if (!ArmyScale.ACTIVITIES.contains(a.activity())) out.add(id + ": unknown activity " + a.activity());
		if (a.share() < 0 || a.share() > 1) out.add(id + ": share " + a.share());
	}

	// ---- parsing ------------------------------------------------------------------------------------------------------------

	static Plan parse(Identifier id, JsonObject r) {
		List<Integer> watch = new ArrayList<>();
		if (r.has("watch_changes")) for (JsonElement e : r.getAsJsonArray("watch_changes")) watch.add(e.getAsInt());
		List<Step> blocks = new ArrayList<>(), events = new ArrayList<>();
		if (r.has("blocks")) for (JsonElement e : r.getAsJsonArray("blocks")) {
			JsonObject b = e.getAsJsonObject();
			int from = i(b, "from", 0), to = i(b, "to", 0);
			blocks.add(step(b, "block_" + from, from, to, 1, false, 0.5));
		}
		if (r.has("events")) for (JsonElement e : r.getAsJsonArray("events")) {
			JsonObject b = e.getAsJsonObject();
			int at = i(b, "at", 12);
			events.add(step(b, str(b, "id", "event"), at, at + i(b, "hours", 1), Math.max(1, i(b, "every_days", 7)), true, 0.8));
		}
		return new Plan(id, List.copyOf(watch), i(r, "watch_jitter_min", 0), List.copyOf(blocks), List.copyOf(events), str(r, "watch_sound", null));
	}

	static Step step(JsonObject b, String id, int from, int to, int every, boolean event, double defShare) {
		Assignment a = assignment(b, defShare, "centre");
		List<Group> groups = groups(b, a);
		Assignment rest = b.has("rest") && b.get("rest").isJsonObject() ? assignment(b.getAsJsonObject("rest"), 1.0, "post") : null;
		List<Assignment> tf = new ArrayList<>();
		if (b.has("townsfolk") && b.get("townsfolk").isJsonArray()) for (JsonElement e : b.getAsJsonArray("townsfolk")) tf.add(assignment(e.getAsJsonObject(), 0.5, a.where()));
		List<Light> lights = new ArrayList<>();
		if (b.has("lights") && b.get("lights").isJsonArray()) for (JsonElement e : b.getAsJsonArray("lights")) {
			JsonObject l = e.getAsJsonObject();
			lights.add(new Light(str(l, "id", "lantern_posts"), !l.has("on") || l.get("on").getAsBoolean()));
		}
		List<Phase> phases = new ArrayList<>();
		if (b.has("phases") && b.get("phases").isJsonArray()) for (JsonElement e : b.getAsJsonArray("phases")) {
			JsonObject ph = e.getAsJsonObject();
			Assignment pa = assignment(ph, a.share(), a.where(), a.activity());
			phases.add(new Phase(i(ph, "at", from + 1), pa, groups(ph, pa), ph.has("sound") ? str(ph, "sound", null) : null));
		}
		return new Step(id, from, to, every, event, a, groups, rest, List.copyOf(tf), b.has("sound") ? str(b, "sound", null) : null,
			b.has("sound_hourly") && b.get("sound_hourly").getAsBoolean(), List.copyOf(lights), b.has("fx") ? str(b, "fx", null) : null,
			b.has("if") ? str(b, "if", null) : null, List.copyOf(phases));
	}

	static List<Group> groups(JsonObject b, Assignment base) {
		List<Group> out = new ArrayList<>();
		if (b.has("groups") && b.get("groups").isJsonArray()) for (JsonElement e : b.getAsJsonArray("groups")) {
			JsonObject g = e.getAsJsonObject();
			out.add(new Group(str(g, "unit", "melee"), assignment(g, base.share(), base.where(), base.activity())));
		}
		return List.copyOf(out);
	}

	static Assignment assignment(JsonObject b, double defShare, String defWhere) {
		return assignment(b, defShare, defWhere, "watch");
	}

	static Assignment assignment(JsonObject b, double defShare, String defWhere, String defActivity) {
		String act = str(b, "activity", defActivity);
		String style = str(b, "style", "");
		return new Assignment(act, str(b, "where", defWhere), d(b, "share", defShare), Pose.byName(str(b, "pose", null), RoutinePoses.defaultFor(act, style)), style);
	}

	private static int i(JsonObject o, String k, int def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
	}

	private static double d(JsonObject o, String k, double def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
	}

	private static @Nullable String str(JsonObject o, String k, @Nullable String def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
	}

	static JsonArray arr(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
	}
}
