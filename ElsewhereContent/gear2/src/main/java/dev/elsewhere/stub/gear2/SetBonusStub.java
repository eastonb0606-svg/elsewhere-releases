package dev.elsewhere.stub.gear2;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * STUB of the core's SetBonus hook (BALANCE G6), same as arsenal's stub: a set is active when all 4 armor slots hold
 * an item in {@code #elsewhere:set/<set>/<slot>}. At merge, point {@link #active} at the core's SetBonus.
 */
public final class SetBonusStub {
	private SetBonusStub() {}

	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final String[] NAMES = {"head", "chest", "legs", "feet"};

	public static TagKey<Item> slotTag(String set, String slot) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "set/" + set + "/" + slot));
	}

	public static boolean active(Player p, String set) {
		return pieces(p, set) == 4;
	}

	public static int pieces(Player p, String set) {
		int n = 0;
		for (int i = 0; i < 4; i++) if (p.getItemBySlot(SLOTS[i]).is(slotTag(set, NAMES[i]))) n++;
		return n;
	}
}
