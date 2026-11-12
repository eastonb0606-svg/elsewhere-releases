package dev.elsewhere.registry;

import dev.elsewhere.Elsewhere;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModTabs {
	public static final CreativeModeTab ELSEWHERE = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Elsewhere.id("elsewhere"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere"))
			.icon(() -> new ItemStack(ModItems.WAYFINDER_PALE_HOLLOW))
			.displayItems((params, out) -> {
				out.accept(ModItems.WAYFINDER);
				out.accept(ModItems.WAYFINDER_PALE_HOLLOW);
				out.accept(ModItems.FRAYED_WAYFINDER);
				out.accept(ModItems.HEARTWOOD_SHARD);
				out.accept(ModItems.PALE_BARK);
				out.accept(ModItems.PALE_SAP);
				out.accept(ModItems.MUSIC_DISC_PALE_LIGHT);
				out.accept(ModItems.UNBLINKING_EYE);
				out.accept(ModItems.HEARTWOOD_EFFIGY);
				out.accept(ModItems.MOONRING_SEED);
				out.accept(ModItems.RESIN_HEART_LOCKET);
				out.accept(ModItems.HEARTWOOD_GREATBOW);
				out.accept(ModItems.EYEBLOSSOM_CIRCLET);
				out.accept(ModItems.MOON_EYE);
				out.accept(ModBlocks.HEARTWOOD_LOG);
				out.accept(ModBlocks.ASHEN_MOSS);
				out.accept(ModBlocks.MOONRING_STONE);
				out.accept(ModBlocks.RESIN_HEART);
				out.accept(ModBlocks.MOON_BLOOM);
				out.accept(ModBlocks.RETURN_RIFT);
				out.accept(ModItems.CREAKING_HUSK_SPAWN_EGG);
				out.accept(ModItems.EYEBLOSSOM_WISP_SPAWN_EGG);
				out.accept(ModItems.AMBER);
				out.accept(ModItems.STAG_ANTLER);
				out.accept(ModItems.HEARTWOOD_SWORD);
				out.accept(ModItems.HEARTWOOD_PICKAXE);
				out.accept(ModItems.HEARTWOOD_AXE);
				out.accept(ModItems.HEARTWOOD_SHOVEL);
				out.accept(ModItems.HEARTWOOD_HOE);
				out.accept(ModItems.PALEWEAVE_HELMET);
				out.accept(ModItems.PALEWEAVE_MANTLE);
				out.accept(ModItems.PALEWEAVE_LEGGINGS);
				out.accept(ModItems.PALEWEAVE_BOOTS);
				out.accept(ModItems.ANTLER_BOW);
				out.accept(ModItems.HOLLOW_STAG_SPAWN_EGG);
				out.accept(ModItems.BOG_WIGHT_SPAWN_EGG);
				out.accept(ModItems.DOCILE_SCARAB_SPAWN_EGG);
				out.accept(ModItems.HAZE_FERRET_SPAWN_EGG);
				out.accept(ModItems.GHOSTFIN);
				out.accept(ModItems.PETALSCALE_KOI);
				out.accept(ModItems.GLASSFIN_DARTER);
				out.accept(ModItems.VERDIGRIS_EEL);
				out.accept(ModItems.FROSTJAW_TROUT);
				out.accept(ModItems.HAZE_GUPPY);
				out.accept(ModItems.HUSH_LANTERNFISH);
				out.accept(ModBlocks.AMBER_ORE);
				out.accept(ModBlocks.RIFT_ANCHOR);
				out.accept(ModBlocks.BOSS_WAYSTONE);
				out.accept(ModBlocks.BOSS_TROPHY);
				out.accept(dev.elsewhere.tiergear.TierGear.CHALLENGERS_CLEAVER);
				out.accept(dev.elsewhere.tiergear.TierGear.AEGIS_OF_THE_LADDER);
				out.accept(dev.elsewhere.tiergear.TierGear.PHASEWALKER_GREAVES);
				out.accept(dev.elsewhere.tiergear.TierGear.STORMCREST_HELM);
				out.accept(dev.elsewhere.tiergear.TierGear.ECHO_FORGED_BLADE);
				out.accept(dev.elsewhere.tiergear.TierGear.HALO_OF_THE_OUTER_END);
			})
			.build());

	private ModTabs() {
	}

	public static void init() {
	}
}
