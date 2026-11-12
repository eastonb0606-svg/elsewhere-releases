package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.HrafnLooks;
import dev.elsewhere.kingdom_villages.RivalPlans;
import dev.elsewhere.kingdom_villages.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Self-test of the 3.0 Hrafnholt village look (s-vlook-hraf, {@link HrafnLooks}), run from {@link RivalsTest#styleChecks}:
 * <ol>
 * <li>on the rebuilt Hrafnholt test village (a real plains village with a shelving pool east of it): the longhall, watch tower,
 * thing-stead, runestones, tofts, palisade gates, green and brown banners, the waterfront (jetty on piles, naust, strand boat), every
 * member whole on its footing, nothing buried, no valuable blocks, and the assembler's {@link FitAudit};</li>
 * <li>on a synthetic 1-in-4 hillside built here: the real longhall planner and writer, which must site the hall along the contours on a
 * plinth one above the highest ground (nothing cut in, nothing hanging), with the same fit rules.</li>
 * </ol>
 */
public final class HrafnLooksTest {
	private HrafnLooksTest() {}

	static final int NONE = Integer.MIN_VALUE;

	static void check(String n, boolean ok, @Nullable Object d) {
		SelfTest.check("[hrafn-look] " + n, ok, d);
	}

	public static void checks(ServerLevel level, VillageRecord vh, BlockPos poolOrigin, int poolX, int poolZ) {
		if (!HrafnLooks.ON) {
			SelfTest.info("[hrafn-look] switched off (-Delsewhere.overhaul3.hrafn_village_looks=false): 2.1 look, checks skipped");
			return;
		}
		village(level, vh, poolOrigin, poolX, poolZ);
		slope(level);
	}

	/** A shelving pool dug into the ground at {@code origin} ({@code sx} along x, 1 then 2 then 3 deep eastward, {@code sz} along z): the waterfront's test water. */
	public static void pool(ServerLevel level, BlockPos origin, int sx, int sz) {
		for (int px = 0; px < sx; px++) for (int pz = 0; pz < sz; pz++) {
			int x = origin.getX() + px, z = origin.getZ() + pz, top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			// fx-hrafwater: a plant left standing on the dug cell would hide the water from the surface heightmap
			for (int py = top + 1; py <= top + 2; py++) if (!level.getBlockState(new BlockPos(x, py, z)).isAir()) level.setBlock(new BlockPos(x, py, z), Blocks.AIR.defaultBlockState(), 2);
			for (int py = top; py > top - (px < 4 ? 1 : px < 8 ? 2 : 3); py--) level.setBlock(new BlockPos(x, py, z), Blocks.WATER.defaultBlockState(), 2);
		}
	}

	// ---- shared fit checks ---------------------------------------------------------------------------------------------

	/** First member column that has a gap, fluid or nothing solid under it, or null when all stand whole. */
	static @Nullable String unfit(ServerLevel level, HrafnLooks.Report rep) {
		for (int[] c : rep.columns) {
			for (int y = c[2]; y <= c[3]; y++) {
				BlockState s = level.getBlockState(new BlockPos(c[0], y, c[1]));
				if (s.isAir() || !s.getFluidState().isEmpty()) return "gap at " + c[0] + " " + y + " " + c[1] + " (" + s + ")";
			}
			BlockState under = level.getBlockState(new BlockPos(c[0], c[2] - 1, c[1]));
			if (!under.isSolid() || !under.getFluidState().isEmpty()) return "floating at " + c[0] + " " + c[2] + " " + c[1] + " over " + under;
		}
		return null;
	}

	static int shut(ServerLevel level, HrafnLooks.Report rep) {
		int n = 0;
		for (BlockPos p : rep.open) if (!level.getBlockState(p).isAir()) n++;
		return n;
	}

	static void audit(ServerLevel level, VillageRecord r, String what) {
		FitAudit.Result a = FitAudit.audit(level, r, what, null);
		check(what + ": FitAudit finds nothing floating, unsupported or unable to survive", a.floatingOk(), a.floating());
		check(what + ": FitAudit finds nothing buried (faces at most 5%, no door blocked) and cuts at most " + FitAudit.MAX_CUT + " deep", a.buriedOk() && a.cutOk(),
			a.buried() + ", deepest cut " + a.maxCut);
		check(what + ": no valuable blocks and no fluid written", a.valuableOk() && a.fluidWritten == 0, a.valuables + " valuables, fluid " + a.fluidWritten);
	}

	static boolean is(BlockState s, DyeColor c) {
		return s.is(Blocks.BANNER.pick(c)) || s.is(Blocks.WALL_BANNER.pick(c));
	}

	// ---- the rebuilt village -------------------------------------------------------------------------------------------

	static void village(ServerLevel level, VillageRecord vh, BlockPos poolOrigin, int poolX, int poolZ) {
		HrafnLooks.Report rep = HrafnLooks.report(vh.id);
		check("the rebuild planned the 3.0 look (plan report kept)", rep != null, vh.id);
		if (rep == null) return;
		int moss = SelfTest.count(vh, Blocks.MOSS_BLOCK, BlockWriter.UP), bone = SelfTest.count(vh, Blocks.BONE_BLOCK, BlockWriter.UP), dark = SelfTest.count(vh, Blocks.DARK_OAK_LOG, BlockWriter.UP);
		check("a bowed longhall on a plinth: turf roof, bone finials, dark-oak posts, hearth", rep.hallFloor != NONE && rep.hallLen >= 9 && moss >= 30 && bone >= 2 && dark >= 10,
			"floor " + rep.hallFloor + " len " + rep.hallLen + " moss " + moss + " bone " + bone + " dark-oak " + dark);
		String bad = unfit(level, rep);
		check("nothing floating: every post, wall, pile, stone and fence stands whole on solid ground (" + rep.columns.size() + " members)", bad == null, bad);
		int shut = shut(level, rep);
		check("nothing buried: the hall floor is one above the highest ground under it and the hall stays open inside", rep.hallFloor == rep.hallMaxGround + 1 && shut == 0,
			"floor " + rep.hallFloor + " ground " + rep.hallMinGround + ".." + rep.hallMaxGround + ", blocked cells " + shut);
		audit(level, vh, "Hrafnholt village");
		// banners: green and brown, alternating, at most MAX_BANNERS added
		int green = 0, brown = 0, added = 0, wool = 0;
		for (var e : vh.journal.long2ObjectEntrySet()) {
			BlockState w = e.getValue().written(), o = e.getValue().original();
			if (is(w, DyeColor.GREEN)) green++;
			if (is(w, DyeColor.BROWN)) brown++;
			Block wb = w.getBlock();
			if ((wb instanceof BannerBlock || wb instanceof WallBannerBlock) && !(o.getBlock() instanceof BannerBlock || o.getBlock() instanceof WallBannerBlock)) added++;
			if (w.is(Blocks.WOOL.pick(DyeColor.GREEN)) || w.is(Blocks.WOOL.pick(DyeColor.BROWN))) wool++;
		}
		check("green and brown banners, at most " + HrafnLooks.MAX_BANNERS + " added", green >= 1 && brown >= 1 && added <= HrafnLooks.MAX_BANNERS, "green " + green + " brown " + brown + " added " + added);
		// the waterfront extends the 2.1 pier: one pier box, jetty on piles to the bed, naust astride the shore, a boat drawn up on the strand
		long pierBoxes = vh.pieces.stream().filter(pc -> pc.type() == RivalPlans.PIER).count();
		BlockPos pier = rep.spots.get("pier"), boat = rep.spots.get("strand_boat");
		boolean inPool = pier != null && pier.getX() >= poolOrigin.getX() && pier.getX() < poolOrigin.getX() + poolX && pier.getZ() >= poolOrigin.getZ() && pier.getZ() < poolOrigin.getZ() + poolZ;
		check("waterfront: one pier box with a jetty on piles to the bed, a naust (boathouse) astride the shore", pierBoxes == 1 && inPool && rep.piles >= 4 && rep.boathouse,
			"pier boxes " + pierBoxes + " pier " + pier + " piles " + rep.piles + " naust " + rep.boathouse + (rep.naustWhy.isEmpty() ? "" : " (" + rep.naustWhy + ")"));
		boolean dry = boat != null && level.getBlockState(boat.below()).isSolid() && level.getFluidState(boat.below()).isEmpty();
		check("a boat drawn up on the strand (on dry ground) with a green-and-brown sail", rep.boat && dry && wool >= 9, "boat " + boat + " sail wool " + wool);
		// the village's own features
		BlockPos ting = rep.spots.get("thingstead");
		check("a thing-stead: eight standing stones round a mossy law rock", rep.stones == 8 && ting != null && level.getBlockState(ting).is(Blocks.MOSSY_COBBLESTONE), "stones " + rep.stones + " at " + ting);
		check("a runestone at every road fork", rep.runestones == rep.forks, rep.runestones + " of " + rep.forks + " forks");
		int gates = SelfTest.count(vh, Blocks.SPRUCE_FENCE_GATE, BlockWriter.UP), hay = SelfTest.count(vh, Blocks.HAY_BLOCK, BlockWriter.UP);
		check("fenced tofts beside the houses (gate, drying rack, hay, woodpile)", rep.tofts >= 1 && gates >= rep.tofts && hay >= 2 * rep.tofts, "tofts " + rep.tofts + " gates " + gates + " hay " + hay);
		check("a watch tower with a hoarding gallery, and gates in the palisade (lintel gate or water gate)",
			rep.spots.containsKey("watchtower") && (rep.spots.containsKey("gate") || rep.spots.containsKey("water_gate")), rep.spots.keySet());
	}

	// ---- a hillside ----------------------------------------------------------------------------------------------------

	/** A 48 x 48 hillside rising one block every four eastward, far from the test villages; one big piece owned by Hrafnholt. */
	static void slope(ServerLevel level) {
		int x0 = -640, z0 = -640, n = 48;
		for (int cx = x0 >> 4; cx <= (x0 + n) >> 4; cx++) for (int cz = z0 >> 4; cz <= (z0 + n) >> 4; cz++) {
			level.setChunkForced(cx, cz, true);
			level.getChunk(cx, cz);
		}
		for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) {
			int top = -61 + x / 4;
			for (int y = -61; y <= top; y++) level.setBlock(new BlockPos(x0 + x, y, z0 + z), (y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
		}
		VillageRecord r = new VillageRecord();
		r.id = -73_000_001L;
		r.owner = RivalsTest.HRAF.toString();
		r.kind = VillageRecord.Kind.VILLAGE;
		r.center = new BlockPos(x0 + n / 2, -61 + n / 8, z0 + n / 2);
		r.bounds = new BoundingBox(x0, -64, z0, x0 + n - 1, -30, z0 + n - 1);
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(x0 + 1, -64, z0 + 1, x0 + n - 2, -40, z0 + n - 2), VillageRecord.OTHER));
		HrafnLooks.Report rep = HrafnLooks.testLonghall(level, r, r.center);
		check("hillside: a longhall is sited on a 1-in-4 slope", rep.hallFloor != NONE, rep.spots);
		if (rep.hallFloor != NONE) {
			check("hillside: the hall runs along the contours on a plinth, floor one above the highest ground (the fall is filled, nothing cut in)",
				rep.hallAlongContour && rep.hallFloor == rep.hallMaxGround + 1 && rep.hallMaxGround - rep.hallMinGround >= 2,
				"along " + rep.hallAlongContour + " floor " + rep.hallFloor + " ground " + rep.hallMinGround + ".." + rep.hallMaxGround);
			String bad = unfit(level, rep);
			int shut = shut(level, rep);
			check("hillside: every member stands whole on ground and the hall stays open inside", bad == null && shut == 0, bad + ", blocked " + shut);
			audit(level, r, "hillside hall");
		}
		for (int cx = x0 >> 4; cx <= (x0 + n) >> 4; cx++) for (int cz = z0 >> 4; cz <= (z0 + n) >> 4; cz++) level.setChunkForced(cx, cz, false);
	}
}
