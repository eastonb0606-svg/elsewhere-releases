package dev.elsewhere.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Optional Just Enough Items support for Elsewhere. Loaded only by JEI, through the {@code jei_mod_plugin} entrypoint in
 * fabric.mod.json; nothing else references this package, so Elsewhere runs the same without JEI.
 *
 * <p>3.0 (POLISH P11, user "everything works with JEI"): besides the Wayfinder attunements and the info pages, the plugin shows
 * structure-chest and creature loot ({@link LootCategory}), the four kingdoms' shops ({@link TradeCategory}) and, with Test Meter, the
 * Transmutation Table ({@link TransmutationCategory}). All of it comes from {@link ElsewhereJeiData#get()}, which parses the jar's
 * files once, the first time JEI registers its recipes (so only when JEI is installed and a world is opened). The coverage self-test
 * ({@code dev.JeiSelfTest}) proves that every registered Elsewhere item has a recipe or a page.
 */
public final class ElsewhereJeiPlugin implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return Identifier.fromNamespaceAndPath("elsewhere", "jei_plugin");
	}

	private static ItemStack icon(final String id, final ItemStack fallback) {
		return ElsewhereJeiData.item(id).map(ItemStack::new).orElse(fallback);
	}

	/** Test Meter's Table and Tablet are vanilla items with custom_data; this makes each its own JEI entry (searchable, own recipe). */
	@Override
	public void registerItemSubtypes(final ISubtypeRegistration registration) {
		if (!TransmutationCategory.available()) {
			return;
		}
		ISubtypeInterpreter<ItemStack> testMeterKind = (final ItemStack stack, final UidContext context) -> TransmutationCategory.subtype(stack);
		registration.registerSubtypeInterpreter(Items.ENCHANTING_TABLE, testMeterKind);
		registration.registerSubtypeInterpreter(Items.RECOVERY_COMPASS, testMeterKind);
	}

	@Override
	public void registerExtraIngredients(final IExtraIngredientRegistration registration) {
		if (TransmutationCategory.available()) {
			registration.addExtraItemStacks(List.of(TransmutationCategory.table(), TransmutationCategory.tablet()));
		}
	}

	@Override
	public void registerCategories(final IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new AttunementCategory(gui, icon("elsewhere:wayfinder", new ItemStack(Items.COMPASS))));
		registration.addRecipeCategories(new LootCategory(gui, new ItemStack(Items.CHEST)));
		registration.addRecipeCategories(new TradeCategory(gui, icon("elsewhere:mirewick_scrip", new ItemStack(Items.EMERALD))));
		if (TransmutationCategory.available()) {
			registration.addRecipeCategories(new TransmutationCategory(gui));
		}
	}

	@Override
	public void registerRecipes(final IRecipeRegistration registration) {
		ElsewhereJeiData.Snapshot data = ElsewhereJeiData.get();
		registration.addRecipes(AttunementCategory.TYPE, data.attunements());
		registration.addRecipes(LootCategory.TYPE, data.loot());
		registration.addRecipes(TradeCategory.TYPE, data.trades());
		int info = 0;
		for (Map.Entry<String, List<String>> e : data.info().entrySet()) {
			var item = ElsewhereJeiData.item(e.getKey());
			if (item.isEmpty() || e.getValue().isEmpty()) {
				continue;
			}
			String key = "jei.elsewhere.info." + e.getKey().substring(e.getKey().indexOf(':') + 1);
			List<Component> lines = new ArrayList<>();
			for (int i = 0; i < e.getValue().size(); i++) {
				lines.add(Component.translatableWithFallback(key + "." + i, e.getValue().get(i)));
			}
			registration.addIngredientInfo(item.get(), lines.toArray(Component[]::new));
			info++;
		}
		if (TransmutationCategory.available()) {
			for (var e : Map.of("transmutation_table", TransmutationCategory.table(), "transmutation_tablet", TransmutationCategory.tablet()).entrySet()) {
				List<Component> lines = new ArrayList<>();
				for (int i = 0; i < TransmutationCategory.INFO.size(); i++) {
					lines.add(Component.translatableWithFallback("jei.elsewhere.info." + e.getKey() + "." + i, TransmutationCategory.INFO.get(i)));
				}
				registration.addIngredientInfo(e.getValue(), VanillaTypes.ITEM_STACK, lines.toArray(Component[]::new));
				info++;
			}
		}
		ElsewhereJeiData.LOG.info("Elsewhere JEI plugin: {} Wayfinder attunements, {} loot pages, {} shop lines, {} info pages",
			data.attunements().size(), data.loot().size(), data.trades().size(), info);
	}

	@Override
	public void registerRecipeCatalysts(final IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(AttunementCategory.TYPE, Items.CRAFTING_TABLE);
		registration.addCraftingStation(AttunementCategory.TYPE, Items.CRAFTER);
		if (TransmutationCategory.available()) {
			registration.addCraftingStation(TransmutationCategory.TYPE, TransmutationCategory.table(), TransmutationCategory.tablet());
		}
	}

	@Override
	public void registerAdvanced(final IAdvancedRegistration registration) {
		if (TransmutationCategory.available()) {
			registration.addSimpleRecipeManagerPlugin(TransmutationCategory.TYPE, new TransmutationCategory.Lookup());
		}
	}

	@Override
	public void registerRecipeTransferHandlers(final IRecipeTransferRegistration registration) {
		// The 9 input slots are laid out like a crafting grid, so JEI's "+" button fills a crafting table.
		registration.addRecipeTransferHandler(CraftingMenu.class, MenuType.CRAFTING, AttunementCategory.TYPE, 1, 9, 10, 36);
	}

	@Override
	public void onRuntimeAvailable(final IJeiRuntime runtime) {
		List<ItemStack> hide = new ArrayList<>();
		for (String id : ElsewhereJeiData.HIDDEN) {
			ElsewhereJeiData.item(id).ifPresent(i -> hide.add(new ItemStack(i)));
		}
		if (!hide.isEmpty()) {
			runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hide);
		}
	}
}
