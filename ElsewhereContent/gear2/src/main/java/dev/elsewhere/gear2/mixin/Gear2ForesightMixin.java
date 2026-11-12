package dev.elsewhere.gear2.mixin;

import dev.elsewhere.gear2.SeersHourglass;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The bosses' ForesightGear only checks hands and armor; a trinket also works from the hotbar, so an active Seer's
 * Hourglass counts too. Optional (@Pseudo, require 0): does nothing if the boss kit is absent or changes.
 */
@Pseudo
@Mixin(targets = "dev.elsewhere.boss.kit.ForesightGear")
public abstract class Gear2ForesightMixin {
	@Inject(method = "sees", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
	private static void gear2$hourglass(Player p, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && SeersHourglass.active(p)) cir.setReturnValue(true);
	}
}
