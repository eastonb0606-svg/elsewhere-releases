package dev.elsewhere.mixin;

import dev.elsewhere.palehollow.fit3.CoreFit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 3.0 core structure fit (STRUCTURES.md 2.6): adds a beard_thin rigid for every core fit piece near the chunk. Injects at RETURN only, so it
 * chains with kingdom_court's realm redirects inside the method; structures without fit pieces get the vanilla result unchanged.
 */
@Mixin(Beardifier.class)
public abstract class CoreFitBeardifierMixin {
	@Inject(method = "forStructuresInChunk", at = @At("RETURN"), cancellable = true)
	private static void elsewhere$coreFitBeards(StructureManager structureManager, ChunkPos chunkPos, CallbackInfoReturnable<Beardifier> cir) {
		Beardifier b = cir.getReturnValue();
		Beardifier out = CoreFit.beards(b, structureManager, chunkPos);
		if (out != b) cir.setReturnValue(out);
	}
}
