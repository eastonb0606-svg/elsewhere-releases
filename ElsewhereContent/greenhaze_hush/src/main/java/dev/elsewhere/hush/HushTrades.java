package dev.elsewhere.hush;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import java.util.Optional;

/** Mute Archivist trades (DIMENSIONS.md 6: Sonar Horn for 4 Resonant Shards + goat horn, Silence Draught; Cantor's Bell Shard is top currency). */
public final class HushTrades {
	private HushTrades() {}

	/** gear-magic2-a: Auris drops the Hushward Bell; the Mute Archivist sells a full Bell (24 Resonant Shards + a Cantor's Bell Shard). */
	public static java.util.List<MerchantOffer> gearOffers(java.util.function.Function<String, Optional<net.minecraft.world.item.Item>> items) {
		java.util.List<MerchantOffer> out = new java.util.ArrayList<>();
		items.apply("hushward_bell").ifPresent(bell ->
			out.add(new MerchantOffer(new ItemCost(HushItems.RESONANT_SHARD, 24), Optional.of(new ItemCost(HushItems.CANTORS_BELL_SHARD, 1)),
				new ItemStack(bell), 1, 30, 0.05F)));
		return out;
	}

	static void archivist(ServerLevel level, net.minecraft.core.BlockPos pos, MerchantOffers offers) {
		if (!offers.isEmpty()) return;
		offers.add(new MerchantOffer(new ItemCost(HushItems.RESONANT_SHARD, 4), Optional.of(new ItemCost(Items.GOAT_HORN, 1)),
			new ItemStack(HushItems.SONAR_HORN), 3, 15, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(HushItems.RESONANT_SHARD, 3), new ItemStack(HushItems.SILENCE_DRAUGHT, 1), 8, 10, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(HushItems.RESONANT_SHARD, 2), new ItemStack(HushBlocks.HUSHWEAVE_CARPET, 8), 16, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(HushItems.ECHO_CRYSTAL, 2), new ItemStack(HushItems.RESONANT_SHARD, 1), 16, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.ECHO_SHARD, 1), new ItemStack(HushItems.RESONANT_SHARD, 2), 12, 2, 0.05F));
		var swiftSneak = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SWIFT_SNEAK);
		swiftSneak.ifPresent(h -> offers.add(new MerchantOffer(new ItemCost(HushItems.RESONANT_SHARD, 20),
			EnchantmentHelper.createBook(new EnchantmentInstance(h, 1)), 1, 20, 0.05F)));
		var reg = net.minecraft.core.registries.BuiltInRegistries.ITEM;
		var resonator = reg.getOptional(dev.elsewhere.greenhaze_hush.Reg.id("sculk_resonator"));
		var wand = reg.getOptional(dev.elsewhere.greenhaze_hush.Reg.id("amethyst_wand"));
		// Sculk Resonator (ARSENAL.md: wand + 8 shards + 2 echo shards): a villager offer holds two costs, so the 2 echo shards
		// (worth 4 Resonant Shards at this trader) are folded in: 12 Resonant Shards + the Amethyst Wand, 2 per restock.
		if (resonator.isPresent() && wand.isPresent()) offers.add(new MerchantOffer(new ItemCost(HushItems.RESONANT_SHARD, 12),
			Optional.of(new ItemCost(wand.get(), 1)), new ItemStack(resonator.get()), 2, 30, 0.05F));
		offers.addAll(gearOffers(id -> reg.getOptional(dev.elsewhere.greenhaze_hush.Reg.id(id))));
		offers.add(new MerchantOffer(new ItemCost(HushItems.CANTORS_BELL_SHARD, 1), new ItemStack(HushItems.MUSIC_DISC_HUSH), 1, 30, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(HushItems.CANTORS_BELL_SHARD, 1), new ItemStack(Items.DISC_FRAGMENT_5, 3), 2, 30, 0.05F));
		// Explorer maps (survival players can't /locate): the two Hush boss sites.
		dev.elsewhere.greenhaze_hush.GHMaps.offer(level, pos, level.dimension(), "listening_engine", "The Listening Engine (Auris)", 12).ifPresent(offers::add);
		dev.elsewhere.greenhaze_hush.GHMaps.offer(level, pos, level.dimension(), "choir_geode", "The Choir Geode (Lithara)", 12).ifPresent(offers::add);
	}
}
