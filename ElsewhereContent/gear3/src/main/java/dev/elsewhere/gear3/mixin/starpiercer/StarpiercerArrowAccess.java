package dev.elsewhere.gear3.mixin.starpiercer;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Starpiercer: pierce +2 on every arrow it shoots (AbstractArrow.setPierceLevel is private). Own name so it never clashes with other modules' accessors. */
@Mixin(AbstractArrow.class)
public interface StarpiercerArrowAccess {
	@Invoker("setPierceLevel")
	void elsewhere$starpiercerPierce(byte level);
}
