package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.Reg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * polish-endgame-sets, Glasswaste: the code-only half of the Sunwright line (BALANCE N1 / Glasswaste section). Per-piece
 * attributes live on the item definitions ({@link GlasswasteItems}); this class holds Noonfire, the Helm / Legs / Boots
 * perks, the freeze con, the Fire Protection ban and the Sunstone daylight pro / dark con.
 *
 * <p>Redundancy review (DECISIONS: no perk vanilla makes trivial): a Fire Resistance potion costs brewing and 8 minutes
 * of upkeep, does nothing about Heat, and netherite gives no fire protection to the wearer, so Noonfire (no upkeep, Heat
 * immunity, ignites attackers) is a real choice, and it is paid for with -4 toughness, no knockback resistance, the
 * freeze con and no Fire Protection. Blaze fireballs and other projectile fire still hurt (only environmental fire,
 * burning, lava and magma are cancelled).
 */
public final class SunwrightPerks {
	private SunwrightPerks() {}

	private static final Identifier SAND_SPEED = Reg.id("sunwright_sand_speed");
	private static final Identifier SUN_MINING = Reg.id("sunstone_sun_mining");
	private static final Identifier DARK_MINING = Reg.id("sunstone_dark_mining");
	private static final Identifier DARK_DAMAGE = Reg.id("sunstone_dark_damage");
	private static final Map<UUID, Integer> LAST_FROZEN = new HashMap<>();
	private static boolean inFreeze;

	/** Full Sunwright set (the Plate counts for the chestplate; the loot Visor is not a set piece). */
	public static boolean noonfire(LivingEntity e) {
		Item chest = e.getItemBySlot(EquipmentSlot.CHEST).getItem();
		return e.getItemBySlot(EquipmentSlot.HEAD).is(GlasswasteItems.SUNWRIGHT_HELMET) && (chest == GlasswasteItems.SUNWRIGHT_CHESTPLATE || chest == GlasswasteItems.SUNWRIGHT_PLATE)
			&& e.getItemBySlot(EquipmentSlot.LEGS).is(GlasswasteItems.SUNWRIGHT_LEGGINGS) && e.getItemBySlot(EquipmentSlot.FEET).is(GlasswasteItems.SUNWRIGHT_BOOTS);
	}

	/** Pieces of the set worn (Helm, Chest or Plate, Legs, Boots; the Visor does not count). */
	public static int setPieces(LivingEntity e) {
		int n = 0;
		if (e.getItemBySlot(EquipmentSlot.HEAD).is(GlasswasteItems.SUNWRIGHT_HELMET)) n++;
		Item chest = e.getItemBySlot(EquipmentSlot.CHEST).getItem();
		if (chest == GlasswasteItems.SUNWRIGHT_CHESTPLATE || chest == GlasswasteItems.SUNWRIGHT_PLATE) n++;
		if (e.getItemBySlot(EquipmentSlot.LEGS).is(GlasswasteItems.SUNWRIGHT_LEGGINGS)) n++;
		if (e.getItemBySlot(EquipmentSlot.FEET).is(GlasswasteItems.SUNWRIGHT_BOOTS)) n++;
		return n;
	}

	public static boolean sunwrightHelm(LivingEntity e) {
		return e.getItemBySlot(EquipmentSlot.HEAD).is(GlasswasteItems.SUNWRIGHT_HELMET) || e.getItemBySlot(EquipmentSlot.HEAD).is(GlasswasteItems.SUNWRIGHT_VISOR);
	}

