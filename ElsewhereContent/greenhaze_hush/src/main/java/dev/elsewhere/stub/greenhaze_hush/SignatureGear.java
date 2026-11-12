package dev.elsewhere.stub.greenhaze_hush;

import dev.elsewhere.greenhaze_hush.Reg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;

/**
 * STUB of the shared tier signature gear (BOSSES.md "Signature gear"); the core / design agent owns these at merge.
 * Only the tiers this module's bosses drop: I Challenger's Cleaver (Grumblegut), III Phasewalker Greaves (Percolax),
 * V Echo-Forged Blade (Auris, Lithara). Guaranteed on a player's first kill of each boss in the tier, 10% after.
 */
public final class SignatureGear {
	private SignatureGear() {}

	public static final float REPEAT_CHANCE = 0.10F;
	public static final float CLEAVER_BOSS_BONUS = overhaul3("rebalance_bossdrops") ? (coreCleaver() ? 1.0F : 1.20F) : 1.15F; // 3.0: +20%, applied once (core)
	public static final int DASH_COOLDOWN = 120, DASH_IFRAMES = 8;
	public static final float BLADE_STORE = 0.20F, BLADE_CAP = 12F;

	public static Item CHALLENGERS_CLEAVER, PHASEWALKER_GREAVES, ECHO_FORGED_BLADE;
	private static final Map<UUID, int[]> SNEAK = new HashMap<>(); // last press tick, was sneaking, cooldown until
	private static final Map<UUID, Float> STORED = new HashMap<>();

	public static void init() {
		java.util.List<Item> tab = Reg.currentTab;
		CHALLENGERS_CLEAVER = Reg.item("challengers_cleaver", Item::new, new Item.Properties()
			.sword(ToolMaterial.DIAMOND, 8F - 1F - ToolMaterial.DIAMOND.attackDamageBonus(), 1.6F - 4F).rarity(Rarity.RARE));
		ArmorMaterial phase = new ArmorMaterial(37, ArmorMaterials.makeDefense(3, 6, 8, 3, 19), 15, SoundEvents.ARMOR_EQUIP_NETHERITE,
			4.0F, 0.1F, net.minecraft.tags.ItemTags.REPAIRS_NETHERITE_ARMOR,
			net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("phasewalker")));
		PHASEWALKER_GREAVES = Reg.item("phasewalker_greaves", Item::new, new Item.Properties().humanoidArmor(phase, ArmorType.LEGGINGS)
			.rarity(Rarity.EPIC).fireResistant());
		ECHO_FORGED_BLADE = Reg.item("echo_forged_blade", Item::new, new Item.Properties()
			.sword(ToolMaterial.NETHERITE, 10F - 1F - ToolMaterial.NETHERITE.attackDamageBonus(), 1.6F - 4F).rarity(Rarity.EPIC).fireResistant());

		// Phasewalker Greaves: double-tap sneak to dash 4 blocks with 0.4 s of invulnerability; 6 s cooldown.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				int[] st = SNEAK.computeIfAbsent(p.getUUID(), k -> new int[]{-100, 0, 0});
				boolean sneaking = p.isShiftKeyDown();
				if (sneaking && st[1] == 0) {
					if (now - st[0] <= 8 && now >= st[2] && p.getItemBySlot(EquipmentSlot.LEGS).is(PHASEWALKER_GREAVES)) dash(p, now, st);
					st[0] = now;
				}
				st[1] = sneaking ? 1 : 0;
			}
		});
		// Echo-Forged Blade: stores 20% of damage taken; next crit releases it (cap 12).
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0 && p.getMainHandItem().is(ECHO_FORGED_BLADE)) {
				STORED.merge(p.getUUID(), taken * BLADE_STORE, (a, b) -> Math.min(BLADE_CAP, a + b));
			}
		});
	}

	private static void dash(ServerPlayer p, int now, int[] st) {
		st[2] = now + DASH_COOLDOWN;
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z).normalize().scale(1.6);
		p.setDeltaMovement(dir.x, 0.1, dir.z);
		p.needsSync = true;
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, DASH_IFRAMES, 4, false, false));
		p.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
		p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	/** Bonus melee damage for the player's hit on target (Cleaver vs bosses, Echo-Forged Blade crit release). */
	public static float modifyOutgoing(ServerPlayer p, LivingEntity target, float amount, boolean crit) {
		if (p.getMainHandItem().is(CHALLENGERS_CLEAVER) && target instanceof BossEntity) amount *= CLEAVER_BOSS_BONUS;
		if (crit && p.getMainHandItem().is(ECHO_FORGED_BLADE)) {
			Float s = STORED.remove(p.getUUID());
			if (s != null) amount += s;
		}
		return amount;
	}

	public static float stored(ServerPlayer p) {
		return STORED.getOrDefault(p.getUUID(), 0F);
	}

	public static void forgetPlayer(UUID id) {
		SNEAK.remove(id);
		STORED.remove(id);
	}

	public static void clearAll() {
		SNEAK.clear();
		STORED.clear();
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
