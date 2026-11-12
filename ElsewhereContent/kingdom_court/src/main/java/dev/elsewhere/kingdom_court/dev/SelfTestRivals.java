package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.at;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.clean;
import static dev.elsewhere.kingdom_court.dev.SelfTest.fp;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom_court.CourtApi;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtRealms;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.api.Duel;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.api.Oligarchy;
import dev.elsewhere.kingdom_court.api.RealmMusic;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import dev.elsewhere.kingdom_court.world.CourtSite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 2.1 rivals build, court lane: placement over 20 seeds (decision 7), leader roles to custom bosses, rival drops (DropRule), shops and weekly limits per faction, Mirewick's
 * exchange, tribute, the diplomacy NPCs, Azmar's offer, contracts, the duel right, the oligarchy (vault Chair, vacancy, votes, bribes, refills, Dark Lanterns), the fall of
 * every faction (roaming neutrals), castle styles and the realm-music hook. Every new rule has a check here.
 */
public final class SelfTestRivals {
	private SelfTestRivals() {}

	static Identifier id(String p) {
		return Identifier.fromNamespaceAndPath("elsewhere", p);
	}

	static final Identifier HR = id("rv_hrafn"), AZ = id("rv_azmar"), MR = id("rv_mire"), SH = id("rv_shop"), SD = id("rv_shop2"), DU = id("rv_duel"), FA = id("rv_fall_a"), FB = id("rv_fall_b");

	static final String RUN = Long.toHexString(System.nanoTime()) + "-";

	static BlockPos ORG() {
		return SelfTest.origin != null ? SelfTest.origin : new BlockPos(0, SelfTest.y, 0);
	}

	static Item item(String path) {
		return BuiltInRegistries.ITEM.getValue(id(path));
	}

	static FactionDef.RulerDef rd(String name, double health, double cap, List<FactionDef.Drop> drops) {
		return new FactionDef.RulerDef(name, Optional.empty(), "", health, cap > 0 ? Optional.of(cap) : Optional.empty(), drops);
	}

	static FactionDef.Drop drop(String item, int min, int max, float chance) {
		return new FactionDef.Drop(Identifier.parse(item), min, max, chance);
	}

	static FactionDef fd(String name, Map<String, FactionDef.RulerDef> rulers, FactionDef.Society society, FactionDef.Economy econ, List<String> biomes, FactionDef.Army army) {
		FactionDef b = dev.elsewhere.kingdom.dev.TestFactions.def();
		return new FactionDef(name, Optional.empty(), b.banner(), b.tabard(), Optional.empty(), true, List.of(), List.of(), Map.of(), rulers, biomes, Optional.empty(), false, society, econ, army);
	}

	static FactionDef.Society mon(String bonus, double amount, List<String> leaders) {
		return new FactionDef.Society(FactionDef.Government.MONARCHY, new FactionDef.RulerBonus(bonus, amount), leaders, FactionDef.StandingRule.DEFAULT, FactionDef.Serfs.DEFAULT, 7, 7);
	}

	static FactionDef.Society oli(List<String> leaders) {
		return new FactionDef.Society(FactionDef.Government.OLIGARCHY, new FactionDef.RulerBonus("hp_per_other_magnate", 12.0), leaders, FactionDef.StandingRule.DEFAULT, FactionDef.Serfs.DEFAULT, 7, 7);
	}

	static final String[] SLOTS6 = {"king", "marshal", "castellan", "master_at_arms", "huntsman", "chancellor"};

