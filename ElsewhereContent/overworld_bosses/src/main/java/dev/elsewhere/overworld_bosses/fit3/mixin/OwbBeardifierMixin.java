package dev.elsewhere.overworld_bosses.fit3.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.overworld_bosses.fit3.OwbBeards;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 3.0 owb fit (STRUCTURES.md 2.6): adds the noise-level terrain rule of every Overworld boss site fit piece near the chunk (the Glacier
 * Shelf plateau's thin beard, the Sun Chamber's encapsulation). {@code @ModifyReturnValue} only, so it chains with kingdom_court's realm
 * redirects and every other module's return-value hooks; chunks without such pieces get the result unchanged.
 */
@Mixin(Beardifier.class)
public abstract class OwbBeardifierMixin {
	@ModifyReturnValue(method = "forStructuresInChunk", at = @At("RETURN"))
	private static Beardifier elsewhere$owbFitBeards(Beardifier original, @Local(argsOnly = true) StructureManager structureManager,
		@Local(argsOnly = true) ChunkPos chunkPos) {
		return OwbBeards.extend(original, structureManager, chunkPos);
	}
}
