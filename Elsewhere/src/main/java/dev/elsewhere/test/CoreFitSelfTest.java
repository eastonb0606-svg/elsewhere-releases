package dev.elsewhere.test;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.mixin.CoreFitBeardifierAccessor;
import dev.elsewhere.palehollow.entity.SapTapper;
import dev.elsewhere.palehollow.fit3.ChapelKit;
import dev.elsewhere.palehollow.fit3.CoreFit;
import dev.elsewhere.palehollow.fit3.FitPiece;
import dev.elsewhere.palehollow.fit3.KilnKit;
import dev.elsewhere.palehollow.fit3.RoadKit;
import dev.elsewhere.palehollow.fit3.ShrineKit;
import dev.elsewhere.palehollow.fit3.W;
import dev.elsewhere.palehollow.world.PHSites;
import dev.elsewhere.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * 3.0 core structure fit (s-struct-core): worldgen checks for the Pale Hollow sites, the Resin Kiln and the Return Rift ring. Every check line
 * starts with {@code struct3_core}. Synthetic 1:3 hillsides prove the per-piece floors and underpins on a known slope; real worldgen sites (two
 * per structure, from fresh areas) prove the whole pipeline with beards: no floating column, no natural block in a room or doorway, contained
 * chapel water, the road's step and lantern rules, the kiln's trader, the beard rigids, plan determinism and planner time.
 */
public final class CoreFitSelfTest {
	private CoreFitSelfTest() {}

	private static void check(boolean ok, String what) {
		SelfTest.check(ok, "struct3_core " + what);
	}

