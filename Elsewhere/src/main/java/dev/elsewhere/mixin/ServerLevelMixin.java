package dev.elsewhere.mixin;

import dev.elsewhere.tiergear.TierGear;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
		at = @At("HEAD"), cancellable = true)
	private void elsewhere$silentBlade(final Holder<GameEvent> event, final Vec3 pos, final GameEvent.Context context, final CallbackInfo ci) {
		if (context.sourceEntity() != null && TierGear.silences(context.sourceEntity(), event)) {
			ci.cancel();
		}
	}
}
