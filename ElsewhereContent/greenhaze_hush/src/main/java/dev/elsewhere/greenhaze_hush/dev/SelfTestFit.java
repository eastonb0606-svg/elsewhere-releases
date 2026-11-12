package dev.elsewhere.greenhaze_hush.dev;

import dev.elsewhere.greenhaze.GreenhazeBlocks;
import dev.elsewhere.greenhaze.GreenhazeMobs;
import dev.elsewhere.greenhaze_hush.Reg;
import dev.elsewhere.greenhaze_hush.world.BuiltStructure;
import dev.elsewhere.greenhaze_hush.world.fit.GhBeards;
import dev.elsewhere.greenhaze_hush.world.fit.GhFit;
import dev.elsewhere.greenhaze_hush.world.fit.GhPlans;
import dev.elsewhere.hush.HushBlocks;
import dev.elsewhere.hush.HushEntities;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;

/**
 * 3.0 structure looks + foundations (s-struct-greenhaze): every new look built live on fresh ground (counts of its working parts, no voids
 * under floors, no water that can flow), plans deterministic and NBT-stable, the beard mixin live on a real worldgen start, 2.x frozen.
 * Runs after the boss fights (ticks 1200-1260), one build per step.
 */
final class SelfTestFit {
	private SelfTestFit() {}

	static final String[] KINDS = {"great_hotbox", "bonfire_terrace", "fern_terrace_farms", "munchie_market", "sesh_hut", "lucid_spring",
		"greenhaze_return_rift", "listening_engine", "choir_geode", "silent_monastery", "hanging_undercity"};

	static void add(ServerLevel gh, ServerLevel hu) {
		if (gh == null || hu == null) return;
		// far from structuresAndLoot's fresh area (40k-80k), so its starts never share these chunks
		int f = (200000 + new java.util.Random().nextInt(100000)) & ~15;
		SelfTestRun.at(1200, () -> plans(gh));
		SelfTestRun.at(1203, () -> hotbox(gh, f, f));
		SelfTestRun.at(1206, () -> bonfire(gh, f, f + 200));
		SelfTestRun.at(1209, () -> farms(gh, f, f + 400));
		SelfTestRun.at(1212, () -> market(gh, f, f + 600));
		SelfTestRun.at(1215, () -> hutAndSpring(gh, f, f + 800));
		SelfTestRun.at(1220, () -> engine(hu, f, f));
		SelfTestRun.at(1225, () -> geode(hu, f + 160, f));
		SelfTestRun.at(1230, () -> monastery(hu, f + 300, f));
		SelfTestRun.at(1235, () -> undercity(hu, f + 400, f));
		SelfTestRun.at(1240, () -> beards(gh, f + 2000, f + 2000));
	}

	// ------------------------------------------------------------------------------------------------ plan checks (no world)

