package dev.elsewhere.core.worldgen;

import dev.elsewhere.Elsewhere;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Structure overlap guard (3.0.0): no Elsewhere structure start is ever created where it would overlap another structure that outranks it.
 *
 * <p>Applied at the end of {@code ChunkGenerator.createStructures} (STRUCTURE_STARTS, see {@code OverlapGuardChunkGeneratorMixin}): a start the
 * rule rejects is removed from its chunk before references, pieces, /locate, explorer maps or advancements can see it, so a skipped start leaves
 * nothing behind in any chunk. Works for every module's structures in the merged jar (they are judged by registry id).
 *
 * <p>Determinism: the rule only reads the <em>raw</em> starts the generator makes for each structure set in each chunk (what vanilla's own
 * selection loop would create there, recomputed from the seed and cached), never which neighbours happen to be generated, so the same pair always
 * resolves the same way whatever the chunk-load order, in new worlds and in new chunks of existing ones.
 *
 * <p>Rule: start A is dropped when a raw start B, A excluded, (1) outranks A, (2) is {@link #contested} with A (Elsewhere on at least one side,
 * not castle against castle) and (3) overlaps A: any piece of one within {@link #GAP} blocks of a piece of the other (whole bounding boxes when
 * either side is a castle, which terraforms its whole box). Filler sets ({@link #isFiller}: nether fossils, mineshafts, buried treasure) never
 * make an Elsewhere start give way. Rank: castles/waterfronts, then vanilla and other mods, then Elsewhere return rifts and
 * the lantern road, then boss arenas, then other Elsewhere structures; ties by id, then start chunk x, then z. B's starts are searched in every chunk within
 * {@link #REACH} chunks of A's inflated box (vanilla never places a piece more than 8 chunks from its start chunk).
 */
public final class OverlapGuard {
	private OverlapGuard() {}

	/** Kill switch: {@code -Delsewhere.overlap_guard=false}. */
	public static final boolean ENABLED = !"false".equals(System.getProperty("elsewhere.overlap_guard"));
	/** Minimum clear gap between two structures' pieces, in blocks. */
	public static final int GAP = 4;
	/** Chunks around a start's box searched for other start chunks (vanilla's structure reference reach). */
	public static final int REACH = 8;

	/** Ranks, best first. RIFT: return-rift structures and the Pale Hollow lantern road (every third carries a Return Rift), so a way home is never dropped for a site. */
	public static final int CASTLE = 0, FOREIGN = 1, RIFT = 2, ARENA = 3, OTHER = 4;

	/** Elsewhere boss arenas (overworld_bosses, nether_end_bosses). */
	private static final Set<String> ARENAS = Set.of("croaking_shrine", "glacier_shelf", "sun_chamber", "wreck_ring", "solaxis_pyre_cradle",
		"mournmaw_weeping_basin", "aphelion_shattered_halo", "grunthar_treasure_hall", "ossyra_matriarch_spire");
	private static final Map<Identifier, Integer> TIER_OVERRIDES = new ConcurrentHashMap<>();

	/** Starts this guard removed since boot (self-test and debug only). */
	public static final AtomicInteger SKIPPED = new AtomicInteger();

	/** Lets a module rank one of its structures differently ({@link #CASTLE} .. {@link #OTHER}). */
	public static void setTier(Identifier id, int tier) {
		TIER_OVERRIDES.put(id, tier);
	}

	public static boolean isElsewhere(Identifier id) {
		return id != null && Elsewhere.MOD_ID.equals(id.getNamespace());
	}

	public static int tier(Identifier id) {
		if (id == null) return FOREIGN;
		Integer o = TIER_OVERRIDES.get(id);
		if (o != null) return o;
		if (!isElsewhere(id)) return FOREIGN;
		String p = id.getPath();
		if (p.startsWith("castle_") || p.startsWith("waterfront_")) return CASTLE;
		if (p.endsWith("_return_rift") || p.equals("lantern_road")) return RIFT;
		if (ARENAS.contains(p)) return ARENA;
		return OTHER;
	}

	// ------------------------------------------------------------------ the pure rule

	/** A start as the guard sees it: id, rank, start chunk, whole box and piece boxes. */
	public record Shape(Identifier id, int tier, int cx, int cz, BoundingBox box, BoundingBox[] pieces) {
		public static Shape of(Identifier id, int cx, int cz, List<BoundingBox> pieces) {
			BoundingBox all = BoundingBox.encapsulatingBoxes(pieces).orElseThrow();
			return new Shape(id, OverlapGuard.tier(id), cx, cz, all, pieces.toArray(new BoundingBox[0]));
		}

		public static Shape of(Identifier id, StructureStart start) {
			List<BoundingBox> ps = new ArrayList<>();
			for (StructurePiece p : start.getPieces()) ps.add(p.getBoundingBox());
			if (ps.isEmpty()) ps.add(start.getBoundingBox());
			ChunkPos c = start.getChunkPos();
			return new Shape(id, OverlapGuard.tier(id), c.x(), c.z(), start.getBoundingBox(), ps.toArray(new BoundingBox[0]));
		}

		boolean sameStart(Shape o) {
			return cx == o.cx && cz == o.cz && id.equals(o.id);
		}
	}

	/** Negative when {@code a} outranks {@code b}: tier, then id, then start chunk x, then z. A total order, so every pair resolves one way. */
	public static int rank(Shape a, Shape b) {
		int c = Integer.compare(a.tier, b.tier);
		if (c == 0) c = a.id.toString().compareTo(b.id.toString());
		if (c == 0) c = Integer.compare(a.cx, b.cx);
		if (c == 0) c = Integer.compare(a.cz, b.cz);
		return c;
	}

	/** Symmetric: pieces within {@link #GAP} blocks (whole boxes when a castle is involved). */
	public static boolean overlaps(Shape a, Shape b) {
		if (!a.box.inflatedBy(GAP).intersects(b.box)) return false;
		if (a.tier == CASTLE || b.tier == CASTLE) return true;
		for (BoundingBox pa : a.pieces) {
			BoundingBox ia = pa.inflatedBy(GAP);
			for (BoundingBox pb : b.pieces) if (ia.intersects(pb)) return true;
		}
		return false;
	}

	/**
	 * Whether the guard judges this pair at all: Elsewhere on at least one side (vanilla against vanilla stays vanilla's business), and not two
	 * castle-rank starts (a realm's castle and waterfront are laid out together by kingdom_court's CourtSite, once per world).
	 */
	public static boolean contested(Shape a, Shape b) {
		return (isElsewhere(a.id) || isElsewhere(b.id)) && !(a.tier == CASTLE && b.tier == CASTLE);
	}

	/** Whether {@code b} makes {@code a} give way. */
	public static boolean yieldsTo(Shape a, Shape b) {
		return !a.sameStart(b) && contested(a, b) && rank(b, a) < 0 && overlaps(a, b);
	}

	/** The rule on a known list of raw starts: kept unless some other start outranks and overlaps it. */
	public static boolean keep(Shape a, Iterable<Shape> raw) {
		for (Shape b : raw) if (yieldsTo(a, b)) return false;
		return true;
	}

	// ------------------------------------------------------------------ worldgen

	/** Per-dimension state (one per ChunkGeneratorStructureState): set ranks, raw-start cache, decision cache. */
	static final class Ctx {
		final List<Holder<StructureSet>> sets;
		/** Best tier among a set's structures, and among its Elsewhere structures only (Integer.MAX_VALUE: none). */
		final int[] minTier, minElsewhereTier;
		/** Filler sets ({@link #isFiller}): never a blocker for an Elsewhere start. */
		final boolean[] filler;
		final Map<Structure, Integer> setOf = new IdentityHashMap<>();
		final Map<Long, Object> raw = new ConcurrentHashMap<>();
		final Map<String, Boolean> decisions = new ConcurrentHashMap<>();
		/** Chunks whose starts were made (STRUCTURE_STARTS ran) this session: generated even if not yet saved or announced to StructureCheck. */
		final it.unimi.dsi.fastutil.longs.LongSet generated = it.unimi.dsi.fastutil.longs.LongSets.synchronize(new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
		/** Self-test only: extra starts treated as raw starts (a synthetic castle). */
		final List<Shape> injected = new java.util.concurrent.CopyOnWriteArrayList<>();

		Ctx(ChunkGeneratorStructureState state) {
			sets = List.copyOf(state.possibleStructureSets());
			minTier = new int[sets.size()];
			minElsewhereTier = new int[sets.size()];
			filler = new boolean[sets.size()];
			for (int i = 0; i < sets.size(); i++) {
				int t = Integer.MAX_VALUE, te = Integer.MAX_VALUE;
				for (StructureSet.StructureSelectionEntry e : sets.get(i).value().structures()) {
					Identifier id = idOf(e.structure());
					int k = tier(id);
					t = Math.min(t, k);
					if (isElsewhere(id)) te = Math.min(te, k);
					setOf.merge(e.structure().value(), i, (x, y) -> -1);
				}
				minTier[i] = t;
				minElsewhereTier[i] = te;
				filler[i] = te == Integer.MAX_VALUE && isFiller(sets.get(i).value());
			}
		}

		boolean relevant(int set, Shape a) {
			if (a.tier == CASTLE) return false; // castles only meet castles, which the guard leaves to CourtSite
			if (filler[set]) return false; // dense vanilla/other-mod filler (nether fossils, mineshafts, buried treasure) never drops an Elsewhere start
			return (isElsewhere(a.id) ? minTier[set] : minElsewhereTier[set]) <= a.tier;
		}
	}

	/** Densest placement still judged as a blocker: sets placed every 1-2 chunks are filler, not sites. */
	public static final int FILLER_SPACING = 2;

	/**
	 * A set placed on a 1- or 2-chunk grid (vanilla: nether fossils, which tile every soul sand valley; mineshafts; buried treasure). Vanilla lets
	 * these overlap anything; ranking them above Elsewhere structures dropped nearly every start in their biomes (Mournmaw's basin is soul sand
	 * valley only, and a fossil sits within reach of almost every candidate: /locate found the nearest one 3,481 blocks out in the merged pre-gate
	 * run). They still yield to castles like any other non-Elsewhere start.
	 */
	public static boolean isFiller(StructureSet set) {
		return set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement rs && rs.spacing() <= FILLER_SPACING;
	}

	private static final Map<ChunkGeneratorStructureState, Ctx> CTX = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Object NONE = new Object();

	static Ctx ctx(ChunkGeneratorStructureState state) {
		synchronized (CTX) {
			return CTX.computeIfAbsent(state, Ctx::new);
		}
	}

	static Identifier idOf(Holder<Structure> h) {
		return h.unwrapKey().map(ResourceKey::identifier).orElse(null);
	}

	/** Everything needed to recompute a raw start anywhere in the dimension. */
	record Env(ChunkGenerator generator, ChunkGeneratorStructureState state, RegistryAccess registries, StructureTemplateManager templates,
		ResourceKey<Level> dimension, LevelHeightAccessor height, Climate.Sampler sampler) {}

	/**
	 * Called once per chunk at the end of {@code ChunkGenerator.createStructures}: removes every start in {@code chunk} that the rule rejects.
	 */
	public static void filterChunk(ChunkGenerator generator, RegistryAccess registries, ChunkGeneratorStructureState state, ChunkAccess chunk,
		StructureTemplateManager templates, ResourceKey<Level> dimension) {
		if (!ENABLED) return;
		Ctx ctx = ctx(state);
		ctx.generated.add(chunk.getPos().pack());
		Map<Structure, StructureStart> starts = chunk.getAllStarts();
		if (starts.isEmpty()) return;
		var reg = registries.lookupOrThrow(Registries.STRUCTURE);
		ChunkPos here = chunk.getPos();
		List<Map.Entry<Structure, Shape>> mine = new ArrayList<>();
		for (Map.Entry<Structure, StructureStart> e : starts.entrySet()) {
			if (e.getValue() == null || !e.getValue().isValid()) continue;
			Shape s = Shape.of(reg.getKey(e.getKey()), e.getValue());
			if (s.id == null) continue;
			mine.add(Map.entry(e.getKey(), s));
			Integer set = ctx.setOf.get(e.getKey());
			if (set != null && set >= 0) ctx.raw.putIfAbsent(key(set, here.x(), here.z()), s); // this chunk's own raw start, saves a recompute
		}
		Env env = null;
		List<Structure> drop = null;
		for (Map.Entry<Structure, Shape> m : mine) {
			if (env == null) env = new Env(generator, state, registries, templates, dimension, chunk,
				state.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build()));
			if (decide(ctx, env, m.getValue())) continue;
			if (drop == null) drop = new ArrayList<>();
			drop.add(m.getKey());
		}
		if (drop == null) return;
		Map<Structure, StructureStart> kept = new java.util.HashMap<>(starts);
		for (Structure s : drop) kept.remove(s);
		chunk.setAllStarts(kept);
		SKIPPED.addAndGet(drop.size());
	}

	/** Cached decision for one start (true: keep). */
	static boolean decide(Ctx ctx, Env env, Shape a) {
		String k = a.id + "@" + a.cx + "," + a.cz;
		Boolean d = ctx.decisions.get(k);
		if (d != null) return d;
		Shape by = blocker(ctx, env, a);
		boolean keep = by == null;
		if (!keep) Elsewhere.LOG.debug("[overlap-guard] skipped {} at chunk {},{}: overlaps {} at chunk {},{}", a.id, a.cx, a.cz, by.id, by.cx, by.cz);
		if (ctx.decisions.size() > 50_000) ctx.decisions.clear();
		ctx.decisions.put(k, keep);
		return keep;
	}

	/** The first raw start that makes {@code a} give way, or null. */
	static Shape blocker(Ctx ctx, Env env, Shape a) {
		BoundingBox b = a.box.inflatedBy(GAP);
		int x0 = (b.minX() >> 4) - REACH, x1 = (b.maxX() >> 4) + REACH, z0 = (b.minZ() >> 4) - REACH, z1 = (b.maxZ() >> 4) + REACH;
		for (Shape o : ctx.injected) if (yieldsTo(a, o)) return o;
		for (int i = 0; i < ctx.sets.size(); i++) {
			if (!ctx.relevant(i, a)) continue;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					Shape o = raw(ctx, env, i, x, z);
					if (o != null && yieldsTo(a, o)) return o;
				}
			}
		}
		return null;
	}

	private static long key(int set, int x, int z) {
		return ((long) set << 48) ^ (((long) x & 0xFFFFFFL) << 24) ^ ((long) z & 0xFFFFFFL);
	}

	/** The start set {@code i} would create in chunk x,z (vanilla's selection loop, no guard), cached; null when none. */
	static Shape raw(Ctx ctx, Env env, int i, int x, int z) {
		long k = key(i, x, z);
		Object c = ctx.raw.get(k);
		if (c != null) return c == NONE ? null : (Shape) c;
		Shape s = computeRaw(env, ctx.sets.get(i).value(), x, z);
		if (ctx.raw.size() > 200_000) ctx.raw.clear();
		ctx.raw.put(k, s == null ? NONE : s);
		return s;
	}

	private static Shape computeRaw(Env env, StructureSet set, int x, int z) {
		if (!set.placement().isStructureChunk(env.state, x, z)) return null;
		List<StructureSet.StructureSelectionEntry> list = set.structures();
		ChunkPos cp = new ChunkPos(x, z);
		if (list.size() == 1) return generate(env, list.get(0), cp);
		List<StructureSet.StructureSelectionEntry> left = new ArrayList<>(list);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(env.state.getLevelSeed(), x, z);
		int total = 0;
		for (StructureSet.StructureSelectionEntry e : left) total += e.weight();
		while (!left.isEmpty()) {
			int r = random.nextInt(total), idx = 0;
			for (StructureSet.StructureSelectionEntry e : left) {
				r -= e.weight();
				if (r < 0) break;
				idx++;
			}
			StructureSet.StructureSelectionEntry e = left.get(idx);
			Shape s = generate(env, e, cp);
			if (s != null) return s;
			left.remove(idx);
			total -= e.weight();
		}
		return null;
	}

	static Env env(net.minecraft.server.level.ServerLevel level) {
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		return new Env(level.getChunkSource().getGenerator(), state, level.registryAccess(), level.getStructureTemplateManager(), level.dimension(), level,
			state.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build()));
	}

	/**
	 * Structure search side (/locate, explorer and cartographer maps, dolphins, eyes: everything that goes through
	 * {@code StructureManager.checkStructurePresence}): whether the guard drops the start {@code structure} would have in chunk x,z. Seed math
	 * only (the same raw start and cached decision worldgen uses), no chunk loads. For an Elsewhere structure also true when its set
	 * makes nothing, or another structure, in that chunk.
	 */
	public static boolean dropsAt(net.minecraft.server.level.ServerLevel level, Structure structure, int x, int z) {
		if (!ENABLED) return false;
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		Ctx ctx = ctx(state);
		Integer set = ctx.setOf.get(structure);
		if (set == null || set < 0) return false;
		Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
		if (id == null || tier(id) == CASTLE) return false;
		if (!isElsewhere(id) && !castleNear(ctx, state, x, z)) return false; // vanilla only ever yields to a castle
		Env env = env(level);
		Shape s = raw(ctx, env, set, x, z);
		// the set makes nothing here, or another of its structures: absent for Elsewhere's own (vanilla's prediction is left as vanilla has it)
		if (s == null || !s.id.equals(id)) return isElsewhere(id);
		return !decide(ctx, env, s);
	}

	/** Whether chunk x,z had its starts made this session (so its real starts exist, saved or not). */
	public static boolean generatedThisSession(net.minecraft.server.level.ServerLevel level, int x, int z) {
		return ctx(level.getChunkSource().getGeneratorState()).generated.contains(ChunkPos.pack(x, z));
	}

	/** Whether a castle-rank start could reach chunk x,z (its start within 2 x REACH chunks): cheap placement checks only. */
	private static boolean castleNear(Ctx ctx, ChunkGeneratorStructureState state, int x, int z) {
		for (Shape o : ctx.injected) if (o.tier == CASTLE) return true;
		int r = 2 * REACH;
		for (int i = 0; i < ctx.sets.size(); i++) {
			if (ctx.minElsewhereTier[i] != CASTLE) continue;
			var placement = ctx.sets.get(i).value().placement();
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (placement.isStructureChunk(state, x + dx, z + dz)) return true;
		}
		return false;
	}

	/** The raw start the set holding {@code structure} makes in chunk x,z, or null (self-test). */
	public static Shape rawAt(net.minecraft.server.level.ServerLevel level, Structure structure, int x, int z) {
		Ctx ctx = ctx(level.getChunkSource().getGeneratorState());
		Integer set = ctx.setOf.get(structure);
		return set == null || set < 0 ? null : raw(ctx, env(level), set, x, z);
	}

	/** Self-test only: treats {@code s} as one more raw start in this dimension (null clears), and forgets cached decisions. */
	public static void injectForTest(net.minecraft.server.level.ServerLevel level, Shape s) {
		Ctx ctx = ctx(level.getChunkSource().getGeneratorState());
		if (s == null) ctx.injected.clear();
		else ctx.injected.add(s);
		ctx.decisions.clear();
	}

	/** What {@link #audit} saw: chunks read, raw starts recomputed, starts kept and dropped, and every inconsistency found. */
	public record Audit(int chunks, int raw, int kept, int dropped, List<String> problems) {}

	/**
	 * Self-test hook: for every chunk in the square, recomputes each set's raw start from scratch (no cache) and checks it against the chunk's real
	 * starts: a raw start is present exactly when no outranking start overlaps it, present starts match their raw box, and every real start is
	 * reproduced. Loads the chunks to STRUCTURE_STARTS.
	 */
	public static Audit audit(net.minecraft.server.level.ServerLevel level, ChunkPos center, int radius) {
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		Ctx ctx = ctx(state);
		Env env = new Env(gen, state, level.registryAccess(), level.getStructureTemplateManager(), level.dimension(), level,
			state.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build()));
		var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<String> problems = new ArrayList<>();
		int chunks = 0, raws = 0, kept = 0, dropped = 0;
		for (int x = center.x() - radius; x <= center.x() + radius; x++) {
			for (int z = center.z() - radius; z <= center.z() + radius; z++) {
				ChunkAccess c = level.getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
				chunks++;
				Map<Identifier, StructureStart> real = new java.util.HashMap<>();
				for (Map.Entry<Structure, StructureStart> e : c.getAllStarts().entrySet())
					if (e.getValue() != null && e.getValue().isValid()) real.put(reg.getKey(e.getKey()), e.getValue());
				for (int i = 0; i < ctx.sets.size(); i++) {
					Shape r = computeRaw(env, ctx.sets.get(i).value(), x, z);
					if (r == null) continue;
					raws++;
					StructureStart have = real.remove(r.id);
					Shape by = blocker(ctx, env, r);
					if (have == null) {
						if (by == null) problems.add(r.id + "@" + x + "," + z + " was dropped but nothing outranks and overlaps it");
						else dropped++;
					} else {
						kept++;
						if (by != null) problems.add(r.id + "@" + x + "," + z + " was kept but " + by.id + "@" + by.cx + "," + by.cz + " outranks and overlaps it");
						if (!have.getBoundingBox().equals(r.box)) problems.add(r.id + "@" + x + "," + z + " raw box " + r.box + " differs from the real " + have.getBoundingBox());
					}
				}
				for (Identifier id : real.keySet()) problems.add(id + "@" + x + "," + z + " exists but its set's raw start does not reproduce it");
			}
		}
		return new Audit(chunks, raws, kept, dropped, problems);
	}

	private static Shape generate(Env env, StructureSet.StructureSelectionEntry e, ChunkPos cp) {
		Holder<Structure> h = e.structure();
		Structure st = h.value();
		StructureStart start = st.generate(h, env.dimension, env.registries, env.generator, env.generator.getBiomeSource(), env.sampler,
			env.state.randomState(), env.templates, env.state.getLevelSeed(), cp, 0, env.height, st.biomes()::contains);
		if (start == null || !start.isValid()) return null;
		Identifier id = idOf(h);
		return id == null ? null : Shape.of(id, start);
	}
}
