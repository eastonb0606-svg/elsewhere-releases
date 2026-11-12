package dev.elsewhere.stub.nether_end_bosses;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the stand-ins for things the core owns: {@code elsewhere:arena_seal}, the boss damage types (data files),
 * the three signature-gear pieces this module's tiers drop (I, II, VI) and their abilities. Dropped at merge.
 */
public final class StubFramework {
	public static final Logger LOGGER = LoggerFactory.getLogger("Elsewhere nether_end_bosses");
	public static final String NS = "elsewhere";

	public static final ResourceKey<DamageType> BOSS_MELEE = damageType("boss_melee");
	public static final ResourceKey<DamageType> BOSS_PROJECTILE = damageType("boss_projectile");
	public static final ResourceKey<DamageType> BOSS_BLAST = damageType("boss_blast");
	public static final ResourceKey<DamageType> BOSS_FIRE = damageType("boss_fire");
	public static final ResourceKey<DamageType> BOSS_MAGIC = damageType("boss_magic");
	public static final ResourceKey<DamageType> VOID_RETURN = damageType("void_return");

	public static final Identifier FIGHT_GRAVITY = id("fight_gravity");

	// Signature gear numbers (BOSSES.md "Signature gear"); the core owns the final versions.
	public static final float CLEAVER_BOSS_BONUS = overhaul3("rebalance_bossdrops") ? (coreCleaver() ? 0.0F : 0.20F) : 0.10F; // BALANCE.md: 7 dmg, +10%; 3.0: +20%, applied once (core)
	public static final int AEGIS_DURABILITY = 900;
	public static final int AEGIS_COOLDOWN_TICKS = 600;
	public static final float AEGIS_REFLECT = 0.25F;
	public static final double HALO_ARMOR = 3, HALO_TOUGHNESS = 3;
	public static final int HALO_RESCUE_COOLDOWN_TICKS = 12000;
	public static final float HALO_RESCUE_DAMAGE = 4.0F;

	public static Block ARENA_SEAL;
	public static Item CHALLENGERS_CLEAVER;
	public static Item AEGIS_OF_THE_LADDER;
	public static Item HALO_OF_THE_OUTER_END;

	private static boolean done;

	private StubFramework() {}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	private static ResourceKey<DamageType> damageType(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, id(name));
	}

	public static void init() {
		if (done) return;
		done = true;
		ARENA_SEAL = block("arena_seal", ArenaSealBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().sound(SoundType.GLASS).lightLevel(s -> 6)
			.isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
			.isViewBlocking((s, l, p, box) -> false).pushReaction(PushReaction.IMMOVEABLE));
		CHALLENGERS_CLEAVER = item("challengers_cleaver", Item::new, new Item.Properties().sword(ToolMaterial.DIAMOND, 3.0F, -2.4F).rarity(Rarity.RARE));
		AEGIS_OF_THE_LADDER = item("aegis_of_the_ladder", ShieldItem::new, new Item.Properties()
			.durability(AEGIS_DURABILITY).rarity(Rarity.RARE)
			.component(DataComponents.BANNER_PATTERNS, net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY)
			.repairable(Items.IRON_INGOT)
			.equippableUnswappable(EquipmentSlot.OFFHAND)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(0.5F, 1.6F,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK));
		HALO_OF_THE_OUTER_END = item("halo_of_the_outer_end", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(600)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
				.setAsset(net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, id("halo_of_the_outer_end"))).build())
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id("halo_armor"), HALO_ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id("halo_toughness"), HALO_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.build()));

		// Aegis of the Ladder: fully blocks one telegraphed big hit (over 25% max HP) every 30 s, reflecting 25% of it.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !Guards.isBossSource(source)) return true;
			if (amount < player.getMaxHealth() * 0.25F) return true;
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = player.getItemInHand(hand);
				if (stack.is(AEGIS_OF_THE_LADDER) && !player.getCooldowns().isOnCooldown(stack)) {
					player.getCooldowns().addCooldown(stack, AEGIS_COOLDOWN_TICKS);
					if (source.getEntity() instanceof LivingEntity attacker && player.level() instanceof ServerLevel level) {
						attacker.hurtServer(level, player.damageSources().thorns(player), amount * AEGIS_REFLECT);
					}
					player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 0.7F);
					return false;
				}
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(StubFramework::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Halo of the Outer End: Levitation immunity
			if (player.getItemBySlot(EquipmentSlot.HEAD).is(HALO_OF_THE_OUTER_END) && player.hasEffect(MobEffects.LEVITATION)) {
				player.removeEffect(MobEffects.LEVITATION);
			}
			if (server.getTickCount() % 20 == 0) {
				for (SetBonus bonus : SetBonus.REGISTERED) if (bonus.isWorn(player)) bonus.tick(player);
			}
		}
	}

	/** Challenger's Cleaver: +15% damage to bosses (applied before the overpower guard). */
	public static float cleaverBonus(Player attacker, float damage) {
		return attacker != null && attacker.getMainHandItem().is(CHALLENGERS_CLEAVER) ? damage * (1 + CLEAVER_BOSS_BONUS) : damage;
	}

	public static ItemStack signatureFor(int tier) {
		return switch (tier) {
			case 1 -> new ItemStack(CHALLENGERS_CLEAVER);
			case 2 -> new ItemStack(AEGIS_OF_THE_LADDER);
			case 6 -> new ItemStack(HALO_OF_THE_OUTER_END);
			default -> ItemStack.EMPTY;
		};
	}

	/** Registers a shared block unless another module (or the core) already did; then reuses that one. */
	public static Block block(String name, Function<BlockBehaviour.Properties, ? extends Block> factory, BlockBehaviour.Properties props) {
		Identifier id = id(name);
		if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.getValue(id);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	/** Registers a shared item unless another module (or the core) already did; then reuses that one. */
	public static Item item(String name, Function<Item.Properties, ? extends Item> factory, Item.Properties props) {
		Identifier id = id(name);
		if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	/** 3.0 kill switch {@code rebalance_bossdrops} (GEAR.md A4): -Delsewhere.overhaul3.rebalance_bossdrops=false or config/elsewhere-overhaul3.json. */
	static boolean overhaul3(String key) {
		String p = System.getProperty("elsewhere.overhaul3." + key);
		if (p != null) return !"false".equalsIgnoreCase(p);
		try {
			java.nio.file.Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (java.nio.file.Files.isRegularFile(f)) {
				com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
				if (o.has(key)) return o.get(key).getAsBoolean();
			}
		} catch (Exception e) {
			// default on
		}
		return true;
	}

	/** True when the core's TierGear is loaded (merged jar): it applies the Cleaver bonus, so this stub's copy stands down (no stacking). */
	static boolean coreCleaver() {
		try {
			Class.forName("dev.elsewhere.tiergear.TierGear", false, StubFramework.class.getClassLoader());
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
