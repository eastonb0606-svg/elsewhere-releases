package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom_villages.VillageRecord;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dev only (dropped at merge): terrain-fit audit of what the kingdom actually wrote for one site, read from its
 * {@link VillageRecord#journal} and the live world. Style-agnostic, so it judges every faction's rebuild, outpost and hamlet
 * the same way (STRUCTURES.md 2.16 metrics, adapted to runtime builds):
 * <ul>
 * <li><b>floating</b>: a standing member (log post, wall, fence, stone or brick course) whose lowest added block has any air,
 * plant or fluid under it; deck pieces (slabs, stairs, planks, trapdoors) must be tied to a grounded member within
 * {@value #TIE} steps; fixtures must pass vanilla {@code canSurvive}; no added block may be detached;</li>
 * <li><b>buried</b>: added faces with natural ground against them 2+ above the column's lowest added block (at most 5%),
 * and no door blocked by an added block or by ground;</li>
 * <li><b>flooding</b>: no lava, no unenclosed water written, and (with {@link #newFluid}) no fluid where there was none
 * next to the build after forced fluid ticks;</li>
 * <li><b>valuable</b>: no ore, ore storage or beacon-type block written;</li>
 * <li><b>cut</b>: natural ground turned to air, at most {@value #MAX_CUT} per column.</li>
 * </ul>
 */
public final class FitAudit {
	private FitAudit() {}

	public static final int TIE = 10, MAX_CUT = 4;
	static final Pattern COPPER = Pattern.compile("^(waxed_)?((exposed|weathered|oxidized)_)?copper(_block)?$");
	static final Set<String> VALUABLE = Set.of("iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block", "netherite_block",
		"coal_block", "raw_iron_block", "raw_gold_block", "raw_copper_block", "ancient_debris", "gilded_blackstone", "beacon", "conduit", "heavy_core", "dragon_egg",
		"budding_amethyst");

	public static final class Result {
		public final String label;
		public int written, additions, verticalCols, floatingCols, maxGap, unsupported, cantSurvive;
		public int exteriorFaces, buriedFaces, doorsBlocked, valuables, fluidWritten, fluidUnenclosed, lava, newFluid, naturalRemoved, maxCut;
		public final List<String> samples = new ArrayList<>();

		Result(String label) {
			this.label = label;
		}

		void sample(String kind, BlockPos p, BlockState s) {
			if (samples.size() < 10) samples.add(kind + "@" + p.toShortString() + "=" + id(s));
		}

		public boolean floatingOk() {
			return floatingCols == 0 && unsupported == 0 && cantSurvive == 0;
		}

		public double buriedPct() {
			return exteriorFaces == 0 ? 0 : 100.0 * buriedFaces / exteriorFaces;
		}

		public boolean buriedOk() {
			return buriedPct() <= 5.0 && doorsBlocked == 0;
		}

		public boolean floodOk() {
			return lava == 0 && fluidUnenclosed == 0 && newFluid == 0;
		}

		public boolean valuableOk() {
			return valuables == 0;
		}

		public boolean cutOk() {
			return maxCut <= MAX_CUT;
		}

		public String floating() {
			return "written " + written + ", added " + additions + ", standing columns " + verticalCols + ", floating " + floatingCols + " (max gap " + maxGap + "), unsupported " + unsupported
				+ ", cannot survive " + cantSurvive + (samples.isEmpty() ? "" : " " + samples);
		}

		public String buried() {
			return String.format("buried faces %d/%d (%.1f%%), doors blocked %d%s", buriedFaces, exteriorFaces, buriedPct(), doorsBlocked, samples.isEmpty() ? "" : " " + samples);
		}

		public String flood() {
			return "fluid written " + fluidWritten + " (unenclosed " + fluidUnenclosed + "), lava " + lava + ", new fluid after forced ticks " + newFluid;
		}
	}

	static String id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	public static boolean valuable(BlockState s) {
		String p = id(s);
		return VALUABLE.contains(p) || p.endsWith("_ore") || COPPER.matcher(p).matches();
	}

	/** Something a block can stand on: solid, not leaves, no plant, snow layer, liquid or air. */
	static boolean ground(BlockState s) {
		return !s.isAir() && s.isSolid() && !(s.getBlock() instanceof LeavesBlock) && !s.canBeReplaced();
	}

	/** Natural terrain (what "buried" and "cut" mean). Village building materials (terracotta, sandstone, planks) are not terrain. */
	static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)
			|| s.is(Blocks.CLAY) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.MUD) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND);
	}

	/** Blocks that hang on or attach to a side or ceiling by design (their support is vanilla's canSurvive). */
	static boolean attached(BlockState s) {
		Block b = s.getBlock();
		return b instanceof LeavesBlock || b instanceof WallBannerBlock || b instanceof WallTorchBlock || b instanceof WallSignBlock || b instanceof WallHangingSignBlock
			|| b instanceof CeilingHangingSignBlock || b instanceof LadderBlock || b instanceof VineBlock || b instanceof MultifaceBlock || b instanceof TrapDoorBlock
			|| b instanceof FaceAttachedHorizontalDirectionalBlock || b instanceof CocoaBlock || b instanceof BellBlock || b instanceof ChainBlock || b instanceof WallSkullBlock
			|| b instanceof LanternBlock && s.getValue(LanternBlock.HANGING);
	}

	/** Horizontal pieces that may span between posts (decks, roofs, eaves, floors). */
	static boolean deck(BlockState s) {
		Block b = s.getBlock();
		return b instanceof SlabBlock || b instanceof StairBlock || b instanceof TrapDoorBlock || b instanceof CarpetBlock || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL);
	}

	/**
	 * Audits {@code r}'s journal. {@code fluidBefore}: fluid positions in the area before the build (null = skip the
	 * new-fluid count; the caller forces fluid ticks next to the build and waits before calling this).
	 */
	public static Result audit(ServerLevel level, VillageRecord r, String label, LongSet fluidBefore) {
		Result res = new Result(label);
		// additions: blocks we put where there was nothing to stand on (air, plants, snow layers, fluid); still ours in the world
		Long2ObjectOpenHashMap<BlockState> add = new Long2ObjectOpenHashMap<>();
		Long2IntOpenHashMap lowest = new Long2IntOpenHashMap();
		Long2IntOpenHashMap cut = new Long2IntOpenHashMap();
		lowest.defaultReturnValue(Integer.MAX_VALUE);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (var e : r.journal.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			VillageRecord.Change ch = e.getValue();
			BlockState w = ch.written(), o = ch.original(), cur = level.getBlockState(p);
			res.written++;
			if (valuable(w)) {
				res.valuables++;
				res.sample("valuable", p, w);
			}
			FluidState wf = w.getFluidState();
			if (!wf.isEmpty() && o.getFluidState().isEmpty()) {
				res.fluidWritten++;
				if (wf.is(net.minecraft.tags.FluidTags.LAVA)) res.lava++;
				else if (!enclosed(level, p)) {
					res.fluidUnenclosed++;
					res.sample("water-unenclosed", p, w);
				}
			}
			if (w.isAir() && natural(o)) {
				res.naturalRemoved++;
				cut.addTo(columnKey(p), 1);
			}
			if (w.isAir() || cur.getBlock() != w.getBlock() || ground(o)) continue;
			add.put(e.getLongKey(), cur);
			res.additions++;
			if (cur.isSolid() && !attached(cur)) {
				long ck = columnKey(p);
				if (p.getY() < lowest.get(ck)) lowest.put(ck, p.getY());
			}
		}
		for (int v : cut.values()) res.maxCut = Math.max(res.maxCut, v);

		// 1. fixtures: vanilla's own survival rule (a banner, lantern or torch with nothing to hold it)
		for (var e : add.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			BlockState s = e.getValue();
			if (!s.canSurvive(level, p)) {
				res.cantSurvive++;
				res.sample("cannot-survive", p, s);
			}
		}

		// 2. standing members: the lowest added block of each column must sit on ground (no air, plant or water gap)
		LongOpenHashSet groundedBottoms = new LongOpenHashSet();
		for (var e : lowest.long2IntEntrySet()) {
			long ck = e.getLongKey();
			int x = colX(ck), z = colZ(ck), y = e.getIntValue();
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = add.get(p.asLong());
			if (s == null) continue;
			int gap = 0;
			m.set(x, y - 1, z);
			while (gap < 64 && m.getY() > level.getMinY() && !ground(level.getBlockState(m))) {
				gap++;
				m.move(Direction.DOWN);
			}
			if (gap == 0) groundedBottoms.add(p.asLong());
			if (deck(s)) continue;
			res.verticalCols++;
			if (gap > 0) {
				res.floatingCols++;
				res.maxGap = Math.max(res.maxGap, gap);
				res.sample("gap" + gap, p, s);
			}
		}

		// 3. every solid added block is tied (26-neighbour, through solid added blocks) to ground within TIE steps
		Long2IntOpenHashMap dist = new Long2IntOpenHashMap();
		dist.defaultReturnValue(-1);
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		for (var e : add.long2ObjectEntrySet()) {
			BlockState s = e.getValue();
			if (!s.isSolid() || attached(s)) continue;
			BlockPos p = BlockPos.of(e.getLongKey());
			boolean anchored = groundedBottoms.contains(e.getLongKey());
			for (Direction d : Direction.values()) {
				if (anchored) break;
				BlockPos n = p.relative(d);
				if (!add.containsKey(n.asLong()) && ground(level.getBlockState(n))) anchored = true;
			}
			if (anchored) {
				dist.put(e.getLongKey(), 0);
				q.enqueue(e.getLongKey());
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int d0 = dist.get(k);
			if (d0 >= TIE) continue;
			BlockPos p = BlockPos.of(k);
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dy == 0 && dz == 0) continue;
				long nk = BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
				BlockState ns = add.get(nk);
				if (ns == null || !ns.isSolid() || attached(ns) || dist.get(nk) >= 0) continue;
				dist.put(nk, d0 + 1);
				q.enqueue(nk);
			}
		}
		for (var e : add.long2ObjectEntrySet()) {
			BlockState s = e.getValue();
			if (!s.isSolid() || attached(s) || dist.get(e.getLongKey()) >= 0) continue;
			res.unsupported++;
			res.sample("unsupported", BlockPos.of(e.getLongKey()), s);
		}

		// 4. buried faces and blocked doors
		for (var e : add.long2ObjectEntrySet()) {
			BlockState s = e.getValue();
			BlockPos p = BlockPos.of(e.getLongKey());
			if (s.getBlock() instanceof DoorBlock) {
				if (s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					Direction f = s.getValue(DoorBlock.FACING);
					for (BlockPos n : List.of(p.relative(f), p.relative(f.getOpposite()), p.relative(f).above(), p.relative(f.getOpposite()).above()))
						if (ground(level.getBlockState(n)) && !(level.getBlockState(n).getBlock() instanceof DoorBlock)) {
							res.doorsBlocked++;
							res.sample("door-blocked", n, level.getBlockState(n));
							break;
						}
				}
				continue;
			}
			if (!s.isSolid() || attached(s)) continue;
			int foot = lowest.get(columnKey(p));
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = p.relative(d);
				if (add.containsKey(n.asLong())) continue;
				BlockState ns = level.getBlockState(n);
				if (ns.getBlock() instanceof DoorBlock && ns.getValue(DoorBlock.FACING).getAxis() == d.getAxis()) {
					res.doorsBlocked++;
					res.sample("blocks-door", p, s);
				}
				res.exteriorFaces++;
				if (foot != Integer.MAX_VALUE && p.getY() >= foot + 2 && natural(ns) && !r.journal.containsKey(n.asLong())) {
					res.buriedFaces++;
					if (res.buriedFaces <= 3) res.sample("buried", p, s);
				}
			}
		}

		// 5. fluid that appeared next to the build (the caller forced fluid ticks there and waited)
		if (fluidBefore != null) {
			LongOpenHashSet seen = new LongOpenHashSet();
			for (long k : r.journal.keySet()) {
				BlockPos p = BlockPos.of(k);
				for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
					long nk = BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
					if (!seen.add(nk) || fluidBefore.contains(nk)) continue;
					BlockPos n = BlockPos.of(nk);
					FluidState f = level.getFluidState(n);
					if (f.isEmpty()) continue;
					VillageRecord.Change ch = r.journal.get(nk);
					if (ch != null && !ch.written().getFluidState().isEmpty()) continue; // written on purpose (counted above)
					res.newFluid++;
					res.sample("new-fluid", n, level.getBlockState(n));
				}
			}
		}
		return res;
	}

	/** A written water block holds: solid or water on every side and below. */
	static boolean enclosed(ServerLevel level, BlockPos p) {
		for (Direction d : Direction.values()) {
			if (d == Direction.UP) continue;
			BlockState n = level.getBlockState(p.relative(d));
			if (!(ground(n) || !n.getFluidState().isEmpty() && n.getFluidState().isSource())) return false;
		}
		return true;
	}

	/** Fluid positions inside {@code box} (before a build). */
	public static LongOpenHashSet fluids(ServerLevel level, BoundingBox box) {
		LongOpenHashSet out = new LongOpenHashSet();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) for (int y = box.minY(); y <= box.maxY(); y++) {
			m.set(x, y, z);
			if (!level.getFluidState(m).isEmpty()) out.add(m.asLong());
		}
		return out;
	}

	/** Schedules a fluid tick for every fluid block next to the build (what the next neighbour update in play would do). Returns how many. */
	public static int forceFluidTicks(ServerLevel level, VillageRecord r) {
		LongOpenHashSet seen = new LongOpenHashSet();
		int n = 0;
		for (long k : r.journal.keySet()) {
			BlockPos p = BlockPos.of(k);
			for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
				long nk = BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
				if (!seen.add(nk)) continue;
				BlockPos q = BlockPos.of(nk);
				FluidState f = level.getFluidState(q);
				if (f.isEmpty()) continue;
				level.scheduleTick(q, f.getType(), f.getType().getTickDelay(level));
				n++;
			}
		}
		return n;
	}

	static long columnKey(BlockPos p) {
		return ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
	}

	static int colX(long k) {
		return (int) (k >> 32);
	}

	static int colZ(long k) {
		return (int) k;
	}
}
