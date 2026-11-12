package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import dev.elsewhere.kingdom_court.world.MireLayout;
import dev.elsewhere.kingdom_court.world.RealmFit;
import dev.elsewhere.kingdom_court.world.Valuables;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * s-layout-mire (3.0.0): Mirewick's own fv 2 castle layout ({@link MireLayout}) on the REAL generator ground of several seeds. For each seed a
 * low polder site (land near sea level, gentle all round the trace and before the gate) is found with the overworld noise generator, its
 * terrain is copied block for block into this test world (fort square and the road corridor), and CASTLE, DUNGEON, ROAD, WALL and KIT_CASTLE are
 * built through the fv 2 path chunk by chunk like worldgen. Checked: bastion tips and every rampart column solid and founded, the batter on its
 * own ground, the yard pad solid, the court's anchors usable (gate fence gates, open hall, yard, barracks, stables, gate, yard loop, wall tops,
 * floor bell, campfire braziers), no valuable block, nothing floating, every water block the layout placed planned and contained, the inner
 * canal present, purple leading crimson, crimson and gilt present (exposed blocks). Over all seeds: a wet moat and a ravelin on at least one; the kill
 * switch falls back to the shared layout.
 */
final class SelfTestMireLayout {
	private SelfTestMireLayout() {}

	static final long[] SEEDS = {1L, 42L, 2026L};
	/** Ground probes (local) for the site search: the trace, the gate front, the ravelin and the first road stretch. */
	static final int[][] PROBES = {{0, 0}, {0, -36}, {33, -24}, {-33, -24}, {36, 6}, {-36, 6}, {28, 36}, {-28, 36}, {0, 46}, {10, 44}, {-10, 44}, {0, 54}};
	static int sites, wetSites, ravelinSites, canalBlocks;

