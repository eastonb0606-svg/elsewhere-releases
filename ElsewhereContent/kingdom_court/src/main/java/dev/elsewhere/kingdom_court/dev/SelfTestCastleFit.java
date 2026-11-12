package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.world.CastleKit;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import dev.elsewhere.kingdom_court.world.RealmFit;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * z-castle (3.0.0): the fv 2 Mirewick quay canal and the Azmar tower batter on the REAL generator ground of several seeds. For each seed a
 * Mirewick shore site (quay dry, water in front of it) and an Azmar hillside site are found with the overworld noise generator; the terrain
 * there (generator ground, sea-level water) is copied block for block into this test world far from the court realm, then the castle, the
 * curtain and the kit are built through the fv 2 path (Placer.ground = RealmFit.HeightField, chunk by chunk like worldgen), and the blocks
 * are checked: canal water only inside its own bed and only at sea level, every canal water block contained by its walls or canal water, no
 * natural land above the water level removed, every tower and curtain batter block standing on solid ground (no air or fluid under it).
 */
final class SelfTestCastleFit {
	private SelfTestCastleFit() {}

	static final String MIREWICK = "{\"palette\":{\"minecraft:stone_bricks\":\"minecraft:quartz_bricks\",\"minecraft:stone_brick_stairs\":\"minecraft:quartz_stairs\",\"minecraft:stone_brick_slab\":\"minecraft:quartz_slab\",\"minecraft:stone_brick_wall\":\"minecraft:diorite_wall\",\"minecraft:cobblestone\":\"minecraft:calcite\",\"minecraft:stone\":\"minecraft:smooth_stone\",\"minecraft:mossy_stone_bricks\":\"minecraft:chiseled_quartz_block\",\"minecraft:cracked_stone_bricks\":\"minecraft:smooth_quartz\",\"minecraft:chiseled_stone_bricks\":\"minecraft:chiseled_quartz_block\",\"minecraft:andesite\":\"minecraft:diorite\",\"minecraft:polished_andesite\":\"minecraft:polished_diorite\",\"minecraft:spruce_planks\":\"minecraft:mangrove_planks\",\"minecraft:spruce_log\":\"minecraft:mangrove_log\",\"minecraft:spruce_slab\":\"minecraft:mangrove_slab\",\"minecraft:spruce_stairs\":\"minecraft:mangrove_stairs\",\"minecraft:spruce_fence\":\"minecraft:mangrove_fence\",\"minecraft:spruce_trapdoor\":\"minecraft:mangrove_trapdoor\",\"minecraft:spruce_fence_gate\":\"minecraft:mangrove_fence_gate\",\"minecraft:oak_planks\":\"minecraft:crimson_planks\",\"minecraft:oak_log\":\"minecraft:crimson_stem\",\"minecraft:oak_fence\":\"minecraft:mangrove_fence\",\"minecraft:oak_stairs\":\"minecraft:crimson_stairs\",\"minecraft:dark_oak_planks\":\"minecraft:red_nether_bricks\",\"minecraft:dark_oak_stairs\":\"minecraft:red_nether_brick_stairs\",\"minecraft:dark_oak_slab\":\"minecraft:red_nether_brick_slab\",\"minecraft:dark_oak_fence\":\"minecraft:mangrove_fence\",\"minecraft:deepslate_bricks\":\"minecraft:purple_terracotta\",\"minecraft:deepslate_tiles\":\"minecraft:purple_terracotta\",\"minecraft:polished_deepslate\":\"minecraft:purple_terracotta\",\"minecraft:polished_blackstone_bricks\":\"minecraft:purple_terracotta\",\"minecraft:gold_block\":\"minecraft:yellow_terracotta\",\"minecraft:blue_wool\":\"minecraft:purple_wool\",\"minecraft:blue_stained_glass_pane\":\"minecraft:purple_stained_glass_pane\",\"minecraft:dirt_path\":\"minecraft:smooth_stone\"},\"pieces\":[{\"type\":\"box\",\"from\":[-16,0,31],\"to\":[16,0,33],\"block\":\"minecraft:smooth_stone\",\"hollow\":false},{\"type\":\"box\",\"from\":[-16,1,34],\"to\":[16,1,34],\"block\":\"minecraft:quartz_bricks\",\"hollow\":false},{\"type\":\"box\",\"from\":[-16,2,34],\"to\":[16,2,34],\"block\":\"minecraft:quartz_slab\",\"hollow\":false},{\"type\":\"column\",\"at\":[-14,1,32],\"height\":3,\"block\":\"minecraft:mangrove_fence\",\"cap\":\"minecraft:lantern\"},{\"type\":\"box\",\"from\":[-15,1,34],\"to\":[-13,1,34],\"block\":\"minecraft:yellow_terracotta\",\"hollow\":false},{\"type\":\"column\",\"at\":[-7,1,32],\"height\":3,\"block\":\"minecraft:mangrove_fence\",\"cap\":\"minecraft:lantern\"},{\"type\":\"box\",\"from\":[-8,1,34],\"to\":[-6,1,34],\"block\":\"minecraft:yellow_terracotta\",\"hollow\":false},{\"type\":\"column\",\"at\":[0,1,32],\"height\":3,\"block\":\"minecraft:mangrove_fence\",\"cap\":\"minecraft:lantern\"},{\"type\":\"box\",\"from\":[-1,1,34],\"to\":[1,1,34],\"block\":\"minecraft:yellow_terracotta\",\"hollow\":false},{\"type\":\"column\",\"at\":[7,1,32],\"height\":3,\"block\":\"minecraft:mangrove_fence\",\"cap\":\"minecraft:lantern\"},{\"type\":\"box\",\"from\":[6,1,34],\"to\":[8,1,34],\"block\":\"minecraft:yellow_terracotta\",\"hollow\":false},{\"type\":\"column\",\"at\":[14,1,32],\"height\":3,\"block\":\"minecraft:mangrove_fence\",\"cap\":\"minecraft:lantern\"},{\"type\":\"box\",\"from\":[13,1,34],\"to\":[15,1,34],\"block\":\"minecraft:yellow_terracotta\",\"hollow\":false},{\"type\":\"column\",\"at\":[-17,0,32],\"height\":5,\"block\":\"minecraft:quartz_pillar\",\"cap\":\"minecraft:yellow_terracotta\"},{\"type\":\"set\",\"at\":[-17,6,32],\"block\":\"minecraft:lantern\"},{\"type\":\"column\",\"at\":[17,0,32],\"height\":5,\"block\":\"minecraft:quartz_pillar\",\"cap\":\"minecraft:yellow_terracotta\"},{\"type\":\"set\",\"at\":[17,6,32],\"block\":\"minecraft:lantern\"}]}";
	static final String AZMAR = "{\"palette\":{\"minecraft:stone_bricks\":\"minecraft:cut_sandstone\",\"minecraft:cobblestone\":\"minecraft:sandstone\",\"minecraft:stone\":\"minecraft:smooth_sandstone\",\"minecraft:stone_brick_wall\":\"minecraft:sandstone_wall\",\"minecraft:stone_brick_stairs\":\"minecraft:sandstone_stairs\",\"minecraft:stone_brick_slab\":\"minecraft:sandstone_slab\",\"minecraft:mossy_stone_bricks\":\"minecraft:chiseled_sandstone\",\"minecraft:spruce_planks\":\"minecraft:acacia_planks\",\"minecraft:spruce_log\":\"minecraft:acacia_log\",\"minecraft:spruce_slab\":\"minecraft:acacia_slab\",\"minecraft:spruce_fence\":\"minecraft:acacia_fence\",\"minecraft:spruce_trapdoor\":\"minecraft:acacia_trapdoor\",\"minecraft:spruce_fence_gate\":\"minecraft:acacia_fence_gate\",\"minecraft:oak_planks\":\"minecraft:acacia_planks\",\"minecraft:oak_log\":\"minecraft:acacia_log\",\"minecraft:oak_fence\":\"minecraft:acacia_fence\",\"minecraft:oak_stairs\":\"minecraft:acacia_stairs\",\"minecraft:glass_pane\":\"minecraft:iron_bars\",\"minecraft:blue_stained_glass_pane\":\"minecraft:iron_bars\",\"minecraft:blue_wool\":\"minecraft:blue_terracotta\",\"minecraft:dirt_path\":\"minecraft:smooth_sandstone\"},\"pieces\":[{\"type\":\"box\",\"from\":[-9,0,31],\"to\":[-5,0,34],\"block\":\"minecraft:smooth_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[-8,1,31],\"to\":[-6,2,33],\"block\":\"minecraft:cut_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[-8,1,34],\"to\":[-8,1,34],\"block\":\"minecraft:cut_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[-6,1,34],\"to\":[-6,1,34],\"block\":\"minecraft:cut_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[-8,3,33],\"to\":[-6,5,33],\"block\":\"minecraft:chiseled_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[-9,4,33],\"to\":[-5,5,33],\"block\":\"minecraft:blue_glazed_terracotta\",\"hollow\":false},{\"type\":\"set\",\"at\":[-7,5,34],\"block\":\"minecraft:yellow_terracotta\"},{\"type\":\"obelisk\",\"at\":[-13,0,33],\"height\":12,\"base\":2,\"block\":\"minecraft:smooth_sandstone\",\"cap\":\"minecraft:yellow_terracotta\"},{\"type\":\"column\",\"at\":[-16,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"column\",\"at\":[-20,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"column\",\"at\":[-24,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"column\",\"at\":[-28,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"set\",\"at\":[-13,4,34],\"block\":\"minecraft:blue_glazed_terracotta\"},{\"type\":\"box\",\"from\":[5,0,31],\"to\":[9,0,34],\"block\":\"minecraft:smooth_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[6,1,31],\"to\":[8,2,33],\"block\":\"minecraft:cut_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[6,1,34],\"to\":[6,1,34],\"block\":\"minecraft:cut_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[8,1,34],\"to\":[8,1,34],\"block\":\"minecraft:cut_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[6,3,33],\"to\":[8,5,33],\"block\":\"minecraft:chiseled_sandstone\",\"hollow\":false},{\"type\":\"box\",\"from\":[5,4,33],\"to\":[9,5,33],\"block\":\"minecraft:blue_glazed_terracotta\",\"hollow\":false},{\"type\":\"set\",\"at\":[7,5,34],\"block\":\"minecraft:yellow_terracotta\"},{\"type\":\"obelisk\",\"at\":[13,0,33],\"height\":12,\"base\":2,\"block\":\"minecraft:smooth_sandstone\",\"cap\":\"minecraft:yellow_terracotta\"},{\"type\":\"column\",\"at\":[16,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"column\",\"at\":[20,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"column\",\"at\":[24,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"column\",\"at\":[28,0,33],\"height\":6,\"block\":\"minecraft:cut_sandstone\",\"cap\":\"minecraft:cyan_terracotta\"},{\"type\":\"set\",\"at\":[13,4,34],\"block\":\"minecraft:blue_glazed_terracotta\"}]}";
	static final long[] SEEDS = {1L, 42L, 2026L, 31337L};
	static int canalSites, canalColumns, batterCells;

