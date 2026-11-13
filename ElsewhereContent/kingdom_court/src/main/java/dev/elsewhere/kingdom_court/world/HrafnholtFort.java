package dev.elsewhere.kingdom_court.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

/**
 * s-layout-hraf (3.0.0): Hrafnholt's own castle LAYOUT for new (fv 2) realm starts, the ring fort of STRUCTURES.md 3.2. Not the Aldermark square
 * re-roofed: a round Trelleborg-style rampart (earth bank with a mossy revetment, a log palisade with fence tips on its crest and a walkway
 * behind it) that follows the generator ground bearing by bearing, four gate towers on the axes (two storeys, an overhanging fighting gallery
 * and a gable roof; a side gate opens only where the ground outside meets it, the gate on the water side always), archer stages at the south
 * wall walk and a watch-fire cairn on the water-side corner ({@link #rampart}). Inside ({@link #court}): the jarl's longhall with bowed walls,
 * a hogback shingle roof with dragon finials, the external post gallery (svalgang), long benches, two hearths, the dais with the high seat and
 * the dungeon hatch; four bowed turf-roofed longhouses (warriors' hall, storehouse, guest house and kin house); the hof (stave temple) with
 * its stepped roofs and spire; the forge, the stables, the holmgang posts and a runestone. Green and brown banners, spruce and dark oak,
 * moss, mossy cobblestone; no valuable block. The waterfront's shore road and the nausts (boat sheds with slips) are {@link HrafnholtShore}.
 * <p>
 * Runs only for fv 2 pieces of the Hrafnholt style ({@code Placer.ground != null}); saved fv 1 starts and every other kingdom are untouched.
 * Every gameplay anchor of {@link CastleLayout} keeps its place and stays open floor (throne at (0, 1, -24) is dark oak stairs, the gate at
 * z 30 is five fence gates, hall, yard, barracks, stables, balconies at (+-9, 5, -17), archer posts at (+-12, 9, 29), yard loop, braziers).
 * Deterministic: only the generator ground ({@code Ground.y}) and the chunk being written are read; nothing ticks (campfires only where the
 * shared layout already lights braziers). Kill switch: {@code -Delsewhere.overhaul3.hrafnholt_fort=false} (Hrafnholt falls back to the
 * shared layout plus the CastleKit re-roof).
 */
public final class HrafnholtFort {
	private HrafnholtFort() {}

	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.hrafnholt_fort", "true"));

