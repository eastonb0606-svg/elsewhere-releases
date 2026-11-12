package dev.elsewhere.gear3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;

/**
 * Registers every catalogue item. Kinds: plain / material, trinket, sword, axe, pickaxe, shovel, hoe, armor, shield.
 * Ids are looked up first (registry-collision rule): an id some other module already registered is reused, not duplicated.
 */
public final class Gear3Items {
	private Gear3Items() {}

	/** Set once registered; null when the catalogue has no such item. */
	public static Item RANGEFINDER, OATH_RING, QUIVER, BANDOLIER, SIGIL;
	public static final List<Item> ALL = new ArrayList<>();
	public static final Map<String, Item> BY_ID = new LinkedHashMap<>();
	/** Ids that were already registered by another module when gear3 ran (a catalogue/registry clash; the self-test fails on any). */
	public static final List<String> CLASHES = new ArrayList<>();
	/** Shields that only block melee (arrows pass): their reduction applies to this damage-type tag (data/elsewhere/tags/damage_type/gear3_melee.json). */
	public static final TagKey<DamageType> MELEE = TagKey.create(Registries.DAMAGE_TYPE, Gear3Content.id("gear3_melee"));

	public static Item get(String id) {
		Item i = BY_ID.get(id);
		if (i == null) throw new IllegalStateException("gear3 item not registered: " + id);
		return i;
	}

	public static void init() {
		ALL.clear();
		BY_ID.clear();
		CLASHES.clear();
		for (Gear3Def d : Gear3Catalogue.DEFS) {
			Item.Properties p = new Item.Properties().rarity(rarity(d.rarity));
			int stack = (int) d.stat("stack", d.kind.equals("trinket") || d.kind.equals("shield") ? 1 : 0);
			ItemAttributeModifiers.Builder extra = ItemAttributeModifiers.builder();
			switch (d.kind) {
				case "sword", "axe", "pickaxe", "shovel", "hoe" -> {
					float dmg = (float) d.stat("damage") - 1.0F, spd = (float) d.stat("speed") - 4.0F;
					ToolMaterial m = tool(d);
					switch (d.kind) {
						case "sword" -> p.sword(m, dmg, spd);
						case "axe" -> p.axe(m, dmg, spd);
						case "pickaxe" -> p.pickaxe(m, dmg, spd);
						case "shovel" -> p.shovel(m, dmg, spd);
						default -> p.hoe(m, dmg, spd);
					}
					if (!d.attrs.isEmpty()) {
						extra.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, dmg, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
						extra.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, spd, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
						for (Gear3Def.Attr a : d.attrs) addAttr(extra, d, a, null);
						p.attributes(extra.build());
					}
				}
				case "armor" -> {
					ArmorType type = ArmorType.valueOf(d.slot.toUpperCase(java.util.Locale.ROOT));
					ArmorMaterial m = material(d, type);
					p.humanoidArmor(m, type);
					if (!d.attrs.isEmpty()) {
						ItemAttributeModifiers mods = m.createAttributes(type);
						ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
						for (ItemAttributeModifiers.Entry e : mods.modifiers()) b.add(e.attribute(), e.modifier(), e.slot());
						for (Gear3Def.Attr a : d.attrs) addAttr(b, d, a, type);
						p.attributes(b.build());
					}
				}
				case "shield" -> shield(d, p);
				case "trinket", "plain", "material" -> {
					if (d.stat("durability", 0) > 0) p.durability((int) d.stat("durability"));
					if (!d.attrs.isEmpty()) {
						for (Gear3Def.Attr a : d.attrs) addAttr(extra, d, a, null);
						p.attributes(extra.build());
					}
				}
				default -> throw new IllegalStateException("catalogue item " + d.id + ": unknown kind " + d.kind);
			}
			if (stack > 0) p.stacksTo(stack);
			if (d.stat("fireproof", 0) > 0) p.fireResistant();
			if (!d.lore.isEmpty()) p.component(DataComponents.LORE, lore(d));
			Item item = register(d.id, Gear3Mechanics.factory(d.mechanic), p);
			BY_ID.put(d.id, item);
		}
		RANGEFINDER = BY_ID.get("rangefinder_lens");
		OATH_RING = BY_ID.get("aldermark_oath_ring");
		QUIVER = BY_ID.get("aldermark_quickdraw_quiver");
		BANDOLIER = BY_ID.get("mirewick_fowlers_bandolier");
		SIGIL = BY_ID.get("hush_binding_sigil");
		final Item icon = Gear3Catalogue.DEFS.isEmpty() ? net.minecraft.world.item.Items.STICK : BY_ID.get(iconId());
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Gear3Content.id("gear3"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.gear3")).icon(() -> new ItemStack(icon))
			.displayItems((params, out) -> {
				for (Item i : ALL) out.accept(i);
			}).build());
	}

	private static String iconId() {
		for (Gear3Def d : Gear3Catalogue.DEFS) if (d.stat("tab_icon", 0) > 0) return d.id;
		return Gear3Catalogue.DEFS.get(0).id;
	}

	static Rarity rarity(String r) {
		return switch (r) {
			case "uncommon" -> Rarity.UNCOMMON;
			case "rare" -> Rarity.RARE;
			case "epic" -> Rarity.EPIC;
			default -> Rarity.COMMON;
		};
	}

	public static TagKey<Item> itemTag(String path) {
		return TagKey.create(Registries.ITEM, path.contains(":") ? Identifier.parse(path) : Gear3Content.id(path));
	}

	/** The repair tag for a catalogue item; gen_resources.py writes data/elsewhere/tags/item/repairs_&lt;id&gt;.json from stats "repair". */
	public static TagKey<Item> repairTag(String id) {
		return itemTag("repairs_" + id);
	}

