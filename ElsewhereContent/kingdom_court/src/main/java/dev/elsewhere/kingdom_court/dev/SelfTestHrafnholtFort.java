package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import dev.elsewhere.kingdom_court.world.HrafnholtFort;
import dev.elsewhere.kingdom_court.world.HrafnholtShore;
import dev.elsewhere.kingdom_court.world.RealmFit;
import dev.elsewhere.kingdom_court.world.Valuables;
import dev.elsewhere.kingdom_court.world.Waterfront;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * s-layout-hraf (3.0.0): Hrafnholt's own fv 2 ring fort ({@link HrafnholtFort}) and shore ({@link HrafnholtShore}) on the REAL generator
 * ground of several seeds. Per seed a seafaring site is searched (castle on dry ground, the shore 56..104 blocks south on even seeds, north on
 * odd ones; a dry site when none is found), the terrain is copied into this test world far from the court realm, the realm's CASTLE, DUNGEON,
 * WALL and KIT_CASTLE pieces and its waterfront are built chunk by chunk through the fv 2 path, and the blocks are checked: court anchors
 * (throne, gate fence gates, every garrison and court post standable), the palisade ring closed except at its open gates, gate passages open,
 * nothing floating, nothing buried, no valuable block, few banners and no furnace, the shore road (steps of at most 1, ends at the jetty at
 * sea level) and the nausts with their slips. Also: the layout never runs for fv 1 or with its kill switch off.
 */
final class SelfTestHrafnholtFort {
	private SelfTestHrafnholtFort() {}

	static final long[] SEEDS = {1L, 42L, 2026L, 31337L};
	static int built, waterSites, nausts;

	static Identifier sid() {
		return CourtContent.id("hl_hrafnholt");
	}

	static void add() {
		for (int i = 0; i < SEEDS.length; i++) {
			int k = i;
			STEPS.add((srv, l) -> {
				site(srv, l, k);
				return 2;
			});
		}
		STEPS.add((srv, l) -> {
			check("hraf: Hrafnholt ring fort built on all " + SEEDS.length + " seeds' sites", built == SEEDS.length, built);
			check("hraf: nausts with slips on the seeds' water sites (at least 2 nausts on at least 2 water sites)", waterSites >= 2 && nausts >= 2, waterSites + " water sites, " + nausts + " nausts");
			fv1(srv, l);
			CastleStyles.clear(sid());
			return 1;
		});
	}

