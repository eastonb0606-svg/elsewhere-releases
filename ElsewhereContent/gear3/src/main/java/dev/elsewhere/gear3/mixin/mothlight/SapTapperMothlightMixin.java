package dev.elsewhere.gear3.mixin.mothlight;

import dev.elsewhere.gear3.s3.mothlight.Mothlight;
import java.util.List;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mothlight Staff source (GEAR.md: "Sap Tapper 20 Pale Bark + 4 Moonsilk Thread"): the Sap Tapper (core, Resin Kilns of the Pale
 * Hollow) rebuilds its offers for each customer from {@code SapTapper.offers()}; this appends the staff to that list. Runs when a
 * player opens the trade screen, never per tick. {@code @Pseudo} + {@code require = 0}: skipped when core is absent or changes.
 */
@Pseudo
@Mixin(targets = "dev.elsewhere.palehollow.entity.SapTapper")
public abstract class SapTapperMothlightMixin {
	@Inject(method = "offers()Ljava/util/List;", at = @At("RETURN"), require = 0)
	private static void gear3$mothlightOffer(CallbackInfoReturnable<List<MerchantOffer>> cir) {
		Mothlight.addSapTapperOffer(cir.getReturnValue());
	}
}
