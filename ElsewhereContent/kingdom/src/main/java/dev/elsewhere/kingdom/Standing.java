package dev.elsewhere.kingdom;

import dev.elsewhere.kingdom.war.Tuning;
import dev.elsewhere.kingdom.war.WarState;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Player Standing with a faction: per player and per faction, -100..+100, saved in {@link KingdomState}.
 * Bands: Hostile -100..-50, Wary -49..-10, Neutral -9..+29, Friendly +30..+69, Allied +70..+100. The start value comes
 * from the faction file ({@code society.standing.start}; default -60 for factions hostile to players, else 0).
 * <h3>Hostility ({@link #attacksOnSight})</h3>
 * <ul>
 * <li>No recorded Standing: a faction flagged hostile to players attacks on sight (Aldermark exactly as in 2.0, and the lore module's neutral switch still
 * works); otherwise the band of the faction's start value decides (Hostile attacks, Wary attacks only inside its realm, the rest leave you alone).</li>
 * <li>Recorded Standing: Hostile attacks on sight anywhere, Wary only inside the faction's realm or near its castle ({@link RealmLocator}), Neutral and up never. A faction
 * flagged hostile to players keeps attacking below Friendly (so Aldermark can only be calmed once its ruler has been killed, the cap below).</li>
 * </ul>
 * <h3>Moving Standing (RIVALS-DECISIONS 2.1)</h3>
 * Losses: soldier -3, officer -8, Serf-tier -2, Lord -30, ruler -50, doubled while the player is Friendly or better with that faction, or holds a truce
 * ({@link #grantTruce}). Gains: a kill of a soldier of a kingdom at war with F gives F +2 (daily cap +20); a loss with A gives half as gain to every kingdom at war with A
 * (same cap; per faction the larger of the two applies); defending a village +10 ({@link #onVillageDefended}); tribute +1 per coin (cap +15 a week); a trade +1 (cap +5 a week).
 * Per-kingdom multipliers ({@code standing_loss}, {@code standing_gain} in {@link Tuning}: Hrafnholt 1.5/0.75, Mirewick 0.5/1.25). Oathbreaker: hurting an Allied
 * kingdom's people sets you to Hostile with it and -10 with every other kingdom, and nothing can raise it for 3 days. Standing drifts 1 point a day towards the start value.
 * Aldermark cannot reach Friendly until its ruler has been killed once ({@link KingdomApi#markRulerKilled}).
 * <p>Soldier kills are handled here automatically (via {@link KingdomEvents#SOLDIER_DIED}); Serf, Lord and ruler kills, tribute, trade and village defence are reported by
 * the module that sees them ({@link #onKilled}, {@link #onTribute}, {@link #onTrade}, {@link #onVillageDefended}).
 */
public final class Standing {
	private Standing() {}

	public enum Band {
		HOSTILE, WARY, NEUTRAL, FRIENDLY, ALLIED
	}

	/** What a player killed or hurt; the base Standing loss is {@link #loss}. */
	public enum Victim {
		SOLDIER(3), OFFICER(8), SERF(2), LORD(30), RULER(50);

		public final int loss;

		Victim(int loss) {
			this.loss = loss;
		}
	}

	/** Court registers this: is the position inside the faction's realm or near its castle (the Wary band only attacks there)? */
	public interface RealmLocator {
		boolean inRealm(ServerLevel level, BlockPos pos, Identifier faction);
	}

	private static RealmLocator locator = (l, p, f) -> false;
	public static final int DAILY_GAIN_CAP = 20, WEEKLY_TRIBUTE_CAP = 15, WEEKLY_TRADE_CAP = 5;
	public static final long DAY = 24000L, WEEK = 7 * DAY, OATH_TICKS = 3 * DAY;

	public static void setRealmLocator(RealmLocator l) {
		locator = l == null ? (lv, p, f) -> false : l;
	}

	public static Band band(int v) {
		if (v <= -50) return Band.HOSTILE;
		if (v <= -10) return Band.WARY;
		if (v <= 29) return Band.NEUTRAL;
		if (v <= 69) return Band.FRIENDLY;
		return Band.ALLIED;
	}

	/** The start value for this faction. */
	public static int start(Identifier faction) {
		FactionDef d = Factions.def(faction);
		if (d != null) {
			if (d.society().standing().start().isEmpty() && !d.hostileToPlayers()) {
				if (faction.getPath().equals("hrafnholt")) return -30;
				if (faction.getPath().equals("mirewick")) return 10;
			}
			return d.startStanding();
		}
		return Factions.get(faction).hostileToPlayers() ? -60 : 0;
	}

	static @Nullable Integer explicit(ServerLevel any, UUID player, Identifier faction) {
		Map<UUID, Integer> m = KingdomState.get(any.getServer().overworld()).standingLedger(faction, false);
		return m == null ? null : m.get(player);
	}

	public static int get(ServerLevel any, UUID player, Identifier faction) {
		Integer v = explicit(any, player, faction);
		return v != null ? v : start(faction);
	}

	public static Band bandOf(ServerLevel any, UUID player, Identifier faction) {
		return band(get(any, player, faction));
	}

	/** Sets Standing directly (clamped; Aldermark's Friendly cap applies). No caps, locks or multipliers: this is the admin/court setter. */
	public static void set(ServerLevel any, UUID player, Identifier faction, int value) {
		KingdomState st = KingdomState.get(any.getServer().overworld());
		int v = Math.clamp(value, -100, 100);
		FactionDef d = Factions.def(faction);
		if (d != null && d.society().standing().capUntilRulerKilled() && !st.isRulerKilled(faction)) v = Math.min(v, 29); // RIVALS-DECISIONS 2.1: cannot reach Friendly until the ruler has been killed once
		st.standingLedger(faction, true).put(player, v);
		st.setDirty();
		if (v >= 70) WarState.get(any).data.anyAllied = true;
	}

	/** Adds a delta directly (see {@link #set}); positive deltas ignore the daily caps but respect an Oathbreaker lock. */
	public static void add(ServerLevel any, UUID player, Identifier faction, int delta) {
		if (delta > 0 && locked(any, player, faction)) return;
		set(any, player, faction, get(any, player, faction) + delta);
	}

	/** True while an Oathbreaker lock keeps this Standing from rising. */
	public static boolean locked(ServerLevel any, UUID player, Identifier faction) {
		WarState.Extra e = WarState.get(any).extra(player, faction, false);
		return e != null && e.oathUntil > any.getServer().overworld().getGameTime();
	}

	/** True when soldiers of {@code faction} attack this player on sight because of Standing (or the faction's default stance when no Standing is recorded). */
	public static boolean attacksOnSight(Player p, Identifier faction) {
		boolean flag = Factions.get(faction).hostileToPlayers();
		if (!(p.level() instanceof ServerLevel sl)) return flag;
		Integer v = explicit(sl, p.getUUID(), faction);
		Band b = band(v != null ? v : start(faction));
		if (v == null && flag) return true;
		if (flag && b.ordinal() < Band.FRIENDLY.ordinal() && v != null) return true;
		if (b == Band.HOSTILE) return true;
		if (b == Band.WARY) return locator.inRealm(sl, p.blockPosition(), Factions.ledgerOf(faction));
		return false;
	}

	/** Hunting squads of a rival come only at Hostile, and only after the player killed an officer, a Lord or the ruler (Aldermark keeps its 2.0 aggression rule). */
	public static boolean huntAllowed(ServerLevel level, ServerPlayer p, Identifier faction) {
		if (Factions.ALDERMARK.equals(faction)) return true;
		if (band(get(level, p.getUUID(), faction)) != Band.HOSTILE) return false;
		WarState.Extra e = WarState.get(level).extra(p.getUUID(), faction, false);
		return e != null && e.officerKilled;
	}

	// ---- moving Standing -------------------------------------------------------------------------------------------

	static Tuning tuning(Identifier faction) {
		return Tuning.of(faction, Factions.def(faction));
	}

	static boolean isKingdom(Identifier f) {
		return Factions.exists(f) && Factions.def(f) != null;
	}

	/** Remaining room under the daily gain cap for this player and faction (resets each in-game day). */
	static int dailyRoom(ServerLevel any, UUID player, Identifier faction) {
		long day = any.getServer().overworld().getGameTime() / DAY;
		WarState.Extra e = WarState.get(any).extra(player, faction, true);
		if (e.gainDay != day) {
			e.gainDay = day;
			e.gainToday = 0;
		}
		return Math.max(0, DAILY_GAIN_CAP - e.gainToday);
	}

	/** A capped gain (kills and enemy-of-my-enemy): returns the Standing actually added. */
	static int gainCapped(ServerLevel any, UUID player, Identifier faction, int amount) {
		if (amount <= 0 || locked(any, player, faction)) return 0;
		int room = Math.min(amount, dailyRoom(any, player, faction));
		if (room <= 0) return 0;
		int before = get(any, player, faction);
		set(any, player, faction, before + room);
		int real = get(any, player, faction) - before;
		WarState.get(any).extra(player, faction, true).gainToday += Math.max(0, real);
		WarState.get(any).setDirty();
		return real;
	}

	/**
	 * A player killed or hurt someone of {@code victimFaction}: applies the loss (with that kingdom's multiplier, doubled for Friendly+ or a truce), the Oathbreaker
	 * rule, the enemy-of-my-enemy gains and the kill gain for kingdoms at war with the victim's. Returns the Standing lost with the victim's faction.
	 */
	public static int onKilled(ServerPlayer p, Identifier victimFaction, Victim kind) {
		ServerLevel l = p.level();
		Identifier vf = Factions.ledgerOf(victimFaction);
		if (p.isCreative() || p.isSpectator() || !isKingdom(vf)) return 0;
		WarState ws = WarState.get(l);
		long now = l.getServer().overworld().getGameTime();
		int before = get(l, p.getUUID(), vf);
		WarState.Extra ex = ws.extra(p.getUUID(), vf, true);
		double mult = tuning(vf).standingLoss;
		if (band(before).ordinal() >= Band.FRIENDLY.ordinal() || ex.truceUntil > now) mult *= 2;
		int loss = (int) Math.max(1, Math.round(kind.loss * mult));
		if (band(before) == Band.ALLIED) {
			// Oathbreaker: straight to Hostile with them, -10 with everyone else, no rise for 3 days
			set(l, p.getUUID(), vf, -60);
			ex.oathUntil = now + OATH_TICKS;
			for (Factions.Faction f : Factions.all()) {
				if (f.id.equals(vf) || f.parent() != null || !isKingdom(f.id)) continue;
				set(l, p.getUUID(), f.id, get(l, p.getUUID(), f.id) - 10);
			}
			loss = before + 60;
		} else {
			set(l, p.getUUID(), vf, before - loss);
		}
		if (kind.ordinal() >= Victim.OFFICER.ordinal() && kind != Victim.SERF) ex.officerKilled = true;
		boolean fighter = kind == Victim.SOLDIER || kind == Victim.OFFICER;
		for (Factions.Faction f : Factions.all()) {
			if (f.id.equals(vf) || f.parent() != null || !isKingdom(f.id) || !Factions.hostile(f.id, vf) || KingdomState.get(l.getServer().overworld()).isFallen(f.id)) continue;
			double gm = tuning(f.id).standingGain;
			int eome = (int) Math.round(loss / 2.0 * gm);
			int kill = fighter ? (int) Math.round(2 * gm) : 0;
			gainCapped(l, p.getUUID(), f.id, Math.max(eome, kill));
		}
		ws.setDirty();
		return loss;
	}

	/** The player helped defend a village of {@code faction} in a siege: +10 (times the kingdom's gain multiplier). */
	public static void onVillageDefended(ServerPlayer p, Identifier faction) {
		Identifier f = Factions.ledgerOf(faction);
		if (!isKingdom(f) || p.isCreative()) return;
		gainCapped(p.level(), p.getUUID(), f, (int) Math.round(10 * tuning(f).standingGain));
	}

	/** Tribute in the faction's own currency: +1 per coin, at most +15 a week. Returns the Standing gained. */
	public static int onTribute(ServerPlayer p, Identifier faction, int coins) {
		ServerLevel l = p.level();
		faction = Factions.ledgerOf(faction);
		if (locked(l, p.getUUID(), faction) || coins <= 0) return 0;
		WarState ws = WarState.get(l);
		WarState.Extra e = ws.extra(p.getUUID(), faction, true);
		long wk = l.getServer().overworld().getGameTime() / WEEK;
		if (e.tributeWeek != wk) {
			e.tributeWeek = wk;
			e.tributeThisWeek = 0;
		}
		int gain = Math.min(Math.max(1, (int) Math.round(coins * tuning(faction).standingGain)), WEEKLY_TRIBUTE_CAP - e.tributeThisWeek);
		if (gain <= 0) return 0;
		int before = get(l, p.getUUID(), faction);
		set(l, p.getUUID(), faction, before + gain);
		int real = get(l, p.getUUID(), faction) - before;
		e.tributeThisWeek += Math.max(0, real);
		ws.setDirty();
		return real;
	}

	/** A trade at the faction's shop: +1, at most +5 a week. Returns the Standing gained. */
	public static int onTrade(ServerPlayer p, Identifier faction) {
		ServerLevel l = p.level();
		faction = Factions.ledgerOf(faction);
		if (locked(l, p.getUUID(), faction)) return 0;
		WarState ws = WarState.get(l);
		WarState.Extra e = ws.extra(p.getUUID(), faction, true);
		long wk = l.getServer().overworld().getGameTime() / WEEK;
		if (e.tradeWeek != wk) {
			e.tradeWeek = wk;
			e.tradeThisWeek = 0;
		}
		if (e.tradeThisWeek >= WEEKLY_TRADE_CAP) return 0;
		int before = get(l, p.getUUID(), faction);
		set(l, p.getUUID(), faction, before + Math.max(1, (int) Math.round(tuning(faction).standingGain)));
		int real = get(l, p.getUUID(), faction) - before;
		e.tradeThisWeek += Math.max(0, real);
		ws.setDirty();
		return real;
	}

	/** A truce between the player and a faction until {@code ticks} from now (attacking during it costs double). */
	public static void grantTruce(ServerPlayer p, Identifier faction, long ticks) {
		WarState ws = WarState.get(p.level());
		ws.extra(p.getUUID(), faction, true).truceUntil = p.level().getServer().overworld().getGameTime() + ticks;
		ws.setDirty();
	}

	/** Shop discount for this player at the faction: 0.05 Friendly, 0.15 Allied (a fraction off the price). */
	public static double shopDiscount(ServerLevel l, UUID player, Identifier faction) {
		return switch (bandOf(l, player, faction)) {
			case FRIENDLY -> 0.05;
			case ALLIED -> 0.15;
			default -> 0.0;
		};
	}

	/** The soldier-kill hook (registered by {@link KingdomContent}). */
	static void onSoldierDied(ServerLevel level, KingdomSoldier s, DamageSource src) {
		if (!(src.getEntity() instanceof ServerPlayer p)) return;
		onKilled(p, s.factionId(), s.unit().officer() ? Victim.OFFICER : Victim.SOLDIER);
	}

	/** Daily drift of every recorded Standing one point towards its start value; entries that reach the start are dropped. */
	public static void driftAll(ServerLevel overworld) {
		KingdomState st = KingdomState.get(overworld);
		boolean any = false;
		for (Identifier f : java.util.List.copyOf(st.standingFactions())) {
			Map<UUID, Integer> m = st.standingLedger(f, false);
			if (m == null) continue;
			int target = start(f);
			for (var it = m.entrySet().iterator(); it.hasNext();) {
				var e = it.next();
				int v = e.getValue();
				if (v == target) {
					it.remove();
					any = true;
					continue;
				}
				e.setValue(v < target ? v + 1 : v - 1);
				any = true;
			}
		}
		if (any) st.setDirty();
	}
}
