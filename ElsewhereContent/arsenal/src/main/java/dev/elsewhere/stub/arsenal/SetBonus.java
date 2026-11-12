package dev.elsewhere.stub.arsenal;

import dev.elsewhere.arsenal.ArsenalContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * STUB of the core's SetBonus hook (BALANCE G6): a set is active when all 4 armor slots hold an item in
 * {@code #elsewhere:set/<set>/<slot>}. At merge, point {@link #active} at the core's SetBonus.
 */
public final class SetBonus {
	private SetBonus() {}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final String[] NAMES = {"head", "chest", "legs", "feet"};

	public static TagKey<Item> slotTag(String set, String slot) {
		return TagKey.create(Registries.ITEM, ArsenalContent.id("set/" + set + "/" + slot));
	}

	public static boolean active(Player p, String set) {
		for (int i = 0; i < 4; i++) if (!p.getItemBySlot(SLOTS[i]).is(slotTag(set, NAMES[i]))) return false;
		return true;
	}

	/** Moonsilk / Lined Moonsilk full set: "Moonlit". */
	public static boolean moonlit(Player p) {
		return active(p, "moonsilk");
	}
}
