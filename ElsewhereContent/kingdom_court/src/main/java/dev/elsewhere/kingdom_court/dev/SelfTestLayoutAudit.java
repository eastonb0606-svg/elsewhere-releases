package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import dev.elsewhere.kingdom_court.world.CourtSite;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import dev.elsewhere.kingdom_court.world.RealmFit;
import dev.elsewhere.kingdom_court.world.Waterfront;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * s-asm-court (3.0.0 swarm assembler): every kingdom's WHOLE fv 2 realm (castle, curtain, kit, dungeon, both towns, road, Hrafnholt's
 * waterfront when its site has one) on the real generator ground of the sites the court's own site search picks, over several seeds.
 * For each site the generator terrain (ground, sea-level water) under every part's box and along the road is copied into this flat test world
 * at the real coordinates, the realm is built through the fv 2 path chunk by chunk like worldgen (the faction's castle style, Placer.ground =
 * RealmFit.HeightField, town floors as RealmStructure computes them), and the result is compared block by block with the copied terrain:
 * <ul>
 * <li>valuable blocks: none anywhere (gold, diamond, emerald, iron, lapis, netherite, copper, amethyst, raw ore blocks, gilded blackstone,
 * beacon, ancient debris: the Longship VALUABLE list);</li>
 * <li>floating: no island of structure blocks (joined on faces, edges or corners) that touches no natural ground (afloat longships inside the
 * waterfront box excepted), and no
 * footprint column whose lowest structure block has more than 1 air or fluid block under it (STRUCTURES.md 2.16), except overhangs and spans
 * (at least 2 above the base of a grounded column of the same build within 2 blocks, or a continuous deck to one within 6), blocks hung on a
 * full structure block, and a longship's submerged hull;</li>
 * <li>buried: no door, fence gate, pane, bars or glass with natural terrain against it, and terrain against at most 5% of the exterior faces of
 * walls above their own ground and base + 1 (2.16);</li>
 * <li>water: every fluid the build placed is contained (solid or fluid on 4 sides and below) and no natural water got an opening the build cut.</li>
 * </ul>
 * Also: the four kingdoms' realms are built from clearly different block mixes. Inland surface water above sea level + 1 is copied as ground
 * (only OCEAN_FLOOR_WG of shore columns is sampled; the site search keeps footprints dry). Styles: the loaded {@code kingdom_castle/<k>.json}
 * when the rival modules are present (merged jar), else the module source next to this one ({@code ../../rival_<k>/...}) or an empty style,
 * registered for the audit under the real faction id (as worldgen looks it up) and removed again at the end.
 */
final class SelfTestLayoutAudit {
	private SelfTestLayoutAudit() {}

	static final long[] SEEDS = {7L, 4242L, 20261112L};
	static final String[] KINGDOMS = {"aldermark", "hrafnholt", "azmar", "mirewick"};
	static final Set<String> VALUABLE = Set.of("gold_block", "diamond_block", "emerald_block", "iron_block", "netherite_block", "lapis_block", "gilded_blackstone",
		"raw_gold_block", "raw_iron_block", "raw_copper_block", "copper_block", "amethyst_block", "beacon", "ancient_debris");

	record Site(String kingdom, long seed, BlockPos o, RandomState rs, int @org.jspecify.annotations.Nullable [] water) {}

	static final List<Site> SITES = new ArrayList<>();
	static final Map<String, Map<Block, Integer>> MIX = new LinkedHashMap<>();
	static final Map<String, Integer> AUDITED = new HashMap<>();
	static final Map<String, CastleStyles.Style> STYLE = new HashMap<>();

	static void add() {
		STEPS.add((srv, l) -> {
			sites(srv, l);
			return 1;
		});
		for (int i = 0; i < KINGDOMS.length * SEEDS.length; i++) {
			int k = i;
			STEPS.add((srv, l) -> {
				if (k < SITES.size()) audit(srv, l, SITES.get(k));
				return 2;
			});
		}
		STEPS.add((srv, l) -> {
			summary();
			return 1;
		});
	}

	static ChunkGenerator generator(MinecraftServer srv) {
		return srv.registryAccess().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
	}

