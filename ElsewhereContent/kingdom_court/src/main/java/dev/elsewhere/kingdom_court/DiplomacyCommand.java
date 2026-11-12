package dev.elsewhere.kingdom_court;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.api.Duel;
import dev.elsewhere.kingdom_court.api.Oligarchy;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /diplomacy ...}: what a player says to the nearest Lawspeaker, Royal Envoy or Clerk (within 8 blocks). Open to everyone; every rule lives in
 * {@link Shops}, {@link Duel}, {@link Oligarchy} and {@link Diplomacy}.
 */
public final class DiplomacyCommand {
	private DiplomacyCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("diplomacy")
			.then(Commands.literal("status").executes(c -> run(c, (p, n) -> say(p, Diplomacy.readout(p, n.factionId())))))
			.then(Commands.literal("tribute").then(Commands.argument("coins", IntegerArgumentType.integer(1, 4096)).executes(c -> {
				int coins = IntegerArgumentType.getInteger(c, "coins");
				return run(c, (p, n) -> {
					int g = Shops.tribute(p, n.factionId(), coins);
					say(p, g < 0 ? Component.literal("You do not have that many coins.") : Component.literal("Your tribute is accepted. Standing +" + g + " (weekly cap " + Shops.TRIBUTE_STANDING_CAP + ")."));
				});
			})))
			.then(Commands.literal("shop").executes(c -> run(c, dev.elsewhere.kingdom_court.trade.TradeUi::shop)))
			.then(Commands.literal("buy").then(Commands.argument("n", IntegerArgumentType.integer(0, 64)).executes(c -> {
				int n = IntegerArgumentType.getInteger(c, "n");
				return run(c, (p, npc) -> dev.elsewhere.kingdom_court.trade.TradeUi.buy(p, npc, n));
			})))
			.then(Commands.literal("duel").then(Commands.argument("lord", StringArgumentType.word()).executes(c -> {
				String lord = StringArgumentType.getString(c, "lord");
				return run(c, (p, n) -> {
					Ruler r = Ruler.byKey(lord);
					say(p, Component.literal(r == null ? "Unknown lord." : "Duel: " + Duel.challenge(p, n.factionId(), r)));
				});
			})))
			.then(Commands.literal("offer").executes(c -> run(c, (p, n) -> {
				if (n.role != Diplomacy.Role.ENVOY) say(p, Component.literal("Only the Royal Envoy makes offers."));
				else say(p, Component.literal(Diplomacy.acceptOffer(p, n.factionId()) ? "The Sultanate pays " + Diplomacy.OFFER_DINARS + " Dinars. Stay out of its wars this week." : "No offer for you."));
			})))
			.then(Commands.literal("contract").then(Commands.argument("enemy", StringArgumentType.word()).executes(c -> {
				String en = StringArgumentType.getString(c, "enemy");
				return run(c, (p, n) -> {
					Identifier e = Identifier.tryParse(en.contains(":") ? en : "elsewhere:" + en);
					say(p, Component.literal(n.role == Diplomacy.Role.CLERK && e != null && Diplomacy.signContract(p, n.factionId(), e) ? "Contract signed against " + en + " for " + Diplomacy.CONTRACT_DAYS + " days: " + Diplomacy.SCRIP_PER_KILL + " Scrip per kill. Collect with /diplomacy collect." : "No contract for you."));
				});
			})))
			.then(Commands.literal("collect").executes(c -> run(c, (p, n) -> say(p, Component.literal("Your contract pays " + Diplomacy.collectContract(p, n.factionId()) + " Scrip.")))))
			.then(Commands.literal("votes").then(Commands.argument("topic", StringArgumentType.word()).executes(c -> {
				String t = StringArgumentType.getString(c, "topic");
				return run(c, (p, n) -> {
					try {
						var r = Oligarchy.tally(p.level().getServer(), n.factionId(), Oligarchy.Topic.valueOf(t.toUpperCase()));
						say(p, Component.literal("Council on " + t + ": yes " + r.yes() + ", no " + r.no() + ", abstain " + r.abstain() + ", delay " + r.delayDays() + " day(s)."));
					} catch (IllegalArgumentException e) {
						say(p, Component.literal("Topics: war, peace, alliance, hire."));
					}
				});
			})))
			.then(Commands.literal("bribe").then(Commands.argument("lord", StringArgumentType.word()).then(Commands.argument("topic", StringArgumentType.word()).then(Commands.argument("dir", StringArgumentType.word()).executes(c -> {
				String lord = StringArgumentType.getString(c, "lord"), t = StringArgumentType.getString(c, "topic"), dir = StringArgumentType.getString(c, "dir");
				return run(c, (p, n) -> {
					Ruler r = Ruler.byKey(lord);
					try {
						say(p, Component.literal(r == null ? "Unknown magnate." : "Bribe: " + Oligarchy.bribe(p, n.factionId(), r, Oligarchy.Topic.valueOf(t.toUpperCase()), dir.startsWith("u") || dir.startsWith("+") ? 1 : -1)));
					} catch (IllegalArgumentException e) {
						say(p, Component.literal("Topics: war, peace, alliance, hire."));
					}
				});
			})))))
			.then(Commands.literal("exchange").then(Commands.argument("from", StringArgumentType.word()).then(Commands.argument("to", StringArgumentType.word()).then(Commands.argument("amount", IntegerArgumentType.integer(1, 4096)).executes(c -> {
				String from = StringArgumentType.getString(c, "from"), to = StringArgumentType.getString(c, "to");
				int amount = IntegerArgumentType.getInteger(c, "amount");
				return run(c, (p, n) -> {
					Identifier f = Identifier.tryParse(from.contains(":") ? from : "elsewhere:" + from), t = Identifier.tryParse(to.contains(":") ? to : "elsewhere:" + to);
					say(p, Component.literal(n.role != Diplomacy.Role.CLERK || f == null || t == null ? "Only the Clerk exchanges coin." : "Exchange: " + Shops.exchange(p, f, t, amount)));
				});
			})))));
		dev.elsewhere.kingdom_court.trade.TradeUi.extend(root);
		d.register(root);
	}

	interface Act { void run(ServerPlayer p, DiplomatNpc n); }

	static int run(CommandContext<CommandSourceStack> c, Act a) {
		if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
		DiplomatNpc n = Diplomacy.npcNear(p, 8.0);
		if (n == null) {
			say(p, Component.literal("Find a Lawspeaker, Smith, Royal Envoy, Market Inspector, Caravan Master, Clerk or Auctioneer first."));
			return 0;
		}
		a.run(p, n);
		return 1;
	}

	static void say(ServerPlayer p, Component c) {
		p.sendSystemMessage(c);
	}

	static void shop(ServerPlayer p, DiplomatNpc n) {
		Identifier f = n.factionId();
		var offers = Shops.offers(p, f);
		if (offers.isEmpty()) {
			say(p, Component.literal("Nothing for sale."));
			return;
		}
		say(p, Component.literal("For sale (" + Factions.get(f).displayName().getString() + ", you hold " + Shops.count(p, f) + "):"));
		for (var o : offers) {
			Component line = Component.literal(" " + o.index() + ": ").append(o.stack().getHoverName()).append(Component.literal(" x" + o.stack().getCount() + " for " + o.price() + (o.remaining() >= 0 ? " (" + o.remaining() + " left this week)" : "")));
			say(p, line.copy().withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/diplomacy buy " + o.index()))));
		}
	}
}