	/** Hook for {@link CastleLayout#build}: true when this part was built (or deliberately left empty) by the Hrafnholt layout. */
	public static boolean build(CastleLayout.Part part, BlockPos origin, CastleLayout.Placer pl) {
		if (!ENABLED || pl.ground == null || CastleKit.kitOf(pl) != CastleKit.Kit.HRAFNHOLT) return false;
		switch (part) {
			case CASTLE -> court(new H(origin, pl), ring(origin, pl.ground, waterDir(pl)));
			case WALL -> rampart(new H(origin, pl), ring(origin, pl.ground, waterDir(pl)));
			case KIT_CASTLE -> {
				// nothing: the 2.x Hrafnholt kit re-roofs the Aldermark keep and towers, which this layout does not build
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	// ---- faction / water lookup -----------------------------------------------------------------------------------

	/** The faction id of the placer's style (identity lookup in the style table), or null. */
	static @Nullable Identifier factionOf(CastleLayout.Placer pl) {
		if (pl.style == null) return null;
		for (Map.Entry<String, CastleStyles.Style> e : CastleStyles.STYLES.entrySet())
			if (e.getValue() == pl.style) return Identifier.tryParse(e.getKey());
		return null;
	}

	/** Waterfront direction of this realm (1 south, 3 north) or 0 for none. */
	static int waterDir(CastleLayout.Placer pl) {
		Identifier f = factionOf(pl);
		if (f == null) return 0;
		int[] w = CourtWorld.water(pl.level.getSeed(), f);
		return w == null ? 0 : w[0];
	}

	// ---- named places for routines (s-routines: CourtRoutineAnchors may call this for Hrafnholt ids) ------------------------------

	/**
	 * World position of a Hrafnholt routine anchor id at the realm {@code site} (floor level), or null when the id is unknown or needs a
	 * waterfront that this realm does not have. {@code water} = {@link CourtWorld#water} of the realm, {@code sea} = sea level.
	 */
	public static @Nullable BlockPos anchor(BlockPos site, String where, int @Nullable [] water, int sea) {
		int[] p = switch (where) {
			case "longhall" -> new int[] {0, 0, -15};
			case "hall_door" -> new int[] {0, 0, 6};
			case "yard" -> new int[] {-8, 0, 8};
			case "wrestling_ring", "holmgang_ring" -> new int[] {-8, 0, 7};
			case "forge" -> new int[] {11, 0, -3};
			case "thing_stone" -> new int[] {-10, 0, -6};
			case "grove" -> new int[] {0, 0, 11};
			default -> null;
		};
		if (p != null) return site.offset(p[0], p[1], p[2]);
		if (water == null) return null;
		int dir = water[0], shore = water[1];
		int[] uv = switch (where) {
			case "jetty" -> new int[] {shore, 0};
			case "shore", "strand" -> new int[] {Math.max(Waterfront.U_MIN, shore - 4), 0};
			case "boats", "shipyard" -> new int[] {Math.max(Waterfront.U_MIN, shore - 4), -3};
			default -> null;
		};
		if (uv == null) return null;
		int[] w = Waterfront.world(site, dir, uv[0], uv[1]);
		return new BlockPos(w[0], sea + 1, w[1]);
	}

	// ---- the ring (constant per realm, from generator ground only) -------------------------------------------------

	static final int N = 128;
	public static final int N_BEARINGS = N;
	/** Gate bearings: south, east, north, west (gate index g = 0..3, bearing 32 g). */
	public static final int[] GATE_R = {31, 33, 33, 33};

	/**
	 * Ring of one realm: bank centre radius {@code rc} and crest lift {@code base} (relative to the floor) per bearing, which gates are open,
	 * each gate's tower lift {@code gl}, the beacon cairn corner and the water direction it was planned for.
	 */
	public record Ring(int[] rc, int[] base, boolean[] open, int[] gl, int beaconX, int beaconZ, int water) {
		public boolean gateOpen(int g) {
			return open[g];
		}
	}

	private static final Map<String, Ring> RINGS = new ConcurrentHashMap<>();

	public static int bearing(int x, int z) {
		return Math.floorMod((int) Math.round(Math.atan2(x, z) / (2 * Math.PI) * N), N);
	}

	static int circ(int a, int b) {
		int d = Math.floorMod(a - b, N);
		return Math.min(d, N - d);
	}

	/** Bank offset of a column: round(r) - rc(bearing). */
	public static int dAt(Ring r, int x, int z) {
		return (int) Math.round(Math.sqrt(x * x + z * z)) - r.rc[bearing(x, z)];
	}

	public static Ring ring(BlockPos o, CastleLayout.Ground g, int water) {
		String key = o.asLong() + ":" + water + ":" + g.y(o.getX() + 33, o.getZ()) + ":" + g.y(o.getX(), o.getZ() - 33) + ":" + g.y(o.getX() - 24, o.getZ() + 24);
		Ring c = RINGS.get(key);
		if (c != null) return c;
		int[] rc = new int[N], base = new int[N], gr = new int[N * 3];
		for (int i = 0; i < N; i++) {
			double a = 2 * Math.PI * i / N, sx = Math.sin(a), sz = Math.cos(a);
			for (int k = 0; k < 3; k++) {
				int r = 32 + k;
				gr[i * 3 + k] = g.y(o.getX() + (int) Math.round(r * sx), o.getZ() + (int) Math.round(r * sz)) - o.getY();
			}
		}
		int[] mid = new int[N];
		for (int i = 0; i < N; i++) mid[i] = gr[i * 3 + 1];
		int[] sorted = mid.clone();
		java.util.Arrays.sort(sorted);
		int t = sorted[N / 2];
		for (int i = 0; i < N; i++) {
			int best = 1;
			for (int k : new int[] {0, 2}) if (Math.abs(gr[i * 3 + k] - t) < Math.abs(gr[i * 3 + best] - t)) best = k;
			rc[i] = 32 + best;
		}
		// which gates open: south always, the water side always, east/west/north only where the ground outside meets the gate floor
		boolean[] open = new boolean[4];
		for (int gi = 0; gi < 4; gi++) {
			if (gi == 0 || gi == 2 && water == 3) {
				open[gi] = true;
				continue;
			}
			boolean ok = true;
			for (int u = 37; u <= 38 && ok; u++) for (int v = -2; v <= 2 && ok; v += 2) {
				int y = g.y(o.getX() + gx(gi, u, v), o.getZ() + gz(gi, u, v)) - o.getY();
				if (Math.abs(y) > 2) ok = false;
			}
			open[gi] = ok;
		}
		for (int i = 0; i < N; i++) {
			int ds = circ(i, 0);
			if (ds <= 5) rc[i] = 31;
			else if (ds <= 8) rc[i] = 32;
			for (int gi = 1; gi < 4; gi++) if (open[gi] && circ(i, 32 * gi) <= 4) rc[i] = 33;
		}
		for (int pass = 0; pass < 3; pass++)
			for (int i = 0; i < N; i++) rc[i] = Math.min(rc[i], Math.min(rc[(i + 1) % N], rc[(i + N - 1) % N]) + 1);
		for (int i = 0; i < N; i++) {
			double a = 2 * Math.PI * i / N;
			int y = g.y(o.getX() + (int) Math.round(rc[i] * Math.sin(a)), o.getZ() + (int) Math.round(rc[i] * Math.cos(a))) - o.getY();
			base[i] = Math.min(10, Math.max(0, y));
		}
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < N; i++) base[i] = Math.max(base[i], base[(i + N - 1) % N] - 1);
			for (int i = N - 1; i >= 0; i--) base[i] = Math.max(base[i], base[(i + 1) % N] - 1);
		}
		int[] gl = new int[4];
		for (int gi = 0; gi < 4; gi++) for (int k = -4; k <= 4; k++) gl[gi] = Math.max(gl[gi], base[Math.floorMod(32 * gi + k, N)]);
		// watch-fire cairn: the water-side corner with the higher ground, else the highest corner
		int bx = 31, bz = 31, best = Integer.MIN_VALUE;
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			if (water == 1 && sz < 0 || water == 3 && sz > 0) continue;
			int y = g.y(o.getX() + 31 * sx, o.getZ() + 31 * sz);
			if (y > best) {
				best = y;
				bx = 31 * sx;
				bz = 31 * sz;
			}
		}
		Ring r = new Ring(rc, base, open, gl, bx, bz, water);
		if (RINGS.size() > 64) RINGS.clear();
		RINGS.put(key, r);
		return r;
	}

	// gate frames: u outward along the gate axis, v across it
	public static int gx(int g, int u, int v) {
		return switch (g) {
			case 0 -> v;
			case 1 -> u;
			case 2 -> -v;
			default -> -u;
		};
	}

	public static int gz(int g, int u, int v) {
		return switch (g) {
			case 0 -> u;
			case 1 -> -v;
			case 2 -> -u;
			default -> v;
		};
	}

	public static int gu(int g, int x, int z) {
		return switch (g) {
			case 0 -> z;
			case 1 -> x;
			case 2 -> -z;
			default -> -x;
		};
	}

