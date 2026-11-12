package dev.elsewhere.boss_ui.gametest;

import dev.elsewhere.hud.ElsewhereHud;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/** Boss bar v2 screenshots: full bar, just-hit (damage trail), post-phase flash, low health. */
public class BossBarShots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Path out = Path.of(System.getProperty("boss_ui.screenshotDir", "screenshots"));
		try { Files.createDirectories(out); } catch (Exception e) { throw new RuntimeException(e); }
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> ElsewhereHud.setForTest("bossbars", ElsewhereHud.Mode.ALWAYS));
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("bossbar add elsewhere:shot {\"text\":\"Strawgrim, the Hollow Shepherd  ·  Reaping Sweep\"}");
			server.runCommand("bossbar set elsewhere:shot players @a");
			server.runCommand("bossbar set elsewhere:shot color red");
			server.runCommand("bossbar set elsewhere:shot max 1000");
			server.runCommand("bossbar set elsewhere:shot value 1000");
			server.runCommand("bossbar set elsewhere:shot visible true");
			ctx.waitTicks(20);
			shot(ctx, out, "bar_full");
			server.runCommand("bossbar set elsewhere:shot value 750");
			ctx.waitTicks(8);
			shot(ctx, out, "bar_trail_after_hit");
			server.runCommand("bossbar set elsewhere:shot value 600");
			ctx.waitTicks(2);
			shot(ctx, out, "bar_phase2_flash");
			ctx.waitTicks(40);
			server.runCommand("bossbar set elsewhere:shot value 200");
			ctx.waitTicks(2);
			shot(ctx, out, "bar_phase3_flash");
			ctx.waitTicks(60);
			shot(ctx, out, "bar_low_settled");
			server.runCommand("bossbar remove elsewhere:shot");
		}
	}

	private static void shot(ClientGameTestContext ctx, Path out, String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of("bossbar_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
