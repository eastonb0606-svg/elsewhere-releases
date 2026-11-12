package dev.elsewhere.greenhaze_hush.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.greenhaze_hush.world.fit.GhBeards;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 3.0 greenhaze_hush fit (STRUCTURES.md 2.6): adds the per-piece terrain rule of every new-style Greenhaze/Hush piece near the chunk (floor
 * beards, encapsulated Hush rooms). {@code @ModifyReturnValue} only, so it chains with kingdom_court's redirects and the other modules'
 * return-value hooks; chunks without such pieces get the result unchanged.
 */
@Mixin(Beardifier.class)
public abstract class GhBeardifierMixin {
	@ModifyReturnValue(method = "forStructuresInChunk", at = @At("RETURN"))
	private static Beardifier elsewhere$ghFitBeards(Beardifier original, @Local(argsOnly = true) StructureManager structureManager,
		@Local(argsOnly = true) ChunkPos chunkPos) {
		return GhBeards.extend(original, structureManager, chunkPos);
	}
}
