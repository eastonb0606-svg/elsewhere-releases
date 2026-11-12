package dev.elsewhere.duelist.gametest;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Client screenshots of a sample duelist mid-move: idle, shield, crouch wind-up, raised-arm swing, bow draw, drinking, bridge, thrown gear, a ruler. */
public class DuelistScreenshots implements FabricClientGameTest {
	private Path out;
	private static final int Y = 100;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("duelist.screenshotDir", "screenshots"));
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
			server.runCommand("gamerule do_mob_spawning false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a minecraft:resistance infinite 4 true");
			server.runCommand("forceload add -48 -48 48 48");
			server.runCommand("fill -40 " + (Y - 1) + " -40 40 " + (Y - 1) + " 40 minecraft:grass_block");
			server.runCommand("fill -40 " + Y + " -40 40 " + (Y + 8) + " 40 minecraft:air");
			anims(ctx, sp, server);
			if (Boolean.getBoolean("duelist.animOnly")) {
				clear(server);
				return;
			}
			scene(ctx, sp, server, "idle", 4.5, b -> {});
			scene(ctx, sp, server, "shield_up", 4.5, b -> {
				b.setItemSlot(EquipmentSlot.OFFHAND, HumanBoss.shieldStack(120F, 5));
				b.startUsingItem(InteractionHand.OFF_HAND);
			});
			scene(ctx, sp, server, "crit_crouch_windup", 4.5, b -> b.startTelegraph("crit", "Sprint Strike", 400, Telegraph.Weight.ORDINARY, 24F, b.style().cue(true, false), true, () -> {}));
			scene(ctx, sp, server, "swing_windup_raised_arm", 4.5, b -> b.startTelegraph("swing", "Heavy Strike", 400, Telegraph.Weight.HEAVY, 30F, b.style().cue(false, true), true, () -> {}));
			scene(ctx, sp, server, "bow_draw", 8.0, b -> {
				b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				b.startUsingItem(InteractionHand.MAIN_HAND);
			});
			scene(ctx, sp, server, "drinking_potion", 4.5, b -> {
				b.setItemSlot(EquipmentSlot.MAINHAND, HumanBoss.healingPotion());
				b.startUsingItem(InteractionHand.MAIN_HAND);
			});
			scene(ctx, sp, server, "ruler_with_armor", 4.5, b -> {
				b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				b.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				b.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			});
			bridge(ctx, sp, server);
			thrown(ctx, sp, server);
			// 0.2.0: two differently configured test duelists, each winding up in its own look (particle, colour, shape)
			scene(ctx, sp, server, "telegraph_ember_spiral", 3.6, b -> b.startTelegraph("look", "Ember Strike", 400, Telegraph.Weight.HEAVY, 30F, b.style().cue(false, true), true, () -> {}));
			scene(ctx, sp, server, "ruler_telegraph_violet_ring", 3.6, b -> b.startTelegraph("look", "Violet Verdict", 400, Telegraph.Weight.HEAVY, 30F, b.style().cue(false, true), true, () -> {}));
			looks(ctx, sp, server);
			clear(server);
		}
	}

	// ---- 3.0 (P4) move animations ----------------------------------------------------------------------------------

	private record Move3(String shot, String id, boolean ruler, net.minecraft.world.item.Item main, net.minecraft.world.item.Item off, boolean crouch) {}

	private static final Move3[] MOVES3 = {
			new Move3("overhead", "oath_cleave", false, Items.IRON_AXE, Items.AIR, false),
			new Move3("sweep", "crescent", true, Items.DIAMOND_SWORD, Items.AIR, false),
			new Move3("thrust", "long_thrust", false, Items.TRIDENT, Items.AIR, false),
			new Move3("slam", "ninth_winter_slam", true, Items.MACE, Items.AIR, false),
			new Move3("leap", "pelt_leap", false, Items.IRON_SWORD, Items.AIR, false),
			new Move3("shield_bash", "bash_chain", false, Items.IRON_SWORD, Items.SHIELD, false),
			new Move3("draw", "rune_throw", true, Items.IRON_AXE, Items.AIR, false),
			new Move3("cast", "glyph_flare", true, Items.BLAZE_ROD, Items.AIR, false)};

	/** Each move key mid wind-up and just after it fires, a three-quarter view, then four bosses side by side mid-move. */
	private void anims(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		for (Move3 m : MOVES3) {
			animScene(ctx, sp, server, m, 400);
			ctx.waitTicks(10);
			shot(ctx, "anim_" + m.shot() + "_windup");
			animScene(ctx, sp, server, m, 10);
			ctx.waitTicks(11);
			shot(ctx, "anim_" + m.shot() + "_strike");
		}
		clear(server);
		tp(server, 0.5, Y, 0.5, 0, 6);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			int[] pick = {0, 3, 7, 5};
			double[] xs = {4.2, 1.4, -1.4, -4.2};
			for (int i = 0; i < 4; i++) {
				Move3 m = MOVES3[pick[i]];
				TestDuelist b = spawn3(l, m, 0.5 + xs[i], 7.0, 180F + (float) (xs[i] * 6));
				b.startTelegraph(m.id(), m.id(), 400, Telegraph.Weight.HEAVY, 30F, b.style().cue(m.crouch(), !m.crouch()), true, () -> {});
			}
		});
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(14);
		shot(ctx, "anim_group_four_moves");
	}

	private void animScene(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, Move3 m, int windup) {
		clear(server);
		tp(server, 0.5, Y, 0.5, 0, 8);
		if (windup > 20) sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			TestDuelist b = spawn3(s.overworld(), m, 0.5, 4.2, 135F);
			b.startTelegraph(m.id(), m.id(), windup, windup > 20 ? Telegraph.Weight.HEAVY : Telegraph.Weight.ORDINARY, windup > 20 ? 30F : 0F, b.style().cue(m.crouch(), !m.crouch()), true, () -> {});
		});
	}

	private static TestDuelist spawn3(ServerLevel l, Move3 m, double x, double z, float yaw) {
		TestDuelist b = m.ruler() ? DuelistContent.TEST_RULER.create(l, EntitySpawnReason.COMMAND) : DuelistContent.TEST_DUELIST.create(l, EntitySpawnReason.COMMAND);
		b.passive = true;
		b.snapTo(x, Y, z, yaw, 0);
		l.addFreshEntity(b);
		b.setYRot(yaw);
		b.setYHeadRot(yaw);
		b.yBodyRot = yaw;
		b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(m.main()));
		b.setItemSlot(EquipmentSlot.OFFHAND, m.off() == Items.AIR ? ItemStack.EMPTY : new ItemStack(m.off()));
		return b;
	}

	private interface Setup {
		void apply(TestDuelist b);
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private void scene(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, String name, double dist, Setup setup) {
		clear(server);
		tp(server, 0.5, Y, 0.5, 0, 0);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			TestDuelist b = name.startsWith("ruler") ? DuelistContent.TEST_RULER.create(l, EntitySpawnReason.COMMAND) : DuelistContent.TEST_DUELIST.create(l, EntitySpawnReason.COMMAND);
			b.passive = true;
			b.snapTo(0.5, Y, 0.5 + dist, 180, 0);
			l.addFreshEntity(b);
			b.setYRot(180F);
			b.setYHeadRot(180F);
			b.yBodyRot = 180F;
			setup.apply(b);
		});
		tp(server, 0.5, Y, 0.5, 0, 6);
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(12);
		server.runOnServer(s -> {
			// keep the pose alive: the longer wind-ups were started with 400 ticks, the item use needs a refresh
			for (TestDuelist b : s.overworld().getEntitiesOfClass(TestDuelist.class, new AABB(-20, Y - 5, -20, 20, Y + 10, 20))) b.passive = true;
		});
		shot(ctx, name);
	}

	/** Both test duelists side by side, each mid wind-up in its own telegraph look. */
	private void looks(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y, 0.5, 0, 4);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			TestDuelist a = DuelistContent.TEST_DUELIST.create(l, EntitySpawnReason.COMMAND);
			TestDuelist r = DuelistContent.TEST_RULER.create(l, EntitySpawnReason.COMMAND);
			double x = 2.4;
			for (TestDuelist b : new TestDuelist[] {a, r}) {
				b.passive = true;
				b.snapTo(0.5 + x, Y, 5.6, 180, 0);
				l.addFreshEntity(b);
				b.setYRot(180F);
				b.setYHeadRot(180F);
				b.yBodyRot = 180F;
				b.startTelegraph("look", "Look", 400, Telegraph.Weight.HEAVY, 30F, b.style().cue(false, true), true, () -> {});
				x = -x;
			}
		});
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(30);
		shot(ctx, "telegraph_two_looks_side_by_side");
	}

	private void bridge(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		server.runCommand("fill -3 " + (Y - 1) + " 6 3 " + (Y - 1) + " 12 minecraft:air");
		tp(server, 0.5, Y, 0.5, 0, 8);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int z = 6; z <= 10; z++) DuelBlocks.place(l, new BlockPos(0, Y - 1, z), java.util.UUID.nameUUIDFromBytes("shot".getBytes()), null, 600);
			DuelBlocks.place(l, new BlockPos(1, Y - 1, 9), java.util.UUID.nameUUIDFromBytes("shot".getBytes()), null, 600);
			TestDuelist b = DuelistContent.TEST_DUELIST.create(l, EntitySpawnReason.COMMAND);
			b.passive = true;
			b.snapTo(0.5, Y, 8.5, 0, 0);
			l.addFreshEntity(b);
		});
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		shot(ctx, "temporary_bridge_duel_blocks");
		server.runOnServer(s -> DuelBlocks.clearAll(s.overworld()));
		server.runCommand("fill -3 " + (Y - 1) + " 6 3 " + (Y - 1) + " 12 minecraft:grass_block");
	}

	private void thrown(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y, 0.5, 0, 4);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			TestDuelist b = DuelistContent.TEST_DUELIST.create(l, EntitySpawnReason.COMMAND);
			b.passive = true;
			b.snapTo(4.5, Y, 7.5, 150, 0);
			l.addFreshEntity(b);
			Mob d = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
			d.snapTo(0.5, Y, 5.5, 0, 0);
			d.setNoAi(true);
			d.setPersistenceRequired();
			l.addFreshEntity(d);
			DuelistProjectile.throwAt(l, b, d, new ItemStack(Items.IRON_AXE), 14F, 1.2F);
		});
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(5);
		shot(ctx, "thrown_axe_in_flight");
		ctx.waitTicks(8);
		shot(ctx, "thrown_axe_stuck");
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(s -> {
			for (Mob m : s.overworld().getEntitiesOfClass(Mob.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) m.discard();
			for (DuelistProjectile p : s.overworld().getEntitiesOfClass(DuelistProjectile.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) p.discard();
		});
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("duelist_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
