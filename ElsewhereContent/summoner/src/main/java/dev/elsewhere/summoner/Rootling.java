package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.stub.summoner.BossCheck;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Rootling (Rootcall Staff): knee-high bark sprite; 4-damage melee every 1 s that roots non-boss targets. */
public class Rootling extends MinionEntity {
	/** target UUID -> game time of its last root (once per 4 s per target). */
	private static final Map<UUID, Long> LAST_ROOT = new HashMap<>();
	/** Roots applied (self-test). */
	public static int rootsApplied;

	public Rootling(EntityType<? extends Rootling> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.ROOTLING_HP)
			.add(Attributes.ARMOR, GearStats.ROOTLING_ARMOR)
			.add(Attributes.MOVEMENT_SPEED, GearStats.ROOTLING_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ATTACK_DAMAGE, GearStats.ROOTLING_DAMAGE);
	}

	@Override
	public boolean isFlyingMinion() {
		return false;
	}

	@Override
	public double baseDamage() {
		return GearStats.ROOTLING_DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return GearStats.ROOTLING_ATTACK_TICKS;
	}

	@Override
	public double attackReach() {
		return 1.6;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		if (!hitTarget(level, owner, target, baseDamage())) return;
		level.playSound(null, blockPosition(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.8F, 1.3F);
		if (BossCheck.isBoss(target)) return;
		long now = level.getGameTime();
		Long last = LAST_ROOT.get(target.getUUID());
		if (last == null || now - last >= GearStats.ROOTLING_ROOT_PER_TARGET_TICKS) {
			LAST_ROOT.put(target.getUUID(), now);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GearStats.ROOTLING_ROOT_TICKS, GearStats.ROOTLING_ROOT_AMPLIFIER), owner);
			rootsApplied++;
			if (LAST_ROOT.size() > 512) LAST_ROOT.entrySet().removeIf(e -> now - e.getValue() > GearStats.ROOTLING_ROOT_PER_TARGET_TICKS);
		}
	}
}
