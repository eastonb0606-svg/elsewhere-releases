package dev.elsewhere.greenhaze_hush.mixin;

import dev.elsewhere.hush.HushNoise;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds every game event to the Hush noise bus (Resonance, blind hunters, Auris). */
@Mixin(ServerLevel.class)
public class ServerLevelGameEventMixin {
	@Inject(method = "gameEvent", at = @At("HEAD"))
	private void elsewhere$hear(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		HushNoise.onGameEvent((ServerLevel) (Object) this, event, pos, context);
	}
}
