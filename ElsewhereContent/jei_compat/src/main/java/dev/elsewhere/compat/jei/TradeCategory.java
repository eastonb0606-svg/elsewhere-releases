package dev.elsewhere.compat.jei;

import dev.elsewhere.compat.jei.ElsewhereJeiData.TradePage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "Kingdom Shops": every line of the four kingdoms' shops (Aldermark's Royal Steward, Hrafnholt's Silver-Scales, Azmar's
 * Counting-Table, Mirewick's Scrip-Desk and its weekly auction), from the same faction and {@code kingdom_trade} files the shops read.
 * Price in the kingdom's coin (plus any extra items) on the left, the goods on the right; tier, weekly limit and sell-back below.
 */
public final class TradeCategory extends AbstractRecipeCategory<TradePage> {
	public static final IRecipeType<TradePage> TYPE = IRecipeType.create("elsewhere", "kingdom_shop", TradePage.class);
	private static final int WIDTH = 150;
	private static final int HEIGHT = 52;

	public TradeCategory(final IGuiHelper gui, final ItemStack icon) {
		super(TYPE, Component.translatableWithFallback("jei.elsewhere.kingdom_shop", "Kingdom Shops"), gui.createDrawableItemStack(icon), WIDTH, HEIGHT);
	}

	/** Each kingdom's shop by its own name (RIVALS.md: Silver-Scales, Counting-Table, Scrip-Desk; Aldermark keeps its Royal Steward). */
	static Component shop(final String faction) {
		String fallback = switch (faction) {
			case "aldermark" -> "Aldermark: the Royal Steward";
			case "hrafnholt" -> "Hrafnholt: the Silver-Scales";
			case "azmar" -> "Azmar: the Counting-Table";
			case "mirewick" -> "Mirewick: the Scrip-Desk";
			default -> LootCategory.pretty(faction);
		};
		return Component.translatableWithFallback("jei.elsewhere.kingdom_shop." + faction, fallback);
	}

	private static ItemStack stack(final Item item, final int count) {
		ItemStack s = new ItemStack(item);
		s.setCount(Math.max(1, Math.min(count, s.getMaxStackSize())));
		return s;
	}

	@Override
	public void setRecipe(final IRecipeLayoutBuilder builder, final TradePage page, final IFocusGroup focuses) {
		builder.addInputSlot(1, 1).setStandardSlotBackground().add(stack(page.currency(), page.price()));
		int x = 19;
		for (Map.Entry<Item, Integer> e : page.extra()) {
			builder.addInputSlot(x, 1).setStandardSlotBackground().add(stack(e.getKey(), e.getValue()));
			x += 18;
		}
		builder.addOutputSlot(WIDTH - 19, 1).setOutputSlotBackground().add(stack(page.result(), page.count()));
	}

	@Override
	public void createRecipeExtras(final IRecipeExtrasBuilder builder, final TradePage page, final IFocusGroup focuses) {
		builder.addRecipeArrowWidget().setPosition(WIDTH - 50, 1);
		List<FormattedText> lines = new ArrayList<>();
		lines.add(shop(page.faction()));
		if (page.auction()) {
			lines.add(Component.translatableWithFallback("jei.elsewhere.kingdom_shop.auction", "Weekly auction lot: bids from %s up to %s",
				page.price(), page.max()));
		} else {
			lines.add(Component.translatableWithFallback("jei.elsewhere.kingdom_shop.price", "Price: %s", page.price()));
		}
		List<String> notes = new ArrayList<>();
		if (!page.tier().isEmpty()) {
			notes.add(LootCategory.pretty(page.tier()));
		}
		if (page.weeklyLimit() > 0) {
			notes.add(page.weeklyLimit() + "/week");
		}
		if (page.sell() > 0) {
			notes.add("sells back for " + page.sell());
		}
		if (!notes.isEmpty()) {
			lines.add(Component.translatableWithFallback("jei.elsewhere.kingdom_shop.notes", "%s", String.join(", ", notes)));
		}
		builder.addText(lines, WIDTH, 32).setPosition(0, 20).setColor(0xFF404040);
	}

	@Override
	public Identifier getIdentifier(final TradePage page) {
		return page.id();
	}
}
