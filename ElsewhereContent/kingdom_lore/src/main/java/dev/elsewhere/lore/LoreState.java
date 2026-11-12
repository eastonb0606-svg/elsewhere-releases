package dev.elsewhere.lore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Saved lore state of the overworld ({@code data/elsewhere/lore_state.dat}): liberated villages and the one-time faction stance. */
public final class LoreState extends SavedData {
	public static final Codec<LoreState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.listOf().optionalFieldOf("liberated", List.of()).forGetter(s -> s.liberated),
		Codec.BOOL.optionalFieldOf("stance_set", false).forGetter(s -> s.stanceSet),
		Codec.STRING.listOf().optionalFieldOf("liberated_factions", List.of()).forGetter(s -> s.factions)
	).apply(i, (l, st, fs) -> {
		LoreState s = new LoreState();
		s.liberated.addAll(l);
		s.stanceSet = st;
		s.factions.addAll(fs);
		return s;
	}));

	public static final SavedDataType<LoreState> TYPE = new SavedDataType<>(LoreContent.id("lore_state"), LoreState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Liberated village centres ({@link BlockPos#asLong()}). */
	final List<Long> liberated = new ArrayList<>();
	/** Faction id of each liberated village (parallel to {@link #liberated}; missing = Aldermark). */
	final List<String> factions = new ArrayList<>();
	boolean stanceSet;

	public LoreState() {}

	public static LoreState get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public void markLiberated(BlockPos pos) {
		markLiberated(pos, "");
	}

	public void markLiberated(BlockPos pos, String faction) {
		if (isLiberated(pos, 32)) return;
		while (factions.size() < liberated.size()) factions.add("");
		liberated.add(pos.asLong());
		factions.add(faction == null ? "" : faction);
		setDirty();
	}

	/** Faction recorded for the liberated village nearest {@code pos} within {@code radius}, or "". */
	public String factionNear(BlockPos pos, int radius) {
		long best = (long) radius * radius;
		String out = "";
		for (int i = 0; i < liberated.size(); i++) {
			BlockPos c = BlockPos.of(liberated.get(i));
			long dx = c.getX() - pos.getX(), dz = c.getZ() - pos.getZ(), d = dx * dx + dz * dz;
			if (d <= best) {
				best = d;
				out = i < factions.size() ? factions.get(i) : "";
			}
		}
		return out;
	}

	/** True when a liberated village centre lies within {@code radius} blocks (horizontal). */
	public boolean isLiberated(BlockPos pos, int radius) {
		long r2 = (long) radius * radius;
		for (long l : liberated) {
			BlockPos c = BlockPos.of(l);
			long dx = c.getX() - pos.getX(), dz = c.getZ() - pos.getZ();
			if (dx * dx + dz * dz <= r2) return true;
		}
		return false;
	}

	public int liberatedCount() {
		return liberated.size();
	}

	public boolean stanceSet() {
		return stanceSet;
	}

	public void setStanceSet() {
		stanceSet = true;
		setDirty();
	}
}
