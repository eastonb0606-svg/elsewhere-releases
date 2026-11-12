package dev.elsewhere.gear3.s3.ascension;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Mechanics;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

/**
 * s-ascension: the Ascension Shard (GEAR.md A6, tier G5). A G4 set piece (any item in {@code #elsewhere:gear3_ascendable}: every
 * gear3 armor piece plus the other modules' pieces of the same sets) and one shard in a smithing table, with no template,
 * make the same piece Ascendant: +1 armor toughness (added to the piece's own toughness modifier), gold "Ascendant" name,
 * one gold lore line, enchantment glint and the {@code elsewhere:gear3_ascended} marker. Everything else on the stack
 * (enchantments, damage, trims, custom name) is kept. A full set of Ascendant pieces gives set bonus II
 * ({@link SetBonuses#ascendant} and {@link SetBonuses#TIER2_SCALE}). The item id never changes, so calling tags and set-bonus
 * piece lists keep working. Drops: {@link AscensionSources} (T5/T6 bosses and kingdom rulers, 1 each).
 * Kill switch: {@code "ascension": false} in config/elsewhere-overhaul3.json stops the drops and the smithing recipe; pieces
 * already Ascendant keep their stats. Event-driven only (one smithing recipe, one AFTER_DEATH hook); nothing ticks.
 */
public final class Ascension {
	private Ascension() {}

	public static final String ID = "gear3_ascension_shard";
	public static final String KILL_KEY = "ascension";
	public static final boolean ENABLED = dev.elsewhere.arsenal.Overhaul3.on(KILL_KEY);
	/** Armor toughness an Ascendant piece gains (GEAR.md: "+1 toughness each"). */
	public static final double TOUGHNESS = 1.0;
	/** The pieces a shard can ascend (data/elsewhere/tags/item/gear3_ascendable.json). */
	public static final TagKey<Item> ASCENDABLE = TagKey.create(Registries.ITEM, Gear3Content.id("gear3_ascendable"));
	/** Used only when a piece has no toughness modifier of its own (none in 3.0; kept so a future piece still gains +1). */
	public static final Identifier TOUGHNESS_ID = Gear3Content.id("ascension_toughness");
	public static final String NAME_KEY = "item.elsewhere." + ID + ".ascendant";
	public static final String LORE_KEY = "item.elsewhere." + ID + ".ascended_lore";

	/** Marker on an Ascendant piece. Persistent and synced (the client's set-bonus tooltip reads it too). */
	public static DataComponentType<Unit> ASCENDED;

	private static boolean done;

	/** Called from Gear3Content.init() BEFORE Gear3Items.init() (the catalogue item's mechanic "ascension_shard" needs its factory). */
	public static void init() {
		if (done) return;
		done = true;
		Gear3Mechanics.FACTORIES.put("ascension_shard", p -> new Item(p.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
		Identifier cid = Gear3Content.id("gear3_ascended");
		@SuppressWarnings("unchecked")
		DataComponentType<Unit> existing = (DataComponentType<Unit>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(cid);
		ASCENDED = existing != null ? existing
			: Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, cid, DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());
		AscensionRecipe.register();
		SetBonuses.ascendant = Ascension::isAscended;
		AscensionSources.init();
	}

	public static Item shard() {
		return BuiltInRegistries.ITEM.getValue(Gear3Content.id(ID));
	}

	public static boolean isAscended(ItemStack s) {
		return ASCENDED != null && !s.isEmpty() && s.has(ASCENDED);
	}

	/** A shard can ascend this stack: a G4 set piece that is not Ascendant yet. */
	public static boolean canAscend(ItemStack s) {
		return !s.isEmpty() && s.is(ASCENDABLE) && !isAscended(s);
	}

	/** The Ascendant copy of {@code base} (count 1); {@code base} is not changed. Callers check {@link #canAscend} first. */
	public static ItemStack ascend(ItemStack base) {
		ItemStack out = base.copyWithCount(1);
		ItemAttributeModifiers mods = out.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
		boolean raised = false;
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			AttributeModifier m = e.modifier();
			if (!raised && e.attribute().value() == Attributes.ARMOR_TOUGHNESS.value() && m.operation() == AttributeModifier.Operation.ADD_VALUE) {
				m = new AttributeModifier(m.id(), m.amount() + TOUGHNESS, m.operation());
				raised = true;
			}
			b.add(e.attribute(), m, e.slot(), e.display());
		}
		if (!raised) b.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_ID, TOUGHNESS, AttributeModifier.Operation.ADD_VALUE), slotGroup(out));
		out.set(DataComponents.ATTRIBUTE_MODIFIERS, b.build());
		out.set(DataComponents.ITEM_NAME, Component.translatable(NAME_KEY, base.getItemName()).withStyle(ChatFormatting.GOLD));
		ItemLore lore = out.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		if (lore.lines().size() < ItemLore.MAX_LINES)
			out.set(DataComponents.LORE, lore.withLineAdded(Component.translatable(LORE_KEY).withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false))));
		out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		out.set(ASCENDED, Unit.INSTANCE);
		return out;
	}

	private static EquipmentSlotGroup slotGroup(ItemStack s) {
		Equippable eq = s.get(DataComponents.EQUIPPABLE);
		return eq != null ? EquipmentSlotGroup.bySlot(eq.slot()) : EquipmentSlotGroup.ARMOR;
	}
}
