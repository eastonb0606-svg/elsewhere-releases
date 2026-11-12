package dev.elsewhere.kingdom_court.mixin;

import com.mojang.datafixers.util.Pair;
import dev.elsewhere.kingdom_court.world.RealmLocate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * /locate, explorer maps and every other structure search: vanilla only searches random-spread and concentric-ring placements, so the realm structures
 * (each placed in the one chunk chosen once per world and saved in CourtState) were never found. When the searched set holds a realm structure
 * (castle_aldermark, castle_azmar, castle_hrafnholt, castle_mirewick, waterfront_hrafnholt), {@link RealmLocate} answers from the saved site; any other
 * search runs the vanilla code unchanged.
 */
@Mixin(ChunkGenerator.class)
public abstract class LocateCastleMixin {
	@Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
	private void elsewhere$locateCastle(ServerLevel level, HolderSet<Structure> structures, BlockPos from, int radius, boolean skipKnown,
		CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
		if (RealmLocate.handles(level, structures)) cir.setReturnValue(RealmLocate.find((ChunkGenerator) (Object) this, level, structures, from, radius, skipKnown));
	}
}
