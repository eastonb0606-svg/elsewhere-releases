package dev.elsewhere.verdigris_sea.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.Level;

/** Brass Guardian: a riveted, armoured guardian with a lamp eye. Vanilla guardian laser and thorns, tougher plating. */
public class BrassGuardian extends Guardian {
	public BrassGuardian(EntityType<? extends Guardian> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Guardian.createAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ARMOR, 8).add(Attributes.ATTACK_DAMAGE, 7);
	}
}
