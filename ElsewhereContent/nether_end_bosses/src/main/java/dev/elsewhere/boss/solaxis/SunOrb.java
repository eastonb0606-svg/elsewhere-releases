package dev.elsewhere.boss.solaxis;

import dev.elsewhere.nether_end_bosses.BossFireball;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** {@code elsewhere:sun_orb}: the Eclipse Core's large fireball (28 raw). Punch it back (vanilla deflection) for 30 to Solaxis. */
public class SunOrb extends BossFireball {
	public static final float REFLECTED_DAMAGE = 30.0F;
	private float damage = 28.0F;

	public SunOrb(EntityType<? extends SunOrb> type, Level level) {
		super(type, level);
		this.setItem(new ItemStack(SolaxisContent.SUN_ORB_ITEM));
	}

	public SunOrb(EntityType<? extends SunOrb> type, LivingEntity owner, Vec3 dir, Level level) {
		super(type, owner, dir, level);
		this.accelerationPower = 0.06;
		this.setItem(new ItemStack(SolaxisContent.SUN_ORB_ITEM));
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	@Override
	protected float rawDamage() {
		return damage;
	}

	@Override
	protected ResourceKey<DamageType> damageType() {
		return StubFramework.BOSS_FIRE;
	}

	@Override
	protected int fireSeconds() {
		return 4;
	}

	@Override
	protected float reflectedDamage() {
		return REFLECTED_DAMAGE;
	}

	@Override
	protected void onImpact(ServerLevel level, HitResult result) {
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.3, 0.3, 0.3, 0);
		level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 30, 0.6, 0.6, 0.6, 0.08);
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 1.2F);
	}

}
