package dev.elsewhere.kingdom_court.dev;

import com.mojang.datafixers.util.Pair;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtRealms;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import dev.elsewhere.kingdom_court.world.RealmLocate;
import dev.elsewhere.kingdom_court.world.Waterfront;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 s-locate: /locate, explorer maps and every other structure search find each realm structure (the four castles and Hrafnholt's waterfront) at its
 * saved site, exactly, instantly and from any distance ({@link RealmLocate}, hooked by {@code mixin/LocateCastleMixin}); everything else keeps the
 * vanilla search. Two parts:
 * <ul>
 * <li>{@link #add}: a court self-test step. The self-test world is flat with structures off, where no realm generates, so after checking that the hook
 * answers nothing there (like vanilla), it turns on {@link RealmLocate#testIgnoreGenerator} to check the answers themselves.</li>
 * <li>{@link #worldgen}: runWorldgen (fresh normal world, structures on), no test switch: the production path, and the located chunk really holds the
 * structure start (the waterfront's piece covers the located jetty).</li>
 * </ul>
 * Court states and CourtWorld sites changed for the checks are restored in the same tick (no court tick sees them).
 */
public final class SelfTestLocate {
	private SelfTestLocate() {}

	static final Identifier ALD = Factions.ALDERMARK, AZ = CourtContent.id("azmar"), HR = CourtContent.id("hrafnholt"), MR = CourtContent.id("mirewick");
	static final List<String> REALMS = List.of("castle_aldermark", "castle_azmar", "castle_hrafnholt", "castle_mirewick", "waterfront_hrafnholt");
	/** The release suite's 4 origins, plus two far beyond any vanilla search radius. */
	static final BlockPos[] ORIGINS = {BlockPos.ZERO, new BlockPos(3000, 64, 3000), new BlockPos(-3000, 64, 3000), new BlockPos(3000, 64, -3000), new BlockPos(-20000, 64, -20000),
		new BlockPos(30000, 64, 30000)};

	/** True while the runWorldgen part reports (WorldgenCheck), else the court self-test. */
	static boolean wg;

	static void check(String name, boolean ok, Object detail) {
		if (wg) WorldgenCheck.check(name, ok, detail);
		else SelfTest.check(name, ok, detail);
	}

	static void info(String line) {
		if (wg) WorldgenCheck.info(line);
		else SelfTest.info(line);
	}

	/** Court self-test hook: one step. */
	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			wg = false;
			selfTest(srv, l);
			return 1;
		});
	}

	/** runWorldgen hook (WorldgenCheck.endTick, after the castle report). */
	static void worldgen(MinecraftServer srv, ServerLevel l) {
		wg = true;
		try {
			realWorld(srv, l);
		} catch (Throwable t) {
			check("locate (real worldgen): the check runs without an exception", false, t);
			t.printStackTrace();
		}
	}

	// ---- the court self-test (flat world, structures off) ---------------------------------------------------------------------------

	static void selfTest(MinecraftServer srv, ServerLevel l) {
		long seed = l.getSeed();
		ChunkGenerator gen = l.getChunkSource().getGenerator();
		int sea = gen.getSeaLevel();
		Map<String, Holder.Reference<Structure>> by = realms(l);
		check("locate: the realm structures are registered (" + String.join(", ", REALMS) + ")", by.keySet().containsAll(REALMS), by.keySet());
		if (!by.keySet().containsAll(REALMS)) return;
		int placed = 0;
		StringBuilder pb = new StringBuilder();
		for (var e : by.entrySet()) {
			Identifier rf = RealmLocate.registryFaction(l, e.getValue());
			if (rf != null && rf.equals(RealmLocate.factionOf(e.getValue()))) placed++;
			else pb.append(' ').append(e.getKey()).append('=').append(rf);
		}
		check("locate: each realm structure has a structure set whose court placement is its own faction's saved site", placed == by.size(), placed + "/" + by.size() + pb);
		var ald = by.get("castle_aldermark");
		var az = by.get("castle_azmar");
		var hr = by.get("castle_hrafnholt");
		var mr = by.get("castle_mirewick");
		var wf = by.get("waterfront_hrafnholt");
		List<Saved> saved = new ArrayList<>();
		for (Identifier f : List.of(ALD, AZ, HR, MR)) saved.add(new Saved(srv, seed, f));
		boolean wasEnabled = RealmLocate.enabled;
		try {
			RealmLocate.enabled = true;
			// test sites out to about 12,500 blocks (the rival search reaches 12,000); Hrafnholt's realm by the water, shore 96 blocks south
			BlockPos sAld = new BlockPos(1720, 72, -2210), sAz = new BlockPos(11880, 96, -4040), sHr = new BlockPos(-8230, 70, 9150), sMr = new BlockPos(4410, 66, 10630);
			put(srv, seed, ALD, sAld, Double.NaN, 0);
			put(srv, seed, AZ, sAz, Double.NaN, 0);
			put(srv, seed, HR, sHr, Math.PI / 2, 96);
			put(srv, seed, MR, sMr, Double.NaN, 0);
			BlockPos sWf = RealmLocate.shore(sHr, 1, 96, sea);
			Map<String, BlockPos> exp = Map.of("castle_aldermark", sAld, "castle_azmar", sAz, "castle_hrafnholt", sHr, "castle_mirewick", sMr, "waterfront_hrafnholt", sWf);
			info("locate: test sites " + exp + ", sea level " + sea);

			// 1. production gate: in this world (flat, structures off) no realm generates, so the hook must answer what vanilla would: nothing
			boolean on = srv.getWorldGenSettings().options().generateStructures();
			int gated = 0;
			StringBuilder gb = new StringBuilder();
			for (String id : REALMS) {
				var h = by.get(id);
				boolean inGen = l.getChunkSource().getGeneratorState().getPlacementsForStructure(h).stream().anyMatch(p -> p instanceof CourtWorld.Placement);
				BlockPos want = on && inGen ? exp.get(id) : null;
				var got = gen.findNearestMapStructure(l, HolderSet.direct(h), BlockPos.ZERO, 100, false);
				if (Objects.equals(got == null ? null : got.getFirst(), want)) gated++;
				else gb.append(' ').append(id).append('=').append(got == null ? "not found" : got.getFirst().toShortString());
			}
			check("locate: the hook answers only where the realm generates, like vanilla (this world: structures " + (on ? "on" : "off") + ", " + gen.getClass().getSimpleName() + ")", gated == REALMS.size(), gated + "/" + REALMS.size() + gb);

			// 2. the answers: every realm structure from every origin, any radius, both entry points
			RealmLocate.testIgnoreGenerator = true;
			long[] ns = new long[3];
			for (String id : REALMS) {
				BlockPos e = exp.get(id);
				String bad = fromOrigins(l, by.get(id), e, ns);
				check("locate: /locate finds " + id + " at its " + (RealmLocate.isWaterfront(by.get(id)) ? "waterfront (the jetty at the shore) " : "saved site ") + e.toShortString() + " from all " + ORIGINS.length + " origins (up to "
					+ farthest(e) + " blocks away), with search radius 1 too, and through ServerLevel with skip-known on (explorer maps, other mods)", bad == null, bad);
			}
			int far = 0;
			for (BlockPos e : exp.values()) far = Math.max(far, farthest(e));
			check("locate: instant (" + ns[2] + " searches up to " + far + " blocks away: max " + ns[1] / 1000 + " us, mean " + ns[0] / Math.max(1, ns[2]) / 1000 + " us; no chunk scan)", ns[0] < 250_000_000L, ns[0] / 1_000_000 + " ms in all");

			// 3. explorer maps: the vanilla exploration_map loot function (skip_existing_chunks on, as in loot tables and cartographer trades)
			int maps = 0;
			StringBuilder mb = new StringBuilder();
			for (String[] t : new String[][] {{"aldermark_castle", "castle_aldermark"}, {"azmar_castle", "castle_azmar"}, {"hrafnholt_castle", "castle_hrafnholt"}, {"mirewick_castle", "castle_mirewick"}}) {
				BlockPos at = explorerMap(l, t[0], BlockPos.ZERO);
				BlockPos e = exp.get(t[1]);
				if (at != null && at.getX() == e.getX() && at.getZ() == e.getZ()) maps++;
				else mb.append(" #").append(t[0]).append("=").append(at);
			}
			check("locate: explorer maps for #elsewhere:aldermark_castle, azmar_castle, hrafnholt_castle and mirewick_castle mark each castle's saved site", maps == 4, maps + "/4" + mb);

			// 4. sets of several structures (tags): the nearest realm; a mixed set also weighs vanilla's own result; vanilla-only sets are not touched
			var n1 = gen.findNearestMapStructure(l, HolderSet.direct(az, mr), sMr.offset(40, 0, -40), 100, false);
			var n2 = gen.findNearestMapStructure(l, HolderSet.direct(az, mr), sAz.offset(-40, 0, 40), 100, false);
			check("locate: a set of several realms answers the nearest one", n1 != null && n1.getSecond().is(mr.key()) && n1.getFirst().equals(sMr) && n2 != null && n2.getSecond().is(az.key()) && n2.getFirst().equals(sAz), n1 + " / " + n2);
			var village = l.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("village_plains"))).orElse(null);
			if (village != null) {
				int before = RealmLocate.answered;
				var vOnly = gen.findNearestMapStructure(l, HolderSet.direct(village), BlockPos.ZERO, 100, false);
				boolean untouched = RealmLocate.answered == before && !RealmLocate.handles(l, HolderSet.direct(village));
				var mixed = gen.findNearestMapStructure(l, HolderSet.direct(az, village), BlockPos.ZERO, 100, false);
				BlockPos want = vOnly != null && dist2(vOnly.getFirst(), BlockPos.ZERO) < dist2(sAz, BlockPos.ZERO) ? vOnly.getFirst() : sAz;
				check("locate: other structures keep the vanilla search (a village search is not intercepted); a mixed set answers the nearer of the realm site and vanilla's result",
					untouched && mixed != null && mixed.getFirst().equals(want), "village " + (vOnly == null ? "not found" : vOnly.getFirst().toShortString()) + ", mixed " + mixed + ", untouched " + untouched);
			}

			// 5. overworld only
			ServerLevel nether = srv.getLevel(Level.NETHER);
			if (nether != null) {
				var nh = nether.getChunkSource().getGenerator().findNearestMapStructure(nether, HolderSet.direct(az), BlockPos.ZERO, 100, false);
				check("locate: realms are overworld-only (a search in the Nether finds none)", nh == null, nh);
			}

			// 6. the waterfront: north-facing, then a Hrafnholt realm that is not by the water (no phantom waterfront; the castle is still found)
			put(srv, seed, HR, sHr, -Math.PI / 2, 120);
			BlockPos north = RealmLocate.shore(sHr, 3, 120, sea);
			var wn = gen.findNearestMapStructure(l, HolderSet.direct(wf), BlockPos.ZERO, 100, false);
			boolean northOk = wn != null && wn.getFirst().equals(north) && north.getX() == sHr.getX() && north.getZ() == sHr.getZ() - 120 && north.getY() == sea;
			put(srv, seed, HR, sHr, Double.NaN, 0);
			int none = 0;
			for (BlockPos o : ORIGINS) if (gen.findNearestMapStructure(l, HolderSet.direct(wf), o, 100, false) == null) none++;
			var castle = gen.findNearestMapStructure(l, HolderSet.direct(hr), BlockPos.ZERO, 100, false);
			check("locate: a north-facing waterfront is found at its jetty; when Hrafnholt's realm is not by the water there is no waterfront (not found from any origin) and the castle is still found",
				northOk && none == ORIGINS.length && castle != null && castle.getFirst().equals(sHr), "north " + wn + " want " + north + ", dry: " + none + "/" + ORIGINS.length + " not found, castle " + castle);
			String why = RealmLocate.absentReason(srv, wf.key().identifier());
			check("locate: absentReason (for suites that locate every structure) says why this world has no waterfront, and is null for a castle it has",
				why != null && why.contains("water") && RealmLocate.absentReason(srv, hr.key().identifier()) == null && RealmLocate.absentReason(srv, Identifier.withDefaultNamespace("village_plains")) == null, why);

			// 7. no saved site (an existing world where every chunk was already generated): not found
			put(srv, seed, MR, null, Double.NaN, 0);
			var noSite = gen.findNearestMapStructure(l, HolderSet.direct(mr), BlockPos.ZERO, 100, false);
			check("locate: a realm without a saved site is not found", noSite == null, noSite);
			put(srv, seed, MR, sMr, Double.NaN, 0);
			put(srv, seed, HR, sHr, Math.PI / 2, 96);

			// 8. the answer is where generation puts the start: each castle's chunk is the court placement's one structure chunk, the waterfront point is in its piece box
			int agree = 0;
			StringBuilder ab = new StringBuilder();
			for (String id : List.of("castle_aldermark", "castle_azmar", "castle_hrafnholt", "castle_mirewick")) {
				BlockPos s = exp.get(id);
				Identifier f = RealmLocate.factionOf(by.get(id));
				if (new CourtWorld.Placement(f).isStructureChunk(l.getChunkSource().getGeneratorState(), s.getX() >> 4, s.getZ() >> 4)) agree++;
				else ab.append(' ').append(id);
			}
			boolean inBox = Waterfront.box(sHr, 1, 96, sea).isInside(sWf);
			check("locate: each located castle is in the chunk its court placement starts it in, and the located waterfront lies inside the waterfront piece", agree == 4 && inBox, agree + "/4" + ab + ", waterfront in box " + inBox);

			// 9. kill switch realm_locate off: the 2.x answer (Aldermark's castle only)
			RealmLocate.enabled = false;
			var offAz = gen.findNearestMapStructure(l, HolderSet.direct(az), BlockPos.ZERO, 100, false);
			var offAld = gen.findNearestMapStructure(l, HolderSet.direct(ald), BlockPos.ZERO, 100, false);
			RealmLocate.enabled = true;
			check("locate: kill switch realm_locate off gives the 2.x answer (Aldermark's castle only, rival realms not found)", offAz == null && offAld != null && offAld.getFirst().equals(sAld), offAz + " / " + offAld);
		} finally {
			RealmLocate.testIgnoreGenerator = false;
			RealmLocate.enabled = wasEnabled;
			for (Saved s : saved) s.restore(srv, seed);
		}
	}

	// ---- runWorldgen (fresh normal world, structures on): the production path ----------------------------------------------------------

	static void realWorld(MinecraftServer srv, ServerLevel l) {
		long seed = l.getSeed();
		ChunkGenerator gen = l.getChunkSource().getGenerator();
		int sea = gen.getSeaLevel();
		Map<String, Holder.Reference<Structure>> by = realms(l);
		check("locate (real worldgen): the realm structures are registered (" + String.join(", ", REALMS) + ")", by.keySet().containsAll(REALMS), by.keySet());
		if (!by.keySet().containsAll(REALMS)) return;
		List<Saved> saved = new ArrayList<>();
		for (Identifier f : List.of(ALD, AZ, HR, MR)) saved.add(new Saved(srv, seed, f));
		boolean wasEnabled = RealmLocate.enabled;
		try {
			RealmLocate.enabled = true;
			RealmLocate.testIgnoreGenerator = false;
			// a rival without a court in this run (the court module alone defines only Aldermark) gets a far test site, saved and handed to generation like syncRealms
			StringBuilder real = new StringBuilder();
			if (hasSite(srv, AZ)) real.append(" azmar");
			else put(srv, seed, AZ, new BlockPos(9640, 80, -5110), Double.NaN, 0);
			if (hasSite(srv, MR)) real.append(" mirewick");
			else put(srv, seed, MR, new BlockPos(5530, 66, 9870), Double.NaN, 0);
			if (hasSite(srv, HR)) {
				real.append(" hrafnholt");
				CourtState h = CourtState.peek(srv, HR);
				if (RealmLocate.water(h) == null) {
					info("locate (real worldgen): this world's Hrafnholt is not by the water; a test waterfront 88 blocks south is set for the waterfront check");
					put(srv, seed, HR, h.site, Math.PI / 2, 88);
				}
			} else put(srv, seed, HR, new BlockPos(-7020, 70, 8480), Math.PI / 2, 88);
			info("locate (real worldgen): rival courts of this run:" + (real.isEmpty() ? " none (court module alone), far test sites used" : real));

			long[] ns = new long[3];
			for (String id : REALMS) {
				var h = by.get(id);
				Identifier f = RealmLocate.factionOf(h);
				boolean isWf = RealmLocate.isWaterfront(h);
				CourtState st = CourtState.peek(srv, f);
				BlockPos want = RealmLocate.site(srv, f, isWf, sea);
				if (st == null || st.site == null || want == null) {
					check("locate (real worldgen): " + id + " has a saved site to find", false, f);
					continue;
				}
				String bad = fromOrigins(l, h, want, ns);
				// the start is really there: bring the site chunk to structure starts (what vanilla's own locate does) and read it
				ChunkAccess c = l.getChunk(st.site.getX() >> 4, st.site.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);
				StructureStart start = c.getStartForStructure(h.value());
				boolean there = start != null && start.isValid() && (!isWf || start.getPieces().stream().anyMatch(p -> p.getBoundingBox().isInside(want)));
				check("locate (real worldgen, no test switch): /locate finds " + id + " at " + want.toShortString() + " from all " + ORIGINS.length + " origins (up to " + farthest(want) + " blocks away), and that chunk holds its structure start"
					+ (isWf ? " with the waterfront piece over the located jetty" : ""), bad == null && there, (bad == null ? "" : bad + "; ") + "start " + (start == null ? "none" : start.isValid() ? start.getPieces().size() + " pieces" : "invalid"));
			}
			check("locate (real worldgen): instant (" + ns[2] + " searches: max " + ns[1] / 1000 + " us, mean " + ns[0] / Math.max(1, ns[2]) / 1000 + " us)", ns[0] < 250_000_000L, ns[0] / 1_000_000 + " ms in all");
			BlockPos az = RealmLocate.site(srv, AZ, false, sea), map = explorerMap(l, "azmar_castle", BlockPos.ZERO);
			check("locate (real worldgen): an explorer map for #elsewhere:azmar_castle marks Azmar's castle", az != null && map != null && map.getX() == az.getX() && map.getZ() == az.getZ(), map + " want " + az);
			var village = l.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("village_plains"))).orElse(null);
			if (village != null) {
				int before = RealmLocate.answered;
				var v = gen.findNearestMapStructure(l, HolderSet.direct(village), BlockPos.ZERO, 100, false);
				check("locate (real worldgen): a vanilla village search is left to vanilla", RealmLocate.answered == before, v == null ? "not found" : v.getFirst().toShortString());
			}
		} finally {
			RealmLocate.enabled = wasEnabled;
			for (Saved s : saved) s.restore(srv, seed);
		}
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------------------------

	/** Every realm structure in the registry, by path. */
	static Map<String, Holder.Reference<Structure>> realms(ServerLevel l) {
		List<Holder.Reference<Structure>> all = new ArrayList<>(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().filter(h -> RealmLocate.factionOf(h) != null).toList());
		all.sort((a, b) -> a.key().identifier().toString().compareTo(b.key().identifier().toString()));
		Map<String, Holder.Reference<Structure>> m = new LinkedHashMap<>();
		for (Holder.Reference<Structure> h : all) m.put(h.key().identifier().getPath(), h);
		return m;
	}

	/**
	 * Searches for {@code h} like /locate (generator, radius 100) from every origin, once with radius 1 from the farthest origin and once through
	 * ServerLevel with skip-known on (the explorer-map call); null when every answer is exactly {@code want}, else what went wrong. {@code ns}: total, max, count.
	 */
	static @Nullable String fromOrigins(ServerLevel l, Holder.Reference<Structure> h, BlockPos want, long[] ns) {
		ChunkGenerator gen = l.getChunkSource().getGenerator();
		StringBuilder bad = new StringBuilder();
		for (BlockPos o : ORIGINS) {
			long t0 = System.nanoTime();
			Pair<BlockPos, Holder<Structure>> got = gen.findNearestMapStructure(l, HolderSet.direct(h), o, 100, false);
			long dt = System.nanoTime() - t0;
			ns[0] += dt;
			ns[1] = Math.max(ns[1], dt);
			ns[2]++;
			if (got == null || !got.getFirst().equals(want) || !got.getSecond().is(h.key())) bad.append(' ').append(o.toShortString()).append(" -> ").append(got == null ? "not found" : got.getFirst().toShortString());
		}
		var r1 = gen.findNearestMapStructure(l, HolderSet.direct(h), ORIGINS[ORIGINS.length - 1], 1, false);
		if (r1 == null || !r1.getFirst().equals(want)) bad.append(" radius 1 -> ").append(r1 == null ? "not found" : r1.getFirst().toShortString());
		BlockPos viaLevel = l.findNearestMapStructure(HolderSet.direct(h), ORIGINS[1], 50, true);
		if (!want.equals(viaLevel)) bad.append(" ServerLevel -> ").append(viaLevel);
		return bad.isEmpty() ? null : bad.toString().trim();
	}

	/** Runs the vanilla exploration_map loot function for tag {@code #elsewhere:<tag>} (skip_existing_chunks on, search radius 50) and returns the marked position, or null. */
	static @Nullable BlockPos explorerMap(ServerLevel l, String tag, BlockPos from) {
		Optional<HolderSet.Named<Structure>> dest = l.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(TagKey.create(Registries.STRUCTURE, CourtContent.id(tag)));
		if (dest.isEmpty()) return null;
		LootParams params = new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(from)).create(LootContextParamSets.CHEST);
		LootContext ctx = new LootContext.Builder(params).create(Optional.empty());
		ItemStack map = ExplorationMapFunction.makeExplorationMap(dest.get()).setSearchRadius(50).setSkipKnownStructures(true).build().apply(new ItemStack(Items.MAP), ctx);
		MapDecorations d = map.get(DataComponents.MAP_DECORATIONS);
		if (d == null || d.decorations().isEmpty() || !map.has(DataComponents.MAP_ID)) return null;
		MapDecorations.Entry e = d.decorations().values().iterator().next();
		return BlockPos.containing(e.x(), 0, e.z());
	}

	static boolean hasSite(MinecraftServer srv, Identifier f) {
		CourtState st = CourtState.peek(srv, f);
		return st != null && st.site != null;
	}

	/** Sets {@code f}'s saved court site and water, then hands them to generation exactly as CourtLife.syncRealms does. */
	static void put(MinecraftServer srv, long seed, Identifier f, @Nullable BlockPos site, double bearing, int dist) {
		CourtState st = CourtState.get(srv, f);
		st.site = site;
		st.siteChosen = true;
		st.waterBearing = bearing;
		st.waterDist = dist;
		CourtWorld.setSite(seed, f, st.site);
		CourtWorld.setWater(seed, f, st.site != null && st.waterDist > 0 && !Double.isNaN(st.waterBearing) ? new int[] {st.waterBearing > 0 ? 1 : 3, st.waterDist} : null);
	}

	static double dist2(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	static int farthest(BlockPos p) {
		double m = 0;
		for (BlockPos o : ORIGINS) m = Math.max(m, dist2(o, p));
		return (int) Math.sqrt(m);
	}

	/** One court's saved site fields and its CourtWorld (generation) entries before the check; restored after it. */
	static final class Saved {
		final Identifier f;
		final boolean existed, chosen;
		final BlockPos site, genSite;
		final double bearing;
		final int dist;
		final int[] genWater;

		Saved(MinecraftServer srv, long seed, Identifier f) {
			this.f = f;
			CourtState st = CourtState.peek(srv, f);
			existed = st != null;
			site = st == null ? null : st.site;
			chosen = st != null && st.siteChosen;
			bearing = st == null ? Double.NaN : st.waterBearing;
			dist = st == null ? 0 : st.waterDist;
			genSite = CourtWorld.site(seed, f);
			genWater = CourtWorld.water(seed, f);
		}

		void restore(MinecraftServer srv, long seed) {
			CourtState st = CourtState.peek(srv, f);
			if (st != null) {
				st.site = site;
				st.siteChosen = chosen;
				st.waterBearing = bearing;
				st.waterDist = dist;
			}
			if (!existed) dropCourt(srv, f);
			CourtWorld.setSite(seed, f, genSite);
			CourtWorld.setWater(seed, f, genWater);
		}
	}

	/** Removes a court the check created for a faction this run has no realm for (CourtRealms keeps no remove method; dev only, by reflection). */
	static void dropCourt(MinecraftServer srv, Identifier f) {
		try {
			var field = CourtRealms.class.getDeclaredField("states");
			field.setAccessible(true);
			((Map<?, ?>) field.get(CourtRealms.get(srv))).remove(f);
		} catch (ReflectiveOperationException | RuntimeException e) {
			info("locate: the temporary court of " + f + " stays (" + e + "); it has no site, so nothing uses it");
		}
	}
}
