package dev.elsewhere.lore.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/** Client screenshots: every kingdom-lore item (the four Proclamations, fragments, Survey Maps, the well-stone, the Seals) in item frames on a wall. */
public class LoreScreenshots implements FabricClientGameTest {
	private static final String[][] ROWS = {
		{"lore_royal_proclamation", "hrafnholt_royal_proclamation", "azmar_royal_proclamation", "mirewick_council_proclamation"},
		{"lore_survey_fragment", "hrafnholt_survey_fragment", "azmar_survey_fragment", "mirewick_survey_fragment"},
		{"lore_royal_survey_map", "hrafnholt_survey_map", "azmar_survey_map", "mirewick_survey_map"},
		{"azmar_well_stone", "lore_seal_marshal", "lore_seal_castellan", "lore_seal_huntsman"},
	};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Path out = Path.of(System.getProperty("lore.screenshotDir", "screenshots"));
		try {
			Files.createDirectories(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> mc.options.hideGui = true);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamerule do_daylight_cycle false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			// a dark wall (x 0..9, y -59..-48) with the frames one block in front of it
			server.runCommand("fill -1 -59 5 10 -46 5 minecraft:deepslate_tiles");
			for (int r = 0; r < ROWS.length; r++) {
				for (int c = 0; c < ROWS[r].length; c++) {
					int x = 1 + c * 3, y = -57 + r * 3;
					server.runCommand(String.format(Locale.ROOT, "summon minecraft:item_frame %d %d 6 {Facing:3b,Item:{id:\"elsewhere:%s\",count:1}}", x, y, ROWS[r][c]));
				}
			}
			ctx.waitTicks(10);
			server.runCommand("tp @a 5.0 -52.5 17.0 180 0");
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(30);
			ctx.takeScreenshot(TestScreenshotOptions.of("lore_items_wall").withDestinationDir(out).disableCounterPrefix());
			// close-ups: the three rival Proclamations and Survey Maps
			server.runCommand("tp @a 5.0 -54.0 9.5 180 5");
			ctx.waitTicks(20);
			ctx.takeScreenshot(TestScreenshotOptions.of("lore_items_closeup").withDestinationDir(out).disableCounterPrefix());
		}
	}
}