	static void plans(ServerLevel gh) {
		check("struct fit: kill switches default on (-Delsewhere.overhaul3.gh_struct_looks / gh_struct_beard)", GhFit.LOOKS && GhFit.BEARDS, "");
		boolean legacy = true;
		for (String k : KINDS) legacy &= BuiltStructure.BLUEPRINTS.containsKey(k);
		var pieceKey = BuiltInRegistries.STRUCTURE_PIECE.getKey(BuiltStructure.PIECE);
		var fitKey = BuiltInRegistries.STRUCTURE_PIECE.getKey(GhFit.PIECE);
		check("struct fit: 2.x frozen (11 blueprints, greenhaze_hush_built_piece) beside the new greenhaze_hush_fit_piece",
			legacy && pieceKey != null && pieceKey.getPath().equals("greenhaze_hush_built_piece") && fitKey != null && fitKey.getPath().equals("greenhaze_hush_fit_piece"),
			pieceKey + " / " + fitKey);
		// a rough synthetic site: rolling ground, a cave band with shelves, a rock roof at 158
		GhFit.Ground ground = new GhFit.Ground() {
			public int y(int x, int z) { return 70 + (int) Math.round(3 * Math.sin(x / 11.0) + 2.5 * Math.cos(z / 7.0)); }

			public boolean wet(int x, int z) { return false; }
		};
		GhFit.Columns cols = (x, y, z) -> (y <= 30 + ((x ^ z) & 3) || y >= 158 + (x & 1) || (y >= -45 && y <= -30 && Math.abs(x - 1000) > 30))
			? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
		var ctx = StructurePieceSerializationContext.fromLevel(gh);
		StringBuilder bad = new StringBuilder();
		int pieces = 0;
		boolean same = true, round = true, steps = true, beardsOk = true, builders = true;
		for (String k : KINDS) {
			int y = switch (k) { case "listening_engine" -> -20; case "choir_geode" -> -72; case "silent_monastery" -> 34; case "hanging_undercity" -> 150; default -> 71; };
			BlockPos o = new BlockPos(1000, y, 1000);
			List<GhFit.Piece> a = GhPlans.plan(k, o, 77L, ground, cols), b = GhPlans.plan(k, o, 77L, ground, cols);
			if (a.isEmpty()) { bad.append(k).append(" empty; "); builders = false; }
			pieces += a.size();
			for (int i = 0; i < a.size(); i++) {
				GhFit.Piece p = a.get(i);
				CompoundTag t = p.createTag(ctx);
				same &= i < b.size() && t.equals(b.get(i).createTag(ctx));
				StructurePiece back = GhFit.PIECE.load(ctx, t);
				round &= back.createTag(ctx).equals(t);
				if (!GhFit.BUILDERS.containsKey(p.kind)) { builders = false; bad.append(p.kind).append(" no builder; "); }
				if (p.kind.equals("hotbox_walk") || p.kind.equals("gallery") || p.kind.equals("market_street") || p.kind.equals("engine_tunnel")) {
					for (int j = 1; j < p.p(0); j++) if (Math.abs(p.p(1 + j) - p.p(j)) > 1) { steps = false; bad.append(p.kind).append(" step; "); }
				}
				if (p.kind.equals("bonfire")) for (int s = 0; s < 16; s++) for (int r = 1; r <= 4; r++) {
					int v = p.p(s * 4 + r - 1), in = r == 1 ? 0 : p.p(s * 4 + r - 2);
					if (v < in || v > in + 1 || v > r) { steps = false; bad.append("bonfire ring; "); }
				}
				if (p.beard != null) {
					boolean thin = p.adapt == TerrainAdjustment.BEARD_THIN && p.beard.maxY() - p.beard.minY() <= 3;
					beardsOk &= thin || p.adapt == TerrainAdjustment.ENCAPSULATE;
				}
			}
		}
		check("struct fit: all 11 looks plan pieces with registered builders; walks, streets, galleries, tunnels and seat rings step at most 1",
			builders && steps, pieces + " pieces " + bad);
		check("struct fit: plans are deterministic and every piece survives an NBT round trip", same && round, "same=" + same + " round=" + round);
		check("struct fit: beards are footprint-only (beard_thin, 4 high) or encapsulate a buried room", beardsOk, "");
	}

	// ------------------------------------------------------------------------------------------------ live builds

	record Tally(Map<Block, Integer> blocks, Map<String, Integer> loot, int litCandles, int litFires, int leaks) {
		int n(Block b) {
			return blocks.getOrDefault(b, 0);
		}

		int loot(String table) {
			return loot.getOrDefault("chests/" + table, 0);
		}
	}

	static BoundingBox span(List<GhFit.Piece> pieces) {
		BoundingBox bb = null;
		for (GhFit.Piece p : pieces) bb = bb == null ? p.getBoundingBox() : BoundingBox.encapsulating(bb, p.getBoundingBox());
		return bb;
	}

