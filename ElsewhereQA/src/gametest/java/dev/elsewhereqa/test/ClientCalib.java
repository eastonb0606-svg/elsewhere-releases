package dev.elsewhereqa.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * s-clientload (3.0.0): short calibration runs of a real client on this MacBook (Apple M1, 8 GB), run with {@code -PqaClient=calib}.
 * Run 1: load A (idle, normal terrain, render distance 12) and load B (N armored kingdom soldiers in view: 16, 32, 64, 128, standing, all four
 * kingdoms' looks). Run 2: a live 32 v 32 battle, particle loads, hovering arrows and banners, so each cost driver gets its own slope.
 * Every frame is timed on the render thread (a HUD element stamps the frame); client tick time, the game's own FPS counter, live particles,
 * heap and the framebuffer size are recorded too. Output: calib.tsv and calib.json in the shot directory. The window closes itself at the end.
 */
public class ClientCalib implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("elsewhereqa-calib");
	private static volatile boolean rec;
	private static final long[] FRAMES = new long[400_000];
	private static int nFrames;
	private static long lastFrame, tickStart, tickSum;
	private static int ticks;
	private static boolean hooked;
	private static volatile int emit;
	private static volatile Vec3 emitAt = Vec3.ZERO;
	private final List<String> rows = new ArrayList<>();
	private final StringBuilder json = new StringBuilder("[\n");
	private Path out;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!"calib".equals(System.getProperty("elsewhereqa.client"))) {
			return;
		}
		String shots = System.getProperty("elsewhereqa.shots");
		out = shots != null ? Path.of(shots) : Path.of("calib");
		try {
			Files.createDirectories(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		hook();
		ctx.getInput().resizeWindow(1280, 800);
		ctx.runOnClient(mc -> {
			mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			mc.options.renderDistance().set(12);
			mc.options.simulationDistance().set(10);
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			// no input reaches a gametest client: with the default AFK limit the game caps itself at 30 FPS after 60 s (FramerateLimitTracker)
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			// a level spot: the player stands on the ground looking north over open terrain
			String where = server.computeOnServer(s -> {
				ServerLevel l = s.overworld();
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				int x = p.getBlockX(), z = p.getBlockZ();
				int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				p.teleportTo(l, x + 0.5, y, z + 0.5, java.util.Set.of(), 180.0F, 5.0F, false);
				p.getAbilities().flying = false;
				return x + " " + y + " " + z;
			});
			ctx.waitTicks(40);
			// the MacBook is shared with other lanes: give chunk meshing up to 2400 lockstep ticks instead of the default 200, then go on anyway
			String chunks;
			try {
				chunks = "rendered after " + sp.getConnection().waitForChunksRender(true, 1200) + " ticks";
			} catch (AssertionError e) {
				chunks = "not all rendered after 1200 ticks";
			}
			ctx.waitTicks(100);
			info(ctx, "setup", "player at " + where + "; chunks " + chunks);
			shot(ctx, "calib_a_idle");

			// ---------------- run 1: A idle, B soldiers
			measure(ctx, "run1", "A idle", 30, 0);
			int[] steps = {16, 32, 64, 128};
			int have = 0;
			for (int n : steps) {
				final int add = n - have;
				final int start = have;
				String made = server.computeOnServer(s -> spawnSoldiers(s, start, add));
				have = n;
				LOG.info("[calib] {}", made);
				ctx.waitTicks(60);
				if (n == 64) {
					shot(ctx, "calib_b_64_soldiers");
				}
				measure(ctx, "run1", "B " + n + " soldiers", 25, n);
			}
			clear(server);
			ctx.waitTicks(40);

			// ---------------- run 2: drift check, live battle, particles, arrows, banners
			measure(ctx, "run2", "A2 idle", 20, 0);
			int r = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(28));
				var src = s.createCommandSourceStack().withLevel(s.overworld()).withPosition(at).withSuppressedOutput()
					.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER);
				return s.getCommands().getDispatcher().execute("kingdom armies battle hrafnholt aldermark 32", src);
			});
			LOG.info("[calib] battle start {}", r);
			ctx.waitTicks(100);
			shot(ctx, "calib_battle_32v32");
			measure(ctx, "run2", "battle 32v32 live", 40, -1);
			server.runCommand("kingdom armies battle stop");
			clear(server);
			ctx.waitTicks(60);
			emitAt = ctx.computeOnClient(mc -> mc.player.position().add(mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(14)).add(0, 1.5, 0));
			for (int per : new int[] {20, 80}) {
				emit = per;
				ctx.waitTicks(60);
				measure(ctx, "run2", "particles " + per + "/tick", 20, 0);
			}
			emit = 0;
			ctx.waitTicks(60);
			String arrows = server.computeOnServer(ClientCalib::arrows);
			LOG.info("[calib] {}", arrows);
			ctx.waitTicks(40);
			measure(ctx, "run2", "60 arrows in view", 15, 0);
			clear(server);
			String banners = server.computeOnServer(ClientCalib::banners);
			LOG.info("[calib] {}", banners);
			ctx.waitTicks(80);
			shot(ctx, "calib_banners");
			measure(ctx, "run2", "200 banners in view", 15, 0);
		} catch (Throwable t) {
			LOG.error("[calib] run failed", t);
			rows.add("ERROR\t" + t);
		} finally {
			try {
				Files.writeString(out.resolve("calib.tsv"), String.join("\n", rows) + "\n");
				Files.writeString(out.resolve("calib.json"), json.append("{}\n]\n").toString());
			} catch (Exception e) {
				LOG.error("[calib] write", e);
			}
		}
	}

	private static void hook() {
		if (hooked) {
			return;
		}
		hooked = true;
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("elsewhereqa", "calib_frame"), (g, dt) -> {
			long now = System.nanoTime();
			if (rec && lastFrame != 0 && nFrames < FRAMES.length) {
				FRAMES[nFrames++] = now - lastFrame;
			}
			lastFrame = now;
		});
		ClientTickEvents.START_CLIENT_TICK.register(mc -> tickStart = System.nanoTime());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (rec) {
				tickSum += System.nanoTime() - tickStart;
				ticks++;
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			int n = emit;
			if (n > 0) {
				Vec3 a = emitAt;
				s.overworld().sendParticles(ParticleTypes.FLAME, a.x, a.y, a.z, n, 4.0, 1.5, 4.0, 0.01);
			}
		});
	}

	private void info(final ClientGameTestContext ctx, final String k, final String v) {
		String sys = ctx.computeOnClient(mc -> "framebuffer " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + ", window "
			+ mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + ", gui scale " + mc.getWindow().getGuiScale() + ", render distance "
			+ mc.options.renderDistance().get() + ", graphics " + mc.options.graphicsPreset().get() + ", vsync " + mc.options.enableVsync().get()
			+ ", fps limit " + mc.options.framerateLimit().get() + ", GPU " + gpu());
		rows.add("INFO\t" + k + "\t" + v + "; " + sys);
		LOG.info("[calib] {} {}; {}", k, v, sys);
	}

	private static String gpu() {
		try {
			var d = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo();
			return d.name() + " / " + d.vendorName() + " / " + d.backendName();
		} catch (Throwable t) {
			return "?";
		}
	}

	private void measure(final ClientGameTestContext ctx, final String run, final String label, final int seconds, final int soldiersExpected) {
		ctx.runOnClient(mc -> {
			nFrames = 0;
			tickSum = 0;
			ticks = 0;
			lastFrame = 0;
			rec = true;
		});
		long w0 = System.nanoTime();
		List<Integer> mcFps = new ArrayList<>(), parts = new ArrayList<>(), soldiers = new ArrayList<>(), ents = new ArrayList<>();
		for (int s = 0; s < seconds; s++) {
			ctx.waitTicks(20);
			int[] v = ctx.computeOnClient(mc -> new int[] {mc.getFps(), particles(mc), soldiers(mc), mc.level.getEntityCount()});
			mcFps.add(v[0]);
			parts.add(v[1]);
			soldiers.add(v[2]);
			ents.add(v[3]);
		}
		double wall = (System.nanoTime() - w0) / 1e9;
		long[] fr = ctx.computeOnClient(mc -> {
			rec = false;
			return Arrays.copyOf(FRAMES, nFrames);
		});
		long tickNs = ctx.computeOnClient(mc -> ticks == 0 ? 0L : tickSum / ticks);
		int tk = ctx.computeOnClient(mc -> ticks);
		Runtime rt = Runtime.getRuntime();
		long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20, totalMb = rt.totalMemory() >> 20, maxMb = rt.maxMemory() >> 20;
		double[] ms = new double[fr.length];
		double sum = 0;
		for (int i = 0; i < fr.length; i++) {
			ms[i] = fr[i] / 1e6;
			sum += ms[i];
		}
		double[] sorted = ms.clone();
		Arrays.sort(sorted);
		int n = sorted.length;
		double mean = n == 0 ? 0 : sum / n;
		double p50 = n == 0 ? 0 : sorted[n / 2], p95 = n == 0 ? 0 : sorted[(int) (n * 0.95)], p99 = n == 0 ? 0 : sorted[(int) (n * 0.99)];
		int worst = Math.max(1, n / 100);
		double worstSum = 0;
		for (int i = n - worst; i < n; i++) {
			worstSum += sorted[Math.max(0, i)];
		}
		double low1 = worstSum > 0 ? 1000.0 / (worstSum / worst) : 0;
		double fps = sum > 0 ? n * 1000.0 / sum : 0;
		double avg = (double) soldiers.stream().mapToInt(Integer::intValue).sum() / Math.max(1, soldiers.size());
		String row = String.format(Locale.ROOT, "%s\t%s\t%d\t%.1f\t%.1f\t%.2f\t%.2f\t%.2f\t%.2f\t%.3f\t%d\t%.1f\t%.0f\t%.0f\t%d\t%d\t%d\t%.1f\t%.2f",
			run, label, n, fps, low1, mean, p50, p95, p99, tickNs / 1e6, tk, avg, avgOf(parts), avgOf(ents), usedMb, totalMb, maxMb, avgOf(mcFps), wall);
		rows.add(row);
		json.append(String.format(Locale.ROOT, "{\"run\":\"%s\",\"label\":\"%s\",\"frames\":%d,\"fps\":%.2f,\"low1\":%.2f,\"meanMs\":%.3f,\"p50Ms\":%.3f,\"p95Ms\":%.3f,"
			+ "\"p99Ms\":%.3f,\"clientTickMs\":%.4f,\"ticks\":%d,\"soldiersInWorld\":%.1f,\"liveParticles\":%.0f,\"clientEntities\":%.0f,\"heapUsedMb\":%d,"
			+ "\"heapTotalMb\":%d,\"heapMaxMb\":%d,\"mcFps\":%.1f,\"wallS\":%.2f,\"expectSoldiers\":%d},\n", run, label, n, fps, low1, mean, p50, p95, p99, tickNs / 1e6, tk,
			avg, avgOf(parts), avgOf(ents), usedMb, totalMb, maxMb, avgOf(mcFps), wall, soldiersExpected));
		LOG.info("[calib] {}", row);
	}

	private static double avgOf(final List<Integer> l) {
		return l.isEmpty() ? 0 : l.stream().mapToInt(Integer::intValue).average().orElse(0);
	}

	private static int particles(final Minecraft mc) {
		try {
			String s = mc.particleEngine.countParticles().replaceAll("[^0-9].*$", "");
			return s.isEmpty() ? 0 : Integer.parseInt(s);
		} catch (Throwable t) {
			return -1;
		}
	}

	private static int soldiers(final Minecraft mc) {
		int n = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.getClass().getName().startsWith("dev.elsewhere.kingdom.") && e.getClass().getSimpleName().contains("Soldier")) {
				n++;
			}
		}
		return n;
	}

	private void shot(final ClientGameTestContext ctx, final String name) {
		ctx.waitTick();
		ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(out).disableCounterPrefix());
	}

	private static void clear(final TestServerContext server) {
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getAllEntities()) {
				if (e != null && e.entityTags().contains("qacalib")) {
					e.discard();
				}
			}
			for (Entity e : s.overworld().getAllEntities()) {
				if (e != null && !(e instanceof ServerPlayer) && e.getClass().getName().startsWith("dev.elsewhere.kingdom")) {
					e.discard();
				}
			}
		});
	}

	/** N standing soldiers 12-34 blocks in front, every kingdom's look, real unit kit (armor, tabards, trims, shields, banners). */
	private static String spawnSoldiers(final MinecraftServer s, final int from, final int count) {
		ServerLevel l = s.overworld();
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		String[] factions = {"aldermark", "hrafnholt", "azmar", "mirewick"};
		String[] units = {"MAN_AT_ARMS", "KNIGHT", "ARCHER", "CROSSBOWMAN", "SERGEANT", "PIKEMAN", "CAPTAIN", "STANDARD_BEARER"};
		Vec3 fwd = p.getLookAngle().multiply(1, 0, 1).normalize(), side = new Vec3(-fwd.z, 0, fwd.x);
		int ok = 0, armored = 0, cloned = 0;
		List<Mob> made = new ArrayList<>();
		for (Entity en : l.getAllEntities()) {
			if (en instanceof Mob mb && mb.entityTags().contains("qacalib")) {
				made.add(mb);
			}
		}
		String err = "";
		try {
			Class<?> api = Class.forName("dev.elsewhere.kingdom.KingdomApi"), ut = Class.forName("dev.elsewhere.kingdom.UnitTypes");
			var m = api.getMethod("spawnSoldier", ServerLevel.class, Identifier.class, Identifier.class, Vec3.class, Class.forName("dev.elsewhere.kingdom.Squad"));
			for (int i = from; i < from + count; i++) {
				int row = i / 16, col = i % 16;
				Vec3 pos = p.position().add(fwd.scale(12 + row * 3)).add(side.scale((col - 7.5) * 1.6));
				int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
				pos = new Vec3(pos.x, y, pos.z);
				Identifier unit = (Identifier) ut.getField(units[i % units.length]).get(null);
				Identifier fac = Identifier.fromNamespaceAndPath("elsewhere", factions[(i / 2) % factions.length]);
				Object e = m.invoke(null, l, unit, fac, pos, null);
				if (!(e instanceof Mob) && !made.isEmpty()) {
					// KingdomApi refuses soldiers past its per-area cap (32 here): clone an already spawned soldier of the same kit instead,
					// so the renderer draws the same model, armor, trims and held items
					Mob t = made.get(i % made.size());
					Entity c = t.getType().create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
					if (c instanceof Mob cm) {
						for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
							cm.setItemSlot(slot, t.getItemBySlot(slot).copy());
						}
						cm.snapTo(pos, p.getYRot() + 180, 0);
						if (l.addFreshEntity(cm)) {
							e = cm;
							cloned++;
						}
					}
				}
				if (e instanceof Mob mob) {
					if (made.size() < 64) {
						made.add(mob);
					}
					mob.setNoAi(true);
					mob.addTag("qacalib");
					mob.setYRot(p.getYRot() + 180);
					mob.setYHeadRot(p.getYRot() + 180);
					mob.setPersistenceRequired();
					ok++;
					if (!mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() || !mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
						armored++;
					}
				}
			}
		} catch (Throwable t) {
			err = " error " + t;
		}
		return "spawned " + ok + " soldiers (" + armored + " armored, " + cloned + " cloned past the KingdomApi cap), now " + (from + ok) + err;
	}

	private static String arrows(final MinecraftServer s) {
		ServerLevel l = s.overworld();
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		Vec3 fwd = p.getLookAngle().multiply(1, 0, 1).normalize(), side = new Vec3(-fwd.z, 0, fwd.x);
		int ok = 0;
		for (int i = 0; i < 60; i++) {
			Vec3 at = p.position().add(fwd.scale(8 + (i / 12) * 4)).add(side.scale((i % 12 - 5.5) * 1.5)).add(0, 2 + (i % 3), 0);
			var a = new net.minecraft.world.entity.projectile.arrow.Arrow(l, at.x, at.y, at.z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
			a.setNoGravity(true);
			a.setDeltaMovement(fwd.scale(0.001));
			a.addTag("qacalib");
			if (l.addFreshEntity(a)) {
				ok++;
			}
		}
		return ok + " hovering arrows";
	}

	private static String banners(final MinecraftServer s) {
		ServerLevel l = s.overworld();
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		Vec3 fwd = p.getLookAngle().multiply(1, 0, 1).normalize(), side = new Vec3(-fwd.z, 0, fwd.x);
		var src = s.createCommandSourceStack().withLevel(l).withSuppressedOutput().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER);
		String[] colors = {"red", "blue", "yellow", "white", "purple", "green", "black", "orange"};
		int ok = 0;
		for (int i = 0; i < 200; i++) {
			Vec3 at = p.position().add(fwd.scale(10 + (i / 20) * 2.5)).add(side.scale((i % 20 - 9.5) * 1.5));
			int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
			int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			String c = colors[i % colors.length], c2 = colors[(i + 3) % colors.length];
			String cmd = "setblock " + x + " " + y + " " + z + " minecraft:" + c + "_banner[rotation=8]{patterns:[{pattern:\"minecraft:stripe_top\",color:\"" + c2
				+ "\"},{pattern:\"minecraft:cross\",color:\"" + c2 + "\"},{pattern:\"minecraft:border\",color:\"" + c2 + "\"}]}";
			try {
				if (s.getCommands().getDispatcher().execute(cmd, src) > 0) {
					ok++;
				}
			} catch (Throwable t) {
				// try the next spot
			}
			if (!l.getBlockState(new BlockPos(x, y, z)).isAir() && ok == 0 && i == 0) {
				LOG.info("[calib] first banner spot not air after setblock");
			}
		}
		return ok + " patterned banners placed";
	}
}
