package dev.elsewhere.stub.summoner.mixin;

import dev.elsewhere.stub.summoner.SummonerAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** STUB (arsenal owns the attributes): gives every player the minion attributes. */
@Mixin(Player.class)
public abstract class PlayerAttributesMixin {
	@Inject(method = "createAttributes", at = @At("RETURN"))
	private static void elsewhere_summoner$attrs(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		SummonerAttributes.init();
		var s = SummonerAttributes.slots();
		var d = SummonerAttributes.damage();
		if (s != null) cir.getReturnValue().add(s);
		if (d != null) cir.getReturnValue().add(d);
		var sp = SummonerAttributes.spellPower();
		var mg = SummonerAttributes.maxGlimmer();
		if (sp != null) cir.getReturnValue().add(sp);
		if (mg != null) cir.getReturnValue().add(mg);
	}
}
