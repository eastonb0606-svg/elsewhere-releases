package dev.elsewhere.kingdom_court.trade;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.war.WarApi;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Oligarchy;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.kingdom_court.trade.TradeData.Line;
import dev.elsewhere.kingdom_court.trade.TradeData.Lot;
import dev.elsewhere.kingdom_court.trade.TradeData.Stock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mirewick trades for money above all (RIVALS-DECISIONS 12): a floating market, haggling, loans with debt-collectors and a weekly auction. The currency exchange and the council
 * bribes stay in {@link Shops} and {@link Oligarchy}.
 * <ul>
 * <li><b>Floating prices</b>: each purchase raises that item's price by 5 points up to +50 percent; selling an item to the market lowers it (down to -30). The offset drifts back
 *     10 points per in-game day, and every price is 20 percent higher while Mirewick is at war. Saved per world ({@link TradeState}).</li>
 * <li><b>Haggling</b>: sneak-use a trader. Chance 30 percent + Standing/4, 15 points less at a miser Magnate's trader. Success: 20 percent off at that trader until the next day.
 *     Failure: that trader will not deal with you until the next day.</li>
 * <li><b>Loans</b>: up to 64 Scrip (96 Friendly, 128 Allied) at 25 percent interest, due in 7 days, with a Loan Note. When late: Standing -20 and a debt-collector squad (3 soldiers and a
 *     sellsword), only inside Mirewick's realm and only for a player with an overdue loan. If they kill you your Scrip pays the debt.</li>
 * <li><b>Auction</b>: every 7th day at midday the Auctioneer sells 3 lots over 1 in-game hour. NPC bidders raise the price; right-click with Scrip to bid; the highest bid at the bell wins.</li>
 * </ul>
 */
public final class MirewickTrade {
	private MirewickTrade() {}

	public static final double STEP = 0.05, CAP = 0.50, FLOOR = -0.30, DRIFT_PER_DAY = 0.10, WAR_SURCHARGE = 0.20, HAGGLE_OFF = 0.20;

	// ---- floating prices ----------------------------------------------------------------------------------------------------------

	/** Whether the faction is at war with anyone (the war lane's relation 2). Replaceable for tests. */
	public static BiPredicate<ServerLevel, Identifier> warProvider = (l, f) -> {
		for (Identifier o : dev.elsewhere.kingdom.FactionDefs.realmFactions()) if (!o.equals(f) && WarApi.relation(l.getServer(), f, o) == 2) return true;
		return false;
	};

	public static boolean atWar(ServerLevel l, Identifier faction) {
		return warProvider.test(l, faction);
	}

	static String fk(Identifier faction, Line line) {
		return "fp|" + faction + "|" + line.key();
	}

	/** The price offset of a line right now (-0.30 .. +0.50): the stored offset drifted back toward 0 by 10 points per elapsed in-game day. */
	public static double offset(ServerLevel l, Identifier faction, Line line) {
		TradeState ts = Trade.state(l);
		double o = ts.getDouble(fk(faction, line), 0.0);
		if (o == 0.0) return 0.0;
		double days = (Trade.now(l) - ts.getLong(fk(faction, line) + "|t", Trade.now(l))) / (double) Trade.DAY;
		double drift = DRIFT_PER_DAY * Math.max(0.0, days);
		return o > 0 ? Math.max(0.0, o - drift) : Math.min(0.0, o + drift);
	}

	static void setOffset(ServerLevel l, Identifier faction, Line line, double o) {
		TradeState ts = Trade.state(l);
		o = Math.max(FLOOR, Math.min(CAP, Math.round(o * 1000.0) / 1000.0));
		ts.putDouble(fk(faction, line), o);
		ts.putLong(fk(faction, line) + "|t", Trade.now(l));
	}

	/** Price multiplier of a line before Standing and haggling: (1 + offset) times 1.2 at war. */
	public static double market(ServerLevel l, Identifier faction, Line line) {
		return (1.0 + offset(l, faction, line)) * (atWar(l, faction) ? 1.0 + WAR_SURCHARGE : 1.0);
	}

	public static int price(ServerPlayer p, Identifier faction, Line line, @Nullable String trader) {
		ServerLevel l = (ServerLevel) p.level();
		double m = market(l, faction, line) * Trade.standingDiscount(p, faction) * (trader != null && haggled(l, p, trader) ? 1.0 - HAGGLE_OFF : 1.0);
		return Trade.scaled(line.price(), m);
	}

