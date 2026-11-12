package dev.elsewhere.kingdom_court.api;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Currency and shop per faction (RIVALS-DECISIONS 6). Data comes from the faction file's {@code economy}
 * ({@code currency}, {@code shop [{item, count, price, extra, weekly_limit}]}); this class does the rest: Standing discounts,
 * weekly limits counted per player and per faction, Standing gains for trading and tribute (with their weekly caps) and
 * Mirewick's exchange. Villages (the Steward equivalents) and the court's NPCs both call it.
 */
public final class Shops {
	private Shops() {}

	public static final long WEEK_TICKS = 24000L * 7;
	public static final int TRADE_STANDING_CAP = Standing.WEEKLY_TRADE_CAP, TRIBUTE_STANDING_CAP = Standing.WEEKLY_TRIBUTE_CAP;
	/** Only this faction's Counting-House swaps currencies. */
	public static Identifier exchangeFaction = Identifier.fromNamespaceAndPath("elsewhere", "mirewick");

	public enum Result { OK, NO_SHOP, NO_SUCH_LINE, WEEKLY_LIMIT, CANT_AFFORD, NO_ITEM, HOSTILE, FALLEN, NOT_FRIENDLY, TOO_SMALL, NOT_EXCHANGE }

	/** One shop line as this player sees it. {@code price} is after the Standing discount; {@code remaining} is -1 when unlimited. */
	public record Offer(int index, ItemStack stack, int basePrice, int price, List<ItemStack> extra, int weeklyLimit, int remaining) {}

	public static @Nullable Identifier currencyId(Identifier faction) {
		FactionDef d = Factions.def(faction);
		if (d != null && d.economy().currency().isPresent()) return d.economy().currency().get();
		return Factions.ALDERMARK.equals(faction) ? Identifier.fromNamespaceAndPath("elsewhere", "aldermark_crown") : null;
	}

	public static @Nullable Item currency(Identifier faction) {
		Identifier id = currencyId(faction);
		return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
	}

	public static int count(ServerPlayer p, Identifier faction) {
		Item c = currency(faction);
		return c == null ? 0 : countItem(p, c);
	}

