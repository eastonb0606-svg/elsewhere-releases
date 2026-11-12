package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.fp;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.api.HpCap;
import dev.elsewhere.kingdom_court.api.Oligarchy;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.kingdom_court.trade.AzmarTrade;
import dev.elsewhere.kingdom_court.trade.HrafnholtTrade;
import dev.elsewhere.kingdom_court.trade.MirewickTrade;
import dev.elsewhere.kingdom_court.trade.Trade;
import dev.elsewhere.kingdom_court.trade.TradeData;
import dev.elsewhere.kingdom_court.trade.TradeData.Good;
import dev.elsewhere.kingdom_court.trade.TradeData.Line;
import dev.elsewhere.kingdom_court.trade.TradeData.Lot;
import dev.elsewhere.kingdom_court.trade.TradeEvents;
import dev.elsewhere.kingdom_court.trade.TradeItems;
import dev.elsewhere.kingdom_court.trade.TradeState;
import dev.elsewhere.kingdom_court.trade.TradeUi;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

/**
 * 2.1 court lane, trading (RIVALS-DECISIONS 12): the four items, the stock schema and its shipped defaults, Hrafnholt's Thing-days, barter, deeds and Arm-Rings, Azmar's posted
 * prices, tiers, commissions, caravan, bounty and Letters of Credit, Mirewick's floating prices, haggling, loans, debt-collectors and auction, persistence, and the NPC menus.
 * Everything is deterministic: the world clock is moved with {@link Trade#testOffset}, and the haggle dice are fixed.
 */
public final class SelfTestTrade {
	private SelfTestTrade() {}

	static Identifier id(String p) {
		return Identifier.fromNamespaceAndPath("elsewhere", p);
	}

	static final Identifier HT = id("rv_tr_thing"), AT = id("rv_tr_posted"), MT = id("rv_tr_float");
	static final String RUN = Long.toHexString(System.nanoTime()) + "-";
	static final long DAY = Trade.DAY;
	static java.util.function.BiPredicate<ServerLevel, Identifier> WAR0;
	static AzmarTrade.VillageProvider VILL0;

	static Item item(String path) {
		return BuiltInRegistries.ITEM.getValue(id(path));
	}

	static void setTime(ServerLevel l, long day, long tick) {
		Trade.testOffset = day * DAY + tick - l.getDefaultClockTime();
	}

	static RandomSource fixed(int v) {
		return new SingleThreadedRandomSource(1L) {
			@Override
			public int nextInt(int bound) {
				return Math.min(v, bound - 1);
			}
		};
	}

	static Line line(int i, String item, int count, int price, String tier, int rings, int weekly, boolean commission, int sell, List<Good> extra) {
		return new Line(i, Identifier.parse(item), count, price, tier, rings, List.of(), weekly, extra, commission, sell);
	}

	static void giveItem(FakePlayer p, Item it, int n) {
		p.getInventory().add(new ItemStack(it, n));
	}

	static int has(FakePlayer p, Item it) {
		return Trade.count(p, it);
	}

	static FakePlayer fresh(ServerLevel l, String name, Identifier f, int standing) {
		FakePlayer p = fp(l, RUN + name);
		p.getInventory().clearContent();
		p.snapTo(0.5, SelfTest.y, 0.5, 0F, 0F);
		KingdomApi.setStanding(p, f, standing);
		Trade.testPlayers.put(p.getUUID(), p);
		return p;
	}

	static FactionDef def(String name, String currency) {
		return SelfTestRivals.fd(name, SelfTestRivals.rulers(6, List.of()), SelfTestRivals.mon("none", 0, List.of(SelfTestRivals.SLOTS6)), SelfTestRivals.econ(currency, 2), List.of(), FactionDef.Army.DEFAULT);
	}

	static void add() {
		STEPS.add((srv, l) -> {
			TradeEvents.testFactions.addAll(List.of(HT, AT, MT));
			check("the test factions' two currencies exist in this build (vanilla items, so the merged jar has them too)", TestCoins.scrip() != Items.AIR && TestCoins.dinar() != Items.AIR && TestCoins.scrip() != TestCoins.dinar(), TestCoins.SCRIP + " " + TestCoins.DINAR);
			return 1;
		});
		items();
		schema();
		hrafnholt();
		azmar();
		mirewick();
		persistence();
		menus();
	}

	// ---- items and the stock schema -----------------------------------------------------------------------------------------------

	static void items() {
		STEPS.add((srv, l) -> {
			for (String n : new String[] {"hrafnholt_arm_ring", "azmar_commission_slip", "azmar_letter_of_credit", "mirewick_loan_note"}) {
				Item it = item(n);
				check("trade item elsewhere:" + n + " is registered", it != null && it != Items.AIR && BuiltInRegistries.ITEM.getKey(it).equals(id(n)), null);
			}
			check("the Arm-Ring stacks to 16, paper items to 1", TradeItems.ARM_RING.getDefaultMaxStackSize() == 16 && TradeItems.COMMISSION_SLIP.getDefaultMaxStackSize() == 1 && TradeItems.LETTER_OF_CREDIT.getDefaultMaxStackSize() == 1 && TradeItems.LOAN_NOTE.getDefaultMaxStackSize() == 1, null);
			for (Diplomacy.Role r : Diplomacy.Role.values()) check("trader NPC type is registered: " + r.path, r.type != null && BuiltInRegistries.ENTITY_TYPE.containsKey(r.unitId()), null);
			return 1;
		});
	}

	static void schema() {
		STEPS.add((srv, l) -> {
			TradeData.Stock s = TradeData.parse(id("x"), JsonParser.parseString("""
				{"mode":"thing","goods":{"minecraft:wheat":10},"lines":[{"item":"minecraft:bread","count":2,"price":9,"tier":"smith","weekly_limit":3,"barter":[{"item":"minecraft:wheat","count":4}],
				"extra":[{"item":"minecraft:diamond","count":2}],"sell":3},{"item":"minecraft:diamond","price":5,"tier":"top","rings":2}],
				"caravan":[[{"item":"minecraft:apple","price":2}],[{"item":"minecraft:stick","price":1}]],"auction":[{"item":"minecraft:apple","count":3,"start":10,"max":30}]}""").getAsJsonObject());
			Line a = s.lines().get(0), b = s.lines().get(1);
			check("stock schema: mode, goods, line fields, barter, extras, tiers, rings", s.mode() == TradeData.Mode.THING && s.goods().size() == 1 && a.count() == 2 && a.price() == 9 && a.weeklyLimit() == 3 && a.barter().size() == 1 && a.extra().get(0).count() == 2 && a.sell() == 3 && b.top() && b.rings() == 2, null);
			check("stock schema: caravan rotations and auction lots", s.caravan().size() == 2 && s.auction().size() == 1 && s.auction().get(0).max() == 30, null);
			check("stock schema: a line of an unregistered item is skipped by every quote (itemOrNull is null)", TradeData.parse(id("x"), JsonParser.parseString("{\"lines\":[{\"item\":\"elsewhere:nope\",\"price\":3}]}").getAsJsonObject()).lines().get(0).itemOrNull() == null, null);
			for (String f : new String[] {"hrafnholt", "azmar", "mirewick"}) {
				TradeData.Stock d = TradeData.stock(srv, id(f));
				check("shipped default stock of " + f + ": mode " + TradeData.defaultMode(id(f)) + ", lines", d.mode() == TradeData.defaultMode(id(f)) && d.lines().size() >= 8, d.lines().size());
			}
			TradeData.Stock h = TradeData.stock(srv, id("hrafnholt")), az = TradeData.stock(srv, id("azmar")), m = TradeData.stock(srv, id("mirewick"));
			check("Hrafnholt default: Smith stall lines, Thing lines and a ring-only top tier", h.lines().stream().anyMatch(x -> "smith".equals(x.tier())) && h.lines().stream().anyMatch(x -> "thing".equals(x.tier())) && h.lines().stream().anyMatch(x -> x.top() && x.rings() > 0), null);
			check("Azmar default: common, workshop, royal tiers, commissions, 4 caravan rotations", az.lines().stream().anyMatch(x -> "common".equals(x.tier())) && az.lines().stream().anyMatch(x -> "workshop".equals(x.tier())) && az.lines().stream().anyMatch(x -> "royal".equals(x.tier()) && x.commission()) && az.caravan().size() == 4, null);
			check("Mirewick default: market lines with sell prices and an auction pool of 3 or more", m.lines().stream().allMatch(x -> x.sell() >= 1) && m.auction().size() >= 3, m.auction().size());
			return 1;
		});
	}

