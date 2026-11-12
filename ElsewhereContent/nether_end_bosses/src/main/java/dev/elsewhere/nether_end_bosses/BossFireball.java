package dev.elsewhere.nether_end_bosses;

import dev.elsewhere.stub.nether_end_bosses.BossPart;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.Guards;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base for this module's boss fireballs: raw Normal damage through the boss damage types, never hits the boss side
 * unless a player deflected it, then it hits the boss for {@link #reflectedDamage()}.
 */
public abstract class BossFireball extends Fireball {
	protected BossFireball(EntityType<? extends BossFireball> type, Level level) {
		super(type, level);
	}

	protected BossFireball(EntityType<? extends BossFireball> type, LivingEntity owner, Vec3 direction, Level level) {
		super(type, owner, direction, level);
	}

	protected abstract float rawDamage();

	protected ResourceKey<DamageType> damageType() {
		return StubFramework.BOSS_PROJECTILE;
	}

	protected int fireSeconds() {
		return 0;
	}

	/** Damage to the boss when a player sent it back. 0 = cannot be reflected for damage. */
	protected float reflectedDamage() {
		return 0;
	}

	protected boolean deflectedByPlayer() {
		return this.getOwner() instanceof Player;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (!super.canHitEntity(entity)) return false;
		boolean bossSide = entity instanceof ElsewhereBoss || entity instanceof BossPart || entity.entityTags().contains(Guards.MINION_TAG);
		return deflectedByPlayer() ? !(entity instanceof Player) : !bossSide;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!(this.level() instanceof ServerLevel level)) return;
		Entity target = hit.getEntity();
		if (deflectedByPlayer()) {
			if (reflectedDamage() > 0) target.hurtServer(level, this.damageSources().fireball(this, this.getOwner()), reflectedDamage());
			return;
		}
		ElsewhereBoss boss = this.getOwner() instanceof ElsewhereBoss b ? b : null;
		float raw = rawDamage();
		if (boss != null) {
			target.hurtServer(level, boss.source(damageType(), this), ElsewhereBoss.scaled(level, raw));
		} else {
			target.hurtServer(level, this.damageSources().fireball(this, this.getOwner()), raw);
		}
		if (fireSeconds() > 0) target.igniteForSeconds(fireSeconds());
		afterHitEntity(level, target, boss);
	}

	protected void afterHitEntity(ServerLevel level, Entity target, @Nullable ElsewhereBoss boss) {
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (this.level() instanceof ServerLevel level) {
			onImpact(level, result);
			this.discard();
		}
	}

	protected void onImpact(ServerLevel level, HitResult result) {
	}

	@Override
	protected @Nullable ParticleOptions getTrailParticle() {
		return super.getTrailParticle();
	}
}
