package dev.elsewhere.overworld_bosses.gametest;

import dev.elsewhere.overworld_bosses.OwbItems;
import dev.elsewhere.overworld_bosses.dev.DevCommands;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import dev.elsewhere.stub.overworld_bosses.SignatureGear;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Client screenshots: each boss in its arena, mid-fight, and an inventory with every drop. */
public class OverworldBossesScreenshots implements FabricClientGameTest {
	private Path out;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String dir = System.getProperty("overworld_bosses.screenshotDir", "screenshots");
		out = Path.of(dir);
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
			server.runCommand("gamerule random_tick_speed 0");
			server.runCommand("gamerule do_daylight_cycle false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode spectator @a");
			String[] ids = DevCommands.BOSSES;
			for (int i = 0; i < ids.length; i++) {
				String id = ids[i];
				BlockPos c = new BlockPos(i * 120 + 200, 110, 200);
				server.runCommand("forceload add " + (c.getX() - 32) + " " + (c.getZ() - 32) + " " + (c.getX() + 32) + " " + (c.getZ() + 32));
				server.runOnServer(s -> {
					var level = s.overworld();
					ElsewhereBoss boss = DevCommands.spawnWithArena(level, id, c);
					if (boss != null) boss.setNoAi(false);
				});
				double vx = c.getX() + 0.5, vy = c.getY() + 6, vz = c.getZ() + 15.5;
				if (id.equals("neferkhet")) { vy = c.getY() + 5; vz = c.getZ() + 9.5; }
				if (id.equals("vorlagh")) { vy = c.getY() + 9; vz = c.getZ() + 22.5; }
				tp(server, vx, vy, vz, 180, 18);
				sp.getConnection().waitForChunksRender();
				ctx.waitTicks(60);
				shot(ctx, id);
				server.runOnServer(s -> {
					for (ElsewhereBoss b : s.overworld().getEntitiesOfClass(ElsewhereBoss.class, new net.minecraft.world.phys.AABB(c).inflate(40), e -> true)) b.discard();
				});
			}
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				ServerPlayer pl = s.getPlayerList().getPlayers().get(0);
				Inventory inv = pl.getInventory();
				inv.clearContent();
				Item[] items = {OwbItems.LILYCROWN, OwbItems.GULP_TONGUE, OwbItems.FROG_LEGS, OwbItems.BUFO_GLAND, OwbItems.HARVEST_SCYTHE, OwbItems.SCARECROWS_GRIN,
					OwbItems.HARVEST_MOON_SEEDS, OwbItems.HARVEST_LOAF, OwbItems.HARVEST_MOON_BUD, OwbItems.CROW_FEATHER, OwbItems.ANKH_OF_NEFERKHET, OwbItems.SUNFIRE_KHOPESH,
					OwbItems.PHARAOHS_NEMES, OwbItems.ROYAL_HONEY, OwbItems.LINEN_WRAP, OwbItems.RIMEHEART_MAUL, OwbItems.RIMEWALKER_BOOTS, OwbItems.GLACIER_WATER,
					OwbItems.UNDERTOW, OwbItems.KRAKEN_INK_BOMB, OwbItems.ABYSSAL_EYE, OwbItems.SIREN_CONCH, OwbItems.KRAKEN_CALAMARI,
					SignatureGear.CHALLENGERS_CLEAVER, SignatureGear.AEGIS_OF_THE_LADDER, SignatureGear.PHASEWALKER_GREAVES,
					dev.elsewhere.stub.overworld_bosses.BossRegistry.BOSS_TOKEN};
				int slot = 9;
				for (Item it : items) inv.setItem(slot++, new ItemStack(it));
				inv.setItem(0, new ItemStack(dev.elsewhere.overworld_bosses.OwbBlocks.FROZEN_HEART));
				inv.setItem(1, new ItemStack(dev.elsewhere.overworld_bosses.OwbBlocks.NEFERKHET_SUN_ALTAR));
				inv.setItem(2, new ItemStack(dev.elsewhere.overworld_bosses.OwbBlocks.NEFERKHET_SUN_IDOL));
				inv.setItem(3, new ItemStack(dev.elsewhere.overworld_bosses.OwbBlocks.GULPMOTHER_SHRINE_CAULDRON));
				inv.setItem(4, new ItemStack(dev.elsewhere.overworld_bosses.OwbBlocks.VORLAGH_CAPSTAN));
			});
			sp.getConnection().waitForClientboundPackets();
			ctx.waitTicks(5);
			ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(5);
			shot(ctx, "inventory");
			ctx.setScreen(() -> null);
		}
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("overworld_bosses_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
