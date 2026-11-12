package dev.elsewhere.verdigris_ironpeak.world.fit;

import dev.elsewhere.boss.galvanox.GalvanoxArena;
import dev.elsewhere.boss.hrolf.ProvingGroundsArena;
import dev.elsewhere.boss.thurgrim.ThurgrimArena;
import dev.elsewhere.boss.verdigrim.VerdigrimArena;
import dev.elsewhere.ironpeak.IronpeakBlocks;
import dev.elsewhere.stub.verdigris_ironpeak.ReturnRiftStub;
import dev.elsewhere.verdigris_ironpeak.Overhaul3;
import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import dev.elsewhere.verdigris_ironpeak.world.GenFit;
import dev.elsewhere.verdigris_ironpeak.world.ProceduralStructure;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Elsewhere 3.0 verdigris_ironpeak structure looks and per-piece ground (STRUCTURES.md sections 2.6, 2.9 and 4; s-struct-verdigris).
 * <ul>
 * <li>Every new start gets a version-2 piece that carries its own ground model ({@link Terrain}, sampled from the generator on a 4-block
 * lattice when the start is made). Builders read the ground from it, never from the live world, so all chunks agree.</li>
 * <li>Foundations are matching-block ladders down to real ground (cap 12, then piers), cuts are soft banks, not box clears.</li>
 * <li>Starts saved by 2.x keep their {@code vi_procedural_piece} without {@code v} and finish with the 2.x blueprint, block for block.</li>
 * <li>Boss arenas keep every functional block (rods, lantern, organ, bells, conduits, drum, obelisks, horn) at the same offsets;
 * the fit adds bases, buttresses, banks, terraces and seating around them.</li>
 * </ul>
 * Kill switch: {@code -Delsewhere.overhaul3.vi_structures=false} or {@code "vi_structures": false} in {@code config/elsewhere-overhaul3.json}
 * (new starts then use the 2.x blueprints and site code again).
 */
public final class FitBuilds {
	private FitBuilds() {}

	public static final boolean ENABLED = Overhaul3.on("vi_structures");
	/** Verdigris Sea: sea_level 96, so the top water block is y 95. */
	public static final int SEA_TOP = 95;

	public static boolean applies(String blueprint) {
		return ENABLED && Blueprint.FIT.containsKey(blueprint);
	}

	public static void init() {
		Blueprint.FIT.put("clan_hold", fit(21, 12, 14, FitBuilds::clanHold));
		Blueprint.FIT.put("climbers_cairn", fit(6, 9, 14, FitBuilds::cairn));
		Blueprint.FIT.put("clockwork_rig", fit(14, 12, 130, FitBuilds::rig));
		Blueprint.FIT.put("engineers_atoll", fit(22, 8, 30, FitBuilds::atoll));
		Blueprint.FIT.put("tidelock_vault", fit(10, 9, 16, FitBuilds::vault));
		Blueprint.FIT.put("drowned_cathedral", fit(VerdigrimArena.RADIUS + 9, VerdigrimArena.HEIGHT + 2, 24, FitBuilds::cathedral));
		Blueprint.FIT.put("lighthouse", fit(GalvanoxArena.RADIUS + 4, GalvanoxArena.HEIGHT, 160, FitBuilds::lighthouse));
		Blueprint.FIT.put("proving_grounds", fit(ProvingGroundsArena.RADIUS + 8, ProvingGroundsArena.HEIGHT + 6, 18, FitBuilds::provingGrounds));
		Blueprint.FIT.put("summit_throne", fit(ThurgrimArena.RADIUS + 14, ThurgrimArena.HEIGHT, 18, FitBuilds::summitThrone));
		Blueprint.FIT.put("return_rift_ironpeak", fit(9, 7, 14, FitBuilds::riftIronpeak));
		Blueprint.FIT.put("return_rift_verdigris_sea", fit(9, 7, 120, FitBuilds::riftSea));
	}

	interface Build {
		void build(BlockSink s);
	}

	static Blueprint fit(int radius, int height, int depth, Build b) {
		return new Blueprint() {
			public int radius() {
				return radius;
			}

			public int height() {
				return height;
			}

			public int depth() {
				return depth;
			}

			public void build(BlockSink s, RandomSource r) {
				b.build(s);
			}
		};
	}

	// ------------------------------------------------------------------------------------------------ site choice (new starts only)

	static final int[][] OFFSETS = {{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}, {12, 12}, {-12, 12}, {12, -12}, {-12, -12}};

