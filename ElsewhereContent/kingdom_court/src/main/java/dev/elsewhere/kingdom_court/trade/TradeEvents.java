package dev.elsewhere.kingdom_court.trade;

import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Wires the trade rules into the game: items, the tick (Mirewick's debt-collectors and auction bell, Azmar's caravan), deeds and caravan bounties on kills, the loan on a debt-collector's
 * kill, claims on join. All of it is server-side and idempotent; {@link CourtContent#init} calls {@link #init}.
 */
public final class TradeEvents {
	private TradeEvents() {}

	private static boolean done;
	public static final String DAY_TAG = "kingdom_caravan_day:";
	/** Live caravan members this session (so the tick can retire them without scanning the world). */
	/** Dev only: extra factions the tick and kill hooks look at (test factions are not realm factions). */
	public static boolean forceNear;
	public static final List<Identifier> testFactions = new ArrayList<>();
	static final Set<UUID> CARAVAN = ConcurrentHashMap.newKeySet();

	public static void init() {
		if (done) return;
		done = true;
		TradeItems.init();
		ServerLifecycleEvents.SERVER_STARTED.register(s -> TradeData.reload());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s, rm, ok) -> TradeData.reload());
		ServerTickEvents.END_SERVER_TICK.register(TradeEvents::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(TradeEvents::afterDeath);
		ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amount) -> {
			if (e instanceof ServerPlayer p) MirewickTrade.onPlayerDeath(p, src);
			return true;
		});
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
			ServerPlayer p = h.getPlayer();
			MirewickTrade.deliverClaims(p);
			if (AzmarTrade.balance((ServerLevel) p.level(), p.getUUID()) > 0) AzmarTrade.updateLetter(p, false);
		});
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (AzmarTrade.isCaravan(e) && !caravanToday(e, level)) e.discard();
			else if (AzmarTrade.isCaravan(e)) CARAVAN.add(e.getUUID());
		});
		CourtEvents.DUEL_ENDED.register((level, p, faction, slot, won) -> {
			if (won && Trade.mode(level, faction) == TradeData.Mode.THING) HrafnholtTrade.addDeed(p, faction, 1, "duel");
		});
	}

	static boolean caravanToday(Entity e, ServerLevel level) {
		long today = Trade.day(level);
		for (String t : e.entityTags()) {
			if (t.startsWith(DAY_TAG)) {
				try {
					return Long.parseLong(t.substring(DAY_TAG.length())) == today;
				} catch (NumberFormatException ignored) {
					return false;
				}
			}
		}
		return false;
	}

	/** Realm factions whose trade mode is {@code mode}. */
	static List<Identifier> factions(ServerLevel l, TradeData.Mode mode) {
		List<Identifier> out = new ArrayList<>();
		for (Identifier f : FactionDefs.realmFactions()) if (Factions.exists(f) && Trade.mode(l, f) == mode) out.add(f);
		for (Identifier f : testFactions) if (!out.contains(f) && Factions.exists(f) && Trade.mode(l, f) == mode) out.add(f);
		return out;
	}

	static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		if (t % 100 != 7) return;
		ServerLevel l = server.overworld();
		if (l == null) return;
		MirewickTrade.tick(l);
		for (Identifier f : factions(l, TradeData.Mode.FLOATING)) {
			MirewickTrade.settle(l, f, MirewickTrade.auctionDay(l));
			MirewickTrade.settle(l, f, MirewickTrade.auctionDay(l) - MirewickTrade.AUCTION_EVERY_DAYS);
		}
		for (Identifier f : factions(l, TradeData.Mode.POSTED)) caravan(server, l, f);
	}

	/** The caravan of {@code faction}: on a caravan day it appears when a player comes within 96 blocks of today's stop; it is retired as soon as the day turns. */
	public static void caravan(MinecraftServer server, ServerLevel l, Identifier f) {
		long today = Trade.day(l);
		for (UUID id : new ArrayList<>(CARAVAN)) {
			Entity e = l.getEntity(id);
			if (e == null) {
				continue;
			}
			if (!e.isAlive() || !caravanToday(e, l)) {
				e.discard();
				CARAVAN.remove(id);
			}
		}
		if (!AzmarTrade.caravanDay(l) || CourtState.isFallen(server, f)) return;
		TradeState ts = Trade.state(l);
		String key = "caravan|" + f;
		if (ts.getLong(key, -1) == today) return;
		BlockPos at = AzmarTrade.caravanSite(l, f);
		if (at == null || (!forceNear && !l.isPositionEntityTicking(at))) return;
		boolean near = false;
		for (ServerPlayer p : l.players()) if (!p.isSpectator() && p.blockPosition().distSqr(at) < 96.0 * 96.0) near = true;
		if (forceNear) near = true;
		if (!near) return;
		BlockPos ground = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		ts.putLong(key, today);
		for (Entity e : AzmarTrade.spawnCaravan(l, f, ground)) {
			e.addTag(DAY_TAG + today);
			CARAVAN.add(e.getUUID());
		}
	}

	public static void afterDeath(LivingEntity dead, net.minecraft.world.damagesource.DamageSource src) {
		if (!(dead.level() instanceof ServerLevel l) || !(src.getEntity() instanceof ServerPlayer p)) return;
		// Azmar: the caravan master pays for the attackers killed near the caravan.
		for (Identifier f : factions(l, TradeData.Mode.POSTED)) AzmarTrade.onKill(l, dead, p, f, l.getRandom());
		// Hrafnholt: a deed is killing an enemy of Hrafnholt near its realm.
		for (Identifier f : factions(l, TradeData.Mode.THING)) {
			if (isEnemyOf(l, dead, f) && MirewickTrade.inRealm(l, dead.blockPosition(), f)) HrafnholtTrade.addDeed(p, f, 1, "kill");
		}
	}

	/** A soldier of a kingdom hostile to {@code f}, or a monster, counts as an enemy of {@code f}. Its own people and the diplomats never do. */
	public static boolean isEnemyOf(ServerLevel l, LivingEntity e, Identifier f) {
		if (e instanceof dev.elsewhere.kingdom_court.npc.DiplomatNpc) return false;
		if (e instanceof KingdomSoldier s) return !s.factionId().equals(f) && Factions.hostile(f, s.factionId());
		return e instanceof Enemy;
	}
}
