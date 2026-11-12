package dev.elsewhere.compat.jei;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * "Transmutation Table" (Test Meter's equivalent exchange), shown only when Test Meter is installed: one page per item with a Quanta
 * value (burn it at the table to learn it, then withdraw copies for the same Quanta). Test Meter is optional, so it is reached by
 * reflection ({@code dev.testmeter.Transmutation.valueOf/format}, {@code TestItems.create}); pages are built lazily, only when JEI
 * looks an item up or the category is opened, from Test Meter's live value snapshot (so values follow /reload).
 */
public final class TransmutationCategory extends AbstractRecipeCategory<TransmutationCategory.Page> {
	public record Page(Item item, double value) {}

	public static final IRecipeType<Page> TYPE = IRecipeType.create("elsewhere", "transmutation", Page.class);
	private static final int WIDTH = 120;
	private static final int HEIGHT = 30;

	private static Method valueOf;
	private static Method format;
	private static Method create;
	private static boolean probed;

	public TransmutationCategory(final IGuiHelper gui) {
		super(TYPE, Component.translatableWithFallback("jei.elsewhere.transmutation", "Transmutation Table"),
			gui.createDrawableItemStack(table()), WIDTH, HEIGHT);
	}

	/** True when Test Meter is installed and its transmutation API is present. */
	public static synchronized boolean available() {
		if (!probed) {
			probed = true;
			if (FabricLoader.getInstance().isModLoaded("testmeter")) {
				try {
					Class<?> t = Class.forName("dev.testmeter.Transmutation");
					valueOf = t.getMethod("valueOf", ItemStack.class);
					format = t.getMethod("format", double.class);
					create = Class.forName("dev.testmeter.TestItems").getMethod("create", String.class, int.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					ElsewhereJeiData.LOG.warn("Test Meter found but its transmutation API is not: no Transmutation Table pages", e);
					valueOf = null;
				}
			}
		}
		return valueOf != null;
	}

	public static double value(final ItemStack stack) {
		if (!available() || stack.isEmpty()) {
			return 0;
		}
		try {
			return (double) valueOf.invoke(null, stack);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	static String formatted(final double v) {
		try {
			return (String) format.invoke(null, v);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return String.valueOf(Math.round(v));
		}
	}

	/** Info-page lines for the Table and Tablet (s-compat-transmute's text; values checked by its TransmutePlan QA). */
	public static final List<String> INFO = List.of(
		"Burn an item at the table to turn it into Quanta and learn it; withdraw copies of anything you've learned for the same Quanta.",
		"Craft: 4 obsidian, 2 amethyst shards, 2 diamonds and an eye of ender. The Tablet (eyes of ender, diamonds, netherite scrap, a recovery compass) opens it anywhere.",
		"Elsewhere's rare ores and gems, and the endgame gear made from them, cost at least 5 netherite ingots' worth.",
		"Boss uniques, kingdom and rival gear, currencies, seals and boss parts can't be transmuted.");

	/**
	 * JEI subtype key for Test Meter's items: the Table is an enchanting table and the Tablet a recovery compass, told apart by
	 * {@code custom_data {testmeter: "<kind>"}}. Returns that kind, or null for the plain vanilla item (one JEI entry).
	 */
	public static String subtype(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		String kind = data == null ? "" : data.copyTag().getStringOr("testmeter", "");
		return kind.isEmpty() ? null : kind;
	}

	/** The Table and Tablet stacks (catalysts), or an enchanting table when Test Meter can't make them. */
	public static ItemStack table() {
		return testItem("transmutation_table", Items.ENCHANTING_TABLE);
	}

	public static ItemStack tablet() {
		return testItem("transmutation_tablet", Items.RECOVERY_COMPASS);
	}

	private static ItemStack testItem(final String kind, final Item fallback) {
		if (available() && create != null) {
			try {
				Object o = create.invoke(null, kind, 1);
				if (o instanceof ItemStack s && !s.isEmpty()) {
					return s;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall through
			}
		}
		return new ItemStack(fallback);
	}

	@Override
	public void setRecipe(final IRecipeLayoutBuilder builder, final Page page, final IFocusGroup focuses) {
		builder.addInputSlot(1, 7).setStandardSlotBackground().add(new ItemStack(page.item()));
		builder.addOutputSlot(WIDTH - 19, 7).setOutputSlotBackground().add(new ItemStack(page.item()));
	}

	@Override
	public void createRecipeExtras(final IRecipeExtrasBuilder builder, final Page page, final IFocusGroup focuses) {
		builder.addText(Component.translatableWithFallback("jei.elsewhere.transmutation.value", "%s Quanta", formatted(page.value())), 70, 10)
			.setPosition(24, 3).setColor(0xFF404040);
		builder.addRecipeArrowWidget().setPosition(46, 14);
	}

	@Override
	public Identifier getIdentifier(final Page page) {
		Identifier item = BuiltInRegistries.ITEM.getKey(page.item());
		return Identifier.fromNamespaceAndPath("elsewhere", "transmutation/" + item.getNamespace() + "/" + item.getPath());
	}

	/** Lazy pages: nothing is computed until JEI asks about one item or opens the category. */
	static final class Lookup implements ISimpleRecipeManagerPlugin<Page> {
		private static Page page(final ITypedIngredient<?> typed) {
			ItemStack s = typed.getItemStack().orElse(ItemStack.EMPTY);
			double v = value(s);
			return v > 0 ? new Page(s.getItem(), v) : null;
		}

		@Override
		public boolean isHandledInput(final ITypedIngredient<?> typed) {
			return page(typed) != null;
		}

		@Override
		public boolean isHandledOutput(final ITypedIngredient<?> typed) {
			return page(typed) != null;
		}

		@Override
		public List<Page> getRecipesForInput(final ITypedIngredient<?> typed) {
			Page p = page(typed);
			return p == null ? List.of() : List.of(p);
		}

		@Override
		public List<Page> getRecipesForOutput(final ITypedIngredient<?> typed) {
			return this.getRecipesForInput(typed);
		}

		@Override
		public List<Page> getAllRecipes() {
			List<Page> out = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				double v = value(new ItemStack(item));
				if (v > 0) {
					out.add(new Page(item, v));
				}
			}
			return out;
		}
	}
}
