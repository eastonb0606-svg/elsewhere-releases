package dev.elsewhere.boss.mournmaw;

import dev.elsewhere.nether_end_bosses.BossFireball;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** {@code elsewhere:soul_fireball}: 24 raw + fire, deflectable (punch it back for 30 to Mournmaw). */
public class SoulFireball extends BossFireball {
	private float damage = 24.0F;

	public SoulFireball(EntityType<? extends SoulFireball> type, Level level) {
		super(type, level);
		this.setItem(new ItemStack(MournmawContent.SOUL_FIREBALL_ITEM));
	}

	public SoulFireball(EntityType<? extends SoulFireball> type, LivingEntity owner, Vec3 dir, Level level) {
		super(type, owner, dir, level);
		this.accelerationPower = 0.07;
		this.setItem(new ItemStack(MournmawContent.SOUL_FIREBALL_ITEM));
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	@Override protected float rawDamage() { return damage; }
	@Override protected ResourceKey<DamageType> damageType() { return StubFramework.BOSS_FIRE; }
	@Override protected int fireSeconds() { return 4; }
	@Override protected float reflectedDamage() { return 30.0F; }
	@Override protected ParticleOptions getTrailParticle() { return ParticleTypes.SOUL_FIRE_FLAME; }
}