	static Identifier id(String p) {
		return CourtContent.id(p);
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
			check("Mirewick fv 2 layout built on at least 2 of " + SEEDS.length + " seeds' polder sites", sites >= 2, sites + " sites, " + canalBlocks + " inner canal water blocks");
			check("Mirewick wet moat cut on at least one seed's flat ground", wetSites >= 1, wetSites + " sites with a wet moat");
			check("Mirewick ravelin raised before the gate on at least one seed", ravelinSites >= 1, ravelinSites + " sites with a ravelin");
			killSwitch(l);
			CastleStyles.clear(id("zm_mirewick"));
			return 1;
		});
	}

	static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty();
	}

	static int h(ChunkGenerator gen, RandomState rs, ServerLevel l, int x, int z, Heightmap.Types t) {
		return gen.getBaseHeight(x, z, t, l, rs);
	}

	/** The copied terrain: the fort square and the road corridor south of it. */
	static boolean copied(int lx, int lz) {
		return Math.abs(lx) <= 42 && lz >= -42 && lz <= 42 || Math.abs(lx) <= 97 && lz >= 26 && lz <= 89;
	}

	static void site(MinecraftServer srv, ServerLevel l, int k) {
		long seed = SEEDS[k];
		var reg = srv.registryAccess();
		ChunkGenerator gen = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
		RandomState rs = RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, ((NoiseBasedChunkGenerator) gen).generatorSettings().value());
		int sea = gen.getSeaLevel();
		Identifier sid = id("zm_mirewick");
		if (CastleStyles.of(sid) == null) CastleStyles.register(sid, CastleStyles.parse(JsonParser.parseString(SelfTestCastleFit.MIREWICK).getAsJsonObject()));
		CastleStyles.Style style = CastleStyles.of(sid);
		String tag = "Mirewick layout seed " + seed;
		// deterministic search in a block of the seed's world no other case uses
		int bx = -6000 - 3000 * k, bz = 6000;
		BlockPos o = null;
		for (int i = 0; i < 4900 && o == null; i++) {
			int x = bx + (i % 70) * 40, z = bz + (i / 70) * 40;
			int c = h(gen, rs, l, x, z, Heightmap.Types.WORLD_SURFACE_WG);
			if (c < sea + 1 || c > sea + 6 || h(gen, rs, l, x, z, Heightmap.Types.OCEAN_FLOOR_WG) < c) continue;
			boolean ok = true;
			for (int[] p : PROBES) {
				int g = h(gen, rs, l, x + p[0], z + p[1], Heightmap.Types.WORLD_SURFACE_WG);
				if (Math.abs(g - c) > 3 || h(gen, rs, l, x + p[0], z + p[1], Heightmap.Types.OCEAN_FLOOR_WG) < g) {
					ok = false;
					break;
				}
			}
			if (ok) o = new BlockPos(x, c, z);
		}
		if (o == null) {
			info(tag + ": no polder site found in the search block");
			return;
		}
		RealmFit.HeightField hf = new RealmFit.HeightField(gen, rs, l, seed, o, o.getY(), o.getY());
		// copy the generator terrain (ground and natural water) into this flat test world
		int x0 = o.getX() - 97, x1 = o.getX() + 97, z0 = o.getZ() - 42, z1 = o.getZ() + 89, w = z1 - z0 + 1;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, true);
		int n = (x1 - x0 + 1) * w;
		int[] top = new int[n], fl = new int[n];
		int base = Integer.MAX_VALUE;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (!copied(x - o.getX(), z - o.getZ())) continue;
			int i = (x - x0) * w + (z - z0);
			top[i] = hf.y(x, z);
			fl[i] = Math.min(top[i], h(gen, rs, l, x, z, Heightmap.Types.OCEAN_FLOOR_WG));
			base = Math.min(base, fl[i]);
		}
		base -= 14;
		BlockState stone = Blocks.STONE.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), sand = Blocks.SAND.defaultBlockState(),
			water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (!copied(x - o.getX(), z - o.getZ())) continue;
			int i = (x - x0) * w + (z - z0);
			for (int y = base; y < fl[i]; y++) l.setBlock(m.set(x, y, z), y == fl[i] - 1 ? (top[i] > fl[i] ? sand : grass) : stone, 2);
			for (int y = fl[i]; y < top[i]; y++) l.setBlock(m.set(x, y, z), water, 2);
			for (int y = top[i]; y <= Math.max(top[i], o.getY()) + 50; y++) if (!l.getBlockState(m.set(x, y, z)).isAir()) l.setBlock(m, air, 2);
		}
		for (CastleLayout.Part part : new CastleLayout.Part[] {CastleLayout.Part.CASTLE, CastleLayout.Part.DUNGEON, CastleLayout.Part.ROAD, CastleLayout.Part.WALL,
			CastleLayout.Part.KIT_CASTLE}) {
			BoundingBox box = CastleLayout.fitBox(part, o);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15));
				pl.style = style;
				pl.ground = hf;
				CastleLayout.build(part, o, pl);
			}
		}
		checks(l, tag, o, hf, style, x0, z0, w, top, fl);
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, false);
	}

	/** Feet room at (x, y, z): low or no collision there (carpet, slab), none at head height, solid support below. */
	static boolean standable(ServerLevel l, BlockPos o, int x, int y, int z) {
		BlockPos f = o.offset(x, y, z);
		var feet = l.getBlockState(f).getCollisionShape(l, f);
		return (feet.isEmpty() || feet.max(Direction.Axis.Y) <= 0.5) && l.getBlockState(f.above()).getCollisionShape(l, f.above()).isEmpty()
			&& solid(l.getBlockState(f.below()));
	}

	static BlockState at(ServerLevel l, BlockPos o, int x, int y, int z) {
		return l.getBlockState(o.offset(x, y, z));
	}

	static String path(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	static void checks(ServerLevel l, String tag, BlockPos o, RealmFit.HeightField hf, CastleStyles.Style style, int x0, int z0, int w, int[] top, int[] fl) {
		MireLayout.Plan plan = MireLayout.plan(o, hf);
		// bastion tips
		int tipsOk = 0;
		for (int[] t : MireLayout.tipProbes()) {
			int tt = 8 + MireLayout.lift(plan, t[0], t[1]), foot = Math.min(0, hf.y(o.getX() + t[0], o.getZ() + t[1]) - o.getY());
			boolean ok = true;
			for (int y = foot - 1; y <= tt; y++) ok &= solid(at(l, o, t[0], y, t[1]));
			if (ok) tipsOk++;
		}
		check(tag + ": five bastion tips solid from the ground to the rampart top", tipsOk == 5, tipsOk + " of 5");
		// rampart columns founded, batter on its own ground, yard pad solid
		int ramp = 0, rampBad = 0, bat = 0, batBad = 0, pad = 0, padBad = 0;
		String first = null;
		for (int x = -36; x <= 36; x++) for (int z = -36; z <= 36; z++) {
			float d = MireLayout.delta(x, z);
			int gy = hf.y(o.getX() + x, o.getZ() + z) - o.getY();
			boolean gate = Math.abs(x) <= 6 && z >= 25, mill = Math.abs(x) <= 2 && z >= -35 && z <= -29;
			if (MireLayout.rampart(x, z) && !gate && !mill) {
				int tt = 8 + MireLayout.lift(plan, x, z);
				ramp++;
				for (int y = Math.min(0, gy) - 1; y <= tt; y++) if (!solid(at(l, o, x, y, z))) {
					rampBad++;
					if (first == null) first = "rampart gap at " + x + "," + y + "," + z + " (" + path(at(l, o, x, y, z)) + ")";
					break;
				}
			} else if (d <= 0 && d > -1.5F && !MireLayout.quay(x, z)) {
				bat++;
				BlockState s = at(l, o, x, gy, z);
				if (!(s.getBlock() instanceof StairBlock) || !solid(at(l, o, x, gy - 1, z))) {
					batBad++;
					if (first == null) first = "batter at " + x + "," + gy + "," + z + " is " + path(s) + " over " + path(at(l, o, x, gy - 1, z));
				}
			} else if (d > 3.5F && !(x >= -11 && x <= 11 && z >= -26 && z <= -8) && !(Math.abs(x) <= 22 && z >= -3 && z <= 1)) {
				pad++;
				for (int y : new int[] {-1, -2, -5}) if (!solid(at(l, o, x, y, z))) {
					padBad++;
					if (first == null) first = "yard pad hole at " + x + "," + y + "," + z;
					break;
				}
			}
		}
		check(tag + ": every rampart column solid and founded (" + ramp + " columns)", rampBad == 0 && ramp > 400, rampBad + (first == null ? "" : "; " + first));
		check(tag + ": batter on its own ground (quartz stair, solid under it; " + bat + " cells)", batBad == 0 && bat > 100, batBad + (first == null ? "" : "; " + first));
		check(tag + ": yard pad solid at floor-1, -2 and -5 (" + pad + " cells)", padBad == 0 && pad > 800, padBad + (first == null ? "" : "; " + first));
		// the court's anchors
		List<String> bad = new ArrayList<>();
		for (int x = -2; x <= 2; x++) if (!(at(l, o, x, 0, 30).getBlock() instanceof FenceGateBlock)) bad.add("gate " + x + " is " + path(at(l, o, x, 0, 30)));
		int[][] open = {{0, -15}, {0, -21}, {0, 24}, {-8, 8}, {-22, 4}, {-14, 20}, {-10, 6}, {10, 6}, {10, 16}, {-10, 16}, {6, 4}, {0, 20}};
		for (int[] p : open) if (!standable(l, o, p[0], 0, p[1])) bad.add("not standable at " + p[0] + ",0," + p[1]);
		for (int s = -12; s <= 12; s += 24) if (!standable(l, o, s, 9, 29)) bad.add("wall top " + s + ",9,29 not standable");
		if (!at(l, o, 6, 0, 14).is(Blocks.BELL)) bad.add("bell is " + path(at(l, o, 6, 0, 14)));
		for (BlockPos b : CastleLayout.braziers(BlockPos.ZERO)) if (!at(l, o, b.getX(), b.getY(), b.getZ()).is(Blocks.CAMPFIRE)) bad.add("brazier " + b.toShortString() + " is " + path(at(l, o, b.getX(), b.getY(), b.getZ())));
		check(tag + ": court anchors usable (gate, hall, yard, barracks, stables, loop, wall tops, bell, braziers)", bad.isEmpty(), bad.isEmpty() ? null : bad);
		// valuables, floating blocks, colour balance (fort and ravelin), water (whole copied area)
		int valuables = 0, floating = 0, purple = 0, crimson = 0, gold = 0;
		String vf = null;
		int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int x = -40; x <= 40; x++) for (int z = -40; z <= 58; z++) for (int y = -8; y <= 48; y++) {
			BlockState s = at(l, o, x, y, z);
			if (s.isAir()) continue;
			if (Valuables.isValuable(s)) {
				valuables++;
				if (vf == null) vf = "valuable " + path(s) + " at " + x + "," + y + "," + z;
			}
			if (!s.getFluidState().isEmpty()) continue;
			int airN = 0;
			for (int[] q : nb) if (at(l, o, x + q[0], y + q[1], z + q[2]).isAir()) airN++;
			if (airN == 6) {
				floating++;
				if (vf == null) vf = "floating " + path(s) + " at " + x + "," + y + "," + z;
			}
			if (airN == 0 || z > 40) continue;
			String p = path(s);
			if (p.contains("purple")) purple++;
			else if (p.startsWith("crimson_") || p.contains("red_nether_brick")) crimson++;
			else if (p.equals("yellow_terracotta")) gold++;
		}
		check(tag + ": no valuable blocks in the fort or the ravelin", valuables == 0, valuables + (vf == null ? "" : "; " + vf));
		check(tag + ": nothing floating (no block with air on all six sides)", floating == 0, floating + (vf == null ? "" : "; " + vf));
		int acc = Math.max(1, purple + crimson + gold);
		check(tag + ": royal colours balanced on exposed blocks (purple leads crimson, crimson at least 15%, gilt at least 4%)",
			purple >= crimson && crimson * 100 >= acc * 15 && gold * 25 >= acc, "purple " + purple + ", crimson " + crimson + ", gold " + gold);
		int stray = 0, leak = 0, inner = 0;
		String wf = null;
		for (int x = -97; x <= 97; x++) for (int z = -42; z <= 89; z++) {
			if (!copied(x, z)) continue;
			int i = (o.getX() + x - x0) * w + (o.getZ() + z - z0);
			int lo = Math.min(fl[i], o.getY() - 5) - 2, hi = Math.max(top[i], o.getY()) + 3;
			for (int wy = lo; wy <= hi; wy++) {
				int y = wy - o.getY();
				BlockState s = at(l, o, x, y, z);
				if (s.getFluidState().isEmpty()) continue;
				if (wy >= fl[i] && wy < top[i]) continue; // natural water
				if (MireLayout.innerCanal(x, y, z) && y == -2) inner++;
				if (!MireLayout.plannedWater(o, hf, style, x, y, z)) {
					stray++;
					if (wf == null) wf = "stray water at " + x + "," + y + "," + z;
				}
				for (int[] q : nb) {
					if (q[1] == 1) continue;
					BlockState ns = at(l, o, x + q[0], y + q[1], z + q[2]);
					if (!solid(ns) && ns.getFluidState().isEmpty()) {
						leak++;
						if (wf == null) wf = "open side at " + (x + q[0]) + "," + (y + q[1]) + "," + (z + q[2]) + " (" + path(ns) + ")";
					}
				}
			}
		}
		check(tag + ": every water block the layout placed is planned (inner canal, moat, quay canal, road ditch)", stray == 0, stray + (wf == null ? "" : "; " + wf));
		check(tag + ": every placed water block contained (solid or water on four sides and below)", leak == 0, leak + (wf == null ? "" : "; " + wf));
		check(tag + ": the inner canal is filled (stilts and sluice gate excepted)", inner >= 100, inner + " surface blocks");
		boolean wet = false;
		for (int x = -34; x <= 34 && !wet; x++) for (int z = -34; z <= 34 && !wet; z++) if (MireLayout.wetMoat(plan, x, z) && at(l, o, x, -2, z).is(Blocks.WATER)) wet = true;
		int rb = MireLayout.ravelinBase(o, hf);
		boolean rav = rb != Integer.MIN_VALUE && solid(at(l, o, 0, rb + 4, 52));
		info(tag + ": site " + o + ", wet moat " + wet + ", ravelin " + (rb == Integer.MIN_VALUE ? "skipped" : "base " + rb + (rav ? " built" : " MISSING")));
		if (rb != Integer.MIN_VALUE) check(tag + ": the ravelin stands where its ground allows it", rav, "base " + rb);
		sites++;
		canalBlocks += inner;
		if (wet) wetSites++;
		if (rav) ravelinSites++;
	}

	/** -Delsewhere.overhaul3.mire_layout=false: the hook declines every part, so Mirewick falls back to the shared layout plus CastleKit. */
	static void killSwitch(ServerLevel l) {
		CastleStyles.Style style = CastleStyles.of(id("zm_mirewick"));
		CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(0, 0, 0, 0, 0, 0));
		pl.style = style;
		pl.ground = (x, z) -> 64;
		boolean was = MireLayout.ENABLED, any = false;
		MireLayout.ENABLED = false;
		try {
			for (CastleLayout.Part p : CastleLayout.Part.values()) any |= MireLayout.build(p, new BlockPos(0, 64, 0), pl);
		} finally {
			MireLayout.ENABLED = was;
		}
		CastleLayout.Placer v1 = new CastleLayout.Placer(l, new BoundingBox(0, 0, 0, 0, 0, 0));
		v1.style = style;
		boolean fv1 = MireLayout.build(CastleLayout.Part.CASTLE, new BlockPos(0, 64, 0), v1);
		check("Mirewick layout kill switch and fv 1 starts fall back to the shared layout", !any && !fv1, "switch off: " + any + ", fv 1: " + fv1);
	}
}
