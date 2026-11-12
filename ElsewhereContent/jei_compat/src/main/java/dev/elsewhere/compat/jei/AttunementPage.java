package dev.elsewhere.compat.jei;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** One Wayfinder attunement from {@code data/elsewhere/attunement/<dimension>.json}: base Wayfinder + 5 ingredients -> attuned Wayfinder. */
public record AttunementPage(Identifier id, Identifier dimension, List<Item> ingredients, Item wayfinder, Item result, String requires) {
	public boolean needsDragon() {
		return "ender_dragon_killed".equals(this.requires);
	}
}
