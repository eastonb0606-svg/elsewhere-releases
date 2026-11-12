package dev.elsewhere.petalwind;

import dev.elsewhere.petalwind_glasswaste.ArsenalHooks;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import java.util.Optional;
import dev.elsewhere.petalwind_glasswaste.MapTrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * The Windwright: Petalwind's kite-market trader (a stay-put wandering trader). Sells the Skyhook and Cloudbread, and
 * (by id, when those modules are loaded) the Kitestring Staff and the Bell of the Flock from ARSENAL.md.
 */
public class Windwright extends WanderingTrader {
	public Windwright(EntityType<? extends WanderingTrader> type, Level level) {
		super(type, level);
	}

	@Override
	protected void updateTrades(ServerLevel level) {
		MerchantOffers offers = this.getOffers();
		offers.add(new MerchantOffer(new ItemCost(PetalwindItems.ZEPHYR_FEATHER, 4), Optional.of(new ItemCost(Items.FISHING_ROD)),
			new ItemStack(PetalwindItems.SKYHOOK), 4, 10, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(PetalwindItems.CLOUDBREAD, 3), 12, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(PetalwindItems.ZEPHYR_FEATHER, 2), 8, 4, 0.05F));
		ArsenalHooks.item("kitestring_staff").ifPresent(staff -> offers.add(new MerchantOffer(new ItemCost(PetalwindItems.ZEPHYR_FEATHER, GearStats.KITESTRING_FEATHERS),
			Optional.of(new ItemCost(PetalwindItems.ZEPHYRITE, GearStats.KITESTRING_ZEPHYRITE)), new ItemStack(staff), 3, 15, 0.05F)));
		// 2.0 (gear2 / summoner items by id; skipped when absent): Gale Rapier and Gale Ribbon
		ArsenalHooks.item("gale_rapier").ifPresent(r -> offers.add(new MerchantOffer(new ItemCost(PetalwindItems.ZEPHYR_FEATHER, GearStats.RAPIER_FEATHERS),
			Optional.of(new ItemCost(PetalwindItems.ZEPHYRITE, GearStats.RAPIER_ZEPHYRITE)), new ItemStack(r), 2, 20, 0.05F)));
		ArsenalHooks.item("gale_ribbon").ifPresent(r -> offers.add(new MerchantOffer(new ItemCost(PetalwindItems.ZEPHYR_FEATHER, GearStats.RIBBON_FEATHERS),
			Optional.of(new ItemCost(PetalwindItems.BLOSSOM_SEAL, GearStats.RIBBON_SEALS)), new ItemStack(r), 2, 20, 0.05F)));
		// Explorer maps (survival players can't /locate): the Petal Pagoda and the Eye of the Gale.
		MapTrades.offer(level, blockPosition(), level.dimension(), "petal_pagoda", "Petal Pagoda (Kazehana)", 12).ifPresent(offers::add);
		MapTrades.offer(level, blockPosition(), level.dimension(), "eye_of_the_gale", "Eye of the Gale (Tempestra)", 16).ifPresent(offers::add);
		ArsenalHooks.item("bell_of_the_flock").ifPresent(bell -> offers.add(new MerchantOffer(new ItemCost(PetalwindItems.BLOSSOM_SEAL, GearStats.BELL_SEALS),
			Optional.of(new ItemCost(Items.BELL)), new ItemStack(bell), 2, 20, 0.05F)));
	}

	@Override
	public void tick() {
		super.tick();
		this.setDespawnDelay(0);
	}
}
