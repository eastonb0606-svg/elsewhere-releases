package dev.elsewhere.kingdom_court.trade;

import com.mojang.serialization.Codec;
import dev.elsewhere.kingdom_court.CourtContent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Everything the 2.1 trade rules remember, per world (overworld data storage {@code data/elsewhere/kingdom_trade.dat}): deeds and rings given, Mirewick's floating
 * price offsets, haggle locks, loans, the weekly auction, Azmar's commissions, Letters of Credit balances and the caravan. Three flat maps with structured string keys
 * (like the court ledger, a compound per map so nothing hits the 65,535-byte NBT string limit); each mechanic documents its own keys.
 */
public final class TradeState extends SavedData {
	public static final Codec<TradeState> CODEC = CompoundTag.CODEC.xmap(TradeState::load, TradeState::save);
	public static final SavedDataType<TradeState> TYPE = new SavedDataType<>(CourtContent.id("kingdom_trade"), TradeState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public final Map<String, Long> longs = new HashMap<>();
	public final Map<String, Double> doubles = new HashMap<>();
	public final Map<String, String> strings = new HashMap<>();

	public static TradeState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public long getLong(String k, long def) {
		return longs.getOrDefault(k, def);
	}

	public void putLong(String k, long v) {
		longs.put(k, v);
		setDirty();
	}

	public void addLong(String k, long d) {
		longs.merge(k, d, Long::sum);
		setDirty();
	}

	public double getDouble(String k, double def) {
		return doubles.getOrDefault(k, def);
	}

	public void putDouble(String k, double v) {
		doubles.put(k, v);
		setDirty();
	}

	public String getString(String k) {
		return strings.get(k);
	}

	public void putString(String k, String v) {
		strings.put(k, v);
		setDirty();
	}

	public void remove(String k) {
		boolean a = longs.remove(k) != null, b = doubles.remove(k) != null, c = strings.remove(k) != null;
		if (a || b || c) setDirty();
	}

	/** Removes every entry whose key starts with {@code prefix}. */
	public void removePrefix(String prefix) {
		boolean a = longs.keySet().removeIf(k -> k.startsWith(prefix)), b = doubles.keySet().removeIf(k -> k.startsWith(prefix)), c = strings.keySet().removeIf(k -> k.startsWith(prefix));
		if (a || b || c) setDirty();
	}

	public void clearForTests() {
		longs.clear();
		doubles.clear();
		strings.clear();
		setDirty();
	}

	public static TradeState load(CompoundTag t) {
		TradeState s = new TradeState();
		CompoundTag l = t.getCompoundOrEmpty("longs"), d = t.getCompoundOrEmpty("doubles"), x = t.getCompoundOrEmpty("strings");
		for (String k : l.keySet()) s.longs.put(k, l.getLongOr(k, 0L));
		for (String k : d.keySet()) s.doubles.put(k, d.getDoubleOr(k, 0.0));
		for (String k : x.keySet()) s.strings.put(k, x.getStringOr(k, ""));
		return s;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag(), l = new CompoundTag(), d = new CompoundTag(), x = new CompoundTag();
		longs.forEach(l::putLong);
		doubles.forEach(d::putDouble);
		strings.forEach(x::putString);
		t.put("longs", l);
		t.put("doubles", d);
		t.put("strings", x);
		return t;
	}
}
