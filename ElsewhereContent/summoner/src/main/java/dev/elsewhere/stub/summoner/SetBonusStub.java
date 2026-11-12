package dev.elsewhere.stub.summoner;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * STUB of the core's SetBonus hook (BALANCE G6): a set is worn when all 4 armor slots hold an item from the slot tags
 * {@code elsewhere:set/<set>/<head|chest|legs|feet>}. At merge, point {@link #wearing} at the core's SetBonus.
 */
public final class SetBonusStub {
	private SetBonusStub() {}

	private static final String[] SLOT_NAMES = {"head", "chest", "legs", "feet"};
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public static TagKey<Item> slotTag(String set, int i) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "set/" + set + "/" + SLOT_NAMES[i]));
	}

	public static boolean wearing(Player p, String set) {
		for (int i = 0; i < 4; i++) {
			if (!p.getItemBySlot(SLOTS[i]).is(slotTag(set, i))) return false;
		}
		return true;
	}
}
