package dev.elsewhere.kingdom_court.world;

import dev.elsewhere.kingdom_court.CourtContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Block layout of the realm, in a local frame around the site (x east, z south, y 0 = floor level, gate on the south).
 * Castle Aldermark: 61x61 curtain wall with 4 corner towers and a gatehouse, the keep (throne hall with dais, throne,
 * banners and balconies; solar above; dungeon below), barracks, armory, chapel, stables, training yard, bell and
 * braziers. Two walled market towns (Lowmarket west, Eastmarket east) with stalls, houses, a tavern and a well, joined
 * by a road. Every write is clipped to the chunk being generated, so worldgen and {@link CourtWorld#buildNow} agree.
 */
public final class CastleLayout {
	private CastleLayout() {}

	/**
	 * Realm parts. 3.0 appends WALL and the KIT_* parts; saved 2.x starts never contain them, so their castles finish exactly as before. The kit
	 * parts must come after the base parts (declaration order) because they dress what the base built in the same chunk.
	 */
	public enum Part { CASTLE, DUNGEON, TOWN_WEST, TOWN_EAST, ROAD, WALL, KIT_CASTLE, KIT_TOWN_WEST, KIT_TOWN_EAST }

	/**
	 * 3.0 terrain hook for new ("fv" 2) realm starts, supplied by the generation lane (INTEGRATION-REQUESTS, 09:52). {@link #y} is the world y of
	 * the first air above generator terrain at a world column; it must be deterministic (generator HeightField, never the WorldGenLevel), so
	 * every chunk computes the same walls and roads. {@code Placer.ground == null} keeps the frozen 2.1 path.
	 */
	public interface Ground {
		int y(int worldX, int worldZ);

		/** World floor y of another part of this realm (roads join the towns at their floors); default: the given fallback. */
		default int floor(Part part, int fallback) { return fallback; }
	}

	public static final int TOWN_DX = 88, TOWN_DZ = 8;

	public static BoundingBox box(Part p, BlockPos o) {
		return switch (p) {
			case CASTLE -> new BoundingBox(o.getX() - 34, o.getY() - 1, o.getZ() - 34, o.getX() + 34, o.getY() + 22, o.getZ() + 34);
			case DUNGEON -> new BoundingBox(o.getX() - 8, o.getY() - 7, o.getZ() - 20, o.getX() - 2, o.getY() - 2, o.getZ() - 14);
			case TOWN_WEST -> townBox(o, -TOWN_DX);
			case TOWN_EAST -> townBox(o, TOWN_DX);
			case ROAD -> new BoundingBox(o.getX() - 90, o.getY() - 1, o.getZ() + 30, o.getX() + 90, o.getY() + 4, o.getZ() + 43);
			// 3.0 parts. Same floor (minY) as the castle so a beard_box start never carves deeper; x/z +-36 for spire eaves and hoardings.
			case WALL, KIT_CASTLE -> new BoundingBox(o.getX() - 36, o.getY() - 1, o.getZ() - 36, o.getX() + 36, o.getY() + 22, o.getZ() + 36);
			case KIT_TOWN_WEST -> townBox(o, -TOWN_DX);
			case KIT_TOWN_EAST -> townBox(o, TOWN_DX);
		};
	}

	static BoundingBox townBox(BlockPos o, int dx) {
		return new BoundingBox(o.getX() + dx - 24, o.getY() - 1, o.getZ() + TOWN_DZ - 24, o.getX() + dx + 24, o.getY() + 10, o.getZ() + TOWN_DZ + 24);
	}

	// ---- named places (world coordinates) used by the garrison and daily life ---------------------------------------

	public static BlockPos at(BlockPos o, int x, int y, int z) {
		BlockPos a = AzmarCastle.place(o, x, y, z); // s-layout-azmar: an Azmar fv 2 realm moves its named places into its own layout
		return a != null ? a : o.offset(x, y, z);
	}

	public static BlockPos throne(BlockPos o) { return at(o, 0, 1, -24); }
	public static BlockPos throneFront(BlockPos o) { return at(o, 0, 0, -21); }
	public static BlockPos hall(BlockPos o) { return at(o, 0, 0, -15); }
	public static BlockPos gateInside(BlockPos o) { return at(o, 0, 0, 24); }
	public static BlockPos yard(BlockPos o) { return at(o, -8, 0, 8); }
	public static BlockPos barracks(BlockPos o) { return at(o, -22, 0, 4); }
	public static BlockPos stables(BlockPos o) { return at(o, -14, 0, 20); }
	public static BlockPos bell(BlockPos o) { return at(o, 6, 0, 14); }
	/** The town square (centre). fv 2 realms (3.0 G4) put each town on its own floor; fv 1 realms keep the castle floor (townFloor falls back to it). */
	public static BlockPos town(BlockPos o, boolean west) {
		BlockPos t = at(o, west ? -TOWN_DX : TOWN_DX, 0, TOWN_DZ);
		return new BlockPos(t.getX(), CourtWorld.townFloor(o, west), t.getZ());
	}
	public static BlockPos balcony(BlockPos o, boolean west) { return at(o, west ? -9 : 9, 5, -17); }
	public static BlockPos wallTop(BlockPos o, boolean west) { return at(o, west ? -12 : 12, 9, 29); }

	public static List<BlockPos> yardLoop(BlockPos o) {
		return List.of(at(o, -10, 0, 6), at(o, 10, 0, 6), at(o, 10, 0, 16), at(o, -10, 0, 16));
	}

	public static List<BlockPos> gates(BlockPos o) {
		List<BlockPos> l = new ArrayList<>();
		for (int x = -2; x <= 2; x++) l.add(at(o, x, 0, 30));
		return l;
	}

	public static List<BlockPos> braziers(BlockPos o) {
		return List.of(at(o, -6, 0, 20), at(o, 6, 0, 20), at(o, -6, 0, -4), at(o, 6, 0, -4), at(o, -3, 0, -6), at(o, 3, 0, -6));
	}

	public static final ResourceKey<LootTable> LOOT_CACHE = ResourceKey.create(Registries.LOOT_TABLE, CourtContent.id("chests/kingdom_cache"));
	public static final ResourceKey<LootTable> LOOT_ARMORY = ResourceKey.create(Registries.LOOT_TABLE, CourtContent.id("chests/kingdom_armory"));
	public static final ResourceKey<LootTable> LOOT_DUNGEON = ResourceKey.create(Registries.LOOT_TABLE, CourtContent.id("chests/kingdom_dungeon"));
	public static final ResourceKey<LootTable> LOOT_STALL = ResourceKey.create(Registries.LOOT_TABLE, CourtContent.id("chests/kingdom_market_stall"));

	// ---- the writer ---------------------------------------------------------------------------------------------

	/** Clipped block writer. */
	public static final class Placer {
		final WorldGenLevel level;
		final BoundingBox clip;
		public int placed;
		/** The faction's castle look (block replacement map), or null for Aldermark's. */
		public CastleStyles.@org.jspecify.annotations.Nullable Style style;
		/** 3.0 terrain fit (fv 2 starts only, set by the generation lane); null = the frozen 2.1 box layout. */
		public @org.jspecify.annotations.Nullable Ground ground;

		public Placer(WorldGenLevel level, BoundingBox clip) {
			this.level = level;
			this.clip = clip;
		}

		// s-throne (B9): every write passes Valuables.safe, so no realm path (fv 1/2, saved starts, any kit or fit switch) places a valuable block
		void set(int x, int y, int z, BlockState s) {
			if (!clip.isInside(x, y, z)) return;
			level.setBlock(new BlockPos(x, y, z), Valuables.safe(style == null ? s : style.map(s)), Block.UPDATE_CLIENTS);
			placed++;
		}

		/** Unmapped write (extra pieces already name their blocks). */
		void setRaw(int x, int y, int z, BlockState s) {
			if (!clip.isInside(x, y, z)) return;
			level.setBlock(new BlockPos(x, y, z), Valuables.safe(s), Block.UPDATE_CLIENTS);
			placed++;
		}

		/** Skirt write: only over natural terrain or air (never a wall). */
		void setSkirt(int x, int y, int z, BlockState s) {
			if (!clip.isInside(x, y, z) || !CastleKit.natural(level.getBlockState(new BlockPos(x, y, z)))) return;
			setRaw(x, y, z, s);
		}

		void setIfAir(int x, int y, int z, BlockState s) {
			if (!clip.isInside(x, y, z)) return;
			BlockPos p = new BlockPos(x, y, z);
			if (!level.getBlockState(p).isAir() && level.getBlockState(p).getFluidState().isEmpty()) return;
			level.setBlock(p, Valuables.safe(style == null ? s : style.map(s)), Block.UPDATE_CLIENTS);
			placed++;
		}

		void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> loot) {
			if (!clip.isInside(x, y, z)) return;
			BlockPos p = new BlockPos(x, y, z);
			level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing), Block.UPDATE_CLIENTS);
			RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(p.asLong()), p, loot);
			placed++;
		}
	}

	/** Local-frame helper around an origin. */
	static final class L {
		final BlockPos o;
		final Placer p;
		/** Vertical shift of b()/fill()/shell() writes (3.0 fit: a tower raised onto higher ground); always 0 on the 2.1 path. */
		int dy;

		L(BlockPos o, Placer p) {
			this.o = o;
			this.p = p;
		}

		void b(int x, int y, int z, BlockState s) {
			p.set(o.getX() + x, o.getY() + y + dy, o.getZ() + z, s);
		}

		/** 3.0 fit: ground (first air) at local column (x, z), relative to this part's floor; 0 without a ground hook. */
		int gy(int x, int z) {
			return p.ground == null ? 0 : p.ground.y(o.getX() + x, o.getZ() + z) - o.getY();
		}

		void b(int x, int y, int z, Block s) {
			b(x, y, z, s.defaultBlockState());
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
					for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, s);
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block s) {
			fill(x1, y1, z1, x2, y2, z2, s.defaultBlockState());
		}

		void fillIfAir(int x1, int y1, int z1, int x2, int y2, int z2, Block s) {
			BlockState st = s.defaultBlockState();
			for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++)
				p.setIfAir(o.getX() + x, o.getY() + y, o.getZ() + z, st);
		}

		/** Fills air and fluid under (x1..x2, z1..z2) from y0 down (at most 48) until solid ground, so nothing floats over a drop or cave. */
		void foundation(int x1, int z1, int x2, int z2, int y0, Block s) {
			BlockState st = s.defaultBlockState();
			for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
				for (int dy = 0; dy < 48; dy++) {
					int wx = o.getX() + x, wy = o.getY() + y0 - dy, wz = o.getZ() + z;
					if (!p.clip.isInside(wx, wy, wz)) break;
					BlockState cur = p.level.getBlockState(new BlockPos(wx, wy, wz));
					if (!cur.isAir() && cur.getFluidState().isEmpty()) break;
					p.set(wx, wy, wz, st);
				}
		}

		/** Hollow box walls (no floor/roof) between the two corners. */
		void shell(int x1, int y1, int z1, int x2, int y2, int z2, Block s) {
			for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
				if (x == x1 || x == x2 || z == z1 || z == z2) for (int y = y1; y <= y2; y++) b(x, y, z, s);
		}

		void chest(int x, int y, int z, Direction f, ResourceKey<LootTable> loot) {
			p.chest(o.getX() + x, o.getY() + y, o.getZ() + z, f, loot);
		}
	}

	static int hash(int x, int y, int z) {
		int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		return Math.floorMod(h ^ (h >>> 15), 100);
	}

	static BlockState wallBlock(int x, int y, int z) {
		int h = hash(x, y, z);
		if (h < 9) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (h < 17) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	static BlockState paving(int x, int z) {
		int h = hash(x, 7, z);
		if (h < 45) return Blocks.COBBLESTONE.defaultBlockState();
		if (h < 72) return Blocks.STONE_BRICKS.defaultBlockState();
		if (h < 90) return Blocks.ANDESITE.defaultBlockState();
		return Blocks.GRAVEL.defaultBlockState();
	}

	public static void build(Part part, BlockPos origin, Placer placer) {
		if (AzmarCastle.build(part, origin, placer)) return; // s-layout-azmar: Azmar's own fv 2 layout (CASTLE, DUNGEON, WALL, KIT_CASTLE)
		if (MireLayout.build(part, origin, placer)) return; // s-layout-mire: Mirewick's own fv 2 layout (CASTLE, WALL, KIT_CASTLE, ROAD)
		if (HrafnholtFort.build(part, origin, placer)) return; // s-layout-hraf: Hrafnholt's own fv 2 ring fort (CASTLE, WALL, KIT_CASTLE)
		switch (part) {
			case CASTLE -> {
				castle(new L(origin, placer));
				if (placer.style != null && !placer.style.pieces.isEmpty()) {
					if (placer.ground == null) CastleStyles.applyPieces(placer.style, origin, placer);
					else fitPieces(placer.style, origin, placer);
				}
			}
			case DUNGEON -> dungeon(new L(origin, placer));
			case TOWN_WEST -> town(new L(origin.offset(-TOWN_DX, 0, TOWN_DZ), placer), Direction.SOUTH);
			case TOWN_EAST -> town(new L(origin.offset(TOWN_DX, 0, TOWN_DZ), placer), Direction.SOUTH);
			case ROAD -> {
				if (placer.ground == null) road(new L(origin, placer));
				else roadFit(new L(origin, placer));
			}
			case WALL -> {
				if (placer.ground != null) wallFit(new L(origin, placer));
			}
			case KIT_CASTLE -> CastleKit.castle(new L(origin, placer));
			case KIT_TOWN_WEST -> CastleKit.town(new L(origin.offset(-TOWN_DX, 0, TOWN_DZ), placer));
			case KIT_TOWN_EAST -> CastleKit.town(new L(origin.offset(TOWN_DX, 0, TOWN_DZ), placer));
		}
	}

	// ---- 3.0 terrain fit (fv 2 starts) -----------------------------------------------------------------------------

	/**
	 * fv 2 style pieces ("nothing floating, nothing buried"). Pieces inside the levelled yard (|x|,|z| <= 28) are placed as designed. Outside it,
	 * touching pieces form a group (a quay and its posts move together; palisade posts each follow their own column). Per group, from
	 * generator ground only (deterministic): if any column is water or lava, or the ground drops more than 4 across it, the group is skipped
	 * (a missing ornament beats a floating one); else it is raised to its highest ground point minus 1 (buried at most 1) and underpinned
	 * down to solid ground (posts with their own block, everything else with the faction's stone bricks).
	 */
	static void fitPieces(CastleStyles.Style style, BlockPos o, Placer pl) {
		L l = new L(o, pl);
		java.util.List<CastleStyles.Piece> ps = style.pieces;
		int n = ps.size();
		int[][] r = new int[n][];
		boolean[] yard = new boolean[n];
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			r[i] = footprint(ps.get(i));
			yard[i] = Math.max(Math.max(Math.abs(r[i][0]), Math.abs(r[i][2])), Math.max(Math.abs(r[i][1]), Math.abs(r[i][3]))) <= 28;
			parent[i] = i;
		}
		for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
			if (yard[i] || yard[j]) continue;
			if (r[i][0] > r[j][2] + 1 || r[j][0] > r[i][2] + 1 || r[i][1] > r[j][3] + 1 || r[j][1] > r[i][3] + 1) continue;
			int a = root(parent, i), b = root(parent, j);
			if (a != b) parent[Math.max(a, b)] = Math.min(a, b);
		}
		java.util.Map<Integer, Integer> lift = new java.util.HashMap<>();
		BlockState stone = style.map(Blocks.STONE_BRICKS.defaultBlockState());
		for (int i = 0; i < n; i++) {
			CastleStyles.Piece p = ps.get(i);
			CastleStyles.Style one = new CastleStyles.Style();
			one.pieces.add(p);
			if (yard[i]) {
				CastleStyles.applyPieces(one, o, pl);
				continue;
			}
			if (!touches(pl.clip, o, r[i])) continue;
			int g = root(parent, i);
			Integer dy = lift.get(g);
			if (dy == null) {
				dy = groupLift(l, r, yard, parent, g);
				lift.put(g, dy);
			}
			if (dy == Integer.MIN_VALUE) continue;
			int base = Math.min(p.a()[1], p.b()[1]);
			l.foundation(r[i][0], r[i][1], r[i][2], r[i][3], base + dy - 1, p.type().equals("column") ? p.block() : stone.getBlock());
			CastleStyles.applyPieces(one, o.offset(0, dy, 0), pl);
		}
	}

	/** The fv 2 lift of one style-piece group (see fitPieces), or Integer.MIN_VALUE when the group is skipped (wet, or a drop over 4). */
	private static int groupLift(L l, int[][] r, boolean[] yard, int[] parent, int g) {
		int n = r.length, hi = Integer.MIN_VALUE, lo = Integer.MAX_VALUE;
		boolean wet = false;
		for (int k = 0; k < n && !wet; k++) {
			if (yard[k] || root(parent, k) != g) continue;
			for (int x = r[k][0]; x <= r[k][2] && !wet; x++) for (int z = r[k][1]; z <= r[k][3]; z++) {
				int y = l.gy(x, z);
				hi = Math.max(hi, y);
				lo = Math.min(lo, y);
				if (wet(l.p.ground, l.o.getX() + x, l.o.getZ() + z)) { wet = true; break; }
			}
		}
		return wet || hi - lo > 4 ? Integer.MIN_VALUE : hi > lo ? hi - 1 : hi;
	}

	/**
	 * z-castle (3.0.0): the lift fitPieces gives the outside-yard style-piece group whose footprint covers local (px, pz) on fv 2 ground, or
	 * Integer.MIN_VALUE when there is no such piece or its group is skipped. Same computation as fitPieces (ground only, deterministic), so
	 * the Mirewick kit's canal lies exactly against the quay that was placed.
	 */
	public static int pieceLift(CastleStyles.Style style, BlockPos o, Ground ground, int px, int pz) {
		Placer pl = new Placer(null, new BoundingBox(0, 0, 0, 0, 0, 0));
		pl.ground = ground;
		L l = new L(o, pl);
		java.util.List<CastleStyles.Piece> ps = style.pieces;
		int n = ps.size(), hit = -1;
		int[][] r = new int[n][];
		boolean[] yard = new boolean[n];
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			r[i] = footprint(ps.get(i));
			yard[i] = Math.max(Math.max(Math.abs(r[i][0]), Math.abs(r[i][2])), Math.max(Math.abs(r[i][1]), Math.abs(r[i][3]))) <= 28;
			parent[i] = i;
			if (hit < 0 && !yard[i] && px >= r[i][0] && px <= r[i][2] && pz >= r[i][1] && pz <= r[i][3]) hit = i;
		}
		if (hit < 0) return Integer.MIN_VALUE;
		for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
			if (yard[i] || yard[j]) continue;
			if (r[i][0] > r[j][2] + 1 || r[j][0] > r[i][2] + 1 || r[i][1] > r[j][3] + 1 || r[j][1] > r[i][3] + 1) continue;
			int a = root(parent, i), b = root(parent, j);
			if (a != b) parent[Math.max(a, b)] = Math.min(a, b);
		}
		return groupLift(l, r, yard, parent, root(parent, hit));
	}

	private static int root(int[] parent, int i) {
		while (parent[i] != i) i = parent[i] = parent[parent[i]];
		return i;
	}

	/** x1, z1, x2, z2 of a style piece's ground footprint (an obelisk's widest bottom tier). */
	static int[] footprint(CastleStyles.Piece p) {
		int[] a = p.a(), b = p.b();
		if (p.type().equals("obelisk")) {
			int r = (Math.max(1, p.base()) - 1) / 2 + 1;
			return new int[] {a[0] - r, a[2] - r, a[0] + r, a[2] + r};
		}
		return new int[] {Math.min(a[0], b[0]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[2], b[2])};
	}

	static boolean touches(BoundingBox clip, BlockPos o, int[] r) {
		return o.getX() + r[2] >= clip.minX() && o.getX() + r[0] <= clip.maxX() && o.getZ() + r[3] >= clip.minZ() && o.getZ() + r[1] <= clip.maxZ();
	}

	/** Water or lava on top of the generator terrain at a world column (fv 2 HeightField only; deterministic). */
	/** s-asm-court: local column (x, z) lies in the chunk being written (so a costly ground probe for one block runs only where it is placed). */
	static boolean inClip(L l, int x, int z) {
		var c = l.p.clip;
		int wx = l.o.getX() + x, wz = l.o.getZ() + z;
		return wx >= c.minX() && wx <= c.maxX() && wz >= c.minZ() && wz <= c.maxZ();
	}

	static boolean wet(Ground g, int x, int z) {
		if (!(g instanceof RealmFit.HeightField hf)) return false;
		return hf.gen.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, hf.h, hf.rs) < hf.y(x, z);
	}

	/** The square ring at distance m, in walking order (closed loop). */
	static int[][] ringCells(int m) {
		int[][] r = new int[8 * m][];
		int i = 0;
		for (int x = -m; x <= m; x++) r[i++] = new int[] {x, -m};
		for (int z = -m + 1; z <= m; z++) r[i++] = new int[] {m, z};
		for (int x = m - 1; x >= -m; x--) r[i++] = new int[] {x, m};
		for (int z = m - 1; z >= -m + 1; z--) r[i++] = new int[] {-m, z};
		return r;
	}

	/**
	 * Wall-top lift along the ring m: the ground under each ring cell relative to the floor, with every valley raised so neighbours differ by at
	 * most 1 (a contour wall steps like a staircase, never a cliff), never below the floor. Grid (2m+1)^2 indexed by {@link #liftAt}.
	 */
	static int[] ringLift(L l, int m) {
		int[][] cells = ringCells(m);
		int n = cells.length;
		int[] s = new int[n];
		for (int i = 0; i < n; i++) s[i] = l.gy(cells[i][0], cells[i][1]);
		for (int rep = 0; rep < 2; rep++) {
			for (int i = 0; i < n; i++) s[i] = Math.max(s[i], s[(i - 1 + n) % n] - 1);
			for (int i = n - 1; i >= 0; i--) s[i] = Math.max(s[i], s[(i + 1) % n] - 1);
		}
		int w = 2 * m + 1;
		int[] grid = new int[w * w];
		for (int i = 0; i < n; i++) grid[(cells[i][0] + m) * w + cells[i][1] + m] = Math.max(0, s[i]);
		return grid;
	}

	/** Lift at (x, z) projected onto the ring m (cells just inside or outside the ring use the nearest ring cell). */
	static int liftAt(int[] grid, int m, int x, int z) {
		int px = Math.clamp(x, -m, m), pz = Math.clamp(z, -m, m);
		if (Math.max(Math.abs(px), Math.abs(pz)) < m) {
			if (Math.abs(px) >= Math.abs(pz)) px = px < 0 ? -m : m;
			else pz = pz < 0 ? -m : m;
		}
		return grid[(px + m) * (2 * m + 1) + pz + m];
	}

	/** Gatehouse lift: the highest wall lift across the gate front (the gate towers rise by it; the gate itself stays at the yard floor). */
	static int gateLift(int[] grid) {
		int g = 0;
		for (int x = -7; x <= 7; x++) g = Math.max(g, liftAt(grid, 30, x, 30));
		return g;
	}

	/**
	 * fv 2 curtain: the 2.1 wall, towers and gate, but every wall column stands on its own ground (down to solid rock through a rubble
	 * footing) and its top follows the smoothed contour; corner towers rise with the ground at their corner; the gate stays at the yard floor.
	 */
	static void wallFit(L l) {
		int[] grid = ringLift(l, 30);
		for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) {
			int m = Math.max(Math.abs(x), Math.abs(z));
			if (m < 29) continue;
			int lift = liftAt(grid, 30, x, z), foot = Math.min(0, l.gy(x, z));
			for (int y = foot; y <= 8 + lift; y++) l.b(x, y, z, wallBlock(x, y, z));
			if (m == 30 && ((x + z) & 1) == 0) l.b(x, 9 + lift, z, wallBlock(x, 9 + lift, z));
			l.foundation(x, z, x, z, foot - 1, Blocks.COBBLESTONE);
		}
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			int cx = sx * 30, cz = sz * 30, lift = liftAt(grid, 30, cx, cz), foot = 0;
			for (int a = -4; a <= 4; a += 4) for (int b = -4; b <= 4; b += 4) foot = Math.min(foot, l.gy(cx + a, cz + b));
			if (lift > foot) l.fill(cx - 4, foot, cz - 4, cx + 4, lift - 1, cz + 4, Blocks.STONE_BRICKS);
			l.foundation(cx - 4, cz - 4, cx + 4, cz + 4, foot - 1, Blocks.COBBLESTONE);
			l.dy = lift;
			tower(l, cx, cz, 4, 13, sx, sz);
			l.dy = 0;
		}
		int gl = gateLift(grid);
		tower(l, -5, 29, 2, 12 + gl, 0, 0);
		tower(l, 5, 29, 2, 12 + gl, 0, 0);
		for (int x = -7; x <= 7; x += 4) if (Math.abs(x) == 3 || Math.abs(x) == 7) {
			int lw = liftAt(grid, 30, x, 30);
			for (int y = 9 + lw; y <= 10 + lw; y++) for (int z = 29; z <= 30; z++) l.b(x, y, z, Blocks.AIR);
		}
		l.fill(-2, 0, 29, 2, 4, 30, Blocks.AIR);
		l.fill(-2, 5, 29, 2, 5, 29, Blocks.IRON_BARS);
		for (BlockPos g : gates(BlockPos.ZERO))
			l.b(g.getX(), 0, g.getZ(), Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH).setValue(FenceGateBlock.OPEN, true));
		for (int sx = -1; sx <= 1; sx += 2) l.b(sx * 5, 7, 32, Blocks.WALL_BANNER.blue().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
		// stairs up to the (possibly raised) wall walk on both sides of the gate
		for (int s = -1; s <= 1; s += 2) {
			int top = 8 + liftAt(grid, 30, s * 18, 30);
			for (int i = 0; i <= top; i++) {
				int x = s * (10 + i);
				l.b(x, i, 28, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, s < 0 ? Direction.WEST : Direction.EAST));
				for (int y = 0; y < i; y++) l.b(x, y, 28, Blocks.STONE_BRICKS);
			}
		}
	}

	/**
	 * A line contour with steps of at most 1: anchors are met exactly (gates at their floors), every other cell is clamped into the anchors'
	 * 1-per-block cones, then valleys are raised (a low causeway rather than a cut).
	 */
	static int[] contour(int[] raw, int[] ai, int[] av) {
		int n = raw.length;
		int[] s = raw.clone();
		for (int i = 0; i < n; i++) for (int k = 0; k < ai.length; k++) {
			int d = Math.abs(i - ai[k]);
			s[i] = Math.min(Math.max(s[i], av[k] - d), av[k] + d);
		}
		for (int i = 1; i < n; i++) s[i] = Math.max(s[i], s[i - 1] - 1);
		for (int i = n - 2; i >= 0; i--) s[i] = Math.max(s[i], s[i + 1] - 1);
		return s;
	}

	static int[] rawLine(L l, int x0, int z0, int dx, int dz, int n) {
		int[] r = new int[n];
		for (int i = 0; i < n; i++) r[i] = l.gy(x0 + i * dx, z0 + i * dz);
		return r;
	}

	/**
	 * fv 2 road: an A* route over the terrain from the castle gate to each town gate (8-neighbour; climbing costs, steep steps cost a lot),
	 * kept out of the castle and town footprints except their gate corridors; then a contour with steps of at most 1 that meets the castle
	 * gate and the town gate at their floors; 3 wide, with lamp posts. Routes are deterministic and cached per realm.
	 */
	static void roadFit(L l) {
		Ground g = l.p.ground;
		// s-asm-court: a 5-wide apron at the floor in front of the 5-wide gate, so its outer leaves never open into earth (the road is 3 wide)
		for (int x = -2; x <= 2; x++) for (int z = 31; z <= 32; z++) pathAt(l, x, z, 0);
		for (int s = -1; s <= 1; s += 2) {
			int tx = s * TOWN_DX;
			int tf = g.floor(s < 0 ? Part.TOWN_WEST : Part.TOWN_EAST, g.y(l.o.getX() + tx, l.o.getZ() + TOWN_DZ)) - l.o.getY();
			int[][] path = route(l, tx);
			int n = path.length;
			int[] raw = new int[n];
			for (int i = 0; i < n; i++) raw[i] = l.gy(path[i][0], path[i][1]);
			int[] h = contour(raw, new int[] {0, n - 1}, new int[] {0, tf});
			for (int i = 0; i < n; i++) for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) pathAt(l, path[i][0] + a, path[i][1] + b, h[i]);
			for (int i = 20; i < n - 10; i += 28) {
				int px = path[i + 1][1] - path[i - 1][1], pz = path[i - 1][0] - path[i + 1][0];
				int x = path[i][0] + Integer.signum(px) * 2, z = path[i][1] + Integer.signum(pz) * 2;
				if (!inClip(l, x, z) || wet(g, l.o.getX() + x, l.o.getZ() + z)) continue; // s-asm-court: no lamp post standing in a river or pond
				int y = Math.max(l.gy(x, z), h[i]);
				l.b(x, y, z, Blocks.STONE_BRICK_WALL);
				l.b(x, y + 1, z, Blocks.LANTERN);
				l.foundation(x, z, x, z, y - 1, Blocks.DIRT); // s-asm-court: underpin the post like the embankment it stands beside
			}
		}
	}

	static final int RX = 92, RZ0 = 29, RZ1 = 84;
	private static final java.util.Map<String, int[][]> ROUTES = new java.util.concurrent.ConcurrentHashMap<>();

	static boolean blocked(int x, int z, int tx) {
		if (Math.abs(x) <= 37 && z <= 37 && Math.abs(x) > 1) return true;
		for (int s = -1; s <= 1; s += 2) {
			int d = Math.abs(x - s * TOWN_DX);
			if (d <= 25 && z <= 33 && !(d <= 1 && s * TOWN_DX == tx)) return true;
		}
		return false;
	}

	/** A* from the castle gate (0, 31) to the town gate (tx, 31) in castle-local coordinates. */
	static int[][] route(L l, int tx) {
		Ground g = l.p.ground;
		String key = l.o.asLong() + ":" + tx + ":" + g.y(l.o.getX(), l.o.getZ() + 60) + ":" + g.y(l.o.getX() + 60, l.o.getZ() + 40) + ":" + g.y(l.o.getX() - 60, l.o.getZ() + 70);
		int[][] cached = ROUTES.get(key);
		if (cached != null) return cached;
		int w = 2 * RX + 1, hgt = RZ1 - RZ0 + 1, size = w * hgt;
		int[] gyc = new int[size], cost = new int[size], from = new int[size];
		java.util.Arrays.fill(gyc, Integer.MIN_VALUE);
		java.util.Arrays.fill(cost, Integer.MAX_VALUE);
		java.util.Arrays.fill(from, -1);
		int start = (0 + RX) * hgt + (31 - RZ0), goal = (tx + RX) * hgt + (31 - RZ0);
		java.util.PriorityQueue<long[]> open = new java.util.PriorityQueue<>((p, q) -> p[0] != q[0] ? Long.compare(p[0], q[0]) : Long.compare(p[1], q[1]));
		cost[start] = 0;
		open.add(new long[] {0, start});
		while (!open.isEmpty()) {
			long[] cur = open.poll();
			int c = (int) cur[1];
			if (c == goal) break;
			int cx = c / hgt - RX, cz = c % hgt + RZ0;
			if (cur[0] - heur(cx, cz, tx, 31) > cost[c]) continue;
			int hc = gyAt(l, gyc, c, cx, cz);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				int nx = cx + dx, nz = cz + dz;
				if (nx < -RX || nx > RX || nz < RZ0 || nz > RZ1 || blocked(nx, nz, tx)) continue;
				int nb = (nx + RX) * hgt + (nz - RZ0);
				int dh = Math.abs(gyAt(l, gyc, nb, nx, nz) - hc);
				int step = (dx != 0 && dz != 0 ? 14 : 10) + (dh == 0 ? 0 : dh == 1 ? 6 : 40 * dh);
				int nc = cost[c] + step;
				if (nc < cost[nb]) {
					cost[nb] = nc;
					from[nb] = c;
					open.add(new long[] {nc + heur(nx, nz, tx, 31), nb});
				}
			}
		}
		java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
		if (from[goal] < 0) {
			// no route (should not happen inside the corridor): fall back to the 2.1 line
			for (int z = 31; z <= 40; z++) out.add(new int[] {0, z});
			for (int x = 0; x != tx; x += Integer.signum(tx)) out.add(new int[] {x, 40});
			for (int z = 40; z >= 31; z--) out.add(new int[] {tx, z});
		} else {
			for (int c = goal; c != -1; c = from[c]) out.add(new int[] {c / hgt - RX, c % hgt + RZ0});
			java.util.Collections.reverse(out);
		}
		int[][] r = out.toArray(new int[0][]);
		if (ROUTES.size() > 64) ROUTES.clear();
		ROUTES.put(key, r);
		return r;
	}

	private static int heur(int x, int z, int gx, int gz) {
		int dx = Math.abs(x - gx), dz = Math.abs(z - gz);
		return 10 * Math.max(dx, dz) + 4 * Math.min(dx, dz);
	}

	private static int gyAt(L l, int[] cache, int i, int x, int z) {
		if (cache[i] == Integer.MIN_VALUE) cache[i] = l.gy(x, z);
		return cache[i];
	}

	/**
	 * fv 2 bounding boxes (adaptation per the generation lane: CASTLE and DUNGEON beard_thin at the floor; WALL, ROAD, towns and KIT_* none,
	 * because they cut and fill their own pads). Wider and deeper than {@link #box} so contour pieces and the A* road corridor are covered.
	 */
	public static BoundingBox fitBox(Part p, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		return switch (p) {
			case ROAD -> new BoundingBox(x - RX - 3, y - 32, z + RZ0 - 1, x + RX + 3, y + 32, z + RZ1 + 3);
			case WALL -> new BoundingBox(x - 36, y - 32, z - 36, x + 36, y + 40, z + 36);
			// z to +38 (z-castle): the fv 2 Mirewick canal's outer wall (z 37) lies in chunks this piece post-processes, so its water stays contained
			case KIT_CASTLE -> new BoundingBox(x - 36, y - 16, z - 36, x + 36, y + 44, z + 38);
			case TOWN_WEST, KIT_TOWN_WEST -> new BoundingBox(x - TOWN_DX - 24, y - 16, z + TOWN_DZ - 24, x - TOWN_DX + 24, y + 28, z + TOWN_DZ + 24);
			case TOWN_EAST, KIT_TOWN_EAST -> new BoundingBox(x + TOWN_DX - 24, y - 16, z + TOWN_DZ - 24, x + TOWN_DX + 24, y + 28, z + TOWN_DZ + 24);
			default -> box(p, o);
		};
	}

	static void pathAt(L l, int x, int z, int s) {
		l.fill(x, s, z, x, Math.max(s + 3, l.gy(x, z) - 1), z, Blocks.AIR);
		l.b(x, s - 1, z, Blocks.DIRT_PATH);
		l.foundation(x, z, x, z, s - 2, Blocks.DIRT);
	}

	// ---- castle ----------------------------------------------------------------------------------------------------

	static void castle(L l) {
		boolean fit = l.p.ground != null;
		if (!fit) {
			l.fill(-34, 0, -34, 34, 12, 34, Blocks.AIR);
			l.fillIfAir(-32, -4, -32, 32, -2, 32, Blocks.STONE);
			l.foundation(-32, -32, 32, 32, -5, Blocks.STONE);
			for (int x = -31; x <= 31; x++) for (int z = -31; z <= 31; z++) l.b(x, -1, z, paving(x, z));
			walls(l);
		} else {
			// fv 2: only the yard pad inside the curtain is levelled (the generation lane's beard_thin blends its edge); WALL builds the curtain
			l.fill(-28, 0, -28, 28, 30, 28, Blocks.AIR); // to 30: no hillside or tree crown left hanging over the yard
			l.fillIfAir(-28, -4, -28, 28, -2, 28, Blocks.STONE);
			l.foundation(-28, -28, 28, 28, -5, Blocks.STONE);
			for (int x = -28; x <= 28; x++) for (int z = -28; z <= 28; z++) l.b(x, -1, z, paving(x, z));
		}
		keep(l);
		// barracks (west)
		hut(l, -27, -4, -17, 12, 4, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS);
		l.fill(-17, 0, 3, -17, 2, 4, Blocks.AIR);
		for (int z = -2; z <= 10; z += 3) bed(l, -26, z, Direction.WEST, Blocks.BED.blue());
		l.b(-20, 0, 11, Blocks.CHEST);
		// armory (east)
		hut(l, 17, -4, 27, 8, 4, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS);
		l.fill(17, 0, 1, 17, 2, 2, Blocks.AIR);
		l.b(21, 0, -2, Blocks.ANVIL);
		l.b(23, 0, -2, Blocks.SMITHING_TABLE);
		l.b(25, 0, -2, Blocks.GRINDSTONE);
		l.b(26, 0, 4, Blocks.BARREL);
		l.b(26, 0, 3, Blocks.BARREL);
		l.chest(26, 0, 6, Direction.WEST, LOOT_ARMORY);
		// chapel (north-east)
		hut(l, 15, -27, 27, -15, 7, Blocks.CALCITE, Blocks.DEEPSLATE_TILES);
		l.fill(15, 0, -22, 15, 2, -20, Blocks.AIR);
		for (int z = -25; z <= -17; z += 4) l.fill(27, 2, z, 27, 4, z, Blocks.STAINED_GLASS_PANE.blue());
		l.b(26, 0, -21, Blocks.CHISELED_STONE_BRICKS);
		l.b(26, 1, -21, Blocks.LANTERN);
		for (int x = 18; x <= 22; x += 2) for (int z = -25; z <= -16; z++) if (z != -21 && z != -20)
			l.b(x, 0, z, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
		// stables (south-west)
		for (int x = -27; x <= -17; x++) for (int z = 16; z <= 26; z++) {
			boolean edge = x == -27 || x == -17 || z == 16 || z == 26;
			if (edge && !(x == -17 && z >= 20 && z <= 22)) l.b(x, 0, z, Blocks.OAK_FENCE);
		}
		for (int x = -27; x <= -17; x += 10) for (int z = 16; z <= 26; z += 10) l.fill(x, 0, z, x, 3, z, Blocks.SPRUCE_LOG);
		l.fill(-27, 4, 16, -17, 4, 26, Blocks.SPRUCE_SLAB.defaultBlockState());
		l.fill(-26, 0, 24, -24, 1, 25, Blocks.HAY_BLOCK);
		// training yard, bell, braziers
		for (int x = -10; x <= -6; x += 4) {
			l.b(x, 0, 4, Blocks.HAY_BLOCK);
			l.b(x, 1, 4, Blocks.TARGET);
		}
		l.b(6, 0, 14, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
		for (BlockPos b : braziers(BlockPos.ZERO)) l.b(b.getX(), b.getY(), b.getZ(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		l.chest(-24, 0, 25, Direction.EAST, LOOT_CACHE);
	}

	/** The 2.1 curtain wall, corner towers, gatehouse and wall-walk stairs (frozen path; fv 2 uses wallFit). */
	static void walls(L l) {
		// curtain wall with crenellations
		for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) {
			int m = Math.max(Math.abs(x), Math.abs(z));
			if (m < 29) continue;
			for (int y = 0; y <= 8; y++) l.b(x, y, z, wallBlock(x, y, z));
			if (m == 30 && ((x + z) & 1) == 0) l.b(x, 9, z, wallBlock(x, 9, z));
		}
		// corner towers
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) tower(l, sx * 30, sz * 30, 4, 13, sx, sz);
		// gatehouse towers and gate
		tower(l, -5, 29, 2, 12, 0, 0);
		tower(l, 5, 29, 2, 12, 0, 0);
		for (int x = -7; x <= 7; x += 4) for (int y = 9; y <= 10; y++) for (int z = 29; z <= 30; z++) if (Math.abs(x) == 3 || Math.abs(x) == 7) l.b(x, y, z, Blocks.AIR);
		l.fill(-2, 0, 29, 2, 4, 30, Blocks.AIR);
		l.fill(-2, 5, 29, 2, 5, 29, Blocks.IRON_BARS);
		for (BlockPos g : gates(BlockPos.ZERO))
			l.b(g.getX(), 0, g.getZ(), Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH).setValue(FenceGateBlock.OPEN, true));
		for (int sx = -1; sx <= 1; sx += 2) l.b(sx * 5, 7, 32, Blocks.WALL_BANNER.blue().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
		// stairs up to the wall walk on both sides of the gate
		for (int i = 0; i <= 8; i++) {
			for (int s = -1; s <= 1; s += 2) {
				int x = s * (10 + i);
				l.b(x, i, 28, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, s < 0 ? Direction.WEST : Direction.EAST));
				for (int y = 0; y < i; y++) l.b(x, y, 28, Blocks.STONE_BRICKS);
			}
		}
	}

	static void tower(L l, int cx, int cz, int r, int h, int sx, int sz) {
		l.shell(cx - r, 0, cz - r, cx + r, h, cz + r, Blocks.STONE_BRICKS);
		l.fill(cx - r + 1, 0, cz - r + 1, cx + r - 1, h - 1, cz + r - 1, Blocks.AIR);
		l.fill(cx - r + 1, 8, cz - r + 1, cx + r - 1, 8, cz + r - 1, Blocks.SPRUCE_PLANKS);
		l.fill(cx - r + 1, h, cz - r + 1, cx + r - 1, h, cz + r - 1, Blocks.STONE_BRICKS);
		for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++)
			if ((x == cx - r || x == cx + r || z == cz - r || z == cz + r) && ((x + z) & 1) == 0) l.b(x, h + 1, z, Blocks.STONE_BRICKS);
		if (sx != 0) {
			// doors: ground towards the courtyard, wall walk on both walls
			l.fill(cx - sx * r, 0, cz - sz * 3, cx - sx * r, 2, cz - sz * 3, Blocks.AIR);
			for (int y = 9; y <= 10; y++) {
				l.b(cx - sx * r, y, cz - sz, Blocks.AIR);
				l.b(cx - sx * r, y, cz, Blocks.AIR);
				l.b(cx - sx, y, cz - sz * r, Blocks.AIR);
				l.b(cx, y, cz - sz * r, Blocks.AIR);
			}
			l.b(cx, 0, cz, Blocks.LANTERN); // s-asm-court: on the tower floor (y -1); at y 1 it hung in the air over the cleared y 0
		}
	}

	static void hut(L l, int x1, int z1, int x2, int z2, int h, Block wall, Block roof) {
		l.shell(x1, 0, z1, x2, h, z2, wall);
		for (int x = x1; x <= x2; x += x2 - x1) for (int z = z1; z <= z2; z += z2 - z1) l.fill(x, 0, z, x, h, z, Blocks.SPRUCE_LOG);
		l.fill(x1, h + 1, z1, x2, h + 1, z2, roof);
		l.fill(x1 + 1, 0, z1 + 1, x2 - 1, h, z2 - 1, Blocks.AIR);
		l.b((x1 + x2) / 2, h, (z1 + z2) / 2, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
	}

	static void bed(L l, int headX, int z, Direction toHead, Block bed) {
		BlockState s = bed.defaultBlockState().setValue(BedBlock.FACING, toHead);
		l.b(headX, 0, z, s.setValue(BedBlock.PART, BedPart.HEAD));
		l.b(headX - toHead.getStepX(), 0, z - toHead.getStepZ(), s.setValue(BedBlock.PART, BedPart.FOOT));
	}

	static void keep(L l) {
		for (int x = -11; x <= 11; x++) for (int z = -26; z <= -8; z++) {
			boolean edge = x == -11 || x == 11 || z == -26 || z == -8;
			if (!edge) continue;
			boolean corner = (x == -11 || x == 11) && (z == -26 || z == -8);
			for (int y = 0; y <= 17; y++)
				l.b(x, y, z, corner ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : y == 0 || y == 9 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : wallBlock(x, y, z));
			if (((x + z) & 1) == 0) l.b(x, 19, z, Blocks.STONE_BRICKS);
		}
		l.fill(-10, -1, -25, 10, -1, -9, Blocks.POLISHED_ANDESITE);
		l.fill(-10, 9, -25, 10, 9, -9, Blocks.DARK_OAK_PLANKS);
		l.fill(-11, 18, -26, 11, 18, -8, Blocks.STONE_BRICKS);
		for (int z = -24; z <= -12; z += 4) for (int s = -1; s <= 1; s += 2) {
			l.fill(s * 11, 3, z, s * 11, 5, z, Blocks.GLASS_PANE);
			l.fill(s * 11, 11, z, s * 11, 12, z, Blocks.GLASS_PANE);
		}
		l.fill(-1, 0, -8, 1, 3, -8, Blocks.AIR);
		l.fill(-1, 0, -23, 1, 0, -9, Blocks.CARPET.red());
		for (int z = -22; z <= -10; z += 4) for (int s = -1; s <= 1; s += 2) l.fill(s * 7, 0, z, s * 7, 8, z, Blocks.POLISHED_DEEPSLATE);
		// dais and throne
		l.fill(-4, 0, -25, 4, 0, -23, Blocks.POLISHED_BLACKSTONE_BRICKS);
		l.b(0, 1, -24, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		// 3.0 (B9): the throne is yellow terracotta on every path (fv 1, fv 2, saved starts, any switch); never gold
		Block throne = Blocks.DYED_TERRACOTTA.yellow();
		l.b(-1, 1, -24, throne);
		l.b(1, 1, -24, throne);
		for (int x = -5; x <= 5; x += 2) if (x != -1 && x != 1) l.b(x, 4, -25, Blocks.WALL_BANNER.blue().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
		// balconies
		for (int s = -1; s <= 1; s += 2) {
			l.fill(s * 10, 4, -24, s * 9, 4, -10, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			l.fill(s * 8, 5, -24, s * 8, 5, -10, Blocks.DARK_OAK_FENCE);
		}
		// solar (upper floor), ladder up, hatch to the dungeon
		l.fill(10, 0, -10, 10, 9, -10, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		bed(l, -9, -24, Direction.WEST, Blocks.BED.red());
		bed(l, -9, -21, Direction.WEST, Blocks.BED.red());
		l.chest(9, 10, -24, Direction.WEST, LOOT_CACHE);
		l.b(0, 10, -17, Blocks.LANTERN);
		l.b(-3, -1, -15, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
	}

	static void dungeon(L l) {
		l.fill(-8, -7, -20, -2, -2, -14, Blocks.CRACKED_STONE_BRICKS);
		l.fill(-7, -6, -19, -3, -3, -15, Blocks.AIR);
		l.fill(-5, -6, -19, -5, -4, -16, Blocks.IRON_BARS);
		l.fill(-3, -6, -15, -3, -2, -15, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		l.chest(-7, -6, -19, Direction.EAST, LOOT_DUNGEON);
		l.b(-7, -4, -15, Blocks.SOUL_LANTERN);
	}

	// ---- towns -----------------------------------------------------------------------------------------------------

	public static final int[][] HOUSES = {{-19, -19}, {-19, 10}, {-10, -19}, {10, -19}, {-10, 12}};
	public static final Block[] STALL_JOBS = {Blocks.SMITHING_TABLE, Blocks.FLETCHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.SMOKER};

	static void town(L l, Direction gate) {
		if (l.p.ground == null) {
			l.fill(-23, 0, -23, 23, 10, 23, Blocks.AIR);
			l.fillIfAir(-23, -3, -23, 23, -2, 23, Blocks.DIRT);
			l.foundation(-23, -23, 23, 23, -4, Blocks.STONE);
			for (int x = -23; x <= 23; x++) for (int z = -23; z <= 23; z++) {
				boolean street = Math.abs(x) <= 1 || Math.abs(z) <= 1;
				boolean square = Math.abs(x) <= 5 && Math.abs(z) <= 5;
				l.b(x, -1, z, square ? Blocks.COBBLESTONE.defaultBlockState() : street ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
			}
		} else {
			// fv 2: no town-wide pad. Only the market square is levelled; the four streets lie on their own contour from the square to the
			// palisade; each house gets its own pad (house()); the ground between stays natural.
			l.fill(-6, 0, -6, 6, 16, 6, Blocks.AIR);
			l.foundation(-6, -6, 6, 6, -2, Blocks.STONE);
			for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++)
				l.b(x, -1, z, Math.max(Math.abs(x), Math.abs(z)) <= 5 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
			for (int d = 0; d < 4; d++) {
				int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dz = d == 2 ? 1 : d == 3 ? -1 : 0;
				int[] h = contour(rawLine(l, dx * 7, dz * 7, dx, dz, 17), new int[] {0}, new int[] {0});
				for (int i = 0; i < 17; i++) for (int w = -1; w <= 1; w++)
					pathAt(l, dx * (7 + i) + (dz != 0 ? w : 0), dz * (7 + i) + (dx != 0 ? w : 0), h[i]);
			}
		}
		// palisade; 3.0 fit (fv 2): each post stands on its own ground and the top follows the smoothed contour
		int[] pal = l.p.ground != null ? ringLift(l, 22) : null;
		boolean azmar = CastleKit.kitOf(l.p) == CastleKit.Kit.AZMAR;
		for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
			if (Math.max(Math.abs(x), Math.abs(z)) != 22) continue;
			if (z == 22 && Math.abs(x) <= 1) continue;
			if (Math.abs(x) == 22 && Math.abs(z) <= 1) continue;
			int lift = pal == null ? 0 : liftAt(pal, 22, x, z), foot = pal == null ? 0 : Math.min(0, l.gy(x, z));
			if (azmar) {
				// Azmar: a sandstone curtain (mud-brick course, lapis band, sandstone merlons) instead of the style-mapped acacia log and fence pickets
				BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState(), mud = Blocks.MUD_BRICKS.defaultBlockState(), lapis = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE).defaultBlockState();
				for (int y = foot; y <= 4 + lift; y++) l.p.setRaw(l.o.getX() + x, l.o.getY() + y, l.o.getZ() + z, y == 3 + lift ? lapis : y <= 1 + lift && y >= lift ? mud : cut);
				if (((x + z) & 1) == 0) l.p.setRaw(l.o.getX() + x, l.o.getY() + 5 + lift, l.o.getZ() + z, Blocks.SANDSTONE_WALL.defaultBlockState());
			} else {
				l.fill(x, foot, z, x, 4 + lift, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
				if (((x + z) & 1) == 0) l.b(x, 5 + lift, z, Blocks.SPRUCE_FENCE);
			}
			if (pal != null) l.foundation(x, z, x, z, foot - 1, Blocks.COBBLESTONE);
		}
		// well
		l.b(0, -1, 0, Blocks.WATER);
		l.b(0, -2, 0, Blocks.COBBLESTONE);
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) l.b(x, 0, z, Blocks.STONE_BRICK_WALL);
		// stalls
		int i = 0;
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			int cx = sx * 4, cz = sz * 4;
			l.fill(cx - 1, 0, cz, cx - 1, 1, cz, Blocks.OAK_FENCE);
			l.fill(cx + 1, 0, cz, cx + 1, 1, cz, Blocks.OAK_FENCE);
			for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++)
				l.b(x, 2, z, ((x + z) & 1) == 0 ? Blocks.WOOL.blue() : Blocks.WOOL.white());
			l.b(cx, 0, cz, STALL_JOBS[i++]);
		}
		l.chest(5, 0, -5, Direction.WEST, LOOT_STALL);
		for (int[] h : HOUSES) house(l, h[0], h[1], 7, 7);
		house(l, 8, 9, 11, 9);
		l.dy = pad(l, 8, 9, 18, 17);
		l.b(14, 0, 15, Blocks.BREWING_STAND);
		l.b(16, 0, 15, Blocks.BARREL);
		l.b(16, 0, 14, Blocks.BARREL);
		for (int s = -1; s <= 1; s += 2) for (int[] c : new int[][] {{s * 3, 8}, {s * 8, -3}}) {
			// s-asm-court: fv 2 lamp posts stand on their own ground, underpinned where it was cut, and none stands in a river or pond
			if (l.p.ground != null && (!inClip(l, c[0], c[1]) || wet(l.p.ground, l.o.getX() + c[0], l.o.getZ() + c[1]))) continue;
			l.dy = l.p.ground == null ? 0 : l.gy(c[0], c[1]);
			l.b(c[0], 0, c[1], Blocks.OAK_FENCE);
			l.b(c[0], 1, c[1], Blocks.LANTERN);
			if (l.p.ground != null) l.foundation(c[0], c[1], c[0], c[1], l.dy - 1, Blocks.COBBLESTONE);
		}
		l.dy = 0;
	}

	/**
	 * fv 2 building pad: floor relative to the part floor = the median ground of 9 samples over the footprint, clamped to +-6. 0 on the 2.1
	 * path. Shared with CastleKit so roofs land on the same pad.
	 */
	static int pad(L l, int x0, int z0, int x1, int z1) {
		if (l.p.ground == null) return 0;
		int[] v = new int[9];
		int i = 0;
		for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) v[i++] = l.gy(x0 + (x1 - x0) * a / 2, z0 + (z1 - z0) * b / 2);
		java.util.Arrays.sort(v);
		return Math.clamp(v[4], -6, 6);
	}

	static void house(L l, int x0, int z0, int w, int d) {
		int x1 = x0 + w - 1, z1 = z0 + d - 1;
		if (l.p.ground != null) {
			// fv 2: cut and fill only this house's pad plus a 1-block apron, then build on it
			int f = pad(l, x0, z0, x1, z1), top = f;
			for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) top = Math.max(top, l.gy(x0 + (x1 - x0) * a / 2, z0 + (z1 - z0) * b / 2));
			l.fill(x0 - 1, f, z0 - 1, x1 + 1, Math.max(f + 20, top + 2), z1 + 1, Blocks.AIR);
			l.foundation(x0 - 1, z0 - 1, x1 + 1, z1 + 1, f - 2, Blocks.COBBLESTONE);
			for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++)
				if (x < x0 || x > x1 || z < z0 || z > z1) l.b(x, f - 1, z, Blocks.GRASS_BLOCK);
			// slope skirt (1:1, grass over dirt) round the pad instead of a sheer cut face; walls and streets are never touched
			CastleKit.skirt(l, 40, (x, z) -> Math.max(Math.max(x0 - 1 - x, x - x1 - 1), Math.max(z0 - 1 - z, z - z1 - 1)), 4, Blocks.GRASS_BLOCK, Blocks.DIRT, f + 20, f - 1, false);
			l.dy = f;
			houseBody(l, x0, z0, w, d, x1, z1);
			l.dy = 0;
			return;
		}
		houseBody(l, x0, z0, w, d, x1, z1);
	}

	static void houseBody(L l, int x0, int z0, int w, int d, int x1, int z1) {
		l.fill(x0, -1, z0, x1, -1, z1, Blocks.COBBLESTONE);
		l.shell(x0, 0, z0, x1, 3, z1, Blocks.OAK_PLANKS);
		for (int x = x0; x <= x1; x += w - 1) for (int z = z0; z <= z1; z += d - 1) l.fill(x, 0, z, x, 3, z, Blocks.OAK_LOG);
		l.fill(x0, 4, z0, x1, 4, z1, Blocks.SPRUCE_PLANKS);
		l.fill(x0 + 1, 5, z0 + 1, x1 - 1, 5, z1 - 1, Blocks.SPRUCE_SLAB.defaultBlockState());
		int dz = z0 < 0 ? z1 : z0;
		int dx = x0 + w / 2;
		l.fill(dx, 0, dz, dx, 1, dz, Blocks.AIR);
		l.b(x0, 1, z0 + d / 2, Blocks.GLASS_PANE);
		l.b(x1, 1, z0 + d / 2, Blocks.GLASS_PANE);
		int bz = z0 < 0 ? z0 + 1 : z1 - 1;
		bed(l, x0 + 1, bz, Direction.WEST, Blocks.BED.white());
		l.b(x1 - 1, 0, bz, Blocks.CRAFTING_TABLE);
	}

	// ---- road ------------------------------------------------------------------------------------------------------

	static void road(L l) {
		for (int z = 31; z <= 41; z++) for (int x = -1; x <= 1; x++) path(l, x, z);
		for (int x = -89; x <= 89; x++) for (int z = 39; z <= 41; z++) path(l, x, z);
		for (int s = -1; s <= 1; s += 2) for (int z = 31; z <= 38; z++) for (int x = -1; x <= 1; x++) path(l, s * TOWN_DX + x, z);
		for (int s = -1; s <= 1; s += 2) {
			l.b(s * 44, 0, 42, Blocks.STONE_BRICK_WALL);
			l.b(s * 44, 1, 42, Blocks.LANTERN);
		}
	}

	static void path(L l, int x, int z) {
		l.b(x, -1, z, Blocks.DIRT_PATH);
		l.fillIfAir(x, -3, z, x, -2, z, Blocks.DIRT);
		l.fill(x, 0, z, x, 3, z, Blocks.AIR);
	}
}