	public record Quote(Line line, int price, double offset, int remaining) {}

	public static List<Quote> quotes(ServerPlayer p, Identifier faction, @Nullable String trader) {
		ServerLevel l = (ServerLevel) p.level();
		List<Quote> out = new ArrayList<>();
		for (Line line : Trade.stock(l, faction).lines()) {
			if (line.itemOrNull() == null) continue;
			out.add(new Quote(line, price(p, faction, line, trader), offset(l, faction, line), Trade.remaining(l, p, faction, line)));
		}
		return out;
	}

	static @Nullable Line lineAt(Stock st, int index) {
		for (Line x : st.lines()) if (x.index() == index) return x;
		return null;
	}

	/** Buys a line at the current floating price from {@code trader} (an NPC key; null = no trader, no haggle). Each purchase raises that line's price by 5 points. */
	public static Trade.Result buy(ServerPlayer p, Identifier faction, int index, @Nullable String trader) {
		ServerLevel l = (ServerLevel) p.level();
		Line line = lineAt(Trade.stock(l, faction), index);
		if (line == null || line.itemOrNull() == null) return Trade.Result.NO_SUCH_LINE;
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (trader != null && locked(l, p, trader)) return Trade.Result.LOCKED;
		if (Trade.remaining(l, p, faction, line) == 0) return Trade.Result.WEEKLY_LIMIT;
		if (!Trade.hasExtras(p, line)) return Trade.Result.CANT_AFFORD;
		int price = price(p, faction, line, trader);
		if (!Shops.take(p, faction, price)) return Trade.Result.CANT_AFFORD;
		Trade.takeExtras(p, line);
		double o = offset(l, faction, line);
		setOffset(l, faction, line, o + STEP);
		Trade.deliver(p, faction, line);
		return Trade.Result.OK;
	}

	/** What the market pays for one of {@code line}'s item: the line's sell price per item, moved by the line's offset. */
	public static int sellPrice(ServerLevel l, Identifier faction, Line line) {
		return Math.max(1, (int) Math.round(line.sell() / (double) Math.max(1, line.count()) * (1.0 + offset(l, faction, line))));
	}