	/** The rival site wishes, as in SelfTestRivals' placement step (RIVALS-DECISIONS 7), under the real faction ids. */
	static List<CourtSite.Spec> specs() {
		List<List<String>> biomes = List.of(
			List.of("minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:windswept_forest", "minecraft:river", "minecraft:frozen_river", "minecraft:cold_ocean", "minecraft:ocean", "minecraft:deep_cold_ocean"),
			List.of("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:savanna", "minecraft:savanna_plateau"),
			List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"));
		List<List<String>> fallback = List.of(List.of("minecraft:snowy_taiga", "minecraft:grove"), List.of(), List.of("minecraft:forest", "minecraft:birch_forest"));
		boolean[] rise = {true, false, false}, water = {false, true, true};
		List<CourtSite.Spec> specs = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			var strict = CourtSite.predicateOf(biomes.get(i));
			var fb = CourtSite.predicateOf(fallback.get(i));
			specs.add(new CourtSite.Spec(CourtContent.id(KINGDOMS[i + 1]), strict, fallback.get(i).isEmpty() ? (b -> strict.test(b) || b.is(CourtSite.BIOMES)) : (b -> strict.test(b) || fb.test(b)), rise[i], water[i], i == 0));
		}
		return specs;
	}

	static void sites(MinecraftServer srv, ServerLevel l) {
		ChunkGenerator gen = generator(srv);
		NoiseBasedChunkGenerator nbg = (NoiseBasedChunkGenerator) gen;
		var noises = srv.registryAccess().lookupOrThrow(Registries.NOISE);
		List<CourtSite.Spec> specs = specs();
		Map<String, Integer> per = new LinkedHashMap<>();
		for (String k : KINGDOMS) per.put(k, 0);
		for (long seed : SEEDS) {
			RandomState rs = RandomState.create(noises, seed, nbg.generatorSettings().value());
			BlockPos a = CourtSite.choose(seed, BlockPos.ZERO, gen, rs, l, cp -> false);
			if (a == null) {
				info("layout audit seed " + seed + ": no Aldermark site");
				continue;
			}
			SITES.add(new Site("aldermark", seed, a, rs, null));
			per.merge("aldermark", 1, Integer::sum);
			var m = CourtSite.chooseRivals(seed, BlockPos.ZERO, gen, rs, l, cp -> false, a, List.of(a), specs);
			for (int i = 0; i < 3; i++) {
				Identifier f = specs.get(i).faction();
				BlockPos p = m.get(f);
				if (p == null) {
					info("layout audit seed " + seed + ": no " + KINGDOMS[i + 1] + " site");
					continue;
				}
				Double b = CourtSite.WATER_BEARING.get(f);
				Integer sh = CourtSite.WATER_SHORE.get(f);
				SITES.add(new Site(KINGDOMS[i + 1], seed, p, rs, b != null && sh != null ? new int[] {b > 0 ? 1 : 3, sh} : null));
				per.merge(KINGDOMS[i + 1], 1, Integer::sum);
			}
		}
		info("layout audit sites: " + SITES.stream().map(s -> s.kingdom() + "/" + s.seed() + " " + s.o().toShortString() + (s.water() == null ? "" : " waterfront")).reduce((x, y) -> x + "; " + y).orElse("none"));
		check("layout audit: every kingdom has a real chosen site on all " + SEEDS.length + " audited seeds", per.values().stream().allMatch(n -> n == SEEDS.length), per);
	}

	/** The kingdom's castle style: loaded data (merged jar), else the rival module's source file beside this module, else an empty style. */
	static CastleStyles.@org.jspecify.annotations.Nullable Style style(String k) {
		if (k.equals("aldermark")) return null;
		CastleStyles.Style loaded = CastleStyles.of(CourtContent.id(k));
		if (loaded != null) return loaded;
		return STYLE.computeIfAbsent(k, kk -> {
			CastleStyles.Style s = null;
			for (String root : new String[] {"../..", "..", "../../.."}) {
				Path p = Path.of(root, "rival_" + kk, "src/main/resources/data/elsewhere/kingdom_castle/" + kk + ".json");
				if (!Files.isRegularFile(p)) continue;
				try {
					s = CastleStyles.parse(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
					info("layout audit: " + kk + " style from " + p.normalize());
					break;
				} catch (Exception e) {
					info("layout audit: " + kk + " style " + p + " unreadable: " + e);
				}
			}
			if (s == null) {
				s = new CastleStyles.Style();
				info("layout audit: no " + kk + " style data here (no rival module); its layout and kit run without a palette");
			}
			// under the real faction id, exactly as worldgen looks it up (every layout's own kingdom test keys on it); removed again at the end
			CastleStyles.register(CourtContent.id(kk), s);
			return s;
		});
	}

	static Object call(String name, Class<?>[] types, Object... args) throws Exception {
		Method m = RealmFit.class.getDeclaredMethod(name, types);
		m.setAccessible(true);
		return m.invoke(null, args);
	}

	/** As CourtWorld.RealmPiece.partOrigin for fv 2: towns at their own floors, everything else at the site. */
	static BlockPos partOrigin(CastleLayout.Part p, BlockPos s, int tw, int te) {
		return switch (p) {
			case TOWN_WEST, KIT_TOWN_WEST -> new BlockPos(s.getX(), tw, s.getZ());
			case TOWN_EAST, KIT_TOWN_EAST -> new BlockPos(s.getX(), te, s.getZ());
			default -> s;
		};
	}

	static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
	}