	/** Counts blocks, loot tables, lit candles and fires, and water that could flow (air or a replaceable beside or under it). */
	static Tally scan(ServerLevel l, BoundingBox bb) {
		Map<Block, Integer> m = new HashMap<>();
		Map<String, Integer> loot = new HashMap<>();
		int candles = 0, fires = 0, leaks = 0;
		for (BlockPos q : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
			BlockState s = l.getBlockState(q);
			if (s.isAir()) continue;
			m.merge(s.getBlock(), 1, Integer::sum);
			if (s.getBlock() instanceof CandleBlock && s.getValue(CandleBlock.LIT)) candles += s.getValue(CandleBlock.CANDLES);
			if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) fires++;
			if (s.hasBlockEntity() && l.getBlockEntity(q) instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null) loot.merge(c.getLootTable().identifier().getPath(), 1, Integer::sum);
			if (s.getFluidState().is(FluidTags.WATER)) {
				for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
					BlockState n = l.getBlockState(q.relative(d));
					if (n.isAir() || (n.canBeReplaced() && n.getFluidState().isEmpty())) leaks++;
				}
			}
		}
		return new Tally(m, loot, candles, fires, leaks);
	}

	/** Walk level on dry ground near (x, z): the first of 8 spots 96 apart with no water over the centre and 8 points at r 20 (worldgen rejects wet sites too). */
	static BlockPos surface(ServerLevel l, int x, int z) {
		// the live Ground reads the chunk itself (Level.getHeight answers min y for a chunk that is not visible yet) and skips trees
		GhFit.Ground g = GhFit.live(l);
		int bx = x;
		for (int k = 0; k < 8; k++) {
			int cx = x + 96 * k;
			boolean dry = true;
			for (int i = 0; i < 9 && dry; i++) {
				double a = i * Math.PI / 4;
				dry = !g.wet(cx + (i == 8 ? 0 : (int) Math.round(Math.cos(a) * 20)), z + (i == 8 ? 0 : (int) Math.round(Math.sin(a) * 20)));
			}
			if (dry) { bx = cx; break; }
		}
		return new BlockPos(bx, g.y(bx, z) + 1, z);
	}

	static boolean lootAt(ServerLevel l, BlockPos p, String table) {
		return l.getBlockEntity(p) instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null && c.getLootTable().identifier().getPath().equals("chests/" + table);
	}

	static <T extends Entity> int countAndClear(ServerLevel l, Class<T> c, BoundingBox bb) {
		List<T> list = l.getEntitiesOfClass(c, AABB.of(bb).inflate(2));
		list.forEach(Entity::discard);
		return list.size();
	}

	static void check(String name, boolean ok, String detail) {
		SelfTestRun.check(name, ok, detail);
	}

	static void hotbox(ServerLevel l, int x, int z) {
		BlockPos o = surface(l, x, z);
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "great_hotbox", o);
		Tally t = scan(l, span(ps));
		// Percolax reads its vents in +-17, y-1..+12 round the altar; a shutter must sit within 3 of its lever
		int levers = 0, paired = 0;
		for (BlockPos q : BlockPos.betweenClosed(o.offset(-17, -1, -17), o.offset(17, 12, 17))) {
			if (!l.getBlockState(q).is(GreenhazeBlocks.VENT_LEVER)) continue;
			levers++;
			BlockPos lv = q.immutable();
			boolean near = false;
			for (BlockPos v : BlockPos.betweenClosed(lv.offset(-3, -3, -3), lv.offset(3, 6, 3))) near |= l.getBlockState(v).is(GreenhazeBlocks.HOTBOX_VENT);
			if (near) paired++;
		}
		int voids = o.getY() - 2 <= l.getMinY() ? 999 : 0; // a floor on the world's bottom is a void too
		for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) if (dx * dx + dz * dz <= 196 && l.getBlockState(o.offset(dx, -2, dz)).isAir()) voids++;
		int glass = t.n(Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIME)) + t.n(Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.GREEN));
		long annexes = ps.stream().filter(p -> p.kind.equals("hotbox_annex")).count(), walks = ps.stream().filter(p -> p.kind.equals("hotbox_walk")).count();
		boolean altar = l.getBlockState(o).is(GreenhazeBlocks.HOTBOX_ALTAR), chest = lootAt(l, ps.get(0).frame().at(1, 0, 18), "great_hotbox");
		check("struct fit: Great Hotbox look (ribbed glass dome, 6 vents each beside its lever, altar, 2 annexes on own floors, 2 stair walks, stocked porch chest, no voids under the floor)",
			levers == 6 && paired == 6 && altar && glass >= 300 && annexes == 2 && walks == 2 && chest && voids == 0,
			levers + " levers (" + paired + " paired), altar " + altar + ", " + glass + " glass, " + annexes + " annexes, " + walks + " walks, porch chest " + chest + ", " + voids + " voids");
	}

	static void bonfire(ServerLevel l, int x, int z) {
		BlockPos o = surface(l, x, z);
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "bonfire_terrace", o);
		Tally t = scan(l, span(ps));
		// Grumblegut sniffs munchfruit in +-18 and y-4..+3 round the arena centre (snack altar north 5): every fruit-tree bush must be in reach
		int treeBushes = 0, inReach = 0;
		for (BlockPos q : BlockPos.betweenClosed(o.offset(-21, -8, -21), o.offset(21, 8, 21))) {
			if (!l.getBlockState(q).is(GreenhazeBlocks.MUNCHFRUIT_BUSH)) continue;
			boolean tree = false;
			for (Direction d : Direction.Plane.HORIZONTAL) tree |= l.getBlockState(q.relative(d)).is(Blocks.JUNGLE_LOG);
			if (!tree) continue;
			treeBushes++;
			if (Math.abs(q.getX() - o.getX()) <= 18 && Math.abs(q.getZ() - o.getZ()) <= 18 && q.getY() - o.getY() >= -4 && q.getY() - o.getY() <= 3) inReach++;
		}
		int voids = o.getY() - 2 <= l.getMinY() ? 999 : 0;
		for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) if (dx * dx + dz * dz <= 49 && l.getBlockState(o.offset(dx, -2, dz)).isAir()) voids++;
		BlockState altar = l.getBlockState(o.offset(0, 0, 5));
		check("struct fit: Bonfire Terrace look (stepped seat rings, one lit signal fire, snack altar, every munchfruit in Grumblegut's reach, stocked chest)",
			altar.is(GreenhazeBlocks.SNACK_ALTAR) && t.litFires() == 1 && treeBushes >= 16 && inReach == treeBushes && t.loot("bonfire_terrace") == 1
				&& t.n(Blocks.MUD_BRICK_STAIRS) >= 16 && voids == 0,
			t.litFires() + " lit fire, " + inReach + "/" + treeBushes + " fruit-tree bushes in reach, " + t.loot("bonfire_terrace") + " chest, " + t.n(Blocks.MUD_BRICK_STAIRS) + " risers, " + voids + " voids");
	}

	static void farms(ServerLevel l, int x, int z) {
		BlockPos o = surface(l, x, z);
		GhFit.buildPlan(l, "fern_terrace_farms", o);
		Tally t = scan(l, new BoundingBox(o.getX() - 16, o.getY() - 8, o.getZ() - 16, o.getX() + 16, o.getY() + 9, o.getZ() + 16));
		int crops = t.n(Blocks.WHEAT) + t.n(Blocks.CARROTS) + t.n(Blocks.POTATOES);
		check("struct fit: Fern Terrace Farms look (terraces with ripe crops, enclosed water, ferns, munchfruit, bunds, field shed chest)",
			t.n(Blocks.FARMLAND) >= 60 && crops >= 60 && t.n(Blocks.WATER) >= 3 && t.n(Blocks.LARGE_FERN) >= 2 && t.n(GreenhazeBlocks.MUNCHFRUIT_BUSH) >= 1
				&& t.loot("fern_terrace_farms") == 1 && t.leaks() == 0,
			t.n(Blocks.FARMLAND) + " farmland, " + crops + " crops, " + t.n(Blocks.WATER) + " water, " + t.leaks() + " leaks, " + t.loot("fern_terrace_farms") + " chest");
	}

	static void market(ServerLevel l, int x, int z) {
		BlockPos o = surface(l, x, z);
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "munchie_market", o);
		BoundingBox bb = span(ps);
		Tally t = scan(l, bb);
		int wool = 0;
		for (var e : t.blocks().entrySet()) if (e.getKey().defaultBlockState().is(net.minecraft.tags.BlockTags.WOOL)) wool += e.getValue();
		int bud = countAndClear(l, GreenhazeMobs.Budtender.class, bb);
		long stalls = ps.stream().filter(p -> p.kind.equals("market_stall")).count();
		check("struct fit: Munchie Market look (stepped souk street, 6 stalls on own pads with striped awnings, stocked chest, unlit fire pit, Budtender)",
			stalls == 6 && wool >= 100 && t.loot("munchie_market") == 1 && t.n(Blocks.CAMPFIRE) == 1 && t.litFires() == 0 && bud >= 1,
			stalls + " stalls, " + wool + " wool, " + t.loot("munchie_market") + " chest, " + t.n(Blocks.CAMPFIRE) + " fire pit, " + bud + " Budtender");
	}

	static void hutAndSpring(ServerLevel l, int x, int z) {
		BlockPos o = surface(l, x, z);
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "sesh_hut", o);
		BoundingBox bb = span(ps);
		Tally t = scan(l, bb);
		int deck = ps.get(0).o.getY(), ground = GhFit.live(l).y(o.getX() + 9, o.getZ() + 9) + 1;
		int bud = countAndClear(l, GreenhazeMobs.Budtender.class, bb);
		check("struct fit: Sesh Hut look (deck on 9 stilts to their own ground, conical hay roof, ladder, stocked chest, Budtender)",
			t.n(GreenhazeBlocks.KUSHWOOD_LOG) >= 18 && t.n(Blocks.HAY_BLOCK) >= 20 && t.n(Blocks.LADDER) >= 2 && t.loot("sesh_hut") == 1 && bud >= 1 && deck > o.getY(),
			t.n(GreenhazeBlocks.KUSHWOOD_LOG) + " log blocks, " + t.n(Blocks.HAY_BLOCK) + " hay, deck " + deck + " over ground " + o.getY() + " (" + ground + " nearby), " + bud + " Budtender");
		BlockPos s = surface(l, o.getX() + 60, z);
		List<GhFit.Piece> sp = GhFit.buildPlan(l, "lucid_spring", s);
		Tally w = scan(l, span(sp));
		check("struct fit: Lucid Spring look (pool in its hollow, weired rill, solid rim, no water that can flow, sunken offering chest)",
			w.n(Blocks.WATER) >= 30 && w.leaks() == 0 && w.loot("lucid_spring") == 1 && w.n(Blocks.SEA_LANTERN) >= 1,
			w.n(Blocks.WATER) + " water, " + w.leaks() + " leaks, " + w.loot("lucid_spring") + " chest");
	}

	static void engine(ServerLevel l, int x, int z) {
		BlockPos o = new BlockPos(x, -20, z);
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "listening_engine", o);
		Tally t = scan(l, span(ps));
		BlockPos throne = null;
		for (GhFit.Piece p : ps) if (p.kind.equals("engine")) throne = p.o.below(4);
		boolean seat = throne != null && l.getBlockState(throne).is(Blocks.REINFORCED_DEEPSLATE) && l.getBlockState(throne.above()).is(Blocks.SCULK_CATALYST);
		int membranes = 0;
		for (int[] c : new int[][] {{12, 0}, {-12, 0}, {0, 12}, {0, -12}}) if (l.getBlockState(o.offset(c[0], 4, c[1])).is(HushBlocks.TYMPANUM_MEMBRANE)) membranes++;
		GhFit.Piece tunnel = ps.stream().filter(p -> p.kind.equals("engine_tunnel")).findFirst().orElse(null);
		int cache = tunnel == null ? 0 : scan(l, tunnel.getBoundingBox()).loot("listening_engine");
		check("struct fit: Listening Engine look (ribbed vault, sculk cochlea, 4 membranes, throne where Auris answers, tunnel to a cave with the surveyor's chest)",
			seat && membranes == 4 && t.n(Blocks.SCULK) >= 20 && t.n(Blocks.DEEPSLATE_BRICKS) >= 100 && cache == 1,
			"throne " + seat + ", " + membranes + " membranes, " + t.n(Blocks.SCULK) + " sculk, " + t.n(Blocks.DEEPSLATE_BRICKS) + " rib, " + cache + " chest in the tunnel");
	}

	static void geode(ServerLevel l, int x, int z) {
		BlockPos o = new BlockPos(x, -72, z);
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "choir_geode", o);
		Tally t = scan(l, span(ps));
		int down = 0;
		for (BlockPos q : BlockPos.betweenClosed(o.offset(-24, 4, -24), o.offset(24, 26, 24))) {
			BlockState s = l.getBlockState(q);
			if (s.is(Blocks.AMETHYST_CLUSTER) && s.getValue(AmethystClusterBlock.FACING) == Direction.DOWN) down++;
		}
		check("struct fit: Choir Geode look (8 choir pillars of 7 notes, crystal stalactites, the rift home under the lake, reliquary chest)",
			t.n(HushBlocks.CHOIR_PILLAR) == 56 && down >= 10 && t.n(StubBlocks.RETURN_RIFT) == 2 && t.loot("choir_geode") == 1,
			t.n(HushBlocks.CHOIR_PILLAR) + " pillar blocks, " + down + " stalactites, " + t.n(StubBlocks.RETURN_RIFT) + " rift, " + t.loot("choir_geode") + " chest");
	}

	static void monastery(ServerLevel l, int x, int z) {
		var col = GhFit.live(l, true);
		int floor = 40;
		for (int y = 40; y > l.getMinY() + 8; y--) {
			if (!col.at(x, y, z).isAir() && col.at(x, y + 1, z).isAir() && col.at(x, y + 2, z).isAir()) { floor = y + 1; break; }
		}
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "silent_monastery", new BlockPos(x, floor, z));
		BoundingBox bb = span(ps);
		Tally t = scan(l, bb);
		int monks = l.getEntities(HushEntities.MUTE_ARCHIVIST, AABB.of(bb).inflate(2), e -> true).size();
		l.getEntities(HushEntities.MUTE_ARCHIVIST, AABB.of(bb).inflate(2), e -> true).forEach(Entity::discard);
		long cells = ps.stream().filter(p -> p.kind.equals("cell")).count(), galleries = ps.stream().filter(p -> p.kind.equals("gallery")).count();
		check("struct fit: Silent Monastery look (octagonal vaulted chapel, 3 rock cells on shelves, galleries on piers, lectern, stocked barrel, Mute Archivist, at most 12 lit candles)",
			cells == 3 && galleries == 3 && t.n(Blocks.LECTERN) == 1 && t.loot("silent_monastery") == 1 && t.n(Blocks.BARREL) == 4 && monks >= 1 && t.litCandles() <= 12
				&& t.n(HushBlocks.HUSHSTONE_BRICK_WALL) >= 20,
			cells + " cells, " + galleries + " galleries, " + t.n(Blocks.BARREL) + " barrels, " + monks + " archivist, " + t.litCandles() + " lit candles");
	}

	static void undercity(ServerLevel l, int x, int z) {
		// a site worldgen would take (fixedSiteOk: a roof over 4+ air within 12 above y 150 at the centre), looked for 96 apart
		GhFit.Columns live = GhFit.live(l, true);
		int sx = x;
		for (int k = 0; k < 24; k++) if (GhPlans.undercitySite(live, x + 96 * k, z, 150)) { sx = x + 96 * k; break; }
		List<GhFit.Piece> ps = GhFit.buildPlan(l, "hanging_undercity", new BlockPos(sx, 150, z));
		Tally t = scan(l, span(ps));
		long towers = ps.stream().filter(p -> p.kind.equals("tower")).count();
		// every tower hangs free: its soul lantern hangs under the spire tip with open air below it
		int free = 0;
		for (GhFit.Piece p : ps) {
			if (!p.kind.equals("tower")) continue;
			BlockPos tip = new BlockPos(p.o.getX(), p.o.getY() - p.p(1) - 2 * p.p(2) + 2, p.o.getZ());
			if (l.getBlockState(tip).is(Blocks.POLISHED_DEEPSLATE) && l.getBlockState(tip.below()).is(Blocks.SOUL_LANTERN) && l.getBlockState(tip.below(2)).isAir()) free++;
		}
		check("struct fit: Hanging Undercity look (octagonal towers hung from the real ceiling by chains, inverted spires, a chest per tower, the Ancients' door)",
			towers >= 1 && free == towers && t.n(StubBlocks.RETURN_RIFT) == 2 && t.loot("hanging_undercity") == towers && t.n(Blocks.IRON_CHAIN) >= 4 * towers && t.n(Blocks.LADDER) >= 6,
			towers + " towers (" + free + " hanging free), " + t.loot("hanging_undercity") + " chests, " + t.n(Blocks.IRON_CHAIN) + " chain, " + t.n(StubBlocks.RETURN_RIFT) + " rift, " + ps.stream().filter(p -> p.kind.equals("bridge")).count() + " bridges");
	}


	/** A real worldgen start on fresh ground: new pieces, and the beard mixin hands the noise its per-piece rule. */
	static void beards(ServerLevel l, int x, int z) {
		try {
			var reg = l.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			var holder = reg.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Reg.id("great_hotbox")));
			var found = l.getChunkSource().getGenerator().findNearestMapStructure(l, net.minecraft.core.HolderSet.direct(holder), new BlockPos(x, 0, z), 100, false);
			if (found == null) { check("struct fit: new worldgen starts use fit pieces and the beard mixin adds their rules", false, "great_hotbox not found"); return; }
			BlockPos at = found.getFirst();
			var chunk = l.getChunk(at.getX() >> 4, at.getZ() >> 4);
			StructureStart start = chunk.getStartForStructure(holder.value());
			List<GhFit.Piece> fit = new ArrayList<>();
			if (start != null) for (StructurePiece p : start.getPieces()) if (p instanceof GhFit.Piece fp) fit.add(fp);
			GhFit.Piece bearded = fit.stream().filter(p -> p.beard != null).findFirst().orElse(null);
			boolean rule = false;
			if (bearded != null) {
				var c = bearded.beard.getCenter();
				Beardifier b = Beardifier.forStructuresInChunk(l.structureManager(), new ChunkPos(c.getX() >> 4, c.getZ() >> 4));
				rule = GhBeards.rules(b).stream().anyMatch(r -> r.box().equals(bearded.beard) && r.terrainAdjustment() == bearded.adapt);
			}
			check("struct fit: new worldgen starts use fit pieces and the beard mixin adds their rules", start != null && fit.size() == start.getPieces().size() && fit.size() >= 5 && rule,
				(start == null ? "no start" : fit.size() + "/" + start.getPieces().size() + " fit pieces") + ", rule " + rule + " near " + at.toShortString());
		} catch (Throwable e) {
			check("struct fit: new worldgen starts use fit pieces and the beard mixin adds their rules", false, e.toString());
		}
	}
}
