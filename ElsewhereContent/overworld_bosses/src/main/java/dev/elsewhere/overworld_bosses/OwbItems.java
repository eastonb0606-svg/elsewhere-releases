package dev.elsewhere.overworld_bosses;

import dev.elsewhere.boss.vorlagh.InkGlob;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;

/** Boss drops, summon items, tie-in foods and projectile sprites. Numbers come from {@link GearStats}. */
public final class OwbItems {
	public static Item LILYCROWN, GULP_TONGUE, FROG_LEGS, BUFO_GLAND, FROGSPAWN_GLOB;
	public static Item HARVEST_SCYTHE, SCARECROWS_GRIN, HARVEST_MOON_SEEDS, HARVEST_LOAF, HARVEST_MOON_BUD, CROW_FEATHER, SEED_FIREBALL;
	public static Item ANKH_OF_NEFERKHET, SUNFIRE_KHOPESH, PHARAOHS_NEMES, ROYAL_HONEY, LINEN_WRAP;
	public static Item RIMEHEART_MAUL, RIMEWALKER_BOOTS, GLACIER_WATER, FROST_SHARD;
	public static Item UNDERTOW, KRAKEN_INK_BOMB, ABYSSAL_EYE, SIREN_CONCH, KRAKEN_CALAMARI;

	private OwbItems() {}

	private static Item item(String id, Item.Properties p) {
		return BossRegistry.item(id, Item::new, p);
	}

	private static Item food(String id, int nutrition, float sat) {
		return item(id, new Item.Properties().food(new FoodProperties(nutrition, sat, false)));
	}

