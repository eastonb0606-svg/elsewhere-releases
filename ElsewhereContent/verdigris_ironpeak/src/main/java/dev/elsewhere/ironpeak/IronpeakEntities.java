package dev.elsewhere.ironpeak;

import dev.elsewhere.verdigris_ironpeak.MapOffers;
import static dev.elsewhere.verdigris_ironpeak.Reg.living;

import dev.elsewhere.ironpeak.mob.CragRoc;
import dev.elsewhere.ironpeak.mob.HighlandYak;
import dev.elsewhere.ironpeak.mob.IronclanRaider;
import dev.elsewhere.ironpeak.mob.Rimeguard;
import dev.elsewhere.verdigris_ironpeak.ElsewhereTrader;
import dev.elsewhere.verdigris_ironpeak.ExtraCostOffer;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.compat.TestMeterBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Ironpeak mobs and the Clan Quartermaster. */
public final class IronpeakEntities {
	public static final EntityType<HighlandYak> HIGHLAND_YAK = living("highland_yak",
		EntityType.Builder.of(HighlandYak::new, MobCategory.CREATURE).sized(1.4F, 1.9F).eyeHeight(1.5F).clientTrackingRange(10), HighlandYak.createAttributes());
	public static final EntityType<IronclanRaider> IRONCLAN_RAIDER = living("ironclan_raider",
		EntityType.Builder.of(IronclanRaider::new, MobCategory.MONSTER).sized(0.84F, 2.75F).eyeHeight(2.4F).clientTrackingRange(8), IronclanRaider.createAttributes());
	public static final EntityType<CragRoc> CRAG_ROC = living("crag_roc",
		EntityType.Builder.of(CragRoc::new, MobCategory.MONSTER).sized(2.8F, 1.2F).clientTrackingRange(12), CragRoc.createAttributes());
	public static final EntityType<Rimeguard> RIMEGUARD = living("rimeguard",
		EntityType.Builder.of(Rimeguard::new, MobCategory.MONSTER).sized(1.8F, 3.0F).clientTrackingRange(10), Rimeguard.createAttributes());
	public static final EntityType<ElsewhereTrader> CLAN_QUARTERMASTER = living("clan_quartermaster",
		EntityType.Builder.<ElsewhereTrader>of((t, l) -> new ElsewhereTrader(t, l, IronpeakEntities::quartermasterOffers), MobCategory.MISC)
			.sized(0.75F, 2.45F).eyeHeight(2.0F).clientTrackingRange(10), WanderingTrader.createMobAttributes().add(Attributes.SCALE, 1.25));

	private IronpeakEntities() {}

	/** Clan Quartermaster: iron-bar trades, gated by clan rank; the best ones also need 700 ng/dL with Test Meter. */
	public static List<MerchantOffer> quartermasterOffers(ServerPlayer p) {
		return quartermasterOffers(null, p);
	}

	public static List<MerchantOffer> quartermasterOffers(ElsewhereTrader self, ServerPlayer p) {
		int rank = Ironpeak.rank(p);
		List<MerchantOffer> o = new ArrayList<>();
		o.add(new MerchantOffer(new ItemCost(Items.IRON_BARS, 16), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
		o.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(IronpeakItems.COOKED_YAK_MEAT, 4), 12, 1, 0.05F));
		if (rank >= 1) o.add(new MerchantOffer(new ItemCost(IronpeakItems.SKYIRON, 4), new ItemStack(IronpeakItems.CRAMPONS), 6, 10, 0.05F)); // 2.0: priced in dimension currency (Test Meter prices Crampons at 4 Skyiron)
		if (rank >= 2) o.add(new MerchantOffer(new ItemCost(IronpeakItems.SKYIRON, 3), Optional.of(new ItemCost(Items.IRON_AXE)), new ItemStack(IronpeakItems.ICE_AXE), 6, 15, 0.05F));
		// gear2: Prospector's Pendulum (by id, skipped when gear2 is absent): 6 Skyiron + a compass.
		if (rank >= 2) net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(dev.elsewhere.verdigris_ironpeak.Reg.id("prospectors_pendulum")).ifPresent(pend ->
			o.add(new MerchantOffer(new ItemCost(IronpeakItems.SKYIRON, 6), Optional.of(new ItemCost(Items.COMPASS)), new ItemStack(pend), 2, 20, 0.05F)));
		if (rank >= 3) o.add(new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(IronpeakBlocks.HEARTHSTONE.asItem(), 2), 12, 10, 0.05F));
		// ARSENAL.md §6: Roc-Feather Longbow at rank 3 for a bow + 4 Roc Feathers in the trade slots + 2 Skyiron taken from the inventory.
		if (rank >= GearStats.LONGBOW_TRADE_RANK) o.add(longbowOffer(p));
		if (rank >= 4) o.add(new MerchantOffer(new ItemCost(IronpeakItems.SKYIRON, 2), new ItemStack(Items.EMERALD, 3), 12, 10, 0.05F));
		boolean testOk = !TestMeterBridge.available() || TestMeterBridge.total(p) >= 700;
		if (rank >= 5 && testOk) {
			o.add(new MerchantOffer(new ItemCost(IronpeakItems.SKYIRON, 8), Optional.of(new ItemCost(Items.EMERALD, 16)), new ItemStack(IronpeakItems.MUSIC_DISC_THIN_AIR), 2, 30, 0.05F));
			// 2.0 bal-trader-climber: a Climber upgrade template (the armor is smithed), once per restock.
			o.add(new MerchantOffer(new ItemCost(IronpeakItems.SKYIRON, 6), Optional.of(new ItemCost(Items.DIAMOND, 4)), new ItemStack(IronpeakItems.CLIMBER_UPGRADE_TEMPLATE), 1, 30, 0.05F));
		}
		// Explorer maps (survival players can't /locate): this dimension's two boss sites; the Nether's three at rank 4 for a high price.
		MapOffers.offer(self, dev.elsewhere.verdigris_ironpeak.Fx.IRONPEAK, "summit_throne", "Thurgrim's Summit Throne", 12).ifPresent(o::add);
		MapOffers.offer(self, dev.elsewhere.verdigris_ironpeak.Fx.IRONPEAK, "proving_grounds", "Hrolf's Proving Grounds", 12).ifPresent(o::add);
		if (rank >= 4) {
			MapOffers.offer(self, net.minecraft.world.level.Level.NETHER, "grunthar_treasure_hall", "Grunthar's Treasure Hall (the Nether)", 48).ifPresent(o::add);
			MapOffers.offer(self, net.minecraft.world.level.Level.NETHER, "solaxis_pyre_cradle", "Solaxis' Pyre Cradle (the Nether)", 48).ifPresent(o::add);
			MapOffers.offer(self, net.minecraft.world.level.Level.NETHER, "mournmaw_weeping_basin", "Mournmaw's Weeping Basin (the Nether)", 48).ifPresent(o::add);
		}
		return o;
	}

	/** The Quartermaster's rank-3 Roc-Feather Longbow trade (also used by the self-test). */
	public static MerchantOffer longbowOffer(ServerPlayer p) {
		return new ExtraCostOffer(p, new ItemCost(Items.BOW), Optional.of(new ItemCost(IronpeakItems.ROC_FEATHER, GearStats.LONGBOW_PRICE_FEATHERS)),
			new ItemStack(IronpeakItems.ROC_FEATHER_LONGBOW), List.of(new ItemStack(IronpeakItems.SKYIRON, GearStats.LONGBOW_PRICE_SKYIRON)), 3, 20, 0.05F);
	}

	public static void init() {}
}
