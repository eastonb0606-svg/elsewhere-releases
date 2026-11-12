package dev.elsewhere.kingdom.armies;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Self-test probe (s-intreq, from s-azmarsize 12:46): fields soldiers exactly the way a wave battle does ({@link ArmyWaves#field}, the
 * plain spawn path), outside the war layer and outside {@link ArmyWaves#battles()}, reports the squads it made and removes them again.
 * The kingdom self-test uses it to prove that every kingdom's waves come in its own {@code army.scale} squads. Test-only: nothing in
 * play calls it.
 */
public final class WaveScaleProbe {
	private WaveScaleProbe() {}

	/** What one field call made: the side's squad and wave size, the soldiers fielded and each squad's head count (leader included). */
	public record Seen(Identifier faction, int squadSize, int waveSize, int fielded, List<Integer> squads) {
		/** True when every squad is at most the kingdom's size and all but the last are full. */
		public boolean followsScale() {
			for (int i = 0; i < squads.size(); i++) {
				int c = squads.get(i);
				if (c > squadSize || (i < squads.size() - 1 && c != squadSize)) return false;
			}
			return !squads.isEmpty();
		}
	}

	/** The scale a faction's wave battle uses (its loaded {@code army.scale}), or null. */
	public static @Nullable ArmyScale scale(Identifier faction) {
		return ArmyScale.of(faction);
	}

	/** A stand-in scale for {@code faction} with the given squad and wave size (proves the code path where no rival data is loaded). */
	public static ArmyScale testScale(Identifier faction, int squadSize, int waveSize) {
		JsonObject army = JsonParser.parseString("{\"scale\":{\"squad_size\":" + squadSize + ",\"wave\":{\"style\":\"relief\",\"size\":" + waveSize
			+ ",\"interval_s\":30}}}").getAsJsonObject();
		return ArmyScale.parse(faction, army);
	}

	/**
	 * Fields {@code n} soldiers of {@code faction} at {@code at} with {@code scale} (null = no scale: the old 10), as the attacking side of a
	 * wave battle, then discards them and disbands their squads.
	 */
	public static Seen field(ServerLevel lv, BlockPos at, Identifier faction, @Nullable ArmyScale scale, int n) {
		ArmyWaves.Side side = new ArmyWaves.Side(faction, scale, n, at, at);
		ArmyWaves.Side other = new ArmyWaves.Side(faction, scale, 0, at, at);
		ArmyWaves.Battle b = new ArmyWaves.Battle(-1, at, side, other, lv.getGameTime());
		int made = ArmyWaves.field(lv, b, side, n);
		Map<UUID, Integer> per = new LinkedHashMap<>();
		List<KingdomSoldier> all = new ArrayList<>();
		for (UUID id : side.soldiers) {
			Entity e = lv.getEntity(id);
			if (!(e instanceof KingdomSoldier k)) continue;
			all.add(k);
			if (k.squadId() != null) per.merge(k.squadId(), 1, Integer::sum);
		}
		KingdomState ks = KingdomState.get(lv);
		for (KingdomSoldier k : all) k.discard();
		for (UUID q : side.squads) {
			Squad sq = ks.squad(q);
			if (sq != null) ks.disband(sq, lv);
		}
		return new Seen(faction, side.squadSize(), side.waveSize(), made, new ArrayList<>(per.values()));
	}
}
