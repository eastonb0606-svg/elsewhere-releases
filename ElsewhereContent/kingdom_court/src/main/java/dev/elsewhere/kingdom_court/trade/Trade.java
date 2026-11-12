package dev.elsewhere.kingdom_court.trade;

import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.trade.TradeData.Good;
import dev.elsewhere.kingdom_court.trade.TradeData.Line;
import dev.elsewhere.kingdom_court.trade.TradeData.Mode;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shared plumbing of the three trading cultures (RIVALS-DECISIONS 12): the clock, Standing discounts, weekly limits, taking and giving goods, and the final
 * "complete a purchase" step (item, extras, weekly counter, Standing +1 per trade). The cultures are {@link HrafnholtTrade}, {@link AzmarTrade} and
 * {@link MirewickTrade}; the stock is {@link TradeData}.
 */
public final class Trade {
	private Trade() {}

	public static final long DAY = 24000L, WEEK = DAY * 7;

	/** Dev only: added to the world clock by every rule below, so self-tests can jump days without waiting. Always 0 in play. */
	public static long testOffset;
	/** Dev only: players the world's player list does not know (fake players), so the rules that look a player up by id can find them. */
	public static final java.util.Map<java.util.UUID, ServerPlayer> testPlayers = new java.util.concurrent.ConcurrentHashMap<>();

	public static @org.jspecify.annotations.Nullable ServerPlayer player(net.minecraft.server.MinecraftServer srv, java.util.UUID id) {
		ServerPlayer p = srv.getPlayerList().getPlayer(id);
		return p != null ? p : testPlayers.get(id);
	}

	public enum Result {
		OK, NO_SHOP, NO_SUCH_LINE, WEEKLY_LIMIT, CANT_AFFORD, HOSTILE, CLOSED, TIER_LOCKED, NEEDS_RINGS, COMMISSION_ONLY, NOT_COMMISSION, NO_BARTER, NOT_READY, NO_SLIP, NOT_OWNER,
		BAD_AMOUNT, ALREADY, NOT_OPEN, TOO_LOW, NO_LOT, NOTHING_TO_SELL, LOCKED, NO_GIVER, NOTHING_DUE, NO_ITEM, NOT_TREASURY, WRONG_FACTION
	}

	public static long now(ServerLevel l) {
		return l.getDefaultClockTime() + testOffset;
	}

	public static long day(ServerLevel l) {
		return Math.floorDiv(now(l), DAY);
	}

	/** Tick within the day (0 = dawn, 12,000 = dusk). */
	public static long timeOfDay(ServerLevel l) {
		return Math.floorMod(now(l), DAY);
	}

	public static TradeData.Stock stock(ServerLevel l, Identifier faction) {
		return TradeData.stock(l.getServer(), faction);
	}

	public static Mode mode(ServerLevel l, Identifier faction) {
		return stock(l, faction).mode();
	}

	public static TradeState state(ServerLevel l) {
		return TradeState.get(l.getServer());
	}

	/** True for a player the faction refuses to trade with. */
	public static boolean hostile(ServerPlayer p, Identifier faction) {
		return Standing.bandOf((ServerLevel) p.level(), p.getUUID(), faction) == Standing.Band.HOSTILE;
	}

	public static Standing.Band band(ServerPlayer p, Identifier faction) {
		return Standing.bandOf((ServerLevel) p.level(), p.getUUID(), faction);
	}

	/** Standing price multiplier: Friendly 0.95, Allied 0.85 (the war lane's {@link Standing#shopDiscount}). */
	public static double standingDiscount(ServerPlayer p, Identifier faction) {
		return Shops.discount(p, faction);
	}

	public static int scaled(int price, double mult) {
		return Math.max(1, (int) Math.round(price * mult));
	}

	// ---- weekly limits (per player, per faction, per line) ---------------------------------------------------------------------

	static String wkKey(ServerPlayer p, Identifier faction, String lineKey) {
		return "wk|" + p.getUUID() + "|" + faction + "|" + lineKey;
	}

	public static int usedThisWeek(ServerLevel l, ServerPlayer p, Identifier faction, String lineKey) {
		long v = state(l).getLong(wkKey(p, faction, lineKey), Long.MIN_VALUE);
		return v != Long.MIN_VALUE && Math.floorDiv(v, 1000L) == Math.floorDiv(now(l), WEEK) ? (int) Math.floorMod(v, 1000L) : 0;
	}

	public static void addUsed(ServerLevel l, ServerPlayer p, Identifier faction, String lineKey) {
		state(l).putLong(wkKey(p, faction, lineKey), Math.floorDiv(now(l), WEEK) * 1000 + Math.min(999, usedThisWeek(l, p, faction, lineKey) + 1));
	}

	/** Purchases left this week on {@code line} (-1 = unlimited). */
	public static int remaining(ServerLevel l, ServerPlayer p, Identifier faction, Line line) {
		return line.weeklyLimit() <= 0 ? -1 : Math.max(0, line.weeklyLimit() - usedThisWeek(l, p, faction, line.key()));
	}

	// ---- goods ------------------------------------------------------------------------------------------------------------------

	public static int count(ServerPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	public static boolean take(ServerPlayer p, Item item, int amount) {
		if (amount < 0 || count(p, item) < amount) return false;
		int left = amount;
		for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.is(item)) continue;
			int k = Math.min(left, s.getCount());
			s.shrink(k);
			left -= k;
		}
		return true;
	}

	public static boolean hasGoods(ServerPlayer p, List<Good> goods) {
		for (Good g : goods) {
			ItemStack s = g.stack();
			if (s == null || count(p, s.getItem()) < g.count()) return false;
		}
		return true;
	}

	/** Takes all the goods or none. */
	public static boolean takeGoods(ServerPlayer p, List<Good> goods) {
		if (!hasGoods(p, goods)) return false;
		for (Good g : goods) take(p, g.stack().getItem(), g.count());
		return true;
	}

	public static void give(ServerPlayer p, ItemStack s) {
		Shops.give(p, s);
	}

	/**
	 * The last step of every purchase once payment has been taken: the item and its weekly counter, and Standing +1 for the trade (the war lane caps it at +5 a week).
	 * {@code extra} on a line is part of the price: it is taken before this by {@link #takeExtras}.
	 */
	public static void deliver(ServerPlayer p, Identifier faction, Line line) {
		ServerLevel l = (ServerLevel) p.level();
		ItemStack s = line.stack();
		if (s != null) give(p, s);
		if (line.weeklyLimit() > 0) addUsed(l, p, faction, line.key());
		Standing.onTrade(p, faction);
	}

	public static boolean hasExtras(ServerPlayer p, Line line) {
		return hasGoods(p, line.extra());
	}

	public static boolean takeExtras(ServerPlayer p, Line line) {
		return takeGoods(p, line.extra());
	}

	public static Identifier id(String path) {
		return CourtContent.id(path);
	}
}
