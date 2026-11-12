package dev.elsewhere.kingdom_court.mixin;

import com.mojang.datafixers.util.Pair;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * /locate, explorer maps and other structure searches: vanilla only searches random-spread and concentric-ring
 * placements, so the realm castle (placed in the one chunk chosen at server start) was never found. When the searched
 * set holds only the castle, answer with the chosen site (overworld only).
 */
@Mixin(ChunkGenerator.class)
public abstract class LocateCastleMixin {
	@Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
	private void elsewhere$locateCastle(ServerLevel level, HolderSet<Structure> structures, BlockPos from, int radius, boolean skipKnown,
		CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
		if (structures.size() != 1 || level.dimension() != Level.OVERWORLD) return;
		Holder<Structure> h = structures.get(0);
		if (!h.is(CourtWorld.STRUCTURE_KEY)) return;
		BlockPos site = CourtWorld.site(level.getSeed());
		if (site != null) cir.setReturnValue(Pair.of(site, h));
	}
}
