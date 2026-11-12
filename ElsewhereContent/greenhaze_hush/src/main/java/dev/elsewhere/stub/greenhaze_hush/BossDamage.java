package dev.elsewhere.stub.greenhaze_hush;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * STUB of the Difficulty framework's damage rules. Scripted boss attacks use the {@code elsewhere:boss_attack} damage
 * type (no vanilla difficulty scaling) and are scaled here: x1.25 on Hard, x0.6 on Easy. The lethal-hit guard
 * (no single hit above 60% of max health after armor) runs in {@code PlayerLethalGuardMixin}.
 */
public final class BossDamage {
	private BossDamage() {}

	public static final ResourceKey<DamageType> BOSS_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE,
		Identifier.fromNamespaceAndPath("elsewhere", "boss_attack"));
	public static final float LETHAL_CAP = 0.6F;
	/** Overpower guard: per-hit damage above 24 is halved past 24, before armor. */
	public static final float OVERPOWER_KNEE = 24F;

	public static float overpowerGuard(float dmg) {
		return dmg > OVERPOWER_KNEE ? OVERPOWER_KNEE + (dmg - OVERPOWER_KNEE) / 2F : dmg;
	}

	public static float scaled(ServerLevel level, float raw) {
		Difficulty d = level.getDifficulty();
		return switch (d) {
			case PEACEFUL -> 0F;
			case EASY -> raw * 0.6F;
			case HARD -> raw * 1.25F;
			default -> raw;
		};
	}

	public static DamageSource source(ServerLevel level, Entity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BOSS_ATTACK), attacker);
	}

	/** Deal a scripted boss hit of raw (Normal-difficulty) damage. */
	public static boolean hit(ServerLevel level, Entity boss, LivingEntity target, float raw) {
		return target.hurtServer(level, source(level, boss), scaled(level, raw));
	}

	/** True if this damage came from a boss (directly or through its projectile or a scripted attack). */
	public static boolean fromBoss(DamageSource source) {
		return source.is(BOSS_ATTACK) || source.getEntity() instanceof BossEntity || source.getDirectEntity() instanceof BossEntity;
	}

	/** Lethal-hit guard applied to already armor-reduced damage. */
	public static float lethalGuard(Player player, DamageSource source, float afterArmor) {
		if (!fromBoss(source)) return afterArmor;
		return Math.min(afterArmor, player.getMaxHealth() * LETHAL_CAP);
	}
}
