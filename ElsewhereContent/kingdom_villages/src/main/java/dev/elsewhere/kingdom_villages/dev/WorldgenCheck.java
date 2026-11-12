package dev.elsewhere.kingdom_villages.dev;

import com.mojang.datafixers.util.Pair;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.Conquest;
import dev.elsewhere.kingdom_villages.Serf;
import dev.elsewhere.kingdom_villages.VillageLedger;
import dev.elsewhere.kingdom_villages.VillageManager;
import dev.elsewhere.kingdom_villages.VillageRecord;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev only (dropped at merge): the villages worldgen check. `./gradlew runWorldgen` starts a normal world (run-wg/,
 * structures on), finds real plains, desert, savanna, taiga and snowy villages, and on each one builds every faction's
 * rebuild (Aldermark, Hrafnholt, Azmar, Mirewick) through the live code path (startFortify + the writer's runOp), then
 * each faction's outpost and a hamlet beside it. Every build is judged by {@link FitAudit} (nothing floating or buried,
 * no flooding after forced fluid ticks, no valuable blocks, cut depth) and then restored, which is checked too. Logs
 * "[villages-worldgen] PASS|FAIL ..." and a TOTAL, and writes run-wg/worldgen-villages.log.
 */
public final class WorldgenCheck {
	private WorldgenCheck() {}

	record Site(String type, Holder<Structure> holder, BlockPos pos) {}

	static final List<String> LOG = new ArrayList<>();
	static final List<Site> SITES = new ArrayList<>();
	static final List<VillageRecord> pending = new ArrayList<>();
	static final Set<String> typesTested = new HashSet<>();
	static final Identifier[] FACTIONS = {Factions.ALDERMARK, RivalsTest.HRAF, RivalsTest.AZMAR, RivalsTest.MIRE};
	static final String[] NAMES = {"Aldermark", "Hrafnholt", "Azmar", "Mirewick"};
	static int tick, pass, fail, phase, wait, si = -1, fi, rebuilds, outposts, hamlets, flatMisses;
	static boolean done;
	static @Nullable ServerLevel level;
	static @Nullable StructureStart start;
	static @Nullable VillageRecord cur;
	static String label = "";
	static @Nullable LongOpenHashSet fluidBefore;
	static @Nullable Method runOp, flatSpot;
	static final List<long[]> forced = new ArrayList<>();

