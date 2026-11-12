package dev.elsewhereqa.shots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-session client shot runner (-PqaClient=shots). Reads the machine-readable shot list that tools/shots_compile.py makes from
 * ElsewhereContent/SHOTS-3.0.md and captures every shot to <shots>/<lane>__<slug>.png in ONE client session: the flat-stage scenes
 * in one consistent (superflat) world, then the worldgen scenes in one normal world (seed -PqaShotSeed, default 4242), then the
 * log checks. index.json (lane -> item -> shots) is rewritten after every scene with ok/fail/manual and a detail, so the progress
 * meter and the wiki can read a partial run. Log lines: "[shots] OK|FAIL|MANUAL <file>: detail" and a final "[shots] TOTAL" line.
 * Properties: elsewhereqa.shotList, elsewhereqa.shots (out dir), elsewhereqa.shotOnly (scene numbers / ranges / lanes),
 * elsewhereqa.shotSeed, elsewhereqa.watchdog (minutes, the JVM halts itself after writing the index).
 */
public class ShotRunner implements FabricClientGameTest {
	static final Logger LOG = LoggerFactory.getLogger("ElsewhereQA-shots");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private Path out;
	private Path indexPath;
	private JsonObject index;
	private final Map<String, JsonObject> entries = new HashMap<>();
	private int ok;
	private int fail;
	private int manual;

