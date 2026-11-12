package dev.elsewhere.kingdom.armies.townsfolk;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Saved townsfolk state (overworld, {@code data/elsewhere/kingdom_townsfolk.dat}): per town its logical resident count, the day it was
 * last refilled and its street graph. Stored as JSON in one string field (the {@code WarState} pattern) so the shape can grow without
 * a codec change. Live townsfolk are never saved; this is the whole save.
 */
public final class TownsfolkState extends SavedData {
	static final Gson GSON = new Gson();

	/** One town. {@code logical} -1 = not seen yet (starts full). */
	public static final class Rec {
		public int logical = -1;
		public long lastDay = -1;
		public int[] gx, gy, gz;
		public byte[] gf;
		public int[][] adj;
		public long builtDay = -1;
	}

	public static final class Data {
		public Map<String, Rec> towns = new LinkedHashMap<>();
	}

	public static final Codec<TownsfolkState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("data", "").forGetter(TownsfolkState::json)
	).apply(i, TownsfolkState::fromJson));

	public static final SavedDataType<TownsfolkState> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("elsewhere", "kingdom_townsfolk"),
		TownsfolkState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public Data data = new Data();

	public TownsfolkState() {}

	static TownsfolkState fromJson(String json) {
		TownsfolkState s = new TownsfolkState();
		if (!json.isEmpty()) {
			try {
				Data d = GSON.fromJson(json, Data.class);
				if (d != null && d.towns != null) s.data = d;
			} catch (RuntimeException ignored) {
				// corrupt: start clean (towns refill to full, graphs rebuild) rather than break the world load
			}
		}
		return s;
	}

	String json() {
		return GSON.toJson(data);
	}

	public static TownsfolkState get(ServerLevel any) {
		return any.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Rec rec(String key) {
		return data.towns.computeIfAbsent(key, k -> new Rec());
	}

	/**
	 * The town's logical residents today: full ({@code max}) when first seen, otherwise refilled by 1 per in-game day since the last
	 * refill, never above {@code max} (ARMIES.md 2.5). Evaluated lazily when the town is looked at, so no daily sweep runs.
	 */
	public int logical(String key, int max, long day) {
		Rec r = rec(key);
		if (r.logical < 0) {
			r.logical = max;
			r.lastDay = day;
			setDirty();
		} else if (day > r.lastDay) {
			int add = (int) Math.min(Integer.MAX_VALUE, day - r.lastDay);
			int v = Math.min(max, r.logical + add);
			r.lastDay = day;
			if (v != r.logical) r.logical = v;
			setDirty();
		} else if (r.logical > max) {
			r.logical = max; // the kingdom's residents were lowered in data
			setDirty();
		}
		return r.logical;
	}

	/** A resident died: one fewer until the daily refill. */
	public void kill(String key) {
		Rec r = rec(key);
		if (r.logical > 0) r.logical--;
		setDirty();
	}

	public @Nullable StreetGraph graph(String key) {
		Rec r = data.towns.get(key);
		if (r == null || r.gx == null || r.adj == null || r.gx.length != r.adj.length) return null;
		return new StreetGraph(r.gx, r.gy, r.gz, r.gf == null ? new byte[r.gx.length] : r.gf, r.adj, r.builtDay);
	}

	public void putGraph(String key, StreetGraph g) {
		Rec r = rec(key);
		r.gx = g.x;
		r.gy = g.y;
		r.gz = g.z;
		r.gf = g.flags;
		r.adj = g.adj;
		r.builtDay = g.builtDay;
		setDirty();
	}

	/** Tests: forget everything. */
	public void clearForTests() {
		data = new Data();
		setDirty();
	}
}