	public static Holder<Attribute> attribute(String id) {
		Identifier rl = Identifier.parse(id);
		return BuiltInRegistries.ATTRIBUTE.get(rl).orElseThrow(() -> new IllegalStateException("gear3: unknown attribute " + id));
	}

	public static Identifier modId(Gear3Def d, Gear3Def.Attr a, ArmorType type) {
		return Gear3Content.id("gear3/" + d.id + "." + a.attr().replace(':', '.'));
	}

	private static void addAttr(ItemAttributeModifiers.Builder b, Gear3Def d, Gear3Def.Attr a, ArmorType type) {
		AttributeModifier.Operation op = switch (a.op()) {
			case "mul_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case "mul_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> AttributeModifier.Operation.ADD_VALUE;
		};
		EquipmentSlotGroup g = switch (a.slot()) {
			case "mainhand" -> EquipmentSlotGroup.MAINHAND;
			case "offhand" -> EquipmentSlotGroup.OFFHAND;
			case "any" -> EquipmentSlotGroup.ANY;
			default -> type != null ? EquipmentSlotGroup.bySlot(type.getSlot()) : EquipmentSlotGroup.ARMOR;
		};
		b.add(attribute(a.attr()), new AttributeModifier(modId(d, a, type), a.amount(), op), g);
	}

	private static TagKey<Block> incorrectFor(String tier) {
		return switch (tier) {
			case "wood", "wooden", "gold", "golden" -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
			case "stone" -> BlockTags.INCORRECT_FOR_STONE_TOOL;
			case "iron", "copper" -> BlockTags.INCORRECT_FOR_IRON_TOOL;
			case "netherite" -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
			default -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
		};
	}

	/** Tool material: tier (stats "tier": wood|stone|iron|diamond|netherite), durability, mining speed, enchantability; attack bonus is 0 (the catalogue damage is the total). */
	private static ToolMaterial tool(Gear3Def d) {
		return new ToolMaterial(incorrectFor(d.sstat("tier", "diamond")), (int) d.stat("durability"), (float) d.stat("mining_speed", 6.0), 0.0F,
			(int) d.stat("enchantability", 15), repairTag(d.id));
	}

	private static Holder<SoundEvent> sound(String s) {
		return switch (s) {
			case "chain" -> SoundEvents.ARMOR_EQUIP_CHAIN;
			case "gold" -> SoundEvents.ARMOR_EQUIP_GOLD;
			case "diamond" -> SoundEvents.ARMOR_EQUIP_DIAMOND;
			case "netherite" -> SoundEvents.ARMOR_EQUIP_NETHERITE;
			case "leather" -> SoundEvents.ARMOR_EQUIP_LEATHER;
			default -> SoundEvents.ARMOR_EQUIP_IRON;
		};
	}

	/** One material per armor item: stats armor (this piece), toughness, kb, durability_mult, enchantability, sound; "set" names the shared equipment asset (worn model). */
	private static ArmorMaterial material(Gear3Def d, ArmorType type) {
		int a = (int) d.stat("armor");
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Gear3Content.id(d.set));
		return new ArmorMaterial((int) d.stat("durability_mult", 37), Map.of(type, a, ArmorType.BODY, a), (int) d.stat("enchantability", 15), sound(d.sstat("sound", "iron")),
			(float) d.stat("toughness", 0), (float) d.stat("kb", 0), repairTag(d.id), asset);
	}

	/** Shield (kind "shield"): BlocksAttacks like vanilla; stats shield_delay (s), disable_scale (1.0 = axe 5 s), melee_only (1 = arrows pass through), block (reduction factor, default 1.0), durability. */
	private static void shield(Gear3Def d, Item.Properties p) {
		p.durability((int) d.stat("durability")).equippableUnswappable(EquipmentSlot.OFFHAND);
		if (!d.sstat("repair", "").isEmpty() || d.stat("repairable", 0) > 0) p.repairable(repairTag(d.id));
		final float delay = (float) d.stat("shield_delay", 0.25), scale = (float) d.stat("disable_scale", 1.0), factor = (float) d.stat("block", 1.0);
		final boolean melee = d.stat("melee_only", 0) > 0;
		p.delayedComponent(DataComponents.BLOCKS_ATTACKS, ctx -> new BlocksAttacks(delay, scale,
			List.of(new BlocksAttacks.DamageReduction(90.0F, melee ? Optional.of(ctx.getOrThrow(MELEE)) : Optional.empty(), 0.0F, factor)),
			new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F), Optional.of(ctx.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
			Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)));
		p.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
	}

	/**
	 * Lore lines {@code item.elsewhere.<id>.lore1..n}: first grey, the rest blue; a catalogue line starting with "!" is a
	 * drawback and shows red (gen_resources.py strips the "!" from the lang text).
	 */
	static ItemLore lore(Gear3Def d) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= d.lore.size(); i++) {
			ChatFormatting c = d.lore.get(i - 1).startsWith("!") ? ChatFormatting.RED : (i == 1 ? ChatFormatting.GRAY : ChatFormatting.BLUE);
			l.add(Component.translatable("item.elsewhere." + d.id + ".lore" + i).withStyle(s -> s.withColor(c).withItalic(false)));
		}
		return new ItemLore(l);
	}

	static Item register(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties props) {
		Identifier id = Gear3Content.id(name);
		if (BuiltInRegistries.ITEM.containsKey(id)) {
			CLASHES.add(name);
			Item existing = BuiltInRegistries.ITEM.getValue(id);
			ALL.add(existing);
			return existing;
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}
}
