package dev.elsewhere.glasswaste;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sunwright Revenant: a gold-wrapped mummy soldier, often riding a camel husk. Caravan raider: it charges with a camel
 * dash. Full gold armor and a golden sword. HP 34, damage 6.
 */
public class SunwrightRevenant extends Husk {
	private int dashCooldown = 60;

	public SunwrightRevenant(EntityType<? extends Husk> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createRevenantAttributes() {
		return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 34.0).add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
		this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
		this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
		for (EquipmentSlot slot : EquipmentSlot.values()) this.setDropChance(slot, 0.02F);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		this.setBaby(false);
		if (reason != EntitySpawnReason.SPAWN_ITEM_USE && level.getRandom().nextFloat() < 0.7F) {
			CamelHusk camel = EntityTypes.CAMEL_HUSK.create(level.getLevel(), EntitySpawnReason.JOCKEY);
			if (camel != null) {
				camel.snapTo(this.getX(), this.getY(), this.getZ());
				camel.finalizeSpawn(level, difficulty, EntitySpawnReason.JOCKEY, null);
				level.addFreshEntity(camel);
				this.startRiding(camel, true, false);
			}
		}
		return data;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (!(this.getVehicle() instanceof Camel camel) || target == null) return;
		if (dashCooldown > 0) {
			dashCooldown--;
			Vec3 to = target.position().subtract(camel.position()).multiply(1, 0, 1);
			if (to.lengthSqr() > 4) {
				Vec3 step = to.normalize().scale(0.12);
				camel.setDeltaMovement(step.x, camel.getDeltaMovement().y, step.z);
				camel.setYRot((float) (Mth.atan2(to.z, to.x) * (180F / Math.PI)) - 90.0F);
				camel.yBodyRot = camel.getYRot();
			}
			return;
		}
		double dist = camel.distanceTo(target);
		if (dist > 4 && dist < 14 && camel.onGround()) {
			Vec3 dir = target.position().subtract(camel.position()).multiply(1, 0, 1).normalize();
			camel.setDeltaMovement(dir.x * 1.3, 0.35, dir.z * 1.3);
			camel.needsSync = true;
			this.playSound(net.minecraft.sounds.SoundEvents.CAMEL_DASH, 1.0F, 1.0F);
			dashCooldown = 100;
		}
	}
}
