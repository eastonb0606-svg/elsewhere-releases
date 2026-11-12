package dev.elsewhere.overworld_bosses.fit3;

import dev.elsewhere.overworld_bosses.OverworldBossesContent;
import dev.elsewhere.overworld_bosses.OwbBlocks;
import dev.elsewhere.overworld_bosses.world.BlockSink;
import dev.elsewhere.overworld_bosses.world.Blueprints;
import dev.elsewhere.overworld_bosses.world.OwbPiece;
import dev.elsewhere.overworld_bosses.world.OwbWorldgen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Self-test for the 3.0 boss-site fit (s-struct-overworld), called from the module self-test:
 * <ul>
 * <li>{@code owb_fit synthetic}: each site planned and built chunk by chunk on synthetic terrain (hillside glacier, wet and dry swamp,
 * deep ocean, desert): trigger block at its offset, no valuable blocks, no lit campfires, nothing floating (every column's lowest
 * structural block rests on ground; deck and walk planks between piles excepted), pool water enclosed, arena standing room, light and
 * block-entity budgets, same result in two chunk orders;</li>
 * <li>{@code owb_fit nbt}: piece NBT round trip, new piece type id, the 2.x piece types still registered;</li>
 * <li>{@code owb_fit planner}: real noise on 3 seeds: every start is fit pieces, covers its start chunk, the plateau cut/fill stays within
 * 12, walks at most 20, noise samples per site (HeightField budget), kill switch gives the frozen pieces, sun_chamber survival after the cave rule;</li>
 * <li>{@code owb_fit world}: generated sites in the test world: trigger block placed, and the Beardifier carries the shelf's beard and
 * the chamber's encapsulation (the mixin is live).</li>
 * </ul>
 */
public final class FitSelfTest {
	private FitSelfTest() {}

	private static BiConsumer<Boolean, String> out;

	private static void check(boolean ok, String what) {
		out.accept(ok, what);
	}

	public static void run(ServerLevel level, BiConsumer<Boolean, String> check) {
		out = check;
		synthetic();
		nbt(level);
		planner(level);
		world(level);
	}

	// ---- synthetic terrain ----------------------------------------------------------------------------------------------------

	/** Terrain from a ground function (first free y) with water up to sea level, plus whatever the pieces wrote. */
	static final class World {
		final Map<Long, BlockState> blocks = new HashMap<>();
		final Set<Long> written = new HashSet<>();
		final IntBinaryOperator ground;
		final int sea;
		final BlockState top, fill;

		World(IntBinaryOperator ground, int sea, BlockState top, BlockState fill) {
			this.ground = ground;
			this.sea = sea;
			this.top = top;
			this.fill = fill;
		}

		BlockState get(int x, int y, int z) {
			BlockState s = this.blocks.get(BlockPos.asLong(x, y, z));
			if (s != null) return s;
			int g = this.ground.applyAsInt(x, z);
			if (y < g - 1) return this.fill;
			if (y == g - 1) return this.top;
			return y < this.sea ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		OwbFit.Heights heights() {
			return new OwbFit.Heights() {
				@Override
				public int ground(int x, int z) {
					return World.this.ground.applyAsInt(x, z);
				}

				@Override
				public int surface(int x, int z) {
					return Math.max(World.this.ground.applyAsInt(x, z), World.this.sea);
				}
			};
		}

		/** Builds every piece chunk by chunk (writes clipped to the chunk and the piece box, reads outside the chunk = stone), like worldgen. */
		void build(List<StructurePiece> pieces, boolean reverse) {
			BoundingBox all = null;
			for (StructurePiece p : pieces) all = all == null ? p.getBoundingBox() : BoundingBox.encapsulating(all, p.getBoundingBox());
			List<int[]> chunks = new ArrayList<>();
			for (int cx = all.minX() >> 4; cx <= all.maxX() >> 4; cx++) for (int cz = all.minZ() >> 4; cz <= all.maxZ() >> 4; cz++) chunks.add(new int[] {cx, cz});
			if (reverse) java.util.Collections.reverse(chunks);
			for (int[] c : chunks) {
				int x0 = c[0] << 4, z0 = c[1] << 4;
				for (StructurePiece sp : pieces) {
					OwbFitPiece p = (OwbFitPiece) sp;
					BoundingBox box = p.getBoundingBox();
					if (!box.intersects(x0, z0, x0 + 15, z0 + 15)) continue;
					FitBuilds.build(p, new BlockSink() {
						boolean in(int x, int z) {
							return x >= x0 && x <= x0 + 15 && z >= z0 && z <= z0 + 15;
						}

						@Override
						public void set(BlockPos pos, BlockState state) {
							if (in(pos.getX(), pos.getZ()) && box.isInside(pos)) {
								World.this.blocks.put(pos.asLong(), state);
								World.this.written.add(pos.asLong());
							}
						}

						@Override
						public BlockState get(BlockPos pos) {
							return in(pos.getX(), pos.getZ()) ? World.this.get(pos.getX(), pos.getY(), pos.getZ()) : Blocks.STONE.defaultBlockState();
						}

						@Override
						public boolean writes(int x, int z) {
							return in(x, z) && x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
						}
					});
				}
			}
		}
	}

