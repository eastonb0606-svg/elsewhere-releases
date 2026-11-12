package dev.elsewhere.mixin;

import dev.elsewhere.core.worldgen.OverlapGuard;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structure overlap guard, search side. {@code StructureCheck.checkStart} is what every structure search asks (/locate, explorer and cartographer
 * maps, dolphins, eyes of ender). It answers from (a) a loaded or cached chunk, (b) the chunk's stored starts, or (c) for a chunk with no stored
 * structure data, a seed prediction. Only (c) gets the guard's verdict: a start {@link OverlapGuard#dropsAt} says the guard will drop is reported
 * absent and the search moves on (seed math, no chunk load). Anything already generated is reported exactly as stored, so a structure standing in a
 * premade world stays locatable. Castles keep their own locate (kingdom_court RealmLocate).
 */
@Mixin(StructureCheck.class)
public abstract class OverlapGuardLocateMixin {
	@Shadow @Final private LevelHeightAccessor heightAccessor;
	@Shadow @Final private Long2ObjectMap<Object2IntMap<Structure>> loadedChunks;
	@Shadow @Final private LongSet chunksWithoutStartsInStorage;

	@Inject(method = "checkStart", at = @At("RETURN"), cancellable = true)
	private void elsewhere$overlapGuardLocate(ChunkPos pos, Structure structure, StructurePlacement placement, boolean skipKnown,
		CallbackInfoReturnable<StructureCheckResult> cir) {
		if (cir.getReturnValue() == StructureCheckResult.START_NOT_PRESENT) return;
		long key = pos.pack();
		if (loadedChunks.get(key) != null || !chunksWithoutStartsInStorage.contains(key)) return; // (a) or (b): stored data, reported as stored
		// StructureCheck's "no stored data" memo is per session and goes stale once the chunk is generated (vanilla then loads it and reads the real
		// start); a chunk whose starts were made this session is generated, so it is left to vanilla too
		if (heightAccessor instanceof ServerLevel sl && !OverlapGuard.generatedThisSession(sl, pos.x(), pos.z())
			&& OverlapGuard.dropsAt(sl, structure, pos.x(), pos.z()))
			cir.setReturnValue(StructureCheckResult.START_NOT_PRESENT);
	}
}
