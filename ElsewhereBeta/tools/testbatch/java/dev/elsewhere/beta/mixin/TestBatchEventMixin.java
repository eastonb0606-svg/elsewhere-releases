package dev.elsewhere.beta.mixin;

import dev.elsewhere.beta.testbatch.TestBatch;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test batch only (tools/testbatch): captures a module self-test's event registrations while its hook runs. */
@Mixin(targets = "net.fabricmc.fabric.impl.base.event.ArrayBackedEvent", remap = false)
public abstract class TestBatchEventMixin {
	@Inject(method = "register(Lnet/minecraft/resources/Identifier;Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
	private void elsewhere_beta$testbatchCapture(Identifier phase, Object listener, CallbackInfo ci) {
		if (TestBatch.capturing != null && TestBatch.capture(this, phase, listener)) ci.cancel();
	}
}
