package dev.elsewhere.kingdom_villages;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3.0 Mirewick village and rebuild looks (STRUCTURES.md 3.4 and 7.1, RIVALS-DECISIONS: the Lantern Republic, royal purple #5B2A86, crimson
 * #9E1B32, gold accents; s-vlook-mire). A Mirewick-held village becomes a polder town:
 * <ul>
 * <li><b>Houses</b>: purple-terracotta walls on a quartz-brick plinth, crimson-stem timber, purple-glass windows, crimson shutters, slate
 * (deepslate-tile) roofs with a crimson eaves course and ridge, a yellow-terracotta gilt cornice under the eaves and gilt finials at both ridge
 * ends. The 2.1 restyle was 94% crimson by block count (mangrove everywhere); this one measures about purple 50 / crimson 35 / gold 15.</li>
 * <li><b>Boardwalk quays</b>: raised pale-stone promenades along the village's own streets from the bell to its far ends. The deck
 * <b>steps with the ground</b> (neighbours differ by at most 1, quartz stairs at every step and at the ends), never skips a cell, never sits
 * below the ground, and stands on mangrove stilts wherever it spans a dip. Lantern posts stand on their own ground beside it.</li>
 * <li><b>Sluice house</b>: an open watch pavilion on mangrove stilts (the archer's post): each corner stilt reaches its own ground, a purple
 * parapet, crow-stepped calcite gables with gilt finials, a crimson ridge, and a red-nether-brick stair flight down to the ground.</li>
 * <li><b>Dike</b>: a grass bank along the village edge on its natural ground, with lantern posts.</li>
 * <li><b>Canal quay</b> (extends the 2.1 "quays" to real water): when a river or pond shore lies within 40 blocks, a quartz-brick quay edge
 * with a smooth-stone walk follows the shoreline in 1-steps at water level +1, with crimson-fence bollards, two lantern posts and a fish
 * market stall (purple and gold awning). Nothing is ever written into water and no water is ever placed, so nothing floods.</li>
 * <li>A peat stack by the bell (Mirewick's Peat-Cutters) instead of the 2.1 mess fire (no lit campfire, no ticking block entity).</li>
 * </ul>
 * Lanterns are the signature and are budgeted ({@link #MAX_LANTERNS} per village plus the border-hint post). No valuable blocks (gold is
 * yellow terracotta). Like {@link RivalPlans} this only lists ops; {@link BlockWriter} still decides every write.
 *
 * <p>Kill switch: {@code -Delsewhere.overhaul3.village_look_mirewick=false} runs the 2.1 Mirewick rebuild instead.
 */
public final class MirewickLooks {
	private MirewickLooks() {}

	/** Kill switch (BUILD-3.0): JVM flag {@code elsewhere.overhaul3.village_look_mirewick}, default on. */
	public static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.village_look_mirewick", "true"));

	/** VillageRecord piece type of the canal quay box (the writable box the plan adds, like Hrafnholt's pier). */
	public static final int QUAY = 31;
	/** Lanterns per village, besides the border-hint post (STRUCTURES 5: lanterns are the signature, budgeted). */
	public static final int MAX_LANTERNS = 16;
	static final int NONE = Integer.MIN_VALUE;

	static BlockState purple() {
		return Blocks.DYED_TERRACOTTA.purple().defaultBlockState();
	}

	static BlockState gilt() {
		return Blocks.DYED_TERRACOTTA.yellow().defaultBlockState();
	}

	static BlockState of(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState();
	}

	// ---- what the self-test reads back (the last plan) -------------------------------------------------------------------

	/** Each boardwalk leg of the last plan: per cell {x, z, deck y, floor y} (floor = first air above the ground). */
	public static final List<int[][]> lastLegs = new ArrayList<>();
	/** The last sluice house: {x, z, deck y, then the four corner floors}. */
	public static int @Nullable [] lastHut;
	/** The last canal quay: its box, the water top y it stands on, and how many edge blocks it planned. */
	public static @Nullable BoundingBox lastQuay;
	public static int lastQuayWater = NONE, lastQuayEdges, lastLanterns;

	// ---- the house restyle (RivalPlans.style for Mirewick) ---------------------------------------------------------------

	static boolean wallStuff(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.DYED_TERRACOTTA.white());
	}

	/** The Mirewick block for an original village block, or null to leave it (roof trim, cornice and finials come in {@link #houseDetails}). */
	static @Nullable BlockState style(BlockState orig, int y, BoundingBox box, RandomSource rnd) {
		if (wallStuff(orig)) {
			boolean stone = orig.is(Blocks.COBBLESTONE) || orig.is(Blocks.MOSSY_COBBLESTONE);
			if (y <= box.minY()) return stone ? of(Blocks.STONE_BRICKS) : of(Blocks.QUARTZ_BRICKS);
			if (y == box.minY() + 1 && stone) return rnd.nextInt(6) == 0 ? of(Blocks.CALCITE) : of(Blocks.QUARTZ_BRICKS);
			return purple();
		}
		if (orig.is(BlockTags.LOGS)) return Blocks.CRIMSON_STEM.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_STAIRS)) return Blocks.DEEPSLATE_TILE_STAIRS.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_SLABS)) return Blocks.DEEPSLATE_TILE_SLAB.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_FENCES)) return Blocks.CRIMSON_FENCE.withPropertiesOf(orig);
		if (orig.is(BlockTags.WOODEN_TRAPDOORS)) return Blocks.CRIMSON_TRAPDOOR.withPropertiesOf(orig);
		if (orig.is(Blocks.GLASS_PANE)) return Blocks.STAINED_GLASS_PANE.purple().withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_STAIRS) || orig.is(Blocks.MOSSY_COBBLESTONE_STAIRS)) return Blocks.STONE_BRICK_STAIRS.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_SLAB) || orig.is(Blocks.MOSSY_COBBLESTONE_SLAB)) return Blocks.STONE_BRICK_SLAB.withPropertiesOf(orig);
		if (orig.is(Blocks.COBBLESTONE_WALL) || orig.is(Blocks.MOSSY_COBBLESTONE_WALL)) return Blocks.STONE_BRICK_WALL.withPropertiesOf(orig);
		if (orig.is(BlockTags.CROPS)) return orig.getBlock().defaultBlockState();
		return null;
	}

	// ---- the plan ---------------------------------------------------------------------------------------------------------

	/** Per-plan state: the shared context plus the lantern budget. */
	static final class B {
		final RivalPlans.Ctx c;
		int lanterns;

		B(RivalPlans.Ctx c) {
			this.c = c;
		}

		boolean lamp() {
			if (lanterns >= MAX_LANTERNS) return false;
			lanterns++;
			return true;
		}
	}

	/** The Mirewick signature (RivalPlans.fortify step 3), after the house restyle ops are in {@code c.ops}. */
	static void build(RivalPlans.Ctx c) {
		B b = new B(c);
		lastLegs.clear();
		lastHut = null;
		lastQuay = null;
		lastQuayWater = NONE;
		lastQuayEdges = 0;
		houseDetails(c);
		sluiceHouse(b);
		boardwalk(b);
		peatStack(c);
		BlockPos hs = RivalPlans.hintSpot(c);
		if (hs != null) {
			for (VillageRecord.Op op : RivalPlans.lanternPostOps(hs)) c.add(BlockPos.of(op.pos()), op.state());
			c.hint = hs.above(5);
		}
		RivalPlans.houseBanners(c);
		dike(b);
		quay(b);
		lastLanterns = b.lanterns;
	}

	// ---- houses: crimson eaves and ridge on the slate roof, gilt cornice and finials ----------------------------------------

	static BlockState orig(RivalPlans.Ctx c, BlockPos p) {
		VillageRecord.Change ch = c.r.journal.get(p.asLong());
		return ch != null ? ch.original() : c.level.getBlockState(p);
	}

	static boolean roof(BlockState s) {
		return s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS);
	}

	/** Replaces the plan's op at {@code p}, or adds one where the restyle left the block (never over a skipped block). */
	static void set(RivalPlans.Ctx c, Map<Long, Integer> at, BlockPos p, BlockState s) {
		Integer i = at.get(p.asLong());
		if (i != null) {
			c.ops.set(i, new VillageRecord.Op(p.asLong(), s));
			return;
		}
		BlockState cur = c.level.getBlockState(p);
		if (Plans.skip(cur) || cur == s || !c.used.add(p.asLong())) return;
		at.put(p.asLong(), c.ops.size());
		c.ops.add(new VillageRecord.Op(p.asLong(), s));
	}

	static void houseDetails(RivalPlans.Ctx c) {
		ServerLevel level = c.level;
		Map<Long, Integer> at = new HashMap<>();
		for (int i = 0; i < c.ops.size(); i++) at.put(c.ops.get(i).pos(), i);
		for (VillageRecord.Piece pc : c.r.pieces) {
			if (pc.type() != VillageRecord.HOUSE && pc.type() != VillageRecord.CENTER) continue;
			BoundingBox b = pc.box();
			if (!level.isLoaded(new BlockPos(b.minX(), b.minY(), b.minZ())) || !level.isLoaded(new BlockPos(b.maxX(), b.minY(), b.maxZ()))) continue;
			List<BlockPos> roofs = new ArrayList<>();
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				BlockPos p = new BlockPos(x, y, z);
				BlockState o = orig(c, p);
				if (roof(o)) {
					roofs.add(p);
					lo = Math.min(lo, y);
					hi = Math.max(hi, y);
				} else if (y > b.minY() + 1 && wallStuff(o) && roof(orig(c, p.above()))) {
					// the wall course right under the roof: a gilt cornice where it shows outside (and along the gable rakes)
					boolean shows = false;
					for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(p.relative(d)).isAir()) shows = true;
					if (shows) set(c, at, p, gilt());
				}
			}
			if (roofs.isEmpty()) continue;
			// crimson eaves course and ridge over the slate field
			for (BlockPos p : roofs) {
				if (p.getY() != lo && p.getY() != hi) continue;
				BlockState o = orig(c, p);
				set(c, at, p, (o.is(BlockTags.WOODEN_STAIRS) ? Blocks.CRIMSON_STAIRS : Blocks.CRIMSON_SLAB).withPropertiesOf(o));
			}
			// gilt finials at both ends of the ridge
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
			List<BlockPos> top = new ArrayList<>();
			for (BlockPos p : roofs) if (p.getY() == hi) {
				top.add(p);
				minX = Math.min(minX, p.getX());
				maxX = Math.max(maxX, p.getX());
				minZ = Math.min(minZ, p.getZ());
				maxZ = Math.max(maxZ, p.getZ());
			}
			boolean alongX = maxX - minX >= maxZ - minZ;
			BlockPos e0 = top.get(0), e1 = top.get(0);
			for (BlockPos p : top) {
				if (alongX ? p.getX() < e0.getX() : p.getZ() < e0.getZ()) e0 = p;
				if (alongX ? p.getX() > e1.getX() : p.getZ() > e1.getZ()) e1 = p;
			}
			for (BlockPos e : e0.equals(e1) ? List.of(e0) : List.of(e0, e1)) {
				BlockPos f = e.above();
				if (level.getBlockState(f).isAir() && c.r.insideFootprint(f, BlockWriter.UP)) c.add(f, gilt());
			}
		}
	}

	// ---- ground ------------------------------------------------------------------------------------------------------------

	static boolean natural(BlockState g) {
		return g.is(Blocks.GRASS_BLOCK) || g.is(BlockTags.DIRT) || g.is(Blocks.SAND) || g.is(Blocks.RED_SAND) || g.is(Blocks.GRAVEL) || g.is(Blocks.CLAY)
			|| g.is(Blocks.MUD) || g.is(Blocks.STONE) || g.is(Blocks.SNOW_BLOCK) || g.is(Blocks.COARSE_DIRT) || g.is(Blocks.PODZOL);
	}

	/**
	 * The first air y above the natural ground of a column (the ground is the top motion-blocking block), or {@link #NONE} for water,
	 * farmland, a building or tree on top, or an unloaded column. Paths count as ground only when {@code pathOk}.
	 */
	static int floor(ServerLevel level, int x, int z, boolean pathOk) {
		BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		if (!level.isLoaded(top)) return NONE;
		BlockState g = level.getBlockState(top);
		if (g.is(Blocks.DIRT_PATH)) return pathOk ? top.getY() + 1 : NONE;
		return natural(g) ? top.getY() + 1 : NONE;
	}

	/** Air the plan may fill: loaded, in the village footprint, not already planned. */
	static boolean free(RivalPlans.Ctx c, BlockPos p) {
		return c.level.isLoaded(p) && c.r.insideFootprint(p, BlockWriter.UP) && !c.used.contains(p.asLong()) && c.level.getBlockState(p).isAir();
	}

	static void column(RivalPlans.Ctx c, int x, int z, int from, int to, BlockState s) {
		for (int y = from; y <= to; y++) c.add(new BlockPos(x, y, z), s);
	}

	// ---- sluice house: the watch pavilion on stilts ---------------------------------------------------------------------------

	static void sluiceHouse(B b) {
		RivalPlans.Ctx c = b.c;
		ServerLevel level = c.level;
		for (int tries = 0; tries < 160; tries++) {
			BlockPos t = c.bell.offset(c.rnd.nextInt(41) - 20, 0, c.rnd.nextInt(41) - 20);
			int[] fl = new int[9];
			int mx = Integer.MIN_VALUE, mn = Integer.MAX_VALUE;
			boolean ok = true;
			for (int i = 0; i < 9 && ok; i++) {
				int f = floor(level, t.getX() + i % 3 - 1, t.getZ() + i / 3 - 1, false);
				if (f == NONE) ok = false;
				fl[i] = f;
				mx = Math.max(mx, f);
				mn = Math.min(mn, f);
			}
			if (!ok || mx - mn > 2) continue;
			int deck = mx + 2;
			for (int i = 0; i < 9 && ok; i++) for (int y = fl[i]; y <= deck + 5 && ok; y++) ok = free(c, new BlockPos(t.getX() + i % 3 - 1, y, t.getZ() + i / 3 - 1));
			if (!ok) continue;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				List<int[]> flight = flight(c, t, d, deck);
				if (flight == null) continue;
				hut(b, t, deck, fl, d, flight);
				return;
			}
		}
	}

	/** Stairs from the hut's door side down to the ground: a landing level with the deck, then one step down per block until a step rests on the ground. */
	static @Nullable List<int[]> flight(RivalPlans.Ctx c, BlockPos t, Direction d, int deck) {
		List<int[]> out = new ArrayList<>();
		for (int k = 2; k <= 8; k++) {
			int x = t.getX() + d.getStepX() * k, z = t.getZ() + d.getStepZ() * k, y = deck - Math.max(0, k - 2);
			int f = floor(c.level, x, z, false);
			if (f == NONE || y < f) return null;
			for (int h = f; h <= y + 2; h++) if (!free(c, new BlockPos(x, h, z))) return null;
			out.add(new int[] {x, z, y, f});
			if (y == f && k > 2) return out;
		}
		return null;
	}

	static void hut(B b, BlockPos t, int deck, int[] fl, Direction d, List<int[]> flight) {
		RivalPlans.Ctx c = b.c;
		Direction s = d.getClockWise();
		BlockState stilt = of(Blocks.STRIPPED_MANGROVE_LOG), calcite = of(Blocks.CALCITE);
		int[] floors = new int[4];
		int n = 0;
		for (int i = 0; i < 9; i++) {
			int dx = i % 3 - 1, dz = i / 3 - 1, x = t.getX() + dx, z = t.getZ() + dz;
			c.add(new BlockPos(x, deck, z), of(Blocks.SMOOTH_STONE));
			if (dx != 0 && dz != 0) {
				column(c, x, z, fl[i], deck - 1, stilt); // each stilt reaches its own ground
				column(c, x, z, deck + 1, deck + 2, stilt); // and carries the roof
				floors[n++] = fl[i];
			}
		}
		// parapet (open toward the stair), roof: crow-stepped calcite gables front and back, a crimson ridge course between, gilt finials
		for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) {
			BlockPos p = t.relative(d, u).relative(s, v);
			int x = p.getX(), z = p.getZ();
			boolean corner = u != 0 && v != 0;
			if (!corner && (u != 0 || v != 0) && !(u == 1 && v == 0)) c.add(new BlockPos(x, deck + 1, z), purple());
			if (u != 0) {
				c.add(new BlockPos(x, deck + 3, z), calcite);
				if (v == 0) {
					c.add(new BlockPos(x, deck + 4, z), calcite);
					c.add(new BlockPos(x, deck + 5, z), gilt());
				}
			} else if (v == 0) {
				c.add(new BlockPos(x, deck + 3, z), of(Blocks.CRIMSON_PLANKS));
				c.add(new BlockPos(x, deck + 4, z), of(Blocks.CRIMSON_SLAB));
			} else c.add(new BlockPos(x, deck + 3, z), Blocks.CRIMSON_STAIRS.defaultBlockState().setValue(StairBlock.FACING, v < 0 ? s : s.getOpposite()));
		}
		// a lantern on the back parapet, the stair flight on red-nether-brick steps with stilts under them
		if (b.lamp()) c.add(t.relative(d, -1).atY(deck + 2), of(Blocks.LANTERN));
		for (int[] f : flight) {
			column(c, f[0], f[1], f[3], f[2] - 1, stilt);
			BlockPos p = new BlockPos(f[0], f[2], f[1]);
			if (f[2] == deck) c.add(p, of(Blocks.SMOOTH_STONE));
			else c.add(p, Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d.getOpposite()));
		}
		c.towerTop = new BlockPos(t.getX(), deck + 1, t.getZ());
		lastHut = new int[] {t.getX(), t.getZ(), deck, floors[0], floors[1], floors[2], floors[3]};
	}

	// ---- boardwalk quays along the streets, stepping with the ground ---------------------------------------------------------

	static boolean pathCell(RivalPlans.Ctx c, int x, int z) {
		if (!Plans.inAnyColumn(c.r, x, z)) return false;
		BlockPos top = new BlockPos(x, c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		return c.level.isLoaded(top) && c.level.getBlockState(top).is(Blocks.DIRT_PATH) && c.level.getBlockState(top.above()).isAir();
	}

	static void boardwalk(B b) {
		RivalPlans.Ctx c = b.c;
		BlockPos bell = c.bell;
		long start = Long.MIN_VALUE;
		search:
		for (int r = 0; r <= 10; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
			if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
			if (pathCell(c, bell.getX() + dx, bell.getZ() + dz)) {
				start = BlockPos.asLong(bell.getX() + dx, 0, bell.getZ() + dz);
				break search;
			}
		}
		if (start == Long.MIN_VALUE) return;
		// breadth-first over the street cells: the legs follow the village's own streets
		Map<Long, Long> parent = new HashMap<>();
		Map<Long, Integer> dist = new HashMap<>();
		ArrayDeque<Long> q = new ArrayDeque<>();
		dist.put(start, 0);
		q.add(start);
		while (!q.isEmpty() && dist.size() < 4000) {
			long k = q.poll();
			int x = BlockPos.getX(k), z = BlockPos.getZ(k);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int nx = x + d.getStepX(), nz = z + d.getStepZ();
				if (Math.abs(nx - bell.getX()) > 64 || Math.abs(nz - bell.getZ()) > 64) continue;
				long nk = BlockPos.asLong(nx, 0, nz);
				if (dist.containsKey(nk) || !pathCell(c, nx, nz)) continue;
				dist.put(nk, dist.get(k) + 1);
				parent.put(nk, k);
				q.add(nk);
			}
		}
		// up to three legs to the farthest street ends, in clearly different directions
		List<Long> cand = new ArrayList<>(dist.keySet());
		cand.sort((a, bb) -> dist.get(bb) - dist.get(a) != 0 ? dist.get(bb) - dist.get(a) : Long.compare(a, bb));
		List<Double> angles = new ArrayList<>();
		Map<Long, Integer> deck = new HashMap<>();
		for (long e : cand) {
			if (angles.size() >= 3 || dist.get(e) < 8) break;
			double a = Math.atan2(BlockPos.getZ(e) - bell.getZ(), BlockPos.getX(e) - bell.getX());
			boolean apart = true;
			for (double o : angles) {
				double diff = Math.abs(a - o) % (2 * Math.PI);
				if (Math.min(diff, 2 * Math.PI - diff) < Math.toRadians(75)) apart = false;
			}
			if (!apart) continue;
			List<Long> chain = new ArrayList<>();
			for (Long k = e; k != null; k = parent.get(k)) chain.add(k);
			Collections.reverse(chain);
			// a later leg starts where it branches off an earlier one (that cell's deck height is fixed)
			int from = 0;
			for (int i = 0; i < chain.size(); i++) if (deck.containsKey(chain.get(i))) from = i;
			if (leg(b, chain.subList(from, chain.size()), deck, deck.containsKey(chain.get(from)))) angles.add(a);
		}
	}

	/** Lays one leg; {@code anchored}: cell 0 already has its deck from an earlier leg. Returns false when nothing was laid. */
	static boolean leg(B b, List<Long> cells, Map<Long, Integer> decks, boolean anchored) {
		RivalPlans.Ctx c = b.c;
		ServerLevel level = c.level;
		int n = cells.size();
		int[] xs = new int[n], zs = new int[n], s = new int[n];
		for (int i = 0; i < n; i++) {
			xs[i] = BlockPos.getX(cells.get(i));
			zs[i] = BlockPos.getZ(cells.get(i));
			s[i] = floor(level, xs[i], zs[i], true);
			if (s[i] == NONE || i > 0 && Math.abs(s[i] - s[i - 1]) > 2) {
				n = i;
				break;
			}
		}
		for (int attempt = 0; attempt < 4 && n >= 4; attempt++) {
			int[] d = new int[n];
			for (int i = 0; i < n; i++) d[i] = anchored && i == 0 ? decks.get(cells.get(0)) : s[i];
			// raise valleys so neighbours differ by at most 1, and lift single-block humps' neighbours (a hump cannot be stairs both ways)
			for (int it = 0; it < 6 * n; it++) {
				boolean changed = false;
				for (int i = anchored ? 1 : 0; i < n; i++) {
					int want = d[i];
					if (i > 0) want = Math.max(want, d[i - 1] - 1);
					if (i < n - 1) want = Math.max(want, d[i + 1] - 1);
					if (i > 0 && i < n - 1 && d[i - 1] > d[i] && d[i + 1] > d[i]) want = Math.max(want, Math.min(d[i - 1], d[i + 1]));
					if (i > 0 && i < n - 1 && d[i] > d[i - 1] && d[i] > d[i + 1]) {
						if (!(anchored && i - 1 == 0)) d[i - 1] = d[i];
						d[i + 1] = d[i];
						changed = true;
					}
					if (want != d[i]) {
						d[i] = want;
						changed = true;
					}
				}
				if (!changed) break;
			}
			// cut the leg where it would stand more than 4 above its ground, run into something, or break the 1-step rule at its anchor
			int cut = n;
			for (int i = 0; i < n && cut == n; i++) {
				if (d[i] - s[i] > 4 || d[i] < s[i] || i > 0 && Math.abs(d[i] - d[i - 1]) > 1) cut = i;
				else if (!(anchored && i == 0)) for (int y = s[i]; y <= d[i] && cut == n; y++) if (!free(c, new BlockPos(xs[i], y, zs[i]))) cut = i;
			}
			if (cut < n) {
				n = cut;
				continue;
			}
			if (n - (anchored ? 1 : 0) < 3) return false;
			int[][] rec = new int[n][];
			for (int i = 0; i < n; i++) {
				rec[i] = new int[] {xs[i], zs[i], d[i], s[i]};
				if (anchored && i == 0) continue;
				decks.put(cells.get(i), d[i]);
				BlockPos p = new BlockPos(xs[i], d[i], zs[i]);
				column(c, xs[i], zs[i], s[i], d[i] - 1, of(Blocks.STRIPPED_MANGROVE_LOG)); // stilts under any raised span
				Direction up = null;
				if (i > 0 && d[i] > d[i - 1]) up = dir(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
				else if (i < n - 1 && d[i] > d[i + 1]) up = dir(xs[i] - xs[i + 1], zs[i] - zs[i + 1]);
				else if ((i == n - 1 || i == 0 && !anchored) && d[i] == s[i] && n > 1) {
					int j = i == 0 ? 1 : i - 1; // step up onto the deck from the street beyond the end
					up = dir(xs[j] - xs[i], zs[j] - zs[i]);
				}
				if (up != null) c.add(p, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
				else c.add(p, i % 6 == 3 ? purple() : of(Blocks.SMOOTH_STONE));
				if (i % 7 == 3) lampBeside(b, xs, zs, i, d[i], n);
			}
			lastLegs.add(rec);
			return true;
		}
		return false;
	}

	/** The horizontal direction of a one-block step. */
	static Direction dir(int dx, int dz) {
		return dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** A crimson-fence lantern post on its own ground beside the deck (alternating sides), never on the street. */
	static void lampBeside(B b, int[] xs, int[] zs, int i, int deckY, int n) {
		RivalPlans.Ctx c = b.c;
		int j = i < n - 1 ? i + 1 : i - 1;
		Direction along = dir(xs[j] - xs[i], zs[j] - zs[i]);
		for (Direction side : (i / 7) % 2 == 0 ? List.of(along.getClockWise(), along.getCounterClockWise()) : List.of(along.getCounterClockWise(), along.getClockWise())) {
			int x = xs[i] + side.getStepX(), z = zs[i] + side.getStepZ();
			int f = floor(c.level, x, z, false);
			if (f == NONE || f > deckY + 1 || deckY + 2 - f > 4) continue;
			boolean ok = true;
			for (int y = f; y <= deckY + 2 && ok; y++) ok = free(c, new BlockPos(x, y, z));
			if (!ok || !b.lamp()) continue;
			column(c, x, z, f, deckY + 1, of(Blocks.CRIMSON_FENCE));
			c.add(new BlockPos(x, deckY + 2, z), of(Blocks.LANTERN));
			return;
		}
	}

	// ---- peat stack, dike ----------------------------------------------------------------------------------------------------

	static void peatStack(RivalPlans.Ctx c) {
		BlockPos f = Plans.freeFloor(c.level, c.r, c.bell.offset(3, 0, 3), 8, c.used);
		if (f == null) return;
		c.add(f, of(Blocks.PACKED_MUD));
		c.add(f.above(), of(Blocks.MUD_BRICK_SLAB));
	}

	/** The low dike: a grass bank on the natural ground of every outer-edge column (gates left open), a lantern post every ninth column. */
	static void dike(B b) {
		RivalPlans.Ctx c = b.c;
		int[] n = {0};
		RivalPlans.perimeter(c, (ground, nearGate) -> {
			if (nearGate) return;
			n[0]++;
			if (n[0] % 9 == 0 && b.lamp()) {
				for (VillageRecord.Op op : RivalPlans.lanternPostOps(ground.above())) c.add(BlockPos.of(op.pos()), op.state());
				return;
			}
			c.add(ground.above(), of(Blocks.GRASS_BLOCK));
		});
	}

	// ---- canal quay on a real shore ------------------------------------------------------------------------------------------

	static void quay(B b) {
		RivalPlans.Ctx c = b.c;
		ServerLevel level = c.level;
		BoundingBox old = null;
		for (VillageRecord.Piece pc : c.r.pieces) if (pc.type() == QUAY) old = pc.box();
		BlockPos ctr = c.r.center;
		for (Direction d : Direction.Plane.HORIZONTAL) for (int lat = -12; lat <= 12; lat += 6) for (int rad = 4; rad <= 40; rad++) {
			BlockPos base = ctr.relative(d, rad).relative(d.getClockWise(), lat);
			if (!level.isLoaded(base) || !level.isLoaded(base.relative(d, 4))) break;
			int w0 = RivalPlans.waterTop(level, base.getX(), base.getZ());
			if (w0 == NONE) continue;
			// the first water on this ray: a shore only if dry land lies behind it and open water ahead
			BlockPos land = base.relative(d, -1);
			boolean shore = RivalPlans.waterTop(level, land.getX(), land.getZ()) == NONE;
			for (int i = 1; i <= 3 && shore; i++) shore = RivalPlans.waterTop(level, base.getX() + d.getStepX() * i, base.getZ() + d.getStepZ() * i) == w0;
			if (shore && (old == null || old.isInside(new BlockPos(land.getX(), old.minY(), land.getZ()))) && buildQuay(b, land, d, w0, old)) return;
			break;
		}
	}

	static boolean buildQuay(B b, BlockPos land0, Direction d, int w0, @Nullable BoundingBox old) {
		RivalPlans.Ctx c = b.c;
		ServerLevel level = c.level;
		Direction side = d.getClockWise();
		List<int[]> edges = new ArrayList<>(); // x, z, quay y, v, inner y (NONE = no inner walk)
		for (int v = -7; v <= 7; v++) {
			BlockPos l = null;
			for (int u = -3; u <= 3 && l == null; u++) {
				BlockPos p = land0.relative(side, v).relative(d, u);
				if (!level.isLoaded(p) || !level.isLoaded(p.relative(d))) continue;
				if (RivalPlans.waterTop(level, p.getX(), p.getZ()) == NONE && RivalPlans.waterTop(level, p.getX() + d.getStepX(), p.getZ() + d.getStepZ()) == w0) l = p;
			}
			if (l == null) continue;
			int f = floor(level, l.getX(), l.getZ(), true);
			if (f == NONE || f - 1 < w0 || f - 1 > w0 + 2) continue;
			int qy = Math.max(w0 + 1, f - 1);
			if (qy == f && !free(c, new BlockPos(l.getX(), qy, l.getZ()))) continue;
			BlockPos in = l.relative(d, -1);
			int fi = floor(level, in.getX(), in.getZ(), true), iy = NONE;
			if (fi != NONE && RivalPlans.waterTop(level, in.getX(), in.getZ()) == NONE && (fi - 1 == qy || fi == qy && free(c, new BlockPos(in.getX(), qy, in.getZ())))) iy = qy;
			edges.add(new int[] {l.getX(), l.getZ(), qy, v, iy});
		}
		if (edges.size() < 5) return false;
		int[] mm = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		List<BlockPos> placed = new ArrayList<>();
		for (int[] e : edges) {
			BlockPos p = new BlockPos(e[0], e[2], e[1]);
			placed.add(p);
			c.add(p, of(Blocks.QUARTZ_BRICKS));
			if (e[3] % 3 == 0 && free(c, p.above())) {
				c.add(p.above(), of(Blocks.CRIMSON_FENCE)); // bollard
				placed.add(p.above());
			}
			if (e[4] != NONE) {
				BlockPos in = p.relative(d, -1);
				c.add(in, of(Blocks.SMOOTH_STONE));
				placed.add(in);
			}
		}
		// lantern posts at both ends of the walk
		for (int[] e : List.of(edges.get(0), edges.get(edges.size() - 1))) {
			if (e[4] == NONE) continue;
			BlockPos in = new BlockPos(e[0], e[2], e[1]).relative(d, -1);
			if (!free(c, in.above()) || !free(c, in.above(2)) || !free(c, in.above(3)) || !b.lamp()) continue;
			c.add(in.above(), of(Blocks.CRIMSON_FENCE));
			c.add(in.above(2), of(Blocks.CRIMSON_FENCE));
			c.add(in.above(3), of(Blocks.LANTERN));
			placed.add(in.above(3));
		}
		// the fish market: a 3x2 stall behind the walk at the middle of the quay (purple and gold awning on crimson posts, a counter, dried fish)
		int[] mid = edges.get(edges.size() / 2);
		if (mid[4] != NONE) {
			BlockPos m0 = new BlockPos(mid[0], mid[2], mid[1]).relative(d, -2);
			int fs = NONE;
			boolean ok = true;
			for (int u = 0; u <= 1 && ok; u++) for (int v = -1; v <= 1 && ok; v++) {
				BlockPos p = m0.relative(d, -u).relative(side, v);
				int f = floor(level, p.getX(), p.getZ(), false);
				if (f == NONE || fs != NONE && f != fs || Math.abs(f - (mid[2] + 1)) > 1) ok = false;
				fs = f;
				for (int h = 0; h <= 3 && ok; h++) ok = free(c, p.atY(f + h));
			}
			if (ok) for (int u = 0; u <= 1; u++) for (int v = -1; v <= 1; v++) {
				BlockPos p = m0.relative(d, -u).relative(side, v).atY(fs);
				if (v != 0) column(c, p.getX(), p.getZ(), fs, fs + 1, of(Blocks.CRIMSON_FENCE));
				else c.add(p, u == 0 ? of(Blocks.SMOOTH_STONE_SLAB) : of(Blocks.DRIED_KELP_BLOCK));
				c.add(p.above(2), (v & 1) == 0 ? Blocks.WOOL.purple().defaultBlockState() : Blocks.WOOL.yellow().defaultBlockState());
				placed.add(p.above(2));
			}
		}
		for (BlockPos p : placed) {
			mm[0] = Math.min(mm[0], p.getX());
			mm[1] = Math.min(mm[1], p.getY());
			mm[2] = Math.min(mm[2], p.getZ());
			mm[3] = Math.max(mm[3], p.getX());
			mm[4] = Math.max(mm[4], p.getY());
			mm[5] = Math.max(mm[5], p.getZ());
		}
		BoundingBox box = old != null ? old : new BoundingBox(mm[0] - 1, w0 - 1, mm[2] - 1, mm[3] + 1, mm[4] + 1, mm[5] + 1);
		if (old == null) c.r.pieces.add(new VillageRecord.Piece(box, QUAY));
		lastQuay = box;
		lastQuayWater = w0;
		lastQuayEdges = edges.size();
		return true;
	}
}
