package dev.elsewhere.kingdom_court;

import com.mojang.serialization.Codec;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The courts of every faction except Aldermark (whose {@link CourtState} keeps its own 2.0 data file so existing worlds
 * load unchanged). Saved as {@code data/elsewhere/kingdom_court_realms.dat}: faction id to the court's own compound.
 */
public final class CourtRealms extends SavedData {
	public static final Codec<CourtRealms> CODEC = CompoundTag.CODEC.xmap(CourtRealms::load, CourtRealms::save);
	public static final SavedDataType<CourtRealms> TYPE = new SavedDataType<>(CourtContent.id("kingdom_court_realms"), CourtRealms::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	final Map<Identifier, CourtState> states = new LinkedHashMap<>();

	public static CourtRealms get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable CourtState state(Identifier faction, boolean create) {
		CourtState s = states.get(faction);
		if (s == null && create) {
			s = new CourtState();
			s.faction = faction;
			s.owner = this;
			states.put(faction, s);
			setDirty();
		}
		return s;
	}

	public static CourtRealms load(CompoundTag t) {
		CourtRealms r = new CourtRealms();
		for (String k : t.keySet()) {
			Identifier id = Identifier.tryParse(k);
			CompoundTag c = t.getCompoundOrEmpty(k);
			if (id == null) continue;
			CourtState s = CourtState.load(c);
			s.faction = id;
			s.owner = r;
			r.states.put(id, s);
		}
		return r;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		states.forEach((k, v) -> t.put(k.toString(), v.save()));
		return t;
	}
}
