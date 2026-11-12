package dev.elsewhere.boss_ui.gametest;

import dev.elsewhere.hud.ElsewhereHud;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * Boss bar v2 screenshots: full bar, just-hit (damage trail), phase flash with the Invulnerable transition chip, low
 * health, the overworld cast bar as the attack line, state chips (Vulnerable, Warded, Enraged) and a raw move id shown
 * as its lang name on a vanilla bar. Entity names come from the gametest lang (src/gametest/resources).
 */
public class BossBarShots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Path out = Path.of(System.getProperty("boss_ui.screenshotDir", "screenshots"));
		try { Files.createDirectories(out); } catch (Exception e) { throw new RuntimeException(e); }
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> ElsewhereHud.setForTest("bossbars", ElsewhereHud.Mode.AUTO));
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			bar(server, "shot", "Strawgrim, the Hollow Shepherd", "red", 1000);
			ctx.waitTicks(20);
			shot(ctx, out, "bar_full");
			server.runCommand("bossbar set elsewhere:shot value 750");
			ctx.waitTicks(8);
			shot(ctx, out, "bar_trail_after_hit");
			server.runCommand("bossbar set elsewhere:shot value 600");
			ctx.waitTicks(2);
			shot(ctx, out, "bar_phase2_flash_invulnerable");
			ctx.waitTicks(40);
			bar(server, "cast", "⚠ Reaping Sweep", "yellow", 1000);
			server.runCommand("bossbar set elsewhere:cast value 550");
			ctx.waitTicks(4);
			shot(ctx, out, "bar_cast_windup");
			server.runCommand("bossbar remove elsewhere:cast");
			server.runCommand("bossbar set elsewhere:shot value 200");
			ctx.waitTicks(2);
			shot(ctx, out, "bar_phase3_flash");
			ctx.waitTicks(60);
			shot(ctx, out, "bar_low_settled");
			server.runCommand("bossbar remove elsewhere:shot");

			bar(server, "perc", "Percolax, the Hotbox Colossus  »  Coughing fit! Core exposed", "green", 1000);
			bar(server, "creak", "Mother Creak, the Elder Heartwood  ❤❤❤", "red", 1000);
			bar(server, "rival", "Test Duelist Ruler · glyph_flare", "purple", 1000);
			ctx.waitTicks(10);
			shot(ctx, out, "bar_states_and_raw_label");
			ctx.runOnClient(mc -> ElsewhereHud.setForTest("bossbars", ElsewhereHud.Mode.ALWAYS));
			server.runCommand("bossbar set elsewhere:rival name \"Test Duelist Ruler · Last Stand\"");
			ctx.waitTicks(10);
			shot(ctx, out, "bar_always_enraged_generic");
			for (String id : new String[] {"perc", "creak", "rival"}) server.runCommand("bossbar remove elsewhere:" + id);
		}
	}

	private static void bar(TestServerContext server, String id, String title, String color, int value) {
		server.runCommand("bossbar add elsewhere:" + id + " \"" + title + "\"");
		server.runCommand("bossbar set elsewhere:" + id + " players @a");
		server.runCommand("bossbar set elsewhere:" + id + " color " + color);
		server.runCommand("bossbar set elsewhere:" + id + " max 1000");
		server.runCommand("bossbar set elsewhere:" + id + " value " + value);
		server.runCommand("bossbar set elsewhere:" + id + " visible true");
	}

	private static void shot(ClientGameTestContext ctx, Path out, String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of("bossbar_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
