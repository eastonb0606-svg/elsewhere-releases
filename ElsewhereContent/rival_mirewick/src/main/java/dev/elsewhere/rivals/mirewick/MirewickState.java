package dev.elsewhere.rivals.mirewick;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Saved Mirewick data ({@code data/elsewhere/rival_mirewick.dat}): the temporary-block ledger, planted Marsh-Lantern
 * Standards, Magnate kill counts (DropRule), per-player drop pity, (the treasury and the sellsword contracts live in the war layer).
 */
public final class MirewickState extends SavedData {
	public static final Codec<MirewickState> CODEC = CompoundTag.CODEC.xmap(MirewickState::load, MirewickState::save);
	public static final SavedDataType<MirewickState> TYPE = new SavedDataType<>(MirewickContent.id("rival_mirewick"), MirewickState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** One temporary block: dimension, position, the block placed (registry id) and the game tick it must be gone by. */
	public record Temp(String dim, long pos, String block, long expiry) {}

	public final List<Temp> temp = new ArrayList<>();
	public final Map<UUID, long[]> standards = new LinkedHashMap<>();   // uuid -> {pos, game tick planted}
	public final Map<String, Integer> kills = new HashMap<>();            // slot -> kills of any holder of that seat
	public final Map<String, Integer> pity = new HashMap<>();             // "uuid:slot" -> consecutive misses
	/** Mirewick's abstract treasury when the war model does not run it (a world with fewer than two kingdoms). */
	public double localTreasury = 300.0;
	/** Pikemaster Dunstan Reeve: set once (he is never reborn). */
	public @org.jspecify.annotations.Nullable UUID dunstan;

	public static MirewickState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static MirewickState load(CompoundTag t) {
		MirewickState s = new MirewickState();
		CompoundTag tmp = t.getCompoundOrEmpty("temp");
		for (String k : tmp.keySet()) {
			CompoundTag c = tmp.getCompoundOrEmpty(k);
			s.temp.add(new Temp(c.getStringOr("d", "minecraft:overworld"), c.getLongOr("p", 0L), c.getStringOr("b", "minecraft:air"), c.getLongOr("e", 0L)));
		}
		CompoundTag st = t.getCompoundOrEmpty("standards");
		for (String k : st.keySet()) {
			CompoundTag c = st.getCompoundOrEmpty(k);
			try {
				s.standards.put(UUID.fromString(k), new long[] {c.getLongOr("p", 0L), c.getLongOr("t", 0L)});
			} catch (IllegalArgumentException ignored) {
			}
		}
		CompoundTag kl = t.getCompoundOrEmpty("kills");
		for (String k : kl.keySet()) s.kills.put(k, kl.getIntOr(k, 0));
		CompoundTag pt = t.getCompoundOrEmpty("pity");
		for (String k : pt.keySet()) s.pity.put(k, pt.getIntOr(k, 0));
		s.localTreasury = t.getDoubleOr("treasury", 300.0);
		String d = t.getStringOr("dunstan", "");
		if (!d.isEmpty()) {
			try {
				s.dunstan = UUID.fromString(d);
			} catch (IllegalArgumentException ignored) {
			}
		}
		return s;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		CompoundTag tmp = new CompoundTag();
		int i = 0;
		for (Temp e : temp) {
			CompoundTag c = new CompoundTag();
			c.putString("d", e.dim());
			c.putLong("p", e.pos());
			c.putString("b", e.block());
			c.putLong("e", e.expiry());
			tmp.put(Integer.toString(i++), c);
		}
		t.put("temp", tmp);
		CompoundTag st = new CompoundTag();
		standards.forEach((k, v) -> {
			CompoundTag c = new CompoundTag();
			c.putLong("p", v[0]);
			c.putLong("t", v[1]);
			st.put(k.toString(), c);
		});
		t.put("standards", st);
		CompoundTag kl = new CompoundTag();
		kills.forEach(kl::putInt);
		t.put("kills", kl);
		CompoundTag pt = new CompoundTag();
		pity.forEach(pt::putInt);
		t.put("pity", pt);
		t.putDouble("treasury", localTreasury);
		if (dunstan != null) t.putString("dunstan", dunstan.toString());
		return t;
	}
}
