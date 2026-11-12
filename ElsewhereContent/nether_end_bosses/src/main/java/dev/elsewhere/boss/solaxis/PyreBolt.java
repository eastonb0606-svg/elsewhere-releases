package dev.elsewhere.boss.solaxis;

import dev.elsewhere.nether_end_bosses.BossFireball;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** {@code elsewhere:pyre_bolt}: the small fireball each rod fires in a Rod Volley (6 raw + fire). */
public class PyreBolt extends BossFireball {
	private float damage = 6.0F;

	public PyreBolt(EntityType<? extends PyreBolt> type, Level level) {
		super(type, level);
	}

	public PyreBolt(EntityType<? extends PyreBolt> type, LivingEntity owner, Vec3 dir, Level level) {
		super(type, owner, dir, level);
		this.accelerationPower = 0.12;
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
		return 3;
	}

}