	static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) && !s.is(Blocks.PALE_MOSS_BLOCK) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
			|| s.is(BlockTags.LOGS) && !s.is(Blocks.PALE_OAK_LOG) && !s.is(Blocks.STRIPPED_PALE_OAK_LOG) || s.is(Blocks.MUD) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK);
	}

	public static void run(MinecraftServer server, ServerLevel overworld, ServerLevel hollow) {
		check(CoreFit.ENABLED, "kill switch -Delsewhere.overhaul3.core_fit is on by default");
		check((Object) Beardifier.EMPTY instanceof CoreFitBeardifierAccessor, "Beardifier accessor mixin applied (per-piece beards can be added)");
		planChecks(hollow);
		for (String kind : new String[] {"shrine", "chapel", "kiln", "road"}) synthetic(overworld, kind);
		real(hollow);
		rift(hollow);
	}

	// ------------------------------------------------------------------ plans
	private static List<StructurePiece> plan(String kind, CoreFit.Ground g, BlockPos o, long seed) {
		return kind.equals("kiln") ? CoreFit.planKiln(g, o, seed) : CoreFit.planSite(kind, g, o, seed);
	}

	private static String sig(List<StructurePiece> ps) {
		StringBuilder b = new StringBuilder();
		for (StructurePiece p : ps) if (p instanceof FitPiece f) b.append(f.kind).append(f.floor).append(f.rot).append(Arrays.toString(f.fp)).append(Arrays.toString(f.data)).append(f.getBoundingBox());
		return b.toString();
	}

	private static void planChecks(ServerLevel hollow) {
		var gen = hollow.getChunkSource().getGenerator();
		var rs = hollow.getChunkSource().randomState();
		long worst = 0, total = 0;
		int n = 0;
		boolean same = true;
		// planner cost is this thread's CPU time (the budget is a CPU cost; wall time also counts other JVMs sharing the cores), after one
		// untimed warm-up plan per kind so the JIT's first compile is not billed to a site
		var mx = java.lang.management.ManagementFactory.getThreadMXBean();
		boolean cpu = mx.isCurrentThreadCpuTimeSupported();
		java.util.function.LongSupplier clock = cpu ? mx::getCurrentThreadCpuTime : System::nanoTime;
		for (String kind : new String[] {"shrine", "chapel", "kiln", "road"}) {
			int x = -20000 - kind.length() * 4111, z = 15000;
			plan(kind, CoreFit.ground(gen, hollow, rs), new BlockPos(x, gen.getFirstOccupiedHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, hollow, rs), z), 7L + kind.hashCode());
		}
		for (String kind : new String[] {"shrine", "chapel", "kiln", "road"}) {
			for (int i = 0; i < 4; i++) {
				int x = 20000 + i * 977 + kind.length() * 4111, z = -15000 + i * 613;
				BlockPos o = new BlockPos(x, gen.getFirstOccupiedHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, hollow, rs), z);
				long seed = 1234567L * (i + 1) + kind.hashCode();
				long t0 = clock.getAsLong();
				List<StructurePiece> a = plan(kind, CoreFit.ground(gen, hollow, rs), o, seed);
				long dt = clock.getAsLong() - t0;
				List<StructurePiece> b = plan(kind, CoreFit.ground(gen, hollow, rs), o, seed);
				same &= sig(a).equals(sig(b)) && !a.isEmpty();
				worst = Math.max(worst, dt);
				total += dt;
				n++;
				if (kind.equals("road")) {
					FitPiece r = (FitPiece) a.get(0);
					int[][] c = RoadKit.cells(r);
					int maxStep = 0;
					double len = 0;
					for (int k = 1; k < c.length; k++) {
						maxStep = Math.max(maxStep, Math.abs(c[k][2] - c[k - 1][2]));
						len += Math.hypot(c[k][0] - c[k - 1][0], c[k][1] - c[k - 1][1]);
					}
					check(maxStep <= 2 && len >= 36 && len <= 110, SelfTest.f("road plan %d: %d cells, %.0f blocks long, max step %d half-blocks (<= 2 = one block)", i, c.length, len, maxStep));
				}
			}
		}
		check(same, "plans are deterministic (same seed and ground -> identical pieces, floors, rotations and road cells)");
		double avg = total / 1e6 / n;
		check(avg <= 8.0, SelfTest.f("planner time per site (%s, warm): avg %.2f ms, worst %.2f ms (budget 8 ms per site, STRUCTURES.md 5)", cpu ? "thread CPU" : "wall", avg, worst / 1e6));
	}

	// ------------------------------------------------------------------ synthetic hillside
	private static void synthetic(ServerLevel level, String kind) {
		int idx = Arrays.asList("shrine", "chapel", "kiln", "road").indexOf(kind);
		int x0 = 1_800_000 + idx * 320, z0 = 1_800_000, base = 90;
		CoreFit.Ground slope = new CoreFit.Ground() {
			@Override
			public int ground(int x, int z) {
				return base + Math.floorDiv(x - x0, 3);
			}

			@Override
			public int surface(int x, int z) {
				return ground(x, z);
			}
		};
		BlockPos o = new BlockPos(x0, slope.ground(x0, z0), z0);
		List<StructurePiece> ps = plan(kind, slope, o, 99L + idx * 31L);
		BoundingBox all = null;
		for (StructurePiece p : ps) all = all == null ? p.getBoundingBox() : BoundingBox.encapsulatingBoxes(List.of(all, p.getBoundingBox())).orElse(all);
		if (all == null) {
			check(false, kind + " synthetic: no pieces planned");
			return;
		}
		// the hillside: stone under dirt under grass, rising 1 block per 3 along x, air above
		int minCx = all.minX() >> 4, maxCx = all.maxX() >> 4, minCz = all.minZ() >> 4, maxCz = all.maxZ() >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) for (int cz = minCz; cz <= maxCz; cz++) level.getChunk(cx, cz);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = all.minX(); x <= all.maxX(); x++) for (int z = all.minZ(); z <= all.maxZ(); z++) {
			int g = slope.ground(x, z);
			for (int y = g - 16; y <= g + 24; y++) {
				BlockState s = y > g ? Blocks.AIR.defaultBlockState() : y == g ? Blocks.GRASS_BLOCK.defaultBlockState() : y > g - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
				level.setBlock(m.set(x, y, z), s, 2 | 16);
			}
		}
		var gen = level.getChunkSource().getGenerator();
		for (StructurePiece p : ps) {
			BoundingBox b = p.getBoundingBox();
			for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
				BoundingBox chunkBB = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY() - 1, (cz << 4) + 15);
				p.postProcess(level, level.structureManager(), gen, level.getRandom(), chunkBB, new ChunkPos(cx, cz), o);
			}
		}
		measure(level, ps, kind + " on a 1:3 hillside", false);
	}

	// ------------------------------------------------------------------ real worldgen
	private static void real(ServerLevel hollow) {
		var reg = hollow.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		String[][] names = {{"hollow_shrine", "shrine"}, {"drowned_chapel", "chapel"}, {"resin_kiln", "kiln"}, {"lantern_road", "road"}};
		boolean beardSeen = false;
		for (String[] nm : names) {
			var holder = reg.get(ResourceKey.create(Registries.STRUCTURE, Elsewhere.id(nm[0])));
			if (holder.isEmpty()) {
				check(false, nm[0] + " is registered");
				continue;
			}
			for (int site = 0; site < 2; site++) {
				int sx = 60000 + site * 37000 + nm[0].length() * 1500, sz = -70000 - site * 23000;
				var found = hollow.getChunkSource().getGenerator().findNearestMapStructure(hollow, net.minecraft.core.HolderSet.direct(holder.get()), new BlockPos(sx, 64, sz), 100, false);
				if (found == null) {
					check(false, nm[0] + " site " + site + ": found by the structure search");
					continue;
				}
				BlockPos p = found.getFirst();
				StructureStart start = hollow.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true).getStartForStructure(holder.get().value());
				if (start == null || !start.isValid()) {
					check(false, nm[0] + " site " + site + ": start exists");
					continue;
				}
				BoundingBox b = start.getBoundingBox();
				for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) hollow.getChunk(cx, cz, ChunkStatus.FULL, true);
				List<StructurePiece> ps = start.getPieces();
				boolean fit = !ps.isEmpty() && ps.stream().allMatch(q -> q instanceof FitPiece f && f.v == CoreFit.V);
				check(fit, nm[0] + " site " + site + " at " + p.toShortString() + ": new starts use fit pieces (" + ps.size() + " pieces)");
				if (!fit) continue;
				measure(hollow, ps, nm[0] + " site " + site + " at " + p.toShortString(), true);
				for (StructurePiece q : ps) {
					FitPiece f = (FitPiece) q;
					if (f.beardBox() == null || beardSeen) continue;
					int[] r = f.worldRect();
					Beardifier bd = Beardifier.forStructuresInChunk(hollow.structureManager(), new ChunkPos(r[0] >> 4, r[1] >> 4));
					boolean has = ((CoreFitBeardifierAccessor) (Object) bd).elsewhereCoreFit$pieces().stream().anyMatch(rg -> rg.box().equals(f.beardBox())
						&& rg.terrainAdjustment() == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN);
					check(has, nm[0] + ": its chunk's Beardifier carries the piece's own beard_thin rigid (footprint, 2 high at floor + 1)");
					beardSeen = true;
				}
			}
		}
	}

	// ------------------------------------------------------------------ metrics
	private static void measure(ServerLevel l, List<StructurePiece> ps, String label, boolean real) {
		int floors = 0, floating = 0, rooms = 0, buried = 0;
		List<String> where = new ArrayList<>();
		for (StructurePiece q : ps) {
			FitPiece p = (FitPiece) q;
			List<int[]> fl = switch (p.kind) {
				case "shrine" -> ShrineKit.floors(p);
				case "chapel_nave", "chapel_east" -> ChapelKit.floors(p);
				case "kiln_a", "kiln_b", "kiln_yard" -> KilnKit.floors(p);
				default -> List.of();
			};
			for (int[] c : fl) {
				floors++;
				BlockState top = l.getBlockState(new BlockPos(c[0], c[1], c[2])), below = l.getBlockState(new BlockPos(c[0], c[1] - 1, c[2]));
				if (W.open(top) || W.open(below)) {
					floating++;
					if (where.size() < 4) where.add(p.kind + "@" + c[0] + "," + c[1] + "," + c[2] + "=" + (W.open(top) ? "top " + top.getBlock() : "below " + below.getBlock()));
				}
			}
			List<int[]> hr = switch (p.kind) {
				case "shrine" -> ShrineKit.headroom(p);
				case "kiln_a", "kiln_b" -> KilnKit.headroom(p);
				case "chapel_nave" -> ChapelKit.door(p);
				default -> List.of();
			};
			for (int[] c : hr) {
				rooms++;
				BlockState s = l.getBlockState(new BlockPos(c[0], c[1], c[2]));
				if (natural(s) || s.isSolid() && !s.is(Blocks.CHEST) && p.kind.equals("chapel_nave")) {
					buried++;
					if (where.size() < 6) where.add("blocked " + p.kind + "@" + c[0] + "," + c[1] + "," + c[2] + "=" + s.getBlock());
				}
			}
		}
		String kind = ((FitPiece) ps.get(0)).kind;
		if (!kind.equals("road")) {
			check(floors > 0 && floating == 0, SelfTest.f("%s: %d floor/plinth columns, %d floating %s", label, floors, floating, where));
			check(rooms > 0 && buried == 0, SelfTest.f("%s: %d room/doorway cells, %d blocked by natural ground %s", label, rooms, buried, where));
		}
		switch (kind) {
			case "shrine" -> {
				FitPiece p = (FitPiece) ps.get(0);
				check(l.getBlockState(ShrineKit.chest(p)).is(Blocks.CHEST), label + ": the shrine's chest stands on its altar");
				int cols = 0;
				for (int[] c : new int[][] {{-3, -3}, {0, -3}, {3, -3}, {-3, 3}, {0, 3}, {3, 3}, {-3, 0}, {3, 0}}) {
					BlockPos b = new BlockPos(p.frame().x(c[0], c[1]), p.data[0] + 1, p.frame().z(c[0], c[1]));
					if (!l.getBlockState(b).isAir()) cols++;
				}
				check(cols == 8, label + ": 8 column bases on the colonnade (" + cols + ")");
			}
			case "chapel_nave" -> {
				FitPiece east = (FitPiece) ps.get(1);
				boolean chest = l.getBlockState(ChapelKit.altarChest(east)).is(Blocks.CHEST);
				int lanterns = (l.getBlockState(ChapelKit.lantern(east, -1)).is(Blocks.SOUL_LANTERN) ? 1 : 0) + (l.getBlockState(ChapelKit.lantern(east, 1)).is(Blocks.SOUL_LANTERN) ? 1 : 0);
				check(chest && lanterns == 2, label + ": altar chest and 2 soul lanterns in the chancel");
				// every planned water cell holds water, and its sides are walls or other planned water (nothing can flow out)
				int cells = 0, leaks = 0, dry = 0;
				FitPiece nave = (FitPiece) ps.get(0);
				int f = nave.data[0];
				var fr = nave.frame();
				for (int x = -9; x <= 9; x++) for (int z = -11; z <= 10; z++) for (int y = f - 2; y <= f; y++) {
					if (!ChapelKit.water(f, x, y, z)) continue;
					cells++;
					BlockState s = l.getBlockState(new BlockPos(fr.x(x, z), y, fr.z(x, z)));
					if (s.getFluidState().isEmpty()) dry++;
					int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
					for (int[] d : nb) {
						if (ChapelKit.water(f, x + d[0], y, z + d[1])) continue;
						BlockState t = l.getBlockState(new BlockPos(fr.x(x + d[0], z + d[1]), y, fr.z(x + d[0], z + d[1])));
						if (W.open(t)) leaks++;
					}
					if (W.open(l.getBlockState(new BlockPos(fr.x(x, z), y - 1, fr.z(x, z)))) && !ChapelKit.water(f, x, y - 1, z)) leaks++;
				}
				check(cells > 0 && leaks == 0 && dry <= cells / 10, SelfTest.f("%s: %d planned water cells, %d open sides or floors (leaks), %d without water (waterlogged pews and fittings)", label, cells, leaks, dry));
				int roofY = f + 11;
				check(l.getBlockState(new BlockPos(fr.x(0, 0), roofY, fr.z(0, 0))).is(Blocks.PALE_OAK_PLANKS)
					&& l.getBlockState(new BlockPos(fr.x(0, 4), roofY, fr.z(0, 4))).isAir(), label + ": pale-oak gable roof with its ridge at floor + 11 and the fallen-in bay open");
			}
			case "kiln_a" -> {
				FitPiece a = (FitPiece) ps.get(0), b = (FitPiece) ps.get(1);
				check(l.getBlockState(KilnKit.barrel(a)).is(Blocks.BARREL), label + ": the loot barrel is in the work room");
				var fr = a.frame();
				int fa = a.data[0];
				boolean ridge = l.getBlockState(new BlockPos(fr.x(0, -2), fa + 11, fr.z(0, -2))).is(Blocks.PALE_OAK_PLANKS);
				boolean chimney = l.getBlockState(new BlockPos(fr.x(-6, -2), fa + 13, fr.z(-6, -2))).is(BlockTags.STONE_BRICKS) || l.getBlockState(new BlockPos(fr.x(-6, -2), fa + 13, fr.z(-6, -2))).isSolid();
				check(ridge && chimney, label + ": steep 2:1 roof (ridge 7 above the wall top) and the chimney stack rising past it");
				check(Math.abs(a.data[0] - a.data[1]) <= 1, label + ": wings on their own floors, within one step (" + a.data[0] + " / " + b.data[1] + " / yard " + a.data[2] + ")");
				if (real) {
					BlockPos s = KilnKit.traderSpot(b);
					check(!l.getEntitiesOfClass(SapTapper.class, new AABB(s).inflate(16)).isEmpty(), label + ": the Sap Tapper was spawned at its spot");
				}
			}
			case "road" -> {
				FitPiece p = (FitPiece) ps.get(0);
				int[][] c = RoadKit.cells(p);
				int maxStep = 0, missing = 0, floatCells = 0, lanterns = 0, rift = 0;
				java.util.Set<Long> seen = new java.util.HashSet<>();
				for (int k = 0; k < c.length; k++) {
					if (k > 0) maxStep = Math.max(maxStep, Math.abs(c[k][2] - c[k - 1][2]));
					if (!seen.add(BlockPos.asLong(c[k][0], 0, c[k][1]))) continue; // a column the centreline revisits keeps its first height
					int dy = RoadKit.deckY(c[k][2]);
					BlockPos d = new BlockPos(c[k][0], dy, c[k][1]);
					BlockState top = l.getBlockState(d);
					if (W.open(top)) missing++;
					boolean timber = top.is(Blocks.PALE_OAK_PLANKS) || top.is(Blocks.PALE_OAK_SLAB);
					int support = (c[k][2] & 1) == 1 ? dy - 1 : dy;
					if (!timber && W.open(l.getBlockState(new BlockPos(c[k][0], support - 1, c[k][1])))) floatCells++;
				}
				BoundingBox b = p.getBoundingBox();
				for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int y = b.minY(); y <= b.maxY(); y++) {
					BlockState s = l.getBlockState(new BlockPos(x, y, z));
					if (s.is(Blocks.LANTERN)) lanterns++;
					if (s.is(ModBlocks.RETURN_RIFT)) rift++;
				}
				check(maxStep <= 2 && missing == 0 && floatCells == 0, SelfTest.f("%s: %d cells, max step %d half-blocks, %d missing deck cells, %d unsupported stone/earth cells", label, c.length, maxStep, missing, floatCells));
				check(lanterns >= 4, label + ": hanging-lantern posts along the road: " + lanterns);
				if (real) check((p.data[0] == 1) == (rift > 0) && PHSites.roadHasRift(p.origin) == (p.data[0] == 1), label + ": Return Rift pad follows the every-third rule (rift blocks " + rift + ")");
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ return rift ring
	private static void rift(ServerLevel hollow) {
		BlockPos a = ReturnRifts.nearestRift(hollow, 3000, -3000);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) hollow.getChunk((a.getX() >> 4) + dx, (a.getZ() >> 4) + dz, ChunkStatus.FULL, true);
		int ry = Integer.MIN_VALUE;
		for (int y = hollow.getMaxY() - 1; y > hollow.getMinY(); y--) if (hollow.getBlockState(new BlockPos(a.getX(), y, a.getZ())).is(ModBlocks.RETURN_RIFT)) {
			ry = y - 1;
			break;
		}
		check(ry != Integer.MIN_VALUE, "Return Rift ring: the rift stands at its anchor " + a.toShortString());
		if (ry == Integer.MIN_VALUE) return;
		int stones = 0, floating = 0, dais = 0;
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			BlockPos d = new BlockPos(a.getX() + dx, ry - 1, a.getZ() + dz);
			BlockState s = hollow.getBlockState(d);
			if (s.is(Blocks.CHISELED_TUFF_BRICKS) || s.is(Blocks.TUFF_BRICKS) || s.is(Blocks.POLISHED_TUFF)) {
				dais++;
				if (W.open(hollow.getBlockState(d.below()))) floating++;
			}
		}
		for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
			double r = Math.hypot(dx, dz);
			if (r < 3.5 || r > 7.5) continue;
			int lowest = Integer.MIN_VALUE;
			for (int y = ry - 10; y <= ry + 10; y++) {
				BlockState s = hollow.getBlockState(new BlockPos(a.getX() + dx, y, a.getZ() + dz));
				if (s.is(Blocks.TUFF_BRICKS) || s.is(Blocks.POLISHED_TUFF) || s.is(Blocks.CHISELED_TUFF_BRICKS) || s.is(Blocks.TUFF_BRICK_STAIRS)) {
					lowest = y;
					break;
				}
			}
			if (lowest == Integer.MIN_VALUE) continue;
			stones++;
			if (W.open(hollow.getBlockState(new BlockPos(a.getX() + dx, lowest - 1, a.getZ() + dz)))) floating++;
		}
		check(dais >= 20 && stones >= 6 && floating == 0, SelfTest.f("Return Rift ring at %s: dais %d blocks, %d ring-stone columns, %d floating", a.toShortString(), dais, stones, floating));
	}
}
