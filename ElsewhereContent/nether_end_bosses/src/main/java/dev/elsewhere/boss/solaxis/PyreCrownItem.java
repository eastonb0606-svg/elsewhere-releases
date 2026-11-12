package dev.elsewhere.boss.solaxis;

import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.Reg;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Pyre Crown (helmet): fire immunity while worn, +2 attack damage while you are on fire. */
public class PyreCrownItem extends Item {
	public static final Identifier ON_FIRE_BONUS = Reg.id("pyre_crown_on_fire");

	public PyreCrownItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!(owner instanceof LivingEntity living)) return;
		AttributeInstance attack = living.getAttribute(Attributes.ATTACK_DAMAGE);
		if (slot != EquipmentSlot.HEAD) {
			if (attack != null && living.getItemBySlot(EquipmentSlot.HEAD).getItem() != this) attack.removeModifier(ON_FIRE_BONUS);
			return;
		}
		if (attack != null) {
			if (living.isOnFire()) {
				attack.addOrUpdateTransientModifier(new AttributeModifier(ON_FIRE_BONUS, GearStats.PYRE_CROWN_ON_FIRE_DAMAGE, AttributeModifier.Operation.ADD_VALUE));
			} else {
				attack.removeModifier(ON_FIRE_BONUS);
			}
		}
	}
}
