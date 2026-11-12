package dev.elsewhere.verdigris_ironpeak.mixin;

import dev.elsewhere.ironpeak.Ironpeak;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Ironpeak's Thin Air (above Y 1100): hunger drain x1.5. */
@Mixin(Player.class)
public abstract class PlayerThinAirMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float elsewhere$thinAirHunger(float amount) {
		Player self = (Player) (Object) this;
		return Ironpeak.inThinAir(self) && !dev.elsewhere.ironpeak.IronpeakItems.thinAirProof(self.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) ? amount * 1.5F : amount;
	}
}