	static Identifier id(String p) {
		return CourtContent.id(p);
	}

	static void add() {
		for (int i = 0; i < SEEDS.length; i++) {
			int k = i;
			STEPS.add((srv, l) -> {
				site(srv, l, k, true);
				return 2;
			});
			STEPS.add((srv, l) -> {
				site(srv, l, k, false);
				return 2;
			});
		}
		STEPS.add((srv, l) -> {
			check("fv 2 Mirewick canal restored: built on real ground of at least 2 of " + SEEDS.length + " seeds' shore sites", canalSites >= 2, canalSites + " sites, " + canalColumns + " water columns");
			check("fv 2 Azmar tower batter restored on every hillside site (" + SEEDS.length + " seeds)", batterCells >= SEEDS.length * 60, batterCells + " batter blocks");
			CastleStyles.clear(id("zc_mirewick"));
			CastleStyles.clear(id("zc_azmar"));
			return 1;
		});
	}

	static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty();
	}

	static void site(net.minecraft.server.MinecraftServer srv, ServerLevel l, int k, boolean mirewick) {
		long seed = SEEDS[k];
		var reg = srv.registryAccess();
		ChunkGenerator gen = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
		RandomState rs = RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, ((NoiseBasedChunkGenerator) gen).generatorSettings().value());
		int sea = gen.getSeaLevel();
		Identifier sid = id(mirewick ? "zc_mirewick" : "zc_azmar");
		if (CastleStyles.of(sid) == null) CastleStyles.register(sid, CastleStyles.parse(JsonParser.parseString(mirewick ? MIREWICK : AZMAR).getAsJsonObject()));
		CastleStyles.Style style = CastleStyles.of(sid);
		String tag = (mirewick ? "Mirewick" : "Azmar") + " seed " + seed;
		// deterministic search in a block of the seed's world that no other case uses (and far from the court realm at 0,0)
		int bx = 3000 + 3000 * k, bz = mirewick ? -1200 : 2400;
		BlockPos o = null;
		RealmFit.HeightField hf = null;
		int tries = 0;
		for (int i = 0; i < 10000 && o == null && tries < 200; i++) {
			int x = bx + (i % 100) * 24, z = bz + (i / 100) * 24;
			int c = h(gen, rs, l, x, z, Heightmap.Types.WORLD_SURFACE_WG);
			if (h(gen, rs, l, x, z, Heightmap.Types.OCEAN_FLOOR_WG) < c) continue;
			if (mirewick) {
				int q = h(gen, rs, l, x, z + 32, Heightmap.Types.WORLD_SURFACE_WG);
				if (q < sea || q > sea + 2 || h(gen, rs, l, x, z + 32, Heightmap.Types.OCEAN_FLOOR_WG) < q) continue;
				if (h(gen, rs, l, x + 10, z + 36, Heightmap.Types.WORLD_SURFACE_WG) > sea && h(gen, rs, l, x - 10, z + 36, Heightmap.Types.WORLD_SURFACE_WG) > sea) continue;
			} else {
				int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
				for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
					int g = h(gen, rs, l, x + 35 * a, z + 35 * b, Heightmap.Types.WORLD_SURFACE_WG);
					if (h(gen, rs, l, x + 35 * a, z + 35 * b, Heightmap.Types.OCEAN_FLOOR_WG) < g) g = Integer.MIN_VALUE / 2;
					lo = Math.min(lo, g);
					hi = Math.max(hi, g);
				}
				if (lo < sea || hi - lo < 4 || hi - lo > 12) continue;
			}
			tries++;
			BlockPos cand = new BlockPos(x, c, z);
			RealmFit.HeightField f = new RealmFit.HeightField(gen, rs, l, seed, cand, c, c);
			if (mirewick && CastleKit.canal(cand, f, style) == null) continue;
			o = cand;
			hf = f;
		}
		if (o == null) {
			info(tag + ": no " + (mirewick ? "shore" : "hillside") + " site found in the search block (" + tries + " candidates)");
			return;
		}
		// copy the generator terrain (ground and sea-level water) into this flat test world, then build CASTLE, WALL and KIT_CASTLE (fv 2)
		int x0 = o.getX() - 40, x1 = o.getX() + 40, z0 = o.getZ() - 40, z1 = o.getZ() + 42;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, true);
		int n = (x1 - x0 + 1) * (z1 - z0 + 1), w = z1 - z0 + 1;
		int[] top = new int[n], fl = new int[n];
		int base = Integer.MAX_VALUE;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			int i = (x - x0) * w + (z - z0);
			top[i] = hf.y(x, z);
			fl[i] = Math.min(top[i], h(gen, rs, l, x, z, Heightmap.Types.OCEAN_FLOOR_WG));
			base = Math.min(base, fl[i]);
		}
		base -= 14;
		BlockState stone = Blocks.STONE.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), sand = Blocks.SAND.defaultBlockState(), water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			int i = (x - x0) * w + (z - z0);
			for (int y = base; y < fl[i]; y++) l.setBlock(m.set(x, y, z), y == fl[i] - 1 ? (top[i] > fl[i] ? sand : grass) : stone, 2);
			for (int y = fl[i]; y < top[i]; y++) l.setBlock(m.set(x, y, z), water, 2);
			for (int y = top[i]; y <= top[i] + 40; y++) if (!l.getBlockState(m.set(x, y, z)).isAir()) l.setBlock(m, air, 2);
		}
		for (CastleLayout.Part part : new CastleLayout.Part[] {CastleLayout.Part.CASTLE, CastleLayout.Part.WALL, CastleLayout.Part.KIT_CASTLE}) {
			BoundingBox box = CastleLayout.fitBox(part, o);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15));
				pl.style = style;
				pl.ground = hf;
				CastleLayout.build(part, o, pl);
			}
		}
		if (mirewick) canalChecks(l, tag, o, hf, style, x0, z0, w, top, fl, sea);
		else batterChecks(l, tag, o, hf);
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, false);
	}

	static int h(ChunkGenerator gen, RandomState rs, ServerLevel l, int x, int z, Heightmap.Types t) {
		return gen.getBaseHeight(x, z, t, l, rs);
	}

	static void canalChecks(ServerLevel l, String tag, BlockPos o, RealmFit.HeightField hf, CastleStyles.Style style, int x0, int z0, int w, int[] top, int[] fl, int sea) {
		CastleKit.Canal c = CastleKit.canal(o, hf, style);
		if (c == null) {
			check(tag + ": the shore site plans a canal", false, o);
			return;
		}
		int wt = c.water(), q = c.quay();
		int stray = 0, above = 0, leak = 0, trench = 0, cells = 0;
		String first = null;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int lx = -24; lx <= 24; lx++) for (int lz = 28; lz <= 41; lz++) {
			int wx = o.getX() + lx, wz = o.getZ() + lz, i = (wx - x0) * w + (wz - z0);
			for (int y = Math.min(wt, fl[i]) - 4; y <= q + 6; y++) {
				BlockState s = l.getBlockState(m.set(wx, y, wz));
				boolean natural = y >= fl[i] && y < top[i];
				boolean bed = c.at(lx) && (lz == 35 || lz == 36) && (y == wt || y == wt - 1);
				if (s.getFluidState().isEmpty()) {
					// no trenching: natural land above the water level in the canal footprint is still there
					if (y > wt && y < fl[i] && Math.abs(lx) >= 3 && Math.abs(lx) <= 17 && lz >= 34 && lz <= 37 && s.isAir()) {
						trench++;
						if (first == null) first = "trench at " + lx + "," + y + "," + lz;
					}
					continue;
				}
				if (!natural && !bed) {
					stray++;
					if (first == null) first = "stray water at " + lx + "," + y + "," + lz;
				}
				if (!natural && y > wt) above++;
				if (bed && y == wt) cells++;
				if (bed) for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
					int nx = lx + d[0], ny = y + d[1], nz = lz + d[2];
					BlockState ns = l.getBlockState(new BlockPos(o.getX() + nx, ny, o.getZ() + nz));
					boolean nbed = c.at(nx) && (nz == 35 || nz == 36) && (ny == wt || ny == wt - 1);
					if (!solid(ns) && !(nbed && !ns.getFluidState().isEmpty())) {
						leak++;
						if (first == null) first = "open side at " + nx + "," + ny + "," + nz + " (" + ns.getBlock() + ")";
					}
				}
			}
		}
		info(tag + ": shore site " + o + ", sea level " + sea + ", canal water top y " + wt + ", quay stone y " + q + ", " + c.columns() + " planned water columns, " + cells + " water surface blocks");
		check(tag + ": canal water only inside its own bed, none above sea level (no flooding)", stray == 0 && above == 0, stray + " stray, " + above + " above sea level" + (first == null ? "" : "; " + first));
		check(tag + ": every canal water block is contained (walls, bed or canal water on all 4 sides and below)", leak == 0, leak + (first == null ? "" : "; " + first));
		check(tag + ": no trenching (no natural land above the water level removed in the canal footprint)", trench == 0, trench + (first == null ? "" : "; " + first));
		if (cells > 0) {
			canalSites++;
			canalColumns += c.columns();
		}
	}

	static void batterChecks(ServerLevel l, String tag, BlockPos o, RealmFit.HeightField hf) {
		int missing = 0, under = 0, n = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		String first = null;
		List<int[]> cells = new java.util.ArrayList<>(CastleKit.towerBatter(o, hf));
		int towerCells = cells.size();
		// the curtain batter (wallFoot, ring 31 without the corners and the gate) uses the same ground rule
		for (int t = -31; t <= 31; t++) for (int[] p : new int[][] {{t, -31}, {t, 31}, {-31, t}, {31, t}}) {
			if (Math.abs(p[0]) >= 26 && Math.abs(p[1]) >= 26 || p[1] >= 29 && Math.abs(p[0]) <= 7) continue;
			cells.add(new int[] {p[0], hf.y(o.getX() + p[0], o.getZ() + p[1]) - o.getY(), p[1], 0, 0});
		}
		for (int[] b : cells) {
			BlockPos p = o.offset(b[0], b[1], b[2]);
			BlockState s = l.getBlockState(p);
			Block blk = s.getBlock();
			n++;
			lo = Math.min(lo, b[1]);
			hi = Math.max(hi, b[1]);
			if (blk != Blocks.SANDSTONE_STAIRS && blk != Blocks.SANDSTONE) {
				missing++;
				if (first == null) first = "no batter at " + b[0] + "," + b[1] + "," + b[2] + " (" + blk + ")";
				continue;
			}
			if (!solid(l.getBlockState(p.below()))) {
				under++;
				if (first == null) first = "air/fluid under the batter at " + b[0] + "," + b[1] + "," + b[2] + " (" + l.getBlockState(p.below()).getBlock() + ")";
			}
		}
		int corb = 0;
		for (int x = -7; x <= 7; x++) for (int y = -6; y <= 45; y++) {
			BlockState cs = l.getBlockState(o.offset(x, y, 32));
			if (cs.getBlock() instanceof net.minecraft.world.level.block.StairBlock && cs.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP) corb++;
		}
		check(tag + ": w6 gatehouse corbelled projecting parapet (inverted stair corbels at z 32)", corb >= 10, corb);
		info(tag + ": hillside site " + o + ", " + towerCells + " tower batter + " + (n - towerCells) + " curtain batter blocks, ground " + lo + ".." + hi + " relative to the floor");
		check(tag + ": tower and curtain batter present on their own ground", missing == 0 && towerCells >= 60, missing + " missing of " + n + (first == null ? "" : "; " + first));
		check(tag + ": no air or fluid under any batter block", under == 0, under + (first == null ? "" : "; " + first));
		if (missing == 0) batterCells += towerCells;
	}
}
