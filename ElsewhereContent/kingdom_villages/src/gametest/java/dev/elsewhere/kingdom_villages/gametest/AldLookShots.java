package dev.elsewhere.kingdom_villages.gametest;

import dev.elsewhere.kingdom_villages.Plans;
import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.dev.AldLookScenes;
import dev.elsewhere.kingdom_villages.s3.ald.AldFort;
import dev.elsewhere.kingdom_villages.s3.ald.AldHamlet;
import dev.elsewhere.kingdom_villages.s3.ald.AldRebuild;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * [ald-look] screenshots of the 3.0 Aldermark looks (s-vlook-ald) on sculpted hills in the flat test world: the Aldermark rebuild
 * of a village on a hill flank (overview, the watchtower on the high point, a timber gatehouse, the palisade stepping down the slope),
 * the motte-and-bailey outpost (overview, gate) and the burnt farmstead. Output: {@code ald_<name>.png} in villages.screenshotDir.
 */
public class AldLookShots implements FabricClientGameTest {
	private record Shot(String name, BlockPos target, double dist, double height, double angle) {}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static volatile String info = "";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Path out = Path.of(System.getProperty("villages.screenshotDir", "screenshots"));
		try {
			Files.createDirectories(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		ctx.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamerule random_tick_speed 0");
			server.runCommand("gamerule do_daylight_cycle false");
			server.runCommand("gamerule do_mob_spawning false");
			server.runCommand("gamerule do_weather_cycle false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			SHOTS.clear();
			server.runOnServer(s -> build(s.overworld()));
			if (SHOTS.isEmpty()) throw new AssertionError("ald-look scenes did not build: " + info);
			for (Shot shot : new ArrayList<>(SHOTS)) {
				double a = Math.toRadians(shot.angle);
				Vec3 c = new Vec3(shot.target.getX() + 0.5 + Math.cos(a) * shot.dist, shot.target.getY() + shot.height, shot.target.getZ() + 0.5 + Math.sin(a) * shot.dist);
				float yaw = (float) Math.toDegrees(Math.atan2(-(shot.target.getX() + 0.5 - c.x), shot.target.getZ() + 0.5 - c.z));
				double dh = Math.hypot(shot.target.getX() + 0.5 - c.x, shot.target.getZ() + 0.5 - c.z);
				float pitch = (float) -Math.toDegrees(Math.atan2(shot.target.getY() + 0.5 - c.y, dh));
				server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", c.x, c.y, c.z, yaw, pitch));
				sp.getConnection().waitForChunksRender();
				ctx.waitTicks(16);
				ctx.takeScreenshot(TestScreenshotOptions.of("ald_" + shot.name).withDestinationDir(out).disableCounterPrefix());
			}
		}
	}

	private static void build(ServerLevel level) {
		try {
			int[] res = new int[3];
			Plans.Fortify[] fp = new Plans.Fortify[1];
			BlockPos v = AldLookScenes.VILLAGE_HILL;
			VillageRecord vr = AldLookScenes.villageScene(level, v, res, fp);
			BlockPos center = vr.center;
			SHOTS.add(new Shot("hill_village_overview", center.offset(6, 0, 4), 46, 26, 225));
			if (AldRebuild.lastTower != null) SHOTS.add(new Shot("hill_village_tower", AldRebuild.lastTower.above(8), 18, 4, 200));
			if (!AldRebuild.lastGates.isEmpty()) {
				BlockPos g = AldRebuild.lastGates.get(0);
				double out = Math.toDegrees(Math.atan2(g.getZ() - center.getZ(), g.getX() - center.getX())); // camera outside, looking in through the gate
				SHOTS.add(new Shot("hill_village_gatehouse", g.below(2), 12, 2, out));
			}
			SHOTS.add(new Shot("hill_village_palisade_slope", new BlockPos(v.getX() + AldLookScenes.V_HALF, center.getY(), v.getZ() + 10), 20, 6, 20));
			AldFort.Plan[] op = new AldFort.Plan[1];
			VillageRecord fr = AldLookScenes.fortScene(level, AldLookScenes.FORT_HILL, res, op);
			if (fr != null && op[0] != null) {
				SHOTS.add(new Shot("hill_outpost_overview", op[0].tower().above(2), 34, 18, 135));
				SHOTS.add(new Shot("hill_outpost_gate", op[0].roadStart().above(1), 10, 2, -90));
			} else info += " no fort site";
			AldHamlet.Plan[] hp = new AldHamlet.Plan[1];
			VillageRecord hr = AldLookScenes.hamletScene(level, AldLookScenes.HAMLET_HILL, res, hp);
			if (hr != null && hp[0] != null) SHOTS.add(new Shot("hamlet_farmstead", hp[0].refuge(), 14, 8, 160));
			else info += " no hamlet site";
		} catch (Throwable t) {
			info += " " + t;
			SHOTS.clear();
		}
	}
}
