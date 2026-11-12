package dev.elsewhere.gear3.s3.totems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Azmar Sunglass Obelisk (Sultanate of Azmar, Sentry, G4): a sandstone-and-gold obelisk with a floating sunglass lens.
 * 50 HP. In daylight or bright light (raw light 12+ at the lens: open sky by day, or a well-lit room) it focuses a
 * sunbeam on one mob within 16 blocks: 4 damage each second, 8 each second once the beam has held the same target for
 * 3 s. In the dark it stands dormant (lens dim, no beam). Light is read once a second on its own timer.
 */
public class SunglassObelisk extends TotemEntity {
	public static final double HP = 50, ARMOR = 0, DAMAGE = 4, RAMP_DAMAGE = 8, RANGE = 16;
	public static final int INTERVAL = 20, RAMP_TICKS = 60, MIN_LIGHT = 12;
	/** Lens height above the feet (the beam's origin). */
	public static final double LENS_Y = 1.75;

	/** Self-test: when >= 0, used instead of the world light. */
	public static int testBrightness = -1;

	private int cooldown = 10;
	private @Nullable LivingEntity beamTarget;
	private int beamTicks;

	public SunglassObelisk(EntityType<? extends SunglassObelisk> type, Level level) {
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
		return SoundEvents.STONE_PLACE;
	}

	@Override
	protected void totemTick(ServerLevel level, ServerPlayer owner) {
		if (--cooldown > 0) return;
		cooldown = INTERVAL;
		burn(level, owner);
	}

	/** Light at the lens (raw: sky light minus the night / weather darkening, or block light). */
	public int light(ServerLevel level) {
		if (testBrightness >= 0) return testBrightness;
		return level.getMaxLocalRawBrightness(BlockPos.containing(getX(), getY() + LENS_Y, getZ()));
	}

	/** One beam second (public for the self-test). Returns the damage dealt (0 when dormant or idle). */
	public double burn(ServerLevel level, ServerPlayer owner) {
		boolean lit = light(level) >= MIN_LIGHT;
		boolean wasLit = isLit();
		setLit(lit);
		if (!lit) {
			if (wasLit) level.playSound(null, getX(), getY() + LENS_Y, getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.5F, 1.6F);
			beamTarget = null;
			beamTicks = 0;
			setTarget(null);
			aimAt(null);
			setRamped(false);
			return 0;
		}
		LivingEntity t = acquire(level, owner, RANGE);
		if (t != beamTarget) {
			beamTarget = t;
			beamTicks = 0;
		}
		aimAt(t);
		if (t == null) {
			setRamped(false);
			return 0;
		}
		boolean ramped = beamTicks >= RAMP_TICKS;
		setRamped(ramped);
		double dmg = ramped ? RAMP_DAMAGE : DAMAGE;
		beamTicks += INTERVAL;
		if (!hitTarget(level, owner, t, dmg)) return 0;
		strikes++;
		pulse();
		level.sendParticles(ramped ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(),
			ramped ? 6 : 3, 0.2, 0.25, 0.2, 0.01);
		level.playSound(null, t.getX(), t.getY() + 1, t.getZ(), ramped ? SoundEvents.BEACON_AMBIENT : SoundEvents.AMETHYST_BLOCK_RESONATE,
			SoundSource.NEUTRAL, 0.6F, ramped ? 1.8F : 1.3F);
		return dmg;
	}

	/** Seconds the beam has held its current target (self-test / tooltip). */
	public int beamTicks() {
		return beamTicks;
	}
}
