package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom_villages.FactionVillages.Profile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

/**
 * 3.0 Azmar village and rebuild looks (STRUCTURES.md 3.3, RIVALS-DECISIONS 9.5; s-vlook-azmar). Azmar is the smallest kingdom in every
 * row, so an Azmar-held village is the smallest of the four: a compact walled compound around the bell, not a wall round the whole
 * sprawl. Inside it the houses become flat-roofed mud-brick houses (cavetto cornice, parapet, lapis faience band, wind-catchers on the
 * larger ones, striped awnings on the rest; no glass, no pitched roofs). The palm wall is mud brick on a sandstone plinth with
 * smooth-sandstone caps whose top undulates like pan-bedded courses, battered at the foot, with palms inside and small pylon gates at
 * the streets. A pair of obelisks flanks the main gate (the one facing the realm) and the nearer one is the border hint. Houses outside the
 * wall are only repaired and flagged. The Migdol outpost (battered tower with a cavetto crown, a walled court with a palm, an obelisk
 * marker) replaces the 2.1 sandstone ring.
 *
 * <p>Terrain fit: every wall, batter, obelisk and palm column stands on its own natural ground (no plane, no box clear); obelisks need
 * a 3x3 within 1 block and get a footing under the low cells; nothing is ever written into the ground, only into air and soft blocks.
 * Like {@link RivalPlans} this only lists ops; {@link BlockWriter} still decides every write. The wall, obelisk and palm boxes are
 * added to the record as {@link #WALL} pieces (as Hrafnholt's waterfront adds its pier box) so the writer accepts them. No water, lava,
 * fire or valuable block anywhere (gold is yellow terracotta, lapis is blue terracotta).
 *
 * <p>Kill switch: {@code -Delsewhere.overhaul3.village_look_azmar=false} runs the 2.1 Azmar rebuild instead.
 */
public final class AzmarLooks {
	private AzmarLooks() {}

	/** Kill switch (BUILD-3.0): JVM flag {@code elsewhere.overhaul3.village_look_azmar}, default on. */
	public static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.village_look_azmar", "true"));

	public static boolean enabled() {
		return ON;
	}

	/** VillageRecord piece type of the compound wall, gate obelisks and wall palms (the writable boxes the plan adds). */
	public static final int WALL = 21;
	/** Block budget of one Azmar rebuild: under the rivals' cap ({@link RivalPlans#MAX_FORTIFY}); the compound is small, so it rarely binds. */
	public static final int MAX_OPS = 3600;
	/** Compound radius bounds (Chebyshev, around the bell). */
	public static final int MIN_R = 12, MAX_R = 22;
	/** Wall courses before growth: a sandstone plinth and two mud-brick courses, then the cap (the undulation adds or takes one). */
	public static final int H0 = 3;
	public static final int OBELISK = 7;

	/** The border hint of the last {@link #fortify} (the gate obelisk's pyramidion). */
	public static @Nullable BlockPos lastHint;
	/** What the last {@link #fortify} planned (read by the self-test). */
	public static Stats lastStats = new Stats(0);

	public static final class Stats {
		public final long id;
		public int radius, compound, outskirts, flattened, windCatchers, awnings, gates, pylons, palms, obelisks, ops, footprint;
		public boolean truncated;
		public @Nullable BlockPos tower, bell;
		/** The compound's house boxes (restyled; the outskirts are not). */
		public final List<BoundingBox> houses = new ArrayList<>();
		/** {x, z, ground y, courses, pylon 1/0} per wall column, in ring order. */
		public final List<int[]> wall = new ArrayList<>();
		/** {minX, minZ, maxX, maxZ, eaves y, maxY} per flattened house. */
		public final List<int[]> roofs = new ArrayList<>();

		Stats(long id) {
			this.id = id;
		}

		@Override
		public String toString() {
			return "R " + radius + " houses " + compound + "/" + (compound + outskirts) + " flat " + flattened + " windcatchers " + windCatchers + " awnings " + awnings
				+ " wall " + wall.size() + " gates " + gates + " pylons " + pylons + " palms " + palms + " obelisks " + obelisks + " ops " + ops + (truncated ? " TRUNCATED" : "");
		}
	}

	// ---- palette -------------------------------------------------------------------------------------------------------

	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	static BlockState s(Block b) {
		return b.defaultBlockState();
	}

