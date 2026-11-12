package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.BossArenaStructure;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 terrain-fit arenas for nether_end_bosses (s-struct-nether). Kill switch {@code -Delsewhere.overhaul3.nether_fit=false}:
 * new starts are 2.x pieces again (built once from the centre chunk with the 2.x look) and the dev command / fights use the
 * 2.x builders. Saved 2.x pieces (no {@code v} tag) always finish with the 2.x code, and a fight rebuild after a restart uses
 * the generator that built that arena, so an old arena never gets a half new look.
 */
public final class NetherFit {
	private NetherFit() {}

	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.nether_fit", "true"));

	/** The v2 arenas, by boss id. */
	public static final Map<String, FitArena> ARENAS = new LinkedHashMap<>();
	/** The 2.x builders, captured before the v2 adapters replace them in {@link ArenaBuilder#BY_BOSS}. */
	public static final Map<String, ArenaBuilder> LEGACY = new LinkedHashMap<>();

	static {
		for (FitArena a : new FitArena[] {new GruntharHall(), new SolaxisCradle(), new MournmawBasin(), new OssyraSpire(), new AphelionHalo()}) ARENAS.put(a.id(), a);
	}

	/** Call once after every boss content init has put its 2.x builder. */
	public static void init() {
		if (!LEGACY.isEmpty()) return;
		LEGACY.putAll(ArenaBuilder.BY_BOSS);
		if (!ENABLED) return;
		for (FitArena a : ARENAS.values()) {
			ArenaBuilder old = LEGACY.get(a.id());
			if (old != null) ArenaBuilder.BY_BOSS.put(a.id(), new Adapter(a, old));
		}
	}

	/** Builds a whole v2 arena on a live level or a feature region, unclipped (the arena stays inside its 3x3 chunks). */
	public static FitW buildWhole(WorldGenLevel level, FitArena arena, BlockPos origin, @Nullable BoundingBox clip) {
		FitW w = new FitW(level, clip, seedOf(origin));
		arena.build(w, origin);
		w.finish();
		return w;
	}

	/** The piece seed for live builds: stable per origin, so a rebuild matches the first build. */
	public static long seedOf(BlockPos origin) {
		return origin.asLong() * 0x2545F4914F6CDD1DL + 0x5DEECE66DL;
	}

	/** Fight rebuild after a restart mid-fight: the 2.x builder for a 2.x arena, the v2 one otherwise. */
	public static void rebuild(ServerLevel level, String id, BlockPos origin) {
		FitArena arena = ARENAS.get(id);
		ArenaBuilder legacy = LEGACY.get(id);
		if (arena == null || !ENABLED || versionAt(level, origin) == 1) {
			if (legacy != null) legacy.build(level, origin, level.getRandom());
			return;
		}
		BossArenaStructure.Piece piece = pieceAt(level, origin);
		FitW w = new FitW(level, null, piece != null ? piece.seed() : seedOf(origin));
		arena.build(w, origin);
		w.finish();
	}

	/** 1 for a 2.x arena piece at this origin, 2 for a v2 piece, 0 when no structure piece is there (dev-built arena). */
	public static int versionAt(ServerLevel level, BlockPos origin) {
		BossArenaStructure.Piece p = pieceAt(level, origin);
		return p == null ? 0 : p.version();
	}

	static BossArenaStructure.@Nullable Piece pieceAt(ServerLevel level, BlockPos origin) {
		for (StructureStart s : level.getChunk(origin.getX() >> 4, origin.getZ() >> 4).getAllStarts().values()) {
			for (StructurePiece p : s.getPieces()) if (p instanceof BossArenaStructure.Piece bp && bp.origin.equals(origin)) return bp;
		}
		return null;
	}

	/** v2 arena behind the old {@link ArenaBuilder} contract (dev command, self-test fights, gametest shots). */
	public record Adapter(FitArena arena, ArenaBuilder legacy) implements ArenaBuilder {
		@Override
		public void build(WorldGenLevel level, BlockPos origin, RandomSource random) {
			buildWhole(level, arena, origin, null);
		}

		@Override
		public @Nullable BlockPos findOrigin(WorldGenLevel level, BlockPos center, RandomSource random) {
			return legacy.findOrigin(level, center, random);
		}

		@Override
		public BlockPos anchor(BlockPos origin) {
			return arena.anchor(origin);
		}
	}
}
