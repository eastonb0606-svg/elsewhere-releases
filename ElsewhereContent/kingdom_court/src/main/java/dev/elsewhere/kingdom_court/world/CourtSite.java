package dev.elsewhere.kingdom_court.world;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.CourtContent;
import net.minecraft.resources.Identifier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

/**
 * Picks every realm site once per world and the result is saved (CourtState.siteChosen); each faction has exactly one realm, ever.
 * Aldermark (the anchor) keeps its 2.0 rules: first fit on 6 seeded rings 1,300-2,400 blocks from spawn, temperate land (biome tag {@code #elsewhere:court_realm_biomes}
 * at the castle and both towns), fairly flat, in chunks that were never generated. The rivals (RIVALS-DECISIONS 15) are scored and chosen jointly in the 90-degree wedge
 * centred on Aldermark's bearing, see {@link #chooseRivals}.
 */
public final class CourtSite {
	private CourtSite() {}

	public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, CourtContent.id("court_realm_biomes"));
	public static final int MIN = 1300, MAX = 2400;
	public static final int[] RINGS = {1500, 1900, 2300, 1350, 1700, 2100};
	public static final int PER_RING = 32;

	public static List<BlockPos> candidates(long seed, BlockPos spawn) {
		return candidates(seed, spawn, Factions.ALDERMARK);
	}

	/** Candidate ring positions for {@code faction}: Aldermark's are the 2.0 ones; every other faction starts at its own angle. */
	public static List<BlockPos> candidates(long seed, BlockPos spawn, Identifier faction) {
		long fs = Factions.ALDERMARK.equals(faction) ? 0L : faction.toString().hashCode() * 0x9E3779B97F4A7C15L;
		RandomSource r = RandomSource.create(seed ^ 0x41DE12A4CL ^ fs);
		double a0 = r.nextDouble() * Math.PI * 2;
		List<BlockPos> out = new ArrayList<>();
		for (int ring = 0; ring < RINGS.length; ring++)
			for (int k = 0; k < PER_RING; k++) {
				double a = a0 + k * Math.PI * 2 / PER_RING + ring * 0.13;
				int x = spawn.getX() + (int) Math.round(Math.cos(a) * RINGS[ring]);
				int z = spawn.getZ() + (int) Math.round(Math.sin(a) * RINGS[ring]);
				out.add(new BlockPos((x & ~15) + 8, 0, (z & ~15) + 8));
			}
		return out;
	}

	public static boolean validBiome(BiomeResolver res, int x, int y, int z) {
		return validBiome(res, x, y, z, b -> b.is(BIOMES));
	}

	public static boolean validBiome(BiomeResolver res, int x, int y, int z, Predicate<Holder<Biome>> ok) {
		return ok.test(res.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
	}

	/** The faction's biome preference from its data ({@code biomes}: ids or #tags); Aldermark (or no preference) uses the court realm tag. */
	public static Predicate<Holder<Biome>> biomePredicate(Identifier faction) {
		FactionDef d = Factions.def(faction);
		if (d == null || d.biomes().isEmpty()) return b -> b.is(BIOMES);
		List<Predicate<Holder<Biome>>> any = new ArrayList<>();
		for (String s : d.biomes()) {
			boolean tag = s.startsWith("#");
			Identifier id = Identifier.tryParse(tag ? s.substring(1) : s);
			if (id == null) continue;
			if (tag) {
				TagKey<Biome> t = TagKey.create(Registries.BIOME, id);
				any.add(b -> b.is(t));
			} else any.add(b -> b.is(id));
		}
		return b -> any.stream().anyMatch(p -> p.test(b));
	}

	/** Realm spacing (RIVALS-DECISIONS 7, 15): castle to castle at least 1,800 blocks; every realm needs another realm's castle within 3,000. */
	public static final int MIN_APART = 1800, NEIGHBOUR = 3000;
	/** Kept for old callers: the hard minimum between castles. */
	public static final int KEEP_APART = MIN_APART;

	static double dist(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	static boolean apart(BlockPos c, List<BlockPos> avoid) {
		for (BlockPos o : avoid) if (dist(o, c) < MIN_APART) return false;
		return true;
	}

	/** The first candidate that fits, with y set to the floor level, or null. */
	public static @Nullable BlockPos choose(long seed, BlockPos spawn, ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, Predicate<ChunkPos> generated) {
		return choose(seed, spawn, gen, rs, h, generated, Factions.ALDERMARK, List.of());
	}

	/** As above for {@code faction} on its ring, skipping candidates within {@link #MIN_APART} of the realms in {@code avoid}. */
	public static @Nullable BlockPos choose(long seed, BlockPos spawn, ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, Predicate<ChunkPos> generated, Identifier faction, List<BlockPos> avoid) {
		if (!(gen instanceof NoiseBasedChunkGenerator)) return null;
		return scan(candidates(seed, spawn, faction), gen, rs, h, generated, biomePredicate(faction), avoid, false, 16);
	}

	// ---- rivals (RIVALS-DECISIONS 15): scored, one 90-degree wedge, jointly chosen -----------------------------------------------

	/** What a rival wants of its site, read from the faction JSON ({@code biomes}, {@code biomes_fallback}, {@code site}). */
	public record Spec(Identifier faction, Predicate<Holder<Biome>> strict, Predicate<Holder<Biome>> wide, boolean rise, boolean nearWater, boolean waterfront) {}

	public static final int WEDGE_MIN = 1500, WEDGE_MAX = 5000, BAND_STEP = 1000, BAND_LAST = 7000, TOP_SCORED = 40, TOP_JOINT = 15, MAX_RANGE = 16;
	public static final double WEDGE_HALF = Math.toRadians(43.0);
	/** Total time of the last rival search in milliseconds (logged by the caller, asserted by the tests). */
	public static volatile long lastSearchMs;
	/** Per-attempt candidate counts of the last search (diagnostics, shown by the self-test on a failure). */
	public static final List<String> DEBUG = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** The faction's placement wish: JSON {@code site: {terrain: rise|flat, near_water: bool}} with these defaults: Hrafnholt rise; Azmar and every other faction flat and near water. */
	public static Spec spec(@Nullable ResourceManager rm, Identifier faction) {
		boolean rise = faction.getPath().contains("hrafnholt"), water = !rise, front = rise;
		Predicate<Holder<Biome>> strict = biomePredicate(faction);
		Predicate<Holder<Biome>> wide = null;
		JsonObject o = rm == null ? null : rawFaction(rm, faction);
		if (o != null) {
			if (o.has("site") && o.get("site").isJsonObject()) {
				JsonObject s = o.getAsJsonObject("site");
				if (s.has("terrain") && s.get("terrain").isJsonPrimitive()) rise = "rise".equals(s.get("terrain").getAsString());
				if (s.has("waterfront") && s.get("waterfront").isJsonPrimitive()) front = s.get("waterfront").getAsBoolean();
				if (s.has("near_water") && s.get("near_water").isJsonPrimitive()) water = s.get("near_water").getAsBoolean();
			}
			if (o.has("biomes_fallback") && o.get("biomes_fallback").isJsonArray()) {
				List<String> l = new ArrayList<>();
				for (JsonElement e : o.getAsJsonArray("biomes_fallback")) if (e.isJsonPrimitive()) l.add(e.getAsString());
				if (!l.isEmpty()) wide = predicateOf(l);
			}
		}
		if (wide == null) {
			Predicate<Holder<Biome>> st = strict;
			wide = b -> st.test(b) || b.is(BIOMES);
		}
		return new Spec(faction, strict, wide, rise, water, front);
	}

	static @Nullable JsonObject rawFaction(ResourceManager rm, Identifier f) {
		try {
			var r = rm.getResource(Identifier.fromNamespaceAndPath(f.getNamespace(), "kingdom_faction/" + f.getPath() + ".json"));
			if (r.isEmpty()) return null;
			try (var in = r.get().openAsReader()) {
				JsonElement e = JsonParser.parseReader(in);
				return e.isJsonObject() ? e.getAsJsonObject() : null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	public static Predicate<Holder<Biome>> predicateOf(List<String> list) {
		List<Predicate<Holder<Biome>>> any = new ArrayList<>();
		for (String s : list) {
			boolean tag = s.startsWith("#");
			Identifier id = Identifier.tryParse(tag ? s.substring(1) : s);
			if (id == null) continue;
			if (tag) {
				TagKey<Biome> t = TagKey.create(Registries.BIOME, id);
				any.add(b -> b.is(t));
			} else any.add(b -> b.is(id));
		}
		return b -> any.stream().anyMatch(p -> p.test(b));
	}

	/** One scored candidate. */
	private static final class Cand {
		final int x, z;
		double score;
		int y;
		int dir = -1, shore;
		int wet; // water-biome samples on the cheap 25-point lattice
		Cand(int x, int z) {
			this.x = x;
			this.z = z;
		}
		BlockPos pos() {
			return new BlockPos(x, y, z);
		}
	}

	/** Caches shared by every pass of one search (biome and height lookups are the cost). */
	private static final class Ctx {
		final ChunkGenerator gen;
		final RandomState rs;
		final LevelHeightAccessor h;
		final BiomeResolver res;
		final int sea;
		final Map<Long, Holder<Biome>> biomes = new HashMap<>();
		final Map<Long, Integer> heights = new HashMap<>();
		Ctx(ChunkGenerator gen, RandomState rs, LevelHeightAccessor h) {
			this.gen = gen;
			this.rs = rs;
			this.h = h;
			this.res = gen.getBiomeSource().createResolver(rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED));
			this.sea = gen.getSeaLevel();
		}
		int heightCalls;
		long heightNs;
		Holder<Biome> biome(int x, int z) {
			return biomes.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> res.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sea + 8), QuartPos.fromBlock(z)));
		}
		/** The surface biome at the floor height (what a player standing there sees; the cheap pass samples at sea level + 8). */
		Holder<Biome> biomeAtY(int x, int y, int z) {
			return res.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		}
		int floor(int x, int z) {
			long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
			Integer v = heights.get(key);
			if (v != null) return v;
			long t = System.nanoTime();
			int y = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, h, rs);
			heightNs += System.nanoTime() - t;
			heightCalls++;
			heights.put(key, y);
			return y;
		}
	}

	/** Water bearing per faction of the last search (radians, atan2(dz, dx) from the castle towards the water), for waterfront realms. */
	public static final Map<Identifier, Double> WATER_BEARING = new java.util.concurrent.ConcurrentHashMap<>();
	/** Shore distance (blocks from the castle centre along the water bearing) per faction of the last search. */
	public static final Map<Identifier, Integer> WATER_SHORE = new java.util.concurrent.ConcurrentHashMap<>();
	static final int[] WATER_RADII = {32, 64, 96};

	/**
	 * Share of 24 cheap samples (3 radii 32-96, 8 directions) that are water (floor at or below sea level). The waterfront faces north or south (the towns lie east and west),
	 * whichever side has more water; {@code info} gets {dir (1 south, 3 north, -1 none), shore distance} where the floor first drops to sea level along that axis (40-136 blocks).
	 */
	static double waterRing(Ctx cx, int x, int z, int @Nullable [] info) {
		int wet = 0, north = 0, south = 0;
		for (int r : WATER_RADII) for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			int dx = (int) Math.round(Math.cos(a) * r), dz = (int) Math.round(Math.sin(a) * r);
			if (cx.floor(x + dx, z + dz) <= cx.sea) {
				wet++;
				if (dz < 0) north++;
				else if (dz > 0) south++;
			}
		}
		if (info != null) {
			info[0] = -1;
			info[1] = 0;
			int dir = south >= north ? 1 : 3;
			if (Math.max(north, south) >= 3) for (int u = 56; u <= 136; u += 8) { // at least 56: the castle's own terrain blending ends about 46 blocks out
				int zz = z + (dir == 1 ? u : -u);
				if (cx.floor(x, zz) <= cx.sea) {
					info[0] = dir;
					info[1] = u;
					break;
				}
			}
		}
		return wet / 24.0;
	}

	/** Water share of the ring around a site (for tests). */
	public static double waterShare(ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, BlockPos site) {
		return waterRing(new Ctx(gen, rs, h), site.getX(), site.getZ(), null);
	}

	/** The noise biome at a block (for tests and tools). */
	public static Holder<Biome> biomeAt(ChunkGenerator gen, RandomState rs, int x, int y, int z) {
		return gen.getBiomeSource().createResolver(rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED)).getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
	}

	static boolean isWater(Holder<Biome> b) {
		return b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN);
	}

	/**
	 * RIVALS-DECISIONS 15: places {@code specs} (rivals) jointly. All sit in the 90-degree wedge centred on Aldermark's bearing from spawn (or a seeded bearing),
	 * 1,500-5,000 blocks out, 1,800+ apart from each other and from {@code fixed} (realms already placed, Aldermark included) and each with a neighbour within 3,000.
	 * Footprints must be in never-generated chunks; when nothing fits the band moves outward 1,000 at a time up to 12,000, and only then do the biome rules relax
	 * (own biomes, then the fallback list, then anything dry). Deterministic from the seed. Returns faction -> site (floor y set); a faction that cannot be placed is absent.
	 */
	public static Map<Identifier, BlockPos> chooseRivals(long seed, BlockPos spawn, ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, Predicate<ChunkPos> generated, @Nullable BlockPos aldermark, List<BlockPos> fixed, List<Spec> specs) {
		long t0 = System.nanoTime();
		DEBUG.clear();
		// chunk-storage lookups (a blocking IO round trip each) are memoised for this search: pools of the three rivals share one candidate grid,
		// the 15- and 60-wide joint passes re-check the same footprints, and bands overlap, so most footprint chunks were read several times
		Predicate<ChunkPos> rawGenerated = generated;
		Map<Long, Boolean> genMemo = new HashMap<>();
		generated = cp -> genMemo.computeIfAbsent(((long) cp.x() << 32) ^ (cp.z() & 0xFFFFFFFFL), k -> rawGenerated.test(cp));
		Map<Identifier, BlockPos> out = new LinkedHashMap<>();
		if (!(gen instanceof NoiseBasedChunkGenerator) || specs.isEmpty()) return out;
		Ctx cx = new Ctx(gen, rs, h);
		double bearing = aldermark != null ? Math.atan2(aldermark.getZ() - spawn.getZ(), aldermark.getX() - spawn.getX()) : RandomSource.create(seed ^ 0x5EC70BL).nextDouble() * Math.PI * 2;
		Cand[] chosen = biomeFirst(cx, seed, spawn, bearing, generated, fixed, specs);
		Cand[] spacingOnly = null;
		List<List<Cand>> widest = null;
		// phase 2 (rare: some faction has none of its own or fallback biomes anywhere in the wedge out to 12,000, or the spacing rules cannot be met with them):
		// the relaxed search, own biomes first, then any dry land and rougher ground, each band 1,500-5,000 + band
		for (int band = 0; band <= BAND_LAST && chosen == null && !(spacingOnly != null && band > 2000); band += BAND_STEP) {
			// each faction's pool starts with level 0 (own biomes + its water wish) and only adds the next relaxation level while it has fewer than 8 candidates:
			// 1 fallback biomes, 2 no water demand, 3 any biome, 4 rougher ground (height range 24). Relaxed levels carry a score penalty, so strict candidates win where they exist.
			List<List<Cand>> pools = new ArrayList<>();
			boolean any = true;
			for (Spec sp : specs) {
				Map<Long, Cand> merged = new HashMap<>();
				for (int level = 0; level <= 4; level++) {
					if (level > 0 && merged.size() >= 8) break;
					if (level == 2 && !sp.waterfront()) continue; // identical to level 1 without a water demand
					for (Cand c : scored(cx, seed, spawn, bearing, WEDGE_MIN + band, WEDGE_MAX + band, band, band, sp, level)) {
						Cand k = new Cand(c.x, c.z);
						k.y = c.y;
						k.dir = c.dir;
						k.shore = c.shore;
						k.score = c.score - 1.2 * level;
						merged.merge(((long) c.x << 32) ^ (c.z & 0xFFFFFFFFL), k, (a, b) -> a.score >= b.score ? a : b);
					}
				}
				List<Cand> c = new ArrayList<>(merged.values());
				c.sort(CourtSite::byScore);
				c = diverse(c, 60, 300);
				if (c.isEmpty()) any = false;
				pools.add(c);
			}
			if (!any) continue;
			for (int top : new int[] {TOP_JOINT, 60}) {
				List<List<Cand>> valid = new ArrayList<>();
				boolean ok = true;
				for (List<Cand> pool : pools) {
					List<Cand> v = new ArrayList<>();
					for (Cand c : pool) {
						if (v.size() >= top) break;
						if (freshFootprint(c, generated, cx.sea)) v.add(c);
					}
					if (v.isEmpty()) ok = false;
					valid.add(v);
				}
				if (!ok) break;
				widest = valid;
				chosen = joint(valid, fixed, true);
				if (chosen != null) break;
				if (top == 60 && spacingOnly == null) spacingOnly = joint(valid, fixed, false);
			}
		}
		if (chosen == null) chosen = spacingOnly;
		if (chosen == null && widest != null) { // last resort: each faction takes its best candidate that keeps the 1,800-block spacing; one that cannot be placed stays out
			Cand[] g = new Cand[specs.size()];
			List<BlockPos> taken = new ArrayList<>(fixed);
			boolean all = true;
			for (int i = 0; i < specs.size(); i++) {
				for (Cand c : widest.get(i)) {
					BlockPos p = new BlockPos(c.x, 0, c.z);
					if (apart(p, taken)) {
						g[i] = c;
						taken.add(p);
						break;
					}
				}
				if (g[i] == null) all = false;
			}
			for (int i = 0; i < specs.size(); i++) if (g[i] != null) putResult(out, specs.get(i).faction(), g[i]);
			if (!all) chosen = null;
		}
		if (chosen != null) for (int i = 0; i < specs.size(); i++) putResult(out, specs.get(i).faction(), chosen[i]);
		lastSearchMs = (System.nanoTime() - t0) / 1_000_000L;
		DEBUG.add("total " + lastSearchMs + " ms, heights " + cx.heightCalls + " in " + cx.heightNs / 1_000_000L + " ms, biomes " + cx.biomes.size());
		return out;
	}

	/** Phase 1 band rings: the 1,500-5,000 wedge first, then 1,000-block rings outward to 12,000 (each scanned once). */
	static final int[][] RINGS_P1 = {{1500, 5000}, {5000, 6000}, {6000, 7000}, {7000, 8000}, {8000, 9000}, {9000, 10000}, {10000, 11000}, {11000, 12000}};

	/**
	 * Phase 1 (RIVALS-DECISIONS 15, "the band moves outward ... biome rules relax to the fallback list last"): every faction's pool holds only sites whose castle centre
	 * stands in its own biomes or its fallback list. A faction whose pool is short scans the next ring outward; when the pools are full but the spacing rules cannot
	 * be met, every faction scans the next ring. Rings are disjoint, so nothing is sampled twice. Null when no ring out to 12,000 gives a valid combination.
	 */
	private static @Nullable Cand[] biomeFirst(Ctx cx, long seed, BlockPos spawn, double bearing, Predicate<ChunkPos> generated, List<BlockPos> fixed, List<Spec> specs) {
		int n = specs.size();
		List<Map<Long, Cand>> merged = new ArrayList<>();
		for (int i = 0; i < n; i++) merged.add(new HashMap<>());
		boolean all = true, focused = false;
		BlockPos anchor = fixed.size() == 1 ? fixed.get(0) : null; // with two or more fixed realms the anchor may already have a neighbour
		for (int k = 0; k < RINGS_P1.length; k++) {
			// focus round: the anchor (Aldermark) needs a neighbour, and only sites 1,800-3,000 from it can be one; outer rings cannot help, so that
			// zone is sampled once more on its own grid before looking further out
			BlockPos focus = null;
			if (k > 0 && anchor != null && !nearAnchor(merged, anchor)) {
				if (focused) return null; // no site can be the anchor's neighbour, so no outer ring can help: the relaxed phase takes over
				focused = true;
				focus = anchor;
				k--;
			}
			int dMin = focus != null ? WEDGE_MIN : RINGS_P1[k][0], dMax = focus != null ? (int) Math.min(WEDGE_MAX + BAND_STEP, Math.hypot(anchor.getX() - spawn.getX(), anchor.getZ() - spawn.getZ()) + NEIGHBOUR) : RINGS_P1[k][1];
			int salt = focus != null ? 0x200000 : k == 0 ? 0 : 0x100000 + k;
			for (int i = 0; i < n; i++) {
				boolean full = merged.get(i).size() >= 8;
				if (focus == null && !all && full) continue;
				// a short pool samples up to 30 heights-checked candidates in the first ring and 16 in each outer ring; a full pool that only widens for the
				// spacing rules takes a few more (10 checked, 6 kept)
				int cap = focus != null ? 24 : full ? 10 : k == 0 ? 30 : 16, keep = focus != null ? 8 : full ? 6 : 12;
				Spec sp = specs.get(i);
				int fresh = 0;
				for (int level = 0; level <= 2; level++) {
					if (level > 0 && fresh >= (focus != null ? 4 : full ? 4 : 8)) break;
					if (level == 2 && !sp.waterfront()) continue;
					for (Cand c : scored(cx, seed, spawn, bearing, dMin, dMax, 0, salt, sp, level, cap, keep, focus)) {
						Cand q = new Cand(c.x, c.z);
						q.y = c.y;
						q.dir = c.dir;
						q.shore = c.shore;
						q.score = c.score - 1.2 * level;
						long key = ((long) c.x << 32) ^ (c.z & 0xFFFFFFFFL);
						Cand old = merged.get(i).get(key);
						if (old == null && !freshFootprint(q, generated, cx.sea)) continue; // existing worlds: only never-generated footprints enter the pool
						if (old == null) fresh++;
						if (old == null || q.score > old.score) merged.get(i).put(key, q);
					}
				}
			}
			List<List<Cand>> pools = new ArrayList<>();
			boolean empty = false;
			StringBuilder sizes = new StringBuilder();
			for (int i = 0; i < n; i++) {
				List<Cand> c = new ArrayList<>(merged.get(i).values());
				c.sort(CourtSite::byScore);
				c = diverse(c, 60, 300);
				if (c.isEmpty()) empty = true;
				pools.add(c);
				sizes.append(' ').append(c.size());
			}
			DEBUG.add("P1 " + (focus != null ? "focus " : "ring ") + dMin + "-" + dMax + " pools" + sizes + " heights " + cx.heightCalls + (anchor != null && !nearAnchor(merged, anchor) ? " (no anchor neighbour)" : ""));
			if (empty) {
				all = false; // only the short pools look further out
				continue;
			}
			for (int top : new int[] {TOP_JOINT, 60}) {
				List<List<Cand>> valid = new ArrayList<>();
				boolean ok = true;
				for (List<Cand> pool : pools) {
					List<Cand> v = new ArrayList<>();
					for (Cand c : pool) {
						if (v.size() >= top) break;
						if (freshFootprint(c, generated, cx.sea)) v.add(c);
					}
					if (v.isEmpty()) ok = false;
					valid.add(v);
				}
				if (!ok) break;
				Cand[] pick = joint(valid, fixed, true);
				if (pick != null) return pick;
			}
			// the pools exist but cannot be combined: the short pools look one ring further out first; after that (or when none is short) everyone does
			boolean anyShort = false;
			for (int i = 0; i < n; i++) if (merged.get(i).size() < 8) anyShort = true;
			all = !anyShort || !all;
		}
		return null;
	}

	/** Whether any pooled candidate could be the anchor's neighbour (1,800-3,000 from it). */
	private static boolean nearAnchor(List<Map<Long, Cand>> merged, BlockPos anchor) {
		for (Map<Long, Cand> m : merged) for (Cand c : m.values()) {
			double d = Math.hypot(c.x - anchor.getX(), c.z - anchor.getZ());
			if (d >= MIN_APART && d <= NEIGHBOUR) return true;
		}
		return false;
	}

	/** Any water biome on two rings (48 and 96 blocks, 16 directions): the cheap gate before the waterfront's height samples. */
	static boolean waterHint(Ctx cx, int x, int z) {
		for (int r : new int[] {48, 96}) for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8;
			if (isWater(cx.biome(x + (int) Math.round(Math.cos(a) * r), z + (int) Math.round(Math.sin(a) * r)))) return true;
		}
		return false;
	}

	private static void putResult(Map<Identifier, BlockPos> out, Identifier f, Cand c) {
		out.put(f, c.pos());
		if (c.dir >= 0) {
			WATER_BEARING.put(f, c.dir == 1 ? Math.PI / 2 : -Math.PI / 2);
			WATER_SHORE.put(f, c.shore);
		} else {
			WATER_BEARING.remove(f);
			WATER_SHORE.remove(f);
		}
	}

	/**
	 * Scored, diverse candidates of one faction at one relaxation level (0 own biomes, 1 fallback biomes, 2 fallback biomes without the water demand, 3 any dry land,
	 * 4 rougher ground) on the grid {@code dMin}-{@code dMax} blocks from spawn, best first. {@code distRef} shifts the preferred distance (3,000) outward for the
	 * relaxed bands; {@code salt} seeds the grid jitter. Heights are sampled for at most the best {@link #TOP_SCORED} candidates (RIVALS-DECISIONS 15).
	 */
	private static List<Cand> scored(Ctx cx, long seed, BlockPos spawn, double bearing, int dMin, int dMax, int distRef, int salt, Spec sp, int level) {
		return scored(cx, seed, spawn, bearing, dMin, dMax, distRef, salt, sp, level, TOP_SCORED, 12, null);
	}

	/** As above with at most {@code cap} candidates height-sampled and at most {@code keep} survivors; with {@code focus}, only sites 1,800-3,000 from it. */
	private static List<Cand> scored(Ctx cx, long seed, BlockPos spawn, double bearing, int dMin, int dMax, int distRef, int salt, Spec sp, int level, int cap, int keep, @Nullable BlockPos focus) {
		Predicate<Holder<Biome>> ok = level == 0 ? sp.strict() : sp.wide();
		RandomSource r = RandomSource.create(seed ^ 0x7A11E5L ^ salt); // same grid for every faction, so the biome and height caches are shared
		List<Cand> cheap = new ArrayList<>();
		int rejDry = 0, rejRange = 0, rejWater = 0, rejBiome = 0, evaluated = 0;
		int step = 160;
		for (double d = dMin; d <= dMax; d += step) {
			int n = Math.max(1, (int) Math.floor(2 * WEDGE_HALF * d / step));
			for (int k = 0; k <= n; k++) {
				double a = bearing - WEDGE_HALF + (n == 0 ? WEDGE_HALF : 2 * WEDGE_HALF * k / n) + (r.nextDouble() - 0.5) * (step / d) * 0.5;
				double dd = d + (r.nextDouble() - 0.5) * step * 0.5;
				int x = spawn.getX() + (int) Math.round(Math.cos(a) * dd), z = spawn.getZ() + (int) Math.round(Math.sin(a) * dd);
				x = (x & ~15) + 8;
				z = (z & ~15) + 8;
				double ang = Math.atan2(z - spawn.getZ(), x - spawn.getX()), df = Math.abs(Math.atan2(Math.sin(ang - bearing), Math.cos(ang - bearing)));
				double dist = Math.hypot(x - spawn.getX(), z - spawn.getZ());
				if (df > WEDGE_HALF + 0.005 || dist < dMin || dist > dMax) continue;
				if (focus != null) {
					double fd = Math.hypot(x - focus.getX(), z - focus.getZ());
					if (fd < MIN_APART || fd > NEIGHBOUR) continue;
				}
				Holder<Biome> centre = cx.biome(x, z);
				if (isWater(centre)) continue;
				if (level < 3 && !ok.test(centre)) continue;
				int hit = 0, wet = 0;
				for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
					Holder<Biome> b = cx.biome(((x + dx * 96) & ~63) + 32, ((z + dz * 96) & ~63) + 32); // 64-block lattice: neighbouring candidates share lookups
					// water biomes (rivers, oceans) only feed the waterfront ring, never the land fit: no sandbars or tiny islands
					if (isWater(b)) wet++;
					else if (ok.test(b)) hit++;
				}
				double fit = hit / 25.0;
				if (level < 3 && fit < (level == 0 ? 0.4 : 0.24)) continue;
				Cand c = new Cand(x, z);
				c.wet = wet;
				double distScore = Math.max(0, 1 - Math.abs(dist - distRef - 3000) / 2500.0);
				c.score = 3.0 * fit + 1.0 * distScore + (sp.nearWater() ? 0.8 * Math.min(1, wet / 3.0) : 0) + (sp.waterfront() ? 2.5 * Math.min(1, wet / 3.0) : 0);
				cheap.add(c);
			}
		}
		cheap.sort(CourtSite::byScore);
		List<Cand> top = diverse(cheap, 120, 220);
		List<Cand> out = new ArrayList<>();
		int maxRange = level >= 4 ? 24 : MAX_RANGE;
		int[][] probes = {{0, 0}, {-30, -30}, {30, 30}, {30, -30}, {-30, 30}, {0, -30}, {0, 30}, {-30, 0}, {30, 0}}; // the castle's own footprint (the towns adapt to their own ground)
		int[][] ring = {{0, -150}, {0, 150}, {-150, 0}, {150, 0}};
		for (Cand c : top) {
			if (out.size() >= keep || evaluated >= cap) break; // heights only for the best 40 (or fewer), and only until enough candidates survive
			// waterfront: a site with no water biome within ~100 blocks cannot pass the water ring, so it is dropped (or scored dry) before any height sample
			boolean hint = sp.waterfront() && waterHint(cx, c.x, c.z);
			if (sp.waterfront() && level < 2 && !hint) {
				rejWater++;
				continue;
			}
			evaluated++;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int[] p : probes) { // the first five probes (centre and the four corners of the keep) reject most rough sites cheaply; a wet probe ends it too
				int y = cx.floor(c.x + p[0], c.z + p[1]);
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
				if (hi - lo > maxRange || lo <= cx.sea + 1) break;
			}
			if (hi - lo > maxRange) {
				rejRange++;
				continue;
			}
			if (lo <= cx.sea + 1 || cx.floor(c.x, c.z) <= cx.sea + 1) {
				rejDry++;
				continue;
			}
			c.y = Math.round((lo + hi) / 2.0F) + 1;
			// the castle centre's biome where it stands (the cheap pass samples at sea level + 8; on hills the surface can differ)
			if (level < 3 && !ok.test(cx.biomeAtY(c.x, c.y, c.z))) {
				rejBiome++;
				continue;
			}
			int rlo = Integer.MAX_VALUE, rhi = Integer.MIN_VALUE, rsum = 0;
			for (int[] p : ring) {
				int y = cx.floor(c.x + p[0], c.z + p[1]);
				rlo = Math.min(rlo, y);
				rhi = Math.max(rhi, y);
				rsum += y;
			}
			if (sp.waterfront()) {
				int[] wi = {-1, 0};
				double share = hint ? waterRing(cx, c.x, c.z, wi) : 0;
				if ((share < 0.25 || wi[0] < 0) && level < 2) {
					rejWater++;
					continue;
				}
				boolean wet = share >= 0.25 && wi[0] >= 0;
				c.dir = wet ? wi[0] : -1;
				c.shore = wet ? wi[1] : 0;
				c.score += 2.0 * clamp01(share / 0.5);
			}
			double terra = 1.0 - (hi - lo) / (double) MAX_RANGE;
			double wish = sp.rise() ? clamp01(((lo + hi) / 2.0 - rsum / 4.0) / 12.0) : clamp01(1.0 - (rhi - rlo) / 24.0);
			double water = rlo <= cx.sea ? 1.0 : 0.0;
			c.score += 1.5 * clamp01(terra) + 1.5 * wish + (sp.nearWater() ? 0.8 * water : 0);
			out.add(c);
		}
		out.sort(CourtSite::byScore);
		DEBUG.add(sp.faction().getPath() + " L" + level + " " + dMin + "-" + dMax + ": cheap " + cheap.size() + " top " + top.size() + " eval " + evaluated + " ok " + out.size() + " (range " + rejRange + ", dry " + rejDry + ", water " + rejWater + ", biome " + rejBiome + ")");
		return diverse(out, TOP_SCORED, 300);
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : Math.min(v, 1);
	}

	private static int byScore(Cand a, Cand b) {
		int c = Double.compare(b.score, a.score);
		if (c != 0) return c;
		c = Integer.compare(a.x, b.x);
		return c != 0 ? c : Integer.compare(a.z, b.z);
	}

	/** The best {@code n} candidates, each at least {@code gap} blocks from the better ones already taken. */
	private static List<Cand> diverse(List<Cand> sorted, int n, int gap) {
		List<Cand> out = new ArrayList<>();
		for (Cand c : sorted) {
			if (out.size() >= n) break;
			boolean far = true;
			for (Cand o : out) if (Math.hypot(o.x - c.x, o.z - c.z) < gap) {
				far = false;
				break;
			}
			if (far) out.add(c);
		}
		return out;
	}

	/** Every footprint chunk (castle, both towns, road: x +-112, z -48..+48) must be absent from chunk storage; the centre chunk goes first. */
	private static boolean freshFootprint(Cand c, Predicate<ChunkPos> generated, int sea) {
		ChunkPos cp = ChunkPos.containing(new BlockPos(c.x, 0, c.z));
		if (generated.test(cp)) return false;
		for (int dx = -7; dx <= 7; dx++) for (int dz = -3; dz <= 3; dz++)
			if ((dx != 0 || dz != 0) && generated.test(new ChunkPos(cp.x() + dx, cp.z() + dz))) return false;
		if (c.dir >= 0) { // the waterfront's own chunks too (jetty, boathouse and ships are built only in new chunks)
			BoundingBox wb = Waterfront.box(new BlockPos(c.x, 0, c.z), c.dir, c.shore, sea);
			for (int cx = wb.minX() >> 4; cx <= wb.maxX() >> 4; cx++) for (int cz = wb.minZ() >> 4; cz <= wb.maxZ() >> 4; cz++)
				if (generated.test(new ChunkPos(cx, cz))) return false;
		}
		return true;
	}

	/** The best-scoring combination (one per pool) under the spacing rules, or null. */
	private static @Nullable Cand[] joint(List<List<Cand>> pools, List<BlockPos> fixed, boolean needNeighbour) {
		Cand[] pick = new Cand[pools.size()];
		double[] bestScore = {Double.NEGATIVE_INFINITY}; // relaxed candidates carry penalties, so a valid sum can be negative
		Cand[][] holder = {null};
		dfs(0, pools, fixed, needNeighbour, pick, 0, bestScore, holder);
		return holder[0];
	}

	private static void dfs(int i, List<List<Cand>> pools, List<BlockPos> fixed, boolean needNeighbour, Cand[] pick, double sum, double[] bestScore, Cand[][] holder) {
		if (i == pools.size()) {
			if (needNeighbour && !neighbours(pick, fixed)) return;
			if (sum > bestScore[0]) {
				bestScore[0] = sum;
				holder[0] = pick.clone();
			}
			return;
		}
		for (Cand c : pools.get(i)) {
			BlockPos p = new BlockPos(c.x, 0, c.z);
			if (!apart(p, fixed)) continue;
			boolean fine = true;
			for (int j = 0; j < i; j++) if (Math.hypot(pick[j].x - c.x, pick[j].z - c.z) < MIN_APART) fine = false;
			if (!fine) continue;
			pick[i] = c;
			dfs(i + 1, pools, fixed, needNeighbour, pick, sum + c.score, bestScore, holder);
		}
	}

	/** Every realm (the picks and the fixed ones) has another realm within 3,000; trivially true when there is only one realm. */
	private static boolean neighbours(Cand[] pick, List<BlockPos> fixed) {
		List<BlockPos> all = new ArrayList<>(fixed);
		for (Cand c : pick) all.add(new BlockPos(c.x, 0, c.z));
		if (all.size() < 2) return true;
		for (int i = 0; i < all.size(); i++) {
			boolean near = false;
			for (int j = 0; j < all.size(); j++) if (i != j && dist(all.get(i), all.get(j)) <= NEIGHBOUR) near = true;
			if (!near) return false;
		}
		return true;
	}

	/** One rival placed after the realms in {@code placed} (the first is Aldermark's); kept for single-faction callers. */
	public static @Nullable BlockPos chooseNear(long seed, BlockPos spawn, ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, Predicate<ChunkPos> generated, Identifier faction, List<BlockPos> placed) {
		Map<Identifier, BlockPos> m = chooseRivals(seed, spawn, gen, rs, h, generated, placed.isEmpty() ? null : placed.get(0), placed, List.of(spec(null, faction)));
		return m.get(faction);
	}

	static @Nullable BlockPos scan(List<BlockPos> cands, ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, Predicate<ChunkPos> generated, Predicate<Holder<Biome>> biomeOk, List<BlockPos> avoid, boolean needNeighbour, int maxSlope) {
		BiomeSource src = gen.getBiomeSource();
		BiomeResolver res = src.createResolver(rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED));
		int sea = gen.getSeaLevel();
		for (BlockPos c : cands) {
			int x = c.getX(), z = c.getZ();
			if (!apart(c, avoid)) continue;
			if (needNeighbour) {
				boolean near = false;
				for (BlockPos o : avoid) if (dist(o, c) <= NEIGHBOUR) near = true;
				if (!near) continue;
			}
			BlockPos site = fit(c, gen, rs, h, generated, res, biomeOk, sea, maxSlope);
			if (site != null) return site;
		}
		return null;
	}

	/** Biome, flatness and freshness check of one candidate; the site with its floor y, or null. */
	static @Nullable BlockPos fit(BlockPos c, ChunkGenerator gen, RandomState rs, LevelHeightAccessor h, Predicate<ChunkPos> generated, BiomeResolver res, Predicate<Holder<Biome>> biomeOk, int sea, int maxSlope) {
		int x = c.getX(), z = c.getZ();
		int y0 = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, h, rs);
		if (!validBiome(res, x, y0, z, biomeOk) || !validBiome(res, x - CastleLayout.TOWN_DX, y0, z + CastleLayout.TOWN_DZ, biomeOk) || !validBiome(res, x + CastleLayout.TOWN_DX, y0, z + CastleLayout.TOWN_DZ, biomeOk)) return null;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		int[][] probes = {{0, 0}, {-28, -28}, {28, -28}, {-28, 28}, {28, 28}, {0, 40}, {-88, 8}, {88, 8}, {-60, 40}, {60, 40}, {-100, -10}, {100, -10}};
		for (int[] p : probes) {
			int y = gen.getBaseHeight(x + p[0], z + p[1], Heightmap.Types.OCEAN_FLOOR_WG, h, rs);
			lo = Math.min(lo, y);
			hi = Math.max(hi, y);
		}
		if (hi - lo > maxSlope || lo <= sea) return null;
		ChunkPos cp = ChunkPos.containing(c);
		// every chunk of the footprint (castle, both towns, road: x +-112, z -48..+48) must be absent from chunk storage
		for (int dx = -7; dx <= 7; dx++) for (int dz = -3; dz <= 3; dz++)
			if (generated.test(new ChunkPos(cp.x() + dx, cp.z() + dz))) return null;
		int floor = Math.round((lo + hi) / 2.0F) + 1;
		return new BlockPos(x, floor, z);
	}

	/** Chooses Aldermark's site for this overworld (spawn = the world's respawn position). */
	public static @Nullable BlockPos choose(ServerLevel overworld) {
		return choose(overworld, Factions.ALDERMARK, List.of());
	}

	public static @Nullable BlockPos choose(ServerLevel overworld, Identifier faction, List<BlockPos> avoid) {
		var gen = overworld.getChunkSource().getGenerator();
		var rs = overworld.getChunkSource().randomState();
		if (Factions.ALDERMARK.equals(faction)) return choose(overworld.getSeed(), overworld.getRespawnData().pos(), gen, rs, overworld, generatedTest(overworld), faction, avoid);
		return chooseRivals(overworld, List.of(faction), avoid.isEmpty() ? null : avoid.get(0), avoid).get(faction);
	}

	/** Places the rivals {@code factions} together (RIVALS-DECISIONS 15); {@code fixed} are the realms already placed. Logs the time. */
	public static Map<Identifier, BlockPos> chooseRivals(ServerLevel overworld, List<Identifier> factions, @Nullable BlockPos aldermark, List<BlockPos> fixed) {
		var rm = overworld.getServer().getResourceManager();
		List<Spec> specs = new ArrayList<>();
		for (Identifier f : factions) specs.add(spec(rm, f));
		Map<Identifier, BlockPos> m = chooseRivals(overworld.getSeed(), overworld.getRespawnData().pos(), overworld.getChunkSource().getGenerator(), overworld.getChunkSource().randomState(), overworld, generatedTest(overworld), aldermark, fixed, specs);
		CourtContent.LOG.info("Rival realm placement of {} took {} ms: {}", factions, lastSearchMs, m);
		return m;
	}

	static Predicate<ChunkPos> generatedTest(ServerLevel overworld) {
		var map = overworld.getChunkSource().chunkMap;
		return cp -> {
			try {
				return map.read(cp).join().isPresent();
			} catch (RuntimeException e) {
				return true;
			}
		};
	}
}
