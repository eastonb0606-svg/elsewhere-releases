package dev.elsewhere.verdigris_sea;

import static dev.elsewhere.verdigris_ironpeak.Reg.living;

import dev.elsewhere.verdigris_ironpeak.ElsewhereTrader;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.MapOffers;
import net.minecraft.world.level.Level;
import dev.elsewhere.verdigris_ironpeak.ExtraCostOffer;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.Reg;
import dev.elsewhere.verdigris_sea.mob.BarnacleGolem;
import dev.elsewhere.verdigris_sea.mob.BrassGuardian;
import dev.elsewhere.verdigris_sea.mob.ClockworkDrowned;
import dev.elsewhere.verdigris_sea.mob.TidewingRay;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Verdigris Sea mobs and the Tidewright trader. */
public final class VerdigrisEntities {
	public static final EntityType<ClockworkDrowned> CLOCKWORK_DROWNED = living("clockwork_drowned",
		EntityType.Builder.of(ClockworkDrowned::new, MobCategory.MONSTER).sized(0.66F, 2.15F).eyeHeight(1.85F).clientTrackingRange(8),
		ClockworkDrowned.createAttributes());
	public static final EntityType<BarnacleGolem> BARNACLE_GOLEM = living("barnacle_golem",
		EntityType.Builder.of(BarnacleGolem::new, MobCategory.CREATURE).sized(1.6F, 2.4F).clientTrackingRange(10), BarnacleGolem.createAttributes());
	public static final EntityType<TidewingRay> TIDEWING_RAY = living("tidewing_ray",
		EntityType.Builder.of(TidewingRay::new, MobCategory.MONSTER).sized(2.6F, 0.6F).clientTrackingRange(10), TidewingRay.createAttributes());
	public static final EntityType<BrassGuardian> BRASS_GUARDIAN = living("brass_guardian",
		EntityType.Builder.of(BrassGuardian::new, MobCategory.MONSTER).sized(1.1F, 1.1F).eyeHeight(0.55F).clientTrackingRange(8), BrassGuardian.createAttributes());
	public static final EntityType<ElsewhereTrader> TIDEWRIGHT = living("tidewright",
		EntityType.Builder.<ElsewhereTrader>of((t, l) -> new ElsewhereTrader(t, l, VerdigrisEntities::tidewrightOffers), MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10), WanderingTrader.createMobAttributes());

	private VerdigrisEntities() {}

	/** Tidewright trades (DIMENSIONS.md: harpoon for a trident + 12 Gears; Scraped Patina is the top currency). */
	public static List<MerchantOffer> tidewrightOffers(ServerPlayer p) {
		return tidewrightOffers(null, p);
	}

	public static List<MerchantOffer> tidewrightOffers(ElsewhereTrader self, ServerPlayer p) {
		List<MerchantOffer> o = new ArrayList<>();
		// 2.0 bal-trader-caps: emerald-for-goods trades restock like vanilla (16, not 99).
		o.add(new MerchantOffer(new ItemCost(VerdigrisItems.VERDIGRIS_GEAR, 6), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
		o.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(VerdigrisItems.BRINE_LUNG, 2), 16, 1, 0.05F));
		o.add(new MerchantOffer(new ItemCost(VerdigrisItems.VERDIGRIS_GEAR, 4), new ItemStack(net.minecraft.world.level.block.Blocks.LIGHTNING_ROD.weathering().unaffected().asItem()), 16, 1, 0.05F));
		o.add(new MerchantOffer(new ItemCost(Items.TRIDENT), Optional.of(new ItemCost(VerdigrisItems.VERDIGRIS_GEAR, 12)), new ItemStack(VerdigrisItems.STORMCALLER_HARPOON), 3, 20, 0.05F));
		o.add(new MerchantOffer(new ItemCost(VerdigrisItems.SCRAPED_PATINA, 4), new ItemStack(VerdigrisItems.MUSIC_DISC_LOW_TIDE_ENGINE), 2, 15, 0.05F));
		o.add(new MerchantOffer(new ItemCost(VerdigrisItems.SCRAPED_PATINA, 3), new ItemStack(VerdigrisItems.PEARLITE, 2), 12, 10, 0.05F));
		o.addAll(arsenalOffers(p));
		// new-repeater: the Clockwork Repeater (arsenal module item, looked up by id: skipped when absent) for a crossbow + 12 Gears.
		net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(Reg.id("clockwork_repeater")).ifPresent(rep ->
			o.add(new MerchantOffer(new ItemCost(Items.CROSSBOW), Optional.of(new ItemCost(VerdigrisItems.VERDIGRIS_GEAR, 12)), new ItemStack(rep), 2, 25, 0.05F)));
		// Explorer maps (survival players can't /locate): this dimension's two boss sites, and the End's two for a high price.
		MapOffers.offer(self, Fx.VERDIGRIS_SEA, "drowned_cathedral", "Verdigrim's Drowned Cathedral", 12).ifPresent(o::add);
		MapOffers.offer(self, Fx.VERDIGRIS_SEA, "lighthouse", "Galvanox's Lighthouse", 12).ifPresent(o::add);
		MapOffers.offer(self, Level.END, "ossyra_matriarch_spire", "Ossyra's Matriarch Spire (the End)", 48).ifPresent(o::add);
		MapOffers.offer(self, Level.END, "aphelion_shattered_halo", "Aphelion's Shattered Halo (the End)", 48).ifPresent(o::add);
		return o;
	}

	/**
	 * ARSENAL.md trades for other modules' items, looked up by id and skipped when absent: Brassback Key (summoner) for
	 * 3 Scraped Patina + 16 Verdigris Gears; Stormcoil Staff (arsenal, rev 3) for an Amethyst Wand + 12 Gears in the trade
	 * slots + 1 Storm Coil and 2 Pearlite taken from the inventory ({@link ExtraCostOffer}).
	 */
	public static List<MerchantOffer> arsenalOffers(ServerPlayer p) {
		List<MerchantOffer> o = new ArrayList<>();
		var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
		registry.getOptional(Reg.id("brassback_key")).ifPresent(key ->
			o.add(new MerchantOffer(new ItemCost(VerdigrisItems.SCRAPED_PATINA, GearStats.BRASSBACK_KEY_PATINA),
				Optional.of(new ItemCost(VerdigrisItems.VERDIGRIS_GEAR, GearStats.BRASSBACK_KEY_GEARS)), new ItemStack(key), 4, 20, 0.05F)));
		registry.getOptional(Reg.id("stormcoil_staff")).ifPresent(staff ->
			registry.getOptional(Reg.id("amethyst_wand")).ifPresent(wand ->
				o.add(new ExtraCostOffer(p, new ItemCost(wand), Optional.of(new ItemCost(VerdigrisItems.VERDIGRIS_GEAR, GearStats.STORMCOIL_GEARS)), new ItemStack(staff),
					List.of(new ItemStack(VerdigrisBlocks.STORM_COIL.asItem(), GearStats.STORMCOIL_COILS), new ItemStack(VerdigrisItems.PEARLITE, GearStats.STORMCOIL_PEARLITE)),
					3, 25, 0.05F))));
		return o;
	}

	public static void init() {}
}
