package dev.elsewhere.kingdom_villages.mixin;

import dev.elsewhere.kingdom_villages.PlayerMarks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Any completed trade marks the villager's chunk: that village is never besieged or occupied (trading halls stay safe). */
@Mixin(AbstractVillager.class)
public abstract class VillageAbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void elsewhereVillages$trade(MerchantOffer offer, CallbackInfo ci) {
		AbstractVillager self = (AbstractVillager) (Object) this;
		if (self.level() instanceof ServerLevel level) PlayerMarks.trade(level, self.blockPosition());
	}
}
