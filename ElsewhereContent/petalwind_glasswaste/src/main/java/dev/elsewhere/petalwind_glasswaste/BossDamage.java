package dev.elsewhere.petalwind_glasswaste;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Scripted boss attacks use their own damage type ({@code elsewhere:pg_boss_attack}, scaling "never") so the numbers in
 * BOSSES.md apply exactly: raw on Normal, x1.25 on Hard, x0.6 on Easy (vanilla's mob scaling would be different).
 */
public final class BossDamage {
	private BossDamage() {}

	public static final ResourceKey<DamageType> BOSS_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE, Reg.id("pg_boss_attack"));

	public static DamageSource source(ServerLevel level, Entity boss) {
		return Dmg.of(level, BOSS_ATTACK, boss);
	}

	public static boolean hit(ServerLevel level, Entity boss, LivingEntity target, float raw) {
		return target.hurtServer(level, source(level, boss), Dmg.scaled(level, raw));
	}
}
