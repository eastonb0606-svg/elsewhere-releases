package dev.elsewhere.greenhaze;

import dev.elsewhere.greenhaze_hush.GHProjectile;
import dev.elsewhere.greenhaze_hush.Reg;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Greenhaze items: materials, food, utility, the Trichome tool set, the Frond armor set and the Greenhaze boss drops. */
public final class GreenhazeItems {
	private GreenhazeItems() {}

	public static final TagKey<Item> TRICHOME_REPAIR = TagKey.create(Registries.ITEM, Reg.id("trichome_tool_materials"));
	public static final TagKey<Item> FROND_REPAIR = TagKey.create(Registries.ITEM, Reg.id("repairs_frond_armor"));
	public static final ResourceKey<EquipmentAsset> FROND_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("frond"));
	public static final ResourceKey<EquipmentAsset> VISOR_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("hotbox_visor"));

	public static final ToolMaterial TRICHOME = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, GearStats.TRICHOME_DURABILITY,
		GearStats.TRICHOME_SPEED, GearStats.TRICHOME_DAMAGE_BONUS, GearStats.TRICHOME_ENCHANT, TRICHOME_REPAIR);
	public static final ArmorMaterial FROND = new ArmorMaterial(GearStats.FROND_DURABILITY,
		net.minecraft.world.item.equipment.ArmorMaterials.makeDefense(GearStats.FROND_DEFENSE[0], GearStats.FROND_DEFENSE[1],
			GearStats.FROND_DEFENSE[2], GearStats.FROND_DEFENSE[3], 11),
		GearStats.FROND_ENCHANT, SoundEvents.ARMOR_EQUIP_LEATHER, GearStats.FROND_TOUGHNESS, 0F, FROND_REPAIR, FROND_ASSET);
	public static final ArmorMaterial VISOR = new ArmorMaterial(33,
		net.minecraft.world.item.equipment.ArmorMaterials.makeDefense(3, 6, 8, GearStats.VISOR_DEFENSE, 11),
		10, SoundEvents.ARMOR_EQUIP_DIAMOND, GearStats.VISOR_TOUGHNESS, 0F, TRICHOME_REPAIR, VISOR_ASSET);

	public static Item HAZE_RESIN, HAZEBORN_BUD, LUCID_WATER, MUNCHIE_BACON, MUNCHFRUIT, HOTBOX_LANTERN, TRICHOME_QUARTZ, TOAD_SLIME,
		SHADE_WISP, SMOKE_RING, EMBER, SPIT_SEED,
		TRICHOME_SWORD, TRICHOME_PICKAXE, TRICHOME_AXE, TRICHOME_SHOVEL, TRICHOME_HOE,
		FROND_HOOD, FROND_CLOAK, FROND_LEGGINGS, FROND_SANDALS,
		GROVE_FRUIT_PLATTER, EVERFLOW_BONG, HOTBOX_VISOR, CLOUD_JAR, ENDLESS_SNACK_POUCH, COUCHLOCK_CLUB, GRUMBLEGUT_TUSK, MUSIC_DISC_FOUR_TWENTY;

	static void init() {
		HAZE_RESIN = Reg.item("haze_resin");
		HAZEBORN_BUD = Reg.item("hazeborn_bud", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
		TRICHOME_QUARTZ = Reg.item("trichome_quartz");
		TOAD_SLIME = Reg.item("toad_slime");
		LUCID_WATER = Reg.item("lucid_water", LucidWaterItem::new, new Item.Properties().stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()).usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE));
		MUNCHIE_BACON = Reg.item("munchie_bacon", MunchieFoodItem::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(GearStats.BACON_FOOD).saturationModifier(GearStats.BACON_SATURATION_MOD).build()));
		MUNCHFRUIT = Reg.item("munchfruit", p -> new net.minecraft.world.item.BlockItem(GreenhazeBlocks.MUNCHFRUIT_BUSH, p), new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(GearStats.MUNCHFRUIT_FOOD).saturationModifier(GearStats.MUNCHFRUIT_SATURATION_MOD).build())
			.useItemDescriptionPrefix());
		GROVE_FRUIT_PLATTER = Reg.item("grove_fruit_platter", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
			.food(new FoodProperties.Builder().nutrition(10).saturationModifier(0.8F).build()));
		SHADE_WISP = Reg.item("shade_wisp", ShadeWispItem::new, new Item.Properties().stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()));
		HOTBOX_LANTERN = Reg.item("hotbox_lantern", HotboxLanternItem::new, new Item.Properties().stacksTo(1));
		// projectile visuals (not in the creative tab's main list but registered so the projectiles have textures)
		SMOKE_RING = Reg.item("smoke_ring");
		EMBER = Reg.item("ember_flick");
		SPIT_SEED = Reg.item("spit_seed");

		TRICHOME_SWORD = Reg.item("trichome_sword", TrichomeToolItem::new, new Item.Properties().sword(TRICHOME, 3.0F, -2.4F));
		TRICHOME_PICKAXE = Reg.item("trichome_pickaxe", TrichomeToolItem::new, new Item.Properties().pickaxe(TRICHOME, 1.0F, -2.8F));
		TRICHOME_AXE = Reg.item("trichome_axe", TrichomeToolItem::new, new Item.Properties().axe(TRICHOME, 5.0F, -3.0F));
		TRICHOME_SHOVEL = Reg.item("trichome_shovel", TrichomeToolItem::new, new Item.Properties().shovel(TRICHOME, 1.5F, -3.0F));
		TRICHOME_HOE = Reg.item("trichome_hoe", TrichomeToolItem::new, new Item.Properties().hoe(TRICHOME, -3.0F, 0.0F));

		FROND_HOOD = Reg.item("frond_hood", Item::new, dev.elsewhere.hush.HushItems.lore(new Item.Properties().humanoidArmor(FROND, ArmorType.HELMET), "frond_piece", 2));
		FROND_CLOAK = Reg.item("frond_cloak", Item::new, dev.elsewhere.hush.HushItems.lore(new Item.Properties().humanoidArmor(FROND, ArmorType.CHESTPLATE), "frond_piece", 2));
		FROND_LEGGINGS = Reg.item("frond_leggings", Item::new, dev.elsewhere.hush.HushItems.lore(new Item.Properties().humanoidArmor(FROND, ArmorType.LEGGINGS), "frond_piece", 2));
		FROND_SANDALS = Reg.item("frond_sandals", Item::new, dev.elsewhere.hush.HushItems.lore(new Item.Properties().humanoidArmor(FROND, ArmorType.BOOTS), "frond_piece", 2));
		// Full-set bonus (BALANCE.md): immune to contact highs you didn't choose (see immuneToContactHigh). Marker + tooltip through SetBonus.
		UNDERGROWTH = dev.elsewhere.stub.greenhaze_hush.SetBonus.register(new dev.elsewhere.stub.greenhaze_hush.SetBonus("undergrowth",
			java.util.List.of(FROND_HOOD, FROND_CLOAK, FROND_LEGGINGS, FROND_SANDALS)) {
			@Override public void apply(ServerPlayer player) {}
			@Override public void remove(ServerPlayer player) {}
			@Override public java.util.List<net.minecraft.network.chat.Component> tooltip() {
				return java.util.List.of(net.minecraft.network.chat.Component.translatable("set.elsewhere.undergrowth.1"));
			}
		});

		// boss drops
		EVERFLOW_BONG = Reg.item("everflow_bong", EverflowBongItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		HOTBOX_VISOR = Reg.item("hotbox_visor", Item::new, new Item.Properties().humanoidArmor(VISOR, ArmorType.HELMET).rarity(Rarity.RARE));
		CLOUD_JAR = Reg.item("cloud_jar", CloudJarItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
		ENDLESS_SNACK_POUCH = Reg.item("endless_snack_pouch", SnackPouchItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
			.useCooldown(GearStats.POUCH_COOLDOWN_S));
		COUCHLOCK_CLUB = Reg.item("couchlock_club", CouchlockClubItem::new, new Item.Properties().sword(ToolMaterial.DIAMOND,
			GearStats.CLUB_DAMAGE - 1F - ToolMaterial.DIAMOND.attackDamageBonus(), GearStats.CLUB_SPEED - 4F).rarity(Rarity.RARE));
		GRUMBLEGUT_TUSK = Reg.item("grumblegut_tusk", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
		MUSIC_DISC_FOUR_TWENTY = Reg.item("music_disc_four_twenty", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, Reg.id("four_twenty"))));
	}

	/** Frond camouflage: mobs further than their reduced follow range lose track of a player standing in ferns/leaves/grass. */
	static void frondCamo(net.minecraft.server.MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int pieces = 0;
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack s = p.getItemBySlot(slot);
				if (s.is(FROND_HOOD) || s.is(FROND_CLOAK) || s.is(FROND_LEGGINGS) || s.is(FROND_SANDALS)) pieces++;
			}
			if (pieces == 0) continue;
			var at = p.level().getBlockState(p.blockPosition());
			boolean cover = at.is(net.minecraft.world.level.block.Blocks.FERN) || at.is(net.minecraft.world.level.block.Blocks.LARGE_FERN)
				|| at.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS) || at.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)
				|| at.is(GreenhazeBlocks.GIANT_FERN) || at.is(net.minecraft.tags.BlockTags.LEAVES);
			if (!cover) continue;
			double factor = 1 - GearStats.FROND_CAMO_PER_PIECE * pieces;
			for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m.getTarget() == p)) {
				double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * factor;
				if (mob.distanceToSqr(p) > range * range) mob.setTarget(null);
			}
		}
	}

	public static boolean wearsFullFrond(LivingEntity e) {
		return e.getItemBySlot(EquipmentSlot.HEAD).is(FROND_HOOD) && e.getItemBySlot(EquipmentSlot.CHEST).is(FROND_CLOAK)
			&& e.getItemBySlot(EquipmentSlot.LEGS).is(FROND_LEGGINGS) && e.getItemBySlot(EquipmentSlot.FEET).is(FROND_SANDALS);
	}

	/** Contact highs you didn't choose: blocked by the full Frond set or the Hotbox Visor. */
	public static boolean immuneToContactHigh(LivingEntity e) {
		return wearsFullFrond(e) || e.getItemBySlot(EquipmentSlot.HEAD).is(HOTBOX_VISOR);
	}

	// ------------------------------------------------------------------------------------------------ item classes

	public static dev.elsewhere.stub.greenhaze_hush.SetBonus UNDERGROWTH;

	private static final java.util.Map<java.util.UUID, Long> SLOWED = new java.util.HashMap<>();
	public static final int SLOW_TARGET_COOLDOWN = 80; // 4 s per target (BALANCE.md)
	private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> BOSSES = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ENTITY_TYPE, Reg.id("bosses"));
	private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> C_BOSSES = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses"));

	public static boolean isBoss(LivingEntity e) {
		return e instanceof dev.elsewhere.stub.greenhaze_hush.BossEntity || e.is(BOSSES) || e.is(C_BOSSES)
			|| e.getType() == net.minecraft.world.entity.EntityTypes.WITHER || e.getType() == net.minecraft.world.entity.EntityTypes.ENDER_DRAGON;
	}

	/** Slowness on hit: never on bosses, and a target that was slowed in the last 4 s is not slowed again (no permanent lock). Returns whether it applied. */
	static boolean slowTarget(LivingEntity target, int ticks, int amp) {
		if (isBoss(target)) return false;
		long now = target.level().getGameTime();
		Long until = SLOWED.get(target.getUUID());
		if (until != null && now < until) return false;
		if (SLOWED.size() > 512) SLOWED.values().removeIf(t -> t <= now);
		SLOWED.put(target.getUUID(), now + SLOW_TARGET_COOLDOWN);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amp));
		return true;
	}

	/** Test hook: the same rule for any caller. */
	public static boolean slowForTest(LivingEntity target, int ticks, int amp) { return slowTarget(target, ticks, amp); }

	public static void clearSlowed() { SLOWED.clear(); }

	public static class TrichomeToolItem extends Item {
		public TrichomeToolItem(Properties p) { super(p); }

		@Override
		public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
			slowTarget(mob, GearStats.TRICHOME_SLOW_TICKS, GearStats.TRICHOME_SLOW_AMPLIFIER);
		}
	}

	public static class LucidWaterItem extends Item {
		public LucidWaterItem(Properties p) { super(p); }

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (entity instanceof ServerPlayer p) {
				Haze.add(p, GearStats.LUCID_WATER_HAZE, true);
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, GearStats.LUCID_WATER_NIGHT_VISION, 0));
				p.removeEffect(MobEffects.NAUSEA);
			}
			return super.finishUsingItem(stack, level, entity);
		}
	}

	/** Munchie Bacon cures the munchies (Hunger effect, and Green Leaf's munchies through the bridge). */
	public static class MunchieFoodItem extends Item {
		public MunchieFoodItem(Properties p) { super(p); }

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			entity.removeEffect(MobEffects.HUNGER);
			return super.finishUsingItem(stack, level, entity);
		}
	}

	/** Shade Wisp: a cure that clears paranoia (and Darkness/Nausea here). */
	public static class ShadeWispItem extends Item {
		public ShadeWispItem(Properties p) { super(p); }

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			entity.removeEffect(MobEffects.NAUSEA);
			entity.removeEffect(MobEffects.DARKNESS);
			if (entity instanceof ServerPlayer p) Haze.add(p, -10, true);
			return super.finishUsingItem(stack, level, entity);
		}
	}

	/** Held: a 3-block smoke bubble. Everyone inside gets a contact high; mobs outside lose track of you. */
	public static class HotboxLanternItem extends Item {
		public HotboxLanternItem(Properties p) { super(p); }

		@Override
		public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
			if (!(owner instanceof ServerPlayer p) || (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND)) return;
			if (level.getGameTime() % 6 == 0) { // P13: every 6th tick, was 4th (cosy smoke lingers 4-6 s)
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 3, 1.2, 0.8, 1.2, 0.005);
			}
			if (level.getGameTime() % 20 != 0) return;
			double r = GearStats.LANTERN_RADIUS;
			for (ServerPlayer other : level.players()) {
				if (other.distanceToSqr(p) <= r * r && !immuneToContactHigh(other)) Haze.add(other, 0.5, false);
			}
			for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
				if (mob.getTarget() == p && mob.distanceToSqr(p) > r * r) mob.setTarget(null);
			}
		}
	}

	/** A Green Leaf bong that needs no water and never breaks. Each rip burns one bud from your inventory. */
	public static class EverflowBongItem extends Item {
		public EverflowBongItem(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
			double factor = 0;
			String strain = null; // the rip is in the packed bud's strain (Hazeborn is a hybrid)
			for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
				ItemStack s = sp.getInventory().getItem(i);
				if (s.is(HAZEBORN_BUD)) { factor = GearStats.BONG_HAZEBORN_FACTOR; strain = "hybrid"; s.shrink(1); break; }
				if (GreenLeafBridge.isBud(s)) { factor = 1; strain = GreenLeafBridge.budStrain(s); s.shrink(1); break; }
			}
			if (factor == 0) {
				sp.sendOverlayMessage(Component.literal("Nothing to pack: you need a bud.").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			ServerLevel sl = sp.level();
			sl.playSound(null, sp.blockPosition(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1F, 0.7F);
			sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, sp.getX(), sp.getEyeY(), sp.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
			if (!GreenLeafBridge.usable() || !GreenLeafBridge.dose(sp, GearStats.BONG_RIP_HIGH * factor, strain)) Haze.add(sp, GearStats.BONG_RIP_HIGH * factor, true);
			sp.getCooldowns().addCooldown(player.getItemInHand(hand), GearStats.BONG_COOLDOWN);
			GreenhazeTrips.onGreenLeafStyleUse(sp);
			return InteractionResult.SUCCESS;
		}
	}

	public static class CloudJarItem extends Item {
		public CloudJarItem(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (player instanceof ServerPlayer sp && !dev.elsewhere.stub.greenhaze_hush.GlimmerStub.trySpend(sp, GearStats.CLOUD_JAR_GLIMMER)) return InteractionResult.FAIL;
			player.getCooldowns().addCooldown(stack, GearStats.CLOUD_JAR_DELAY_TICKS);
			if (level instanceof ServerLevel sl) {
				GHProjectile jar = new GHProjectile(GreenhazeEntities.CLOUD_JAR, sl, player, stack.copyWithCount(1), 0F);
				jar.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.2F, 1F);
				sl.addFreshEntity(jar);
				sl.playSound(null, player.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 0.8F);
			}
			stack.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}

	public static class SnackPouchItem extends Item {
		public SnackPouchItem(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!player.getFoodData().needsFood() && !player.hasEffect(MobEffects.HUNGER)) return InteractionResult.PASS;
			player.getFoodData().eat(GearStats.POUCH_FOOD, GearStats.POUCH_SATURATION);
			player.removeEffect(MobEffects.HUNGER);
			level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1F, 1.1F);
			return InteractionResult.SUCCESS;
		}
	}

	public static class CouchlockClubItem extends Item {
		public CouchlockClubItem(Properties p) { super(p); }

		@Override
		public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
			slowTarget(mob, GearStats.CLUB_SLOW_TICKS, GearStats.CLUB_SLOW_AMPLIFIER);
			if (attacker instanceof ServerPlayer p && p.isShiftKeyDown() && !p.getCooldowns().isOnCooldown(stack)) {
				ServerLevel sl = p.level();
				double r = GearStats.CLUB_SHOCKWAVE_RADIUS;
				for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(mob.blockPosition()).inflate(r))) {
					if (e == p || e.distanceToSqr(mob) > r * r) continue;
					e.hurtServer(sl, sl.damageSources().playerAttack(p), GearStats.CLUB_SHOCKWAVE_DAMAGE);
					dev.elsewhere.greenhaze_hush.BossUtil.shove(e, 0.6, p.getX() - e.getX(), p.getZ() - e.getZ());
				}
				sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, mob.getX(), mob.getY() + 0.2, mob.getZ(), 20, r / 2, 0.1, r / 2, 0.02);
				sl.playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1F, 0.8F);
				p.getCooldowns().addCooldown(stack, GearStats.CLUB_SHOCKWAVE_COOLDOWN);
			}
		}
	}

	public static List<Component> tooltip(Item item) {
		return List.of();
	}
}
