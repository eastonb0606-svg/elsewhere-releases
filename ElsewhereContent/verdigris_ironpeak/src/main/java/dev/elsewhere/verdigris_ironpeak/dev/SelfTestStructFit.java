package dev.elsewhere.verdigris_ironpeak.dev;

import static dev.elsewhere.verdigris_ironpeak.dev.SelfTest.check;
import static dev.elsewhere.verdigris_ironpeak.dev.SelfTest.f;

import dev.elsewhere.boss.galvanox.GalvanoxArena;
import dev.elsewhere.boss.hrolf.ProvingGroundsArena;
import dev.elsewhere.boss.thurgrim.ThurgrimArena;
import dev.elsewhere.boss.verdigrim.VerdigrimArena;
import dev.elsewhere.ironpeak.IronpeakBlocks;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.MapOffers;
import dev.elsewhere.verdigris_ironpeak.Reg;
import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakContent;
import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import dev.elsewhere.verdigris_ironpeak.world.ProceduralStructure;
import dev.elsewhere.verdigris_ironpeak.world.fit.FitBuilds;
import dev.elsewhere.verdigris_ironpeak.world.fit.Terrain;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 3.0 structure looks and per-piece ground (s-struct-verdigris). Every fit blueprint is built on a synthetic slope or sea bed with a
 * known ground, then measured: floor cells standing on continuous support (float metric), functional arena blocks at their offsets,
 * no valuable blocks or lit campfires, the trader spots free. Then real world-generated starts that the earlier checks already loaded
 * are checked the same way (version-2 piece with a saved ground model, floors supported on real terrain).
 */
final class SelfTestStructFit {
	private SelfTestStructFit() {}

	static final Set<String> VALUABLE = Set.of("iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block", "redstone_block",
		"beacon", "copper_block", "waxed_copper_block", "exposed_copper", "weathered_copper", "oxidized_copper", "waxed_exposed_copper", "waxed_weathered_copper",
		"waxed_oxidized_copper", "raw_iron_block", "raw_gold_block", "raw_copper_block", "amethyst_block");

	static void run(MinecraftServer server) {
		ServerLevel sea = server.getLevel(Fx.VERDIGRIS_SEA);
		if (sea == null) {
			check(false, "struct fit: Verdigris Sea missing");
			return;
		}
		check(FitBuilds.ENABLED && Blueprint.FIT.size() == 11, f("struct fit: 11 terrain-fit blueprints registered, kill switch vi_structures on (%d)", Blueprint.FIT.size()));
		int i = 0;
		// land kits on a slope rising 1 in 3 to the north (so the clan hold's front faces south) and 1 in 5 to the east
		for (String bp : List.of("clan_hold", "climbers_cairn", "proving_grounds", "summit_throne", "return_rift_ironpeak"))
			synthetic(sea, bp, 22000 + 112 * i++, false);
		for (String bp : List.of("engineers_atoll", "tidelock_vault", "drowned_cathedral", "lighthouse", "clockwork_rig", "return_rift_verdigris_sea"))
			synthetic(sea, bp, 22000 + 112 * i++, true);
		real(server);
	}

