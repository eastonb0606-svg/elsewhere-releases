package dev.elsewhere.kingdom_court.world;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * s-layout-azmar (3.0.0): Azmar's own castle layout, "Citadel of the Scarab" (Ideas/overhaul/STRUCTURES.md 3.3 and 7.1), for NEW (fv 2) Azmar
 * realms. It replaces the dressed Aldermark plan for the parts CASTLE, DUNGEON, WALL and KIT_CASTLE; the towns and the road are unchanged.
 * <p>
 * The smallest castle of the four kingdoms: the battered mud-brick palm wall encloses 49 x 50 blocks (Aldermark's curtain is 61 x 61). One
 * processional axis runs north from the road: the sphinx avenue (8 couchant sphinxes, each on its own ground), the obelisk pair and the
 * lapis-tiled sacred lake with its ghat steps and papyrus beds, the twin battered pylons of the gate (cavetto cornices, banner niches, a gold
 * scarab between lapis wings over the gateway), the palm court (colonnaded porticos, two lapis pools, palms, the barracks and the armory behind
 * the porticos), the hypostyle hall on the first terrace (18 lotus and papyrus columns, a blue ceiling with yellow stars, a stone-grille
 * clerestory over the nave), and on the top terrace the throne hall (ebony throne on a blackstone dais), the naos with the crypt below it
 * and the private palace with its roof terrace, kiosk and awning. The palace terrace follows the land: on flat ground it is the top terrace
 * (two low terraces in all), and on a site rising away from the gate it climbs up to 3 more courses.
 * <p>
 * Terrain fit: everything except the avenue's far end and its sphinxes stands on the generation lane's levelled yard pad (|x|,|z| <= 28),
 * which this layout carves and fills itself like the Aldermark fv 2 castle; the sphinxes outside the pad stand on their own generator ground
 * (skipped over water or a drop of more than 3) and are underpinned. Writes are clipped to the chunk like every realm part and depend only on
 * the generator ground, so every chunk agrees. No valuable blocks (gold is yellow terracotta, lapis is blue terracotta), no glass, no
 * entities, no tickers; 6 banners, 11 lanterns and a soul lantern.
 * <p>
 * The court's named places ({@code CastleLayout.throne()}, {@code hall()}, {@code gates()}, ...) move into this plan through
 * {@link #place}, called from {@code CastleLayout.at}, for Azmar fv 2 sites only.
 * <p>
 * Kill switch: {@code -Delsewhere.overhaul3.azmar_layout=false} (Azmar fv 2 realms get the dressed Aldermark plan of CastleKit again).
 */
public final class AzmarCastle {
	private AzmarCastle() {}

	/** Kill switch {@code azmar_layout} (default on; non-final so SelfTestValuables sweeps both values). */
	public static volatile boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.azmar_layout", "true"));
	/** The faction whose castle style selects this layout, and the prefix of self-test styles that do too. */
	public static final String AZMAR = "elsewhere:azmar", TEST_PREFIX = "elsewhere:sla_";
	/** Half-size of the levelled yard pad (the generation lane's thin beard covers the same square). */
	public static final int PAD = 28;
	/** Palm wall outer course lines (x = +-WX, north z = WN, south z = WS) and the batter ring one block outside them. */
	public static final int WX = 23, WN = -27, WS = 20;

	static final BlockState SAND = Blocks.SAND.defaultBlockState(), SANDSTONE = Blocks.SANDSTONE.defaultBlockState(),
		SMOOTH = Blocks.SMOOTH_SANDSTONE.defaultBlockState(), CUT = Blocks.CUT_SANDSTONE.defaultBlockState(),
		CHISELED = Blocks.CHISELED_SANDSTONE.defaultBlockState(), SWALL = Blocks.SANDSTONE_WALL.defaultBlockState(),
		MUD = Blocks.MUD_BRICKS.defaultBlockState(), PACKED = Blocks.PACKED_MUD.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState(),
		WATER = Blocks.WATER.defaultBlockState(), GRASS = Blocks.GRASS_BLOCK.defaultBlockState(),
		LAPIS = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE).defaultBlockState(), GOLD = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState(),
		TURQ = Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN).defaultBlockState(), OBSIDIAN = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	static final Block STAIRS = Blocks.SANDSTONE_STAIRS;

	/** Blue glazed terracotta (lapis tiles) by id, so a renamed constant never breaks the build; blue terracotta if absent. */
	static BlockState tile() {
		Block b = CastleStyles.block("minecraft:blue_glazed_terracotta");
		return b != null ? b.defaultBlockState() : LAPIS;
	}

	static BlockState slabB(Block b) {
		return CastleKit.slab(b, SlabType.BOTTOM);
	}

	// ---- dispatch --------------------------------------------------------------------------------------------------

	/** Realm sites known to carry this layout (filled when a part is built; positive answers of {@link #isAzmarSite} are kept too). */
	static final Set<Long> SEEN = ConcurrentHashMap.newKeySet();

	/**
	 * Builds {@code part} with this layout when the placer is an fv 2 (terrain-fit) Azmar realm and the switch is on; returns false (nothing done)
	 * otherwise, so CastleLayout builds the part as before. The towns and the road always stay with CastleLayout.
	 */
	public static boolean build(CastleLayout.Part part, BlockPos o, CastleLayout.Placer pl) {
		if (!ENABLED || pl.ground == null || !isAzmar(pl)) return false;
		switch (part) {
			case CASTLE -> castle(new A(new CastleLayout.L(o, pl)));
			case DUNGEON -> {
				// the crypt under the naos is built with CASTLE (the Aldermark dungeon box lies under this layout's hypostyle)
			}
			case WALL -> palmWall(new A(new CastleLayout.L(o, pl)));
			case KIT_CASTLE -> outside(new A(new CastleLayout.L(o, pl)));
			default -> {
				return false;
			}
		}
		SEEN.add(o.asLong());
		return true;
	}

	/** True when the placer's castle style is Azmar's (or a self-test style named {@code elsewhere:sla_*}). */
	static boolean isAzmar(CastleLayout.Placer pl) {
		if (pl.style == null) return false;
		for (Map.Entry<String, CastleStyles.Style> e : CastleStyles.STYLES.entrySet())
			if (e.getValue() == pl.style) return e.getKey().equals(AZMAR) || e.getKey().startsWith(TEST_PREFIX);
		return false;
	}

	/**
	 * True when {@code site} is an Azmar realm laid out by this class: an fv 2 start (its town floors are registered by CourtWorld when the
	 * start is made or loaded) at the Azmar faction's site, or a site this class has built.
	 */
	public static boolean isAzmarSite(BlockPos site) {
		if (!ENABLED) return false;
		long k = site.asLong();
		if (SEEN.contains(k)) return true;
		if (!CourtWorld.TOWN_FLOORS.containsKey(k)) return false;
		for (Map.Entry<String, BlockPos> e : CourtWorld.FACTION_SITES.entrySet())
			if (site.equals(e.getValue()) && e.getKey().endsWith("|" + AZMAR)) {
				SEEN.add(k);
				return true;
			}
		return false;
	}

	/** Self-test hook: treat {@code site} as an Azmar layout site (its named places move) or forget it again. */
	public static void markTestSite(BlockPos site, boolean on) {
		if (on) SEEN.add(site.asLong());
		else SEEN.remove(site.asLong());
	}

	// ---- named places ----------------------------------------------------------------------------------------------

	/** Aldermark-frame local offset (the literal CastleLayout passes to {@code at}) -> this layout's local offset. */
	static final Map<Long, int[]> REMAP = new HashMap<>();

	static long key(int x, int y, int z) {
		return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
	}

	static void map(int x, int y, int z, int ax, int ay, int az) {
		REMAP.put(key(x, y, z), new int[] {ax, ay, az});
	}

	static {
		map(0, 1, -24, 0, 3, -22); // throne (the seat block on the dais)
		map(0, 0, -21, 0, 2, -19); // before the throne
		map(0, 0, -15, 0, 1, -6); // hall: the hypostyle nave
		map(0, 0, 24, 0, 0, 9); // inside the pylon gate
		map(-8, 0, 8, -8, 0, 9); // drill yard (palm court)
		map(-22, 0, 4, -19, 0, 5); // barracks (west chamber)
		map(-14, 0, 20, -11, 0, 3); // stables -> the Marshal's post in the court
		map(6, 0, 14, 0, 0, 7); // bell (a sound position only)
		map(-9, 5, -17, -6, 2, -17); // balconies -> the throne-hall floor
		map(9, 5, -17, 6, 2, -17);
		map(-12, 9, 29, -7, 16, 15); // wall tops -> the pylon roofs
		map(12, 9, 29, 7, 16, 15);
		map(-10, 0, 6, -11, 0, 7); // yard loop
		map(10, 0, 6, 11, 0, 7);
		map(10, 0, 16, 11, 0, 10);
		map(-10, 0, 16, -11, 0, 10);
		for (int x = -2; x <= 2; x++) map(x, 0, 30, x, 0, 20); // the gate leaves
		map(-6, 0, 20, -3, 0, 11); // braziers
		map(6, 0, 20, 3, 0, 11);
		map(-6, 0, -4, -4, 1, -1);
		map(6, 0, -4, 4, 1, -1);
		map(-3, 0, -6, -11, 0, 1);
		map(3, 0, -6, 11, 0, 1);
		map(-3, 0, -18, -4, 2, -18); // courtiers
		map(3, 0, -18, 4, 2, -18);
		map(-14, 0, -4, -17, 1, -8); // Huntsman's post (west garden)
		map(4, 0, -20, 6, 2, -21); // Chancellor's post (throne hall)
		map(0, 1, -22, -6, 2, -22); // vault chest spot (throne hall)
		map(0, 0, 10, 0, 0, 26); // the "avenue" routine anchor -> the processional way between the obelisks and the sphinxes
	}

	/** CastleLayout.at hook: this layout's world position for a named place of an Azmar layout site, or null (unchanged). */
	public static @Nullable BlockPos place(BlockPos o, int x, int y, int z) {
		if (!ENABLED) return null;
		int[] m = REMAP.get(key(x, y, z));
		if (m == null || !isAzmarSite(o)) return null;
		return o.offset(m[0], m[1], m[2]);
	}

	/** The local offsets this layout gives the named places (self-test). */
	public static List<int[]> remapTargets() {
		return List.copyOf(REMAP.values());
	}

	// ---- writer ----------------------------------------------------------------------------------------------------

	/** Raw local-frame writer (this layout names its blocks; the faction palette is not applied), clipped to the chunk. */
	static final class A {
		final CastleLayout.L l;
		/** Vertical shift of the writes (a sphinx on its own ground, the palace on its terrace). */
		int dy;

		A(CastleLayout.L l) {
			this.l = l;
		}

		void s(int x, int y, int z, BlockState st) {
			l.p.setRaw(l.o.getX() + x, l.o.getY() + y + dy, l.o.getZ() + z, st);
		}

		void s(int x, int y, int z, Block b) {
			s(x, y, z, b.defaultBlockState());
		}

		void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
			for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
					for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) s(x, y, z, st);
		}

		/** The block at a local position, or null outside this chunk. */
		@Nullable BlockState get(int x, int y, int z) {
			int wx = l.o.getX() + x, wy = l.o.getY() + y + dy, wz = l.o.getZ() + z;
			if (!l.p.clip.isInside(wx, wy, wz)) return null;
			return l.p.level.getBlockState(new BlockPos(wx, wy, wz));
		}

		void ifAir(int x, int y, int z, BlockState st) {
			BlockState c = get(x, y, z);
			if (c != null && c.isAir()) s(x, y, z, st);
		}

		/** Fills air, fluid and replaceable blocks under (x, y, z) down to solid ground (at most 48), so nothing above floats. */
		void under(int x, int y, int z, BlockState st) {
			for (int k = 1; k <= 48; k++) {
				BlockState c = get(x, y - k, z);
				if (c == null) return;
				if (!c.isAir() && c.getFluidState().isEmpty() && !c.canBeReplaced()) return;
				s(x, y - k, z, st);
			}
		}

		int gy(int x, int z) {
			return l.gy(x, z);
		}
	}

	static BlockState stair(Direction facing, Half half) {
		return CastleKit.stair(STAIRS, facing, half);
	}

	/** Court paving: smooth sandstone, a cut-sandstone grid every 4 blocks, about 12% plain sandstone. */
	static BlockState paving(int x, int z) {
		if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) return CUT;
		return CastleLayout.hash(x, 9, z) < 12 ? SANDSTONE : SMOOTH;
	}

	/** The ceiling: lapis (blue terracotta) with yellow-terracotta stars. */
	static BlockState sky(int x, int z) {
		return CastleLayout.hash(x, 31, z) < 8 ? GOLD : LAPIS;
	}

	/** Temple wall course: plinth course at {@code base}, glyph frieze, lapis faience band, smooth sandstone. */
	static BlockState templeWall(int y, int base, int frieze, int band) {
		return y == base ? SANDSTONE : y == frieze ? CHISELED : y == band ? LAPIS : SMOOTH;
	}

	/**
	 * A lotus (closed) or papyrus (open, taller, with a lapis ring) column: torus base, a sandstone-wall shaft, a turquoise band under the
	 * capital, and the capital flaring in upside-down stairs.
	 */
	static void column(A a, int x, int z, int yBase, int yCap, boolean papyrus) {
		a.s(x, yBase, z, CUT);
		for (int y = yBase + 1; y <= yCap - 2; y++) a.s(x, y, z, papyrus && y == yCap - 4 ? LAPIS : SWALL);
		a.s(x, yCap - 1, z, TURQ);
		a.s(x, yCap, z, CUT);
		for (Direction d : Direction.Plane.HORIZONTAL) a.s(x + d.getStepX(), yCap, z + d.getStepZ(), stair(d.getOpposite(), Half.TOP));
	}

	/** Date palm: a jungle trunk leaning by (lx, lz) above 4, a crown of persistent fronds (fronds only into air). */
	static void palm(A a, int x, int y0, int z, int lx, int lz) {
		BlockState trunk = Blocks.JUNGLE_LOG.defaultBlockState(), leaf = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int y = 0; y <= 6; y++) {
			int k = y >= 4 ? 1 : 0;
			a.s(x + k * lx, y0 + y, z + k * lz, trunk);
		}
		int tx = x + lx, tz = z + lz, ty = y0 + 7;
		a.ifAir(tx, ty, tz, leaf);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			a.ifAir(tx + d.getStepX(), ty, tz + d.getStepZ(), leaf);
			a.ifAir(tx + 2 * d.getStepX(), ty, tz + 2 * d.getStepZ(), leaf);
			a.ifAir(tx + 3 * d.getStepX(), ty - 1, tz + 3 * d.getStepZ(), leaf);
		}
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			a.ifAir(tx + sx, ty, tz + sz, leaf);
			a.ifAir(tx + 2 * sx, ty - 1, tz + 2 * sz, leaf);
		}
	}

	static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
	}

	static BlockState banner() {
		return Blocks.WALL_BANNER.blue().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH);
	}

	static BlockState brazier() {
		return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
	}

	// ---- CASTLE: pad, terraces, court, pylons, halls -----------------------------------------------------------------

	static void castle(A a) {
		CastleLayout.L l = a.l;
		CastleKit.W w = new CastleKit.W(l, false);
		// the yard pad (same square the generation lane's thin beard levels): no hill or tree crown left over it, solid under it
		l.fill(-PAD, 0, -PAD, PAD, 30, PAD, Blocks.AIR);
		l.fillIfAir(-PAD, -4, -PAD, PAD, -2, PAD, Blocks.SANDSTONE);
		l.foundation(-PAD, -PAD, PAD, PAD, -5, Blocks.SANDSTONE);
		for (int x = -PAD; x <= PAD; x++) for (int z = -PAD; z <= PAD; z++) {
			boolean inside = Math.abs(x) <= 21 && z >= -25 && z <= 18 || Math.abs(x) <= 2 && z >= 12 && z <= 20;
			a.s(x, -1, z, inside ? paving(x, z) : SAND);
		}
		// slope skirt instead of a sheer box cut: the hillside is cut back 1:1 outside the pad, a low side is raised, overhangs cleared
		CastleKit.skirt(l, PAD + 12, (x, z) -> Math.max(Math.abs(x), Math.abs(z)) - PAD, 10, Blocks.SAND, Blocks.SANDSTONE, 30, -1, true);
		terraces(a);
		court(a, w);
		pylons(a, w);
		hypostyle(a, w);
		throneHall(a, w);
		naos(a, w);
		crypt(a);
		palace(a, w, palaceLift(a.l.o, a.l.p.ground));
		// palms: the hypostyle gardens (every 7) and the court
		for (int s = -1; s <= 1; s += 2) {
			palm(a, s * 17, 1, -11, 0, 1);
			palm(a, s * 17, 1, -4, 0, -1);
			palm(a, s * 11, 0, 11, -s, 0);
		}
	}

	/** First terrace (+1: hypostyle and gardens) and top terrace (+2: throne hall, naos, palace), with battered stair edges. */
	static void terraces(A a) {
		a.fill(-21, 0, -14, 21, 0, -1, SANDSTONE);
		for (int x = -21; x <= 21; x++) for (int z = -14; z <= -1; z++) a.s(x, 0, z, Math.abs(x) >= 14 ? GRASS : paving(x, z));
		for (int x = -15; x <= 15; x++) a.s(x, 0, 0, stair(Direction.NORTH, Half.BOTTOM));
		a.fill(-21, 0, -25, 21, 1, -15, SANDSTONE);
		for (int x = -21; x <= 21; x++) for (int z = -25; z <= -15; z++) a.s(x, 1, z, paving(x, z));
		for (int s = -1; s <= 1; s += 2) for (int k = 14; k <= 21; k++) a.s(s * k, 1, -15, stair(Direction.NORTH, Half.BOTTOM));
	}

	/** The palm court: two lapis pools, braziers, the colonnaded porticos and the barracks (west) and armory (east) chambers behind them. */
	static void court(A a, CastleKit.W w) {
		BlockState tile = tile();
		for (int s = -1; s <= 1; s += 2) {
			for (int k = 5; k <= 10; k++) for (int z = 1; z <= 5; z++) {
				boolean rim = k == 5 || k == 10 || z == 1 || z == 5;
				a.s(s * k, -2, z, rim ? LAPIS : tile);
				a.s(s * k, -1, z, rim ? LAPIS : WATER);
			}
			a.s(s * 7, 0, 3, Blocks.LILY_PAD);
			a.s(s * 3, 0, 11, brazier());
			a.s(s * 11, 0, 1, brazier());
			// portico: lotus columns, flat roof with a cavetto cornice toward the court
			for (int z = 1; z <= 10; z += 3) column(a, s * 13, z, 0, 4, false);
			for (int k = 13; k <= 15; k++) for (int z = 1; z <= 11; z++) a.s(s * k, 5, z, SMOOTH);
			for (int z = 1; z <= 11; z++) {
				a.s(s * 12, 4, z, stair(s < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
				a.s(s * 12, 5, z, slabB(Blocks.SMOOTH_SANDSTONE_SLAB));
			}
			// chamber: inner wall x = +-16 with two doors, north wall z = 0, the palm wall behind; flat roof
			for (int z = 0; z <= 18; z++) for (int y = 0; y <= 4; y++) a.s(s * 16, y, z, y == 0 ? SANDSTONE : y == 3 ? LAPIS : MUD);
			for (int k = 17; k <= 21; k++) for (int y = 0; y <= 4; y++) a.s(s * k, y, 0, y == 0 ? SANDSTONE : y == 3 ? LAPIS : MUD);
			for (int z : new int[] {5, 13}) {
				a.s(s * 16, 0, z, AIR);
				a.s(s * 16, 1, z, AIR);
			}
			for (int k = 16; k <= 21; k++) for (int z = 0; z <= 18; z++) a.s(s * k, 5, z, SMOOTH);
			a.s(s * 19, 4, 9, lantern(true));
			for (int k = 14; k <= 15; k++) for (int z = 12; z <= 18; z++) a.s(s * k, -1, z, paving(s * k, z));
		}
		// barracks (west): five beds, barrels and the cache chest
		for (int z = 8; z <= 16; z += 2) CastleLayout.bed(a.l, -21, z, Direction.WEST, Blocks.BED.pick(DyeColor.CYAN));
		a.s(-17, 0, 18, Blocks.BARREL);
		a.s(-18, 0, 18, Blocks.BARREL);
		a.l.chest(-21, 0, 18, Direction.EAST, CastleLayout.LOOT_CACHE);
		// armory (east)
		a.s(21, 0, 2, Blocks.SMITHING_TABLE);
		a.s(21, 0, 4, Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR));
		a.s(21, 0, 6, Blocks.FLETCHING_TABLE);
		a.s(21, 0, 8, Blocks.BARREL);
		a.s(21, 0, 9, Blocks.BARREL);
		a.l.chest(21, 0, 11, Direction.WEST, CastleLayout.LOOT_ARMORY);
	}

	/**
	 * The twin pylons of the gate: trapezoid towers 11 wide and 9 deep, the front and outer faces narrowing 1 block per 4 courses to 16 high,
	 * cut-sandstone course lines and torus edges, a lapis faience band, banner niches with lapis banners, a cavetto cornice (upside-down
	 * stairs), a parapet with a slab lip and an archer's roof; the gateway between them with its own cornice and the gold scarab between
	 * lapis wings; acacia gate leaves.
	 */
	static void pylons(A a, CastleKit.W w) {
		for (int s = -1; s <= 1; s += 2) {
			for (int y = 0; y <= 15; y++) {
				int in = y / 4, xo = 13 - in, zf = 20 - in;
				for (int k = 3; k <= xo; k++) for (int z = 12; z <= zf; z++) {
					boolean face = k == 3 || k == xo || z == 12 || z == zf;
					BlockState b = !face ? SANDSTONE : k == xo && (z == zf || z == 12) ? CUT : y == 12 ? LAPIS : y % 4 == 3 ? CUT : SANDSTONE;
					a.s(s * k, y, z, b);
				}
			}
			int x1 = s < 0 ? -11 : 2, x2 = s < 0 ? -2 : 11;
			CastleKit.ring(w, x1, 11, x2, 18, 14, STAIRS, Half.TOP, CUT, null);
			CastleKit.ringFull(w, x1, 11, x2, 18, 15, CUT, null);
			CastleKit.ringFull(w, x1, 11, x2, 18, 16, CUT, null);
			CastleKit.ringFull(w, x1, 11, x2, 18, 17, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), null);
			// w6-castles (scene 16, L24.1): a kiosk on each pylon roof (pillars stand on the solid core top at y 15; the archers' posts stay clear)
			CastleKit.kiosk(w, s * 6, 14, 16, CUT, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), GOLD);
			for (int k : new int[] {6, 10}) {
				for (int y = 1; y <= 11; y++) a.s(s * k, y, 20 - y / 4, AIR);
				a.s(s * k, 10, 18, banner());
			}
		}
		// gateway: passage x -2..2, y 0..5; the portal above it
		for (int x = -2; x <= 2; x++) for (int z = 12; z <= 20; z++) {
			for (int y = 0; y <= 5; y++) a.s(x, y, z, AIR);
			for (int y = 6; y <= 9; y++) a.s(x, y, z, z == 12 || z == 20 ? (y == 6 ? CUT : SMOOTH) : SANDSTONE);
			a.s(x, -1, z, SMOOTH);
		}
		for (int z : new int[] {12, 20}) {
			a.s(0, 8, z, GOLD);
			for (int x = 1; x <= 2; x++) {
				a.s(x, 8, z, LAPIS);
				a.s(-x, 8, z, LAPIS);
			}
		}
		for (int x = -2; x <= 2; x++) {
			a.s(x, 9, 21, stair(Direction.NORTH, Half.TOP));
			a.s(x, 10, 21, slabB(Blocks.SMOOTH_SANDSTONE_SLAB));
			a.s(x, 9, 11, stair(Direction.SOUTH, Half.TOP));
			a.s(x, 10, 11, slabB(Blocks.SMOOTH_SANDSTONE_SLAB));
			a.s(x, 0, 20, Blocks.ACACIA_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH).setValue(FenceGateBlock.OPEN, true));
		}
	}

	/**
	 * The hypostyle hall on the first terrace (stand y 1): walls x +-13, z -14..-2; 3 ranks of lotus columns (aisles, 6 high) and papyrus
	 * columns (nave, 9 high) in 6 files (5 bays, the middle one the nave); blue ceilings with yellow stars; the raised nave roof over a
	 * stone-grille clerestory; a cavetto cornice; the façade door under the winged scarab; the door up to the throne hall.
	 */
	static void hypostyle(A a, CastleKit.W w) {
		for (int x = -13; x <= 13; x++) for (int z = -14; z <= -2; z++) {
			if (Math.abs(x) != 13 && z != -14 && z != -2) continue;
			int top = Math.abs(x) <= 4 ? 11 : 8;
			for (int y = 1; y <= top; y++) a.s(x, y, z, templeWall(y, 1, 4, 6));
		}
		for (int z : new int[] {-12, -8, -4}) for (int s = -1; s <= 1; s += 2) {
			column(a, s * 3, z, 1, 9, true);
			column(a, s * 7, z, 1, 6, false);
			column(a, s * 11, z, 1, 6, false);
		}
		for (int x = -12; x <= 12; x++) for (int z = -13; z <= -3; z++) if (Math.abs(x) >= 4) a.s(x, 7, z, sky(x, z));
		for (int x = -13; x <= 13; x++) for (int z = -14; z <= -2; z++) if (Math.abs(x) >= 4) a.s(x, 8, z, SMOOTH);
		for (int x = -3; x <= 3; x++) for (int z = -13; z <= -3; z++) a.s(x, 10, z, sky(x, z));
		for (int x = -4; x <= 4; x++) for (int z = -14; z <= -2; z++) a.s(x, 11, z, SMOOTH);
		for (int z = -13; z <= -3; z++) for (int s = -1; s <= 1; s += 2) {
			boolean pier = (z & 1) == 0;
			a.s(s * 4, 9, z, pier ? CUT : AIR);
			a.s(s * 4, 10, z, pier ? CUT : SWALL);
		}
		// façade door, lintel and the gold scarab between lapis wings; the two side doors into the aisles
		a.fill(-2, 1, -2, 2, 5, -2, AIR);
		a.fill(-5, 1, -2, -5, 3, -2, AIR);
		a.fill(5, 1, -2, 5, 3, -2, AIR);
		for (int x = -2; x <= 2; x++) a.s(x, 6, -2, CUT);
		a.s(0, 7, -2, GOLD);
		for (int x = 1; x <= 2; x++) {
			a.s(x, 7, -2, LAPIS);
			a.s(-x, 7, -2, LAPIS);
		}
		// w6-castles (scene 17, L25.1): battered stair piers and blue banners flanking the keep door
		for (int s = -1; s <= 1; s += 2) {
			a.s(s * 3, 1, -1, stair(Direction.NORTH, Half.BOTTOM));
			a.s(s * 3, 3, -1, banner());
		}
		// back door up to the throne hall (one step)
		a.fill(-1, 1, -14, 1, 4, -14, AIR);
		for (int x = -1; x <= 1; x++) a.s(x, 1, -14, stair(Direction.NORTH, Half.BOTTOM));
		// cavetto cornice on the aisle roof (not on the back, which meets the top terrace) and on the nave façade
		CastleKit.ring(w, -14, -15, 14, -1, 8, STAIRS, Half.TOP, CUT, (x, z) -> z == -15);
		CastleKit.ringFull(w, -14, -15, 14, -1, 9, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), (x, z) -> z == -15);
		for (int x = -5; x <= 5; x++) {
			a.s(x, 11, -1, stair(Direction.NORTH, Half.TOP));
			a.s(x, 12, -1, slabB(Blocks.SMOOTH_SANDSTONE_SLAB));
		}
		for (int z = -12; z <= -4; z += 4) a.s(0, 9, z, lantern(true));
		for (int s = -1; s <= 1; s += 2) {
			a.s(s * 9, 6, -8, lantern(true));
			a.s(s * 4, 1, -1, brazier());
		}
	}

	/** The throne hall on the top terrace (stand y 2): ebony throne with gold arms on a blackstone dais, lapis runner, banners, columns, kiosk. */
	static void throneHall(A a, CastleKit.W w) {
		for (int x = -9; x <= 9; x++) for (int z = -25; z <= -15; z++) {
			if (Math.abs(x) != 9 && z != -25 && z != -15) continue;
			for (int y = 2; y <= 10; y++) a.s(x, y, z, y == 10 ? SMOOTH : templeWall(y, 2, 5, 7));
		}
		for (int x = -8; x <= 8; x++) for (int z = -24; z <= -16; z++) a.s(x, 10, z, sky(x, z));
		for (int x = -9; x <= 9; x++) for (int z = -25; z <= -15; z++) a.s(x, 11, z, SMOOTH);
		a.fill(-1, 2, -15, 1, 4, -15, AIR);
		a.fill(-9, 2, -20, -9, 3, -20, AIR);
		a.fill(9, 2, -16, 9, 3, -16, AIR);
		CastleKit.ring(w, -10, -26, 10, -14, 10, STAIRS, Half.TOP, CUT, (x, z) -> z == -26 || z == -14);
		CastleKit.ringFull(w, -10, -26, 10, -14, 11, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), (x, z) -> z == -26 || z == -14);
		a.fill(-2, 2, -23, 2, 2, -21, OBSIDIAN);
		a.s(0, 3, -22, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		a.s(-1, 3, -22, GOLD);
		a.s(1, 3, -22, GOLD);
		for (int z = -20; z <= -16; z++) a.s(0, 2, z, Blocks.CARPET.pick(DyeColor.BLUE));
		for (int s = -1; s <= 1; s += 2) {
			column(a, s * 5, -23, 2, 9, false);
			column(a, s * 5, -19, 2, 9, false);
			a.s(s * 4, 6, -24, banner());
			a.s(s * 3, 9, -18, lantern(true));
		}
		CastleKit.kiosk(w, 0, -20, 12, CUT, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), GOLD);
	}

	/** The naos (sanctuary) west of the throne hall: the lapis-and-gold ark between two guardian stones, the trapdoor to the crypt. */
	static void naos(A a, CastleKit.W w) {
		for (int x = -20; x <= -12; x++) for (int z = -24; z <= -17; z++) {
			if (x != -20 && x != -12 && z != -24 && z != -17) continue;
			for (int y = 2; y <= 7; y++) a.s(x, y, z, templeWall(y, 2, 5, -99));
		}
		for (int x = -20; x <= -12; x++) for (int z = -24; z <= -17; z++) a.s(x, 8, z, SMOOTH);
		CastleKit.ring(w, -21, -25, -11, -16, 7, STAIRS, Half.TOP, CUT, null);
		CastleKit.ringFull(w, -21, -25, -11, -16, 8, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), null);
		a.fill(-12, 2, -20, -12, 3, -20, AIR);
		BlockState tile = tile();
		for (int z = -21; z <= -20; z++) {
			a.s(-19, 2, z, tile);
			a.s(-19, 3, z, GOLD);
		}
		for (int z : new int[] {-22, -19}) {
			a.s(-19, 2, z, CHISELED);
			a.s(-19, 3, z, CHISELED);
		}
		a.s(-16, 2, -18, lantern(false));
		a.s(-13, 1, -23, Blocks.ACACIA_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
	}

	/** The crypt under the naos (the castle's dungeon): sarcophagus, the dungeon chest, a soul lantern, a ladder up to the naos. */
	static void crypt(A a) {
		for (int x = -20; x <= -12; x++) for (int z = -24; z <= -17; z++) for (int y = -7; y <= -2; y++) {
			boolean shell = x == -20 || x == -12 || z == -24 || z == -17 || y == -7 || y == -2;
			a.s(x, y, z, !shell ? AIR : y == -4 ? CHISELED : CUT);
		}
		for (int y = -6; y <= 0; y++) a.s(-13, y, -23, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		a.fill(-18, -6, -21, -16, -6, -20, CUT);
		a.fill(-18, -5, -21, -16, -5, -20, LAPIS);
		a.s(-17, -5, -21, GOLD);
		a.s(-17, -5, -20, GOLD);
		a.l.chest(-19, -6, -18, Direction.EAST, CastleLayout.LOOT_DUNGEON);
		a.s(-14, -6, -19, Blocks.SOUL_LANTERN);
	}

	/**
	 * The palace terrace above the top terrace, in courses of 1 (0 to 3): the median generator ground along the north band minus the south
	 * band, a third of it. Flat land: 0 (the palace stands on the top terrace); a site rising away from the gate: up to 3.
	 */
	public static int palaceLift(BlockPos o, CastleLayout.@Nullable Ground g) {
		if (g == null) return 0;
		int[] n = new int[5], s = new int[5];
		for (int i = 0; i < 5; i++) {
			int x = o.getX() - 16 + 8 * i;
			n[i] = g.y(x, o.getZ() - 26);
			s[i] = g.y(x, o.getZ() + 16);
		}
		Arrays.sort(n);
		Arrays.sort(s);
		return Math.clamp(Math.round((n[2] - s[2]) / 3.0F), 0, 3);
	}

	/** The private palace (x 12..21, z -25..-17): mud brick on its own terrace, stairs up the side lane, a roof terrace with kiosk and awning. */
	static void palace(A a, CastleKit.W w, int pl) {
		int f = 1 + pl;
		if (f >= 2) a.fill(12, 2, -25, 21, f, -17, SANDSTONE);
		for (int x = 12; x <= 21; x++) for (int z = -25; z <= -17; z++) a.s(x, f, z, paving(x, z));
		for (int x = 12; x <= 21; x++) for (int z = -25; z <= -17; z++) {
			if (x != 12 && x != 21 && z != -25 && z != -17) continue;
			for (int y = f + 1; y <= f + 5; y++) a.s(x, y, z, y == f + 1 ? SANDSTONE : y == f + 4 ? LAPIS : MUD);
		}
		for (int x = 12; x <= 21; x++) for (int z = -25; z <= -17; z++) a.s(x, f + 6, z, SMOOTH);
		CastleKit.ringFull(w, 12, -25, 21, -17, f + 7, CUT, null);
		CastleKit.ring(w, 11, -26, 22, -16, f + 6, STAIRS, Half.TOP, CUT, (x, z) -> x == 22 || z == -26);
		CastleKit.ringFull(w, 11, -26, 22, -16, f + 7, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), (x, z) -> x == 22 || z == -26);
		int door = -18 - pl;
		a.fill(12, f + 1, door, 12, f + 2, door, AIR);
		// the side lane (x 10..11) climbs to the door
		for (int x = 10; x <= 11; x++) {
			for (int k = 1; k <= pl; k++) {
				for (int y = 2; y <= k; y++) a.s(x, y, -17 - k, SANDSTONE);
				a.s(x, 1 + k, -17 - k, stair(Direction.NORTH, Half.BOTTOM));
			}
			if (pl > 0) for (int z = door; z >= -25; z--) {
				for (int y = 2; y < f; y++) a.s(x, y, z, SANDSTONE);
				a.s(x, f, z, paving(x, z));
			}
		}
		// rooms
		a.l.dy = f + 1;
		CastleLayout.bed(a.l, 20, -24, Direction.EAST, Blocks.BED.pick(DyeColor.CYAN));
		CastleLayout.bed(a.l, 20, -22, Direction.EAST, Blocks.BED.pick(DyeColor.CYAN));
		a.l.dy = 0;
		a.l.chest(20, f + 1, -19, Direction.WEST, CastleLayout.LOOT_CACHE);
		a.s(13, f + 1, -24, Blocks.BARREL);
		a.s(13, f + 1, -23, Blocks.LOOM);
		a.s(17, f + 5, -21, lantern(true));
		// roof terrace: kiosk and a striped awning on posts
		CastleKit.kiosk(w, 17, -21, f + 7, CUT, slabB(Blocks.SMOOTH_SANDSTONE_SLAB), GOLD);
		for (int z : new int[] {-24, -18}) for (int y = f + 7; y <= f + 8; y++) a.s(13, y, z, Blocks.ACACIA_FENCE);
		for (int x = 13; x <= 14; x++) for (int z = -24; z <= -18; z++)
			a.s(x, f + 9, z, Blocks.WOOL.pick((z & 1) == 0 ? DyeColor.BLUE : DyeColor.WHITE));
	}

	// ---- WALL: the palm wall ---------------------------------------------------------------------------------------

	/**
	 * The enclosure: mud brick (about 15% packed mud), 2 thick, 7 high with the top course undulating +-1 on a slow sine (pan-bedded walls), a
	 * smooth-sandstone cap, the outer course battered (3 courses and a sloping mud-brick stair) over a sandstone batter foot. The south side
	 * runs from the pylons to the corners. On the levelled pad, so nothing floats or is buried.
	 */
	static void palmWall(A a) {
		BlockState foot = SANDSTONE;
		for (int x = -WX - 1; x <= WX + 1; x++) for (int z = WN - 1; z <= WS + 1; z++) {
			int ax = Math.abs(x);
			boolean south = ax >= 14;
			boolean batter = ax == WX + 1 || z == WN - 1 || z == WS + 1 && south;
			boolean outer = !batter && (ax == WX || z == WN || z == WS && south);
			boolean inner = !batter && !outer && (ax == WX - 1 || z == WN + 1 || z == WS - 1 && south);
			if (!batter && !outer && !inner) continue;
			if (batter) {
				boolean bc = ax == WX + 1 && (z == WN - 1 || z == WS + 1);
				a.s(x, 0, z, bc ? SANDSTONE : stair(batterIn(x, z), Half.BOTTOM));
			} else if (outer) {
				for (int y = 0; y <= 2; y++) a.s(x, y, z, mud(x, y, z));
				boolean oc = ax == WX && (z == WN || z == WS);
				a.s(x, 3, z, oc ? MUD : CastleKit.stair(Blocks.MUD_BRICK_STAIRS, batterIn(x, z), Half.BOTTOM));
			} else {
				int top = 6 + undulation(ax == WX - 1 ? z : x);
				for (int y = 0; y < top; y++) a.s(x, y, z, mud(x, y, z));
				a.s(x, top, z, SMOOTH);
			}
			a.under(x, 0, z, foot);
		}
	}

	/** The palm wall's courses: mud brick with about 15% packed mud. */
	static BlockState mud(int x, int y, int z) {
		return CastleLayout.hash(x, y, z) < 15 ? PACKED : MUD;
	}

	/** +-1 on a slow sine (period 24 blocks) along the wall. */
	public static int undulation(int u) {
		return (int) Math.round(Math.sin(u * Math.PI / 12.0));
	}

	/** Facing of a batter or outer-course stair: toward the wall (its tall side against it). */
	static Direction batterIn(int x, int z) {
		if (Math.abs(x) >= WX) return x > 0 ? Direction.WEST : Direction.EAST;
		return z < 0 ? Direction.SOUTH : Direction.NORTH;
	}

	// ---- KIT_CASTLE: outside the walls -----------------------------------------------------------------------------

	/** The sphinx avenue's rows (z of each 2-deep sphinx), both sides. */
	public static final int[] SPHINX_Z = {26, 29, 32, 35};

	/**
	 * The processional way from the gate to the road, the obelisk pair, the sphinx avenue (each sphinx on its own ground), the sacred lake with
	 * its ghat and papyrus beds, and the esplanade palms.
	 */
	static void outside(A a) {
		CastleKit.W w = new CastleKit.W(a.l, false);
		for (int x = -2; x <= 2; x++) for (int z = 21; z <= 30; z++) {
			if (z > PAD) {
				int g = a.gy(x, z);
				a.fill(x, 0, z, x, Math.max(3, g - 1), z, AIR);
				a.under(x, -1, z, SANDSTONE);
			}
			a.s(x, -1, z, Math.abs(x) == 2 ? CUT : SMOOTH);
		}
		for (int s = -1; s <= 1; s += 2) CastleKit.obelisk(w, s * 6, 23, CUT, CHISELED, GOLD, SMOOTH);
		for (int s = -1; s <= 1; s += 2) for (int z0 : SPHINX_Z) sphinx(a, s, z0);
		lake(a);
		palm(a, -11, 0, 24, 0, 1);
		palm(a, -18, 0, 24, 0, -1);
		palm(a, -25, 0, 25, 1, 0);
		palm(a, 24, 0, 25, -1, 0);
	}

	/**
	 * The y (relative to the floor) of the plinth of the sphinx on side {@code s} at row {@code z0}, or Integer.MIN_VALUE when it is skipped
	 * (water or lava under it, or its ground drops more than 3). On the pad: 0. Outside: the highest ground minus 1 (buried at most 1).
	 */
	public static int sphinxBase(BlockPos o, CastleLayout.@Nullable Ground g, int s, int z0) {
		if (z0 + 1 <= PAD || g == null) return 0;
		int hi = Integer.MIN_VALUE, lo = Integer.MAX_VALUE;
		for (int k = 3; k <= 7; k++) for (int z = z0; z <= z0 + 1; z++) {
			int wx = o.getX() + s * k, wz = o.getZ() + z, y = g.y(wx, wz) - o.getY();
			hi = Math.max(hi, y);
			lo = Math.min(lo, y);
			if (CastleLayout.wet(g, wx, wz)) return Integer.MIN_VALUE;
		}
		if (hi - lo > 6) return Integer.MIN_VALUE; // w6-castles: was 3, which skipped three of the four rows on a hillside (the plinth is underpinned to the ground)
		return hi > lo ? hi - 1 : hi;
	}

	/** A couchant sphinx facing the avenue: lapis-fronted plinth, paws, chest, body and haunch, head with its headdress. */
	static void sphinx(A a, int s, int z0) {
		int base = sphinxBase(a.l.o, a.l.p.ground, s, z0);
		if (base == Integer.MIN_VALUE) return;
		Direction toHead = s > 0 ? Direction.WEST : Direction.EAST;
		a.dy = base;
		for (int k = 3; k <= 7; k++) for (int z = z0; z <= z0 + 1; z++) {
			a.under(s * k, 0, z, SANDSTONE);
			a.s(s * k, 0, z, k == 3 ? LAPIS : SMOOTH);
		}
		for (int z = z0; z <= z0 + 1; z++) {
			a.s(s * 3, 1, z, slabB(Blocks.SMOOTH_SANDSTONE_SLAB));
			a.s(s * 4, 1, z, SANDSTONE);
			a.s(s * 5, 1, z, SANDSTONE);
			a.s(s * 6, 1, z, SANDSTONE);
			a.s(s * 7, 1, z, stair(toHead, Half.BOTTOM));
			a.s(s * 4, 2, z, CHISELED);
			a.s(s * 5, 2, z, stair(toHead, Half.BOTTOM));
			a.s(s * 4, 3, z, slabB(Blocks.CUT_SANDSTONE_SLAB));
		}
		a.dy = 0;
	}

	/**
	 * The sacred lake east of the avenue (on the pad): a lapis-walled basin tiled in blue glazed terracotta, water 2 deep below the paving, the
	 * ghat (waterlogged steps) on its west side, papyrus (sugar cane on sand) in its long rims. Every water block touches only water, a
	 * waterlogged step or the basin's own blocks.
	 */
	static void lake(A a) {
		BlockState tile = tile(), wet = stair(Direction.WEST, Half.BOTTOM).setValue(StairBlock.WATERLOGGED, true);
		for (int x = 11; x <= 20; x++) for (int z = 22; z <= 27; z++) {
			boolean rim = x == 11 || x == 20 || z == 22 || z == 27;
			a.s(x, -3, z, rim ? LAPIS : tile);
			if (rim) {
				a.s(x, -2, z, LAPIS);
				a.s(x, -1, z, LAPIS);
			} else if (x == 12) {
				a.s(x, -2, z, SANDSTONE);
				a.s(x, -1, z, wet);
			} else if (x == 13) {
				a.s(x, -2, z, wet);
				a.s(x, -1, z, WATER);
			} else {
				a.s(x, -2, z, WATER);
				a.s(x, -1, z, WATER);
			}
		}
		for (int x : new int[] {14, 15, 17, 18}) for (int z : new int[] {22, 27}) {
			a.s(x, -1, z, SAND);
			a.s(x, 0, z, Blocks.SUGAR_CANE);
			a.s(x, 1, z, Blocks.SUGAR_CANE);
		}
		for (int z = 22; z <= 27; z++) a.s(10, -1, z, SMOOTH);
	}
}
