package dev.elsewhere.glasswaste;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Sun Mote: a floating orb inside spinning glass rings. Shoots small fireballs, then bursts in a blinding flash. HP 14. */
public class SunMote extends Blaze {
	private boolean burst;

	public SunMote(EntityType<? extends Blaze> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createMoteAttributes() {
		return Blaze.createAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.ATTACK_DAMAGE, 4.0);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && !burst && this.getHealth() <= 5.0F) flash(level);
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && !burst) flash(level);
		super.die(source);
	}

	/** Bursts in a flash: Blindness 3 s for players within 5 blocks. */
	public void flash(ServerLevel level) {
		burst = true;
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE0A0), getX(), getY() + 0.4, getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 30, 0.6, 0.6, 0.6, 0.2);
		level.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5F, 1.4F);
		for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(5))) {
			p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this);
		}
	}
}