	public static int gv(int g, int x, int z) {
		return switch (g) {
			case 0 -> x;
			case 1 -> -z;
			case 2 -> -x;
			default -> z;
		};
	}

	static Direction out(int g) {
		return switch (g) {
			case 0 -> Direction.SOUTH;
			case 1 -> Direction.EAST;
			case 2 -> Direction.NORTH;
			default -> Direction.WEST;
		};
	}

	/** The open gate whose zone (passage, tower, lane) holds column (x, z), or -1. */
	static int gateZone(Ring r, int x, int z) {
		for (int g = 0; g < 4; g++) {
			if (!r.open[g]) continue;
			int u = gu(g, x, z), v = gv(g, x, z);
			if (Math.abs(v) <= 4 && u >= GATE_R[g] - 3 && u <= 36) return g;
		}
		return -1;
	}

	// ---- writer ---------------------------------------------------------------------------------------------------

	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** Unmapped local writer (the layout names its own blocks; the style palette would turn the throne into spruce). */
	static final class H {
		final BlockPos o;
		final CastleLayout.Placer p;

		H(BlockPos o, CastleLayout.Placer p) {
			this.o = o;
			this.p = p;
		}

		boolean in(int x, int y, int z) {
			return p.clip.isInside(o.getX() + x, o.getY() + y, o.getZ() + z);
		}

		boolean col(int x, int z) {
			int wx = o.getX() + x, wz = o.getZ() + z;
			return wx >= p.clip.minX() && wx <= p.clip.maxX() && wz >= p.clip.minZ() && wz <= p.clip.maxZ();
		}

		void s(int x, int y, int z, BlockState st) {
			p.setRaw(o.getX() + x, o.getY() + y, o.getZ() + z, st);
		}

		void s(int x, int y, int z, Block b) {
			s(x, y, z, b.defaultBlockState());
		}

		BlockState get(int x, int y, int z) {
			return p.level.getBlockState(new BlockPos(o.getX() + x, o.getY() + y, o.getZ() + z));
		}

		/** Ground (first air) at a local column, relative to the floor. */
		int g(int x, int z) {
			return p.ground == null ? 0 : p.ground.y(o.getX() + x, o.getZ() + z) - o.getY();
		}

		/** Air from y0 to y1 (only cells that are not air yet). */
		void carve(int x, int y0, int y1, int z) {
			if (!col(x, z)) return;
			for (int y = y0; y <= y1; y++) if (in(x, y, z) && !get(x, y, z).isAir()) s(x, y, z, AIR);
		}

		/** Fills air, fluid and replaceable cells from y down (at most 48) until solid ground. */
		void down(int x, int y, int z, BlockState st) {
			if (!col(x, z)) return;
			for (int k = 0; k < 48; k++) {
				if (!in(x, y - k, z)) return;
				BlockState c = get(x, y - k, z);
				if (!c.isAir() && c.getFluidState().isEmpty() && !c.canBeReplaced()) return;
				s(x, y - k, z, st);
			}
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
					for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) s(x, y, z, st);
		}

		void chest(int x, int y, int z, Direction f, net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot) {
			p.chest(o.getX() + x, o.getY() + y, o.getZ() + z, f, loot);
		}

