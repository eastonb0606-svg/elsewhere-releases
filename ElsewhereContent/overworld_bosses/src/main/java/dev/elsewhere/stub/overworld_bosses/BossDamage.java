package dev.elsewhere.stub.overworld_bosses;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/**
 * Difficulty framework helpers (BOSSES.md "Difficulty framework"): scripted attack scaling per difficulty, the
 * overpower guard on damage dealt to bosses, the lethal-hit guard on damage dealt by bosses, and the Aegis of the
 * Ladder block. STUB: moves to the core framework at merge.
 */
public final class BossDamage {
	public static final float OVERPOWER_THRESHOLD = 24.0F;
	public static final float LETHAL_CAP = 0.60F;
	/** Scoreboard tag on minions and boss projectiles, so the lethal-hit guard covers them too. */
	public static final String MINION_TAG = "elsewhere_boss_minion";

	private BossDamage() {}

	/** Raw values are Normal; scripted attacks are x1.25 on Hard and x0.6 on Easy. */
	public static float scaled(ServerLevel level, float raw) {
		Difficulty d = level.getDifficulty();
		return switch (d) {
			case PEACEFUL, EASY -> raw * 0.6F;
			case HARD -> raw * 1.25F;
			default -> raw;
		};
	}

	/** Overpower guard: per-hit damage above 24 is halved past 24, before armor. */
	public static float overpowerGuard(float damage) {
		return damage <= OVERPOWER_THRESHOLD ? damage : OVERPOWER_THRESHOLD + (damage - OVERPOWER_THRESHOLD) * 0.5F;
	}

	public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, Entity direct, Entity cause) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), direct, cause);
	}

	/** True when the damage comes from a boss, a boss projectile or a boss minion (lethal-hit guard applies). */
	public static boolean isBossDamage(DamageSource source) {
		Entity cause = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (cause instanceof ElsewhereBoss || direct instanceof ElsewhereBoss) return true;
		if (cause instanceof BossPart || direct instanceof BossPart) return true;
		if (cause != null && cause.entityTags().contains(MINION_TAG)) return true;
		if (direct != null && direct.entityTags().contains(MINION_TAG)) return true;
		return direct instanceof Projectile p && p.getOwner() instanceof ElsewhereBoss;
	}

	/** Lethal-hit guard: no single hit takes more than 60% of a player's max health after armor. */
	public static float lethalGuard(LivingEntity target, DamageSource source, float afterArmor) {
		if (target instanceof Player && isBossDamage(source)) {
			return Math.min(afterArmor, target.getMaxHealth() * LETHAL_CAP);
		}
		return afterArmor;
	}

	/**
	 * Deals a scripted boss hit: difficulty scaling, then the Aegis of the Ladder check for telegraphed big hits
	 * (fully blocks one every 30 s and reflects 25% to the boss), then the hit itself.
	 */
	public static boolean hit(ElsewhereBoss boss, LivingEntity target, float raw, ResourceKey<DamageType> type, boolean big) {
		return hit(boss, boss, target, raw, type, big);
	}

	public static boolean hit(ElsewhereBoss boss, Entity direct, LivingEntity target, float raw, ResourceKey<DamageType> type, boolean big) {
		if (!(boss.level() instanceof ServerLevel level)) return false;
		float dmg = scaled(level, raw);
		if (big && target instanceof ServerPlayer player && tryAegis(level, boss, player, dmg)) return false;
		return target.hurtServer(level, source(level, type, direct, boss), dmg);
	}

	private static boolean tryAegis(ServerLevel level, ElsewhereBoss boss, ServerPlayer player, float dmg) {
		if (SignatureGear.AEGIS_OF_THE_LADDER == null || !player.isBlocking()) return false;
		ItemStack using = player.getUseItem();
		if (!using.is(SignatureGear.AEGIS_OF_THE_LADDER) || player.getCooldowns().isOnCooldown(using)) return false;
		player.getCooldowns().addCooldown(using, SignatureGear.AEGIS_COOLDOWN_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 1.6F);
		boss.hurtServer(level, player.damageSources().thorns(player), dmg * SignatureGear.AEGIS_REFLECT);
		return true;
	}
}
