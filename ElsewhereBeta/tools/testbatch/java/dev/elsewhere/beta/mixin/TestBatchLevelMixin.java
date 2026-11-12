package dev.elsewhere.beta.mixin;

import dev.elsewhere.beta.testbatch.TestBatch;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test batch only (tools/testbatch): records chunks a module force-loads, so they are released when it ends. */
@Mixin(ServerLevel.class)
public abstract class TestBatchLevelMixin {
	@Inject(method = "setChunkForced", at = @At("RETURN"))
	private void elsewhere_beta$testbatchForced(int x, int z, boolean forced, CallbackInfoReturnable<Boolean> cir) {
		if (TestBatch.ENABLED && forced && cir.getReturnValueZ()) TestBatch.onForced((ServerLevel) (Object) this, x, z);
	}
}
