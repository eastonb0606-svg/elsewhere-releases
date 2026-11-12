package dev.elsewhere.palehollow.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/** Bog Wight (Resin Bog, Weeping Deeps): a drowned bog corpse. Its grip slows (Slowness II 2 s); it doesn't burn or convert. */
public class BogWight extends Zombie {
	public BogWight(final EntityType<? extends Zombie> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createWightAttributes() {
		return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.ARMOR, 3.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	public boolean canBreakDoors() {
		return false;
	}

	@Override
	public boolean doHurtTarget(final ServerLevel level, final Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity le) {
			le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), this);
		}
		return hit;
	}
}