	/** fv 1 (ground null) and the kill switch keep the shared layout: the Hrafnholt layout claims nothing. */
	static void fv1(MinecraftServer srv, ServerLevel l) {
		BlockPos o = new BlockPos(9000, 120, 9000);
		CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX(), o.getY(), o.getZ()));
		pl.style = CastleStyles.of(sid());
		boolean claims = false;
		for (CastleLayout.Part p : CastleLayout.Part.values()) claims |= HrafnholtFort.build(p, o, pl);
		boolean prev = HrafnholtFort.ENABLED;
		HrafnholtFort.ENABLED = false;
		CastleLayout.Ground flat = (x, z) -> o.getY();
		pl.ground = flat;
		boolean off = false;
		for (CastleLayout.Part p : CastleLayout.Part.values()) off |= HrafnholtFort.build(p, o, pl);
		HrafnholtShore.build(pl, o, 1, 64, 63, flat);
		HrafnholtFort.ENABLED = prev;
		check("hraf: fv 1 realms (no ground) and -Delsewhere.overhaul3.hrafnholt_fort=false keep the shared layout (nothing claimed, nothing written)", !claims && !off && pl.placed == 0, claims + "/" + off + "/" + pl.placed);
	}

	static int h(ChunkGenerator gen, RandomState rs, ServerLevel l, int x, int z, Heightmap.Types t) {
		return gen.getBaseHeight(x, z, t, l, rs);
	}

	static boolean dry(ChunkGenerator gen, RandomState rs, ServerLevel l, int x, int z, int min) {
		int c = h(gen, rs, l, x, z, Heightmap.Types.WORLD_SURFACE_WG);
		return c >= min && h(gen, rs, l, x, z, Heightmap.Types.OCEAN_FLOOR_WG) >= c;
	}

	static void site(MinecraftServer srv, ServerLevel l, int k) {
		long seed = SEEDS[k];
		var reg = srv.registryAccess();
		ChunkGenerator gen = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
		RandomState rs = RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, ((NoiseBasedChunkGenerator) gen).generatorSettings().value());
		int sea = gen.getSeaLevel();
		if (CastleStyles.of(sid()) == null) CastleStyles.register(sid(), CastleStyles.parse(JsonParser.parseString("{\"palette\":{}}").getAsJsonObject()));
		CastleStyles.Style style = CastleStyles.of(sid());
		int dir = (k & 1) == 0 ? 1 : 3, sgn = dir == 1 ? 1 : -1;
		String tag = "hraf seed " + seed;
		int bx = -6000 - 3000 * k, bz = 6000;
		BlockPos o = null;
		int shore = -1, tries = 0;
		// a seafaring site (shore 56..104 out on the water side), else any dry site
		for (int pass = 0; pass < 2 && o == null; pass++) for (int i = 0; i < 10000 && o == null && tries < 300 * (pass + 1); i++) {
			int x = bx + (i % 100) * 24, z = bz + (i / 100) * 24;
			if (!dry(gen, rs, l, x, z, sea + 3)) continue;
			int c = h(gen, rs, l, x, z, Heightmap.Types.WORLD_SURFACE_WG);
			boolean ok = true;
			for (int b = 0; b < 16 && ok; b++) {
				double a = Math.PI * b / 8;
				if (!dry(gen, rs, l, x + (int) Math.round(37 * Math.sin(a)), z + (int) Math.round(37 * Math.cos(a)), sea + 2)) ok = false;
				int g = h(gen, rs, l, x + (int) Math.round(33 * Math.sin(a)), z + (int) Math.round(33 * Math.cos(a)), Heightmap.Types.WORLD_SURFACE_WG);
				if (Math.abs(g - c) > 8) ok = false;
			}
			if (!ok) continue;
			tries++;
			int s = -1;
			for (int u = 56; u <= 104 && s < 0; u += 8) if (h(gen, rs, l, x, z + sgn * u, Heightmap.Types.WORLD_SURFACE_WG) <= sea) s = u;
			if (pass == 0 && s < 0) continue;
			o = new BlockPos(x, c, z);
			shore = s;
		}
		if (o == null) {
			info(tag + ": no site found in the search block (" + tries + " candidates)");
			return;
		}
		RealmFit.HeightField hf = new RealmFit.HeightField(gen, rs, l, seed, o, o.getY(), o.getY());
		int[] water = shore > 0 ? new int[] {dir, shore} : null;
		CourtWorld.setWater(l.getSeed(), sid(), water);
		int x0 = o.getX() - 40, x1 = o.getX() + 40, z0 = o.getZ() - 40, z1 = o.getZ() + 42;
		if (water != null && dir == 1) z1 = o.getZ() + shore + 18;
		if (water != null && dir == 3) z0 = o.getZ() - shore - 18;
		try {
			for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, true);
			int w = z1 - z0 + 1, n = (x1 - x0 + 1) * w;
			int[] top = new int[n], fl = new int[n];
			int base = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
				int i = (x - x0) * w + (z - z0);
				top[i] = hf.y(x, z);
				fl[i] = Math.min(top[i], h(gen, rs, l, x, z, Heightmap.Types.OCEAN_FLOOR_WG));
				base = Math.min(base, fl[i]);
				hi = Math.max(hi, top[i]);
			}
			base -= 14;
			BlockState stone = Blocks.STONE.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), sand = Blocks.SAND.defaultBlockState(), wat = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
				int i = (x - x0) * w + (z - z0);
				for (int y = base; y < fl[i]; y++) l.setBlock(m.set(x, y, z), y == fl[i] - 1 ? (top[i] > fl[i] ? sand : grass) : stone, 2);
				for (int y = fl[i]; y < top[i]; y++) l.setBlock(m.set(x, y, z), wat, 2);
				for (int y = top[i]; y <= Math.max(top[i], o.getY()) + 40; y++) if (!l.getBlockState(m.set(x, y, z)).isAir()) l.setBlock(m, air, 2);
			}
			for (CastleLayout.Part part : new CastleLayout.Part[] {CastleLayout.Part.CASTLE, CastleLayout.Part.DUNGEON, CastleLayout.Part.WALL, CastleLayout.Part.KIT_CASTLE}) {
				BoundingBox box = CastleLayout.fitBox(part, o);
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					CastleLayout.Placer pl = new CastleLayout.Placer(l, chunk(l, cx, cz));
					pl.style = style;
					pl.ground = hf;
					CastleLayout.build(part, o, pl);
				}
			}
			HrafnholtShore.Plan plan = null;
			if (water != null) {
				BoundingBox box = Waterfront.box(o, dir, shore, sea);
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					CastleLayout.Placer pl = new CastleLayout.Placer(l, chunk(l, cx, cz));
					Waterfront.build(pl, o, dir, shore, sea, Waterfront.parse(null), (int) (o.asLong() >>> 3));
					pl.style = style;
					HrafnholtShore.build(pl, o, dir, shore, sea, hf);
				}
				CastleLayout.Placer pp = new CastleLayout.Placer(l, chunk(l, o.getX() >> 4, o.getZ() >> 4));
				pp.style = style;
				pp.ground = hf;
				plan = HrafnholtShore.plan(pp, o, dir, shore, sea);
			}
			HrafnholtFort.Ring ring = HrafnholtFort.ring(o, hf, water == null ? 0 : dir);
			info(tag + ": site " + o + ", sea " + sea + (water == null ? ", no waterfront" : ", water " + (dir == 1 ? "south" : "north") + " at " + shore) + ", gates open S/E/N/W " + ring.open()[0] + "/" + ring.open()[1] + "/" + ring.open()[2] + "/" + ring.open()[3]);
			checks(l, tag, o, ring, plan, dir, sea, x0, z0, x1, z1, base, hi + 44);
			built++;
		} finally {
			CourtWorld.setWater(l.getSeed(), sid(), null);
			for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, false);
		}
	}

	static BoundingBox chunk(ServerLevel l, int cx, int cz) {
		return new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15);
	}

	static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty();
	}

	static boolean open(ServerLevel l, BlockPos p) {
		return l.getBlockState(p).getCollisionShape(l, p).isEmpty() && l.getBlockState(p).getFluidState().isEmpty();
	}

	static boolean stand(ServerLevel l, BlockPos p) {
		return open(l, p) && open(l, p.above()) && !l.getBlockState(p.below()).getCollisionShape(l, p.below()).isEmpty();
	}

	static BlockPos uv(BlockPos o, int dir, int u, int y, int v) {
		return dir == 1 ? new BlockPos(o.getX() - v, y, o.getZ() + u) : new BlockPos(o.getX() + v, y, o.getZ() - u);
	}

	static void checks(ServerLevel l, String tag, BlockPos o, HrafnholtFort.Ring ring, HrafnholtShore.Plan plan, int dir, int sea, int x0, int z0, int x1, int z1, int y0, int y1) {
		// court anchors
		check(tag + ": the high seat is the court's throne (dark oak stairs at (0, 1, -24))", l.getBlockState(CastleLayout.throne(o)).is(Blocks.DARK_OAK_STAIRS), l.getBlockState(CastleLayout.throne(o)).getBlock());
		int gates = 0;
		for (BlockPos p : CastleLayout.gates(o)) if (l.getBlockState(p).getBlock() instanceof FenceGateBlock) gates++;
		check(tag + ": the south gate holds the court's 5 fence gates at z 30", gates == 5, gates);
		List<BlockPos> posts = new ArrayList<>(List.of(CastleLayout.throneFront(o), CastleLayout.hall(o), o.offset(0, 0, -11), CastleLayout.gateInside(o), o.offset(0, 0, 20), CastleLayout.yard(o),
			CastleLayout.barracks(o), CastleLayout.stables(o), CastleLayout.balcony(o, true), CastleLayout.balcony(o, false), CastleLayout.wallTop(o, true), CastleLayout.wallTop(o, false),
			o.offset(-14, 0, -4), o.offset(6, 0, 4), o.offset(4, 0, -20), o.offset(3, 0, -18), o.offset(-3, 0, -18), o.offset(4, 0, 21), o.offset(7, 0, 21), o.offset(10, 0, 21), o.offset(0, 1, -22)));
		posts.addAll(CastleLayout.yardLoop(o));
		for (int x = -3; x <= 2; x++) posts.add(o.offset(x, 0, -17));
		for (int x = -11; x <= -6; x++) posts.add(o.offset(x, 0, 6));
		for (int x = -24; x <= -20; x++) posts.add(o.offset(x, 0, 3));
		for (int x = -3; x <= 2; x++) posts.add(o.offset(x, 0, 22));
		int bad = 0;
		String first = null;
		for (BlockPos p : posts) {
			BlockPos q = p.getY() == o.getY() + 1 && p.getX() == o.getX() && p.getZ() == o.getZ() - 22 ? p.below() : p;
			if (!stand(l, q)) {
				bad++;
				if (first == null) first = "post " + (q.getX() - o.getX()) + "," + (q.getY() - o.getY()) + "," + (q.getZ() - o.getZ()) + " (" + l.getBlockState(q).getBlock() + " / below " + l.getBlockState(q.below()).getBlock() + ")";
			}
		}
		check(tag + ": every garrison and court post is standable (feet and head open, solid floor)", bad == 0, bad + " of " + posts.size() + (first == null ? "" : "; " + first));
		check(tag + ": the longhall's hogback ridge stands over the hall (0, 9, -11)", l.getBlockState(o.offset(0, 9, -11)).is(Blocks.DARK_OAK_PLANKS), l.getBlockState(o.offset(0, 9, -11)).getBlock());
		// palisade ring closed except at open gates
		int gaps = 0;
		first = null;
		for (int i = 0; i < HrafnholtFort.N_BEARINGS; i++) {
			double a = 2 * Math.PI * i / HrafnholtFort.N_BEARINGS;
			int sx = (int) Math.round(33 * Math.sin(a)), sz = (int) Math.round(33 * Math.cos(a));
			boolean gate = false;
			for (int g = 0; g < 4; g++) if (ring.open()[g] && Math.abs(HrafnholtFort.gv(g, sx, sz)) <= 2 && HrafnholtFort.gu(g, sx, sz) > 0) gate = true;
			if (gate) continue;
			boolean hit = false;
			for (double r = 31; r <= 37 && !hit; r += 0.5) {
				int x = (int) Math.round(r * Math.sin(a)), z = (int) Math.round(r * Math.cos(a));
				for (int y = -2; y <= 30 && !hit; y++) {
					BlockState s = l.getBlockState(o.offset(x, y, z));
					hit = s.is(Blocks.SPRUCE_LOG) || s.is(Blocks.DARK_OAK_LOG) || s.is(Blocks.DARK_OAK_PLANKS);
				}
			}
			if (!hit) {
				gaps++;
				if (first == null) first = "gap at bearing " + i + " (" + sx + "," + sz + ")";
			}
		}
		check(tag + ": the palisade ring is closed except at its open gates", gaps == 0, gaps + (first == null ? "" : "; " + first));
		// gate passages open (the south passage beyond z 30 belongs to the road piece)
		int shut = 0;
		first = null;
		for (int g = 0; g < 4; g++) {
			if (!ring.open()[g]) continue;
			int rg = HrafnholtFort.GATE_R[g];
			for (int u = rg - 3; u <= (g == 0 ? rg - 1 : rg + 2); u++) {
				BlockPos p = o.offset(HrafnholtFort.gx(g, u, 0), 0, HrafnholtFort.gz(g, u, 0));
				if (!(open(l, p) && open(l, p.above()))) {
					shut++;
					if (first == null) first = "gate " + g + " u " + u + " (" + l.getBlockState(p).getBlock() + ")";
				}
			}
		}
		check(tag + ": every open gate passage is open at the floor (fence gates open)", shut == 0, shut + (first == null ? "" : "; " + first));
		// nothing buried: doors and rooms open
		int[][] rooms = {{0, 0, 4}, {0, 1, 4}, {0, 0, -15}, {0, 2, -15}, {4, 0, 0}, {-4, 0, 0}, {3, 1, 11}, {3, 2, 11}, {-14, 0, -12}, {-14, 0, 12}, {14, 0, -12}, {14, 0, 12}, {-17, 1, -9}, {17, 1, 9}, {6, 2, 11}};
		int buried = 0;
		first = null;
		for (int[] c : rooms) if (!open(l, o.offset(c[0], c[1], c[2]))) {
			buried++;
			if (first == null) first = c[0] + "," + c[1] + "," + c[2] + " " + l.getBlockState(o.offset(c[0], c[1], c[2])).getBlock();
		}
		for (int g = 0; g < 4; g++) if (ring.open()[g]) {
			int rg = HrafnholtFort.GATE_R[g], dk = 9 + ring.gl()[g];
			for (int y : new int[] {6, 7, dk + 1}) {
				BlockPos p = o.offset(HrafnholtFort.gx(g, rg, 0), y, HrafnholtFort.gz(g, rg, 0));
				if (!open(l, p)) {
					buried++;
					if (first == null) first = "gate " + g + " tower y " + y + " " + l.getBlockState(p).getBlock();
				}
			}
		}
		check(tag + ": nothing buried (hall, hof, longhouse doors and gate tower storeys open)", buried == 0, buried + (first == null ? "" : "; " + first));
		// nothing floating: every built block is connected through solid blocks to the ground below the copied terrain
		int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
		BitSet seen = new BitSet(sx * sy * sz);
		int[] queue = new int[sx * sy * sz];
		int qh = 0, qt = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
			int id = (x * sy) * sz + z;
			if (solid(l.getBlockState(m.set(x0 + x, y0, z0 + z)))) {
				seen.set(id);
				queue[qt++] = id;
			}
		}
		int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (qh < qt) {
			int id = queue[qh++], x = id / (sy * sz), y = id / sz % sy, z = id % sz;
			for (int[] d : nb) {
				int nx = x + d[0], ny = y + d[1], nz = z + d[2];
				if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
				int ni = (nx * sy + ny) * sz + nz;
				if (seen.get(ni)) continue;
				if (!solid(l.getBlockState(m.set(x0 + nx, y0 + ny, z0 + nz)))) continue;
				seen.set(ni);
				queue[qt++] = ni;
			}
		}
		int floating = 0, valuables = 0, banners = 0, furnaces = 0;
		first = null;
		for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
			int lx = x0 + x - o.getX(), lz = z0 + z - o.getZ();
			boolean castle = Math.abs(lx) <= 36 && Math.abs(lz) <= 36;
			if (!castle && (plan == null || !mine(plan, dir, o, x0 + x, z0 + z))) continue;
			for (int y = 0; y < sy; y++) {
			BlockState s = l.getBlockState(m.set(x0 + x, y0 + y, z0 + z));
			if (s.isAir()) continue;
			if (castle) {
				if (Valuables.isValuable(s)) valuables++;
				if (s.getBlock() instanceof BannerBlock || s.getBlock() instanceof WallBannerBlock) banners++;
				if (s.is(Blocks.FURNACE) || s.is(Blocks.SMOKER) || s.is(Blocks.BLAST_FURNACE)) furnaces++;
			}
			if (!solid(s) || seen.get((x * sy + y) * sz + z)) continue;
			floating++;
			if (first == null) first = "floating " + s.getBlock() + " at " + lx + "," + (y0 + y - o.getY()) + "," + lz;
			}
		}
		check(tag + ": nothing floating (every block of the fort and the shore works stands on the ground)", floating == 0, floating + (first == null ? "" : "; " + first));
		check(tag + ": no valuable block in the fort", valuables == 0, valuables);
		check(tag + ": at most 12 banners and no furnace, smoker or blast furnace in the fort", banners <= 12 && furnaces == 0, banners + " banners, " + furnaces + " furnaces");
		if (plan == null) return;
		waterSites++;
		// shore road: steps of at most 1, from the castle side down to the jetty at sea level
		int steps = 0, missing = 0, prev = Integer.MIN_VALUE;
		first = null;
		for (int i = 0; i < plan.cells().length; i++) {
			if (i > 0 && Math.abs(plan.h()[i] - plan.h()[i - 1]) > 1) steps++;
			int u = plan.cells()[i][0], v = plan.cells()[i][1], got = Integer.MIN_VALUE;
			for (int d = -1; d <= 1 && got == Integer.MIN_VALUE; d++) {
				BlockPos p = uv(o, dir, u, o.getY() + plan.h()[i] + d, v);
				if (l.getBlockState(p.below()).is(Blocks.DIRT_PATH) && open(l, p) && open(l, p.above())) got = plan.h()[i] + d;
			}
			if (got == Integer.MIN_VALUE) {
				missing++;
				if (first == null) first = "no road at u " + u + " v " + v;
			} else {
				if (prev != Integer.MIN_VALUE && Math.abs(got - prev) > 1) steps++;
				prev = got;
			}
		}
		int n = plan.cells().length;
		boolean meets = n == 0 || plan.h()[n - 1] == sea + 1 - o.getY();
		info(tag + ": shore road " + n + " cells" + (n == 0 ? "" : " from height " + plan.h()[0] + " to " + plan.h()[n - 1]) + ", berths " + plan.berths().size());
		check(tag + ": the shore road has steps of at most 1 and meets the jetty at sea level", steps == 0 && missing == 0 && meets, steps + " steps, " + missing + " missing" + (first == null ? "" : "; " + first));
		int ok = 0;
		for (int[] b : plan.berths()) {
			int vc = b[0], ub = b[1];
			boolean boat = l.getBlockState(uv(o, dir, ub - 6, sea + 1, vc)).is(Blocks.DARK_OAK_LOG);
			boolean slip = l.getBlockState(uv(o, dir, ub + 1, sea - 1, vc + 1)).is(Blocks.SPRUCE_LOG) && l.getBlockState(uv(o, dir, ub + 1, sea - 1, vc - 1)).is(Blocks.SPRUCE_LOG);
			boolean roof = l.getBlockState(uv(o, dir, ub - 6, sea + 6, vc)).is(Blocks.MOSS_BLOCK);
			if (boat && slip && roof) ok++;
		}
		check(tag + ": every planned naust has its boat, turf roof and log slip into the water", ok == plan.berths().size(), ok + " of " + plan.berths().size());
		nausts += ok;
	}

	/** True for a column of the shore works (road strip or naust footprint). */
	static boolean mine(HrafnholtShore.Plan plan, int dir, BlockPos o, int wx, int wz) {
		int u = dir == 1 ? wz - o.getZ() : o.getZ() - wz, v = dir == 1 ? o.getX() - wx : wx - o.getX();
		for (int[] c : plan.cells()) if (Math.abs(c[0] - u) <= 1 && Math.abs(c[1] - v) <= 1) return true;
		for (int[] b : plan.berths()) if (u >= b[1] - 13 && u <= b[1] && Math.abs(v - b[0]) <= 3) return true;
		return false;
	}
}
