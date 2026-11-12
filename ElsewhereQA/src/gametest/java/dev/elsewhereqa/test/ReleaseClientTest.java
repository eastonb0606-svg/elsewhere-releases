package dev.elsewhereqa.test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Release client pass (-PqaClient=release). One world with structures on (setUseConsistentSettings(false)), everything in SURVIVAL:
 * model lookup for every item, teleport to every Elsewhere structure with screenshots, boss summons with boss bar + music + leash checks,
 * HUD toggles, JEI pages (when JEI is loaded). Verdicts go to the log as "[qa-client] RELEASE STATUS area: detail" and to
 * results-client.tsv in the screenshot folder.
 */
public class ReleaseClientTest implements FabricClientGameTest {
	static final Logger LOG = LoggerFactory.getLogger("ElsewhereQA-client");
	static final String NS = "elsewhere";
	private Path out;
	private final List<String[]> results = new ArrayList<>();

	private void rep(final String status, final String area, final String detail) {
		results.add(new String[] {status, area, detail});
		LOG.info("[qa-client] RELEASE {} {}: {}", status, area, detail);
	}

	private void shot(final ClientGameTestContext ctx, final String name) {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		ctx.waitTick();
		Path file = ctx.takeScreenshot(TestScreenshotOptions.of("rel_" + name).withDestinationDir(out).disableCounterPrefix());
		LOG.info("[qa-client] screenshot {}", file);
	}

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		String mode = System.getProperty("elsewhereqa.client");
		if (!"release".equals(mode) && !"fps".equals(mode) && !"gallery".equals(mode)) {
			return;
		}
		String shots = System.getProperty("elsewhereqa.shots");
		out = shots != null ? Path.of(shots) : FabricLoader.getInstance().getGameDir().resolve("../../../screenshots").normalize();
		try {
			Files.createDirectories(out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			if ("gallery".equals(mode)) {
				runGallery(ctx);
				} else if ("fps".equals(mode)) {
				runFps(ctx);
			} else {
				run(ctx);
			}
		} catch (Throwable t) {
			LOG.error("[qa-client] release test crashed", t);
			rep("FAIL", "harness", "client test crashed: " + t);
		} finally {
			StringBuilder sb = new StringBuilder();
			for (String[] r : results) {
				sb.append(r[0]).append('\t').append(r[1]).append('\t').append(r[2].replace('\t', ' ').replace('\n', ' ')).append('\n');
			}
			try {
				Files.writeString(out.resolve("results-client.tsv"), sb.toString());
			} catch (IOException e) {
				LOG.error("could not write results-client.tsv", e);
			}
		}
	}

