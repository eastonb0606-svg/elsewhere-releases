package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom_villages.FactionVillages.Profile;
import dev.elsewhere.kingdom_villages.FactionVillages.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Hrafnholt's 3.0 village rebuild look (STRUCTURES.md 3.2 "Towns", 2.7, 2.9, 2.11 and the 7.1 style sheet): shoreline farmsteads of
 * the seafaring Jarldom instead of a palette swap.
 * <ul>
 * <li><b>Longhall</b>: bowed (7 wide in the middle, 5 at the ends), long axis along the contours, earthen floor and long hearth, steep
 * spruce-stair roof with a moss turf and a hogback ridge, crossed dark-oak finials with bone teeth, raking porch posts under the low
 * eaves. On a slope it stands on a stepped plinth (mossy cobblestone, cobblestone, stone) with a grass apron, never cut in and never
 * floating;</li>
 * <li><b>palisade</b>: contiguous spruce and dark-oak logs, 3 and 4 high with sharpened fence tips, each column on its own ground;
 * gates get dark-oak posts with bone caps, a plank lintel and a banner; a water gate opens toward the jetty;</li>
 * <li><b>waterfront</b> (extends the 2.1 pier): a 3-wide jetty on log piles to the bed, out to depth 3, with a T-head, bollards and
 * lanterns; a naust boathouse astride the shoreline (walls on piles over the water, turf roof, open boat bay); a boat drawn up on the
 * strand with a green-and-brown sail;</li>
 * <li><b>farmsteads</b>: fenced tofts beside the houses with drying racks (fence and hay); a thing-stead ring of standing stones on the
 * flattest meadow; runestones at road forks; a timber watch tower with a hoarding gallery and turf roof;</li>
 * <li>green and brown banners (at most {@value #MAX_BANNERS}).</li>
 * </ul>
 * Like the rest of {@link RivalPlans} this only lists ops; {@link BlockWriter} still decides. Every feature is planned as a unit and checked
 * before it is committed (each op in the village footprint, on air or a plant the writer may replace), so a refused op can never leave
 * half a roof in the air. Ops are ordered so supports come before what they hold. No water, lava or fire; logs replace water only in the
 * pier box (the writer's rule). One pass at fortify time, no ticking.
 * <p>Kill switch: {@code -Delsewhere.overhaul3.hrafn_village_looks=false} brings back the 2.1 Hrafnholt rebuild.
 */
public final class HrafnLooks {
	private HrafnLooks() {}

	public static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.hrafn_village_looks", "true"));
	/** Plan cap for a Hrafnholt rebuild (the 2.1 cap of {@link RivalPlans#MAX_FORTIFY} would cut the palisade). */
	public static final int MAX_OPS = 7000;
	public static final int MAX_BANNERS = 12;
	/** Plinth spread (keeps every member within FitAudit.TIE of its footing) and deepest jetty pile. */
	static final int MAX_PLINTH = 3, MAX_PILE = 6, NONE = Integer.MIN_VALUE;

	/** What the last plan of a village built (screenshots and self-test; not saved). */
	public static final class Report {
		public final Map<String, BlockPos> spots = new LinkedHashMap<>();
		/** Grounded members {x, z, yBottom, yTop}: every block from yBottom to yTop is written by the plan and stands on the block below yBottom. */
		public final List<int[]> columns = new ArrayList<>();
		/** Cells the plan keeps open (longhall floor and headroom): never ground, never a plan block. */
		public final List<BlockPos> open = new ArrayList<>();
		public int banners, tofts, forks, runestones, stones, piles, hallFloor = NONE, hallMinGround, hallMaxGround, hallLen;
		/** The hall's long axis runs along the contours (the ends differ less than the sides). */
		public boolean hallAlongContour;
		public boolean boathouse, boat;
		/** fx-hrafwater: why no naust stood (each tried lane's first refusal), for the self-test's detail. */
		public String naustWhy = "";
	}

	static final Map<Long, Report> REPORTS = new LinkedHashMap<Long, Report>(16, 0.75F, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Report> e) {
			return size() > 64;
		}
	};

	public static @Nullable Report report(long villageId) {
		return REPORTS.get(villageId);
	}

	/** Hrafnholt's banner colours: the faction colour (green) and brown on alternate positions. Unrest grey and other kingdoms are untouched. */
	public static DyeColor tint(Profile p, BlockPos pos, DyeColor color) {
		if (!ON || p.shape() != Shape.HRAFNHOLT || color != p.banner()) return color;
		return ((pos.getX() + pos.getY() + pos.getZ()) & 1) == 0 ? color : DyeColor.BROWN;
	}

	public static DyeColor tint(VillageRecord r, BlockPos pos, DyeColor color) {
		return tint(FactionVillages.of(r.owner), pos, color);
	}

	// ---- plan scaffolding ---------------------------------------------------------------------------------------------

	/** A feature's ops in build order. Group 0 is required; another group is committed only when every op in it can be written. */
	static final class Part {
		final LinkedHashMap<Long, BlockState> ops = new LinkedHashMap<>();
		final Map<Long, Integer> group = new HashMap<>();
		int groups;

		void put(BlockPos p, BlockState s) {
			put(0, p, s);
		}

		void put(int g, BlockPos p, BlockState s) {
			if (ops.containsKey(p.asLong())) return;
			ops.put(p.asLong(), s);
			if (g != 0) group.put(p.asLong(), g);
		}

		int group() {
			return ++groups;
		}
	}

	static final class B {
		final RivalPlans.Ctx c;
		final ServerLevel level;
		final Report rep;

		B(RivalPlans.Ctx c, Report rep) {
			this.c = c;
			this.level = c.level;
			this.rep = rep;
		}
	}

	/** Every required op passes {@code ok}. */
	static boolean fits(Part part, Predicate<BlockPos> ok) {
		for (var e : part.ops.entrySet()) if (!part.group.containsKey(e.getKey()) && !ok.test(BlockPos.of(e.getKey()))) return false;
		return true;
	}

	/** Commits the part (required ops plus every optional group that fully passes); returns the committed positions, or null when a required op fails. */
	static @Nullable Set<Long> commit(B b, Part part, Predicate<BlockPos> ok) {
		if (!fits(part, ok)) return null;
		Set<Integer> failed = new HashSet<>();
		for (var e : part.ops.entrySet()) {
			Integer g = part.group.get(e.getKey());
			if (g != null && !failed.contains(g) && !ok.test(BlockPos.of(e.getKey()))) failed.add(g);
		}
		Set<Long> done = new HashSet<>();
		for (var e : part.ops.entrySet()) {
			Integer g = part.group.get(e.getKey());
			if (g != null && failed.contains(g)) continue;
			b.c.add(BlockPos.of(e.getKey()), e.getValue());
			done.add(e.getKey());
		}
		return done;
	}

	/** In the village footprint (the writer's rule), not planned yet, and air or a plant the writer may replace. */
	static boolean writable(B b, BlockPos p) {
		if (b.c.used.contains(p.asLong()) || !b.level.isLoaded(p) || !b.c.r.insideFootprint(p, BlockWriter.UP)) return false;
		return clear(b.level.getBlockState(p), b.c.r.insidePieces(p));
	}

	static boolean clear(BlockState s, boolean mayReplace) {
		if (s.isAir()) return true;
		return mayReplace && s.canBeReplaced() && s.getFluidState().isEmpty() && !s.hasBlockEntity() && !(s.getBlock() instanceof DoublePlantBlock);
	}

	/** Natural open ground a build may stand on (never a path, a field or a village block). */
	static boolean natural(BlockState g) {
		if (g.is(Blocks.DIRT_PATH) || g.is(Blocks.FARMLAND)) return false;
		return g.is(Blocks.GRASS_BLOCK) || g.is(BlockTags.DIRT) || g.is(BlockTags.SAND) || g.is(Blocks.GRAVEL) || g.is(Blocks.SNOW_BLOCK) || g.is(Blocks.STONE)
			|| g.is(Blocks.ANDESITE) || g.is(Blocks.DIORITE) || g.is(Blocks.GRANITE) || g.is(Blocks.TUFF) || g.is(Blocks.CLAY);
	}

	/** The column's top block y when it is natural open ground, else {@link #NONE}. */
	static int ground(B b, int x, int z) {
		if (!b.level.isLoaded(new BlockPos(x, b.c.bell.getY(), z))) return NONE;
		int y = b.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		return natural(b.level.getBlockState(new BlockPos(x, y, z))) ? y : NONE;
	}

	static boolean isPath(B b, int x, int z) {
		if (!b.level.isLoaded(new BlockPos(x, b.c.bell.getY(), z))) return false;
		int y = b.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		return b.level.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT_PATH);
	}

	static BlockState st(Block blk) {
		return blk.defaultBlockState();
	}

	static BlockState stair(Block blk, Direction facing, Half half) {
		return blk.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	static BlockState log(Block blk, Direction.Axis axis) {
		return blk.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	static BlockState fence(Block blk, Direction... connect) {
		BlockState s = blk.defaultBlockState();
		for (Direction d : connect) {
			var prop = CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d);
			if (prop != null && s.hasProperty(prop)) s = s.setValue(prop, true);
		}
		return s;
	}

	static BlockState banner(B b, BlockPos at, int rot) {
		return Blocks.BANNER.pick(tint(b.c.p, at, b.c.p.banner())).defaultBlockState().setValue(BannerBlock.ROTATION, rot & 15);
	}

	static BlockState wallBanner(B b, BlockPos at, Direction facing) {
		return Blocks.WALL_BANNER.pick(tint(b.c.p, at, b.c.p.banner())).defaultBlockState().setValue(WallBannerBlock.FACING, facing);
	}

	static void reserve(B b, int x, int z, int y0, int y1) {
		for (int y = y0; y <= y1; y++) b.c.used.add(BlockPos.asLong(x, y, z));
	}

	// ---- the plan -----------------------------------------------------------------------------------------------------

	/** Hrafnholt's rebuild signature (replaces the 2.1 {@code RivalPlans.hrafnholt} body while {@link #ON}). */
	static void build(RivalPlans.Ctx c) {
		Report rep = new Report();
		REPORTS.put(c.r.id, rep);
		B b = new B(c, rep);
		longhall(b);
		watchTower(b);
		BlockPos fireAt = Plans.freeFloor(c.level, c.r, c.bell.offset(3, 0, 3), 8, c.used);
		if (fireAt != null) c.add(fireAt, st(Blocks.CAMPFIRE));
		thingstead(b);
		runestones(b);
		// the raven post at the edge toward the realm is the border hint (2.1)
		BlockPos hs = RivalPlans.hintSpot(c);
		if (hs != null) {
			RivalPlans.ravenPost(c, hs);
			c.hint = hs.above(4);
			rep.banners++;
			rep.spots.put("raven_post", hs);
		}
		BlockPos shore = waterfront(b);
		tofts(b);
		houseBanners(b);
		palisade(b, shore);
	}

	// ---- longhall -----------------------------------------------------------------------------------------------------

	/** A bowed hall: {@code len} cells along i, {@code hm} half-width in the middle, one less in the two end bays at each gable. */
	static final class Hall {
		final int len, hm, ox, oz;
		final boolean alongX;

		Hall(int len, int wid, boolean alongX, int ox, int oz) {
			this.len = len;
			this.hm = wid / 2;
			this.alongX = alongX;
			this.ox = ox;
			this.oz = oz;
		}

		int half(int i) {
			int k = Math.max(0, Math.min(len - 1, i));
			return k <= 1 || k >= len - 2 ? hm - 1 : hm;
		}

		boolean in(int i, int j) {
			return i >= 0 && i < len && Math.abs(j) <= half(i);
		}

		boolean wall(int i, int j) {
			if (!in(i, j)) return false;
			for (int di = -1; di <= 1; di++) for (int dj = -1; dj <= 1; dj++) if (!in(i + di, j + dj)) return true;
			return false;
		}

		/** Chebyshev distance from the plan (0 inside), capped at 3. */
		int dist(int i, int j) {
			int best = 3;
			for (int di = -2; di <= 2; di++) for (int dj = -2; dj <= 2; dj++) if (in(i + di, j + dj)) best = Math.min(best, Math.max(Math.abs(di), Math.abs(dj)));
			return best;
		}

		BlockPos at(int i, int j, int y) {
			return alongX ? new BlockPos(ox + i, y, oz + j) : new BlockPos(ox + j, y, oz + i);
		}

		Direction iDir() {
			return alongX ? Direction.EAST : Direction.SOUTH;
		}

		Direction jDir() {
			return alongX ? Direction.SOUTH : Direction.EAST;
		}

		/** Roof course over cell (i, j): eaves 2 out at floor+1, one up per block in, ridge at floor+3+half. */
		int roofY(int floor, int i, int j) {
			return floor + 1 + half(i) + 2 - Math.abs(j);
		}

		boolean post(int i, int j) {
			if (!wall(i, j)) return false;
			int h = half(i);
			if (i == 0 || i == len - 1) return Math.abs(j) == h; // gable corners
			if (Math.abs(j) < h) return true; // the bow's jog
			if (h != hm) return false;
			return Math.floorMod(i - 2, 3) == 0 || half(i - 1) != hm || half(i + 1) != hm;
		}

		boolean door(int i, int j) {
			return i == 0 && j == 0 || i == 4 && j == -half(4) || i == len - 5 && j == half(len - 5);
		}
	}

	/**
	 * Ground of every cell of the hall's plan and its 2-cell apron, or null when a plan or inner-apron cell is not open natural ground (an
	 * outer-apron cell may be a path or field: {@link #NONE}, left as it is). Index [i + 2][j + hm + 2].
	 */
	static int[][] groundMap(B b, Hall h) {
		int[][] g = new int[h.len + 4][2 * h.hm + 5];
		for (int i = -2; i <= h.len + 1; i++) for (int j = -(h.hm + 2); j <= h.hm + 2; j++) {
			g[i + 2][j + h.hm + 2] = NONE;
			if (h.dist(i, j) > 2) continue;
			BlockPos q = h.at(i, j, 0);
			int y = ground(b, q.getX(), q.getZ());
			if (y == NONE && h.dist(i, j) == 2) continue;
			if (y == NONE || nextToDoor(b, q.getX(), y, q.getZ())) return null;
			g[i + 2][j + h.hm + 2] = y;
		}
		return g;
	}

	static void longhall(B b) {
		int[][] sizes = {{15, 7}, {11, 5}, {9, 5}};
		for (int[] sz : sizes) {
			Hall best = null;
			int[][] bestG = null;
			Part bestPart = null;
			int bestScore = Integer.MAX_VALUE, found = 0;
			for (int t = 0; t < 260 && found < 6; t++) {
				// the first 140 tries stay near the bell; a big village (120 wide) with no free ground there gets the whole footprint
				int rad = t < 140 ? 28 : 54;
				BlockPos s = b.c.bell.offset(b.c.rnd.nextInt(2 * rad + 1) - rad, 0, b.c.rnd.nextInt(2 * rad + 1) - rad);
				for (int o = 0; o < 2; o++) {
					Hall h = new Hall(sz[0], sz[1], o == 0, s.getX(), s.getZ());
					int[][] g = groundMap(b, h);
					if (g == null) continue;
					int max = NONE, min = Integer.MAX_VALUE;
					for (int[] row : g) for (int y : row) if (y != NONE) {
						max = Math.max(max, y);
						min = Math.min(min, y);
					}
					if (max - min > MAX_PLINTH) continue;
					int score = (max - min) * 10 + (int) Math.round(alongSlope(h, g) * 3);
					if (score >= bestScore) continue;
					Part part = planHall(b, h, g, max + 1);
					if (!fits(part, p -> writable(b, p))) continue;
					best = h;
					bestG = g;
					bestPart = part;
					bestScore = score;
					found++;
				}
			}
			if (best == null) continue;
			int floor = 0, max = NONE, min = Integer.MAX_VALUE;
			for (int[] row : bestG) for (int y : row) if (y != NONE) {
				max = Math.max(max, y);
				min = Math.min(min, y);
			}
			floor = max + 1;
			Set<Long> done = commit(b, bestPart, p -> writable(b, p));
			if (done == null) continue;
			for (var e : bestPart.ops.entrySet()) if (done.contains(e.getKey()) && (e.getValue().getBlock() instanceof BannerBlock || e.getValue().getBlock() instanceof WallBannerBlock)) b.rep.banners++;
			hallReport(b, best, bestG, floor, min, max);
			return;
		}
	}

	/** How much the ground differs between the two gable ends (a hall along the contours has a small value). */
	static double alongSlope(Hall h, int[][] g) {
		double a = 0, z = 0;
		int na = 0, nz = 0;
		for (int j = -h.hm; j <= h.hm; j++) {
			for (int i = 0; i <= 1; i++) if (h.in(i, j)) {
				a += g[i + 2][j + h.hm + 2];
				na++;
			}
			for (int i = h.len - 2; i < h.len; i++) if (h.in(i, j)) {
				z += g[i + 2][j + h.hm + 2];
				nz++;
			}
		}
		return na == 0 || nz == 0 ? 0 : Math.abs(a / na - z / nz);
	}

	static double acrossSlope(Hall h, int[][] g) {
		double a = 0, z = 0;
		int na = 0, nz = 0;
		for (int i = 0; i < h.len; i++) {
			a += g[i + 2][-h.half(i) + h.hm + 2];
			z += g[i + 2][h.half(i) + h.hm + 2];
			na++;
			nz++;
		}
		return Math.abs(a / na - z / nz);
	}

	static Part planHall(B b, Hall h, int[][] g, int floor) {
		Part p = new Part();
		int len = h.len, hm = h.hm;
		Direction jd = h.jDir(), id = h.iDir();
		BlockState mossy = st(Blocks.MOSSY_COBBLESTONE), plank = st(Blocks.SPRUCE_PLANKS), post = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
		// 1. plinth under the plan (floor = the highest ground in the apron, so nothing is cut in or buried): mossy cobblestone, cobblestone,
		//    stone under the walls (STRUCTURES 2.9), earth under the floor; the apron rings slope down from it in the column's own ground
		Map<Long, Integer> apronGroup = new HashMap<>();
		for (int i = -2; i <= len + 1; i++) for (int j = -(hm + 2); j <= hm + 2; j++) {
			int d = h.dist(i, j);
			if (d > 2) continue;
			int gy = g[i + 2][j + hm + 2];
			if (d == 0) {
				boolean wall = h.wall(i, j);
				for (int y = gy + 1; y <= floor - 1; y++) {
					int depth = floor - 1 - y;
					BlockState s = wall ? (depth <= 1 ? mossy : depth == 2 ? st(Blocks.COBBLESTONE) : st(Blocks.STONE)) : (y == floor - 1 ? st(Blocks.COARSE_DIRT) : st(Blocks.DIRT));
					p.put(h.at(i, j, y), s);
				}
			} else {
				int top = floor - 1 - d;
				if (gy == NONE || gy >= top) continue;
				BlockPos q0 = h.at(i, j, gy);
				BlockState own = b.level.getBlockState(q0), under = b.level.getBlockState(q0.below());
				BlockState topS = natural(own) ? own.getBlock().defaultBlockState() : st(Blocks.GRASS_BLOCK);
				BlockState fill = natural(under) && !under.is(Blocks.GRASS_BLOCK) ? under.getBlock().defaultBlockState() : st(Blocks.DIRT);
				int grp = p.group();
				for (int y = gy + 1; y <= top; y++) p.put(grp, h.at(i, j, y), y == top ? topS : fill);
				apronGroup.put(BlockPos.asLong(i, 0, j), grp);
			}
		}
		// 2. walls: a mossy-cobblestone sill course, spruce planks, dark-oak posts at the corners, the bow and every third bay; wind-eyes
		//    (no glass in Hrafnholt), a gable door and two side doors
		for (int i = 0; i < len; i++) for (int j = -hm; j <= hm; j++) {
			if (!h.wall(i, j)) continue;
			boolean isPost = h.post(i, j), door = h.door(i, j), gable = i == 0 || i == len - 1;
			boolean window = !isPost && !door && !gable && Math.abs(j) == h.half(i) && Math.floorMod(i - 2, 3) == 1;
			for (int k = 0; k < 3; k++) {
				if (door && k < 2) continue;
				BlockState s = isPost ? post : k == 0 ? mossy : window && k == 1 ? fence(Blocks.SPRUCE_FENCE) : plank;
				p.put(h.at(i, j, floor + k), s);
			}
		}
		// 3. gable fill under the roof, a dark-oak king post in the middle
		for (int i : new int[] {0, len - 1}) for (int j = -(h.half(i) - 1); j <= h.half(i) - 1; j++)
			for (int y = floor + 3; y < h.roofY(floor, i, j); y++) p.put(h.at(i, j, y), j == 0 ? post : plank);
		// 4. finials over both gables: two dark-oak posts crossing above the ridge with a bone "dragon head" between them (one unit each)
		for (int i : new int[] {0, len - 1}) {
			int r = h.roofY(floor, i, 0);
			int grp = p.group();
			p.put(grp, h.at(i, -1, r), st(Blocks.MOSS_BLOCK));
			p.put(grp, h.at(i, 1, r), st(Blocks.MOSS_BLOCK));
			p.put(grp, h.at(i, 0, r + 1), st(Blocks.MOSS_BLOCK));
			for (int k = 1; k <= 2; k++) {
				p.put(grp, h.at(i, -1, r + k), fence(Blocks.DARK_OAK_FENCE, jd));
				p.put(grp, h.at(i, 1, r + k), fence(Blocks.DARK_OAK_FENCE, jd.getOpposite()));
			}
			p.put(grp, h.at(i, 0, r + 2), st(Blocks.BONE_BLOCK));
		}
		// 5. the roof: spruce stairs facing the ridge, 2 out at the eaves, 1 over the gables, a plank ridge; the end bays are narrower, so
		//    the ridge dips 1 there (hogback)
		for (int i = -1; i <= len; i++) {
			int hr = h.half(i);
			for (int j = -(hr + 2); j <= hr + 2; j++) {
				BlockPos q = h.at(i, j, h.roofY(floor, i, j));
				p.put(q, j == 0 ? plank : stair(Blocks.SPRUCE_STAIRS, j < 0 ? jd : jd.getOpposite(), Half.BOTTOM));
			}
		}
		// 6. turf: moss blocks over the stairs and a moss ridge cap (the eave course and the gable overhangs stay timber)
		for (int i = 0; i < len; i++) {
			int hr = h.half(i);
			for (int j = -(hr + 1); j <= hr + 1; j++) p.put(p.group(), h.at(i, j, h.roofY(floor, i, j) + 1), st(Blocks.MOSS_BLOCK));
		}
		// 7. porch (svalgang): dark-oak posts under the low eaves along both long walls, on the apron
		for (int i = 2; i < len - 2; i++) {
			if (Math.floorMod(i - 2, 3) != 0) continue;
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				int j = sgn * (hm + 1);
				int gy = g[i + 2][j + hm + 2];
				int top = Math.max(gy, floor - 2);
				Integer ag = apronGroup.get(BlockPos.asLong(i, 0, j));
				int grp = ag != null ? ag : p.group();
				for (int y = top + 1; y < h.roofY(floor, i, j); y++) p.put(grp, h.at(i, j, y), post);
			}
		}
		// 8. steps up to each door where the plinth lifts the floor (in the apron's unit, so a step never hangs)
		int[][] doors = {{0, 0, -1, 0}, {4, -h.half(4), 0, -1}, {len - 5, h.half(len - 5), 0, 1}};
		for (int[] dr : doors) {
			Direction out = dr[2] < 0 ? id.getOpposite() : dr[3] < 0 ? jd.getOpposite() : jd;
			for (int k = 1; k <= 2; k++) {
				int i = dr[0] + dr[2] * k, j = dr[1] + dr[3] * k;
				int gy = g[i + 2][j + hm + 2];
				if (gy == NONE) break;
				int stepY = floor - k;
				int top = Math.max(gy, floor - 1 - k);
				if (top != stepY - 1) break;
				Integer ag = apronGroup.get(BlockPos.asLong(i, 0, j));
				p.put(ag != null ? ag : p.group(), h.at(i, j, stepY), stair(Blocks.SPRUCE_STAIRS, out.getOpposite(), Half.BOTTOM));
			}
		}
		// 9. inside: the long hearth with the Hearth-Fire, benches along the walls, lanterns hung from the ridge
		int mid = len / 2;
		p.put(h.at(mid, 0, floor), st(Blocks.CAMPFIRE));
		for (int di : new int[] {-2, -1, 1, 2}) p.put(h.at(mid + di, 0, floor), st(Blocks.MOSSY_COBBLESTONE_SLAB));
		for (int i = 3; i <= len - 4; i++) for (int sgn = -1; sgn <= 1; sgn += 2) {
			int j = sgn * (h.half(i) - 1);
			if (h.door(i, j + sgn) || !h.in(i, j) || h.wall(i, j)) continue;
			p.put(p.group(), h.at(i, j, floor), stair(Blocks.SPRUCE_STAIRS, sgn < 0 ? jd.getOpposite() : jd, Half.BOTTOM));
		}
		for (int i : new int[] {len / 4, len - 1 - len / 4}) {
			BlockPos q = h.at(i, 0, h.roofY(floor, i, 0) - 1);
			p.put(p.group(), q, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		}
		// 10. green and brown banners under the gable overhangs, over the doors
		BlockPos fb = h.at(-1, 0, floor + 3), bb = h.at(len, 0, floor + 3);
		p.put(p.group(), fb, wallBanner(b, fb, id.getOpposite()));
		p.put(p.group(), bb, wallBanner(b, bb, id));
		// planned grass under a planned block (porch posts, steps) would die to dirt and outlive a restore: plan earth there instead
		for (var e : p.ops.entrySet()) {
			BlockPos q = BlockPos.of(e.getKey());
			long below = BlockPos.asLong(q.getX(), q.getY() - 1, q.getZ());
			BlockState u = p.ops.get(below);
			if (u != null && u.is(Blocks.GRASS_BLOCK)) p.ops.put(below, st(Blocks.DIRT));
		}
		return p;
	}

	static void hallReport(B b, Hall h, int[][] g, int floor, int min, int max) {
		Report rep = b.rep;
		rep.hallFloor = floor;
		rep.hallMinGround = min;
		rep.hallMaxGround = max;
		rep.hallLen = h.len;
		rep.hallAlongContour = alongSlope(h, g) <= acrossSlope(h, g);
		rep.spots.put("longhall", h.at(h.len / 2, 0, floor));
		for (int i = -2; i <= h.len + 1; i++) for (int j = -(h.hm + 2); j <= h.hm + 2; j++) {
			int d = h.dist(i, j);
			if (d > 2) continue;
			int gy = g[i + 2][j + h.hm + 2];
			BlockPos q = h.at(i, j, 0);
			if (d == 0) {
				boolean door = h.door(i, j);
				if (h.wall(i, j)) {
					int top = door ? floor - 1 : floor + 2;
					if (top >= gy + 1) rep.columns.add(new int[] {q.getX(), q.getZ(), gy + 1, top});
				} else {
					if (floor - 1 >= gy + 1) rep.columns.add(new int[] {q.getX(), q.getZ(), gy + 1, floor - 1});
					if (i != h.len / 2 || j != 0) rep.open.add(new BlockPos(q.getX(), floor + 1, q.getZ()));
					reserve(b, q.getX(), q.getZ(), floor, floor + 1);
				}
			} else if (gy != NONE) reserve(b, q.getX(), q.getZ(), gy + 1, floor + 2);
		}
	}

	/** Self-test entry: plans and writes a longhall near {@code near} for record {@code r} (owner Hrafnholt); returns its report. */
	public static Report testLonghall(ServerLevel level, VillageRecord r, BlockPos near) {
		RivalPlans.Ctx c = new RivalPlans.Ctx(level, r, level.getRandom(), FactionVillages.of(r.owner));
		c.bell = near;
		Report rep = new Report();
		longhall(new B(c, rep));
		for (VillageRecord.Op op : c.ops) BlockWriter.write(level, r, BlockPos.of(op.pos()), op.state());
		return rep;
	}

	// ---- watch tower --------------------------------------------------------------------------------------------------

	static void watchTower(B b) {
		for (int t = 0; t < 90; t++) {
			BlockPos s = b.c.bell.offset(b.c.rnd.nextInt(49) - 24, 0, b.c.rnd.nextInt(49) - 24);
			int x0 = s.getX(), z0 = s.getZ();
			int[][] g = new int[5][5];
			int coreMax = NONE, coreMin = Integer.MAX_VALUE, ringMax = NONE;
			boolean ok = true;
			for (int dx = -2; dx <= 2 && ok; dx++) for (int dz = -2; dz <= 2 && ok; dz++) {
				int gy = ground(b, x0 + dx, z0 + dz);
				if (gy == NONE) {
					ok = false;
					break;
				}
				g[dx + 2][dz + 2] = gy;
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					coreMax = Math.max(coreMax, gy);
					coreMin = Math.min(coreMin, gy);
				} else ringMax = Math.max(ringMax, gy);
			}
			if (!ok || coreMax - coreMin > 2) continue;
			int T = coreMax + 1;
			if (ringMax > T + 1) continue;
			Part p = new Part();
			// corner posts on their own ground: a mossy-cobblestone foot, dark-oak logs to the gallery
			for (int cx = -1; cx <= 1; cx += 2) for (int cz = -1; cz <= 1; cz += 2) {
				int gy = g[cx + 2][cz + 2];
				for (int y = gy + 1; y <= T + 3; y++) p.put(new BlockPos(x0 + cx, y, z0 + cz), y == gy + 1 ? st(Blocks.MOSSY_COBBLESTONE) : log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
			}
			// corbels under the overhanging hoarding gallery (upside-down stairs against the posts)
			for (int cx = -1; cx <= 1; cx += 2) for (int cz = -1; cz <= 1; cz += 2) {
				p.put(new BlockPos(x0 + 2 * cx, T + 3, z0 + cz), stair(Blocks.SPRUCE_STAIRS, cx > 0 ? Direction.WEST : Direction.EAST, Half.TOP));
				p.put(new BlockPos(x0 + cx, T + 3, z0 + 2 * cz), stair(Blocks.SPRUCE_STAIRS, cz > 0 ? Direction.NORTH : Direction.SOUTH, Half.TOP));
			}
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) p.put(new BlockPos(x0 + dx, T + 4, z0 + dz), st(Blocks.SPRUCE_PLANKS));
			// the rail around the gallery, joined along the ring; a banner on one corner
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
				if (dx == 2 && dz == 2) continue;
				List<Direction> con = new ArrayList<>();
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = dx + d.getStepX(), nz = dz + d.getStepZ();
					if (Math.abs(nx) > 2 || Math.abs(nz) > 2 || Math.abs(nx) != 2 && Math.abs(nz) != 2 || nx == 2 && nz == 2) continue;
					con.add(d);
				}
				p.put(new BlockPos(x0 + dx, T + 5, z0 + dz), fence(Blocks.SPRUCE_FENCE, con.toArray(new Direction[0])));
			}
			BlockPos bn = new BlockPos(x0 + 2, T + 5, z0 + 2);
			int bg = p.group();
			p.put(bg, bn, banner(b, bn, 6));
			// the turf roof on dark-oak posts, high enough for the archer underneath (one unit)
			int rg = p.group();
			for (int cx = -1; cx <= 1; cx += 2) for (int cz = -1; cz <= 1; cz += 2) for (int y = T + 5; y <= T + 7; y++) p.put(rg, new BlockPos(x0 + cx, y, z0 + cz), fence(Blocks.DARK_OAK_FENCE));
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				BlockPos q = new BlockPos(x0 + dx, T + 9 - ring, z0 + dz);
				if (ring == 0) {
					p.put(rg, q, st(Blocks.MOSS_BLOCK));
					continue;
				}
				Direction f = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
				p.put(rg, q, stair(Blocks.SPRUCE_STAIRS, f, Half.BOTTOM));
			}
			Set<Long> done = commit(b, p, q -> writable(b, q));
			if (done == null) continue;
			if (done.contains(bn.asLong())) b.rep.banners++;
			b.c.towerTop = new BlockPos(x0, T + 5, z0);
			for (int cx = -1; cx <= 1; cx += 2) for (int cz = -1; cz <= 1; cz += 2) b.rep.columns.add(new int[] {x0 + cx, z0 + cz, g[cx + 2][cz + 2] + 1, T + 3});
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) reserve(b, x0 + dx, z0 + dz, g[dx + 2][dz + 2] + 1, T + 3);
			b.rep.spots.put("watchtower", new BlockPos(x0, T, z0));
			return;
		}
	}

	// ---- thing-stead and runestones ------------------------------------------------------------------------------------

	static final int[][] RING = {{4, 0}, {3, 3}, {0, 4}, {-3, 3}, {-4, 0}, {-3, -3}, {0, -4}, {3, -3}};

	/** A ring of eight standing stones round a law rock on the flattest open meadow of the village. */
	static void thingstead(B b) {
		BlockPos best = null;
		int[][] bestG = null;
		int bestSpread = 3;
		// every second cell within 30 of the bell, nearest first (70 random tries missed the open meadow in a crowded village: s-asm-vill)
		List<int[]> cand = new ArrayList<>();
		for (int dx = -56; dx <= 56; dx += 2) for (int dz = -56; dz <= 56; dz += 2) cand.add(new int[] {dx, dz});
		cand.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
		for (int t = 0; t < cand.size() && bestSpread > 0; t++) {
			BlockPos s = b.c.bell.offset(cand.get(t)[0], 0, cand.get(t)[1]);
			int[][] g = new int[9][9];
			int max = NONE, min = Integer.MAX_VALUE;
			boolean ok = true;
			for (int dx = -4; dx <= 4 && ok; dx++) for (int dz = -4; dz <= 4 && ok; dz++) {
				if (dx * dx + dz * dz > 20) continue;
				int gy = ground(b, s.getX() + dx, s.getZ() + dz);
				if (gy == NONE || !writable(b, new BlockPos(s.getX() + dx, gy + 1, s.getZ() + dz)) || nextToDoor(b, s.getX() + dx, gy, s.getZ() + dz)) {
					ok = false;
					break;
				}
				g[dx + 4][dz + 4] = gy;
				max = Math.max(max, gy);
				min = Math.min(min, gy);
			}
			if (!ok || max - min >= bestSpread) continue;
			best = s;
			bestG = g;
			bestSpread = max - min;
		}
		if (best == null) return;
		Part p = new Part();
		for (int k = 0; k < RING.length; k++) {
			int dx = RING[k][0], dz = RING[k][1], gy = bestG[dx + 4][dz + 4];
			boolean tall = k % 2 == 0;
			BlockPos q = new BlockPos(best.getX() + dx, gy + 1, best.getZ() + dz);
			p.put(q, st(Blocks.STONE));
			p.put(q.above(), tall ? st(Blocks.STONE) : st(Blocks.ANDESITE_WALL));
			if (tall) p.put(q.above(2), st(Blocks.ANDESITE_WALL));
		}
		int cy = bestG[4][4];
		p.put(new BlockPos(best.getX(), cy + 1, best.getZ()), st(Blocks.MOSSY_COBBLESTONE));
		if (commit(b, p, q -> writable(b, q)) == null) return;
		for (int k = 0; k < RING.length; k++) {
			int dx = RING[k][0], dz = RING[k][1], gy = bestG[dx + 4][dz + 4];
			b.rep.columns.add(new int[] {best.getX() + dx, best.getZ() + dz, gy + 1, gy + (k % 2 == 0 ? 3 : 2)});
		}
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) if (dx * dx + dz * dz <= 20) reserve(b, best.getX() + dx, best.getZ() + dz, bestG[dx + 4][dz + 4] + 1, bestG[dx + 4][dz + 4] + 1);
		b.rep.stones = RING.length;
		b.rep.spots.put("thingstead", new BlockPos(best.getX(), cy + 1, best.getZ()));
	}

	/** Road forks: path cells from which the road runs on for 4 blocks in 3 or 4 directions (one per 12 blocks, at most 3). */
	static List<BlockPos> forks(B b) {
		List<BlockPos> out = new ArrayList<>();
		Map<Long, Boolean> cache = new HashMap<>();
		Predicate<long[]> path = xz -> cache.computeIfAbsent(BlockPos.asLong((int) xz[0], 0, (int) xz[1]), k -> isPath(b, (int) xz[0], (int) xz[1]));
		for (VillageRecord.Piece pc : b.c.r.pieces) {
			if (pc.type() != VillageRecord.STREET) continue;
			BoundingBox box = pc.box();
			for (int x = box.minX(); x <= box.maxX() && out.size() < 3; x++) for (int z = box.minZ(); z <= box.maxZ() && out.size() < 3; z++) {
				if (!path.test(new long[] {x, z})) continue;
				int dirs = 0;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					boolean run = true;
					for (int k = 1; k <= 4 && run; k++) run = path.test(new long[] {x + d.getStepX() * k, z + d.getStepZ() * k});
					if (run) dirs++;
				}
				if (dirs < 3) continue;
				boolean far = true;
				for (BlockPos f : out) if (Math.abs(f.getX() - x) < 12 && Math.abs(f.getZ() - z) < 12) far = false;
				if (far) out.add(new BlockPos(x, 0, z));
			}
		}
		return out;
	}

	/** A runestone (stone, carved stone, a worn cap) at the roadside of each fork. */
	static void runestones(B b) {
		List<BlockPos> forks = forks(b);
		b.rep.forks = forks.size();
		for (BlockPos f : forks) {
			placed:
			for (int r = 2; r <= 5; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
				int x = f.getX() + dx, z = f.getZ() + dz;
				int gy = ground(b, x, z);
				if (gy == NONE || nextToDoor(b, x, gy, z)) continue;
				boolean roadside = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (isPath(b, x + d.getStepX(), z + d.getStepZ())) roadside = true;
				if (!roadside) continue;
				Part p = new Part();
				BlockPos q = new BlockPos(x, gy + 1, z);
				p.put(q, st(Blocks.STONE));
				p.put(q.above(), st(Blocks.CHISELED_STONE_BRICKS));
				p.put(q.above(2), st(Blocks.MOSSY_STONE_BRICK_WALL));
				if (commit(b, p, w -> writable(b, w)) == null) continue;
				b.rep.columns.add(new int[] {x, z, gy + 1, gy + 3});
				b.rep.runestones++;
				b.rep.spots.putIfAbsent("runestone", q);
				break placed;
			}
		}
	}

	// ---- farmsteads ---------------------------------------------------------------------------------------------------

	static void tofts(B b) {
		for (VillageRecord.Piece pc : b.c.r.pieces) {
			if (b.rep.tofts >= 6) break;
			if (pc.type() != VillageRecord.HOUSE) continue;
			search:
			for (Direction side : Direction.Plane.HORIZONTAL) for (int out = 1; out <= 3; out++) for (int slide = -2; slide <= 2; slide += 2) {
				if (toft(b, pc.box(), side, out, slide)) {
					b.rep.tofts++;
					break search;
				}
			}
		}
	}

	/** A fenced toft (7 x 5) beside a house: gate toward the house, a drying rack (fence posts with hay), a hay bale and a woodpile. */
	static boolean toft(B b, BoundingBox hb, Direction side, int out, int slide) {
		final int W = 7, D = 5;
		Direction along = side.getClockWise();
		int ex = side == Direction.EAST ? hb.maxX() : side == Direction.WEST ? hb.minX() : (hb.minX() + hb.maxX()) / 2;
		int ez = side == Direction.SOUTH ? hb.maxZ() : side == Direction.NORTH ? hb.minZ() : (hb.minZ() + hb.maxZ()) / 2;
		int[][] g = new int[W][D];
		int max = NONE, min = Integer.MAX_VALUE;
		for (int a = 0; a < W; a++) for (int c = 0; c < D; c++) {
			int x = ex + side.getStepX() * (out + c) + along.getStepX() * (slide - 3 + a);
			int z = ez + side.getStepZ() * (out + c) + along.getStepZ() * (slide - 3 + a);
			if (x >= hb.minX() && x <= hb.maxX() && z >= hb.minZ() && z <= hb.maxZ()) return false;
			int gy = ground(b, x, z);
			if (gy == NONE || !writable(b, new BlockPos(x, gy + 1, z)) || nextToDoor(b, x, gy, z)) return false;
			g[a][c] = gy;
			max = Math.max(max, gy);
			min = Math.min(min, gy);
		}
		if (max - min > 1) return false;
		if (g[2][2] != g[3][2] || g[3][2] != g[4][2]) return false;
		Part p = new Part();
		java.util.function.BiFunction<Integer, Integer, BlockPos> at = (a, c) -> new BlockPos(ex + side.getStepX() * (out + c) + along.getStepX() * (slide - 3 + a), g[a][c] + 1,
			ez + side.getStepZ() * (out + c) + along.getStepZ() * (slide - 3 + a));
		for (int a = 0; a < W; a++) for (int c = 0; c < D; c++) {
			boolean ring = a == 0 || a == W - 1 || c == 0 || c == D - 1;
			if (!ring) continue;
			if (a == 3 && c == 0) {
				p.put(at.apply(a, c), st(Blocks.SPRUCE_FENCE_GATE).setValue(FenceGateBlock.FACING, side));
				continue;
			}
			List<Direction> con = new ArrayList<>();
			int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] n : nb) {
				int na = a + n[0], nc = c + n[1];
				if (na < 0 || na >= W || nc < 0 || nc >= D) continue;
				if (!(na == 0 || na == W - 1 || nc == 0 || nc == D - 1) || g[na][nc] != g[a][c]) continue;
				con.add(n[0] > 0 ? along : n[0] < 0 ? along.getOpposite() : n[1] > 0 ? side : side.getOpposite());
			}
			p.put(at.apply(a, c), fence(Blocks.SPRUCE_FENCE, con.toArray(new Direction[0])));
		}
		// the drying rack (hesje): three joined posts, the outer two two high, hay hung over the middle
		BlockPos r2 = at.apply(2, 2), r3 = at.apply(3, 2), r4 = at.apply(4, 2);
		p.put(r2, fence(Blocks.SPRUCE_FENCE, along));
		p.put(r3, fence(Blocks.SPRUCE_FENCE, along, along.getOpposite()));
		p.put(r4, fence(Blocks.SPRUCE_FENCE, along.getOpposite()));
		p.put(r2.above(), fence(Blocks.SPRUCE_FENCE, along));
		p.put(r3.above(), st(Blocks.HAY_BLOCK).setValue(RotatedPillarBlock.AXIS, along.getAxis()));
		p.put(r4.above(), fence(Blocks.SPRUCE_FENCE, along.getOpposite()));
		p.put(at.apply(1, 3), st(Blocks.HAY_BLOCK));
		p.put(at.apply(5, 1), log(Blocks.SPRUCE_LOG, along.getAxis()));
		p.put(at.apply(5, 3), log(Blocks.SPRUCE_LOG, along.getAxis()));
		if (commit(b, p, q -> writable(b, q)) == null) return false;
		for (int a = 0; a < W; a++) for (int c = 0; c < D; c++) {
			BlockPos q = at.apply(a, c);
			b.c.used.add(q.asLong());
			if (a == 0 || a == W - 1 || c == 0 || c == D - 1) b.rep.columns.add(new int[] {q.getX(), q.getZ(), q.getY(), q.getY()});
		}
		for (BlockPos q : new BlockPos[] {r2, r3, r4}) b.rep.columns.add(new int[] {q.getX(), q.getZ(), q.getY(), q.getY() + 1});
		b.rep.spots.putIfAbsent("toft", r3);
		return true;
	}

	static void houseBanners(B b) {
		for (VillageRecord.Piece pc : b.c.r.pieces) {
			if (b.rep.banners >= MAX_BANNERS) return;
			if (pc.type() != VillageRecord.HOUSE) continue;
			BlockPos ctr = pc.box().getCenter();
			if (!b.level.isLoaded(ctr)) continue;
			int top = b.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ctr.getX(), ctr.getZ());
			BlockPos at = new BlockPos(ctr.getX(), top, ctr.getZ());
			if (top > pc.box().maxY() + 2 || !writable(b, at) || !b.level.getBlockState(at.below()).isSolid()) continue;
			b.c.add(at, banner(b, at, 0));
			b.rep.banners++;
		}
	}

	// ---- palisade -----------------------------------------------------------------------------------------------------

	/** A door beside the column (at the two heights a palisade log would stand in front of): that column stays open. */
	static boolean nextToDoor(B b, int x, int top, int z) {
		for (Direction d : Direction.Plane.HORIZONTAL) for (int dy = 1; dy <= 2; dy++) {
			BlockPos q = new BlockPos(x + d.getStepX(), top + dy, z + d.getStepZ());
			if (!b.level.isLoaded(q)) return true; // never load a neighbour chunk at plan time; unknown counts as a door (s-review-kingdom)
			if (b.level.getBlockState(q).getBlock() instanceof DoorBlock) return true;
		}
		return false;
	}

	static boolean inVillageColumn(VillageRecord r, int x, int z) {
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() == RivalPlans.PIER) continue;
			BoundingBox bx = pc.box();
			if (x >= bx.minX() && x <= bx.maxX() && z >= bx.minZ() && z <= bx.maxZ()) return true;
		}
		return false;
	}

	/**
	 * The palisade on the outer edge of the village's own pieces (piers excluded): contiguous logs 3 and 4 high with sharpened fence tips,
	 * each column on its own ground; gates (where a path leaves) get dark-oak posts with bone caps and, where both posts stand, a plank lintel
	 * with a banner; a water gate opens toward the jetty.
	 */
	static void palisade(B b, @Nullable BlockPos waterGate) {
		VillageRecord r = b.c.r;
		Map<Long, Integer> wall = new LinkedHashMap<>(), posts = new LinkedHashMap<>(), gates = new LinkedHashMap<>();
		Set<Long> cols = new HashSet<>();
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() == RivalPlans.PIER) continue;
			BoundingBox bx = pc.box();
			for (int x = bx.minX(); x <= bx.maxX(); x++) for (int z = bx.minZ(); z <= bx.maxZ(); z++) {
				if (x != bx.minX() && x != bx.maxX() && z != bx.minZ() && z != bx.maxZ()) continue;
				long key = BlockPos.asLong(x, 0, z);
				if (!cols.add(key)) continue;
				boolean outer = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (!inVillageColumn(r, x + d.getStepX(), z + d.getStepZ())) outer = true;
				if (!outer || !b.level.isLoaded(new BlockPos(x, bx.minY(), z))) continue;
				int top = b.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				BlockState g = b.level.getBlockState(new BlockPos(x, top, z));
				if (g.is(Blocks.DIRT_PATH)) {
					gates.put(key, top);
					continue;
				}
				if (!natural(g) || !writable(b, new BlockPos(x, top + 1, z)) || nextToDoor(b, x, top, z)) continue;
				boolean nearGate = false;
				for (Direction d : Direction.Plane.HORIZONTAL) if (isPath(b, x + d.getStepX(), z + d.getStepZ())) nearGate = true;
				(nearGate ? posts : wall).put(key, top);
			}
		}
		// the water gate: the wall column nearest the jetty's root opens 3 wide between two posts
		if (waterGate != null) {
			long near = 0;
			double nd = 24 * 24 + 1;
			for (long k : wall.keySet()) {
				double d = Math.pow(BlockPos.getX(k) - waterGate.getX(), 2) + Math.pow(BlockPos.getZ(k) - waterGate.getZ(), 2);
				if (d < nd) {
					nd = d;
					near = k;
				}
			}
			if (nd <= 24 * 24) {
				int nx = BlockPos.getX(near), nz = BlockPos.getZ(near);
				for (long k : new ArrayList<>(wall.keySet())) {
					int cheb = Math.max(Math.abs(BlockPos.getX(k) - nx), Math.abs(BlockPos.getZ(k) - nz));
					if (cheb <= 1) wall.remove(k);
					else if (cheb == 2) posts.put(k, wall.remove(k));
				}
				b.rep.spots.put("water_gate", new BlockPos(nx, b.c.bell.getY(), nz));
			}
		}
		// lintels: a run of at most 5 gate cells along the line with a post at each end
		Set<Long> spanned = new HashSet<>();
		Map<Long, Integer> postTop = new HashMap<>();
		int gateBanners = 0;
		for (long k : gates.keySet()) {
			if (spanned.contains(k)) continue;
			int x = BlockPos.getX(k), z = BlockPos.getZ(k);
			for (Direction.Axis ax : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
				int sx = ax == Direction.Axis.X ? 1 : 0, sz = 1 - sx;
				int lo = 0, hi = 0;
				while (lo < 6 && gates.containsKey(BlockPos.asLong(x - sx * (lo + 1), 0, z - sz * (lo + 1)))) lo++;
				while (hi < 6 && gates.containsKey(BlockPos.asLong(x + sx * (hi + 1), 0, z + sz * (hi + 1)))) hi++;
				long pa = BlockPos.asLong(x - sx * (lo + 1), 0, z - sz * (lo + 1)), pb = BlockPos.asLong(x + sx * (hi + 1), 0, z + sz * (hi + 1));
				if (lo + hi + 1 > 5 || !posts.containsKey(pa) || !posts.containsKey(pb)) continue;
				int ly = Math.max(posts.get(pa), posts.get(pb)) + 5;
				Part p = new Part();
				for (int s = -lo; s <= hi; s++) p.put(new BlockPos(x + sx * s, ly, z + sz * s), st(Blocks.DARK_OAK_PLANKS));
				int mid = (hi - lo) / 2;
				BlockPos bn = new BlockPos(x + sx * mid, ly + 1, z + sz * mid);
				int bg = p.group();
				if (gateBanners < 4 && b.rep.banners < MAX_BANNERS) p.put(bg, bn, banner(b, bn, ax == Direction.Axis.X ? 0 : 4));
				// both posts must stand to the lintel: plan them with it
				for (long pk : new long[] {pa, pb}) {
					int gy = posts.get(pk);
					for (int y = gy + 1; y <= ly; y++) p.put(new BlockPos(BlockPos.getX(pk), y, BlockPos.getZ(pk)), y == gy + 1 ? st(Blocks.MOSSY_COBBLESTONE) : log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
					p.put(new BlockPos(BlockPos.getX(pk), ly + 1, BlockPos.getZ(pk)), st(Blocks.BONE_BLOCK));
				}
				// posts first, then the lintel (supports before what they hold)
				Part ordered = new Part();
				for (var e : p.ops.entrySet()) if (!p.ops.get(e.getKey()).is(Blocks.DARK_OAK_PLANKS) && !(e.getValue().getBlock() instanceof BannerBlock)) ordered.put(BlockPos.of(e.getKey()), e.getValue());
				for (var e : p.ops.entrySet()) if (e.getValue().is(Blocks.DARK_OAK_PLANKS)) ordered.put(BlockPos.of(e.getKey()), e.getValue());
				for (var e : p.ops.entrySet()) if (e.getValue().getBlock() instanceof BannerBlock) ordered.put(ordered.group(), BlockPos.of(e.getKey()), e.getValue());
				Set<Long> done = commit(b, ordered, q -> writable(b, q));
				if (done == null) continue;
				if (done.contains(bn.asLong())) {
					gateBanners++;
					b.rep.banners++;
				}
				for (long pk : new long[] {pa, pb}) postTop.put(pk, ly + 1);
				for (int s = -lo; s <= hi; s++) spanned.add(BlockPos.asLong(x + sx * s, 0, z + sz * s));
				b.rep.spots.putIfAbsent("gate", new BlockPos(x, ly - 4, z));
				break;
			}
		}
		// the remaining gate posts: dark-oak logs with a bone cap
		for (var e : posts.entrySet()) {
			if (postTop.containsKey(e.getKey())) {
				b.rep.columns.add(new int[] {BlockPos.getX(e.getKey()), BlockPos.getZ(e.getKey()), e.getValue() + 1, postTop.get(e.getKey())});
				continue;
			}
			int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey()), gy = e.getValue();
			Part p = new Part();
			for (int y = gy + 1; y <= gy + 4; y++) p.put(new BlockPos(x, y, z), y == gy + 1 ? st(Blocks.MOSSY_COBBLESTONE) : log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
			p.put(new BlockPos(x, gy + 5, z), st(Blocks.BONE_BLOCK));
			if (commit(b, p, q -> writable(b, q)) != null) b.rep.columns.add(new int[] {x, z, gy + 1, gy + 5});
		}
		// the wall: spruce logs (every fourth dark oak), alternately 3 and 4 high, with sharpened spruce-fence tips
		for (var e : wall.entrySet()) {
			int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey()), gy = e.getValue();
			int hgt = 3 + ((x + z) & 1);
			BlockState lg = ((x ^ z) & 3) == 0 ? log(Blocks.DARK_OAK_LOG, Direction.Axis.Y) : log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
			Part p = new Part();
			for (int y = gy + 1; y <= gy + hgt; y++) p.put(new BlockPos(x, y, z), lg);
			p.put(new BlockPos(x, gy + hgt + 1, z), fence(Blocks.SPRUCE_FENCE));
			if (commit(b, p, q -> writable(b, q)) != null) b.rep.columns.add(new int[] {x, z, gy + 1, gy + hgt + 1});
		}
	}

	// ---- waterfront (extends the 2.1 pier: STRUCTURES 2.11 jetty, naust, boat on the strand) --------------------------

	/** Top water source y of an open-water column, else {@link #NONE}. */
	static int wtop(B b, int x, int z) {
		return b.level.isLoaded(new BlockPos(x, b.c.bell.getY(), z)) ? RivalPlans.waterTop(b.level, x, z) : NONE;
	}

	/** Water depth below {@code wy} down to a solid bed (1-14), else {@link #NONE} (too deep, or weed or air under). */
	static int depth(B b, int x, int z, int wy) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, wy, z);
		int n = 0;
		while (n < 15 && b.level.getBlockState(m).is(Blocks.WATER)) {
			n++;
			m.move(Direction.DOWN);
		}
		if (n == 0 || n > 14) return NONE;
		BlockState bed = b.level.getBlockState(m);
		return bed.isSolid() && bed.getFluidState().isEmpty() && !bed.canBeReplaced() ? n : NONE;
	}

	/** The pier box allows what the writer allows there: air, a plant, or a log in water (piles). */
	static boolean wetOk(B b, BlockPos p, BlockState target) {
		if (b.c.used.contains(p.asLong()) || !b.level.isLoaded(p)) return false;
		BlockState s = b.level.getBlockState(p);
		if (s.is(Blocks.WATER)) return target.is(BlockTags.LOGS);
		return clear(s, true);
	}

	static @Nullable BlockPos waterfront(B b) {
		// one waterfront per village: a pier built before (2.1 or an earlier plan) stays, and the water gate opens toward it
		for (VillageRecord.Piece pc : b.c.r.pieces) if (pc.type() == RivalPlans.PIER) return pc.box().getCenter();
		BlockPos ctr = b.c.r.center;
		for (int rad = 6; rad <= 48; rad += 2) for (Direction d : Direction.Plane.HORIZONTAL) for (int lat = -12; lat <= 12; lat += 4) {
			BlockPos base = ctr.relative(d, rad).relative(d.getClockWise(), lat);
			int wy = wtop(b, base.getX(), base.getZ());
			if (wy == NONE) continue;
			BlockPos land = base.relative(d, -1);
			if (wtop(b, land.getX(), land.getZ()) != NONE) continue;
			int lg = ground(b, land.getX(), land.getZ());
			if (lg != wy && lg != wy + 1) continue;
			if (shore(b, base, d, wy)) return land.atY(lg + 1);
		}
		return null;
	}

	static boolean shore(B b, BlockPos base, Direction d, int wy) {
		Direction sd = d.getClockWise();
		java.util.function.IntBinaryOperator X = (u, v) -> base.getX() + d.getStepX() * u + sd.getStepX() * v;
		java.util.function.IntBinaryOperator Z = (u, v) -> base.getZ() + d.getStepZ() * u + sd.getStepZ() * v;
		// 1. the jetty: 3 wide, from the shore out to depth 3 (and 2 beyond), at most 14 long
		int len = 0, deep = -1;
		int[][] dep = new int[14][5];
		for (int u = 0; u < 14; u++) {
			boolean ok = true;
			for (int v = -1; v <= 1 && ok; v++) {
				int x = X.applyAsInt(u, v), z = Z.applyAsInt(u, v);
				ok = wtop(b, x, z) == wy && (dep[u][v + 2] = depth(b, x, z, wy)) != NONE && dep[u][v + 2] <= MAX_PILE;
			}
			if (!ok) break;
			len = u + 1;
			if (deep < 0 && dep[u][2] >= 3) deep = u;
			if (deep >= 0 && u >= deep + 2) break;
		}
		if (len < 3) return false;
		boolean head = false;
		if (len >= 7) {
			head = true;
			for (int u = len - 2; u < len && head; u++) for (int v = -2; v <= 2 && head; v += 4) {
				int x = X.applyAsInt(u, v), z = Z.applyAsInt(u, v);
				head = wtop(b, x, z) == wy && (dep[u][v + 2] = depth(b, x, z, wy)) != NONE && dep[u][v + 2] <= MAX_PILE;
			}
		}
		Part p = new Part();
		BlockState pile = log(Blocks.SPRUCE_LOG, Direction.Axis.Y), slab = st(Blocks.SPRUCE_SLAB);
		List<int[]> piles = new ArrayList<>();
		for (int u = 0; u < len; u++) {
			boolean at = Math.floorMod(u, 3) == 2 || u == len - 1;
			if (!at) continue;
			int w = head && u >= len - 2 ? 2 : 1;
			for (int v = -w; v <= w; v += 2 * w) piles.add(new int[] {u, v});
			if (head && u == len - 1) for (int v = -1; v <= 1; v += 2) piles.add(new int[] {u, v});
		}
		for (int[] pl : piles) {
			int x = X.applyAsInt(pl[0], pl[1]), z = Z.applyAsInt(pl[0], pl[1]), dp = dep[pl[0]][pl[1] + 2];
			for (int y = wy - dp + 1; y <= wy; y++) p.put(new BlockPos(x, y, z), pile);
		}
		for (int u = 0; u < len; u++) {
			int w = head && u >= len - 2 ? 2 : 1;
			for (int v = -w; v <= w; v++) p.put(new BlockPos(X.applyAsInt(u, v), wy + 1, Z.applyAsInt(u, v)), slab);
		}
		for (int u = 2; u < len - 1; u += 3) for (int v = -1; v <= 1; v += 2) p.put(new BlockPos(X.applyAsInt(u, v), wy + 2, Z.applyAsInt(u, v)), fence(Blocks.SPRUCE_FENCE));
		int lw = head ? 2 : 1;
		for (int v = -lw; v <= lw; v += 2 * lw) {
			BlockPos q = new BlockPos(X.applyAsInt(len - 1, v), wy + 2, Z.applyAsInt(len - 1, v));
			p.put(q, fence(Blocks.SPRUCE_FENCE));
			p.put(q.above(), st(Blocks.LANTERN));
		}
		if (!fits(p, q -> wetOk(b, q, p.ops.get(q.asLong())))) return false;
		// 2. the naust: a boathouse astride the shoreline beside the jetty (either side), walls on piles over the water, an open boat bay
		// fx-hrafwater: lanes 3..8 out from the jetty on either side (it used to try only lanes 3..7, so a path, a village block or a planned
		// cell on that one strip of bank left the waterfront without its naust)
		Part house = null;
		int hs = 0, ho = 3;
		StringBuilder why = new StringBuilder();
		for (int off = 3; off <= 8 && house == null; off++) for (int s : new int[] {1, -1}) {
			house = naust(b, X, Z, sd, d, wy, s, off);
			if (house != null) {
				hs = s;
				ho = off;
				break;
			}
			if (why.length() < 400) why.append(why.length() == 0 ? "" : "; ").append(s * off).append(": ").append(naustWhy);
		}
		b.rep.naustWhy = house != null ? "" : why.toString();
		// 3. a boat drawn up on the strand, mast stepped, green-and-brown sail
		// (the far side first; on the naust's side only behind its back eave)
		Part boat = null;
		for (int s : hs != 0 ? new int[] {-hs, hs} : new int[] {-1, 1}) {
			int from = s == hs ? -9 : -7;
			for (int u0 = from; u0 >= from - 4 && boat == null; u0--) boat = strandBoat(b, X, Z, sd, d, s, u0);
			if (boat != null) break;
		}
		// commit: jetty, naust, boat; then the pier box over all of it (the writer lets piles replace water only inside it)
		Set<Long> all = new HashSet<>();
		Set<Long> done = commit(b, p, q -> wetOk(b, q, p.ops.get(q.asLong())));
		if (done == null) return false;
		all.addAll(done);
		for (int[] pl : piles) {
			int x = X.applyAsInt(pl[0], pl[1]), z = Z.applyAsInt(pl[0], pl[1]), dp = dep[pl[0]][pl[1] + 2];
			b.rep.columns.add(new int[] {x, z, wy - dp + 1, wy});
			b.rep.piles++;
		}
		if (house != null) {
			final Part hp = house;
			Set<Long> hd = commit(b, hp, q -> wetOk(b, q, hp.ops.get(q.asLong())));
			if (hd != null) {
				all.addAll(hd);
				b.rep.boathouse = true;
				for (var e : hp.ops.entrySet()) if (hd.contains(e.getKey()) && e.getValue().getBlock() instanceof WallBannerBlock) b.rep.banners++;
				b.rep.columns.addAll(naustColumns);
				b.rep.spots.put("boathouse", new BlockPos(X.applyAsInt(2, hs * (ho + 2)), wy + 1, Z.applyAsInt(2, hs * (ho + 2))));
			} else b.rep.naustWhy = "naust planned but its commit failed";
		}
		if (boat != null) {
			final Part bp = boat;
			Set<Long> bd = commit(b, bp, q -> wetOk(b, q, bp.ops.get(q.asLong())));
			if (bd != null) {
				all.addAll(bd);
				b.rep.boat = true;
				b.rep.spots.put("strand_boat", boatSpot);
			}
		}
		int[] mm = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (long k : all) {
			mm[0] = Math.min(mm[0], BlockPos.getX(k));
			mm[1] = Math.min(mm[1], BlockPos.getY(k));
			mm[2] = Math.min(mm[2], BlockPos.getZ(k));
			mm[3] = Math.max(mm[3], BlockPos.getX(k));
			mm[4] = Math.max(mm[4], BlockPos.getY(k));
			mm[5] = Math.max(mm[5], BlockPos.getZ(k));
		}
		b.c.r.pieces.add(new VillageRecord.Piece(new BoundingBox(mm[0] - 1, mm[1] - 1, mm[2] - 1, mm[3] + 1, mm[4] + 1, mm[5] + 1), RivalPlans.PIER));
		b.rep.spots.put("pier", new BlockPos(X.applyAsInt(len - 1, 0), wy + 1, Z.applyAsInt(len - 1, 0)));
		return true;
	}

	/** Grounded wall columns of the last planned naust (piles plus wall), for the report. */
	static final List<int[]> naustColumns = new ArrayList<>();
	static @Nullable BlockPos boatSpot;

	/**
	 * The naust: lanes {@code s*(3..7)} beside the jetty, rows -3 (back wall on the bank) to 2 (open bay mouth over the water). Walls stand on
	 * the bank or, over the water, on log piles down to the bed; a plank lintel over the mouth; a steep spruce roof with moss turf; a banner on
	 * the front gable and a lantern hung inside.
	 */
	static String naustWhy = "";

	static @Nullable Part naust(B b, java.util.function.IntBinaryOperator X, java.util.function.IntBinaryOperator Z, Direction sd, Direction d, int wy, int s, int off) {
		naustColumns.clear();
		int[][] base = new int[6][5], dep = new int[6][5];
		boolean[][] wet = new boolean[6][5];
		for (int k = 0; k <= 4; k++) for (int u = -3; u <= 2; u++) {
			int v = s * (off + k), x = X.applyAsInt(u, v), z = Z.applyAsInt(u, v);
			boolean interior = k >= 1 && k <= 3;
			int w = wtop(b, x, z);
			naustWhy = "u " + u + " k " + k + " at " + x + "," + z;
			if (w == wy) {
				if (u < 0) return whyNull("wet bank"); // the bank must be dry behind the shoreline
				int dp = depth(b, x, z, wy);
				if (dp == NONE) return whyNull("no bed");
				wet[u + 3][k] = true;
				dep[u + 3][k] = dp;
				base[u + 3][k] = wy + 1;
			} else {
				if (w != NONE) return whyNull("other water " + w);
				int lg = ground(b, x, z);
				if (u >= 0 && interior) return whyNull("dry bay"); // the bay is water
				if (lg != wy && !(lg == wy + 1 && u < 0)) return whyNull("ground " + (lg == NONE ? b.level.getBlockState(new BlockPos(x, b.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z)).getBlock().getDescriptionId() : lg));
				base[u + 3][k] = lg + 1;
			}
		}
		int top = wy + 4;
		Part p = new Part();
		BlockState pile = log(Blocks.SPRUCE_LOG, Direction.Axis.Y), post = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y), plank = st(Blocks.SPRUCE_PLANKS);
		Direction kd = s > 0 ? sd : sd.getOpposite(); // direction of growing k
		java.util.function.IntBinaryOperator XK = (u, k) -> X.applyAsInt(u, s * (off + k));
		java.util.function.IntBinaryOperator ZK = (u, k) -> Z.applyAsInt(u, s * (off + k));
		for (int k = 0; k <= 4; k++) for (int u = -3; u <= 2; u++) {
			boolean wallCell = k == 0 || k == 4 || u == -3;
			if (!wallCell) continue;
			int x = XK.applyAsInt(u, k), z = ZK.applyAsInt(u, k), b0 = base[u + 3][k];
			if (wet[u + 3][k]) for (int y = wy - dep[u + 3][k] + 1; y <= wy; y++) p.put(new BlockPos(x, y, z), pile);
			boolean corner = (k == 0 || k == 4) && (u == -3 || u == 0 || u == 2);
			boolean door = u == -3 && k == 2;
			for (int y = b0; y <= top; y++) {
				if (door && y < b0 + 2) continue;
				p.put(new BlockPos(x, y, z), corner ? post : y == b0 && !wet[u + 3][k] ? st(Blocks.MOSSY_COBBLESTONE) : plank);
			}
			if (!door) naustColumns.add(new int[] {x, z, wet[u + 3][k] ? wy - dep[u + 3][k] + 1 : b0, top});
		}
		// the bay mouth: a plank lintel (spans like a deck), then the gables above it and at the back
		for (int k = 1; k <= 3; k++) p.put(new BlockPos(XK.applyAsInt(2, k), top, ZK.applyAsInt(2, k)), st(Blocks.DARK_OAK_PLANKS));
		for (int u : new int[] {-3, 2}) for (int k = 1; k <= 3; k++) {
			int dk = Math.abs(k - 2);
			for (int y = top + 1; y < top + 3 - dk; y++) p.put(new BlockPos(XK.applyAsInt(u, k), y, ZK.applyAsInt(u, k)), plank);
		}
		// roof along u over lane 2: eaves 1 out, spruce stairs, plank ridge; moss turf on top
		for (int u = -4; u <= 3; u++) for (int k = -1; k <= 5; k++) {
			int dk = Math.abs(k - 2);
			BlockPos q = new BlockPos(XK.applyAsInt(u, k), top + 3 - dk, ZK.applyAsInt(u, k));
			p.put(q, dk == 0 ? plank : stair(Blocks.SPRUCE_STAIRS, k < 2 ? kd : kd.getOpposite(), Half.BOTTOM));
		}
		for (int u = -3; u <= 2; u++) for (int k = 0; k <= 4; k++) {
			int dk = Math.abs(k - 2);
			p.put(p.group(), new BlockPos(XK.applyAsInt(u, k), top + 4 - dk, ZK.applyAsInt(u, k)), st(Blocks.MOSS_BLOCK));
		}
		BlockPos bn = new BlockPos(XK.applyAsInt(3, 2), top + 1, ZK.applyAsInt(3, 2));
		p.put(p.group(), bn, wallBanner(b, bn, d));
		p.put(p.group(), new BlockPos(XK.applyAsInt(-1, 2), top + 2, ZK.applyAsInt(-1, 2)), st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		final Part fp = p;
		for (var e : fp.ops.entrySet()) if (!fp.group.containsKey(e.getKey()) && !wetOk(b, BlockPos.of(e.getKey()), e.getValue())) {
			BlockPos q = BlockPos.of(e.getKey());
			return whyNull("blocked at " + q.toShortString() + " by " + (b.c.used.contains(e.getKey()) ? "a planned cell" : b.level.getBlockState(q).getBlock().getDescriptionId()));
		}
		return p;
	}

	static @Nullable Part whyNull(String why) {
		naustWhy = why + " (" + naustWhy + ")";
		return null;
	}

	/** A boat drawn up on the strand (lanes {@code s*(3..5)}, rows u0..u0+4, bow to the water): hull of stairs and slabs, a mast, a green-and-brown sail. */
	static @Nullable Part strandBoat(B b, java.util.function.IntBinaryOperator X, java.util.function.IntBinaryOperator Z, Direction sd, Direction d, int s, int u0) {
		int gy = NONE;
		for (int u = u0; u <= u0 + 4; u++) for (int k = 3; k <= 5; k++) {
			int x = X.applyAsInt(u, s * k), z = Z.applyAsInt(u, s * k);
			if (wtop(b, x, z) != NONE) return null;
			int g = ground(b, x, z);
			if (g == NONE || gy != NONE && g != gy) return null;
			gy = g;
		}
		int y = gy + 1;
		Direction kd = s > 0 ? sd : sd.getOpposite();
		java.util.function.IntBinaryOperator XK = (u, k) -> X.applyAsInt(u, s * k);
		java.util.function.IntBinaryOperator ZK = (u, k) -> Z.applyAsInt(u, s * k);
		Part p = new Part();
		p.put(new BlockPos(XK.applyAsInt(u0, 4), y, ZK.applyAsInt(u0, 4)), stair(Blocks.SPRUCE_STAIRS, d.getOpposite(), Half.BOTTOM));
		for (int u = u0 + 1; u <= u0 + 3; u++) {
			p.put(new BlockPos(XK.applyAsInt(u, 4), y, ZK.applyAsInt(u, 4)), st(Blocks.SPRUCE_SLAB));
			p.put(new BlockPos(XK.applyAsInt(u, 3), y, ZK.applyAsInt(u, 3)), stair(Blocks.SPRUCE_STAIRS, kd.getOpposite(), Half.BOTTOM));
			p.put(new BlockPos(XK.applyAsInt(u, 5), y, ZK.applyAsInt(u, 5)), stair(Blocks.SPRUCE_STAIRS, kd, Half.BOTTOM));
		}
		p.put(new BlockPos(XK.applyAsInt(u0 + 4, 4), y, ZK.applyAsInt(u0 + 4, 4)), stair(Blocks.SPRUCE_STAIRS, d, Half.BOTTOM));
		p.put(new BlockPos(XK.applyAsInt(u0 + 4, 4), y + 1, ZK.applyAsInt(u0 + 4, 4)), fence(Blocks.DARK_OAK_FENCE));
		for (int h = 1; h <= 5; h++) p.put(new BlockPos(XK.applyAsInt(u0 + 2, 4), y + h, ZK.applyAsInt(u0 + 2, 4)), fence(Blocks.SPRUCE_FENCE));
		for (int h = 2; h <= 4; h++) for (int k = 3; k <= 5; k++)
			p.put(new BlockPos(XK.applyAsInt(u0 + 3, k), y + h, ZK.applyAsInt(u0 + 3, k)), (k == 4 ? Blocks.WOOL.pick(DyeColor.BROWN) : Blocks.WOOL.pick(DyeColor.GREEN)).defaultBlockState());
		final Part fp = p;
		if (!fits(fp, q -> wetOk(b, q, fp.ops.get(q.asLong())))) return null;
		boatSpot = new BlockPos(XK.applyAsInt(u0 + 2, 4), y, ZK.applyAsInt(u0 + 2, 4));
		return p;
	}
}
