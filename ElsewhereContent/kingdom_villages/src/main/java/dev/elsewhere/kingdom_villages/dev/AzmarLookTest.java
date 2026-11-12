package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom_villages.AzmarLooks;
import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.FactionVillages;
import dev.elsewhere.kingdom_villages.Plans;
import dev.elsewhere.kingdom_villages.RivalPlans;
import dev.elsewhere.kingdom_villages.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * [azmar-look] self-test for the 3.0 Azmar village look ({@link AzmarLooks}, s-vlook-azmar): run from {@link RivalsTest#styleChecks} once
 * the Azmar village VA is FORTIFIED. Checks the compound palm wall, flat roofs, obelisks, palms, that Azmar's is the smallest of the four
 * villages, that nothing floats or is dug in (in VA and on a synthetic hillside), no valuable blocks, and the Migdol outpost's ops.
 */
public final class AzmarLookTest {
	private AzmarLookTest() {}

	static final String P = "azmar-look: ";
	static ServerLevel level;

	static void check(String name, boolean ok, @Nullable Object detail) {
		SelfTest.check(P + name, ok, detail);
	}

	static FactionVillages.Profile azmar() {
		return FactionVillages.of(RivalsTest.AZMAR);
	}

	/** Blocks the look builds as masonry (what must stand on something and must never replace solid ground). */
	static boolean masonry(BlockState s) {
		return s.is(Blocks.SANDSTONE) || s.is(Blocks.CUT_SANDSTONE) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.CHISELED_SANDSTONE) || s.is(Blocks.MUD_BRICKS)
			|| s.is(Blocks.MUD_BRICK_STAIRS) || s.is(Blocks.SANDSTONE_STAIRS) || s.is(Blocks.SMOOTH_SANDSTONE_SLAB) || s.is(Blocks.CUT_SANDSTONE_SLAB)
			|| s.is(Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE)) || s.is(Blocks.GLAZED_TERRACOTTA.yellow());
	}

	static boolean valuable(BlockState s) {
		return s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.LAPIS_BLOCK) || s.is(Blocks.DIAMOND_BLOCK) || s.is(Blocks.EMERALD_BLOCK) || s.is(Blocks.IRON_BLOCK)
			|| s.is(Blocks.RAW_GOLD_BLOCK) || s.is(Blocks.NETHERITE_BLOCK) || s.is(Blocks.AMETHYST_BLOCK);
	}

	static boolean inHouse(VillageRecord r, BlockPos p) {
		for (VillageRecord.Piece pc : r.pieces) if ((pc.type() == VillageRecord.HOUSE || pc.type() == VillageRecord.CENTER) && pc.box().isInside(p)) return true;
		return false;
	}

	/** {dug, floating, masonry columns}: masonry the look wrote in its wall boxes that replaced something solid, or has air under its lowest block. */
	static int[] fit(VillageRecord r) {
		int dug = 0, floating = 0;
		Map<Long, Integer> lowest = new HashMap<>();
		for (var e : r.journal.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			BlockState w = e.getValue().written();
			if (!masonry(w) || !AzmarLooks.inWall(r, p) || inHouse(r, p)) continue;
			if (!AzmarLooks.isSoft(e.getValue().original())) dug++;
			lowest.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), p.getY(), Math::min);
		}
		for (var e : lowest.entrySet()) {
			BlockPos col = BlockPos.of(e.getKey());
			BlockState below = level.getBlockState(new BlockPos(col.getX(), e.getValue() - 1, col.getZ()));
			if (below.isAir() || !below.getFluidState().isEmpty()) floating++;
		}
		return new int[] {dug, floating, lowest.size()};
	}

	/** {built columns, distinct course counts, min ground, max ground, max cap step between neighbours}. */
	static int[] wall(AzmarLooks.Stats st) {
		int built = 0, gmin = Integer.MAX_VALUE, gmax = Integer.MIN_VALUE, step = 0;
		Set<Integer> courses = new TreeSet<>();
		int[] prev = null;
		for (int[] w : st.wall) {
			int x = w[0], z = w[1], g = w[2], n = w[3];
			boolean py = w[4] == 1;
			BlockState cap = level.getBlockState(new BlockPos(x, g + n + 1, z)), first = level.getBlockState(new BlockPos(x, g + 1, z)), ground = level.getBlockState(new BlockPos(x, g, z));
			boolean ok = (py ? cap.is(Blocks.CUT_SANDSTONE) : cap.is(Blocks.SMOOTH_SANDSTONE_SLAB) || cap.is(Blocks.SANDSTONE) || cap.is(Blocks.CHISELED_SANDSTONE))
				&& (py ? first.is(Blocks.CUT_SANDSTONE) : first.is(Blocks.SANDSTONE)) && !ground.isAir() && !AzmarLooks.isSoft(ground);
			if (ok) built++;
			gmin = Math.min(gmin, g);
			gmax = Math.max(gmax, g);
			if (py) { // pylons stand taller on purpose
				prev = null;
				continue;
			}
			courses.add(n);
			if (prev != null && Math.abs(prev[0] - x) + Math.abs(prev[1] - z) == 1) step = Math.max(step, Math.abs(prev[2] + prev[3] - g - n));
			prev = w;
		}
		return new int[] {built, courses.size(), gmin, gmax, step};
	}

	static long area(VillageRecord r) {
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (VillageRecord.Piece pc : r.pieces) {
			int t = pc.type();
			if (t != VillageRecord.HOUSE && t != VillageRecord.STREET && t != VillageRecord.CENTER && t != VillageRecord.OTHER) continue;
			x0 = Math.min(x0, pc.box().minX());
			z0 = Math.min(z0, pc.box().minZ());
			x1 = Math.max(x1, pc.box().maxX());
			z1 = Math.max(z1, pc.box().maxZ());
		}
		return x1 < x0 ? 0 : (long) (x1 - x0 + 1) * (z1 - z0 + 1);
	}

	// ---- the rebuilt Azmar village (VA) --------------------------------------------------------------------------------------

	public static void village(ServerLevel l, VillageRecord va, List<VillageRecord> others) {
		level = l;
		if (!AzmarLooks.enabled()) {
			SelfTest.info(P + "kill switch off (-Delsewhere.overhaul3.village_look_azmar=false): the 2.1 Azmar rebuild ran; look checks skipped");
			return;
		}
		AzmarLooks.Stats st = AzmarLooks.lastStats;
		SelfTest.info(P + "VA " + st);
		check("the last Azmar rebuild planned is VA's", st.id == va.id, st.id + " vs " + va.id);
		int[] w = wall(st);
		check("compound palm wall round the bell: mud brick on a sandstone plinth, smooth caps, pylon gates at the streets",
			st.wall.size() >= 2 * st.radius && w[0] * 10 >= st.wall.size() * 9 && st.gates >= 1 && st.pylons >= 2,
			"columns " + st.wall.size() + " built " + w[0] + " R " + st.radius + " gates " + st.gates + " pylons " + st.pylons);
		check("wall top undulates (pan-bedded courses) and steps with the ground", w[1] >= 2 && w[4] <= 3, "course counts " + w[1] + " ground " + w[2] + ".." + w[3] + " max cap step " + w[4]);
		// flat roofs, no glass, no wood showing in the compound
		int pitched = 0, cornice = 0, parapet = 0, wood = 0, glass = 0;
		for (int[] rf : st.roofs) {
			int H = rf[4];
			for (int x = rf[0]; x <= rf[2]; x++) for (int z = rf[1]; z <= rf[3]; z++) {
				BlockState at = level.getBlockState(new BlockPos(x, H, z));
				if (at.is(Blocks.SANDSTONE_STAIRS) && at.getValue(StairBlock.HALF) == Half.TOP) cornice++;
				if (level.getBlockState(new BlockPos(x, H + 1, z)).is(Blocks.CUT_SANDSTONE_SLAB)) parapet++;
				for (int y = H + 1; y <= rf[5] + BlockWriter.UP; y++) if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.STAIRS)) pitched++;
			}
		}
		for (BoundingBox b : st.houses) for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)) wood++;
			if (s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)) glass++;
		}
		check("compound houses get Egyptian flat roofs (cavetto cornice, parapet, no pitched roof left)", st.flattened >= 1 && pitched == 0 && cornice >= 4 && parapet >= 4,
			"flat " + st.flattened + "/" + st.compound + " stairs above eaves " + pitched + " cornice " + cornice + " parapet " + parapet);
		check("compound houses are mud brick and sandstone: no glass, no wooden stairs or slabs", st.truncated || wood == 0 && glass == 0, "wood " + wood + " glass " + glass + (st.truncated ? " (plan truncated)" : ""));
		check("roof wind-catchers or striped awnings, and the watch tower", st.windCatchers + st.awnings >= 1 && st.tower != null,
			"windcatchers " + st.windCatchers + " awnings " + st.awnings + " tower " + st.tower);
		// obelisks and the hint
		BlockPos h = va.hint;
		boolean tip = h != null && level.getBlockState(h).is(Blocks.GLAZED_TERRACOTTA.yellow()) && level.getBlockState(h.below()).is(Blocks.CUT_SANDSTONE)
			&& level.getBlockState(h.below(2)).is(Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE));
		boolean outside = h != null && st.bell != null && Math.max(Math.abs(h.getX() - st.bell.getX()), Math.abs(h.getZ() - st.bell.getZ())) > st.radius;
		check("an obelisk pair flanks the gate outside the wall; the border hint is a pyramidion (yellow terracotta over a blue faience band)",
			st.obelisks >= 2 && tip && outside, "obelisks " + st.obelisks + " hint " + h + " tip " + tip + " outside " + outside);
		int leaves = 0, logs = 0;
		for (var e : va.journal.long2ObjectEntrySet()) {
			BlockState s = e.getValue().written();
			if (s.is(Blocks.JUNGLE_LEAVES)) leaves++;
			if (s.is(Blocks.JUNGLE_LOG)) logs++;
		}
		check("palms along the inside of the wall and in the court", st.palms >= 3 && leaves >= 30 && logs >= 12, "palms " + st.palms + " fronds " + leaves + " trunk logs " + logs);
		// no valuable blocks
		int val = 0;
		for (var e : va.journal.long2ObjectEntrySet()) if (valuable(e.getValue().written())) val++;
		int valWorld = RivalsTest.countTag(va, AzmarLookTest::valuable);
		check("no valuable blocks: gold is yellow terracotta, lapis is blue terracotta (none written, none standing)", val == 0 && valWorld == 0, "written " + val + " standing " + valWorld);
		// terrain fit
		int[] f = fit(va);
		check("terrain-fitted: wall, obelisks and palms never replace solid ground and nothing floats", f[0] == 0 && f[1] == 0 && f[2] >= st.wall.size() / 2, "dug " + f[0] + " floating " + f[1] + " columns " + f[2]);
		// the smallest of the four
		long own = area(va), compound = st.footprint, min = Long.MAX_VALUE;
		StringBuilder sizes = new StringBuilder();
		for (VillageRecord o : others) {
			if (o == null) continue;
			min = Math.min(min, area(o));
			sizes.append(' ').append(o.owner).append(" area ").append(area(o)).append(" journal ").append(o.journal.size());
		}
		SelfTest.info(P + "sizes: Azmar compound " + compound + " (village " + own + ") journal " + va.journal.size() + " ops " + st.ops + " |" + sizes);
		check("the smallest of the four kingdoms' villages: Azmar walls a compound smaller than its own village and every rival's, under the rivals' block cap",
			compound < own && compound < min && st.ops <= AzmarLooks.MAX_OPS && AzmarLooks.MAX_OPS < RivalPlans.MAX_FORTIFY,
			"compound " + compound + " own " + own + " smallest rival " + min + " ops " + st.ops);
	}

	// ---- a synthetic hillside: the wall follows a slope ------------------------------------------------------------------------

	public static void hillside(ServerLevel l) {
		level = l;
		if (!AzmarLooks.enabled()) return;
		int x0 = 640, z0 = 1920, half = 30;
		for (int cx = (x0 - half - 8) >> 4; cx <= (x0 + half + 8) >> 4; cx++) for (int cz = (z0 - half - 8) >> 4; cz <= (z0 + half + 8) >> 4; cz++) {
			l.setChunkForced(cx, cz, true);
			l.getChunk(cx, cz);
		}
		int base = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0) - 1;
		for (int x = x0 - half; x < x0 + half; x++) for (int z = z0 - half; z < z0 + half; z++) {
			int top = base + Math.floorDiv(x - (x0 - half), 5) + Math.floorDiv(z - (z0 - half), 12);
			boolean path = Math.abs(x - x0) <= 1 || Math.abs(z - z0) <= 1;
			for (int y = base - 2; y <= top + 24; y++) {
				BlockState s = y < top ? Blocks.DIRT.defaultBlockState() : y == top ? (path ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK).defaultBlockState() : Blocks.AIR.defaultBlockState();
				l.setBlock(new BlockPos(x, y, z), s, BlockWriter.FLAGS);
			}
		}
		int cTop = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
		VillageRecord r = new VillageRecord();
		r.id = 0x5A5A_0001L;
		r.owner = RivalsTest.AZMAR.toString();
		r.center = new BlockPos(x0, cTop, z0);
		r.castle = new BlockPos(x0, cTop, z0 + 600); // the realm lies south
		int lo = base - 3, hi = base + 40;
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(x0 - 3, lo, z0 - 3, x0 + 3, hi, z0 + 3), VillageRecord.CENTER));
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(x0 - 1, lo, z0 - half, x0 + 1, hi, z0 + half - 1), VillageRecord.STREET));
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(x0 - half, lo, z0 - 1, x0 + half - 1, hi, z0 + 1), VillageRecord.STREET));
		Plans.Fortify f = AzmarLooks.fortify(l, r, RandomSource.create(7), azmar());
		for (VillageRecord.Op o : f.ops()) BlockWriter.write(l, r, BlockPos.of(o.pos()), o.state());
		AzmarLooks.Stats st = AzmarLooks.lastStats;
		SelfTest.info(P + "hillside " + st);
		int[] w = wall(st), fit = fit(r);
		boolean ownGround = w[0] == st.wall.size();
		check("hillside: the wall climbs the slope column by column, each on its own ground (span >= 4, cap steps <= 2)",
			w[3] - w[2] >= 4 && ownGround && w[4] <= 2 && st.wall.size() >= 4 * st.radius, "ground " + w[2] + ".." + w[3] + " built " + w[0] + "/" + st.wall.size() + " max step " + w[4]);
		check("hillside: nothing floating, nothing dug in", fit[0] == 0 && fit[1] == 0 && fit[2] > 0, "dug " + fit[0] + " floating " + fit[1] + " columns " + fit[2]);
		BlockPos h = AzmarLooks.lastHint;
		check("hillside: four street gates, an obelisk pair at the gate facing the realm (south) carrying the hint",
			st.gates >= 4 && st.obelisks >= 2 && h != null && h.getZ() > z0 + st.radius && l.getBlockState(h).is(Blocks.GLAZED_TERRACOTTA.yellow()),
			"gates " + st.gates + " obelisks " + st.obelisks + " hint " + h);
		BlockWriter.restore(l, r);
		for (int cx = (x0 - half - 8) >> 4; cx <= (x0 + half + 8) >> 4; cx++) for (int cz = (z0 - half - 8) >> 4; cz <= (z0 + half + 8) >> 4; cz++) l.setChunkForced(cx, cz, false);
	}

	// ---- the Migdol outpost (pure ops) -------------------------------------------------------------------------------------------

	public static void outpost() {
		FactionVillages.Profile p = azmar();
		BlockPos c = new BlockPos(0, 64, 0);
		int ok = 0;
		StringBuilder bad = new StringBuilder();
		for (Direction gate : Direction.Plane.HORIZONTAL) {
			List<VillageRecord.Op> ops = RivalPlans.outpost(p, c, gate);
			Map<Long, BlockState> m = new HashMap<>();
			for (VillageRecord.Op o : ops) m.putIfAbsent(o.pos(), o.state());
			boolean tip = RivalPlans.hintPos(p, c).equals(c.offset(3, 7, 3)) && is(m, c.offset(3, 7, 3), Blocks.GLAZED_TERRACOTTA.yellow());
			int tx = gate.getStepX() != 0 ? -2 * gate.getStepX() : -2, tz = gate.getStepZ() != 0 ? -2 * gate.getStepZ() : -2;
			BlockPos t = c.offset(tx, 0, tz);
			int batter = 0, deck = 0, cornice = 0;
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				if (is(m, t.offset(dx, 0, dz), Blocks.MUD_BRICK_STAIRS)) batter++;
				if (is(m, t.offset(dx, 5, dz), Blocks.SMOOTH_SANDSTONE)) deck++;
				BlockState cs = m.get(t.offset(dx, 5, dz).asLong());
				if (cs != null && cs.is(Blocks.SANDSTONE_STAIRS) && cs.getValue(StairBlock.HALF) == Half.TOP) cornice++;
			}
			int unfooted = 0, logs = 0, valuables = 0;
			for (var e : m.entrySet()) {
				BlockPos q = BlockPos.of(e.getKey());
				if (q.getY() == c.getY() && masonry(e.getValue()) && !m.containsKey(q.below().asLong())) unfooted++;
				if (e.getValue().is(Blocks.JUNGLE_LOG)) logs++;
				if (valuable(e.getValue())) valuables++;
			}
			boolean gap = true;
			for (int lat = -1; lat <= 1; lat++) for (int y = 0; y <= 4; y++) {
				BlockPos g = c.offset(5 * gate.getStepX() + (gate.getStepX() == 0 ? lat : 0), y, 5 * gate.getStepZ() + (gate.getStepZ() == 0 ? lat : 0));
				if (m.containsKey(g.asLong())) gap = false;
			}
			boolean good = tip && batter == 12 && deck == 9 && cornice == 12 && unfooted == 0 && logs >= 5 && valuables == 0 && gap;
			if (good) ok++;
			else bad.append(gate).append(": tip ").append(tip).append(" batter ").append(batter).append(" deck ").append(deck).append(" cornice ").append(cornice)
				.append(" unfooted ").append(unfooted).append(" logs ").append(logs).append(" valuables ").append(valuables).append(" gap ").append(gap).append("; ");
		}
		check("Migdol outpost: battered tower with cavetto crown, walled court, palm, obelisk hint at c+(3,7,3), every column footed, open gate (all four facings)",
			!AzmarLooks.enabled() || ok == 4, ok + "/4 " + bad);
	}

	static boolean is(Map<Long, BlockState> m, BlockPos p, Block b) {
		BlockState s = m.get(p.asLong());
		return s != null && s.is(b);
	}
}
