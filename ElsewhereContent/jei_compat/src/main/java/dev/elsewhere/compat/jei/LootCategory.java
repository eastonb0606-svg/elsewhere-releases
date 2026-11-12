package dev.elsewhere.compat.jei;

import dev.elsewhere.compat.jei.ElsewhereJeiData.LootEntry;
import dev.elsewhere.compat.jei.ElsewhereJeiData.LootPage;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * "Elsewhere Loot": one page per structure-chest or creature loot table of the Elsewhere jar. The drops sit in a scrolling grid; each
 * slot's tooltip gives its chance per roll and its count. R on any drop shows where it can be found; U on a spawn egg shows the
 * creature's drops.
 */
public final class LootCategory extends AbstractRecipeCategory<LootPage> {
	public static final IRecipeType<LootPage> TYPE = IRecipeType.create("elsewhere", "loot", LootPage.class);
	private static final int WIDTH = 162;
	private static final int HEIGHT = 80;

	public LootCategory(final IGuiHelper gui, final ItemStack icon) {
		super(TYPE, Component.translatableWithFallback("jei.elsewhere.loot", "Elsewhere Loot"), gui.createDrawableItemStack(icon), WIDTH, HEIGHT);
	}

	static Component title(final LootPage page) {
		String pretty = pretty(page.source());
		if (page.kind().equals("creature")) {
			return Component.translatableWithFallback("jei.elsewhere.loot.creature", "Dropped by: %s",
				Component.translatableWithFallback("entity.elsewhere." + page.source(), pretty));
		}
		return Component.translatableWithFallback("jei.elsewhere.loot.chest", "Chests of: %s",
			Component.translatableWithFallback("jei.elsewhere.structure." + page.source(), pretty));
	}

	static String pretty(final String id) {
		StringBuilder b = new StringBuilder();
		for (String w : id.split("_")) {
			if (w.isEmpty()) {
				continue;
			}
			if (b.length() > 0) {
				b.append(' ');
			}
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString();
	}

	@Override
	public void setRecipe(final IRecipeLayoutBuilder builder, final LootPage page, final IFocusGroup focuses) {
		if (page.kind().equals("creature")) {
			BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("elsewhere", page.source() + "_spawn_egg"))
				.ifPresent(egg -> builder.addInputSlot(WIDTH - 18, 0).setStandardSlotBackground().add(new ItemStack(egg)));
		}
		for (LootEntry e : page.entries()) {
			ItemStack stack = new ItemStack(e.item());
			stack.setCount(Math.max(1, Math.min(e.max(), stack.getMaxStackSize())));
			String pct = e.chance() >= 0.995F ? "100" : String.format(Locale.ROOT, e.chance() >= 0.1F ? "%.0f" : "%.1f", e.chance() * 100F);
			String count = e.min() == e.max() ? Integer.toString(e.min()) : e.min() + "-" + e.max();
			builder.addOutputSlot().setStandardSlotBackground().add(stack).addRichTooltipCallback((view, tooltip) -> {
				tooltip.add(Component.translatableWithFallback("jei.elsewhere.loot.chance", "Chance per roll: %s%%", pct));
				tooltip.add(Component.translatableWithFallback("jei.elsewhere.loot.count", "Count: %s", count));
			});
		}
	}

	@Override
	public void createRecipeExtras(final IRecipeExtrasBuilder builder, final LootPage page, final IFocusGroup focuses) {
		List<FormattedText> head = List.of(title(page),
			Component.translatableWithFallback("jei.elsewhere.loot.rolls", "Rolls: %s", page.rolls()));
		builder.addText(head, WIDTH - 20, 20).setPosition(0, 0).setColor(0xFF404040);
		builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT), 8, 3).setPosition(0, 22);
	}

	@Override
	public Identifier getIdentifier(final LootPage page) {
		return page.id();
	}
}
