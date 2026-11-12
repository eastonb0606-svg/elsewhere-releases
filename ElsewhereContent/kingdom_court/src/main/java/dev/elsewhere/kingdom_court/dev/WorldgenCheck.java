package dev.elsewhere.kingdom_court.dev;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

/** Dev: real-worldgen check of the castle (fresh world, structures on). Run `./gradlew runWorldgen`. */
public final class WorldgenCheck {
	private WorldgenCheck() {}

	static int tick, pass, fail;
	static final List<String> LOG = new ArrayList<>();
	static BlockPos site;
	static boolean done;

	static void check(String n, boolean ok, Object d) {
		String line = (ok ? "PASS " : "FAIL ") + n + (d == null ? "" : " (" + d + ")");
		LOG.add(line);
		System.out.println("[court-worldgen] " + line);
		if (ok) pass++; else fail++;
	}

	static void info(String s) {
		LOG.add(s);
		System.out.println("[court-worldgen] " + s);
	}

	public static void endTick(MinecraftServer srv) {
		if (done) return;
		tick++;
		ServerLevel l = srv.overworld();
		if (tick == 40) {
			CourtLife.testMode = true;
			dev.elsewhere.kingdom.KingdomPerf.testObservers.add(net.minecraft.world.phys.Vec3.atCenterOf(CourtState.get(srv).site == null ? BlockPos.ZERO : CourtState.get(srv).site));
			CourtState st = CourtState.get(srv);
			info("state site=" + st.site + " chosen=" + st.siteChosen);
			check("a realm site was chosen on the real generator", st.site != null, st.site);
			if (st.site == null) { finish(srv); return; }
			var tag = TagKey.<Structure>create(Registries.STRUCTURE, CourtContent.id("aldermark_castle"));
			var found = l.findNearestMapStructure(tag, BlockPos.ZERO, 100, false);
			info("findNearestMapStructure(#aldermark_castle) = " + found + " (before generation)");
			site = st.site;
			for (int cx = (site.getX() - 112) >> 4; cx <= (site.getX() + 112) >> 4; cx++)
				for (int cz = (site.getZ() - 48) >> 4; cz <= (site.getZ() + 56) >> 4; cz++) l.setChunkForced(cx, cz, true);
		}
		if (tick == 900) report(srv, l);
		if (tick == 1250) SelfTestLocate.worldgen(srv, l); // s-locate: /locate of every realm structure, production path, start in the located chunk
		if (tick == 1300) finish2(srv, l);
	}

	static int top(ServerLevel l, int x, int z) {
		return l.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
	}

