package dev.elsewhere.kingdom_court.trade;

import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The four trade items of RIVALS-DECISIONS 12 (ids are final): Hrafnholt's Arm-Ring, Azmar's Commission Slip and Letter of Credit, Mirewick's Loan Note. The slip, letter and note
 * carry their facts in {@code custom_data} (what the player sees is lore); the server's {@link TradeState} is the authority, so a forged item buys nothing.
 */
public final class TradeItems {
	private TradeItems() {}

	public static Item ARM_RING, COMMISSION_SLIP, LETTER_OF_CREDIT, LOAN_NOTE;
	public static final List<Item> ALL = new ArrayList<>();

	static <T extends Item> T reg(String id, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CourtContent.id(id));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		CourtItems.ALL.add(item);
		return item;
	}

	static Item.Properties lore(Item.Properties p, String id, int lines) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= lines; i++) l.add(Component.translatable("item.elsewhere." + id + ".desc" + i));
		return p.component(DataComponents.LORE, new ItemLore(l));
	}

	public static void init() {
		ARM_RING = reg("hrafnholt_arm_ring", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), "hrafnholt_arm_ring", 3));
		COMMISSION_SLIP = reg("azmar_commission_slip", Item::new, lore(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "azmar_commission_slip", 2));
		LETTER_OF_CREDIT = reg("azmar_letter_of_credit", LetterItem::new, lore(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "azmar_letter_of_credit", 2));
		LOAN_NOTE = reg("mirewick_loan_note", Item::new, lore(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "mirewick_loan_note", 2));
	}

	/** Right-click the Letter of Credit to read the balance the Sultanate holds for you. */
	public static final class LetterItem extends Item {
		public LetterItem(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.literal("Letter of Credit: " + AzmarTrade.balance(sl, sp.getUUID()) + " Dinars held in your name at every Azmar treasury."));
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---- custom data on the paper items --------------------------------------------------------------------------------------

	public static ItemStack withData(Item item, CompoundTag data, List<Component> lore) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		s.set(DataComponents.LORE, new ItemLore(lore));
		return s;
	}

	public static CompoundTag data(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d == null ? new CompoundTag() : d.copyTag();
	}

	/** First stack of {@code item} in the player's inventory (main hand first), or null. */
	public static @Nullable ItemStack find(ServerPlayer p, Item item) {
		if (p.getMainHandItem().is(item)) return p.getMainHandItem();
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) return p.getInventory().getItem(i);
		return null;
	}

	public static Identifier idOf(Item i) {
		return BuiltInRegistries.ITEM.getKey(i);
	}
}
