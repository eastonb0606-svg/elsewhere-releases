package dev.elsewhere.gear2.mixin;

import dev.elsewhere.gear2.Gear2Hooks;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.3 sends a punch packet for a swing that hits nothing: an Ironclad Flail miss resets the chain. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class Gear2PunchMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At("TAIL"))
	private void gear2$punch(ServerboundPunchPacket packet, CallbackInfo ci) {
		Gear2Hooks.onPunch(this.player);
	}
}
