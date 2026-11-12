package dev.elsewhere.hush;

import dev.elsewhere.greenhaze_hush.GHProjectile;
import dev.elsewhere.greenhaze_hush.Reg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;

/** Hush items: materials, Echo tools, Hushweave armor, utility items and the Hush boss drops. */
public final class HushItems {
	private HushItems() {}

	public static final TagKey<Item> ECHO_REPAIR = TagKey.create(Registries.ITEM, Reg.id("echo_tool_materials"));
	public static final TagKey<Item> HUSHWEAVE_REPAIR = TagKey.create(Registries.ITEM, Reg.id("repairs_hushweave_armor"));
	public static final ResourceKey<EquipmentAsset> HUSHWEAVE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("hushweave"));
	public static final ResourceKey<EquipmentAsset> GREAVES_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("hushstep"));
	public static final ResourceKey<EquipmentAsset> VESTMENT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("choir_vestment"));

	public static final ToolMaterial ECHO = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, GearStats.ECHO_DURABILITY,
		GearStats.ECHO_SPEED, GearStats.ECHO_DAMAGE_BONUS, GearStats.ECHO_ENCHANT, ECHO_REPAIR);
	public static final ArmorMaterial HUSHWEAVE = new ArmorMaterial(GearStats.HUSHWEAVE_DURABILITY,
		ArmorMaterials.makeDefense(GearStats.HUSHWEAVE_DEFENSE[0], GearStats.HUSHWEAVE_DEFENSE[1], GearStats.HUSHWEAVE_DEFENSE[2],
			GearStats.HUSHWEAVE_DEFENSE[3], 11),
		GearStats.HUSHWEAVE_ENCHANT, SoundEvents.ARMOR_EQUIP_LEATHER, GearStats.HUSHWEAVE_TOUGHNESS, 0F, HUSHWEAVE_REPAIR, HUSHWEAVE_ASSET);
	public static final ArmorMaterial HUSHSTEP = new ArmorMaterial(37, ArmorMaterials.makeDefense(3, GearStats.GREAVES_DEFENSE, 8, 3, 19),
		15, SoundEvents.ARMOR_EQUIP_NETHERITE, GearStats.GREAVES_TOUGHNESS, 0F, HUSHWEAVE_REPAIR, GREAVES_ASSET);
	public static final ArmorMaterial VESTMENT = new ArmorMaterial(37, ArmorMaterials.makeDefense(3, 6, GearStats.VESTMENT_DEFENSE, 3, 19),
		15, SoundEvents.ARMOR_EQUIP_NETHERITE, GearStats.VESTMENT_TOUGHNESS, 0F, ECHO_REPAIR, VESTMENT_ASSET);

	public static Holder<MobEffect> MUTED;
	public static Item ECHO_CRYSTAL, RESONANT_SHARD, HUSH_MEMBRANE, CRYSTAL_SHARD, SONAR_HORN, SCULK_CENSER, SILENCE_DRAUGHT,
		TUNING_FORK_OF_AURIS, CHOIR_VESTMENT, CANTORS_BELL_SHARD, RESONANT_TUNING, HUSHWEAVE_HOOD, HUSHCLOAK, HUSHSTEP_GREAVES,
		HUSHWEAVE_BOOTS, ECHO_SWORD, ECHO_PICKAXE, ECHO_AXE, ECHO_SHOVEL, ECHO_HOE, MUSIC_DISC_HUSH, ECHO_ALLOY, HUSHWEAVE_TEMPLATE;

	static void init() {
		MUTED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Reg.id("muted"),
			new MobEffect(MobEffectCategory.BENEFICIAL, 0x0D3B3E) {});

		ECHO_CRYSTAL = Reg.item("echo_crystal");
		RESONANT_SHARD = Reg.item("resonant_shard", Item::new, new Item.Properties().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
		HUSH_MEMBRANE = Reg.item("hush_membrane");
		CRYSTAL_SHARD = Reg.item("crystal_shard");
		SONAR_HORN = Reg.item("sonar_horn", SonarHornItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.useCooldown(GearStats.SONAR_COOLDOWN_S));
		SCULK_CENSER = Reg.item("sculk_censer", Item::new, new Item.Properties().durability(GearStats.CENSER_DURABILITY).rarity(Rarity.UNCOMMON));
		SILENCE_DRAUGHT = Reg.item("silence_draught", SilenceDraughtItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()).usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE));
		TUNING_FORK_OF_AURIS = Reg.item("tuning_fork_of_auris", TuningForkItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
		ECHO_ALLOY = Reg.item("echo_alloy", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
		HUSHWEAVE_TEMPLATE = Reg.item("hushweave_upgrade_smithing_template", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON),
			"hushweave_upgrade_smithing_template", 2));
		CANTORS_BELL_SHARD = Reg.item("cantors_bell_shard", Item::new, new Item.Properties().rarity(Rarity.EPIC));
		RESONANT_TUNING = Reg.item("resonant_tuning", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

		ECHO_SWORD = Reg.item("echo_sword", Item::new, new Item.Properties().sword(ECHO, 3.0F, -2.4F));
		ECHO_PICKAXE = Reg.item("echo_pickaxe", Item::new, new Item.Properties().pickaxe(ECHO, 1.0F, -2.8F));
		ECHO_AXE = Reg.item("echo_axe", Item::new, new Item.Properties().axe(ECHO, 5.0F, -3.0F));
		ECHO_SHOVEL = Reg.item("echo_shovel", Item::new, new Item.Properties().shovel(ECHO, 1.5F, -3.0F));
		ECHO_HOE = Reg.item("echo_hoe", Item::new, new Item.Properties().hoe(ECHO, -4.0F, 0.0F));

		// N1 (polish-endgame-sets): per-piece perks. Hood: Darkness immunity + the Resonance meter anywhere. Cloak: your noise x0.5.
		// Greaves: sneaking speed +0.3. Boots: walking and landing make no vibrations. All four: slow Mending (#elsewhere:slow_mending).
		HUSHWEAVE_HOOD = Reg.item("hushweave_hood", Item::new, lore(new Item.Properties().humanoidArmor(HUSHWEAVE, ArmorType.HELMET), "hushweave_hood", 2));
		HUSHCLOAK = Reg.item("hushcloak", Item::new, lore(new Item.Properties().humanoidArmor(HUSHWEAVE, ArmorType.CHESTPLATE), "hushcloak", 2));
		HUSHSTEP_GREAVES = Reg.item("hushstep_greaves", Item::new, lore(new Item.Properties().humanoidArmor(HUSHSTEP, ArmorType.LEGGINGS)
			.attributes(HUSHSTEP.createAttributes(ArmorType.LEGGINGS).withModifierAdded(Attributes.SNEAKING_SPEED,
				new AttributeModifier(Reg.id("hushstep_sneak"), GearStats.GREAVES_SNEAK_SPEED, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.LEGS))
			.rarity(Rarity.EPIC).fireResistant(), "hushstep_greaves", 2));
		HUSHWEAVE_BOOTS = Reg.item("hushweave_boots", Item::new, lore(new Item.Properties().humanoidArmor(HUSHWEAVE, ArmorType.BOOTS), "hushweave_boots", 2));
		CHOIR_VESTMENT = Reg.item("choir_vestment", Item::new, new Item.Properties().humanoidArmor(VESTMENT, ArmorType.CHESTPLATE)
			.rarity(Rarity.EPIC).fireResistant());
		MUSIC_DISC_HUSH = Reg.item("music_disc_hush", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, Reg.id("hush"))));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				tuningBar(p);
				if (server.getTickCount() % 20 == 0) stripBannedEnchants(p);
				hoodPerk(p);
				echoDepth(p);
			}
		});
		// Choir Vestment: when its wearer is hit, allies within 8 blocks (not the wearer) get 2 HP of Absorption for 6 s; 12 s cooldown
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0) vestmentPulse(p);
		});
		// Unheard (the full Hushweave set): tooltip + marker; the behaviour lives in noiseFactor / HushNoise / the visibility mixin.
		UNHEARD = dev.elsewhere.stub.greenhaze_hush.SetBonus.register(new dev.elsewhere.stub.greenhaze_hush.SetBonus("unheard",
			java.util.List.of(HUSHWEAVE_HOOD, HUSHCLOAK, HUSHSTEP_GREAVES, HUSHWEAVE_BOOTS)) {
			@Override public void apply(ServerPlayer player) {}
			@Override public void remove(ServerPlayer player) {}
			@Override public java.util.List<Component> tooltip() {
				return java.util.List.of(Component.translatable("set.elsewhere.unheard.1"), Component.translatable("set.elsewhere.unheard.2"));
			}
		});
	}

	public static dev.elsewhere.stub.greenhaze_hush.SetBonus UNHEARD;

	private static final Map<UUID, Long> VESTMENT_READY = new HashMap<>();

	/** Returns how many allies were shielded (0 when not worn, on cooldown or alone). */
	public static int vestmentPulse(ServerPlayer p) {
		return vestmentPulse(p, p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(GearStats.VESTMENT_RADIUS), e -> e != p && e.isAlive()));
	}

	public static int vestmentPulse(ServerPlayer p, java.util.Collection<? extends LivingEntity> candidates) {
		if (!p.getItemBySlot(EquipmentSlot.CHEST).is(CHOIR_VESTMENT)) return 0;
		long now = p.level().getGameTime();
		Long ready = VESTMENT_READY.get(p.getUUID());
		if (ready != null && now < ready) return 0;
		int n = 0;
		for (LivingEntity e : candidates) {
			if (e == p) continue;
			boolean ally = (e instanceof Player pl && !pl.isSpectator()) || (e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame());
			if (!ally || e.distanceToSqr(p) > GearStats.VESTMENT_RADIUS * GearStats.VESTMENT_RADIUS) continue;
			e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, GearStats.VESTMENT_TICKS, 0));
			e.setAbsorptionAmount(Math.min(e.getAbsorptionAmount(), GearStats.VESTMENT_ABSORB));
			n++;
		}
		if (n > 0) {
			VESTMENT_READY.put(p.getUUID(), now + GearStats.VESTMENT_COOLDOWN);
			((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 12, 1.5, 0.5, 1.5, 0.02);
		}
		return n;
	}

	/** Hushweave can't take Thorns or Swift Sneak (N1 con): they are stripped from every Hushweave piece the player carries. */
	public static int stripBannedEnchants(ServerPlayer p) {
		int removed = 0;
		for (int i = 0; i < p.getInventory().getContainerSize() + 4; i++) {
			ItemStack s = i < p.getInventory().getContainerSize() ? p.getInventory().getItem(i) : p.getItemBySlot(new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}[i - p.getInventory().getContainerSize()]);
			if (!(s.is(HUSHWEAVE_HOOD) || s.is(HUSHCLOAK) || s.is(HUSHSTEP_GREAVES) || s.is(HUSHWEAVE_BOOTS))) continue;
			int[] c = {0};
			net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(s, m -> {
				c[0] += m.keySet().size();
				m.removeIf(h -> h.is(net.minecraft.world.item.enchantment.Enchantments.THORNS) || h.is(net.minecraft.world.item.enchantment.Enchantments.SWIFT_SNEAK));
				c[0] -= m.keySet().size();
			});
			removed += c[0];
		}
		if (removed > 0) p.sendOverlayMessage(Component.literal("Hushweave sheds Thorns and Swift Sneak.").withStyle(ChatFormatting.GRAY));
		return removed;
	}

	public static void clearVestment() { VESTMENT_READY.clear(); }

	/** True while the full Hushweave set (hood, Hushcloak, Hushstep Greaves, boots) is worn: the Unheard bonus. */
	public static boolean unheard(LivingEntity e) {
		return fullHushweave(e);
	}

	private static final Identifier ECHO_DEPTH_ID = Reg.id("echo_depth_speed");

	/** Hood: Darkness immunity (Auris's permanent Darkness, the Silence Draught's last 10 s, wardens). */
	public static void hoodPerk(ServerPlayer p) {
		if (p.getItemBySlot(EquipmentSlot.HEAD).is(HUSHWEAVE_HOOD) && p.hasEffect(MobEffects.DARKNESS)) p.removeEffect(MobEffects.DARKNESS);
	}

	/** Echo Pickaxe: speed 9 in the Hush and below Y 0, 7 elsewhere (the tool is 7; +2 while deep). */
	public static void echoDepth(ServerPlayer p) {
		var attr = p.getAttribute(Attributes.MINING_EFFICIENCY);
		if (attr == null) return;
		boolean deep = p.getMainHandItem().is(ECHO_PICKAXE) && (Hush.isHush(p.level()) || p.getY() < 0);
		boolean has = attr.getModifier(ECHO_DEPTH_ID) != null;
		if (deep && !has) attr.addTransientModifier(new AttributeModifier(ECHO_DEPTH_ID, GearStats.ECHO_DEEP_BONUS, AttributeModifier.Operation.ADD_VALUE));
		else if (!deep && has) attr.removeModifier(ECHO_DEPTH_ID);
	}

	static net.minecraft.world.item.component.ItemLore loreOf(String key, int lines) {
		java.util.List<Component> l = new java.util.ArrayList<>();
		for (int i = 1; i <= lines; i++) {
			ChatFormatting c = i == 1 ? ChatFormatting.GRAY : ChatFormatting.BLUE;
			l.add(Component.translatable("item.elsewhere." + key + ".lore" + i).withStyle(st -> st.withColor(c).withItalic(false)));
		}
		return new net.minecraft.world.item.component.ItemLore(l);
	}

	public static Item.Properties lore(Item.Properties p, String key, int lines) {
		return p.component(DataComponents.LORE, loreOf(key, lines));
	}

	public static boolean isEchoTool(ItemStack s) {
		return s.is(ECHO_PICKAXE) || s.is(ECHO_AXE) || s.is(ECHO_SHOVEL) || s.is(ECHO_HOE) || s.is(ECHO_SWORD);
	}

	public static boolean fullHushweave(LivingEntity e) {
		return e.getItemBySlot(EquipmentSlot.HEAD).is(HUSHWEAVE_HOOD) && e.getItemBySlot(EquipmentSlot.CHEST).is(HUSHCLOAK)
			&& e.getItemBySlot(EquipmentSlot.LEGS).is(HUSHSTEP_GREAVES) && e.getItemBySlot(EquipmentSlot.FEET).is(HUSHWEAVE_BOOTS);
	}

	/** Noise multiplier from gear and effects (1 = normal, 0 = silent). */
	public static double noiseFactor(LivingEntity e) {
		if (e.hasEffect(MUTED)) return 0;
		// Unheard con: sprinting vibrations travel 50% farther
		if (e.getItemBySlot(EquipmentSlot.CHEST).is(CHOIR_VESTMENT)) return GearStats.VESTMENT_NOISE; // Vestment con: louder wearer
		if (fullHushweave(e)) return e.isSprinting() ? GearStats.HUSHWEAVE_SET_NOISE * 1.5 : GearStats.HUSHWEAVE_SET_NOISE;
		if (e.getItemBySlot(EquipmentSlot.CHEST).is(HUSHCLOAK)) return GearStats.HUSHCLOAK_NOISE;
		return 1;
	}

	// ------------------------------------------------------------------------------------------------ Resonant Tuning boss bar

	private static final Map<UUID, ServerBossEvent> TUNING_BARS = new HashMap<>();

	private static void tuningBar(ServerPlayer p) {
		boolean holding = p.getOffhandItem().is(RESONANT_TUNING);
		ServerBossEvent bar = TUNING_BARS.get(p.getUUID());
		if (holding) {
			if (bar == null) {
				bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Resonance"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
				bar.addPlayer(p);
				TUNING_BARS.put(p.getUUID(), bar);
			}
			float r = Resonance.get(p);
			bar.setProgress(Math.min(1F, r / 100F));
			bar.setName(Component.literal("Resonance " + Math.round(r) + "%").withStyle(r >= 50 ? ChatFormatting.RED : ChatFormatting.AQUA));
		} else if (bar != null) {
			bar.removeAllPlayers();
			TUNING_BARS.remove(p.getUUID());
		}
	}

	// ------------------------------------------------------------------------------------------------ item classes

	/** All mobs within 48 blocks glow for 8 s, at a cost of +40% Resonance. 60 s cooldown. */
	public static class SonarHornItem extends Item {
		public SonarHornItem(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
				sl.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3F, 1.2F);
				sl.playSound(null, sp.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 3F, 0.5F);
				double r = GearStats.SONAR_RADIUS;
				for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(r), e -> e != sp)) {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, GearStats.SONAR_GLOW_TICKS, 0));
				}
				Resonance.addRaw(sp, GearStats.SONAR_RESONANCE);
				HushNoise.post(sl, sp.position(), sp, 4);
			}
			return InteractionResult.SUCCESS;
		}
	}

	public static class SilenceDraughtItem extends Item {
		public SilenceDraughtItem(Properties p) { super(p); }

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, GearStats.DRAUGHT_INVIS, 0));
			entity.addEffect(new MobEffectInstance(MUTED, GearStats.DRAUGHT_MUTED, 0));
			entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, GearStats.DRAUGHT_DARKNESS, 0));
			return super.finishUsingItem(stack, level, entity);
		}
	}

	/** Thrown decoy: a vibration source that distracts Wardens, Echo Stalkers, sensors and Auris. */
	public static class TuningForkItem extends Item {
		public TuningForkItem(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (player instanceof ServerPlayer sp && !dev.elsewhere.stub.greenhaze_hush.GlimmerStub.trySpend(sp, GearStats.FORK_GLIMMER)) return InteractionResult.FAIL;
			player.getCooldowns().addCooldown(stack, GearStats.FORK_DELAY_TICKS);
			if (level instanceof ServerLevel sl) {
				GHProjectile fork = new GHProjectile(HushEntities.TUNING_FORK, sl, player, stack.copyWithCount(1), 0F);
				fork.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.3F, 0.5F);
				sl.addFreshEntity(fork);
			}
			stack.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}

	public static void forgetPlayer(UUID id) {
		ServerBossEvent bar = TUNING_BARS.remove(id);
		if (bar != null) bar.removeAllPlayers();
	}

	public static void clearAll() {
		for (ServerBossEvent b : TUNING_BARS.values()) b.removeAllPlayers();
		TUNING_BARS.clear();
	}
}
