package dev.elsewhere.summoner.gametest;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.SummonerAttributes;
import dev.elsewhere.summoner.SummonerEntities;
import dev.elsewhere.summoner.SummonerItems;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Client screenshots: each minion fighting, close-ups of the three models, the armor on stands, all items. */
public class SummonerScreenshots implements FabricClientGameTest {
	private Path out;
	private static final int Y = 100;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		out = Path.of(System.getProperty("summoner.screenshotDir", "screenshots"));
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
			server.runCommand("gamemode creative @a");
			server.runCommand("forceload add -48 -48 48 48");
			server.runCommand("fill -40 " + (Y - 1) + " -40 40 " + (Y - 1) + " 40 minecraft:grass_block");
			server.runCommand("fill -40 " + Y + " -40 40 " + (Y + 8) + " 40 minecraft:air");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAttribute(SummonerAttributes.slots()).setBaseValue(4);
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
			});

			fight(ctx, sp, server, SummonerEntities.ROOTLING, "rootling_fighting");
			fight(ctx, sp, server, SummonerEntities.PAPER_KITE, "paper_kite_fighting");
			fight(ctx, sp, server, SummonerEntities.BRASSBACK, "brassback_fighting");
			herd(ctx, sp, server);
			closeUp(ctx, sp, server, SummonerEntities.ROOTLING, "rootling_closeup", 0.0);
			closeUp(ctx, sp, server, SummonerEntities.PAPER_KITE, "paper_kite_closeup", 0.8);
			closeUp(ctx, sp, server, SummonerEntities.BRASSBACK, "brassback_closeup", 0.0);
			armor(ctx, sp, server);
			inventory(ctx, sp, server);
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			MinionManager.dismissAll(p.getUUID());
			for (Mob m : s.overworld().getEntitiesOfClass(Mob.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60), e -> !(e instanceof MinionEntity))) m.discard();
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
	}

	private static Mob mob(ServerLevel l, EntityType<? extends Mob> t, double x, double z, float yaw) {
		Mob m = t.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(x, Y, z, yaw, 0);
		m.setYHeadRot(yaw);
		m.setNoAi(true);
		m.setPersistenceRequired();
		l.addFreshEntity(m);
		return m;
	}

	/** Owner at the origin looking north; a husk 5 blocks ahead that the owner "hit"; one minion attacking it. */
	private void fight(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, EntityType<? extends MinionEntity> type, String name) {
		clear(server);
		boolean kite = type == SummonerEntities.PAPER_KITE;
		// the kite hovers above its owner's head: show it from behind the owner
		ctx.runOnClient(mc -> mc.options.setCameraType(kite ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
		tp(server, 0.5, Y, 4.5, 180, kite ? 4 : 12);
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Mob husk = mob(s.overworld(), EntityTypes.HUSK, 0.5, -1.5, 0);
			Mob husk2 = mob(s.overworld(), EntityTypes.HUSK, -2.0, -2.5, 20);
			MinionEntity m = MinionManager.summon(p, type, 0);
			if (m != null) m.teleportTo(0.5, type == SummonerEntities.PAPER_KITE ? Y + 2.5 : Y, 1.5);
			p.setLastHurtMob(husk);
		});
		ctx.waitTicks(type == SummonerEntities.PAPER_KITE ? 33 : 26);
		shot(ctx, name);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** A full herd (4 slots) working over a small crowd. */
	private void herd(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y + 1.5, 7.5, 180, 22);
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Mob a = mob(s.overworld(), EntityTypes.HUSK, 0.5, -1.0, 0);
			mob(s.overworld(), EntityTypes.ZOMBIE, -2.5, -2.0, 30);
			mob(s.overworld(), EntityTypes.HUSK, 3.0, -2.5, -30);
			p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false));
			// 4 slots for the herd shot (as with Herdcaller cap + vest + Bell of the Flock)
			var slots = dev.elsewhere.stub.summoner.SummonerAttributes.slots();
			var inst = slots == null ? null : p.getAttribute(slots);
			net.minecraft.resources.Identifier mid = net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "gametest.herd_slots");
			if (inst != null && !inst.hasModifier(mid)) inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(mid, 3, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
			MinionManager.summon(p, SummonerEntities.ROOTLING, 0);
			MinionManager.summon(p, SummonerEntities.ROOTLING, 0);
			MinionManager.summon(p, SummonerEntities.PAPER_KITE, 0);
			MinionManager.summon(p, SummonerEntities.BRASSBACK, 0);
			for (MinionEntity m : MinionManager.minions(p)) m.teleportTo(m.getX(), m.getY(), 2.0);
			p.setLastHurtMob(a);
		});
		ctx.waitTicks(30);
		shot(ctx, "herd_fighting");
	}

	/** One minion frozen (no AI) 2.5 blocks in front of the camera, facing it. */
	private void closeUp(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, EntityType<? extends MinionEntity> type, String name, double lift) {
		clear(server);
		tp(server, 0.5, Y, 3.0, 180, 25);
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(5);
		server.runOnServer(s -> {
			MinionEntity m = MinionManager.summon(player(s), type, 0);
			if (m != null) {
				m.setNoAi(true);
				m.snapTo(0.5, Y + lift, 0.8, 20F, 0F);
				m.setYBodyRot(20F);
				m.setYHeadRot(20F);
			}
		});
		ctx.waitTicks(15);
		shot(ctx, name);
	}

	private void armor(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		tp(server, 0.5, Y + 0.6, 4.0, 180, 8);
		sp.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerLevel l = s.overworld();
			Item[][] sets = {
				{SummonerItems.HERDCALLER_CAP, SummonerItems.HERDCALLER_VEST, SummonerItems.HERDCALLER_LEGGINGS, SummonerItems.HERDCALLER_BOOTS},
				{SummonerItems.LINED_HERDCALLER_CAP, SummonerItems.LINED_HERDCALLER_VEST, SummonerItems.LINED_HERDCALLER_LEGGINGS, SummonerItems.LINED_HERDCALLER_BOOTS},
				{SummonerItems.ELDER_BARK_CROWN, SummonerItems.ENGINEERS_HARNESS, SummonerItems.HERDCALLER_LEGGINGS, SummonerItems.HERDCALLER_BOOTS},
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
		// the player wearing the set, third person
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(SummonerItems.HERDCALLER_CAP));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SummonerItems.HERDCALLER_VEST));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SummonerItems.HERDCALLER_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(SummonerItems.HERDCALLER_BOOTS));
			p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(SummonerItems.ROOTCALL_STAFF));
			for (ArmorStand a : s.overworld().getEntitiesOfClass(ArmorStand.class, new AABB(-60, Y - 5, -60, 60, Y + 30, 60))) a.discard();
		});
		tp(server, 0.5, Y, 0.5, 200, 10);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(15);
		shot(ctx, "player_herdcaller");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private void inventory(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
		clear(server);
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Inventory inv = p.getInventory();
			inv.clearContent();
			for (EquipmentSlot e : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) p.setItemSlot(e, ItemStack.EMPTY);
			int slot = 9;
			for (Item it : SummonerItems.ALL) inv.setItem(slot++, new ItemStack(it, it == SummonerItems.TRAIL_BISCUIT ? 16 : it == SummonerItems.GORGER_FLUFF ? 12 : 1));
			inv.setItem(0, new ItemStack(SummonerItems.KITESTRING_STAFF));
			inv.setItem(1, new ItemStack(SummonerItems.BELL_OF_THE_FLOCK));
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(SummonerItems.HERDCALLER_CAP));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SummonerItems.HERDCALLER_VEST));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SummonerItems.HERDCALLER_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(SummonerItems.HERDCALLER_BOOTS));
		});
		sp.getConnection().waitForClientboundPackets();
		ctx.waitTicks(5);
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.getInput().setCursorPos(2, 2); // keep the cursor off the slots so no tooltip hides the items
		ctx.waitTicks(5);
		shot(ctx, "inventory");
		ctx.setScreen(() -> null);
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of("summoner_" + name).withDestinationDir(out).disableCounterPrefix());
	}
}
