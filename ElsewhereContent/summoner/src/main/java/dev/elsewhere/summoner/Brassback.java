package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionTargeting;
import dev.elsewhere.stub.summoner.BossCheck;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * Brassback (Brassback Key): a riveted brass crab. Pinch 2 damage every 1 s; every 5 s taunts non-boss mobs within 8
 * blocks (they target it for 3 s); heals 1 HP/s while standing in water.
 */
public class Brassback extends MinionEntity {
	/** taunted mob UUID -> game time the taunt ends. */
	private final Map<UUID, Long> taunted = new HashMap<>();
	public int lastTauntCount;

	public Brassback(EntityType<? extends Brassback> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.BRASSBACK_HP)
			.add(Attributes.ARMOR, GearStats.BRASSBACK_ARMOR)
			.add(Attributes.MOVEMENT_SPEED, GearStats.BRASSBACK_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
			.add(Attributes.ATTACK_DAMAGE, GearStats.BRASSBACK_DAMAGE);
	}

	@Override
	public boolean isFlyingMinion() {
		return false;
	}

	@Override
	public double baseDamage() {
		return GearStats.BRASSBACK_DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return GearStats.BRASSBACK_ATTACK_TICKS;
	}

	@Override
	public double attackReach() {
		return 1.8;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		if (hitTarget(level, owner, target, baseDamage())) {
			level.playSound(null, blockPosition(), SoundEvents.COPPER_HIT, SoundSource.NEUTRAL, 1.0F, 1.4F);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl && this.tickCount % GearStats.BRASSBACK_TAUNT_EVERY_TICKS == 0 && this.tickCount > 0) {
			ServerPlayer owner = ownerPlayer();
			if (owner != null) taunt(sl, owner);
		}
		if (level() instanceof ServerLevel sl && !taunted.isEmpty()) {
			long now = sl.getGameTime();
			taunted.entrySet().removeIf(e -> {
				if (now < e.getValue()) return false;
				if (sl.getEntity(e.getKey()) instanceof Mob m && m.getTarget() == this) m.setTarget(null);
				return true;
			});
		}
	}

	/** Every non-boss mob within 8 blocks that may be fought targets this crab for 3 s. */
	public int taunt(ServerLevel level, ServerPlayer owner) {
		double r = GearStats.BRASSBACK_TAUNT_RANGE;
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r),
			m -> m.distanceTo(this) <= r && MinionTargeting.isHostile(m) && !BossCheck.isBoss(m) && !MinionTargeting.isProtected(owner, m) && m.isAlive());
		long until = level.getGameTime() + GearStats.BRASSBACK_TAUNT_TICKS;
		for (Mob m : mobs) {
			m.setTarget(this);
			taunted.put(m.getUUID(), until);
		}
		lastTauntCount = mobs.size();
		if (!mobs.isEmpty()) {
			level.playSound(null, blockPosition(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.NEUTRAL, 1.0F, 0.7F);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3, getZ(), 1, 0, 0, 0, 0);
		}
		return mobs.size();
	}

	@Override
	protected void minionSecond(ServerLevel level, ServerPlayer owner) {
		if (this.isInWater() && getHealth() < getMaxHealth()) heal(GearStats.BRASSBACK_WATER_HEAL_PER_S);
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return true;
	}
}
