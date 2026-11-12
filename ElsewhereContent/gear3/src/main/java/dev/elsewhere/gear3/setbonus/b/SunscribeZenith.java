package dev.elsewhere.gear3.setbonus.b;

import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Sunscribe Vestments set "Zenith" (Artillery, 4 pieces; GEAR.md A5): under open daylight sky, spells and Glimmer items
 * cost -25% and Glimmer regenerates x1.25; at night spell damage -10%.
 *
 * <p>"Open daylight sky" = the level is bright outside (vanilla's day test: not a fixed-time dimension, sky not darkened)
 * and the wearer's head sees the sky. "Night" = vanilla's dark-outside test (night, or a storm-dark sky) in a dimension
 * with a day cycle; the Nether, the End and fixed-time realms are neither. The sky is read only when arsenal asks for a
 * cost, a regen step or a spell's damage, and cached per wearer for 1 s (regen is asked every tick while it refills).
 * Ascendant pieces scale the bonuses (x1.5), never the night drawback. Kill switch {@code setbonus_zenith}.
 */
public final class SunscribeZenith {
	private SunscribeZenith() {}

	public static final float COST_CUT = 0.25F, REGEN_BONUS = 0.25F, NIGHT_POWER = 0.90F;
	public static final int SKY_CACHE_TICKS = 20;
	public static SetBonus SET;

	public enum Sky {
		DAY, NIGHT, NEITHER
	}

	/** Self-test only: forces the sky reading (null = read the level). */
	public static Sky forceSky;

	private record Cached(long tick, Sky sky) {}

	private static final Map<UUID, Cached> CACHE = new HashMap<>();

	static void init() {
		SET = SetBonuses.register(SetBonus.of("zenith")
			.piece(EquipmentSlot.HEAD, "sunscribe_hood")
			.piece(EquipmentSlot.CHEST, "sunscribe_robe")
			.piece(EquipmentSlot.LEGS, "sunscribe_leggings")
			.piece(EquipmentSlot.FEET, "sunscribe_boots")
			.details(3)
			.onChange((p, on) -> CACHE.remove(p.getUUID())));
		SetBonuses.SPELL_COST.add(SunscribeZenith::costMult);
		SetBonuses.GLIMMER_REGEN.add(SunscribeZenith::regenMult);
		Spells.DAMAGE_MULT.add(SunscribeZenith::spellMult);
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> CACHE.remove(h.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> CACHE.clear());
	}

	/** Pure rule: which sky a position is under. */
	public static Sky classify(boolean brightOutside, boolean seesSky, boolean darkOutside) {
		if (brightOutside && seesSky) return Sky.DAY;
		return darkOutside ? Sky.NIGHT : Sky.NEITHER;
	}

	public static Sky read(Level level, BlockPos head) {
		return classify(level.isBrightOutside(), level.canSeeSky(head), level.isDarkOutside());
	}

	/** The wearer's sky now (cached 1 s). */
	public static Sky sky(ServerPlayer p) {
		if (forceSky != null) return forceSky;
		long now = p.level().getGameTime();
		Cached c = CACHE.get(p.getUUID());
		if (c != null && now - c.tick() < SKY_CACHE_TICKS && now >= c.tick()) return c.sky();
		Sky s = read(p.level(), BlockPos.containing(p.getX(), p.getEyeY(), p.getZ()));
		CACHE.put(p.getUUID(), new Cached(now, s));
		return s;
	}

	/** SetBonuses.SPELL_COST: x(1 - 0.25 x power) under open daylight sky. */
	public static float costMult(ServerPlayer p) {
		float pw = SetBonuses.power(p, SET);
		if (pw <= 0F || sky(p) != Sky.DAY) return 1F;
		return Math.max(0.1F, 1F - COST_CUT * pw);
	}

	/** SetBonuses.GLIMMER_REGEN: x(1 + 0.25 x power) under open daylight sky. */
	public static float regenMult(ServerPlayer p) {
		float pw = SetBonuses.power(p, SET);
		if (pw <= 0F || sky(p) != Sky.DAY) return 1F;
		return 1F + REGEN_BONUS * pw;
	}

	/** Spells.DAMAGE_MULT: x0.90 at night (the drawback does not grow with Ascendant pieces). */
	public static double spellMult(ServerPlayer owner, LivingEntity target) {
		if (!SetBonuses.active(owner, SET)) return 1.0;
		return sky(owner) == Sky.NIGHT ? NIGHT_POWER : 1.0;
	}
}