	/** True when this damage is cancelled for {@code e} (Noonfire: fire, burning, lava, magma; Legs: magma and campfires). Pure, for tests. */
	public static boolean blocks(LivingEntity e, DamageSource source) {
		boolean legs = e.getItemBySlot(EquipmentSlot.LEGS).is(GlasswasteItems.SUNWRIGHT_LEGGINGS);
		if (legs && (source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.CAMPFIRE))) return true;
		if (!noonfire(e)) return false;
		return source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)
			|| source.is(DamageTypes.CAMPFIRE);
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) return true;
			if (blocks(p, source)) {
				if (p.isOnFire() && noonfire(p)) p.clearFire();
				return false;
			}
			int pieces = setPieces(p);
			if (!inFreeze && pieces > 0 && source.is(DamageTypes.FREEZE)) {
				inFreeze = true;
				try {
					p.hurtServer(p.level(), source, (float) (amount * (1 + GearStats.SUNWRIGHT_FREEZE_DAMAGE_PER_PIECE * pieces)));
				} finally {
					inFreeze = false;
				}
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || taken <= 0 || !(entity instanceof ServerPlayer p) || !noonfire(p)) return;
			if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity() && attacker != p) {
				attacker.igniteForSeconds(GearStats.NOONFIRE_IGNITE_SECONDS);
			}
		});
		EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, target, context) -> bannedEnchant(enchantment, target) ? TriState.FALSE : TriState.DEFAULT);
	}

	/** The Sunwright armor can't take Fire Protection (its Noonfire is the fire answer). */
	public static boolean bannedEnchant(Holder<net.minecraft.world.item.enchantment.Enchantment> e, ItemStack s) {
		var key = e.unwrapKey().orElse(null);
		if (key == null || !key.equals(Enchantments.FIRE_PROTECTION)) return false;
		Item i = s.getItem();
		return i == GlasswasteItems.SUNWRIGHT_HELMET || i == GlasswasteItems.SUNWRIGHT_CHESTPLATE || i == GlasswasteItems.SUNWRIGHT_LEGGINGS
			|| i == GlasswasteItems.SUNWRIGHT_BOOTS || i == GlasswasteItems.SUNWRIGHT_PLATE || i == GlasswasteItems.SUNWRIGHT_VISOR;
	}

	public static void serverTick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p);
	}

	private static void setModifier(ServerPlayer p, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op, boolean on) {
		AttributeInstance a = p.getAttribute(attr);
		if (a == null) return;
		AttributeModifier cur = a.getModifier(id);
		if (!on) {
			if (cur != null) a.removeModifier(id);
		} else if (cur == null || Math.abs(cur.amount() - amount) > 1e-6) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** Sand, red sand, soul sand or soul soil under the feet. */
	public static boolean sandy(BlockState s) {
		return s.is(BlockTags.SAND) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL);
	}

	/** One player's code-only perks (public so the self-test can drive it). */
	public static void tickPlayer(ServerPlayer p) {
		if (noonfire(p) && p.isOnFire()) p.clearFire();
		if (sunwrightHelm(p) && p.hasEffect(MobEffects.BLINDNESS)) p.removeEffect(MobEffects.BLINDNESS);
		boolean boots = p.getItemBySlot(EquipmentSlot.FEET).is(GlasswasteItems.SUNWRIGHT_BOOTS);
		setModifier(p, Attributes.MOVEMENT_SPEED, SAND_SPEED, GearStats.SUNWRIGHT_SAND_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
			boots && p.onGround() && sandy(p.level().getBlockState(p.getBlockPosBelowThatAffectsMyMovement())));
		// Freeze con: with the full set freezing builds 2x faster.
		int frozen = p.getTicksFrozen(), last = LAST_FROZEN.getOrDefault(p.getUUID(), 0);
		if (noonfire(p) && frozen > last) {
			int extra = (int) Math.round((frozen - last) * (GearStats.SUNWRIGHT_FREEZE_BUILDUP_SET - 1.0));
			p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, frozen + extra));
			frozen = p.getTicksFrozen();
		}
		LAST_FROZEN.put(p.getUUID(), frozen);
		// Sunstone: +15% mining speed in daylight under open sky; underground, at night or in rain -1 attack damage and pick speed 8 instead of 9.
		Item held = p.getMainHandItem().getItem();
		boolean sunstone = isSunstoneTool(held);
		boolean sun = sunstone && p.level() != null && Heat.inDirectSun(p.level(), p);
		setModifier(p, Attributes.BLOCK_BREAK_SPEED, SUN_MINING, GearStats.SUNSTONE_SUN_MINING, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, sun);
		boolean dark = sunstone && !sun;
		setModifier(p, Attributes.BLOCK_BREAK_SPEED, DARK_MINING, GearStats.SUNSTONE_DARK_SPEED - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, dark);
		boolean blade = held == GlasswasteItems.SUNSTONE_SWORD || held == GlasswasteItems.SUNSTONE_AXE;
		setModifier(p, Attributes.ATTACK_DAMAGE, DARK_DAMAGE, -GearStats.SUNSTONE_DARK_PENALTY, AttributeModifier.Operation.ADD_VALUE, blade && dark);
	}

	public static boolean isSunstoneTool(Item i) {
		return i == GlasswasteItems.SUNSTONE_SWORD || i == GlasswasteItems.SUNSTONE_PICKAXE || i == GlasswasteItems.SUNSTONE_AXE
			|| i == GlasswasteItems.SUNSTONE_SHOVEL || i == GlasswasteItems.SUNSTONE_HOE;
	}

	public static void forget(ServerPlayer p) {
		LAST_FROZEN.remove(p.getUUID());
	}
}
