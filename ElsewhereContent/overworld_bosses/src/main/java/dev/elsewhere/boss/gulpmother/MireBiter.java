package dev.elsewhere.boss.gulpmother;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Mire Biter (BOSSES.md supporting mobs): a small slime with a lily-pad hat that hatches from Gulpmother's
 * frogspawn, latches on (its hits slow, Slowness I) and never splits. Drops slimeballs.
 */
public class MireBiter extends Slime {
	public MireBiter(EntityType<? extends MireBiter> type, Level level) {
		super(type, level);
		setSize(1, true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.MOVEMENT_SPEED, 0.35);
	}

	@Override
	public void setSize(int size, boolean updateHealth) {
		super.setSize(1, updateHealth);
		if (updateHealth) {
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(8.0);
			getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0);
			setHealth(getMaxHealth());
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
		SpawnGroupData d = super.finalizeSpawn(level, difficulty, reason, data);
		setSize(1, true);
		return d;
	}

	@Override
	protected boolean canDealDamage() {
		return isEffectiveAi();
	}

	@Override
	protected void dealDamage(LivingEntity target) {
		float before = target.getHealth();
		super.dealDamage(target);
		if (target.getHealth() < before) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), this);
	}

	@Override
	public void remove(RemovalReason reason) {
		// Size 1: never splits; skip Slime's split logic entirely.
		this.setRemoved(reason);
	}

	public boolean canSpawnIn(ServerLevel level) {
		return true;
	}
}
