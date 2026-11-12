package dev.elsewhere.arsenal.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("setPierceLevel")
	void elsewhere$setPierceLevel(byte level);

	@org.spongepowered.asm.mixin.gen.Accessor("baseDamage")
	double elsewhere$getBaseDamage();
}
