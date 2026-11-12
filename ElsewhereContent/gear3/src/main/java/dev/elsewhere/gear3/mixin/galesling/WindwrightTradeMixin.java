package dev.elsewhere.gear3.mixin.galesling;

import dev.elsewhere.gear3.s3.galesling.GaleSling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gale Sling source (GEAR.md A5: "Windwright 6 Zephyr Feathers + string"): adds the offer when a Windwright (Petalwind's kite-market
 * trader) first makes its trades. Runs once per trader, never per tick. {@code @Pseudo}: skipped when Petalwind is not loaded.
 * Switch: {@code gale_sling_trade} ({@link GaleSling#tradeEnabled()}).
 */
@Pseudo
@Mixin(targets = "dev.elsewhere.petalwind.Windwright")
public abstract class WindwrightTradeMixin {
	@Inject(method = "updateTrades", at = @At("TAIL"))
	private void gear3$galeSlingTrade(ServerLevel level, CallbackInfo ci) {
		GaleSling.addWindwrightTrade((AbstractVillager) (Object) this);
	}
}
