package dev.elsewhere.overworld_bosses;

import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import dev.elsewhere.stub.overworld_bosses.SignatureGear;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Creative tab "Elsewhere: Overworld Bosses" (dev convenience; the core's tab replaces it at merge). */
public final class OwbCreativeTab {
	private OwbCreativeTab() {}

	public static void init() {
		if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(BossRegistry.id("overworld_bosses"))) return;
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.overworld_bosses"))
			.icon(() -> new ItemStack(OwbItems.LILYCROWN))
			.displayItems((params, out) -> {
				out.accept(OwbItems.LILYCROWN); out.accept(OwbItems.GULP_TONGUE); out.accept(OwbItems.FROG_LEGS); out.accept(OwbItems.BUFO_GLAND);
				out.accept(OwbItems.HARVEST_SCYTHE); out.accept(OwbItems.SCARECROWS_GRIN); out.accept(OwbItems.HARVEST_MOON_SEEDS); out.accept(OwbItems.HARVEST_LOAF);
				out.accept(OwbItems.HARVEST_MOON_BUD); out.accept(OwbItems.CROW_FEATHER);
				out.accept(OwbItems.ANKH_OF_NEFERKHET); out.accept(OwbItems.SUNFIRE_KHOPESH); out.accept(OwbItems.PHARAOHS_NEMES); out.accept(OwbItems.ROYAL_HONEY); out.accept(OwbItems.LINEN_WRAP);
				out.accept(OwbItems.RIMEHEART_MAUL); out.accept(OwbItems.RIMEWALKER_BOOTS); out.accept(OwbItems.GLACIER_WATER);
				out.accept(OwbItems.UNDERTOW); out.accept(OwbItems.KRAKEN_INK_BOMB); out.accept(OwbItems.ABYSSAL_EYE); out.accept(OwbItems.SIREN_CONCH); out.accept(OwbItems.KRAKEN_CALAMARI);
				out.accept(SignatureGear.CHALLENGERS_CLEAVER); out.accept(SignatureGear.AEGIS_OF_THE_LADDER); out.accept(SignatureGear.PHASEWALKER_GREAVES); out.accept(BossRegistry.BOSS_TOKEN);
				out.accept(OwbBlocks.GULPMOTHER_SHRINE_CAULDRON); out.accept(OwbBlocks.NEFERKHET_SUN_ALTAR); out.accept(OwbBlocks.NEFERKHET_SUN_IDOL);
				out.accept(OwbBlocks.FROZEN_HEART); out.accept(OwbBlocks.VORLAGH_CAPSTAN);
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BossRegistry.id("overworld_bosses"), tab);
	}
}
