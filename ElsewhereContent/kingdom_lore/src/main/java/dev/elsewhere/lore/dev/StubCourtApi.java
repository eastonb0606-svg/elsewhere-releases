package dev.elsewhere.lore.dev;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/** Self-test stand-in for dev.elsewhere.kingdom_court.CourtApi (same static signatures). */
public final class StubCourtApi {
	private StubCourtApi() {}

	public static BlockPos centre;
	public static final Map<Identifier, BlockPos> centres = new HashMap<>();
	public static final Set<Identifier> fallen = new HashSet<>();

	public static BlockPos realmCenter(MinecraftServer server) {
		return centre;
	}

	public static BlockPos realmCenter(MinecraftServer server, Identifier faction) {
		return centres.get(faction);
	}

	public static boolean kingdomFallen(MinecraftServer server, Identifier faction) {
		return fallen.contains(faction);
	}
}