	// per-scene state
	private TestSingleplayerContext sp;
	private TestServerContext server;
	private JsonObject scene;
	private boolean flatWorld;
	private int groundY;
	private int[] anchor;
	private String anchorDim = "minecraft:overworld";
	private UUID target;
	private boolean invSwitched;
	private boolean useHeld;
	private String label = "";
	private final Map<String, Integer> named = new HashMap<>();

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if (!"shots".equals(System.getProperty("elsewhereqa.client"))) {
			return;
		}
		out = Path.of(System.getProperty("elsewhereqa.shots", "shots-3.0")).toAbsolutePath();
		Path list = Path.of(System.getProperty("elsewhereqa.shotList", out.resolve("shotlist.json").toString()));
		indexPath = out.resolve("index.json");
		startWatchdog();
		try {
			Files.createDirectories(out);
			JsonObject doc = JsonParser.parseString(Files.readString(list, StandardCharsets.UTF_8)).getAsJsonObject();
			index = Files.exists(indexPath) ? JsonParser.parseString(Files.readString(indexPath, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
			if (!index.has("lanes")) {
				index.add("lanes", new JsonObject());
			}
			List<JsonObject> flat = new ArrayList<>();
			List<JsonObject> normal = new ArrayList<>();
			List<JsonObject> none = new ArrayList<>();
			Selection sel = new Selection(System.getProperty("elsewhereqa.shotOnly", ""));
			for (JsonElement e : doc.getAsJsonArray("scenes")) {
				JsonObject s = e.getAsJsonObject();
				if (!sel.wants(s)) {
					continue;
				}
				indexEntries(s);
				String w = s.get("world").getAsString();
				(w.equals("flat") ? flat : w.equals("normal") ? normal : none).add(s);
			}
			LOG.info("[shots] {} flat-stage scenes, {} worldgen scenes, {} log/manual scenes from {}", flat.size(), normal.size(), none.size(), list);
			ctx.getInput().resizeWindow(1280, 720);
			ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			if (!flat.isEmpty()) {
				try (TestSingleplayerContext w = ctx.worldBuilder().create()) {
					world(ctx, w, true, flat);
				}
			}
			if (!normal.isEmpty()) {
				String seed = System.getProperty("elsewhereqa.shotSeed", "4242");
				try (TestSingleplayerContext w = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(st -> st.setSeed(seed)).create()) {
					world(ctx, w, false, normal);
				}
			}
			for (JsonObject s : none) {
				scene = s;
				runSteps(ctx, s);
				saveIndex();
			}
		} catch (Throwable t) {
			LOG.error("[shots] runner crashed", t);
		} finally {
			LOG.info("[shots] TOTAL {} ok, {} fail, {} manual", ok, fail, manual);
			saveIndex();
		}
	}

	// ------------------------------------------------------------------ worlds and scenes
	private void world(final ClientGameTestContext ctx, final TestSingleplayerContext w, final boolean flat, final List<JsonObject> scenes) {
		sp = w;
		server = w.getServer();
		flatWorld = flat;
		sp.getConnection().waitForChunksRender();
		for (String c : new String[] {"gamerule random_tick_speed 0", "gamerule spawn_mobs false", "gamerule do_daylight_cycle false",
			"gamerule do_weather_cycle false", "gamerule do_mob_spawning false", "gamerule send_command_feedback false", "time set 6000", "weather clear"}) {
			server.runCommand(c);
		}
		groundY = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0));
		if (flat) {
			server.runCommand("forceload add -48 -48 48 48");
		}
		for (JsonObject s : scenes) {
			scene = s;
			long t0 = System.currentTimeMillis();
			try {
				runSteps(ctx, s);
			} catch (Throwable t) {
				LOG.error("[shots] scene {} threw", s.get("n"), t);
				failRemaining(s, "scene threw: " + t);
			} finally {
				cleanUp(ctx);
			}
			LOG.info("[shots] scene {} ({} | {}) done in {} ms", s.get("n"), s.get("lane").getAsString(), s.get("item").getAsString(), System.currentTimeMillis() - t0);
			saveIndex();
		}
		sp = null;
		server = null;
	}

	private void runSteps(final ClientGameTestContext ctx, final JsonObject s) {
		anchor = null;
		anchorDim = "minecraft:overworld";
		target = null;
		label = "";
		named.clear();
		for (JsonElement el : s.getAsJsonArray("steps")) {
			JsonArray st = el.getAsJsonArray();
			String op = st.get(0).getAsString();
			try {
				step(ctx, op, st);
			} catch (SceneFail f) {
				LOG.warn("[shots] scene {} stopped at {}: {}", s.get("n"), op, f.getMessage());
				failRemaining(s, op + ": " + f.getMessage());
				return;
			}
		}
	}

	/** Marks every shot of the scene that has no verdict yet as failed (the scene could not reach it). */
	private void failRemaining(final JsonObject s, final String why) {
		for (JsonElement e : s.getAsJsonArray("shots")) {
			JsonObject sh = e.getAsJsonObject();
			JsonObject en = entries.get(sh.get("file").getAsString());
			if (en != null && !en.has("taken")) {
				verdict(sh.get("file").getAsString(), "fail", why);
			}
		}
	}

	private static final class SceneFail extends RuntimeException {
		SceneFail(final String m) {
			super(m);
		}
	}

	// ------------------------------------------------------------------ the step vocabulary
	private void step(final ClientGameTestContext ctx, final String op, final JsonArray a) {
		switch (op) {
			case "reset" -> reset(ctx, a.get(1).getAsString());
			case "cmd" -> server.runCommand("execute as @p at @p run " + a.get(1).getAsString());
			case "tp" -> tp(ctx, a);
			case "tp_downhill" -> tpDownhill(ctx, a.get(1).getAsDouble());
			case "anchor_from_log" -> anchorFromLog(a.get(1).getAsString(), a.get(2).getAsString());
			case "locate", "locate_near" -> locate(ctx, a.get(1).getAsString(), op.equals("locate_near"));
			case "summon" -> summon(ctx, a);
			case "view" -> view(ctx, a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble());
			case "look_target" -> lookTarget(ctx);
			case "camera" -> {
				String m = a.get(1).getAsString();
				CameraType t = m.equals("front") ? CameraType.THIRD_PERSON_FRONT : m.equals("back") ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
				ctx.runOnClient(mc -> mc.options.setCameraType(t));
			}
			case "gui" -> gui(ctx, a.get(1).getAsBoolean());
			case "f3" -> {
				boolean on = a.get(1).getAsBoolean();
				ctx.runOnClient(mc -> mc.debugEntries.setOverlayVisible(on));
			}
			case "chat" -> {
				boolean on = a.get(1).getAsBoolean();
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(on ? ChatVisiblity.FULL : ChatVisiblity.HIDDEN));
			}
			case "subtitles" -> {
				boolean on = a.get(1).getAsBoolean();
				ctx.runOnClient(mc -> mc.options.showSubtitles().set(on));
			}
			case "wait" -> ctx.waitTicks(Math.max(1, a.get(1).getAsInt()));
			case "chunks" -> chunks(ctx);
			case "damage" -> server.runCommand("damage " + needTarget() + " " + a.get(1).getAsString() + " minecraft:generic");
			case "kill" -> server.runCommand("kill " + needTarget());
			case "hurt_to" -> hurtTo(ctx, a.get(1).getAsFloat());
			case "equip" -> server.runCommand("item replace entity @p " + a.get(1).getAsString() + " with " + a.get(2).getAsString());
			case "hold" -> {
				server.runCommand("item replace entity @p " + (a.get(1).getAsString().equals("offhand") ? "weapon.offhand" : "weapon.mainhand") + " with " + a.get(2).getAsString());
				server.runOnServer(s -> player(s).getInventory().setSelectedSlot(0));
				sp.getConnection().waitForClientboundPackets();
			}
			case "wear_set" -> wearSet(ctx, a.get(1).getAsString());
			case "fly" -> server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			});
			case "inventory" -> inventory(ctx, a.get(1).getAsString());
			case "close" -> closeScreen(ctx);
			case "use_down" -> {
				ctx.getInput().holdKey(o -> o.keyUse);
				useHeld = true;
			}
			case "use_up" -> {
				ctx.getInput().releaseKey(o -> o.keyUse);
				useHeld = false;
			}
			case "attack" -> ctx.getInput().pressKey(o -> o.keyAttack);
			case "client_set" -> clientSet(ctx, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3));
			case "player_cmd" -> {
				String c = a.get(1).getAsString();
				ctx.runOnClient(mc -> mc.player.connection.sendCommand(c));
				sp.getConnection().waitForServerboundPackets();
				sp.getConnection().waitForClientboundPackets();
			}
			case "wait_label" -> waitLabel(ctx, a.get(1).getAsInt(), a.size() > 2 ? a.get(2).getAsString() : "");
			case "wait_label_clear" -> waitLabelClear(ctx, a.get(1).getAsInt());
			case "mannequin" -> mannequin(ctx, a.get(1).getAsFloat());
			case "give_named" -> giveNamed(ctx, a.get(1).getAsJsonArray());
			case "hover_named" -> hoverNamed(ctx, a.get(1).getAsString(), a.size() > 2 && a.get(2).getAsBoolean());
			case "sites" -> sites(ctx, a.size() > 1 ? a.get(1).getAsInt() : 80);
			case "auto" -> auto(ctx, a.get(1).getAsString());
			case "shot" -> shot(ctx, a.get(1).getAsString());
			case "logcheck" -> logcheck(a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString());
			case "manual" -> {
				for (JsonElement e : scene.getAsJsonArray("shots")) {
					verdict(e.getAsJsonObject().get("file").getAsString(), "manual", "not automatable: " + a.get(1).getAsString());
				}
				if (scene.getAsJsonArray("shots").isEmpty()) {
					manual++;
					LOG.info("[shots] MANUAL scene {} ({} | {}): {}", scene.get("n"), scene.get("lane").getAsString(), scene.get("item").getAsString(), a.get(1).getAsString());
				}
			}
			default -> throw new SceneFail("unknown step " + op);
		}
	}

	private void reset(final ClientGameTestContext ctx, final String mode) {
		cleanUp(ctx);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel l = (ServerLevel) p.level();
			for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(p.blockPosition()).inflate(96), x -> !(x instanceof ServerPlayer))) {
				e.discard();
			}
		});
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		server.runCommand("gamemode " + mode + " @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().setSelectedSlot(0);
			p.setHealth(p.getMaxHealth());
			boolean fly = p.getAbilities().instabuild || p.isSpectator();
			p.getAbilities().flying = fly && !flatWorld;
			p.onUpdateAbilities();
		});
		if (flatWorld) {
			server.runCommand(String.format(Locale.ROOT, "tp @p 0.5 %d 0.5 0 0", groundY));
		}
		sp.getConnection().waitForClientboundPackets();
		ctx.waitTicks(2);
	}

	/** Leaves the client neutral between scenes: no screen, keys up, first person, HUD on, F3 off, chat hidden, subtitles off. */
	private void cleanUp(final ClientGameTestContext ctx) {
		if (useHeld) {
			ctx.getInput().releaseKey(o -> o.keyUse);
			useHeld = false;
		}
		closeScreen(ctx);
		ctx.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.debugEntries.setOverlayVisible(false);
			mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			mc.options.showSubtitles().set(false);
		});
	}

	private void gui(final ClientGameTestContext ctx, final boolean shown) {
		ctx.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == shown) {
				mc.gui.hud.toggle();
			}
		});
	}

	private void chunks(final ClientGameTestContext ctx) {
		try {
			sp.getConnection().waitForChunksRender();
		} catch (Throwable t) {
			LOG.warn("[shots] chunks did not finish rendering: {}", t.toString());
		}
	}

	private void tp(final ClientGameTestContext ctx, final JsonArray a) {
		String base = a.get(1).getAsString();
		double x = a.get(2).getAsDouble(), y = a.get(3).getAsDouble(), z = a.get(4).getAsDouble();
		float yaw = a.get(5).getAsFloat(), pitch = a.get(6).getAsFloat();
		if (base.equals("anchor")) {
			if (anchor == null) {
				throw new SceneFail("tp relative to C before a successful locate");
			}
			x += anchor[0] + 0.5;
			y += anchor[1];
			z += anchor[2] + 0.5;
		} else if (base.equals("stage")) {
			x += 0.5;
			y += groundY;
			z += 0.5;
		}
		String dim = base.equals("anchor") ? anchorDim : "minecraft:overworld";
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @p %.2f %.2f %.2f %.1f %.1f", dim, x, y, z, yaw, pitch));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			if (p.getAbilities().mayfly) {
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			}
		});
		sp.getConnection().waitForClientboundPackets();
	}

	/** The camera on the lowest ground `dist` out from C (8 directions), at eye height, facing C (the "downhill side" views). */
	private void tpDownhill(final ClientGameTestContext ctx, final double dist) {
		if (anchor == null) {
			throw new SceneFail("downhill view before a successful locate");
		}
		int[] c = anchor;
		String dim = anchorDim;
		double[] pose = server.computeOnServer(s -> {
			ServerLevel l = s.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
			if (l == null) {
				return null;
			}
			double[] best = null;
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int x = (int) Math.floor(c[0] + 0.5 + Math.cos(a) * dist), z = (int) Math.floor(c[2] + 0.5 + Math.sin(a) * dist);
				l.getChunk(x >> 4, z >> 4);
				int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (best == null || y < best[1]) {
					best = new double[] {x + 0.5, y, z + 0.5};
				}
			}
			double dx = c[0] + 0.5 - best[0], dz = c[2] + 0.5 - best[2], dy = c[1] + 4 - (best[1] + 1.62);
			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
			return new double[] {best[0], best[1], best[2], yaw, pitch};
		});
		if (pose == null) {
			throw new SceneFail("dimension " + dim + " is not loaded");
		}
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @p %.2f %.2f %.2f %.1f %.1f", dim, pose[0], pose[1], pose[2], pose[3], pose[4]));
		sp.getConnection().waitForClientboundPackets();
	}

	// ------------------------------------------------------------------ worldgen anchors
	private void locate(final ClientGameTestContext ctx, final String what, final boolean near) {
		int[] from = near && anchor != null ? anchor : new int[] {0, 0, 0};
		String fromDim = near ? anchorDim : null;
		Object[] got = server.computeOnServer(s -> findStructure(s, what, new BlockPos(from[0], 0, from[2]), fromDim));
		if (got == null) {
			throw new SceneFail(what + " could not be located within 100 chunks in any dimension that can hold it");
		}
		anchor = (int[]) got[0];
		anchorDim = (String) got[1];
		LOG.info("[shots] located {} in {} at C = {} {} {}", what, anchorDim, anchor[0], anchor[1], anchor[2]);
	}

	/** {x, floor, z} of the nearest start; the floor is the most common surface height 10..22 blocks around C (the yard pad). */
	private static Object[] findStructure(final MinecraftServer s, final String what, final BlockPos from, final String onlyDim) {
		var reg = s.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		HolderSet<Structure> set;
		if (what.startsWith("#")) {
			List<Holder<Structure>> hs = new ArrayList<>();
			reg.getTagOrEmpty(TagKey.create(Registries.STRUCTURE, Identifier.parse(what.substring(1)))).forEach(hs::add);
			if (hs.isEmpty()) {
				return null;
			}
			set = HolderSet.direct(hs);
		} else {
			Holder.Reference<Structure> h = reg.get(Identifier.parse(what)).orElse(null);
			if (h == null) {
				return null;
			}
			set = HolderSet.direct(h);
		}
		List<ServerLevel> levels = new ArrayList<>();
		levels.add(s.overworld());
		for (ServerLevel l : s.getAllLevels()) {
			if (l != s.overworld()) {
				levels.add(l);
			}
		}
		for (ServerLevel l : levels) {
			if (onlyDim != null && !l.dimension().identifier().toString().equals(onlyDim)) {
				continue;
			}
			boolean any = false;
			for (Holder<Biome> b : l.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
				for (Holder<Structure> h : set) {
					any |= h.value().biomes().contains(b);
				}
			}
			if (!any) {
				continue;
			}
			try {
				var pair = l.getChunkSource().getGenerator().findNearestMapStructure(l, set, from, 100, false);
				if (pair != null) {
					return new Object[] {floorAt(l, pair.getFirst()), l.dimension().identifier().toString()};
				}
			} catch (Throwable t) {
				LOG.warn("[shots] locating {} in {} threw {}", what, l.dimension().identifier(), t.toString());
			}
		}
		return null;
	}

	private static int[] floorAt(final ServerLevel l, final BlockPos c) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				l.getChunk((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz);
			}
		}
		Map<Integer, Integer> hist = new HashMap<>();
		for (int dx = -22; dx <= 22; dx += 2) {
			for (int dz = -22; dz <= 22; dz += 2) {
				int r2 = dx * dx + dz * dz;
				if (r2 < 100 || r2 > 484) {
					continue;
				}
				hist.merge(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + dx, c.getZ() + dz), 1, Integer::sum);
			}
		}
		int floor = hist.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(l.getSeaLevel());
		if (l.dimensionType().hasCeiling()) {
			floor = l.getMinY() + 40;
			for (int y = Math.min(l.getMaxY() - 8, 120); y > l.getMinY() + 2; y--) {
				if (l.getBlockState(new BlockPos(c.getX(), y, c.getZ())).isAir() && !l.getBlockState(new BlockPos(c.getX(), y - 1, c.getZ())).isAir()) {
					floor = y;
					break;
				}
			}
		}
		return new int[] {c.getX(), floor, c.getZ()};
	}

	// ------------------------------------------------------------------ stage actors
	private void summon(final ClientGameTestContext ctx, final JsonArray a) {
		String type = a.get(1).getAsString();
		String nbt = a.size() > 2 ? a.get(2).getAsString() : "{}";
		double dx = a.size() > 4 ? a.get(3).getAsDouble() : 0, dz = a.size() > 4 ? a.get(4).getAsDouble() : 6;
		if (!BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.parse(type))) {
			throw new SceneFail("entity type " + type + " is not registered in this build");
		}
		double x = 0.5 + dx, y = groundY, z = 0.5 + dz;
		server.runCommand(String.format(Locale.ROOT, "summon %s %.2f %.2f %.2f %s", type, x, y, z, nbt));
		UUID id = server.computeOnServer(s -> {
			ServerLevel l = s.overworld();
			var t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type));
			Entity best = null;
			for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(x - 8, y - 4, z - 8, x + 8, y + 12, z + 8), q -> q.getType() == t)) {
				if (best == null || e.distanceToSqr(x, y, z) < best.distanceToSqr(x, y, z)) {
					best = e;
				}
			}
			if (best == null) {
				return null;
			}
			best.setYRot(180.0F);
			if (best instanceof LivingEntity le) {
				le.setYHeadRot(180.0F);
				le.setYBodyRot(180.0F);
			}
			return best.getUUID();
		});
		if (id == null) {
			throw new SceneFail("summon " + type + " produced no entity");
		}
		target = id;
		sp.getConnection().waitForClientboundPackets();
	}

	private String needTarget() {
		if (target == null) {
			throw new SceneFail("no summoned target");
		}
		return target.toString();
	}

	/** Puts the camera (the player) in front of the target, `angle` degrees off its facing, distance max(min, mult x height). */
	private void view(final ClientGameTestContext ctx, final double mult, final double min, final double angle) {
		UUID id = target;
		if (id == null) {
			throw new SceneFail("view before summon");
		}
		double[] pose = server.computeOnServer(s -> {
			Entity e = s.overworld().getEntity(id);
			if (e == null) {
				return null;
			}
			ServerPlayer p = player(s);
			double h = Math.max(0.5, e.getBbHeight());
			double d = Math.max(min, mult * h);
			double yaw = Math.toRadians(e.getYRot() + angle);
			double cx = e.getX() - Math.sin(yaw) * d, cz = e.getZ() + Math.cos(yaw) * d;
			boolean walk = !p.getAbilities().mayfly && !p.isSpectator();
			double eyeY = walk ? e.getY() + p.getEyeHeight() : e.getY() + Math.max(1.6, h * 0.6) + d * 0.12;
			double tx = e.getX() - cx, ty = e.getY() + h * 0.5 - eyeY, tz = e.getZ() - cz;
			float lookYaw = (float) (Math.toDegrees(Math.atan2(tz, tx)) - 90.0);
			float lookPitch = (float) (-Math.toDegrees(Math.atan2(ty, Math.sqrt(tx * tx + tz * tz))));
			return new double[] {cx, walk ? e.getY() : eyeY - p.getEyeHeight(), cz, lookYaw, lookPitch};
		});
		if (pose == null) {
			throw new SceneFail("target is gone before the camera was placed");
		}
		server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", pose[0], pose[1], pose[2], pose[3], pose[4]));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			if (p.getAbilities().mayfly) {
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
			}
		});
		sp.getConnection().waitForClientboundPackets();
	}

	private void lookTarget(final ClientGameTestContext ctx) {
		UUID id = target;
		if (id == null) {
			return;
		}
		ctx.runOnClient(mc -> {
			Entity e = null;
			for (Entity c : mc.level.entitiesForRendering()) {
				if (c != null && id.equals(c.getUUID())) {
					e = c;
				}
			}
			if (e == null) {
				return;
			}
			Vec3 eye = mc.player.getEyePosition();
			double dx = e.getX() - eye.x, dy = e.getY() + e.getBbHeight() * 0.5 - eye.y, dz = e.getZ() - eye.z;
			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.yRotO = yaw;
			mc.player.xRotO = pitch;
		});
	}

	private void hurtTo(final ClientGameTestContext ctx, final float frac) {
		UUID id = target;
		Boolean done = server.computeOnServer(s -> {
			if (s.overworld().getEntity(id) instanceof LivingEntity le) {
				le.setHealth(le.getMaxHealth() * frac + 2.0F);
				return true;
			}
			return false;
		});
		if (!Boolean.TRUE.equals(done)) {
			throw new SceneFail("hurt_to: target is not a living entity");
		}
		server.runCommand("damage " + id + " 2.5 minecraft:generic");
	}

	/** A mannequin wearing the player's gear, side-on to the camera (the worn set from the side). */
	private void mannequin(final ClientGameTestContext ctx, final float yawOff) {
		double z = 3.5;
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:mannequin 0.5 %d %.1f", groundY, z));
		UUID id = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			for (Entity e : s.overworld().getEntitiesOfClass(LivingEntity.class, new AABB(-2, groundY - 2, z - 2, 3, groundY + 3, z + 2),
				q -> BuiltInRegistries.ENTITY_TYPE.getKey(q.getType()).getPath().equals("mannequin"))) {
				LivingEntity m = (LivingEntity) e;
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					if (slot.isArmor() || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
						m.setItemSlot(slot, p.getItemBySlot(slot).copy());
					}
				}
				m.setYRot(yawOff);
				m.setYHeadRot(yawOff);
				m.setYBodyRot(yawOff);
				return m.getUUID();
			}
			return null;
		});
		if (id == null) {
			throw new SceneFail("mannequin could not be summoned");
		}
		target = id;
		server.runCommand(String.format(Locale.ROOT, "tp @p 0.5 %d 0.5 0 15", groundY));
		sp.getConnection().waitForClientboundPackets();
		ctx.waitTicks(3);
		lookTarget(ctx);
	}

	private void wearSet(final ClientGameTestContext ctx, final String prefix) {
		int n = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			int c = 0;
			Set<EquipmentSlot> done = new java.util.HashSet<>();
			for (Item it : BuiltInRegistries.ITEM) {
				Identifier key = BuiltInRegistries.ITEM.getKey(it);
				if (!key.getNamespace().equals("elsewhere") || !key.getPath().startsWith(prefix + "_")) {
					continue;
				}
				ItemStack st = new ItemStack(it);
				Equippable eq = st.get(DataComponents.EQUIPPABLE);
				if (eq == null || !eq.slot().isArmor() || !done.add(eq.slot())) {
					continue;
				}
				p.setItemSlot(eq.slot(), st);
				c++;
			}
			return c;
		});
		if (n == 0) {
			throw new SceneFail("no elsewhere:" + prefix + "_* armor pieces are registered");
		}
		sp.getConnection().waitForClientboundPackets();
	}

	// ------------------------------------------------------------------ inventory and tooltips
	private void inventory(final ClientGameTestContext ctx, final String which) {
		openInventory(ctx);
		if (which.equals("none")) {
			ctx.getInput().setCursorPos(2, 2);
			return;
		}
		int idx = switch (which) {
			case "mainhand" -> 36 + ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
			case "offhand", "weapon.offhand" -> 45;
			case "armor.head" -> 5;
			case "armor.chest" -> 6;
			case "armor.legs" -> 7;
			case "armor.feet" -> 8;
			default -> slotOf(ctx, which);
		};
		hoverSlot(ctx, idx);
	}

	private void openInventory(final ClientGameTestContext ctx) {
		boolean open = ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen);
		if (open) {
			return;
		}
		// the survival inventory layout (creative players get the creative screen instead)
		boolean creative = server.computeOnServer(s -> player(s).getAbilities().instabuild);
		if (creative) {
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 255 true");
			invSwitched = true;
			sp.getConnection().waitForClientboundPackets();
			ctx.waitTicks(2);
		}
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(3);
	}

	private void closeScreen(final ClientGameTestContext ctx) {
		boolean any = ctx.computeOnClient(mc -> mc.gui.screen() != null);
		if (any) {
			ctx.setScreen(() -> null);
		}
		if (invSwitched && server != null) {
			server.runCommand("gamemode creative @a");
			server.runCommand("effect clear @a minecraft:resistance");
			invSwitched = false;
		}
	}

	private int slotOf(final ClientGameTestContext ctx, final String item) {
		Integer i = ctx.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> scr)) {
				return null;
			}
			Item want = BuiltInRegistries.ITEM.getValue(Identifier.parse(item));
			for (Slot s : scr.getMenu().slots) {
				if (s.getItem().is(want)) {
					return s.index;
				}
			}
			return null;
		});
		if (i == null) {
			throw new SceneFail(item + " is not in the open inventory");
		}
		return i;
	}

	/** Moves the cursor onto menu slot `idx` and checks the screen's hovered slot (two cursor scalings, macOS retina safe). */
	private void hoverSlot(final ClientGameTestContext ctx, final int idx) {
		double[][] tries = ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> scr = (AbstractContainerScreen<?>) mc.gui.screen();
			Slot s = scr.getMenu().slots.get(idx);
			int left = intField(scr, "leftPos"), top = intField(scr, "topPos");
			double gx = left + s.x + 8, gy = top + s.y + 8;
			var w = mc.getWindow();
			double fx = (double) w.getScreenWidth() / w.getGuiScaledWidth(), fy = (double) w.getScreenHeight() / w.getGuiScaledHeight();
			double g = w.getGuiScale();
			return new double[][] {{gx * fx, gy * fy}, {gx * g, gy * g}, {gx * g / 2, gy * g / 2}};
		});
		for (double[] t : tries) {
			ctx.getInput().setCursorPos(t[0], t[1]);
			ctx.waitTicks(2);
			int hovered = ctx.computeOnClient(mc -> {
				try {
					Field f = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
					f.setAccessible(true);
					Slot h = (Slot) f.get(mc.gui.screen());
					return h == null ? -1 : h.index;
				} catch (ReflectiveOperationException | RuntimeException e) {
					return -2;
				}
			});
			if (hovered == idx || hovered == -2) {
				return;
			}
		}
		throw new SceneFail("cursor could not be placed over inventory slot " + idx);
	}

	private static int intField(final Object o, final String name) {
		try {
			Field f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.getInt(o);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Puts the items whose shown names match into hotbar slots 0..n (names are resolved on the client, in its language). */
	private void giveNamed(final ClientGameTestContext ctx, final JsonArray names) {
		List<String> want = new ArrayList<>();
		names.forEach(e -> want.add(e.getAsString()));
		Map<String, String> ids = ctx.computeOnClient(mc -> {
			Map<String, String> m = new LinkedHashMap<>();
			for (Item it : BuiltInRegistries.ITEM) {
				Identifier k = BuiltInRegistries.ITEM.getKey(it);
				if (!k.getNamespace().equals("elsewhere")) {
					continue;
				}
				String shown = new ItemStack(it).getHoverName().getString().toLowerCase(Locale.ROOT);
				for (String n : want) {
					if (shown.equals(n.toLowerCase(Locale.ROOT)) && !m.containsKey(n)) {
						m.put(n, k.toString());
					}
				}
			}
			return m;
		});
		int slot = 0;
		for (String n : want) {
			String id = ids.get(n);
			if (id == null) {
				continue;
			}
			server.runCommand("item replace entity @p hotbar." + slot + " with " + id);
			named.put(n, slot++);
		}
		sp.getConnection().waitForClientboundPackets();
		LOG.info("[shots] give_named: {} of {} resolved {}", ids.size(), want.size(), ids);
	}

	private void hoverNamed(final ClientGameTestContext ctx, final String name, final boolean optional) {
		Integer slot = named.get(name);
		if (slot == null) {
			if (optional) {
				label = "absent: " + name + " is not in this build (expected 'once it is in')";
				return;
			}
			throw new SceneFail("no item is shown as '" + name + "' in this build");
		}
		openInventory(ctx);
		hoverSlot(ctx, 36 + slot);
		label = "";
	}

	// ------------------------------------------------------------------ boss bar move labels
	private static Map<?, ?> barMap(final Minecraft mc) throws ReflectiveOperationException {
		Object ov = mc.gui.hud.getBossOverlay();
		Field f = ov.getClass().getDeclaredField("events");
		f.setAccessible(true);
		return (Map<?, ?>) f.get(ov);
	}

	private String moveLabel(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			try {
				for (Object v : barMap(mc).values()) {
					String n = ((net.minecraft.world.BossEvent) v).getName().getString();
					int i = n.indexOf(" · ");
					if (i >= 0) {
						return n.substring(i + 3);
					}
				}
			} catch (ReflectiveOperationException e) {
				return "";
			}
			return "";
		});
	}

	private void waitLabel(final ClientGameTestContext ctx, final int timeout, final String mustContain) {
		for (int t = 0; t < timeout; t++) {
			String l = moveLabel(ctx);
			if (!l.isEmpty() && (mustContain.isEmpty() || l.contains(mustContain))) {
				label = l;
				return;
			}
			if (t % 20 == 0) {
				lookTarget(ctx);
			}
			ctx.waitTick();
		}
		throw new SceneFail("no boss bar move label" + (mustContain.isEmpty() ? "" : " containing '" + mustContain + "'") + " within " + timeout / 20 + " s");
	}

	private void waitLabelClear(final ClientGameTestContext ctx, final int timeout) {
		for (int t = 0; t < timeout; t++) {
			if (moveLabel(ctx).isEmpty()) {
				return;
			}
			ctx.waitTick();
		}
		throw new SceneFail("move label '" + label + "' did not clear within " + timeout / 20 + " s");
	}

	// ------------------------------------------------------------------ misc
	private void clientSet(final ClientGameTestContext ctx, final String cls, final String field, final JsonElement v) {
		try {
			Class<?> c = Class.forName(cls, true, ShotRunner.class.getClassLoader());
			Field f = c.getDeclaredField(field);
			f.setAccessible(true);
			ctx.runOnClient(mc -> f.set(null, v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() ? (Object) v.getAsBoolean() : v.getAsString()));
		} catch (ReflectiveOperationException e) {
			throw new SceneFail("client_set " + cls + "." + field + ": " + e);
		}
	}

	/** Fallback for a line no family matched: the id decides (entity -> stage view, item -> held third person front). */
	private void auto(final ClientGameTestContext ctx, final String id) {
		Identifier k = Identifier.parse(id);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(k)) {
			JsonArray a = new JsonArray();
			a.add("summon");
			a.add(id);
			a.add("{NoAI:1b,PersistenceRequired:1b}");
			summon(ctx, a);
			view(ctx, 1.6, 5.0, 35.0);
			chunks(ctx);
			ctx.waitTicks(20);
		} else if (BuiltInRegistries.ITEM.containsKey(k)) {
			server.runCommand("item replace entity @p weapon.mainhand with " + id);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(12);
		} else {
			throw new SceneFail(id + " is neither an entity type nor an item in this build");
		}
	}

	/** Every Elsewhere structure (the release client test's sites section), two views each, as shots of this scene. */
	private void sites(final ClientGameTestContext ctx, final int max) {
		List<String> names = server.computeOnServer(s -> {
			List<String> l = new ArrayList<>();
			s.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
				if (h.key().identifier().getNamespace().equals("elsewhere")) {
					l.add(h.key().identifier().toString());
				}
			});
			l.sort(String::compareTo);
			return l;
		});
		int n = 0;
		for (String name : names) {
			if (n++ >= max) {
				break;
			}
			String safe = name.substring(name.indexOf(':') + 1);
			String where = server.computeOnServer(s -> goToStructure(s, name));
			String lane = scene.get("lane").getAsString();
			for (String side : new String[] {"a", "b"}) {
				String file = lane + "__site_" + safe + "_" + side + ".png";
				addDynamicEntry(file);
				if (where == null) {
					verdict(file, "fail", name + " could not be located within 60 chunks in any matching dimension");
					continue;
				}
				if (side.equals("a")) {
					ctx.waitTicks(25);
					chunks(ctx);
					ctx.waitTicks(15);
				} else {
					ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180.0F));
					ctx.waitTicks(8);
				}
				take(ctx, file, where);
			}
		}
	}

	private static String goToStructure(final MinecraftServer s, final String name) {
		var reg = s.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder.Reference<Structure> h = reg.get(Identifier.parse(name)).orElse(null);
		if (h == null) {
			return null;
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
				BlockPos c = pair.getFirst();
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						l.getChunk((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz);
					}
				}
				int x = c.getX() + 8, z = c.getZ() + 8;
				int y = l.dimensionType().hasCeiling() ? l.getMinY() + 40 : Math.max(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), l.getMinY() + 2) + 12;
				ServerPlayer p = player(s);
				p.teleportTo(l, x + 0.5, y, z + 0.5, Set.of(), 0.0F, 30.0F, true);
				return l.dimension().identifier() + " " + new BlockPos(x, y, z).toShortString();
			} catch (Throwable t) {
				LOG.warn("[shots] locating {} in {} threw {}", name, l.dimension().identifier(), t.toString());
			}
		}
		return null;
	}

	/** C from a line the mod printed (for example a self-test anchor): the first match's three groups are x, y, z. */
	private void anchorFromLog(final String regex, final String dim) {
		Path log = FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
		try {
			var m = Pattern.compile(regex).matcher(new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
			if (!m.find()) {
				throw new SceneFail("no log line matches /" + regex + "/ (the self-test anchor was not printed in this session)");
			}
			anchor = new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
			anchorDim = dim;
		} catch (IOException e) {
			throw new SceneFail("could not read " + log + ": " + e);
		}
	}

	/** Greps the client's own latest.log: lines containing `must` and matching `bad` are failures (0 lines = ok). */
	private void logcheck(final String slug, final String must, final String bad) {
		String file = scene.get("lane").getAsString() + "__" + slug + ".txt";
		Path log = FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
		Pattern p = Pattern.compile(bad, Pattern.CASE_INSENSITIVE);
		List<String> hits = new ArrayList<>();
		try {
			for (String line : new String(Files.readAllBytes(log), StandardCharsets.UTF_8).split("\n")) {
				if (line.contains(must) && p.matcher(line).find()) {
					hits.add(line);
				}
			}
			Files.writeString(out.resolve(file), "grep " + log + " for '" + must + "' with /" + bad + "/: " + hits.size() + " lines\n" + String.join("\n", hits) + "\n");
			verdict(file, hits.isEmpty() ? "ok" : "fail", hits.size() + " matching log lines");
		} catch (IOException e) {
			verdict(file, "fail", "could not read " + log + ": " + e);
		}
	}

	// ------------------------------------------------------------------ shots and the index
	private void shot(final ClientGameTestContext ctx, final String slug) {
		take(ctx, scene.get("lane").getAsString() + "__" + slug + ".png", label.isEmpty() ? "" : "move label: " + label);
	}

	private void take(final ClientGameTestContext ctx, final String file, final String detail) {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
				mc.gui.hud.getChat().clearMessages(false);
			}
		});
		ctx.waitTick();
		String base = file.substring(0, file.lastIndexOf('.'));
		Path p = ctx.takeScreenshot(TestScreenshotOptions.of(base).withDestinationDir(out).disableCounterPrefix());
		verdict(file, Files.exists(p) ? "ok" : "fail", detail.isEmpty() ? "" : detail);
	}

	private void verdict(final String file, final String status, final String detail) {
		JsonObject e = entries.get(file);
		if (e == null) {
			e = addDynamicEntry(file);
		}
		e.addProperty("status", status);
		if (detail != null && !detail.isEmpty()) {
			e.addProperty("detail", detail);
		} else {
			e.remove("detail");
		}
		e.addProperty("taken", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
		switch (status) {
			case "ok" -> ok++;
			case "manual" -> manual++;
			default -> fail++;
		}
		LOG.info("[shots] {} {}: {}", status.toUpperCase(Locale.ROOT), file, detail == null ? "" : detail);
	}

	/** Index entries (lane -> item -> [shots]) for one scene; existing entries are reused so a partial run keeps the others. */
	private void indexEntries(final JsonObject s) {
		JsonObject lanes = index.getAsJsonObject("lanes");
		String lane = s.get("lane").getAsString(), item = s.get("item").getAsString();
		if (!lanes.has(lane)) {
			lanes.add(lane, new JsonObject());
		}
		JsonObject items = lanes.getAsJsonObject(lane);
		if (!items.has(item)) {
			items.add(item, new JsonArray());
		}
		JsonArray arr = items.getAsJsonArray(item);
		for (JsonElement sh : s.getAsJsonArray("shots")) {
			String file = sh.getAsJsonObject().get("file").getAsString();
			JsonObject found = null;
			for (JsonElement e : arr) {
				if (e.getAsJsonObject().get("file").getAsString().equals(file)) {
					found = e.getAsJsonObject();
				}
			}
			if (found == null) {
				found = new JsonObject();
				found.addProperty("file", file);
				found.addProperty("scene", s.get("n").getAsInt());
				found.addProperty("line", s.get("line").getAsInt());
				found.addProperty("check", s.get("check").getAsString());
				arr.add(found);
			}
			found.addProperty("status", "pending");
			found.remove("taken");
			found.remove("detail");
			entries.put(file, found);
		}
	}

	private JsonObject addDynamicEntry(final String file) {
		JsonObject lanes = index.getAsJsonObject("lanes");
		String lane = scene.get("lane").getAsString(), item = scene.get("item").getAsString();
		if (!lanes.has(lane)) {
			lanes.add(lane, new JsonObject());
		}
		if (!lanes.getAsJsonObject(lane).has(item)) {
			lanes.getAsJsonObject(lane).add(item, new JsonArray());
		}
		JsonObject e = entries.get(file);
		if (e == null) {
			e = new JsonObject();
			e.addProperty("file", file);
			e.addProperty("scene", scene.get("n").getAsInt());
			e.addProperty("line", scene.get("line").getAsInt());
			e.addProperty("check", scene.get("check").getAsString());
			e.addProperty("status", "pending");
			lanes.getAsJsonObject(lane).getAsJsonArray(item).add(e);
			entries.put(file, e);
		}
		return e;
	}

	private synchronized void saveIndex() {
		if (index == null || indexPath == null) {
			return;
		}
		try {
			index.addProperty("run", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
			JsonObject totals = new JsonObject();
			totals.addProperty("ok", ok);
			totals.addProperty("fail", fail);
			totals.addProperty("manual", manual);
			index.add("totals", totals);
			Path tmp = indexPath.resolveSibling("index.json.tmp");
			Files.writeString(tmp, GSON.toJson(index), StandardCharsets.UTF_8);
			Files.move(tmp, indexPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.error("[shots] could not write {}", indexPath, e);
		}
	}

	private void startWatchdog() {
		int minutes = Integer.getInteger("elsewhereqa.watchdog", 0);
		if (minutes <= 0) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(minutes * 60_000L);
			} catch (InterruptedException e) {
				return;
			}
			LOG.error("[shots] watchdog: {} minutes passed, writing the index and halting", minutes);
			LOG.info("[shots] TOTAL {} ok, {} fail, {} manual (watchdog)", ok, fail, manual);
			saveIndex();
			Runtime.getRuntime().halt(3);
		}, "shots-watchdog");
		t.setDaemon(true);
		t.start();
	}

	private static ServerPlayer player(final MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	/** -PqaShotOnly: comma list of scene numbers, ranges (3-9) and lane names; empty = all. */
	private static final class Selection {
		private final List<int[]> ranges = new ArrayList<>();
		private final List<String> lanes = new ArrayList<>();

		Selection(final String spec) {
			for (String t : spec.split(",")) {
				t = t.trim();
				if (t.isEmpty()) {
					continue;
				}
				if (t.matches("\\d+")) {
					ranges.add(new int[] {Integer.parseInt(t), Integer.parseInt(t)});
				} else if (t.matches("\\d+-\\d+")) {
					String[] p = t.split("-");
					ranges.add(new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])});
				} else {
					lanes.add(t);
				}
			}
		}

		boolean wants(final JsonObject s) {
			if (ranges.isEmpty() && lanes.isEmpty()) {
				return true;
			}
			int n = s.get("n").getAsInt();
			for (int[] r : ranges) {
				if (n >= r[0] && n <= r[1]) {
					return true;
				}
			}
			return lanes.contains(s.get("lane").getAsString());
		}
	}
}
