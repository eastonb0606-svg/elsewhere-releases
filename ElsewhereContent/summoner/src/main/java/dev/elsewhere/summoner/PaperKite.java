package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionEntity;
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
import net.minecraft.world.phys.Vec3;

/**
 * Paper Kite (Kitestring Staff): a flying silk kite that fires a small wind bolt (4 damage, knockback 0.5, range 16)
 * every 1.5 s. The bolt is an instant hit with a gust trail: it never touches blocks (no doors, buttons or bells).
 */
public class PaperKite extends MinionEntity {
	public PaperKite(EntityType<? extends PaperKite> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.KITE_HP)
			.add(Attributes.ARMOR, GearStats.KITE_ARMOR)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, GearStats.KITE_FLY_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	public boolean isFlyingMinion() {
		return true;
	}

	@Override
	public boolean isStarterMinion() {
		return true;
	}

	@Override
	public double baseDamage() {
		return GearStats.KITE_DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return GearStats.KITE_ATTACK_TICKS;
	}

	@Override
	public double attackReach() {
		return GearStats.KITE_RANGE;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
		Vec3 d = to.subtract(from);
		int steps = (int) Math.max(2, d.length() * 2);
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(d.scale(i / (double) steps));
			level.sendParticles(ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 0.6F, 1.6F);
		if (hitTarget(level, owner, target, baseDamage())) {
			Vec3 h = new Vec3(d.x, 0, d.z);
			if (h.lengthSqr() > 1.0E-4) {
				h = h.normalize();
				target.knockback(GearStats.KITE_KNOCKBACK, -h.x, -h.z, dev.elsewhere.minion.MinionApi.damageSource(level, this, owner), (float) baseDamage());
			}
		}
	}
}
