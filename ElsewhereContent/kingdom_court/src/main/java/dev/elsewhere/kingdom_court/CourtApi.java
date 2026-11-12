package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Public court API for the other kingdom lanes (call by reflection if you do not compile against this module):
 * {@code dev.elsewhere.kingdom_court.CourtApi.kingdomFallen(MinecraftServer)} and {@code realmCenter(MinecraftServer)}.
 */
public final class CourtApi {
	private CourtApi() {}

	/** True once all six rulers are dead: conquest, sieges, patrols and hunting squads must stop. */
	public static boolean kingdomFallen(MinecraftServer server) {
		return CourtState.get(server).fallen;
	}

	/** The realm centre (castle courtyard, overworld), or null when this world has no realm. */
	public static @Nullable BlockPos realmCenter(MinecraftServer server) {
		return CourtState.get(server).site;
	}

	public static boolean rulerAlive(MinecraftServer server, String key) {
		Ruler r = Ruler.byKey(key);
		return r != null && CourtState.get(server).alive(r);
	}

	/** The ruler on the throne ("king", then the highest living Lord). */
	public static String crowned(MinecraftServer server) {
		return CourtState.get(server).crowned.key;
	}

	// ---- 2.1 multi-faction: the same calls for any faction's court -------------------------------------------------

	public static boolean kingdomFallen(MinecraftServer server, Identifier faction) {
		return CourtState.isFallen(server, faction);
	}

	public static @Nullable BlockPos realmCenter(MinecraftServer server, Identifier faction) {
		CourtState st = CourtState.peek(server, faction);
		return st == null ? null : st.site;
	}

	public static boolean rulerAlive(MinecraftServer server, Identifier faction, String key) {
		Ruler r = Ruler.byKey(key);
		CourtState st = CourtState.peek(server, faction);
		return r != null && st != null && st.slots().contains(r) && st.alive(r);
	}

	/** The ruler on the throne, or (oligarchy) the current Chair. */
	public static String crowned(MinecraftServer server, Identifier faction) {
		return CourtState.get(server, faction).crowned.key;
	}
}
