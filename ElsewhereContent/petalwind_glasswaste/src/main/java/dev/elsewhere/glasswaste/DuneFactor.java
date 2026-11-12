package dev.elsewhere.glasswaste;

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
 * The Dune Factor: the Glasswaste's caravan trader (a stay-put wandering trader). Sells the Burning Glass and Cactus Water,
 * and (by id, when the arsenal module is loaded) the Sunlance Staff and the Glimmerstone Focus from ARSENAL.md.
 */
public class DuneFactor extends WanderingTrader {
	public DuneFactor(EntityType<? extends WanderingTrader> type, Level level) {
		super(type, level);
	}

	@Override
	protected void updateTrades(ServerLevel level) {
		MerchantOffers offers = this.getOffers();
		offers.add(new MerchantOffer(new ItemCost(GlasswasteItems.SUNGLASS_SHARD, 8), Optional.of(new ItemCost(Items.SPYGLASS)),
			new ItemStack(GlasswasteItems.BURNING_GLASS), 3, 10, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(GlasswasteItems.CACTUS_WATER, 2), 12, 2, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(GlasswasteItems.SANDSWIMMER_SCALE, 2), new ItemStack(Items.EMERALD, 6), 8, 4, 0.05F));
		ArsenalHooks.item("sunlance_staff").ifPresent(staff -> ArsenalHooks.item("amethyst_wand").ifPresent(wand -> offers.add(new MerchantOffer(
			new ItemCost(wand), Optional.of(new ItemCost(GlasswasteItems.SUNGLASS_SHARD, GearStats.SUNLANCE_SHARDS)), new ItemStack(staff), 3, 15, 0.05F))));
		// Explorer maps (survival players can't /locate): the Sunwright Ziggurat and the Sunpit; the Zenith disc.
		MapTrades.offer(level, blockPosition(), level.dimension(), "sunwright_ziggurat", "Sunwright Ziggurat (Heliodor)", 12).ifPresent(offers::add);
		MapTrades.offer(level, blockPosition(), level.dimension(), "sunpit", "The Sunpit (Ankhara)", 12).ifPresent(offers::add);
		offers.add(new MerchantOffer(new ItemCost(GlasswasteItems.SUNGLASS_SHARD, 12), Optional.of(new ItemCost(Items.EMERALD, 8)), new ItemStack(GlasswasteItems.MUSIC_DISC_ZENITH), 1, 30, 0.05F));
		ArsenalHooks.item("glimmerstone_focus").ifPresent(focus -> offers.add(new MerchantOffer(new ItemCost(GlasswasteItems.SUNGLASS_SHARD, GearStats.FOCUS_SHARDS),
			Optional.of(new ItemCost(Items.AMETHYST_SHARD, GearStats.FOCUS_AMETHYST)), new ItemStack(focus), 2, 20, 0.05F)));
	}

	@Override
	public void tick() {
		super.tick();
		this.setDespawnDelay(0);
	}
}
