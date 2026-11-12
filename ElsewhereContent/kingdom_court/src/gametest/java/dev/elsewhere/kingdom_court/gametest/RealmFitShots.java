package dev.elsewhere.kingdom_court.gametest;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * 3.0 G4 proof (generation lane): a realm made by REAL worldgen on a seeded hillside. Seed {@code court.fitSeed} (default 4242); the Aldermark
 * realm site is moved to the best untouched hillside 600..1100 blocks from spawn (relief 8..20 over the yard), then the chunks are generated
 * normally, so the castle is an fv 2 start: generator HeightField floors, the thin yard beard (BeardifierMixin) and contour walls/roads.
 * Files: screenshots/realmfit_*.png; log line {@code [realmfit]} with the site, relief, fv and piece count.
 */
public class RealmFitShots implements FabricClientGameTest {
	private Path out;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("court.screenshotDir", "screenshots"));
		try {
			Files.createDirectories(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		String seed = System.getProperty("court.fitSeed", "4242");
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed(seed)).create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			for (String c : new String[] {"gamerule random_tick_speed 0", "gamerule do_daylight_cycle false", "gamerule do_mob_spawning false", "time set 6000", "gamemode creative @a", "weather clear"}) server.runCommand(c);
			int[] site = server.computeOnServer(s -> pickSite(s.overworld()));
			int sx = site[0], fl = site[1], sz = site[2];
			System.out.println("[realmfit] seed " + seed + " site " + sx + " " + fl + " " + sz + " relief " + site[3]);
			// fly in from above so the realm generates through normal worldgen, then look at it
			view(ctx, sp, server, sx + 0.5, fl + 60, sz + 0.5, 0, 90, null);
			ctx.waitTicks(100);
			String info = server.computeOnServer(s -> describe(s.overworld(), new BlockPos(sx, fl, sz)));
			System.out.println("[realmfit] " + info);
			view(ctx, sp, server, sx + 52.5, fl + 40, sz + 52.5, 135, 32, "castle_cam");
			view(ctx, sp, server, sx + 0.5, fl + 22, sz + 82.5, 180, 14, "front");
			view(ctx, sp, server, sx - 70.5, fl + 18, sz + 0.5, -90, 12, "side");
			view(ctx, sp, server, sx + 0.5, fl + 75, sz + 150.5, 180, 32, "realm");
			view(ctx, sp, server, sx - 88.5, fl + 26, sz + 52.5, 180, 26, "town_west");
			view(ctx, sp, server, sx + 88.5, fl + 26, sz + 52.5, 180, 26, "town_east");
		}
	}

	/** {x, floor, z, relief}: the untouched hillside with relief nearest 14 (8..20) over the yard, towns and road on dry land. */
	static int[] pickSite(ServerLevel l) {
		ChunkGenerator gen = l.getChunkSource().getGenerator();
		RandomState rs = l.getChunkSource().randomState();
		int sea = gen.getSeaLevel();
		int[] best = null;
		for (int r = 640; r <= 1088; r += 112) for (int a = 0; a < 16; a++) {
			int cx = (int) Math.round(Math.cos(a * Math.PI / 8) * r) & ~15, cz = (int) Math.round(Math.sin(a * Math.PI / 8) * r) & ~15;
			cx += 8;
			cz += 8;
			int[] g = new int[25];
			int i = 0;
			boolean dry = true;
			for (int dx = -28; dx <= 28; dx += 14) for (int dz = -28; dz <= 28; dz += 14) {
				int y = gen.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG, l, rs);
				int f = gen.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.OCEAN_FLOOR_WG, l, rs);
				if (y != f || y <= sea + 1) dry = false;
				g[i++] = y;
			}
			for (int s = -1; s <= 1 && dry; s += 2) for (int dz = 0; dz <= 40; dz += 20)
				if (gen.getBaseHeight(cx + s * 88, cz + dz, Heightmap.Types.WORLD_SURFACE_WG, l, rs) <= sea + 1) dry = false;
			if (!dry) continue;
			Arrays.sort(g);
			int relief = g[24] - g[0];
			if (relief < 8 || relief > 20) continue;
			if (best == null || Math.abs(relief - 14) < Math.abs(best[3] - 14)) best = new int[] {cx, g[12], cz, relief};
		}
		if (best == null) best = new int[] {808, gen.getBaseHeight(808, 8, Heightmap.Types.WORLD_SURFACE_WG, l, rs), 8, -1};
		BlockPos site = new BlockPos(best[0], best[1], best[2]);
		dev.elsewhere.kingdom_court.CourtState.get(l, Factions.ALDERMARK).site = site;
		CourtWorld.setSite(l.getSeed(), Factions.ALDERMARK, site);
		return best;
	}

	static String describe(ServerLevel l, BlockPos site) {
		Structure st = l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(CourtWorld.STRUCTURE_KEY);
		StructureStart start = st == null ? null : l.structureManager().getStructureAt(site, st);
		if (start == null || !start.isValid()) return "NO realm start at " + site.toShortString();
		int fit = 0, n = 0;
		for (StructurePiece p : start.getPieces()) {
			n++;
			if (p instanceof CourtWorld.RealmPiece rp && rp.fit()) fit++;
		}
		return String.format(Locale.ROOT, "start ok: %d pieces, %d fv2; towns at %d / %d (castle %d)", n, fit,
			CourtWorld.townFloor(site, true), CourtWorld.townFloor(site, false), site.getY());
	}

	private void view(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, double px, double py, double pz, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", px, py, pz, yaw, pitch));
		server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}));
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(20);
		if (name == null) return;
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("realmfit_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
