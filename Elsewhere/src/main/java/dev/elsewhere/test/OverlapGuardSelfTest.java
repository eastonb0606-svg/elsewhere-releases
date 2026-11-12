package dev.elsewhere.test;

import dev.elsewhere.core.worldgen.OverlapGuard;
import dev.elsewhere.core.worldgen.OverlapGuard.Shape;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Structure overlap guard (3.0.0): every check line starts with {@code overlap}. Synthetic pairs prove the rule (exactly one of two overlapping
 * starts survives, the higher-ranked one, whichever order the starts are seen in); live audits over real terrain prove the worldgen side (every
 * real start is the raw start its set makes there, and a raw start is missing exactly when an outranking start overlaps it).
 */
public final class OverlapGuardSelfTest {
	private OverlapGuardSelfTest() {}

	private static void check(boolean ok, String what) {
		SelfTest.check(ok, "overlap " + what);
	}

	private static Identifier id(String s) {
		return Identifier.parse(s);
	}

	/** One-piece start: a box of the given x range, z 0..40, y 60..90, start chunk at its min corner. */
	private static Shape box(String id, int minX, int maxX) {
		return Shape.of(id(id), minX >> 4, 0, List.of(new BoundingBox(minX, 60, 0, maxX, 90, 40)));
	}

	/** Exactly one of a and b survives, it is {@code winner}, and the answer is the same in both list orders. */
	private static void pair(String what, Shape a, Shape b, Shape winner) {
		boolean ka1 = OverlapGuard.keep(a, List.of(a, b)), ka2 = OverlapGuard.keep(a, List.of(b, a));
		boolean kb1 = OverlapGuard.keep(b, List.of(a, b)), kb2 = OverlapGuard.keep(b, List.of(b, a));
		check(ka1 == ka2 && kb1 == kb2, what + ": decision does not depend on the order starts are seen");
		check(ka1 ^ kb1, what + ": exactly one of the two overlapping starts is placed (a " + ka1 + ", b " + kb1 + ")");
		check((winner == a ? ka1 : kb1), what + ": the placed one is the higher-ranked " + winner.id());
	}

