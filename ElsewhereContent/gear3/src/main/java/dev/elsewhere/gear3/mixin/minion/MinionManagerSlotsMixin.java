package dev.elsewhere.gear3.mixin.minion;

import dev.elsewhere.gear3.minion.MinionSlots;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Weighted minion slots (gear3 {@link MinionSlots}): for an owner with a weighted minion out (War-Yak = 2 slots),
 * the summoner's count rule in {@code MinionManager.enforceSlots} is replaced by the weighted one. Every other owner
 * keeps the summoner's own rule untouched. gear3 already needs the summoner module (whips, crooks, staves).
 */
@Mixin(targets = "dev.elsewhere.minion.MinionManager")
public abstract class MinionManagerSlotsMixin {
	@Inject(method = "enforceSlots", at = @At("HEAD"), cancellable = true, remap = false)
	private static void gear3$weightedSlots(ServerPlayer owner, CallbackInfo ci) {
		if (MinionSlots.handles(owner)) {
			MinionSlots.enforce(owner);
			ci.cancel();
		}
	}
}
