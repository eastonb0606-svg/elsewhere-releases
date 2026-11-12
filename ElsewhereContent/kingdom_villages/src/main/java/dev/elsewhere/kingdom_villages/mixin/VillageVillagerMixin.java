package dev.elsewhere.kingdom_villages.mixin;

import dev.elsewhere.kingdom_villages.Steward;
import dev.elsewhere.kingdom_villages.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Royal Steward trades, weekly limits and Master gate; liberation discount and Steward price floors. */
@Mixin(Villager.class)
public abstract class VillageVillagerMixin {
	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void elsewhereVillages$stewardTrades(ServerLevel level, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (Steward.isSteward(self)) {
			Steward.addTrades(self, level);
			ci.cancel();
		}
	}

	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void elsewhereVillages$prices(Player player, CallbackInfo ci) {
		VillageManager.applyPrices((Villager) (Object) this);
	}

	@Inject(method = "restock", at = @At("HEAD"))
	private void elsewhereVillages$beforeRestock(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (Steward.isSteward(self)) Steward.beforeRestock(self);
	}

	@Inject(method = "restock", at = @At("TAIL"))
	private void elsewhereVillages$afterRestock(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (Steward.isSteward(self)) Steward.afterRestock(self);
	}

	@Inject(method = "shouldIncreaseLevel", at = @At("RETURN"), cancellable = true)
	private void elsewhereVillages$masterGate(CallbackInfoReturnable<Boolean> cir) {
		Villager self = (Villager) (Object) this;
		if (cir.getReturnValueZ() && Steward.isSteward(self) && self.getVillagerData().level() >= 4
			&& self.level() instanceof ServerLevel level && !Steward.mayReachMaster(level)) cir.setReturnValue(false);
	}
}
