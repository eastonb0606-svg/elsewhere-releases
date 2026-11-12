package dev.elsewhere.gear3.s3.totems;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * GEAR.md source "Budtender 12 Haze Resin + 4 Giant Fern": the Greenhaze Budtender sells the Bramble Totem, 2 per
 * Budtender (it never restocks), so it costs real Greenhaze farming instead of being a free summon. Added once, the
 * first time a player right-clicks a Budtender (Fabric UseEntityCallback, server side; the trade screen opens after);
 * the offer then lives in the Budtender's saved offers. Nothing when greenhaze_hush is absent or the kill switch is off.
 */
public final class TotemTrades {
	private TotemTrades() {}

	public static final Identifier BUDTENDER = Identifier.fromNamespaceAndPath("elsewhere", "budtender");
	public static final Identifier HAZE_RESIN = Identifier.fromNamespaceAndPath("elsewhere", "haze_resin");
	public static final Identifier GIANT_FERN = Identifier.fromNamespaceAndPath("elsewhere", "giant_fern");
	public static final int RESIN = 12, FERN = 4, MAX_USES = 2, XP = 15;

	private static boolean done;

	static void init() {
		if (done) return;
		done = true;
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && entity instanceof Merchant m && isBudtender(entity)) addTo(m.getOffers());
			return InteractionResult.PASS;
		});
	}

	public static boolean isBudtender(Entity e) {
		return BUDTENDER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
	}

	/** The Bramble Totem offer, or null when greenhaze_hush (resin / fern) or the totem item is missing. */
	public static MerchantOffer offer() {
		Item totem = Gear3Items.BY_ID.get(Totems.BRAMBLE_ID);
		if (totem == null || !BuiltInRegistries.ITEM.containsKey(HAZE_RESIN) || !BuiltInRegistries.ITEM.containsKey(GIANT_FERN)) return null;
		Item resin = BuiltInRegistries.ITEM.getValue(HAZE_RESIN), fern = BuiltInRegistries.ITEM.getValue(GIANT_FERN);
		return new MerchantOffer(new ItemCost(resin, RESIN), Optional.of(new ItemCost(fern, FERN)), new ItemStack(totem), MAX_USES, XP, 0.05F);
	}

	/** Adds the offer once (by result item). Returns true when it was added now. */
	public static boolean addTo(List<MerchantOffer> offers) {
		if (offers == null || !Totems.enabled) return false;
		Item totem = Gear3Items.BY_ID.get(Totems.BRAMBLE_ID);
		if (totem == null) return false;
		for (MerchantOffer o : offers) if (o.getResult().is(totem)) return false;
		MerchantOffer o = offer();
		if (o == null) return false;
		offers.add(o);
		return true;
	}

	static Identifier id(String path) {
		return Gear3Content.id(path);
	}
}
