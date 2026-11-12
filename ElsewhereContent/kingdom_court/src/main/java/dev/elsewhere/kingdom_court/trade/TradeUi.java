package dev.elsewhere.kingdom_court.trade;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.trade.TradeData.Good;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the trader NPCs say and do (RIVALS-DECISIONS 12). Right-click prints the NPC's menu (every price and action is a clickable {@code /diplomacy ...}); sneak-use on a
 * Mirewick trader haggles; right-click with Scrip in hand at the Auctioneer bids. The same actions exist as {@code /diplomacy} sub-commands, acting on the nearest NPC within 8 blocks.
 * Aldermark's Steward never goes through here, and a faction without a trade mode keeps the 2.0 shop menu.
 */
public final class TradeUi {
	private TradeUi() {}

	static Component cmd(String text, String command) {
		return Component.literal(text).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	static void say(ServerPlayer p, String s) {
		p.sendSystemMessage(Component.literal(s));
	}

	static void say(ServerPlayer p, Component c) {
		p.sendSystemMessage(c);
	}

	static String name(Identifier faction) {
		return Factions.get(faction).displayName().getString();
	}

	static String coin(Identifier faction) {
		return switch (faction.getPath()) {
			case "hrafnholt" -> "Hacksilver";
			case "azmar" -> "Dinars";
			case "mirewick" -> "Scrip";
			default -> "coins";
		};
	}

	public static String describe(Trade.Result r) {
		return switch (r) {
			case OK -> "Done.";
			case NO_SHOP -> "There is nothing for sale here.";
			case NO_SUCH_LINE -> "There is no such line.";
			case WEEKLY_LIMIT -> "You have bought all you may this week.";
			case CANT_AFFORD -> "You cannot afford that.";
			case HOSTILE -> "They will not trade with you.";
			case CLOSED -> "That stall is closed today.";
			case TIER_LOCKED -> "Your Standing is too low for that stock.";
			case NEEDS_RINGS -> "That costs Arm-Rings only.";
			case COMMISSION_ONLY -> "That is made to order: use /diplomacy order.";
			case NOT_COMMISSION -> "That is not for that kind of payment.";
			case NO_BARTER -> "You do not carry that good, or not enough of it.";
			case NOT_READY -> "The commission is not ready yet.";
			case NO_SLIP -> "You hold no valid Commission Slip.";
			case NOT_OWNER -> "That slip is not yours.";
			case BAD_AMOUNT -> "That amount is not allowed.";
			case ALREADY -> "You already have that.";
			case NOT_OPEN -> "The auction is not open.";
			case TOO_LOW -> "Your bid is too low.";
			case NO_LOT -> "There is no such lot.";
			case NOTHING_TO_SELL -> "Nothing to sell: hold the item in your main hand.";
			case LOCKED -> "This trader will not deal with you until tomorrow.";
			case NO_GIVER -> "Nobody is left to give rings.";
			case NOTHING_DUE -> "Nothing is due.";
			case NO_ITEM -> "You do not carry that.";
			case NOT_TREASURY -> "Find a treasury (the Envoy, the Market Inspector or the Caravan Master).";
			case WRONG_FACTION -> "That is not done here.";
		};
	}

	// ---- right-click ---------------------------------------------------------------------------------------------------------------

	public static void interact(ServerPlayer p, DiplomatNpc n) {
		Identifier f = n.factionId();
		ServerLevel l = (ServerLevel) p.level();
		if (n.role == Diplomacy.Role.AUCTIONEER) {
			MirewickTrade.deliverClaims(p);
			net.minecraft.world.item.Item cur = Shops.currency(f);
			if (!p.isShiftKeyDown() && cur != null && p.getMainHandItem().is(cur)) {
				Trade.Result[] r = {Trade.Result.OK};
				int lot = MirewickTrade.quickBid(p, f, r);
				if (lot >= 0) say(p, "Bid placed on lot " + (lot + 1) + ". Hold the highest bid at the bell to win it.");
				else say(p, describe(r[0]));
				return;
			}
		}
		if (p.isShiftKeyDown() && Trade.mode(l, f) == TradeData.Mode.FLOATING && (n.role == Diplomacy.Role.CLERK || n.role == Diplomacy.Role.AUCTIONEER)) {
			haggle(p, n);
			return;
		}
		menu(p, n);
	}

	static void menu(ServerPlayer p, DiplomatNpc n) {
		Identifier f = n.factionId();
		ServerLevel l = (ServerLevel) p.level();
		say(p, "== " + n.getDisplayName().getString() + " of " + name(f) + " ==");
		say(p, Diplomacy.readout(p, f));
		TradeData.Mode mode = Trade.mode(l, f);
		switch (n.role) {
			case LAWSPEAKER -> {
				if (mode == TradeData.Mode.THING) thing(p, f, false);
				action(p, "Claim the Arm-Rings you have earned", "/diplomacy claim");
			}
			case SMITH -> {
				thing(p, f, true);
				action(p, "Cut an Arm-Ring into " + HrafnholtTrade.RING_CUTS_INTO + " Hacksilver", "/diplomacy cut");
				action(p, "Repair the tool in your hand", "/diplomacy repair");
			}
			case INSPECTOR -> {
				posted(p, f);
				treasury(p, f);
			}
			case ENVOY -> {
				treasury(p, f);
				if (Diplomacy.offerAvailable(p, f)) action(p, "Accept the Sultanate's offer (" + Diplomacy.OFFER_DINARS + " Dinars)", "/diplomacy offer");
			}
			case CARAVAN_MASTER -> {
				caravan(p, f);
				treasury(p, f);
			}
			case CLERK -> {
				floating(p, f, n);
				loans(p, f);
				action(p, "Sell the item in your hand to the market", "/diplomacy sell");
				say(p, "Sneak and right-click to haggle (20 percent off; a failure shuts this trader to you until tomorrow).");
			}
			case AUCTIONEER -> auction(p, f);
		}
		if (n.role != Diplomacy.Role.SMITH && n.role != Diplomacy.Role.INSPECTOR && n.role != Diplomacy.Role.AUCTIONEER && n.role != Diplomacy.Role.CARAVAN_MASTER) {
			action(p, "Pay tribute (/diplomacy tribute <coins>)", "/diplomacy status");
		}
	}

	static void action(ServerPlayer p, String text, String command) {
		say(p, cmd(" [" + text + "]", command));
	}

	static String goodName(Good g) {
		var s = g.stack();
		return g.count() + " " + (s == null ? g.item().getPath() : s.getHoverName().getString());
	}

	static String lineName(TradeData.Line line) {
		var s = line.stack();
		return (s == null ? line.item().getPath() : s.getHoverName().getString()) + (line.count() > 1 ? " x" + line.count() : "");
	}

	static void thing(ServerPlayer p, Identifier f, boolean smith) {
		ServerLevel l = (ServerLevel) p.level();
		if (!smith) {
			if (HrafnholtTrade.thingOpen(l)) say(p, "The Thing is open until dusk.");
			else say(p, "The Thing is closed. It opens at dawn in " + HrafnholtTrade.daysToThing(l) + " day(s); the Smith's stall is always open.");
		}
		say(p, "You hold " + Shops.count(p, f) + " Hacksilver, " + Trade.count(p, TradeItems.ARM_RING) + " Arm-Rings" + (HrafnholtTrade.hasRing(p) ? " (10 percent off)" : "") + ". Deeds: " + HrafnholtTrade.deeds(l, p.getUUID()) + ", rings waiting: " + HrafnholtTrade.pendingRings(l, p.getUUID()) + ".");
		for (var q : HrafnholtTrade.quotes(p, f, smith)) {
			StringBuilder b = new StringBuilder(" " + q.line().index() + ": " + lineName(q.line()));
			if (q.rings() > 0) b.append(" - ").append(q.rings()).append(" Arm-Rings only");
			else {
				b.append(" - ").append(q.coins()).append(" Hacksilver");
				for (Good g : q.barter()) b.append(" | ").append(goodName(g));
			}
			if (q.remaining() >= 0) b.append(" (").append(q.remaining()).append(" left this week)");
			if (!q.open()) b.append(" [closed]");
			String c = q.rings() > 0 ? "/diplomacy rings " + q.line().index() : "/diplomacy buy " + q.line().index();
			say(p, cmd(b.toString(), c));
		}
	}

	static void posted(ServerPlayer p, Identifier f) {
		say(p, "Posted prices (fixed): you hold " + Shops.count(p, f) + " Dinars. Stock opens at Neutral, Friendly and Allied Standing.");
		for (var q : AzmarTrade.quotes(p, f)) {
			String s = " " + q.line().index() + ": " + lineName(q.line()) + " - " + q.price() + " Dinars" + (q.line().commission() ? " (made to order: pay half now)" : "")
				+ (q.remaining() >= 0 ? " (" + q.remaining() + " left)" : "") + (q.unlocked() ? "" : " [needs " + q.needs() + "]");
			say(p, cmd(s, q.line().commission() ? "/diplomacy order " + q.line().index() + " plain" : "/diplomacy buy " + q.line().index()));
		}
		say(p, "Orders: /diplomacy order <n> <trim colour>. A Commission Slip is ready after " + AzmarTrade.COMMISSION_DAYS + " days.");
		action(p, "Collect the commission on your slip", "/diplomacy pickup");
	}

	static void caravan(ServerPlayer p, Identifier f) {
		say(p, "The caravan's rare stock (this visit only):");
		for (var q : AzmarTrade.caravanQuotes(p, f)) say(p, cmd(" " + q.line().index() + ": " + lineName(q.line()) + " - " + q.price() + " Dinars", "/diplomacy buy " + q.line().index()));
	}

	static void treasury(ServerPlayer p, Identifier f) {
		ServerLevel l = (ServerLevel) p.level();
		say(p, "Letter of Credit: " + AzmarTrade.balance(l, p.getUUID()) + " Dinars held in your name at every treasury (safe if you die).");
		say(p, "/diplomacy deposit <n>, /diplomacy withdraw <n>");
	}

	static void floating(ServerPlayer p, Identifier f, DiplomatNpc n) {
		say(p, "Floating prices (each purchase +5 percent, up to +50; selling lowers them; war +20): you hold " + Shops.count(p, f) + " Scrip.");
		for (var q : MirewickTrade.quotes(p, f, n.getUUID().toString())) {
			String s = " " + q.line().index() + ": " + lineName(q.line()) + " - " + q.price() + " Scrip (" + (q.offset() >= 0 ? "+" : "") + Math.round(q.offset() * 100) + "%)" + (q.remaining() >= 0 ? " (" + q.remaining() + " left)" : "");
			say(p, cmd(s, "/diplomacy buy " + q.line().index()));
		}
	}

	static void loans(ServerPlayer p, Identifier f) {
		ServerLevel l = (ServerLevel) p.level();
		var x = MirewickTrade.loanOf(l, p.getUUID());
		if (x == null) say(p, "Loans: up to " + MirewickTrade.limit(p, f) + " Scrip at 25 percent, due in " + MirewickTrade.LOAN_DAYS + " days (/diplomacy loan <n>).");
		else {
			say(p, "You owe " + x.owed() + " Scrip, due day " + Math.floorDiv(x.due(), Trade.DAY) + (x.late() ? " (OVERDUE)" : "") + ".");
			action(p, "Repay the loan", "/diplomacy repay");
		}
	}

	static void auction(ServerPlayer p, Identifier f) {
		ServerLevel l = (ServerLevel) p.level();
		var ph = MirewickTrade.phase(l);
		say(p, switch (ph) {
			case OPEN -> "The auction is open for one in-game hour. Right-click with Scrip in hand to outbid, or /diplomacy bid <lot> <amount>.";
			case WAITING -> "The auction opens at midday today.";
			case CLOSED -> "The bell has rung. The next auction is in " + MirewickTrade.daysToAuction(l) + " day(s).";
		});
		long ad = MirewickTrade.auctionDay(l);
		for (var lot : MirewickTrade.lots(l, f, ad)) {
			int cur = MirewickTrade.current(l, f, lot);
			say(p, " lot " + (lot.index() + 1) + ": " + lot.stack().getHoverName().getString() + (lot.stack().getCount() > 1 ? " x" + lot.stack().getCount() : "")
				+ " - " + cur + " Scrip" + (MirewickTrade.leading(l, f, p, lot) ? " (you lead)" : ""));
		}
	}

	// ---- actions (shared by right-click and /diplomacy) ---------------------------------------------------------------------------

	static void haggle(ServerPlayer p, DiplomatNpc n) {
		Identifier f = n.factionId();
		ServerLevel l = (ServerLevel) p.level();
		boolean miser = MirewickTrade.isMiser(l.getServer(), f, n.patron());
		var r = MirewickTrade.haggle(p, f, n.getUUID().toString(), miser, p.getRandom());
		say(p, switch (r) {
			case SUCCESS -> "Haggle won: this trader sells to you for 20 percent less today.";
			case FAIL -> "Haggle lost. This trader will not deal with you until tomorrow.";
			case LOCKED -> "This trader will not deal with you until tomorrow.";
			case ALREADY -> "You have already haggled with this trader today.";
			case HOSTILE -> "They will not trade with you.";
		});
	}

	public static void shop(ServerPlayer p, DiplomatNpc n) {
		if (Trade.mode((ServerLevel) p.level(), n.factionId()) == TradeData.Mode.NONE) legacyShop(p, n);
		else menu(p, n);
	}

	static void legacyShop(ServerPlayer p, DiplomatNpc n) {
		Identifier f = n.factionId();
		var offers = Shops.offers(p, f);
		if (offers.isEmpty()) {
			say(p, "Nothing for sale.");
			return;
		}
		say(p, "For sale (" + name(f) + ", you hold " + Shops.count(p, f) + "):");
		for (var o : offers) say(p, cmd(" " + o.index() + ": " + o.stack().getHoverName().getString() + " x" + o.stack().getCount() + " for " + o.price() + (o.remaining() >= 0 ? " (" + o.remaining() + " left this week)" : ""), "/diplomacy buy " + o.index()));
	}

	public static void buy(ServerPlayer p, DiplomatNpc n, int index) {
		Identifier f = n.factionId();
		ServerLevel l = (ServerLevel) p.level();
		Trade.Result r;
		switch (Trade.mode(l, f)) {
			case THING -> r = n.role == Diplomacy.Role.LAWSPEAKER || n.role == Diplomacy.Role.SMITH ? HrafnholtTrade.buy(p, f, index, n.role == Diplomacy.Role.SMITH) : Trade.Result.NO_SHOP;
			case POSTED -> r = n.role == Diplomacy.Role.CARAVAN_MASTER ? AzmarTrade.buyCaravan(p, f, index) : n.role == Diplomacy.Role.INSPECTOR ? AzmarTrade.buy(p, f, index) : Trade.Result.NO_SHOP;
			case FLOATING -> r = n.role == Diplomacy.Role.CLERK ? MirewickTrade.buy(p, f, index, n.getUUID().toString()) : Trade.Result.NO_SHOP;
			default -> {
				say(p, "Shop: " + Shops.buy(p, f, index));
				return;
			}
		}
		say(p, describe(r));
	}

	interface Act { void run(ServerPlayer p, DiplomatNpc n, Identifier faction); }

	static int run(CommandContext<CommandSourceStack> c, Diplomacy.Role only, Act a) {
		if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
		DiplomatNpc n = Diplomacy.npcNear(p, 8.0);
		if (n == null) {
			say(p, "Find a trader first (Lawspeaker, Smith, Envoy, Market Inspector, Caravan Master, Clerk or Auctioneer).");
			return 0;
		}
		a.run(p, n, n.factionId());
		return 1;
	}

	static boolean is(DiplomatNpc n, Diplomacy.Role... roles) {
		for (Diplomacy.Role r : roles) if (n.role == r) return true;
		return false;
	}

	/** Adds the 2.1 trade sub-commands to {@code /diplomacy}. */
	public static void extend(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("barter").then(Commands.argument("n", IntegerArgumentType.integer(0, 64)).executes(c -> {
			int i = IntegerArgumentType.getInteger(c, "n");
			return run(c, null, (p, n, f) -> say(p, is(n, Diplomacy.Role.LAWSPEAKER, Diplomacy.Role.SMITH) ? describe(HrafnholtTrade.buyBarter(p, f, i, null, n.role == Diplomacy.Role.SMITH)) : "Only Hrafnholt barters."));
		}).then(Commands.argument("good", StringArgumentType.word()).executes(c -> {
			int i = IntegerArgumentType.getInteger(c, "n");
			String g = StringArgumentType.getString(c, "good");
			return run(c, null, (p, n, f) -> {
				Identifier gid = Identifier.tryParse(g.contains(":") ? g : "minecraft:" + g);
				say(p, is(n, Diplomacy.Role.LAWSPEAKER, Diplomacy.Role.SMITH) && gid != null ? describe(HrafnholtTrade.buyBarter(p, f, i, gid, n.role == Diplomacy.Role.SMITH)) : "Only Hrafnholt barters.");
			});
		}))));
		root.then(Commands.literal("rings").then(Commands.argument("n", IntegerArgumentType.integer(0, 64)).executes(c -> {
			int i = IntegerArgumentType.getInteger(c, "n");
			return run(c, null, (p, n, f) -> say(p, is(n, Diplomacy.Role.LAWSPEAKER) ? describe(HrafnholtTrade.buyRings(p, f, i)) : "Only the Lawspeaker takes rings for the top tier."));
		})));
		root.then(Commands.literal("claim").executes(c -> run(c, null, (p, n, f) -> {
			if (!is(n, Diplomacy.Role.LAWSPEAKER, Diplomacy.Role.SMITH)) {
				say(p, "Only Hrafnholt's people give rings.");
				return;
			}
			ServerLevel l = (ServerLevel) p.level();
			String giver = HrafnholtTrade.giver(l, f);
			var r = HrafnholtTrade.claimRings(p, f);
			say(p, r == Trade.Result.OK ? "The " + ("king".equals(giver) ? "Jarl" : "senior champion") + " sends you Arm-Rings for your deeds." : describe(r));
		})));
		root.then(Commands.literal("cut").executes(c -> run(c, null, (p, n, f) -> say(p, n.role == Diplomacy.Role.SMITH ? describe(HrafnholtTrade.cutRing(p, f)) : "Only the Smith cuts rings."))));
		root.then(Commands.literal("repair").executes(c -> run(c, null, (p, n, f) -> say(p, n.role == Diplomacy.Role.SMITH ? describe(HrafnholtTrade.repair(p, f)) : "Only the Smith repairs."))));
		root.then(Commands.literal("deposit").then(Commands.argument("n", IntegerArgumentType.integer(1, 4096)).executes(c -> {
			int i = IntegerArgumentType.getInteger(c, "n");
			return run(c, null, (p, n, f) -> say(p, n.role.treasury() ? describe(AzmarTrade.deposit(p, f, i)) : describe(Trade.Result.NOT_TREASURY)));
		})));
		root.then(Commands.literal("withdraw").then(Commands.argument("n", IntegerArgumentType.integer(1, 4096)).executes(c -> {
			int i = IntegerArgumentType.getInteger(c, "n");
			return run(c, null, (p, n, f) -> say(p, n.role.treasury() ? describe(AzmarTrade.withdraw(p, f, i)) : describe(Trade.Result.NOT_TREASURY)));
		})));
		root.then(Commands.literal("order").then(Commands.argument("n", IntegerArgumentType.integer(0, 64)).then(Commands.argument("colour", StringArgumentType.word()).executes(c -> {
			int i = IntegerArgumentType.getInteger(c, "n");
			String col = StringArgumentType.getString(c, "colour");
			return run(c, null, (p, n, f) -> say(p, n.role == Diplomacy.Role.INSPECTOR ? describe(AzmarTrade.order(p, f, i, col)) : "Only the Market Inspector takes orders."));
		}))));
		root.then(Commands.literal("pickup").executes(c -> run(c, null, (p, n, f) -> {
			if (n.role != Diplomacy.Role.INSPECTOR && n.role != Diplomacy.Role.ENVOY) {
				say(p, "Collect at the Market Inspector.");
				return;
			}
			var r = AzmarTrade.collect(p);
			say(p, r == Trade.Result.OK ? "Your commission is delivered." : describe(r));
		})));
		root.then(Commands.literal("sell").executes(c -> run(c, null, (p, n, f) -> say(p, n.role == Diplomacy.Role.CLERK ? describe(MirewickTrade.sell(p, f)) : "Only the Clerk buys from you."))));
		root.then(Commands.literal("haggle").executes(c -> run(c, null, (p, n, f) -> {
			if (n.role == Diplomacy.Role.CLERK || n.role == Diplomacy.Role.AUCTIONEER) haggle(p, n);
			else say(p, "Only Mirewick haggles; Hrafnholt and Azmar never do.");
		})));
		root.then(Commands.literal("loan").then(Commands.argument("n", IntegerArgumentType.integer(1, 256)).executes(c -> {
			int i = IntegerArgumentType.getInteger(c, "n");
			return run(c, null, (p, n, f) -> say(p, n.role == Diplomacy.Role.CLERK ? describe(MirewickTrade.borrow(p, f, i)) : "Only the Clerk lends."));
		})));
		root.then(Commands.literal("repay").executes(c -> run(c, null, (p, n, f) -> say(p, n.role == Diplomacy.Role.CLERK ? describe(MirewickTrade.repay(p)) : "Repay at the Clerk."))));
		root.then(Commands.literal("lots").executes(c -> run(c, null, (p, n, f) -> auction(p, f))));
		root.then(Commands.literal("bid").then(Commands.argument("lot", IntegerArgumentType.integer(1, 3)).then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000)).executes(c -> {
			int lot = IntegerArgumentType.getInteger(c, "lot"), amt = IntegerArgumentType.getInteger(c, "amount");
			return run(c, null, (p, n, f) -> say(p, n.role == Diplomacy.Role.AUCTIONEER ? describe(MirewickTrade.bid(p, f, lot - 1, amt)) : "Bid at the Auctioneer."));
		}))));
	}
}
