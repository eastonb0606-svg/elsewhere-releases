package dev.elsewhere.gear3.s3.reliquary;

import dev.elsewhere.gear3.Gear3Items;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * The Mute Archivist's Echo Reliquary offer (GEAR.md A5: "Mute Archivist (Silent Monastery)"): 32 Resonant Shards and a
 * Cantor's Bell Shard, once per Archivist. The Archivist's own list lives in the Hush module (HushTrades); this adds one
 * offer the first time a player opens an Archivist's trades (old worlds' Archivists included), the event fires only on
 * that click. The offer is saved with the Archivist like the others, so it is added at most once.
 */
public final class ReliquaryTrade {
	private ReliquaryTrade() {}

	public static final Identifier ARCHIVIST = Identifier.fromNamespaceAndPath("elsewhere", "mute_archivist");
	public static final Identifier RESONANT_SHARD = Identifier.fromNamespaceAndPath("elsewhere", "resonant_shard");
	public static final Identifier BELL_SHARD = Identifier.fromNamespaceAndPath("elsewhere", "cantors_bell_shard");
	private static boolean hooked;

	public static void init() {
		if (hooked) return;
		hooked = true;
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && entity instanceof AbstractVillager v && isArchivist(entity)) ensureOffer(v);
			return InteractionResult.PASS; // the Archivist's own trading screen opens as usual
		});
	}

	public static boolean isArchivist(Entity e) {
		return ARCHIVIST.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
	}

	/** Server only. Adds the offer unless the switch is off, the Hush items are missing, or it is already there. True if added. */
	public static boolean ensureOffer(AbstractVillager v) {
		if (!EchoReliquary.enabled || v.level().isClientSide()) return false;
		Item relic = Gear3Items.BY_ID.get(EchoReliquary.ITEM_ID);
		Optional<Item> shard = item(RESONANT_SHARD), bell = item(BELL_SHARD);
		if (relic == null || shard.isEmpty() || bell.isEmpty()) return false;
		MerchantOffers offers = v.getOffers();
		if (has(offers, relic)) return false;
		offers.add(offer(relic, shard.get(), bell.get()));
		return true;
	}

	static MerchantOffer offer(Item relic, Item shard, Item bell) {
		return new MerchantOffer(new ItemCost(shard, EchoReliquary.TRADE_RESONANT_SHARDS), Optional.of(new ItemCost(bell, EchoReliquary.TRADE_BELL_SHARDS)),
			new ItemStack(relic), EchoReliquary.TRADE_USES, EchoReliquary.TRADE_XP, EchoReliquary.TRADE_PRICE_MULT);
	}

	public static boolean has(MerchantOffers offers, Item relic) {
		for (MerchantOffer o : offers) if (o.getResult().is(relic)) return true;
		return false;
	}

	public static int count(MerchantOffers offers, Item relic) {
		int n = 0;
		for (MerchantOffer o : offers) if (o.getResult().is(relic)) n++;
		return n;
	}

	static Optional<Item> item(Identifier id) {
		return BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(BuiltInRegistries.ITEM.getValue(id)) : Optional.empty();
	}
}
