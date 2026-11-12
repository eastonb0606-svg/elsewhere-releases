package dev.elsewhere.kingdom_court.standing;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.Overhaul3;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * POLISH P7, server half of the Standing chip: answers a client's {@link StandingNet.Query} ("my crosshair is on this ruler") with one
 * {@link StandingNet.Reply}. The client asks only when its crosshair lands on a ruler and its copy for that kingdom is stale, so this runs a
 * handful of times per castle visit, never per tick. Checks: the entity is a court ruler in the player's level within {@link #RANGE} blocks;
 * at most one answer per kingdom per {@link #MIN_TICKS} ticks per player (anything faster is a misbehaving client and is ignored).
 * Kill switch {@code standing_chip} ({@link Overhaul3#STANDING_CHIP}).
 */
public final class StandingQuery {
	private StandingQuery() {}

	public static final double RANGE = 48.0;
	public static final int MIN_TICKS = 10;
	/** Last answer per player and kingdom (game time). Weak keys: a player that leaves (or respawns into a new object) drops out by itself. */
	private static final Map<ServerPlayer, Map<String, Long>> LAST = new WeakHashMap<>();

	static void handle(ServerPlayer p, int entityId) {
		StandingNet.Reply r = answer(p, entityId);
		if (r != null && ServerPlayNetworking.canSend(p, StandingNet.Reply.TYPE)) ServerPlayNetworking.send(p, r);
	}

	/** The reply for this query, or null when it is refused (switch off, not a ruler, too far, too soon). */
	public static StandingNet.@Nullable Reply answer(ServerPlayer p, int entityId) {
		if (!Overhaul3.STANDING_CHIP) return null;
		Entity e = p.level().getEntity(entityId);
		if (!(e instanceof CourtRuler ruler) || ruler.isRemoved() || ruler.distanceToSqr(p) > RANGE * RANGE) return null;
		Identifier f = Factions.ledgerOf(ruler.factionId());
		ServerLevel l = (ServerLevel) p.level();
		long now = l.getGameTime();
		Map<String, Long> mine = LAST.computeIfAbsent(p, k -> new HashMap<>());
		Long last = mine.get(f.toString());
		if (last != null && now >= last && now - last < MIN_TICKS) return null;
		mine.put(f.toString(), now);
		return reply(p, ruler.getId(), f);
	}

	/** The reply for a kingdom, without the range and rate checks. */
	public static StandingNet.Reply reply(ServerPlayer p, int entityId, Identifier faction) {
		ServerLevel l = (ServerLevel) p.level();
		int v = Standing.get(l, p.getUUID(), faction);
		int flags = 0;
		if (Standing.attacksOnSight(p, faction)) flags |= StandingNet.R_ATTACKS;
		if (Standing.locked(l, p.getUUID(), faction)) flags |= StandingNet.R_OATH;
		FactionDef d = Factions.def(faction);
		if (d != null && d.society().standing().capUntilRulerKilled() && !KingdomState.get(l.getServer().overworld()).isRulerKilled(faction)) flags |= StandingNet.R_CAPPED;
		return new StandingNet.Reply(entityId, StandingNet.clip(faction.toString()), Math.clamp(v, -100, 100), (byte) flags);
	}

	/** Dev only: forget the rate limit for this player (self-tests ask twice in one tick on purpose). */
	public static void resetForTest(ServerPlayer p) {
		LAST.remove(p);
	}
}
