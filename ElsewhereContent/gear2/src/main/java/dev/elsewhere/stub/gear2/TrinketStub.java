package dev.elsewhere.stub.gear2;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * STUB of arsenal's {@code dev.elsewhere.arsenal.trinket.Trinkets} (ARSENAL.md §7), same rule: items in
 * {@code #elsewhere:trinket} work from the off hand or the hotbar; at most 2 different ones are active (off hand first,
 * then hotbar 1→9). At merge, point {@link #active} / {@link #isActive} at {@code Trinkets.active / isActive}.
 */
public final class TrinketStub {
	private TrinketStub() {}

	public static final TagKey<Item> TRINKET = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "trinket"));
	public static final int MAX_ACTIVE = 2;

	public static List<Item> active(Player p) {
		List<Item> out = new ArrayList<>(2);
		consider(out, p.getOffhandItem());
		for (int i = 0; i < 9 && out.size() < MAX_ACTIVE; i++) consider(out, p.getInventory().getItem(i));
		return out;
	}

	private static void consider(List<Item> out, ItemStack s) {
		if (out.size() >= MAX_ACTIVE || s.isEmpty() || !s.is(TRINKET)) return;
		if (!out.contains(s.getItem())) out.add(s.getItem());
	}

	public static boolean isActive(Player p, Item item) {
		return active(p).contains(item);
	}

	/** The stack that makes {@code item} active (off hand first, then hotbar), or EMPTY. */
	public static ItemStack activeStack(Player p, Item item) {
		if (!isActive(p, item)) return ItemStack.EMPTY;
		if (p.getOffhandItem().is(item)) return p.getOffhandItem();
		for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(item)) return p.getInventory().getItem(i);
		return ItemStack.EMPTY;
	}
}
