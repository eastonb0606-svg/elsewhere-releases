package dev.elsewhere.greenhaze_hush.world.fit;

import dev.elsewhere.greenhaze_hush.mixin.GhBeardifierAccessor;
import dev.elsewhere.greenhaze_hush.world.BuiltStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Per-piece noise-level terrain rules (STRUCTURES.md 2.6). The structure JSONs keep {@code terrain_adaptation: none}, so saved 2.x starts
 * (frozen {@code greenhaze_hush_built_piece}) are never bearded; only {@link GhFit.Piece}s with a rule add a {@code Beardifier.Rigid} for
 * their small box. One {@code startsForStructure} lookup per chunk at the noise step, nothing per tick.
 */
public final class GhBeards {
	private GhBeards() {}

	public static Beardifier extend(Beardifier original, StructureManager sm, ChunkPos cp) {
		if (!GhFit.BEARDS) return original;
		List<StructureStart> starts = sm.startsForStructure(cp.x(), cp.z(), s -> s instanceof BuiltStructure);
		if (starts.isEmpty()) return original;
		List<Beardifier.Rigid> extra = null;
		BoundingBox area = null;
		for (StructureStart start : starts) {
			for (StructurePiece piece : start.getPieces()) {
				if (!(piece instanceof GhFit.Piece fp) || fp.beard == null || fp.adapt == null) continue;
				BoundingBox b = fp.beard;
				if (!b.intersects(cp.getMinBlockX() - 12, cp.getMinBlockZ() - 12, cp.getMaxBlockX() + 12, cp.getMaxBlockZ() + 12)) continue;
				if (extra == null) extra = new ArrayList<>();
				extra.add(new Beardifier.Rigid(b, fp.adapt, 0));
				area = area == null ? b : BoundingBox.encapsulating(area, b);
			}
		}
		if (extra == null) return original;
		GhBeardifierAccessor acc = (GhBeardifierAccessor) (Object) original;
		List<Beardifier.Rigid> pieces = new ArrayList<>(acc.elsewhereGhFit$pieces());
		pieces.addAll(extra);
		BoundingBox affected = area.inflatedBy(24);
		if (acc.elsewhereGhFit$affectedBox() != null) affected = BoundingBox.encapsulating(affected, acc.elsewhereGhFit$affectedBox());
		return new Beardifier(List.copyOf(pieces), acc.elsewhereGhFit$junctions(), affected);
	}

	/** The rules a Beardifier holds (self-test: proves the mixin is live). */
	public static List<Beardifier.Rigid> rules(Beardifier b) {
		return ((GhBeardifierAccessor) (Object) b).elsewhereGhFit$pieces();
	}
}
