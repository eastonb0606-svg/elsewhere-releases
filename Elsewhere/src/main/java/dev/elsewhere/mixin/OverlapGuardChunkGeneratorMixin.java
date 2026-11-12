package dev.elsewhere.mixin;

import dev.elsewhere.core.worldgen.OverlapGuard;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Structure overlap guard: once a chunk's starts are made (STRUCTURE_STARTS), drop the ones {@link OverlapGuard} rejects. */
@Mixin(ChunkGenerator.class)
public abstract class OverlapGuardChunkGeneratorMixin {
	@Inject(method = "createStructures", at = @At("TAIL"))
	private void elsewhere$overlapGuard(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures, ChunkAccess chunk,
		StructureTemplateManager templates, ResourceKey<Level> dimension, CallbackInfo ci) {
		OverlapGuard.filterChunk((ChunkGenerator) (Object) this, registries, state, chunk, templates, dimension);
	}
}
