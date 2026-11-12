package dev.elsewhere.summoner.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads an arrow's base damage (summoner pieces cut the wearer's arrows by 20% per piece). */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Accessor("baseDamage")
	double elsewhere_summoner$getBaseDamage();
}
