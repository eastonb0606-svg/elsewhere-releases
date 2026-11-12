package dev.elsewhere.palehollow.fit3;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.palehollow.world.PHSites;
import dev.elsewhere.palehollow.world.ResinKiln;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Elsewhere 3.0 core structure overhaul (STRUCTURES.md sections 2 and 4, core lines): Pale Hollow sites and the Resin Kiln as many small
 * terrain-fitted pieces, each on its own floor, with a vanilla-village-style beard (noise level, {@link #beards}) and a matching-block
 * underpin (block level, {@link W#underpin}). New starts only: saved 2.x starts keep their legacy {@code ph_site_piece}/{@code resin_kiln_piece}
 * pieces and finish exactly as they began.
 * <p>Kill switch: {@code -Delsewhere.overhaul3.core_fit=false} plans new starts with the legacy pieces again (beards stay on for fit pieces
 * that already exist, so their unfinished chunks match).
 */
public final class CoreFit {
	private CoreFit() {}

	/** Overhaul3 kill switch for the core structure looks and per-piece foundations. */
	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.core_fit", "true"));
	/** Piece schema version, saved in NBT; a behaviour change bumps it and keeps the old branch. */
	public static final int V = 1;
	/** Deepest underpin (STRUCTURES.md 2.9). */
	public static final int UNDERPIN_CAP = 12;

	public static StructurePieceType PIECE;

	public static void init() {
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Elsewhere.id("fit/core/piece"), (StructurePieceType) (ctx, tag) -> new FitPiece(tag));
	}

	/** Deterministic ground model: heights from the chunk generator's noise only (never the live world), memoised per plan. */
	public interface Ground {
		/** y of the top solid block (water bed under water). */
		int ground(int x, int z);

		/** y of the top block including water (equals {@link #ground} on dry land). */
		int surface(int x, int z);

		default boolean wet(int x, int z) {
			return surface(x, z) > ground(x, z);
		}
	}

	public static Ground ground(ChunkGenerator gen, LevelHeightAccessor h, RandomState rs) {
		Map<Long, Integer> g = new HashMap<>(), s = new HashMap<>();
		// Without aquifers (the Pale Hollow's noise settings have none) the only noise fluid is the sea below sea level, so a top block at or
		// above sea level is solid ground: one noise column instead of two (each height query evaluates a whole column).
		int sea = gen.getSeaLevel();
		boolean seaOnly = gen instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator nb && nb.generatorSettings().value().aquifers().isEmpty();
		return new Ground() {
			@Override
			public int ground(int x, int z) {
				return g.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
					if (seaOnly) {
						int top = surface(x, z);
						if (top >= sea) return top;
					}
					return gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, h, rs);
				});
			}

			@Override
			public int surface(int x, int z) {
				return s.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, h, rs));
			}
		};
	}

	public static Ground ground(Structure.GenerationContext c) {
		return ground(c.chunkGenerator(), c.heightAccessor(), c.randomState());
	}

	/** Plan seed for a start: world seed, start chunk and a per-family salt. */
	public static long seed(Structure.GenerationContext c, int salt) {
		ChunkPos cp = c.chunkPos();
		return c.seed() ^ (cp.x() * 341873128712L + cp.z() * 132897987541L) ^ (salt * 0x9E3779B97F4A7C15L);
	}

	/** Median ground under a local rectangle (9 samples), the floor rule for buildings (STRUCTURES.md 2.5). */
	public static int median(Ground g, Frame f, int x0, int z0, int x1, int z1) {
		int[] v = new int[9];
		int i = 0;
		for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) {
			int lx = x0 + (x1 - x0) * a / 2, lz = z0 + (z1 - z0) * b / 2;
			v[i++] = g.ground(f.x(lx, lz), f.z(lx, lz));
		}
		java.util.Arrays.sort(v);
		return v[4];
	}

	/** Plans the pieces of a Pale Hollow site of {@code kind} at {@code origin} (y = site floor from GenFit). */
	public static List<StructurePiece> planSite(String kind, Ground g, BlockPos origin, long seed) {
		List<StructurePiece> out = new ArrayList<>();
		switch (kind) {
			case "chapel" -> ChapelKit.plan(g, origin, seed, out);
			case "road" -> RoadKit.plan(g, origin, seed, PHSites.roadHasRift(origin), out);
			default -> ShrineKit.plan(g, origin, seed, out);
		}
		return out;
	}

	public static List<StructurePiece> planKiln(Ground g, BlockPos origin, long seed) {
		List<StructurePiece> out = new ArrayList<>();
		KilnKit.plan(g, origin, seed, out);
		return out;
	}

	/**
	 * Per-piece beards (STRUCTURES.md 2.6), called from {@code CoreFitBeardifierMixin} at the end of {@code Beardifier.forStructuresInChunk}.
	 * The core site structures keep {@code terrain_adaptation: none} in JSON (so legacy pieces never get a beard); every fit piece that asks
	 * for one adds a {@code beard_thin} rigid of its footprint only, 2 high at its floor, which is how vanilla village houses sit.
	 */
	public static Beardifier beards(Beardifier original, StructureManager sm, ChunkPos cp) {
		List<StructureStart> starts = sm.startsForStructure(cp.x(), cp.z(), s -> s.type() == PHSites.TYPE || s.type() == ResinKiln.TYPE);
		if (starts.isEmpty()) return original;
		List<Beardifier.Rigid> mine = null;
		BoundingBox any = null;
		int x0 = cp.getMinBlockX() - 12, z0 = cp.getMinBlockZ() - 12, x1 = cp.getMaxBlockX() + 12, z1 = cp.getMaxBlockZ() + 12;
		for (StructureStart st : starts) {
			for (StructurePiece p : st.getPieces()) {
				if (!(p instanceof FitPiece fp)) continue;
				BoundingBox b = fp.beardBox();
				if (b == null || !b.intersects(x0, z0, x1, z1)) continue;
				if (mine == null) mine = new ArrayList<>();
				mine.add(new Beardifier.Rigid(b, TerrainAdjustment.BEARD_THIN, 0));
				any = any == null ? b : BoundingBox.encapsulatingBoxes(List.of(any, b)).orElse(any);
			}
		}
		if (mine == null) return original;
		dev.elsewhere.mixin.CoreFitBeardifierAccessor acc = (dev.elsewhere.mixin.CoreFitBeardifierAccessor) (Object) original;
		List<Beardifier.Rigid> all = new ArrayList<>(acc.elsewhereCoreFit$pieces());
		all.addAll(mine);
		BoundingBox affected = any.inflatedBy(24);
		BoundingBox old = acc.elsewhereCoreFit$affectedBox();
		if (old != null) affected = BoundingBox.encapsulatingBoxes(List.of(old, affected)).orElse(affected);
		return new Beardifier(List.copyOf(all), List.copyOf(acc.elsewhereCoreFit$junctions()), affected);
	}
}