	static int countItem(ServerPlayer p, Item c) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(c)) n += s.getCount();
		}
		return n;
	}

	/** Removes {@code amount} coins of the faction's currency; false (and nothing removed) when the player has fewer. */
	public static boolean take(ServerPlayer p, Identifier faction, int amount) {
		Item c = currency(faction);
		if (c == null || amount < 0 || countItem(p, c) < amount) return false;
		int left = amount;
		for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.is(c)) continue;
			int k = Math.min(left, s.getCount());
			s.shrink(k);
			left -= k;
		}
		return true;
	}

	public static void give(ServerPlayer p, ItemStack s) {
		if (s.isEmpty()) return;
		ItemStack c = s.copy();
		if (!p.getInventory().add(c) && !c.isEmpty()) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), c));
	}

	public static void give(ServerPlayer p, Identifier faction, int amount) {
		Item c = currency(faction);
		if (c == null) return;
		int left = amount;
		while (left > 0) {
			int k = Math.min(left, c.getDefaultMaxStackSize());
			give(p, new ItemStack(c, k));
			left -= k;
		}
	}

	/** Price multiplier by Standing band: Friendly 0.95, Allied 0.85 (the war lane's {@link Standing#shopDiscount}). */
	public static double discount(ServerPlayer p, Identifier faction) {
		return 1.0 - Standing.shopDiscount((ServerLevel) p.level(), p.getUUID(), faction);
	}

	static long week(ServerLevel level) {
		return level.getDefaultClockTime() / WEEK_TICKS;
	}

	static int usedThisWeek(CourtState st, ServerLevel level, String key) {
		long v = st.ledger.getOrDefault(key, Long.MIN_VALUE);
		return v != Long.MIN_VALUE && Math.floorDiv(v, 1000L) == week(level) ? (int) Math.floorMod(v, 1000L) : 0;
	}

	static void addUsed(CourtState st, ServerLevel level, String key, int n) {
		st.ledger.put(key, week(level) * 1000 + Math.min(999, usedThisWeek(st, level, key) + n));
		st.touch();
	}

	public static List<Offer> offers(ServerPlayer p, Identifier faction) {
		List<Offer> out = new ArrayList<>();
		FactionDef d = Factions.def(faction);
		if (d == null) return out;
		ServerLevel level = (ServerLevel) p.level();
		CourtState st = CourtState.get(level, faction);
		double disc = discount(p, faction);
		int i = 0;
		for (FactionDef.ShopEntry e : d.economy().shop()) {
			int idx = i++;
			if (!BuiltInRegistries.ITEM.containsKey(e.item())) continue;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(e.item()), e.count());
			List<ItemStack> extra = new ArrayList<>();
			for (FactionDef.Drop x : e.extra()) if (BuiltInRegistries.ITEM.containsKey(x.item())) extra.add(new ItemStack(BuiltInRegistries.ITEM.getValue(x.item()), Math.max(1, x.min())));
			int rem = e.weeklyLimit() <= 0 ? -1 : Math.max(0, e.weeklyLimit() - usedThisWeek(st, level, "shop|" + p.getUUID() + "|" + idx));
			out.add(new Offer(idx, stack, e.price(), Math.max(1, (int) Math.round(e.price() * disc)), extra, e.weeklyLimit(), rem));
		}
		return out;
	}

	/** Buys shop line {@code index} (the index in the faction file's shop list, as {@link Offer#index()}). One trade gives Standing +1 (cap +5 per week). */
	public static Result buy(ServerPlayer p, Identifier faction, int index) {
		FactionDef d = Factions.def(faction);
		if (d == null || d.economy().shop().isEmpty()) return Result.NO_SHOP;
		ServerLevel level = (ServerLevel) p.level();
		CourtState st = CourtState.get(level, faction);
		if (Standing.bandOf(level, p.getUUID(), faction) == Standing.Band.HOSTILE) return Result.HOSTILE;
		Offer o = null;
		for (Offer x : offers(p, faction)) if (x.index() == index) o = x;
		if (o == null) return Result.NO_SUCH_LINE;
		if (o.remaining() == 0) return Result.WEEKLY_LIMIT;
		if (currency(faction) == null) return Result.NO_ITEM;
		if (!take(p, faction, o.price())) return Result.CANT_AFFORD;
		give(p, o.stack());
		for (ItemStack x : o.extra()) give(p, x);
		if (o.weeklyLimit() > 0) addUsed(st, level, "shop|" + p.getUUID() + "|" + index, 1);
		Standing.onTrade(p, faction);
		return Result.OK;
	}

	/** Tribute in the faction's own currency: the coins are taken, Standing +1 per coin (cap +15 per week, the war lane's {@link Standing#onTribute}). Returns the Standing gained (-1 when the player lacks the coins). */
	public static int tribute(ServerPlayer p, Identifier faction, int coins) {
		if (coins <= 0 || !take(p, faction, coins)) return -1;
		return Standing.onTribute(p, faction, coins);
	}

	/**
	 * Mirewick's exchange: any currency becomes the exchange faction's currency (Scrip) at 4:3 (4 in, 3 out, whole units), and Scrip becomes any other
	 * currency 1:1 while the player is Friendly or better with the exchange faction. {@code amount} is what the player pays in {@code from}.
	 */
	public static Result exchange(ServerPlayer p, Identifier from, Identifier to, int amount) {
		if (!exchangeFaction.equals(from) && !exchangeFaction.equals(to)) return Result.NOT_EXCHANGE;
		if (from.equals(to) || currency(from) == null || currency(to) == null) return Result.NO_ITEM;
		ServerLevel level = (ServerLevel) p.level();
		if (exchangeFaction.equals(to)) {
			int units = amount / 4;
			if (units < 1) return Result.TOO_SMALL;
			if (!take(p, from, units * 4)) return Result.CANT_AFFORD;
			give(p, to, units * 3);
			return Result.OK;
		}
		Standing.Band b = Standing.bandOf(level, p.getUUID(), exchangeFaction);
		if (b != Standing.Band.FRIENDLY && b != Standing.Band.ALLIED) return Result.NOT_FRIENDLY;
		if (amount < 1) return Result.TOO_SMALL;
		if (!take(p, from, amount)) return Result.CANT_AFFORD;
		give(p, to, amount);
		return Result.OK;
	}
}
