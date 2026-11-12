package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.glasswaste.GlasswasteBlocks;
import dev.elsewhere.petalwind.PetalwindBlocks;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Creative tabs "Elsewhere: Petalwind Isles" and "Elsewhere: The Glasswaste". */
public final class CreativeTabs {
	private CreativeTabs() {}

	public static void init() {
		tab("petalwind", () -> new ItemStack(PetalwindBlocks.SKYBLOSSOM_SAPLING));
		tab("glasswaste", () -> new ItemStack(GlasswasteBlocks.SUNGLASS));
	}

	private static void tab(String name, java.util.function.Supplier<ItemStack> icon) {
		CreativeModeTab tab = FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.elsewhere." + name)).icon(icon)
			.displayItems((params, out) -> {
				for (Item item : Reg.TAB_ITEMS.getOrDefault(name, java.util.List.of())) out.accept(item);
			}).build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Reg.id(name), tab);
	}
}
