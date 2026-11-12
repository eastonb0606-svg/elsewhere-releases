package dev.elsewhere.stub.overworld_bosses;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

/**
 * The per-tier signature items from BOSSES.md "Signature gear" that this module's bosses drop (Tier I, II, III).
 * Numbers are the doc values until BALANCE.md is final. STUB: the core registers the full set (I..VI) after the
 * merge; this module only needs these three.
 */
public final class SignatureGear {
	// Doc values (BOSSES.md "Signature gear"). Design owner: BALANCE.md.
	public static final float CLEAVER_BASELINE = 3.0F; // BALANCE.md final: diamond material 1 + 3 + 3 = 7 damage
	public static final float CLEAVER_SPEED = -2.4F; // 1.6 attacks/s
	public static final float CLEAVER_BOSS_BONUS = overhaul3("rebalance_bossdrops") ? (coreCleaver() ? 0.0F : 0.20F) : 0.10F; // 3.0: +20%, applied once (core)
	public static final int AEGIS_DURABILITY = 900;
	public static final int AEGIS_COOLDOWN_TICKS = 30 * 20;
	public static final float AEGIS_REFLECT = 0.25F;
	public static final int GREAVES_ARMOR = 6;
	public static final float GREAVES_TOUGHNESS = 2.0F; // BALANCE.md final 6 / 2 / 0
	public static final float GREAVES_KB = 0.0F;
	public static final double GREAVES_DASH_BLOCKS = 4.0;
	public static final int GREAVES_IFRAMES = 5; // 0.25 s
	public static final int GREAVES_COOLDOWN_TICKS = 10 * 20;
	public static final int DOUBLE_TAP_WINDOW = 7;

	public static Item CHALLENGERS_CLEAVER;
	public static Item AEGIS_OF_THE_LADDER;
	public static Item PHASEWALKER_GREAVES;

	public static final ResourceKey<EquipmentAsset> PHASEWALKER_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, BossRegistry.id("phasewalker"));

	static void init() {
		CHALLENGERS_CLEAVER = BossRegistry.item("challengers_cleaver", Item::new, new Item.Properties()
			.sword(ToolMaterial.DIAMOND, CLEAVER_BASELINE, CLEAVER_SPEED).rarity(Rarity.RARE));
		AEGIS_OF_THE_LADDER = BossRegistry.item("aegis_of_the_ladder", ShieldItem::new, new Item.Properties()
			.durability(AEGIS_DURABILITY).rarity(Rarity.EPIC)
			.repairable(ItemTags.DIAMOND_TOOL_MATERIALS)
			.equippableUnswappable(EquipmentSlot.OFFHAND)
			.component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(0.25F, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK));
		ArmorMaterial phasewalker = new ArmorMaterial(37, Map.of(ArmorType.LEGGINGS, GREAVES_ARMOR), 15,
			SoundEvents.ARMOR_EQUIP_NETHERITE, GREAVES_TOUGHNESS, GREAVES_KB, ItemTags.REPAIRS_NETHERITE_ARMOR, EquipmentAssets.NETHERITE);
		PHASEWALKER_GREAVES = BossRegistry.item("phasewalker_greaves", Item::new, new Item.Properties()
			.humanoidArmor(phasewalker, ArmorType.LEGGINGS).rarity(Rarity.EPIC).fireResistant());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) tickGreaves(p);
		});
	}

	public static boolean isCleaver(ItemStack stack) {
		return CHALLENGERS_CLEAVER != null && stack.is(CHALLENGERS_CLEAVER);
	}

	// Phasewalker Greaves: double-tap sneak to dash 4 blocks with 0.4 s of invulnerability; 6 s cooldown.
	private static final Map<UUID, int[]> SNEAK = new HashMap<>(); // {wasDown, lastPressTick}

	private static void tickGreaves(ServerPlayer p) {
		ItemStack legs = p.getItemBySlot(EquipmentSlot.LEGS);
		if (PHASEWALKER_GREAVES == null || !legs.is(PHASEWALKER_GREAVES)) return;
		int[] st = SNEAK.computeIfAbsent(p.getUUID(), u -> new int[] {0, -100});
		boolean down = p.isShiftKeyDown();
		if (down && st[0] == 0) {
			int now = p.tickCount;
			if (now - st[1] <= DOUBLE_TAP_WINDOW && !p.getCooldowns().isOnCooldown(legs)) {
				dash(p, legs);
				st[1] = -100;
			} else {
				st[1] = now;
			}
		}
		st[0] = down ? 1 : 0;
	}

	public static void dash(ServerPlayer p, ItemStack legs) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
		flat = flat.normalize().scale(GREAVES_DASH_BLOCKS * 0.42);
		p.setDeltaMovement(flat.x, 0.15, flat.z);
		p.needsSync = true;
		p.setInvulnerableTime(Math.max(p.getInvulnerableTime(), GREAVES_IFRAMES));
		p.getCooldowns().addCooldown(legs, GREAVES_COOLDOWN_TICKS);
		p.causeFoodExhaustion(4.0F);
		if (p.level() instanceof ServerLevel sl) {
			sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
	}

	public static Component tierName(int tier) {
		return Component.translatable("tier.elsewhere." + tier);
	}

	static ResourceKey<Item> key(String path) {
		return ResourceKey.create(Registries.ITEM, BossRegistry.id(path));
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
			Class.forName("dev.elsewhere.tiergear.TierGear", false, SignatureGear.class.getClassLoader());
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
