package dev.elsewhere.kingdom_court.standing;

import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.Overhaul3;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.trade.AzmarTrade;
import dev.elsewhere.kingdom_court.trade.HrafnholtTrade;
import dev.elsewhere.kingdom_court.trade.MirewickTrade;
import dev.elsewhere.kingdom_court.trade.Trade;
import dev.elsewhere.kingdom_court.trade.TradeData;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * POLISH P7, server half of the trader dialogue: a right-click on a trader (the existing {@code TradeUi.interact} menu path) sends one
 * {@link StandingNet.TraderOpen} and the client opens its dialogue screen over the chat menu, which is printed as before (the screen's
 * Wares button opens the chat on it). Nothing ticks and nothing is polled: the numbers are computed once, here, from the same rules the
 * trade code prices with. Kill switch {@code trader_screen} ({@link Overhaul3#TRADER_SCREEN}); a client without the module, a fake player or
 * the switch off gets the chat menu alone.
 */
public final class TraderDialogue {
	private TraderDialogue() {}

	/** Dev only: right-clicks that reached the dialogue hook (the self-test checks the hook fires once per menu and never on a haggle). */
	public static int attempts;

	/** Sends the dialogue to a real player whose client can show it. Returns true when a payload was sent. */
	public static boolean open(ServerPlayer p, DiplomatNpc n) {
		attempts++;
		if (!Overhaul3.TRADER_SCREEN || p instanceof FakePlayer || n.isRemoved()) return false;
		try {
			if (!ServerPlayNetworking.canSend(p, StandingNet.TraderOpen.TYPE)) return false;
			ServerPlayNetworking.send(p, snapshot(p, n));
			return true;
		} catch (RuntimeException e) {
			// never let the screen break the trade menu itself: the chat menu has already been printed
			dev.elsewhere.kingdom_court.CourtContent.LOG.warn("trader dialogue not sent: {}", e.toString());
			return false;
		}
	}

	/** The dialogue data for this player at this trader, from the live trade rules. */
	public static StandingNet.TraderOpen snapshot(ServerPlayer p, DiplomatNpc n) {
		Identifier f = n.factionId();
		ServerLevel l = (ServerLevel) p.level();
		int standing = Standing.get(l, p.getUUID(), f);
		double mult = Trade.standingDiscount(p, f);
		int flags = 0, detail = 0;
		if (Trade.hostile(p, f)) flags |= StandingNet.HOSTILE;
		if (Standing.locked(l, p.getUUID(), f)) flags |= StandingNet.OATH;
		TradeData.Mode mode = Trade.mode(l, f);
		switch (mode) {
			case THING -> {
				if (HrafnholtTrade.hasRing(p)) {
					flags |= StandingNet.RING;
					mult *= HrafnholtTrade.RING_DISCOUNT;
				}
				if (HrafnholtTrade.thingOpen(l)) flags |= StandingNet.THING_OPEN;
				detail = HrafnholtTrade.daysToThing(l);
			}
			case POSTED -> detail = (flags & StandingNet.HOSTILE) != 0 ? 0 : AzmarTrade.unlockedRank(p, f) + 1;
			case FLOATING -> {
				String trader = n.getUUID().toString();
				if (n.role == Diplomacy.Role.CLERK && MirewickTrade.haggled(l, p, trader)) {
					flags |= StandingNet.HAGGLED;
					mult *= 1.0 - MirewickTrade.HAGGLE_OFF;
				}
				if (MirewickTrade.locked(l, p, trader)) flags |= StandingNet.LOCKED;
				if (MirewickTrade.atWar(l, f)) {
					flags |= StandingNet.WAR;
					mult *= 1.0 + MirewickTrade.WAR_SURCHARGE;
				}
				boolean miser = MirewickTrade.isMiser(l.getServer(), f, n.patron());
				if (miser) flags |= StandingNet.MISER;
				detail = MirewickTrade.chancePercent(l, p, f, miser);
				MirewickTrade.Loan loan = MirewickTrade.loanOf(l, p.getUUID());
				if (loan != null) flags |= loan.late() ? StandingNet.OWES | StandingNet.LATE : StandingNet.OWES;
			}
			default -> {}
		}
		if (n.role == Diplomacy.Role.ENVOY && Diplomacy.offerAvailable(p, f)) flags |= StandingNet.OFFER;
		if (Diplomacy.hasPact(l, f, p.getUUID())) flags |= StandingNet.PACT;
		int pct = (int) Math.round(mult * 100.0);
		return new StandingNet.TraderOpen(n.getId(), StandingNet.clip(f.toString()), (byte) n.role.ordinal(), (byte) mode.ordinal(), Math.clamp(standing, -100, 100),
			(short) Math.clamp(pct, 1, 999), (short) flags, (byte) Math.clamp(detail, 0, 127));
	}
}
