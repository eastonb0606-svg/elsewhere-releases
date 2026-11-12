package dev.elsewhere.gear3.s3.totems;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bramble Totem (Greenhaze, Swarm, G2): a fern-crowned bramble post. 30 HP. Every 2 s it lashes up to 3 mobs within
 * 5 blocks (nearest first, line of sight) for 2 minion damage and Slowness I for 1 s.
 * <p>The gag: the post is thick with haze pollen, so every 4th mob it lashes sneezes (panda sneeze + sneeze puffs).
 * Purely cosmetic: no damage, no effect, no randomness (a per-totem counter, so it is testable).
 */
public class BrambleTotem extends TotemEntity {
	public static final double HP = 30, ARMOR = 0, DAMAGE = 2, RANGE = 5;
	public static final int INTERVAL = 40, MAX_TARGETS = 3, SLOW_TICKS = 20, SNEEZE_EVERY = 4;

	private int cooldown = 20;
	private int lashed;
	/** Self-test: mobs hit by the last lash, and sneezes so far. */
	public int lastLashCount, sneezes;

	public BrambleTotem(EntityType<? extends BrambleTotem> type, Level level) {
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
		return SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES;
	}

	@Override
	protected void totemTick(ServerLevel level, ServerPlayer owner) {
		if (--cooldown > 0) return;
		cooldown = INTERVAL;
		lash(level, owner);
	}

	/** One lash (public for the self-test). Returns the number of mobs hit. */
	public int lash(ServerLevel level, ServerPlayer owner) {
		List<LivingEntity> targets = targetsInRange(level, owner, RANGE, MAX_TARGETS);
		int hit = 0;
		Vec3 from = new Vec3(getX(), getY() + 0.9, getZ());
		for (LivingEntity t : targets) {
			if (!hitTarget(level, owner, t, DAMAGE)) continue;
			hit++;
			t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0), owner);
			// a thorny whip-crack along the lash (a few server particles, once per hit)
			Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
			for (int i = 1; i <= 4; i++) {
				Vec3 p = from.lerp(to, i / 5.0);
				level.sendParticles(ParticleTypes.COMPOSTER, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.1);
			if (++lashed % SNEEZE_EVERY == 0) sneeze(level, t);
		}
		lastLashCount = hit;
		if (hit > 0) {
			strikes += hit;
			pulse();
			level.playSound(null, getX(), getY() + 0.8, getZ(), SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.NEUTRAL, 0.9F, 0.7F + 0.1F * hit);
			level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 1.1, getZ(), 6, 0.3, 0.2, 0.3, 0.0);
		}
		return hit;
	}

	/** Haze pollen: the mob sneezes (cosmetic). */
	private void sneeze(ServerLevel level, LivingEntity t) {
		sneezes++;
		Vec3 look = t.getLookAngle();
		double x = t.getX() + look.x * 0.4, y = t.getEyeY() - 0.1, z = t.getZ() + look.z * 0.4;
		level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 8, look.x * 0.15 + 0.05, 0.05, look.z * 0.15 + 0.05, 0.03);
		level.playSound(null, t.getX(), t.getEyeY(), t.getZ(), SoundEvents.PANDA_SNEEZE, SoundSource.NEUTRAL, 0.9F, 1.15F + (sneezes % 3) * 0.12F);
	}
}