	private void run(final ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		boolean jei = FabricLoader.getInstance().isModLoaded("jei");
		rep("PASS", "client start", "reached the title screen and is building a world; mods: " + FabricLoader.getInstance().getAllMods().stream()
			.map(m -> m.getMetadata().getId() + "@" + m.getMetadata().getVersion().getFriendlyString())
			.filter(s -> s.startsWith("elsewhere@") || s.startsWith("fabric-api@") || s.startsWith("jei@") || s.startsWith("testmeter@") || s.startsWith("greenleaf@")).toList());
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamerule random_tick_speed 0");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 255 true");
			server.runCommand("effect give @a saturation infinite 255 true");
			server.runCommand("effect give @a regeneration infinite 255 true");
			String mode = ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode().getName());
			rep(mode.equals("survival") ? "PASS" : "FAIL", "client survival", "all shots are taken in game mode " + mode);

			items(ctx);
			kingdomUnits(ctx, server);
			if (sectionOn("sites")) {
				sites(ctx, sp, server);
			}
			if (sectionOn("bosses")) {
				bosses(ctx, sp, server);
			}
			if (sectionOn("hud")) {
				hud(ctx, server);
			}
			if (sectionOn("jei")) {
				if (jei) {
					JeiShots.release(ctx, n -> shot(ctx, n), this::rep);
				} else {
					rep("SKIP", "jei", "JEI is not loaded in this run (run with -Pjei)");
				}
			}
		}
	}

	private static boolean sectionOn(final String s) {
		String v = System.getProperty("elsewhereqa.sections", "");
		return v.isBlank() || v.contains(s);
	}

	// ------------------------------------------------------------------ item models
	private void items(final ClientGameTestContext ctx) {
		List<String> missing = ctx.computeOnClient(mc -> {
			List<String> m = new ArrayList<>();
			for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
				if (!id.getNamespace().equals(NS)) {
					continue;
				}
				ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
				Identifier model = st.get(DataComponents.ITEM_MODEL);
				if (model == null || mc.getModelManager().getItemModel(model) instanceof MissingItemModel) {
					m.add(id.getPath());
				}
			}
			return m;
		});
		rep(missing.isEmpty() ? "PASS" : "FAIL", "item models", missing.size() + " Elsewhere items without a client item model: " + missing);
	}

	// ------------------------------------------------------------------ sites
	private void sites(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server) {
		int max = Integer.getInteger("elsewhereqa.maxSites", 60);
		List<String> names = server.computeOnServer(s -> {
			List<String> l = new ArrayList<>();
			s.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
				if (h.key().identifier().getNamespace().equals(NS)) {
					l.add(h.key().identifier().toString());
				}
			});
			l.sort(String::compareTo);
			return l;
		});
		int done = 0;
		for (String name : names) {
			if (done >= max) {
				break;
			}
			String where = server.computeOnServer(s -> goToStructure(s, name));
			if (where.startsWith("none")) {
				rep("FAIL", "site " + name, "could not be located within 60 chunks of spawn in any matching dimension");
				continue;
			}
			done++;
			ctx.waitTicks(25);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(15);
			String safe = name.substring(name.indexOf(':') + 1);
			shot(ctx, "site_" + safe + "_a");
			ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180.0F));
			ctx.waitTicks(8);
			shot(ctx, "site_" + safe + "_b");
			// the two boss-site actors the alpha.2 QA could not see
			String want = safe.equals("petal_pagoda") ? "kazehana" : safe.equals("sunpit") ? "ankhara" : null;
			if (want != null) {
				ctx.waitTicks(20);
				int seen = ctx.computeOnClient(mc -> {
					int n = 0;
					EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, want));
					for (Entity e : mc.level.entitiesForRendering()) {
						if (e == null) continue;
						if (e.getType() == t && e.distanceTo(mc.player) < 96) {
							n++;
						}
					}
					return n;
				});
				rep(seen > 0 ? "PASS" : "FAIL", "site " + name, want + " is a rendered client entity within 96 blocks (" + seen + ")");
			}
			rep("PASS", "site " + name, "visited " + where + " in survival, 2 screenshots");
		}
		rep(done > 0 ? "PASS" : "FAIL", "sites", done + " of " + names.size() + " Elsewhere structures visited");
	}

	/** Teleports the (only) player beside the nearest instance of a structure; returns a description or "none". */
	private static String goToStructure(final net.minecraft.server.MinecraftServer s, final String name) {
		var reg = s.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder.Reference<Structure> h = reg.get(Identifier.parse(name)).orElse(null);
		if (h == null) {
			return "none";
		}
		for (ServerLevel l : s.getAllLevels()) {
			try {
				boolean any = false;
				for (Holder<Biome> b : l.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
					if (h.value().biomes().contains(b)) {
						any = true;
						break;
					}
				}
				if (!any) {
					continue;
				}
				var pair = l.getChunkSource().getGenerator().findNearestMapStructure(l, HolderSet.direct(h), BlockPos.ZERO, 60, false);
				if (pair == null) {
					continue;
				}
				BlockPos sp = pair.getFirst();
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						l.getChunk((sp.getX() >> 4) + dx, (sp.getZ() >> 4) + dz);
					}
				}
				int x = sp.getX() + 8, z = sp.getZ() + 8;
				int y = safeY(l, x, z);
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				p.teleportTo(l, x + 0.5, y, z + 0.5, Set.of(), 0.0F, 5.0F, true);
				return l.dimension().identifier() + " " + new BlockPos(x, y, z).toShortString();
			} catch (Throwable t) {
				LOG.warn("[qa-client] locating {} in {} threw {}", name, l.dimension().identifier(), t.toString());
			}
		}
		return "none";
	}

	private static int safeY(final ServerLevel l, final int x, final int z) {
		if (!l.dimensionType().hasCeiling()) {
			return Math.max(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), l.getMinY() + 2);
		}
		for (int y = Math.min(l.getMaxY() - 8, 120); y > l.getMinY() + 2; y--) {
			if (l.getBlockState(new BlockPos(x, y, z)).isAir() && l.getBlockState(new BlockPos(x, y + 1, z)).isAir() && !l.getBlockState(new BlockPos(x, y - 1, z)).isAir()
				&& l.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) {
				return y;
			}
		}
		return l.getMinY() + 40;
	}

	// ------------------------------------------------------------------ bosses: bars, music, leash
	private static final List<String> DEFAULT_BOSSES = List.of("tempestra", "kazehana", "ankhara", "grunthar", "solaxis", "mournmaw", "aphelion", "mother_creak");

	private void bosses(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server) {
		// music: one unique track per boss
		List<String> bossIds = new ArrayList<>(List.of("mother_creak", "selenne", "kazehana", "tempestra", "heliodor", "ankhara", "verdigrim", "galvanox", "thurgrim", "hrolf", "percolax",
			"grumblegut", "auris", "lithara", "gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh", "grunthar", "solaxis", "mournmaw", "ossyra", "aphelion"));
		// Boss music events are client-only sounds from assets/elsewhere/sounds.json (not registry entries): check the client sound manager.
		List<String> sounds = new ArrayList<>();
		List<String> noTrack = new ArrayList<>();
		int mapped = 0;
		com.google.gson.JsonObject map = null;
		try (var in = Minecraft.class.getClassLoader().getResourceAsStream("assets/" + NS + "/boss_music.json")) {
			if (in != null) {
				map = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("bosses");
			}
		} catch (Exception e) {
			noTrack.add("boss_music.json unreadable: " + e);
		}
		boolean haveMusic = map != null;
		if (!haveMusic) {
			if (noTrack.isEmpty()) {
				rep("SKIP", "boss music", "no boss_music.json in this jar (boss_music module not merged)");
			} else {
				rep("FAIL", "boss music", noTrack.toString());
			}
		} else {
			// Look the sound events up on the client thread (Minecraft.getInstance() is not allowed on the gametest thread).
			final List<String> wanted = new ArrayList<>();
			for (var e : map.entrySet()) {
				Identifier ev = Identifier.tryParse(e.getValue().getAsString());
				if (ev != null) {
					for (String suffix : new String[] {".intro", ".loop"}) {
						wanted.add(ev.getNamespace() + ":" + ev.getPath() + suffix);
					}
				}
			}
			final java.util.Set<String> registered = ctx.computeOnClient(mc -> {
				java.util.Set<String> have = new java.util.HashSet<>();
				for (String id : wanted) {
					if (mc.getSoundManager().getSoundEvent(Identifier.parse(id)) != null) {
						have.add(id);
					}
				}
				return have;
			});
			for (var e : map.entrySet()) {
				Identifier ev = Identifier.tryParse(e.getValue().getAsString());
				boolean ok = ev != null;
				for (String suffix : new String[] {".intro", ".loop"}) {
					if (ev == null || !registered.contains(ev.getNamespace() + ":" + ev.getPath() + suffix)) {
						ok = false;
						noTrack.add(e.getKey() + " -> " + e.getValue() + suffix);
					} else {
						sounds.add(ev.getPath() + suffix);
					}
				}
				if (ok) {
					mapped++;
				}
			}
			rep(noTrack.isEmpty() && mapped > 0 ? "PASS" : "FAIL", "boss music", mapped + " of " + map.size() + " boss_music.json mappings have .intro and .loop in the client sound manager; missing: " + noTrack);
		}
		// boss_ui: BossUiClient.onInitializeClient registers the "bossbars" meter in the shared HUD; absent = the custom bar never initialised
		try {
			Class.forName("dev.elsewhere.boss_ui.BossUiClient");
			var f = Class.forName("dev.elsewhere.hud.ElsewhereHud").getDeclaredField("CONFIG_KEYS");
			f.setAccessible(true);
			boolean active = ((java.util.Map<?, ?>) f.get(null)).containsKey("bossbars");
			rep(active ? "PASS" : "FAIL", "boss_ui custom bar", "ElsewhereHud meter 'bossbars' registered by BossUiClient = " + active);
		} catch (ClassNotFoundException e) {
			rep("SKIP", "boss_ui custom bar", "boss_ui not in this jar");
		} catch (Exception e) {
			rep("FAIL", "boss_ui custom bar", "cannot read HUD meter registry: " + e);
		}
		String bossList = System.getProperty("elsewhereqa.bosses", "");
		List<String> pick = bossList.isBlank() ? DEFAULT_BOSSES : List.of(bossList.split(","));
		server.runCommand("gamemode survival @a");
		for (String boss : pick) {
			Identifier bid = Identifier.fromNamespaceAndPath(NS, boss);
			if (!BuiltInRegistries.ENTITY_TYPE.containsKey(bid)) {
				rep("SKIP", "boss " + boss, "entity type not registered");
				continue;
			}
			int before = bossBars(ctx);
			String res = server.computeOnServer(s -> summon(s, boss));
			if (!res.startsWith("ok")) {
				ctx.waitTicks(60);
				res = server.computeOnServer(s -> locate(s, boss));
			}
			if (!res.startsWith("ok")) {
				rep("FAIL", "boss " + boss, "could not be summoned: " + res);
				continue;
			}
			String sumInfo = res;
			ctx.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(40);
			lookAt(ctx, server, bid);
			ctx.waitTicks(10);
			List<String> bars = barNames(ctx);
			int during = bars.size();
			List<String> playing = playingSounds(ctx);
			shot(ctx, "boss_" + boss);
			boolean direct = sumInfo.contains("direct spawn");
			rep(during > before ? "PASS" : direct ? "WARN" : "FAIL", "boss " + boss, "boss bar shows in survival (bars " + before + " -> " + during + " " + bars + ") after " + sumInfo);
			if (haveMusic) {
				boolean ok = playing.stream().anyMatch(s -> s.startsWith(NS + ":") && (s.contains("music") || s.contains("theme") || s.contains("boss")));
				rep(ok ? "PASS" : direct ? "WARN" : "FAIL", "boss " + boss, "boss music event plays on the client; playing now " + playing);
			}
			// stays in its arena: 5 s later it is still within 120 blocks of where it started and above the void
			ctx.waitTicks(100);
			String leash = server.computeOnServer(s -> {
				EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(bid);
				for (ServerLevel l : s.getAllLevels()) {
					for (Entity e : l.getEntities(t, x -> x.isAlive())) {
						ServerPlayer p = s.getPlayerList().getPlayers().get(0);
						double d = e.distanceTo(p);
						boolean bad = e.getY() < l.getMinY() + 12 || d > 120 || e.level() != p.level();
						return (bad ? "BAD " : "OK ") + "boss at " + e.blockPosition().toShortString() + ", " + (int)d + " blocks from the player";
					}
				}
				return "GONE boss no longer alive (killed by its own arena?)";
			});
			rep(leash.startsWith("OK") ? "PASS" : "FAIL", "boss " + boss, "stays in its arena after summon, player survives: " + leash);
			// clear: bars disappear when the boss is discarded
			server.runOnServer(s -> {
				EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(bid);
				for (ServerLevel l : s.getAllLevels()) {
					for (Entity e : l.getEntities(t, x -> true)) {
						e.discard();
					}
					ServerPlayer pl = s.getPlayerList().getPlayers().get(0);
					if (l == pl.level()) {
						for (Entity e : l.getEntitiesOfClass(Entity.class, pl.getBoundingBox().inflate(150), x -> !(x instanceof ServerPlayer)
							&& BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()).getNamespace().equals(NS))) {
							e.discard(); // mounts and adds (Ashtusk) carry their own bars
						}
					}
				}
			});
			ctx.waitTicks(40);
			int after = bossBars(ctx);
			rep(after <= before ? "PASS" : "FAIL", "boss " + boss, "boss bar is removed when the boss goes (bars " + after + " vs " + before + " before)");
			server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(), 0.5, 120, 0.5, Set.of(), 0, 0, true));
			ctx.waitTicks(20);
		}
	}

	private static String summon(final net.minecraft.server.MinecraftServer s, final String boss) {
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		var src = s.createCommandSourceStack().withEntity(p).withPosition(p.position()).withLevel(p.level()).withSuppressedOutput();
		List<String> roots = new ArrayList<>();
		for (var node : s.getCommands().getDispatcher().getRoot().getChildren()) {
			if (node.getName().startsWith("elsewhere")) {
				roots.add(node.getName());
			}
		}
		String tried = "";
		for (String r : roots) {
			for (String form : new String[] {r + " boss " + boss, r + " arena " + boss}) {
				try {
					var parse = s.getCommands().getDispatcher().parse(form, src);
					if (parse.getReader().canRead()) {
						continue;
					}
					s.getCommands().getDispatcher().execute(parse);
					String r2 = locate(s, boss);
					if (r2.startsWith("ok")) {
						return r2 + " via /" + form;
					}
					tried += form + "; ";
				} catch (Throwable t) {
					tried += form + " threw " + t + "; ";
				}
			}
		}
		// plain summon on a stone platform above the player
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, boss));
		ServerLevel lvl = s.overworld();
		int bx = 40000, bz = 40000;
		lvl.getChunk(bx >> 4, bz >> 4);
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -16; dz <= 16; dz++) {
				lvl.setBlock(new BlockPos(bx + dx, 119, bz + dz), Blocks.STONE.defaultBlockState(), 2);
				for (int dy = 0; dy < 14; dy++) {
					lvl.setBlock(new BlockPos(bx + dx, 120 + dy, bz + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		Entity e = type.create(lvl, EntitySpawnReason.COMMAND);
		e.snapTo(bx + 0.5, 120, bz + 8.5, 180.0F, 0.0F);
		if (e instanceof Mob m) {
			m.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.COMMAND, null);
		}
		lvl.addFreshEntity(e);
		p.teleportTo(lvl, bx + 0.5, 120, bz + 0.5, Set.of(), 0, 0, true);
		return "ok direct spawn on a platform (no dev command: " + tried + ")";
	}

	private static String locate(final net.minecraft.server.MinecraftServer s, final String boss) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, boss));
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		for (ServerLevel l : s.getAllLevels()) {
			for (Entity e : l.getEntities(type, x -> x.isAlive())) {
				if (e.level() != p.level() || e.distanceTo(p) > 40) {
					p.teleportTo((ServerLevel)e.level(), e.getX(), e.getY(), e.getZ() - 10, Set.of(), 0, 0, true);
				}
				if (e instanceof LivingEntity le) {
					le.hurtServer((ServerLevel)e.level(), e.level().damageSources().playerAttack(p), 1.0F);
				}
				return "ok at " + e.blockPosition().toShortString() + " in " + e.level().dimension().identifier();
			}
		}
		return "no live boss found";
	}

	private void lookAt(final ClientGameTestContext ctx, final TestServerContext server, final Identifier id) {
		double[] c = server.computeOnServer(s -> {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			for (Entity e : ((ServerLevel)p.level()).getEntities(type, x -> x.isAlive())) {
				return new double[] {e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ()};
			}
			return null;
		});
		if (c == null) {
			return;
		}
		ctx.runOnClient(mc -> {
			var eye = mc.player.getEyePosition();
			double dx = c[0] - eye.x, dy = c[1] - eye.y, dz = c[2] - eye.z;
			double h = Math.sqrt(dx * dx + dz * dz);
			float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float)(-Math.toDegrees(Math.atan2(dy, h)));
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.yRotO = yaw;
			mc.player.xRotO = pitch;
		});
	}

	private static java.util.Map<?, ?> barMap(final Minecraft mc) throws ReflectiveOperationException {
		Object ov = mc.gui.hud.getBossOverlay();
		Field f = ov.getClass().getDeclaredField("events");
		f.setAccessible(true);
		return (java.util.Map<?, ?>)f.get(ov);
	}

	private int bossBars(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			try {
				return barMap(mc).size();
			} catch (Throwable t) {
				return -1;
			}
		});
	}

	private List<String> barNames(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			List<String> l = new ArrayList<>();
			try {
				for (Object v : barMap(mc).values()) {
					Object name = v.getClass().getMethod("getName").invoke(v);
					l.add(String.valueOf(name.getClass().getMethod("getString").invoke(name)));
				}
			} catch (Throwable t) {
				l.add("?" + t);
			}
			return l;
		});
	}

	/** Ids of the sounds the client is playing right now (sound engine channel map + the music manager's current track). */
	private List<String> playingSounds(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			Set<String> ids = new LinkedHashSet<>();
			try {
				Object sm = mc.getSoundManager();
				Field se = sm.getClass().getDeclaredField("soundEngine");
				se.setAccessible(true);
				Object eng = se.get(sm);
				Field ic = eng.getClass().getDeclaredField("instanceToChannel");
				ic.setAccessible(true);
				for (Object k : ((java.util.Map<?, ?>)ic.get(eng)).keySet()) {
					ids.add(String.valueOf(k.getClass().getMethod("getIdentifier").invoke(k)));
				}
			} catch (Throwable t) {
				ids.add("?soundEngine:" + t);
			}
			try {
				Object mm = mc.getMusicManager();
				for (Field f : mm.getClass().getDeclaredFields()) {
					f.setAccessible(true);
					Object v = f.get(mm);
					if (v != null && v.getClass().getName().contains("SoundInstance") || v instanceof net.minecraft.client.resources.sounds.SoundInstance) {
						ids.add("music:" + ((net.minecraft.client.resources.sounds.SoundInstance)v).getIdentifier());
					}
				}
			} catch (Throwable t) {
				ids.add("?musicManager:" + t);
			}
			return new ArrayList<>(ids);
		});
	}

	// ------------------------------------------------------------------ HUD toggles
	private void hud(final ClientGameTestContext ctx, final TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with elsewhere:frayed_wayfinder");
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 45 10");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(100);
		shot(ctx, "hud_default");
		QAClientTest.clientCmd(ctx, "elsewhere_hud all always");
		ctx.waitTicks(40);
		shot(ctx, "hud_always");
		QAClientTest.hudAllHidden(ctx, true);
		ctx.waitTicks(40);
		shot(ctx, "hud_allhidden");
		QAClientTest.hudAllHidden(ctx, false);
		QAClientTest.clientCmd(ctx, "elsewhere_hud all auto");
		ctx.waitTicks(20);
		shot(ctx, "hud_auto");
		String state = ctx.computeOnClient(mc -> {
			try {
				return String.valueOf(Class.forName("dev.elsewhere.hud.ElsewhereHud").getMethod("isAllHidden").invoke(null));
			} catch (Throwable t) {
				return "n/a";
			}
		});
		rep("PASS", "hud toggles", "default, always, all-hidden and auto states screenshotted in survival (all-hidden now " + state + "); compare rel_hud_*.png");
	}


	// ------------------------------------------------------------------ kingdom units on the client (a constructor bug once kicked the client)
	private static long errorLines() {
		try {
			Path log = FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
			long n = 0;
			for (String ln : Files.readAllLines(log, java.nio.charset.StandardCharsets.UTF_8)) {
				if (ln.contains("/ERROR]") && !ln.matches(".*(401|Realms|Anisotropic|authentication|Yggdrasil|sessionserver|Telemetry|profile).*")) {
					n++;
				}
			}
			return n;
		} catch (Exception e) {
			return -1;
		}
	}

	private void kingdomUnits(final ClientGameTestContext ctx, final TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("tp @a 0 -60 0 0 10");
		ctx.waitTicks(40);
		long before = errorLines();
		String made = server.computeOnServer(s -> {
			ServerLevel lvl = s.overworld();
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			java.util.Set<EntityType<?>> types = new java.util.LinkedHashSet<>();
			try {
				Class<?> ke = Class.forName("dev.elsewhere.kingdom.KingdomEntities");
				for (Object o : (List<?>)ke.getField("BUILT_IN").get(null)) {
					types.add((EntityType<?>)o);
				}
			} catch (ReflectiveOperationException e) {
				// module absent: the registry scan below still looks for named kingdom entities
			}
			for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
				String path = id.getPath();
				if (id.getNamespace().equals(NS) && path.matches(".*(serf|steward|court_|marshal|castellan|master_at_arms|huntsman|chancellor|king|osric|soldier|knight|archer|sergeant|captain|lancer|herald|squire|pikeman|surgeon|standard_bearer|man_at_arms|crossbowman|cavalier).*")) {
					types.add(BuiltInRegistries.ENTITY_TYPE.getValue(id));
				}
			}
			int ok = 0, bad = 0;
			StringBuilder names = new StringBuilder(), fails = new StringBuilder();
			int i = 0;
			for (EntityType<?> t : types) {
				String name = BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath();
				try {
					Entity e = t.create(lvl, EntitySpawnReason.COMMAND);
					double ang = i++ * 2 * Math.PI / Math.max(1, types.size());
					e.snapTo(p.getX() + Math.cos(ang) * 6, p.getY(), p.getZ() + Math.sin(ang) * 6, 0, 0);
					e.addTag("qaking");
					if (e instanceof Mob m) {
						m.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.COMMAND, null);
						m.setPersistenceRequired();
					}
					lvl.addFreshEntity(e);
					ok++;
					names.append(name).append(' ');
				} catch (Throwable ex) {
					bad++;
					fails.append(name).append("=").append(ex).append("; ");
				}
			}
			return ok + "|" + bad + "|" + names + "|" + fails;
		});
		String[] parts = made.split("\\|", 4);
		int ok = Integer.parseInt(parts[0]), bad = Integer.parseInt(parts[1]);
		if (ok == 0 && bad == 0) {
			rep("SKIP", "kingdom units", "no kingdom/court/serf/steward entity types are registered in this jar");
			return;
		}
		ctx.waitTicks(100); // about 5 s: renderers, spawn packets, first AI ticks
		boolean connected = ctx.computeOnClient(mc -> mc.getConnection() != null && mc.level != null && mc.player != null && mc.isSameThread());
		int seen = ctx.computeOnClient(mc -> {
			int n = 0;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e == null) continue;
				if (e.entityTags().contains("qaking") || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS) && e != mc.player) {
					n++;
				}
			}
			return n;
		});
		shot(ctx, "kingdom_units");
		long after = errorLines();
		rep(bad == 0 ? "PASS" : "FAIL", "kingdom units", "spawned " + ok + " units near the player (" + (parts.length > 2 ? parts[2].trim() : "") + "); " + bad + " failed to spawn: " + (parts.length > 3 ? parts[3] : ""));
		rep(connected && seen > 0 ? "PASS" : "FAIL", "kingdom units client", "client still connected after 5 s with " + seen + " of those entities known to the client");
		rep(after >= 0 && after == before ? "PASS" : "FAIL", "kingdom units log", "client log ERROR lines during the spawn: " + (after - before) + " (must be 0)");
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getAllEntities()) {
				if (e != null && e.entityTags().contains("qaking")) {
					e.discard();
				}
			}
		});
		ctx.waitTicks(20);
	}

	// ------------------------------------------------------------------ gallery: every Aldermark character, front / 3/4 / back
	private static final String[] GALLERY_IDS = {
		"kingdom_man_at_arms", "kingdom_knight", "kingdom_lancer", "kingdom_mounted_swordsman", "kingdom_archer", "kingdom_crossbowman",
		"kingdom_squire", "kingdom_pikeman", "kingdom_sergeant", "kingdom_captain", "kingdom_standard_bearer", "kingdom_herald", "kingdom_surgeon",
		"court_marshal", "court_castellan", "court_master_at_arms", "court_huntsman", "court_chancellor", "court_king", "serf", "royal_steward"};
	private final List<String> galleryRows = new ArrayList<>();

	private void galleryShot(final ClientGameTestContext ctx, final String name) {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		ctx.waitTick();
		Path file = ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(out).disableCounterPrefix());
		LOG.info("[qa-client] gallery screenshot {}", file);
	}

	/** Trim pattern of the spawned character's helmet (or first trimmed piece), "none" if untrimmed. */
	private static String trimOf(final net.minecraft.server.MinecraftServer s) {
		for (Entity e : s.overworld().getAllEntities()) {
			if (e != null && e.entityTags().contains("qagal") && e instanceof LivingEntity le) {
				for (net.minecraft.world.entity.EquipmentSlot sl : new net.minecraft.world.entity.EquipmentSlot[] {
					net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
					net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
					var t = le.getItemBySlot(sl).get(DataComponents.TRIM);
					if (t != null) {
						return t.pattern().getRegisteredName();
					}
				}
			}
		}
		return "none";
	}

	/** Gives a plain-summoned kingdom soldier its unit and the Aldermark faction (what the spawn paths do), by reflection. */
	private static String setUnitReflect(final net.minecraft.server.MinecraftServer s, final String unitPath) {
		try {
			Identifier unit = Identifier.fromNamespaceAndPath(NS, unitPath);
			Identifier faction = (Identifier)Class.forName("dev.elsewhere.kingdom.Factions").getField("ALDERMARK").get(null);
			for (Entity e : s.overworld().getAllEntities()) {
				if (e != null && e.entityTags().contains("qagal")) {
					e.getClass().getMethod("setFaction", Identifier.class).invoke(e, faction);
					e.getClass().getMethod("setUnit", Identifier.class).invoke(e, unit);
					return "ok " + e.getType();
				}
			}
			return "no entity";
		} catch (ReflectiveOperationException e) {
			return "failed " + e;
		}
	}

	private void runGallery(final ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1000, 720);
		ctx.runOnClient(mc -> {
			mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) { // default gametest world is superflat
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			// plain backdrop: a white concrete room open to the sky, floor at y -61, centre (0, -60, 0)
			server.runCommand("tp @a 0 -59 0");
			ctx.waitTicks(20);
			server.runCommand("fill -9 -61 -9 9 -50 9 minecraft:white_concrete hollow");
			server.runCommand("fill -8 -60 -8 8 -51 8 minecraft:air");
			server.runCommand("fill -9 -50 -9 9 -50 9 minecraft:air");
			server.runCommand("fill -8 -61 -8 8 -61 8 minecraft:white_concrete");
			ctx.waitTicks(20);
			String only = System.getProperty("elsewhereqa.galleryOnly", "");
			for (String id : GALLERY_IDS) {
				if (!only.isBlank() && !java.util.Arrays.asList(only.split(",")).contains(id)) {
					continue;
				}
				boolean steward = id.equals("royal_steward");
				boolean exists = steward || server.computeOnServer(s -> BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.fromNamespaceAndPath(NS, id)));
				if (!exists) {
					rep("FAIL", "gallery " + id, "entity type not registered");
					continue;
				}
				boolean mounted = id.equals("kingdom_lancer") || id.equals("kingdom_mounted_swordsman") || id.equals("court_marshal");
				Set<String> seen = new LinkedHashSet<>();
				int sample = 0;
				for (int attempt = 0; attempt < 8 && sample < 3; attempt++) {
					server.runOnServer(s -> {
						for (Entity e : s.overworld().getAllEntities()) {
							if (e != null && !(e instanceof ServerPlayer)) {
								e.discard();
							}
						}
					});
					ctx.waitTicks(30); // let any smoke settle
					server.runCommand("time set 6000");
					// plain summon (no NBT) so finalizeSpawn applies the unit loadout; freeze afterwards
					if (steward) {
						server.runCommand("summon minecraft:villager 0 -60 0");
					} else {
						server.runCommand("summon " + NS + ":" + id + " 0 -60 0");
					}
					ctx.waitTicks(5);
					server.runCommand("tag @e[type=!minecraft:player] add qagal");
					if (!steward && !id.equals("serf")) {
						final String unitPath = id.startsWith("kingdom_") ? id.substring("kingdom_".length()) : id;
						String res = server.computeOnServer(s -> setUnitReflect(s, unitPath));
						LOG.info("[qa-client] gallery setUnit {}: {}", id, res);
					}
					ctx.waitTicks(25); // first server tick: loadout, mount
					server.runCommand("tag @e[type=!minecraft:player] add qagal");
					server.runCommand("execute as @e[tag=qagal] run data merge entity @s {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}");
					if (steward) {
						server.runCommand("data merge entity @e[type=minecraft:villager,limit=1] {VillagerData:{profession:\"elsewhere:royal_steward\",level:1,type:\"minecraft:plains\"}}");
					server.runCommand("tp @e[type=minecraft:villager] 0.5 -60 0.5 0 0");
					}
					ctx.waitTicks(20);
					String trim = server.computeOnServer(ReleaseClientTest::trimOf);
					boolean alive = server.computeOnServer(s -> {
						for (Entity e : s.overworld().getAllEntities()) {
							if (e != null && e.entityTags().contains("qagal")) {
								return true;
							}
						}
						return false;
					});
					if (!alive) {
						rep("FAIL", "gallery " + id, "summon produced no entity");
						break;
					}
					if (!seen.add(trim)) {
						if (attempt > 0) {
							continue; // already have this trim; roll another UUID
						}
					}
					sample++;
					String suffix = sample == 1 ? "" : "_" + sample;
					double d = mounted ? 4.2 : 2.8, cy = mounted ? -60 + 1.45 : -60 + 0.95;
					double a = Math.toRadians(40);
					double[][] cams = {{0, d, 180}, {d * Math.sin(a), d * Math.cos(a), 180 - 40}, {0, -d, 0}};
					String[] names = {"front", "34", "back"};
					for (int v = 0; v < 3; v++) {
						server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f 0", cams[v][0], cy - 1.62, cams[v][1], cams[v][2]));
						ctx.waitTicks(8);
						galleryShot(ctx, id + "_" + names[v] + suffix);
					}
					galleryRows.add(id + "\t" + sample + "\t" + trim);
					rep("PASS", "gallery " + id + " #" + sample, "3 views, trim " + trim);
				}
			}
			ctx.waitTicks(5);
		} finally {
			try {
				Files.writeString(out.resolve("samples.tsv"), "id\tsample\ttrim\n" + String.join("\n", galleryRows) + "\n");
			} catch (IOException e) {
				LOG.error("samples.tsv", e);
			}
		}
	}

	// ------------------------------------------------------------------ FPS with kingdom soldiers (this Mac, no JEI)
	private final List<String> fpsRows = new ArrayList<>();

	private void runFps(final ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		boolean kingdom = ctx.computeOnClient(mc -> {
			try {
				Class.forName("dev.elsewhere.kingdom.KingdomEntities");
				return true;
			} catch (Throwable t) {
				return false;
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 255 true");
			server.runCommand("effect give @a regeneration infinite 255 true");
			server.runCommand("effect give @a saturation infinite 255 true");
			server.runCommand("tp @a 0 -60 0 0 10");
			ctx.waitTicks(60);
			measure(ctx, "a no soldiers");
			if (!kingdom) {
				rep("SKIP", "fps soldiers", "no dev.elsewhere.kingdom classes in this run: soldier cases b and c skipped (pass the kingdom jar via EXTRA_JARS)");
				return;
			}
			for (int[] cfg : new int[][] {{20, 20}, {40, 30}}) {
				final int soldiers = cfg[0], zombies = cfg[1];
				String made = server.computeOnServer(s -> spawnFpsArmy(s, soldiers, zombies));
				LOG.info("[qa-client] fps army: {}", made);
				ctx.waitTicks(60);
				shot(ctx, "fps_" + soldiers + "v" + zombies);
				measure(ctx, (soldiers == 20 ? "b " : "c ") + soldiers + " soldiers vs " + zombies + " zombies");
				server.runOnServer(s -> {
					for (Entity e : s.overworld().getAllEntities()) {
						if (e != null && e.entityTags().contains("qafps")) {
							e.discard();
						}
					}
				});
				ctx.waitTicks(40);
			}
		} finally {
			try {
				Files.writeString(out.resolve("fps.tsv"), String.join("\n", fpsRows) + "\n");
			} catch (IOException e) {
				LOG.error("fps.tsv", e);
			}
		}
	}

	private static String spawnFpsArmy(final net.minecraft.server.MinecraftServer s, final int soldiers, final int zombies) {
		ServerLevel lvl = s.overworld();
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		List<EntityType<?>> types = new ArrayList<>();
		try {
			Class<?> ke = Class.forName("dev.elsewhere.kingdom.KingdomEntities");
			for (Object o : (List<?>)ke.getField("BUILT_IN").get(null)) {
				types.add((EntityType<?>)o);
			}
		} catch (ReflectiveOperationException e) {
			return "no kingdom entities: " + e;
		}
		String[] trims = {"minecraft:sentry", "minecraft:coast", "minecraft:wild", "minecraft:rib"};
		String[] mats = {"minecraft:gold", "minecraft:redstone", "minecraft:diamond", "minecraft:copper"};
		int ok = 0;
		for (int i = 0; i < soldiers; i++) {
			EntityType<?> t = types.get(i % Math.min(types.size(), 7));
			Entity e = t.create(lvl, EntitySpawnReason.COMMAND);
			if (e == null) {
				continue;
			}
			double ang = i * 2 * Math.PI / soldiers;
			e.snapTo(p.getX() + Math.cos(ang) * 7, p.getY(), p.getZ() + Math.sin(ang) * 7, 0, 0);
			e.addTag("qafps");
			if (e instanceof Mob m) {
				m.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.COMMAND, null);
				m.setPersistenceRequired();
			}
			lvl.addFreshEntity(e);
			ok++;
		}
		for (int i = 0; i < zombies; i++) {
			Entity z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(lvl, EntitySpawnReason.COMMAND);
			double ang = i * 2 * Math.PI / zombies + 0.2;
			z.snapTo(p.getX() + Math.cos(ang) * 12, p.getY(), p.getZ() + Math.sin(ang) * 12, 0, 0);
			z.addTag("qafps");
			if (z instanceof Mob m) {
				m.setPersistenceRequired();
			}
			lvl.addFreshEntity(z);
		}
		// mixed trimmed armour on every soldier: four pieces, trim pattern/material rotating
		var src = s.createCommandSourceStack().withLevel(lvl).withSuppressedOutput().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER);
		String[] pieces = {"head:iron_helmet", "chest:iron_chestplate", "legs:chainmail_leggings", "feet:diamond_boots"};
		int n = 0;
		for (int k = 0; k < pieces.length; k++) {
			String slot = pieces[k].split(":")[0], item = pieces[k].split(":")[1];
			try {
				s.getCommands().performPrefixedCommand(src, "item replace entity @e[tag=qafps,type=!minecraft:zombie] armor." + slot + " with minecraft:" + item
					+ "[trim={material:\"" + mats[k] + "\",pattern:\"" + trims[k] + "\"}]");
				n++;
			} catch (Throwable t) {
				LOG.warn("[qa-client] trim command failed: {}", t.toString());
			}
		}
		return ok + " soldiers, " + zombies + " zombies, trimmed armour commands " + n + "/4";
	}

	private void measure(final ClientGameTestContext ctx, final String label) {
		List<Double> frames = new ArrayList<>();
		for (int i = 0; i < 400; i++) { // 20 s of ticks
			ctx.waitTick();
			frames.add(ctx.computeOnClient(mc -> mc.getFrameTimeNs() / 1.0e6));
		}
		double mean = frames.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		List<Double> sorted = new ArrayList<>(frames);
		sorted.sort(Double::compare);
		double p95 = sorted.get((int)(sorted.size() * 0.95)), p99 = sorted.get((int)(sorted.size() * 0.99));
		double fps = mean > 0 ? 1000.0 / mean : 0;
		String line = String.format(Locale.ROOT, "%s\t%.1f\t%.2f\t%.2f", label, fps, p95, p99);
		fpsRows.add(line);
		rep("PASS", "fps " + label, String.format(Locale.ROOT, "average %.1f fps over 20 s (mean frame %.2f ms), 95th percentile frame %.2f ms, 99th %.2f ms", fps, mean, p95, p99));
	}
}
