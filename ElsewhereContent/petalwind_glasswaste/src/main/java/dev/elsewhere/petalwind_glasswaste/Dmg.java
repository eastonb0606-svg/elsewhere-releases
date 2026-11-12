package dev.elsewhere.petalwind_glasswaste;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/** Damage-source and ray helpers shared by items, mobs and bosses. */
public final class Dmg {
	private Dmg() {}

	public static DamageSource of(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), attacker);
	}

	public static DamageSource fire(ServerLevel level, @Nullable Entity attacker) {
		return of(level, DamageTypes.IN_FIRE, attacker);
	}

	/** Scripted boss/mob attack damage scaled by difficulty (BOSSES.md: x1.25 Hard, x0.6 Easy, raw on Normal). */
	public static float scaled(ServerLevel level, float raw) {
		return switch (level.getDifficulty()) {
			case PEACEFUL -> 0.0F;
			case EASY -> raw * 0.6F;
			case NORMAL -> raw;
			case HARD -> raw * 1.25F;
		};
	}

	/** First living entity along the look ray of {@code from} within range, stopping at blocks. */
	public static @Nullable LivingEntity lookTarget(LivingEntity from, double range) {
		Vec3 eye = from.getEyePosition();
		Vec3 end = eye.add(from.getLookAngle().scale(range));
		HitResult block = from.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from));
		if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
		AABB box = from.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(from, eye, end, box,
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != from && !(e instanceof Player p && p.isCreative()), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
	}
}
