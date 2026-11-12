package dev.elsewhere.stub.nether_end_bosses.mixin;

import dev.elsewhere.stub.nether_end_bosses.BossPart;
import dev.elsewhere.stub.nether_end_bosses.Parts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attack/interact packets that name a boss part's id resolve to the part. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "getEntityOrPart", at = @At("RETURN"), cancellable = true)
	private void elsewhere$bossPart(int id, CallbackInfoReturnable<Entity> cir) {
		if (cir.getReturnValue() == null) {
			BossPart part = Parts.get((ServerLevel) (Object) this, id);
			if (part != null) cir.setReturnValue(part);
		}
	}
}
