package dev.elsewhere.petalwind_glasswaste.gametest;

import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.tempestra.Tempestra;
import dev.elsewhere.petalwind.PetalwindDimension;
import dev.elsewhere.petalwind_glasswaste.Bosses;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Art-only screenshot run for Kazehana and Tempestra: idle, attacks, phase roar, death. */
public class BossArtPetalwindTest implements FabricClientGameTest {
	private Path dir;
	private ClientGameTestContext ctx;
	private TestSingleplayerContext world;
	private BossBase boss;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.ctx = context;
		this.dir = Path.of(System.getProperty("petalwind_glasswaste.screenshotDir", "screenshots")).resolve("bossart");
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			this.world = sp;
			sp.getConnection().waitForChunksRender();
			context.getInput().pressKey(o -> o.keyToggleGui);
			kazehana();
			tempestra();
		}
	}

	private void shot(String name) {
		ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(dir).disableCounterPrefix());
	}

	private <T> T server(java.util.function.Function<MinecraftServer, T> fn) {
		return world.getServer().computeOnServer(fn::apply);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void look(ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Math.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0F;
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI);
		p.teleportTo(level, eye.x, eye.y - p.getEyeHeight(), eye.z, java.util.Set.of(), yaw, pitch, true);
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
	}

	private BossBase summon(String id, double camDist, double camUp, double targetUp) {
		return server(s -> {
			ServerLevel level = s.getLevel(PetalwindDimension.LEVEL);
			BlockPos at = new BlockPos(3000 + Bosses.IDS.indexOf(id) * 300, 170, 3000);
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			BossBase b = Bosses.summon(level, id, at);
			Vec3 target = b.position().add(0, targetUp, 0);
			look(player(s), level, target.add(0.5, camUp, -camDist), target);
			return b;
		});
	}

	private void event(byte ev) {
		server(s -> {
			((ServerLevel) boss.level()).broadcastEntityEvent(boss, ev);
			return null;
		});
	}

	private void kazehana() {
		boss = summon("kazehana", 7.0, 0.3, 2.0);
		world.getConnection().waitForChunksRender();
		ctx.waitTicks(40);
		shot("kazehana_bow");
		server(s -> {
			Kazehana k = (Kazehana) boss;
			ServerLevel level = (ServerLevel) k.level();
			k.beginDuel(level, player(s));
			k.testDrawCut(level, player(s));
			return null;
		});
		ctx.waitTicks(12);
		shot("kazehana_iaido");
		ctx.waitTicks(9);
		shot("kazehana_dash");
		ctx.waitTicks(30);
		shot("kazehana_stance");
		event(Kazehana.EV_COMBO);
		ctx.waitTicks(3);
		shot("kazehana_combo");
		ctx.waitTicks(10);
		event(Kazehana.EV_PARRY);
		ctx.waitTicks(4);
		shot("kazehana_parry");
		ctx.waitTicks(20);
		event(Kazehana.EV_PHASE);
		ctx.waitTicks(10);
		shot("kazehana_phase");
		ctx.waitTicks(40);
		event(Kazehana.EV_STUN);
		ctx.waitTicks(15);
		shot("kazehana_stun");
		event(Kazehana.EV_GALE);
		ctx.waitTicks(6);
		shot("kazehana_gale");
		ctx.waitTicks(20);
		server(s -> {
			boss.die(boss.level().damageSources().genericKill());
			return null;
		});
		ctx.waitTicks(12);
		shot("kazehana_death");
	}

	@SuppressWarnings("unchecked")
	private void tempestra() {
		boss = summon("tempestra", 16.0, -1.0, 5.0);
		world.getConnection().waitForChunksRender();
		ctx.waitTicks(40);
		shot("tempestra_idle");
		event(Tempestra.EV_VOLLEY);
		ctx.waitTicks(7);
		shot("tempestra_volley");
		ctx.waitTicks(30);
		event(Tempestra.EV_DOWNBURST);
		ctx.waitTicks(24);
		shot("tempestra_downburst_windup");
		ctx.waitTicks(7);
		shot("tempestra_downburst_slam");
		ctx.waitTicks(30);
		server(s -> {
			try {
				var f = Tempestra.class.getDeclaredField("DATA_SHIELD");
				f.setAccessible(true);
				boss.getEntityData().set((EntityDataAccessor<Boolean>) f.get(null), false);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			return null;
		});
		ctx.waitTicks(50);
		shot("tempestra_rings_open");
		event(Tempestra.EV_PHASE);
		ctx.waitTicks(8);
		shot("tempestra_phase");
		ctx.waitTicks(40);
		event(Tempestra.EV_STRIKE);
		ctx.waitTicks(20);
		shot("tempestra_stormrim");
		ctx.waitTicks(30);
		server(s -> {
			boss.die(boss.level().damageSources().genericKill());
			return null;
		});
		ctx.waitTicks(12);
		shot("tempestra_death");
	}
}