	private static ItemAttributeModifiers armorAttrs(String id, double armor, double tough, EquipmentSlotGroup slot) {
		return ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(BossRegistry.id("armor." + id), armor, AttributeModifier.Operation.ADD_VALUE), slot)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(BossRegistry.id("toughness." + id), tough, AttributeModifier.Operation.ADD_VALUE), slot)
			.build();
	}

	public static void init() {
		// Old Gulpmother
		LILYCROWN = item("lilycrown", new Item.Properties().stacksTo(1).rarity(Rarity.RARE).equippable(EquipmentSlot.HEAD)
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(BossRegistry.id("armor.lilycrown"), GearStats.LILYCROWN_ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(BossRegistry.id("toughness.lilycrown"), GearStats.LILYCROWN_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.OXYGEN_BONUS, new AttributeModifier(BossRegistry.id("oxygen.lilycrown"), GearStats.LILYCROWN_OXYGEN, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.WATER_MOVEMENT_EFFICIENCY, new AttributeModifier(BossRegistry.id("water.lilycrown"), GearStats.LILYCROWN_WATER_MOVE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.JUMP_STRENGTH, new AttributeModifier(BossRegistry.id("jump.lilycrown"), GearStats.LILYCROWN_JUMP, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.build()));
		GULP_TONGUE = BossRegistry.item("gulp_tongue", FishingRodItem::new, new Item.Properties().durability(GearStats.GULP_TONGUE_DURABILITY).rarity(Rarity.RARE).useCooldown(GearStats.GULP_TONGUE_COOLDOWN_S));
		FROG_LEGS = food("frog_legs", GearStats.FROG_LEGS_NUTRITION, GearStats.FROG_LEGS_SATURATION);
		BUFO_GLAND = item("bufo_gland", new Item.Properties().food(new FoodProperties(1, 0.1F, true), Consumables.defaultFood()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NAUSEA, GearStats.BUFO_GLAND_NAUSEA_TICKS, 0))).build()));
		FROGSPAWN_GLOB = item("frogspawn_glob", new Item.Properties());
		// Strawgrim
		HARVEST_SCYTHE = item("harvest_scythe", new Item.Properties().durability(GearStats.SCYTHE_DURABILITY).rarity(Rarity.RARE).enchantable(14)
			.useCooldown(GearStats.SCYTHE_COOLDOWN_S).component(DataComponents.WEAPON, new Weapon(1))
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, GearStats.SCYTHE_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, GearStats.SCYTHE_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build()));
		SCARECROWS_GRIN = item("scarecrows_grin", new Item.Properties().stacksTo(1).rarity(Rarity.RARE).equippable(EquipmentSlot.HEAD)
			.attributes(armorAttrs("scarecrows_grin", GearStats.GRIN_ARMOR, GearStats.GRIN_TOUGHNESS, EquipmentSlotGroup.HEAD)));
		HARVEST_MOON_SEEDS = item("harvest_moon_seeds", new Item.Properties());
		HARVEST_LOAF = food("harvest_loaf", GearStats.HARVEST_LOAF_NUTRITION, GearStats.HARVEST_LOAF_SATURATION);
		HARVEST_MOON_BUD = item("harvest_moon_bud", new Item.Properties().rarity(Rarity.UNCOMMON));
		CROW_FEATHER = item("crow_feather", new Item.Properties());
		SEED_FIREBALL = item("seed_fireball", new Item.Properties());
		// Neferkhet
		ANKH_OF_NEFERKHET = item("ankh_of_neferkhet", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			.component(DataComponents.DEATH_PROTECTION, new DeathProtection(List.of(new ApplyStatusEffectsConsumeEffect(List.of(
				new MobEffectInstance(MobEffects.REGENERATION, GearStats.ANKH_REGEN_TICKS, GearStats.ANKH_REGEN_AMP),
				new MobEffectInstance(MobEffects.INVISIBILITY, GearStats.ANKH_UNDEAD_IGNORE_TICKS, 0)))))));
		SUNFIRE_KHOPESH = item("sunfire_khopesh", new Item.Properties().sword(ToolMaterial.DIAMOND, 3.0F, (float) GearStats.KHOPESH_SPEED)
			.enchantable(GearStats.KHOPESH_ENCHANTABLE).rarity(Rarity.RARE));
		ArmorMaterial nemes = new ArmorMaterial(25, Map.of(ArmorType.HELMET, (int) GearStats.NEMES_ARMOR), 25, SoundEvents.ARMOR_EQUIP_GOLD,
			(float) GearStats.NEMES_TOUGHNESS, 0.0F, ItemTags.REPAIRS_GOLD_ARMOR, EquipmentAssets.GOLD);
		PHARAOHS_NEMES = item("pharaohs_nemes", new Item.Properties().humanoidArmor(nemes, ArmorType.HELMET).rarity(Rarity.RARE));
		ROYAL_HONEY = item("royal_honey", new Item.Properties().stacksTo(16).food(new FoodProperties(GearStats.ROYAL_HONEY_NUTRITION, GearStats.ROYAL_HONEY_SATURATION, false), Consumables.HONEY_BOTTLE));
		LINEN_WRAP = item("linen_wrap", new Item.Properties());
		// Rimeback
		RIMEHEART_MAUL = BossRegistry.item("rimeheart_maul", MaceItem::new, new Item.Properties().rarity(Rarity.EPIC).durability(500)
			.component(DataComponents.TOOL, MaceItem.createToolProperties()).attributes(MaceItem.createAttributes()).enchantable(15)
			.component(DataComponents.WEAPON, new Weapon(1)));
		ArmorMaterial rime = new ArmorMaterial(13, Map.of(ArmorType.BOOTS, (int) GearStats.BOOTS_ARMOR), 15, SoundEvents.ARMOR_EQUIP_LEATHER,
			(float) GearStats.BOOTS_TOUGHNESS, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR, EquipmentAssets.LEATHER);
		RIMEWALKER_BOOTS = item("rimewalker_boots", new Item.Properties().humanoidArmor(rime, ArmorType.BOOTS).rarity(Rarity.RARE)
			.component(DataComponents.DYED_COLOR, new DyedItemColor(GearStats.BOOTS_DYE)));
		GLACIER_WATER = item("glacier_water", new Item.Properties().stacksTo(16).food(new FoodProperties(0, 0.0F, true), Consumables.defaultDrink()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, GearStats.GLACIER_WATER_FIRE_RES_TICKS, 0))).build()));
		FROST_SHARD = item("frost_shard", new Item.Properties());
		// Vorlagh
		UNDERTOW = BossRegistry.item("undertow", TridentItem::new, new Item.Properties().rarity(Rarity.EPIC).durability(GearStats.UNDERTOW_DURABILITY)
			.attributes(TridentItem.createAttributes()).component(DataComponents.TOOL, TridentItem.createToolProperties()).enchantable(1)
			.component(DataComponents.WEAPON, new Weapon(1)));
		KRAKEN_INK_BOMB = BossRegistry.item("kraken_ink_bomb", InkBombItem::new, new Item.Properties().stacksTo(16).useCooldown(GearStats.INK_BOMB_COOLDOWN_S));
		ABYSSAL_EYE = item("abyssal_eye", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		SIREN_CONCH = BossRegistry.item("siren_conch", SirenConchItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).useCooldown(3.0F));
		KRAKEN_CALAMARI = food("kraken_calamari", GearStats.CALAMARI_NUTRITION, GearStats.CALAMARI_SATURATION);
	}

	/** First kill: one of the boss's uniques guaranteed; every kill: each unique rolls the repeat chance. */
	public static void rollUniques(RandomSource random, Item[] uniques, boolean firstKill, List<ItemStack> out) {
		int guaranteed = firstKill ? random.nextInt(uniques.length) : -1;
		for (int i = 0; i < uniques.length; i++) {
			float chance = uniques[i] == ANKH_OF_NEFERKHET ? GearStats.ANKH_REPEAT_CHANCE : GearStats.UNIQUE_REPEAT_CHANCE;
			if (i == guaranteed || random.nextFloat() < chance) out.add(new ItemStack(uniques[i]));
		}
	}

	/** Kraken Ink Bomb: throw a blindness cloud. */
	public static class InkBombItem extends Item {
		public InkBombItem(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel sl) {
				InkGlob g = new InkGlob(OwbEntities.INK_GLOB, sl);
				g.setOwner(player);
				g.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
				g.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);
				sl.addFreshEntity(g);
				sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1.0F, 0.8F);
				stack.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** Siren Conch: blown on the Wreck Ring deck during a thunderstorm, it calls Vorlagh. */
	public static class SirenConchItem extends Item {
		public SirenConchItem(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel sl) {
				sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 2.0F, 0.6F);
				String problem = Summons.trySummonVorlagh(sl, player);
				if (problem != null) player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(problem));
				else stack.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
	}

	public static Identifier id(String path) {
		return BossRegistry.id(path);
	}
}
