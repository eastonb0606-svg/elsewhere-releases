package dev.elsewhere.gear2.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mirrorbolt bolts: built-in pierce (setPierceLevel is private) and base damage for the self-test. */
@Mixin(AbstractArrow.class)
public interface Gear2ArrowAccess {
	@Invoker("setPierceLevel")
	void gear2$setPierceLevel(byte level);

	@Accessor("baseDamage")
	double gear2$baseDamage();
}
