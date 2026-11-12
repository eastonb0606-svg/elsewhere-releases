package dev.elsewhere.petalwind;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Blossom Breeze: a breeze wrapped in a petal vortex. Vanilla wind charges plus a "petal gust" that blinds briefly. HP 36. */
public class BlossomBreeze extends Breeze {
	public static final byte EVENT_GUST = 62;
	public final AnimationState gustAnimation = new AnimationState();
	private int gustCooldown = 100;

	public BlossomBreeze(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createBlossomAttributes() {
		return Breeze.createAttributes().add(Attributes.MAX_HEALTH, 36.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (target != null && --gustCooldown <= 0 && this.distanceTo(target) < 6.0) {
			gustCooldown = 140 + this.random.nextInt(60);
			level.broadcastEntityEvent(this, EVENT_GUST);
			level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1, getZ(), 60, 2.5, 1.2, 2.5, 0.2);
			level.playSound(null, blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.6F);
			for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(5))) {
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
			}
		}
		if (this.tickCount % 3 == 0) level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1.0, getZ(), 1, 0.6, 0.6, 0.6, 0.02);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_GUST) gustAnimation.start(this.tickCount);
		else super.handleEntityEvent(id);
	}
}
