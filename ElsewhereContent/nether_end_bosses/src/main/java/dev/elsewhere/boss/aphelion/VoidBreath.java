package dev.elsewhere.boss.aphelion;

import dev.elsewhere.nether_end_bosses.BossFireball;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** {@code elsewhere:void_breath}: falls to the ground and leaves a 6-second pool: 6 damage per 0.5 s while you stand in it (step out). */
public class VoidBreath extends BossFireball {
	private float damage = 6.0F;

	public VoidBreath(EntityType<? extends VoidBreath> type, Level level) {
		super(type, level);
		this.setItem(new ItemStack(AphelionContent.VOID_BREATH_ITEM));
	}

	public VoidBreath(EntityType<? extends VoidBreath> type, LivingEntity owner, Vec3 dir, Level level) {
		super(type, owner, dir, level);
		this.setItem(new ItemStack(AphelionContent.VOID_BREATH_ITEM));
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	@Override protected float rawDamage() { return damage; }
	@Override protected ParticleOptions getTrailParticle() { return ParticleTypes.PORTAL; }

	@Override
	protected void onImpact(ServerLevel level, HitResult result) {
		if (this.getOwner() instanceof ElsewhereBoss boss) AphelionContent.addPool(level, boss, this.position(), damage);
	}
}
