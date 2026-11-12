package dev.elsewhere.beta.mixin;

import dev.elsewhere.beta.testbatch.TestBatch;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test batch only (tools/testbatch): a module self-test's halt(false) ends that module, not the server. */
@Mixin(MinecraftServer.class)
public abstract class TestBatchServerMixin {
	@Inject(method = "halt", at = @At("HEAD"), cancellable = true)
	private void elsewhere_beta$testbatchHalt(boolean waitForShutdown, CallbackInfo ci) {
		if (TestBatch.ENABLED && TestBatch.interceptHalt((MinecraftServer) (Object) this)) ci.cancel();
	}
}
