package dev.elsewhere.ironpeak.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Rimeguard: a stone golem crusted with ice. Hostile; lobs packed-ice boulders (falling blocks) at targets 6-18 blocks away. */
public class Rimeguard extends Monster {
	public int attackAnim, throwAnim;
	private int throwCooldown = 60;

	public Rimeguard(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 120).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR, 8).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(7, new RandomStrollGoal(this, 0.6));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (attackAnim > 0) attackAnim--;
		if (throwAnim > 0) {
			throwAnim--;
			if (throwAnim == 6 && getTarget() != null) lob(level, getTarget());
		}
		LivingEntity t = getTarget();
		if (t != null && --throwCooldown <= 0) {
			double d = distanceTo(t);
			if (d > 6 && d < 18 && hasLineOfSight(t)) {
				throwAnim = 20;
				level.broadcastEntityEvent(this, (byte) 5);
				throwCooldown = 80 + random.nextInt(40);
			} else {
				throwCooldown = 20;
			}
		}
	}

	private void lob(ServerLevel level, LivingEntity t) {
		BlockPos start = BlockPos.containing(getX(), getY() + getBbHeight() + 0.5, getZ());
		if (!level.getBlockState(start).isAir()) return;
		FallingBlockEntity ice = FallingBlockEntity.fall(level, start, Blocks.PACKED_ICE.defaultBlockState());
		Vec3 d = t.position().subtract(position());
		double flight = Math.max(10, d.horizontalDistance() * 1.4);
		ice.setDeltaMovement(d.x / flight, 0.04 * flight / 2 + d.y / flight, d.z / flight);
		ice.setHurtsEntities(2.0F, 12);
		ice.dropItem = false;
		ice.needsSync = true;
		level.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		attackAnim = 10;
		level.broadcastEntityEvent(this, (byte) 4);
		return super.doHurtTarget(level, target);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 4) attackAnim = 10;
		else if (id == 5) throwAnim = 20;
		else super.handleEntityEvent(id);
	}
}