	static void report(MinecraftServer srv, ServerLevel l) {
		var gen = l.getChunkSource().getGenerator();
		var rs = l.getChunkSource().randomState();
		var tag = TagKey.<Structure>create(Registries.STRUCTURE, CourtContent.id("aldermark_castle"));
		var found = l.findNearestMapStructure(tag, site, 100, false);
		info("vanilla findNearestMapStructure(#aldermark_castle) = " + found + " (vanilla only searches random-spread and ring placements, not a custom one; use CourtApi.realmCenter)");
		BlockPos rc = dev.elsewhere.kingdom_court.CourtApi.realmCenter(srv);
		check("CourtApi.realmCenter returns the castle site", site.equals(rc), rc);
		var tagSet = l.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(TagKey.<Structure>create(Registries.STRUCTURE, CourtContent.id("aldermark_castle")));
		check("tag #elsewhere:aldermark_castle contains the castle in this world", tagSet.isPresent() && tagSet.get().stream().anyMatch(h -> h.is(CourtWorld.STRUCTURE_KEY)), null);
		var start = l.structureManager().getStructureAt(site, l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(CourtWorld.STRUCTURE_KEY));
		check("structure start is valid at the site with pieces", start.isValid() && !start.getPieces().isEmpty(), start.isValid() ? start.getPieces().size() + " pieces" : "invalid");
		check("realm chunks are generated and loaded", l.isLoaded(site) && l.isLoaded(CastleLayout.town(site, true)) && l.isLoaded(CastleLayout.town(site, false)), null);
		BlockPos th = CastleLayout.throne(site);
		check("throne (dark oak stairs) exists", l.getBlockState(th).is(Blocks.DARK_OAK_STAIRS), l.getBlockState(th).getBlock());
		check("west and east towns exist (blocks at town centres' floor are not natural air)", !l.getBlockState(CastleLayout.town(site, true).below()).isAir() && !l.getBlockState(CastleLayout.town(site, false).below()).isAir(), null);
		// ground fit: castle footprint edge vs terrain
		int fy = site.getY();
		// 3.0 G4: an fv 2 realm (terrain fit) has a contour curtain at +-29..30 on its own ground and leaves the slope outside it natural, so its
		// wall-ring checks sample the curtain only and check that the wall rises above the ground there instead of the fv 1 flat +-32 pad
		boolean fit = start.isValid() && start.getPieces().stream().anyMatch(pc -> pc instanceof CourtWorld.RealmPiece rp && rp.fit());
		info("realm start is fv " + (fit ? 2 : 1) + (fit ? " (3.0 terrain fit: contour curtain, natural slope outside it)" : ""));
		int ring = fit ? 30 : 32, buriedWalls = 0;
		int maxAbove = -99, maxBelow = -99, floating = 0, n = 0;
		for (int x = -ring; x <= ring; x += 2) for (int z = -ring; z <= ring; z += 2) {
			if (Math.abs(x) < 29 && Math.abs(z) < 29) continue; // the wall ring
			if (fit && Math.max(Math.abs(x), Math.abs(z)) == 30 && !(z == 30 && Math.abs(x) <= 10) && !(Math.abs(x) >= 25 && Math.abs(z) >= 25)) {
				// fv 2 outer wall face, curtain only (gatehouse and the hollow corner towers excluded): the wall stands at least 2 above its ground
				int foot = Math.max(fy, gen.getBaseHeight(site.getX() + x, site.getZ() + z, Heightmap.Types.OCEAN_FLOOR_WG, l, rs));
				if (!l.getBlockState(new BlockPos(site.getX() + x, foot, site.getZ() + z)).isSolid() || !l.getBlockState(new BlockPos(site.getX() + x, foot + 1, site.getZ() + z)).isSolid()) {
					buriedWalls++;
					info("wall face not standing above its ground at " + site.offset(x, 0, z) + " (ground " + foot + ")");
				}
			}
			BlockPos p = site.offset(x, 0, z);
			int t = gen.getBaseHeight(p.getX(), p.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, l, rs);
			n++;
			maxAbove = Math.max(maxAbove, t - fy);
			maxBelow = Math.max(maxBelow, fy - t);
			if (!l.getBlockState(new BlockPos(p.getX(), fy - 2, p.getZ())).isSolid() || !l.getBlockState(new BlockPos(p.getX(), fy - 5, p.getZ())).isSolid()) {
				floating++;
				info("unsupported wall column at " + p + " base terrain " + t + " block below " + l.getBlockState(p.below(2)).getBlock());
			}
		}
		info(String.format("ground fit: floor y=%d, ORIGINAL terrain (generator base height) at the wall ring vs floor: up to %d above, up to %d below (%d samples), %d columns with no support under the wall base", fy, maxAbove, maxBelow, n, floating));
		check("keep not floating: wall base supported on every sample", floating == 0, floating);
		if (fit) check("keep not buried (fv 2): the contour curtain rises at least 2 above its ground on every outer-face sample", buriedWalls == 0, buriedWalls + " of the samples; ground up to " + maxAbove + " above the floor");
		else check("keep not buried: terrain at the wall ring is at most 3 above the floor", maxAbove <= 3, maxAbove);
		info("original terrain dropped up to " + maxBelow + " below the floor at the wall ring; the foundation pillars fill it (support check above)");
		if (fit) {
			// z-castle: the fv 2 curtain batter (ring 31 outside the wall, without corners and gate) sits on its own generator ground and is
			// underpinned, so on real worldgen (beards, carvers, ponds) no batter block has air or fluid under it
			int batter = 0, bad = 0, missing = 0;
			for (int t = -31; t <= 31; t++) for (int[] c : new int[][] {{t, -31}, {t, 31}, {-31, t}, {31, t}}) {
				if (Math.abs(c[0]) >= 26 && Math.abs(c[1]) >= 26 || c[1] >= 29 && Math.abs(c[0]) <= 7) continue;
				BlockPos p = new BlockPos(site.getX() + c[0], gen.getBaseHeight(site.getX() + c[0], site.getZ() + c[1], Heightmap.Types.WORLD_SURFACE_WG, l, rs), site.getZ() + c[1]);
				batter++;
				var s = l.getBlockState(p);
				if (!(s.getBlock() instanceof net.minecraft.world.level.block.StairBlock)) {
					missing++;
					if (missing <= 3) info("no batter stair at " + p + " (" + s.getBlock() + ")");
					continue;
				}
				var b = l.getBlockState(p.below());
				if (b.isAir() || !b.getFluidState().isEmpty()) {
					bad++;
					if (bad <= 3) info("air/fluid under the batter at " + p + " (" + b.getBlock() + ")");
				}
			}
			check("curtain batter (fv 2) on its own ground: present on every cell, no air or fluid under any batter block", batter >= 180 && missing == 0 && bad == 0, batter + " cells, " + missing + " missing, " + bad + " unsupported");
		}
		BlockPos hall = CastleLayout.hall(site);
		check("hall is open: solid floor, 2 air blocks above the carpet", l.getBlockState(hall.below()).isSolid() && l.getBlockState(hall.above()).isAir() && l.getBlockState(hall.above(2)).isAir(), l.getBlockState(hall).getBlock());
		BlockPos yard = CastleLayout.yard(site);
		check("yard is open: solid below, air above (no terrain intruding; a snow layer from a cold biome is fine)", l.getBlockState(yard.below()).isSolid() && (l.getBlockState(yard).isAir() || l.getBlockState(yard).is(Blocks.SNOW)) && l.getBlockState(yard.above()).isAir(), l.getBlockState(yard).getBlock());
		for (int s = -1; s <= 1; s += 2) {
			BlockPos t = CastleLayout.town(site, s < 0);
			int ty = top(l, t.getX(), t.getZ());
			info("town " + (s < 0 ? "west" : "east") + " centre: terrain top " + ty + " vs floor " + fy);
			check("town " + (s < 0 ? "west" : "east") + " sits on the ground (terrain top within 8 of the floor)", Math.abs(ty - fy) <= 8, ty - fy);
		}
	}

	static void finish2(MinecraftServer srv, ServerLevel l) {
		CourtState st = CourtState.get(srv);
		AABB box = new AABB(site.getX() - 140, site.getY() - 30, site.getZ() - 80, site.getX() + 140, site.getY() + 50, site.getZ() + 80);
		var all = l.getEntitiesOfClass(KingdomSoldier.class, box);
		long rulers = all.stream().filter(s -> s instanceof CourtRuler).count();
		boolean king = all.stream().anyMatch(s -> s instanceof CourtRuler r && r.ruler == dev.elsewhere.kingdom_court.ruler.Ruler.KING);
		check("castle marked built by the court", st.built, null);
		check("the King stands in the castle", king, null);
		check("all 6 rulers present", rulers == 6, rulers);
		check("garrison present (>= 17 soldiers)", all.size() - rulers >= 17, all.size() - rulers);
		finish(srv);
	}

	static void finish(MinecraftServer srv) {
		done = true;
		info("TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL"));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("worldgen-court.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		srv.halt(false);
	}
}
