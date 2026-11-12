package dev.elsewhere.kingdom_court.gametest;

import dev.elsewhere.kingdom_court.world.CastleLayout;
import dev.elsewhere.kingdom_court.world.CastleLayout.Part;
import dev.elsewhere.kingdom_court.world.CastleStyles;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 3.0 structures-build screenshots (STRUCTURES.md section 8): per kingdom the castle camera of the new faction kit, the keep, the market town,
 * Mirewick's quay, and the fv 2 terrain fit (contour curtain, raised towers) on a synthetic hillside. Files: screenshots/struct_*.png.
 */
public class StructureShots implements FabricClientGameTest {
	private static final int Y = 100, R = 44;
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
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			for (String c : new String[] {"gamerule random_tick_speed 0", "gamerule do_daylight_cycle false", "gamerule do_mob_spawning false", "time set 6000", "gamemode creative @a"}) server.runCommand(c);
			server.runOnServer(s -> {
				for (int cx = -5; cx <= 4; cx++) for (int cz = -5; cz <= 4; cz++) s.overworld().setChunkForced(cx, cz, true);
			});
			ctx.waitTicks(40);
			for (String f : new String[] {"aldermark", "hrafnholt", "azmar", "mirewick"}) {
				Identifier fid = Identifier.fromNamespaceAndPath("elsewhere", f);
				// 1. the faction kit on the frozen box layout (new fv 1 starts)
				terrain(server, null);
				build(server, fid, new BlockPos(0, Y, 0), null, Part.CASTLE, Part.KIT_CASTLE);
				ctx.waitTicks(20);
				view(ctx, sp, server, 52.5, Y + 34, 52.5, 135, 30, f + "_castle_cam");
				view(ctx, sp, server, 0.5, Y + 16, 72.5, 180, 12, f + "_front");
				view(ctx, sp, server, 14.5, Y + 9, 10.5, 200, 14, f + "_keep");
				if (f.equals("mirewick")) {
					view(ctx, sp, server, 0.5, Y + 6, 48.5, 180, 14, f + "_quay");
					view(ctx, sp, server, 26.5, Y + 5, 42.5, 140, 16, f + "_quay_side");
				}
				// 2. a market town (town centre at 0,0)
				terrain(server, null);
				build(server, fid, new BlockPos(CastleLayout.TOWN_DX, Y, -CastleLayout.TOWN_DZ), null, Part.TOWN_WEST, Part.KIT_TOWN_WEST);
				ctx.waitTicks(20);
				view(ctx, sp, server, 0.5, Y + 20, 38.5, 180, 28, f + "_town");
				// 3. fv 2 terrain fit on a hillside: yard pad at the floor, contour curtain, towers raised with the ground
				CastleLayout.Ground g = (x, z) -> Y + hill(x, z);
				terrain(server, g);
				build(server, fid, new BlockPos(0, Y, 0), g, Part.CASTLE, Part.WALL, Part.KIT_CASTLE);
				ctx.waitTicks(20);
				view(ctx, sp, server, 0.5, Y + 22, 76.5, 180, 16, f + "_fit_front");
				view(ctx, sp, server, 76.5, Y + 20, 0.5, 90, 14, f + "_fit_side");
				view(ctx, sp, server, -54.5, Y + 36, -54.5, -45, 30, f + "_fit_cam");
			}
		}
	}

	/** Synthetic hillside: rises to the north-east with a wave, about -10..+10 across the castle. */
	static int hill(int x, int z) {
		return (int) Math.round(x * 0.13 - z * 0.11 + 3.0 * Math.sin(x / 9.0) * Math.cos(z / 11.0));
	}

	/** Resets the test area: flat grass at Y-1 (g == null) or the hill; air above, stone below. Only changed blocks are written. */
	private static void terrain(TestServerContext server, CastleLayout.Ground g) {
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = -R - 20; x <= R + 20; x++) for (int z = -R - 20; z <= R + 20; z++) {
				int h = g == null ? Y : g.y(x, z);
				for (int y = Y - 16; y <= Y + 46; y++) {
					BlockState st = y < h - 3 ? stone : y < h - 1 ? dirt : y == h - 1 ? grass : air;
					p.set(x, y, z);
					if (l.getBlockState(p) != st) l.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		});
	}

	/** Builds the parts chunk by chunk, as worldgen does. */
	private static void build(TestServerContext server, Identifier fid, BlockPos o, CastleLayout.Ground g, Part... parts) {
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			CastleStyles.Style style = fid.getPath().equals("aldermark") ? null : CastleStyles.of(fid);
			for (Part part : parts) {
				BoundingBox box = CastleLayout.box(part, o);
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					BoundingBox cb = new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15);
					CastleLayout.Placer pl = new CastleLayout.Placer(l, cb);
					pl.style = style;
					pl.ground = g;
					CastleLayout.build(part, o, pl);
				}
			}
		});
	}

	private void view(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, double px, double py, double pz, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", px, py, pz, yaw, pitch));
		server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}));
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(15);
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("struct_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
