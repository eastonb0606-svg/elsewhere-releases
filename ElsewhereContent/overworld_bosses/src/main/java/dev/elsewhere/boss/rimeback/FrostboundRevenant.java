package dev.elsewhere.boss.rimeback;

import dev.elsewhere.overworld_bosses.OwbEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

/**
 * Frostbound Revenant (BOSSES.md supporting mobs): a stray with blue-ice shoulder plates. Slowness arrows; shatters
 * into 3 frost shards on death. Drops packed ice and arrows.
 */
public class FrostboundRevenant extends Stray {
	public FrostboundRevenant(EntityType<? extends FrostboundRevenant> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ARMOR, 6.0);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		super.populateDefaultEquipmentSlots(random, difficulty);
		ItemStack plates = new ItemStack(Items.LEATHER_CHESTPLATE);
		plates.set(DataComponents.DYED_COLOR, new DyedItemColor(0x7FD3F5));
		setItemSlot(EquipmentSlot.CHEST, plates);
		setDropChance(EquipmentSlot.CHEST, 0.0F);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel sl) {
			for (int i = 0; i < 3; i++) {
				FrostShard s = new FrostShard(OwbEntities.FROST_SHARD, sl);
				s.setOwner(this);
				s.setPos(getX(), getY() + 1.2, getZ());
				double a = Math.PI * 2 * i / 3;
				s.shoot(Math.cos(a), 0.3, Math.sin(a), 0.6F, 4.0F);
				sl.addFreshEntity(s);
			}
		}
	}
}