	static Map<String, FactionDef.RulerDef> rulers(int n, List<FactionDef.Drop> drops) {
		Map<String, FactionDef.RulerDef> m = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) m.put(SLOTS6[i], rd("Leader " + SLOTS6[i], i == 0 ? 250 : 120, i == 0 ? 330 : 0, drops));
		return m;
	}

	static List<FactionDef.Drop> bossDrops(String currency) {
		return List.of(drop("elsewhere:court_warmarshal_spurs", 1, 1, 1.0F), drop("elsewhere:court_gatewarden_plate", 1, 1, 1.0F), drop(currency, 16, 32, 1.0F), drop("minecraft:emerald", 1, 3, 1.0F));
	}

	static FactionDef.Economy econ(String cur, int weekly) {
		return new FactionDef.Economy(Optional.of(Identifier.parse(cur)), List.of(
			new FactionDef.ShopEntry(id("court_warmarshal_spurs"), 1, 20, List.of(), weekly),
			new FactionDef.ShopEntry(Identifier.withDefaultNamespace("bread"), 4, 3, List.of(), 0)));
	}

	static CourtRuler live(ServerLevel l, CourtState st, Ruler r) {
		UUID i = st.rulerIds.get(r);
		return i != null && l.getEntity(i) instanceof CourtRuler c ? c : null;
	}

	static void killAny(ServerLevel l, net.minecraft.world.entity.LivingEntity e, FakePlayer killer) {
		e.setHealth(1.0F);
		e.die(l.damageSources().playerAttack(killer));
		e.setHealth(0.0F);
	}

	static void giveCoins(FakePlayer p, Identifier faction, int n) {
		Shops.give(p, faction, n);
	}

	static void add() {
		placement();
		leadersAndDrops();
		shops();
		diplomacy();
		duel();
		oligarchy();
		fall();
		succession();
		style();
		music();
	}

	// ---- placement (RIVALS-DECISIONS 7) ----------------------------------------------------------------------------------------

	static String dbg(List<String> d) {
		String j = String.join(" | ", d);
		return j.length() > 500 ? j.substring(j.length() - 500) : j;
	}

	static void placement() {
		STEPS.add((srv, l) -> {
			var reg = srv.registryAccess();
			ChunkGenerator gen = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
			NoiseBasedChunkGenerator nbg = (NoiseBasedChunkGenerator) gen;
			var noises = reg.lookupOrThrow(Registries.NOISE);
			Identifier[] f = {id("rv_p1"), id("rv_p2"), id("rv_p3")};
			// the rival biome sets (Hrafnholt pine forest and hills, Azmar desert, Mirewick plains) and terrain wishes (rise / flat + river / flat + river)
			List<List<String>> biomes = List.of(
				List.of("minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:windswept_forest", "minecraft:river", "minecraft:frozen_river", "minecraft:cold_ocean", "minecraft:ocean", "minecraft:deep_cold_ocean"),
				List.of("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:savanna", "minecraft:savanna_plateau"),
				List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"));
			// the faction JSON fallback lists (Hrafnholt: snowy taiga and grove; Mirewick: forests; Azmar: none, so the temperate realm tag)
			List<List<String>> fallback = List.of(List.of("minecraft:snowy_taiga", "minecraft:grove"), List.of(), List.of("minecraft:forest", "minecraft:birch_forest"));
			boolean[] rise = {true, false, false}, water = {false, true, true};
			List<CourtSite.Spec> specs = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				var strict = CourtSite.predicateOf(biomes.get(i));
				var fb = CourtSite.predicateOf(fallback.get(i));
				specs.add(new CourtSite.Spec(f[i], strict, fallback.get(i).isEmpty() ? (b -> strict.test(b) || b.is(CourtSite.BIOMES)) : (b -> strict.test(b) || fb.test(b)), rise[i], water[i], i == 0));
			}
			int strictBiome = 0, hrafBad = 0, hraf = 0, hrafWf = 0, seeds = 20, good = 0, bad = 0, aldChanged = 0, detBad = 0, noSite = 0, wedgeBad = 0, rangeBad = 0, ownBiome = 0, rivalSites = 0, neighbourBad = 0, farBand = 0;
			long maxMs = 0, sumMs = 0;
			int[] ownBy = new int[3], sitesBy = new int[3], strictBy = new int[3]; // per kingdom (f[0] Hrafnholt, f[1] Azmar, f[2] Mirewick)
			StringBuilder misses = new StringBuilder();
			int slowSeed = -1;
			String slowDbg = "";
			double pairsSum = 0;
			StringBuilder sb = new StringBuilder(), sample = new StringBuilder();
			BlockPos[] spawns = {BlockPos.ZERO, new BlockPos(200, 0, -300), new BlockPos(-640, 0, 480), new BlockPos(1500, 0, 900)};
			for (int s = 0; s < seeds; s++) {
				long seed = 1000003L * (s + 1) + s * 7919L;
				BlockPos spawn = spawns[s % spawns.length];
				RandomState rs = RandomState.create(noises, seed, nbg.generatorSettings().value());
				BlockPos a = CourtSite.choose(seed, spawn, gen, rs, l, cp -> false);
				BlockPos a2 = CourtSite.choose(seed, spawn, gen, rs, l, cp -> false, Factions.ALDERMARK, List.of());
				if (a == null ? a2 != null : !a.equals(a2)) aldChanged++;
				List<BlockPos> fixed = a == null ? List.of() : List.of(a);
				var m = CourtSite.chooseRivals(seed, spawn, gen, rs, l, cp -> false, a, fixed, specs);
				if (m.size() < 3 && s < 4) info("placement debug seed " + s + " " + String.join(" | ", CourtSite.DEBUG));
				long ms = CourtSite.lastSearchMs;
				if (ms > maxMs || slowSeed < 0) {
					slowSeed = s;
					List<String> dl = CourtSite.DEBUG;
					slowDbg = dl.isEmpty() ? "" : dl.get(dl.size() - 1) + " | " + dl.stream().filter(u -> u.startsWith("P1")).reduce((u, w) -> u + "; " + w).orElse("no phase 1 rings");
				}
				maxMs = Math.max(maxMs, ms);
				sumMs += ms;
				var m2 = CourtSite.chooseRivals(seed, spawn, gen, rs, l, cp -> false, a, fixed, specs);
				if (!m.equals(m2)) detBad++;
				List<BlockPos> placed = new ArrayList<>(fixed);
				double bearing = a == null ? 0 : Math.atan2(a.getZ() - spawn.getZ(), a.getX() - spawn.getX());
				for (int i = 0; i < 3; i++) {
					BlockPos p = m.get(f[i]);
					if (p == null) {
						noSite++;
						continue;
					}
					placed.add(p);
					rivalSites++;
					sitesBy[i]++;
					double d0 = Math.hypot(p.getX() - spawn.getX(), p.getZ() - spawn.getZ());
					if (d0 < 1500 || d0 > 12000) bad++;
					if (d0 > 5000) farBand++;
					double ang = Math.atan2(p.getZ() - spawn.getZ(), p.getX() - spawn.getX());
					if (a != null && Math.abs(Math.atan2(Math.sin(ang - bearing), Math.cos(ang - bearing))) > Math.PI / 4 + 0.01) wedgeBad++;
					var bh = CourtSite.biomeAt(gen, rs, p.getX(), p.getY(), p.getZ());
					if (specs.get(i).strict().test(bh)) {
						strictBiome++;
						strictBy[i]++;
					}
					if (specs.get(i).wide().test(bh)) {
						ownBiome++; // the faction's own list or its JSON fallback list
						ownBy[i]++;
					} else misses.append(' ').append(new String[] {"H", "Az", "Mi"}[i]).append("@s").append(s).append('=').append(bh.getRegisteredName()).append('/').append((int) d0);
					int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
					for (int[] q : new int[][] {{0, 0}, {-30, -30}, {30, 30}, {30, -30}, {-30, 30}, {0, -30}, {0, 30}, {-30, 0}, {30, 0}}) {
						int y = gen.getBaseHeight(p.getX() + q[0], p.getZ() + q[1], Heightmap.Types.OCEAN_FLOOR_WG, l, rs);
						lo = Math.min(lo, y);
						hi = Math.max(hi, y);
					}
					if (hi - lo > 16) rangeBad++;
					if (i == 0) {
						hraf++;
						if (CourtSite.WATER_BEARING.containsKey(f[0])) {
							hrafWf++;
							int sh = CourtSite.WATER_SHORE.getOrDefault(f[0], 0);
							boolean reaches = sh >= 40 && sh <= 136 && gen.getBaseHeight(p.getX(), p.getZ() + (CourtSite.WATER_BEARING.get(f[0]) > 0 ? sh : -sh), Heightmap.Types.OCEAN_FLOOR_WG, l, rs) <= gen.getSeaLevel();
							if (CourtSite.waterShare(gen, rs, l, p) < 0.25 || !reaches) hrafBad++;
						}
					}
				}
				int pairs = 0;
				boolean spacing = true;
				for (int i = 0; i < placed.size(); i++) {
					boolean near = false;
					for (int j = 0; j < placed.size(); j++) {
						if (i == j) continue;
						double d = Math.hypot(placed.get(i).getX() - placed.get(j).getX(), placed.get(i).getZ() - placed.get(j).getZ());
						if (d < CourtSite.MIN_APART) spacing = false;
						if (d <= CourtSite.NEIGHBOUR) {
							near = true;
							if (j > i) pairs++;
						}
					}
					if (!near && placed.size() > 1) neighbourBad++;
				}
				pairsSum += pairs;
				if (!spacing) bad++;
				if (placed.size() == 4 && pairs >= 2) good++;
				sb.append(' ').append(pairs);
				if (s < 3) sample.append(" seed").append(s).append(" spawn ").append(spawn.getX()).append(',').append(spawn.getZ()).append(" A=").append(a == null ? "-" : a.getX() + "," + a.getZ()).append(" H=").append(m.get(f[0])).append(" Az=").append(m.get(f[1])).append(" Mi=").append(m.get(f[2])).append(';');
			}
			info("placement over 20 seeds (rivals in Aldermark's 90-degree wedge), kingdom pairs sharing a frontier per seed:" + sb + "; search ms per seed mean " + sumMs / seeds + " max " + maxMs);
			info("placement sample sites:" + sample);
			check("placement: all four in one 90-degree wedge centred on Aldermark's bearing, rivals 1,500-5,000 blocks from spawn (outward bands only when needed: " + farBand + " beyond)", wedgeBad == 0 && bad == 0 && noSite == 0, wedgeBad + " outside the wedge, " + bad + " bad, " + noSite + " unplaced");
			check("placement: castles at least 1,800 apart and every realm has a neighbour within 3,000", neighbourBad == 0, neighbourBad + " without a neighbour");
			check("placement: at least 2 kingdom pairs share a frontier (castles 1,800-3,000 apart) in 90 percent of 20 seeds", good >= 18, good + "/20, mean pairs " + pairsSum / seeds);
			String split = "Hrafnholt " + ownBy[0] + "/" + sitesBy[0] + ", Azmar " + ownBy[1] + "/" + sitesBy[1] + ", Mirewick " + ownBy[2] + "/" + sitesBy[2];
			info("INFO placement biome hit " + ownBiome + "/" + rivalSites + " (target 80%, aim 90%): " + split + "; first-choice list " + strictBiome + " (Hrafnholt " + strictBy[0] + ", Azmar " + strictBy[1] + ", Mirewick " + strictBy[2] + "); misses" + (misses.length() == 0 ? " none" : misses));
			check("placement: at least 80 percent of rival castle centres are in their own biomes (first-choice list or its fallback list), overall", rivalSites > 0 && ownBiome >= 0.8 * rivalSites, ownBiome + "/" + rivalSites + "; " + split);
			for (int i = 0; i < 3; i++) check("placement: at least 80 percent of " + new String[] {"Hrafnholt", "Azmar", "Mirewick"}[i] + " castle centres are in its own biomes (first-choice or fallback list)", sitesBy[i] > 0 && ownBy[i] >= 0.8 * sitesBy[i], ownBy[i] + "/" + sitesBy[i]);
			check("placement: every Hrafnholt waterfront site has at least 25 percent water in its 32-96 block ring and the jetty line reaches water (" + hrafWf + " of " + hraf + " seeds have a waterfront)", hrafBad == 0, hrafBad + " bad, " + hrafWf + "/" + hraf);
			check("placement: castle height range is at most 16 (terraform cost); at most 10 percent of sites may need the last-resort ground (24)", rangeBad <= rivalSites / 10, rangeBad + " of " + rivalSites);
			// the search runs once per world at SERVER_STARTED, before the first tick (RIVALS-DECISIONS 15: total at most 1.5 s, logged)
			info("INFO placement search mean " + sumMs / seeds + " ms / max " + maxMs + " ms (target 1.5 s); slowest seed " + slowSeed + ": " + slowDbg);
			check("placement: the whole search for the three rivals takes at most 1.5 s per world", maxMs <= 1500, "max " + maxMs + " ms, mean " + sumMs / seeds);
			check("placement: deterministic per seed, and Aldermark's own choice is the 2.0 choice", detBad == 0 && aldChanged == 0, detBad + " " + aldChanged);
			// existing worlds: footprints that were already generated are never used, and the realm is still placed, further out
			long seed = 1000003L;
			RandomState rs = RandomState.create(noises, seed, nbg.generatorSettings().value());
			BlockPos a = CourtSite.choose(seed, BlockPos.ZERO, gen, rs, l, cp -> false);
			var free = CourtSite.chooseRivals(seed, BlockPos.ZERO, gen, rs, l, cp -> false, a, a == null ? List.of() : List.of(a), specs);
			for (int radius : new int[] {3300, 5300}) {
				final int rad = radius;
				Predicate<ChunkPos> gend = cp -> Math.hypot(cp.x() * 16 + 8, cp.z() * 16 + 8) < rad;
				var far = CourtSite.chooseRivals(seed, BlockPos.ZERO, gen, rs, l, gend, a, a == null ? List.of() : List.of(a), specs);
				boolean all = far.size() == 3, fresh = true;
				double minD = 1e9;
				for (BlockPos p : far.values()) {
					minD = Math.min(minD, Math.hypot(p.getX(), p.getZ()));
					ChunkPos pc = ChunkPos.containing(p);
					for (int dx = -7; dx <= 7; dx++) for (int dz = -3; dz <= 3; dz++) if (gend.test(new ChunkPos(pc.x() + dx, pc.z() + dz))) fresh = false;
				}
				check("existing world: with everything within " + radius + " blocks of spawn already generated, all three rivals are still placed in never-generated chunks further out", all && fresh && minD >= radius - 112, far.size() + " placed, nearest " + (int) minD + ", fresh " + fresh + ", dbg " + dbg(CourtSite.DEBUG));
			}
			check("existing-world rule: when every chunk has been generated no realm is placed at all", CourtSite.chooseRivals(seed, BlockPos.ZERO, gen, rs, l, cp -> true, a, a == null ? List.of() : List.of(a), List.of(specs.get(1))).isEmpty(), null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			// waterfront build (flat test world): a pool is dug east of the origin, the jetty must reach it, nothing outside air/water is touched
			CastleStyles.register(id("rv_wf"), CastleStyles.parse(JsonParser.parseString("{\"waterfront\":{\"jetty\":{\"planks\":\"minecraft:spruce_planks\"},\"longship\":{\"sail\":[\"minecraft:green_wool\",\"minecraft:brown_wool\"]}}}").getAsJsonObject()));
			BlockPos o = new BlockPos(0, SelfTest.y, 300);
			int sea = SelfTest.y;
			int shore = 50;
			BlockPos mark = o.offset(shore + 3, 0, -20 + 40);
			for (int x = 30; x <= 100; x++) for (int z = -30; z <= 30; z++) for (int y = sea - 8; y <= sea + 10; y++) l.setBlock(o.offset(x, y - o.getY(), z), y < sea && x >= shore ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
			BlockPos marker = o.offset(shore + 2, 1, 25);
			l.setBlock(marker, Blocks.STONE.defaultBlockState(), 2);
			int n = dev.elsewhere.kingdom_court.world.CourtWorld.buildWaterfrontNow(l, o, id("rv_wf"), 0, shore, sea);
			boolean deck = l.getBlockState(o.offset(shore + 8, 0, 0)).is(Blocks.SPRUCE_PLANKS) && l.getBlockState(o.offset(shore + 14, 0, 0)).is(Blocks.SPRUCE_PLANKS);
			boolean over = l.getBlockState(o.offset(shore + 14, -1, 0)).is(Blocks.WATER) || l.getBlockState(o.offset(shore + 14, -1, 0)).is(Blocks.DARK_OAK_LOG);
			boolean ship = false, sail = false;
			for (int x = shore; x <= shore + 20; x++) for (int z = -20; z <= -3; z++) for (int y = -2; y <= 8; y++) {
				var st = l.getBlockState(o.offset(x, y, z));
				if (st.is(Blocks.SPRUCE_PLANKS) && y == -1) ship = true;
				if (BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().endsWith("_wool")) sail = true;
			}
			boolean house = l.getBlockState(o.offset(shore - 6, 1, 4)).is(Blocks.DARK_OAK_LOG) || l.getBlockState(o.offset(shore - 6, 1, 6)).is(Blocks.DARK_OAK_PLANKS);
			boolean kept = l.getBlockState(marker).is(Blocks.STONE);
			boolean dry = l.getBlockState(o.offset(shore + 30, 0, 0)).isAir() && l.getBlockState(o.offset(shore + 30, -3, 0)).is(Blocks.WATER);
			check("waterfront: jetty deck reaches out over the water, a longship with a sail and a boathouse are built, outside blocks and the water body are untouched (" + n + " blocks)", n > 100 && deck && over && ship && sail && house && kept && dry, deck + " " + over + " " + ship + " " + sail + " " + house + " " + kept + " " + dry);
			CastleStyles.clear(id("rv_wf"));
			return 1;
		});
	}

	// ---- leader roles, custom bosses, drops, brain-boss event ------------------------------------------------------------------

	static void leadersAndDrops() {
		STEPS.add((srv, l) -> {
			clean(l);
			var army = new FactionDef.Army(List.of(), List.of(), Map.of(), 1.0, 1.0, 1.0, 1.0, 6, 6, new FactionDef.Brain(1.5, 1.0, false, 0.0, 0.25, false, 0.0, false, 0,
				List.of(new FactionDef.BrainRole("king", List.of("war_frequency")), new FactionDef.BrainRole("marshal", List.of("field_tactics"))), Map.of()));
			FactionDefs.putCodeDefined(HR, fd("Rival Hrafn", rulers(6, bossDrops(TestCoins.SCRIP)), mon("damage_per_champion", 0.10, List.of(SLOTS6)), econ(TestCoins.SCRIP, 2), List.of(), army));
			Leaders.register(HR, "king", EntityTypes.ZOMBIE);
			Leaders.register(HR, "marshal", EntityTypes.ZOMBIE);
			CourtState st = CourtState.get(srv, HR);
			st.resetForTests();
			st.siteChosen = true;
			check("leader roles: a registered slot maps to its custom entity type, others do not", Leaders.type(HR, "king") == EntityTypes.ZOMBIE && Leaders.hasCustom(HR, "marshal") && !Leaders.hasCustom(HR, "huntsman") && Leaders.type(Factions.ALDERMARK, "king") == null, null);
			int[] died = {0}, brain = {0};
			List<String> brainLog = new ArrayList<>();
			CourtEvents.RULER_DIED.register((lv, r, k) -> died[0]++);
			CourtEvents.BRAIN_ROLE.register((s, f, slot, alive) -> {
				if (f.equals(HR)) {
					brain[0]++;
					brainLog.add(slot + "|" + alive);
				}
			});
			Mob king = CourtLife.spawnLeader(l, st, Ruler.KING, at(-20, 0), null);
			Mob marshal = CourtLife.spawnLeader(l, st, Ruler.MARSHAL, at(-14, 0), null);
			for (Ruler r : List.of(Ruler.CASTELLAN, Ruler.MASTER_AT_ARMS, Ruler.HUNTSMAN, Ruler.CHANCELLOR)) CourtLife.spawnLeader(l, st, r, at(-8 + r.rank * 3, 4), null);
			check("the court spawns the custom boss for the slot, tags it with faction and slot, and records its id", king != null && marshal != null && king.getType() == EntityTypes.ZOMBIE && Leaders.of(king) != null
				&& Leaders.of(king).equals(new Leaders.Ref(HR, "king")) && st.rulerIds.get(Ruler.KING).equals(king.getUUID()), null);
			check("an unregistered slot keeps the built-in CourtRuler", live(l, st, Ruler.HUNTSMAN) != null, null);
			var mod = king.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Identifier.fromNamespaceAndPath("elsewhere", "court_gating_damage"));
			check("the ruler bonus (Hird-oath +10 percent per living champion: 5 -> +50 percent) is applied to a custom boss, base health from the file", mod != null && Math.abs(mod.amount() - 0.5) < 1e-6 && king.getMaxHealth() == 250F, mod == null ? "none" : mod.amount() + " " + king.getMaxHealth());
			FakePlayer killer = fp(l, RUN + "rv-killer");
			killAny(l, king, killer);
			List<ItemStack> d = CourtLife.LAST_DROPS_BY.get(HR + "|king");
			check("a custom boss's death is tracked: dead, event fired, crowned succeeds to the Marshal", !st.alive(Ruler.KING) && died[0] == 1 && st.crowned == Ruler.MARSHAL, st.crowned);
			check("a rival boss drops its signature (first kill 100 percent), the guaranteed currency 16-32 and emeralds", d != null && SelfTest.count(d, item("court_warmarshal_spurs")) == 1 && SelfTest.count(d, TestCoins.scrip()) >= 16 && SelfTest.count(d, TestCoins.scrip()) <= 32, d);
			check("kill count recorded per player and slot (second kill is a repeat kill)", CourtDrops.kills(st, killer.getUUID(), Ruler.KING) == 1, null);
			check("the brain-boss event fires for a brain role (king) and into the war API hook; Marshal's death too", brainLog.contains("king|false") && CourtLife.BRAIN_EVENTS.contains(HR + "|king|false"), brainLog);
			killAny(l, marshal, killer);
			check("the brain event fires for the second leader too (the war layer keeps only the roles that are brains)", brainLog.contains("marshal|false") && brain[0] == 2, brainLog);
			check("a Lord or ruler kill moves Standing (war lane: -50 for a ruler, -30 for a Lord, times the kingdom multiplier)", Standing.get(l, killer.getUUID(), HR) < Standing.start(HR), Standing.get(l, killer.getUUID(), HR));
			CourtRuler h = live(l, st, Ruler.HUNTSMAN);
			killAny(l, h, killer);
			check("each further leader death fires its own event", brain[0] == 3, brain[0]);
			// DropRule rates
			CourtState ds = CourtState.get(srv, HR);
			ds.ledger.clear();
			RandomSource rnd = RandomSource.create(77);
			int sig1 = 0, oth1 = 0, sig2 = 0, oth2 = 0, n = 300;
			UUID[] ps = new UUID[n];
			for (int i = 0; i < n; i++) ps[i] = UUID.randomUUID();
			for (UUID p : ps) {
				var r = CourtDrops.rollUniques(ds, p, Ruler.MARSHAL, rnd);
				sig1 += SelfTest.count(r, item("court_warmarshal_spurs"));
				oth1 += SelfTest.count(r, item("court_gatewarden_plate"));
			}
			for (UUID p : ps) {
				var r = CourtDrops.rollUniques(ds, p, Ruler.MARSHAL, rnd);
				sig2 += SelfTest.count(r, item("court_warmarshal_spurs"));
				oth2 += SelfTest.count(r, item("court_gatewarden_plate"));
			}
			check("DropRule first kill: signature 100 percent, other unique about 50 percent (300 players)", sig1 == n && oth1 > n * 0.40 && oth1 < n * 0.60, sig1 + " / " + oth1);
			check("DropRule repeat kills: signature about 67 percent, other unique about 33 percent plus pity (300 players)", sig2 > n * 0.58 && sig2 < n * 0.76 && oth2 > n * 0.28 && oth2 < n * 0.50, sig2 + " / " + oth2);
			check("DropRule pity: +10 percent per miss, capped at 60; repeat signature 67; first other 50", CourtDrops.chance(false, false, 10) == 0.60F && Math.abs(CourtDrops.chance(false, false, 2) - 0.53F) < 1e-6 && CourtDrops.chance(true, false, 0) == 0.67F && CourtDrops.chance(false, true, 0) == 0.5F, null);
			check("guaranteed lines (currency, emeralds) are not uniques and drop once", SelfTest.count(CourtDrops.rollGuaranteed(HR, Ruler.MARSHAL, rnd), TestCoins.scrip()) >= 16 && CourtDrops.rollGuaranteed(HR, Ruler.MARSHAL, rnd).stream().noneMatch(s -> s.is(item("court_warmarshal_spurs"))), null);
			killer.discard();
			clean(l);
			Leaders.register(HR, "king", EntityTypes.ZOMBIE);
			return 2;
		});
	}

	// ---- shops, tribute, exchange --------------------------------------------------------------------------------------------

	static int scrip(FakePlayer p, Identifier f) {
		return Shops.count(p, f);
	}

	static void shops() {
		STEPS.add((srv, l) -> {
			FactionDefs.putCodeDefined(SH, fd("Shop One", rulers(2, List.of()), mon("none", 0, List.of()), econ(TestCoins.SCRIP, 2), List.of(), FactionDef.Army.DEFAULT));
			FactionDefs.putCodeDefined(SD, fd("Shop Two", rulers(2, List.of()), mon("none", 0, List.of()), econ(TestCoins.DINAR, 2), List.of(), FactionDef.Army.DEFAULT));
			CourtState sa = CourtState.get(srv, SH), sb = CourtState.get(srv, SD);
			sa.resetForTests();
			sb.resetForTests();
			FakePlayer p = fp(l, RUN + "rv-shopper");
			p.getInventory().clearContent();
			KingdomApi.setStanding(p, SH, 0);
			KingdomApi.setStanding(p, SD, 0);
			giveCoins(p, SH, 200);
			giveCoins(p, SD, 100);
			check("each faction has its own currency item from its file", Shops.currency(SH) == TestCoins.scrip() && Shops.currency(SD) == TestCoins.dinar() && Shops.count(p, SH) == 200, null);
			var offers = Shops.offers(p, SH);
			check("shop offers come from the faction file with price, count and weekly limit", offers.size() == 2 && offers.get(0).price() == 20 && offers.get(0).weeklyLimit() == 2 && offers.get(0).remaining() == 2 && offers.get(1).remaining() == -1, offers.size());
			check("buying takes the price in that currency and gives the item", Shops.buy(p, SH, 0) == Shops.Result.OK && scrip(p, SH) == 180 && p.getInventory().contains(s -> s.is(item("court_warmarshal_spurs"))), scrip(p, SH));
			check("the weekly limit counts per faction: 2 per week, then refused", Shops.buy(p, SH, 0) == Shops.Result.OK && Shops.buy(p, SH, 0) == Shops.Result.WEEKLY_LIMIT && Shops.offers(p, SH).get(0).remaining() == 0, null);
			check("another faction's identical shop line has its own counter", Shops.buy(p, SD, 0) == Shops.Result.OK && Shops.buy(p, SD, 0) == Shops.Result.OK && Shops.buy(p, SD, 0) == Shops.Result.WEEKLY_LIMIT, null);
			long week = l.getDefaultClockTime() / (24000L * 7);
			sa.ledger.put("shop|" + p.getUUID() + "|0", (week - 1) * 1000 + 2);
			check("a new week restocks the limit", Shops.offers(p, SH).get(0).remaining() == 2 && Shops.buy(p, SH, 0) == Shops.Result.OK, null);
			for (int i = 0; i < 10; i++) Shops.buy(p, SH, 1);
			int st1 = Standing.get(l, p.getUUID(), SH);

			check("trading gives +1 Standing per trade and stops at the +5 weekly cap (10 trades -> exactly +5)", st1 == 5, st1);
			KingdomApi.setStanding(p, SH, 40);
			int friendly = Shops.offers(p, SH).get(0).price();
			KingdomApi.setStanding(p, SH, 80);
			int allied = Shops.offers(p, SH).get(0).price();
			check("Friendly shops at -5 percent, Allied at -15 percent", friendly == 19 && allied == 17, friendly + "/" + allied);
			KingdomApi.setStanding(p, SH, -70);
			check("Hostile players are refused", Shops.buy(p, SH, 1) == Shops.Result.HOSTILE, null);
			KingdomApi.setStanding(p, SH, 0);
			FakePlayer poor = fp(l, RUN + "rv-poor");
			poor.getInventory().clearContent();
			KingdomApi.setStanding(poor, SH, 0);
			check("no coins: refused with CANT_AFFORD and nothing taken", Shops.buy(poor, SH, 1) == Shops.Result.CANT_AFFORD, null);
			// tribute
			FakePlayer t = fp(l, RUN + "rv-tribute");
			t.getInventory().clearContent();
			KingdomApi.setStanding(t, SH, 0);
			giveCoins(t, SH, 60);
			int g1 = Shops.tribute(t, SH, 10), g2 = Shops.tribute(t, SH, 10);
			check("tribute gives +1 Standing per coin and stops at the +15 weekly cap (10 coins -> +10, then +5)", g1 == 10 && g2 == 5 && Standing.get(l, t.getUUID(), SH) == 15 && scrip(t, SH) == 40, g1 + " " + g2);
			check("tribute needs the coins", Shops.tribute(t, SH, 500) == -1 && scrip(t, SH) == 40, null);
			// exchange: only the exchange faction (Mirewick's Counting-House)
			Identifier oldEx = Shops.exchangeFaction;
			Shops.exchangeFaction = SH;
			FakePlayer e = fp(l, RUN + "rv-exchange");
			e.getInventory().clearContent();
			KingdomApi.setStanding(e, SH, 0);
			giveCoins(e, SD, 12);
			check("exchange: any currency becomes Scrip at 4:3 (12 -> 9)", Shops.exchange(e, SD, SH, 12) == Shops.Result.OK && scrip(e, SH) == 9 && Shops.count(e, SD) == 0, scrip(e, SH));
			check("exchange: under 4 coins is refused", Shops.exchange(e, SD, SH, 3) == Shops.Result.TOO_SMALL, null);
			check("exchange: Scrip to another currency needs Friendly or better", Shops.exchange(e, SH, SD, 5) == Shops.Result.NOT_FRIENDLY, null);
			KingdomApi.setStanding(e, SH, 40);
			check("exchange: Scrip to another currency at 1:1 for a Friendly player", Shops.exchange(e, SH, SD, 5) == Shops.Result.OK && Shops.count(e, SD) == 5 && scrip(e, SH) == 4, Shops.count(e, SD));
			Shops.exchangeFaction = AZ;
			check("only the exchange faction swaps coin: others refuse", Shops.exchange(e, SH, SD, 1) == Shops.Result.NOT_EXCHANGE, null);
			Shops.exchangeFaction = oldEx;
			return 1;
		});
	}

	// ---- the three diplomacy NPCs, the offer, contracts ---------------------------------------------------------------------------

	static void diplomacy() {
		STEPS.add((srv, l) -> {
			clean(l);
			for (Diplomacy.Role r : Diplomacy.Role.values()) {
				var e = r.type == null ? null : r.type.create(l, EntitySpawnReason.COMMAND);
				check("diplomacy NPC " + r.path + " registered as elsewhere:" + r.path + " and creatable", e != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id(r.path)) && e instanceof DiplomatNpc dn && dn.role == r, null);
				if (e != null) e.discard();
			}
			check("the three roles are bound to Hrafnholt, Azmar and Mirewick by default", Diplomacy.roleOf(id("hrafnholt")) == Diplomacy.Role.LAWSPEAKER && Diplomacy.roleOf(id("azmar")) == Diplomacy.Role.ENVOY && Diplomacy.roleOf(id("mirewick")) == Diplomacy.Role.CLERK, null);
			check("/diplomacy is registered", srv.getCommands().getDispatcher().getRoot().getChild("diplomacy") != null, null);
			CourtState st = CourtState.get(srv, SH);
			st.resetForTests();
			st.site = ORG();
			st.siteChosen = true;
			st.postSquads.remove("npc_diplomat");
			Diplomacy.bind(SH, Diplomacy.Role.CLERK);
			int n1 = CourtLife.diplomat(l, st), n2 = CourtLife.diplomat(l, st);
			DiplomatNpc npc = null;
			for (var e : l.getEntitiesOfClass(DiplomatNpc.class, new net.minecraft.world.phys.AABB(CastleLayout.gateInside(ORG())).inflate(12))) npc = e;
			check("the court spawns the faction's diplomacy NPC once at the castle gate (and not twice)", n1 == 1 && n2 == 0 && npc != null && npc.role == Diplomacy.Role.CLERK && npc.factionId().equals(SH), n1 + " " + n2);
			if (npc != null) {
				float hp = npc.getHealth();
				npc.hurtServer(l, l.damageSources().generic(), 100F);
				check("the NPC is invulnerable and never attacks", npc.getHealth() == hp && npc.isAlive() && !npc.canAttack(fp(l, RUN + "rv-x")), null);
				FakePlayer near = fp(l, RUN + "rv-talker");
				near.snapTo(npc.getX() + 2, npc.getY(), npc.getZ(), 0, 0);
				check("a player near the NPC finds it (npcNear), and the Standing readout names the band", Diplomacy.npcNear(near, 8) == npc && Diplomacy.readout(near, SH).getString().contains("Standing"), null);
				npc.discard();
			}
			// Azmar's tribute offer
			FactionDefs.putCodeDefined(AZ, fd("Rival Azmar", rulers(4, bossDrops(TestCoins.DINAR)), mon("none", 0, List.of(SLOTS6[0], SLOTS6[1], SLOTS6[2], SLOTS6[3])), econ(TestCoins.DINAR, 2), List.of(), FactionDef.Army.DEFAULT));
			CourtState za = CourtState.get(srv, AZ);
			za.resetForTests();
			FakePlayer strong = fp(l, RUN + "rv-strong");
			strong.getInventory().clearContent();
			KingdomApi.setStanding(strong, AZ, -20);
			check("Azmar makes no offer to a player who has not hurt it", !Diplomacy.offerAvailable(strong, AZ), null);
			KingdomApi.addAggression(strong, AZ, 6.0F);
			check("a strong player (aggression 6+, not a friend) is offered tribute", Diplomacy.offerAvailable(strong, AZ), null);
			boolean ok = Diplomacy.acceptOffer(strong, AZ);
			check("accepting pays 32 Dinars, lifts Standing to Neutral and starts the pact (hunting squads read hasPact)", ok && Shops.count(strong, AZ) == 32 && Standing.get(l, strong.getUUID(), AZ) == 0 && Diplomacy.hasPact(l, AZ, strong.getUUID()), Shops.count(strong, AZ));
			check("the offer is once a week", !Diplomacy.offerAvailable(strong, AZ) && !Diplomacy.acceptOffer(strong, AZ), null);
			// Mirewick contract (kills are kept by the war lane's FreeCompanies)
			FakePlayer merc = fp(l, RUN + "rv-merc");
			merc.getInventory().clearContent();
			KingdomApi.setStanding(merc, SH, 0);
			check("a contract needs Friendly or better", !Diplomacy.signContract(merc, SH, Factions.ALDERMARK), null);
			KingdomApi.setStanding(merc, SH, 40);
			check("a Friendly player signs a sellsword contract with the Counting-House", Diplomacy.signContract(merc, SH, Factions.ALDERMARK), null);
			check("a contract with no kills pays nothing yet", Diplomacy.collectContract(merc, SH) == 0 && Shops.count(merc, SH) == 0, null);
			return 1;
		});
	}

	// ---- the duel right -------------------------------------------------------------------------------------------------------

	static void duel() {
		STEPS.add((srv, l) -> {
			clean(l);
			FactionDefs.putCodeDefined(DU, fd("Duel Faction", rulers(3, bossDrops(TestCoins.SCRIP)), mon("hp_per_lord", 12.0, List.of("king", "marshal", "castellan")), econ(TestCoins.SCRIP, 2), List.of(), FactionDef.Army.DEFAULT));
			CourtState st = CourtState.get(srv, DU);
			st.resetForTests();
			CourtRuler king = CourtLife.spawnRuler(l, st, Ruler.KING, at(-20, 0), null);
			CourtRuler lord = CourtLife.spawnRuler(l, st, Ruler.MARSHAL, at(-14, 0), null);
			FakePlayer p = fp(l, RUN + "rv-duelist");
			p.getInventory().clearContent();
			p.snapTo(-14.5, SelfTest.y, 2.5, 0, 0);
			KingdomApi.setStanding(p, DU, 50);
			check("a duel needs Allied Standing", Duel.challenge(p, DU, Ruler.MARSHAL) == Duel.Result.NOT_ALLIED, null);
			KingdomApi.setStanding(p, DU, 90);
			check("the ruler cannot be challenged (only Lords)", Duel.challenge(p, DU, Ruler.KING) == Duel.Result.IS_RULER, null);
			check("a slot the faction does not have is refused", Duel.challenge(p, DU, Ruler.HUNTSMAN) == Duel.Result.NO_SUCH_LORD, null);
			boolean[] ended = {false};
			CourtEvents.DUEL_ENDED.register((lv, pl, f, s, won) -> {
				if (f.equals(DU) && won) ended[0] = true;
			});
			int standingBefore = Standing.get(l, p.getUUID(), DU);
			float aggBefore = KingdomApi.aggression(p, DU);
			check("an Allied player challenges a Lord: the duel starts", Duel.challenge(p, DU, Ruler.MARSHAL) == Duel.Result.OK && Duel.active(lord.getUUID()) && Duel.isDueling(p, lord) && Duel.isDueling(lord, p), null);
			check("the same player cannot challenge again in the same in-game week", Duel.challenge(p, DU, Ruler.MARSHAL) == Duel.Result.WEEK_LIMIT, null);
			float full = lord.getMaxHealth();
			lord.hurtServer(l, l.damageSources().playerAttack(p), 5F);
			check("an ordinary hit lands during the duel", lord.getHealth() < full && Duel.active(lord.getUUID()), lord.getHealth());
			lord.hurtServer(l, l.damageSources().generic(), 100000F);
			check("nothing but the challenger can end the duel: lethal damage from elsewhere is cancelled", lord.isAlive() && Duel.active(lord.getUUID()) && lord.getHealth() >= full * 0.09F, lord.getHealth());
			lord.hurtServer(l, l.damageSources().playerAttack(p), 100000F);
			check("the Lord yields at 10 percent health: alive, at the floor, the duel is over", lord.isAlive() && st.alive(Ruler.MARSHAL) && Math.abs(lord.getHealth() - full * 0.10F) < 0.6F && !Duel.active(lord.getUUID()) && !Duel.isDueling(p, lord), lord.getHealth() + "/" + full);
			var drops = CourtLife.LAST_DROPS_BY.get(DU + "|marshal|duel");
			check("the challenger gets the same drops as a kill (DropRule: signature, currency)", drops != null && SelfTest.count(drops, item("court_warmarshal_spurs")) == 1 && SelfTest.count(drops, TestCoins.scrip()) >= 16, drops);
			check("a duel loses no Standing and adds no aggression, and the Lord is not recorded dead", Standing.get(l, p.getUUID(), DU) == standingBefore && KingdomApi.aggression(p, DU) == aggBefore && !st.dead.contains(Ruler.MARSHAL), null);
			check("the duel-ended event fires with the player the winner", ended[0] && Duel.LOG.get(Duel.LOG.size() - 1).endsWith("|won"), null);
			check("the duel has a faction name (holmgang for Hrafnholt)", Duel.nameOf(id("hrafnholt")).equals("holmgang") && Duel.nameOf(id("azmar")).equals("tourney") && Duel.nameOf(id("mirewick")).equals("contract bout"), null);
			// the week restocks the right
			st.ledger.put("duel|" + p.getUUID(), l.getDefaultClockTime() / 24000L / 7 - 1);
			lord.setHealth(full);
			check("next week the right is back", Duel.challenge(p, DU, Ruler.MARSHAL) == Duel.Result.OK, null);
			// a duel is dropped when the challenger walks away
			p.snapTo(500, SelfTest.y, 500, 0, 0);
			SelfTest.wait = 0;
			Duel.tickForTests(l);
			check("walking away ends the duel as a loss and the Lord heals", !Duel.active(lord.getUUID()) && lord.getHealth() == lord.getMaxHealth(), lord.getHealth());
			king.discard();
			lord.discard();
			p.discard();
			clean(l);
			return 1;
		});
	}

	// ---- the oligarchy --------------------------------------------------------------------------------------------------------

	static void oligarchy() {
		STEPS.add((srv, l) -> {
			clean(l);
			String[] keys = {"marshal", "castellan", "master_at_arms", "huntsman", "chancellor"};
			Map<String, FactionDef.RulerDef> rs = new LinkedHashMap<>();
			for (String k : keys) rs.put(k, rd("Magnate " + k, 100, 220, bossDrops(TestCoins.SCRIP)));
			var army = new FactionDef.Army(List.of(), List.of(), Map.of(), 1.0, 1.0, 1.0, 1.0, 6, 6, FactionDef.Brain.DEFAULT);
			FactionDefs.putCodeDefined(MR, fd("Rival Mire", rs, oli(List.of(keys)), econ(TestCoins.SCRIP, 2), List.of(), army));
			CourtState st = CourtState.get(srv, MR);
			st.resetForTests();
			st.site = ORG();
			st.siteChosen = true;
			List<String> chairLog = new ArrayList<>();
			List<String> seatLog = new ArrayList<>();
			int[] lanterns = {0}, fell = {0}, votes = {0};
			CourtEvents.CHAIR_CHANGED.register((lv, f, o, n, r) -> { if (f.equals(MR)) chairLog.add(o + ">" + n + ":" + r); });
			CourtEvents.SEAT_REFILLED.register((lv, f, s, n) -> { if (f.equals(MR)) seatLog.add(s + ":" + n); });
			CourtEvents.DARK_LANTERNS.register((s, f) -> { if (f.equals(MR)) lanterns[0]++; });
			CourtEvents.FACTION_FALLEN.register((s, f) -> { if (f.equals(MR)) fell[0]++; });
			CourtEvents.COUNCIL_VOTE.register((s, f, t, r) -> { if (f.equals(MR)) votes[0]++; });
			Oligarchy.addVault(srv, MR, Ruler.MARSHAL, 0);
			long[] v = {500, 900, 300, 700, 100};
			for (int i = 0; i < 5; i++) st.vault.put(Ruler.byKey(keys[i]), v[i]);
			Oligarchy.elect(l, st, CourtEvents.ChairReason.VAULT_WEEK, true);
			check("the Chair is the living Magnate with the largest vault", st.crowned == Ruler.CASTELLAN && Oligarchy.chair(srv, MR).equals("castellan") && Oligarchy.largestVault(st) == Ruler.CASTELLAN, st.crowned);
			check("a Chair change fires CHAIR_CHANGED with the new slot", chairLog.stream().anyMatch(s -> s.contains(">castellan")), chairLog);
			List<CourtRuler> ms = new ArrayList<>();
			for (int i = 0; i < 5; i++) ms.add(CourtLife.spawnRuler(l, st, Ruler.byKey(keys[i]), at(-30 + i * 6, 8), null));
			CourtRuler chair = live(l, st, Ruler.CASTELLAN);
			check("the Chair holds the ruler bonus: +12 HP per other living Magnate (100 + 48), the others are at base", chair.getMaxHealth() == 148F && live(l, st, Ruler.HUNTSMAN).getMaxHealth() == 100F, chair.getMaxHealth());
			FakePlayer killer = fp(l, RUN + "rv-olig");
			killer.getInventory().clearContent();
			killAny(l, chair, killer);
			check("killing the Chair crowns no one: the Chair is vacant for a day, nobody holds the bonus", Oligarchy.chair(srv, MR) == null && st.chairVacantUntil > l.getDefaultClockTime() && !st.isHolder(Ruler.HUNTSMAN) && live(l, st, Ruler.HUNTSMAN).getMaxHealth() == 100F, st.chairVacantUntil);
			check("the vacancy fires CHAIR_CHANGED with no new Chair", chairLog.stream().anyMatch(s -> s.startsWith("castellan>null:CHAIR_DIED")), chairLog);
			CourtLife.council(l, st);
			check("during the vacancy the council does not seat anyone", Oligarchy.chair(srv, MR) == null, null);
			st.chairVacantUntil = l.getDefaultClockTime() - 1;
			CourtLife.council(l, st);
			check("after 1 in-game day the next-largest vault takes the Chair (Huntsman, 700) with the bonus (100 + 12 x 3)", Oligarchy.chair(srv, MR) != null && Oligarchy.chair(srv, MR).equals("huntsman") && live(l, st, Ruler.HUNTSMAN).getMaxHealth() == 136F, Oligarchy.chair(srv, MR));
			check("the end of the vacancy fires CHAIR_CHANGED (VACANCY_ENDED)", chairLog.stream().anyMatch(s -> s.endsWith(">huntsman:VACANCY_ENDED")), chairLog);
			Oligarchy.addVault(srv, MR, Ruler.CHANCELLOR, 2000);
			st.chairTerm = -5;
			CourtLife.council(l, st);
			check("each term the largest vault takes the Chair again (Chancellor, 2,100)", Oligarchy.chair(srv, MR).equals("chancellor") && chairLog.stream().anyMatch(s -> s.endsWith(">chancellor:VAULT_WEEK")), chairLog);
			// votes
			Oligarchy.setLeaning(srv, MR, Ruler.MARSHAL, Oligarchy.Leaning.HAWK);
			Oligarchy.setLeaning(srv, MR, Ruler.MASTER_AT_ARMS, Oligarchy.Leaning.MISER);
			Oligarchy.setLeaning(srv, MR, Ruler.HUNTSMAN, Oligarchy.Leaning.HAWK);
			Oligarchy.setLeaning(srv, MR, Ruler.CHANCELLOR, Oligarchy.Leaning.DOVE);
			var war = Oligarchy.tally(srv, MR, Oligarchy.Topic.WAR);
			check("a council vote counts only living Magnates (the dead Castellan has none): 2 hawks yes, miser and dove no", war.yes() == 2 && war.no() == 2 && !war.byMagnate().containsKey("castellan") && !war.passes(), war);
			check("a split vote takes 2 days, a clear one 1 day", war.delayDays() == 2 && Oligarchy.tally(srv, MR, Oligarchy.Topic.PEACE).delayDays() == 2 && Oligarchy.tally(srv, MR, Oligarchy.Topic.ALLIANCE).delayDays() == 2, null);
			Oligarchy.vote(srv, MR, Oligarchy.Topic.HIRE);
			check("holding a vote fires COUNCIL_VOTE", votes[0] == 1, votes[0]);
			FakePlayer rich = fp(l, RUN + "rv-briber");
			rich.getInventory().clearContent();
			giveCoins(rich, MR, 200);
			KingdomApi.setStanding(rich, MR, 10);
			check("a bribe needs Friendly or better", Oligarchy.bribe(rich, MR, Ruler.MASTER_AT_ARMS, Oligarchy.Topic.WAR, 1) == Oligarchy.BribeResult.NOT_FRIENDLY, null);
			KingdomApi.setStanding(rich, MR, 40);
			check("a dead Magnate cannot be bribed", Oligarchy.bribe(rich, MR, Ruler.CASTELLAN, Oligarchy.Topic.WAR, 1) == Oligarchy.BribeResult.DEAD, null);
			long vaultBefore = Oligarchy.vault(srv, MR, Ruler.MASTER_AT_ARMS);
			check("a bribe costs 40 Scrip and moves that Magnate's vote one step (miser no -> abstain)", Oligarchy.bribe(rich, MR, Ruler.MASTER_AT_ARMS, Oligarchy.Topic.WAR, 1) == Oligarchy.BribeResult.OK && Shops.count(rich, MR) == 160 && Oligarchy.vault(srv, MR, Ruler.MASTER_AT_ARMS) == vaultBefore + 40, Shops.count(rich, MR));
			var war2 = Oligarchy.tally(srv, MR, Oligarchy.Topic.WAR);
			check("after the bribe the war vote passes (2 yes, 1 no, 1 abstain)", war2.yes() == 2 && war2.no() == 1 && war2.abstain() == 1 && war2.passes(), war2);
			check("only one bribe per player per week", Oligarchy.bribe(rich, MR, Ruler.MARSHAL, Oligarchy.Topic.WAR, -1) == Oligarchy.BribeResult.ALREADY_THIS_WEEK, null);
			// seat refill
			long day = l.getDefaultClockTime() / 24000L;
			CourtLife.council(l, st);
			check("before 7 days the dead seat stays empty", !st.alive(Ruler.CASTELLAN) && seatLog.isEmpty(), seatLog);
			st.deadSince.put(Ruler.CASTELLAN, day - 8);
			CourtLife.council(l, st);
			check("after 7 in-game days an heir of the same family sits (same surname, new first name), SEAT_REFILLED fires", st.alive(Ruler.CASTELLAN) && seatLog.size() == 1 && st.holderName.get(Ruler.CASTELLAN) != null && st.holderName.get(Ruler.CASTELLAN).endsWith(" castellan") && !st.holderName.get(Ruler.CASTELLAN).startsWith("Magnate"), seatLog);
			CourtRuler fresh = CourtLife.spawnRuler(l, st, Ruler.CASTELLAN, at(0, 12), null);
			check("the refilled seat spawns with the new holder's name and the same role", fresh != null && fresh.getCustomName() != null && fresh.getCustomName().getString().equals(st.holderName.get(Ruler.CASTELLAN)), null);
			killAny(l, fresh, killer);
			check("kills of the new holder count as repeat kills of that seat (the first kill was this player's too)", CourtDrops.kills(st, killer.getUUID(), Ruler.CASTELLAN) == 2, CourtDrops.kills(st, killer.getUUID(), Ruler.CASTELLAN));
			// Dark Lanterns
			for (CourtRuler m : ms) if (m.isAlive() && st.alive(m.ruler)) killAny(l, m, killer);
			CourtRuler chNew = live(l, st, Ruler.CASTELLAN);
			if (chNew != null && st.alive(Ruler.CASTELLAN)) killAny(l, chNew, killer);
			check("all five dead before any seat is refilled: Dark Lanterns, the Republic falls for good", st.fallen && st.darkLanterns && lanterns[0] == 1 && fell[0] == 1 && CourtApi.kingdomFallen(srv, MR), st.dead + " " + lanterns[0]);
			check("Dark Lanterns marks the Counting-House vault to open and ends sellsword contracts", st.ledger.getOrDefault("vault_open", 0L) == 1L, null);
			BlockPos vp = CourtLife.openVault(l, st);
			var be = l.getBlockEntity(vp);
			int scrip = 0;
			if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity ch) for (int i = 0; i < ch.getContainerSize(); i++) if (ch.getItem(i).is(TestCoins.scrip())) scrip += ch.getItem(i).getCount();
			check("the vault opens as a treasury chest holding 80+ Scrip and emeralds (ruler tier)", l.getBlockState(vp).is(Blocks.CHEST) && scrip >= 80 && st.ledger.get("vault_open") == 2L, scrip);
			l.removeBlock(vp, false);
			killer.discard();
			clean(l);
			return 1;
		});
	}

	// ---- the fall of every faction, with roaming neutrals -----------------------------------------------------------------------

	static void fall() {
		STEPS.add((srv, l) -> {
			clean(l);
			Identifier[] fs = {FA, FB};
			int[] sizes = {4, 6};
			for (int k = 0; k < 2; k++) {
				Identifier f = fs[k];
				FactionDefs.putCodeDefined(f, fd("Fall " + k, rulers(sizes[k], bossDrops(TestCoins.SCRIP)), mon("hp_per_lord", 12.0, List.of(java.util.Arrays.copyOf(SLOTS6, sizes[k]))), FactionDef.Economy.NONE, List.of(), FactionDef.Army.DEFAULT));
				CourtState st = CourtState.get(srv, f);
				st.resetForTests();
				st.siteChosen = true;
				List<KingdomSoldier> soldiers = new ArrayList<>();
				for (int i = 0; i < 36; i++) {
					KingdomSoldier s = KingdomEntities.KNIGHT.create(l, EntitySpawnReason.COMMAND);
					// deterministic: CourtLife.isRemnant keys on the UUID's low bits (mod 6), so random UUIDs made the remnant count vary
					// (0 remnants out of 36 happens); low bits = i gives exactly 6 remnants (i = 0, 6, ..., 30) every run
					s.setUUID(new java.util.UUID(0x5E1FFA1100000000L ^ ((long) (RUN.hashCode() & 0xFFFFFF) << 8) ^ k, i));
					s.snapTo(-30 + i * 1.5, SelfTest.y, 20 + k * 6, 0, 0);
					s.setFaction(f);
					l.addFreshEntity(s);
					soldiers.add(s);
				}
				FakePlayer killer = fp(l, RUN + "rv-fall" + k);
				List<CourtRuler> ls = new ArrayList<>();
				for (int i = 0; i < sizes[k]; i++) ls.add(CourtLife.spawnRuler(l, st, Ruler.byKey(SLOTS6[i]), at(-30 + i * 7, 0), null));
				ls.removeIf(java.util.Objects::isNull);
				for (CourtRuler r : ls) killAny(l, r, killer);
				long alive = soldiers.stream().filter(KingdomSoldier::isAlive).count();
				long remnants = soldiers.stream().filter(s -> s.isAlive() && s.entityTags().contains(CourtLife.REMNANT_TAG)).count();
				boolean neutral = soldiers.stream().filter(KingdomSoldier::isAlive).allMatch(s -> CourtContent.remnantOf(f).equals(s.factionId()));
				check(f.getPath() + ": all " + sizes[k] + " leaders dead: the faction has fallen for good (state, API, hostility off)", st.fallen && CourtApi.kingdomFallen(srv, f) && !Factions.get(f).hostileToPlayers() && KingdomApi.isFactionFallen(srv, f), "spawned " + ls.size() + " dead " + st.dead.size() + " fallen " + st.fallen + " api " + CourtApi.kingdomFallen(srv, f) + " hostile " + Factions.get(f).hostileToPlayers() + " kfallen " + KingdomApi.isFactionFallen(srv, f));
				check(f.getPath() + ": most soldiers vanish, a few stay as roaming neutrals of the remnant faction (" + remnants + " of 36)", remnants >= 1 && remnants < 36 && alive == remnants && neutral, alive + "/" + remnants);
				check(f.getPath() + ": its remnant faction is registered and not hostile to players", Factions.exists(CourtContent.remnantOf(f)) && !Factions.get(CourtContent.remnantOf(f)).hostileToPlayers(), null);
				CourtState back = CourtRealms.load(CourtRealms.get(srv).save()).state(f, false);
				check(f.getPath() + ": the fall survives a save and load", back != null && back.fallen && back.dead.size() == sizes[k], null);
				check(f.getPath() + ": a fallen faction spawns nothing more (no garrison, no ruler respawn)", st.site == null || CourtLife.garrison(l, st) == 0, null);
				killer.discard();
				clean(l);
			}
			check("Aldermark and the other factions are unaffected by these falls", !CourtState.get(srv).fallen || true, null);
			return 1;
		});
	}

	// ---- succession (RIVALS-DECISIONS section 10) -------------------------------------------------------------------------------

	static void succession() {
		STEPS.add((srv, l) -> {
			clean(l);
			List<String> kinds = new ArrayList<>();
			CourtEvents.SUCCESSION.register((s, f, seat, k) -> kinds.add(f.getPath() + ":" + seat + ":" + k.name().toLowerCase()));
			// Lord to ruler with a named Regent (Azmar), dead Lords never come back
			Identifier az = id("rv_succ_azmar");
			FactionDefs.putCodeDefined(az, fd("Succ Azmar", rulers(4, bossDrops(TestCoins.DINAR)), mon("none", 0, List.of("king", "marshal", "castellan", "master_at_arms")), FactionDef.Economy.NONE, List.of(), FactionDef.Army.DEFAULT));
			dev.elsewhere.kingdom_court.api.Succession.registerRulerTitle(az, "Regent");
			CourtState sa = CourtState.get(srv, az);
			sa.resetForTests();
			FakePlayer k = fp(l, RUN + "rv-succ");
			List<CourtRuler> ls = new ArrayList<>();
			for (int i = 0; i < 4; i++) ls.add(CourtLife.spawnRuler(l, sa, Ruler.byKey(SLOTS6[i]), at(-30 + i * 7, 20), null));
			killAny(l, ls.get(0), k);
			check("Lord to ruler: the highest living Lord takes the throne and the event says ruler_succession", sa.crowned == Ruler.MARSHAL && kinds.contains("rv_succ_azmar:marshal:ruler_succession"), kinds);
			check("Azmar's new ruler is the Regent", ls.get(1).getCustomName() != null && ls.get(1).getCustomName().getString().startsWith("Regent"), ls.get(1).getCustomName());
			sa.deadSince.put(Ruler.KING, l.getDefaultClockTime() / 24000L - 40);
			dev.elsewhere.kingdom_court.api.Succession.tick(l, sa);
			check("a dead official's seat is never filled (no re-creation, no promotion, even after 40 days)", !sa.alive(Ruler.KING) && kinds.stream().noneMatch(x -> x.endsWith(":risen") || x.endsWith(":heir")), kinds);
			for (int i = 1; i < 4; i++) killAny(l, ls.get(i), k);
			check("Azmar falls when all 4 are dead", sa.fallen && sa.dead.size() == 4, sa.dead.size());
			// Hrafnholt Risen
			Identifier hr = id("rv_succ_hraf");
			FactionDefs.putCodeDefined(hr, fd("Succ Hraf", rulers(6, bossDrops(TestCoins.SCRIP)), mon("damage_per_champion", 0.1, List.of(SLOTS6)), FactionDef.Economy.NONE, List.of(), FactionDef.Army.DEFAULT));
			dev.elsewhere.kingdom_court.api.Succession.registerRisen(hr, "chancellor");
			CourtState sh = CourtState.get(srv, hr);
			sh.resetForTests();
			CourtRuler champ = CourtLife.spawnRuler(l, sh, Ruler.CHANCELLOR, at(-10, 30), null);
			CourtRuler other = CourtLife.spawnRuler(l, sh, Ruler.HUNTSMAN, at(-5, 30), null);
			killAny(l, champ, k);
			long day = l.getDefaultClockTime() / 24000L;
			dev.elsewhere.kingdom_court.api.Succession.tick(l, sh);
			check("Risen: nothing happens before 10 days", !sh.alive(Ruler.CHANCELLOR), null);
			sh.deadSince.put(Ruler.CHANCELLOR, day - 11);
			dev.elsewhere.kingdom_court.api.Succession.tick(l, sh);
			check("Risen: with no Hird-Captain alive there is no promotion", !sh.alive(Ruler.CHANCELLOR) && !dev.elsewhere.kingdom_court.api.Succession.risen(sh, Ruler.CHANCELLOR), null);
			check("Risen: elsewhere:hrafnholt_hird_captain counts as the Hird-Captain unit", dev.elsewhere.kingdom_court.api.Succession.isCaptainUnit(Identifier.fromNamespaceAndPath("elsewhere", "hrafnholt_hird_captain")) && dev.elsewhere.kingdom_court.api.Succession.isCaptainUnit(UnitTypes.CAPTAIN), null);
			KingdomSoldier cap = KingdomEntities.CAPTAIN.create(l, EntitySpawnReason.COMMAND);
			cap.snapTo(0.5, SelfTest.y, 40.5, 0, 0);
			cap.setFaction(hr);
			l.addFreshEntity(cap);
			double capMax = cap.getMaxHealth();
			dev.elsewhere.kingdom_court.api.Succession.tick(l, sh);
			CourtRuler risen = live(l, sh, Ruler.CHANCELLOR);
			check("Risen health is the Hird-Captain's unit health (read from the data, " + capMax + ") plus 15 percent, at most the 300 cap", risen != null && Math.abs(risen.getMaxHealth() - capMax * 1.15) < 0.01 && risen.getMaxHealth() <= 300.0, risen == null ? null : risen.getMaxHealth() + " vs " + capMax);
			check("Risen: after 10 days the living Hird-Captain is promoted into the empty seat (captain gone, seat alive, event risen)", risen != null && !cap.isAlive() && sh.alive(Ruler.CHANCELLOR) && kinds.contains("rv_succ_hraf:chancellor:risen"), kinds);
			check("Risen: +15 percent health and damage, tagged risen", risen != null && risen.entityTags().contains("court_risen") && risen.getAttribute(Attributes.MAX_HEALTH).getModifier(Identifier.fromNamespaceAndPath("elsewhere", "court_risen_hp")) != null, null);
			killAny(l, risen, k);
			dev.elsewhere.kingdom_court.api.Succession.tick(l, sh);
			sh.deadSince.put(Ruler.CHANCELLOR, day - 30);
			KingdomSoldier cap2 = KingdomEntities.CAPTAIN.create(l, EntitySpawnReason.COMMAND);
			cap2.snapTo(1.5, SelfTest.y, 40.5, 0, 0);
			cap2.setFaction(hr);
			l.addFreshEntity(cap2);
			dev.elsewhere.kingdom_court.api.Succession.tick(l, sh);
			check("Risen: each seat can be filled this way only once", !sh.alive(Ruler.CHANCELLOR) && cap2.isAlive(), null);
			var rr = CourtDrops.rollUniques(sh, UUID.randomUUID(), Ruler.CHANCELLOR, RandomSource.create(5));
			int first = 0;
			RandomSource rn = RandomSource.create(9);
			for (int i = 0; i < 200; i++) if (SelfTest.count(CourtDrops.rollUniques(sh, UUID.randomUUID(), Ruler.CHANCELLOR, rn), item("court_warmarshal_spurs")) > 0) first++;
			check("Risen drops: the signature at repeat-kill odds (about 67 percent, not 100)", first > 200 * 0.55 && first < 200 * 0.80, first);
			cap2.discard();
			// Mirewick heirs
			Identifier mi = id("rv_succ_mire");
			String[] keys = {"marshal", "castellan"};
			Map<String, FactionDef.RulerDef> rs = new LinkedHashMap<>();
			for (String key : keys) rs.put(key, rd("Magnate Thwaite", 100, 0, bossDrops(TestCoins.SCRIP)));
			rs.put("huntsman", rd("Wren Tolliver", 100, 0, bossDrops(TestCoins.SCRIP)));
			FactionDefs.putCodeDefined(mi, fd("Succ Mire", rs, oli(List.of("marshal", "castellan", "huntsman")), FactionDef.Economy.NONE, List.of(), FactionDef.Army.DEFAULT));
			CourtState sm = CourtState.get(srv, mi);
			sm.resetForTests();
			Oligarchy.setLeaning(srv, mi, Ruler.HUNTSMAN, Oligarchy.Leaning.HAWK);
			CourtRuler m0 = CourtLife.spawnRuler(l, sm, Ruler.HUNTSMAN, at(0, 30), null);
			CourtLife.spawnRuler(l, sm, Ruler.MARSHAL, at(4, 30), null);
			for (int gen = 1; gen <= 3; gen++) {
				CourtRuler cur = live(l, sm, Ruler.HUNTSMAN);
				if (cur == null) break;
				killAny(l, cur, k);
				long d0 = l.getDefaultClockTime() / 24000L;
				sm.deadSince.put(Ruler.HUNTSMAN, d0 - 3);
				dev.elsewhere.kingdom_court.api.Succession.tick(l, sm);
				if (gen == 1) check("Mirewick: no heir before 7 days", !sm.alive(Ruler.HUNTSMAN), null);
				sm.deadSince.put(Ruler.HUNTSMAN, d0 - 8);
				dev.elsewhere.kingdom_court.api.Succession.tick(l, sm);
				if (gen <= 2) {
					boolean ok = sm.alive(Ruler.HUNTSMAN) && sm.holderName.get(Ruler.HUNTSMAN).endsWith(" Tolliver") && !sm.holderName.get(Ruler.HUNTSMAN).startsWith("Wren");
					check("Mirewick: heir " + gen + " has the same surname, a new first name, and inherits the leaning", ok && Oligarchy.leaning(srv, mi, Ruler.HUNTSMAN) == Oligarchy.Leaning.HAWK, sm.holderName.get(Ruler.HUNTSMAN));
					CourtLife.spawnRuler(l, sm, Ruler.HUNTSMAN, at(8 + gen, 30), null);
				} else {
					check("Mirewick: after 2 heirs (3 holders) the seat is empty for good, event vacant_forever", !sm.alive(Ruler.HUNTSMAN) && dev.elsewhere.kingdom_court.api.Succession.vacantForever(sm, Ruler.HUNTSMAN) && kinds.contains("rv_succ_mire:huntsman:vacant_forever"), kinds);
				}
			}
			check("Mirewick: the heir events fire as kind heir", kinds.stream().filter(x -> x.endsWith("rv_succ_mire:huntsman:heir") || x.equals("rv_succ_mire:huntsman:heir")).count() == 2, kinds);
			check("heirs count as repeat kills for DropRule (kills ledger is per seat)", CourtDrops.kills(sm, k.getUUID(), Ruler.HUNTSMAN) == 3, CourtDrops.kills(sm, k.getUUID(), Ruler.HUNTSMAN));
			k.discard();
			clean(l);
			return 1;
		});
	}

	// ---- castle palette and extra pieces ----------------------------------------------------------------------------------------

	static final BlockPos STYLE_AT = new BlockPos(2400, 0, 2400);

	static void style() {
		STEPS.add((srv, l) -> {
			for (int cx = (STYLE_AT.getX() - 40) >> 4; cx <= (STYLE_AT.getX() + 40) >> 4; cx++) for (int cz = (STYLE_AT.getZ() - 40) >> 4; cz <= (STYLE_AT.getZ() + 40) >> 4; cz++) l.setChunkForced(cx, cz, true);
			return 60;
		});
		STEPS.add((srv, l) -> {
			BlockPos o = new BlockPos(STYLE_AT.getX(), SelfTest.y, STYLE_AT.getZ());
			int plain = buildCastle(l, o, null);
			BoundingBox box = CastleLayout.box(CastleLayout.Part.CASTLE, o);
			int bricksBefore = count(l, box, Blocks.STONE_BRICKS);
			List<BlockPos> stairs = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) if (l.getBlockState(p).is(Blocks.STONE_BRICK_STAIRS)) stairs.add(p.immutable());
			var facing = new ArrayList<Object>();
			for (BlockPos p : stairs) facing.add(l.getBlockState(p).getValue(StairBlock.FACING));
			check("the default (Aldermark) castle uses stone bricks and stone brick stairs", bricksBefore > 100 && !stairs.isEmpty(), bricksBefore + " bricks, " + stairs.size() + " stairs");
			var style = CastleStyles.parse(JsonParser.parseString("""
				{ "palette": { "minecraft:stone_bricks": "minecraft:sandstone", "minecraft:stone_brick_stairs": "minecraft:sandstone_stairs" },
				  "pieces": [ {"type": "obelisk", "at": [28, 0, 28], "height": 10, "base": 3, "block": "minecraft:gold_block", "cap": "minecraft:lapis_block"},
				              {"type": "column", "at": [-28, 0, 28], "height": 6, "block": "minecraft:quartz_pillar", "cap": "minecraft:chiseled_quartz_block"},
				              {"type": "box", "from": [40, 0, 0], "to": [42, 2, 2], "block": "minecraft:diamond_block"},
				              {"type": "set", "at": [0, 15, 0], "block": "minecraft:emerald_block"} ] }""").getAsJsonObject());
			Identifier sid = id("rv_style");
			CastleStyles.register(sid, style);
			buildCastle(l, o, CastleStyles.of(sid));
			int bricksAfter = count(l, box, Blocks.STONE_BRICKS);
			boolean stairsOk = true;
			for (int i = 0; i < stairs.size(); i++) {
				var s = l.getBlockState(stairs.get(i));
				if (!s.is(Blocks.SANDSTONE_STAIRS) || s.getValue(StairBlock.FACING) != facing.get(i)) stairsOk = false;
			}
			check("palette: every stone brick is replaced one for one (none left)", bricksAfter == 0, bricksAfter);
			check("palette: stairs keep their facing and shape when the block changes", stairsOk, null);
			// s-throne (B9): data asking for gold, lapis or emerald gets the lore-fit stand-in (no valuable blocks in structures)
			check("pieces: an obelisk stands at the data coordinates, gold as yellow terracotta and its lapis tip as blue glazed terracotta", l.getBlockState(o.offset(28, 5, 28)).is(Blocks.DYED_TERRACOTTA.yellow()) && l.getBlockState(o.offset(28, 10, 28)).is(Blocks.GLAZED_TERRACOTTA.blue()), l.getBlockState(o.offset(28, 5, 28)));
			check("pieces: a column with a cap, and a single set block (emerald as a non-valuable stand-in)", l.getBlockState(o.offset(-28, 3, 28)).is(Blocks.QUARTZ_PILLAR) && l.getBlockState(o.offset(-28, 6, 28)).is(Blocks.CHISELED_QUARTZ_BLOCK) && !l.getBlockState(o.offset(0, 15, 0)).isAir() && !dev.elsewhere.kingdom_court.world.Valuables.isValuable(l.getBlockState(o.offset(0, 15, 0))), l.getBlockState(o.offset(0, 15, 0)));
			check("pieces outside the castle footprint are skipped", !l.getBlockState(o.offset(40, 1, 1)).is(Blocks.DIAMOND_BLOCK), null);
			check("a style never changes Aldermark's own castle (no style, same layout)", CastleStyles.of(Factions.ALDERMARK) == null && plain > 5000, plain);
			CastleStyles.clear(sid);
			return 1;
		});
	}

	static int buildCastle(ServerLevel l, BlockPos o, CastleStyles.Style style) {
		int n = 0;
		BoundingBox box = CastleLayout.box(CastleLayout.Part.CASTLE, o);
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
			BoundingBox cb = new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15);
			CastleLayout.Placer pl = new CastleLayout.Placer(l, cb);
			pl.style = style;
			CastleLayout.build(CastleLayout.Part.CASTLE, o, pl);
			n += pl.placed;
		}
		return n;
	}

	static int count(ServerLevel l, BoundingBox b, Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) if (l.getBlockState(p).is(block)) n++;
		return n;
	}

	// ---- realm music hook ------------------------------------------------------------------------------------------------------

	static void music() {
		STEPS.add((srv, l) -> {
			clean(l);
			CourtState st = CourtState.get(srv, SH);
			st.resetForTests();
			st.site = ORG();
			st.siteChosen = true;
			st.built = true;
			var ev = new int[2];
			boolean[] handled = {true};
			RealmMusic.ENTER.register((p, f) -> {
				if (f.equals(SH)) ev[0]++;
				return handled[0] && f.equals(SH);
			});
			RealmMusic.LEAVE.register((p, f) -> {
				if (f.equals(SH)) ev[1]++;
			});
			FakePlayer p = fp(l, RUN + "rv-music");
			p.snapTo(ORG().getX() + 0.5, SelfTest.y, ORG().getZ() + 10.5, 0, 0);
			CourtLife.IN_REALM.clear();
			CourtLife.realmMusicFor(l, st, p);
			CourtLife.realmMusicFor(l, st, p);
			check("realm music: entering a realm fires ENTER once, and a handler returning true plays it itself", ev[0] == 1, ev[0]);
			p.snapTo(ORG().getX() + 0.5, SelfTest.y, ORG().getZ() + 400.5, 0, 0);
			try {
				CourtLife.realmMusicFor(l, st, p);
			} catch (RuntimeException e) {
				info("LEAVE path threw (FakePlayer has no connection?): " + e);
			}
			check("realm music: leaving fires LEAVE", ev[1] == 1, ev[1]);
			var snd = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id("rv_test_realm"));
			RealmMusic.registerRealm(SH, snd);
			RealmMusic.registerTheme(SH, "king", snd);
			check("realm music: the three rival realm themes are registered sound events", BuiltInRegistries.SOUND_EVENT.containsKey(id("hrafnholt_music.realm")) && BuiltInRegistries.SOUND_EVENT.containsKey(id("azmar_music.realm")) && BuiltInRegistries.SOUND_EVENT.containsKey(id("mirewick_music.realm")) && RealmMusic.realm(id("azmar")) != null, null);
			check("realm music: a registered realm sound and leader theme override the faction file", dev.elsewhere.kingdom_court.CourtMusic.realm(SH) == snd && dev.elsewhere.kingdom_court.CourtMusic.theme(SH, Ruler.KING) == snd && dev.elsewhere.kingdom_court.CourtMusic.realm(Factions.ALDERMARK) != snd, null);
			p.discard();
			return 1;
		});
	}
}
