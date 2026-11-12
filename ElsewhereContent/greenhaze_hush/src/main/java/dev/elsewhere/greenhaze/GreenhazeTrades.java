package dev.elsewhere.greenhaze;

import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Budtender trades (DIMENSIONS.md 7: seeds, strains, Lucid Water; Haze Resin is the currency; Grumblegut Tusk is top currency). */
public final class GreenhazeTrades {
	private GreenhazeTrades() {}

	/**
	 * 2.0 gear trades. The items belong to the summoner and gear2 modules, so they are looked up by id and skipped when that
	 * module is absent. Sporebloom Staff (gear-summoner-a, Percolax also drops it): 32 Haze Resin + 4 Hazeborn Buds.
	 * Termite Hive Lantern (gear-areaminer-b): 24 Haze Resin + a lantern (gear2 notes).
	 */
	public static java.util.List<MerchantOffer> gearOffers(java.util.function.Function<String, java.util.Optional<net.minecraft.world.item.Item>> items) {
		java.util.List<MerchantOffer> out = new java.util.ArrayList<>();
		items.apply("sporebloom_staff").ifPresent(staff ->
			out.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 32), java.util.Optional.of(new ItemCost(GreenhazeItems.HAZEBORN_BUD, 4)),
				new ItemStack(staff), 1, 30, 0.05F)));
		items.apply("termite_hive_lantern").ifPresent(lantern ->
			out.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 24), java.util.Optional.of(new ItemCost(Items.LANTERN, 1)),
				new ItemStack(lantern), 1, 30, 0.05F)));
		return out;
	}

	static void budtender(WanderingTrader trader, MerchantOffers offers) {
		if (!offers.isEmpty()) return;
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 2), new ItemStack(GreenhazeItems.LUCID_WATER, 1), 16, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 6), new ItemStack(GreenhazeItems.HOTBOX_LANTERN, 1), 4, 10, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 4), new ItemStack(GreenhazeItems.HAZEBORN_BUD, 1), 8, 5, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(GreenhazeItems.HAZE_RESIN, 3), 12, 1, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 3), new ItemStack(GreenhazeItems.MUNCHIE_BACON, 2), 12, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 2), new ItemStack(Items.FERN, 4), 16, 1, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.GRUMBLEGUT_TUSK, 1), new ItemStack(GreenhazeItems.MUSIC_DISC_FOUR_TWENTY, 1), 1, 20, 0.05F));
		// ARSENAL.md rev 3: Herding Crook (summoner module's item), 16 Haze Resin + 2 Trichome Quartz; skipped if not registered
		net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(dev.elsewhere.greenhaze_hush.Reg.id("herding_crook")).ifPresent(crook ->
			offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 16), java.util.Optional.of(new ItemCost(GreenhazeItems.TRICHOME_QUARTZ, 2)),
				new ItemStack(crook), 3, 15, 0.05F)));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.GRUMBLEGUT_TUSK, 1), new ItemStack(GreenhazeItems.HAZEBORN_BUD, 8), 4, 15, 0.05F));
		offers.addAll(gearOffers(id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(dev.elsewhere.greenhaze_hush.Reg.id(id))));
		// 2.0 second sources for the generated plants and ore (Haze Resin is the dimension currency).
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 3), new ItemStack(GreenhazeBlocks.GIANT_FERN, 2), 8, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 3), new ItemStack(GreenhazeBlocks.LUCID_LILY, 2), 8, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 2), new ItemStack(GreenhazeItems.MUNCHFRUIT, 3), 8, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GreenhazeItems.HAZE_RESIN, 6), new ItemStack(GreenhazeBlocks.HAZE_VENT, 1), 4, 5, 0.05F));
		// Explorer maps (survival players can't /locate): the two Greenhaze boss sites.
		if (trader.level() instanceof net.minecraft.server.level.ServerLevel sl) {
			var own = sl.dimension();
			dev.elsewhere.greenhaze_hush.GHMaps.offer(sl, trader.blockPosition(), own, "great_hotbox", "The Great Hotbox (Grumblegut)", 12).ifPresent(offers::add);
			dev.elsewhere.greenhaze_hush.GHMaps.offer(sl, trader.blockPosition(), own, "bonfire_terrace", "The Bonfire Terrace (Percolax)", 12).ifPresent(offers::add);
		}
	}
}
