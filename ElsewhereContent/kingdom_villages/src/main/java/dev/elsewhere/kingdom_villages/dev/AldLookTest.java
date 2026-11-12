package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.Plans;
import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.VillagesContent;
import dev.elsewhere.kingdom_villages.s3.ald.AldFort;
import dev.elsewhere.kingdom_villages.s3.ald.AldHamlet;
import dev.elsewhere.kingdom_villages.s3.ald.AldLook;
import dev.elsewhere.kingdom_villages.s3.ald.AldRebuild;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [ald-look] self-test for the 3.0 Aldermark looks (s-vlook-ald): run once from {@code SelfTest.conquest()} (B is liberated, its
 * rebuild still stands). Checks B's rebuild in place, then builds three scenes on sculpted hills ({@link AldLookScenes}): a village
 * on a hill flank (watchtower on the high point, palisade on every column's own ground, gatehouses), the motte-and-bailey outpost and
 * the burnt farmstead; each must stand on the ground (nothing floating, nothing carved), keep its budgets and undo completely.
 */
public final class AldLookTest {
	private AldLookTest() {}

	static final String P = "ald-look: ";
	static ServerLevel level;

	static void check(String name, boolean ok, @Nullable Object detail) {
		SelfTest.check(P + name, ok, detail);
	}

	static boolean valuable(BlockState s) {
		return s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.LAPIS_BLOCK) || s.is(Blocks.DIAMOND_BLOCK) || s.is(Blocks.EMERALD_BLOCK) || s.is(Blocks.IRON_BLOCK)
			|| s.is(Blocks.RAW_GOLD_BLOCK) || s.is(Blocks.RAW_IRON_BLOCK) || s.is(Blocks.NETHERITE_BLOCK) || s.is(Blocks.AMETHYST_BLOCK);
	}

	static boolean light(BlockState s) {
		return s.is(Blocks.LANTERN) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT) || s.is(Blocks.GLOWSTONE);
	}

	/** Budget readout of a list of ops: {banners, blue banners, block entities, lights, lit fires, valuables}. */
	static int[] budget(List<VillageRecord.Op> ops) {
		int[] n = new int[6];
		for (VillageRecord.Op o : ops) {
			BlockState s = o.state();
			if (s.getBlock() instanceof BannerBlock) n[0]++;
			if (s.is(Blocks.BANNER.blue())) n[1]++;
			if (s.hasBlockEntity()) n[2]++;
			if (light(s)) n[3]++;
			if (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT)) n[4]++;
			if (valuable(s)) n[5]++;
		}
		return n;
	}

	/**
	 * Written blocks (still standing) that touch nothing solid outside the build through a chain of other written blocks: they float.
	 * A block is anchored when a face neighbour is a non-air block the build did not write (ground, an original wall).
	 */
	static int floating(VillageRecord r, List<BlockPos> sample) {
		LongOpenHashSet ours = new LongOpenHashSet();
		for (var e : r.journal.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			BlockState cur = level.getBlockState(p);
			if (!cur.isAir() && cur == e.getValue().written()) ours.add(e.getLongKey());
		}
		LongOpenHashSet anchored = new LongOpenHashSet();
		ArrayDeque<Long> q = new ArrayDeque<>();
		for (long k : ours) {
			BlockPos p = BlockPos.of(k);
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (ours.contains(n.asLong())) continue;
				BlockState s = level.getBlockState(n);
				if (!s.isAir() && !s.canBeReplaced()) {
					anchored.add(k);
					q.add(k);
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			BlockPos p = BlockPos.of(q.poll());
			for (Direction d : Direction.values()) {
				long n = p.relative(d).asLong();
				if (ours.contains(n) && anchored.add(n)) q.add(n);
			}
		}
		int bad = 0;
		for (long k : ours) if (!anchored.contains(k)) {
			bad++;
			if (sample.size() < 4) sample.add(BlockPos.of(k));
		}
		return bad;
	}

	/** The ground height at (x, z) as it was before {@code r} was written (journaled cells read as their originals). */
	static int groundBefore(VillageRecord r, int x, int z) {
		for (int y = level.getMaxY(); y > level.getMinY(); y--) {
			BlockPos p = new BlockPos(x, y, z);
			VillageRecord.Change ch = r.journal.get(p.asLong());
			BlockState s = ch != null ? ch.original() : level.getBlockState(p);
			if ((s.isSolid() || !s.getFluidState().isEmpty()) && !s.is(BlockTags.LEAVES)) return y;
		}
		return Integer.MIN_VALUE;
	}

	/** Natural ground the build replaced by anything but a track or scorched earth (a carve or a box clear). */
	static int carved(VillageRecord r) {
		int n = 0;
		for (var e : r.journal.values()) {
			BlockState o = e.original(), w = e.written();
			if (!AldLook.natural(o) || o.is(Blocks.DIRT_PATH)) continue;
			if (w.is(Blocks.DIRT_PATH) || w.is(Blocks.COARSE_DIRT) || w.is(Blocks.PODZOL) || AldLook.natural(w)) continue;
			n++;
		}
		return n;
	}

	/** Distinct ground heights under the written palisade logs (bottom log of each column). */
	static int[] palisadeColumns(VillageRecord r, BoundingBox inside) {
		Set<Integer> heights = new HashSet<>();
		int cols = 0, onGround = 0;
		for (var e : r.journal.long2ObjectEntrySet()) {
			if (!e.getValue().written().is(Blocks.SPRUCE_LOG)) continue;
			BlockPos p = BlockPos.of(e.getLongKey());
			if (inside.isInside(p.getX(), inside.minY(), p.getZ())) continue;
			if (r.journal.containsKey(p.below().asLong())) continue; // not the bottom log
			if (!level.getBlockState(p.above(3)).is(Blocks.SPRUCE_FENCE) && !level.getBlockState(p.above(3)).is(Blocks.STONE_BRICK_WALL)) continue;
			cols++;
			BlockState g = level.getBlockState(p.below());
			if (AldLook.natural(g)) onGround++;
			heights.add(p.getY() - 1);
		}
		return new int[] {cols, onGround, heights.size()};
	}

	static int written(VillageRecord r, net.minecraft.world.level.block.Block b) {
		int n = 0;
		for (var e : r.journal.values()) if (e.written().is(b)) n++;
		return n;
	}

	public static void run(ServerLevel lvl, @Nullable VillageRecord b) {
		level = lvl;
		check("kill switches vlook_aldermark_rebuild / vlook_aldermark_fort / vlook_hamlet default on", AldLook.REBUILD == AldLook.on("vlook_aldermark_rebuild")
			&& AldLook.FORT == AldLook.on("vlook_aldermark_fort") && AldLook.HAMLET == AldLook.on("vlook_hamlet"), AldLook.REBUILD + " " + AldLook.FORT + " " + AldLook.HAMLET);
		if (!AldLook.REBUILD) return;
		try {
			inSitu(b);
		} catch (Throwable t) {
			check("B's rebuild readable", false, t);
		}
		try {
			hillVillage();
		} catch (Throwable t) {
			check("hill village scene", false, t);
		}
		try {
			fort();
		} catch (Throwable t) {
			check("outpost scene", false, t);
		}
		try {
			hamlet();
		} catch (Throwable t) {
			check("hamlet scene", false, t);
		}
		swapSignature();
	}

	/** The real self-test village B (flat ground): its rebuild was planned by {@link AldRebuild}. */
	static void inSitu(@Nullable VillageRecord b) {
		if (b == null) return;
		VillageRecord.Piece tp = null;
		for (VillageRecord.Piece p : b.pieces) if (p.type() == AldRebuild.TOWER_PIECE) tp = p;
		boolean spire = false;
		if (tp != null) {
			BoundingBox t = tp.box();
			int cx = (t.minX() + t.maxX()) / 2, cz = (t.minZ() + t.maxZ()) / 2, f = t.maxY() - 16;
			BlockState flag = level.getBlockState(new BlockPos(cx, f + 15, cz)), pole = level.getBlockState(new BlockPos(cx, f + 14, cz));
			spire = flag.getBlock() instanceof BannerBlock && pole.is(Blocks.SPRUCE_FENCE) && level.getBlockState(new BlockPos(cx, f + 13, cz)).is(Blocks.DEEPSLATE_TILES)
				&& level.getBlockState(new BlockPos(cx, f + 6, cz)).is(Blocks.SPRUCE_PLANKS) && level.getBlockState(new BlockPos(cx + 2, f + 9, cz + 2)).is(Blocks.DEEPSLATE_TILES);
		}
		check("B: watchtower with a deepslate spire and the flag (no flat top)", spire, tp == null ? "no tower piece" : tp.box());
		int deep = written(b, Blocks.DEEPSLATE_TILE_STAIRS) + written(b, Blocks.DEEPSLATE_TILE_SLAB);
		int bricks = written(b, Blocks.STONE_BRICKS), weathered = written(b, Blocks.MOSSY_STONE_BRICKS) + written(b, Blocks.CRACKED_STONE_BRICKS);
		check("B: deepslate roofs, spruce timber, hashed stone footings", deep > 0 && written(b, Blocks.SPRUCE_PLANKS) > 0 && bricks > weathered && weathered > 0,
			"deep " + deep + " bricks " + bricks + " weathered " + weathered);
		int fenceTips = 0, wallTips = 0;
		for (var e : b.journal.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			BlockState w = e.getValue().written();
			if (!level.getBlockState(p.below()).is(Blocks.SPRUCE_LOG)) continue;
			if (w.is(Blocks.SPRUCE_FENCE)) fenceTips++;
			if (w.is(Blocks.STONE_BRICK_WALL)) wallTips++;
		}
		check("B: palisade of spruce logs with alternating fence and wall tips", fenceTips > 0 && wallTips > 0, fenceTips + " fence / " + wallTips + " wall tips");
		int banners = 0, lit = 0, val = 0, lights = 0;
		for (var e : b.journal.values()) {
			BlockState w = e.written();
			if (w.getBlock() instanceof BannerBlock) banners++;
			if (w.is(Blocks.CAMPFIRE) && w.getValue(CampfireBlock.LIT)) lit++;
			if (valuable(w)) val++;
			if (light(w)) lights++;
		}
		check("B: budgets (<= 16 banners, <= 12 lights, no lit fire, no valuable block)", banners <= 16 && lights <= 12 && lit == 0 && val == 0,
			banners + " banners, " + lights + " lights, " + lit + " lit, " + val + " valuable");
		List<BlockPos> s = new java.util.ArrayList<>();
		int fl = floating(b, s);
		check("B: nothing the rebuild wrote floats", fl == 0, fl + " " + s);
	}

	static void hillVillage() {
		int[] res = new int[3];
		Plans.Fortify[] plan = new Plans.Fortify[1];
		BlockPos v = new BlockPos(AldLookScenes.VILLAGE_HILL.getX(), 0, AldLookScenes.VILLAGE_HILL.getZ());
		AldLookScenes.load(level, v, 8, true);
		int base = AldLook.ground(level, v.getX(), v.getZ());
		VillageRecord r = AldLookScenes.villageScene(level, v, res, plan);
		Plans.Fortify f = plan[0];
		int peak = groundBefore(r, v.getX() + 8, v.getZ() + 8); // the hill's own top (the tower may now stand on it)
		check("hill village: watchtower on the high point", AldRebuild.lastTower != null && AldRebuild.lastTowerGround >= peak - 1 && f.towerTop() != null,
			AldRebuild.lastTower + " ground " + AldRebuild.lastTowerGround + " peak " + peak + " best " + AldRebuild.lastBestGround);
		check("hill village: every op stands when written bottom-up (no canSurvive skips)", res[1] == 0, res[0] + " written, " + res[1] + " unsurvivable, " + res[2] + " refused");
		BoundingBox inner = new BoundingBox(v.getX() - AldLookScenes.V_HALF + 1, -64, v.getZ() - AldLookScenes.V_HALF + 1, v.getX() + AldLookScenes.V_HALF - 1, 320, v.getZ() + AldLookScenes.V_HALF - 1);
		int[] pc = palisadeColumns(r, inner);
		check("hill village: palisade on each column's own ground, stepping with the slope", pc[0] >= 40 && pc[1] == pc[0] && pc[2] >= 2,
			pc[0] + " columns, " + pc[1] + " on ground, " + pc[2] + " levels, wall cells " + AldRebuild.lastWallCells);
		check("hill village: timber gatehouses over both paths out", AldRebuild.lastExits >= 2 && AldRebuild.lastGatehouses == 2 && written(r, Blocks.STRIPPED_SPRUCE_WOOD) >= 4,
			AldRebuild.lastExits + " exits, " + AldRebuild.lastGatehouses + " gatehouses " + AldRebuild.lastGates);
		check("hill village: houses restyled (deepslate roofs, stone footing, spruce)", written(r, Blocks.DEEPSLATE_TILE_STAIRS) >= 30 && written(r, Blocks.SPRUCE_PLANKS) >= 20
			&& written(r, Blocks.STONE_BRICKS) > 0 && written(r, Blocks.SPRUCE_LOG) > 0, written(r, Blocks.DEEPSLATE_TILE_STAIRS) + " roof stairs");
		check("hill village: chimneys and ridge flags", AldRebuild.lastChimneys >= 1 && AldRebuild.lastFlags >= 1, AldRebuild.lastChimneys + " chimneys, " + AldRebuild.lastFlags + " flags");
		int[] bud = budget(f.ops());
		check("hill village: budgets (<= 12 banners, <= 8 lights, no lit fire, no valuable, <= 4200 ops)",
			bud[0] <= AldRebuild.MAX_BANNERS && bud[3] <= 8 && bud[4] == 0 && bud[5] == 0 && f.ops().size() <= AldRebuild.MAX_OPS, java.util.Arrays.toString(bud) + " ops " + f.ops().size());
		check("hill village: unlit mess fire by the bell (tents where the ground is level)", written(r, Blocks.CAMPFIRE) == 1, AldRebuild.lastTents + " tents");
		List<BlockPos> s = new java.util.ArrayList<>();
		int fl = floating(r, s), cv = carved(r);
		check("hill village: nothing floating, no ground carved", fl == 0 && cv == 0, fl + " floating " + s + ", " + cv + " carved");
		check("hill village: every rebuilt block in the footprint", r.journal.keySet().longStream().allMatch(p -> r.insideFootprint(BlockPos.of(p), BlockWriter.UP)), null);
		int[] u = BlockWriter.restore(level, r);
		check("hill village: undo restores everything", r.journal.isEmpty() || u[1] == 0, u[0] + " restored, " + u[1] + " left");
		AldLookScenes.flatten(level, v.offset(8, 0, 8), 31 + 8, base, 30);
		AldLookScenes.unforce(level, v, AldLookScenes.V_HALF + 40);
	}

	static int written(net.minecraft.world.level.block.Block b, VillageRecord r) {
		return written(r, b);
	}

	static void fort() {
		int[] res = new int[3];
		AldFort.Plan[] plan = new AldFort.Plan[1];
		BlockPos c = AldLookScenes.FORT_HILL;
		AldLookScenes.load(level, c, 8, true);
		int base = AldLook.ground(level, c.getX(), c.getZ());
		VillageRecord r = AldLookScenes.fortScene(level, c, res, plan);
		int peak = r == null ? AldLook.ground(level, c.getX(), c.getZ()) : groundBefore(r, c.getX(), c.getZ()); // the hill's own top, not the fort on it
		check("outpost: a motte site on the hill was found", r != null && plan[0] != null, null);
		if (r == null || plan[0] == null) {
			AldLookScenes.flatten(level, c, 33, base, 20);
			return;
		}
		AldFort.Plan p = plan[0];
		check("outpost: tower on the highest point within 24", p.floor() >= peak - 1, "floor " + p.floor() + " peak " + peak + " tower " + p.tower());
		int[] bud = budget(p.ops());
		check("outpost: <= 900 ops, <= 3 block entities, <= 6 lights, one blue flag, unlit fire", p.ops().size() <= AldFort.MAX_OPS && bud[2] <= 3 && bud[3] <= 6 && bud[1] == 1 && bud[4] == 0 && bud[5] == 0,
			p.ops().size() + " ops " + java.util.Arrays.toString(bud));
		check("outpost: every op written (in its box, can stand)", res[1] == 0 && res[2] == 0, res[0] + " written, " + res[1] + " unsurvivable, " + res[2] + " refused");
		check("outpost: ring steps with the slope", AldFort.lastRingLevels >= 2 && AldFort.lastRingCells >= 40, AldFort.lastRingCells + " cells on " + AldFort.lastRingLevels + " levels");
		BlockState flag = level.getBlockState(p.flag());
		check("outpost: the flag on the spire is the hint (capture banner spot)", flag.is(Blocks.BANNER.blue()) && level.getBlockState(p.flag().below()).is(Blocks.SPRUCE_FENCE)
			&& r.hint != null && r.hint.equals(p.flag()), flag);
		Direction gd = AldFort.gateDir(p.tower(), AldLookScenes.CASTLE);
		double lu = 0;
		int ln = 0;
		for (var e : r.journal.long2ObjectEntrySet()) if (e.getValue().written().is(Blocks.STRIPPED_SPRUCE_WOOD) && BlockPos.of(e.getLongKey()).getY() > p.floor() - 12) {
			BlockPos q = BlockPos.of(e.getLongKey());
			int du = (q.getX() - p.tower().getX()) * gd.getStepX() + (q.getZ() - p.tower().getZ()) * gd.getStepZ();
			if (du > AldFort.YARD) {
				lu += du;
				ln++;
			}
		}
		check("outpost: the gate faces the castle (lintel on the castle side)", ln >= 3 && lu / ln >= AldFort.YARD + 4, ln + " lintel blocks");
		List<BlockPos> s = new java.util.ArrayList<>();
		int fl = floating(r, s), cv = carved(r);
		check("outpost: plinth and palisade reach the ground (nothing floating, nothing carved)", fl == 0 && cv == 0, fl + " floating " + s + ", " + cv + " carved, plinth " + AldFort.lastPlinth + " batter " + AldFort.lastBatter);
		boolean muster = level.getBlockState(p.yard().offset(2, 0, -2)).isAir() || level.getBlockState(p.yard().offset(2, 0, -2)).canBeReplaced();
		check("outpost: the garrison's muster spot in the yard is free", muster, p.yard());
		int[] u = BlockWriter.restore(level, r);
		check("outpost: undo restores everything", u[1] == 0, u[0] + " restored, " + u[1] + " left");
		// water: a drowned spot is never a site
		BlockPos w = AldLookScenes.WATER;
		AldLookScenes.load(level, w, 40, true);
		int wg = AldLook.ground(level, w.getX(), w.getZ());
		for (int dx = -32; dx <= 32; dx++) for (int dz = -32; dz <= 32; dz++) level.setBlock(new BlockPos(w.getX() + dx, wg, w.getZ() + dz), Blocks.WATER.defaultBlockState(), 2);
		check("outpost: never sited on water", AldFort.site(level, w, AldLookScenes.CASTLE) == null && AldHamlet.site(level, w) == null, null);
		for (int dx = -32; dx <= 32; dx++) for (int dz = -32; dz <= 32; dz++) level.setBlock(new BlockPos(w.getX() + dx, wg, w.getZ() + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		AldLookScenes.unforce(level, w, 40);
		AldLookScenes.flatten(level, c, 33, base, 20);
		AldLookScenes.unforce(level, c, 50);
	}

	static void hamlet() {
		int[] res = new int[3];
		AldHamlet.Plan[] plan = new AldHamlet.Plan[1];
		BlockPos c = AldLookScenes.HAMLET_HILL;
		AldLookScenes.load(level, c, 8, true);
		int base = AldLook.ground(level, c.getX(), c.getZ());
		VillageRecord r = AldLookScenes.hamletScene(level, c, res, plan);
		check("hamlet: a farmstead site on the slope was found", r != null, null);
		if (r == null) {
			AldLookScenes.flatten(level, c, 34, base, 12);
			return;
		}
		AldHamlet.Plan p = plan[0];
		check("hamlet: every op written (in its box, can stand)", res[1] == 0 && res[2] == 0 && res[0] > 20, res[0] + " written, " + res[1] + " unsurvivable, " + res[2] + " refused");
		List<BlockPos> s = new java.util.ArrayList<>();
		int fl = floating(r, s), cv = carved(r);
		check("hamlet: placed per column (nothing floating, nothing carved)", fl == 0 && cv == 0, fl + " floating " + s + ", " + cv + " carved");
		int levels = 0;
		Set<Integer> ys = new HashSet<>();
		for (var e : r.journal.long2ObjectEntrySet()) if (e.getValue().written().is(VillagesContent.CHARRED_PLANKS) || e.getValue().written().is(Blocks.SPRUCE_FENCE)) ys.add(BlockPos.getY(e.getLongKey()));
		levels = ys.size();
		check("hamlet: burnt shell, scorched field, broken pen and the composter job site", written(r, Blocks.COMPOSTER) == 1 && written(r, VillagesContent.CHARRED_PLANKS) > 0
			&& written(r, Blocks.COARSE_DIRT) > 0 && written(r, Blocks.SPRUCE_FENCE) > 0 && levels >= 2, "levels " + levels);
		check("hamlet: the refugees' spot is free", level.getBlockState(p.refuge()).isAir() && level.getBlockState(p.refuge().above()).isAir() && r.bounds.isInside(p.refuge()), p.refuge());
		int[] u = BlockWriter.restore(level, r);
		check("hamlet: undo restores everything", u[1] == 0, u[0] + " restored, " + u[1] + " left");
		AldLookScenes.flatten(level, c, 34, base, 12);
		AldLookScenes.unforce(level, c, 40);
	}

	static void swapSignature() {
		BlockPos spot = new BlockPos(0, 100, 0);
		List<VillageRecord.Op> ops = AldRebuild.flagPostOps(spot, DyeColor.BLUE);
		boolean ok = ops.size() == 4 && BlockPos.of(ops.get(3).pos()).equals(spot.above(3)) && ops.get(3).state().is(Blocks.BANNER.blue())
			&& ops.get(2).state().is(Blocks.SPRUCE_FENCE) && BlockPos.of(ops.get(0).pos()).equals(spot) && !ops.get(0).state().isAir();
		check("swap signature: the banner stands on a stone-footed flag pole (not one block up in the air)", ok, ops.size());
		Set<Long> seen = new HashSet<>();
		boolean bottomUp = true;
		for (VillageRecord.Op o : ops) {
			if (!seen.isEmpty() && !seen.contains(BlockPos.of(o.pos()).below().asLong())) bottomUp = false;
			seen.add(o.pos());
		}
		check("swap signature: written bottom-up", bottomUp, null);
	}
}
