package dev.elsewhere.rivals.hrafnholt.gametest;

import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import dev.elsewhere.rivals.hrafnholt.HrafnholtItems;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.AABB;

/** Client screenshots of every Hrafnholt unit, boss and item (look at them: skins, armor layering, icons). */
public class HrafnholtScreenshots implements FabricClientGameTest {
	private Path out;
	private static final int Y = 100;
	static final List<List<String>> UNIT_SHOTS = List.of(
		List.of("huskarl", "shield_thane", "hunter", "bolt_thrall", "spear_thane"),
		List.of("hird_sergeant", "hird_captain", "raven_bearer", "skald", "leech"),
		List.of("shield_lad", "berserker", "wolf_handler"),
		List.of("wolf_rider", "wolf_rider_axe"));

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("hrafnholt.screenshotDir", "screenshots"));
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
			if (Boolean.getBoolean("hrafnholt.castleonly")) {
				castle(ctx, sp, server);
				return;
			}
			if (!Boolean.getBoolean("hrafnholt.bossesonly")) units(ctx, sp, server);
			if (!Boolean.getBoolean("hrafnholt.bossesonly")) {
				armor(ctx, sp, server);
				banner(ctx, sp, server);
				inventory(ctx, sp, server);
			}
			bosses(ctx, sp, server);
		}
	}


	/** The six bosses and five Risen champions: group shots, then one close-up each (and Eydis with her wolves). Bosses are left on their AI; a creative camera player is ignored by them. */
	private void bosses(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		server.runCommand("gamemode creative @a");
		server.runCommand("effect clear @a");
		java.util.List<java.util.List<String>> rows = List.of(List.of("jarl", "hersir", "hallwarden"), List.of("skald", "chief", "huntmaster"), List.of("risen_hersir", "risen_hallwarden", "risen_skald"), List.of("risen_chief", "risen_huntmaster"));
		int n = 0;
		for (List<String> row : rows) {
			clear(server);
			tp(server, 0.5, Y, 7.0, 180, 2);
			sp.getConnection().waitForChunksRender();
			spawnBosses(server, row, 2.6, 0.5);
			ctx.waitTicks(30);
			shot(ctx, "boss_group_" + (++n) + "_" + row.get(0));
		}
		for (String k : List.of("jarl", "hersir", "hallwarden", "skald", "chief", "huntmaster", "risen_hersir", "risen_hallwarden", "risen_skald", "risen_chief", "risen_huntmaster")) {
			clear(server);
			tp(server, 0.5, Y, 4.2, 180, 2);
			spawnBosses(server, List.of(k), 0, 0.5);
			if (k.equals("huntmaster")) server.runOnServer(s -> {
				for (var b : s.overworld().getEntitiesOfClass(dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss.class, new AABB(-20, Y - 2, -20, 20, Y + 10, 20))) b.spawnWolves(s.overworld(), 2);
			});
			ctx.waitTicks(25);
			shot(ctx, "boss_" + k);
		}
		clear(server);
		server.runOnServer(s -> {
			for (var b : s.overworld().getEntitiesOfClass(dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) b.discard();
			for (var w : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.wolf.Wolf.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) w.discard();
		});
	}

	/** The castle in Hrafnholt's own style (data/elsewhere/kingdom_castle/hrafnholt.json), built on a grass platform: front, corner and gate views. */
	private void castle(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		Identifier fid = Identifier.fromNamespaceAndPath("elsewhere", "hrafnholt");
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			net.minecraft.core.BlockPos o = new net.minecraft.core.BlockPos(0, Y, 0);
			var box = dev.elsewhere.kingdom_court.world.CastleLayout.box(dev.elsewhere.kingdom_court.world.CastleLayout.Part.CASTLE, o);
			for (int cx = (box.minX() - 32) >> 4; cx <= (box.maxX() + 32) >> 4; cx++) for (int cz = (box.minZ() - 32) >> 4; cz <= (box.maxZ() + 96) >> 4; cz++) {
				l.setChunkForced(cx, cz, true);
				l.getChunk(cx, cz);
			}
			for (int x = -70; x <= 70; x++) for (int z = -70; z <= 110; z++) {
				l.setBlock(new net.minecraft.core.BlockPos(x, Y - 1, z), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				for (int y = Y; y <= Y + 30; y++) l.setBlock(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			}
			var style = dev.elsewhere.kingdom_court.world.CastleStyles.of(fid);
			if (style == null) throw new IllegalStateException("no castle style for " + fid);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				var cb = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx << 4, l.getMinY(), cz << 4, (cx << 4) + 15, l.getMaxY(), (cz << 4) + 15);
				var pl = new dev.elsewhere.kingdom_court.world.CastleLayout.Placer(l, cb);
				pl.style = style;
				dev.elsewhere.kingdom_court.world.CastleLayout.build(dev.elsewhere.kingdom_court.world.CastleLayout.Part.CASTLE, o, pl);
			}
		});
		ctx.waitTicks(20);
		server.runCommand("gamemode creative @a");
		castleView(ctx, sp, server, 0.5, 80.5, 180, 14, "castle_front", Y + 18);
		castleView(ctx, sp, server, 62.5, 62.5, 135, 22, "castle_corner", Y + 26);
		castleView(ctx, sp, server, 0.5, 36.5, 180, 6, "castle_gate", Y + 4);
	}

	private void castleView(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, double px, double pz, float yaw, float pitch, String name, double py) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", px, py, pz, yaw, pitch));
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(15);
		shot(ctx, name);
	}

	private static void spawnBosses(TestServerContext server, List<String> keys, double spacing, double z) {
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (var old : l.getEntitiesOfClass(dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) old.discard();
			for (int i = 0; i < keys.size(); i++) {
				String k = keys.get(i);
				boolean risen = k.startsWith("risen_");
				var h = dev.elsewhere.rivals.hrafnholt.boss.Hrafn.byKey(risen ? k.substring(6) : k);
				var b = (risen ? h.risenType : h.type).create(l, EntitySpawnReason.COMMAND);
				double x = (i - (keys.size() - 1) / 2.0) * spacing;
				b.snapTo(x, Y, z, 0F, 0F);
				b.setHome(net.minecraft.core.BlockPos.containing(x, Y, z));
				b.setYHeadRot(0F);
				l.addFreshEntity(b);
			}
		});
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
					KingdomSoldier k = KingdomApi.spawnSoldier(l, HrafnholtContent.id("hrafnholt_" + row.get(i)), HrafnholtContent.FACTION, new net.minecraft.world.phys.Vec3(x, Y, 0.5), null);
					if (k != null) {
						k.setNoAi(true);
						k.setYRot(0F);
						k.setYHeadRot(0F);
						k.setYBodyRot(0F);
					}
				}
			});
			ctx.waitTicks(20);
			shot(ctx, "units_" + (++n) + "_" + row.get(0));
		}
		// 3/4 view of the Berserker and Wolf-Handler from the front-left
		clear(server);
		tp(server, -3.0, Y, 3.0, 220, 5);
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			for (int i = 0; i < 2; i++) {
				KingdomSoldier k = KingdomApi.spawnSoldier(l, HrafnholtContent.id(i == 0 ? "hrafnholt_berserker" : "hrafnholt_wolf_handler"), HrafnholtContent.FACTION,
					new net.minecraft.world.phys.Vec3(-1.0 + i * 1.6, Y, 0.5), null);
				if (k != null) { k.setNoAi(true); }
			}
		});
		server.runOnServer(s -> {
			KingdomSoldier k = KingdomApi.spawnSoldier(s.overworld(), Identifier.fromNamespaceAndPath("elsewhere", "man_at_arms"), Identifier.fromNamespaceAndPath("elsewhere", "aldermark"),
				new net.minecraft.world.phys.Vec3(2.0, Y, 0.5), null);
			if (k != null) k.setNoAi(true);
			KingdomSoldier k2 = KingdomApi.spawnSoldier(s.overworld(), Identifier.fromNamespaceAndPath("elsewhere", "archer"), Identifier.fromNamespaceAndPath("elsewhere", "aldermark"),
				new net.minecraft.world.phys.Vec3(3.6, Y, 0.5), null);
			if (k2 != null) k2.setNoAi(true);
		});
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) if (e instanceof KingdomSoldier k) System.out.println("[hrafnholt-shot] client soldier " + k.getType() + " unit=" + k.unitId() + " skin=" + k.skin());
		});
		shot(ctx, "units_unique_close");
		clear(server);
	}

	private void armor(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y + 0.6, 4.0, 180, 8);
		sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			Item[][] sets = {
				{HrafnholtItems.IRON_CROWN, HrafnholtItems.HEARTH_WARD_MAIL, null, HrafnholtItems.WOLFSKIN_BOOTS},
				{null, HrafnholtItems.ULFHEDNAR_PELT, null, null},
				{HrafnholtItems.IRON_CROWN, HrafnholtItems.ULFHEDNAR_PELT, null, HrafnholtItems.WOLFSKIN_BOOTS},
			};
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (int i = 0; i < 3; i++) {
				ArmorStand a = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
				a.snapTo(-1.6 + i * 1.6, Y, 0.5, 0F, 0F);
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
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HrafnholtItems.IRON_CROWN));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HrafnholtItems.HEARTH_WARD_MAIL));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(HrafnholtItems.WOLFSKIN_BOOTS));
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(HrafnholtItems.RUNE_AXE));
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
		tp(server, 0.5, Y, 0.5, 200, 10);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(15);
		shot(ctx, "player_crown_mail_boots_axe");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HrafnholtItems.ULFHEDNAR_PELT));
			p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(HrafnholtItems.HORNBOW));
		});
		ctx.waitTicks(10);
		shot(ctx, "player_pelt_hornbow");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private void banner(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		});
		tp(server, 0.5, Y, 6.0, 180, 4);
		// banners carrying the custom raven pattern, plus a plain one with trimmed iron armor on a stand
		server.runCommand("setblock -1 " + Y + " 0 minecraft:green_banner[rotation=8]{patterns:[{pattern:\"elsewhere:hrafnholt\",color:\"brown\"}]}");
		server.runCommand("setblock 1 " + Y + " 0 minecraft:brown_banner[rotation=8]{patterns:[{pattern:\"elsewhere:hrafnholt\",color:\"green\"}]}");
		server.runCommand("setblock -3 " + Y + " 0 minecraft:green_banner[rotation=8]");
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			ArmorStand a = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
			a.snapTo(3.0, Y, 0.5, 0F, 0F);
			ItemStack[] pcs = {new ItemStack(net.minecraft.world.item.Items.IRON_HELMET), new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE),
				new ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS), new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS)};
			var reg = s.registryAccess();
			var pattern = reg.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_PATTERN, HrafnholtContent.id("hrafnholt")));
			var material = reg.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace("gold")));
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (int k = 0; k < 4; k++) {
				pcs[k].set(net.minecraft.core.component.DataComponents.TRIM, new net.minecraft.world.item.equipment.trim.ArmorTrim(material, pattern));
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
			for (Item it : HrafnholtItems.ALL) inv.setItem(slot++, new ItemStack(it));
			for (Mob unused : List.<Mob>of()) { }
			for (String egg : new String[] {"hrafnholt_berserker_spawn_egg", "hrafnholt_wolf_handler_spawn_egg"}) {
				Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(HrafnholtContent.id(egg));
				inv.setItem(slot++, new ItemStack(it));
			}
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
			for (Entity m : s.overworld().getEntitiesOfClass(Mob.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) if (!(m instanceof dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss) || true) m.discard();
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("hrafnholt_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
