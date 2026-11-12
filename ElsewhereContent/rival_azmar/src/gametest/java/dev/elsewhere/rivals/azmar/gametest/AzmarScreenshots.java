package dev.elsewhere.rivals.azmar.gametest;

import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.AzmarFalcon;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Client screenshots of every Azmar unit, boss, headpiece (front, side, back), the falcon (perched and in flight) and the item set. */
public class AzmarScreenshots implements FabricClientGameTest {
	private Path out;
	private static final int Y = 100;
	static final String[] UNITS = {"spearguard", "sunguard", "jackal_guard", "camel_lancer", "bowman", "arbalester", "jemadar", "qaid", "scarab_bearer", "crier", "physician", "page", "pikeman", "sand_dancer", "naffatun"};
	static final String[] ITEMS = {"azmar_dinar", "azmar_seal_sultana", "azmar_seal_wazir", "azmar_seal_falconer", "azmar_seal_amir", "azmar_nemes_of_the_uraeus", "azmar_ibis_crest", "azmar_falconers_gauntlet",
		"azmar_sandrunner_sandals", "azmar_winged_scarab_standard", "azmar_copper_khopesh", "azmar_gilded_khopesh", "azmar_twin_khopesh", "azmar_scarab_spear", "azmar_composite_bow", "azmar_fire_pot",
		"azmar_jackal_helm", "azmar_falcon_helm", "azmar_horned_scarab_crown", "azmar_sunplate_cuirass", "azmar_armor_trim_smithing_template", "azmar_banner_pattern"};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("azmar.screenshotDir", "screenshots"));
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
			for (String c : new String[] {"gamerule random_tick_speed 0", "gamerule do_daylight_cycle false", "gamerule do_mob_spawning false", "time set 6000", "gamemode creative @a",
				"forceload add -48 -48 48 48"}) server.runCommand(c);
			server.runCommand("tp @a 0 " + (Y + 2) + " 0");
			ctx.waitTicks(80);                                    // let the forceloaded chunks generate before the platform is filled
			server.runCommand("fill -40 " + (Y - 1) + " -40 40 " + (Y - 1) + " 40 minecraft:smooth_sandstone");
			ctx.waitTicks(10);
			server.runCommand("fill -40 " + Y + " -40 40 " + (Y + 3) + " 40 minecraft:air");
			// full-body views of each unit
			for (String u : UNITS) {
				clear(server);
				spawnSoldier(server, u, 0.5, 4.5);
				view(ctx, sp, server, 0.5, 0.5, 0, 0, "unit_" + u);
			}
			// bosses: full body, then head from the front, side and back
			String[] bosses = {"sultana", "wazir", "falconer", "amir"};
			for (String b : bosses) {
				clear(server);
				spawnBoss(server, b, 0.5, 5.5);
				ctx.waitTicks(30);
				server.runOnServer(sv -> { for (var e : sv.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(-60, Y - 5, -60, 60, Y + 30, 60), en -> en instanceof Mob)) { Mob mb = (Mob) e; mb.setNoAi(true); mb.setDeltaMovement(Vec3.ZERO); mb.setYRot(180F); mb.yRotO = 180F; mb.yBodyRot = 180F; mb.yBodyRotO = 180F; mb.setYHeadRot(180F); mb.yHeadRotO = 180F; } });
				ctx.waitTicks(4);
				// the Amir's camel is seen from the front-left three-quarter so the caparison and the rider's face show
				if (b.equals("amir")) view(ctx, sp, server, 3.3, 1.8, 36.9F, 8, "boss_" + b);
				else view(ctx, sp, server, 0.5, 0.5, 0, 0, "boss_" + b);
				head(ctx, sp, server, b);
			}
			clear(server);
			spawnSoldier(server, "jackal_guard", 0.5, 4.5);
			head(ctx, sp, server, "jackal_guard");
			falcon(ctx, sp, server);
			items(ctx, sp, server);
			clear(server);
		}
	}

	private static void spawnSoldier(TestServerContext server, String u, double x, double z) {
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			KingdomSoldier k = KingdomApi.spawnSoldier(l, AzmarContent.id("azmar_" + u), AzmarContent.FACTION, new Vec3(x, Y, z), null);
			if (k == null) return;
			k.setNoAi(true);
			k.setPersistenceRequired();
			k.snapTo(x, Y, z, 180, 0);
			k.setYHeadRot(180F);
			k.yBodyRot = 180F;
		});
	}

	private static void spawnBoss(TestServerContext server, String b, double x, double z) {
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			EntityType<? extends Mob> t = switch (b) {
				case "sultana" -> AzmarBosses.SULTANA;
				case "wazir" -> AzmarBosses.WAZIR;
				case "falconer" -> AzmarBosses.FALCONER;
				default -> AzmarBosses.AMIR;
			};
			Mob m = t.create(l, EntitySpawnReason.COMMAND);
			m.snapTo(x, Y, z, 180, 0);
			m.setPersistenceRequired();
			if (!b.equals("amir")) m.setNoAi(true);          // a still pose without guards or companions (the Amir needs a few ticks to mount his camel)
			l.addFreshEntity(m);
			m.setYRot(180F);
			m.setYHeadRot(180F);
			m.yBodyRot = 180F;
		});
	}

	private void view(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, double px, double pz, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f", px, Y, pz, yaw, pitch));
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		shot(ctx, name);
	}

	/** Front, side and back close-ups of the head. */
	private void head(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, String who) {
		double z = who.equals("jackal_guard") ? 4.5 : 5.5;
		double eye = who.equals("jackal_guard") ? 0.5 : 1.0;   // the pitch looks slightly up at the head
		server.runCommand(String.format(Locale.ROOT, "tp @a 0.5 %.2f %.2f 0 -8", Y + 0.55, z - 2.4));
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(8);
		shot(ctx, "head_" + who + "_front");
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 90 -8", 2.9, Y + 0.55, z));
		ctx.waitTicks(6);
		shot(ctx, "head_" + who + "_side");
		server.runCommand(String.format(Locale.ROOT, "tp @a 0.5 %.2f %.2f 180 -8", Y + 0.55, z + 2.4));
		ctx.waitTicks(6);
		shot(ctx, "head_" + who + "_back");
		server.runCommand(String.format(Locale.ROOT, "tp @a 1.9 %.2f %.2f 40 -8", Y + 0.55, z - 2.0));
		ctx.waitTicks(6);
		shot(ctx, "head_" + who + "_three_quarter");
	}

	private void falcon(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			AzmarFalcon f = AzmarFalcon.TYPE.create(l, EntitySpawnReason.COMMAND);
			f.snapTo(-0.8, Y, 3.5, 150, 0);
			f.setNoAi(true);
			f.setPersistenceRequired();
			l.addFreshEntity(f);
			AzmarFalcon g = AzmarFalcon.TYPE.create(l, EntitySpawnReason.COMMAND);
			g.snapTo(0.9, Y + 1.8, 3.5, 200, 0);
			g.setNoGravity(true);
			g.setNoAi(true);
			g.setPersistenceRequired();
			l.addFreshEntity(g);
		});
		server.runCommand("tp @a 0.5 " + (Y + 1) + " 0.5 0 12");
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(12);
		shot(ctx, "falcon_perched_and_flying");
		server.runCommand("tp @a -0.8 " + (Y + 0.6) + " 1.9 0 8");
		ctx.waitTicks(8);
		shot(ctx, "falcon_perched_closeup");
	}

	private void items(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		for (int i = 0; i < ITEMS.length; i++) {
			double x = -5.5 + (i % 8) * 1.5, y = Y + 3.4 - (i / 8) * 1.4;
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:item_display %.2f %.2f 8.5 {item:{id:\"elsewhere:%s\",count:1},item_display:\"fixed\",transformation:{left_translation:[0f,0f,0f],right_translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[1.3f,1.3f,1.3f]}}", x, y, ITEMS[i]));
		}
		server.runCommand("tp @a 0.5 " + (Y + 2) + " 1.5 0 0");
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(15);
		shot(ctx, "items_all");
		for (String id : new String[] {"azmar_copper_khopesh", "azmar_gilded_khopesh", "azmar_twin_khopesh"}) {
			clear(server);
			server.runCommand("summon minecraft:item_display 0.5 " + (Y + 1.6) + " 3.5 {item:{id:\"elsewhere:" + id + "\",count:1},item_display:\"fixed\",transformation:{left_translation:[0f,0f,0f],right_translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[2.6f,2.6f,2.6f]}}");
			server.runCommand("tp @a 0.5 " + (Y + 1.6) + " 0.5 0 0");
			ctx.waitTicks(8);
			shot(ctx, "khopesh_" + id.replace("azmar_", "").replace("_khopesh", ""));
		}
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(s -> {
			for (var e : s.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(-60, Y - 5, -60, 60, Y + 30, 60), en -> !(en instanceof net.minecraft.world.entity.player.Player))) e.discard();
		});
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("azmar_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