	public static void run(ServerLevel overworld, ServerLevel hollow) {
		check(OverlapGuard.ENABLED, "guard is on by default (-Delsewhere.overlap_guard=false turns it off)");
		// ranks
		check(OverlapGuard.tier(id("elsewhere:castle_aldermark")) == OverlapGuard.CASTLE && OverlapGuard.tier(id("elsewhere:waterfront_hrafnholt")) == OverlapGuard.CASTLE,
			"castles and waterfronts rank first");
		check(OverlapGuard.tier(id("minecraft:village_plains")) == OverlapGuard.FOREIGN, "vanilla structures rank second");
		check(OverlapGuard.tier(id("elsewhere:sun_chamber")) == OverlapGuard.ARENA && OverlapGuard.tier(id("elsewhere:solaxis_pyre_cradle")) == OverlapGuard.ARENA,
			"boss arenas (overworld and nether/end) rank after vanilla");
		check(OverlapGuard.tier(id("elsewhere:greenhaze_return_rift")) == OverlapGuard.RIFT && OverlapGuard.tier(id("elsewhere:lantern_road")) == OverlapGuard.RIFT
			&& OverlapGuard.RIFT < OverlapGuard.ARENA, "return rifts and the lantern road rank above boss arenas");
		check(OverlapGuard.tier(id("elsewhere:sunpit")) == OverlapGuard.OTHER && OverlapGuard.tier(id("elsewhere:hollow_shrine")) == OverlapGuard.OTHER,
			"other Elsewhere structures rank last");

		// pairs that overlap (boxes share x 100..120)
		Shape castle = box("elsewhere:castle_azmar", 0, 120), village = box("minecraft:village_plains", 100, 180);
		pair("castle vs village", castle, village, castle);
		Shape arena = box("elsewhere:sun_chamber", 150, 220);
		pair("village vs boss arena", village, arena, village);
		Shape site = box("elsewhere:sunpit", 200, 260);
		pair("boss arena vs Elsewhere site", arena, site, arena);
		Shape shrine = box("elsewhere:hollow_shrine", 250, 300);
		pair("Elsewhere site vs Elsewhere site (tie broken by id)", shrine, site, shrine);
		Shape s1 = box("elsewhere:sunpit", 400, 460), s2 = box("elsewhere:sunpit", 450, 520);
		pair("same structure twice (tie broken by start chunk)", s1, s2, s1);
		// the gap
		Shape near = box("elsewhere:sunpit", 124, 160), far = box("elsewhere:sunpit", 125, 160);
		check(!OverlapGuard.keep(near, List.of(castle, near)), "a start " + OverlapGuard.GAP + " blocks from a higher-ranked one is skipped");
		check(OverlapGuard.keep(far, List.of(castle, far)), "a start " + (OverlapGuard.GAP + 1) + " blocks away is placed");
		// a realm's castle and waterfront are laid out together by CourtSite: both stay
		Shape wf = box("elsewhere:waterfront_hrafnholt", 110, 170), keep = box("elsewhere:castle_hrafnholt", 0, 120);
		check(OverlapGuard.keep(wf, List.of(keep, wf)) && OverlapGuard.keep(keep, List.of(keep, wf)), "a castle and a waterfront touching each other are both placed");
		// vanilla against vanilla is left alone
		Shape v1 = box("minecraft:village_plains", 600, 700), v2 = box("minecraft:pillager_outpost", 650, 720);
		check(OverlapGuard.keep(v1, List.of(v1, v2)) && OverlapGuard.keep(v2, List.of(v1, v2)), "vanilla against vanilla is not the guard's business");
		// pieces, not just the hull: an L of two pieces with a free corner
		Shape ell = Shape.of(id("elsewhere:sunpit"), 50, 0, List.of(new BoundingBox(800, 60, 0, 900, 90, 20), new BoundingBox(800, 60, 0, 820, 90, 100)));
		Shape inCorner = box("minecraft:igloo", 860, 880);
		Shape corner = Shape.of(inCorner.id(), 54, 3, List.of(new BoundingBox(860, 60, 60, 880, 90, 90)));
		check(OverlapGuard.keep(ell, List.of(ell, corner)), "a start sharing only its hull's empty corner with a higher-ranked one is placed");

		// live: real worldgen, both dimensions
		audit("pale hollow", hollow, new ChunkPos(0, 0), 16);
		var spawn = overworld.getRespawnData().pos();
		audit("overworld", overworld, new ChunkPos(spawn.getX() >> 4, spawn.getZ() >> 4), 8);
		locateSkipsDropped(overworld, spawn);
	}

