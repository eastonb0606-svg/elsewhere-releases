package dev.elsewhere.boss.kit;

import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Per-boss openings after a big attack (DECISIONS {@code bal-boss-punish-window}: different per boss in length, trigger
 * and bonus, not one standard window). While open the boss takes extra damage and sparkles (a cue everyone sees, no
 * foresight gear needed). Each boss owns a {@link Tracker}: call {@link Tracker#attackFinished} when an attack ends,
 * scale incoming damage by {@link Tracker#multiplier}, and call {@link Tracker#tick} once per tick.
 */
public final class Openings {
	private Openings() {}

	/** @param ticks how long the opening lasts; @param bonus extra damage taken (0.4 = +40%). */
	public record Opening(int ticks, float bonus) {}

	private static Opening o(int ticks, float bonus) { return new Opening(ticks, bonus); }

	/** boss id to (attack that triggers it to its opening). Short quick attacks give short small openings, the long crushers give long ones. */
	private static final Map<String, Map<String, Opening>> TABLE = Map.of(
		"gulpmother", Map.of("belly_flop", o(50, 0.30F), "tongue_lash", o(24, 0.20F), "devour", o(30, 0.50F)),
		"strawgrim", Map.of("reap", o(30, 0.40F), "furrow", o(20, 0.15F)),
		"neferkhet", Map.of("staff_swing", o(40, 0.25F), "quicksand", o(70, 0.15F), "sun_lance", o(26, 0.35F)),
		"rimeback", Map.of("fist_slam", o(70, 0.35F), "glacier_hurl", o(40, 0.20F), "avalanche", o(50, 0.30F)),
		"vorlagh", Map.of("tentacle_slam", o(40, 0.50F), "grab", o(30, 0.30F)),
		"grunthar", Map.of("charge", o(50, 0.40F), "molten_hoard", o(30, 0.20F)),
		"solaxis", Map.of("supernova", o(100, 0.60F), "rod_volley", o(16, 0.15F)),
		"mournmaw", Map.of("grief_storm", o(60, 0.25F), "soul_fireball", o(14, 0.10F)),
		"ossyra", Map.of("shell_slam", o(45, 0.50F), "void_bloom", o(35, 0.20F)),
		"aphelion", Map.of("devour_dive", o(40, 0.35F), "coil", o(55, 0.25F), "collapse", o(30, 0.40F)));

	public static @Nullable Opening get(String bossId, String attackId) {
		Map<String, Opening> m = TABLE.get(bossId);
		return m == null ? null : m.get(attackId);
	}

	public static final class Tracker {
		private long until;
		private float bonus;

		public void attackFinished(ServerLevel level, LivingEntity boss, String bossId, String attackId) {
			Opening op = get(bossId, attackId);
			if (op == null) return;
			until = level.getGameTime() + op.ticks();
			bonus = op.bonus();
			level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, 0.7F);
		}

		public boolean isOpen(long now) { return now < until; }

		/** Damage multiplier right now (1.0 when closed). */
		public float multiplier(long now) { return isOpen(now) ? 1.0F + bonus : 1.0F; }

		public void tick(ServerLevel level, LivingEntity boss) {
			if (isOpen(level.getGameTime()) && level.getGameTime() % 4 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 3, boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.02);
			}
		}
	}

	/** Wind-up tell everyone hears and sees (the hint particles are foresight-only): a roar and angry sparks above the boss. */
	public static void windupTell(ServerLevel level, LivingEntity boss, boolean big) {
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), big ? SoundEvents.RAVAGER_ROAR : SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, big ? 2.5F : 1.0F, big ? 1.25F : 1.0F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, boss.getX(), boss.getY() + boss.getBbHeight() + 0.3, boss.getZ(), big ? 6 : 2, 0.5, 0.2, 0.5, 0.0);
	}
}
