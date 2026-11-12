package dev.elsewhere.kingdom_court.trade;

import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtApi;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.trade.TradeData.Good;
import dev.elsewhere.kingdom_court.trade.TradeData.Line;
import dev.elsewhere.kingdom_court.trade.TradeData.Stock;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Hrafnholt trades by custom and honour (RIVALS-DECISIONS 12): the Thing-day market, barter beside every price, Arm-Rings given for deeds, no haggling, and a ring in the purse
 * is worth 10 percent off.
 * <ul>
 * <li><b>Thing</b>: every 3rd in-game day, dawn to dusk, the Lawspeaker's market has the full stock. On other days only the Smith's stall (lines tiered {@code smith}) is open.</li>
 * <li><b>Barter</b>: each coin price has goods prices (iron ingots, leather, raw cod or salmon, wheat) at fixed values ({@link TradeData#defaultGoods}), or the line's own {@code barter}.</li>
 * <li><b>Rings</b>: top-tier lines ({@code tier: top}) cost {@code rings} Arm-Rings and nothing else. 5 deeds earn a ring, given by the Jarl, or by the senior living champion if the Jarl is dead.</li>
 * <li><b>The Smith</b> cuts a ring into 8 coins and repairs the held tool.</li>
 * </ul>
 */
public final class HrafnholtTrade {
	private HrafnholtTrade() {}

	public static final int THING_EVERY_DAYS = 3, THING_OPEN_TICKS = 12000, DEEDS_PER_RING = 5, DAILY_DEED_CAP = 10, RING_CUTS_INTO = 8;
	public static final double RING_DISCOUNT = 0.9;

	// ---- the Thing ----------------------------------------------------------------------------------------------------------

	public static boolean isThingDay(ServerLevel l) {
		return Math.floorMod(Trade.day(l), (long) THING_EVERY_DAYS) == 0;
	}

	/** Dawn (tick 0) to dusk (tick 12,000) of a Thing-day. */
	public static boolean thingOpen(ServerLevel l) {
		return isThingDay(l) && Trade.timeOfDay(l) < THING_OPEN_TICKS;
	}

	/** In-game days until the next Thing opens (0 when it is open now or opens later today). */
	public static int daysToThing(ServerLevel l) {
		if (isThingDay(l) && Trade.timeOfDay(l) < THING_OPEN_TICKS) return 0;
		long d = Trade.day(l);
		return (int) (THING_EVERY_DAYS - Math.floorMod(d, (long) THING_EVERY_DAYS));
	}

	// ---- quotes -------------------------------------------------------------------------------------------------------------

	public static boolean hasRing(ServerPlayer p) {
		return Trade.count(p, TradeItems.ARM_RING) > 0;
	}

	/** The coin price this player pays: Standing discount times 10 percent off for a ring in the pack. No haggling. */
	public static int coinPrice(ServerPlayer p, Identifier faction, Line line) {
		return Trade.scaled(line.price(), Trade.standingDiscount(p, faction) * (hasRing(p) ? RING_DISCOUNT : 1.0));
	}

	public static int ringPrice(Line line) {
		return line.rings() > 0 ? line.rings() : Math.max(1, line.price() / 50);
	}

	/** The goods prices beside the coin price: the line's own list scaled by the discounts, else every barter good at its fixed value. */
	public static List<Good> barterOptions(ServerPlayer p, Identifier faction, Stock stock, Line line) {
		double mult = Trade.standingDiscount(p, faction) * (hasRing(p) ? RING_DISCOUNT : 1.0);
		List<Good> out = new ArrayList<>();
		if (!line.barter().isEmpty()) {
			for (Good g : line.barter()) out.add(new Good(g.item(), Math.max(1, (int) Math.ceil(g.count() * mult - 1e-9))));
			return out;
		}
		int coins = Trade.scaled(line.price(), mult);
		for (var e : stock.goods().entrySet()) out.add(new Good(e.getKey(), Math.max(1, (int) Math.ceil(coins * 10.0 / e.getValue() - 1e-9))));
		return out;
	}

	public record Quote(Line line, int coins, List<Good> barter, int rings, boolean open, int remaining) {}

	/** The stock as this player sees it: {@code smithStall} true lists only the Smith's lines (always open); false is the Thing (open on Thing-days only). */
	public static List<Quote> quotes(ServerPlayer p, Identifier faction, boolean smithStall) {
		ServerLevel l = (ServerLevel) p.level();
		Stock st = Trade.stock(l, faction);
		boolean open = smithStall || thingOpen(l);
		List<Quote> out = new ArrayList<>();
		for (Line line : st.lines()) {
			if (line.itemOrNull() == null) continue;
			boolean smith = "smith".equals(line.tier());
			if (smithStall && !smith) continue;
			boolean top = line.top();
			out.add(new Quote(line, top ? 0 : coinPrice(p, faction, line), top ? List.of() : barterOptions(p, faction, st, line), top ? ringPrice(line) : 0, open, Trade.remaining(l, p, faction, line)));
		}
		return out;
	}

	static Line lineAt(Stock st, int index) {
		for (Line x : st.lines()) if (x.index() == index) return x;
		return null;
	}

	/** Common checks; returns the line or null with {@code why[0]} set. */
	static @Nullable Line check(ServerPlayer p, Identifier faction, int index, boolean smithStall, Trade.Result[] why) {
		ServerLevel l = (ServerLevel) p.level();
		Stock st = Trade.stock(l, faction);
		Line line = lineAt(st, index);
		if (line == null || line.itemOrNull() == null) {
			why[0] = Trade.Result.NO_SUCH_LINE;
			return null;
		}
		if (Trade.hostile(p, faction)) {
			why[0] = Trade.Result.HOSTILE;
			return null;
		}
		if (smithStall ? !"smith".equals(line.tier()) : !thingOpen(l)) {
			why[0] = Trade.Result.CLOSED;
			return null;
		}
		if (Trade.remaining(l, p, faction, line) == 0) {
			why[0] = Trade.Result.WEEKLY_LIMIT;
			return null;
		}
		return line;
	}

	/** Buy for coin. Top-tier lines refuse coin ({@code NEEDS_RINGS}). */
	public static Trade.Result buy(ServerPlayer p, Identifier faction, int index, boolean smithStall) {
		Trade.Result[] why = {Trade.Result.OK};
		Line line = check(p, faction, index, smithStall, why);
		if (line == null) return why[0];
		if (line.top()) return Trade.Result.NEEDS_RINGS;
		if (!Trade.hasExtras(p, line)) return Trade.Result.CANT_AFFORD;
		int price = coinPrice(p, faction, line);
		if (Shops.count(p, faction) < price) return Trade.Result.CANT_AFFORD;
		Shops.take(p, faction, price);
		Trade.takeExtras(p, line);
		Trade.deliver(p, faction, line);
		return Trade.Result.OK;
	}

	/** Buy with goods instead of coin ({@code good} null = the first option the player can pay). */
	public static Trade.Result buyBarter(ServerPlayer p, Identifier faction, int index, @Nullable Identifier good, boolean smithStall) {
		Trade.Result[] why = {Trade.Result.OK};
		Line line = check(p, faction, index, smithStall, why);
		if (line == null) return why[0];
		if (line.top()) return Trade.Result.NEEDS_RINGS;
		if (!Trade.hasExtras(p, line)) return Trade.Result.CANT_AFFORD;
		ServerLevel l = (ServerLevel) p.level();
		Good pick = null;
		for (Good g : barterOptions(p, faction, Trade.stock(l, faction), line)) {
			if (good != null && !good.equals(g.item())) continue;
			if (Trade.hasGoods(p, List.of(g))) {
				pick = g;
				break;
			}
		}
		if (pick == null) return good == null ? Trade.Result.CANT_AFFORD : Trade.Result.NO_BARTER;
		Trade.takeGoods(p, List.of(pick));
		Trade.takeExtras(p, line);
		Trade.deliver(p, faction, line);
		return Trade.Result.OK;
	}

	/** Buy a top-tier line with Arm-Rings only. */
	public static Trade.Result buyRings(ServerPlayer p, Identifier faction, int index) {
		Trade.Result[] why = {Trade.Result.OK};
		Line line = check(p, faction, index, false, why);
		if (line == null) return why[0];
		if (!line.top()) return Trade.Result.NOT_COMMISSION;
		if (!Trade.hasExtras(p, line)) return Trade.Result.CANT_AFFORD;
		int rings = ringPrice(line);
		if (Trade.count(p, TradeItems.ARM_RING) < rings) return Trade.Result.NEEDS_RINGS;
		Trade.take(p, TradeItems.ARM_RING, rings);
		Trade.takeExtras(p, line);
		Trade.deliver(p, faction, line);
		return Trade.Result.OK;
	}

	// ---- deeds and ring-giving ------------------------------------------------------------------------------------------------

	static String dk(java.util.UUID u) {
		return "deeds|" + u;
	}

	public static int deeds(ServerLevel l, java.util.UUID u) {
		return (int) Trade.state(l).getLong(dk(u), 0);
	}

	public static int ringsGiven(ServerLevel l, java.util.UUID u) {
		return (int) Trade.state(l).getLong("rings|" + u, 0);
	}

	/** Rings the player has earned and not yet been given. */
	public static int pendingRings(ServerLevel l, java.util.UUID u) {
		return Math.max(0, deeds(l, u) / DEEDS_PER_RING - ringsGiven(l, u));
	}

	/** Records {@code n} deeds (a Hostile player earns none; at most {@link #DAILY_DEED_CAP} a day). Returns the deeds counted. */
	public static int addDeed(ServerPlayer p, Identifier faction, int n, String why) {
		ServerLevel l = (ServerLevel) p.level();
		if (n <= 0 || Trade.hostile(p, faction)) return 0;
		TradeState ts = Trade.state(l);
		String dayKey = "deedday|" + p.getUUID();
		long day = Trade.day(l);
		long used = ts.getLong(dayKey, -1) / 1000 == day ? ts.getLong(dayKey, -1) % 1000 : 0;
		int ok = (int) Math.min(n, DAILY_DEED_CAP - used);
		if (ok <= 0) return 0;
		ts.putLong(dayKey, day * 1000 + used + ok);
		ts.putLong(dk(p.getUUID()), deeds(l, p.getUUID()) + ok);
		return ok;
	}

	/**
	 * Who hands out rings: the Jarl (slot {@code king}) while he lives, else the highest-ranking living champion. Null when every leader is dead. Returns the slot key.
	 */
	public static @Nullable String giver(ServerLevel l, Identifier faction) {
		var server = l.getServer();
		if (CourtApi.rulerAlive(server, faction, "king")) return "king";
		CourtState st = CourtState.peek(server, faction);
		if (st == null) return null;
		for (Ruler r : st.slots()) if (!"king".equals(r.key) && CourtApi.rulerAlive(server, faction, r.key)) return r.key;
		return null;
	}

	/** The giver hands over every ring the player has earned. */
	public static Trade.Result claimRings(ServerPlayer p, Identifier faction) {
		ServerLevel l = (ServerLevel) p.level();
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (giver(l, faction) == null) return Trade.Result.NO_GIVER;
		int n = pendingRings(l, p.getUUID());
		if (n <= 0) return Trade.Result.NOTHING_DUE;
		Trade.state(l).putLong("rings|" + p.getUUID(), ringsGiven(l, p.getUUID()) + n);
		Trade.give(p, new ItemStack(TradeItems.ARM_RING, n));
		return Trade.Result.OK;
	}

	// ---- the Smith ------------------------------------------------------------------------------------------------------------

	/** A ring cut into {@link #RING_CUTS_INTO} coins of the faction's currency. */
	public static Trade.Result cutRing(ServerPlayer p, Identifier faction) {
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (!Trade.take(p, TradeItems.ARM_RING, 1)) return Trade.Result.NO_ITEM;
		Shops.give(p, faction, RING_CUTS_INTO);
		return Trade.Result.OK;
	}

	public static int repairCost(ServerPlayer p, Identifier faction, ItemStack s) {
		if (s.isEmpty() || !s.isDamaged()) return 0;
		return Trade.scaled((int) Math.ceil(6.0 * s.getDamageValue() / Math.max(1, s.getMaxDamage())), Trade.standingDiscount(p, faction) * (hasRing(p) ? RING_DISCOUNT : 1.0));
	}

	/** Repairs the item in the main hand for coin (up to 6 for a wrecked tool). */
	public static Trade.Result repair(ServerPlayer p, Identifier faction) {
		ItemStack s = p.getMainHandItem();
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (s.isEmpty() || !s.isDamaged()) return Trade.Result.NOTHING_DUE;
		int cost = repairCost(p, faction, s);
		if (!Shops.take(p, faction, cost)) return Trade.Result.CANT_AFFORD;
		s.setDamageValue(0);
		Standing.onTrade(p, faction);
		return Trade.Result.OK;
	}

	public static Item ring() {
		return TradeItems.ARM_RING;
	}
}