	public static Optional<Structure.GenerationStub> findGenerationPoint(ProceduralStructure st, Structure.GenerationContext ctx) {
		String name = st.blueprint();
		Blueprint bp = Blueprint.FIT.get(name);
		ChunkPos cp = ctx.chunkPos();
		int mx = cp.getMiddleBlockX(), mz = cp.getMiddleBlockZ();
		var gen = ctx.chunkGenerator();
		// rarity as in 2.1: the start chunk's middle must be in a valid biome
		int g0 = gen.getFirstOccupiedHeight(mx, mz, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
		if (!ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(mx >> 2, g0 >> 2, mz >> 2))) return Optional.empty();
		int[] at = switch (name) {
			case "lighthouse" -> lighthouseSite(ctx, mx, mz);
			case "clockwork_rig" -> g0 <= SEA_TOP - 8 ? new int[] {mx, mz, SEA_TOP + 1} : null;
			case "return_rift_verdigris_sea" -> new int[] {mx, mz, Math.max(SEA_TOP + 4, g0 + 2)};
			default -> latticeSite(st, ctx, mx, mz);
		};
		if (at == null) return Optional.empty();
		BlockPos origin = new BlockPos(at[0], at[1], at[2]);
		boolean sea = st.placement().equals("ocean_floor") || name.equals("lighthouse") || name.equals("clockwork_rig") || name.equals("engineers_atoll")
			|| name.equals("return_rift_verdigris_sea");
		return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(
			ProceduralStructure.Piece.fit(name, origin, bp, Terrain.sample(ctx, origin.getX(), origin.getZ(), bp.radius() + 2, sea)))));
	}

	/** GenFit's lattice test (cut, fill, wet, biome at centre and rim) with a per-kit preference; returns {x, y, z}. */
	static int[] latticeSite(ProceduralStructure st, Structure.GenerationContext ctx, int mx, int mz) {
		String name = st.blueprint();
		int lo = st.minHeight(), hi = st.maxHeight();
		GenFit.Limits lim = switch (name) {
			// the 2.x/3.0 GenFit limits (cut 6, fill 8; the foundation ladder takes the fill), so density and map targets stay as before
			case "clan_hold" -> GenFit.Limits.surface(7, 6, 8, 15, lo, hi);
			case "climbers_cairn" -> GenFit.Limits.surface(3, 6, 8, 15, lo, hi);
			case "engineers_atoll" -> GenFit.Limits.surface(5, 6, 8, 45, lo, hi);
			case "proving_grounds", "summit_throne" -> GenFit.Limits.surface(12, 6, 8, 15, lo, hi);
			// the sea-bed spread test over the nave's 12-block core; buttresses and the foundation carry the rest to the bed
			case "drowned_cathedral" -> new GenFit.Limits(12, 6, 8, 15, lo, hi, true);
			case "tidelock_vault" -> new GenFit.Limits(5, 6, 8, 15, lo, hi, true);
			default -> GenFit.Limits.surface(4, 6, 8, 15, lo, hi);
		};
		boolean highPoint = name.equals("climbers_cairn") || name.equals("summit_throne");
		int[][] offs = OFFSETS;
		GenFit.Site best = null;
		int bestScore = Integer.MAX_VALUE;
		for (int[] o : offs) {
			GenFit.Site s = GenFit.evaluate(ctx, mx + o[0], mz + o[1], lim, Math.abs(o[0]) + Math.abs(o[1]));
			if (s == null) continue;
			int score = switch (name) {
				// a high point: the highest floor wins (a rock top for the cairn, the true summit cap for the throne)
				case "climbers_cairn", "summit_throne" -> -4 * s.floor() + s.range();
				// earth-sheltered: a moderate slope to cut the hall into
				case "clan_hold" -> 3 * Math.abs(s.range() - 4) + s.wetPct() / 10 + (Math.abs(o[0]) + Math.abs(o[1])) / 8;
				// ground rising to the north: seating (proving grounds) or the buried back (vault) goes uphill
				case "proving_grounds", "tidelock_vault" -> s.score() - 2 * Math.max(-6, Math.min(6, northRise(ctx, s.x(), s.z(), lim.radius())));
				default -> s.score();
			};
			if (score < bestScore) {
				best = s;
				bestScore = score;
			}
			if (!highPoint && !name.equals("clan_hold") && s.range() <= 2 && (lim.underwater() || s.wetPct() == 0)) break;
		}
		if (best == null) return null;
		return new int[] {best.x(), best.floor() + st.yOffset(), best.z()};
	}

	static int northRise(Structure.GenerationContext ctx, int x, int z, int r) {
		var gen = ctx.chunkGenerator();
		int n = gen.getFirstOccupiedHeight(x, z - r, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
		int s = gen.getFirstOccupiedHeight(x, z + r, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
		return n - s;
	}

	/** The lighthouse takes the highest islet or shoal near the start; the platform stands at least 24 above the sea and 12 above that rock. */
	static int[] lighthouseSite(Structure.GenerationContext ctx, int mx, int mz) {
		var gen = ctx.chunkGenerator();
		int[] best = null;
		for (int[] o : OFFSETS) {
			int cx = mx + o[0], cz = mz + o[1], top = Integer.MIN_VALUE;
			for (int[] p : new int[][] {{0, 0}, {7, 0}, {-7, 0}, {0, 7}, {0, -7}, {5, 5}, {-5, 5}, {5, -5}, {-5, -5}})
				top = Math.max(top, gen.getFirstOccupiedHeight(cx + p[0], cz + p[1], Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState()));
			if (!ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(cx >> 2, top >> 2, cz >> 2))) continue;
			if (best == null || top > best[3]) best = new int[] {cx, Math.max(SEA_TOP + 25, top + 12), cz, top};
		}
		return best == null ? null : new int[] {best[0], best[1], best[2]};
	}

	/**
	 * Floor cells {dx, dy, dz} that must stand on continuous support down to the ground (self-test float metric, STRUCTURES.md 2.16):
	 * the floor of every hall, hut, chamber, nave, arena and dais, and the lighthouse tower's wall ring. Empty for kits on piles by design.
	 */
	public static List<int[]> floorCells(String blueprint, BlockSink s) {
		List<int[]> out = new ArrayList<>();
		switch (blueprint) {
			case "clan_hold" -> {
				Direction f = downhill(s, 9);
				for (int u = -5; u <= 5; u++) for (int w = -7; w <= 12; w++) {
					int[] d = rot(f, u, w);
					out.add(new int[] {d[0], 0, d[1]});
				}
			}
			case "climbers_cairn" -> {
				for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (x * x + z * z <= 4 && s.ground(x, z) <= 0) out.add(new int[] {x, 0, z});
			}
			case "engineers_atoll" -> square(out, 3, 0);
			case "tidelock_vault" -> square(out, 4, 0);
			case "return_rift_ironpeak" -> square(out, 2, -1);
			case "drowned_cathedral", "proving_grounds", "summit_throne" -> {
				double r = blueprint.equals("drowned_cathedral") ? VerdigrimArena.RADIUS + 1.6 : blueprint.equals("proving_grounds") ? ProvingGroundsArena.RADIUS + 1.5 : ThurgrimArena.RADIUS + 0.5;
				int y = blueprint.equals("drowned_cathedral") ? -3 : 0;
				for (int x = -(int) r; x <= r; x++) for (int z = -(int) r; z <= r; z++) if (Math.sqrt(x * x + z * z) <= r) out.add(new int[] {x, y, z});
			}
			case "lighthouse" -> {
				int TR = GalvanoxArena.TOWER_RADIUS;
				for (int x = -TR; x <= TR; x++) for (int z = -TR; z <= TR; z++) {
					double d = Math.sqrt(x * x + z * z);
					if (d > TR - 1.2 && d <= TR + 0.3 && !(z == 0 && x >= TR - 1)) out.add(new int[] {x, -1, z});
				}
			}
			default -> {}
		}
		return out;
	}

	static void square(List<int[]> out, int r, int y) {
		for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) out.add(new int[] {x, y, z});
	}

	// ------------------------------------------------------------------------------------------------ helpers

	static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	static BlockState stair(Block b, Direction facing, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slabTop(Block b) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
	}

	static BlockState pipe(Direction.Axis axis) {
		return VerdigrisBlocks.VERDIGRIS_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	/** Stable per-block variation from the start origin (no Math.random, no iteration order). */
	static int hash(BlockSink s, int dx, int dy, int dz) {
		long h = s.origin.asLong() * 0x9E3779B97F4A7C15L + dx * 73856093L + dy * 19349663L + dz * 83492791L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) (h & 0x7fffffff);
	}

	static BlockState rubble(BlockSink s, int dx, int dy, int dz) {
		return switch (hash(s, dx, dy, dz) % 7) {
			case 0, 1 -> st(Blocks.MOSSY_COBBLESTONE);
			case 2 -> st(Blocks.ANDESITE);
			case 3 -> st(Blocks.TUFF);
			default -> st(Blocks.COBBLESTONE);
		};
	}

	static Direction outward(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** A pile (log or post) from {@code dy} down to bearing ground; no cap (piles are posts by design). */
	static void pile(BlockSink s, int dx, int dy, int dz, BlockState state) {
		for (int y = dy; y > dy - 160; y--) {
			BlockPos p = s.at(dx, y, dz);
			if (!s.inside(p)) return;
			if (y < dy && BlockSink.bearing(s.level.getBlockState(p))) return;
			s.set(dx, y, dz, state);
		}
	}

	// ------------------------------------------------------------------------------------------------ Clan Hold (Ironpeak)

	/**
	 * An earth-sheltered hall cut into the slope: back and sides banked with the hill's own soil, a steep spruce roof with the ridge
	 * running down the hall, a timber gable front facing downhill with a porch and a paved terrace with steps to the ground.
	 * Orientation comes from the saved ground model (the front faces the lowest side). The quartermaster's spot (+2, 1, +2) stays free.
	 */
	static void clanHold(BlockSink s) {
		Direction f = downhill(s, 9);
		BlockState cobble = st(Blocks.COBBLESTONE), bricks = st(Blocks.STONE_BRICKS), stone = st(Blocks.STONE), planks = st(Blocks.SPRUCE_PLANKS);
		BlockState post = st(Blocks.STRIPPED_SPRUCE_LOG);
		// 1. ground: floor, porch and terrace on their own foundations; the front area cut as a soft bank
		for (int u = -6; u <= 6; u++) for (int w = -8; w <= 12; w++) {
			int[] d = rot(f, u, w);
			if (Math.abs(u) <= 5 && w >= -7) {
				s.underpin(d[0], -1, d[1], bricks, cobble, stone, 24); // the terrace's retaining wall on the downhill side
				s.set(d[0], 0, d[1], w >= 9 ? terracePave(s, d[0], d[1]) : w >= 6 ? planks : (Math.abs(u) == 5 || w == -7 ? cobble : planks));
			}
			if (w >= 5) s.trim(d[0], d[1], Math.abs(u) <= 5 ? 0 : Math.abs(u) - 5, 12);
		}
		// banks beside the terrace fall away gently instead of a sheer cut
		for (int u : new int[] {-8, -7, 7, 8}) for (int w = 5; w <= 12; w++) {
			int[] d = rot(f, u, w);
			s.trim(d[0], d[1], Math.abs(u) - 5, 12);
		}
		// 2. walls: a stone course, spruce planks between stripped posts; the back wall is stone (it is in the hill)
		for (int u = -5; u <= 5; u++) for (int w = -7; w <= 5; w++) {
			boolean side = Math.abs(u) == 5, back = w == -7, front = w == 5;
			if (!side && !back && !front) continue;
			int[] d = rot(f, u, w);
			for (int y = 1; y <= 3; y++) {
				BlockState b;
				if (back) b = hash(s, u, y, w) % 5 == 0 ? st(Blocks.MOSSY_COBBLESTONE) : cobble;
				else if (front) b = Math.abs(u) == 5 || Math.abs(u) == 2 ? post : planks;
				else b = (w == -7 || w == -3 || w == 1 || w == 5) ? post : y == 1 ? (hash(s, u, y, w) % 4 == 0 ? st(Blocks.MOSSY_COBBLESTONE) : cobble) : planks;
				s.set(d[0], y, d[1], b);
			}
		}
		// 3. interior air (only where the hill stood), under the roof
		for (int u = -4; u <= 4; u++) for (int w = -6; w <= 4; w++) {
			int[] d = rot(f, u, w);
			for (int y = 1; y < roofY(u); y++) s.hollow(d[0], y, d[1]);
		}
		// 4. the roof: steep spruce stairs, ridge of stripped logs down the hall, overhanging the back and the porch
		for (int u = -6; u <= 6; u++) for (int w = -8; w <= 8; w++) {
			int[] d = rot(f, u, w);
			int y = roofY(u);
			if (u == 0) s.set(d[0], y, d[1], st(Blocks.STRIPPED_SPRUCE_LOG).setValue(RotatedPillarBlock.AXIS, (f.getAxis() == Direction.Axis.X) ? Direction.Axis.X : Direction.Axis.Z));
			else s.set(d[0], y, d[1], stair(Blocks.SPRUCE_STAIRS, u < 0 ? rotDir(f, Direction.EAST) : rotDir(f, Direction.WEST), false));
		}
		// 5. the timber gable: planks between the roof slopes, a king post, crossed finials at both ends of the ridge
		for (int u = -4; u <= 4; u++) for (int y = 4; y < roofY(u); y++) {
			int[] d = rot(f, u, 5);
			s.set(d[0], y, d[1], u == 0 ? post : planks);
			int[] b = rot(f, u, -7);
			s.set(b[0], y, b[1], cobble);
		}
		for (int w : new int[] {-8, 8}) {
			int[] d = rot(f, 0, w);
			s.set(d[0], roofY(0) + 1, d[1], st(Blocks.DARK_OAK_FENCE));
			int[] l = rot(f, -1, w), r = rot(f, 1, w);
			s.set(l[0], roofY(-1) + 1, l[1], st(Blocks.DARK_OAK_FENCE));
			s.set(r[0], roofY(1) + 1, r[1], st(Blocks.DARK_OAK_FENCE));
		}
		// door and shuttered windows in the gable
		int[] door = rot(f, 0, 5);
		BlockState dr = st(Blocks.SPRUCE_DOOR).setValue(DoorBlock.FACING, f).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
		s.set(door[0], 1, door[1], dr.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		s.set(door[0], 2, door[1], dr.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		for (int u : new int[] {-3, 3}) {
			int[] d = rot(f, u, 5);
			s.set(d[0], 2, d[1], st(Blocks.SPRUCE_FENCE));
		}
		// 6. porch: posts and rails under the roof overhang
		for (int u : new int[] {-4, 4}) {
			int[] d = rot(f, u, 8);
			for (int y = 1; y < roofY(u); y++) s.set(d[0], y, d[1], post);
			for (int w = 6; w <= 7; w++) {
				int[] rl = rot(f, u, w);
				s.set(rl[0], 1, rl[1], st(Blocks.SPRUCE_FENCE));
			}
		}
		for (int u = -3; u <= 3; u++) for (int w = 6; w <= 8; w++) {
			int[] d = rot(f, u, w);
			for (int y = 1; y < roofY(u); y++) s.hollow(d[0], y, d[1]);
		}
		// 7. terrace: paved, low stone-brick parapet on the downhill edge, steps down to the ground, a prayer-flag pole
		for (int u = -5; u <= 5; u++) {
			int[] d = rot(f, u, 12);
			if (Math.abs(u) > 1 && s.ground(d[0], d[1]) < -1) s.set(d[0], 1, d[1], st(Blocks.STONE_BRICK_WALL));
		}
		for (int u = -1; u <= 1; u++) {
			for (int k = 1; k <= 8; k++) {
				int[] d = rot(f, u, 12 + k);
				int y = 1 - k;
				if (s.ground(d[0], d[1]) >= y) break;
				s.set(d[0], y, d[1], stair(Blocks.STONE_BRICK_STAIRS, f.getOpposite(), false));
				s.underpin(d[0], y - 1, d[1], cobble, cobble, stone, 12);
			}
		}
		int[] pole = rot(f, 4, 11);
		for (int y = 1; y <= 4; y++) s.set(pole[0], y, pole[1], st(Blocks.SPRUCE_FENCE));
		s.set(pole[0], 5, pole[1], st(IronpeakBlocks.PRAYER_FLAGS));
		// 8. earth shelter: the hill's own soil banked against both sides and the back, up to the eaves
		for (int u = -9; u <= 9; u++) for (int w = -11; w <= 4; w++) {
			int du = Math.max(0, Math.abs(u) - 5), dw = Math.max(0, -7 - w);
			if (du == 0 && dw == 0) continue;
			int top = 4 - Math.max(du, dw);
			if (top < 1) continue;
			int[] d = rot(f, u, w);
			s.berm(d[0], d[1], top);
		}
		// 9. inside: the hearthstone at the back (no lit fire), benches along the walls, a long table, lanterns from the ridge, the chest
		int[] hearth = rot(f, 0, -5);
		s.set(hearth[0], 1, hearth[1], st(IronpeakBlocks.HEARTHSTONE));
		for (int u : new int[] {-1, 1}) {
			int[] d = rot(f, u, -5);
			s.set(d[0], 1, d[1], st(Blocks.STONE_BRICKS));
		}
		for (int w = -4; w <= 2; w++) {
			int[] l = rot(f, -4, w), r = rot(f, 4, w);
			s.set(l[0], 1, l[1], stair(Blocks.SPRUCE_STAIRS, rotDir(f, Direction.WEST), false));
			s.set(r[0], 1, r[1], stair(Blocks.SPRUCE_STAIRS, rotDir(f, Direction.EAST), false));
		}
		for (int w = -3; w <= 1; w++) {
			int[] d = rot(f, 0, w);
			s.set(d[0], 1, d[1], slabTop(Blocks.SPRUCE_SLAB));
		}
		for (int w : new int[] {-4, 1}) {
			int[] d = rot(f, 0, w);
			s.set(d[0], roofY(0) - 1, d[1], st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		}
		int[] chest = rot(f, -3, -6);
		s.chest(chest[0], 1, chest[1], f, BlockSink.loot("chests/clan_hold"));
		int[] flags = rot(f, 3, -6);
		s.set(flags[0], 1, flags[1], st(IronpeakBlocks.PRAYER_FLAGS));
	}

	static int roofY(int u) {
		return 3 + (6 - Math.abs(u));
	}

	static BlockState terracePave(BlockSink s, int dx, int dz) {
		return switch (hash(s, dx, 0, dz) % 6) {
			case 0 -> st(Blocks.GRAVEL);
			case 1, 2 -> st(Blocks.COBBLESTONE);
			case 3 -> st(Blocks.MOSSY_STONE_BRICKS);
			default -> st(Blocks.STONE_BRICKS);
		};
	}

	/** The cardinal side whose ground (r blocks out) is lowest: the front faces downhill. */
	static Direction downhill(BlockSink s, int r) {
		Direction best = Direction.SOUTH;
		int lo = Integer.MAX_VALUE;
		for (Direction d : new Direction[] {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
			int g = s.ground(d.getStepX() * r, d.getStepZ() * r) + s.ground(d.getStepX() * r + d.getStepZ() * 4, d.getStepZ() * r + d.getStepX() * 4)
				+ s.ground(d.getStepX() * r - d.getStepZ() * 4, d.getStepZ() * r - d.getStepX() * 4);
			if (g < lo) {
				lo = g;
				best = d;
			}
		}
		return best;
	}

	/** Local (u across, w toward the front) to (dx, dz) for a front facing {@code f}. */
	static int[] rot(Direction f, int u, int w) {
		return switch (f) {
			case NORTH -> new int[] {-u, -w};
			case EAST -> new int[] {w, -u};
			case WEST -> new int[] {-w, u};
			default -> new int[] {u, w};
		};
	}

	/** A direction given in the SOUTH-facing local frame, turned for a front facing {@code f}. */
	static Direction rotDir(Direction f, Direction local) {
		return switch (f) {
			case NORTH -> local.getOpposite();
			case EAST -> local.getCounterClockWise();
			case WEST -> local.getClockWise();
			default -> local;
		};
	}

	// ------------------------------------------------------------------------------------------------ Climber's Cairn (Ironpeak)

	/** Stacked stones on a rock high point, every base stone on its own ground, a chest at the foot and a waymark pole on top. */
	static void cairn(BlockSink s) {
		for (int y = 0; y <= 4; y++) {
			double r = 2.6 - 0.5 * y;
			int ri = (int) Math.ceil(r);
			for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
				if (x * x + z * z > r * r) continue;
				int h = hash(s, x, y, z);
				if (y > 0 && h % 5 == 0 && (x != 0 || z != 0)) continue; // gaps between stacked stones
				int g = s.ground(x, z);
				if (y == 0) s.underpin(x, -1, z, st(Blocks.COBBLESTONE), st(Blocks.COBBLESTONE), st(Blocks.STONE), 12);
				if (y == 0 && g > 0) continue; // rock already there
				s.set(x, y, z, switch (h % 4) {
					case 0 -> st(Blocks.MOSSY_COBBLESTONE);
					case 1 -> st(Blocks.ANDESITE);
					case 2 -> st(Blocks.STONE);
					default -> st(Blocks.COBBLESTONE);
				});
			}
		}
		for (int y = 5; y <= 7; y++) s.set(0, y, 0, st(Blocks.SPRUCE_FENCE));
		s.set(0, 8, 0, st(IronpeakBlocks.PRAYER_FLAGS));
		s.set(1, 7, 0, st(Blocks.SPRUCE_FENCE));
		s.set(1, 6, 0, st(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
		s.underpin(0, -1, -3, st(Blocks.COBBLESTONE), st(Blocks.COBBLESTONE), st(Blocks.STONE), 12);
		s.chest(0, 0, -3, Direction.NORTH, BlockSink.loot("chests/climbers_cairn"));
	}

	// ------------------------------------------------------------------------------------------------ Clockwork Rig (Verdigris Sea)

	/** Deck at sea level +4 on four splayed copper-pipe legs braced every 8 blocks, down to the sea bed; a ladder to the water. */
	static void rig(BlockSink s) {
		int deck = 4;
		for (int[] l : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			int prev = 5;
			for (int y = deck - 1; y > deck - 128; y--) {
				int o = Math.min(10, 5 + (deck - y) / 6);
				int x = l[0] * o, z = l[1] * o;
				if (y < s.ground(x, z) - 8) break;
				BlockPos p = s.at(x, y, z);
				if (!s.inside(p)) continue;
				if (y < deck - 1 && BlockSink.bearing(s.level.getBlockState(p))) break;
				s.set(x, y, z, pipe(Direction.Axis.Y));
				if (o != prev) s.set(l[0] * prev, y, l[1] * prev, VerdigrisBlocks.VERDIGRIS_PIPE_JUNCTION.defaultBlockState());
				prev = o;
				// bracing frames at the waterline and every 8 below it
				if ((deck - y) % 8 == 5 && y > s.ground(0, 0) + 2) {
					for (int t = -o + 1; t < o; t++) {
						s.set(t, y, l[1] * o, pipe(Direction.Axis.X));
						s.set(l[0] * o, y, t, pipe(Direction.Axis.Z));
					}
				}
			}
		}
		for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
			boolean rim = Math.abs(x) == 6 || Math.abs(z) == 6;
			s.set(x, deck, z, rim ? st(VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE) : st(VerdigrisBlocks.VERDIGRIS_GRATING));
			for (int y = deck + 1; y <= deck + 4; y++) s.hollow(x, y, z);
			if (rim && !(x == 0 && z == 6)) s.set(x, deck + 1, z, st(VerdigrisBlocks.VERDIGRIS_PLATE_WALL));
		}
		for (int[] b : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) s.set(b[0], deck + 2, b[1], Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(CopperBulbBlock.LIT, true));
		s.chest(0, deck + 1, 0, Direction.SOUTH, BlockSink.loot("chests/clockwork_rig"));
		s.set(2, deck + 1, 0, st(VerdigrisBlocks.STORM_COIL));
		s.set(-2, deck + 1, 2, st(VerdigrisBlocks.BARNACLE_CLUSTER));
		// mast with a lightning rod and a crane boom over the water
		for (int y = deck + 1; y <= deck + 6; y++) s.set(0, y, -4, pipe(Direction.Axis.Y));
		s.set(0, deck + 7, -4, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
		for (int x = 1; x <= 8; x++) s.set(x, deck + 5, -4, pipe(Direction.Axis.X));
		s.set(8, deck + 4, -4, pipe(Direction.Axis.Y));
		s.set(8, deck + 3, -4, st(VerdigrisBlocks.VERDIGRIS_PIPE_JUNCTION));
		// boarding ladder on the south edge, from the water to the deck
		for (int y = -1; y < deck; y++) s.set(0, y, 6, st(VerdigrisBlocks.VERDIGRIS_PLATE));
		for (int y = -1; y <= deck; y++) s.set(0, y, 7, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
	}

	// ------------------------------------------------------------------------------------------------ Engineer's Atoll (Verdigris Sea)

	/** The engineer's hut on a plinth, a jetty on log piles from the hut to deep water, and a short tuff quay along the shore. */
	static void atoll(BlockSink s) {
		BlockState planks = st(VerdigrisBlocks.KELPWOOD_PLANKS), log = st(VerdigrisBlocks.KELPWOOD_LOG);
		for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
			s.underpin(x, -1, z, st(Blocks.TUFF_BRICKS), st(Blocks.TUFF), st(Blocks.COBBLESTONE), 12);
			s.set(x, 0, z, planks);
			boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3, corner = Math.abs(x) == 3 && Math.abs(z) == 3;
			for (int y = 1; y <= 3; y++) {
				if (!edge) s.hollow(x, y, z);
				else if (z == 3 && x == 0 && y <= 2) s.hollow(x, y, z);
				else s.set(x, y, z, corner ? log : (y == 2 && (x == 0 || z == 0) ? st(Blocks.GLASS_PANE) : planks));
			}
			s.set(x, 4, z, st(VerdigrisBlocks.VERDIGRIS_PLATE_SLAB));
		}
		// a raised plate eave and a copper weathervane
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (Math.abs(x) == 4 || Math.abs(z) == 4) s.set(x, 3, z, stair(VerdigrisBlocks.VERDIGRIS_PLATE_STAIRS, outward(x, z).getOpposite(), true));
		s.set(0, 5, 0, pipe(Direction.Axis.Y));
		s.set(0, 6, 0, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
		s.set(0, 3, 0, Blocks.COPPER_LANTERN.weathering().unaffected().defaultBlockState().setValue(LanternBlock.HANGING, true));
		s.chest(2, 1, -2, Direction.WEST, BlockSink.loot("chests/engineers_atoll"));
		// the jetty runs toward the nearest deep water
		Direction best = null;
		int bestDist = 99;
		for (Direction d : new Direction[] {Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.NORTH}) {
			for (int k = 4; k <= 20; k++) if (s.water(d.getStepX() * k, d.getStepZ() * k) - s.ground(d.getStepX() * k, d.getStepZ() * k) >= 2) {
				if (k < bestDist) {
					bestDist = k;
					best = d;
				}
				break;
			}
		}
		if (best != null) {
			Direction d = best;
			int sea = s.water(d.getStepX() * bestDist, d.getStepZ() * bestDist);
			int deckY = 0, end = Math.min(21, bestDist + 5);
			// a doorway on the jetty side
			for (int y = 1; y <= 2; y++) s.hollow(d.getStepX() * 3, y, d.getStepZ() * 3);
			for (int k = 4; k <= end; k++) {
				int target = Math.max(sea + 1, Math.min(0, s.ground(d.getStepX() * k, d.getStepZ() * k) + 1));
				int ny = Math.max(target, deckY - 1);
				boolean step = ny < deckY;
				deckY = ny;
				for (int a = -1; a <= 1; a++) {
					int x = d.getStepX() * k + d.getStepZ() * a, z = d.getStepZ() * k + d.getStepX() * a;
					s.set(x, deckY, z, step ? stair(VerdigrisBlocks.KELPWOOD_STAIRS, d.getOpposite(), false) : planks);
					for (int y = deckY + 1; y <= deckY + 3; y++) s.hollow(x, y, z);
					boolean wetHere = s.wet(x, z);
					if (a != 0 && (k % 3 == 0 || k == end)) pile(s, x, deckY - 1, z, log);
					else if (!wetHere) s.underpin(x, deckY - 1, z, st(Blocks.TUFF), st(Blocks.TUFF), st(Blocks.COBBLESTONE), 12);
					if (a != 0 && wetHere) s.set(x, deckY + 1, z, st(VerdigrisBlocks.KELPWOOD_FENCE));
				}
			}
			int ex = d.getStepX() * end, ez = d.getStepZ() * end;
			for (int a : new int[] {-1, 1}) {
				int x = ex + d.getStepZ() * a, z = ez + d.getStepX() * a;
				s.set(x, deckY + 2, z, st(VerdigrisBlocks.KELPWOOD_FENCE));
				s.set(x, deckY + 3, z, Blocks.COPPER_LANTERN.weathering().unaffected().defaultBlockState());
			}
		}
		// quay: where dry land meets water within 9 blocks, a tuff-brick edge at the waterline with a bollard every few blocks
		for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
			if (Math.abs(x) <= 4 && Math.abs(z) <= 4) continue;
			if (x * x + z * z > 81 || s.wet(x, z)) continue;
			boolean shore = s.wet(x + 1, z) || s.wet(x - 1, z) || s.wet(x, z + 1) || s.wet(x, z - 1);
			if (!shore) continue;
			int g = realTop(s, x, z, s.ground(x, z));
			if (g == Integer.MIN_VALUE) continue;
			s.set(x, g, z, st(Blocks.TUFF_BRICKS));
			if (hash(s, x, 0, z) % 5 == 0) s.set(x, g + 1, z, st(VerdigrisBlocks.KELPWOOD_FENCE));
		}
	}

	// ------------------------------------------------------------------------------------------------ Tidelock Vault (Verdigris Sea)

	/** The 2.x chamber, set into a sea-bed slope on purpose: sediment banked over the back and sides, a plated portal facing downslope. */
	static void vault(BlockSink s) {
		// the 2.x chamber (same blocks, same vault offset 0,1,-3), on a matching-block foundation instead of an uncapped tuff pillar
		BlockState plate = st(VerdigrisBlocks.VERDIGRIS_PLATE), riveted = st(VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE);
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
			s.underpin(x, -1, z, riveted, st(Blocks.TUFF), st(Blocks.COBBLED_DEEPSLATE), 12);
			s.set(x, 0, z, riveted);
			boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
			for (int y = 1; y <= 4; y++) s.set(x, y, z, edge && !(z == 4 && x == 0 && y <= 2) ? (y == 3 && (x + z) % 2 == 0 ? st(VerdigrisBlocks.VERDIGRIS_GRATING) : plate) : st(Blocks.WATER));
			s.set(x, 5, z, st(VerdigrisBlocks.VERDIGRIS_PLATE_SLAB));
		}
		for (int[] b : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) s.set(b[0], 1, b[1], st(VerdigrisBlocks.VERDIGRIS_PIPE));
		s.vault(0, 1, -3, Direction.SOUTH, VerdigrisItems.TIDE_KEY, BlockSink.loot("chests/tidelock_vault"));
		s.set(0, 4, -3, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(CopperBulbBlock.LIT, true));
		s.set(-2, 1, -3, st(VerdigrisBlocks.BARNACLE_CLUSTER));
		s.set(2, 1, -3, st(VerdigrisBlocks.STORM_COIL));
		// sediment over the roof's back half and against the sides and back: the vault sits in the slope
		for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
			int dx = Math.max(0, Math.abs(x) - 4), dzb = Math.max(0, -4 - z), dzf = Math.max(0, z - 4);
			if (dzf > 0 && Math.abs(x) <= 3) continue; // the portal side stays open
			if (dx == 0 && dzb == 0 && dzf == 0) {
				// on the roof: one layer of sediment over the back half
				BlockPos p = s.at(x, 6, z);
				if (z <= 1 && s.inside(p) && (s.level.getBlockState(p).isAir() || s.level.getBlockState(p).is(Blocks.WATER))) s.set(x, 6, z, sediment(s, x, z));
				continue;
			}
			int top = 5 - Math.max(dx, Math.max(dzb, dzf)) * 2;
			if (top < 1) continue;
			mound(s, x, z, top);
		}
		// the portal: riveted pilasters, a plate lintel, two lit bulbs, a plated apron down to the bed
		for (int x : new int[] {-2, 2}) {
			for (int y = 1; y <= 4; y++) s.set(x, y, 5, st(VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE));
			s.underpin(x, 0, 5, st(VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE), st(Blocks.TUFF), st(Blocks.COBBLED_DEEPSLATE), 12);
		}
		for (int x = -2; x <= 2; x++) s.set(x, 5, 5, stair(VerdigrisBlocks.VERDIGRIS_PLATE_STAIRS, Direction.NORTH, true));
		for (int x : new int[] {-3, 3}) s.set(x, 3, 5, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(CopperBulbBlock.LIT, true));
		for (int x = -1; x <= 1; x++) for (int z = 5; z <= 8; z++) {
			int g = Math.min(0, s.ground(x, z) + 1);
			s.set(x, g, z, st(VerdigrisBlocks.VERDIGRIS_PLATE));
			s.underpin(x, g - 1, z, st(Blocks.TUFF), st(Blocks.TUFF), st(Blocks.COBBLED_DEEPSLATE), 12);
		}
	}

	/** Raises this column with the bed's own sediment (sand, gravel, clay...) up to {@code top}, under water or on land. */
	static void mound(BlockSink s, int dx, int dz, int top) {
		int g = realTop(s, dx, dz, s.ground(dx, dz));
		if (g == Integer.MIN_VALUE) return;
		BlockState soil = sediment(s, dx, dz);
		for (int y = g + 1; y <= top; y++) {
			BlockPos p = s.at(dx, y, dz);
			if (!s.inside(p)) return;
			BlockState old = s.level.getBlockState(p);
			if (!old.isAir() && !old.canBeReplaced() && !old.is(Blocks.WATER)) continue;
			s.set(dx, y, dz, soil);
		}
	}

	/** The bed's own top block in this column (sand, gravel, clay, mud...), gravel when it cannot be read. */
	static BlockState sediment(BlockSink s, int dx, int dz) {
		int g = realTop(s, dx, dz, s.ground(dx, dz));
		if (g == Integer.MIN_VALUE) return st(Blocks.GRAVEL);
		BlockState b = s.level.getBlockState(s.at(dx, g, dz));
		return b.is(Blocks.GRASS_BLOCK) ? st(Blocks.DIRT) : b;
	}

	/**
	 * The real top bearing block of this column near the model height {@code g} (the lattice is interpolated, so it can be a few blocks
	 * off on rough ground). Reads only inside this chunk; MIN_VALUE when the column is outside it.
	 */
	static int realTop(BlockSink s, int dx, int dz, int g) {
		for (int y = g + 4; y >= g - 8; y--) {
			BlockPos p = s.at(dx, y, dz);
			if (!s.inside(p)) {
				if (y == g + 4) return Integer.MIN_VALUE;
				continue;
			}
			if (BlockSink.bearing(s.level.getBlockState(p))) return y;
		}
		return g;
	}

	// ------------------------------------------------------------------------------------------------ Drowned Cathedral (Verdigrim)

	/** The 2.x nave (an arena: one floor level), underpinned to the bed, with 10 stepped buttresses down to the sea floor. */
	static void cathedral(BlockSink s) {
		VerdigrimArena.BLUEPRINT.build(s, s.random);
		int R = VerdigrimArena.RADIUS;
		for (int x = -R - 2; x <= R + 2; x++) for (int z = -R - 2; z <= R + 2; z++) {
			if (Math.sqrt(x * x + z * z) > R + 1.6) continue;
			s.underpin(x, -4, z, st(Blocks.PRISMARINE_BRICKS), st(Blocks.TUFF), st(Blocks.COBBLED_DEEPSLATE), 12);
		}
		// organ bases were copper blocks (9 ingots each): waxed cut copper looks the same and is not a free resource
		for (int i = 0; i < VerdigrimArena.PIPES; i++) s.set(VerdigrimArena.pipeX(i), 0, -11, Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState());
		for (int k = 0; k < 12; k++) {
			double a = Math.toRadians(30 * k + 1.5);
			if (Math.abs(Math.toDegrees(a) - 90) < 16) continue; // the south doors
			for (int t = 1; t <= 4; t++) {
				int top = 13 - 3 * t;
				for (double da : new double[] {-0.05, 0, 0.05}) {
					int x = (int) Math.round(Math.cos(a + da) * (R + 1.5 + t)), z = (int) Math.round(Math.sin(a + da) * (R + 1.5 + t));
					for (int y = top - 1; y >= -3; y--) s.set(x, y, z, y % 4 == 0 ? st(Blocks.PRISMARINE_BRICKS) : st(Blocks.DARK_PRISMARINE));
					s.set(x, top, z, stair(Blocks.PRISMARINE_BRICK_STAIRS, outward(-x, -z), false));
					s.underpin(x, -4, z, st(Blocks.PRISMARINE_BRICKS), st(Blocks.TUFF), st(Blocks.COBBLED_DEEPSLATE), 12);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ Lighthouse (Galvanox)

	/** The 2.x platform and lantern spire on a banded tower that stands on a rubble sea stack, rooted on the bed; a door and landing at the water. */
	static void lighthouse(BlockSink s) {
		int TR = GalvanoxArena.TOWER_RADIUS, R = GalvanoxArena.RADIUS;
		BlockState white = st(Blocks.CALCITE), teal = Blocks.CUT_COPPER.weathering().oxidized().defaultBlockState();
		int sea = s.water(0, 0);
		int landing = Math.max(sea + 1, s.ground(TR + 2, 0) + 1);
		// 1. the sea stack: rubble from the bed up past the waterline, widest at the water, every column on its own ground
		for (int x = -TR - 4; x <= TR + 4; x++) for (int z = -TR - 4; z <= TR + 4; z++) {
			double d = Math.sqrt(x * x + z * z);
			if (d > TR + 4.3) continue;
			int g = s.ground(x, z);
			int top = d <= TR + 0.3 ? landing - 1 : Math.min(landing - 1, sea + 2 - (int) Math.round((d - TR) * 1.5));
			if (top <= g) continue;
			for (int y = g + 1; y <= top; y++) s.set(x, y, z, rubble(s, x, y, z));
			s.underpin(x, g, z, rubble(s, x, g, z), rubble(s, x, g, z), rubble(s, x, g, z), 12);
			if (d > TR + 0.3 && top >= sea && hash(s, x, top, z) % 9 == 0) s.set(x, top + 1, z, st(VerdigrisBlocks.BARNACLE_CLUSTER));
		}
		// 2. the banded tower from the landing to the platform, hollow, with a ladder
		for (int y = -1; y >= landing; y--) {
			for (int x = -TR; x <= TR; x++) for (int z = -TR; z <= TR; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > TR + 0.3) continue;
				if (d > TR - 1.2) s.set(x, y, z, ((-y) / 4) % 2 == 0 ? teal : white);
				else s.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
			s.set(0, y, TR - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
			s.set(0, y, TR - 1, white);
		}
		for (int x = -TR; x <= TR; x++) for (int z = -TR; z <= TR; z++) if (x * x + z * z <= (TR - 1.2) * (TR - 1.2)) s.set(x, landing - 1, z, st(VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE));
		// the sea door on the east side (the ladder's wall stays whole) and its plated landing
		for (int y = landing; y <= landing + 1; y++) for (int x = TR - 1; x <= TR; x++) s.set(x, y, 0, Blocks.AIR.defaultBlockState());
		for (int z = -1; z <= 1; z++) for (int x = TR + 1; x <= TR + 3; x++) {
			s.set(x, landing - 1, z, st(VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE));
			s.hollow(x, landing, z);
			s.hollow(x, landing + 1, z);
		}
		// 3. the 2.x platform: copper floor with the prismarine cross, plate rim and wall, 10 rods (pedestals in cut copper, not copper blocks)
		for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
			double d = Math.sqrt(x * x + z * z);
			if (d > R + 1.5) continue;
			boolean cross = x == 0 || z == 0;
			s.set(x, 0, z, cross ? st(Blocks.DARK_PRISMARINE) : ((x + z) & 1) == 0 ? Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState() : Blocks.CUT_COPPER.waxed().weathered().defaultBlockState());
			if (d > TR - 1.2) s.set(x, -1, z, st(VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE));
			// corbels under the overhang: upside-down plate stairs
			if (d > TR + 0.3 && d <= R + 1.5 && d > R - 1.5) s.set(x, -2, z, stair(VerdigrisBlocks.VERDIGRIS_PLATE_STAIRS, outward(x, z).getOpposite(), true));
			for (int y = 1; y <= GalvanoxArena.HEIGHT; y++) s.hollow(x, y, z);
			if (d > R + 0.5) s.set(x, 1, z, st(VerdigrisBlocks.VERDIGRIS_PLATE_WALL));
		}
		s.set(0, 0, TR - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		s.set(0, 1, TR - 2, Blocks.AIR.defaultBlockState());
		for (int i = 0; i < GalvanoxArena.RODS; i++) {
			BlockPos p = GalvanoxArena.rod(s.origin, i);
			int dx = p.getX() - s.origin.getX(), dz = p.getZ() - s.origin.getZ();
			s.set(dx, 1, dz, Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState());
			s.set(dx, 2, dz, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
		}
		int[] L = GalvanoxArena.LANTERN;
		for (int y = 1; y < L[1]; y++) s.set(L[0], y, L[2], pipe(Direction.Axis.Y));
		s.set(L[0], L[1], L[2], st(VerdigrisBlocks.LIGHTHOUSE_LANTERN));
		s.set(L[0], L[1] + 1, L[2], Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
		for (int[] c : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) s.set(L[0] + c[0], L[1], L[2] + c[1], Blocks.COPPER_GRATE.waxed().unaffected().defaultBlockState());
	}

	// ------------------------------------------------------------------------------------------------ Proving Grounds (Hrolf)

	/** The 2.x shelf colosseum as a ledge cut into the mountain: the cliff edge to the south on a stone foundation, stone tiers uphill. */
	static void provingGrounds(BlockSink s) {
		int R = ProvingGroundsArena.RADIUS;
		BlockState bricks = st(Blocks.STONE_BRICKS), cobble = st(Blocks.COBBLESTONE), stone = st(Blocks.STONE);
		for (int x = -R - 8; x <= R + 8; x++) for (int z = -R - 8; z <= R + 8; z++) {
			double d = Math.sqrt(x * x + z * z);
			if (d <= R + 1.5) {
				s.underpin(x, -1, z, bricks, cobble, stone, 12);
				s.set(x, 0, z, d < 4 ? st(Blocks.POLISHED_ANDESITE) : ((int) d) % 4 == 0 ? st(Blocks.POLISHED_GRANITE)
					: hash(s, x, 0, z) % 6 == 0 ? st(Blocks.ANDESITE) : bricks);
				for (int y = 1; y <= ProvingGroundsArena.HEIGHT; y++) s.hollow(x, y, z);
				if (d > R + 0.4 && !ProvingGroundsArena.isOpenEdge(x, z)) {
					s.set(x, 1, z, st(Blocks.IRON_BARS));
					s.set(x, 2, z, st(Blocks.IRON_BARS));
				}
			} else if (d <= R + 8) {
				int t = (int) Math.floor(d - R - 1.5) + 1; // 1..7 out from the rim
				boolean stands = z < 0 && Math.abs(x) < -z * 1.3 && t <= 5;
				if (stands) {
					// spectator tiers: one stone row per block outward, each a step higher, on the uphill side
					s.trim(x, z, t, 14);
					s.underpin(x, t - 1, z, cobble, cobble, stone, 12);
					s.set(x, t, z, stair(Blocks.STONE_BRICK_STAIRS, outward(x, z), false));
					for (int y = t + 1; y <= t + 3; y++) s.hollow(x, y, z);
				} else if (!ProvingGroundsArena.isOpenEdge(x, z)) {
					s.trim(x, z, t, 14); // a sloped bank, not a cylinder cut
				}
			}
		}
		for (int[] p : new int[][] {{8, -8}, {-8, -8}, {9, 6}, {-9, 6}}) {
			for (int y = 1; y <= 3; y++) s.set(p[0], y, p[1], st(Blocks.SPRUCE_LOG));
			s.set(p[0], 4, p[1], Blocks.BANNER.pick(DyeColor.RED).defaultBlockState().setValue(BannerBlock.ROTATION, 0));
			s.set(p[0], 0, p[1], st(Blocks.POLISHED_ANDESITE));
		}
		int[] H = ProvingGroundsArena.HORN;
		s.set(H[0], H[1], H[2], st(IronpeakBlocks.PROVING_CHALLENGE_HORN));
		s.set(-3, 1, -13, IronpeakBlocks.PRAYER_FLAGS.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST));
		s.set(3, 1, -13, st(IronpeakBlocks.HEARTHSTONE));
	}

	// ------------------------------------------------------------------------------------------------ Summit Throne (Thurgrim)

	/** The 2.x ice plateau on the summit cap: an ice and frostgranite plinth to the rock, a bank instead of a cylinder, waymark obelisks on the ridge points. */
	static void summitThrone(BlockSink s) {
		int R = ThurgrimArena.RADIUS;
		BlockState ice = st(Blocks.PACKED_ICE);
		for (int x = -R - 7; x <= R + 7; x++) for (int z = -R - 7; z <= R + 7; z++) {
			double d = Math.sqrt(x * x + z * z);
			if (d <= R + 0.5) {
				s.underpin(x, -1, z, ice, st(IronpeakBlocks.FROSTGRANITE_BRICKS), st(IronpeakBlocks.FROSTGRANITE), 12);
				int h = hash(s, x, 0, z);
				s.set(x, 0, z, d < 5 ? st(IronpeakBlocks.POLISHED_FROSTGRANITE) : h % 5 == 0 ? st(Blocks.SNOW_BLOCK) : h % 3 == 0 ? st(IronpeakBlocks.GLACIER_ICE) : ice);
				for (int y = 1; y <= ThurgrimArena.HEIGHT; y++) s.hollow(x, y, z);
			} else if (d <= R + 7) {
				s.trim(x, z, (int) Math.floor(d - R - 0.5), 16);
			}
		}
		// the six storm obelisks (the boss strikes them): same offsets, frostgranite and deepslate (no iron blocks), each on an ice plinth
		for (int i = 0; i < ThurgrimArena.OBELISKS; i++) {
			BlockPos p = ThurgrimArena.obelisk(s.origin, i);
			int dx = p.getX() - s.origin.getX(), dz = p.getZ() - s.origin.getZ();
			for (int y = 1; y <= 6; y++) s.set(dx, y, dz, y == 6 ? st(Blocks.CHISELED_DEEPSLATE) : st(Blocks.POLISHED_DEEPSLATE));
			s.set(dx, 7, dz, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
			for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
				s.set(dx + dir.getStepX(), 1, dz + dir.getStepZ(), stair(IronpeakBlocks.POLISHED_FROSTGRANITE_STAIRS, dir.getOpposite(), false));
		}
		for (int x = -2; x <= 2; x++) {
			s.set(x, 1, -14, st(IronpeakBlocks.FROSTGRANITE_BRICKS));
			s.set(x, 2, -15, st(IronpeakBlocks.FROSTGRANITE_BRICKS));
			s.set(x, 3, -15, Math.abs(x) == 2 ? st(Blocks.CHISELED_DEEPSLATE) : st(IronpeakBlocks.FROSTGRANITE_BRICKS));
		}
		s.set(0, 1, -13, st(IronpeakBlocks.POLISHED_FROSTGRANITE_STAIRS));
		int[] D = ThurgrimArena.DRUM;
		s.set(D[0], D[1], D[2], st(IronpeakBlocks.SUMMIT_WAR_DRUM));
		for (int[] f : new int[][] {{-5, -12}, {5, -12}}) {
			s.set(f[0], 1, f[1], st(Blocks.SPRUCE_FENCE));
			s.set(f[0], 2, f[1], st(IronpeakBlocks.PRAYER_FLAGS));
		}
		// waymark obelisks on the ridge points around the cap (highest lattice points 22-28 out, at least 12 apart)
		if (s.terrain != null) {
			List<int[]> marks = new ArrayList<>();
			for (int k = 0; k < 8 && marks.size() < 3; k++) {
				double a = Math.toRadians(45 * k + 10);
				int cx = s.origin.getX() + (int) Math.round(Math.cos(a) * 25), cz = s.origin.getZ() + (int) Math.round(Math.sin(a) * 25);
				int[] h = s.terrain.highest(cx, cz, 4);
				if (h[2] == Integer.MIN_VALUE) continue;
				boolean far = true;
				for (int[] m : marks) if ((m[0] - h[0]) * (m[0] - h[0]) + (m[1] - h[1]) * (m[1] - h[1]) < 144) far = false;
				int g = h[2] - s.origin.getY();
				double dist = Math.sqrt((h[0] - s.origin.getX()) * (double) (h[0] - s.origin.getX()) + (h[1] - s.origin.getZ()) * (double) (h[1] - s.origin.getZ()));
				if (far && g >= -6 && dist >= R + 8) marks.add(new int[] {h[0], h[1], g});
			}
			marks.sort((m1, m2) -> Integer.compare(m2[2], m1[2]));
			for (int[] m : marks) {
				int dx = m[0] - s.origin.getX(), dz = m[1] - s.origin.getZ(), g = s.ground(dx, dz);
				s.underpin(dx, g, dz, ice, ice, st(IronpeakBlocks.FROSTGRANITE), 12);
				s.set(dx, g + 1, dz, ice);
				for (int y = g + 2; y <= g + 5; y++) s.set(dx, y, dz, st(IronpeakBlocks.FROSTGRANITE_BRICKS));
				s.set(dx, g + 6, dz, st(IronpeakBlocks.FROSTGRANITE_BRICK_WALL));
				s.set(dx, g + 7, dz, st(IronpeakBlocks.PRAYER_FLAGS));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ Return Rifts

	/** Ironpeak: eight ring stones, each on its own ground and leaning outward, around a small dais with the rift. */
	static void riftIronpeak(BlockSink s) {
		ring(s, st(Blocks.POLISHED_ANDESITE), st(Blocks.STONE_BRICKS), st(Blocks.STONE_BRICK_STAIRS), false);
	}

	/** Verdigris Sea: the dais and every ring stone stand on their own kelpwood piles down to the sea bed. */
	static void riftSea(BlockSink s) {
		ring(s, Blocks.CUT_COPPER.weathering().oxidized().defaultBlockState(), st(Blocks.PRISMARINE_BRICKS), st(Blocks.PRISMARINE_BRICK_STAIRS), true);
	}

	static void ring(BlockSink s, BlockState floor, BlockState pillar, BlockState lean, boolean piles) {
		BlockState log = st(VerdigrisBlocks.KELPWOOD_LOG);
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
			if (piles) {
				if (Math.abs(x) == 2 && Math.abs(z) == 2 || x == 0 && z == 0) pile(s, x, -2, z, log);
			} else {
				s.underpin(x, -2, z, pillar, pillar, st(Blocks.COBBLESTONE), 12);
			}
			s.set(x, -1, z, (x + z) % 2 == 0 ? floor : pillar);
			for (int y = 0; y <= 3; y++) s.hollow(x, y, z);
		}
		s.set(0, -1, 0, st(Blocks.CHISELED_STONE_BRICKS));
		s.set(0, 0, 0, ReturnRiftStub.RETURN_RIFT.defaultBlockState());
		s.set(0, 1, 0, ReturnRiftStub.RETURN_RIFT.defaultBlockState());
		for (int k = 0; k < 8; k++) {
			double a = Math.toRadians(45 * k + 22.5);
			int x = (int) Math.round(Math.cos(a) * 5), z = (int) Math.round(Math.sin(a) * 5);
			int base = piles ? -1 : s.ground(x, z) + 1;
			if (piles) pile(s, x, base - 1, z, log);
			else s.underpin(x, base - 1, z, pillar, pillar, st(Blocks.COBBLESTONE), 12);
			int h = 2 + hash(s, x, 0, z) % 3;
			for (int y = 0; y < h; y++) s.set(x, base + y, z, y == h - 1 && hash(s, x, y, z) % 2 == 0 ? st(Blocks.CRACKED_STONE_BRICKS) : pillar);
			// a lean: the cap stone tips outward
			Direction out = outward(x, z);
			if (lean.hasProperty(StairBlock.FACING)) s.set(x + out.getStepX(), base + h - 1, z + out.getStepZ(), lean.setValue(StairBlock.FACING, out.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
		}
	}
}