	static BlockState lapis() {
		return Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE).defaultBlockState();
	}

	static BlockState turquoise() {
		return Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN).defaultBlockState();
	}

	/** The pyramidion: yellow (glazed) terracotta stands for gold. Only obelisk tips use it ({@link VillagesApi#layout} finds obelisks by it). */
	static BlockState tip() {
		return Blocks.GLAZED_TERRACOTTA.yellow().defaultBlockState();
	}

	static BlockState stair(Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	static BlockState wallCap() {
		return s(Blocks.SMOOTH_SANDSTONE_SLAB); // only the compound wall uses it: growth finds the caps by it
	}

	static int hash(int x, int y, int z) {
		int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		h ^= h >>> 13;
		return h & 0x7fffffff;
	}

	/** Pan-bedded courses: the wall top rises and falls by one on a slow sine (period about 16 blocks). */
	static int undulate(int x, int z) {
		return (int) Math.round(Math.sin((x + z) * 0.39));
	}

	/** Air, plants, snow: what an addition may replace. Never fluids, never anything built or solid ground. */
	public static boolean isSoft(BlockState s) {
		return s.isAir() || s.getFluidState().isEmpty() && (s.canBeReplaced() || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS));
	}

	/** Natural ground a wall, obelisk or palm may stand on. */
	public static boolean natural(BlockState g) {
		return g.is(Blocks.GRASS_BLOCK) || g.is(BlockTags.DIRT) || g.is(Blocks.SAND) || g.is(Blocks.RED_SAND) || g.is(Blocks.SNOW_BLOCK) || g.is(Blocks.GRAVEL)
			|| g.is(Blocks.COARSE_DIRT) || g.is(Blocks.PODZOL) || g.is(Blocks.TERRACOTTA) || g.is(Blocks.CLAY);
	}

	/** The top solid block of a column (the ground under plants, snow layers and leaves). */
	static BlockPos ground(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
	}

	/** Inside a house, the centre or a pier box (where the building itself is the enclosure). */
	static boolean inBuilding(VillageRecord r, int x, int z) {
		for (VillageRecord.Piece pc : r.pieces) {
			int t = pc.type();
			if (t != VillageRecord.HOUSE && t != VillageRecord.CENTER && t != RivalPlans.PIER) continue;
			BoundingBox b = pc.box();
			if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) return true;
		}
		return false;
	}

	static boolean inType(VillageRecord r, int x, int z, int type) {
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() != type) continue;
			BoundingBox b = pc.box();
			if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) return true;
		}
		return false;
	}

	/** Inside one of the boxes this look added (wall, obelisk, palm). */
	public static boolean inWall(VillageRecord r, BlockPos p) {
		for (VillageRecord.Piece pc : r.pieces) if (pc.type() == WALL && pc.box().isInside(p)) return true;
		return false;
	}

	static void addPiece(VillageRecord r, BoundingBox b) {
		for (VillageRecord.Piece pc : r.pieces) {
			BoundingBox o = pc.box();
			if (pc.type() == WALL && o.minX() == b.minX() && o.minY() == b.minY() && o.minZ() == b.minZ() && o.maxX() == b.maxX() && o.maxY() == b.maxY() && o.maxZ() == b.maxZ()) return;
		}
		r.pieces.add(new VillageRecord.Piece(b, WALL));
	}

	// ---- planning context -----------------------------------------------------------------------------------------------

	static final class Ctx {
		final ServerLevel level;
		final VillageRecord r;
		final RandomSource rnd;
		final Profile p;
		final Stats st;
		final List<VillageRecord.Op> ops = new ArrayList<>();
		final Set<Long> used = new HashSet<>();
		/** Upper halves of two-block plants under an addition: cleared at the end so nothing hangs in the air. */
		final List<Long> tops = new ArrayList<>();
		BlockPos bell = BlockPos.ZERO;
		int R;
		@Nullable BlockPos towerTop, hint;

		Ctx(ServerLevel level, VillageRecord r, RandomSource rnd, Profile p) {
			this.level = level;
			this.r = r;
			this.rnd = rnd;
			this.p = p;
			this.st = new Stats(r.id);
		}

		BlockState at(BlockPos pos) {
			return level.getBlockState(pos);
		}

		/** The original state (journal first: a siege may have burned it). */
		BlockState orig(BlockPos pos) {
			VillageRecord.Change ch = r.journal.get(pos.asLong());
			return ch != null ? ch.original() : level.getBlockState(pos);
		}

		void add(BlockPos pos, BlockState s) {
			Plans.add(ops, used, pos, s);
		}

		/** An addition: only into air or soft blocks; returns false (and adds nothing) otherwise. */
		boolean soft(BlockPos pos, BlockState s) {
			if (used.contains(pos.asLong()) || !level.isLoaded(pos)) return false;
			BlockState cur = level.getBlockState(pos);
			if (!isSoft(cur)) return false;
			add(pos, s);
			if (cur.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && cur.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
				&& level.getBlockState(pos.above()).is(cur.getBlock())) tops.add(pos.above().asLong());
			return true;
		}
	}

	// ---- the rebuild ----------------------------------------------------------------------------------------------------

	public static Plans.Fortify fortify(ServerLevel level, VillageRecord r, RandomSource rnd, Profile p) {
		Ctx c = new Ctx(level, r, rnd, p);
		c.bell = findBell(c);
		c.R = radius(c);
		c.st.radius = c.R;
		List<VillageRecord.Piece> inner = new ArrayList<>(), outer = new ArrayList<>();
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() != VillageRecord.HOUSE && pc.type() != VillageRecord.CENTER) continue;
			if (near(c.bell, pc.box()) < c.R) inner.add(pc);
			else outer.add(pc);
		}
		for (VillageRecord.Piece pc : inner) if (pc.type() == VillageRecord.HOUSE) c.st.compound++;
		for (VillageRecord.Piece pc : outer) if (pc.type() == VillageRecord.HOUSE) c.st.outskirts++;
		// 1. the palm wall round the compound, its pylon gates and the obelisk pair at the main gate (the border hint)
		wall(c);
		// 2. inside: the archer's watch tower, a market awning by the bell and a palm court
		BlockPos t = freeSpot(c, 5, c.R - 3, 8);
		if (t != null) tower(c, t);
		BlockPos m = freeSpot(c, 3, 9, 4);
		if (m != null) awning(c, m, 0, true);
		for (int i = 0; i < 2; i++) {
			BlockPos pt = freeSpot(c, 4 + 2 * i, c.R - 3, 8);
			if (pt == null) break;
			int trunk = 4 + (hash(pt.getX(), 0, pt.getZ()) & 1);
			addPiece(c.r, new BoundingBox(pt.getX() - 3, pt.getY() - 1, pt.getZ() - 3, pt.getX() + 3, pt.getY() + trunk + 2, pt.getZ() + 3)); // room for the fronds
			palm(c, pt, trunk);
		}
		if (c.hint == null) { // no gate could take an obelisk: a lone one in the court
			BlockPos ob = freeSpot(c, 4, c.R - 3, OBELISK + 2);
			if (ob != null) {
				for (VillageRecord.Op o : obeliskOps(ob, OBELISK)) c.add(BlockPos.of(o.pos()), o.state());
				c.hint = ob.above(OBELISK);
				c.st.obelisks++;
			}
		}
		// 3. flat roofs on the compound's houses, then banners on every house (the outskirts are Azmar's too)
		Set<BoundingBox> flat = new HashSet<>();
		for (VillageRecord.Piece pc : inner) if (pc.type() == VillageRecord.HOUSE && flatRoof(c, pc.box())) flat.add(pc.box());
		for (VillageRecord.Piece pc : r.pieces) {
			if (pc.type() != VillageRecord.HOUSE || flat.contains(pc.box())) continue;
			BlockPos ctr = pc.box().getCenter();
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ctr.getX(), ctr.getZ());
			if (top <= pc.box().maxY() + 2) c.soft(new BlockPos(ctr.getX(), top, ctr.getZ()), RivalPlans.banner(p, 0));
		}
		// 4. restyle the compound (mud brick, sandstone, lapis-tiled well), repair burned blocks in the outskirts, last so a cap cuts it first
		for (long top : c.tops) c.add(BlockPos.of(top), AIR); // before the restyle, so a truncated plan never leaves half a plant
		for (VillageRecord.Piece pc : inner) restyle(c, pc, true);
		for (VillageRecord.Piece pc : outer) restyle(c, pc, false);
		List<VillageRecord.Op> ops = new ArrayList<>(c.ops);
		ops.removeIf(o -> !r.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP));
		if (ops.size() > MAX_OPS) {
			ops = new ArrayList<>(ops.subList(0, MAX_OPS));
			c.st.truncated = true;
		}
		c.st.ops = ops.size();
		c.st.tower = c.towerTop;
		c.st.footprint = (2 * c.R + 1) * (2 * c.R + 1);
		c.st.bell = c.bell;
		for (VillageRecord.Piece pc : inner) if (pc.type() == VillageRecord.HOUSE) c.st.houses.add(pc.box());
		lastHint = c.hint;
		lastStats = c.st;
		return new Plans.Fortify(ops, null, c.towerTop, c.bell);
	}

	static BlockPos findBell(Ctx c) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (VillageRecord.Piece pc : c.r.pieces) {
			if (pc.type() != VillageRecord.CENTER) continue;
			BoundingBox b = pc.box();
			for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				m.set(x, y, z);
				if (c.level.isLoaded(m) && c.orig(m).is(Blocks.BELL)) return m.immutable();
			}
		}
		return Plans.surface(c.level, c.r.center);
	}

	/** Chebyshev distance from {@code o} to the nearest point of the box (0 when inside). */
	static int near(BlockPos o, BoundingBox b) {
		int dx = Math.max(0, Math.max(b.minX() - o.getX(), o.getX() - b.maxX())), dz = Math.max(0, Math.max(b.minZ() - o.getZ(), o.getZ() - b.maxZ()));
		return Math.max(dx, dz);
	}

	/** Chebyshev distance from {@code o} to the farthest point of the box. */
	static int far(BlockPos o, BoundingBox b) {
		return Math.max(Math.max(Math.abs(b.minX() - o.getX()), Math.abs(b.maxX() - o.getX())), Math.max(Math.abs(b.minZ() - o.getZ()), Math.abs(b.maxZ() - o.getZ())));
	}

	/** The compound takes in the nearer half of the houses (at least three), between {@link #MIN_R} and {@link #MAX_R}. */
	static int radius(Ctx c) {
		List<Integer> d = new ArrayList<>();
		for (VillageRecord.Piece pc : c.r.pieces) if (pc.type() == VillageRecord.HOUSE) d.add(far(c.bell, pc.box()));
		if (d.isEmpty()) return MIN_R;
		d.sort(Integer::compare);
		int k = Math.min(d.size(), Math.max(3, (d.size() + 1) / 2));
		return Math.max(MIN_R, Math.min(MAX_R, d.get(k - 1) + 2));
	}

	// ---- the palm wall --------------------------------------------------------------------------------------------------

	static final int SKIP = 0, WALLC = 1, GATE = 2;

	record Cell(int x, int z, Direction out, int side, int kind, int g) {}

	static Cell classify(Ctx c, int x, int z, Direction out, int side) {
		ServerLevel level = c.level;
		if (!level.isLoaded(new BlockPos(x, c.bell.getY(), z)) || inBuilding(c.r, x, z)) return new Cell(x, z, out, side, SKIP, 0);
		BlockPos g = ground(level, x, z);
		BlockState gs = level.getBlockState(g);
		boolean street = inType(c.r, x, z, VillageRecord.STREET);
		if (gs.is(Blocks.DIRT_PATH) || street && !natural(gs) && gs.getFluidState().isEmpty() && !gs.is(Blocks.FARMLAND)) return new Cell(x, z, out, side, GATE, g.getY());
		if (!natural(gs) || gs.is(Blocks.FARMLAND) || !gs.getFluidState().isEmpty()) return new Cell(x, z, out, side, SKIP, 0);
		for (int h = 1; h <= H0 + 2; h++) if (!isSoft(level.getBlockState(g.above(h)))) return new Cell(x, z, out, side, SKIP, 0);
		return new Cell(x, z, out, side, WALLC, g.getY());
	}

	/** The square ring at radius R round the bell, clockwise from the north-west corner, each cell with its outward side. */
	static List<Cell> ring(Ctx c) {
		List<Cell> cells = new ArrayList<>();
		int R = c.R, bx = c.bell.getX(), bz = c.bell.getZ();
		for (int dx = -R; dx < R; dx++) cells.add(classify(c, bx + dx, bz - R, Direction.NORTH, 0));
		for (int dz = -R; dz < R; dz++) cells.add(classify(c, bx + R, bz + dz, Direction.EAST, 1));
		for (int dx = R; dx > -R; dx--) cells.add(classify(c, bx + dx, bz + R, Direction.SOUTH, 2));
		for (int dz = R; dz > -R; dz--) cells.add(classify(c, bx - R, bz + dz, Direction.WEST, 3));
		return cells;
	}

	static void wall(Ctx c) {
		List<Cell> cells = ring(c);
		int n = cells.size();
		// pylons: the two wall cells either side of every gate, on the same side of the ring
		boolean[] pylon = new boolean[n], beside = new boolean[n];
		for (int i = 0; i < n; i++) {
			if (cells.get(i).kind() != GATE) continue;
			for (int s = -1; s <= 1; s += 2) for (int k = 1; k <= 2; k++) {
				Cell cj = cells.get(Math.floorMod(i + s * k, n));
				if (cj.kind() != WALLC || cj.side() != cells.get(i).side()) break;
				pylon[Math.floorMod(i + s * k, n)] = true;
				if (k == 1) beside[Math.floorMod(i + s * k, n)] = true;
			}
		}
		// gate runs (consecutive gate cells on one side); the main gate faces the realm
		List<int[]> runs = new ArrayList<>(); // {first index, last index}
		for (int i = 0; i < n; i++) {
			if (cells.get(i).kind() != GATE) continue;
			int j = i;
			while (j + 1 < n && cells.get(j + 1).kind() == GATE && cells.get(j + 1).side() == cells.get(i).side()) j++;
			runs.add(new int[] {i, j});
			i = j;
		}
		c.st.gates = runs.size();
		BlockPos target = c.r.castle != null ? c.r.castle : VillageManager.edgeToward(c.level, c.r);
		runs.sort((a, b) -> Double.compare(mid(cells, a).distSqr(target), mid(cells, b).distSqr(target)));
		// the obelisk pair first: it is the hint, and the soldiers picket there by day (the next gate takes any the main one could not)
		List<BlockPos> tips = new ArrayList<>();
		for (int[] run : runs) {
			if (tips.size() >= 2) break;
			gateObelisks(c, cells, run, tips);
		}
		tips.sort((a, b) -> Double.compare(a.distSqr(target), b.distSqr(target)));
		if (!tips.isEmpty()) c.hint = tips.get(0);
		// the wall itself, column by column on its own ground
		int[] box = null;
		int side = -1, idx = 0;
		List<Cell> palms = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Cell cl = cells.get(i);
			if (cl.kind() != WALLC) {
				if (box != null) addPiece(c.r, new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
				box = null;
				continue;
			}
			if (box != null && cl.side() != side) {
				addPiece(c.r, new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
				box = null;
			}
			side = cl.side();
			idx++;
			int x = cl.x(), z = cl.z(), g = cl.g();
			Direction out = cl.out();
			boolean py = pylon[i];
			for (int h = H0 + 3; py && h <= H0 + 4; h++) if (!isSoft(c.at(new BlockPos(x, g + h, z)))) py = false; // no room: a plain column
			int courses = py ? H0 + 3 : H0 + undulate(x, z);
			if (py) c.st.pylons++;
			for (int h = 1; h <= courses; h++) {
				BlockState b = py ? (h == courses - 1 ? lapis() : s(Blocks.CUT_SANDSTONE)) : h == 1 ? s(Blocks.SANDSTONE) : s(Blocks.MUD_BRICKS);
				c.soft(new BlockPos(x, g + h, z), b);
			}
			c.soft(new BlockPos(x, g + courses + 1, z), py ? s(Blocks.CUT_SANDSTONE) : wallCap());
			c.st.wall.add(new int[] {x, z, g, courses, py ? 1 : 0});
			int lo = g, hi = g + courses + 5; // room for the three growth courses and a lamp
			// batter: a sloped mud-brick skirt at the outer foot (never onto a path, a field or a house)
			int ox = x + out.getStepX(), oz = z + out.getStepZ();
			boolean batter = false;
			if (!inBuilding(c.r, ox, oz) && c.level.isLoaded(new BlockPos(ox, g, oz))) {
				BlockPos og = ground(c.level, ox, oz);
				BlockState os = c.at(og);
				if (natural(os) && !os.is(Blocks.FARMLAND) && Math.abs(og.getY() - g) <= 1)
					batter = c.soft(og.above(), stair(Blocks.MUD_BRICK_STAIRS, out.getOpposite(), Half.BOTTOM));
				lo = Math.min(lo, og.getY());
			}
			if (py && batter) {
				// cavetto cornice on the pylon's outer face, a lip over it, and a banner niche beside the opening
				BlockPos ob = new BlockPos(ox, g + courses, oz);
				if (c.soft(ob, stair(Blocks.SANDSTONE_STAIRS, out.getOpposite(), Half.TOP))) c.soft(ob.above(), s(Blocks.CUT_SANDSTONE_SLAB));
				if (beside[i]) c.soft(new BlockPos(ox, g + 3, oz), Blocks.WALL_BANNER.pick(c.p.banner()).defaultBlockState().setValue(WallBannerBlock.FACING, out));
			}
			if (box == null) box = new int[] {Math.min(x, ox), lo, Math.min(z, oz), Math.max(x, ox), hi, Math.max(z, oz)};
			else {
				box[0] = Math.min(box[0], Math.min(x, ox));
				box[1] = Math.min(box[1], lo);
				box[2] = Math.min(box[2], Math.min(z, oz));
				box[3] = Math.max(box[3], Math.max(x, ox));
				box[4] = Math.max(box[4], hi);
				box[5] = Math.max(box[5], Math.max(z, oz));
			}
			// a palm every seventh column, just inside the wall (planted once the whole wall stands, so no frond takes a course)
			if (!py && idx % 7 == 0) palms.add(cl);
		}
		if (box != null) addPiece(c.r, new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
		for (Cell cl : palms) wallPalm(c, cl);
	}

	static BlockPos mid(List<Cell> cells, int[] run) {
		Cell a = cells.get(run[0]), b = cells.get(run[1]);
		return new BlockPos((a.x() + b.x()) / 2, 0, (a.z() + b.z()) / 2);
	}

	/** Two obelisks flanking the main gate outside the wall; the one nearer the realm carries the border hint. */
	static void gateObelisks(Ctx c, List<Cell> cells, int[] run, List<BlockPos> tips) {
		Cell first = cells.get(run[0]);
		Direction out = first.out(), along = out.getClockWise();
		BlockPos m = mid(cells, run);
		int half = (run[1] - run[0] + 2) / 2;
		for (int s = -1; s <= 1 && tips.size() < 2; s += 2) {
			for (int o : new int[] {3, 4, 2}) {
				int cx = m.getX() + along.getStepX() * s * (half + 3) + out.getStepX() * o;
				int cz = m.getZ() + along.getStepZ() * s * (half + 3) + out.getStepZ() * o;
				BlockPos tipAt = obeliskOnGround(c, cx, cz, OBELISK);
				if (tipAt != null) {
					tips.add(tipAt);
					break;
				}
			}
		}
	}

	/**
	 * A 3x3 of level natural ground in the compound (not a house, street, path or field), open to the sky for {@code height}, searched
	 * ring by ring from the bell between {@code minD} and {@code maxD}; its box joins the wall pieces. Returns the air cell above its centre.
	 */
	static @Nullable BlockPos freeSpot(Ctx c, int minD, int maxD, int height) {
		for (int d = minD; d <= maxD; d++) for (int dx = -d; dx <= d; dx++) for (int dz = -d; dz <= d; dz++) {
			if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
			int x = c.bell.getX() + dx, z = c.bell.getZ() + dz;
			if (!c.level.isLoaded(new BlockPos(x, c.bell.getY(), z))) continue;
			int gy = ground(c.level, x, z).getY();
			boolean ok = true;
			for (int a = -1; a <= 1 && ok; a++) for (int b = -1; b <= 1 && ok; b++) {
				int qx = x + a, qz = z + b;
				if (!c.level.isLoaded(new BlockPos(qx, gy, qz)) || inBuilding(c.r, qx, qz) || inType(c.r, qx, qz, VillageRecord.STREET)) {
					ok = false;
					break;
				}
				BlockPos g = ground(c.level, qx, qz);
				BlockState gs = c.at(g);
				if (g.getY() != gy || !natural(gs) || gs.is(Blocks.DIRT_PATH) || gs.is(Blocks.FARMLAND)) {
					ok = false;
					break;
				}
				for (int h = 1; h <= height && ok; h++) if (c.used.contains(g.above(h).asLong()) || !c.at(g.above(h)).isAir()) ok = false;
			}
			if (!ok) continue;
			addPiece(c.r, new BoundingBox(x - 1, gy, z - 1, x + 1, gy + height, z + 1));
			return new BlockPos(x, gy + 1, z);
		}
		return null;
	}

	/**
	 * An obelisk standing on its own 3x3 of natural ground (within 1 block; a footing fills under the low cells); null when the spot
	 * is a path, a field, a house, uneven, or not open to the sky for the whole shaft. Returns the pyramidion's position.
	 */
	static @Nullable BlockPos obeliskOnGround(Ctx c, int cx, int cz, int height) {
		int gmax = Integer.MIN_VALUE, gmin = Integer.MAX_VALUE;
		int[] gy = new int[9];
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			int x = cx + dx, z = cz + dz;
			if (!c.level.isLoaded(new BlockPos(x, c.bell.getY(), z)) || inBuilding(c.r, x, z)) return null;
			BlockPos g = ground(c.level, x, z);
			BlockState gs = c.at(g);
			if (!natural(gs) || gs.is(Blocks.FARMLAND)) return null;
			gy[(dx + 1) * 3 + dz + 1] = g.getY();
			gmax = Math.max(gmax, g.getY());
			gmin = Math.min(gmin, g.getY());
		}
		if (gmax - gmin > 1) return null;
		int y0 = gmax + 1;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			int x = cx + dx, z = cz + dz, upTo = dx == 0 && dz == 0 ? y0 + height : y0 + 1;
			for (int y = gy[(dx + 1) * 3 + dz + 1] + 1; y <= upTo; y++) {
				BlockPos q = new BlockPos(x, y, z);
				if (c.used.contains(q.asLong()) || !isSoft(c.at(q))) return null;
			}
		}
		addPiece(c.r, new BoundingBox(cx - 1, gmin - 1, cz - 1, cx + 1, y0 + height + 1, cz + 1));
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
			for (int y = gy[(dx + 1) * 3 + dz + 1] + 1; y < y0; y++) c.soft(new BlockPos(cx + dx, y, cz + dz), s(Blocks.SANDSTONE));
		BlockPos base = new BlockPos(cx, y0, cz);
		for (VillageRecord.Op o : obeliskOps(base, height)) c.soft(BlockPos.of(o.pos()), o.state());
		c.st.obelisks++;
		return base.above(height);
	}

	static void wallPalm(Ctx c, Cell cl) {
		int ix = cl.x() - cl.out().getStepX(), iz = cl.z() - cl.out().getStepZ();
		if (inBuilding(c.r, ix, iz) || !c.level.isLoaded(new BlockPos(ix, cl.g(), iz))) return;
		BlockPos ig = ground(c.level, ix, iz);
		BlockState gs = c.at(ig);
		if (!natural(gs) || gs.is(Blocks.FARMLAND) || Math.abs(ig.getY() - cl.g()) > 2) return;
		int trunk = 4 + (hash(ix, 0, iz) & 1);
		for (int h = 1; h <= trunk + 1; h++) {
			BlockPos q = ig.above(h);
			if (c.used.contains(q.asLong()) || !isSoft(c.at(q))) return;
		}
		addPiece(c.r, new BoundingBox(ix - 3, ig.getY() - 1, iz - 3, ix + 3, ig.getY() + trunk + 3, iz + 3));
		palm(c, ig.above(), trunk);
	}

	static void palm(Ctx c, BlockPos g, int trunk) {
		for (VillageRecord.Op o : palmOps(g, trunk)) c.soft(BlockPos.of(o.pos()), o.state());
		c.st.palms++;
	}

	// ---- shared pieces (also used by the owner swap, wall growth and the outpost) -------------------------------------------

	/**
	 * Obelisk on {@code g} (the air cell above flat ground): a 3x3 smooth-sandstone plinth, a flared foot of stairs, a cut-sandstone shaft
	 * with a chiselled glyph band and a lapis (blue terracotta) band, and a yellow-terracotta pyramidion at {@code g + height}.
	 */
	public static List<VillageRecord.Op> obeliskOps(BlockPos g, int height) {
		List<VillageRecord.Op> out = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(new VillageRecord.Op(g.offset(dx, 0, dz).asLong(), s(Blocks.SMOOTH_SANDSTONE)));
		for (Direction d : Direction.Plane.HORIZONTAL) out.add(new VillageRecord.Op(g.relative(d).above().asLong(), stair(Blocks.SANDSTONE_STAIRS, d.getOpposite(), Half.BOTTOM)));
		for (int h = 1; h < height; h++) {
			BlockState b = h == 2 ? s(Blocks.CHISELED_SANDSTONE) : h == height - 2 && height >= 5 ? lapis() : s(Blocks.CUT_SANDSTONE);
			out.add(new VillageRecord.Op(g.above(h).asLong(), b));
		}
		out.add(new VillageRecord.Op(g.above(height).asLong(), tip()));
		return out;
	}

	/** Date palm on {@code g}: a jungle trunk leaning one block at the top, a crown of persistent fronds with drooping tips. */
	public static List<VillageRecord.Op> palmOps(BlockPos g, int trunk) {
		List<VillageRecord.Op> out = new ArrayList<>();
		BlockState log = s(Blocks.JUNGLE_LOG), leaf = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		int hsh = hash(g.getX(), g.getY(), g.getZ());
		int lean = (hsh & 1) == 0 ? 1 : -1;
		int lx = (hsh & 2) == 0 ? lean : 0, lz = (hsh & 2) == 0 ? 0 : lean;
		for (int h = 0; h < trunk; h++) {
			boolean top = h == trunk - 1;
			out.add(new VillageRecord.Op(g.offset(top ? lx : 0, h, top ? lz : 0).asLong(), log));
		}
		BlockPos crown = g.offset(lx, trunk, lz);
		out.add(new VillageRecord.Op(crown.asLong(), leaf));
		out.add(new VillageRecord.Op(crown.above().asLong(), leaf));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			out.add(new VillageRecord.Op(crown.relative(d).asLong(), leaf));
			out.add(new VillageRecord.Op(crown.relative(d, 2).asLong(), leaf));
			out.add(new VillageRecord.Op(crown.relative(d, 3).below().asLong(), leaf));
			out.add(new VillageRecord.Op(crown.relative(d).relative(d.getClockWise()).asLong(), leaf));
		}
		return out;
	}

	// ---- inside the compound --------------------------------------------------------------------------------------------

	/** The archers' watch tower: a solid mud-brick tower on a sandstone course, a faience band, a deck and a crenellated top. */
	static void tower(Ctx c, BlockPos t) {
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			boolean edge = dx != 0 || dz != 0, corner = dx != 0 && dz != 0;
			c.add(t.offset(dx, 0, dz), s(Blocks.SANDSTONE));
			c.add(t.offset(dx, 1, dz), s(Blocks.MUD_BRICKS));
			c.add(t.offset(dx, 2, dz), s(Blocks.MUD_BRICKS));
			c.add(t.offset(dx, 3, dz), edge ? (corner ? turquoise() : lapis()) : s(Blocks.MUD_BRICKS));
			c.add(t.offset(dx, 4, dz), s(Blocks.SMOOTH_SANDSTONE));
			if (edge) c.add(t.offset(dx, 5, dz), corner ? s(Blocks.CUT_SANDSTONE) : s(Blocks.SANDSTONE_WALL));
		}
		c.add(t.offset(1, 6, 1), RivalPlans.banner(c.p, 4));
		c.towerTop = t.above(5);
	}

	/** A striped lapis-and-linen awning on four posts ({@code y} = the floor the posts stand on, relative to {@code m}). */
	static void awning(Ctx c, BlockPos m, int y, boolean stone) {
		BlockState post = stone ? s(Blocks.CUT_SANDSTONE) : s(Blocks.SANDSTONE_WALL);
		for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) for (int h = 0; h < 2; h++) c.soft(m.offset(dx, y + h, dz), post);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
			c.soft(m.offset(dx, y + 2, dz), Blocks.WOOL.pick((dz & 1) == 0 ? DyeColor.BLUE : DyeColor.WHITE).defaultBlockState());
		c.st.awnings++;
	}

	// ---- flat roofs -------------------------------------------------------------------------------------------------------

	/**
	 * Turns a pitched roof into an Egyptian flat roof: the deck at the eaves, a cavetto cornice (upside-down stairs) and a parapet round
	 * it, a lapis band on the top course of the walls, a wind-catcher on a big roof or an awning on a smaller one. Leaves the house alone
	 * (returns false) when the roof is already flat or something worth keeping (a bed, door, bell, chest or job site) sits above the eaves.
	 */
	static boolean flatRoof(Ctx c, BoundingBox b) {
		int w = b.getXSpan(), d = b.getZSpan();
		int[] top = new int[w * d];
		boolean[] sl = new boolean[w * d];
		int count = 0, i0 = w, i1 = -1, k0 = d, k1 = -1;
		for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) {
			int x = b.minX() + i, z = b.minZ() + k;
			top[i * d + k] = Integer.MIN_VALUE;
			if (!c.level.isLoaded(new BlockPos(x, b.minY(), z))) return false;
			for (int y = b.maxY(); y >= b.minY(); y--) {
				BlockState o = c.orig(new BlockPos(x, y, z));
				if (o.isAir() || o.is(BlockTags.LEAVES) || !o.getFluidState().isEmpty() || o.canBeReplaced()) continue;
				top[i * d + k] = y;
				sl[i * d + k] = (o.is(BlockTags.STAIRS) || o.is(BlockTags.SLABS)) && y >= b.minY() + 3;
				break;
			}
			if (!sl[i * d + k]) continue;
			count++;
			i0 = Math.min(i0, i);
			i1 = Math.max(i1, i);
			k0 = Math.min(k0, k);
			k1 = Math.max(k1, k);
		}
		if (count < 6) return false;
		// the eaves: the commonest top among the roof's outer stairs (beside a lower column or the box side; not the ridge's flanks)
		Map<Integer, Integer> freq = new HashMap<>();
		for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) {
			if (!sl[i * d + k]) continue;
			boolean outer = false;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int ni = i + dir.getStepX(), nk = k + dir.getStepZ();
				if (ni < 0 || nk < 0 || ni >= w || nk >= d || top[ni * d + nk] < top[i * d + k]) outer = true;
			}
			if (outer) freq.merge(top[i * d + k], 1, Integer::sum);
		}
		if (freq.isEmpty()) return false;
		int H = Integer.MAX_VALUE, best = -1;
		for (var e : freq.entrySet()) if (e.getValue() > best || e.getValue() == best && e.getKey() < H) {
			best = e.getValue();
			H = e.getKey();
		}
		// the deck: every column of the roof's rectangle that reaches the eaves (ridge planks and gable ends go too)
		boolean[] deck = new boolean[w * d];
		int area = 0;
		for (int i = i0; i <= i1; i++) for (int k = k0; k <= k1; k++) if (top[i * d + k] >= H) {
			deck[i * d + k] = true;
			area++;
		}
		// nothing worth keeping above the eaves
		for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) {
			if (!deck[i * d + k]) continue;
			for (int y = H; y <= top[i * d + k]; y++) {
				BlockState cur = c.at(new BlockPos(b.minX() + i, y, b.minZ() + k));
				if (!cur.isAir() && (Plans.skip(cur) || cur.is(Blocks.BELL))) return false;
			}
		}
		Map<Long, BlockState> m = new LinkedHashMap<>();
		List<int[]> interior = new ArrayList<>();
		for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) {
			int x = b.minX() + i, z = b.minZ() + k, t = top[i * d + k];
			if (deck[i * d + k]) {
				for (int y = H + 1; y <= t; y++) if (!c.at(new BlockPos(x, y, z)).isAir()) m.put(BlockPos.asLong(x, y, z), AIR);
				if (edge(deck, w, d, i, k)) {
					m.put(BlockPos.asLong(x, H, z), stair(Blocks.SANDSTONE_STAIRS, inward(deck, w, d, i, k), Half.TOP));
					m.put(BlockPos.asLong(x, H + 1, z), s(Blocks.CUT_SANDSTONE_SLAB));
				} else {
					m.put(BlockPos.asLong(x, H, z), s(Blocks.SMOOTH_SANDSTONE));
					interior.add(new int[] {i, k});
				}
			} else if (t >= H - 2 && t < H && c.orig(new BlockPos(x, t, z)).is(BlockTags.STAIRS)) {
				m.put(BlockPos.asLong(x, t, z), s(Blocks.SANDSTONE_SLAB)); // a lean-to under the eaves: flat too
			}
		}
		// the lapis faience band on the top course of the walls, just inside the cornice
		for (int[] ik : interior) {
			int i = ik[0], k = ik[1];
			boolean byEdge = false;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int ni = i + dir.getStepX(), nk = k + dir.getStepZ();
				if (ni >= 0 && nk >= 0 && ni < w && nk < d && deck[ni * d + nk] && edge(deck, w, d, ni, nk)) byEdge = true;
			}
			if (!byEdge) continue;
			int x = b.minX() + i, z = b.minZ() + k;
			BlockState o = c.orig(new BlockPos(x, H - 1, z));
			if (o.is(BlockTags.PLANKS) || o.is(BlockTags.LOGS)) m.put(BlockPos.asLong(x, H - 1, z), Math.floorMod(x + z, 4) == 0 ? turquoise() : lapis());
		}
		// roof furniture: a wind-catcher facing the north wind on a big roof, a striped awning on a smaller one; a banner on the deck
		Set<Long> in = new HashSet<>();
		for (int[] ik : interior) in.add(BlockPos.asLong(ik[0], 0, ik[1]));
		Set<Long> taken = new HashSet<>();
		boolean done = false;
		if (area >= 40) {
			for (int[] ik : interior) {
				int i = ik[0], k = ik[1];
				if (!(in.contains(BlockPos.asLong(i + 1, 0, k)) && in.contains(BlockPos.asLong(i, 0, k + 1)) && in.contains(BlockPos.asLong(i + 1, 0, k + 1)))) continue;
				for (int a = 0; a <= 1; a++) for (int e = 0; e <= 1; e++) {
					int x = b.minX() + i + a, z = b.minZ() + k + e;
					m.put(BlockPos.asLong(x, H + 1, z), s(Blocks.MUD_BRICKS));
					m.put(BlockPos.asLong(x, H + 2, z), s(Blocks.MUD_BRICKS));
					if (e == 1) m.put(BlockPos.asLong(x, H + 3, z), s(Blocks.MUD_BRICKS)); // the north face stays open: the vent
					m.put(BlockPos.asLong(x, H + 4, z), s(Blocks.CUT_SANDSTONE_SLAB));
					taken.add(BlockPos.asLong(i + a, 0, k + e));
				}
				c.st.windCatchers++;
				done = true;
				break;
			}
		}
		if (!done && area >= 16) {
			for (int[] ik : interior) {
				int i = ik[0], k = ik[1];
				boolean fits = true;
				for (int a = 0; a <= 2 && fits; a++) for (int e = 0; e <= 2 && fits; e++) fits = in.contains(BlockPos.asLong(i + a, 0, k + e));
				if (!fits) continue;
				for (int a = 0; a <= 2; a++) for (int e = 0; e <= 2; e++) {
					int x = b.minX() + i + a, z = b.minZ() + k + e;
					boolean corner = (a == 0 || a == 2) && (e == 0 || e == 2);
					if (corner) {
						m.put(BlockPos.asLong(x, H + 1, z), s(Blocks.SANDSTONE_WALL));
						m.put(BlockPos.asLong(x, H + 2, z), s(Blocks.SANDSTONE_WALL));
					}
					m.put(BlockPos.asLong(x, H + 3, z), Blocks.WOOL.pick((e & 1) == 0 ? DyeColor.BLUE : DyeColor.WHITE).defaultBlockState());
					taken.add(BlockPos.asLong(i + a, 0, k + e));
				}
				c.st.awnings++;
				break;
			}
		}
		int ci = w / 2, ck = d / 2, bi = -1, bk = -1, bd = Integer.MAX_VALUE;
		for (int[] ik : interior) {
			if (taken.contains(BlockPos.asLong(ik[0], 0, ik[1]))) continue;
			int dd = Math.abs(ik[0] - ci) + Math.abs(ik[1] - ck);
			if (dd < bd) {
				bd = dd;
				bi = ik[0];
				bk = ik[1];
			}
		}
		if (bi >= 0) m.put(BlockPos.asLong(b.minX() + bi, H + 1, b.minZ() + bk), RivalPlans.banner(c.p, 0));
		for (var e : m.entrySet()) c.add(BlockPos.of(e.getKey()), e.getValue());
		c.st.flattened++;
		c.st.roofs.add(new int[] {b.minX(), b.minZ(), b.maxX(), b.maxZ(), H, b.maxY()});
		return true;
	}

	static boolean edge(boolean[] roof, int w, int d, int i, int k) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int ni = i + dir.getStepX(), nk = k + dir.getStepZ();
			if (ni < 0 || nk < 0 || ni >= w || nk >= d || !roof[ni * d + nk]) return true;
		}
		return false;
	}

	/** The side of an edge column that faces into the roof (the cornice stair's back). */
	static Direction inward(boolean[] roof, int w, int d, int i, int k) {
		Direction best = Direction.NORTH;
		int bestScore = -1;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int ni = i + dir.getStepX(), nk = k + dir.getStepZ(), oi = i - dir.getStepX(), ok = k - dir.getStepZ();
			boolean inRoof = ni >= 0 && nk >= 0 && ni < w && nk < d && roof[ni * d + nk];
			boolean outOpen = oi < 0 || ok < 0 || oi >= w || ok >= d || !roof[oi * d + ok];
			int score = (inRoof ? 2 : 0) + (outOpen ? 1 : 0);
			if (score > bestScore) {
				bestScore = score;
				best = dir;
			}
		}
		return best;
	}

	// ---- restyle ----------------------------------------------------------------------------------------------------------

	/** The compound's houses and well in mud brick and sandstone; the outskirts only get what a siege burned back. */
	static void restyle(Ctx c, VillageRecord.Piece pc, boolean compound) {
		BoundingBox b = pc.box();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
			m.set(x, y, z);
			if (c.used.contains(m.asLong()) || !c.level.isLoaded(m)) continue;
			BlockState cur = c.level.getBlockState(m);
			VillageRecord.Change ch = c.r.journal.get(m.asLong());
			BlockState orig = ch != null ? ch.original() : cur;
			if (ch == null && Plans.skip(cur)) continue;
			BlockState target = compound ? style(c, orig, m, b, pc.type() == VillageRecord.CENTER) : null;
			if (target == null && ch != null) target = orig; // repair
			if (target == null || target == cur) continue;
			c.add(m.immutable(), target);
		}
	}

	static @Nullable BlockState style(Ctx c, BlockState orig, BlockPos pos, BoundingBox box, boolean centre) {
		int x = pos.getX(), y = pos.getY(), z = pos.getZ();
		boolean low = y < box.minY() + 2;
		if (orig.is(BlockTags.PLANKS)) return low ? s(Blocks.SANDSTONE) : hash(x, y, z) % 6 == 0 ? s(Blocks.PACKED_MUD) : s(Blocks.MUD_BRICKS);
		if (orig.is(BlockTags.LOGS)) return low ? s(Blocks.CUT_SANDSTONE) : s(Blocks.SMOOTH_SANDSTONE); // pale quoins and lintels
		if (orig.is(BlockTags.WOODEN_STAIRS)) return Blocks.MUD_BRICK_STAIRS.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_SLABS)) return Blocks.MUD_BRICK_SLAB.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_FENCES)) return s(Blocks.SANDSTONE_WALL);
		if (orig.is(Blocks.COBBLESTONE) || orig.is(Blocks.MOSSY_COBBLESTONE)) return centre && besideWater(c, pos) ? lapis() : s(Blocks.SANDSTONE); // the lapis-tiled well
		if (orig.is(Blocks.COBBLESTONE_STAIRS) || orig.is(Blocks.MOSSY_COBBLESTONE_STAIRS)) return Blocks.SANDSTONE_STAIRS.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_SLAB) || orig.is(Blocks.MOSSY_COBBLESTONE_SLAB)) return Blocks.SANDSTONE_SLAB.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_WALL) || orig.is(Blocks.MOSSY_COBBLESTONE_WALL)) return Blocks.SANDSTONE_WALL.withPropertiesOf(orig);
		if (orig.is(Blocks.GLASS_PANE) || orig.is(Blocks.GLASS)) return s(Blocks.SANDSTONE_WALL); // no glass in Azmar: a stone grille
		return null;
	}

	static boolean besideWater(Ctx c, BlockPos p) {
		for (Direction d : Direction.Plane.HORIZONTAL) if (c.level.getBlockState(p.relative(d)).is(Blocks.WATER)) return true;
		return c.level.getBlockState(p.above()).is(Blocks.WATER);
	}

	// ---- wall growth (border villages, 1 step per 0.1 of fort) -------------------------------------------------------------

	/**
	 * Growth step {@code step} (1..9): steps 1-3 raise every compound-wall column by a dressed sandstone course under its cap (never past
	 * three extra courses, so a village that changes hands and comes back does not grow a tower), 4-6 turn every ninth cap into a lamp
	 * pillar with a lantern, 7-9 raise an obelisk in the court.
	 */
	public static List<VillageRecord.Op> growth(ServerLevel level, VillageRecord r, Profile p, int step, RandomSource rnd) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		if (step <= 6) {
			int n = 0;
			for (var e : r.journal.long2ObjectEntrySet()) {
				if (!e.getValue().written().is(Blocks.SMOOTH_SANDSTONE_SLAB)) continue;
				BlockPos cap = BlockPos.of(e.getLongKey());
				if (!level.isLoaded(cap) || !level.getBlockState(cap).is(Blocks.SMOOTH_SANDSTONE_SLAB) || !inWall(r, cap)) continue;
				int courses = 0;
				BlockPos q = cap.below();
				while (courses < 12 && (level.getBlockState(q).is(Blocks.MUD_BRICKS) || level.getBlockState(q).is(Blocks.SANDSTONE))) {
					courses++;
					q = q.below();
				}
				if (courses == 0) continue;
				n++;
				if (step <= 3) {
					if (courses >= H0 + undulate(cap.getX(), cap.getZ()) + 3 || !level.getBlockState(cap.above()).isAir()) continue;
					Plans.add(ops, used, cap, s(Blocks.SANDSTONE));
					Plans.add(ops, used, cap.above(), wallCap());
				} else if (n % 9 == step % 9 && level.getBlockState(cap.above()).isAir()) {
					Plans.add(ops, used, cap, s(Blocks.CHISELED_SANDSTONE));
					Plans.add(ops, used, cap.above(), s(Blocks.LANTERN));
				}
			}
		} else {
			BlockPos bell = Plans.surface(level, r.center);
			BlockPos spot = Plans.freeArea(level, r, bell, 16, 3, step + 2, used, rnd);
			if (spot != null) for (VillageRecord.Op o : obeliskOps(spot, step)) Plans.add(ops, used, BlockPos.of(o.pos()), o.state());
		}
		ops.removeIf(o -> !r.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP));
		return ops;
	}

	// ---- the Migdol outpost --------------------------------------------------------------------------------------------------

	/**
	 * Azmar outpost "Migdol" on the flat site at {@code c} (the surface air block; the site is within 1 block over radius 6): a court
	 * wall at radius 5 with an undulating top and two gate posts (wall banners) toward the castle, a battered square tower (5 wide at the
	 * foot, 3 wide above, a cavetto crown and parapet), a palm, and the obelisk border marker whose pyramidion is at {@code c + (3, 7, 3)}
	 * ({@link RivalPlans#hintPos}). Every column starts one block down (a footing), so nothing floats where the ground dips by one.
	 */
	public static List<VillageRecord.Op> outpost(Profile p, BlockPos c, Direction gate) {
		List<VillageRecord.Op> ops = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		BlockState foot = s(Blocks.SANDSTONE), mud = s(Blocks.MUD_BRICKS);
		// the obelisk marker first (the hint), on a footing
		BlockPos ob = c.offset(3, 0, 3);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) Plans.add(ops, used, ob.offset(dx, -1, dz), foot);
		for (VillageRecord.Op o : obeliskOps(ob, OBELISK)) Plans.add(ops, used, BlockPos.of(o.pos()), o.state());
		// the tower, in the quarter away from the gate
		int tx = gate.getStepX() != 0 ? -2 * gate.getStepX() : -2, tz = gate.getStepZ() != 0 ? -2 * gate.getStepZ() : -2;
		BlockPos t = c.offset(tx, 0, tz);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
			Plans.add(ops, used, t.offset(dx, -1, dz), foot);
			if (ring) {
				Direction in = Math.abs(dx) == 2 ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
				Plans.add(ops, used, t.offset(dx, 0, dz), corner ? s(Blocks.SANDSTONE) : stair(Blocks.MUD_BRICK_STAIRS, in, Half.BOTTOM));
				Plans.add(ops, used, t.offset(dx, 5, dz), corner ? s(Blocks.CUT_SANDSTONE) : stair(Blocks.SANDSTONE_STAIRS, in, Half.TOP));
				Plans.add(ops, used, t.offset(dx, 6, dz), s(Blocks.CUT_SANDSTONE_SLAB));
			} else {
				boolean face = dx != 0 || dz != 0;
				for (int h = 0; h <= 4; h++) Plans.add(ops, used, t.offset(dx, h, dz), h == 3 && face ? (dx != 0 && dz != 0 ? turquoise() : lapis()) : mud);
				Plans.add(ops, used, t.offset(dx, 5, dz), s(Blocks.SMOOTH_SANDSTONE));
			}
		}
		Plans.add(ops, used, t.offset(0, 6, 0), RivalPlans.banner(p, 4));
		// the court wall with its gate posts
		for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
			if (Math.abs(dx) != 5 && Math.abs(dz) != 5) continue;
			boolean onGateSide = gate.getStepX() != 0 ? dx == 5 * gate.getStepX() : dz == 5 * gate.getStepZ();
			int lateral = gate.getStepX() != 0 ? Math.abs(dz) : Math.abs(dx);
			if (onGateSide && lateral <= 1) continue; // the gate
			boolean post = onGateSide && lateral == 2;
			BlockPos q = c.offset(dx, 0, dz);
			int courses = post ? 4 : Math.max(1, 2 + undulate(q.getX(), q.getZ()));
			Plans.add(ops, used, q.below(), foot);
			for (int h = 0; h < courses; h++) Plans.add(ops, used, q.above(h), post ? s(Blocks.CUT_SANDSTONE) : mud);
			Plans.add(ops, used, q.above(courses), post ? s(Blocks.CHISELED_SANDSTONE) : wallCap());
			if (post) Plans.add(ops, used, q.relative(gate).above(2), Blocks.WALL_BANNER.pick(p.banner()).defaultBlockState().setValue(WallBannerBlock.FACING, gate));
		}
		// a palm in a free corner of the court, away from the gate, planted one block down like everything else
		for (int[] pc : new int[][] {{3, -3}, {-3, 3}, {-3, -3}, {3, 3}}) {
			BlockPos g = c.offset(pc[0], -1, pc[1]);
			if (used.contains(g.asLong()) || used.contains(g.above().asLong())) continue;
			if (Math.abs(pc[0] - 5 * gate.getStepX()) + Math.abs(pc[1] - 5 * gate.getStepZ()) < 5) continue;
			for (VillageRecord.Op o : palmOps(g, 5)) Plans.add(ops, used, BlockPos.of(o.pos()), o.state());
			break;
		}
		return ops;
	}
}
