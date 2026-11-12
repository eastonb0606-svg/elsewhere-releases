package dev.elsewhere.verdigris_ironpeak.gametest;

import dev.elsewhere.verdigris_ironpeak.Reg;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Screenshots: both dimensions, every boss in its arena, the mobs, and the items. Run with `gradlew runClientGameTest`. */
public class VerdigrisIronpeakScreenshotTest implements FabricClientGameTest {
	private Path out;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("verdigris_ironpeak.screenshotDir", "screenshots"));
		try {
			Files.createDirectories(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext s = sp.getServer();
			sp.getConnection().waitForChunksRender();
			s.runCommand("gamerule advance_time false");
			s.runCommand("time set 6000");
			s.runCommand("gamemode creative @a");
			s.runCommand("difficulty peaceful");

			// The Verdigris Sea from above the waves.
			s.runCommand("execute as @a run elsewhere_dev tp verdigris_sea");
			s.runCommand("execute in elsewhere:verdigris_sea run tp @a 0 112 0 -30 15");
			settle(ctx, sp, 80);
			shot(ctx, "verdigris_sea");
			s.runCommand("execute in elsewhere:verdigris_sea run tp @a 0 112 0 150 30");
			settle(ctx, sp, 30);
			shot(ctx, "verdigris_sea_2");

			// Ironpeak from the foothills and high up.
			s.runCommand("execute as @a run elsewhere_dev tp ironpeak");
			settle(ctx, sp, 60);
			s.runCommand("execute as @a at @s run tp @s ~ ~2 ~ 30 -5");
			settle(ctx, sp, 40);
			shot(ctx, "ironpeak_foothills");
			s.runCommand("gamemode spectator @a");
			s.runCommand("execute in elsewhere:ironpeak run tp @a 40 1460 40 225 35");
			settle(ctx, sp, 80);
			shot(ctx, "ironpeak_high");

			s.runCommand("difficulty normal");
			// Bosses (built at the player's feet in the air above the sea).
			boss(ctx, sp, s, "verdigris_sea", "verdigrim", "0 140 400", 0, 8, 160);
			boss(ctx, sp, s, "verdigris_sea", "galvanox", "300 140 400", 0, 10, 160);
			s.runCommand("gamemode creative @a");
			boss(ctx, sp, s, "ironpeak", "hrolf", "2000 700 300", 0, 10, 160);
			boss(ctx, sp, s, "ironpeak", "thurgrim", "2300 1400 300", 0, 10, 160);

			// Mobs in a row on a platform.
			s.runCommand("execute in elsewhere:verdigris_sea run tp @a 600 140 400 180 15");
			settle(ctx, sp, 20);
			s.runCommand("execute in elsewhere:verdigris_sea run fill 590 139 390 610 139 410 minecraft:prismarine_bricks");
			String[] mobs = {"clockwork_drowned", "barnacle_golem", "brass_guardian", "tidewing_ray", "tidewright", "highland_yak", "ironclan_raider", "rimeguard", "crag_roc", "clan_quartermaster"};
			for (int i = 0; i < mobs.length; i++) {
				s.runCommand("execute in elsewhere:verdigris_sea run summon elsewhere:" + mobs[i] + " " + (592 + i * 2) + " 140 " + (394) + " {NoAI:1b,PersistenceRequired:1b}");
			}
			s.runCommand("execute in elsewhere:verdigris_sea run tp @a 601 142 386 0 10");
			settle(ctx, sp, 30);
			shot(ctx, "mobs");

			// Roc-Feather Longbow zoom: normal full draw (1.5x) vs sneaking at full draw (2x).
			s.runCommand("gamemode survival @a");
			s.runOnServer(srv -> {
				var p = srv.getPlayerList().getPlayers().get(0);
				p.getInventory().clearContent();
				p.getInventory().setItem(0, new ItemStack(dev.elsewhere.ironpeak.IronpeakItems.ROC_FEATHER_LONGBOW));
				p.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.ARROW, 64));
				p.getInventory().setSelectedSlot(0);
			});
			s.runCommand("execute in elsewhere:verdigris_sea run tp @a 601 140 380 0 0");
			settle(ctx, sp, 20);
			shot(ctx, "longbow_idle");
			ctx.getInput().holdKey(o -> o.keyUse);
			ctx.waitTicks(34);
			shot(ctx, "longbow_draw");
			ctx.getInput().holdKey(o -> o.keyShift);
			ctx.waitTicks(12);
			shot(ctx, "longbow_sneak_zoom");
			ctx.getInput().releaseKey(o -> o.keyShift);
			ctx.getInput().releaseKey(o -> o.keyUse);
			ctx.waitTicks(5);

			// Items.
			s.runCommand("gamemode survival @a");
			s.runOnServer(srv -> {
				var p = srv.getPlayerList().getPlayers().get(0);
				p.getInventory().clearContent();
				int slot = 0;
				for (Item item : Reg.ITEMS.values()) {
					if (slot >= 36) break;
					p.getInventory().setItem(slot++, new ItemStack(item));
				}
			});
			ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(10);
			shot(ctx, "items_1");
			ctx.setScreen(() -> null);
			s.runOnServer(srv -> {
				var p = srv.getPlayerList().getPlayers().get(0);
				p.getInventory().clearContent();
				int slot = 0, i = 0;
				for (Item item : Reg.ITEMS.values()) {
					if (i++ < 36) continue;
					if (slot >= 36) break;
					p.getInventory().setItem(slot++, new ItemStack(item));
				}
			});
			ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(10);
			shot(ctx, "items_2");
			ctx.setScreen(() -> null);
			s.runOnServer(srv -> {
				var p = srv.getPlayerList().getPlayers().get(0);
				p.getInventory().clearContent();
				int slot = 0, i = 0;
				for (Item item : Reg.ITEMS.values()) {
					if (i++ < 72) continue;
					if (slot >= 36) break;
					p.getInventory().setItem(slot++, new ItemStack(item));
				}
			});
			ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(10);
			shot(ctx, "items_3");
			ctx.setScreen(() -> null);
		}
	}

	private void boss(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext s, String dim, String id, String at, int dummy, int back, int wait) {
		s.runCommand("execute in elsewhere:" + dim + " run tp @a " + at);
		settle(ctx, sp, 30);
		s.runCommand("gamemode creative @a");
		s.runCommand("execute as @a at @s run elsewhere_dev boss " + id);
		s.runCommand("execute as @a at @s run tp @s ~ ~5 ~" + back + " 180 25");
		s.runCommand("gamemode spectator @a");
		settle(ctx, sp, 40);
		ctx.waitTicks(wait);
		shot(ctx, "boss_" + id);
		s.runCommand("gamemode creative @a");
		s.runCommand("execute as @a at @s run kill @e[type=!player,distance=..60]");
	}

	private void settle(ClientGameTestContext ctx, TestSingleplayerContext sp, int ticks) {
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(ticks);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(out).disableCounterPrefix());
	}
}