	static boolean open(BlockState s) {
		return s.getFluidState().isEmpty() && (s.isAir() || s.canBeReplaced());
	}

	// ---- one site --------------------------------------------------------------------------------------------------------------------

	static final BlockState STONE = Blocks.STONE.defaultBlockState(), GRASS = Blocks.GRASS_BLOCK.defaultBlockState(), SAND = Blocks.SAND.defaultBlockState(),
		WATER = Blocks.WATER.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();

	/** Copied terrain over the audited columns. */
	static final class T {
		int x0, z0, w, h, yMin, yMax;
		boolean[] mask;
		int[] fl, top, bot;

		int i(int x, int z) {
			int dx = x - x0, dz = z - z0;
			return dx < 0 || dz < 0 || dx >= w || dz >= h || !mask[dx * h + dz] ? -1 : dx * h + dz;
		}

		/** The copied (natural) state of a cell of an audited column at or above its slab bottom. */
		BlockState nat(int i, int y) {
			if (y < fl[i] - 1) return STONE;
			if (y == fl[i] - 1) return top[i] > fl[i] ? SAND : GRASS;
			return y < top[i] ? WATER : AIR;
		}
	}

	static void audit(MinecraftServer srv, ServerLevel l, Site st) throws Exception {
		long t0 = System.nanoTime();
		ChunkGenerator gen = generator(srv);
		RandomState rs = st.rs();
		int sea = gen.getSeaLevel();
		BlockPos s = st.o();
		String tag = st.kingdom().substring(0, 1).toUpperCase() + st.kingdom().substring(1) + " seed " + st.seed();
		CastleStyles.Style style = style(st.kingdom());
		Identifier faction = st.kingdom().equals("aldermark") ? Factions.ALDERMARK : CourtContent.id(st.kingdom());
		// town floors exactly as RealmStructure.findGenerationPoint computes them for a new start
		RealmFit.HeightField hf0 = new RealmFit.HeightField(gen, rs, l, st.seed(), s, s.getY(), s.getY());
		int tw = s.getY(), te = s.getY();
		if ((boolean) call("anchorsFollowTownFloor", new Class<?>[0])) {
			Class<?>[] ty = {RealmFit.HeightField.class, BlockPos.class, boolean.class};
			tw = (int) call("townFloor", ty, hf0, s, true);
			te = (int) call("townFloor", ty, hf0, s, false);
		}
		RealmFit.HeightField hf = new RealmFit.HeightField(gen, rs, l, st.seed(), s, tw, te);
		new CourtWorld.RealmPiece(CastleLayout.Part.CASTLE, s, faction, 2, tw, te); // registers the town floors like a real start
		// the road route first (writes clipped away), so only its cells need terrain
		CastleLayout.Placer dry = new CastleLayout.Placer(l, new BoundingBox(s.getX(), l.getMaxY() - 1, s.getZ(), s.getX(), l.getMaxY() - 1, s.getZ()));
		dry.style = style;
		dry.ground = hf;
		CastleLayout.build(CastleLayout.Part.ROAD, s, dry);
		List<int[]> road = roadCells(s);
		// audited columns: every part's fv 2 box but the road's (+3), the road route (+4) or its whole box without a route, the waterfront box
		List<BoundingBox> boxes = new ArrayList<>();
		for (CastleLayout.Part p : CastleLayout.Part.values()) {
			if (p == CastleLayout.Part.ROAD && !road.isEmpty()) continue;
			boxes.add(CastleLayout.fitBox(p, partOrigin(p, s, tw, te)).inflatedBy(3));
		}
		BoundingBox wbox = st.water() == null ? null : Waterfront.box(s, st.water()[0], st.water()[1], sea);
		if (wbox != null) boxes.add(wbox.inflatedBy(3));
		for (int[] c : road) boxes.add(new BoundingBox(s.getX() + c[0] - 4, s.getY(), s.getZ() + c[1] - 4, s.getX() + c[0] + 4, s.getY(), s.getZ() + c[1] + 4));
		T t = new T();
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE, yTop = Integer.MIN_VALUE;
		for (BoundingBox b : boxes) {
			x0 = Math.min(x0, b.minX());
			z0 = Math.min(z0, b.minZ());
			x1 = Math.max(x1, b.maxX());
			z1 = Math.max(z1, b.maxZ());
			yTop = Math.max(yTop, b.maxY());
		}
		t.x0 = x0;
		t.z0 = z0;
		t.w = x1 - x0 + 1;
		t.h = z1 - z0 + 1;
		int n = t.w * t.h;
		t.mask = new boolean[n];
		for (BoundingBox b : boxes) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) t.mask[(x - x0) * t.h + (z - z0)] = true;
		t.fl = new int[n];
		t.top = new int[n];
		t.bot = new int[n];
		int minFloor = Math.min(s.getY(), Math.min(tw, te)), cols = 0, yMin = Integer.MAX_VALUE, yMax = yTop + 2;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			int i = t.i(x, z);
			if (i < 0) continue;
			cols++;
			int top = hf.y(x, z);
			int fl = top <= sea + 1 ? Math.min(top, gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, l, rs)) : top;
			t.top[i] = top;
			t.fl[i] = fl;
			t.bot[i] = Math.min(fl - 12, minFloor - 14);
			yMin = Math.min(yMin, t.bot[i]);
			yMax = Math.max(yMax, top + 2);
		}
		t.yMin = yMin;
		t.yMax = yMax;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, true);
		// copy the terrain; above it, clear anything an earlier site or test left in these columns
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			int i = t.i(x, z);
			if (i < 0) continue;
			for (int y = t.bot[i]; y < t.top[i]; y++) l.setBlock(m.set(x, y, z), t.nat(i, y), 2);
			for (int y = t.top[i]; y <= yMax; y++) if (!l.getBlockState(m.set(x, y, z)).isAir()) l.setBlock(m, AIR, 2);
		}
		// build the realm like worldgen: the waterfront (its own structure), then every part chunk by chunk
		int placed = 0;
		if (wbox != null) {
			for (int cx = wbox.minX() >> 4; cx <= wbox.maxX() >> 4; cx++) for (int cz = wbox.minZ() >> 4; cz <= wbox.maxZ() >> 4; cz++) {
				CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15));
				Waterfront.build(pl, s, st.water()[0], st.water()[1], sea, Waterfront.parse(style == null ? null : style.waterfront), (int) (s.asLong() >>> 3));
				placed += pl.placed;
			}
		}
		for (CastleLayout.Part p : CastleLayout.Part.values()) {
			BlockPos po = partOrigin(p, s, tw, te);
			BoundingBox box = CastleLayout.fitBox(p, po);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15));
				pl.style = style;
				pl.ground = hf;
				CastleLayout.build(p, po, pl);
				placed += pl.placed;
			}
		}
		long t1 = System.nanoTime();
		measure(l, st, tag, t, s, sea, wbox, cols, placed, (t1 - t0) / 1_000_000);
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, false);
		AUDITED.merge(st.kingdom(), 1, Integer::sum);
	}

	@SuppressWarnings("unchecked")
	static List<int[]> roadCells(BlockPos s) {
		List<int[]> out = new ArrayList<>();
		try {
			var f = CastleLayout.class.getDeclaredField("ROUTES");
			f.setAccessible(true);
			Map<String, int[][]> routes = (Map<String, int[][]>) f.get(null);
			for (var e : routes.entrySet()) if (e.getKey().startsWith(s.asLong() + ":")) for (int[] c : e.getValue()) out.add(c);
		} catch (ReflectiveOperationException e) {
			info("layout audit: road route not readable (" + e + "); the whole road box is audited");
		}
		return out;
	}

	// ---- the measurements ------------------------------------------------------------------------------------------------------------

	static void measure(ServerLevel l, Site st, String tag, T t, BlockPos s, int sea, @org.jspecify.annotations.Nullable BoundingBox wbox, int cols, int placed, long ms) {
		int n = t.w * t.h;
		int[] ys = new int[n], yt = new int[n], gap = new int[n];
		java.util.Arrays.fill(ys, Integer.MIN_VALUE);
		LongOpenHashSet struct = new LongOpenHashSet();
		Map<Block, Integer> mix = MIX.computeIfAbsent(st.kingdom(), k -> new HashMap<>());
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
		int valuable = 0, blocked = 0, faces = 0, buried = 0, leaks = 0, sFluid = 0;
		String vFirst = null, bFirst = null, lFirst = null, faceFirst = null;
		int[][] h4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int x = t.x0; x < t.x0 + t.w; x++) for (int z = t.z0; z < t.z0 + t.h; z++) {
			int i = t.i(x, z);
			if (i < 0) continue;
			for (int y = t.bot[i]; y <= t.yMax; y++) {
				BlockState a = l.getBlockState(m.set(x, y, z));
				BlockState nat = t.nat(i, y);
				boolean fluid = !a.getFluidState().isEmpty();
				// water: a placed fluid must be contained; no fluid may face an opening the build cut
				if (fluid) {
					boolean placedFluid = a != nat;
					if (placedFluid) sFluid++;
					for (int d = 0; d < 5; d++) {
						int nx = x + (d < 4 ? h4[d][0] : 0), ny = y - (d < 4 ? 0 : 1), nz = z + (d < 4 ? h4[d][1] : 0);
						int j = t.i(nx, nz);
						if (j < 0 || ny < t.bot[j]) continue;
						BlockState b = l.getBlockState(q.set(nx, ny, nz));
						if (!open(b)) continue;
						if (placedFluid || t.nat(j, ny) != AIR) {
							leaks++;
							if (lFirst == null) lFirst = (placedFluid ? "placed " : "natural ") + a.getBlock() + " at " + rel(s, x, y, z) + " open toward " + rel(s, nx, ny, nz) + " (" + b.getBlock() + ")";
						}
					}
				}
				if (a == nat || a.isAir()) continue;
				// a structure block
				Block blk = a.getBlock();
				if (VALUABLE.contains(BuiltInRegistries.BLOCK.getKey(blk).getPath())) {
					valuable++;
					if (vFirst == null) vFirst = blk + " at " + rel(s, x, y, z);
				}
				if (blk instanceof LiquidBlock) continue;
				struct.add(BlockPos.asLong(x, y, z));
				mix.merge(blk, 1, Integer::sum);
				if (ys[i] == Integer.MIN_VALUE) {
					ys[i] = y;
					int g = 0;
					for (int yy = y - 1; yy >= t.bot[i] && !solid(l.getBlockState(q.set(x, yy, z))); yy--) g++;
					gap[i] = g;
				}
				yt[i] = y;
				// buried: openings against natural terrain; terrain against wall faces above their ground and base + 1
				boolean opening = blk instanceof DoorBlock || blk instanceof FenceGateBlock || blk instanceof IronBarsBlock || BuiltInRegistries.BLOCK.getKey(blk).getPath().endsWith("glass");
				boolean wall = a.isCollisionShapeFullBlock(l, m) && y >= ys[i] + 2 && y >= t.fl[i];
				if (!opening && !wall) continue;
				for (int[] d : h4) {
					int nx = x + d[0], nz = z + d[1];
					int j = t.i(nx, nz);
					if (j < 0 || y < t.bot[j]) continue;
					BlockState b = l.getBlockState(q.set(nx, y, nz));
					BlockState bn = t.nat(j, y);
					boolean terrain = b == bn && (bn == STONE || bn == GRASS || bn == SAND);
					if (opening && terrain) {
						blocked++;
						if (bFirst == null) bFirst = blk + " at " + rel(s, x, y, z) + " against terrain at " + rel(s, nx, y, nz);
					}
					if (wall && (b == bn || b.isAir())) {
						faces++;
						if (terrain) {
							buried++;
							if (faceFirst == null) faceFirst = "terrain against " + blk + " at " + rel(s, x, y, z) + " (its ground " + (t.fl[i] - s.getY()) + ", base " + (ys[i] - s.getY()) + ")";
						}
					}
				}
			}
		}
		// floating 1: islands of structure blocks that touch no natural ground (26-neighbour components)
		int islands = 0, islandBlocks = 0, afloat = 0;
		String iFirst = null;
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		var it = struct.iterator();
		while (it.hasNext()) {
			long p0 = it.nextLong();
			if (seen.contains(p0)) continue;
			seen.add(p0);
			queue.enqueue(p0);
			boolean anchored = false, wet = false;
			int size = 0;
			long first = p0;
			while (!queue.isEmpty()) {
				long p = queue.dequeueLong();
				size++;
				int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
				// 26-neighbour: Minecraft builds join on edges and corners too (a spire on its parapet, palm fronds on the trunk)
				for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					int nx = x + dx, ny = y + dy, nz = z + dz;
					long np = BlockPos.asLong(nx, ny, nz);
					if (struct.contains(np)) {
						if (seen.add(np)) queue.enqueue(np);
						continue;
					}
					if (anchored && (dx != 0 || dy != -1 || dz != 0)) continue;
					int j = t.i(nx, nz);
					if (j < 0 || ny < t.bot[j]) {
						anchored = true;
						continue;
					}
					BlockState b = l.getBlockState(q.set(nx, ny, nz));
					BlockState bn = t.nat(j, ny);
					// every non-structure cell is natural (b == bn), cut (air) or a placed fluid: natural terrain touching it anchors the island
					if (b == bn && (bn == STONE || bn == GRASS || bn == SAND)) anchored = true;
					if (dx == 0 && dy == -1 && dz == 0 && !b.getFluidState().isEmpty()) wet = true;
				}
			}
			if (anchored) continue;
			if (wet && wbox != null && wbox.isInside(BlockPos.of(first))) {
				afloat += size;
				continue;
			}
			islands++;
			islandBlocks += size;
			if (islands <= 6) iFirst = (iFirst == null ? "" : iFirst + " | ") + size + " blocks from " + rel(s, BlockPos.getX(first), BlockPos.getY(first), BlockPos.getZ(first)) + " (" + l.getBlockState(BlockPos.of(first)).getBlock() + ")";
		}
		// floating 2: footprint columns with more than 1 air/fluid block under their lowest structure block (overhangs, spans and hung blocks excused)
		int floatCols = 0, maxGap = 0;
		String fFirst = null;
		for (int x = t.x0; x < t.x0 + t.w; x++) for (int z = t.z0; z < t.z0 + t.h; z++) {
			int i = t.i(x, z);
			if (i < 0 || ys[i] == Integer.MIN_VALUE || gap[i] < 2) continue;
			int y = ys[i];
			if (y < sea && submerged(l, t, i, x, y, z)) continue; // a longship's hull in the water
			if (hung(l, struct, x, y, z) || overhang(t, ys, yt, gap, x, y, z)) continue;
			floatCols++;
			maxGap = Math.max(maxGap, gap[i]);
			if (floatCols <= 4) fFirst = (fFirst == null ? "" : fFirst + " | ") + l.getBlockState(new BlockPos(x, y, z)).getBlock() + " at " + rel(s, x, y, z) + " over " + gap[i] + " air/fluid";
		}
		double share = faces == 0 ? 0 : (double) buried / faces;
		info(String.format("%s: site %s, town floors %d/%d, %d columns, %d blocks placed, %d structure blocks, %d placed fluid, %d ms; floating islands %d (%d blocks), unsupported columns %d (max gap %d), afloat %d; blocked openings %d, buried faces %d of %d (%.1f%%); leaks %d; valuable %d",
			tag, s.toShortString(), CourtWorld.townFloor(s, true) - s.getY(), CourtWorld.townFloor(s, false) - s.getY(), cols, placed, struct.size(), sFluid, ms, islands, islandBlocks, floatCols, maxGap, afloat, blocked, buried, faces, share * 100, leaks, valuable));
		check(tag + ": the realm is built (castle, towns, road)", struct.size() > 20000, struct.size() + " structure blocks");
		check(tag + ": no valuable blocks anywhere in the realm", valuable == 0, valuable + (vFirst == null ? "" : "; " + vFirst));
		check(tag + ": nothing floating (no island off the ground; no footprint column over more than 1 air/fluid block, overhangs and spans excepted)", islands == 0 && floatCols == 0,
			islands + " islands, " + floatCols + " columns" + (iFirst == null ? "" : "; islands: " + iFirst) + (fFirst == null ? "" : "; columns: " + fFirst));
		check(tag + ": nothing buried (no door, gate or window against terrain; terrain against at most 5% of the wall faces above their ground)", blocked == 0 && share <= 0.05,
			blocked + " blocked, " + String.format("%.1f%%", share * 100) + (bFirst == null ? "" : "; " + bFirst) + (faceFirst == null ? "" : "; " + faceFirst));
		check(tag + ": no water leaks (every placed fluid contained, no natural water opened)", leaks == 0, leaks + (lFirst == null ? "" : "; " + lFirst));
	}

	static String rel(BlockPos s, int x, int y, int z) {
		return (x - s.getX()) + "," + (y - s.getY()) + "," + (z - s.getZ());
	}

	/** Everything under the lowest block down to solid ground is fluid: a hull in the water. */
	static boolean submerged(ServerLevel l, T t, int i, int x, int y, int z) {
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int yy = y - 1; yy >= t.bot[i]; yy--) {
			BlockState b = l.getBlockState(q.set(x, yy, z));
			if (solid(b)) return true;
			if (b.getFluidState().isEmpty()) return false;
		}
		return true;
	}

	/** A non-full block (banner, lantern, torch, sign, ladder) hung on a full structure block beside or above it. */
	static boolean hung(ServerLevel l, LongOpenHashSet struct, int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		if (l.getBlockState(p).isCollisionShapeFullBlock(l, p)) return false;
		for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
			BlockPos np = p.offset(d[0], d[1], d[2]);
			if (struct.contains(np.asLong()) && l.getBlockState(np).isCollisionShapeFullBlock(l, np)) return true;
		}
		return false;
	}

	/**
	 * An eave, hoarding or gallery: a grounded column of the build within 2 blocks whose base lies at least 2 below and whose top reaches it;
	 * or a deck: a straight run of columns with structure at this height to such a column within 6 (bridges, jetties).
	 */
	static boolean overhang(T t, int[] ys, int[] yt, int[] gap, int x, int y, int z) {
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (grounded(t, ys, yt, gap, x + dx, z + dz, y)) return true;
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int k = 1; k <= 6; k++) {
			int nx = x + d[0] * k, nz = z + d[1] * k, j = t.i(nx, nz);
			if (j < 0 || ys[j] == Integer.MIN_VALUE || ys[j] > y + 1 || yt[j] < y) break;
			if (grounded(t, ys, yt, gap, nx, nz, y)) return true;
		}
		return false;
	}

	static boolean grounded(T t, int[] ys, int[] yt, int[] gap, int x, int z, int y) {
		int j = t.i(x, z);
		return j >= 0 && ys[j] != Integer.MIN_VALUE && gap[j] <= 1 && ys[j] + 2 <= y && yt[j] >= y;
	}

	// ---- summary ---------------------------------------------------------------------------------------------------------------------

	static void summary() {
		check("layout audit: every kingdom's realm audited on " + SEEDS.length + " seeds", java.util.Arrays.stream(KINGDOMS).allMatch(k -> AUDITED.getOrDefault(k, 0) == SEEDS.length), AUDITED);
		// each kingdom unique: histogram intersection of the structure block mixes, pairwise
		double worst = 0;
		String pair = "";
		StringBuilder sb = new StringBuilder();
		List<String> ks = new ArrayList<>(MIX.keySet());
		for (int a = 0; a < ks.size(); a++) for (int b = a + 1; b < ks.size(); b++) {
			double o = overlap(MIX.get(ks.get(a)), MIX.get(ks.get(b)));
			sb.append(ks.get(a)).append('/').append(ks.get(b)).append(' ').append(String.format("%.2f", o)).append("; ");
			if (o > worst) {
				worst = o;
				pair = ks.get(a) + "/" + ks.get(b);
			}
		}
		info("layout audit block-mix overlap: " + sb);
		check("layout audit: each kingdom's realm is built from its own block mix (pairwise overlap below 50%)", ks.size() == KINGDOMS.length && worst < 0.5, "worst " + pair + " " + String.format("%.2f", worst));
		for (String k : STYLE.keySet()) CastleStyles.clear(CourtContent.id(k));
	}

	static double overlap(Map<Block, Integer> a, Map<Block, Integer> b) {
		double ta = a.values().stream().mapToInt(Integer::intValue).sum(), tb = b.values().stream().mapToInt(Integer::intValue).sum(), o = 0;
		if (ta == 0 || tb == 0) return 1;
		for (var e : a.entrySet()) o += Math.min(e.getValue() / ta, b.getOrDefault(e.getKey(), 0) / tb);
		return o;
	}
}
