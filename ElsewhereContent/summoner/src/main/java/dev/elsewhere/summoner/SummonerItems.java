package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionStaffItem;
import dev.elsewhere.stub.summoner.SummonerAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Every summoner item of ARSENAL.md §5 (numbers in {@link GearStats}). */
public final class SummonerItems {
	private SummonerItems() {}

	public static Item SPOREBLOOM_STAFF, GALE_RIBBON;
	public static Item ROOTCALL_STAFF, KITESTRING_STAFF, BRASSBACK_KEY;
	public static Item HERDCALLER_CAP, HERDCALLER_VEST, HERDCALLER_LEGGINGS, HERDCALLER_BOOTS;
	public static Item LINED_HERDCALLER_CAP, LINED_HERDCALLER_VEST, LINED_HERDCALLER_LEGGINGS, LINED_HERDCALLER_BOOTS;
	public static Item ELDER_BARK_CROWN, ENGINEERS_HARNESS;
	public static Item GORGER_FLUFF, BELL_OF_THE_FLOCK, TRAIL_BISCUIT, HERDING_CROOK;
	public static final List<Item> ALL = new ArrayList<>();

	private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

	static TagKey<Item> tag(String path) {
		return TagKey.create(Registries.ITEM, SummonerContent.id(path));
	}

