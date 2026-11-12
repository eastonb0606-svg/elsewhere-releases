package dev.elsewhere.tiergear;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.registry.ModItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * fix-tier-gear-owner: the core owns the six tier signature items (BALANCE.md M-core) and all their abilities. It registers
 * them before any content module, so the modules' stubs ({@code Reg.item}/{@code sharedItem}/{@code BossRegistry.item})
 * return these copies. Numbers: Cleaver 7 / 1.6, Aegis 900, Phasewalker 6 / 2 / 0, Stormcrest 3 / 2 / 0.15,
 * Echo-Forged Blade 9 / 1.4 (1600, ench 15), Halo 3 / 3 / 0.
 */
public final class TierGear {
	private TierGear() {}

	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Elsewhere.id("bosses"));
	public static final TagKey<Item> ECHO_ALLOY_REPAIR = TagKey.create(Registries.ITEM, Elsewhere.id("repairs_echo_forged_blade"));

	public static final float CLEAVER_BOSS_BONUS = overhaul3("rebalance_bossdrops") ? 1.20F : 1.10F; // 3.0 (GEAR.md A4): +20% vs bosses; module stubs stand down
	public static final int AEGIS_COOLDOWN = 30 * 20;
	public static final float AEGIS_REFLECT = 0.25F;
	public static final float AEGIS_RAISE_SECONDS = 0.5F;
	/** Axe disable: vanilla 5 s x 1.6 = 8 s. */
	public static final float AEGIS_DISABLE_SCALE = 1.6F;
	public static final double DASH_BLOCKS = 4.0;
	public static final int DASH_IMMUNE_TICKS = 5;
	public static final int DASH_COOLDOWN = 10 * 20;
	public static final int DOUBLE_TAP_WINDOW = 7;
	/** Stormcrest: boss warnings this many ticks earlier (0.25 s). */
	public static final int STORMCREST_LEAD_TICKS = 5;
	public static final float AMBUSH_BONUS = 1.5F;
	public static final int HALO_RESCUE_COOLDOWN = 10 * 60 * 20;
	public static final float HALO_RESCUE_COST = 4.0F;

	public static Item CHALLENGERS_CLEAVER, AEGIS_OF_THE_LADDER, PHASEWALKER_GREAVES, STORMCREST_HELM, ECHO_FORGED_BLADE, HALO_OF_THE_OUTER_END;

	private static ResourceKey<EquipmentAsset> asset(final String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Elsewhere.id(name));
	}

	public static void register() {
		if (CHALLENGERS_CLEAVER != null) {
			return;
		}
		CHALLENGERS_CLEAVER = ModItems.register("challengers_cleaver", Item::new, new Item.Properties()
			.sword(ToolMaterial.DIAMOND, 3.0F, -2.4F).rarity(Rarity.RARE));
		AEGIS_OF_THE_LADDER = ModItems.register("aegis_of_the_ladder", ShieldItem::new, new Item.Properties()
			.durability(900).rarity(Rarity.EPIC).repairable(ItemTags.DIAMOND_TOOL_MATERIALS)
			.equippableUnswappable(EquipmentSlot.OFFHAND)
			.component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(AEGIS_RAISE_SECONDS, AEGIS_DISABLE_SCALE,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK));
		PHASEWALKER_GREAVES = ModItems.register("phasewalker_greaves", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
			.humanoidArmor(new ArmorMaterial(37, Map.of(ArmorType.LEGGINGS, 6), 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.0F,
				ItemTags.REPAIRS_NETHERITE_ARMOR, asset("phasewalker")), ArmorType.LEGGINGS));
		STORMCREST_HELM = ModItems.register("stormcrest_helm", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
			.humanoidArmor(new ArmorMaterial(37, Map.of(ArmorType.HELMET, 3), 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.15F,
				ItemTags.REPAIRS_NETHERITE_ARMOR, asset("stormcrest")), ArmorType.HELMET));
		ECHO_FORGED_BLADE = ModItems.register("echo_forged_blade", Item::new, new Item.Properties()
			.sword(ToolMaterial.NETHERITE, 4.0F, -2.6F).durability(1600).enchantable(15).repairable(ECHO_ALLOY_REPAIR).rarity(Rarity.EPIC));
		HALO_OF_THE_OUTER_END = ModItems.register("halo_of_the_outer_end", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
			.humanoidArmor(new ArmorMaterial(37, Map.of(ArmorType.HELMET, 3), 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.0F,
				ItemTags.REPAIRS_NETHERITE_ARMOR, asset("halo_of_the_outer_end")), ArmorType.HELMET));
	}

	// ------------------------------------------------------------------ abilities
	private static final Map<UUID, int[]> SNEAK = new HashMap<>(); // {wasDown, lastPressTick}
	private static final Map<UUID, Long> DASH_IMMUNE_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> AEGIS_READY_AT = new HashMap<>();
	private static final Map<UUID, Long> HALO_READY_AT = new HashMap<>();
	/** Last solid ground per player, with its dimension (fix-stability: position records carry the dimension). */
	private static final Map<UUID, GroundPos> LAST_GROUND = new HashMap<>();

	public record GroundPos(ResourceKey<Level> dim, BlockPos pos) {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				tick(p);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(TierGear::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0.0F && p.getMainHandItem().is(ECHO_FORGED_BLADE)) {
				p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false)); // con: you glow 3 s
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearState());
	}

	public static void clearState() {
		SNEAK.clear();
		DASH_IMMUNE_UNTIL.clear();
		AEGIS_READY_AT.clear();
		HALO_READY_AT.clear();
		LAST_GROUND.clear();
	}

	public static boolean isBoss(final Entity e) {
		return e != null && e.getType().builtInRegistryHolder().is(BOSSES);
	}

	/** True while a living boss of {@code #elsewhere:bosses} is within 64 blocks. */
	public static boolean inBossFight(final ServerPlayer p) {
		ServerLevel level = p.level();
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(64.0), e -> e.isAlive() && isBoss(e)).isEmpty();
	}

	private static void tick(final ServerPlayer p) {
		long now = p.level().getGameTime();
		if (p.onGround() && !p.isSpectator()) {
			LAST_GROUND.put(p.getUUID(), new GroundPos(p.level().dimension(), p.blockPosition()));
		}
		ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
		if (head.is(HALO_OF_THE_OUTER_END) && p.hasEffect(MobEffects.LEVITATION)) {
			p.removeEffect(MobEffects.LEVITATION);
		}
		if (head.is(STORMCREST_HELM) && p.tickCount % 10 == 0 && (p.hasEffect(MobEffects.BLINDNESS) || p.hasEffect(MobEffects.DARKNESS)) && inBossFight(p)) {
			p.removeEffect(MobEffects.BLINDNESS);
			p.removeEffect(MobEffects.DARKNESS);
		}
		ItemStack legs = p.getItemBySlot(EquipmentSlot.LEGS);
		if (legs.is(PHASEWALKER_GREAVES)) {
			int[] st = SNEAK.computeIfAbsent(p.getUUID(), u -> new int[] {0, -100});
			boolean down = p.isShiftKeyDown();
			if (down && st[0] == 0) {
				if (p.tickCount - st[1] <= DOUBLE_TAP_WINDOW && !p.getCooldowns().isOnCooldown(legs)) {
					dash(p, legs);
					st[1] = -100;
				} else {
					st[1] = p.tickCount;
				}
			}
			st[0] = down ? 1 : 0;
		}
	}

	/** Phasewalker dash: 4 blocks, 0.25 s immune to mob and boss damage (not players), 10 s cooldown, 1 hunger point. */
	public static void dash(final ServerPlayer p, final ItemStack legs) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = new Vec3(0, 0, 1);
		}
		flat = flat.normalize().scale(DASH_BLOCKS * 0.42);
		p.setDeltaMovement(flat.x, 0.15, flat.z);
		p.needsSync = true;
		DASH_IMMUNE_UNTIL.put(p.getUUID(), p.level().getGameTime() + DASH_IMMUNE_TICKS);
		p.getCooldowns().addCooldown(legs, DASH_COOLDOWN);
		p.causeFoodExhaustion(4.0F);
		p.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	public static boolean dashImmune(final Player p) {
		Long until = DASH_IMMUNE_UNTIL.get(p.getUUID());
		return until != null && p.level().getGameTime() < until;
	}

	private static boolean allowDamage(final LivingEntity entity, final DamageSource source, final float amount) {
		if (!(entity instanceof ServerPlayer p)) {
			return true;
		}
		Entity attacker = source.getEntity();
		long now = p.level().getGameTime();
		// Phasewalker: mob and boss damage ignored during the dash
		if (dashImmune(p) && attacker != null && !(attacker instanceof Player)) {
			return false;
		}
		// Aegis: one boss hit every 30 s is blocked completely and 25% is reflected
		if (attacker instanceof LivingEntity boss && isBoss(boss) && p.isBlocking() && p.getUseItem().is(AEGIS_OF_THE_LADDER)
			&& now >= AEGIS_READY_AT.getOrDefault(p.getUUID(), 0L)) {
			AEGIS_READY_AT.put(p.getUUID(), now + AEGIS_COOLDOWN);
			boss.hurtServer(p.level(), p.level().damageSources().thorns(p), amount * AEGIS_REFLECT);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.7F);
			p.level().sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1.2, p.getZ(), 16, 0.5, 0.5, 0.5, 0.2);
			p.sendOverlayMessage(Component.translatable("message.elsewhere.aegis.block").withStyle(ChatFormatting.AQUA));
			return false;
		}
		// Halo: falling into the void returns you to your last solid ground once per 10 min (-4 HP)
		if (source.is(DamageTypes.FELL_OUT_OF_WORLD) && p.getItemBySlot(EquipmentSlot.HEAD).is(HALO_OF_THE_OUTER_END)
			&& now >= HALO_READY_AT.getOrDefault(p.getUUID(), 0L)) {
			GroundPos g = LAST_GROUND.get(p.getUUID());
			if (g != null && g.dim().equals(p.level().dimension())) {
				HALO_READY_AT.put(p.getUUID(), now + HALO_RESCUE_COOLDOWN);
				p.teleportTo(g.pos().getX() + 0.5, g.pos().getY(), g.pos().getZ() + 0.5);
				p.setDeltaMovement(Vec3.ZERO);
				p.resetFallDistance();
				p.setHealth(Math.max(1.0F, p.getHealth() - HALO_RESCUE_COST));
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
				p.sendOverlayMessage(Component.translatable("message.elsewhere.halo.rescue").withStyle(ChatFormatting.LIGHT_PURPLE));
				return false;
			}
		}
		return true;
	}

	/** Damage hook (LivingEntity.hurtServer): Cleaver +10% vs bosses + weak-point glow; Echo-Forged Blade Ambush +50%. */
	public static float modifyDamage(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(source.getEntity() instanceof ServerPlayer p) || source.getDirectEntity() != p || amount <= 0.0F) {
			return amount;
		}
		ItemStack main = p.getMainHandItem();
		if (CHALLENGERS_CLEAVER != null && main.is(CHALLENGERS_CLEAVER) && isBoss(victim)) {
			victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
			return amount * CLEAVER_BOSS_BONUS;
		}
		if (ECHO_FORGED_BLADE != null && main.is(ECHO_FORGED_BLADE) && !isBoss(victim) && victim instanceof Mob mob && mob.getTarget() != p
			&& mob.getLastHurtByMob() != p) {
			if (victim.level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY() + victim.getBbHeight() * 0.7, victim.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
			}
			return amount * AMBUSH_BONUS;
		}
		return amount;
	}

	/** Echo-Forged Blade: your hits make no vibrations (game events whose source is a player holding it, on hits). */
	public static boolean silences(final Entity source, final net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event) {
		return source instanceof Player p && ECHO_FORGED_BLADE != null && p.getMainHandItem().is(ECHO_FORGED_BLADE)
			&& (event.is(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DAMAGE) || event.is(net.minecraft.world.level.gameevent.GameEvent.ENTITY_ACTION));
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
}