	/**
	 * Search side. Premade world: a village already generated near spawn stays locatable even with a synthetic castle laid over it (stored data is
	 * reported as stored). Never-generated terrain: a village the guard will drop under a synthetic castle is skipped by vanilla's own search
	 * (locate and map) by seed math alone, without loading its chunk, and is found again once the castle is gone.
	 */
	private static void locateSkipsDropped(ServerLevel ow, net.minecraft.core.BlockPos spawn) {
		var reg = ow.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		var gen = ow.getChunkSource().getGenerator();
		// (1) stored: the nearest village to spawn, generated
		var found = gen.findNearestMapStructure(ow, reg.getOrThrow(net.minecraft.tags.StructureTags.VILLAGE), spawn, 100, false);
		check(found != null, "locate: a village is found near spawn");
		if (found != null) {
			ChunkPos sc = new ChunkPos(found.getFirst().getX() >> 4, found.getFirst().getZ() >> 4);
			ow.getChunk(sc.x(), sc.z(), net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
			Shape sv = OverlapGuard.rawAt(ow, found.getSecond().value(), sc.x(), sc.z());
			check(sv != null && found.getSecond().is(sv.id()), "locate: the stored village at chunk " + sc.x() + "," + sc.z() + " is its set's raw start");
			if (sv != null) {
				var only = net.minecraft.core.HolderSet.<net.minecraft.world.level.levelgen.structure.Structure>direct(found.getSecond());
				OverlapGuard.injectForTest(ow, Shape.of(id("elsewhere:castle_aldermark"), sc.x(), sc.z(), List.of(sv.box())));
				try {
					check(OverlapGuard.dropsAt(ow, found.getSecond().value(), sc.x(), sc.z()), "locate: the seed verdict would drop the stored village under a castle");
					var still = gen.findNearestMapStructure(ow, only, spawn, 100, false);
					check(still != null && sameChunk(still.getFirst(), sc), "locate: premade world: the village standing in a generated chunk is still found (stored data wins)");
				} finally {
					OverlapGuard.injectForTest(ow, null);
				}
			}
		}
		// (2) never generated: a village found by seed math only, far from anything loaded
		var plains = reg.get(id("minecraft:village_plains")).orElse(null);
		check(plains != null, "locate: minecraft:village_plains is registered");
		if (plains == null) return;
		Shape v = null;
		int ox = 200_000 >> 4, oz = -200_000 >> 4;
		for (int x = ox; x < ox + 340 && v == null; x++) for (int z = oz; z < oz + 340 && v == null; z++) v = OverlapGuard.rawAt(ow, plains.value(), x, z);
		check(v != null, "locate: the guard's raw start finds a never-generated village");
		if (v == null) return;
		ChunkPos vc = new ChunkPos(v.cx(), v.cz());
		var holder = reg.get(v.id()).orElseThrow();
		var only = net.minecraft.core.HolderSet.<net.minecraft.world.level.levelgen.structure.Structure>direct(holder);
		var placement = ow.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).get(0);
		var from = new net.minecraft.core.BlockPos(vc.getMiddleBlockX(), 64, vc.getMiddleBlockZ());
		var predicted = ow.structureManager().checkStructurePresence(vc, holder.value(), placement, false);
		check(predicted == net.minecraft.world.level.levelgen.structure.StructureCheckResult.CHUNK_LOAD_NEEDED,
			"locate: vanilla's prediction has " + v.id() + " at never-generated chunk " + vc.x() + "," + vc.z() + " (" + predicted + ")");
		OverlapGuard.injectForTest(ow, Shape.of(id("elsewhere:castle_aldermark"), vc.x(), vc.z(), List.of(v.box())));
		try {
			check(OverlapGuard.dropsAt(ow, holder.value(), vc.x(), vc.z()), "locate: the guard drops a village under a castle (seed math)");
			var after = gen.findNearestMapStructure(ow, only, from, 100, false);
			check(after == null || !sameChunk(after.getFirst(), vc), "locate: /locate skips the dropped village (now " + (after == null ? "none" : after.getFirst().toShortString()) + ")");
			var map = gen.findNearestMapStructure(ow, only, from, 100, true);
			check(map == null || !sameChunk(map.getFirst(), vc), "locate: the explorer-map search (skip known) skips it too");
		} finally {
			OverlapGuard.injectForTest(ow, null);
		}
		check(ow.structureManager().checkStructurePresence(vc, holder.value(), placement, false) == net.minecraft.world.level.levelgen.structure.StructureCheckResult.CHUNK_LOAD_NEEDED,
			"locate: deciding needed no chunk load at the dropped village (still no stored data)");
		var again = gen.findNearestMapStructure(ow, only, from, 100, false);
		check(again != null && sameChunk(again.getFirst(), vc), "locate: without the castle the same village is found again");
	}

	private static boolean sameChunk(net.minecraft.core.BlockPos p, ChunkPos c) {
		return (p.getX() >> 4) == c.x() && (p.getZ() >> 4) == c.z();
	}

	private static void audit(String where, ServerLevel level, ChunkPos center, int radius) {
		long t0 = System.nanoTime();
		OverlapGuard.Audit a = OverlapGuard.audit(level, center, radius);
		long ms = (System.nanoTime() - t0) / 1_000_000L;
		dev.elsewhere.Elsewhere.LOG.info("[selftest] overlap {}: {} chunks, {} raw starts, {} placed, {} skipped, {} ms", where, a.chunks(), a.raw(), a.kept(), a.dropped(), ms);
		for (String p : a.problems().subList(0, Math.min(10, a.problems().size()))) dev.elsewhere.Elsewhere.LOG.info("[selftest] overlap {} problem: {}", where, p);
		check(a.raw() > 0, where + ": the audit square holds structure starts (" + a.raw() + ")");
		check(a.problems().isEmpty(), where + ": real starts match the guard's rule from any load order (" + a.problems().size() + " problems)");
	}
}
