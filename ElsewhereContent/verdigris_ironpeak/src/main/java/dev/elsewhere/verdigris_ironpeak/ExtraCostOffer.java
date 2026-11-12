package dev.elsewhere.verdigris_ironpeak;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A trade with more than the two inputs a merchant screen has (ARSENAL.md: "bow + 4 Roc Feathers + 2 Skyiron", "Amethyst
 * Wand + Storm Coil + 12 Gears + 2 Pearlite"). The first two inputs go in the trade slots as usual; the extra inputs are
 * taken from the customer's inventory when the trade completes and the trade is unavailable while they are missing.
 * The offered result shows the extra price as lore; the item you receive has no lore. Offers are rebuilt for each
 * customer ({@link ElsewhereTrader}), so the captured player is always the one trading.
 */
public class ExtraCostOffer extends MerchantOffer {
	private final Player player;
	private final List<ItemStack> extras;
	private final ItemStack clean;

	public ExtraCostOffer(Player player, ItemCost a, Optional<ItemCost> b, ItemStack result, List<ItemStack> extras, int maxUses, int xp, float mult) {
		super(a, b, labelled(result, extras), maxUses, xp, mult);
		this.player = player;
		this.extras = List.copyOf(extras);
		this.clean = result.copy();
	}

	private static ItemStack labelled(ItemStack result, List<ItemStack> extras) {
		ItemStack shown = result.copy();
		List<Component> lines = new ArrayList<>();
		for (ItemStack e : extras) {
			lines.add(Component.translatableWithFallback("trade.elsewhere.extra_cost", "Also takes %s x %s from your inventory", e.getCount(), e.getHoverName())
				.withStyle(ChatFormatting.GOLD));
		}
		shown.set(DataComponents.LORE, new ItemLore(lines));
		return shown;
	}

	public List<ItemStack> extras() {
		return extras;
	}

	/** True when the customer carries every extra input. */
	public boolean hasExtras() {
		if (player == null) return false;
		if (player.hasInfiniteMaterials()) return true;
		for (ItemStack e : extras) {
			if (player.getInventory().countItem(e.getItem()) < e.getCount()) return false;
		}
		return true;
	}

	@Override
	public boolean satisfiedBy(ItemStack buyA, ItemStack buyB) {
		return super.satisfiedBy(buyA, buyB) && hasExtras();
	}

	@Override
	public boolean take(ItemStack buyA, ItemStack buyB) {
		if (!hasExtras() || !super.take(buyA, buyB)) return false;
		if (!player.hasInfiniteMaterials()) {
			for (ItemStack e : extras) {
				player.getInventory().clearOrCountMatchingItems(s -> s.is(e.getItem()), false, e.getCount(), player.inventoryMenu.getCraftSlots());
			}
			player.inventoryMenu.broadcastChanges();
		}
		return true;
	}

	@Override
	public ItemStack assemble() {
		return clean.copy();
	}
}