	/** Sells one of the held item to the market for coin; the item's price falls by 5 points. */
	public static Trade.Result sell(ServerPlayer p, Identifier faction) {
		ServerLevel l = (ServerLevel) p.level();
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) return Trade.Result.NOTHING_TO_SELL;
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		for (Line line : Trade.stock(l, faction).lines()) {
			if (line.itemOrNull() != held.getItem()) continue;
			int pay = sellPrice(l, faction, line);
			held.shrink(1);
			Shops.give(p, faction, pay);
			setOffset(l, faction, line, offset(l, faction, line) - STEP);
			return Trade.Result.OK;
		}
		return Trade.Result.NOTHING_TO_SELL;
	}

	// ---- haggling -----------------------------------------------------------------------------------------------------------------------

	public enum Haggle { SUCCESS, FAIL, LOCKED, ALREADY, HOSTILE }

	/** The percent chance: 30 + Standing/4, minus 15 at a miser's trader, clamped to 0-95. */
	public static int chancePercent(ServerLevel l, ServerPlayer p, Identifier faction, boolean miserTrader) {
		int standing = Standing.get(l, p.getUUID(), faction);
		return Math.max(0, Math.min(95, 30 + standing / 4 - (miserTrader ? 15 : 0)));
	}

	public static boolean isMiser(MinecraftServer server, Identifier faction, @Nullable String slot) {
		Ruler r = slot == null ? null : Ruler.byKey(slot);
		return r != null && Oligarchy.leaning(server, faction, r) == Oligarchy.Leaning.MISER;
	}

	static String hk(String kind, ServerPlayer p, String trader) {
		return kind + "|" + p.getUUID() + "|" + trader;
	}

	public static boolean haggled(ServerLevel l, ServerPlayer p, String trader) {
		return Trade.state(l).getLong(hk("haggle", p, trader), -1) == Trade.day(l);
	}

	public static boolean locked(ServerLevel l, ServerPlayer p, String trader) {
		return Trade.state(l).getLong(hk("hlock", p, trader), -1) == Trade.day(l);
	}

	public static Haggle haggle(ServerPlayer p, Identifier faction, String trader, boolean miserTrader, RandomSource rnd) {
		ServerLevel l = (ServerLevel) p.level();
		if (Trade.hostile(p, faction)) return Haggle.HOSTILE;
		if (locked(l, p, trader)) return Haggle.LOCKED;
		if (haggled(l, p, trader)) return Haggle.ALREADY;
		if (rnd.nextInt(100) < chancePercent(l, p, faction, miserTrader)) {
			Trade.state(l).putLong(hk("haggle", p, trader), Trade.day(l));
			return Haggle.SUCCESS;
		}
		Trade.state(l).putLong(hk("hlock", p, trader), Trade.day(l));
		return Haggle.FAIL;
	}

	// ---- loans -------------------------------------------------------------------------------------------------------------------------

	public static final int LOAN_BASE_LIMIT = 64, LOAN_DAYS = 7, LATE_STANDING = -20, COLLECTOR_SOLDIERS = 3;
	public static final double INTEREST = 0.25;
	public static final String COLLECTOR_TAG = "kingdom_debt_collector";

	public record Loan(UUID player, int principal, int owed, long due, boolean late, String noteId, Identifier faction) {
		String encode() {
			return principal + "\t" + owed + "\t" + due + "\t" + (late ? 1 : 0) + "\t" + noteId + "\t" + faction;
		}

		static @Nullable Loan decode(UUID p, @Nullable String s) {
			if (s == null) return null;
			String[] a = s.split("\t");
			return a.length < 6 ? null : new Loan(p, Integer.parseInt(a[0]), Integer.parseInt(a[1]), Long.parseLong(a[2]), "1".equals(a[3]), a[4], Identifier.parse(a[5]));
		}
	}

	static String lk(UUID u) {
		return "loan|" + u;
	}

	public static @Nullable Loan loanOf(ServerLevel l, UUID u) {
		return Loan.decode(u, Trade.state(l).getString(lk(u)));
	}

	static void save(ServerLevel l, Loan x) {
		Trade.state(l).putString(lk(x.player()), x.encode());
	}

	public static int limit(ServerPlayer p, Identifier faction) {
		return switch (Trade.band(p, faction)) {
			case FRIENDLY -> 96;
			case ALLIED -> 128;
			default -> LOAN_BASE_LIMIT;
		};
	}

	public static boolean overdue(ServerLevel l, Loan x) {
		return Trade.now(l) > x.due();
	}

	/** Borrow {@code amount} Scrip (1 to the limit) at 25 percent interest, due in 7 days; one loan at a time. */
	public static Trade.Result borrow(ServerPlayer p, Identifier faction, int amount) {
		ServerLevel l = (ServerLevel) p.level();
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (loanOf(l, p.getUUID()) != null) return Trade.Result.ALREADY;
		if (amount < 1 || amount > limit(p, faction)) return Trade.Result.BAD_AMOUNT;
		int owed = (int) Math.ceil(amount * (1.0 + INTEREST) - 1e-9);
		long due = Trade.now(l) + LOAN_DAYS * Trade.DAY;
		String id = UUID.randomUUID().toString();
		Loan x = new Loan(p.getUUID(), amount, owed, due, false, id, faction);
		save(l, x);
		Shops.give(p, faction, amount);
		Trade.give(p, note(x));
		return Trade.Result.OK;
	}

	static ItemStack note(Loan x) {
		CompoundTag d = new CompoundTag();
		d.putString("id", x.noteId());
		d.putInt("owed", x.owed());
		d.putLong("due", x.due());
		return TradeItems.withData(TradeItems.LOAN_NOTE, d, List.of(
			Component.literal("Borrowed " + x.principal() + " Scrip, " + x.owed() + " owed"),
			Component.literal("Due day " + Math.floorDiv(x.due(), Trade.DAY) + (x.late() ? " (OVERDUE)" : ""))));
	}

	static void refreshNote(ServerPlayer p, Loan x) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(TradeItems.LOAN_NOTE)) s.set(DataComponents.LORE, new ItemLore(note(x).get(DataComponents.LORE).lines()));
		}
	}

	/** Repays the whole debt (principal plus interest, or what is left of it). */
	public static Trade.Result repay(ServerPlayer p) {
		ServerLevel l = (ServerLevel) p.level();
		Loan x = loanOf(l, p.getUUID());
		if (x == null) return Trade.Result.NOTHING_DUE;
		if (!Shops.take(p, x.faction(), x.owed())) return Trade.Result.CANT_AFFORD;
		clear(l, p, x);
		return Trade.Result.OK;
	}

	static void clear(ServerLevel l, ServerPlayer p, Loan x) {
		Trade.state(l).remove(lk(x.player()));
		if (p != null) while (Trade.take(p, TradeItems.LOAN_NOTE, 1)) { /* every note */ }
		stand(l, x.player());
	}

	// ---- the debt-collectors ----------------------------------------------------------------------------------------------------------

	static final Map<UUID, List<UUID>> COLLECTORS = new HashMap<>();
	static final Map<UUID, Long> COLLECTOR_AT = new HashMap<>();
	public static final int COLLECTOR_COOLDOWN = 2400, COLLECTOR_LIFETIME = 6000;

	static void stand(ServerLevel l, UUID player) {
		List<UUID> ids = COLLECTORS.remove(player);
		COLLECTOR_AT.remove(player);
		if (ids == null) return;
		for (UUID id : ids) {
			Entity e = l.getEntity(id);
			if (e != null) e.discard();
		}
	}

	public static boolean inRealm(ServerLevel l, BlockPos pos, Identifier faction) {
		CourtState st = CourtState.peek(l.getServer(), faction);
		return st != null && st.site != null && Math.hypot(pos.getX() - st.site.getX(), pos.getZ() - st.site.getZ()) < CourtLife.REALM_RADIUS + 64;
	}

	public static boolean isCollector(Entity e) {
		for (String t : e.entityTags()) if (t.startsWith(COLLECTOR_TAG + ":")) return true;
		return false;
	}

	static boolean collectorOf(Entity e, UUID player) {
		return e.entityTags().contains(COLLECTOR_TAG + ":" + player);
	}

	/** Spawns Marrow's squad (3 soldiers and a sellsword) around the player and sets them on him. Caller checks the rules; see {@link #tick}. */
	public static List<KingdomSoldier> spawnCollectors(ServerLevel l, ServerPlayer p, Identifier faction) {
		Vec3 c = p.position();
		BlockPos at = BlockPos.containing(c.x + 9, c.y, c.z + 9);
		List<Identifier> members = new ArrayList<>();
		for (int i = 1; i < COLLECTOR_SOLDIERS; i++) members.add(Trade.id("man_at_arms"));
		members.add(FreeCompanies.BLADE);
		List<KingdomSoldier> out = KingdomApi.spawnSquad(l, at, faction, Squad.Formation.LINE, Trade.id("man_at_arms"), members);
		List<UUID> ids = new ArrayList<>();
		for (KingdomSoldier s : out) {
			s.addTag(COLLECTOR_TAG + ":" + p.getUUID());
			s.setPersistenceRequired();
			s.setTarget(p);
			ids.add(s.getUUID());
		}
		s0(out);
		COLLECTORS.put(p.getUUID(), ids);
		COLLECTOR_AT.put(p.getUUID(), l.getGameTime());
		return out;
	}

	static void s0(List<KingdomSoldier> l) {
		if (!l.isEmpty()) l.get(0).setCustomName(Component.literal("Debt-Collector"));
	}

	/** Every 100 ticks: loans that fell due go late (Standing -20); an overdue player inside the realm is hunted by one squad at a time. */
	public static void tick(ServerLevel l) {
		MinecraftServer srv = l.getServer();
		TradeState ts = Trade.state(l);
		for (String k : new ArrayList<>(ts.strings.keySet())) {
			if (!k.startsWith("loan|")) continue;
			Loan x = Loan.decode(UUID.fromString(k.substring(5)), ts.strings.get(k));
			if (x == null || !overdue(l, x)) continue;
			ServerPlayer p = Trade.player(srv, x.player());
			if (!x.late()) {
				Loan late = new Loan(x.player(), x.principal(), x.owed(), x.due(), true, x.noteId(), x.faction());
				save(l, late);
				Standing.add(l, x.player(), x.faction(), LATE_STANDING);
				x = late;
				if (p != null) {
					refreshNote(p, x);
					p.sendSystemMessage(Component.literal("Your Loan Note is overdue. Marrow's debt-collectors have been told."));
				}
			}
			if (p == null || !p.isAlive()) continue;
			List<UUID> ids = COLLECTORS.get(x.player());
			if (ids != null) {
				boolean any = false;
				for (UUID id : ids) {
					Entity e = l.getEntity(id);
					if (e != null && e.isAlive()) any = true;
				}
				long age = l.getGameTime() - COLLECTOR_AT.getOrDefault(x.player(), 0L);
				if (!any || age > COLLECTOR_LIFETIME || !inRealm(l, p.blockPosition(), x.faction())) {
					stand(l, x.player());
					COLLECTOR_AT.put(x.player(), l.getGameTime() - COLLECTOR_LIFETIME + COLLECTOR_COOLDOWN);
					continue;
				}
				continue;
			}
			if (!inRealm(l, p.blockPosition(), x.faction()) || p.isCreative() || p.isSpectator()) continue;
			if (l.getGameTime() - COLLECTOR_AT.getOrDefault(x.player(), -1_000_000L) < COLLECTOR_LIFETIME) continue;
			spawnCollectors(l, p, x.faction());
		}
	}

	/**
	 * A player died. If a debt-collector of theirs dealt the blow, their Scrip pays the debt (as much as they carry; the rest of the debt stays). Returns the Scrip taken.
	 * Called from the death event before the drops, so the coins never reach the ground.
	 */
	public static int onPlayerDeath(ServerPlayer p, DamageSource src) {
		ServerLevel l = (ServerLevel) p.level();
		Entity killer = src.getEntity();
		Loan x = loanOf(l, p.getUUID());
		if (x == null || killer == null || !collectorOf(killer, p.getUUID())) return 0;
		int have = Shops.count(p, x.faction());
		int pay = Math.min(have, x.owed());
		if (pay > 0) Shops.take(p, x.faction(), pay);
		if (pay >= x.owed()) clear(l, p, x);
		else {
			save(l, new Loan(x.player(), x.principal(), x.owed() - pay, x.due(), x.late(), x.noteId(), x.faction()));
			stand(l, x.player());
		}
		return pay;
	}

	// ---- the weekly auction -------------------------------------------------------------------------------------------------------------

	public static final int AUCTION_EVERY_DAYS = 7, AUCTION_OPEN_TICK = 6000, AUCTION_TICKS = 1000, LOTS = 3;

	public enum Phase { WAITING, OPEN, CLOSED }

	/** The latest auction day at or before today (days divisible by 7). */
	public static long auctionDay(ServerLevel l) {
		long d = Trade.day(l);
		return d - Math.floorMod(d, (long) AUCTION_EVERY_DAYS);
	}

	public static long startOf(long auctionDay) {
		return auctionDay * Trade.DAY + AUCTION_OPEN_TICK;
	}

	public static long endOf(long auctionDay) {
		return startOf(auctionDay) + AUCTION_TICKS;
	}

	public static Phase phase(ServerLevel l) {
		long t = Trade.now(l), ad = auctionDay(l);
		if (t < startOf(ad)) return Phase.WAITING;
		return t < endOf(ad) ? Phase.OPEN : Phase.CLOSED;
	}

	/** In-game days until the next auction day (0 on an auction day). */
	public static int daysToAuction(ServerLevel l) {
		long d = Trade.day(l);
		return (int) Math.floorMod(AUCTION_EVERY_DAYS - Math.floorMod(d, (long) AUCTION_EVERY_DAYS), (long) AUCTION_EVERY_DAYS);
	}

	/** A lot of one auction: the stack, the start price, the NPC bidders' ceiling and how fast they get there (0.55-1.0). */
	public record AuctionLot(int index, ItemStack stack, int start, int max, double pace) {}

	/** The 3 lots of an auction day: picked from the stock's pool by a seed of the day (deterministic), the NPC bidders' pace seeded per lot. */
	public static List<AuctionLot> lots(ServerLevel l, Identifier faction, long auctionDay) {
		List<Lot> pool = new ArrayList<>();
		for (Lot x : Trade.stock(l, faction).auction()) if (x.stack() != null) pool.add(x);
		List<AuctionLot> out = new ArrayList<>();
		if (pool.isEmpty()) return out;
		RandomSource r = RandomSource.create(auctionDay * 7919L + faction.hashCode());
		List<Lot> bag = new ArrayList<>(pool);
		for (int i = 0; i < LOTS; i++) {
			if (bag.isEmpty()) bag.addAll(pool);
			Lot x = bag.remove(r.nextInt(bag.size()));
			out.add(new AuctionLot(i, x.stack(), x.start(), x.max(), 0.55 + 0.45 * r.nextDouble()));
		}
		return out;
	}

	/** What the NPC bidders have reached {@code elapsed} ticks into the hour. */
	public static int npcBid(AuctionLot lot, long elapsed) {
		double f = Math.max(0.0, Math.min(1.0, elapsed / (double) AUCTION_TICKS));
		return lot.start() + (int) Math.floor((lot.max() - lot.start()) * f * lot.pace());
	}

	static String ak(long ad, int lot, String f) {
		return "auc|" + ad + "|" + lot + "|" + f;
	}

	public static int playerBid(ServerLevel l, long ad, int lot) {
		return (int) Trade.state(l).getLong(ak(ad, lot, "bid"), 0);
	}

	public static @Nullable UUID bidder(ServerLevel l, long ad, int lot) {
		String s = Trade.state(l).getString(ak(ad, lot, "who"));
		return s == null ? null : UUID.fromString(s);
	}

	/** The price to beat right now: the higher of the NPC bidders and the best player bid. */
	public static int current(ServerLevel l, Identifier faction, AuctionLot lot) {
		long ad = auctionDay(l);
		return Math.max(npcBid(lot, Trade.now(l) - startOf(ad)), playerBid(l, ad, lot.index()));
	}

	public static int minRaise(int current) {
		return Math.max(1, (int) Math.ceil(current * 0.05 - 1e-9));
	}

	/** True when {@code p} holds the top bid on the lot right now. */
	public static boolean leading(ServerLevel l, Identifier faction, ServerPlayer p, AuctionLot lot) {
		long ad = auctionDay(l);
		UUID w = bidder(l, ad, lot.index());
		return w != null && w.equals(p.getUUID()) && playerBid(l, ad, lot.index()) >= npcBid(lot, Trade.now(l) - startOf(ad));
	}

	/** Bids {@code amount} Scrip on a lot: at least the current price plus 5 percent. The coins are held until the bell; an outbid player gets theirs back at once. */
	public static Trade.Result bid(ServerPlayer p, Identifier faction, int lotIndex, int amount) {
		ServerLevel l = (ServerLevel) p.level();
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (phase(l) != Phase.OPEN) return Trade.Result.NOT_OPEN;
		List<AuctionLot> lots = lots(l, faction, auctionDay(l));
		if (lotIndex < 0 || lotIndex >= lots.size()) return Trade.Result.NO_LOT;
		AuctionLot lot = lots.get(lotIndex);
		int cur = current(l, faction, lot);
		if (amount < cur + minRaise(cur)) return Trade.Result.TOO_LOW;
		long ad = auctionDay(l);
		UUID prev = bidder(l, ad, lotIndex);
		int prevBid = playerBid(l, ad, lotIndex);
		int own = prev != null && prev.equals(p.getUUID()) ? prevBid : 0;
		if (!Shops.take(p, faction, amount - own)) return Trade.Result.CANT_AFFORD;
		if (prev != null && !prev.equals(p.getUUID()) && prevBid > 0) payBack(l.getServer(), prev, faction, prevBid);
		Trade.state(l).putString(ak(ad, lotIndex, "who"), p.getUUID().toString());
		Trade.state(l).putLong(ak(ad, lotIndex, "bid"), amount);
		Trade.state(l).putString(ak(ad, lotIndex, "faction"), faction.toString());
		Standing.onTrade(p, faction);
		return Trade.Result.OK;
	}

	/** Right-click with Scrip: the minimum raise on the cheapest lot the player is not already winning. Returns the lot bid on, or -1. */
	public static int quickBid(ServerPlayer p, Identifier faction, Trade.Result[] result) {
		ServerLevel l = (ServerLevel) p.level();
		result[0] = Trade.Result.NOT_OPEN;
		if (phase(l) != Phase.OPEN) return -1;
		int best = -1, bestPrice = Integer.MAX_VALUE;
		List<AuctionLot> lots = lots(l, faction, auctionDay(l));
		for (AuctionLot lot : lots) {
			if (leading(l, faction, p, lot)) continue;
			int cur = current(l, faction, lot);
			if (cur < bestPrice) {
				bestPrice = cur;
				best = lot.index();
			}
		}
		if (best < 0) {
			result[0] = Trade.Result.ALREADY;
			return -1;
		}
		result[0] = bid(p, faction, best, bestPrice + minRaise(bestPrice));
		return best;
	}

	public static boolean settled(ServerLevel l, long ad, Identifier faction) {
		return Trade.state(l).getLong("auc|" + ad + "|settled|" + faction, 0) == 1;
	}

	/**
	 * The bell: each lot goes to the best player bid if it is at least what the NPC bidders reached, else the bidders keep it and the player's coins come back. Idempotent; runs
	 * from the tick as soon as the hour is over (also for an auction day the server slept through). Returns the lots a player won.
	 */
	public static int settle(ServerLevel l, Identifier faction, long ad) {
		if (settled(l, ad, faction) || Trade.now(l) < endOf(ad)) return 0;
		Trade.state(l).putLong("auc|" + ad + "|settled|" + faction, 1);
		int won = 0;
		for (AuctionLot lot : lots(l, faction, ad)) {
			UUID w = bidder(l, ad, lot.index());
			int bid = playerBid(l, ad, lot.index());
			if (w == null || bid <= 0) continue;
			int npc = npcBid(lot, AUCTION_TICKS);
			if (bid >= npc) {
				won++;
				claimOrGive(l.getServer(), w, "i:" + BuiltInRegistries.ITEM.getKey(lot.stack().getItem()) + ":" + lot.stack().getCount());
			} else {
				payBack(l.getServer(), w, faction, bid);
			}
			Trade.state(l).remove(ak(ad, lot.index(), "who"));
			Trade.state(l).remove(ak(ad, lot.index(), "bid"));
		}
		return won;
	}

	static void payBack(MinecraftServer srv, UUID who, Identifier faction, int coins) {
		claimOrGive(srv, who, "c:" + faction + ":" + coins);
	}

	static void claimOrGive(MinecraftServer srv, UUID who, String entry) {
		TradeState ts = TradeState.get(srv);
		ServerPlayer p = Trade.player(srv, who);
		if (p != null) {
			deliver(p, entry);
			return;
		}
		String key = "claim|" + who, old = ts.getString(key);
		ts.putString(key, old == null || old.isEmpty() ? entry : old + ";" + entry);
	}

	static void deliver(ServerPlayer p, String entry) {
		String[] a = entry.split(":");
		if (a[0].equals("c") && a.length >= 4) Shops.give(p, Identifier.fromNamespaceAndPath(a[1], a[2]), Integer.parseInt(a[3]));
		else if (a[0].equals("i") && a.length >= 4) {
			Identifier id = Identifier.fromNamespaceAndPath(a[1], a[2]);
			if (BuiltInRegistries.ITEM.containsKey(id)) Trade.give(p, new ItemStack(BuiltInRegistries.ITEM.getValue(id), Integer.parseInt(a[3])));
		}
		p.sendSystemMessage(Component.literal(a[0].equals("c") ? "The Counting-House returns your Scrip." : "The Auctioneer sends your lot."));
	}

	/** Hands over what the auction owes a player who was away (called on join and whenever they talk to the Auctioneer). Returns the entries delivered. */
	public static int deliverClaims(ServerPlayer p) {
		ServerLevel l = (ServerLevel) p.level();
		String key = "claim|" + p.getUUID(), s = Trade.state(l).getString(key);
		if (s == null || s.isEmpty()) return 0;
		Trade.state(l).remove(key);
		int n = 0;
		for (String e : s.split(";")) {
			if (e.isEmpty()) continue;
			deliver(p, e);
			n++;
		}
		return n;
	}

	public static boolean isMirewickMode(ServerLevel l, Identifier f) {
		return Trade.mode(l, f) == TradeData.Mode.FLOATING && Factions.exists(f);
	}
}
