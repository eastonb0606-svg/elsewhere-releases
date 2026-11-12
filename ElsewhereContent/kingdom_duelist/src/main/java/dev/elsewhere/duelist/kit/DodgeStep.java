package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.boss.HumanBoss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * X1 {@code DodgeStep}: a sidestep or roll with i-frames and an afterimage in the boss's own telegraph colour. Nothing
 * dodges unless its boss calls this, and every number (i-frames, distance, cooldown) is the caller's: see {@link Params}.
 */
public final class DodgeStep {
	private DodgeStep() {}

	/** One boss's dodge: i-frame ticks, roughly how many blocks it travels, and the cooldown in ticks. */
	public record Params(int iframes, double distance, int cooldown) {}

	/**
	 * Dodges sideways relative to {@code from} (the threat). Returns true if it happened (the move was off cooldown and the
	 * boss is on the ground and free).
	 */
	public static boolean dodge(ServerLevel level, HumanBoss boss, Vec3 from, Params p) {
		if (!boss.ready("dodge") || !boss.onGround() || boss.isStaggered() || boss.inLastStand()) return false;
		Vec3 away = boss.position().subtract(from).multiply(1, 0, 1);
		if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
		Vec3 side = new Vec3(-away.z, 0, away.x).normalize();
		if (boss.getRandom().nextBoolean()) side = side.scale(-1);
		// prefer the side that has room
		if (!roomFor(level, boss, side)) side = side.scale(-1);
		if (!roomFor(level, boss, side)) return false;
		boss.setCooldown("dodge", p.cooldown());
		boss.startDodge(p.iframes());
		double v = p.distance() * 0.25;
		var puff = new net.minecraft.core.particles.DustParticleOptions(boss.style().colour(), 1.4F);
		Vec3 start = boss.position();
		boss.setDeltaMovement(side.x * v, 0.12, side.z * v);
		boss.needsSync = true;
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1F, 1.4F);
		for (int i = 0; i < p.iframes(); i += 2) {
			int k = i;
			Timers.after(level.getServer(), i, () -> level.sendParticles(puff, boss.getX(), boss.getY() + 1.0, boss.getZ(), 4, 0.2, 0.5, 0.2, 0.01));
		}
		level.sendParticles(puff, start.x, start.y + 1, start.z, 8, 0.2, 0.5, 0.2, 0.02);
		boss.bump("dodge");
		return true;
	}

	private static boolean roomFor(ServerLevel level, HumanBoss boss, Vec3 dir) {
		var box = boss.getBoundingBox().move(dir.scale(2.0));
		return level.noCollision(boss, box);
	}
}
