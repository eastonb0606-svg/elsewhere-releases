package dev.elsewhere.kingdom_villages;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-level saved state ({@code data/elsewhere/kingdom_villages.dat}): every known village and kingdom site. */
public final class VillageLedger extends SavedData {
	public static final Codec<VillageLedger> CODEC = CompoundTag.CODEC.xmap(VillageLedger::load, VillageLedger::save);
	public static final SavedDataType<VillageLedger> TYPE = new SavedDataType<>(VillagesContent.id("kingdom_villages"), VillageLedger::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public final Long2ObjectLinkedOpenHashMap<VillageRecord> villages = new Long2ObjectLinkedOpenHashMap<>();
	public final Long2ObjectLinkedOpenHashMap<VillageRecord> sites = new Long2ObjectLinkedOpenHashMap<>();
	/** Chunks (packed ChunkPos) where a player placed or broke a block: villages touching them are never changed. */
	public final LongOpenHashSet playerChunks = new LongOpenHashSet();
	/** Chunks where a villager traded with a player (for villages not yet discovered). */
	public final LongOpenHashSet tradeChunks = new LongOpenHashSet();
	public final Map<UUID, Long> stewardWeekly = new HashMap<>();
	/** Rival outposts per faction id (Aldermark keeps {@link #outpostsFounded}) and the last founding time per faction. */
	public final Map<String, Integer> outpostsBy = new HashMap<>();
	public final Map<String, Long> lastOutpostBy = new HashMap<>();
	/** Game time until which a faction's Peat-Cutters are on strike (Mirewick, after a Counting-House raid). */
	public final Map<String, Long> strikeUntil = new HashMap<>();
	public boolean conquestOn = true;
	public int outpostsFounded;
	public long lastOutpostTime = Long.MIN_VALUE / 2;
	public long nextSiteId = 1;

	public static VillageLedger get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public void dirty() {
		setDirty();
	}

	private static VillageLedger load(CompoundTag t) {
		VillageLedger l = new VillageLedger();
		for (Tag tag : t.getListOrEmpty("villages")) if (tag instanceof CompoundTag c) { VillageRecord r = VillageRecord.load(c); l.villages.put(r.id, r); }
		for (Tag tag : t.getListOrEmpty("sites")) if (tag instanceof CompoundTag c) { VillageRecord r = VillageRecord.load(c); l.sites.put(r.id, r); }
		for (long v : t.getLongArray("playerChunks").orElse(new long[0])) l.playerChunks.add(v);
		for (long v : t.getLongArray("tradeChunks").orElse(new long[0])) l.tradeChunks.add(v);
		t.read("stewardWeekly", Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)).ifPresent(l.stewardWeekly::putAll);
		t.read("outpostsBy", Codec.unboundedMap(Codec.STRING, Codec.INT)).ifPresent(l.outpostsBy::putAll);
		t.read("lastOutpostBy", Codec.unboundedMap(Codec.STRING, Codec.LONG)).ifPresent(l.lastOutpostBy::putAll);
		t.read("strikeUntil", Codec.unboundedMap(Codec.STRING, Codec.LONG)).ifPresent(l.strikeUntil::putAll);
		l.conquestOn = t.getBooleanOr("conquestOn", true);
		l.outpostsFounded = t.getIntOr("outpostsFounded", 0);
		l.lastOutpostTime = t.getLongOr("lastOutpostTime", Long.MIN_VALUE / 2);
		l.nextSiteId = t.getLongOr("nextSiteId", 1L);
		return l;
	}

	private CompoundTag save() {
		CompoundTag t = new CompoundTag();
		ListTag v = new ListTag();
		for (VillageRecord r : villages.values()) v.add(r.save());
		t.put("villages", v);
		ListTag s = new ListTag();
		for (VillageRecord r : sites.values()) s.add(r.save());
		t.put("sites", s);
		t.putLongArray("playerChunks", playerChunks.toLongArray());
		t.putLongArray("tradeChunks", tradeChunks.toLongArray());
		t.store("stewardWeekly", Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG), stewardWeekly);
		t.store("outpostsBy", Codec.unboundedMap(Codec.STRING, Codec.INT), outpostsBy);
		t.store("lastOutpostBy", Codec.unboundedMap(Codec.STRING, Codec.LONG), lastOutpostBy);
		t.store("strikeUntil", Codec.unboundedMap(Codec.STRING, Codec.LONG), strikeUntil);
		t.putBoolean("conquestOn", conquestOn);
		t.putInt("outpostsFounded", outpostsFounded);
		t.putLong("lastOutpostTime", lastOutpostTime);
		t.putLong("nextSiteId", nextSiteId);
		return t;
	}
}
