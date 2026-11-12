package dev.elsewhere.boss.vorlagh;

import dev.elsewhere.overworld_bosses.GearStats;
import dev.elsewhere.overworld_bosses.OwbItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** {@code elsewhere:ink_glob}: Vorlagh's ink (and the Kraken Ink Bomb): bursts into a Blindness cloud. */
public class InkGlob extends ThrowableItemProjectile {
	public InkGlob(EntityType<? extends InkGlob> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item getDefaultItem() {
		return OwbItems.KRAKEN_INK_BOMB;
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel sl && !isRemoved()) {
			AreaEffectCloud cloud = new AreaEffectCloud(sl, getX(), getY(), getZ());
			if (getOwner() instanceof LivingEntity le) cloud.setOwner(le);
			cloud.setRadius(GearStats.INK_CLOUD_RADIUS);
			cloud.setDuration(GearStats.INK_CLOUD_TICKS);
			cloud.setRadiusPerTick(-0.01F);
			cloud.setCustomParticle(ParticleTypes.SQUID_INK);
			cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, GearStats.INK_BLIND_TICKS, 0));
			sl.addFreshEntity(cloud);
			sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY(), getZ(), 40, 1.0, 0.5, 1.0, 0.1);
			discard();
		}
	}
}
