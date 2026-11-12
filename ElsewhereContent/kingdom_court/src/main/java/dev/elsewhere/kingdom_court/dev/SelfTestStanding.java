package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.Overhaul3;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.kingdom_court.standing.StandingNet;
import dev.elsewhere.kingdom_court.standing.StandingQuery;
import dev.elsewhere.kingdom_court.standing.StandingText;
import dev.elsewhere.kingdom_court.standing.TraderDialogue;
import dev.elsewhere.kingdom_court.trade.HrafnholtTrade;
import dev.elsewhere.kingdom_court.trade.MirewickTrade;
import dev.elsewhere.kingdom_court.trade.Trade;
import dev.elsewhere.kingdom_court.trade.TradeData;
import dev.elsewhere.kingdom_court.trade.TradeItems;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/**
 * s-p7-standing (POLISH P7): the trader dialogue's data and the Standing chip's query, server side. The screen and the chip themselves are
 * client-only and are checked by the SHOTS-3.0 lines; here: the payloads fit the 64-byte budget and round-trip, the dialogue hook fires
 * once per menu (never on a haggle or a quick bid), the numbers match the trade rules of all three cultures, the query answers only what
 * it should, both kill switches remove their feature, and every lang key and GUI sprite exists.
 */
final class SelfTestStanding {
	private SelfTestStanding() {}

	static Identifier id(String p) {
		return Identifier.fromNamespaceAndPath("elsewhere", p);
	}

	static final Identifier PT = id("p7_thing"), PP = id("p7_posted"), PF = id("p7_float");

	static void add() {
		STEPS.add((srv, l) -> {
			payloads(srv);
			return 1;
		});
		STEPS.add((srv, l) -> {
			setup();
			try {
				hook(l);
				posted(l);
				thing(l);
				floating(l);
			} finally {
				MirewickTrade.warProvider = WAR0;
				Trade.testOffset = 0;
			}
			return 1;
		});
		STEPS.add((srv, l) -> {
			query(l);
			return 1;
		});
		STEPS.add((srv, l) -> {
			resources();
			return 1;
		});
	}

	// ---- payloads ------------------------------------------------------------------------------------------------------------

