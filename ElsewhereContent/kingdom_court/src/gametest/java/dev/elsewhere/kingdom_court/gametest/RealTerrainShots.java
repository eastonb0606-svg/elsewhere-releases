package dev.elsewhere.kingdom_court.gametest;

import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleLayout.Part;
import dev.elsewhere.kingdom_court.world.CastleStyles;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 3.0 fv 2 terrain fit on REAL overworld terrain (normal world): the ground hook is the generator's own WORLD_SURFACE_WG height (what the
 * generation lane's HeightField samples), part floors are the median ground under each part (stand-in for RealmFit.partFloor). One whole
 * realm (castle, towns with per-house pads, A* roads) and three more castles on other hillsides. Files: screenshots/real_*.png.
 */
public class RealTerrainShots implements FabricClientGameTest {
	private Path out;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("court.screenshotDir", "screenshots"));
		try {
			Files.createDirectories(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			for (String c : new String[] {"gamerule random_tick_speed 0", "gamerule do_daylight_cycle false", "gamerule do_mob_spawning false", "time set 6000", "gamemode creative @a", "weather clear"}) server.runCommand(c);
			int[][] sites = server.computeOnServer(s -> findSites(s.overworld(), 4));
			String[] factions = {"aldermark", "hrafnholt", "azmar", "mirewick"};
			for (int i = 0; i < sites.length && i < 4; i++) {
				String f = factions[i];
				int sx = sites[i][0], sz = sites[i][1];
				boolean realm = i == 0;
				int[] floors = server.computeOnServer(s -> buildRealm(s.overworld(), f, sx, sz, realm));
				int fl = floors[0];
				ctx.waitTicks(40);
				view(ctx, sp, server, sx + 52.5, fl + 40, sz + 52.5, 135, 32, f + "_castle_cam");
				view(ctx, sp, server, sx + 0.5, fl + 22, sz + 82.5, 180, 14, f + "_front");
				view(ctx, sp, server, sx - 70.5, fl + 18, sz + 0.5, -90, 12, f + "_side");
				if (realm) {
					view(ctx, sp, server, sx + 0.5, fl + 75, sz + 150.5, 180, 32, f + "_realm");
					view(ctx, sp, server, sx - CastleLayout.TOWN_DX + 0.5, floors[1] + 26, sz + 52.5, 180, 26, f + "_town_west");
					view(ctx, sp, server, sx + CastleLayout.TOWN_DX + 0.5, floors[2] + 26, sz + 52.5, 180, 26, f + "_town_east");
					view(ctx, sp, server, sx + 44.5, fl + 14, sz + 64.5, 200, 14, f + "_road");
				}
			}
		}
	}

	static CastleLayout.Ground ground(ServerLevel l) {
		ChunkGenerator gen = l.getChunkSource().getGenerator();
		RandomState rs = l.getChunkSource().randomState();
		return (x, z) -> gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, l, rs);
	}

	/** Hillside sites near spawn: dry land (all samples above sea level + 1), relief 5..14 over the castle, towns on land. Best first. */
	static int[][] findSites(ServerLevel l, int n) {
		CastleLayout.Ground g = ground(l);
		int sea = l.getChunkSource().getGenerator().getSeaLevel();
		java.util.List<int[]> found = new java.util.ArrayList<>();
		for (int i = -7; i <= 7; i++) for (int j = -7; j <= 7; j++) {
			int cx = i * 160, cz = j * 160;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			boolean ok = true;
			for (int a = -40; a <= 40 && ok; a += 20) for (int b = -40; b <= 40; b += 20) {
				int y = g.y(cx + a, cz + b);
				if (y <= sea + 1) { ok = false; break; }
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
			if (!ok) continue;
			for (int s = -1; s <= 1; s += 2) if (g.y(cx + s * CastleLayout.TOWN_DX, cz + CastleLayout.TOWN_DZ) <= sea + 1) ok = false;
			int relief = hi - lo;
			if (ok && relief >= 5 && relief <= 14) found.add(new int[] {cx, cz, Math.abs(relief - 9)});
		}
		found.sort((p, q) -> p[2] != q[2] ? Integer.compare(p[2], q[2]) : Integer.compare(Math.abs(p[0]) + Math.abs(p[1]), Math.abs(q[0]) + Math.abs(q[1])));
		if (found.isEmpty()) found.add(new int[] {0, 0, 0});
		return found.subList(0, Math.min(n, found.size())).toArray(new int[0][]);
	}

	static int median(CastleLayout.Ground g, int cx, int cz, int r) {
		int[] v = new int[25];
		int i = 0;
		for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) v[i++] = g.y(cx + a * r / 2, cz + b * r / 2);
		Arrays.sort(v);
		return v[12];
	}

	/** Generates the chunks, then builds the fv 2 parts chunk by chunk in the contract order. Returns {castle, west town, east town} floors. */
	static int[] buildRealm(ServerLevel l, String f, int sx, int sz, boolean realm) {
		CastleLayout.Ground raw = ground(l);
		int fc = median(raw, sx, sz, 28), fw = median(raw, sx - CastleLayout.TOWN_DX, sz + CastleLayout.TOWN_DZ, 20), fe = median(raw, sx + CastleLayout.TOWN_DX, sz + CastleLayout.TOWN_DZ, 20);
		CastleLayout.Ground g = new CastleLayout.Ground() {
			@Override
			public int y(int x, int z) { return raw.y(x, z); }

			@Override
			public int floor(Part part, int fallback) { return part == Part.TOWN_WEST ? fw : part == Part.TOWN_EAST ? fe : fallback; }
		};
		Identifier fid = Identifier.fromNamespaceAndPath("elsewhere", f);
		CastleStyles.Style style = f.equals("aldermark") ? null : CastleStyles.of(fid);
		Part[] parts = realm ? new Part[] {Part.CASTLE, Part.TOWN_WEST, Part.TOWN_EAST, Part.ROAD, Part.WALL, Part.KIT_CASTLE, Part.KIT_TOWN_WEST, Part.KIT_TOWN_EAST}
			: new Part[] {Part.CASTLE, Part.WALL, Part.KIT_CASTLE};
		for (Part part : parts) {
			int floor = part == Part.TOWN_WEST || part == Part.KIT_TOWN_WEST ? fw : part == Part.TOWN_EAST || part == Part.KIT_TOWN_EAST ? fe : fc;
			BlockPos o = new BlockPos(sx, floor, sz);
			BoundingBox box = CastleLayout.fitBox(part, o);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				l.setChunkForced(cx, cz, true);
				l.getChunk(cx, cz);
			}
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				BoundingBox cb = new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15);
				CastleLayout.Placer pl = new CastleLayout.Placer(l, cb);
				pl.style = style;
				pl.ground = g;
				CastleLayout.build(part, o, pl);
			}
		}
		return new int[] {fc, fw, fe};
	}

	private void view(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, double px, double py, double pz, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", px, py, pz, yaw, pitch));
		server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}));
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("real_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
