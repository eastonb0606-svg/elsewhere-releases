package dev.elsewhere.overworld_bosses.fit3;

import dev.elsewhere.overworld_bosses.fit3.mixin.OwbBeardifierAccess;
import dev.elsewhere.overworld_bosses.world.OwbStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Per-piece noise-level terrain rules for the boss sites' fit pieces. The structure JSONs keep {@code terrain_adaptation: none}, so saved
 * 2.x starts (frozen {@code OwbPiece}) are never bearded; only {@link OwbFitPiece}s with a rule add a {@code Beardifier.Rigid} for their
 * small footprint box. One {@code startsForStructure} lookup per chunk at the noise step, nothing per tick.
 */
public final class OwbBeards {
	private OwbBeards() {}

	public static Beardifier extend(Beardifier original, StructureManager sm, ChunkPos cp) {
		List<StructureStart> starts = sm.startsForStructure(cp.x(), cp.z(), s -> s instanceof OwbStructure);
		if (starts.isEmpty()) return original;
		List<Beardifier.Rigid> extra = null;
		BoundingBox area = null;
		for (StructureStart start : starts) {
			for (StructurePiece piece : start.getPieces()) {
				if (!(piece instanceof OwbFitPiece fp)) continue;
				BoundingBox b = fp.beardBox();
				if (b == null || !b.intersects(cp.getMinBlockX() - 12, cp.getMinBlockZ() - 12, cp.getMaxBlockX() + 12, cp.getMaxBlockZ() + 12)) continue;
				if (extra == null) extra = new ArrayList<>();
				extra.add(new Beardifier.Rigid(b, fp.adapt, 0));
				area = area == null ? b : BoundingBox.encapsulating(area, b);
			}
		}
		if (extra == null) return original;
		OwbBeardifierAccess acc = (OwbBeardifierAccess) (Object) original;
		List<Beardifier.Rigid> pieces = new ArrayList<>(acc.elsewhereOwbFit$pieces());
		pieces.addAll(extra);
		BoundingBox affected = area.inflatedBy(24);
		if (acc.elsewhereOwbFit$affectedBox() != null) affected = BoundingBox.encapsulating(affected, acc.elsewhereOwbFit$affectedBox());
		return new Beardifier(List.copyOf(pieces), acc.elsewhereOwbFit$junctions(), affected);
	}

	/** The rules a Beardifier holds (self-test: proves the mixin is live). */
	public static List<Beardifier.Rigid> rules(Beardifier b) {
		return ((OwbBeardifierAccess) (Object) b).elsewhereOwbFit$pieces();
	}
}
