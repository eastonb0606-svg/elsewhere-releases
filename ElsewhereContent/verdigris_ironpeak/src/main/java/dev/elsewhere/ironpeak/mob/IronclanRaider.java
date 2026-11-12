package dev.elsewhere.ironpeak.mob;

import dev.elsewhere.ironpeak.Ironpeak;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Ironclan Raider: a giant mountain warrior in fur and iron, patrolling the terraces in threes. It tests your rank:
 * players of clan rank 5 or more are bowed to instead of attacked (unless they strike first).
 */
public class IronclanRaider extends Monster {
	private static final EntityDataAccessor<Integer> BOWING = SynchedEntityData.defineId(IronclanRaider.class, EntityDataSerializers.INT);
	public final AnimationState bowAnim = new AnimationState();
	public int attackAnim;
	/** Raiders called into a boss fight attack regardless of rank. */
	public boolean summoned;

	public IronclanRaider(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 44).add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ARMOR, 6).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BOWING, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (accessor == BOWING && level().isClientSide()) {
			if (entityData.get(BOWING) > 0) bowAnim.startIfStopped(tickCount);
			else bowAnim.stop();
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
		goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, IronclanRaider.class).setAlertOthers());
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> !honours(target)));
	}

	/** Rank 5+ (Hrolf beaten) earns a bow instead of a fight. */
	public boolean honours(LivingEntity target) {
		return !summoned && target instanceof ServerPlayer p && getLastHurtByMob() != p
			&& (Ironpeak.rank(p) >= 5 || p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(dev.elsewhere.ironpeak.IronpeakItems.SUMMIT_CROWN));
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		setDropChance(EquipmentSlot.MAINHAND, 0.05F);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (getMainHandItem().isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		if (getTarget() != null && honours(getTarget())) setTarget(null);
		int bow = entityData.get(BOWING);
		if (bow > 0) {
			entityData.set(BOWING, bow - 1);
			getNavigation().stop();
		} else if (tickCount % 20 == 0 && getTarget() == null) {
			Player near = level.getNearestPlayer(this, 5);
			if (near != null && honours(near)) {
				entityData.set(BOWING, 40);
				getLookControl().setLookAt(near);
			}
		}
		if (attackAnim > 0) attackAnim--;
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
		else super.handleEntityEvent(id);
	}

	public boolean isBowing() {
		return entityData.get(BOWING) > 0;
	}
}
