package dev.elsewhere.greenhaze_hush.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Screenshots of both dimensions, all four bosses in their arenas, the mobs and the items (saved to screenshots/). */
public class ScreenshotTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			var server = sp.getServer();
			ctx.waitTicks(40);
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("execute as @p at @p run elsewhere_dev tp greenhaze");
			ctx.waitTicks(120);
			server.runCommand("execute as @p at @p run tp @s ~ ~12 ~ -30 15");
			server.runCommand("gamemode spectator @p");
			ctx.waitTicks(100);
			shot(ctx, "greenhaze");

			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p at @p run tp @s ~200 ~ ~");
			ctx.waitTicks(60);
			server.runCommand("execute as @p at @p positioned over motion_blocking_no_leaves run tp @s ~ ~ ~");
			ctx.waitTicks(20);
			server.runCommand("execute as @p at @p run elsewhere_dev boss percolax");
			ctx.waitTicks(40);
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p at @p run tp @s ~ ~4 ~10 facing ~ ~4 ~-6");
			ctx.waitTicks(80);
			shot(ctx, "boss_percolax");

			server.runCommand("gamemode creative @p");
			server.runCommand("kill @e[type=elsewhere:percolax]");
			server.runCommand("execute as @p at @p run tp @s ~200 ~ ~");
			ctx.waitTicks(60);
			server.runCommand("execute as @p at @p positioned over motion_blocking_no_leaves run tp @s ~ ~ ~");
			ctx.waitTicks(20);
			server.runCommand("execute as @p at @p run elsewhere_dev boss grumblegut");
			ctx.waitTicks(40);
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p at @p run tp @s ~12 ~5 ~-4 facing ~ ~2 ~-12");
			ctx.waitTicks(80);
			shot(ctx, "boss_grumblegut");

			// mob lineup in the Greenhaze
			server.runCommand("execute as @p at @p run tp @s ~ ~ ~40");
			ctx.waitTicks(40);
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p at @p positioned over motion_blocking_no_leaves run tp @s ~ ~ ~");
			ctx.waitTicks(10);
			String[] gm = {"lazy_gorger", "munchie_boar", "toke_toad", "hotbox_smog", "bud_bomb", "budtender"};
			for (int i = 0; i < gm.length; i++) {
				server.runCommand("execute as @p at @p run summon elsewhere:" + gm[i] + " ~" + (i * 3 - 7) + " ~ ~-7 {NoAI:1b}");
			}
			server.runCommand("execute as @p at @p run tp @s ~ ~3 ~2 facing ~ ~ ~-7");
			ctx.waitTicks(40);
			shot(ctx, "mobs_greenhaze");

			// The Hush
			server.runCommand("effect give @p night_vision 99999 0 true");
			server.runCommand("execute as @p at @p run elsewhere_dev tp the_hush");
			ctx.waitTicks(120);
			shot(ctx, "the_hush");
			server.runCommand("execute as @p at @p run tp @s ~200 ~ ~");
			ctx.waitTicks(40);
			server.runCommand("execute as @p at @p run elsewhere_dev boss auris");
			ctx.waitTicks(40);
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p at @p run tp @s ~ ~1 ~10 facing ~ ~-1 ~2");
			ctx.waitTicks(80);
			shot(ctx, "boss_auris");
			server.runCommand("gamemode creative @p");
			server.runCommand("kill @e[type=elsewhere:auris]");
			server.runCommand("execute as @p at @p run tp @s ~200 ~ ~");
			ctx.waitTicks(40);
			server.runCommand("execute as @p at @p run elsewhere_dev boss lithara");
			ctx.waitTicks(40);
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p at @p run tp @s ~ ~4 ~16 facing ~ ~4 ~");
			ctx.waitTicks(80);
			shot(ctx, "boss_lithara");
			server.runCommand("gamemode creative @p");
			String[] hm = {"echo_stalker", "ossuary_chorister", "catalyst_mimic", "shrieker_swarm", "mute_archivist"};
			for (int i = 0; i < hm.length; i++) {
				server.runCommand("execute as @p at @p run summon elsewhere:" + hm[i] + " ~" + (i * 3 - 6) + " ~ ~-8 {NoAI:1b}");
			}
			server.runCommand("execute as @p at @p run tp @s ~ ~2 ~ facing ~ ~1 ~-8");
			ctx.waitTicks(40);
			shot(ctx, "mobs_hush");

			// items
			server.runCommand("clear @p");
			String[] items = {"trichome_sword", "trichome_pickaxe", "trichome_axe", "frond_hood", "frond_cloak", "frond_leggings", "frond_sandals",
				"haze_resin", "hazeborn_bud", "lucid_water", "munchie_bacon", "hotbox_lantern", "everflow_bong", "hotbox_visor", "cloud_jar",
				"endless_snack_pouch", "couchlock_club", "grumblegut_tusk", "echo_sword", "echo_pickaxe", "hushweave_hood", "hushcloak",
				"hushstep_greaves", "hushweave_boots", "sonar_horn", "sculk_censer", "silence_draught", "tuning_fork_of_auris", "choir_vestment",
				"cantors_bell_shard", "resonant_tuning", "resonant_shard", "echo_crystal", "kushwood_log", "hushstone_bricks", "giant_fern"};
			for (String it : items) server.runCommand("give @p elsewhere:" + it);
			ctx.waitTicks(10);
			ctx.setScreen(() -> new InventoryScreen(ctx.computeOnClient(mc -> mc.player)));
			ctx.waitTicks(20);
			shot(ctx, "items");
			ctx.setScreen(() -> null);
		}
	}

	private static void shot(ClientGameTestContext ctx, String name) {
		Path p = ctx.takeScreenshot("greenhaze_hush_" + name);
		String dir = System.getProperty("greenhaze_hush.screenshotDir");
		if (dir != null) {
			try {
				Files.createDirectories(Path.of(dir));
				Files.copy(p, Path.of(dir, name + ".png"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