	static <T> int roundTrip(MinecraftServer srv, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value, boolean[] same) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), srv.registryAccess());
		codec.encode(buf, value);
		int n = buf.readableBytes();
		same[0] = value.equals(codec.decode(buf)) && buf.readableBytes() == 0;
		return n;
	}

	static void payloads(MinecraftServer srv) {
		String longest = "elsewhere:" + "x".repeat(StandingNet.MAX_FACTION - "elsewhere:".length());
		boolean[] same = {false};
		var open = new StandingNet.TraderOpen(Integer.MAX_VALUE, longest, (byte) 6, (byte) 3, -100, (short) 999, (short) 0xFFF, (byte) 127);
		int n = roundTrip(srv, StandingNet.TraderOpen.CODEC, open, same);
		check("P7 dialogue payload: the worst case round-trips and stays under 64 bytes", same[0] && n < StandingNet.MAX_BYTES, n + " bytes");
		boolean[] s2 = {false}, s3 = {false};
		int r = roundTrip(srv, StandingNet.Reply.CODEC, new StandingNet.Reply(Integer.MAX_VALUE, longest, -100, (byte) 7), s2);
		int q = roundTrip(srv, StandingNet.Query.CODEC, new StandingNet.Query(Integer.MAX_VALUE), s3);
		check("P7 Standing reply and query payloads round-trip under 64 bytes", s2[0] && s3[0] && r < StandingNet.MAX_BYTES && q < StandingNet.MAX_BYTES, "reply " + r + ", query " + q + " bytes");
	}

	// ---- the dialogue numbers -------------------------------------------------------------------------------------------------

	static java.util.function.BiPredicate<ServerLevel, Identifier> WAR0;
	static int npcs;

	static void setup() {
		WAR0 = MirewickTrade.warProvider;
		FactionDefs.putCodeDefined(PT, SelfTestTrade.def("P7 Thing", TestCoins.SCRIP));
		FactionDefs.putCodeDefined(PP, SelfTestTrade.def("P7 Posted", TestCoins.DINAR));
		FactionDefs.putCodeDefined(PF, SelfTestTrade.def("P7 Float", TestCoins.SCRIP));
		TradeData.put(PT, new TradeData.Stock(TradeData.Mode.THING, SelfTestTrade.thingLines(), List.of(), List.of(), TradeData.defaultGoods()));
		TradeData.put(PP, new TradeData.Stock(TradeData.Mode.POSTED, SelfTestTrade.postedLines(), List.of(), List.of(), TradeData.defaultGoods()));
		TradeData.put(PF, new TradeData.Stock(TradeData.Mode.FLOATING, SelfTestTrade.floatLines(), List.of(), SelfTestTrade.lotPool(), TradeData.defaultGoods()));
	}

	static DiplomatNpc npc(ServerLevel l, Diplomacy.Role r, Identifier f) {
		DiplomatNpc n = r.type.create(l, EntitySpawnReason.COMMAND);
		n.snapTo(2.5, SelfTest.y, 0.5 + (npcs++ % 8), 0F, 0F);
		n.setFaction(f);
		l.addFreshEntity(n);
		return n;
	}

	static FakePlayer player(ServerLevel l, String name, Identifier f, int standing) {
		return SelfTestTrade.fresh(l, "p7-" + name, f, standing);
	}

	static void hook(ServerLevel l) {
		DiplomatNpc clerk = npc(l, Diplomacy.Role.CLERK, PF), auct = npc(l, Diplomacy.Role.AUCTIONEER, PF);
		try {
			FakePlayer p = player(l, "hook", PF, 0);
			int a0 = TraderDialogue.attempts;
			dev.elsewhere.kingdom_court.trade.TradeUi.interact(p, clerk);
			int plain = TraderDialogue.attempts - a0;
			p.setShiftKeyDown(true);
			dev.elsewhere.kingdom_court.trade.TradeUi.interact(p, clerk);
			p.setShiftKeyDown(false);
			int haggle = TraderDialogue.attempts - a0 - plain;
			net.minecraft.world.item.Item cur = Shops.currency(PF);
			if (cur != null) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(cur, 8));
			dev.elsewhere.kingdom_court.trade.TradeUi.interact(p, auct);
			int bid = TraderDialogue.attempts - a0 - plain - haggle;
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			check("P7 right-click reaches the dialogue hook once; a sneak-haggle and an Auctioneer quick-bid do not", cur != null && plain == 1 && haggle == 0 && bid == 0, plain + "/" + haggle + "/" + bid + " (currency " + cur + ")");
			boolean saved = Overhaul3.TRADER_SCREEN;
			int s0 = TraderDialogue.passedSwitch;
			boolean sentOn = TraderDialogue.open(p, clerk);
			int on = TraderDialogue.passedSwitch - s0;
			Overhaul3.TRADER_SCREEN = false;
			boolean sentOff;
			try {
				sentOff = TraderDialogue.open(p, clerk);
			} finally {
				Overhaul3.TRADER_SCREEN = saved;
			}
			int off = TraderDialogue.passedSwitch - s0 - on;
			check("P7 kill switch trader_screen: off stops the dialogue before anything is built; a fake player never gets one", saved && on == 1 && off == 0 && !sentOn && !sentOff, on + "/" + off);
		} finally {
			clerk.discard();
			auct.discard();
		}
	}

	static void posted(ServerLevel l) {
		DiplomatNpc insp = npc(l, Diplomacy.Role.INSPECTOR, PP);
		try {
			FakePlayer p = player(l, "posted", PP, 0);
			var neutral = TraderDialogue.snapshot(p, insp);
			KingdomApi.setStanding(p, PP, 40);
			var friendly = TraderDialogue.snapshot(p, insp);
			KingdomApi.setStanding(p, PP, 80);
			var allied = TraderDialogue.snapshot(p, insp);
			KingdomApi.setStanding(p, PP, -80);
			var hostile = TraderDialogue.snapshot(p, insp);
			check("P7 posted prices: Neutral 100% with 1 tier, Friendly 95% with 2, Allied 85% with all 3",
				neutral.pricePct() == 100 && neutral.detail() == 1 && friendly.pricePct() == 95 && friendly.detail() == 2 && allied.pricePct() == 85 && allied.detail() == 3
					&& allied.standing() == 80 && allied.mode() == (byte) TradeData.Mode.POSTED.ordinal() && allied.role() == (byte) Diplomacy.Role.INSPECTOR.ordinal(),
				neutral.pricePct() + "/" + neutral.detail() + " " + friendly.pricePct() + "/" + friendly.detail() + " " + allied.pricePct() + "/" + allied.detail());
			check("P7 a Hostile player gets the refuse flag and no open tier", hostile.has(StandingNet.HOSTILE) && hostile.detail() == 0 && !allied.has(StandingNet.HOSTILE), hostile.flags());
			check("P7 dialogue carries the trader's entity id and faction", allied.entity() == insp.getId() && allied.faction().equals(PP.toString()), allied.faction());
		} finally {
			insp.discard();
		}
	}

	static void thing(ServerLevel l) {
		DiplomatNpc law = npc(l, Diplomacy.Role.LAWSPEAKER, PT);
		try {
			FakePlayer p = player(l, "thing", PT, 40);
			SelfTestTrade.setTime(l, 90, 6100); // a Thing-day morning
			var plain = TraderDialogue.snapshot(p, law);
			p.getInventory().add(new ItemStack(TradeItems.ARM_RING));
			var ring = TraderDialogue.snapshot(p, law);
			int want = (int) Math.round(Trade.standingDiscount(p, PT) * HrafnholtTrade.RING_DISCOUNT * 100.0);
			check("P7 Thing: Friendly pays 95%, with an Arm-Ring 85-86% and the ring flag",
				plain.pricePct() == 95 && !plain.has(StandingNet.RING) && ring.has(StandingNet.RING) && ring.pricePct() == want && want >= 85 && want <= 86, plain.pricePct() + " -> " + ring.pricePct());
			SelfTestTrade.setTime(l, 91, 6100);
			var closed = TraderDialogue.snapshot(p, law);
			check("P7 Thing hours: open flag on a Thing-day morning, closed the next day with the days to wait",
				ring.has(StandingNet.THING_OPEN) && ring.detail() == 0 && !closed.has(StandingNet.THING_OPEN) && closed.detail() == HrafnholtTrade.daysToThing(l) && closed.detail() > 0, closed.detail());
		} finally {
			Trade.testOffset = 0;
			law.discard();
		}
	}

	static void floating(ServerLevel l) {
		DiplomatNpc clerk = npc(l, Diplomacy.Role.CLERK, PF);
		try {
			FakePlayer p = player(l, "float", PF, 0);
			MirewickTrade.warProvider = (lv, f) -> f.equals(PF);
			var base = TraderDialogue.snapshot(p, clerk);
			boolean won = MirewickTrade.haggle(p, PF, clerk.getUUID().toString(), false, SelfTestTrade.fixed(0)) == MirewickTrade.Haggle.SUCCESS;
			var haggled = TraderDialogue.snapshot(p, clerk);
			check("P7 floating: war +20% (120%), haggled at war 96%, haggle chance 30 at Standing 0",
				won && base.pricePct() == 120 && base.has(StandingNet.WAR) && base.detail() == 30 && haggled.has(StandingNet.HAGGLED) && haggled.pricePct() == 96,
				base.pricePct() + " -> " + haggled.pricePct() + ", chance " + base.detail());
			FakePlayer q = player(l, "float-lock", PF, 0);
			MirewickTrade.haggle(q, PF, clerk.getUUID().toString(), false, SelfTestTrade.fixed(99));
			var locked = TraderDialogue.snapshot(q, clerk);
			boolean lent = MirewickTrade.borrow(q, PF, 10) == Trade.Result.OK;
			var owes = TraderDialogue.snapshot(q, clerk);
			check("P7 floating: a failed haggle sets the locked flag; a loan sets the owes flag", locked.has(StandingNet.LOCKED) && !locked.has(StandingNet.HAGGLED) && lent && owes.has(StandingNet.OWES) && !owes.has(StandingNet.LATE),
				locked.flags() + " " + owes.flags());
			if (lent) {
				Shops.give(q, PF, 8); // the interest
				MirewickTrade.repay(q);
			}
		} finally {
			MirewickTrade.warProvider = WAR0;
			clerk.discard();
		}
	}

	// ---- the chip's query -------------------------------------------------------------------------------------------------------

	static void query(ServerLevel l) {
		CourtRuler ruler = Ruler.MARSHAL.type == null ? null : (CourtRuler) Ruler.MARSHAL.type.create(l, EntitySpawnReason.COMMAND);
		if (ruler == null) {
			check("P7 Standing query: a ruler in range is answered with the live Standing and kingdom, no attack flag at Friendly", false, "no marshal type");
			return;
		}
		DiplomatNpc other = npc(l, Diplomacy.Role.SMITH, PT);
		try {
			ruler.snapTo(4.5, SelfTest.y, 4.5, 0F, 0F);
			ruler.setFaction(PP);
			ruler.setNoAi(true);
			l.addFreshEntity(ruler);
			FakePlayer p = player(l, "chip", PP, 45);
			StandingQuery.resetForTest(p);
			var r1 = StandingQuery.answer(p, ruler.getId());
			var r2 = StandingQuery.answer(p, ruler.getId());
			check("P7 Standing query: a ruler in range is answered with the live Standing and kingdom, no attack flag at Friendly",
				r1 != null && r1.standing() == 45 && r1.standing() == Standing.get(l, p.getUUID(), PP) && r1.faction().equals(PP.toString()) && !r1.has(StandingNet.R_ATTACKS) && r1.entity() == ruler.getId(),
				r1 == null ? "null" : r1.standing() + " " + r1.faction());
			StandingQuery.resetForTest(p);
			KingdomApi.setStanding(p, PP, -80);
			var hostile = StandingQuery.answer(p, ruler.getId());
			StandingQuery.resetForTest(p);
			var notRuler = StandingQuery.answer(p, other.getId());
			ruler.snapTo(4.5 + StandingQuery.RANGE + 8, SelfTest.y, 4.5, 0F, 0F);
			var far = StandingQuery.answer(p, ruler.getId());
			ruler.snapTo(4.5, SelfTest.y, 4.5, 0F, 0F);
			StandingQuery.resetForTest(p);
			boolean saved = Overhaul3.STANDING_CHIP;
			StandingNet.Reply off;
			Overhaul3.STANDING_CHIP = false;
			try {
				off = StandingQuery.answer(p, ruler.getId());
			} finally {
				Overhaul3.STANDING_CHIP = saved;
			}
			check("P7 Standing query: Hostile is answered with the attack flag", hostile != null && hostile.standing() == -80 && hostile.has(StandingNet.R_ATTACKS), hostile == null ? "null" : hostile.flags());
			check("P7 Standing query refused: twice within 10 ticks, not a ruler, beyond 48 blocks", r2 == null && notRuler == null && far == null, (r2 == null) + " " + (notRuler == null) + " " + (far == null));
			check("P7 kill switch standing_chip: off answers no query (on, the same query is answered)", saved && r1 != null && off == null, null);
		} finally {
			ruler.discard();
			other.discard();
		}
	}

	// ---- lang and art ---------------------------------------------------------------------------------------------------------------

	static Set<String> langKeys() throws Exception {
		Set<String> keys = new HashSet<>();
		Enumeration<URL> all = SelfTestStanding.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
		while (all.hasMoreElements()) {
			try (InputStream in = all.nextElement().openStream()) {
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				keys.addAll(o.keySet());
			}
		}
		return keys;
	}

	static void resources() {
		try {
			Set<String> keys = langKeys();
			List<String> missing = new ArrayList<>();
			for (String k : StandingText.allKeys()) if (!keys.contains(k)) missing.add(k);
			for (Diplomacy.Role r : Diplomacy.Role.values()) if (!keys.contains("entity.elsewhere." + r.path)) missing.add("entity.elsewhere." + r.path);
			check("P7 lang: every dialogue and chip key exists (" + StandingText.allKeys().size() + " keys, greetings for 7 traders x 5 bands, band names for 4 kingdoms)", missing.isEmpty(), missing.isEmpty() ? null : missing.size() + " missing, e.g. " + missing.get(0));
		} catch (Exception e) {
			check("P7 lang: every dialogue and chip key exists", false, e);
		}
		List<String> art = new ArrayList<>();
		ClassLoader cl = SelfTestStanding.class.getClassLoader();
		for (String k : StandingText.KINGDOMS) for (String what : new String[] {"frame", "chip", "crest"}) {
			String base = "assets/elsewhere/textures/gui/sprites/standing/" + what + "_" + k + ".png";
			if (cl.getResource(base) == null) art.add(base);
			if (!what.equals("crest")) {
				try (InputStream in = cl.getResourceAsStream(base + ".mcmeta")) {
					String meta = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
					if (!meta.contains("nine_slice")) art.add(base + ".mcmeta");
				} catch (Exception e) {
					art.add(base + ".mcmeta");
				}
			}
		}
		check("P7 art: 12 standing GUI sprites (frame, chip, crest per kingdom) present, frames and chips nine-sliced", art.isEmpty(), art.isEmpty() ? null : art);
		info("P7 kill switches: trader_screen " + Overhaul3.TRADER_SCREEN + ", standing_chip " + Overhaul3.STANDING_CHIP);
	}
}
