package dev.elsewhere.kingdom_villages.gametest;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_villages.RealmLocator;
import dev.elsewhere.kingdom_villages.Serf;
import dev.elsewhere.kingdom_villages.VillageLedger;
import dev.elsewhere.kingdom_villages.VillageManager;
import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.VillagesContent;
import dev.elsewhere.kingdom_villages.dev.RivalsTest;
import dev.elsewhere.kingdom_villages.dev.SelfTest;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client screenshots of the four rebuild styles (Aldermark for comparison, Hrafnholt longhall and palisade, Azmar obelisks and
 * palm-shaded walls, Mirewick boardwalks and stilts) and the Serf-tier skins. Each village is a real generated plains village
 * occupied and rebuilt by the villages manager.
 */
public class VillagesScreenshots implements FabricClientGameTest {
	private Path out;

	private record Shot(String name, BlockPos target, double dist, double height, double angle) {}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static volatile int state; // 0 building, 1 fortified, -1 failed
	private static volatile String info = "";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("villages.screenshotDir", "screenshots"));
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
			server.runOnServer(s -> {
				RivalsTest.define(RivalsTest.HRAF, "Jarldom of Hrafnholt", "{\"type\":\"elsewhere:thrall\",\"count_multiplier\":2.0,\"health\":20.0,\"levy\":{\"fraction\":0.3333,\"weapon\":\"minecraft:stone_axe\"}}", 1.2, 0x202428);
				RivalsTest.define(RivalsTest.AZMAR, "Sultanate of Azmar", "{\"type\":\"elsewhere:porter\",\"count_multiplier\":0.5,\"health\":20.0}", 0.75, 0x2BB3A3);
				RivalsTest.define(RivalsTest.MIRE, "Lantern Republic of Mirewick", "{\"type\":\"elsewhere:peat_cutter\",\"count_multiplier\":1.0,\"health\":20.0}", 0.9, 0x4F6B3A);
				VillageLedger.get(s.overworld()).conquestOn = true;
				VillageManager.siegeChanceOverride = 1.0;
				VillageManager.opInterval = 1;
				VillageManager.opsPerRun = 60;
			});
			String[] names = {"aldermark", "hrafnholt", "azmar", "mirewick"};
			Identifier[] ids = {Factions.ALDERMARK, RivalsTest.HRAF, RivalsTest.AZMAR, RivalsTest.MIRE};
			for (int i = 0; i < names.length; i++) {
				final int idx = i;
				state = 0;
				SHOTS.clear();
				server.runOnServer(s -> build(s, idx, ids[idx]));
				for (int w = 0; w < 80 && state == 0; w++) {
					ctx.waitTicks(20);
					server.runOnServer(s -> poll(s, idx));
				}
				if (state != 1) throw new AssertionError(names[i] + " village did not finish its rebuild: " + info);
				ctx.waitTicks(20);
				for (Shot shot : new ArrayList<>(SHOTS)) {
					Vec3 c = camera(shot);
					float yaw = (float) Math.toDegrees(Math.atan2(-(shot.target.getX() + 0.5 - c.x), shot.target.getZ() + 0.5 - c.z));
					double dh = Math.hypot(shot.target.getX() + 0.5 - c.x, shot.target.getZ() + 0.5 - c.z);
					float pitch = (float) -Math.toDegrees(Math.atan2(shot.target.getY() + 0.5 - c.y, dh));
					server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", c.x, c.y, c.z, yaw, pitch));
					sp.getConnection().waitForChunksRender();
					ctx.waitTicks(12);
					ctx.takeScreenshot(TestScreenshotOptions.of("villages_" + names[i] + "_" + shot.name).withDestinationDir(out).disableCounterPrefix());
				}
			}
			serfs(ctx, sp, server);
		}
	}

	private static Vec3 camera(Shot s) {
		double a = Math.toRadians(s.angle);
		return new Vec3(s.target.getX() + 0.5 + Math.cos(a) * s.dist, s.target.getY() + s.height, s.target.getZ() + 0.5 + Math.sin(a) * s.dist);
	}

	private static VillageRecord record;

	private static void build(MinecraftServer s, int idx, Identifier faction) {
		ServerLevel level = s.overworld();
		int cx = idx * 26, cz = 40;
		var start = SelfTest.village(level, cx, cz);
		for (var g : level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, net.minecraft.world.phys.AABB.of(start.getBoundingBox().inflatedBy(40)), e -> true)) g.discard();
		BlockPos c = start.getBoundingBox().getCenter();
		BlockPos castle = new BlockPos(c.getX(), -60, c.getZ() + 120);
		if (faction.equals(Factions.ALDERMARK)) RealmLocator.testRealm = castle;
		else RealmLocator.testRealms.put(faction, castle);
		record = VillageManager.discover(level, start, null);
		if (idx == 1 && record != null) dev.elsewhere.kingdom_villages.dev.HrafnLooksTest.pool(level, record.center.offset(30, 0, -6), 16, 18); // Hrafnholt's waterfront water (s-vlook-hraf)
		VillageManager.testObservers.add(Vec3.atCenterOf(c));
	}

	private static void poll(MinecraftServer s, int idx) {
		VillageRecord r = record;
		if (r == null) {
			state = -1;
			info = "no record";
			return;
		}
		info = r.state + " " + r.planIndex + "/" + r.plan.size();
		if (r.state != VillageRecord.State.FORTIFIED) return;
		ServerLevel level = s.overworld();
		BlockPos c = r.center;
		SHOTS.add(new Shot("overview", Plans2.surf(level, c), 46, 22, 225));
		SHOTS.add(new Shot("overview_street", Plans2.surf(level, c), 30, 9, 135));
		Block[] want = switch (idx) {
			case 1 -> new Block[] {Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_LOG};
			case 2 -> new Block[] {Blocks.GLAZED_TERRACOTTA.yellow(), Blocks.JUNGLE_LEAVES};
			case 3 -> new Block[] {Blocks.SMOOTH_STONE, Blocks.LANTERN};
			default -> new Block[] {Blocks.STONE_BRICKS, Blocks.SPRUCE_FENCE};
		};
		String[] label = switch (idx) {
			case 1 -> new String[] {"longhall", "palisade"};
			case 2 -> new String[] {"obelisk", "palm_wall"};
			case 3 -> new String[] {"quay", "lantern_post"};
			default -> new String[] {"stone", "palisade"};
		};
		for (int k = 0; k < want.length; k++) {
			BlockPos found = null;
			for (var e : r.journal.long2ObjectEntrySet()) {
				if (e.getValue().written().is(want[k])) {
					found = BlockPos.of(e.getLongKey());
					if (k == 1 || (idx == 3 && k == 0)) { /* keep scanning for a spot toward the middle of the list */ }
					break;
				}
			}
			if (found != null) SHOTS.add(new Shot(label[k], found, k == 0 ? 20 : 14, k == 0 ? 8 : 5, 200 + 60 * k));
		}
		if (idx > 0 && r.hint != null) SHOTS.add(new Shot("border_hint", r.hint.below(2), 11, 3, 150));
		var hr = idx == 1 ? dev.elsewhere.kingdom_villages.HrafnLooks.report(r.id) : null; // 3.0 Hrafnholt look (s-vlook-hraf): one shot per feature
		if (hr != null) {
			SHOTS.removeIf(sh -> sh.name().equals("longhall"));
			for (var e : hr.spots.entrySet()) {
				if (e.getKey().equals("raven_post")) continue; // the border_hint shot
				boolean big = e.getKey().equals("longhall") || e.getKey().equals("boathouse") || e.getKey().equals("pier") || e.getKey().equals("water_gate");
				SHOTS.add(new Shot(e.getKey(), e.getValue(), big ? 22 : 13, big ? 9 : 5, 210));
			}
		}
		if (idx == 3 && r.towerTop != null) SHOTS.add(new Shot("sluice_house", r.towerTop.below(3), 14, 5, 20));
		state = 1;
	}

	/** The four Serf-tier skins in a row. */
	private void serfs(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			int y = -60;
			String[] variants = {"serf", "thrall", "porter", "peat_cutter", "thrall"};
			for (int i = 0; i < 5; i++) {
				Serf e = VillagesContent.SERF.create(level, EntitySpawnReason.COMMAND);
				e.snapTo(-300 + i * 1.5, y, -300, 0F, 0F);
				e.variant = variants[i];
				if (i == 4) e.arm(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_AXE)); // the levy: a Thrall called up with the Hrafnholt axe
				e.setNoAi(true);
				e.setPersistenceRequired();
				level.addFreshEntity(e);
			}
			for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) level.getChunk((-300 + dx * 16) >> 4, (-300 + dz * 16) >> 4);
		});
		server.runCommand("tp @a -296.4 -58.4 -296.0 180 4");
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(30);
		ctx.takeScreenshot(TestScreenshotOptions.of("villages_serf_variants").withDestinationDir(out).disableCounterPrefix());
	}

	/** Surface helper (the villages module's own is package-visible only through Plans). */
	private static final class Plans2 {
		static BlockPos surf(ServerLevel level, BlockPos p) {
			return dev.elsewhere.kingdom_villages.Plans.surface(level, p);
		}
	}
}
