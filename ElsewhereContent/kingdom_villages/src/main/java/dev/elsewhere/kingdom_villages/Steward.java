package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom.item.KingdomItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The Royal Steward (villager profession {@code elsewhere:royal_steward}, job site: an unsealed Royal Coffer). A long-term
 * Crown sink, deliberately very expensive (user, binding). Prices above 64 Crowns are paid in Royal Purses (55 Crowns
 * each; the Steward exchanges them at par, no discount, no profit), because a trade slot holds one stack.
 *
 * <pre>
 * Novice      purse exchange (55 Crowns -> 1 Purse, at par), Aldermark banner pattern 20, Bodkin Bolts x8 27
 * Apprentice  Aldermark shield 27
 * Journeyman  Aldermark trim template 40
 * Expert      Oathkeeper's Seal 4 Purses (220), 2 per in-game week
 * Master      2 of 4 books (Sharpness V, Protection IV, Efficiency V, Unbreaking III): 3 Purses (165) + 3 emerald blocks,
 *             2 per week each; Netherite Upgrade Template 5 Purses (275) + 20 diamonds, 2 per week.
 *             Master needs at least 2 dead Lords ({@link #LORDS_DEAD}, set by the court lane).
 * </pre>
 * Floors: a Steward price only ever gets the 10% liberation discount once (rounded down so it never undercuts 90%), and
 * reputation / Hero of the Village never stack below that.
 */
public final class Steward {
	private Steward() {}

	public static final int CROWNS_PER_PURSE = 55;
	/** Up to 2 per in-game week for the Seal, each book and the template (user, 14:55). */
	public static final int WEEKLY_USES = 2;
	public static final long WEEK = 24000L * 7;
	/** Number of dead Lords. K-B: {@code Steward.LORDS_DEAD = srv -> CourtState.get(srv).deadLords();} */
	public static ToIntFunction<MinecraftServer> LORDS_DEAD = srv -> 0;

	// K-A registers every reward item (kingdom base 0.2.0, dev.elsewhere.kingdom.item.KingdomItems); never registered here.
	public static @Nullable Item crown() {
		return KingdomItems.ALDERMARK_CROWN;
	}

	public static @Nullable Item bodkin() {
		return KingdomItems.BODKIN_BOLT;
	}

	public static @Nullable Item seal() {
		return KingdomItems.OATHKEEPERS_SEAL;
	}

	public static @Nullable Item bannerPattern() {
		return KingdomItems.ALDERMARK_BANNER_PATTERN;
	}

	public static @Nullable Item trim() {
		return KingdomItems.ALDERMARK_TRIM_TEMPLATE;
	}

	public static boolean isSteward(Villager v) {
		return v.getVillagerData().profession().is(VillagesContent.STEWARD);
	}

	/** Weekly offers: the Seal, the books and the template. */
	public static boolean weekly(MerchantOffer o) {
		ItemStack r = o.getResult();
		Item s = seal();
		return r.is(Items.ENCHANTED_BOOK) || r.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) || s != null && r.is(s);
	}

	/** The listed price of an offer's first cost in Crowns (Purses count 64). */
	public static int listedCrowns(MerchantOffer o) {
		ItemStack a = o.getBaseCostA();
		return a.is(VillagesContent.ROYAL_PURSE) ? a.getCount() * CROWNS_PER_PURSE : a.getCount();
	}

	/** Lowest allowed price in Crowns after every discount: the listed price minus at most 10%. */
	public static int floorCrowns(MerchantOffer o) {
		return (int) Math.ceil(listedCrowns(o) * 0.9);
	}

	/** Effective price in Crowns right now. */
	public static int effectiveCrowns(MerchantOffer o) {
		ItemStack a = o.getCostA();
		return a.is(VillagesContent.ROYAL_PURSE) ? a.getCount() * CROWNS_PER_PURSE : a.getCount();
	}

	static boolean isExchange(MerchantOffer o) {
		return o.getResult().is(VillagesContent.ROYAL_PURSE);
	}

	/** Called from the Villager.updateSpecialPrices mixin (TAIL) for Stewards: replaces every other discount. */
	public static void applyPriceRules(Villager v) {
		for (MerchantOffer o : v.getOffers()) {
			int base = o.getBaseCostA().getCount();
			o.setSpecialPriceDiff(isExchange(o) ? 0 : -(int) Math.floor(base * 0.10));
		}
	}

	/** Called from the Villager.updateTrades mixin when a Steward reaches a new level (1-5). */
	public static void addTrades(Villager v, ServerLevel level) {
		MerchantOffers offers = v.getOffers();
		int lvl = v.getVillagerData().level();
		Item crown = crown();
		if (crown == null) return;
		Item purse = VillagesContent.ROYAL_PURSE;
		switch (lvl) {
			case 1 -> {
				offers.add(new MerchantOffer(new ItemCost(crown, CROWNS_PER_PURSE), Optional.empty(), new ItemStack(purse), 16, 1, 0.0F));
				Item bp = bannerPattern();
				if (bp != null) offers.add(new MerchantOffer(new ItemCost(crown, 20), Optional.empty(), new ItemStack(bp), 4, 10, 0.05F));
				Item b = bodkin();
				if (b != null) offers.add(new MerchantOffer(new ItemCost(crown, 27), Optional.empty(), new ItemStack(b, 8), 6, 8, 0.05F));
			}
			case 2 -> {
				ItemStack shield = KingdomItems.aldermarkShield(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
				offers.add(new MerchantOffer(new ItemCost(crown, 27), Optional.empty(), shield, 4, 15, 0.05F));
			}
			case 3 -> {
				Item t = trim();
				if (t != null) offers.add(new MerchantOffer(new ItemCost(crown, 40), Optional.empty(), new ItemStack(t), 2, 20, 0.05F));
			}
			case 4 -> {
				Item s = seal();
				if (s != null) offers.add(new MerchantOffer(new ItemCost(purse, 4), Optional.empty(), new ItemStack(s), WEEKLY_USES, 30, 0.0F));
			}
			case 5 -> {
				List<ResourceKey<Enchantment>> books = new ArrayList<>(List.of(Enchantments.SHARPNESS, Enchantments.PROTECTION, Enchantments.EFFICIENCY, Enchantments.UNBREAKING));
				int[] lv = {5, 4, 5, 3};
				List<Integer> idx = new ArrayList<>(List.of(0, 1, 2, 3));
				Collections.shuffle(idx, new java.util.Random(v.getUUID().getLeastSignificantBits()));
				var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
				for (int k = 0; k < 2; k++) {
					int i = idx.get(k);
					Holder<Enchantment> h = reg.getOrThrow(books.get(i));
					ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(h, lv[i]));
					offers.add(new MerchantOffer(new ItemCost(purse, 3), Optional.of(new ItemCost(Items.EMERALD_BLOCK, 3)), book, WEEKLY_USES, 30, 0.0F));
				}
				offers.add(new MerchantOffer(new ItemCost(purse, 5), Optional.of(new ItemCost(Items.DIAMOND, 20)), new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), WEEKLY_USES, 30, 0.0F));
			}
			default -> {}
		}
		VillageLedger l = VillageLedger.get(level);
		l.stewardWeekly.putIfAbsent(v.getUUID(), level.getGameTime());
		l.dirty();
	}

	// ---- weekly limits: vanilla restock resets every offer; weekly ones are put back unless a week has passed ----------

	private static @Nullable UUID restocking;
	private static int[] usedBefore = new int[0];

	public static void beforeRestock(Villager v) {
		restocking = v.getUUID();
		MerchantOffers o = v.getOffers();
		usedBefore = new int[o.size()];
		for (int i = 0; i < o.size(); i++) usedBefore[i] = o.get(i).getUses();
	}

	public static void afterRestock(Villager v) {
		if (!v.getUUID().equals(restocking) || !(v.level() instanceof ServerLevel level)) return;
		restocking = null;
		VillageLedger l = VillageLedger.get(level);
		long now = level.getGameTime();
		long start = l.stewardWeekly.getOrDefault(v.getUUID(), now);
		if (now - start >= WEEK) {
			l.stewardWeekly.put(v.getUUID(), now); // new week: weekly offers restocked
			l.dirty();
			return;
		}
		MerchantOffers o = v.getOffers();
		for (int i = 0; i < o.size() && i < usedBefore.length; i++) {
			if (!weekly(o.get(i))) continue;
			for (int k = 0; k < usedBefore[i] && o.get(i).getUses() < o.get(i).getMaxUses(); k++) o.get(i).increaseUses();
		}
	}

	/** Master needs 2+ dead Lords (the kingdom's treasury breaks open as its rulers fall). */
	public static boolean mayReachMaster(ServerLevel level) {
		return LORDS_DEAD.applyAsInt(level.getServer()) >= 2;
	}
}
