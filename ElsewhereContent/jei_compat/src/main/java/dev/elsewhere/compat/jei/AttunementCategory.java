package dev.elsewhere.compat.jei;

import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** "Wayfinder Attunement": a base Wayfinder in the middle of a crafting grid with a dimension's 5 ingredients (any layout works). */
public final class AttunementCategory extends AbstractRecipeCategory<AttunementPage> {
	public static final IRecipeType<AttunementPage> TYPE = IRecipeType.create("elsewhere", "wayfinder_attunement", AttunementPage.class);
	/** Canonical layout of DIMENSIONS.md section 8: a b a / c W c / d e d (ingredient indices; -1 = the base Wayfinder). */
	private static final int[] LAYOUT = {0, 1, 0, 2, -1, 2, 3, 4, 3};
	private static final int WIDTH = 120;
	private static final int HEIGHT = 100;

	public AttunementCategory(final IGuiHelper gui, final ItemStack icon) {
		super(TYPE, Component.translatableWithFallback("jei.elsewhere.wayfinder_attunement", "Wayfinder Attunement"),
			gui.createDrawableItemStack(icon), WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(final IRecipeLayoutBuilder builder, final AttunementPage page, final IFocusGroup focuses) {
		for (int i = 0; i < 9; i++) {
			int idx = LAYOUT[i];
			var slot = builder.addInputSlot(1 + (i % 3) * 18, 1 + (i / 3) * 18).setStandardSlotBackground();
			if (idx < 0) {
				slot.add(page.wayfinder());
			} else if (idx < page.ingredients().size()) {
				slot.add(page.ingredients().get(idx));
			}
		}
		builder.addOutputSlot(95, 19).setOutputSlotBackground().add(page.result());
	}

	@Override
	public void createRecipeExtras(final IRecipeExtrasBuilder builder, final AttunementPage page, final IFocusGroup focuses) {
		builder.addRecipeArrowWidget().setPosition(62, 19);
		// Two short paragraphs, each wrapped to the page width, in a box tall enough for 4 lines: the old single sentence was
		// cut off after the third line on The Hush page (QA #11).
		List<FormattedText> lines = page.needsDragon()
			? List.of(Component.translatableWithFallback("jei.elsewhere.attunement.dragon", "Needs the Ender Dragon defeated first."),
				Component.translatableWithFallback("jei.elsewhere.attunement.any_layout_short", "Any layout works."))
			: List.of(Component.translatableWithFallback("jei.elsewhere.attunement.any_layout", "Any layout works: Wayfinder + one of each."));
		builder.addText(lines, WIDTH, 40).setPosition(0, 57).setColor(page.needsDragon() ? 0xFFAA2222 : 0xFF555555);
	}

	@Override
	public Identifier getIdentifier(final AttunementPage page) {
		return page.id();
	}
}
