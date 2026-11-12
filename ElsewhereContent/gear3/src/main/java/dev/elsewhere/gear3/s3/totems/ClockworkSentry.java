package dev.elsewhere.gear3.s3.totems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Clockwork Sentry (Verdigris Sea, Sentry, G3): a verdigris-green copper tripod with a crossbow head, wound by a brass
 * key. 40 HP, armor 8. Once a second it fires a 3-damage bolt at one mob within 16 blocks it can see (keeps its target
 * while it stays valid; otherwise the owner's focus, else the nearest hostile). The bolt is instant (a streak drawn on
 * the client from the pulse); the head turns to the aimed target on the client.
 */
public class ClockworkSentry extends TotemEntity {
	public static final double HP = 40, ARMOR = 8, DAMAGE = 3, RANGE = 16;
	public static final int INTERVAL = 20;
	/** Head height of the bolt's start (blocks above the feet). */
	public static final double MUZZLE_Y = 0.85;

	private int cooldown = 10;

	public ClockworkSentry(EntityType<? extends ClockworkSentry> type, Level level) {
		super(type, level);
	}

	@Override
	public double baseDamage() {
		return DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return INTERVAL;
	}

	@Override
	public double attackReach() {
		return RANGE;
	}

	@Override
	public SoundEvent materialSound() {
		return SoundEvents.COPPER_HIT;
	}

	@Override
	protected void totemTick(ServerLevel level, ServerPlayer owner) {
		if (--cooldown > 0) return;
		cooldown = INTERVAL;
		fire(level, owner);
	}

	/** One shot (public for the self-test). True when a bolt hit. */
	public boolean fire(ServerLevel level, ServerPlayer owner) {
		LivingEntity t = acquire(level, owner, RANGE);
		aimAt(t);
		if (t == null) return false;
		if (!hitTarget(level, owner, t, DAMAGE)) return false;
		strikes++;
		pulse();
		Vec3 from = new Vec3(getX(), getY() + MUZZLE_Y, getZ());
		Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 0.7F, 1.35F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 3, 0.05, 0.05, 0.05, 0.05);
		level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 5, 0.15, 0.15, 0.15, 0.15);
		return true;
	}
}
