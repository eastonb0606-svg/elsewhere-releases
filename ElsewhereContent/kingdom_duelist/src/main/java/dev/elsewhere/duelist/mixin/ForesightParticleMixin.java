package dev.elsewhere.duelist.mixin;

import dev.elsewhere.duelist.foresight.Foresight;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B5 foresight gate: every {@code ServerLevel.sendParticles} overload ends in this per-player send. Inside
 * {@link Foresight#hint} a player without foresight gear is skipped; outside a hint scope it does nothing (one int read).
 */
@Mixin(ServerLevel.class)
public abstract class ForesightParticleMixin {
	@Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z", at = @At("HEAD"), cancellable = true)
	private void elsewhere$foresightHint(ServerPlayer player, boolean overrideLimiter, double x, double y, double z, Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
		if (Foresight.hinting() && !Foresight.allowParticle(player)) cir.setReturnValue(false);
	}
}
