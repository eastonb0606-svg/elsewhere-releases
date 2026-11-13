package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.world.AzmarCastle;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import dev.elsewhere.kingdom_court.world.RealmFit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * s-layout-azmar (3.0.0): Azmar's own fv 2 castle layout ({@link AzmarCastle}) on the REAL generator ground of several seeds. For each seed a dry
 * site of gentle relief is found with the overworld noise generator, its terrain is copied into this test world far from the court realm, the
 * castle, dungeon, wall and kit parts are built through the fv 2 path chunk by chunk (style id {@code elsewhere:sla_azmar}, which routes
 * through the layout like {@code elsewhere:azmar}), and the result is checked: the plan (pylons battered, obelisks, 18 hypostyle columns,
 * starred ceilings, clerestory, pools, palms, the undulating battered palm wall, the crypt, the palace), the smallest enclosure of the four
 * kingdoms, every named court place and spawn spot walkable, nothing floating or buried, water contained, no valuable block.
 */
final class SelfTestAzmarLayout {
	private SelfTestAzmarLayout() {}

	static final long[] SEEDS = {1L, 42L, 2026L, 31337L};
	static final Pattern VALUABLE = Pattern.compile("^(raw_)?(gold|iron|diamond|emerald|lapis|netherite|copper)_block$|^(chipped_|damaged_)?anvil$|^bell$|^beacon$|^gilded_blackstone$");
	static int built;

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
			check("Azmar layout (fv 2): built and checked on the real ground of at least 3 of " + SEEDS.length + " seeds", built >= 3, built + " sites");
			// routing: only fv 2 Azmar placers take the layout
			BlockPos far = new BlockPos(-30000, 64, 30000);
			BoundingBox none = new BoundingBox(far.getX(), far.getY(), far.getZ(), far.getX(), far.getY(), far.getZ());
			CastleLayout.Placer fv1 = new CastleLayout.Placer(l, none);
			fv1.style = CastleStyles.of(id("sla_azmar"));
			CastleLayout.Placer alder = new CastleLayout.Placer(l, none);
			alder.ground = (x, z) -> 64;
			boolean r1 = AzmarCastle.build(CastleLayout.Part.CASTLE, far, fv1), r2 = AzmarCastle.build(CastleLayout.Part.CASTLE, far, alder);
			boolean towns = false;
			if (fv1.style != null) {
				CastleLayout.Placer town = new CastleLayout.Placer(l, none);
				town.style = fv1.style;
				town.ground = (x, z) -> 64;
				towns = AzmarCastle.build(CastleLayout.Part.TOWN_WEST, far, town) || AzmarCastle.build(CastleLayout.Part.ROAD, far, town);
			}
			check("Azmar layout: an fv 1 Azmar realm, an Aldermark fv 2 realm and the towns/road keep their own builder", !r1 && !r2 && !towns, "fv1 " + r1 + ", aldermark " + r2 + ", towns/road " + towns);
			AzmarCastle.markTestSite(far, false);
			CastleStyles.clear(id("sla_azmar"));
			return 1;
		});
	}

	static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty();
	}

	static int h(ChunkGenerator gen, RandomState rs, ServerLevel l, int x, int z, Heightmap.Types t) {
		return gen.getBaseHeight(x, z, t, l, rs);
	}

	static void site(MinecraftServer srv, ServerLevel l, int k) {
		if (!AzmarCastle.ENABLED) {
			info("Azmar layout: switched off (-Delsewhere.overhaul3.azmar_layout=false), seed " + SEEDS[k] + " skipped");
			return;
		}
		long seed = SEEDS[k];
		var reg = srv.registryAccess();
		ChunkGenerator gen = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
		RandomState rs = RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, ((NoiseBasedChunkGenerator) gen).generatorSettings().value());
		int sea = gen.getSeaLevel();
		Identifier sid = id("sla_azmar");
		if (CastleStyles.of(sid) == null) CastleStyles.register(sid, CastleStyles.parse(JsonParser.parseString(SelfTestCastleFit.AZMAR).getAsJsonObject()));
		CastleStyles.Style style = CastleStyles.of(sid);
		String tag = "Azmar layout seed " + seed;
		// a dry site of gentle relief (the realm site rule) in a block of the seed's world no other case uses
		int bx = -9000 - 3000 * k, bz = 5400;
		BlockPos o = null;
		RealmFit.HeightField hf = null;
		for (int i = 0; i < 10000 && o == null; i++) {
			int x = bx + (i % 100) * 24, z = bz + (i / 100) * 24;
			int c = h(gen, rs, l, x, z, Heightmap.Types.WORLD_SURFACE_WG);
			if (c < sea || h(gen, rs, l, x, z, Heightmap.Types.OCEAN_FLOOR_WG) < c) continue;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
				int g = h(gen, rs, l, x + 36 * a, z + 36 * b, Heightmap.Types.WORLD_SURFACE_WG);
				if (h(gen, rs, l, x + 36 * a, z + 36 * b, Heightmap.Types.OCEAN_FLOOR_WG) < g) g = Integer.MIN_VALUE / 2;
				lo = Math.min(lo, g);
				hi = Math.max(hi, g);
			}
			if (lo < sea || hi - lo > 8) continue;
			BlockPos cand = new BlockPos(x, c, z);
			hf = new RealmFit.HeightField(gen, rs, l, seed, cand, c, c);
			o = cand;
		}
		if (o == null || hf == null || style == null) {
			info(tag + ": no dry gentle site found in the search block");
			return;
		}
		// copy the generator terrain (ground and sea-level water) into this test world, then build the castle parts (fv 2, chunk by chunk)
		int x0 = o.getX() - 44, x1 = o.getX() + 44, z0 = o.getZ() - 44, z1 = o.getZ() + 44;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, true);
		int w = z1 - z0 + 1;
		int[] top = new int[(x1 - x0 + 1) * w], fl = new int[top.length];
		int base = Integer.MAX_VALUE;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
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
			int i = (x - x0) * w + (z - z0);
			for (int y = base; y < fl[i]; y++) l.setBlock(m.set(x, y, z), y == fl[i] - 1 ? (top[i] > fl[i] ? sand : grass) : stone, 2);
			for (int y = fl[i]; y < top[i]; y++) l.setBlock(m.set(x, y, z), water, 2);
			for (int y = top[i]; y <= top[i] + 50; y++) if (!l.getBlockState(m.set(x, y, z)).isAir()) l.setBlock(m, air, 2);
		}
		boolean took = true;
		for (CastleLayout.Part part : new CastleLayout.Part[] {CastleLayout.Part.CASTLE, CastleLayout.Part.DUNGEON, CastleLayout.Part.WALL, CastleLayout.Part.KIT_CASTLE}) {
			BoundingBox box = CastleLayout.fitBox(part, o);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				CastleLayout.Placer pl = new CastleLayout.Placer(l, new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15));
				pl.style = style;
				pl.ground = hf;
				took &= AzmarCastle.build(part, o, pl);
			}
		}
		AzmarCastle.markTestSite(o, true);
		info(tag + ": site " + o + ", sea level " + sea + ", palace terrace +" + AzmarCastle.palaceLift(o, hf) + " above the top terrace");
		check(tag + ": the Azmar style routes CASTLE, DUNGEON, WALL and KIT_CASTLE through the Azmar layout", took, null);
		int before = SelfTest.fail;
		checks(l, tag, o, hf);
		if (SelfTest.fail == before) built++;
		AzmarCastle.markTestSite(o, false);
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.setChunkForced(cx, cz, false);
	}

	static BlockState at(ServerLevel l, BlockPos o, int x, int y, int z) {
		return l.getBlockState(o.offset(x, y, z));
	}

	static boolean is(ServerLevel l, BlockPos o, int x, int y, int z, Block b) {
		return at(l, o, x, y, z).is(b);
	}

	/** A spawn or routine spot: nothing to collide with at the feet (a carpet is fine) and the head, something to stand on below. */
	static boolean walkable(ServerLevel l, BlockPos p) {
		VoxelShape feet = l.getBlockState(p).getCollisionShape(l, p), head = l.getBlockState(p.above()).getCollisionShape(l, p.above());
		VoxelShape below = l.getBlockState(p.below()).getCollisionShape(l, p.below());
		boolean feetOk = feet.isEmpty() || feet.max(Direction.Axis.Y) <= 0.1;
		boolean ground = !feet.isEmpty() || !below.isEmpty() && below.max(Direction.Axis.Y) >= 0.5;
		return feetOk && ground && head.isEmpty() && l.getBlockState(p).getFluidState().isEmpty();
	}

	static void checks(ServerLevel l, String tag, BlockPos o, RealmFit.HeightField hf) {
		Block yellow = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW), blue = Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE), cyan = Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN);
		// the throne and the named places
		BlockPos throne = CastleLayout.throne(o);
		check(tag + ": CastleLayout.throne is the ebony (dark oak) throne on the dais of the Azmar throne hall", throne.equals(o.offset(0, 3, -22)) && l.getBlockState(throne).is(Blocks.DARK_OAK_STAIRS)
			&& is(l, o, 0, 2, -22, Blocks.POLISHED_BLACKSTONE_BRICKS), throne + " " + l.getBlockState(throne).getBlock());
		// pylons: battered front, cavetto cornice, roof and parapet, banner niches, the scarab over the gate
		boolean pylons = true;
		for (int s = -1; s <= 1; s += 2) {
			pylons &= solid(at(l, o, s * 8, 0, 20)) && at(l, o, s * 8, 13, 20).isAir() && solid(at(l, o, s * 8, 13, 17)) && is(l, o, s * 7, 15, 15, Blocks.SANDSTONE)
				&& is(l, o, s * 11, 15, 15, Blocks.CUT_SANDSTONE) && is(l, o, s * 6, 14, 11, Blocks.SANDSTONE_STAIRS) && is(l, o, s * 6, 10, 18, Blocks.WALL_BANNER.blue());
		}
		pylons &= is(l, o, 0, 8, 20, yellow) && is(l, o, 2, 8, 20, blue);
		check(tag + ": twin battered pylons (face steps back 1 per 4 courses to 16 high), cavetto cornice, parapet, banner niches, gold scarab over the gate", pylons, null);
		// obelisks
		check(tag + ": obelisk pair with yellow pyramidions before the pylons", is(l, o, -6, 14, 23, yellow) && is(l, o, 6, 14, 23, yellow) && is(l, o, 6, 4, 22, Blocks.CHISELED_SANDSTONE), null);
		// hypostyle: 18 columns (turquoise band under each capital), starred ceilings, clerestory grille
		int cols = 0;
		for (int z : new int[] {-12, -8, -4}) for (int s = -1; s <= 1; s += 2) {
			if (is(l, o, s * 3, 1, z, Blocks.CUT_SANDSTONE) && is(l, o, s * 3, 8, z, cyan) && is(l, o, s * 3, 5, z, blue)) cols++;
			if (is(l, o, s * 7, 1, z, Blocks.CUT_SANDSTONE) && is(l, o, s * 7, 5, z, cyan)) cols++;
			if (is(l, o, s * 11, 1, z, Blocks.CUT_SANDSTONE) && is(l, o, s * 11, 5, z, cyan)) cols++;
		}
		int stars = 0, sky = 0, grille = 0;
		for (int x = -12; x <= 12; x++) for (int z = -13; z <= -3; z++) {
			if (Math.abs(x) < 4) continue;
			if (is(l, o, x, 7, z, yellow)) stars++;
			else if (is(l, o, x, 7, z, blue)) sky++;
		}
		for (int z = -13; z <= -3; z++) for (int s = -1; s <= 1; s += 2) if ((z & 1) != 0 && at(l, o, s * 4, 9, z).isAir() && at(l, o, s * 4 + s, 9, z).isAir()) grille++;
		info(tag + ": hypostyle " + cols + " columns, ceiling " + stars + " stars in " + (stars + sky) + " blocks, " + grille + " clerestory openings");
		check(tag + ": hypostyle hall: 18 lotus and papyrus columns, blue ceiling with yellow stars, clerestory over the nave", cols == 18 && stars > 0 && sky > stars && grille == 12, cols + " columns, " + stars + "/" + sky + " stars, " + grille + " openings");
		// court pools and palms
		int pool = 0;
		for (int s = -1; s <= 1; s += 2) for (int x = 6; x <= 9; x++) for (int z = 2; z <= 4; z++) if (at(l, o, s * x, -1, z).is(Blocks.WATER)) pool++;
		int[][] palms = {{-17, 1, -11}, {17, 1, -11}, {-17, 1, -4}, {17, 1, -4}, {-11, 0, 11}, {11, 0, 11}, {-11, 0, 24}, {-18, 0, 24}, {-25, 0, 25}, {24, 0, 25}};
		int palm = 0;
		for (int[] p : palms) if (is(l, o, p[0], p[1], p[2], Blocks.JUNGLE_LOG) && is(l, o, p[0], p[1] + 3, p[2], Blocks.JUNGLE_LOG) && solid(at(l, o, p[0], p[1] - 1, p[2]))) palm++;
		check(tag + ": palm court: two lapis pools (24 water blocks) and 10 palms standing on the ground", pool == 24 && palm == palms.length, pool + " water, " + palm + " palms");
		// the palm wall: undulating top, battered outer course, batter foot on solid ground
		Set<Integer> tops = new HashSet<>();
		int badTop = 0, batter = 0, batterBad = 0;
		for (int z = -26; z <= 19; z++) {
			int t = -1;
			for (int y = 4; y <= 8; y++) if (is(l, o, -22, y, z, Blocks.SMOOTH_SANDSTONE)) t = y;
			if (t < 5 || t > 7) badTop++;
			else tops.add(t);
		}
		for (int z = -27; z <= 20; z++) for (int s = -1; s <= 1; s += 2) {
			BlockState b = at(l, o, s * 24, 0, z);
			if (b.is(Blocks.SANDSTONE_STAIRS)) batter++;
			if (!b.is(Blocks.SANDSTONE_STAIRS) || !solid(at(l, o, s * 24, -1, z)) || !is(l, o, s * 23, 3, z, Blocks.MUD_BRICK_STAIRS) && z != -27 && z != 20) batterBad++;
		}
		check(tag + ": palm wall: mud brick, top undulating 5..7, battered outer course and sandstone batter foot on solid ground", badTop == 0 && tops.size() >= 2 && batter == 96 && batterBad == 0,
			"tops " + tops + ", " + badTop + " bad, " + batter + " batter, " + batterBad + " unsound");
		// the smallest enclosure (measured on the batter ring); Aldermark's curtain is 61 x 61
		int ex = 0, nz = 0, sz = 0;
		for (int x = 20; x <= 40; x++) if (ex == 0 && is(l, o, x, 0, 0, Blocks.SANDSTONE_STAIRS)) ex = x;
		for (int z = -20; z >= -40; z--) if (nz == 0 && is(l, o, 0, 0, z, Blocks.SANDSTONE_STAIRS)) nz = z;
		for (int z = 15; z <= 40; z++) if (sz == 0 && is(l, o, 18, 0, z, Blocks.SANDSTONE_STAIRS)) sz = z;
		int width = 2 * ex + 1, depth = sz - nz + 1;
		info(tag + ": enclosure " + width + " x " + depth + " (Aldermark 61 x 61)");
		check(tag + ": Azmar's castle is the smallest of the four (enclosure under 61 x 61)", ex > 0 && nz < 0 && sz > 0 && width < 61 && depth < 61, width + " x " + depth);
		// every named place and spawn spot walkable
		List<BlockPos> spots = new ArrayList<>();
		BlockPos hall = CastleLayout.hall(o), gate = CastleLayout.gateInside(o), yard = CastleLayout.yard(o), barracks = CastleLayout.barracks(o);
		spots.add(CastleLayout.throneFront(o));
		spots.add(hall);
		spots.add(gate);
		spots.add(yard);
		spots.add(barracks);
		spots.add(CastleLayout.stables(o));
		spots.add(CastleLayout.balcony(o, true));
		spots.add(CastleLayout.balcony(o, false));
		spots.add(CastleLayout.wallTop(o, true));
		spots.add(CastleLayout.wallTop(o, false));
		spots.addAll(CastleLayout.yardLoop(o));
		for (int i = 0; i < 2; i++) spots.add(CastleLayout.at(o, i * 6 - 3, 0, -18));
		spots.add(CastleLayout.at(o, -14, 0, -4));
		spots.add(CastleLayout.at(o, 4, 0, -20));
		spots.add(CastleLayout.at(o, 0, 1, -22));
		spots.add(CastleLayout.at(o, 0, 0, 10));
		spots.add(gate.offset(0, 0, -4));
		spots.add(yard.offset(14, 0, -4));
		for (int slot = 0; slot < 3; slot++) spots.add(gate.offset(4 + slot * 3, 0, -3));
		spots.add(hall.offset(0, 0, 4));
		spots.add(hall.offset(0, 0, 6));
		spots.add(hall.offset(5, 0, 4));
		spots.add(yard.offset(6, 0, 0));
		spots.add(yard.offset(-4, 0, -6));
		// squad members stand in the blocks x-2..x+2 one row behind their post (KingdomApi.spawnSquad); the barracks squad up to 3 rows
		for (BlockPos p : new BlockPos[] {hall, gate, yard, barracks}) for (int dx = -2; dx <= 2; dx++) spots.add(p.offset(dx, 0, -1));
		for (int dx = -2; dx <= 2; dx++) {
			spots.add(barracks.offset(dx, 0, -3));
			spots.add(barracks.offset(dx, 0, -4));
		}
		int bad = 0;
		String first = null;
		for (BlockPos p : spots) {
			if (walkable(l, p)) continue;
			bad++;
			if (first == null) first = "not walkable at " + p.subtract(o) + " (" + l.getBlockState(p).getBlock() + " / " + l.getBlockState(p.below()).getBlock() + ")";
		}
		check(tag + ": every named court place, ruler post, routine anchor and squad spot lies in the Azmar plan and is walkable", bad == 0 && !hall.equals(o.offset(0, 0, -15)), bad + " of " + spots.size() + (first == null ? "" : "; " + first));
		int gates = 0, braziers = 0;
		for (BlockPos g : CastleLayout.gates(o)) if (l.getBlockState(g).hasProperty(FenceGateBlock.OPEN)) gates++;
		for (BlockPos b : CastleLayout.braziers(o)) if (l.getBlockState(b).hasProperty(CampfireBlock.LIT)) braziers++;
		check(tag + ": the gate leaves are fence gates (5) and the braziers are campfires (6) for the court's day/night schedule", gates == 5 && braziers == 6, gates + " gates, " + braziers + " braziers");
		BlockPos other = o.offset(997, 0, 0);
		check(tag + ": a realm that is not an Azmar layout site keeps the classic places", CastleLayout.throne(other).equals(other.offset(0, 1, -24)) && AzmarCastle.place(other, 0, 0, -15) == null, CastleLayout.throne(other));
		// sphinx avenue: each sphinx on its own ground, supported, not buried, or skipped by the ground rule (water, drop over 3)
		int placed = 0, unsound = 0;
		for (int s = -1; s <= 1; s += 2) for (int z0 : AzmarCastle.SPHINX_Z) {
			int b = AzmarCastle.sphinxBase(o, hf, s, z0);
			if (b == Integer.MIN_VALUE) continue;
			placed++;
			boolean ok = is(l, o, s * 4, b + 2, z0, Blocks.CHISELED_SANDSTONE) && is(l, o, s * 3, b, z0, blue) && at(l, o, s * 4, b + 4, z0).isAir() && at(l, o, s * 6, b + 2, z0 + 1).isAir();
			for (int kx = 3; kx <= 7; kx++) for (int z = z0; z <= z0 + 1; z++) ok &= solid(at(l, o, s * kx, b, z)) && solid(at(l, o, s * kx, b - 1, z));
			if (!ok) unsound++;
		}
		int ceil = 0;
		for (int y = 6; y <= 14; y++) if (!at(l, o, 0, y, -20).isAir()) ceil++;
		check(tag + ": w6 throne hall has its ceiling and roof over the dais (no sky over (0, 6..14, -20))", ceil >= 2, ceil);
		check(tag + ": sphinx avenue: every sphinx stands on its own ground, supported and not buried (at least 4 of 8 placed)", placed >= 4 && unsound == 0, placed + " placed, " + unsound + " unsound");
		// the pad: no air or fluid under the paving (outside the pools, the lake and the crypt), no stray water, water contained
		int holes = 0, stray = 0, leak = 0;
		String why = null;
		for (int x = -AzmarCastle.PAD; x <= AzmarCastle.PAD; x++) for (int z = -AzmarCastle.PAD; z <= AzmarCastle.PAD; z++) {
			boolean pools = Math.abs(x) >= 5 && Math.abs(x) <= 10 && z >= 1 && z <= 5, lake = x >= 11 && x <= 20 && z >= 22 && z <= 27;
			boolean crypt = x >= -20 && x <= -12 && z >= -24 && z <= -17;
			if (!pools && !lake && !crypt && (!solid(at(l, o, x, -1, z)) && !at(l, o, x, -1, z).is(Blocks.LADDER) || !solid(at(l, o, x, -2, z)))) {
				holes++;
				if (why == null) why = "hole under the pad at " + x + "," + z;
			}
			for (int y = -6; y <= 12; y++) {
				BlockState b = at(l, o, x, y, z);
				if (b.getFluidState().isEmpty()) continue;
				boolean mine = pools && (y == -1) && Math.abs(x) >= 6 && Math.abs(x) <= 9 && z >= 2 && z <= 4 || lake && y >= -2 && y <= -1 && x >= 12 && x <= 19 && z >= 23 && z <= 26;
				if (!mine) {
					stray++;
					if (why == null) why = "stray fluid at " + x + "," + y + "," + z;
					continue;
				}
				for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
					BlockState n = at(l, o, x + d[0], y + d[1], z + d[2]);
					if (!solid(n) && n.getFluidState().isEmpty()) {
						leak++;
						if (why == null) why = "open side at " + (x + d[0]) + "," + (y + d[1]) + "," + (z + d[2]);
					}
				}
			}
		}
		check(tag + ": nothing floating on the pad, water only in the pools and the sacred lake, every water block contained", holes == 0 && stray == 0 && leak == 0, holes + " holes, " + stray + " stray, " + leak + " open" + (why == null ? "" : "; " + why));
		check(tag + ": sacred lake with ghat steps and papyrus, lily pads in the pools", is(l, o, 12, -1, 24, Blocks.SANDSTONE_STAIRS) && is(l, o, 15, 1, 22, Blocks.SUGAR_CANE) && is(l, o, 7, 0, 3, Blocks.LILY_PAD) && is(l, o, -7, 0, 3, Blocks.LILY_PAD), null);
		// crypt and palace
		check(tag + ": the crypt under the naos: dungeon chest, sarcophagus, ladder up to the trapdoor", is(l, o, -19, -6, -18, Blocks.CHEST) && is(l, o, -13, -6, -23, Blocks.LADDER) && is(l, o, -13, 0, -23, Blocks.LADDER)
			&& is(l, o, -13, 1, -23, Blocks.ACACIA_TRAPDOOR) && is(l, o, -17, -5, -21, yellow), null);
		int pl = AzmarCastle.palaceLift(o, hf), f = 1 + pl;
		check(tag + ": the palace on its terrace (+" + pl + "): door open, two beds, cache chest, kiosk and awning on the roof", at(l, o, 12, f + 1, -18 - pl).isAir() && at(l, o, 12, f + 2, -18 - pl).isAir()
			&& at(l, o, 20, f + 1, -24).getBlock() instanceof BedBlock && at(l, o, 20, f + 1, -22).getBlock() instanceof BedBlock && is(l, o, 20, f + 1, -19, Blocks.CHEST)
			&& is(l, o, 17, f + 11, -21, yellow) && at(l, o, 13, f + 9, -21).is(Blocks.WOOL.pick(DyeColor.WHITE)) && walkable(l, o.offset(10, f + 1, -19 - pl)), null);
		// no valuable block anywhere in the realm footprint
		int val = 0;
		String vb = null;
		for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) for (int y = -16; y <= 32; y++) {
			Block b = at(l, o, x, y, z).getBlock();
			if (b == Blocks.AIR || b == Blocks.STONE) continue;
			String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (VALUABLE.matcher(p).find() || p.contains("glass")) {
				val++;
				if (vb == null) vb = p + " at " + x + "," + y + "," + z;
			}
		}
		check(tag + ": no valuable block (gold, iron, lapis, ... blocks, anvils, bells, beacons) and no glass in the Azmar castle", val == 0, val + (vb == null ? "" : "; " + vb));
	}
}