	/** Builds one fit blueprint on prepared ground and measures it. */
	static void synthetic(ServerLevel level, String name, int cx, boolean wet) {
		int cz = 22000;
		Blueprint bp = Blueprint.FIT.get(name);
		int r = bp.radius() + 3;
		for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) level.getChunk(x, z, ChunkStatus.FULL, true);
		int seaTop = FitBuilds.SEA_TOP;
		IntBinaryOperator ground;
		if (!wet) ground = (x, z) -> 150 + (cz - z) / 3 + (x - cx) / 5;
		else if (name.equals("engineers_atoll")) ground = (x, z) -> Math.max(seaTop - 12, seaTop + 3 - Math.max(0, (int) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - 6)); // an atoll islet
		else if (name.equals("lighthouse")) ground = (x, z) -> seaTop - 30 + Math.max(0, 22 - (int) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz))); // a shoal under the tower
		else ground = (x, z) -> 62 + (cz - z) / 4 + (x - cx) / 6; // sea bed rising to the north
		Terrain t = Terrain.synthetic(cx, cz, bp.radius() + 2, ground, wet ? seaTop : Integer.MIN_VALUE);
		int g0 = ground.applyAsInt(cx, cz);
		int oy = switch (name) {
			case "clockwork_rig" -> seaTop + 1;
			case "return_rift_verdigris_sea" -> Math.max(seaTop + 4, g0 + 2);
			case "lighthouse" -> Math.max(seaTop + 25, t.maxGround() + 12);
			case "tidelock_vault" -> g0;
			default -> g0 + 1;
		};
		// prepare the columns: stone up to the ground, water to the sea line (wet kits), air above
		for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
			int g = ground.applyAsInt(x, z);
			for (int y = g - 6, top = Math.max(g + 36, oy + bp.height() + 2); y <= top; y++) {
				BlockState want = y <= g ? Blocks.STONE.defaultBlockState() : wet && y <= seaTop ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
				BlockPos p = new BlockPos(x, y, z);
				if (level.getBlockState(p) != want) level.setBlock(p, want, Block.UPDATE_CLIENTS);
			}
		}
		BlockPos o = new BlockPos(cx, oy, cz);
		RandomSource rnd = RandomSource.create(o.asLong());
		BlockSink s = new BlockSink(level, o, null, rnd, t);
		long t0 = System.nanoTime();
		bp.build(s, rnd);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		// float metric: floor cells with an air or water gap between them and the ground
		List<int[]> cells = FitBuilds.floorCells(name, s);
		int floating = floating(level, o, s, cells);
		// valuables, lit campfires, block count in the box
		int valuable = 0, campfires = 0, blocks = 0;
		int R = bp.radius();
		StringBuilder found = new StringBuilder();
		for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
			// from this column's prepared ground down 6: natural ore veins and geodes below it are not ours
			int scanFrom = Math.max(-bp.depth(), Math.max(level.getMinY() - oy, ground.applyAsInt(cx + x, cz + z) - 6 - oy));
			for (int y = scanFrom; y <= bp.height(); y++) {
			BlockState b = level.getBlockState(o.offset(x, y, z));
			if (b.isAir()) continue;
			String id = BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
			if (VALUABLE.contains(id) && valuable++ < 3) found.append(f(" %s at (%d, %d, %d)", id, x, y, z));
			if (b.getBlock() instanceof CampfireBlock && b.getValue(CampfireBlock.LIT)) campfires++;
			blocks++;
			}
		}
		check(floating == 0 && (!cells.isEmpty() || name.equals("clockwork_rig") || name.equals("return_rift_verdigris_sea")),
			f("struct fit %s: every floor cell stands on continuous support to the ground (%d of %d floating) on a %s", name, floating, cells.size(), wet ? "sea bed" : "1-in-3 slope"));
		check(valuable == 0 && campfires == 0, f("struct fit %s: no valuable blocks (%d%s) and no lit campfires (%d)", name, valuable, found, campfires));
		VerdigrisIronpeakContent.LOG.info("[selftest] struct fit {}: built in {} ms at {}, {} non-air blocks in its box", name, ms, o.toShortString(), blocks);
		switch (name) {
			case "clan_hold" -> {
				BlockPos spot = o.offset(2, 1, 2);
				boolean roofed = false;
				for (int y = 2; y <= 10; y++) if (!level.getBlockState(spot.above(y)).isAir()) roofed = true;
				int hearth = count(level, o, 8, IronpeakBlocks.HEARTHSTONE);
				check(level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir() && roofed && hearth == 1 && level.getBlockState(o.offset(0, 1, 5)).is(Blocks.SPRUCE_DOOR),
					"struct fit clan_hold: the quartermaster's spot is free under the roof, one hearthstone, the door in the downhill gable");
				int berm = 0;
				for (int z = -6; z <= 4; z++) if (!level.getBlockState(o.offset(6, 2, z)).isAir() && !level.getBlockState(o.offset(-6, 2, z)).isAir()) berm++;
				check(berm >= 9, f("struct fit clan_hold: earth-sheltered (soil banked against both side walls on %d of 11 rows)", berm));
			}
			case "climbers_cairn" -> check(level.getBlockState(o.offset(0, 8, 0)).is(IronpeakBlocks.PRAYER_FLAGS) && level.getBlockState(o.offset(0, 0, -3)).is(Blocks.CHEST),
				"struct fit climbers_cairn: waymark pole with prayer flags on the stack, chest at its foot");
			case "proving_grounds" -> {
				int seats = 0;
				for (int x = -10; x <= 10; x++) for (int z = -23; z <= -15; z++) for (int y = 1; y <= 6; y++) if (level.getBlockState(o.offset(x, y, z)).is(Blocks.STONE_BRICK_STAIRS)) seats++;
				int[] H = ProvingGroundsArena.HORN;
				check(level.getBlockState(o.offset(H[0], H[1], H[2])).is(IronpeakBlocks.PROVING_CHALLENGE_HORN) && seats >= 40,
					f("struct fit proving_grounds: the Challenge Horn at its offset, %d spectator seats on the uphill tiers", seats));
			}
			case "summit_throne" -> {
				int rods = 0;
				for (int k = 0; k < ThurgrimArena.OBELISKS; k++) if (level.getBlockState(ThurgrimArena.obelisk(o, k).above(6)).getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock) rods++;
				int[] D = ThurgrimArena.DRUM;
				check(rods == 6 && level.getBlockState(o.offset(D[0], D[1], D[2])).is(IronpeakBlocks.SUMMIT_WAR_DRUM), f("struct fit summit_throne: war drum and the 6 storm obelisks at their offsets (%d rods)", rods));
			}
			case "tidelock_vault" -> check(level.getBlockState(o.offset(0, 1, -3)).is(Blocks.VAULT) && level.getBlockEntity(o.offset(0, 1, -3)) instanceof net.minecraft.world.level.block.entity.vault.VaultBlockEntity v
				&& v.getConfig().keyItem().is(VerdigrisItems.TIDE_KEY) && !level.getBlockState(o.offset(0, 6, -3)).isAir() && !level.getBlockState(o.offset(0, 6, -3)).is(Blocks.WATER),
				"struct fit tidelock_vault: the Tide Key vault at (0, 1, -3), the back half buried in sediment");
			case "drowned_cathedral" -> {
				int ok = level.getBlockState(o.offset(VerdigrimArena.ORGAN[0], VerdigrimArena.ORGAN[1], VerdigrimArena.ORGAN[2])).is(VerdigrisBlocks.DROWNED_ORGAN) ? 1 : 0;
				for (int k = 0; k < VerdigrimArena.PIPES; k++) if (level.getBlockState(VerdigrimArena.noteBlock(o, k)).is(Blocks.NOTE_BLOCK)) ok++;
				for (int[] b : VerdigrimArena.BELLS) if (level.getBlockState(o.offset(b[0], b[1], b[2])).is(Blocks.BELL)) ok++;
				int buttress = 0;
				for (int k = 0; k < 12; k++) {
					double a = Math.toRadians(30 * k + 1.5);
					if (!level.getBlockState(o.offset((int) Math.round(Math.cos(a) * 19.5), -3, (int) Math.round(Math.sin(a) * 19.5))).isAir()
						&& !level.getBlockState(o.offset((int) Math.round(Math.cos(a) * 19.5), -3, (int) Math.round(Math.sin(a) * 19.5))).is(Blocks.WATER)) buttress++;
				}
				check(ok == 13 && buttress >= 10, f("struct fit drowned_cathedral: organ, 8 note blocks and 4 bells at their offsets (%d of 13), %d buttresses stepping to the bed", ok, buttress));
			}
			case "lighthouse" -> {
				int rods = 0;
				for (int k = 0; k < GalvanoxArena.RODS; k++) if (level.getBlockState(GalvanoxArena.rod(o, k)).getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock) rods++;
				int[] L = GalvanoxArena.LANTERN;
				check(rods == 10 && level.getBlockState(o.offset(L[0], L[1], L[2])).is(VerdigrisBlocks.LIGHTHOUSE_LANTERN) && oy > FitBuilds.SEA_TOP + 24,
					f("struct fit lighthouse: lantern and 10 rods at their offsets, platform at Y %d on the shoal (not fixed at 120)", oy));
			}
			case "clockwork_rig" -> {
				int feet = 0;
				for (int[] l : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}})
					for (int off = 5; off <= 10; off++) {
						int x = l[0] * off, z = l[1] * off, gy = ground.applyAsInt(cx + x, cz + z) - oy;
						if (level.getBlockState(o.offset(x, gy + 1, z)).is(VerdigrisBlocks.VERDIGRIS_PIPE)) {
							feet++;
							break;
						}
					}
				StringBuilder shortLegs = new StringBuilder();
				for (int[] l : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
					int x = l[0] * 10, z = l[1] * 10, low = Integer.MAX_VALUE;
					for (int y = 3; y >= -130; y--) if (level.getBlockState(o.offset(x, y, z)).is(VerdigrisBlocks.VERDIGRIS_PIPE)) low = y;
					int gy = ground.applyAsInt(cx + x, cz + z) - oy;
					if (low != gy + 1) shortLegs.append(f(" (%d,%d) lowest pipe %s, bed %d, below it %s", x, z, low == Integer.MAX_VALUE ? "none" : String.valueOf(low), gy,
						low == Integer.MAX_VALUE ? "-" : BuiltInRegistries.BLOCK.getKey(level.getBlockState(o.offset(x, low - 1, z)).getBlock()).getPath()));
				}
				check(feet == 4 && level.getBlockState(o.offset(0, 4, 0)).is(VerdigrisBlocks.VERDIGRIS_GRATING), f("struct fit clockwork_rig: deck at sea level +4, %d of 4 splayed legs reach the sea bed%s", feet, shortLegs));
			}
			case "engineers_atoll" -> {
				int deck = 0;
				for (int x = -21; x <= 21; x++) for (int z = -21; z <= 21; z++) for (int y = -8; y <= -1; y++) if (level.getBlockState(o.offset(x, y, z)).is(VerdigrisBlocks.KELPWOOD_PLANKS)) deck++;
				check(level.getBlockState(o.offset(0, 1, 1)).isAir() && deck >= 9, f("struct fit engineers_atoll: the tidewright's spot is free, a jetty runs out to deep water (%d deck planks)", deck));
			}
			case "return_rift_verdigris_sea", "return_rift_ironpeak" -> {
				int stones = 0;
				for (int k = 0; k < 8; k++) {
					double a = Math.toRadians(45 * k + 22.5);
					int x = (int) Math.round(Math.cos(a) * 5), z = (int) Math.round(Math.sin(a) * 5);
					int base = name.endsWith("sea") ? -1 : ground.applyAsInt(cx + x, cz + z) - oy + 1;
					if (!level.getBlockState(o.offset(x, base, z)).isAir() && !level.getBlockState(o.offset(x, base - 1, z)).isAir() && !level.getBlockState(o.offset(x, base - 1, z)).is(Blocks.WATER)) stones++;
				}
				check(stones == 8 && !level.getBlockState(o).isAir(), f("struct fit %s: 8 ring stones each on its own %s (%d), the rift on its dais", name, name.endsWith("sea") ? "pile" : "ground", stones));
			}
			default -> {}
		}
	}

	static int count(ServerLevel level, BlockPos o, int r, Block b) {
		int n = 0;
		for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int y = -8; y <= 12; y++) if (level.getBlockState(o.offset(x, y, z)).is(b)) n++;
		return n;
	}

	/** Floor cells that are missing, or have air, water or a plant between them and the model ground (2 blocks of slack below it). */
	static int floating(ServerLevel level, BlockPos o, BlockSink s, List<int[]> cells) {
		int bad = 0;
		for (int[] c : cells) {
			BlockPos p = o.offset(c[0], c[1], c[2]);
			if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
			int stop = s.ground(c[0], c[2]) - 2;
			for (int y = c[1]; y >= stop; y--) {
				BlockState b = level.getBlockState(o.offset(c[0], y, c[2]));
				if (b.isAir() || !b.getFluidState().isEmpty() || b.canBeReplaced()) {
					bad++;
					break;
				}
			}
		}
		return bad;
	}

	/** Real world-generated starts the earlier checks already loaded (atoll, clan hold, tidelock vault): v2 pieces, floors supported. */
	static void real(MinecraftServer server) {
		ServerLevel sea = server.getLevel(Fx.VERDIGRIS_SEA), peak = server.getLevel(Fx.IRONPEAK);
		for (String[] st : new String[][] {{"engineers_atoll", "sea"}, {"tidelock_vault", "sea"}, {"clan_hold", "peak"}}) {
			ServerLevel level = st[1].equals("sea") ? sea : peak;
			if (level == null) continue;
			var found = MapOffers.find(level, List.of(Reg.id(st[0])), new BlockPos(0, 64, 0));
			if (found == null) {
				check(false, "struct fit real " + st[0] + ": none found");
				continue;
			}
			BlockPos at = found.getFirst();
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, ChunkStatus.FULL, true);
			ProceduralStructure.Piece piece = null;
			for (var start : level.structureManager().startsForStructure(at.getX() >> 4, at.getZ() >> 4, x -> x instanceof ProceduralStructure ps && ps.blueprint().equals(st[0])))
				for (var pc : start.getPieces()) if (pc instanceof ProceduralStructure.Piece pp) piece = pp;
			if (piece == null) {
				check(false, "struct fit real " + st[0] + ": no piece at " + at.toShortString());
				continue;
			}
			BlockSink s = new BlockSink(level, piece.origin(), null, RandomSource.create(0), piece.terrain());
			List<int[]> cells = FitBuilds.floorCells(st[0], s);
			int floating = piece.terrain() == null ? -1 : floating(level, piece.origin(), s, cells);
			check(piece.version() == 2 && piece.terrain() != null && floating == 0,
				f("struct fit real %s at %s: a 3.0 piece (v%d) with its saved ground model, %d of %d floor cells floating on generated terrain",
					st[0], piece.origin().toShortString(), piece.version(), floating, cells.size()));
		}
	}
}
