package dev.elsewhere.kingdom_court.mixin;

import dev.elsewhere.kingdom_court.world.CourtWorld;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 3.0 G4 (generation lane): per-piece beards for NEW realm starts. A realm structure keeps {@code beard_box} (so saved 2.x starts finish
 * exactly as before), but a terrain-fit ("fv" 2) realm piece offers its own beard: the yard or town pad as a 1-high box at floor - 1, which
 * under beard_box blends like beard_thin, or none at all (walls, roads, dungeon, kit). Every other piece in the game is untouched.
 */
@Mixin(Beardifier.class)
public abstract class BeardifierMixin {
	@Redirect(method = "forStructuresInChunk", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;isCloseToChunk(Lnet/minecraft/world/level/ChunkPos;I)Z"))
	private static boolean elsewhere$realmFitClose(StructurePiece piece, ChunkPos pos, int d) {
		if (piece instanceof CourtWorld.RealmPiece rp && rp.fit()) {
			BoundingBox b = rp.fitBeard();
			return b != null && b.intersects(pos.getMinBlockX() - d, pos.getMinBlockZ() - d, pos.getMaxBlockX() + d, pos.getMaxBlockZ() + d);
		}
		return piece.isCloseToChunk(pos, d);
	}

	@Redirect(method = "forStructuresInChunk", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;getBoundingBox()Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"))
	private static BoundingBox elsewhere$realmFitBox(StructurePiece piece) {
		if (piece instanceof CourtWorld.RealmPiece rp && rp.fit()) {
			BoundingBox b = rp.fitBeard();
			if (b != null) return b;
		}
		return piece.getBoundingBox();
	}
}
