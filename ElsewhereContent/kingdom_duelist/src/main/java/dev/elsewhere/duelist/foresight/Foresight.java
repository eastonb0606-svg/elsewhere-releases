package dev.elsewhere.duelist.foresight;

import dev.elsewhere.boss.kit.ForesightGear;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * B5 foresight gate for every boss built on the duelist kit (DECISIONS-2.0 {@code bal-boss-ground-markings}): attack
 * hints (wind-up telegraph particles, landing marks, the move name on the boss bar) reach only players who hold or wear
 * {@code #elsewhere:foresight_gear}. Everyone keeps the boss's pose and wind-up sound, which is the readable tell.
 * <ul>
 * <li><b>The server decides.</b> {@link #sees} is {@link ForesightGear#sees} (the shared boss-kit rule, so gear2's active
 * Seer's Hourglass counts from the hotbar too), cached per player. The cache changes only on events: equipment change,
 * join, respawn, and a re-check of the players near a boss when one of its wind-ups starts ({@link #refreshNear}). No
 * per-tick polling.</li>
 * <li><b>Particles:</b> wrap hint drawing in {@link #hint}; inside it, every particle packet (both {@code sendParticles}
 * overloads) goes only to players who see ({@code ForesightParticleMixin}). Effects that are the attack itself stay
 * outside.</li>
 * <li><b>Boss bar:</b> {@link ForesightBossEvent} sends "Name · Move" to players who see and "Name" to the rest.</li>
 * <li><b>The client gets a flag:</b> {@link ForesightPayload} on join and whenever a player's sight changes
 * ({@code ForesightClient} on the client; boss_ui reads it).</li>
 * </ul>
 * Kill switch {@code foresight_gate}: {@code -Delsewhere.overhaul3.foresight_gate=false} shows every hint to everyone again.
 */
public final class Foresight {
	private Foresight() {}

	public static final String KEY = "foresight_gate";
	/** Kill switch, read once. Tests may flip it. */
	public static boolean enabled = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3." + KEY, "true"));
	/** Style insertion on the move-name part of a boss bar title: boss_ui knows the line is a foresight hint. */
	public static final String MARK = "elsewhere:foresight";
	/** Bosses re-check players within this many blocks when a wind-up starts (a hotbar trinket fires no equipment event). */
	public static final double NEAR = 64.0;

	private static final Map<UUID, Boolean> SIGHT = new HashMap<>();
	private static int depth;
	private static boolean inited;
	/** Counters for the self-tests: hint scopes opened, particle packets let through or held back, flags sent, sight changes. */
	public static int scopes, shown, held, flags, changes;

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		try {
			PayloadTypeRegistry.clientboundPlay().register(ForesightPayload.TYPE, ForesightPayload.CODEC);
		} catch (IllegalArgumentException e) {
			// already registered (a second init path in a merged build)
		}
		ServerEntityEvents.EQUIPMENT_CHANGE.register((e, slot, from, to) -> {
			if (e instanceof ServerPlayer p) refresh(p);
		});
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
			SIGHT.remove(h.player.getUUID());
			refresh(h.player);
			sendFlag(h.player, sees(h.player));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> SIGHT.remove(h.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> refresh(newP));
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			SIGHT.clear();
			ForesightBossEvent.clearLive();
			depth = 0;
		});
	}

	/** True if this player gets attack hints: always when the gate is off. Cached; computed on first use. */
	public static boolean sees(ServerPlayer p) {
		if (!enabled) return true;
		Boolean b = SIGHT.get(p.getUUID());
		return b != null ? b : refresh(p);
	}

	/** Re-reads the player's gear. On a change: updates the boss bars they watch and sends the client flag. Returns the new value. */
	public static boolean refresh(ServerPlayer p) {
		boolean now = ForesightGear.sees(p);
		Boolean old = SIGHT.put(p.getUUID(), now);
		if (old != null && old != now) {
			changes++;
			ForesightBossEvent.sightChanged(p);
			sendFlag(p, now);
		}
		return now;
	}

	/** One re-check of the players near a boss, when its wind-up starts (catches a trinket moved into the hotbar). */
	public static void refreshNear(ServerLevel level, Entity boss) {
		for (ServerPlayer p : level.players()) if (p.distanceToSqr(boss) <= NEAR * NEAR) refresh(p);
	}

	/** Runs {@code draw} with every particle it sends limited to players who see. Nests; a no-op wrapper when the gate is off. */
	public static void hint(Runnable draw) {
		if (!enabled) {
			draw.run();
			return;
		}
		scopes++;
		depth++;
		try {
			draw.run();
		} finally {
			depth--;
		}
	}

	/** Called by the particle mixin for each player a particle packet would reach: false holds it back. */
	public static boolean allowParticle(ServerPlayer p) {
		if (depth <= 0) return true;
		if (sees(p)) {
			shown++;
			return true;
		}
		held++;
		return false;
	}

	/** True while inside {@link #hint}. */
	public static boolean hinting() {
		return depth > 0;
	}

	static void sendFlag(ServerPlayer p, boolean sees) {
		flags++;
		try {
			if (ServerPlayNetworking.canSend(p, ForesightPayload.TYPE)) ServerPlayNetworking.send(p, new ForesightPayload(sees));
		} catch (RuntimeException | LinkageError e) {
			// a fake player or a client without the module: the server-side gate still holds
		}
	}

	/** Test hook: forget a player's cached sight. */
	public static void forget(ServerPlayer p) {
		SIGHT.remove(p.getUUID());
	}
}
