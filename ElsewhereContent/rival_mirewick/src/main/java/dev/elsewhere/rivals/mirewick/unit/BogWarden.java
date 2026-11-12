package dev.elsewhere.rivals.mirewick.unit;

import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Bog-Warden: 30 HP, iron helm, tower shield (blocks the front 90+ percent, an axe disables it as for any shield), speed 0.22.
 * Shield shove every 6 s: the shield comes up for half a second (the telegraph), then knockback and raw 4.
 */
public class BogWarden extends KingdomSoldier {
	public static final int SHOVE_COOLDOWN = 120, SHOVE_WINDUP = 10;
	public int shoveCd = 60, shoveWind, shoves, holds;

	public BogWarden(EntityType<? extends KingdomSoldier> type, Level level) {
		super(type, level);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (shoveCd > 0) shoveCd--;
		LivingEntity t = getTarget();
		if (shoveWind > 0) {
			raiseShield(4);
			if (--shoveWind == 0) shove(level, t);
			return;
		}
		if (tickCount % 10 == 0) holdChoke(level);
		if (t != null && shoveCd == 0 && distanceToSqr(t) < 2.8 * 2.8 && hasShield()) {
			shoveWind = SHOVE_WINDUP;
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), 4, 0.3, 0.2, 0.3, 0.02);
			level.playSound(null, blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.6F, 1.4F);
		}
	}

	/** tower_shield_hold: with enemies in view but none within 4 blocks the Warden plants himself with the shield up. */
	void holdChoke(ServerLevel level) {
		var sq = squad();
		if (sq == null || !hasShield()) return;
		var es = sq.enemies();
		if (es.isEmpty()) return;
		double d = Double.MAX_VALUE;
		for (LivingEntity e : es) d = Math.min(d, distanceToSqr(e));
		if (d < 16 * 16 && d > 4 * 4) {
			raiseShield(12);
			getNavigation().stop();
			holds++;
		}
	}

	void shove(ServerLevel level, LivingEntity t) {
		shoveCd = SHOVE_COOLDOWN;
		if (t == null || !t.isAlive() || distanceToSqr(t) > 3.4 * 3.4) return;
		shoves++;
		t.hurtServer(level, damageSources().mobAttack(this), 4.0F);
		t.knockback(1.2, -(t.getX() - getX()), -(t.getZ() - getZ()), damageSources().mobAttack(this), 0.0F);
		level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
	}
}
