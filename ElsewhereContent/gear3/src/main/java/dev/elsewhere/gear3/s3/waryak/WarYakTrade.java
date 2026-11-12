package dev.elsewhere.gear3.s3.waryak;

import dev.elsewhere.gear3.Gear3Items;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * GEAR.md source "Quartermaster rank 5": the Ironpeak Clan Quartermaster (verdigris_ironpeak) sells the War-Yak Horn
 * from clan rank 5, like its other rank-5 wares (also gated on 700 ng/dL when Test Meter is installed): 8 Skyiron + a
 * goat horn, once per restock. Appended by {@code mixin.waryak.QuartermasterOffersMixin} to the offers the Quartermaster
 * builds for each customer; only loaded when verdigris_ironpeak is present.
 */
public final class WarYakTrade {
	private WarYakTrade() {}

	public static final int RANK = 5, SKYIRON = 8, MAX_USES = 1, XP = 30;

	public static boolean sells(ServerPlayer p) {
		if (!WarYak.ENABLED || p == null) return false;
		if (dev.elsewhere.ironpeak.Ironpeak.rank(p) < RANK) return false;
		return !dev.elsewhere.verdigris_ironpeak.compat.TestMeterBridge.available() || dev.elsewhere.verdigris_ironpeak.compat.TestMeterBridge.total(p) >= 700;
	}

	public static MerchantOffer offer() {
		Item horn = Gear3Items.get(WarYak.HORN_ID);
		return new MerchantOffer(new ItemCost(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON, SKYIRON), Optional.of(new ItemCost(Items.GOAT_HORN)),
			new ItemStack(horn), MAX_USES, XP, 0.05F);
	}

	/** Adds the offer once (both overloads of quartermasterOffers may run for one customer). */
	public static void addTo(List<MerchantOffer> offers, ServerPlayer p) {
		if (offers == null || !sells(p)) return;
		Item horn = Gear3Items.BY_ID.get(WarYak.HORN_ID);
		if (horn == null) return;
		for (MerchantOffer o : offers) if (o.getResult().is(horn)) return;
		offers.add(offer());
	}
}