	private static boolean structural(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && s.isSolid() && !FitBuilds.soft(s);
	}

	private static final Block[] VALUABLE = {Blocks.GOLD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.IRON_BLOCK,
		Blocks.NETHERITE_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.COPPER_BLOCK.waxed().unaffected(), Blocks.AMETHYST_BLOCK};

	/** Checks one planned site on its synthetic world; span() marks deck/walk plank blocks that legitimately sit between piles. */
	private static void site(String name, List<StructurePiece> pieces, World w, BlockPos trigger, Block triggerBlock, BlockPos stand,
		java.util.function.Predicate<BlockState> span, boolean waterBelowOk) {
		w.build(pieces, false);
		check(pieces.stream().allMatch(p -> p instanceof OwbFitPiece), f("owb_fit synthetic %s: %d fit pieces", name, pieces.size()));
		check(w.get(trigger.getX(), trigger.getY(), trigger.getZ()).is(triggerBlock), f("owb_fit synthetic %s: trigger block at its arena offset %s", name, trigger.toShortString()));
		int valuable = 0, lit = 0, lights = 0, bes = 0, leaks = 0;
		Map<Long, Integer> lowest = new HashMap<>();
		for (long l : w.written) {
			BlockPos p = BlockPos.of(l);
			BlockState s = w.get(p.getX(), p.getY(), p.getZ());
			for (Block b : VALUABLE) if (s.is(b)) valuable++;
			if (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT)) lit++;
			if (s.getLightEmission() > 0) lights++;
			if (s.hasBlockEntity()) bes++;
			if (s.is(Blocks.WATER)) {
				for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
					if (w.get(p.getX() + d[0], p.getY() + d[1], p.getZ() + d[2]).isAir()) leaks++;
				}
			}
			if (structural(s) && !span.test(s) && !s.is(Blocks.WOOL.white())) {
				long col = BlockPos.asLong(p.getX(), 0, p.getZ());
				lowest.merge(col, p.getY(), Math::min);
			}
		}
		int floating = 0;
		String firstFloat = "";
		for (var e : lowest.entrySet()) {
			BlockPos c = BlockPos.of(e.getKey());
			BlockState below = w.get(c.getX(), e.getValue() - 1, c.getZ());
			boolean bad = below.isAir() || !below.getFluidState().isEmpty() && !waterBelowOk;
			if (bad) {
				if (floating == 0) firstFloat = " first at " + c.getX() + "," + (e.getValue() - 1) + "," + c.getZ() + " (" + below.getBlock().getName().getString() + ")";
				floating++;
			}
		}
		check(valuable == 0, f("owb_fit synthetic %s: no valuable blocks (%d)", name, valuable));
		int braziers = name.startsWith("glacier") ? Blueprints.CAMPFIRES.length : 0;
		check(lit == braziers, f("owb_fit synthetic %s: lit campfires only as the fight's braziers (%d, expected %d)", name, lit, braziers));
		check(floating == 0, f("owb_fit synthetic %s: nothing floating: %d of %d columns unsupported%s", name, floating, lowest.size(), firstFloat));
		check(leaks == 0, f("owb_fit synthetic %s: placed water enclosed (%d open faces)", name, leaks));
		check(lights <= 40 && bes <= 10, f("owb_fit synthetic %s: budgets: %d light sources (<= 40), %d block entities (<= 10)", name, lights, bes));
		boolean room = !structural(w.get(stand.getX(), stand.getY(), stand.getZ())) && !structural(w.get(stand.getX(), stand.getY() + 1, stand.getZ()));
		check(room, f("owb_fit synthetic %s: standing room at %s", name, stand.toShortString()));
		World again = new World(w.ground, w.sea, w.top, w.fill);
		again.build(pieces, true);
		check(again.blocks.equals(w.blocks), f("owb_fit synthetic %s: same blocks in reverse chunk order (%d blocks)", name, w.blocks.size()));
	}

	private static void synthetic() {
		BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
		// Glacier Shelf on a hillside rising north and east (spread about 20 over the box)
		World gw = new World((x, z) -> 100 + (int) Math.floor(-z * 0.45) + Math.floorDiv(x, 8), 63, snow, stone);
		List<StructurePiece> gp = OwbFit.glacier(0, 0, gw.heights());
		OwbFitPiece core = (OwbFitPiece) gp.get(0);
		site("glacier_shelf", gp, gw, core.o.offset(Blueprints.FROZEN_HEART), OwbBlocks.FROZEN_HEART, core.o.offset(3, 0, 3), s -> false, false);
		int[] lat = OwbFit.lattice(0, 0, 16, gw.heights());
		int lo = java.util.Arrays.stream(lat).min().getAsInt(), hi = java.util.Arrays.stream(lat).max().getAsInt();
		check(hi - core.o.getY() <= 12 && core.o.getY() - lo <= 12, f("owb_fit synthetic glacier_shelf: plateau cut %d, fill %d (each <= 12)", hi - core.o.getY(), core.o.getY() - lo));
		check(gp.stream().anyMatch(p -> ((OwbFitPiece) p).part.equals(OwbFit.STAIR)) && core.adapt == TerrainAdjustment.BEARD_THIN,
			"owb_fit synthetic glacier_shelf: entrance stair planned, plateau carries a thin beard");
		// Croaking Shrine: swamp water everywhere, dry land beyond x = 22 (the east walk lands, south and west end on landings)
		World sw = new World((x, z) -> x > 22 ? 64 : 61, 63, Blocks.MUD.defaultBlockState(), Blocks.MUD.defaultBlockState());
		List<StructurePiece> sp = OwbFit.shrine(0, 0, 63, sw.heights());
		OwbFitPiece sc = (OwbFitPiece) sp.get(0);
		java.util.function.Predicate<BlockState> plank = s -> s.is(Blocks.MANGROVE_PLANKS) || s.is(Blocks.STRIPPED_MANGROVE_LOG) || s.is(Blocks.MANGROVE_STAIRS)
			|| s.is(Blocks.MANGROVE_SLAB); // deck, walk and roof boards between piles and posts
		site("croaking_shrine (wet)", sp, sw, sc.o.offset(Blueprints.SHRINE_CAULDRON), OwbBlocks.GULPMOTHER_SHRINE_CAULDRON, sc.o.offset(3, 0, 3), plank, true);
		OwbFitPiece east = (OwbFitPiece) sp.get(1);
		check(sc.o.getY() == 64 && east.prof.length < OwbFit.WALK_MAX && east.prof[east.prof.length - 1] == 64,
			f("owb_fit synthetic croaking_shrine: deck one above the water (y %d), east walk lands on the shore after %d steps", sc.o.getY() - 1, east.prof.length));
		World dw = new World((x, z) -> 64 + ((x + z) & 1), 63, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
		List<StructurePiece> dp = OwbFit.shrine(0, 0, 63, dw.heights());
		OwbFitPiece dc = (OwbFitPiece) dp.get(0);
		site("croaking_shrine (dry)", dp, dw, dc.o.offset(Blueprints.SHRINE_CAULDRON), OwbBlocks.GULPMOTHER_SHRINE_CAULDRON, dc.o.offset(3, 0, 3), plank, true);
		// Wreck Ring over a deep, sloping sea bed
		World ww = new World((x, z) -> 36 + Math.floorDiv(x + z, 10), 63, Blocks.SAND.defaultBlockState(), Blocks.STONE.defaultBlockState());
		List<StructurePiece> wp = OwbFit.wreck(0, 0, 63, ww.heights());
		OwbFitPiece wc = (OwbFitPiece) wp.get(0);
		java.util.function.Predicate<BlockState> deckPlank = s -> s.is(Blocks.SPRUCE_PLANKS) || s.is(Blocks.DARK_OAK_PLANKS) || s.is(Blocks.OAK_PLANKS)
			|| s.is(Blocks.STRIPPED_SPRUCE_LOG);
		site("wreck_ring", wp, ww, wc.o.offset(Blueprints.WRECK_CAPSTAN), OwbBlocks.VORLAGH_CAPSTAN, wc.o.offset(3, 0, 3), deckPlank, true);
		check(wp.stream().filter(p -> ((OwbFitPiece) p).part.equals(OwbFit.STACK)).count() == 8 && wp.stream().filter(p -> ((OwbFitPiece) p).part.equals(OwbFit.HULK)).count() == 3,
			"owb_fit synthetic wreck_ring: deck carried on 8 hull stacks, 3 sunken hulks with masts");
		// Sun Chamber under a desert at 70
		World dsw = new World((x, z) -> 70, 63, Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState());
		BlockPos so = new BlockPos(0, 31, 0);
		List<StructurePiece> up = OwbFit.sun(so, 70);
		site("sun_chamber", up, dsw, so.offset(Blueprints.SUN_ALTAR), OwbBlocks.NEFERKHET_SUN_ALTAR, so.offset(3, 0, 3), s -> false, false);
		BlockPos idol = Blueprints.sunIdolPos(so, 0);
		check(dsw.get(idol.getX(), idol.getY() + 1, idol.getZ()).is(OwbBlocks.NEFERKHET_SUN_IDOL) || dsw.get(idol.getX(), idol.getY(), idol.getZ()).is(OwbBlocks.NEFERKHET_SUN_IDOL),
			"owb_fit synthetic sun_chamber: sun idols on their pillars");
		check(dsw.get(16, 70 + 6, 0).is(Blocks.DYED_TERRACOTTA.yellow()) && dsw.get(17, 70, 0).is(Blocks.LADDER),
			"owb_fit synthetic sun_chamber: shaft reaches the kiosk at the surface, pyramid cap with its pyramidion");
		// cave rule: a void in the hall rejects the site, solid rock does not
		check(OwbFit.chamberHitsCave(0, 0, 30, (x, y, z) -> x == 8 && z == 8 && y == 36) && !OwbFit.chamberHitsCave(0, 0, 30, (x, y, z) -> false),
			"owb_fit synthetic sun_chamber: a cave void crossing the hall rejects the site");
	}

	// ---- NBT -----------------------------------------------------------------------------------------------------------------

	private static void nbt(ServerLevel level) {
		World gw = new World((x, z) -> 100 + (int) Math.floor(-z * 0.45), 63, Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.STONE.defaultBlockState());
		List<StructurePiece> gp = OwbFit.glacier(4000, 4000, gw.heights());
		var ctx = StructurePieceSerializationContext.fromLevel(level);
		int ok = 0;
		for (StructurePiece sp : gp) {
			OwbFitPiece p = (OwbFitPiece) sp;
			var tag = sp.createTag(ctx);
			OwbFitPiece q = new OwbFitPiece(tag);
			if (tag.getIntOr("V", 0) == OwbFitPiece.VERSION && q.kind.equals(p.kind) && q.part.equals(p.part) && q.o.equals(p.o) && q.at.equals(p.at)
				&& q.a == p.a && q.b == p.b && java.util.Arrays.equals(q.prof, p.prof) && q.adapt == p.adapt && q.getBoundingBox().equals(p.getBoundingBox())) ok++;
		}
		check(ok == gp.size(), f("owb_fit nbt: %d of %d pieces round-trip (V %d)", ok, gp.size(), OwbFitPiece.VERSION));
		var key = BuiltInRegistries.STRUCTURE_PIECE.getKey(OwbFit.pieceType());
		boolean legacy = true;
		for (String k : OwbWorldgen.KINDS) legacy &= OwbWorldgen.pieceTypeOf(k) != null;
		check(key != null && key.toString().equals("elsewhere:owb_fit") && legacy, "owb_fit nbt: piece type elsewhere:owb_fit registered, 2.x piece types kept for saved starts");
	}

	// ---- planner on real noise -------------------------------------------------------------------------------------------------

	private static void planner(ServerLevel level) {
		var gen = level.getChunkSource().getGenerator();
		if (!(gen instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator nb)) {
			check(false, "owb_fit planner: overworld generator is not noise based");
			return;
		}
		var reg = level.registryAccess();
		boolean was = OwbFit.ENABLED;
		try {
			for (String kind : OwbWorldgen.KINDS) {
				net.minecraft.world.level.levelgen.structure.StructureSet set = null;
				for (var e : reg.lookupOrThrow(Registries.STRUCTURE_SET).entrySet()) {
					if (e.getKey().identifier().getNamespace().equals("elsewhere") && e.getKey().identifier().getPath().equals(kind)) set = e.getValue();
				}
				if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement p)) {
					check(false, "owb_fit planner " + kind + ": structure set missing");
					continue;
				}
				Structure st = set.structures().get(0).structure().value();
				int sites = 0, legacySites = 0, fitStarts = 0, covered = 0, badFit = 0, longWalk = 0, killOk = 0;
				long nanos = 0, samples = 0;
				int maxSamples = 0;
				for (long seed : new long[] {1001L, 2002L, 3003L}) {
					var rs = net.minecraft.world.level.levelgen.RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, nb.generatorSettings().value());
					for (int rx = -6; rx < 6; rx++) for (int rz = -6; rz < 6; rz++) {
						ChunkPos cp = p.getPotentialStructureChunk(seed, rx * p.spacing(), rz * p.spacing());
						var ctx = new Structure.GenerationContext(reg, gen, gen.getBiomeSource(),
							rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED), rs,
							level.getServer().getStructureTemplateManager(), seed, cp, level, st.biomes()::contains);
						OwbFit.ENABLED = false;
						var old = st.findValidGenerationPoint(ctx);
						if (old.isPresent()) {
							legacySites++;
							if (old.get().getPiecesBuilder().build().pieces().stream().allMatch(q -> q instanceof OwbPiece)) killOk++;
						}
						OwbFit.ENABLED = true;
						// site rule plus plan, the best of 3 runs (each run plans afresh: a new HeightField), and its noise samples
						List<StructurePiece> pieces = null;
						long best = Long.MAX_VALUE;
						int smp = 0;
						for (int rep = 0; rep < 3; rep++) {
							int[] cnt = OwbFit.SAMPLES.get();
							cnt[0] = 0;
							long t0 = System.nanoTime();
							var stub = st.findValidGenerationPoint(ctx);
							List<StructurePiece> ps = stub.isEmpty() ? null : stub.get().getPiecesBuilder().build().pieces();
							long dt = System.nanoTime() - t0;
							if (ps == null) break;
							pieces = ps;
							best = Math.min(best, dt);
							smp = cnt[0];
						}
						if (pieces == null) continue;
						nanos += best;
						samples += smp;
						maxSamples = Math.max(maxSamples, smp);
						sites++;
						if (!pieces.isEmpty() && pieces.stream().allMatch(q -> q instanceof OwbFitPiece)) fitStarts++;
						BoundingBox all = null;
						for (StructurePiece q : pieces) all = all == null ? q.getBoundingBox() : BoundingBox.encapsulating(all, q.getBoundingBox());
						if (all != null && all.minX() <= cp.getMiddleBlockX() && all.maxX() >= cp.getMiddleBlockX() && all.minZ() <= cp.getMiddleBlockZ() && all.maxZ() >= cp.getMiddleBlockZ()) covered++;
						if (pieces.isEmpty() || !(pieces.get(0) instanceof OwbFitPiece c)) continue;
						OwbFit.Heights h = OwbFit.heights(ctx);
						if (kind.equals(OwbPiece.GLACIER_SHELF)) {
							int[] lat = OwbFit.lattice(c.o.getX(), c.o.getZ(), 16, h);
							int lo = java.util.Arrays.stream(lat).min().getAsInt(), hi = java.util.Arrays.stream(lat).max().getAsInt();
							if (hi - c.o.getY() > 12 || c.o.getY() - lo > 12) badFit++;
						}
						for (StructurePiece q : pieces) if (q instanceof OwbFitPiece fp && fp.part.equals(OwbFit.WALK) && fp.prof.length > OwbFit.WALK_MAX) longWalk++;
					}
				}
				double ms = sites == 0 ? 0 : nanos / 1e6 / sites, avg = sites == 0 ? 0 : (double) samples / sites;
				OverworldBossesContent.LOGGER.info("[selftest] owb_fit planner {}: {} sites (2.1 code {}), {} ms per site for {} noise samples ({} ms per sample on this machine)",
					kind, sites, legacySites, String.format(Locale.ROOT, "%.2f", ms), String.format(Locale.ROOT, "%.1f", avg),
					String.format(Locale.ROOT, "%.3f", avg == 0 ? 0 : ms / avg));
				check(sites == fitStarts && sites == covered, f("owb_fit planner %s: %d of %d starts are fit pieces, %d cover their start chunk", kind, fitStarts, sites, covered));
				check(badFit == 0 && longWalk == 0, f("owb_fit planner %s: plateau cut/fill <= 12 (%d over), walks <= %d (%d over)", kind, badFit, OwbFit.WALK_MAX, longWalk));
				check(killOk == legacySites, f("owb_fit planner %s: kill switch off gives the frozen 2.x piece (%d of %d)", kind, killOk, legacySites));
				// planner cost as the HeightField budget (STRUCTURES.md 2.3: about 80 samples, at most 250 per normal site); deterministic, where
				// wall-clock ms depend on the machine's load (logged above)
				check(avg <= 80 && maxSamples <= 250, f("owb_fit planner %s: %.1f noise samples per site, %d at most (HeightField budget about 80, at most 250)", kind, avg, maxSamples));
				if (kind.equals(OwbPiece.SUN_CHAMBER)) {
					check(legacySites == 0 || sites * 100 >= legacySites * 70, f("owb_fit planner sun_chamber: %d of %d sites survive the cave rule (>= 70%%)", sites, legacySites));
				} else {
					check(sites == legacySites || kind.equals(OwbPiece.GLACIER_SHELF), f("owb_fit planner %s: site count unchanged by the fit (%d vs %d)", kind, sites, legacySites));
				}
			}
		} finally {
			OwbFit.ENABLED = was;
		}
	}

	// ---- generated sites in the test world -------------------------------------------------------------------------------------

	private static void world(ServerLevel level) {
		boolean access = OwbBeardifierAccessCheck.applied();
		boolean hook = java.util.Arrays.stream(Beardifier.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("owbFitBeards"));
		check(access && hook, f("owb_fit world: Beardifier mixins applied (accessor %s, return-value hook %s)", access, hook));
		var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String kind : OwbWorldgen.KINDS) {
			var holder = reg.get(OwbWorldgen.key(kind));
			if (holder.isEmpty()) continue;
			var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder.get()), BlockPos.ZERO, 64, false);
			if (found == null) {
				check(true, "owb_fit world " + kind + ": none within 64 chunks of 0,0 on this seed (synthetic and planner checks cover it)");
				continue;
			}
			BlockPos at = found.getFirst();
			var start = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(found.getSecond().value());
			if (start == null || !start.isValid()) {
				check(false, "owb_fit world " + kind + ": start at " + at.toShortString());
				continue;
			}
			if (!(start.getPieces().get(0) instanceof OwbFitPiece core)) {
				check(true, "owb_fit world " + kind + ": saved 2.x start at " + at.toShortString() + " keeps its frozen piece");
				continue;
			}
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.getChunk((core.o.getX() >> 4) + dx, (core.o.getZ() >> 4) + dz);
			BlockPos trig;
			Block want;
			switch (kind) {
				case OwbPiece.CROAKING_SHRINE -> { trig = core.o.offset(Blueprints.SHRINE_CAULDRON); want = OwbBlocks.GULPMOTHER_SHRINE_CAULDRON; }
				case OwbPiece.SUN_CHAMBER -> { trig = core.o.offset(Blueprints.SUN_ALTAR); want = OwbBlocks.NEFERKHET_SUN_ALTAR; }
				case OwbPiece.GLACIER_SHELF -> { trig = core.o.offset(Blueprints.FROZEN_HEART); want = OwbBlocks.FROZEN_HEART; }
				default -> { trig = core.o.offset(Blueprints.WRECK_CAPSTAN); want = OwbBlocks.VORLAGH_CAPSTAN; }
			}
			check(level.getBlockState(trig).is(want), f("owb_fit world %s: %d fit pieces at %s, trigger block placed", kind, start.getPieces().size(), core.o.toShortString()));
			BoundingBox beard = core.beardBox();
			if (beard != null) {
				Beardifier b = Beardifier.forStructuresInChunk(level.structureManager(), new ChunkPos(core.o.getX() >> 4, core.o.getZ() >> 4));
				boolean has = OwbBeards.rules(b).stream().anyMatch(r -> r.box().equals(beard) && r.terrainAdjustment() == core.adapt);
				check(has, f("owb_fit world %s: the Beardifier carries the piece's %s rule (mixin live)", kind, core.adapt.getSerializedName()));
			}
		}
	}

	private static final class OwbBeardifierAccessCheck {
		static boolean applied() {
			return dev.elsewhere.overworld_bosses.fit3.mixin.OwbBeardifierAccess.class.isAssignableFrom(Beardifier.class);
		}
	}

	private static String f(String fmt, Object... args) {
		return String.format(Locale.ROOT, fmt, args);
	}
}