	static void check(String n, boolean ok, @Nullable Object d) {
		String line = (ok ? "PASS " : "FAIL ") + n + (d == null ? "" : " :: " + d);
		LOG.add(line);
		System.out.println("[villages-worldgen] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void info(String s) {
		LOG.add(s);
		System.out.println("[villages-worldgen] " + s);
	}

	public static void endTick(MinecraftServer srv) {
		if (done) return;
		tick++;
		if (tick < 40) return;
		if (wait > 0) {
			wait--;
			return;
		}
		try {
			step(srv);
		} catch (Throwable t) {
			check("worldgen check threw in phase " + phase + " (" + label + ")", false, t);
			t.printStackTrace();
			finish(srv);
		}
	}

	static void step(MinecraftServer srv) throws Exception {
		switch (phase) {
			case 0 -> setup(srv);
			case 1 -> loadNext();
			case 2 -> buildRebuild();
			case 3 -> judge(true);
			case 4 -> buildOutpost();
			case 5 -> judgePending();
			case 6 -> buildHamlet();
			case 7 -> judgePending();
			case 8 -> unload();
			default -> finish(srv);
		}
	}

	// ---- setup: rival factions (as RivalsTest defines them) and the real villages to test ---------------------------

	static void setup(MinecraftServer srv) throws Exception {
		level = srv.overworld();
		VillageLedger.get(level).conquestOn = false; // no sieges or occupation on discovery: this check drives every build itself
		VillageManager.testObservers.clear();
		RivalsTest.define(RivalsTest.HRAF, "Jarldom of Hrafnholt", "{\"type\":\"elsewhere:thrall\",\"count_multiplier\":2.0,\"health\":18.0,\"levy\":{\"fraction\":0.3333,\"weapon\":\"minecraft:stone_axe\"}}", 1.2, 0x202428);
		RivalsTest.define(RivalsTest.AZMAR, "Sultanate of Azmar", "{\"type\":\"elsewhere:porter\",\"count_multiplier\":0.5,\"health\":18.0}", 0.75, 0x2BB3A3);
		RivalsTest.define(RivalsTest.MIRE, "Lantern Republic of Mirewick", "{\"type\":\"elsewhere:peat_cutter\",\"count_multiplier\":1.0,\"health\":18.0}", 0.9, 0x4F6B3A);
		dev.elsewhere.kingdom_villages.FactionVillages.invalidate();
		runOp = VillageManager.class.getDeclaredMethod("runOp", ServerLevel.class, VillageRecord.class);
		runOp.setAccessible(true);
		try {
			flatSpot = Conquest.class.getDeclaredMethod("flatSpot", ServerLevel.class, BlockPos.class, int.class);
			flatSpot.setAccessible(true);
		} catch (NoSuchMethodException e) {
			check("Conquest.flatSpot(level, pos, radius) is the outpost and hamlet site test", false, e);
		}
		var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var gen = level.getChunkSource().getGenerator();
		List<Pair<String, ResourceKey<Structure>>> kinds = List.of(Pair.of("plains", BuiltinStructures.VILLAGE_PLAINS), Pair.of("desert", BuiltinStructures.VILLAGE_DESERT),
			Pair.of("savanna", BuiltinStructures.VILLAGE_SAVANNA), Pair.of("taiga", BuiltinStructures.VILLAGE_TAIGA), Pair.of("snowy", BuiltinStructures.VILLAGE_SNOWY));
		int per = Integer.getInteger("villages.worldgen.perType", 1);
		List<BlockPos> origins = List.of(BlockPos.ZERO, new BlockPos(3000, 0, -3000), new BlockPos(-3000, 0, 3000));
		for (var k : kinds) {
			Holder<Structure> h = reg.getOrThrow(k.getSecond());
			Set<Long> seen = new HashSet<>();
			int got = 0;
			for (BlockPos o : origins) {
				if (got >= per) break;
				Pair<BlockPos, Holder<Structure>> found = gen.findNearestMapStructure(level, HolderSet.direct(h), o, 100, false);
				if (found == null || !seen.add(found.getFirst().asLong())) continue;
				SITES.add(new Site(k.getFirst(), h, found.getFirst()));
				got++;
			}
			info("found " + got + " " + k.getFirst() + " village(s)");
		}
		info("seed " + level.getSeed() + ", " + SITES.size() + " villages to test");
		phase = 1;
	}

	// ---- one village at a time: generate it for real, then every faction's rebuild --------------------------------

	static void loadNext() {
		si++;
		if (si >= SITES.size()) {
			phase = 9;
			return;
		}
		Site s = SITES.get(si);
		int cx = s.pos().getX() >> 4, cz = s.pos().getZ() >> 4;
		for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
			level.setChunkForced(cx + dx, cz + dz, true);
			forced.add(new long[] {cx + dx, cz + dz});
		}
		for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) level.getChunk(cx + dx, cz + dz);
		start = level.getChunk(cx, cz).getStartForStructure(s.holder().value());
		if (start == null || !start.isValid()) {
			info("SKIP " + s.type() + " village at " + s.pos().toShortString() + ": no valid start in its chunk");
			phase = 8;
			return;
		}
		cur = fresh();
		if (cur == null || cur.state == VillageRecord.State.PROTECTED) {
			info("SKIP " + s.type() + " village at " + s.pos().toShortString() + ": " + (cur == null ? "not discoverable" : "protected (" + cur.reason + ")"));
			phase = 8;
			return;
		}
		typesTested.add(s.type());
		info("village " + (si + 1) + "/" + SITES.size() + ": " + s.type() + " at " + cur.center.toShortString() + ", " + cur.pieces.size() + " pieces, bounds " + span(cur.bounds));
		fi = 0;
		phase = 2;
	}

	static @Nullable VillageRecord fresh() {
		VillageLedger.get(level).villages.remove(start.getChunkPos().pack());
		return VillageManager.discover(level, start, null);
	}

	static void buildRebuild() throws Exception {
		Site s = SITES.get(si);
		if (fi > 0) cur = fresh();
		VillageRecord r = cur;
		if (r == null) {
			check(s.type() + " village: rediscovered after restore for " + NAMES[fi], false, null);
			phase = 8;
			return;
		}
		label = s.type() + " village " + r.center.toShortString() + " / " + NAMES[fi] + " rebuild";
		r.owner = FACTIONS[fi].toString();
		double ang = Math.PI / 2 * fi + 0.4;
		r.castle = r.center.offset((int) (Math.cos(ang) * 400), 0, (int) (Math.sin(ang) * 400));
		r.castleDist = 400;
		fluidBefore = FitAudit.fluids(level, region(r.bounds, 8));
		VillageManager.startFortify(level, r);
		int planned = r.plan.size(), guard = 0;
		while (r.planIndex < r.plan.size() && guard++ < 60000) runOp.invoke(null, level, r);
		info(label + ": plan " + planned + " ops, journal " + r.journal.size() + ", fluid ticks forced " + FitAudit.forceFluidTicks(level, r));
		check(label + ": the plan is not empty", planned > 0 && !r.journal.isEmpty(), "plan " + planned + ", written " + r.journal.size());
		rebuilds++;
		wait = 40;
		phase = 3;
	}

	static void judge(boolean village) {
		VillageRecord r = cur;
		audit(r, label);
		restore(r, label);
		for (Serf e : level.getEntitiesOfClass(Serf.class, AABB.of(r.bounds.inflatedBy(48)), e -> true)) e.discard();
		VillageLedger ledger = VillageLedger.get(level);
		ledger.sites.values().removeIf(x -> x.kind == VillageRecord.Kind.ROAD && x.builtAt == r.id);
		fi++;
		if (fi < FACTIONS.length) {
			phase = 2;
		} else {
			fi = 0;
			phase = 4;
		}
	}

	static void audit(VillageRecord r, String what) {
		FitAudit.Result a = FitAudit.audit(level, r, what, fluidBefore);
		check(what + ": nothing floating", a.floatingOk(), a.floating());
		check(what + ": nothing buried, no door blocked", a.buriedOk(), a.buried());
		check(what + ": no flooding", a.floodOk(), a.flood());
		check(what + ": no valuable blocks", a.valuableOk(), a.valuables == 0 ? null : a.valuables + " " + a.samples);
		check(what + ": cuts at most " + FitAudit.MAX_CUT + " deep", a.cutOk(), "natural blocks removed " + a.naturalRemoved + ", deepest column " + a.maxCut);
		if (!(a.floatingOk() && a.buriedOk() && a.floodOk())) for (String d : a.details) LOG.add("  detail " + what + ": " + d); // report file only
	}

	static void restore(VillageRecord r, String what) {
		Long2ObjectOpenHashMap<BlockState> orig = new Long2ObjectOpenHashMap<>();
		for (var e : r.journal.long2ObjectEntrySet()) orig.put(e.getLongKey(), e.getValue().original());
		int[] rr = BlockWriter.restore(level, r);
		int bad = 0;
		String first = null;
		for (var e : orig.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			BlockState now = level.getBlockState(p);
			if (now.getBlock() != e.getValue().getBlock()) {
				bad++;
				if (first == null) first = p.toShortString() + " " + now + " (was " + e.getValue() + ")";
			}
		}
		check(what + ": restore puts every block back", bad == 0 && rr[1] == 0, "restored " + rr[0] + ", skipped " + rr[1] + ", wrong " + bad + (first == null ? "" : ", e.g. " + first));
	}

	// ---- each faction's outpost (and whatever the founding builds with it), then a hamlet ---------------------------

	/** The site the live code would pick near a candidate (Conquest.tryFoundOutpost / tryHamlet): 0 = outpost, 1 = hamlet. */
	static @Nullable BlockPos siteAt(BlockPos p, int kind, boolean aldermark, BlockPos castle) throws Exception {
		if (kind == 0 && aldermark && dev.elsewhere.kingdom_villages.s3.ald.AldLook.FORT) return dev.elsewhere.kingdom_villages.s3.ald.AldFort.site(level, p, castle);
		if (kind == 1 && dev.elsewhere.kingdom_villages.s3.ald.AldLook.HAMLET) return dev.elsewhere.kingdom_villages.s3.ald.AldHamlet.site(level, p);
		if (flatSpot == null) return null;
		return flatSpot.invoke(null, level, p, kind == 0 ? 6 : 4) instanceof BlockPos bp ? bp : null;
	}

	static @Nullable BlockPos flat(BlockPos around, int kind, int startDir, boolean aldermark, BlockPos castle) throws Exception {
		VillageRecord v = cur;
		int base = Math.max(v.bounds.getXSpan(), v.bounds.getZSpan()) / 2 + 20;
		for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 16; i++) {
			double ang = Math.PI * 2 * ((i + startDir) % 16) / 16.0;
			int d = base + ring * 14;
			BlockPos p = around.offset((int) (Math.cos(ang) * d), 0, (int) (Math.sin(ang) * d));
			if (!level.isLoaded(p) || !level.isLoaded(p.offset(32, 0, 32)) || !level.isLoaded(p.offset(-32, 0, -32))) continue;
			BlockPos c = siteAt(p, kind, aldermark, castle);
			if (c != null) return c;
		}
		return null;
	}

	static void buildOutpost() throws Exception {
		VillageRecord v = cur;
		double ang = Math.PI / 2 * fi + 0.4;
		BlockPos castle = v.center.offset((int) (Math.cos(ang) * 500), 0, (int) (Math.sin(ang) * 500));
		BlockPos c = flat(v.center, 0, fi * 4, fi == 0, castle);
		label = SITES.get(si).type() + " village " + v.center.toShortString() + " / " + NAMES[fi] + " outpost";
		if (c == null) {
			info(label + ": no outpost site within reach (the live site rule found none); nothing to judge");
			flatMisses++;
			next4();
			return;
		}
		VillageLedger ledger = VillageLedger.get(level);
		Set<Long> before = new HashSet<>(ledger.sites.keySet());
		fluidBefore = FitAudit.fluids(level, new BoundingBox(c.getX() - 64, c.getY() - 20, c.getZ() - 64, c.getX() + 64, c.getY() + 20, c.getZ() + 64));
		Conquest.foundOutpost(level, ledger, c, castle, FACTIONS[fi], RandomSource.create(1000L * si + fi), level.getGameTime());
		pending.clear();
		for (VillageRecord s : ledger.sites.values()) {
			if (before.contains(s.id)) continue;
			if (s.kind == VillageRecord.Kind.ROAD) continue;
			pending.add(s);
			FitAudit.forceFluidTicks(level, s);
		}
		ledger.sites.values().removeIf(s -> !before.contains(s.id) && s.kind == VillageRecord.Kind.ROAD);
		info(label + " at " + c.toShortString() + ": " + pending.size() + " site(s) built");
		outposts++;
		wait = 40;
		phase = 5;
	}

	static void next4() {
		fi++;
		if (fi >= FACTIONS.length) {
			fi = 0;
			phase = 6;
		} else phase = 4;
	}

	static void judgePending() {
		for (VillageRecord s : pending) {
			String what = phase == 5 ? label + (s.kind == VillageRecord.Kind.OUTPOST ? "" : " + its " + s.kind.name().toLowerCase()) : label;
			audit(s, what);
			restore(s, what);
			VillageLedger.get(level).sites.remove(s.id);
		}
		pending.clear();
		if (phase == 5) next4();
		else phase = 8;
	}

	static void buildHamlet() throws Exception {
		VillageRecord v = cur;
		BlockPos c = flat(v.center, 1, 8, false, v.center.offset(500, 0, 0));
		label = SITES.get(si).type() + " village " + v.center.toShortString() + " / hamlet";
		if (c == null) {
			info(label + ": no hamlet site within reach (the live site rule found none); nothing to judge");
			flatMisses++;
			phase = 8;
			return;
		}
		VillageLedger ledger = VillageLedger.get(level);
		fluidBefore = FitAudit.fluids(level, new BoundingBox(c.getX() - 16, c.getY() - 16, c.getZ() - 16, c.getX() + 16, c.getY() + 16, c.getZ() + 16));
		VillageRecord h = Conquest.buildHamlet(level, ledger, c, c.offset(400, 0, 0), RandomSource.create(77L + si));
		pending.clear();
		pending.add(h);
		FitAudit.forceFluidTicks(level, h);
		hamlets++;
		wait = 40;
		phase = 7;
	}

	static void unload() {
		for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
		forced.clear();
		if (start != null) VillageLedger.get(level).villages.remove(start.getChunkPos().pack());
		phase = 1;
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	static BoundingBox region(BoundingBox b, int grow) {
		return new BoundingBox(b.minX() - grow, b.minY() - grow, b.minZ() - grow, b.maxX() + grow, b.maxY() + BlockWriter.UP + grow, b.maxZ() + grow);
	}

	static String span(BoundingBox b) {
		return b.getXSpan() + "x" + b.getYSpan() + "x" + b.getZSpan();
	}

	static void finish(MinecraftServer srv) {
		if (done) return;
		check("real villages tested: at least 4 villages of at least 3 kinds (" + typesTested + ")", typesTested.size() >= 3 && rebuilds >= 4 * FACTIONS.length, rebuilds + " rebuilds");
		check("outposts and hamlets were judged on real terrain", outposts >= 4 && hamlets >= 1, outposts + " outposts, " + hamlets + " hamlets, " + flatMisses + " sites had no flat spot");
		String total = "[villages-worldgen] TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL");
		LOG.add(total);
		System.out.println(total);
		done = true;
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("worldgen-villages.log")))) {
			LOG.forEach(w::println);
		} catch (Exception e) {
			System.out.println("[villages-worldgen] could not write log: " + e);
		}
		srv.halt(false);
	}
}
