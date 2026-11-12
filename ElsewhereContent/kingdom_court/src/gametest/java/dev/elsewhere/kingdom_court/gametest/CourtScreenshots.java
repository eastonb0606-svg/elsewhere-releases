package dev.elsewhere.kingdom_court.gametest;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.api.Diplomacy;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.trade.AzmarTrade;
import dev.elsewhere.kingdom_court.world.CastleLayout;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Client screenshots of the court's NPCs (Lawspeaker, Royal Envoy, Clerk, Smith, Market Inspector, Auctioneer, Caravan Master with its camels) and one castle per faction
 * (Aldermark's layout in each faction's look: Hrafnholt and Mirewick have no palette file yet, Azmar's is the one in its module).
 */
public class CourtScreenshots implements FabricClientGameTest {
	private Path out;
	private static final int Y = 100;
	static final Diplomacy.Role[] ROLES = {Diplomacy.Role.LAWSPEAKER, Diplomacy.Role.ENVOY, Diplomacy.Role.CLERK, Diplomacy.Role.INSPECTOR, Diplomacy.Role.AUCTIONEER, Diplomacy.Role.SMITH, Diplomacy.Role.CARAVAN_MASTER};

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
			for (String c : new String[] {"gamerule random_tick_speed 0", "gamerule do_daylight_cycle false", "gamerule do_mob_spawning false", "time set 6000", "gamemode creative @a", "forceload add -48 -48 48 48"}) server.runCommand(c);
			server.runCommand("fill -40 " + (Y - 1) + " -40 40 " + (Y - 1) + " 40 minecraft:grass_block");
			server.runCommand("fill -40 " + Y + " -40 40 " + (Y + 20) + " 40 minecraft:air");
			for (Diplomacy.Role r : ROLES) {
				clear(server);
				spawn(server, r, 0.5, 4.5);
				if (r == Diplomacy.Role.CARAVAN_MASTER) server.runOnServer(s -> AzmarTrade.spawnCaravan(s.overworld(), Identifier.fromNamespaceAndPath("elsewhere", "azmar"), new BlockPos(0, Y, 6)));
				ctx.waitTicks(20);
				view(ctx, sp, server, 0.5, r == Diplomacy.Role.CARAVAN_MASTER ? -2.5 : 0.5, 0, 6, "npc_" + r.path);
			}
			// the whole cast in a row
			clear(server);
			for (int i = 0; i < ROLES.length; i++) spawn(server, ROLES[i], -4.5 + i * 1.5, 4.5);
			ctx.waitTicks(20);
			view(ctx, sp, server, 0.5, -1.0, 0, 4, "npc_all");
			clear(server);
			castles(ctx, sp, server);
		}
	}

	private void castles(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		for (String f : new String[] {"aldermark", "hrafnholt", "azmar", "mirewick"}) {
			Identifier fid = Identifier.fromNamespaceAndPath("elsewhere", f);
			server.runOnServer(s -> {
				ServerLevel l = s.overworld();
				BlockPos o = new BlockPos(0, Y, 0);
				BoundingBox box = CastleLayout.box(CastleLayout.Part.CASTLE, o);
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) l.setChunkForced(cx, cz, true);
			});
			ctx.waitTicks(10);
			server.runCommand("fill -64 " + Y + " -64 64 " + (Y + 24) + " 64 minecraft:air");
			server.runCommand("fill -64 " + (Y - 1) + " -64 64 " + (Y - 1) + " 64 minecraft:grass_block");
			server.runOnServer(s -> {
				ServerLevel l = s.overworld();
				BlockPos o = new BlockPos(0, Y, 0);
				BoundingBox box = CastleLayout.box(CastleLayout.Part.CASTLE, o);
				CastleStyles.Style style = CastleStyles.of(fid);
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					BoundingBox cb = new BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15);
					CastleLayout.Placer pl = new CastleLayout.Placer(l, cb);
					pl.style = style;
					CastleLayout.build(CastleLayout.Part.CASTLE, o, pl);
				}
			});
			ctx.waitTicks(20);
			view(ctx, sp, server, 0.5, 70.5, 180, 22, "castle_" + f + "_front", Y + 22);
			view(ctx, sp, server, 60.5, 40.5, 150, 28, "castle_" + f + "_corner", Y + 30);
			view(ctx, sp, server, 0.5, 30.5, 180, 10, "castle_" + f + "_gate", Y + 4);
			if (f.equals("hrafnholt")) waterfront(ctx, sp, server, fid);
		}
	}

	/** RIVALS-DECISIONS 16a: a lake north of the Hrafnholt castle with the jetty, boathouse and longships (sea level = Y, the shore 60 blocks out). */
	private void waterfront(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, Identifier fid) {
		server.runCommand("forceload add -48 -150 48 -48");
		ctx.waitTicks(20);
		server.runCommand("fill -40 " + (Y - 1) + " -150 40 " + (Y - 1) + " -40 minecraft:grass_block");
		server.runCommand("fill -40 " + Y + " -150 40 " + (Y + 12) + " -40 minecraft:air");
		server.runCommand("fill -30 " + (Y - 6) + " -140 30 " + (Y - 1) + " -61 minecraft:water");
		server.runOnServer(s -> dev.elsewhere.kingdom_court.world.CourtWorld.buildWaterfrontNow(s.overworld(), new BlockPos(0, Y, 0), fid, 3, 60, Y));
		ctx.waitTicks(20);
		view(ctx, sp, server, 0.5, -40.5, 180, 28, "waterfront_front", Y + 16);
		view(ctx, sp, server, -44.5, -76.5, -90, 25, "waterfront_side", Y + 10);
		view(ctx, sp, server, 36.5, -90.5, 120, 30, "waterfront_boathouse", Y + 12);
	}

	private static void spawn(TestServerContext server, Diplomacy.Role r, double x, double z) {
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			DiplomatNpc n = r.type.create(l, EntitySpawnReason.COMMAND);
			n.setFaction(Factions.ALDERMARK);
			n.setUnit(r.unitId());
			n.snapTo(x, Y, z, 180, 0);
			n.setNoAi(true);
			n.setPersistenceRequired();
			n.setYHeadRot(180F);
			n.yBodyRot = 180F;
			l.addFreshEntity(n);
		});
	}

	private void view(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, double px, double pz, float yaw, float pitch, String name) {
		view(ctx, sp, server, px, pz, yaw, pitch, name, Y + 1.0);
	}

	private void view(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, double px, double pz, float yaw, float pitch, String name, double py) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", px, py, pz, yaw, pitch));
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		shot(ctx, name);
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getEntities((Entity) null, new AABB(-60, Y - 5, -60, 60, Y + 30, 60), en -> !(en instanceof net.minecraft.world.entity.player.Player))) e.discard();
		});
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("court_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
