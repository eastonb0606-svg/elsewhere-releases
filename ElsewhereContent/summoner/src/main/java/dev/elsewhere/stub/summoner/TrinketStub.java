package dev.elsewhere.stub.summoner;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * STUB of arsenal's trinket scanning (ARSENAL.md §7): items tagged {@code elsewhere:trinket} work in the off hand or
 * the hotbar; at most 2 different trinkets are active, off hand first, then hotbar slots 1 to 9. At merge, point
 * {@link #isActive} at arsenal's trinket API.
 */
public final class TrinketStub {
	private TrinketStub() {}

	public static final TagKey<Item> TRINKET = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "trinket"));
	public static final int MAX_ACTIVE = 2;

	public static List<Item> active(Player player) {
		List<Item> out = new ArrayList<>(MAX_ACTIVE);
		consider(out, player.getOffhandItem());
		for (int i = 0; i < 9 && out.size() < MAX_ACTIVE; i++) consider(out, player.getInventory().getItem(i));
		return out;
	}

	private static void consider(List<Item> out, ItemStack stack) {
		if (out.size() >= MAX_ACTIVE || stack.isEmpty() || !stack.is(TRINKET)) return;
		if (!out.contains(stack.getItem())) out.add(stack.getItem());
	}

	private static boolean looked;
	private static java.lang.reflect.Method arsenalIsActive;

	/** Soft link: arsenal's {@code dev.elsewhere.arsenal.trinket.Trinkets.isActive(Player, Item)} when present (same rules). */
	public static boolean isActive(Player player, Item item) {
		if (!looked) {
			looked = true;
			try {
				arsenalIsActive = Class.forName("dev.elsewhere.arsenal.trinket.Trinkets").getMethod("isActive", Player.class, Item.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				arsenalIsActive = null;
			}
		}
		if (arsenalIsActive != null) {
			try {
				return (Boolean) arsenalIsActive.invoke(null, player, item);
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall back to the local scan
			}
		}
		return active(player).contains(item);
	}
}