	static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, SummonerContent.id(name));
	}

	private static final Map<String, Integer> LORE = Map.ofEntries(
		Map.entry("rootcall_staff", 1), Map.entry("kitestring_staff", 1), Map.entry("brassback_key", 1),
		Map.entry("herdcaller_cap", 2), Map.entry("herdcaller_vest", 2), Map.entry("herdcaller_leggings", 2), Map.entry("herdcaller_boots", 3),
		Map.entry("lined_herdcaller_cap", 2), Map.entry("lined_herdcaller_vest", 2), Map.entry("lined_herdcaller_leggings", 2), Map.entry("lined_herdcaller_boots", 3),
		Map.entry("elder_bark_crown", 2), Map.entry("engineers_harness", 2), Map.entry("bell_of_the_flock", 1), Map.entry("trail_biscuit", 1), Map.entry("herding_crook", 1), Map.entry("sporebloom_staff", 2), Map.entry("gale_ribbon", 2));

	/** Registers an item, or returns the one already registered under this id (shared ids in the merged beta). */
	public static Item item(String path, Function<Item.Properties, ? extends Item> factory, Item.Properties props) {
		Identifier id = SummonerContent.id(path);
		int n = LORE.getOrDefault(path, 0);
		if (n > 0) {
			List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
			for (int i = 0; i < n; i++) lines.add(net.minecraft.network.chat.Component.translatable("item.elsewhere." + path + ".desc" + i)
				.withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)));
			props.component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
		}
		Item it;
		if (BuiltInRegistries.ITEM.containsKey(id)) it = BuiltInRegistries.ITEM.getValue(id);
		else {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
			it = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		}
		ALL.add(it);
		return it;
	}

	private static ArmorMaterial material(int durMult, int[] def, int ench, float tough, float kb, String repairTag, String assetName, Holder<SoundEvent> sound) {
		Map<ArmorType, Integer> m = new EnumMap<>(ArmorType.class);
		for (int i = 0; i < 4; i++) m.put(TYPES[i], def[i]);
		m.put(ArmorType.BODY, def[1]);
		return new ArmorMaterial(durMult, m, ench, sound, tough, kb, tag(repairTag), asset(assetName));
	}

	/** Armor + toughness + KB (if any) + extra perk modifiers, all on the piece's slot. */
	private static ItemAttributeModifiers armorMods(String id, ArmorType type, double armor, double tough, double kb, Object... perks) {
		EquipmentSlotGroup g = EquipmentSlotGroup.bySlot(type.getSlot());
		ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(SummonerContent.id("armor." + id), armor, AttributeModifier.Operation.ADD_VALUE), g)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(SummonerContent.id("toughness." + id), tough, AttributeModifier.Operation.ADD_VALUE), g);
		if (kb > 0) b.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(SummonerContent.id("kb." + id), kb, AttributeModifier.Operation.ADD_VALUE), g);
		for (int i = 0; i + 1 < perks.length; i += 2) {
			@SuppressWarnings("unchecked") Holder<Attribute> h = (Holder<Attribute>) perks[i];
			double v = (Double) perks[i + 1];
			if (h == null) continue; // arsenal's attribute missing: skip cleanly
			boolean slotPerk = h == SummonerAttributes.slots();
			String key = h == Attributes.ATTACK_DAMAGE ? "own_damage." : h == SummonerAttributes.damage() ? "minion_damage." : slotPerk ? "minion_slots." : "spell_power.";
			b.add(h, new AttributeModifier(SummonerContent.id(key + id), v, slotPerk ? AttributeModifier.Operation.ADD_VALUE : AttributeModifier.Operation.ADD_MULTIPLIED_BASE), g);
		}
		return b.build();
	}

	/** Summoner pieces: own attack_damage and spell_power -20% (add_multiplied_base) on top of the perks. */
	private static Item armor(String id, ArmorMaterial mat, int slot, Rarity rarity, Object... perks) {
		ArmorType type = TYPES[slot];
		Holder<Attribute> spell = BuiltInRegistries.ATTRIBUTE.get(SummonerContent.id("spell_power")).map(h -> (Holder<Attribute>) h).orElse(null);
		Object[] all = new Object[perks.length + 4];
		System.arraycopy(perks, 0, all, 0, perks.length);
		all[perks.length] = Attributes.ATTACK_DAMAGE;
		all[perks.length + 1] = GearStats.OWN_DAMAGE_PER_PIECE;
		all[perks.length + 2] = spell;
		all[perks.length + 3] = GearStats.OWN_DAMAGE_PER_PIECE;
		perks = all;
		return item(id, Item::new, new Item.Properties().humanoidArmor(mat, type).rarity(rarity)
			.attributes(armorMods(id, type, mat.defense().get(type), mat.toughness(), mat.knockbackResistance(), perks)));
	}

	public static void init() {
		SummonerAttributes.init();
		Holder<Attribute> slots = SummonerAttributes.slots();
		Holder<Attribute> dmg = SummonerAttributes.damage();

		// ---- Herding Crook (rev 3 §5b): 5 damage, 1.6 speed, +1.5 reach, 1561 durability ----
		HERDING_CROOK = item("herding_crook", HerdingCrookItem::new, new Item.Properties().durability(GearStats.CROOK_DURABILITY).enchantable(GearStats.CROOK_ENCHANT)
			.rarity(Rarity.UNCOMMON).component(net.minecraft.core.component.DataComponents.WEAPON, new net.minecraft.world.item.component.Weapon(1))
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, GearStats.CROOK_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, GearStats.CROOK_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(SummonerContent.id("herding_crook.reach"), GearStats.CROOK_REACH, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build()));

		// ---- staffs (§5b) ----
		SPOREBLOOM_STAFF = item("sporebloom_staff", p -> new MinionStaffItem(() -> SummonerEntities.SPORELING, p),
			new Item.Properties().durability(GearStats.STAFF_DURABILITY).repairable(tag("repairs_sporebloom_staff")).rarity(Rarity.RARE));
		GALE_RIBBON = item("gale_ribbon", GaleRibbonItem::new, new Item.Properties().durability(GearStats.RIBBON_DURABILITY).rarity(Rarity.RARE)
			.repairable(tag("repairs_gale_ribbon")).attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, GearStats.RIBBON_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, GearStats.RIBBON_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(SummonerContent.id("gale_ribbon.reach"), GearStats.RIBBON_REACH, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build()));
		ROOTCALL_STAFF = item("rootcall_staff", p -> new MinionStaffItem(() -> SummonerEntities.ROOTLING, p),
			new Item.Properties().durability(GearStats.STAFF_DURABILITY).repairable(tag("repairs_rootcall_staff")).rarity(Rarity.UNCOMMON));
		KITESTRING_STAFF = item("kitestring_staff", p -> new MinionStaffItem(() -> SummonerEntities.PAPER_KITE, p),
			new Item.Properties().durability(GearStats.STAFF_DURABILITY).repairable(tag("repairs_kitestring_staff")).rarity(Rarity.UNCOMMON));
		BRASSBACK_KEY = item("brassback_key", p -> new MinionStaffItem(() -> SummonerEntities.BRASSBACK, p),
			new Item.Properties().durability(GearStats.STAFF_DURABILITY).repairable(tag("repairs_brassback_key")).rarity(Rarity.RARE));

		// ---- Herdcaller (§5c) ----
		ArmorMaterial herd = material(GearStats.HERDCALLER_DURABILITY_MULT, GearStats.HERDCALLER_ARMOR, GearStats.HERDCALLER_ENCHANT,
			GearStats.HERDCALLER_TOUGHNESS, 0F, "repairs_herdcaller_armor", "herdcaller", SoundEvents.ARMOR_EQUIP_LEATHER);
		HERDCALLER_CAP = armor("herdcaller_cap", herd, 0, Rarity.UNCOMMON, slots, GearStats.CAP_SLOTS);
		HERDCALLER_VEST = armor("herdcaller_vest", herd, 1, Rarity.UNCOMMON, slots, GearStats.VEST_SLOTS);
		HERDCALLER_LEGGINGS = armor("herdcaller_leggings", herd, 2, Rarity.UNCOMMON, dmg, GearStats.LEGGINGS_MINION_DAMAGE);
		HERDCALLER_BOOTS = armor("herdcaller_boots", herd, 3, Rarity.UNCOMMON);

		ArmorMaterial lined = material(GearStats.LINED_DURABILITY_MULT, GearStats.LINED_ARMOR, GearStats.HERDCALLER_ENCHANT,
			GearStats.LINED_TOUGHNESS, GearStats.LINED_KB, "repairs_herdcaller_armor", "lined_herdcaller", SoundEvents.ARMOR_EQUIP_NETHERITE);
		LINED_HERDCALLER_CAP = armor("lined_herdcaller_cap", lined, 0, Rarity.RARE, slots, GearStats.CAP_SLOTS);
		LINED_HERDCALLER_VEST = armor("lined_herdcaller_vest", lined, 1, Rarity.RARE, slots, GearStats.VEST_SLOTS);
		LINED_HERDCALLER_LEGGINGS = armor("lined_herdcaller_leggings", lined, 2, Rarity.RARE, dmg, GearStats.LEGGINGS_MINION_DAMAGE);
		LINED_HERDCALLER_BOOTS = armor("lined_herdcaller_boots", lined, 3, Rarity.RARE);

		// ---- boss pieces ----
		ArmorMaterial crown = material(GearStats.BOSS_PIECE_DURABILITY_MULT, new int[]{GearStats.CROWN_ARMOR, 8, 6, 3}, GearStats.BOSS_PIECE_ENCHANT,
			GearStats.CROWN_TOUGHNESS, 0F, "repairs_herdcaller_armor", "elder_bark_crown", SoundEvents.ARMOR_EQUIP_LEATHER);
		ELDER_BARK_CROWN = armor("elder_bark_crown", crown, 0, Rarity.EPIC, slots, GearStats.CROWN_SLOTS);
		ArmorMaterial harness = material(GearStats.BOSS_PIECE_DURABILITY_MULT, new int[]{3, GearStats.HARNESS_ARMOR, 6, 3}, GearStats.BOSS_PIECE_ENCHANT,
			GearStats.HARNESS_TOUGHNESS, 0F, "repairs_engineers_harness", "engineers_harness", SoundEvents.ARMOR_EQUIP_IRON);
		ENGINEERS_HARNESS = armor("engineers_harness", harness, 1, Rarity.EPIC, slots, GearStats.HARNESS_SLOTS, dmg, GearStats.HARNESS_MINION_DAMAGE);

		// ---- material, trinket, consumable ----
		GORGER_FLUFF = item("gorger_fluff", Item::new, new Item.Properties());
		BELL_OF_THE_FLOCK = item("bell_of_the_flock", BellOfTheFlockItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		TRAIL_BISCUIT = item("trail_biscuit", TrailBiscuitItem::new, new Item.Properties().stacksTo(GearStats.BISCUIT_STACK)
			.food(new FoodProperties(GearStats.BISCUIT_NUTRITION, GearStats.BISCUIT_SATURATION, true),
				Consumables.defaultFood().consumeSeconds(GearStats.BISCUIT_USE_SECONDS).build()));
	}
}