		/** A 1-wide path cell: dirt path under standing height s, air above (s .. max(s + 3, ground - 1)), dirt down to solid. */
		void path(int x, int z, int s) {
			if (!col(x, z)) return;
			carve(x, s, Math.max(s + 3, g(x, z) + 1), z);
			s(x, s - 1, z, Blocks.DIRT_PATH);
			down(x, s - 2, z, Blocks.DIRT.defaultBlockState());
		}
	}

	static BlockState st(Block b, Direction f) {
		return CastleKit.stair(b, f, Half.BOTTOM);
	}

	static BlockState banner(DyeColor c, Direction f) {
		return Blocks.WALL_BANNER.pick(c).defaultBlockState().setValue(WallBannerBlock.FACING, f);
	}

	static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
	}

	static BlockState ladder(Direction f) {
		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f);
	}

	static BlockState log(int x, int z) {
		return CastleLayout.hash(x, 3, z) < 55 ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.DARK_OAK_LOG.defaultBlockState();
	}

	static void bed(H h, int headX, int z, Direction toHead, DyeColor c) {
		BlockState s = Blocks.BED.pick(c).defaultBlockState().setValue(BedBlock.FACING, toHead);
		h.s(headX, 0, z, s.setValue(BedBlock.PART, BedPart.HEAD));
		h.s(headX - toHead.getStepX(), 0, z - toHead.getStepZ(), s.setValue(BedBlock.PART, BedPart.FOOT));
	}

	// ---- WALL: rampart, palisade, gate towers, archer stages, beacon ------------------------------------------------

	static final int[] PROFILE = {1, 2, 3, 3, 2};

	static void rampart(H h, Ring r) {
		for (int x = -36; x <= 36; x++) for (int z = -36; z <= 36; z++) {
			if (!h.col(x, z)) continue;
			int rr = (int) Math.round(Math.sqrt(x * x + z * z));
			if (rr < 29 || rr > 36) continue;
			int i = bearing(x, z), d = rr - r.rc[i];
			if (rr == 29 && d < -2 || d > 2) continue;
			if (gateZone(r, x, z) >= 0) continue;
			int g = h.g(x, z);
			if (d <= -3) {
				// apron between the yard and the bank: floor level
				h.carve(x, 0, Math.max(g, 0) + 1, z);
				h.s(x, -1, z, CastleLayout.hash(x, 1, z) < 70 ? Blocks.GRASS_BLOCK : Blocks.COARSE_DIRT);
				h.down(x, -2, z, Blocks.DIRT.defaultBlockState());
				continue;
			}
			int b = r.base[i];
			boolean pal = d >= 1 && (dAt(r, x + 1, z) <= 0 || dAt(r, x - 1, z) <= 0 || dAt(r, x, z + 1) <= 0 || dAt(r, x, z - 1) <= 0);
			int t = pal ? b + 3 : b + PROFILE[d + 2];
			boolean revet = d == -2 && t > 3 || d >= 1 && !pal && g < t - 3;
			BlockState core = revet ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
			BlockState top;
			if (revet) top = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			else if (pal) top = Blocks.COARSE_DIRT.defaultBlockState();
			else if (d == 0) top = Blocks.DIRT_PATH.defaultBlockState();
			else top = CastleLayout.hash(x, 2, z) < 25 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
			h.carve(x, t, Math.max(g, t) + 1, z);
			h.s(x, t - 1, z, top);
			if (revet && d >= 1) {
				// f-castles2: the outer face is stone only for its top two courses; below it the bank is earth (no sheer grey cliff down the slope)
				h.s(x, t - 2, z, core);
				h.down(x, t - 3, z, Blocks.COARSE_DIRT.defaultBlockState());
			} else h.down(x, t - 2, z, core);
			if (pal) {
				BlockState lg = log(x, z);
				// w6-castles: timber hoarding along the palisade head (plank gallery between log posts under a slab roof, fence tips on the posts)
				boolean post = ((x + z) % 3 + 3) % 3 == 0;
				for (int y = t; y <= t + 3; y++) h.s(x, y, z, y >= t + 2 && !post ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : lg);
				h.s(x, t + 4, z, Blocks.SPRUCE_SLAB);
				if (post) h.s(x, t + 5, z, Blocks.SPRUCE_FENCE);
			}
		}
		for (int gi = 0; gi < 4; gi++) if (r.open[gi]) gate(h, r, gi);
		for (int s = -1; s <= 1; s += 2) stage(h, r, s);
		beacon(h, r);
	}

	/** Gate tower (two storeys, overhanging gallery, gable roof) over a floor-level passage with fence gates. */
	static void gate(H h, Ring r, int g) {
		int rg = GATE_R[g], gl = r.gl[g];
		Direction o = out(g), in = o.getOpposite();
		boolean south = g == 0;
		// passage (the south one stops at z 30: the road owns z >= 31)
		for (int u = rg - 3; u <= rg + 2; u++) for (int v = -2; v <= 2; v++) {
			if (south && u >= rg) continue;
			int x = gx(g, u, v), z = gz(g, u, v);
			h.carve(x, 0, u == rg - 3 ? Math.max(h.g(x, z), 4) + 1 : 4, z);
			h.s(x, -1, z, Blocks.DIRT_PATH);
			h.down(x, -2, z, Blocks.DIRT.defaultBlockState());
		}
		// the yard strip in front of the tower (floor level; the outside ladder stands on it)
		for (int v = -4; v <= 4; v += 1) {
			if (Math.abs(v) < 3) continue;
			int x = gx(g, rg - 3, v), z = gz(g, rg - 3, v);
			h.carve(x, 0, Math.max(h.g(x, z), 0) + 1, z);
			h.s(x, -1, z, Blocks.GRASS_BLOCK);
			h.down(x, -2, z, Blocks.DIRT.defaultBlockState());
		}
		for (int v = -2; v <= 2; v++)
			h.s(gx(g, rg - 1, v), 0, gz(g, rg - 1, v), Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, o).setValue(FenceGateBlock.OPEN, true));
		// lane outside (east, north, west): towards the ground with a step of at most 1; the water gate stays at the floor for the shore road
		if (!south) for (int v = -2; v <= 2; v++) {
			int x = gx(g, rg + 3, v), z = gz(g, rg + 3, v);
			int s = g == 2 && r.water == 3 ? 0 : Math.max(-1, Math.min(1, h.g(gx(g, rg + 4, v), gz(g, rg + 4, v))));
			h.path(x, z, s);
		}
		// carve the tower volume above the bank
		for (int u = rg - 4; u <= rg + 4; u++) for (int v = -6; v <= 6; v++) {
			int x = gx(g, u, v), z = gz(g, u, v);
			if (Math.abs(v) <= 4 && u >= rg - 2 && u <= rg + 2) h.carve(x, 5, Math.max(h.g(x, z), 16 + gl) + 1, z);
			else h.carve(x, 9 + gl, Math.max(h.g(x, z), 16 + gl) + 1, z);
		}
		// storey 1: side walls on foundations, plank floor at 5
		for (int u = rg - 2; u <= rg + 2; u++) for (int v = -4; v <= 4; v++) {
			int x = gx(g, u, v), z = gz(g, u, v);
			boolean side = Math.abs(v) >= 3, corner = (u == rg - 2 || u == rg + 2) && Math.abs(v) >= 3;
			if (side) {
				BlockState w = corner ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState();
				for (int y = -1; y <= 4; y++) h.s(x, y, z, w);
				h.down(x, -2, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			}
			h.s(x, 5, z, Blocks.SPRUCE_PLANKS);
			// storey 2 shell
			boolean edge = u == rg - 2 || u == rg + 2 || Math.abs(v) == 4;
			for (int y = 6; y <= 8 + gl; y++) h.s(x, y, z, edge ? (corner ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState()) : AIR);
		}
		// arrow slits, the doorway from the outside ladder
		for (int v = -2; v <= 2; v += 4) {
			h.s(gx(g, rg + 2, v), 7, gz(g, rg + 2, v), AIR);
			h.s(gx(g, rg - 2, -v), 7, gz(g, rg - 2, -v), AIR);
		}
		h.s(gx(g, rg - 2, 3), 6, gz(g, rg - 2, 3), AIR);
		h.s(gx(g, rg - 2, 3), 7, gz(g, rg - 2, 3), AIR);
		for (int y = 0; y <= 5; y++) h.s(gx(g, rg - 3, 3), y, gz(g, rg - 3, 3), ladder(in));
		// gallery deck, rail and corner posts
		int dk = 9 + gl;
		for (int u = rg - 3; u <= rg + 3; u++) for (int v = -5; v <= 5; v++) {
			int x = gx(g, u, v), z = gz(g, u, v);
			h.s(x, dk, z, u == rg + 1 && v == 3 ? AIR : Blocks.SPRUCE_PLANKS.defaultBlockState());
			boolean rim = u == rg - 3 || u == rg + 3 || Math.abs(v) == 5, cor = (u == rg - 3 || u == rg + 3) && Math.abs(v) == 5;
			if (rim) h.s(x, dk + 1, z, cor ? Blocks.SPRUCE_LOG : Blocks.SPRUCE_FENCE);
		}
		for (int y = 6; y <= dk; y++) h.s(gx(g, rg + 1, 3), y, gz(g, rg + 1, 3), ladder(in));
		// gable roof, ridge across the gate axis, gable ends filled
		for (int u = rg - 4; u <= rg + 4; u++) for (int v = -6; v <= 6; v++) {
			int x = gx(g, u, v), z = gz(g, u, v), du = u - rg, y = dk + 2 + (4 - Math.abs(du));
			h.s(x, y, z, du == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : st(Blocks.SPRUCE_STAIRS, du < 0 ? o : in));
			if (Math.abs(v) == 5 && Math.abs(du) <= 3) for (int yy = dk + 2; yy < y; yy++) h.s(x, yy, z, Blocks.DARK_OAK_PLANKS);
		}
		// w6-castles: a steep spruce spire needle over the ridge (log shaft, stair flare, fence tip) so the gate tower reads as a spire
		for (int y = dk + 7; y <= dk + 10; y++) h.s(gx(g, rg, 0), y, gz(g, rg, 0), Blocks.SPRUCE_LOG);
		for (int y = dk + 11; y <= dk + 14; y++) h.s(gx(g, rg, 0), y, gz(g, rg, 0), Blocks.DARK_OAK_FENCE);
		h.s(gx(g, rg, 0), 4, gz(g, rg, 0), lantern(true));
		h.s(gx(g, rg + 3, 0), 7, gz(g, rg + 3, 0), banner((g & 1) == 0 ? DyeColor.GREEN : DyeColor.BROWN, o));
	}

	/** Archer stage behind the south palisade: deck at y 8 so the archers posted at (+-12, 9, 29) stand on it; posts and a ladder. */
	static void stage(H h, Ring r, int s) {
		for (int x = 11; x <= 13; x++) for (int z = 27; z <= 29; z++) {
			int lx = s * x;
			h.carve(lx, 9, Math.max(10, h.g(lx, z) + 4), z);
			h.s(lx, 8, z, Blocks.SPRUCE_PLANKS);
			boolean post = (x == 11 || x == 13) && (z == 27 || z == 29) || x == 12 && z == 27;
			if (post) for (int y = 7; y >= -1; y--) {
				if (!h.in(lx, y, z)) break;
				BlockState c = h.get(lx, y, z);
				if (!c.isAir() && c.getFluidState().isEmpty() && !c.canBeReplaced()) break;
				h.s(lx, y, z, Blocks.SPRUCE_LOG);
			}
		}
		for (int y = 0; y <= 8; y++) h.s(s * 12, y, 26, ladder(Direction.NORTH));
	}

	/** Watch-fire cairn on a corner outside the ring (the water side when there is one). */
	static void beacon(H h, Ring r) {
		int cx = r.beaconX, cz = r.beaconZ, b = Integer.MIN_VALUE;
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) b = Math.max(b, h.g(cx + x, cz + z));
		BlockState mc = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
			h.s(cx + x, b, cz + z, mc);
			h.down(cx + x, b - 1, cz + z, mc);
			h.carve(cx + x, b + 1, b + 5, cz + z);
		}
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
			h.s(cx + x, b + 1, cz + z, mc);
			h.s(cx + x, b + 2, cz + z, x == 0 || z == 0 ? mc : Blocks.COBBLESTONE.defaultBlockState());
		}
		h.s(cx, b + 3, cz, Blocks.CAMPFIRE);
		for (Direction d : Direction.Plane.HORIZONTAL) h.s(cx + d.getStepX(), b + 3, cz + d.getStepZ(), Blocks.IRON_BARS);
	}

	// ---- CASTLE: yard, longhall, longhouses, hof, forge, stables -----------------------------------------------------

	static void court(H h, Ring r) {
		// yard pad: grass, podzol and trodden earth on the floor, earth under it, nothing above
		for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) {
			if (!h.col(x, z)) continue;
			int rr = (int) Math.round(Math.sqrt(x * x + z * z));
			if (rr > 29 || rr == 29 && dAt(r, x, z) > -3) continue;
			int hs = CastleLayout.hash(x, 0, z);
			h.carve(x, 0, Math.max(h.g(x, z), 0) + 1, z);
			h.s(x, -1, z, hs < 62 ? Blocks.GRASS_BLOCK : hs < 80 ? Blocks.PODZOL : Blocks.COARSE_DIRT);
			h.down(x, -2, z, Blocks.DIRT.defaultBlockState());
		}
		// streets: gate to hall door, the cross street to the side doors and gates, round the hall's back to the north gate
		for (int x = -2; x <= 2; x++) for (int z = 5; z <= 28; z++) street(h, x, z);
		for (int z = -1; z <= 1; z++) for (int x = 5; x <= 29; x++) {
			street(h, x, z);
			street(h, -x, z);
		}
		for (int x = -2; x <= 2; x++) for (int z = -29; z <= -27; z++) street(h, x, z);
		longhall(h);
		longhouse(h, -17, -12, 1, DyeColor.BROWN);
		longhouse(h, -17, 12, 0, DyeColor.GREEN);
		longhouse(h, 17, -12, 2, DyeColor.GREEN);
		longhouse(h, 17, 12, 3, DyeColor.BROWN);
		hof(h);
		yardWorks(h);
	}

	static void street(H h, int x, int z) {
		if (!h.col(x, z)) return;
		h.s(x, -1, z, CastleLayout.hash(x, 5, z) < 80 ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT);
	}

	/** Half-width of the longhall at slice t (bowed: 5 in the middle, 4 then 3 towards the gable ends). */
	static int hallHw(int t) {
		int a = Math.abs(t);
		return a <= 9 ? 5 : a <= 12 ? 4 : a <= 15 ? 3 : -1;
	}

	static int houseHw(int t) {
		int a = Math.abs(t);
		return a <= 7 ? 3 : a <= 10 ? 2 : -1;
	}

	/**
	 * A bow-sided building along z around (cx, cz), half-length len, walls y 0..top, roof eaves {@code over} past the walls; the hall gets a
	 * shingle hogback, the houses a turf roof (moss on spruce eaves). Returns nothing; doors and furniture are set by the callers.
	 */
	static void bowHouse(H h, int cx, int cz, int len, java.util.function.IntUnaryOperator hw, int top, int over, boolean turf, BlockState wall, BlockState floor) {
		int y0 = top + 1 - over;
		BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int t = -len; t <= len; t++) {
			int w = hw.applyAsInt(t), wp = hw.applyAsInt(t + 1), wm = hw.applyAsInt(t - 1);
			for (int a = -w; a <= w; a++) {
				int x = cx + a, z = cz + t;
				boolean isWall = Math.abs(a) == w || Math.abs(t) == len || Math.abs(a) > wp || Math.abs(a) > wm;
				int roofY = y0 + (w + over) - Math.abs(a);
				h.carve(x, 0, roofY - 1, z);
				h.s(x, -1, z, isWall ? Blocks.SPRUCE_PLANKS.defaultBlockState() : floor);
				h.down(x, -2, z, Blocks.DIRT.defaultBlockState());
				if (isWall) {
					boolean isPost = Math.abs(t) == len && Math.abs(a) == w || Math.abs(a) == w && Math.floorMod(t, 3) == 0;
					for (int y = 0; y <= top; y++) h.s(x, y, z, isPost ? post : wall);
					if (Math.abs(t) == len) for (int y = top + 1; y < roofY; y++) h.s(x, y, z, Blocks.DARK_OAK_PLANKS);
				}
				// tie band where the roof steps down towards the ends (closes the hogback step)
				if ((w > wp || w > wm) && Math.abs(a) < w) h.s(x, roofY - 1, z, Blocks.DARK_OAK_PLANKS);
			}
		}
		for (int t = -len - 1; t <= len + 1; t++) {
			int tc = Math.max(-len, Math.min(len, t)), wr = hw.applyAsInt(tc) + over;
			for (int a = -wr; a <= wr; a++) {
				int x = cx + a, z = cz + t, y = y0 + wr - Math.abs(a);
				BlockState s;
				if (a == 0) s = turf ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState();
				else if (turf && Math.abs(a) < wr) s = Blocks.MOSS_BLOCK.defaultBlockState();
				else s = st(Blocks.SPRUCE_STAIRS, a < 0 ? Direction.EAST : Direction.WEST);
				h.carve(x, y, y + 1, z);
				h.s(x, y, z, s);
			}
		}
		// bargeboard finials at both gable ends
		for (int e = -1; e <= 1; e += 2) {
			int z = cz + e * (len + 1), ry = y0 + hw.applyAsInt(e * len) + over;
			h.s(cx - 1, ry + 1, z, st(Blocks.DARK_OAK_STAIRS, Direction.WEST));
			h.s(cx + 1, ry + 1, z, st(Blocks.DARK_OAK_STAIRS, Direction.EAST));
			h.s(cx, ry + 1, z, Blocks.DARK_OAK_FENCE);
			h.s(cx, ry + 2, z, Blocks.DARK_OAK_FENCE);
			if (!turf) h.s(cx, ry + 3, z, lantern(false));
		}
	}

	/** The jarl's longhall: z -26..4 around (0, -11), the high seat on the dais at the north end, door to the yard in the south gable. */
	static void longhall(H h) {
		int cz = -11;
		bowHouse(h, 0, cz, 15, HrafnholtFort::hallHw, 3, 2, false, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
		// doors: south gable (3 wide, 3 high), the side doors on the cross street
		for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) h.s(x, y, 4, AIR);
		for (int s = -1; s <= 1; s += 2) for (int y = 0; y <= 1; y++) h.s(s * 4, y, 0, AIR);
		// svalgang: the outer post gallery under the eaves; hearth fires under it where the court lights braziers
		for (int t = -9; t <= 9; t += 3) for (int s = -1; s <= 1; s += 2) for (int y = 0; y <= 1; y++) h.s(s * 7, y, cz + t, Blocks.SPRUCE_LOG);
		h.s(-6, 0, -4, Blocks.CAMPFIRE);
		h.s(6, 0, -4, Blocks.CAMPFIRE);
		// long benches along the walls, two hearths
		for (int t = -6; t <= 6; t++) {
			h.s(-4, 0, cz + t, st(Blocks.SPRUCE_STAIRS, Direction.WEST));
			h.s(4, 0, cz + t, st(Blocks.SPRUCE_STAIRS, Direction.EAST));
		}
		for (int s = -1; s <= 1; s += 2) {
			h.s(s * 3, -1, -6, Blocks.MOSSY_COBBLESTONE);
			h.s(s * 3, 0, -6, Blocks.CAMPFIRE);
		}
		// dais, high seat (the court's throne: dark oak stairs at (0, 1, -24)), high-seat pillars, banner, the jarl's chest
		for (int x = -2; x <= 2; x++) for (int z = -25; z <= -23; z++) h.s(x, 0, z, Blocks.DARK_OAK_PLANKS);
		h.s(0, 1, -24, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		for (int s = -1; s <= 1; s += 2) for (int y = 1; y <= 3; y++) h.s(s, y, -24, Blocks.SPRUCE_LOG);
		h.s(0, 3, -25, banner(DyeColor.GREEN, Direction.SOUTH));
		h.chest(-2, 1, -25, Direction.SOUTH, CastleLayout.LOOT_CACHE);
		// the dungeon hatch (the DUNGEON part's ladder rises to it)
		h.s(-3, -1, -15, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.FACING, Direction.NORTH));
		// lanterns hung from the ridge
		h.s(0, 8, -15, lantern(true));
		h.s(0, 8, -5, lantern(true));
		h.s(0, 7, -22, lantern(true));
		// banners flanking the south door
		h.s(-2, 2, 5, banner(DyeColor.GREEN, Direction.SOUTH));
		h.s(2, 2, 5, banner(DyeColor.BROWN, Direction.SOUTH));
		// balcony stages for the crossbowmen posted at (+-9, 5, -17)
		for (int s = -1; s <= 1; s += 2) {
			for (int x = 8; x <= 10; x++) for (int z = -18; z <= -16; z++) {
				int lx = s * x;
				h.s(lx, 4, z, Blocks.SPRUCE_PLANKS);
				h.carve(lx, 5, 6, z);
				if ((x == 8 || x == 10) && (z == -18 || z == -16) || x == 9 && z == -16) for (int y = 0; y <= 3; y++) h.s(lx, y, z, Blocks.SPRUCE_LOG);
				if (!(x == 9 && (z == -17 || z == -16))) h.s(lx, 5, z, Blocks.SPRUCE_FENCE);
			}
			for (int y = 0; y <= 4; y++) h.s(s * 9, y, -15, ladder(Direction.SOUTH));
		}
	}

	/** A bowed turf-roofed longhouse along z at (cx, cz), door on the side facing the hall; kind 0 warriors, 1 kin, 2 guests, 3 store. */
	static void longhouse(H h, int cx, int cz, int kind, DyeColor c) {
		bowHouse(h, cx, cz, 10, HrafnholtFort::houseHw, 2, 1, true, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState());
		int side = cx < 0 ? 1 : -1, dx = cx + side * 3, back = cx - side * 2, foot = cx - side;
		for (int y = 0; y <= 1; y++) h.s(dx, y, cz, AIR);
		// banner on the gable end that faces the cross street
		int ez = cz < 0 ? cz + 11 : cz - 11;
		h.s(cx, 2, ez, banner(c, cz < 0 ? Direction.SOUTH : Direction.NORTH));
		Direction toBack = side > 0 ? Direction.WEST : Direction.EAST;
		switch (kind) {
			case 0 -> {
				for (int t : new int[] {-6, -4, -2, 2, 4, 6}) bed(h, back, cz + t, toBack, DyeColor.GREEN);
				h.s(foot + side, 0, cz - 7, Blocks.BARREL);
				h.s(foot + side, 0, cz + 7, Blocks.BARREL);
			}
			case 1 -> {
				for (int t : new int[] {-5, -3}) bed(h, back, cz + t, toBack, DyeColor.BROWN);
				h.s(back, 0, cz + 3, Blocks.LOOM);
				h.s(back, 0, cz + 5, Blocks.BARREL);
				h.s(back, 0, cz + 6, Blocks.BARREL);
			}
			case 2 -> {
				for (int t : new int[] {-6, -4, 4, 6}) bed(h, back, cz + t, toBack, DyeColor.WHITE);
				h.s(back, 0, cz, Blocks.CARTOGRAPHY_TABLE);
			}
			default -> {
				h.chest(back, 0, cz, side > 0 ? Direction.EAST : Direction.WEST, CastleLayout.LOOT_CACHE);
				for (int t : new int[] {-7, -6, -5, 5, 6, 7}) {
					h.s(back, 0, cz + t, Blocks.BARREL);
					h.s(foot, 0, cz + t, t > 0 ? Blocks.HAY_BLOCK : Blocks.BARREL);
				}
			}
		}
		h.s(cx, 5, cz, lantern(true));
	}

	/** The hof (stave temple): stone plinth with steps, two stepped roofs, a belfry and a shingled spire, at (6, 11). */
	static void hof(H h) {
		CastleKit.W w = new CastleKit.W(new CastleLayout.L(h.o, h.p), false);
		for (int x = 2; x <= 10; x++) for (int z = 7; z <= 15; z++) {
			h.carve(x, 0, 22, z);
			boolean ex = x == 2 || x == 10, ez = z == 7 || z == 15;
			BlockState s;
			if (ex && ez) s = Blocks.STONE_BRICKS.defaultBlockState();
			else if (x == 2) s = st(Blocks.STONE_BRICK_STAIRS, Direction.EAST);
			else if (x == 10) s = st(Blocks.STONE_BRICK_STAIRS, Direction.WEST);
			else if (z == 7) s = st(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH);
			else if (z == 15) s = st(Blocks.STONE_BRICK_STAIRS, Direction.NORTH);
			else s = CastleLayout.hash(x, 0, z) < 30 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
			h.s(x, 0, z, s);
			h.s(x, -1, z, Blocks.STONE_BRICKS);
		}
		for (int x = 3; x <= 9; x++) for (int z = 8; z <= 14; z++) {
			boolean edge = x == 3 || x == 9 || z == 8 || z == 14, corner = (x == 3 || x == 9) && (z == 8 || z == 14);
			if (edge) for (int y = 1; y <= 4; y++) h.s(x, y, z, corner || x == 6 || z == 11 ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_PLANKS);
			h.s(x, 5, z, Blocks.DARK_OAK_PLANKS);
		}
		for (int y = 1; y <= 2; y++) h.s(3, y, 11, AIR);
		h.s(2, 3, 11, banner(DyeColor.GREEN, Direction.WEST));
		h.s(8, 1, 11, Blocks.MOSSY_COBBLESTONE);
		h.s(8, 2, 11, lantern(false));
		BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		// one steep spruce gable over the nave (ridge along z, overhang 1), crossed bargeboard finials at both gable ends, lanterns on the eave posts
		int ridge = CastleKit.gable(w, 3, 8, 9, 14, 5, 1, false, Blocks.SPRUCE_STAIRS, dark, dark, false, null);
		for (int z : new int[] {7, 15}) {
			h.s(5, ridge + 1, z, st(Blocks.DARK_OAK_STAIRS, Direction.EAST));
			h.s(7, ridge + 1, z, st(Blocks.DARK_OAK_STAIRS, Direction.WEST));
			h.s(6, ridge + 1, z, Blocks.DARK_OAK_FENCE);
			h.s(6, ridge + 2, z, Blocks.DARK_OAK_FENCE);
			h.s(6, ridge + 3, z, lantern(false));
		}
		// w6-castles: stave tower spire on the hof (log shaft over the nave ridge, flared spruce stair skirt, fence needle)
		for (int y = ridge + 1; y <= ridge + 7; y++) h.s(6, y, 11, Blocks.SPRUCE_LOG);
		for (Direction d : Direction.Plane.HORIZONTAL) h.s(6 + d.getStepX(), ridge + 4, 11 + d.getStepZ(), st(Blocks.SPRUCE_STAIRS, d.getOpposite()));
		for (int y = ridge + 8; y <= ridge + 11; y++) h.s(6, y, 11, Blocks.DARK_OAK_FENCE);
		for (int[] c : new int[][] {{2, 7}, {10, 7}, {2, 15}, {10, 15}}) {
			h.s(c[0], 6, c[1], Blocks.DARK_OAK_FENCE);
			h.s(c[0], 7, c[1], lantern(false));
		}
	}

	/** Forge, stables, holmgang posts, runestone and the gate fires. */
	static void yardWorks(H h) {
		// forge (open smithy under a slab roof; no furnace: nothing ticks)
		for (int x = 9; x <= 12; x++) for (int z = -9; z <= -4; z++) {
			h.carve(x, 0, 4, z);
			h.s(x, 3, z, CastleKit.slab(Blocks.SPRUCE_SLAB, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
		}
		for (int[] p : new int[][] {{9, -9}, {12, -9}, {9, -4}, {12, -4}}) for (int y = 0; y <= 2; y++) h.s(p[0], y, p[1], Blocks.SPRUCE_LOG);
		for (int x = 10; x <= 12; x++) h.s(x, -1, -8, Blocks.POLISHED_BLACKSTONE_BRICKS);
		h.s(11, 0, -8, Blocks.POLISHED_BLACKSTONE_BRICKS);
		h.s(11, 1, -8, lantern(false));
		h.s(11, 0, -6, Blocks.ANVIL);
		h.s(10, 0, -6, Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
		h.s(12, 0, -7, Blocks.SMITHING_TABLE);
		h.s(12, 0, -6, Blocks.BARREL);
		h.chest(10, 0, -8, Direction.SOUTH, CastleLayout.LOOT_ARMORY);
		// stables: fenced pen with a lean-to over hay
		for (int x = -13; x <= -9; x++) for (int z = 18; z <= 23; z++) {
			boolean edge = x == -13 || x == -9 || z == 18 || z == 23;
			if (edge) h.s(x, 0, z, Blocks.SPRUCE_FENCE);
			if (x <= -11) h.s(x, 3, z, CastleKit.slab(Blocks.SPRUCE_SLAB, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
		}
		for (int z : new int[] {18, 23}) for (int x : new int[] {-13, -11}) for (int y = 0; y <= 2; y++) h.s(x, y, z, Blocks.SPRUCE_LOG);
		h.s(-9, 0, 20, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
		h.s(-12, 0, 22, Blocks.HAY_BLOCK);
		h.s(-12, 0, 21, Blocks.HAY_BLOCK);
		h.s(-12, 1, 22, Blocks.HAY_BLOCK);
		h.s(-12, 0, 19, Blocks.WATER_CAULDRON);
		// holmgang posts and the runestone (the thing stone)
		for (int[] p : new int[][] {{-12, 5}, {-4, 5}, {-12, 10}, {-4, 10}}) {
			h.s(p[0], 0, p[1], Blocks.SPRUCE_FENCE);
			h.s(p[0], 1, p[1], Blocks.SPRUCE_FENCE);
		}
		h.s(-10, 0, -8, Blocks.MOSSY_COBBLESTONE);
		h.s(-10, 1, -8, Blocks.CHISELED_STONE_BRICKS);
		h.s(-10, 2, -8, Blocks.MOSSY_STONE_BRICK_WALL);
		// gate fires (the court lights braziers at (+-6, 0, 20))
		h.s(-6, 0, 20, Blocks.CAMPFIRE);
		h.s(6, 0, 20, Blocks.CAMPFIRE);
	}
}
