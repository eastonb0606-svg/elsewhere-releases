package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.FactionVillages;
import dev.elsewhere.kingdom_villages.MirewickLooks;
import dev.elsewhere.kingdom_villages.Plans;
import dev.elsewhere.kingdom_villages.RivalPlans;
import dev.elsewhere.kingdom_villages.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Self-test of the 3.0 Mirewick village look (s-vlook-mire, {@link MirewickLooks}), run from {@link RivalsTest#styleChecks}:
 * <ol>
 * <li>on the rebuilt Mirewick test village (a real plains village): purple/crimson/gold balance, gilt, no valuable blocks, no lit
 * campfire, the lantern budget, boardwalk legs that step with the ground without gaps, the sluice house's stilts, nothing floating;</li>
 * <li>on a synthetic polder site built here (a street over a 2-deep dip, a 1-block hump and a 4-block rise, the square on the slope so the
 * sluice house stands on uneven ground, a pond west of it): the real plan is written synchronously through {@link BlockWriter}, then the same terrain-fit rules plus the canal quay
 * (water level +1, bollards, market stall) and no flooding.</li>
 * </ol>
 */
public final class MirewickLooksTest {
	private MirewickLooksTest() {}

	static void check(String n, boolean ok, @Nullable Object d) {
		SelfTest.check("[mirewick-look] " + n, ok, d);
	}

	public static void run(ServerLevel level, @Nullable VillageRecord vm) {
		if (!MirewickLooks.ON) {
			SelfTest.info("[mirewick-look] switched off (-Delsewhere.overhaul3.village_look_mirewick=false): 2.1 look, checks skipped");
			return;
		}
		if (vm != null) village(level, vm);
		site(level);
	}

	// ---- shared checks ---------------------------------------------------------------------------------------------------

	static boolean any(BlockState s, Block... bs) {
		for (Block b : bs) if (s.is(b)) return true;
		return false;
	}

	/** Blocks of written components (6-connected, still standing) that touch nothing else: floating. */
	static int floating(ServerLevel level, VillageRecord r) {
		Set<Long> w = new HashSet<>();
		for (var e : r.journal.long2ObjectEntrySet()) {
			BlockState written = e.getValue().written();
			if (written.isAir()) continue;
			if (level.getBlockState(BlockPos.of(e.getLongKey())).getBlock() != written.getBlock()) continue;
			w.add(e.getLongKey());
		}
		Set<Long> seen = new HashSet<>();
		int bad = 0;
		for (long k : w) {
			if (!seen.add(k)) continue;
			ArrayDeque<Long> q = new ArrayDeque<>();
			q.add(k);
			boolean anchored = false;
			int size = 0;
			while (!q.isEmpty()) {
				BlockPos p = BlockPos.of(q.poll());
				size++;
				for (Direction d : Direction.values()) {
					BlockPos nb = p.relative(d);
					if (w.contains(nb.asLong())) {
						if (seen.add(nb.asLong())) q.add(nb.asLong());
					} else if (!level.getBlockState(nb).isAir()) anchored = true;
				}
			}
			if (!anchored) bad += size;
		}
		return bad;
	}

	/** Boardwalk legs: {legs, cells, max step, buried cells, gaps (deck block missing), raised cells without stilts, raised cells, stairs}. */
	static int[] legs(ServerLevel level, MirewickLooks.@Nullable Plan plan) {
		int[] out = new int[8];
		if (plan != null) for (int[][] leg : plan.legs()) {
			out[0]++;
			for (int i = 0; i < leg.length; i++) {
				int[] c = leg[i];
				out[1]++;
				if (i > 0) {
					int[] p = leg[i - 1];
					out[2] = Math.max(out[2], Math.abs(c[2] - p[2]));
					if (Math.abs(c[0] - p[0]) + Math.abs(c[1] - p[1]) != 1) out[4]++; // cells must be neighbours: no skipped cell
				}
				if (c[2] < c[3]) out[3]++;
				BlockState deck = level.getBlockState(new BlockPos(c[0], c[2], c[1]));
				if (!any(deck, Blocks.SMOOTH_STONE, Blocks.QUARTZ_STAIRS, Blocks.DYED_TERRACOTTA.purple())) out[4]++;
				if (deck.is(Blocks.QUARTZ_STAIRS)) out[7]++;
				if (!level.getBlockState(new BlockPos(c[0], c[2] + 1, c[1])).getCollisionShape(level, new BlockPos(c[0], c[2] + 1, c[1])).isEmpty()) out[3]++; // no headroom: buried
				if (c[2] > c[3]) {
					out[6]++;
					for (int y = c[3]; y < c[2]; y++) if (!level.getBlockState(new BlockPos(c[0], y, c[1])).is(Blocks.STRIPPED_MANGROVE_LOG)) {
						out[5]++;
						break;
					}
				}
			}
		}
		return out;
	}

	/** The sluice house: its four corner stilts run unbroken from the deck down to their own ground. */
	static boolean hutOnGround(ServerLevel level, MirewickLooks.@Nullable Plan plan) {
		int[] h = plan == null ? null : plan.hut();
		if (h == null) return false;
		if (!level.getBlockState(new BlockPos(h[0], h[2], h[1])).is(Blocks.SMOOTH_STONE)) return false;
		for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) {
			int x = h[0] + dx, z = h[1] + dz, y = h[2] - 1;
			int n = 0;
			while (y > level.getMinY() && level.getBlockState(new BlockPos(x, y, z)).is(Blocks.STRIPPED_MANGROVE_LOG)) {
				y--;
				n++;
			}
			BlockState under = level.getBlockState(new BlockPos(x, y, z));
			if (n < 2 || under.isAir() || !under.getFluidState().isEmpty()) return false;
		}
		return true;
	}

	// ---- 1. the rebuilt Mirewick test village ----------------------------------------------------------------------------

	static void village(ServerLevel level, VillageRecord vm) {
		MirewickLooks.Plan plan = MirewickLooks.plans.get(vm.id);
		int used = plan == null ? -1 : plan.lanterns();
		int purple = 0, crimson = 0, gold = 0;
		Set<Long> seen = new HashSet<>();
		for (VillageRecord.Piece pc : vm.pieces) {
			BoundingBox b = pc.box();
			for (int y = b.minY(); y <= b.maxY() + BlockWriter.UP; y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				if (!seen.add(BlockPos.asLong(x, y, z))) continue;
				BlockState s = level.getBlockState(new BlockPos(x, y, z));
				if (s.isAir()) continue;
				if (any(s, Blocks.DYED_TERRACOTTA.purple(), Blocks.STAINED_GLASS_PANE.purple(), Blocks.BANNER.purple(), Blocks.WALL_BANNER.purple(), Blocks.WOOL.purple())) purple++;
				else if (any(s, Blocks.CRIMSON_STEM, Blocks.CRIMSON_PLANKS, Blocks.CRIMSON_STAIRS, Blocks.CRIMSON_SLAB, Blocks.CRIMSON_FENCE, Blocks.CRIMSON_TRAPDOOR,
					Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICKS, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_LOG)) crimson++;
				else if (any(s, Blocks.DYED_TERRACOTTA.yellow(), Blocks.WOOL.yellow())) gold++;
			}
		}
		int all = Math.max(1, purple + crimson + gold);
		String mix = String.format("purple %d (%.0f%%) crimson %d (%.0f%%) gold %d (%.0f%%)", purple, 100.0 * purple / all, crimson, 100.0 * crimson / all, gold, 100.0 * gold / all);
		check("Mirewick village palette is purple, crimson and gold in balance, not mostly crimson (purple >= 30%, crimson 15-50%, gold >= 5%)",
			purple + crimson + gold >= 200 && purple * 100 >= 30 * all && crimson * 100 >= 15 * all && crimson * 100 < 50 * all && gold * 100 >= 5 * all, mix);
		int valuable = 0, campfires = 0, lanterns = 0, gilt = 0, fluids = 0;
		for (var e : vm.journal.long2ObjectEntrySet()) {
			BlockState w = e.getValue().written();
			if (any(w, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_IRON_BLOCK))
				valuable++;
			if (w.is(Blocks.CAMPFIRE) || w.is(Blocks.SOUL_CAMPFIRE)) campfires++;
			if (w.is(Blocks.LANTERN)) lanterns++;
			if (w.is(Blocks.DYED_TERRACOTTA.yellow())) gilt++;
			if (!w.getFluidState().isEmpty()) fluids++;
		}
		check("Mirewick village: no valuable blocks, no lit campfire, no fluid written (gold is yellow-terracotta gilt)", valuable == 0 && campfires == 0 && fluids == 0 && gilt >= 4,
			"valuable " + valuable + " campfires " + campfires + " fluids " + fluids + " gilt " + gilt);
		check("Mirewick village: lanterns are the signature but budgeted (3.." + (MirewickLooks.MAX_LANTERNS + 1) + ", border post included)",
			lanterns >= 3 && lanterns <= MirewickLooks.MAX_LANTERNS + 1 && plan != null && used <= MirewickLooks.MAX_LANTERNS, "lanterns " + lanterns + " budget used " + used);
		int[] l = legs(level, plan);
		check("Mirewick boardwalk quays follow the streets and step with the ground: steps <= 1, no gaps, never buried, stilts under raised spans",
			l[0] >= 1 && l[1] >= 16 && l[2] <= 1 && l[3] == 0 && l[4] == 0 && l[5] == 0,
			"legs " + l[0] + " cells " + l[1] + " max step " + l[2] + " buried " + l[3] + " gaps " + l[4] + " unstilted " + l[5] + " raised " + l[6] + " stairs " + l[7] + " | bell " + vm.center.toShortString() + " street start " + MirewickLooks.lastWalkStart + " cells " + MirewickLooks.lastWalkCells + " far " + MirewickLooks.lastWalkFar + " tried " + MirewickLooks.lastWalkTried);
		check("Mirewick sluice house stands on four mangrove stilts that each reach their own ground (the archer's post)",
			hutOnGround(level, plan) && vm.towerTop != null && plan != null && plan.hut() != null && vm.towerTop.getY() == plan.hut()[2] + 1, plan == null || plan.hut() == null ? "no hut" : java.util.Arrays.toString(plan.hut()));
		int fl = floating(level, vm);
		check("Mirewick village: nothing the rebuild placed floats (every written component touches the ground or the village)", fl == 0, fl + " floating blocks");
	}

	// ---- 2. the synthetic polder site ------------------------------------------------------------------------------------

	static int water(ServerLevel level, int x0, int z0, int x1, int z1) {
		int n = 0;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = -64; y <= -56; y++)
			if (!level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) n++;
		return n;
	}

	static void site(ServerLevel level) {
		int ox = 640, oz = 640, g = -61; // the flat test world: grass at -61, dirt to -63
		for (int cx = (ox - 48) >> 4; cx <= (ox + 64) >> 4; cx++) for (int cz = (oz - 48) >> 4; cz <= (oz + 48) >> 4; cz++) {
			level.setChunkForced(cx, cz, true);
			level.getChunk(cx, cz);
		}
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), air = Blocks.AIR.defaultBlockState(), path = Blocks.DIRT_PATH.defaultBlockState();
		// a rise to the east: +1 every 2 blocks from x = ox+8, up to +4
		for (int x = ox + 8; x <= ox + 44; x++) {
			int h = Math.min(4, (x - ox - 8) / 2 + 1);
			for (int z = oz - 14; z <= oz + 8; z++) {
				for (int y = g; y < g + h; y++) level.setBlock(new BlockPos(x, y, z), dirt, 2);
				level.setBlock(new BlockPos(x, g + h, z), grass, 2);
			}
		}
		// a 2-deep dip and a 1-block hump across the street
		for (int z = oz - 1; z <= oz + 1; z++) {
			level.setBlock(new BlockPos(ox - 4, g, z), air, 2);
			level.setBlock(new BlockPos(ox - 4, g - 1, z), air, 2);
			level.setBlock(new BlockPos(ox - 4, g - 2, z), grass, 2);
			level.setBlock(new BlockPos(ox + 2, g, z), dirt, 2);
			level.setBlock(new BlockPos(ox + 2, g + 1, z), grass, 2);
		}
		// the street: a dirt path along z = oz, on whatever ground is there
		for (int x = ox - 16; x <= ox + 36; x++) {
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, oz) - 1;
			level.setBlock(new BlockPos(x, top, oz), path, 2);
		}
		// a pond south of the square (water top = g, the land beside it at g: the quay stands at g+1)
		for (int x = ox - 18; x <= ox - 2; x++) for (int z = oz + 10; z <= oz + 19; z++) {
			level.setBlock(new BlockPos(x, g, z), Blocks.WATER.defaultBlockState(), 2);
			level.setBlock(new BlockPos(x, g - 1, z), Blocks.WATER.defaultBlockState(), 2);
		}
		int water0 = water(level, ox - 24, oz + 4, ox + 4, oz + 26);
		VillageRecord r = new VillageRecord();
		r.id = 990_001L;
		r.owner = RivalsTest.MIRE.toString();
		r.state = VillageRecord.State.OCCUPIED;
		r.center = new BlockPos(ox + 20, g + 5, oz); // the square on the rise: the sluice house lands on the slope, the quay on the pond 21+ blocks west
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(ox - 20, -64, oz - 12, ox - 6, -52, oz + 9), VillageRecord.CENTER));
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(ox - 20, -64, oz - 3, ox + 40, -52, oz + 3), VillageRecord.STREET));
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(ox + 4, -64, oz - 14, ox + 40, -50, oz - 4), VillageRecord.OTHER));
		r.bounds = new BoundingBox(ox - 20, -64, oz - 14, ox + 40, -50, oz + 9);
		Plans.Fortify f = RivalPlans.fortify(level, r, RandomSource.create(20261112L), FactionVillages.of(RivalsTest.MIRE));
		int refused = 0;
		for (VillageRecord.Op op : f.ops()) {
			BlockPos p = BlockPos.of(op.pos());
			if (!op.state().isAir() && !op.state().canSurvive(level, p) || !BlockWriter.write(level, r, p, op.state())) refused++;
		}
		MirewickLooks.Plan plan = MirewickLooks.plans.get(r.id);
		int[] l = legs(level, plan);
		check("synthetic polder: the boardwalk follows the street over a 2-deep dip, a hump and a 4-block rise in 1-steps (stairs), no gaps, never buried",
			l[0] >= 1 && l[1] >= 30 && l[2] <= 1 && l[3] == 0 && l[4] == 0 && l[5] == 0 && l[6] >= 1 && l[7] >= 4,
			"legs " + l[0] + " cells " + l[1] + " max step " + l[2] + " buried " + l[3] + " gaps " + l[4] + " unstilted " + l[5] + " raised " + l[6] + " stairs " + l[7]);
		check("synthetic polder: the sluice house's stilts each reach their own ground", hutOnGround(level, plan), plan == null || plan.hut() == null ? "no hut" : java.util.Arrays.toString(plan.hut()));
		int edge = 0, bollards = 0, awning = 0;
		BoundingBox q = plan == null ? null : plan.quay();
		if (q != null) for (int x = q.minX(); x <= q.maxX(); x++) for (int z = q.minZ(); z <= q.maxZ(); z++) for (int y = q.minY(); y <= q.maxY(); y++) {
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (s.is(Blocks.QUARTZ_BRICKS) && y == g + 1) for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(new BlockPos(x, g, z).relative(d)).is(Blocks.WATER)) {
				edge++;
				break;
			}
			if (s.is(Blocks.CRIMSON_FENCE) && level.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.QUARTZ_BRICKS)) bollards++;
			if (s.is(Blocks.WOOL.purple()) || s.is(Blocks.WOOL.yellow())) awning++;
		}
		boolean piece = r.pieces.stream().anyMatch(pc -> pc.type() == MirewickLooks.QUAY);
		check("synthetic polder: a canal quay follows the pond shore at water level +1 with bollards and a fish-market stall",
			q != null && piece && plan.quayWater() == g && edge >= 8 && bollards >= 3 && awning == 6, "edge " + edge + " bollards " + bollards + " awning " + awning + " box " + q);
		boolean fluidWritten = false;
		for (var e : r.journal.long2ObjectEntrySet()) if (!e.getValue().written().getFluidState().isEmpty()) fluidWritten = true;
		int water1 = water(level, ox - 24, oz + 4, ox + 4, oz + 26);
		check("synthetic polder: nothing floods (pond water unchanged, no fluid written)", water1 == water0 && !fluidWritten, water0 + " -> " + water1);
		int fl = floating(level, r);
		check("synthetic polder: every planned block was written (nothing refused, nothing buried) and nothing floats", refused == 0 && fl == 0 && r.journal.size() > 100,
			"ops " + f.ops().size() + " refused " + refused + " floating " + fl);
		SelfTest.info("[mirewick-look] synthetic site at " + ox + "," + oz + ": " + f.ops().size() + " ops, legs " + (plan == null ? 0 : plan.legs().size()) + ", hut " + (plan == null ? "-" : java.util.Arrays.toString(plan.hut())) + ", quay " + q);
	}

}
