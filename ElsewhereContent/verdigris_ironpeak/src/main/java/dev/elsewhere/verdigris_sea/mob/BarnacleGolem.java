package dev.elsewhere.verdigris_sea.mob;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Barnacle Golem: a hulking barnacle-crusted copper golem that tends the rigs. Neutral; it turns on anyone who opens
 * a chest within 10 blocks of it (see VerdigrisEvents) or hits it, and forgives after a minute.
 */
public class BarnacleGolem extends PathfinderMob {
	public int attackAnim;
	private @Nullable UUID grudge;
	private int grudgeTicks;

	public BarnacleGolem(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.ARMOR, 4).add(Attributes.FOLLOW_RANGE, 24);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	public void provoke(Player player) {
		grudge = player.getUUID();
		grudgeTicks = 1200;
		setTarget(player);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (grudgeTicks > 0 && --grudgeTicks == 0) {
			grudge = null;
			if (getLastHurtByMob() == null) setTarget(null);
		}
		if (attackAnim > 0) attackAnim--;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		attackAnim = 10;
		level.broadcastEntityEvent(this, (byte) 4);
		boolean hit = super.doHurtTarget(level, target);
		if (hit) target.setDeltaMovement(target.getDeltaMovement().add(0, 0.35, 0));
		return hit;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 4) attackAnim = 10;
		else super.handleEntityEvent(id);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("GrudgeTicks", grudgeTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		grudgeTicks = in.getIntOr("GrudgeTicks", 0);
	}

	public boolean isAngryAt(LivingEntity e) {
		return grudge != null && grudge.equals(e.getUUID());
	}
}
