package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.Overhaul3;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import dev.elsewhere.kingdom_court.world.Valuables;
import dev.elsewhere.kingdom_court.world.Waterfront;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * s-throne (final review B9, 3.0.0): no valuable block in any realm structure, under ANY switch setting. With {@code castle_kit=false} the
 * frozen fv 1 path used to bring back Aldermark's gold throne. Now every realm write passes {@link Valuables#safe} (CastleLayout.Placer and the
 * waterfront), and this test proves it on the built blocks:
 * <ol>
 * <li>the shipped palettes ({@code kingdom_castle/*.json}: palette targets, piece blocks and caps, waterfront blocks) name no valuable block;</li>
 * <li>the filter knows every valuable block and maps each to a lore-fit stand-in that is not valuable (gold: yellow terracotta, lapis: blue
 * glazed terracotta), and leaves ordinary blocks alone;</li>
 * <li>every castle style the server knows (Aldermark, the shipped rival palettes in the merged jar) plus hostile test palettes that map
 * ordinary blocks to gold, diamond, emerald, iron, netherite, lapis, beacons and ores (one per kit, Azmar's through its own fv 2 layout), is
 * built whole (castle, dungeon, towns, road, walls, kit) on the frozen fv 1 path and the fv 2 terrain-fit path, under every combination of
 * the court switches ({@code castle_kit}, {@code town_kit}, and the non-final layout switches {@code mire_layout}/{@code azmar_layout} for
 * their own kingdom), and every block of every part box is scanned: zero valuable blocks;</li>
 * <li>the waterfront (jetty, boathouse, longships) of every style with one, including a hostile one, has none;</li>
 * <li>in the merged jar (where the other modules are on the classpath; found by reflection, skipped in this module's dev run) every
 * structure blueprint of the other modules is built and scanned too: the nether boss arenas (legacy halls), Verdigris/Ironpeak legacy
 * and 3.0 fit blueprints, Greenhaze/Hush blueprints and the overworld boss blueprints.</li>
 * </ol>
 * All of it runs far from the court realm, one build per step, and every switch is restored afterwards.
 */
final class SelfTestValuables {
	private SelfTestValuables() {}

	static final BlockPos AT = new BlockPos(-7000, 0, -7000);
	/** Hostile palette: ordinary castle blocks mapped to valuables, valuable pieces and caps, a valuable waterfront. */
	static final String HOSTILE = """
		{ "palette": { "minecraft:stone_bricks": "minecraft:diamond_block", "minecraft:cobblestone": "minecraft:emerald_block",
		               "minecraft:spruce_planks": "minecraft:iron_block", "minecraft:oak_planks": "minecraft:netherite_block",
		               "minecraft:polished_blackstone_bricks": "minecraft:gilded_blackstone", "minecraft:blue_wool": "minecraft:lapis_block",
		               "minecraft:polished_deepslate": "minecraft:raw_gold_block", "minecraft:lantern": "minecraft:beacon",
		               "minecraft:dark_oak_planks": "minecraft:copper_block", "minecraft:andesite": "minecraft:diamond_ore",
		               "minecraft:stone": "minecraft:gold_block", "minecraft:deepslate_tiles": "minecraft:redstone_block" },
		  "pieces": [ {"type": "obelisk", "at": [20, 0, 20], "height": 8, "base": 3, "block": "minecraft:gold_block", "cap": "minecraft:beacon"},
		              {"type": "box", "from": [-22, 0, 20], "to": [-20, 2, 22], "block": "minecraft:emerald_block"},
		              {"type": "column", "at": [0, 0, 26], "height": 4, "block": "minecraft:iron_block", "cap": "minecraft:conduit"},
		              {"type": "set", "at": [0, 12, 0], "block": "minecraft:lodestone"} ],
		  "waterfront": { "jetty": {"planks": "minecraft:gold_block", "post": "minecraft:iron_block", "light": "minecraft:beacon"},
		                  "boathouse": {"wall": "minecraft:emerald_block"},
		                  "longship": {"hull": "minecraft:diamond_block", "keel": "minecraft:netherite_block", "sail": ["minecraft:lapis_block", "minecraft:gold_block"]} } }""";
	/** Test styles: one per kit (the id picks the kit; the {@code sla_} prefix also routes through Azmar's own fv 2 layout). */
	static final String[] HOSTILE_IDS = {"sv_hostile", "sv_hostile_hrafnholt", "sla_sv_hostile_azmar", "sv_hostile_mirewick"};
	/** Non-final layout switches, swept for their own kingdom when present (looked up by name so a layout lane can rename or finalise them). */
	static final String[][] LAYOUT_SWITCHES = {
		{"mire_layout", "dev.elsewhere.kingdom_court.world.MireLayout", "ENABLED", "mirewick"},
		{"azmar_layout", "dev.elsewhere.kingdom_court.world.AzmarCastle", "ENABLED", "azmar"}};

	record Job(@Nullable String style, int fv, boolean castleKit, boolean townKit, @Nullable Field layout, boolean layoutOn) {}

	static boolean castleKit0, townKit0;
	static final List<Field> SWEPT = new ArrayList<>();
	static final List<Boolean> SWEPT0 = new ArrayList<>();
	static long blocksScanned;
	static int jobs, valuableTotal, throneOk = -1, hostileStandIns, waterfronts, waterfrontValuable;
	static final List<String> BAD = new ArrayList<>();
	static final List<String> OTHER_BAD = new ArrayList<>();
	static int otherBuilt, otherValuable, otherCrashed;
	static final Set<String> STYLES_SEEN = new TreeSet<>();

	static Identifier id(String p) {
		return CourtContent.id(p);
	}

	static BlockPos origin() {
		return new BlockPos(AT.getX(), SelfTest.y, AT.getZ());
	}

	static void add() {
		// 1: data + filter checks, hostile styles, chunks, then the job list is inserted right after this step
		STEPS.add((srv, l) -> {
			List<String> f = Valuables.paletteFindings(srv);
			check("valuables: no kit palette (" + Valuables.paletteFiles(srv) + " kingdom_castle/*.json files) names a valuable block as a palette target, piece, cap or waterfront block",
				f.isEmpty(), f.isEmpty() ? null : f);
			filterChecks();
			for (String h : HOSTILE_IDS) CastleStyles.register(id(h), CastleStyles.parse(JsonParser.parseString(HOSTILE).getAsJsonObject()));
			BoundingBox all = area(origin());
			for (int cx = all.minX() >> 4; cx <= all.maxX() >> 4; cx++) for (int cz = all.minZ() >> 4; cz <= all.maxZ() >> 4; cz++) l.setChunkForced(cx, cz, true);
			castleKit0 = Overhaul3.CASTLE_KIT;
			townKit0 = Overhaul3.TOWN_KIT;
			List<SelfTest.Step> steps = new ArrayList<>();
			for (Job j : plan(srv)) steps.add((s2, l2) -> {
				run(l2, j);
				return 1;
			});
			steps.add((s2, l2) -> {
				waterfronts(l2);
				return 1;
			});
			List<Other> others = others();
			for (int i = 0; i < others.size(); i++) {
				Other ot = others.get(i);
				BlockPos at = new BlockPos(AT.getX() + 2000 + (i % 8) * 144, SelfTest.y, AT.getZ() + (i / 8) * 144);
				steps.add((s2, l2) -> {
					other(l2, ot, at);
					return 1;
				});
			}
			STEPS.addAll(SelfTest.idx, steps);
			return 60;
		});
		STEPS.add((srv, l) -> {
			restore();
			check("valuables: Aldermark's throne is yellow terracotta on the frozen fv 1 path with castle_kit=false (the B9 gold throne is gone)", throneOk == 1, throneOk);
			check("valuables: the hostile palettes were really applied and neutralised (stand-in blocks present)", hostileStandIns > 0, hostileStandIns + " stand-ins");
			check("valuables: " + jobs + " realm builds (styles " + STYLES_SEEN + ", fv 1 and fv 2, every switch combination) contain no valuable block",
				jobs >= 8 * HOSTILE_IDS.length && valuableTotal == 0, valuableTotal + " valuable in " + blocksScanned + " scanned blocks" + (BAD.isEmpty() ? "" : "; " + BAD));
			check("valuables: every waterfront (jetty, boathouse, longships; " + waterfronts + " built, hostile included) has no valuable block", waterfronts >= 1 && waterfrontValuable == 0, waterfrontValuable);
			// s-asm-court: the court's dev run has the other modules only as its pre-3.0 devlib (merged elsewhere.jar 2.0.0-alpha.4), whose old
			// blueprints are not what 3.0 ships; the check is for the merged 3.0 jar, where the "elsewhere" mod is 3.x
			String core = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("elsewhere")
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
			if (otherBuilt > 0 && !core.startsWith("3.")) info("valuables: the other modules on this classpath are the court's pre-3.0 devlib (elsewhere " + core
				+ "), not 3.0 code: " + otherBuilt + " blueprints, " + otherValuable + " valuable there; the cross-module scan is checked in the merged 3.0 jar");
			else if (otherBuilt == 0) info("valuables: no other module's structures on this classpath (module dev run): the cross-module scan runs in the merged jar"
				+ (otherCrashed > 0 ? "; " + otherCrashed + " could not be built here" : ""));
			else check("valuables: " + otherBuilt + " structure blueprints of the other modules (nether arenas, Verdigris/Ironpeak legacy and fit, Greenhaze/Hush, overworld bosses) contain no valuable block",
				otherValuable == 0, otherValuable + " valuable" + (OTHER_BAD.isEmpty() ? "" : "; " + OTHER_BAD) + (otherCrashed > 0 ? "; " + otherCrashed + " not buildable in this test world (info)" : ""));
			check("valuables: every swept switch is restored (castle_kit " + Overhaul3.CASTLE_KIT + ", town_kit " + Overhaul3.TOWN_KIT + ")",
				Overhaul3.CASTLE_KIT == castleKit0 && Overhaul3.TOWN_KIT == townKit0 && swept0(), null);
			for (String h : HOSTILE_IDS) CastleStyles.clear(id(h));
			BoundingBox all = area(origin());
			for (int cx = all.minX() >> 4; cx <= all.maxX() >> 4; cx++) for (int cz = all.minZ() >> 4; cz <= all.maxZ() >> 4; cz++) l.setChunkForced(cx, cz, false);
			return 1;
		});
	}

	// ---- filter -----------------------------------------------------------------------------------------------------

	static void filterChecks() {
		Block[] valuable = {Blocks.GOLD_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK,
			Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.ANCIENT_DEBRIS, Blocks.GILDED_BLACKSTONE, Blocks.BEACON, Blocks.CONDUIT,
			Blocks.LODESTONE, Blocks.RAW_COPPER_BLOCK, Blocks.COPPER_BLOCK.waxed().unaffected(), Blocks.COPPER_BLOCK.weathering().oxidized(), Blocks.DIAMOND_ORE,
			Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE, Blocks.EMERALD_ORE};
		List<String> missed = new ArrayList<>(), leaked = new ArrayList<>();
		for (Block b : valuable) {
			if (!Valuables.isValuable(b)) missed.add(name(b));
			BlockState s = Valuables.safe(b.defaultBlockState());
			if (Valuables.isValuable(s) || s.isAir()) leaked.add(name(b) + "->" + name(s.getBlock()));
		}
		check("valuables: the filter knows every valuable block (" + valuable.length + " samples: storage, raw, copper, ores, debris, gilded, beacon, conduit, lodestone)", missed.isEmpty(), missed.isEmpty() ? null : missed);
		check("valuables: every stand-in is a real, non-valuable block", leaked.isEmpty(), leaked.isEmpty() ? null : leaked);
		check("valuables: gold becomes yellow terracotta and lapis blue glazed terracotta (STRUCTURES.md 3, RIVALS-DECISIONS 16)",
			Valuables.safe(Blocks.GOLD_BLOCK.defaultBlockState()).is(Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW))
				&& Valuables.safe(Blocks.LAPIS_BLOCK.defaultBlockState()).is(Blocks.GLAZED_TERRACOTTA.pick(DyeColor.BLUE)), null);
		Block[] plain = {Blocks.STONE_BRICKS, Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW), Blocks.CUT_COPPER.waxed().unaffected(), Blocks.IRON_BARS,
			Blocks.AMETHYST_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.HONEYCOMB_BLOCK, Blocks.LANTERN, Blocks.CHEST};
		List<String> touched = new ArrayList<>();
		for (Block b : plain) if (Valuables.safe(b.defaultBlockState()) != b.defaultBlockState()) touched.add(name(b));
		check("valuables: ordinary blocks pass the filter unchanged (" + plain.length + " samples)", touched.isEmpty(), touched.isEmpty() ? null : touched);
	}

	static String name(Block b) {
		return BuiltInRegistries.BLOCK.getKey(b).getPath();
	}

	// ---- the sweep --------------------------------------------------------------------------------------------------

	/** Every style the server knows plus the hostile ones, times fv 1/2, times every combination of the switches that can change its blocks. */
	static List<Job> plan(MinecraftServer srv) {
		List<String> styles = new ArrayList<>();
		styles.add(null); // Aldermark: no style
		for (var e : srv.getResourceManager().listResources("kingdom_castle", i -> i.getPath().endsWith(".json")).keySet()) {
			String p = e.getPath();
			String sid = e.getNamespace() + ":" + p.substring("kingdom_castle/".length(), p.length() - 5);
			if (CastleStyles.of(Identifier.parse(sid)) != null) styles.add(sid);
		}
		for (String h : HOSTILE_IDS) styles.add(id(h).toString());
		SWEPT.clear();
		SWEPT0.clear();
		List<Job> out = new ArrayList<>();
		for (String st : styles) {
			STYLES_SEEN.add(st == null ? "aldermark" : st);
			for (int fv = 1; fv <= 2; fv++) {
				Field layout = null;
				if (fv == 2 && st != null) for (String[] sw : LAYOUT_SWITCHES) if (st.contains(sw[3])) layout = sweepable(sw[1], sw[2]);
				for (int k = 0; k < 4; k++) {
					boolean ck = (k & 1) != 0, tk = (k & 2) != 0;
					if (layout == null) out.add(new Job(st, fv, ck, tk, null, false));
					else for (int m = 0; m < 2; m++) out.add(new Job(st, fv, ck, tk, layout, m == 1));
				}
			}
		}
		info("valuables: " + out.size() + " realm builds planned over " + styles.size() + " styles; swept layout switches " + SWEPT.size());
		return out;
	}

	static @Nullable Field sweepable(String cls, String name) {
		try {
			Field f = Class.forName(cls).getField(name);
			if (Modifier.isFinal(f.getModifiers()) || f.getType() != boolean.class) {
				info("valuables: " + cls.substring(cls.lastIndexOf('.') + 1) + "." + name + " is final: its current value (" + f.getBoolean(null)
					+ ") is built; the other value is covered by the Placer filter, which every layout writes through");
				return null;
			}
			if (!SWEPT.contains(f)) {
				SWEPT.add(f);
				SWEPT0.add(f.getBoolean(null));
			}
			return f;
		} catch (Throwable t) {
			return null;
		}
	}

	static boolean swept0() {
		try {
			for (int i = 0; i < SWEPT.size(); i++) if (SWEPT.get(i).getBoolean(null) != SWEPT0.get(i)) return false;
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	static void restore() {
		Overhaul3.CASTLE_KIT = castleKit0;
		Overhaul3.TOWN_KIT = townKit0;
		try {
			for (int i = 0; i < SWEPT.size(); i++) SWEPT.get(i).setBoolean(null, SWEPT0.get(i));
		} catch (Throwable ignored) {
		}
	}

	/** Gentle synthetic relief for the fv 2 path (deterministic; the walls, road and towns follow it). */
	static CastleLayout.Ground ground(BlockPos o) {
		return (x, z) -> o.getY() + (int) Math.round(1.5 * Math.sin(x * 0.11) + 1.5 * Math.cos(z * 0.09));
	}

	static void run(ServerLevel l, Job j) {
		BlockPos o = origin();
		CastleStyles.Style style = j.style() == null ? null : CastleStyles.of(Identifier.parse(j.style()));
		CastleLayout.Ground g = j.fv() == 2 ? ground(o) : null;
		String tag = (j.style() == null ? "aldermark" : j.style()) + " fv" + j.fv() + " castle_kit=" + j.castleKit() + " town_kit=" + j.townKit()
			+ (j.layout() == null ? "" : " " + j.layout().getDeclaringClass().getSimpleName() + "=" + j.layoutOn());
		try {
			Overhaul3.CASTLE_KIT = j.castleKit();
			Overhaul3.TOWN_KIT = j.townKit();
			if (j.layout() != null) j.layout().setBoolean(null, j.layoutOn());
			for (CastleLayout.Part p : CastleLayout.Part.values()) {
				BoundingBox box = g == null ? CastleLayout.box(p, o) : CastleLayout.fitBox(p, o);
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15));
					pl.style = style;
					pl.ground = g;
					CastleLayout.build(p, o, pl);
				}
			}
		} catch (Throwable t) {
			BAD.add(tag + ": build crashed " + t);
		} finally {
			restore();
		}
		List<String> hits = new ArrayList<>();
		int n = 0;
		for (BoundingBox b : regions(o)) n += scan(l, b, hits);
		jobs++;
		valuableTotal += n;
		if (n > 0) BAD.add(tag + ": " + n + " " + hits);
		if (j.style() == null && j.fv() == 1 && !j.castleKit() && !j.townKit()) {
			Block y = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW);
			throneOk = l.getBlockState(o.offset(-1, 1, -24)).is(y) && l.getBlockState(o.offset(1, 1, -24)).is(y) ? 1 : 0;
		}
		if (j.style() != null && j.style().endsWith("sv_hostile") && j.fv() == 1) hostileStandIns += count(l, CastleLayout.box(CastleLayout.Part.CASTLE, o), Blocks.DYED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE));
		info("valuables: " + tag + ": " + n + " valuable block(s)");
	}

	/** The waterfront of every style that has one (and the hostile one), built in the air south of the castle and scanned. */
	static void waterfronts(ServerLevel l) {
		BlockPos o = origin();
		int sea = o.getY() + 6;
		for (String st : STYLES_SEEN) {
			if (st.equals("aldermark")) continue;
			Identifier f = Identifier.parse(st);
			CastleStyles.Style s = CastleStyles.of(f);
			if (s == null || s.waterfront == null) continue;
			CourtWorld.buildWaterfrontNow(l, o, f, 1, 60, sea);
			List<String> hits = new ArrayList<>();
			int n = scan(l, Waterfront.box(o, 1, 60, sea), hits);
			waterfronts++;
			waterfrontValuable += n;
			if (n > 0) BAD.add(st + " waterfront: " + n + " " + hits);
			info("valuables: " + st + " waterfront: " + n + " valuable block(s)");
		}
	}

	// ---- other modules (merged jar) ----------------------------------------------------------------------------------

	interface Builder {
		void at(ServerLevel l, BlockPos o) throws Exception;
	}

	/** A structure blueprint of another module: name, scan half-size, depth below and height above its origin, and how to build it. */
	record Other(String name, int r, int below, int above, Builder b) {}

	/** Every structure blueprint of the other modules present on this classpath, by reflection (no compile dependency on them). */
	static List<Other> others() {
		List<Other> out = new ArrayList<>();
		try { // nether_end_bosses: every boss arena builder (the legacy v1 halls; v2 fit halls write through the same blocks)
			Class<?> ab = Class.forName("dev.elsewhere.nether_end_bosses.ArenaBuilder");
			Method build = ab.getMethod("build", WorldGenLevel.class, BlockPos.class, RandomSource.class);
			for (Map.Entry<String, Object> e : named((Map<?, ?>) ab.getField("BY_BOSS").get(null)).entrySet()) {
				Object a = e.getValue();
				out.add(new Other("nether arena " + e.getKey(), 52, 16, 56, (l, o) -> build.invoke(a, l, o, RandomSource.create(7))));
			}
		} catch (ClassNotFoundException ignored) {
		} catch (Throwable t) {
			info("valuables: nether arenas not reachable: " + t);
		}
		try { // verdigris_ironpeak: Blueprint.ALL (legacy, saved 2.x pieces) and Blueprint.FIT (3.0 pieces)
			Class<?> bp = Class.forName("dev.elsewhere.verdigris_ironpeak.world.Blueprint");
			Class<?> sink = Class.forName("dev.elsewhere.verdigris_ironpeak.world.BlockSink");
			Constructor<?> ctor = sink.getConstructor(WorldGenLevel.class, BlockPos.class, BoundingBox.class, RandomSource.class);
			Method build = bp.getMethod("build", sink, RandomSource.class), radius = bp.getMethod("radius"), height = bp.getMethod("height"), depth = bp.getMethod("depth");
			for (String map : new String[] {"ALL", "FIT"}) {
				for (Map.Entry<String, Object> e : named((Map<?, ?>) bp.getField(map).get(null)).entrySet()) {
					Object b = e.getValue();
					out.add(new Other("verdigris " + map + " " + e.getKey(), (int) radius.invoke(b) + 4, (int) depth.invoke(b) + 4, (int) height.invoke(b) + 4, (l, o) -> {
						RandomSource rs = RandomSource.create(7);
						build.invoke(b, ctor.newInstance(l, o, null, rs), rs);
					}));
				}
			}
		} catch (ClassNotFoundException ignored) {
		} catch (Throwable t) {
			info("valuables: verdigris blueprints not reachable: " + t);
		}
		try { // greenhaze_hush: every BuiltStructure blueprint, through the live sink
			Class<?> bs = Class.forName("dev.elsewhere.greenhaze_hush.world.BuiltStructure");
			Class<?> bp = Class.forName("dev.elsewhere.greenhaze_hush.world.Blueprint");
			Class<?> sink = Class.forName("dev.elsewhere.greenhaze_hush.world.Sink");
			Method live = sink.getMethod("live", ServerLevel.class);
			Method build = bp.getMethod("build", sink, BlockPos.class, RandomSource.class), radius = bp.getMethod("radius"), below = bp.getMethod("below"), above = bp.getMethod("above");
			for (Map.Entry<String, Object> e : named((Map<?, ?>) bs.getField("BLUEPRINTS").get(null)).entrySet()) {
				Object b = e.getValue();
				out.add(new Other("greenhaze " + e.getKey(), (int) radius.invoke(b) + 4, (int) below.invoke(b) + 4, (int) above.invoke(b) + 4,
					(l, o) -> build.invoke(b, live.invoke(null, l), o, RandomSource.create(7))));
			}
		} catch (ClassNotFoundException ignored) {
		} catch (Throwable t) {
			info("valuables: greenhaze blueprints not reachable: " + t);
		}
		try { // overworld_bosses: every public static Blueprints builder (BlockSink, BlockPos, ...) through a live-level sink
			Class<?> bps = Class.forName("dev.elsewhere.overworld_bosses.world.Blueprints");
			Class<?> sink = Class.forName("dev.elsewhere.overworld_bosses.world.BlockSink");
			Method levelSink = null;
			try {
				levelSink = Class.forName("dev.elsewhere.overworld_bosses.Summons").getMethod("levelSink", ServerLevel.class);
			} catch (ReflectiveOperationException ignored) {
			}
			Method ls = levelSink;
			for (Method m : bps.getMethods()) {
				Class<?>[] ps = m.getParameterTypes();
				if (!Modifier.isStatic(m.getModifiers()) || ps.length < 2 || ps[0] != sink || ps[1] != BlockPos.class) continue;
				out.add(new Other("overworld " + m.getName(), 40, 20, 40, (l, o) -> {
					Object[] args = new Object[ps.length];
					args[0] = ls != null ? ls.invoke(null, l) : liveSink(sink, l);
					args[1] = o;
					for (int i = 2; i < ps.length; i++) args[i] = ps[i] == boolean.class ? Boolean.TRUE : ps[i] == int.class ? (Object) (o.getY() + 14) : null;
					m.invoke(null, args);
				}));
			}
		} catch (ClassNotFoundException ignored) {
		} catch (Throwable t) {
			info("valuables: overworld blueprints not reachable: " + t);
		}
		return out;
	}

	/** String-keyed copy of a registry map, sorted (stable step order). */
	static Map<String, Object> named(Map<?, ?> m) {
		Map<String, Object> out = new TreeMap<>();
		for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
		return out;
	}

	/** A live-level implementation of the overworld module's BlockSink interface (set/get abstract, the rest default). */
	static Object liveSink(Class<?> sink, ServerLevel l) {
		InvocationHandler h = (proxy, method, args) -> {
			if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
			if (method.getName().equals("set") && args != null && args.length == 2) {
				l.setBlock((BlockPos) args[0], (BlockState) args[1], Block.UPDATE_CLIENTS);
				return null;
			}
			if (method.getName().equals("get") && args != null && args.length == 1) return l.getBlockState((BlockPos) args[0]);
			if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
			if (method.getName().equals("equals")) return proxy == args[0];
			if (method.getName().equals("toString")) return "valuables-live-sink";
			throw new UnsupportedOperationException(method.getName());
		};
		return Proxy.newProxyInstance(sink.getClassLoader(), new Class<?>[] {sink}, h);
	}

	static void other(ServerLevel l, Other ot, BlockPos o) {
		BoundingBox box = new BoundingBox(o.getX() - ot.r(), o.getY() - ot.below(), o.getZ() - ot.r(), o.getX() + ot.r(), o.getY() + ot.above(), o.getZ() + ot.r());
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) l.setChunkForced(cx, cz, true);
		try {
			ot.b().at(l, o);
		} catch (Throwable t) {
			Throwable c = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
			otherCrashed++;
			info("valuables: " + ot.name() + " could not be built in this test world (" + c + "); scanning what it placed");
		}
		List<String> hits = new ArrayList<>();
		int n = scan(l, box, hits);
		otherBuilt++;
		otherValuable += n;
		if (n > 0) OTHER_BAD.add(ot.name() + ": " + n + " " + hits);
		info("valuables: " + ot.name() + ": " + n + " valuable block(s)");
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) l.setChunkForced(cx, cz, false);
	}

	// ---- scanning ---------------------------------------------------------------------------------------------------

	/** Every part's box on both paths (fv 1 box and fv 2 fitBox), merged per part group; overlaps are counted once. */
	static List<BoundingBox> regions(BlockPos o) {
		List<BoundingBox> out = new ArrayList<>();
		for (CastleLayout.Part p : CastleLayout.Part.values()) {
			BoundingBox a = CastleLayout.box(p, o), b = CastleLayout.fitBox(p, o);
			BoundingBox u = new BoundingBox(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()),
				Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
			if (!out.contains(u)) out.add(u);
		}
		return out;
	}

	/** Everything the sweep and the waterfront may touch (for chunk forcing). */
	static BoundingBox area(BlockPos o) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		List<BoundingBox> all = regions(o);
		all.add(Waterfront.box(o, 1, 60, o.getY() + 6));
		for (BoundingBox b : all) {
			x0 = Math.min(x0, b.minX());
			y0 = Math.min(y0, b.minY());
			z0 = Math.min(z0, b.minZ());
			x1 = Math.max(x1, b.maxX());
			y1 = Math.max(y1, b.maxY());
			z1 = Math.max(z1, b.maxZ());
		}
		return new BoundingBox(x0, y0, z0, x1, y1, z1);
	}

	/** Counts valuable blocks in {@code b}, notes the first few and clears each one found (so overlapping regions count it once and the next build starts clean). */
	static int scan(ServerLevel l, BoundingBox b, List<String> hits) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int lo = Math.max(b.minY(), l.getMinY()), hi = Math.min(b.maxY(), l.getMaxY());
		for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int y = lo; y <= hi; y++) {
			BlockState s = l.getBlockState(m.set(x, y, z));
			blocksScanned++;
			if (!Valuables.isValuable(s)) continue;
			n++;
			if (hits.size() < 6) hits.add(name(s.getBlock()) + "@" + (x - AT.getX()) + "," + (y - SelfTest.y) + "," + (z - AT.getZ()));
			l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
		}
		return n;
	}

	static int count(ServerLevel l, BoundingBox b, Block block) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int y = b.minY(); y <= b.maxY(); y++)
			if (l.getBlockState(m.set(x, y, z)).is(block)) n++;
		return n;
	}
}
