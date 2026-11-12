package dev.elsewhere.kingdom;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Server-side hooks for the boss/conquest layer. */
public final class KingdomEvents {
	private KingdomEvents() {}

	@FunctionalInterface
	public interface SoldierDied {
		void onSoldierDied(ServerLevel level, KingdomSoldier soldier, DamageSource source);
	}

	@FunctionalInterface
	public interface LeaderDied {
		/** {@code leader} may be any mob that led the squad (a boss too); {@code routed} members started fleeing. */
		void onLeaderDied(ServerLevel level, Squad squad, LivingEntity leader, int routed);
	}

	@FunctionalInterface
	public interface HornBlown {
		/** A herald blew its horn; {@code reinforcements} soldiers were called (materialized or spawned). */
		void onHorn(ServerLevel level, KingdomSoldier herald, int reinforcements);
	}

	@FunctionalInterface
	public interface HuntStarted {
		/** A hunting squad was sent after {@code player} (aggression); the squad is already spawned. */
		void onHunt(ServerLevel level, net.minecraft.server.level.ServerPlayer player, Squad squad);
	}

	@FunctionalInterface
	public interface FactionClash {
		/** Two squads of hostile factions met with no player near and the fight was resolved as a seeded roll (see {@link FactionConflict}). */
		void onClash(ServerLevel level, FactionConflict.Record record);
	}

	/** 2.1: an abstract (entity-free) clash between two factions' squads was resolved. */
	public static final Event<FactionClash> FACTION_CLASH = EventFactory.createArrayBacked(FactionClash.class,
		ls -> (level, r) -> {
			for (FactionClash l : ls) l.onClash(level, r);
		});

	/** 0.2.0: a herald's horn sounded (after the default reinforcements). */
	public static final Event<HornBlown> HORN_BLOWN = EventFactory.createArrayBacked(HornBlown.class,
		ls -> (level, h, n) -> {
			for (HornBlown l : ls) l.onHorn(level, h, n);
		});

	/** 0.2.0: a hunting squad set out after an aggressive player. */
	public static final Event<HuntStarted> HUNT_STARTED = EventFactory.createArrayBacked(HuntStarted.class,
		ls -> (level, p, q) -> {
			for (HuntStarted l : ls) l.onHunt(level, p, q);
		});

	/** Fired after any soldier dies (after squad bookkeeping). */
	public static final Event<SoldierDied> SOLDIER_DIED = EventFactory.createArrayBacked(SoldierDied.class,
		ls -> (level, s, src) -> {
			for (SoldierDied l : ls) l.onSoldierDied(level, s, src);
		});

	/** Fired when a squad leader dies, after the morale/rout rolls and before a new leader is promoted. */
	public static final Event<LeaderDied> LEADER_DIED = EventFactory.createArrayBacked(LeaderDied.class,
		ls -> (level, q, leader, routed) -> {
			for (LeaderDied l : ls) l.onLeaderDied(level, q, leader, routed);
		});
}
