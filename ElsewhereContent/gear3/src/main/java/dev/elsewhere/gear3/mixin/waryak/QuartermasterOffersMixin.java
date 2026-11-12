package dev.elsewhere.gear3.mixin.waryak;

import dev.elsewhere.gear3.s3.waryak.WarYakTrade;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * War-Yak Horn at the Ironpeak Clan Quartermaster, clan rank 5 (GEAR.md source). The Quartermaster builds its offers
 * per customer in {@code IronpeakEntities.quartermasterOffers}; this appends the horn once. Optional (@Pseudo, require 0):
 * nothing happens when verdigris_ironpeak is absent or changes. Both overloads are hooked (2.x has the 1-argument one,
 * 3.0 the trader-aware 2-argument one that the 1-argument one calls); {@link WarYakTrade#addTo} never adds it twice.
 */
@Pseudo
@Mixin(targets = "dev.elsewhere.ironpeak.IronpeakEntities")
public abstract class QuartermasterOffersMixin {
	@Inject(method = "quartermasterOffers(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/List;", at = @At("RETURN"), require = 0, remap = false)
	private static void gear3$warYak(ServerPlayer p, CallbackInfoReturnable<List<MerchantOffer>> cir) {
		WarYakTrade.addTo(cir.getReturnValue(), p);
	}

	@Inject(method = "quartermasterOffers(Ldev/elsewhere/verdigris_ironpeak/ElsewhereTrader;Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/List;",
		at = @At("RETURN"), require = 0, remap = false)
	private static void gear3$warYakTrader(dev.elsewhere.verdigris_ironpeak.ElsewhereTrader trader, ServerPlayer p, CallbackInfoReturnable<List<MerchantOffer>> cir) {
		WarYakTrade.addTo(cir.getReturnValue(), p);
	}
}
