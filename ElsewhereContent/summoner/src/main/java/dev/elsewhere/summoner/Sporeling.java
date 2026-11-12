package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.CallingStub;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Sporeling (Sporebloom Staff): never attacks; every 6 s heals its owner and the owner's other minions (scales with Summoner Calling). */
public class Sporeling extends MinionEntity {
	private int secondsAlive;
	/** Total healing events fired (self-test). */
	public static int pulses;

	public Sporeling(EntityType<? extends Sporeling> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.SPORELING_HP)
			.add(Attributes.ARMOR, 0)
			.add(Attributes.MOVEMENT_SPEED, GearStats.ROOTLING_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ATTACK_DAMAGE, 0);
	}

	/** Heal strength: 1/3 with no summoner pieces, rising to full at Calling 4. */
	public static double strength(int summonerCalling) {
		return 1.0 / 3.0 + (2.0 / 3.0) * Math.max(0, Math.min(4, summonerCalling)) / 4.0;
	}

	@Override
	public boolean isSupport() {
		return true;
	}

	@Override
	public boolean isFlyingMinion() {
		return false;
	}

	@Override
	public double baseDamage() {
		return 0;
	}

	@Override
	public int attackIntervalTicks() {
		return 1000000;
	}

	@Override
	public double attackReach() {
		return 0;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {}

	@Override
	protected void minionSecond(ServerLevel level, ServerPlayer owner) {
		if (++secondsAlive % GearStats.SPORELING_PERIOD_SECONDS != 0) return;
		pulse(level, owner);
	}

	/** One heal pulse (the self-test calls it directly). */
	public void pulse(ServerLevel level, ServerPlayer owner) {
		float mastery = CallingStub.mastery(owner); // 3.0 Callings: 0.33 + 0.67 M (Primary full, Unattuned 0.60); 2.1 table otherwise
		float amount = (float) (GearStats.SPORELING_HEAL * (mastery >= 0 ? 1.0 / 3 + 2.0 / 3 * mastery : strength(CallingStub.summoner(owner))));
		owner.heal(amount);
		for (MinionEntity m : MinionManager.minions(owner)) if (m != this && m.isAlive()) m.heal(amount);
		level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 0.5, getZ(), 12, 0.6, 0.4, 0.6, 0.01);
		level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6F, 0.8F);
		pulses++;
	}
}
