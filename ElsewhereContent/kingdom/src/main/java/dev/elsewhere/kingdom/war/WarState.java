package dev.elsewhere.kingdom.war;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Saved state of the 2.1 war layer (overworld, {@code data/elsewhere/kingdom_war.dat}): the abstract war model, Standing counters, Free Companies,
 * Serf strikes. Everything is stored as JSON inside two string fields so the shape can grow without a codec change. A world that never had
 * a rival kingdom never creates this file's contents (the data is only written once something is recorded).
 */
public final class WarState extends SavedData {
	static final Gson GSON = new Gson();

	/** Per player and faction Standing bookkeeping (caps, locks, flags). */
	public static final class Extra {
		public long gainDay = -1;
		public int gainToday;
		public long tributeWeek = -1, tradeWeek = -1;
		public int tributeThisWeek, tradeThisWeek;
		/** Game tick until which Standing cannot rise (Oathbreaker), and until which a truce with this faction holds. */
		public long oathUntil, truceUntil;
		/** The player has killed an officer, Lord or ruler of this faction (the hunting-squad rule). */
		public boolean officerKilled;
	}

	/** A named band of sellswords. {@code heads} is its abstract strength; {@code employer} is the kingdom paying it (empty = unemployed). */
	public static final class Company {
		public String id = "", name = "", captain = "", employer = "", against = "";
		public double heads;
		public double payPerHead = 1.2;
		public long hiredTick;
		public boolean deserted;
	}

	/** A contract between a player and a kingdom (Mirewick hires Friendly+ players). */
	public static final class Contract {
		public String player = "", employer = "", enemy = "";
		public long untilTick;
		public int kills;
		public boolean paid;
	}

	/** A village as the war layer knows it. {@code owner} is a faction id or empty (free). */
	public static final class VRec {
		public String key = "", owner = "";
		public double x, z;
		public boolean contestable = true;
	}

	/** Everything that is not the sim itself. */
	public static final class Data {
		public List<String> ids = new ArrayList<>();
		/** Castle x/z per faction id (set by the court module). */
		public Map<String, double[]> castles = new LinkedHashMap<>();
		/** Every village known to the war layer (registered by the villages module); kept in step with the model's arrays by index. */
		public List<VRec> villages = new ArrayList<>();
		public Map<String, Extra> extras = new LinkedHashMap<>();
		public List<Company> companies = new ArrayList<>();
		public List<Contract> contracts = new ArrayList<>();
		/** Serf strikes: faction id to the in-game day the strike ends. */
		public Map<String, Long> strikeUntil = new LinkedHashMap<>();
		public Map<String, Double> treasuryDelta = new LinkedHashMap<>();
		public long lastDay = -1;
		public boolean anyAllied;
		public List<String> log = new ArrayList<>();
		public Map<String, Integer> flipHistory = new LinkedHashMap<>();
		/** Placed longship props awaiting restore (Hrafnholt landing). */
		public List<Longship.ShipRec> ships = new ArrayList<>();
	}

	public static final Codec<WarState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("sim", "").forGetter(WarState::simJson),
		Codec.STRING.optionalFieldOf("data", "").forGetter(WarState::dataJson)
	).apply(i, WarState::fromJson));

	public static final SavedDataType<WarState> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("elsewhere", "kingdom_war"), WarState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public WarSim sim;
	public Data data = new Data();
	private String pendingSim = "";

	public WarState() {}

	static WarState fromJson(String simJson, String dataJson) {
		WarState s = new WarState();
		s.pendingSim = simJson;
		if (!dataJson.isEmpty()) {
			try {
				Data d = GSON.fromJson(dataJson, Data.class);
				if (d != null) s.data = d;
			} catch (RuntimeException ignored) {
				// corrupt data: start clean rather than crash the world load
			}
		}
		return s;
	}

	/** The saved model JSON as read from disk (empty when none); consumed by the layer when it builds the live model. */
	@Nullable String takePendingSim() {
		String p = pendingSim;
		pendingSim = "";
		return p.isEmpty() ? null : p;
	}

	String simJson() {
		return sim == null ? pendingSim : GSON.toJson(sim);
	}

	String dataJson() {
		return GSON.toJson(data);
	}

	public static WarState get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Extra extra(java.util.UUID player, Identifier faction, boolean create) {
		String k = player + "|" + faction;
		Extra e = data.extras.get(k);
		if (e == null && create) {
			e = new Extra();
			data.extras.put(k, e);
		}
		return e;
	}

	/** Tests: forget the model and every counter. */
	public void clearForTests() {
		sim = null;
		pendingSim = "";
		data = new Data();
		WarLayer.forget();
		setDirty();
	}
}
