package dev.elsewhere.arsenal.gametest;

import dev.elsewhere.arsenal.ArsenalEffects;
import dev.elsewhere.arsenal.ArsenalItems;
import dev.elsewhere.arsenal.Overhaul3;
import dev.elsewhere.arsenal.client.CallingPanel;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.hud.ElsewhereHud;
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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;

/** Client screenshots: Glimmer bar in each mode, each caster weapon firing, the armor, an inventory of every item. */
public class ArsenalScreenshots implements FabricClientGameTest {
	private Path out;
	private static final int Y = 100;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("arsenal.screenshotDir", "screenshots"));
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
			if (Overhaul3.CALLINGS && Overhaul3.on("calling_panel")) calling(ctx, sp, server);
			if (System.getenv("ARSENAL_CALLING_ONLY") != null) return;
			hud(ctx, sp, server);
			weapon(ctx, sp, server, ArsenalItems.AMETHYST_WAND, "amethyst_wand", 3);
			weapon(ctx, sp, server, ArsenalItems.SUNLANCE_STAFF, "sunlance_staff", 1);
			weapon(ctx, sp, server, ArsenalItems.STORMCOIL_STAFF, "stormcoil_staff", 1);
			weapon(ctx, sp, server, ArsenalItems.SCULK_RESONATOR, "sculk_resonator", 1);
			armor(ctx, sp, server);
			inventory(ctx, sp, server);
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void hud(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		tp(server, 0.5, Y, 0.5, 180, 10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().setItem(0, new ItemStack(ArsenalItems.AMETHYST_WAND));
			p.getInventory().setItem(1, new ItemStack(ArsenalItems.GLIMMERSTONE_FOCUS));
			p.getInventory().setSelectedSlot(0);
			Glimmer.setForTest(p, 9);
			Glimmer.markSpentForTest(p, -100000);
			Glimmer.forceSync(p);
		});
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> ElsewhereHud.setForTest("glimmer", ElsewhereHud.Mode.AUTO));
		shot(ctx, "hud_auto_holding_wand");
		server.runOnServer(s -> player(s).getInventory().setSelectedSlot(5));
		ctx.runOnClient(mc -> ElsewhereHud.setForTest("glimmer", ElsewhereHud.Mode.ALWAYS));
		ctx.waitTicks(3);
		shot(ctx, "hud_always");
		ctx.runOnClient(mc -> ElsewhereHud.setForTest("glimmer", ElsewhereHud.Mode.HIDDEN));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().setSelectedSlot(0);
			Glimmer.setForTest(p, 0);
			((CasterItem) ArsenalItems.AMETHYST_WAND).castNow(s.overworld(), p, p.getMainHandItem(), InteractionHand.MAIN_HAND);
		});
		ctx.waitTicks(3);
		shot(ctx, "hud_hidden_failed_cast");
		ctx.runOnClient(mc -> ElsewhereHud.setForTest("glimmer", ElsewhereHud.Mode.AUTO));
		server.runOnServer(s -> {
			Glimmer.devMinions = 2;
			ServerPlayer p = player(s);
			Glimmer.setForTest(p, 2);
			Glimmer.forceSync(p);
		});
		ctx.waitTicks(3);
		shot(ctx, "hud_auto_reserve");
		server.runOnServer(s -> {
			Glimmer.devMinions = 0;
			ServerPlayer p = player(s);
			Glimmer.markSpentForTest(p, 100);
			Glimmer.setForTest(p, 40);
		});
	}

	/** 3.0 Calling panel (GEAR.md A1): full Moonsilk + Sunlance derives Caster Primary; the panel is opt-in, shown here for the shot. */
	private void calling(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y, 0.5, 180, 10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ArsenalItems.MOONSILK_HOOD));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ArsenalItems.MOONSILK_ROBE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArsenalItems.MOONSILK_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArsenalItems.MOONSILK_BOOTS));
			p.getInventory().setItem(0, new ItemStack(ArsenalItems.SUNLANCE_STAFF));
			p.getInventory().setSelectedSlot(0);
			Calling.recompute(p, true);
		});
		sp.getConnection().waitForChunksRender();
		sp.getConnection().waitForClientboundPackets();
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> CallingPanel.shown = true);
		ctx.waitTicks(2);
		shot(ctx, "calling_panel_caster");
		ctx.runOnClient(mc -> CallingPanel.shown = false);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) p.setItemSlot(slot, ItemStack.EMPTY);
			p.getInventory().setItem(0, ItemStack.EMPTY);
			Calling.recompute(p, true);
		});
		ctx.waitTicks(2);
	}

	private void weapon(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, Item item, String name, int casts) {
		clear(server);
		tp(server, 0.5, Y, 6.5, 180, 4);
		sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			mob(l, 0.5, -3.5);
			mob(l, -1.5, -5.5);
			mob(l, 1.8, -6.5);
			ServerPlayer p = player(s);
			p.getInventory().setItem(0, new ItemStack(item));
			p.getInventory().setSelectedSlot(0);
			Glimmer.setForTest(p, 40);
		});
		ctx.waitTicks(8);
		for (int i = 0; i < casts; i++) {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(p.getMainHandItem()));
				Glimmer.setForTest(p, 40);
				((CasterItem) item).castNow(s.overworld(), p, p.getMainHandItem(), InteractionHand.MAIN_HAND);
			});
			ctx.waitTicks(2);
		}
		shot(ctx, "fire_" + name);
	}

	private void armor(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y + 0.6, 4.0, 180, 8);
		sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			Item[][] sets = {
				{ArsenalItems.MOONSILK_HOOD, ArsenalItems.MOONSILK_ROBE, ArsenalItems.MOONSILK_LEGGINGS, ArsenalItems.MOONSILK_BOOTS},
				{ArsenalItems.LINED_MOONSILK_HOOD, ArsenalItems.LINED_MOONSILK_ROBE, ArsenalItems.LINED_MOONSILK_LEGGINGS, ArsenalItems.LINED_MOONSILK_BOOTS},
				{ArsenalItems.CANTORS_DIADEM, ArsenalItems.STORMSAGE_MANTLE, ArsenalItems.MOONSILK_LEGGINGS, ArsenalItems.MOONSILK_BOOTS},
			};
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (int i = 0; i < 3; i++) {
				ArmorStand a = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
				a.snapTo(-1.6 + i * 2.1, Y, 0.5, 0F, 0F);
				a.setYBodyRot(0F);
				a.setYHeadRot(0F);
				for (int k = 0; k < 4; k++) a.setItemSlot(slots[k], new ItemStack(sets[i][k]));
				l.addFreshEntity(a);
			}
		});
		ctx.waitTicks(10);
		shot(ctx, "armor_sets");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ArsenalItems.MOONSILK_HOOD));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ArsenalItems.MOONSILK_ROBE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArsenalItems.MOONSILK_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArsenalItems.MOONSILK_BOOTS));
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(ArsenalItems.SUNLANCE_STAFF));
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
		tp(server, 0.5, Y, 0.5, 200, 10);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(15);
		shot(ctx, "player_moonsilk");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private void inventory(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Inventory inv = p.getInventory();
			inv.clearContent();
			int slot = 9;
			for (Item it : ArsenalItems.ALL) inv.setItem(slot++, new ItemStack(it));
			inv.setItem(slot++, PotionContents.createItemStack(Items.POTION, ArsenalEffects.CLARITY_DRAUGHT));
			inv.setItem(slot++, PotionContents.createItemStack(Items.POTION, ArsenalEffects.LONG_CLARITY_DRAUGHT));
			inv.setItem(0, new ItemStack(ArsenalItems.SCULK_RESONATOR));
			inv.setItem(1, new ItemStack(ArsenalItems.GLIMMERSTONE_FOCUS));
		});
		sp.getConnection().waitForClientboundPackets();
		ctx.waitTicks(5);
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.getInput().setCursorPos(2, 2);
		ctx.waitTicks(5);
		shot(ctx, "inventory");
		ctx.setScreen(() -> null);
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(s -> {
			for (Mob m : s.overworld().getEntitiesOfClass(Mob.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) m.discard();
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
	}

	private static Mob mob(ServerLevel l, double x, double z) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(x, Y, z, 0, 0);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setHealth(m.getMaxHealth());
		l.addFreshEntity(m);
		return m;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("arsenal_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