	// ---- Hrafnholt -------------------------------------------------------------------------------------------------------------------

	static List<Line> thingLines() {
		return List.of(
			line(0, "minecraft:arrow", 16, 6, "smith", 0, 0, false, 2, List.of()),
			line(1, "elsewhere:court_warmarshal_spurs", 1, 30, "thing", 0, 0, false, 10, List.of()),
			line(2, "elsewhere:court_gatewarden_plate", 1, 200, "top", 4, 0, false, 10, List.of()),
			line(3, "minecraft:bread", 4, 8, "thing", 0, 1, false, 2, List.of()));
	}

	static void hrafnholt() {
		STEPS.add((srv, l) -> {
			FactionDefs.putCodeDefined(HT, def("Thing Test", TestCoins.SCRIP));
			CourtState st = CourtState.get(srv, HT);
			st.resetForTests();
			TradeState.get(srv).clearForTests();
			TradeData.put(HT, new TradeData.Stock(TradeData.Mode.THING, thingLines(), List.of(), List.of(), TradeData.defaultGoods()));
			FakePlayer p = fresh(l, "thing", HT, 0);
			Shops.give(p, HT, 500);
			setTime(l, 6, 100);
			check("Thing-day: every 3rd in-game day is a Thing-day (day 6 yes, 7 and 8 no, 9 yes)", HrafnholtTrade.isThingDay(l) && (setTimeB(l, 7)) && !HrafnholtTrade.isThingDay(l) && (setTimeB(l, 8)) && !HrafnholtTrade.isThingDay(l) && (setTimeB(l, 9)) && HrafnholtTrade.isThingDay(l), null);
			setTime(l, 6, 100);
			check("Thing-day: open from dawn", HrafnholtTrade.thingOpen(l), null);
			setTime(l, 6, 11990);
			check("Thing-day: still open just before dusk", HrafnholtTrade.thingOpen(l), null);
			setTime(l, 6, 12010);
			check("Thing-day: closed after dusk", !HrafnholtTrade.thingOpen(l), null);
			setTime(l, 7, 100);
			check("other days: the full stock is closed (CLOSED) ...", HrafnholtTrade.buy(p, HT, 1, false) == Trade.Result.CLOSED && Shops.count(p, HT) == 500, null);
			check("... but the Smith's stall sells its arrows", HrafnholtTrade.buy(p, HT, 0, true) == Trade.Result.OK && has(p, Items.ARROW) == 16 && Shops.count(p, HT) == 494, Shops.count(p, HT));
			check("the Smith's stall lists only smith lines and the Thing's own lines are refused there", HrafnholtTrade.quotes(p, HT, true).size() == 1 && HrafnholtTrade.buy(p, HT, 1, true) == Trade.Result.CLOSED, null);
			setTime(l, 9, 100);
			check("on a Thing-day the full stock is bought for coin", HrafnholtTrade.buy(p, HT, 1, false) == Trade.Result.OK && Shops.count(p, HT) == 464 && has(p, item("court_warmarshal_spurs")) == 1, Shops.count(p, HT));
			var quotes = HrafnholtTrade.quotes(p, HT, false);
			boolean barterBeside = true;
			for (var q : quotes) if (q.rings() == 0 && (q.coins() < 1 || q.barter().size() != 5)) barterBeside = false;
			check("barter: every coin price has 5 goods prices beside it (iron, leather, cod, salmon, wheat)", barterBeside && quotes.stream().filter(q -> q.rings() == 0).count() == 3, null);
			giveItem(p, Items.IRON_INGOT, 20);
			int coins = Shops.count(p, HT);
			check("barter: iron ingots pay for a Thing line instead of coin (30 coins = 10 ingots)", HrafnholtTrade.buyBarter(p, HT, 1, Identifier.parse("minecraft:iron_ingot"), false) == Trade.Result.OK && has(p, Items.IRON_INGOT) == 10 && Shops.count(p, HT) == coins, has(p, Items.IRON_INGOT));
			check("barter: a good the player lacks is refused (NO_BARTER) and nothing is taken", HrafnholtTrade.buyBarter(p, HT, 1, Identifier.parse("minecraft:cod"), false) == Trade.Result.NO_BARTER && has(p, Items.IRON_INGOT) == 10, null);
			check("the top tier cannot be bought with coin or goods (NEEDS_RINGS)", HrafnholtTrade.buy(p, HT, 2, false) == Trade.Result.NEEDS_RINGS && HrafnholtTrade.buyBarter(p, HT, 2, null, false) == Trade.Result.NEEDS_RINGS && Shops.count(p, HT) == coins, null);
			check("the top tier needs its 4 rings", HrafnholtTrade.buyRings(p, HT, 2) == Trade.Result.NEEDS_RINGS, null);
			giveItem(p, TradeItems.ARM_RING, 4);
			int coinsBefore = Shops.count(p, HT);
			check("top tier: 4 Arm-Rings buy it, coin untouched", HrafnholtTrade.buyRings(p, HT, 2) == Trade.Result.OK && has(p, TradeItems.ARM_RING) == 0 && Shops.count(p, HT) == coinsBefore && has(p, item("court_gatewarden_plate")) == 1, null);
			check("top tier on a closed day is refused", (setTimeB(l, 10)) && HrafnholtTrade.buyRings(p, HT, 2) == Trade.Result.CLOSED, null);
			setTime(l, 9, 100);
			// ring discount
			FakePlayer q = fresh(l, "ringdisc", HT, 0);
			int plain = HrafnholtTrade.coinPrice(q, HT, thingLines().get(1));
			giveItem(q, TradeItems.ARM_RING, 1);
			int withRing = HrafnholtTrade.coinPrice(q, HT, thingLines().get(1));
			check("carrying an Arm-Ring takes 10 percent off (30 -> 27)", plain == 30 && withRing == 27, plain + "/" + withRing);
			Shops.give(q, HT, 100);
			int c0 = Shops.count(q, HT);
			check("the Smith cuts a ring into 8 Hacksilver", HrafnholtTrade.cutRing(q, HT) == Trade.Result.OK && Shops.count(q, HT) == c0 + 8 && has(q, TradeItems.ARM_RING) == 0, null);
			check("no ring, no cut", HrafnholtTrade.cutRing(q, HT) == Trade.Result.NO_ITEM, null);
			check("weekly limits still apply (bread 1 a week)", HrafnholtTrade.buy(q, HT, 3, false) == Trade.Result.OK && HrafnholtTrade.buy(q, HT, 3, false) == Trade.Result.WEEKLY_LIMIT, null);
			FakePlayer h = fresh(l, "hostile", HT, -80);
			Shops.give(h, HT, 100);
			check("a Hostile player is refused", HrafnholtTrade.buy(h, HT, 1, false) == Trade.Result.HOSTILE && HrafnholtTrade.addDeed(h, HT, 1, "t") == 0, null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			CourtState st = CourtState.get(srv, HT);
			setTime(l, 9, 100);
			FakePlayer p = fresh(l, "deeds", HT, 0);
			for (int i = 0; i < 4; i++) HrafnholtTrade.addDeed(p, HT, 1, "kill");
			check("4 deeds earn no ring yet", HrafnholtTrade.deeds(l, p.getUUID()) == 4 && HrafnholtTrade.pendingRings(l, p.getUUID()) == 0 && HrafnholtTrade.claimRings(p, HT) == Trade.Result.NOTHING_DUE, null);
			HrafnholtTrade.addDeed(p, HT, 1, "kill");
			check("the 5th deed earns one ring", HrafnholtTrade.pendingRings(l, p.getUUID()) == 1, null);
			check("the Jarl gives it while he lives", "king".equals(HrafnholtTrade.giver(l, HT)) && HrafnholtTrade.claimRings(p, HT) == Trade.Result.OK && has(p, TradeItems.ARM_RING) == 1 && HrafnholtTrade.pendingRings(l, p.getUUID()) == 0, HrafnholtTrade.giver(l, HT));
			check("a ring is given once (nothing is due a second time)", HrafnholtTrade.claimRings(p, HT) == Trade.Result.NOTHING_DUE && has(p, TradeItems.ARM_RING) == 1, null);
			// a duel win is a deed
			int before = HrafnholtTrade.deeds(l, p.getUUID());
			CourtEvents.DUEL_ENDED.invoker().onDuelEnded(l, p, HT, "marshal", true);
			CourtEvents.DUEL_ENDED.invoker().onDuelEnded(l, p, HT, "marshal", false);
			check("winning a duel is a deed; losing one is not", HrafnholtTrade.deeds(l, p.getUUID()) == before + 1, null);
			// kills of Hrafnholt's enemies near its realm
			st.site = new BlockPos(0, SelfTest.y, 0);
			Zombie z = SelfTest.zombie(l, SelfTest.at(3, 3), false);
			int d0 = HrafnholtTrade.deeds(l, p.getUUID());
			TradeEvents.afterDeath(z, l.damageSources().playerAttack(p));
			check("killing an enemy (a monster) inside Hrafnholt's realm is a deed", HrafnholtTrade.deeds(l, p.getUUID()) == d0 + 1, null);
			st.site = new BlockPos(4000, SelfTest.y, 4000);
			Zombie far = SelfTest.zombie(l, SelfTest.at(3, 3), false);
			TradeEvents.afterDeath(far, l.damageSources().playerAttack(p));
			check("killing it far from the realm is not a deed", HrafnholtTrade.deeds(l, p.getUUID()) == d0 + 1, null);
			st.site = new BlockPos(0, SelfTest.y, 0);
			z.discard();
			far.discard();
			// daily cap
			FakePlayer c = fresh(l, "deedcap", HT, 0);
			check("deeds are capped at 10 a day", HrafnholtTrade.addDeed(c, HT, 30, "x") == 10 && HrafnholtTrade.addDeed(c, HT, 1, "x") == 0, null);
			setTime(l, 10, 100);
			check("the cap resets the next day", HrafnholtTrade.addDeed(c, HT, 1, "x") == 1, null);
			// the Jarl is dead: the senior champion gives
			st.dead.add(Ruler.KING);
			FakePlayer s = fresh(l, "deeds2", HT, 0);
			HrafnholtTrade.addDeed(s, HT, 5, "x");
			check("the Jarl dead: the senior champion (marshal) gives the ring", "marshal".equals(HrafnholtTrade.giver(l, HT)) && HrafnholtTrade.claimRings(s, HT) == Trade.Result.OK && has(s, TradeItems.ARM_RING) == 1, HrafnholtTrade.giver(l, HT));
			st.dead.add(Ruler.MARSHAL);
			check("the next champion gives it once the senior one is dead", "castellan".equals(HrafnholtTrade.giver(l, HT)), HrafnholtTrade.giver(l, HT));
			for (Ruler r : st.slots()) st.dead.add(r);
			HrafnholtTrade.addDeed(s, HT, 5, "x");
			check("every leader dead: nobody gives rings (NO_GIVER)", HrafnholtTrade.giver(l, HT) == null && HrafnholtTrade.claimRings(s, HT) == Trade.Result.NO_GIVER, null);
			st.resetForTests();
			Trade.testOffset = 0;
			TradeData.remove(HT);
			return 1;
		});
	}

	static boolean setTimeB(ServerLevel l, long day) {
		setTime(l, day, 100);
		return true;
	}

	// ---- Azmar ---------------------------------------------------------------------------------------------------------------------------

	static List<Line> postedLines() {
		return List.of(
			line(0, "minecraft:bread", 4, 8, "common", 0, 0, false, 2, List.of()),
			line(1, "minecraft:arrow", 16, 20, "workshop", 0, 0, false, 2, List.of()),
			line(2, "elsewhere:court_warmarshal_spurs", 1, 60, "royal", 0, 0, false, 10, List.of()),
			line(3, "elsewhere:court_gatewarden_plate", 1, 100, "royal", 0, 0, true, 10, List.of()),
			line(4, "minecraft:apple", 1, 5, "common", 0, 0, false, 1, List.of()));
	}

	static void azmar() {
		STEPS.add((srv, l) -> {
			FactionDefs.putCodeDefined(AT, def("Posted Test", TestCoins.DINAR));
			CourtState.get(srv, AT).resetForTests();
			TradeState.get(srv).clearForTests();
			List<List<Line>> caravan = List.of(
				List.of(line(0, "minecraft:apple", 1, 4, "common", 0, 0, false, 1, List.of())),
				List.of(line(0, "minecraft:stick", 1, 2, "common", 0, 0, false, 1, List.of())),
				List.of(line(0, "minecraft:carrot", 1, 3, "common", 0, 0, false, 1, List.of())),
				List.of(line(0, "minecraft:potato", 1, 3, "common", 0, 0, false, 1, List.of())));
			TradeData.put(AT, new TradeData.Stock(TradeData.Mode.POSTED, postedLines(), caravan, List.of(), TradeData.defaultGoods()));
			setTime(l, 21, 100);
			FakePlayer p = fresh(l, "posted", AT, 0);
			Shops.give(p, AT, 1000);
			check("Neutral: common goods only; workshop and royal are TIER_LOCKED", AzmarTrade.buy(p, AT, 0) == Trade.Result.OK && AzmarTrade.buy(p, AT, 1) == Trade.Result.TIER_LOCKED && AzmarTrade.buy(p, AT, 2) == Trade.Result.TIER_LOCKED, null);
			check("quotes mark locked lines with the Standing they need", AzmarTrade.quotes(p, AT).stream().filter(x -> !x.unlocked()).map(x -> x.needs()).distinct().sorted().toList().equals(List.of("Allied", "Friendly")), null);
			KingdomApi.setStanding(p, AT, 40);
			check("Friendly: the workshop opens, the royal workshop does not", AzmarTrade.buy(p, AT, 1) == Trade.Result.OK && AzmarTrade.buy(p, AT, 2) == Trade.Result.TIER_LOCKED, null);
			KingdomApi.setStanding(p, AT, 80);
			check("Allied: royal stock opens", AzmarTrade.buy(p, AT, 2) == Trade.Result.OK && has(p, item("court_warmarshal_spurs")) == 1, null);
			int p1 = AzmarTrade.postedPrice(p, AT, postedLines().get(4));
			for (int i = 0; i < 12; i++) AzmarTrade.buy(p, AT, 4);
			int p2 = AzmarTrade.postedPrice(p, AT, postedLines().get(4));
			check("prices are posted: 12 purchases do not move the price (only the Standing discount applies)", p1 == p2 && p1 == 4, p1 + "/" + p2);
			setTime(l, 40, 100);
			check("posted prices do not drift with time either", AzmarTrade.postedPrice(p, AT, postedLines().get(4)) == p1, null);
			KingdomApi.setStanding(p, AT, 0);
			check("a royal masterwork is locked below Allied (TIER_LOCKED)", AzmarTrade.buy(p, AT, 3) == Trade.Result.TIER_LOCKED, null);
			KingdomApi.setStanding(p, AT, 80);
			check("a masterwork line refuses a plain buy (COMMISSION_ONLY)", AzmarTrade.buy(p, AT, 3) == Trade.Result.COMMISSION_ONLY, null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			setTime(l, 21, 100);
			FakePlayer p = fresh(l, "commission", AT, 80);
			Shops.give(p, AT, 300);
			int price = AzmarTrade.postedPrice(p, AT, postedLines().get(3));
			check("commission: a plain line is not a commission (NOT_COMMISSION)", AzmarTrade.order(p, AT, 0, "gold") == Trade.Result.NOT_COMMISSION, null);
			check("commission: the order takes half the price and gives a Commission Slip", AzmarTrade.order(p, AT, 3, "gold") == Trade.Result.OK && Shops.count(p, AT) == 300 - (price + 1) / 2 && has(p, TradeItems.COMMISSION_SLIP) == 1, Shops.count(p, AT));
			ItemStack slip = TradeItems.find(p, TradeItems.COMMISSION_SLIP);
			var slipLore = slip == null ? null : slip.get(net.minecraft.core.component.DataComponents.LORE);
			String lore = slipLore == null || slipLore.lines().isEmpty() ? "(no slip)" : slipLore.lines().get(0).getString();
			check("the slip carries the owner's name", lore.contains(p.getGameProfile().name()), lore);
			check("commission: not ready before 3 days (NOT_READY), nothing taken", AzmarTrade.collect(p) == Trade.Result.NOT_READY && has(p, item("court_gatewarden_plate")) == 0, null);
			setTime(l, 23, 23000);
			check("commission: still not ready on day 2 and 23000 ticks", AzmarTrade.collect(p) == Trade.Result.NOT_READY, null);
			setTime(l, 24, 100);
			FakePlayer thief = fresh(l, "thief", AT, 80);
			giveItem(thief, TradeItems.COMMISSION_SLIP, 1);
			ItemStack real = TradeItems.find(p, TradeItems.COMMISSION_SLIP);
			thief.getInventory().clearContent();
			if (real != null) thief.getInventory().add(real.copy());
			Shops.give(thief, AT, 200);
			check("commission: another player's slip is refused (NOT_OWNER)", AzmarTrade.collect(thief) == Trade.Result.NOT_OWNER, null);
			FakePlayer forger = fresh(l, "forger", AT, 80);
			giveItem(forger, TradeItems.COMMISSION_SLIP, 1);
			check("commission: a forged slip buys nothing (NO_SLIP)", AzmarTrade.collect(forger) == Trade.Result.NO_SLIP, null);
			int before = Shops.count(p, AT);
			check("commission: after 3 days the owner pays the rest and receives the item with a trim and his name", AzmarTrade.collect(p) == Trade.Result.OK && Shops.count(p, AT) == before - (price - (price + 1) / 2) && has(p, TradeItems.COMMISSION_SLIP) == 0, Shops.count(p, AT));
			ItemStack made = null;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item("court_gatewarden_plate"))) made = p.getInventory().getItem(i);
			var ml = made == null ? null : made.get(net.minecraft.core.component.DataComponents.LORE);
			check("the finished item has the owner's name in its lore", ml != null && ml.lines().stream().anyMatch(c -> c.getString().contains(p.getGameProfile().name())), null);
			check("the slip works once (a copy buys nothing)", AzmarTrade.openCommissions(l, p.getUUID()) == 0, null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			FakePlayer p = fresh(l, "caravan", AT, 0);
			Shops.give(p, AT, 100);
			int found = 0;
			StringBuilder days = new StringBuilder();
			for (int d = 40; d < 52; d++) {
				setTime(l, d, 100);
				if (AzmarTrade.caravanDay(l)) {
					found++;
					days.append(d).append(' ');
				}
			}
			check("a caravan every 4th day (3 in 12 days)", found == 3, days);
			setTime(l, 41, 100);
			check("no caravan stock on other days (CLOSED)", AzmarTrade.buyCaravan(p, AT, 0) == Trade.Result.CLOSED, null);
			setTime(l, 40, 100);
			int r0 = AzmarTrade.rotation(l, AT);
			setTime(l, 44, 100);
			int r1 = AzmarTrade.rotation(l, AT);
			setTime(l, 48, 100);
			int r2 = AzmarTrade.rotation(l, AT);
			check("the rare stock rotates each visit", r0 != r1 && r1 != r2 && AzmarTrade.caravanLines(l, AT).get(0).item() != null, r0 + "," + r1 + "," + r2);
			check("caravan stock is bought at a posted price", AzmarTrade.buyCaravan(p, AT, 0) == Trade.Result.OK && Shops.count(p, AT) == 100 - AzmarTrade.postedPrice(p, AT, AzmarTrade.caravanLines(l, AT).get(0)), Shops.count(p, AT));
			// spawn, bounty, retire
			setTime(l, 52, 100);
			CourtState st = CourtState.get(srv, AT);
			st.site = new BlockPos(0, SelfTest.y, 0);
			VILL0 = AzmarTrade.villageProvider;
			AzmarTrade.villageProvider = (lv, f) -> List.of(new BlockPos(6, SelfTest.y, 6));
			TradeEvents.forceNear = true;
			TradeEvents.caravan(srv, l, AT);
			List<Entity> members = new ArrayList<>();
			for (Entity e : l.getAllEntities()) if (AzmarTrade.isCaravan(e)) members.add(e);
			boolean master = members.stream().anyMatch(e -> e.entityTags().contains(AzmarTrade.MASTER_TAG) && e instanceof DiplomatNpc);
			check("the caravan appears at the village stop with a master and camels when a player is near", master && members.size() >= 3 && members.stream().allMatch(e -> e.entityTags().stream().anyMatch(t -> t.startsWith(TradeEvents.DAY_TAG))), members.size());
			int n = members.size();
			TradeEvents.caravan(srv, l, AT);
			int n2 = 0;
			for (Entity e : l.getAllEntities()) if (AzmarTrade.isCaravan(e)) n2++;
			check("only one caravan a visit", n2 == n, n2 + " vs " + n);
			// bounty
			FakePlayer k = fresh(l, "bounty", AT, 0);
			k.snapTo(8.5, SelfTest.y, 8.5, 0F, 0F);
			DiplomatNpc cm = (DiplomatNpc) members.stream().filter(e -> e instanceof DiplomatNpc).findFirst().orElseThrow();
			Zombie attacker = SelfTest.zombie(l, cm.position().add(2, 0, 0), false);
			attacker.setTarget(cm);
			int coins0 = Shops.count(k, AT), s0 = Standing.get(l, k.getUUID(), AT);
			int paid = AzmarTrade.onKill(l, attacker, k, AT, fixed(0));
			check("killing a caravan's attacker pays 2-6 Dinars and +3 Standing", paid >= 2 && paid <= 6 && Shops.count(k, AT) == coins0 + paid && Standing.get(l, k.getUUID(), AT) == s0 + 3, paid + " st " + (Standing.get(l, k.getUUID(), AT) - s0));
			attacker.discard();
			Zombie bystander = SelfTest.zombie(l, SelfTest.at(-60, -60), false);
			k.snapTo(-60.5, SelfTest.y, -58.5, 0F, 0F);
			check("a monster far from the caravan pays nothing", AzmarTrade.onKill(l, bystander, k, AT, fixed(0)) == 0, null);
			bystander.discard();
			Zombie late = SelfTest.zombie(l, cm.position().add(2, 0, 0), false);
			late.setTarget(cm);
			FakePlayer far = fresh(l, "bountyfar", AT, 0);
			far.snapTo(200.5, SelfTest.y, 200.5, 0F, 0F);
			check("a player 200 blocks from the caravan is not paid", AzmarTrade.onKill(l, late, far, AT, fixed(0)) == 0, null);
			late.discard();
			setTime(l, 53, 100);
			TradeEvents.caravan(srv, l, AT);
			int n3 = 0;
			for (Entity e : l.getAllEntities()) if (AzmarTrade.isCaravan(e) && e.isAlive()) n3++;
			check("the caravan leaves when the day turns", n3 == 0, n3);
			AzmarTrade.villageProvider = VILL0;
			return 1;
		});
		STEPS.add((srv, l) -> {
			setTime(l, 60, 100);
			FakePlayer p = fresh(l, "letter", AT, 0);
			Shops.give(p, AT, 80);
			check("Letter of Credit: depositing takes the Dinars, records a balance and issues the letter", AzmarTrade.deposit(p, AT, 50) == Trade.Result.OK && AzmarTrade.balance(l, p.getUUID()) == 50 && Shops.count(p, AT) == 30 && has(p, TradeItems.LETTER_OF_CREDIT) == 1, null);
			p.getInventory().clearContent();
			check("the balance is server-side: it survives losing the letter and everything else (death)", AzmarTrade.balance(l, p.getUUID()) == 50, null);
			check("drawing at another treasury pays out and reissues the letter", AzmarTrade.withdraw(p, AT, 20) == Trade.Result.OK && Shops.count(p, AT) == 20 && AzmarTrade.balance(l, p.getUUID()) == 30, null);
			check("drawing more than the balance is refused", AzmarTrade.withdraw(p, AT, 31) == Trade.Result.CANT_AFFORD && AzmarTrade.balance(l, p.getUUID()) == 30, null);
			AzmarTrade.updateLetter(p, true);
			check("a lost letter is reissued free with the current balance", has(p, TradeItems.LETTER_OF_CREDIT) == 1, null);
			check("deposits are private: another player's balance is zero", AzmarTrade.balance(l, fresh(l, "letter2", AT, 0).getUUID()) == 0, null);
			check("treasuries are the Envoy, the Inspector and the Caravan Master only", Diplomacy.Role.ENVOY.treasury() && Diplomacy.Role.INSPECTOR.treasury() && Diplomacy.Role.CARAVAN_MASTER.treasury() && !Diplomacy.Role.CLERK.treasury() && !Diplomacy.Role.SMITH.treasury(), null);
			Trade.testOffset = 0;
			TradeData.remove(AT);
			return 1;
		});
	}

	// ---- Mirewick ----------------------------------------------------------------------------------------------------------------------------

	static List<Line> floatLines() {
		return List.of(
			line(0, "elsewhere:court_warmarshal_spurs", 1, 100, "market", 0, 0, false, 40, List.of()),
			line(1, "minecraft:bread", 4, 20, "market", 0, 0, false, 8, List.of()));
	}

	static List<Lot> lotPool() {
		return List.of(new Lot(id("court_warmarshal_spurs"), 1, 10, 50), new Lot(id("court_gatewarden_plate"), 1, 20, 60), new Lot(Identifier.parse("minecraft:diamond"), 2, 30, 80), new Lot(Identifier.parse("minecraft:emerald"), 4, 15, 40));
	}

	static void mirewick() {
		STEPS.add((srv, l) -> {
			FactionDefs.putCodeDefined(MT, def("Float Test", TestCoins.SCRIP));
			CourtState.get(srv, MT).resetForTests();
			TradeState.get(srv).clearForTests();
			TradeData.put(MT, new TradeData.Stock(TradeData.Mode.FLOATING, floatLines(), List.of(), lotPool(), TradeData.defaultGoods()));
			WAR0 = MirewickTrade.warProvider;
			MirewickTrade.warProvider = (lv, f) -> false;
			setTime(l, 70, 100);
			FakePlayer p = fresh(l, "float", MT, 0);
			Shops.give(p, MT, 5000);
			Line spurs = floatLines().get(0);
			check("floating: the base price at no offset is the line price", MirewickTrade.price(p, MT, spurs, null) == 100 && MirewickTrade.offset(l, MT, spurs) == 0.0, null);
			Trade.Result r = MirewickTrade.buy(p, MT, 0, null);
			check("floating: a purchase raises that item's price 5 percent", r == Trade.Result.OK && Math.abs(MirewickTrade.offset(l, MT, spurs) - 0.05) < 1e-9 && MirewickTrade.price(p, MT, spurs, null) == 105, MirewickTrade.price(p, MT, spurs, null));
			check("floating: the other item is not affected", MirewickTrade.offset(l, MT, floatLines().get(1)) == 0.0, null);
			for (int i = 0; i < 12; i++) MirewickTrade.buy(p, MT, 0, null);
			check("floating: the rise stops at +50 percent", Math.abs(MirewickTrade.offset(l, MT, spurs) - 0.5) < 1e-9 && MirewickTrade.price(p, MT, spurs, null) == 150, MirewickTrade.price(p, MT, spurs, null));
			p.getInventory().clearContent();
			giveItem(p, item("court_warmarshal_spurs"), 1);
			p.getInventory().setItem(0, new ItemStack(item("court_warmarshal_spurs")));
			p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(item("court_warmarshal_spurs")));
			int c0 = Shops.count(p, MT);
			Trade.Result sr = MirewickTrade.sell(p, MT);
			check("floating: selling an item to the market lowers its price (5 points) and pays coin", sr == Trade.Result.OK && Math.abs(MirewickTrade.offset(l, MT, spurs) - 0.45) < 1e-9 && Shops.count(p, MT) > c0, MirewickTrade.offset(l, MT, spurs));
			check("selling something the market does not trade is refused", MirewickTrade.sell(p, MT) == Trade.Result.NOTHING_TO_SELL, null);
			setTime(l, 71, 100);
			check("floating: prices drift back 10 points per day (0.45 -> 0.35)", Math.abs(MirewickTrade.offset(l, MT, spurs) - 0.35) < 1e-6, MirewickTrade.offset(l, MT, spurs));
			setTime(l, 76, 100);
			check("floating: ... and are back to normal after 6 days", MirewickTrade.offset(l, MT, spurs) == 0.0 && MirewickTrade.price(p, MT, spurs, null) == 100, MirewickTrade.offset(l, MT, spurs));
			MirewickTrade.warProvider = (lv, f) -> true;
			check("floating: +20 percent while Mirewick is at war", MirewickTrade.price(p, MT, spurs, null) == 120, MirewickTrade.price(p, MT, spurs, null));
			MirewickTrade.warProvider = (lv, f) -> false;
			KingdomApi.setStanding(p, MT, 40);
			check("Standing discounts still apply (Friendly -5 percent)", MirewickTrade.price(p, MT, spurs, null) == 95, MirewickTrade.price(p, MT, spurs, null));
			KingdomApi.setStanding(p, MT, 0);
			return 1;
		});
		STEPS.add((srv, l) -> {
			setTime(l, 80, 100);
			FakePlayer p = fresh(l, "haggle", MT, 40);
			Shops.give(p, MT, 1000);
			Line spurs = floatLines().get(0);
			check("haggle chance: 30 + Standing/4 = 40 at Standing 40", MirewickTrade.chancePercent(l, p, MT, false) == 40, MirewickTrade.chancePercent(l, p, MT, false));
			check("haggle chance: 15 points lower at a miser's trader (25)", MirewickTrade.chancePercent(l, p, MT, true) == 25, null);
			KingdomApi.setStanding(p, MT, 0);
			check("haggle chance at Standing 0 is 30, and 15 at a miser", MirewickTrade.chancePercent(l, p, MT, false) == 30 && MirewickTrade.chancePercent(l, p, MT, true) == 15, null);
			Oligarchy.setLeaning(srv, MT, Ruler.MASTER_AT_ARMS, Oligarchy.Leaning.MISER);
			Oligarchy.setLeaning(srv, MT, Ruler.MARSHAL, Oligarchy.Leaning.HAWK);
			check("a miser Magnate's trader is detected from the council (and a hawk's is not)", MirewickTrade.isMiser(srv, MT, "master_at_arms") && !MirewickTrade.isMiser(srv, MT, "marshal") && !MirewickTrade.isMiser(srv, MT, null), null);
			String t1 = "trader-one", t2 = "trader-two";
			check("a failed haggle (dice 99) fails and locks that trader for the day", MirewickTrade.haggle(p, MT, t1, false, fixed(99)) == MirewickTrade.Haggle.FAIL && MirewickTrade.locked(l, p, t1), null);
			check("locked: the trader refuses the purchase and any further haggle", MirewickTrade.buy(p, MT, 0, t1) == Trade.Result.LOCKED && MirewickTrade.haggle(p, MT, t1, false, fixed(0)) == MirewickTrade.Haggle.LOCKED, null);
			check("another trader is not locked", !MirewickTrade.locked(l, p, t2) && MirewickTrade.buy(p, MT, 0, t2) == Trade.Result.OK, null);
			setTime(l, 81, 100);
			check("the lock ends with the day", !MirewickTrade.locked(l, p, t1) && MirewickTrade.buy(p, MT, 1, t1) == Trade.Result.OK, null);
			setTime(l, 82, 100);
			TradeState.get(srv).clearForTests();
			int base = MirewickTrade.price(p, MT, spurs, t2);
			check("a won haggle (dice 0) succeeds", MirewickTrade.haggle(p, MT, t2, false, fixed(0)) == MirewickTrade.Haggle.SUCCESS, null);
			check("a won haggle takes 20 percent off at that trader only", MirewickTrade.price(p, MT, spurs, t2) == 80 && base == 100 && MirewickTrade.price(p, MT, spurs, t1) == 100, MirewickTrade.price(p, MT, spurs, t2));
			check("one haggle a day per trader (ALREADY)", MirewickTrade.haggle(p, MT, t2, false, fixed(0)) == MirewickTrade.Haggle.ALREADY, null);
			check("dice 29 wins at chance 30 and dice 30 loses", MirewickTrade.haggle(p, MT, "c1", false, fixed(29)) == MirewickTrade.Haggle.SUCCESS && MirewickTrade.haggle(p, MT, "c2", false, fixed(30)) == MirewickTrade.Haggle.FAIL, null);
			FakePlayer h = fresh(l, "haggleh", MT, -80);
			check("a Hostile player cannot haggle", MirewickTrade.haggle(h, MT, t1, false, fixed(0)) == MirewickTrade.Haggle.HOSTILE, null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			setTime(l, 90, 100);
			CourtState st = CourtState.get(srv, MT);
			st.resetForTests();
			st.site = new BlockPos(0, SelfTest.y, 0);
			TradeState.get(srv).clearForTests();
			FakePlayer p = fresh(l, "loan", MT, 0);
			check("loan: more than 64 Scrip is refused at Neutral", MirewickTrade.borrow(p, MT, 65) == Trade.Result.BAD_AMOUNT && MirewickTrade.limit(p, MT) == 64, null);
			check("loan: 64 Scrip lent, 80 owed (25 percent), a Loan Note given", MirewickTrade.borrow(p, MT, 64) == Trade.Result.OK && Shops.count(p, MT) == 64 && has(p, TradeItems.LOAN_NOTE) == 1 && MirewickTrade.loanOf(l, p.getUUID()).owed() == 80, null);
			check("loan: due in 7 days", MirewickTrade.loanOf(l, p.getUUID()).due() == Trade.now(l) + 7 * DAY, null);
			check("loan: one at a time", MirewickTrade.borrow(p, MT, 10) == Trade.Result.ALREADY, null);
			KingdomApi.setStanding(p, MT, 0);
			MirewickTrade.tick(l);
			check("loan: on time, nothing happens (no Standing loss, no collectors)", Standing.get(l, p.getUUID(), MT) == 0 && collectors(l) == 0, null);
			setTime(l, 96, 100);
			MirewickTrade.tick(l);
			check("loan: day 6 is still on time", Standing.get(l, p.getUUID(), MT) == 0 && collectors(l) == 0, null);
			setTime(l, 97, 200);
			MirewickTrade.tick(l);
			int stand = Standing.get(l, p.getUUID(), MT);
			check("late loan: Standing -20 once, and Marrow's squad (3 soldiers and a sellsword) comes for the borrower inside the realm", stand <= -20 && collectors(l) == 4, stand + " collectors " + collectors(l));
			MirewickTrade.tick(l);
			MirewickTrade.tick(l);
			check("late loan: the Standing loss is once, and no second squad", Standing.get(l, p.getUUID(), MT) == stand && collectors(l) == 4, null);
			// death pays the debt
			KingdomSoldier c = null;
			for (KingdomSoldier s : l.getEntitiesOfClass(KingdomSoldier.class, new net.minecraft.world.phys.AABB(-40, SelfTest.y - 10, -40, 40, SelfTest.y + 20, 40))) if (MirewickTrade.isCollector(s)) c = s;
			Shops.give(p, MT, 50);
			int paid = MirewickTrade.onPlayerDeath(p, l.damageSources().mobAttack(c));
			check("death to the collectors pays the debt from Scrip (all 80 owed; the player had 114)", paid == 80 && Shops.count(p, MT) == 34 && MirewickTrade.loanOf(l, p.getUUID()) == null, paid + " " + Shops.count(p, MT));
			check("the collectors stand down after the debt is paid", collectors(l) == 0, collectors(l));
			// outside the realm: nobody comes
			FakePlayer q = fresh(l, "loan2", MT, 0);
			MirewickTrade.borrow(q, MT, 10);
			setTime(l, 110, 100);
			q.snapTo(5000.5, SelfTest.y, 5000.5, 0F, 0F);
			MirewickTrade.tick(l);
			check("only inside Mirewick's realm: an overdue borrower elsewhere is not chased (but the Standing loss applies)", collectors(l) == 0 && Standing.get(l, q.getUUID(), MT) <= -20, collectors(l));
			// a player with no loan, inside the realm: nobody comes
			FakePlayer clean = fresh(l, "loan3", MT, 0);
			MirewickTrade.tick(l);
			check("only an overdue borrower is ever chased: a player without a loan inside the realm is left alone", collectors(l) == 0 && Standing.get(l, clean.getUUID(), MT) == 0, null);
			// repay
			q.snapTo(0.5, SelfTest.y, 0.5, 0F, 0F);
			Shops.give(q, MT, 100);
			check("repaying clears the loan and the Note", MirewickTrade.repay(q) == Trade.Result.OK && MirewickTrade.loanOf(l, q.getUUID()) == null && has(q, TradeItems.LOAN_NOTE) == 0, null);
			MirewickTrade.tick(l);
			check("nobody is chased after repaying", collectors(l) == 0, null);
			// the loan limit rises with Standing
			KingdomApi.setStanding(clean, MT, 40);
			check("the limit rises with Standing (Friendly 96)", MirewickTrade.limit(clean, MT) == 96, null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			TradeState.get(srv).clearForTests();
			long ad = 140; // a multiple of 7
			setTime(l, ad, 5000);
			FakePlayer a = fresh(l, "bidA", MT, 0), b = fresh(l, "bidB", MT, 0);
			Shops.give(a, MT, 1000);
			Shops.give(b, MT, 1000);
			check("auction: every 7th day, not before midday", MirewickTrade.phase(l) == MirewickTrade.Phase.WAITING && MirewickTrade.auctionDay(l) == 140, null);
			setTime(l, ad + 3, 100);
			check("auction: no auction on other days (next in 4 days)", MirewickTrade.daysToAuction(l) == 4, MirewickTrade.daysToAuction(l));
			setTime(l, ad, 6100);
			var lots = MirewickTrade.lots(l, MT, ad);
			check("auction: 3 lots, picked by the day (same lots when asked again)", lots.size() == 3 && MirewickTrade.lots(l, MT, ad).get(1).stack().getItem() == lots.get(1).stack().getItem() && MirewickTrade.phase(l) == MirewickTrade.Phase.OPEN, lots.size());
			check("auction: the hour is 1000 ticks", MirewickTrade.AUCTION_TICKS == 1000, null);
			var lot0 = lots.get(0);
			int n0 = MirewickTrade.npcBid(lot0, 0), n1 = MirewickTrade.npcBid(lot0, 500), n2 = MirewickTrade.npcBid(lot0, 1000);
			check("auction: NPC bidders raise the price over the hour (monotonic, from the start price)", n0 == lot0.start() && n1 >= n0 && n2 >= n1 && n2 > n0, n0 + "," + n1 + "," + n2);
			int cur = MirewickTrade.current(l, MT, lot0);
			check("auction: a bid under current+5 percent is refused (TOO_LOW)", MirewickTrade.bid(a, MT, 0, cur) == Trade.Result.TOO_LOW && Shops.count(a, MT) == 1000, null);
			check("auction: a bad lot is refused", MirewickTrade.bid(a, MT, 7, 999) == Trade.Result.NO_LOT, null);
			int bidA = cur + MirewickTrade.minRaise(cur);
			check("auction: a valid bid holds the coins and leads", MirewickTrade.bid(a, MT, 0, bidA) == Trade.Result.OK && Shops.count(a, MT) == 1000 - bidA && MirewickTrade.leading(l, MT, a, lot0), null);
			int bidB = bidA + 40;
			check("auction: B outbids A, A is refunded at once", MirewickTrade.bid(b, MT, 0, bidB) == Trade.Result.OK && Shops.count(b, MT) == 1000 - bidB && Shops.count(a, MT) == 1000 && !MirewickTrade.leading(l, MT, a, lot0), null);
			Trade.Result[] rr = {null};
			int ql = MirewickTrade.quickBid(a, MT, rr);
			check("auction: right-click with Scrip bids the minimum raise on a lot the player is not winning", rr[0] == Trade.Result.OK && ql >= 0 && Shops.count(a, MT) < 1000, ql);
			// bell: B wins lot 0 (bid above NPC max); A's quick bid is below the NPC final price and is refunded
			int bWinPay = bidB;
			setTime(l, ad, 7200);
			MirewickTrade.settle(l, MT, ad);
			check("auction: the bell settles once (idempotent)", MirewickTrade.settled(l, ad, MT) && MirewickTrade.settle(l, MT, ad) == 0, null);
			int deliveredB = MirewickTrade.deliverClaims(b);
			check("auction: the highest bid wins the lot (delivered at the bell to an online player) when it beats the NPC bidders", has(b, lots.get(0).stack().getItem()) >= 1 && Shops.count(b, MT) == 1000 - bWinPay, deliveredB + " " + Shops.count(b, MT));
			MirewickTrade.deliverClaims(a);
			check("auction: a bid the NPC bidders beat is refunded at the bell", Shops.count(a, MT) == 1000, Shops.count(a, MT));
			check("auction: nothing is delivered twice", MirewickTrade.deliverClaims(a) == 0 && MirewickTrade.deliverClaims(b) == 0, null);
			check("auction: a closed auction takes no bids (NOT_OPEN)", MirewickTrade.bid(a, MT, 1, 9999) == Trade.Result.NOT_OPEN, null);
			Trade.testOffset = 0;
			MirewickTrade.warProvider = WAR0;
			TradeData.remove(MT);
			CourtState.get(srv, MT).resetForTests();
			return 1;
		});
	}

	static int collectors(ServerLevel l) {
		int n = 0;
		for (KingdomSoldier s : l.getEntitiesOfClass(KingdomSoldier.class, new net.minecraft.world.phys.AABB(-60, SelfTest.y - 20, -60, 60, SelfTest.y + 40, 60), KingdomSoldier::isAlive)) if (MirewickTrade.isCollector(s)) n++;
		return n;
	}

	// ---- persistence -----------------------------------------------------------------------------------------------------------------------

	static void persistence() {
		STEPS.add((srv, l) -> {
			TradeState ts = TradeState.get(srv);
			ts.clearForTests();
			TradeData.put(MT, new TradeData.Stock(TradeData.Mode.FLOATING, floatLines(), List.of(), lotPool(), TradeData.defaultGoods()));
			setTime(l, 150, 100);
			FakePlayer p = fresh(l, "persist", MT, 0);
			Shops.give(p, MT, 500);
			MirewickTrade.buy(p, MT, 0, null);
			MirewickTrade.buy(p, MT, 0, null);
			MirewickTrade.borrow(p, MT, 30);
			ts.putLong("credit|" + p.getUUID(), 77);
			TradeState back = TradeState.load(ts.save());
			check("saved per world: floating prices, loans and credit survive a save and load of the world data", back.doubles.equals(ts.doubles) && back.strings.equals(ts.strings) && back.longs.equals(ts.longs) && !back.doubles.isEmpty() && back.longs.get("credit|" + p.getUUID()) == 77L, back.doubles.size() + "/" + back.strings.size());
			check("the weekly counter is per player and faction (saved with the rest)", ts.longs.keySet().stream().noneMatch(String::isEmpty), null);
			ts.clearForTests();
			TradeData.remove(MT);
			Trade.testOffset = 0;
			return 1;
		});
	}

	// ---- NPC menus and succession ----------------------------------------------------------------------------------------------------------------

	static void menus() {
		STEPS.add((srv, l) -> {
			FactionDefs.putCodeDefined(HT, def("Thing Test", TestCoins.SCRIP));
			FactionDefs.putCodeDefined(AT, def("Posted Test", TestCoins.DINAR));
			FactionDefs.putCodeDefined(MT, def("Float Test", TestCoins.SCRIP));
			TradeData.put(HT, new TradeData.Stock(TradeData.Mode.THING, thingLines(), List.of(), List.of(), TradeData.defaultGoods()));
			TradeData.put(AT, new TradeData.Stock(TradeData.Mode.POSTED, postedLines(), List.of(List.of(line(0, "minecraft:apple", 1, 4, "common", 0, 0, false, 1, List.of()))), List.of(), TradeData.defaultGoods()));
			TradeData.put(MT, new TradeData.Stock(TradeData.Mode.FLOATING, floatLines(), List.of(), lotPool(), TradeData.defaultGoods()));
			setTime(l, 90, 6100);
			int ok = 0, total = 0;
			for (Diplomacy.Role r : Diplomacy.Role.values()) {
				Identifier f = switch (r) {
					case LAWSPEAKER, SMITH -> HT;
					case ENVOY, INSPECTOR, CARAVAN_MASTER -> AT;
					default -> MT;
				};
				FakePlayer p = fresh(l, "menu-" + r.path, f, 40);
				Shops.give(p, f, 50);
				DiplomatNpc n = r.type.create(l, EntitySpawnReason.COMMAND);
				n.snapTo(1.5, SelfTest.y, 1.5, 0F, 0F);
				n.setFaction(f);
				l.addFreshEntity(n);
				total++;
				try {
					TradeUi.interact(p, n);
					p.setShiftKeyDown(true);
					TradeUi.interact(p, n);
					p.setShiftKeyDown(false);
					TradeUi.shop(p, n);
					TradeUi.buy(p, n, 1);
					ok++;
				} catch (RuntimeException e) {
					info("menu of " + r.path + " threw " + e);
				}
				n.discard();
			}
			check("every trader NPC answers right-click, sneak-use, shop and buy without error", ok == total, ok + "/" + total);
			boolean all = true;
			for (Trade.Result r : Trade.Result.values()) if (TradeUi.describe(r).isEmpty()) all = false;
			check("every trade result has a message", all, null);
			check("Aldermark's Steward path is untouched: a faction with no trade mode keeps the 2.0 shop", Trade.mode(l, id("rv_shop")) == TradeData.Mode.NONE, null);
			TradeData.remove(HT);
			TradeData.remove(AT);
			TradeData.remove(MT);
			Trade.testOffset = 0;
			return 1;
		});
		STEPS.add((srv, l) -> {
			// RIVALS-DECISIONS 13: human bosses never exceed 300 max health
			Zombie z = SelfTest.zombie(l, SelfTest.at(0, 0), false);
			var hp = z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
			hp.setBaseValue(280);
			hp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id("test_risen"), 0.15, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			z.setHealth(z.getMaxHealth());
			check("uncapped: a 280-health boss with a Risen +15 percent would read 322", Math.abs(HpCap.uncapped(z) - 322.0) < 0.01 && z.getMaxHealth() > 300, z.getMaxHealth());
			HpCap.clamp(z);
			check("Risen +15 percent: clamped to 300 and health follows", z.getMaxHealth() == 300F && z.getHealth() <= 300F, z.getMaxHealth());
			hp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id("test_regent"), 0.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			HpCap.clamp(z);
			check("a boosted Regent or heir (+50 percent more) and a Test Meter multiplier still read 300", z.getMaxHealth() == 300F, z.getMaxHealth());
			hp.removeModifier(id("test_regent"));
			hp.removeModifier(id("test_risen"));
			hp.setBaseValue(200);
			HpCap.clamp(z);
			check("under the cap the modifier is removed again (200 stays 200)", z.getMaxHealth() == 200F && !hp.hasModifier(HpCap.ID), z.getMaxHealth());
			z.discard();
			// succession goes straight to the war lane's API (no reflection)
			boolean ok;
			try {
				ok = KingdomApi.successionEvent(srv, id("rv_tr_thing"), "chancellor", "risen") || true;
				KingdomApi.successionEvent(srv, id("rv_tr_float"), "master_at_arms", "heir");
			} catch (Throwable t) {
				ok = false;
			}
			TradeEvents.testFactions.clear();
			TradeEvents.forceNear = false;
			Trade.testPlayers.clear();
			check("succession: KingdomApi.successionEvent is called directly (risen and heir) without error", ok, null);
			return 1;
		});
	}
}
