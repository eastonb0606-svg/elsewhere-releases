package dev.elsewhere.rivals.mirewick.gametest;

import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import dev.elsewhere.rivals.mirewick.MirewickLoot;
import dev.elsewhere.rivals.mirewick.MirewickUnits;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import dev.elsewhere.rivals.mirewick.boss.MagnateBoss;
import dev.elsewhere.rivals.mirewick.item.MirewickItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Client screenshots of every Mirewick unit, Magnate and key item (look at them: skins, armor layering, trims, icons). */
public class MirewickScreenshots implements FabricClientGameTest {
	private Path out;
	private static final int Y = 100;
	static final List<List<String>> UNIT_SHOTS = List.of(
		List.of("dikeguard", "reed_knight", "fowler", "punt_crossbow", "pikeman"),
		List.of("reeve", "wardman", "lantern_bearer", "bellman", "almoners_hand"),
		List.of("linkboy", "marsh_rider", "peat_cutter", "bog_warden", "mire_trapper"));

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("mirewick.screenshotDir", "screenshots"));
		try {
			Files.createDirectories(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		ctx.getInput().resizeWindow(1600, 900);
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
			units(ctx, sp, server);
			freeCompany(ctx, sp, server);
			magnates(ctx, sp, server);
			armor(ctx, sp, server);
			shield(ctx, sp, server);
			banner(ctx, sp, server);
			inventory(ctx, sp, server);
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private void units(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		int n = 0;
		for (List<String> row : UNIT_SHOTS) {
			clear(server);
			tp(server, 0.5, Y, 5.0, 180, 3);
			sp.getConnection().waitForChunksRender();
			final int base = row.size();
			server.runOnServer(s -> {
				ServerLevel l = s.overworld();
				for (int i = 0; i < base; i++) {
					double x = (i - (base - 1) / 2.0) * 1.7;
					KingdomSoldier k = KingdomApi.spawnSoldier(l, MirewickContent.id("mirewick_" + row.get(i)), MirewickContent.FACTION, new Vec3(x, Y, 0.5), null);
					if (k != null) still(k);
				}
			});
			ctx.waitTicks(20);
			shot(ctx, "units_" + (++n) + "_" + row.get(0));
		}
		// the two unique soldiers, 3/4 view, with the dressed Dunstan Reeve
		clear(server);
		tp(server, -3.0, Y, 3.0, 220, 5);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			KingdomSoldier a = KingdomApi.spawnSoldier(l, MirewickContent.id("mirewick_bog_warden"), MirewickContent.FACTION, new Vec3(-1.0, Y, 0.5), null);
			KingdomSoldier b = KingdomApi.spawnSoldier(l, MirewickContent.id("mirewick_mire_trapper"), MirewickContent.FACTION, new Vec3(0.6, Y, 0.5), null);
			KingdomSoldier d = MirewickLoot.spawnDunstan(l, new Vec3(2.2, Y, 0.5));
			for (KingdomSoldier k : new KingdomSoldier[] {a, b, d}) if (k != null) still(k);
		});
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) if (e instanceof KingdomSoldier k) System.out.println("[mirewick-shot] client soldier unit=" + k.unitId() + " skin=" + k.skin());
		});
		shot(ctx, "units_unique_and_dunstan");
		clear(server);
	}

	private void freeCompany(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y, 5.0, 180, 3);
		sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			String[] fc = {"fc_sellsword", "fc_brute", "fc_crossbow", "fc_captain", "fc_banner"};
			for (int i = 0; i < fc.length; i++) {
				KingdomSoldier k = KingdomApi.spawnSoldier(l, MirewickContent.id("mirewick_" + fc[i]), Identifier.fromNamespaceAndPath("elsewhere", "free_company"), new Vec3((i - 2) * 1.7, Y, 0.5), null);
				if (k != null) still(k);
			}
		});
		ctx.waitTicks(20);
		shot(ctx, "units_4_free_company");
		clear(server);
	}

	private void magnates(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		// the council in a row, then each Magnate close up
		clear(server);
		tp(server, 0.5, Y, 7.0, 180, 3);
		sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			int i = 0;
			for (Magnate m : Magnate.values()) {
				MagnateBoss b = MagnateBoss.spawn(l, m, new Vec3((i++ - 2) * 2.2, Y, 0.5));
				if (b != null) {
					b.setNoAi(true);
					b.setYRot(0F);
					b.setYHeadRot(0F);
					b.setYBodyRot(0F);
					if (m == Magnate.MARROW) b.onHolder(true);
				}
			}
		});
		ctx.waitTicks(30);
		shot(ctx, "magnates_council");
		for (Magnate m : Magnate.values()) {
			tp(server, (m.ordinal() - 2) * 2.2 + 1.2, Y, 3.2, 200, 6);
			ctx.waitTicks(10);
			shot(ctx, "magnate_" + m.key);
		}
		clear(server);
	}

	private void armor(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y + 0.6, 4.0, 180, 8);
		sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			Item[][] sets = {
				{null, null, MirewickItems.DEBT_COLLECTORS_LEGGINGS, null},
				{null, null, null, MirewickItems.FEN_MARSHALS_WADERS},
				{null, MirewickItems.DIKEWARDENS_BRIGANDINE, null, null},
				{MirewickItems.ALMONERS_COWL, null, null, null},
				{MirewickItems.LANTERN_CORONET, null, null, null},
			};
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (int i = 0; i < sets.length; i++) {
				ArmorStand a = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
				a.snapTo(-3.4 + i * 1.7, Y, 0.5, 0F, 0F);
				a.setYBodyRot(0F);
				a.setYHeadRot(0F);
				for (int k = 0; k < 4; k++) if (sets[i][k] != null) a.setItemSlot(slots[k], new ItemStack(sets[i][k]));
				l.addFreshEntity(a);
			}
		});
		ctx.waitTicks(10);
		shot(ctx, "armor_stands");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MirewickItems.LANTERN_CORONET));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MirewickItems.DIKEWARDENS_BRIGANDINE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MirewickItems.DEBT_COLLECTORS_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(MirewickItems.FEN_MARSHALS_WADERS));
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(MirewickItems.REEDCUTTER_PIKE));
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
		tp(server, 0.5, Y, 0.5, 200, 10);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(15);
		shot(ctx, "player_full_set_pike");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MirewickItems.ALMONERS_COWL));
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(MirewickItems.FOWLERS_MARSHBOW));
		});
		ctx.waitTicks(10);
		shot(ctx, "player_cowl_marshbow");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private void shield(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.IRON_SWORD));
			p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(MirewickItems.LANTERN_SHIELD));
		});
		tp(server, 0.5, Y, 0.5, 180, 0);
		ctx.waitTicks(5);
		shot(ctx, "lantern_shield_first_person_lowered");
		// raise it: use item in the offhand
		ctx.getInput().holdMouse(1);
		ctx.waitTicks(12);
		shot(ctx, "lantern_shield_first_person_raised");
		ctx.getInput().releaseMouse(1);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.getInput().holdMouse(1);
		ctx.waitTicks(12);
		shot(ctx, "lantern_shield_third_person_raised");
		ctx.getInput().releaseMouse(1);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		server.runOnServer(s -> player(s).setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY));
	}

	private void banner(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y, 6.0, 180, 4);
		server.runCommand("setblock -1 " + Y + " 0 minecraft:purple_banner[rotation=8]{patterns:[{pattern:\"elsewhere:mirewick\",color:\"red\"}]}");
		server.runCommand("setblock 1 " + Y + " 0 minecraft:red_banner[rotation=8]{patterns:[{pattern:\"elsewhere:mirewick\",color:\"purple\"}]}");
		server.runCommand("setblock -3 " + Y + " 0 minecraft:purple_banner[rotation=8]");
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			ArmorStand a = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
			a.snapTo(3.0, Y, 0.5, 0F, 0F);
			ItemStack[] pcs = {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)};
			var reg = s.registryAccess();
			var pattern = reg.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(ResourceKey.create(Registries.TRIM_PATTERN, MirewickContent.id("mirewick")));
			var material = reg.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace("emerald")));
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (int k = 0; k < 4; k++) {
				pcs[k].set(DataComponents.TRIM, new ArmorTrim(material, pattern));
				a.setItemSlot(slots[k], pcs[k]);
			}
			l.addFreshEntity(a);
		});
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(20);
		shot(ctx, "banner_and_trim");
	}

	private void inventory(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Inventory inv = p.getInventory();
			inv.clearContent();
			int slot = 9;
			for (Item it : MirewickItems.ALL) inv.setItem(slot++, new ItemStack(it));
			for (Item egg : MirewickUnits.EGGS) inv.setItem(slot++, new ItemStack(egg));
		});
		sp.getConnection().waitForClientboundPackets();
		ctx.waitTicks(5);
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.getInput().setCursorPos(2, 2);
		ctx.waitTicks(5);
		shot(ctx, "inventory");
		ctx.setScreen(() -> null);
	}

	private static void still(KingdomSoldier k) {
		k.setNoAi(true);
		k.setYRot(0F);
		k.setYHeadRot(0F);
		k.setYBodyRot(0F);
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(s -> {
			for (Entity m : s.overworld().getEntitiesOfClass(Mob.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) m.discard();
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("mirewick_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
