package dev.elsewhereqa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * s-clientload (3.0.0): the client-relevant load of every big 3.0 scene, measured on a dedicated server with the merged jar.
 * Run with {@code -Delsewhereqa.suite=clientload}. For each scenario a measuring player ({@link ClientLoadProbe}) stands where a real player
 * would watch from; for the measuring window it records what that client would be sent and would have to draw:
 * <ul>
 * <li>entities in view by kind (armored soldiers, mounts, projectiles, ranks, bosses, NPCs, other mobs), their render passes (body + armor
 * pieces + trims + held items) and the soldiers' distance bands (the client LOD bands 24/48/96);</li>
 * <li>clientbound packets and bytes per second by bucket (entity movement, entity state, spawns, particles, sounds, custom payloads, blocks,
 * chunks), bytes as encoded by the real play protocol (before compression);</li>
 * <li>server-sent particles per second by type, plus the client-side particles that crit and poof events trigger (estimate);</li>
 * <li>block entities in view distance and within 64 blocks (the block-entity render range);</li>
 * <li>the server's own MSPT during the window.</li>
 * </ul>
 * Results: {@code clientload.json} in the run directory (written after every scenario), and one QA line per scenario.
 */
final class ClientLoad implements QAServer.Step {
	static final List<String> LOG_DISCONNECT = new ArrayList<>();
	static final String KP = "dev.elsewhere.kingdom.";
	static final int VIEW = 10;
	static long tickStart;
	static final List<Long> tickNs = new ArrayList<>();
	static boolean timing;

	static void plan(final MinecraftServer server) {
		ServerTickEvents.START_SERVER_TICK.register(s -> tickStart = System.nanoTime());
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (timing) {
				tickNs.add(System.nanoTime() - tickStart);
			}
		});
		QAServer.STEPS.add(new ClientLoad());
	}

	// ------------------------------------------------------------------ scenarios
	interface Action {
		String run(ClientLoad cl, MinecraftServer s) throws Exception;
	}

	static final class Scn {
		final String id, title;
		String how = "";
		int dist = 32, warm = 100, settle = 100, measure = 600;
		double height = 2.0;
		GameType mode = GameType.CREATIVE;
		String where = "arena";
		Action start = (cl, s) -> "";
		Action tick = null;
		Action stop = (cl, s) -> "";

		Scn(final String id, final String title) {
			this.id = id;
			this.title = title;
		}
	}

	final List<Scn> scns = new ArrayList<>();
	final JsonArray results = new JsonArray();
	int idx = -1, phase, t;
	ServerLevel lvl;
	BlockPos arena, anchor;
	ServerPlayer probe;
	ClientLoadProbe conn;
	Meter meter;
	FakePlayer caster;
	final List<Entity> spawned = new ArrayList<>();
	Vec3 eye, look;
	long t0;

	ClientLoad() {
		battle("b15", "15 v 15 field battle (Aldermark v Hrafnholt), watched from 32 blocks", "hrafnholt", "aldermark", 15, 32, 500);
		battle("b32", "32 v 32 epic caps (Hrafnholt v Aldermark), watched from 32 blocks", "hrafnholt", "aldermark", 32, 32, 500);
		battle("b24", "24 v 24 fallback caps (Hrafnholt v Aldermark), watched from 32 blocks", "hrafnholt", "aldermark", 24, 32, 500);
		battle("w150h", "150 v 150 waves, live front (Hrafnholt v Aldermark), watched from 40 blocks", "hrafnholt", "aldermark", 150, 40, 900);
		battle("w150a", "150 v 150 waves, live front (Azmar v Aldermark), watched from 40 blocks", "azmar", "aldermark", 150, 40, 900);
		battle("w150m", "150 v 150 waves, live front (Mirewick v Aldermark), watched from 40 blocks", "mirewick", "aldermark", 150, 40, 900);
		battle("r32a", "Rival army: Azmar 32 v 32 against Aldermark, from 32 blocks", "azmar", "aldermark", 32, 32, 500);
		battle("r32m", "Rival army: Mirewick 32 v 32 against Aldermark, from 32 blocks", "mirewick", "aldermark", 32, 32, 500);
		battle("r32h", "Rival army: Hrafnholt 32 v 32 against Mirewick, from 32 blocks", "hrafnholt", "mirewick", 32, 32, 500);

		Scn idle = new Scn("idle", "Idle arena (B0c): the same spot, nothing staged");
		idle.measure = 300;
		scns.add(0, idle);

		Scn b0a = new Scn("b0a", "Vanilla baseline (B0a): 80 zombies + 20 skeletons chasing the player");
		b0a.dist = 0;
		b0a.mode = GameType.SURVIVAL;
		b0a.measure = 500;
		b0a.start = (cl, s) -> {
			cl.cmd(s, "time set 18000");
			for (int i = 0; i < 100; i++) {
				EntityType<?> ty = i < 80 ? net.minecraft.world.entity.EntityTypes.ZOMBIE : net.minecraft.world.entity.EntityTypes.SKELETON;
				double a = i * 2 * Math.PI / 100, r = 10 + (i % 5) * 3;
				cl.spawn(ty, cl.anchor.getX() + 0.5 + Math.cos(a) * r, cl.anchor.getZ() + 0.5 + Math.sin(a) * r);
			}
			return "100 vanilla mobs at 10-22 blocks, night, the measuring player in survival (invulnerable)";
		};
		b0a.stop = (cl, s) -> {
			cl.cmd(s, "time set 6000");
			return "";
		};
		scns.add(1, b0a);

		Scn siege = new Scn("siege", "Full siege of a village (Aldermark siege force, villagers, burning plan), watched from the edge");
		siege.where = "village";
		siege.dist = 24;
		siege.height = 6;
		siege.warm = 300;
		siege.settle = 200;
		siege.measure = 900;
		siege.start = ClientLoad::startSiege;
		siege.stop = (cl, s) -> {
			cl.cmd(s, "kingdom armies battle stop");
			return "";
		};
		scns.add(siege);

		Scn market = new Scn("market", "Capital at market hour (Aldermark realm centre, garrison + court NPCs + villagers), standing in the town");
		market.where = "capital";
		market.dist = 12;
		market.warm = 600;
		market.settle = 100;
		market.measure = 600;
		market.start = (cl, s) -> {
			cl.cmd(s, "time set 6000");
			return "realm centre " + cl.anchor.toShortString() + ", noon (market hour)";
		};
		scns.add(market);

		Scn castle = new Scn("castle", "Castle view: Aldermark castle from 64 blocks, raised viewpoint");
		castle.where = "capital";
		castle.dist = 64;
		castle.height = 14;
		castle.warm = 400;
		castle.settle = 40;
		castle.measure = 300;
		castle.start = (cl, s) -> "castle at " + cl.anchor.toShortString();
		scns.add(castle);

		Scn gear = new Scn("gear3", "Gear 3.0 spell and summon fight: a caster uses every staff, horn, wand and summon item on 10 husks");
		gear.dist = 10;
		gear.measure = 600;
		gear.start = ClientLoad::startGear;
		gear.tick = ClientLoad::tickGear;
		gear.stop = (cl, s) -> {
			if (cl.caster != null) {
				cl.caster.discard();
			}
			return "";
		};
		scns.add(gear);

		Scn boss = new Scn("boss", "Boss with telegraphs, fighting the watching player at 16 blocks");
		boss.dist = 16;
		boss.mode = GameType.SURVIVAL;
		boss.measure = 800;
		boss.start = ClientLoad::startBoss;
		scns.add(boss);
	}

	void battle(final String id, final String title, final String a, final String d, final int heads, final int dist, final int measure) {
		Scn b = new Scn(id, title);
		b.dist = dist;
		b.measure = measure;
		b.settle = heads >= 100 ? 300 : 160;
		b.start = (cl, s) -> {
			int r = cl.cmd(s, "kingdom armies battle " + a + " " + d + " " + heads);
			return "/kingdom armies battle " + a + " " + d + " " + heads + " -> " + r;
		};
		b.stop = (cl, s) -> {
			cl.cmd(s, "kingdom armies battle stop");
			return "";
		};
		scns.add(b);
	}

	// ------------------------------------------------------------------ step
	@Override
	public String name() {
		return "client load";
	}

	@Override
	public boolean tick(final MinecraftServer server) throws Exception {
		if (lvl == null) {
			lvl = server.overworld();
			cmd(server, "gamerule spawn_mobs false");
			cmd(server, "gamerule advance_time false");
			cmd(server, "gamerule advance_weather false");
			cmd(server, "time set 6000");
			cmd(server, "weather clear");
			arena = prepareArena(new BlockPos(6000, 0, 6000));
			QAServer.LOG.info("[clientload] arena at {}", arena.toShortString());
			idx = 0;
			phase = 0;
		}
		if (conn != null && conn.ackDue) {
			conn.ackDue = false;
			conn.chunkSender.onChunkBatchReceivedByClient(64.0F);
		}
		if (idx >= scns.size()) {
			write();
			QAServer.record("PASS", "client load", "measured " + scns.size() + " scenarios; results in clientload.json");
			return true;
		}
		Scn s = scns.get(idx);
		t++;
		try {
			switch (phase) {
				case 0 -> {
					anchor = switch (s.where) {
						case "village" -> village(server);
						case "capital" -> capital(server);
						default -> arena;
					};
					if (anchor == null) {
						fail(s, "no " + s.where + " in this world");
						return false;
					}
					placeProbe(server, s);
					t = 0;
					phase = 1;
				}
				case 1 -> {
					if (t >= s.warm && (meter.chunks >= expectedChunks() * 0.85 || t >= s.warm * 3)) {
						s.how = s.start.run(this, server);
						QAServer.LOG.info("[clientload] {} started after {} warm ticks ({} chunks sent): {}", s.id, t, meter.chunks, s.how);
						t = 0;
						phase = 2;
					}
				}
				case 2 -> {
					if (s.tick != null) {
						s.tick.run(this, server);
					}
					if (t >= s.settle) {
						meter.reset();
						meter.on = true;
						tickNs.clear();
						timing = true;
						t0 = System.nanoTime();
						t = 0;
						phase = 3;
					}
				}
				case 3 -> {
					if (s.tick != null) {
						s.tick.run(this, server);
					}
					if (t % 20 == 0) {
						meter.sample(eye, look);
					}
					if (t >= s.measure) {
						timing = false;
						meter.on = false;
						results.add(result(s, t));
						s.stop.run(this, server);
						teardown(server);
						write();
						idx++;
						phase = 0;
						t = 0;
					}
				}
				default -> phase = 0;
			}
		} catch (Throwable e) {
			QAServer.LOG.error("[clientload] scenario {} threw", s.id, e);
			fail(s, "threw " + e);
		}
		return false;
	}

	void fail(final Scn s, final String why) {
		QAServer.record("FAIL", "client load " + s.id, why);
		JsonObject o = new JsonObject();
		o.addProperty("id", s.id);
		o.addProperty("title", s.title);
		o.addProperty("error", why);
		results.add(o);
		timing = false;
		try {
			s.stop.run(this, lvl.getServer());
		} catch (Throwable ignored) {
			// best effort
		}
		teardown(lvl.getServer());
		idx++;
		phase = 0;
		t = 0;
	}

	int expectedChunks() {
		return (2 * VIEW + 1) * (2 * VIEW + 1);
	}

	// ------------------------------------------------------------------ the measuring player
	void placeProbe(final MinecraftServer server, final Scn s) {
		BlockPos at = anchor.offset(0, 0, -s.dist);
		lvl.getChunk(at);
		int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		double ey = y + s.height;
		GameProfile gp = new GameProfile(UUID.nameUUIDFromBytes(("clientload-" + s.id).getBytes(StandardCharsets.UTF_8)), "LoadProbe");
		ClientInformation info = new ClientInformation("en_us", VIEW, ChatVisiblity.FULL, true, 0x7F, HumanoidArm.RIGHT, false, true, ParticleStatus.ALL);
		probe = new ServerPlayer(server, lvl, gp, info);
		conn = new ClientLoadProbe(server, probe);
		meter = new Meter(probe);
		conn.meter(meter);
		float pitch = s.dist == 0 ? 0F : (float) Math.toDegrees(Math.atan2(s.height + 1, Math.max(1, s.dist)));
		probe.snapTo(at.getX() + 0.5, ey, at.getZ() + 0.5, 0.0F, Math.min(30F, pitch));
		lvl.addNewPlayer(probe);
		QAServer.listPlayer(server, probe, true);
		probe.gameMode.changeGameModeForPlayer(s.mode);
		probe.getAbilities().flying = s.mode == GameType.CREATIVE;
		eye = probe.getEyePosition();
		look = probe.getLookAngle();
		if (s.dist == 0) {
			anchor = BlockPos.containing(probe.position());
		}
	}

	void teardown(final MinecraftServer server) {
		for (Entity e : spawned) {
			if (e != null && !e.isRemoved()) {
				e.discard();
			}
		}
		spawned.clear();
		if (anchor != null && (idx >= scns.size() || scns.get(idx).where.equals("arena") || scns.get(idx).where.equals("village"))) {
			for (Entity e : lvl.getEntitiesOfClass(Entity.class, new AABB(anchor).inflate(96), e -> !(e instanceof ServerPlayer))) {
				String ns = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace();
				if (ns.equals("elsewhere") || e instanceof Projectile || e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb) {
					e.discard();
				}
			}
		}
		if (probe != null) {
			conn.meter(null);
			lvl.removePlayerImmediately(probe, Entity.RemovalReason.DISCARDED);
			QAServer.listPlayer(server, probe, false);
			probe = null;
			conn = null;
		}
	}

	int cmd(final MinecraftServer s, final String c) {
		CommandSourceStack src = s.createCommandSourceStack().withLevel(lvl).withPosition(anchor == null ? Vec3.ZERO : Vec3.atBottomCenterOf(anchor))
			.withSuppressedOutput().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER);
		return QAServer.runCommand(s, src, c);
	}

	Entity spawn(final EntityType<?> ty, final double x, final double z) {
		Entity e = ty.create(lvl, EntitySpawnReason.COMMAND);
		if (e == null) {
			return null;
		}
		int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		e.snapTo(x, y, z, 0, 0);
		if (e instanceof Mob m) {
			m.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.COMMAND, null);
			m.setPersistenceRequired();
		}
		lvl.addFreshEntity(e);
		spawned.add(e);
		return e;
	}

	// ------------------------------------------------------------------ places
	BlockPos prepareArena(final BlockPos c0) {
		lvl.getChunk(c0);
		int y = Math.max(lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c0.getX(), c0.getZ()), lvl.getSeaLevel()) + 1;
		BlockPos c = new BlockPos(c0.getX(), y, c0.getZ());
		for (int dx = -60; dx <= 60; dx++) {
			for (int dz = -60; dz <= 60; dz++) {
				for (int dy = 0; dy < 14; dy++) {
					BlockPos q = c.offset(dx, dy, dz);
					if (!lvl.getBlockState(q).isAir()) {
						lvl.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				lvl.setBlock(c.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				for (int dy = -4; dy < -1; dy++) {
					if (lvl.getBlockState(c.offset(dx, dy, dz)).isAir() || !lvl.getFluidState(c.offset(dx, dy, dz)).isEmpty()) {
						lvl.setBlock(c.offset(dx, dy, dz), Blocks.DIRT.defaultBlockState(), 2);
					}
				}
			}
		}
		return c;
	}

	BlockPos villagePos;

	BlockPos village(final MinecraftServer s) {
		if (villagePos == null) {
			BlockPos v = lvl.findNearestMapStructure(StructureTags.VILLAGE, new BlockPos(0, 64, 0), 100, false);
			if (v == null) {
				return null;
			}
			lvl.getChunk(v);
			villagePos = new BlockPos(v.getX(), lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, v.getX(), v.getZ()), v.getZ());
		}
		return villagePos;
	}

	BlockPos capitalPos;

	BlockPos capital(final MinecraftServer s) throws Exception {
		if (capitalPos == null) {
			Class<?> api = ReleaseSuite.cls("dev.elsewhere.kingdom_court.CourtApi");
			if (api == null) {
				return null;
			}
			Object p = ReleaseSuite.call(null, api, "realmCenter", s, Identifier.fromNamespaceAndPath("elsewhere", "aldermark"));
			if (p == null) {
				p = ReleaseSuite.call(null, api, "realmCenter", s);
			}
			if (!(p instanceof BlockPos bp)) {
				return null;
			}
			lvl.getChunk(bp);
			capitalPos = new BlockPos(bp.getX(), lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ()), bp.getZ());
		}
		return capitalPos;
	}

	// ------------------------------------------------------------------ scenario actions
	static String startSiege(final ClientLoad cl, final MinecraftServer s) throws Exception {
		int r = cl.cmd(s, "aldermark conquest on");
		int st = cl.cmd(s, "aldermark siege");
		if (st > 0) {
			return "/aldermark siege at the village " + cl.anchor.toShortString() + " (conquest on " + r + ")";
		}
		// the ledger may not know this village yet: the kingdom's own siege force at the village, through the wave battle (proxy)
		int b = cl.cmd(s, "kingdom armies battle aldermark hrafnholt 60");
		return "village " + cl.anchor.toShortString() + " not in the Aldermark ledger (siege " + st + "); siege-sized wave battle at the village instead: " + b;
	}

	static final String[] GEAR_ITEMS = {"kitestring_staff", "brassback_key", "rootcall_staff", "sporebloom_staff", "sergeants_horn", "avalanche_horn",
		"hymnal_horn", "sunlance_staff", "stormcoil_staff", "storm_coil", "sculk_resonator", "amethyst_wand", "emberfall_rod", "frostwake_scepter",
		"ninefold_rod", "sun_orb", "mirrorglass_prism", "thornlash", "war_yak_horn", "wolfcall_horn", "herdcaller_pennant", "echo_reliquary",
		"choirlight_censer", "will_o_lantern", "mothlight_staff", "ascension_shard", "tidebrass_dredge", "lantern_sickle", "glimmer_lantern",
		"stone_totem", "ember_totem", "frost_totem", "storm_totem", "hurlbat", "gale_sling", "prism_bow", "aphelion_starpiercer"};
	final List<ItemStack> gearStacks = new ArrayList<>();
	final List<String> gearUsed = new ArrayList<>();
	int gearNext;

	static String startGear(final ClientLoad cl, final MinecraftServer s) {
		for (int i = 0; i < 10; i++) {
			cl.spawn(net.minecraft.world.entity.EntityTypes.HUSK, cl.anchor.getX() + 0.5 + (i % 5 - 2) * 2.0, cl.anchor.getZ() + 6.5 + (i / 5) * 2.0);
		}
		cl.caster = QAServer.fake(cl.lvl, "LoadCaster");
		cl.caster.snapTo(cl.anchor.getX() + 0.5, cl.anchor.getY(), cl.anchor.getZ() + 0.5, 0, 0);
		if (!cl.lvl.players().contains(cl.caster)) {
			cl.lvl.addNewPlayer(cl.caster);
		}
		cl.caster.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
		cl.gearStacks.clear();
		List<String> found = new ArrayList<>();
		for (String id : GEAR_ITEMS) {
			Identifier key = Identifier.fromNamespaceAndPath("elsewhere", id);
			if (BuiltInRegistries.ITEM.containsKey(key)) {
				cl.gearStacks.add(new ItemStack(BuiltInRegistries.ITEM.getValue(key)));
				found.add(id);
			}
		}
		return "caster (creative, so no Glimmer or cooldown gate) at the husks, items in this jar: " + found;
	}

	static String tickGear(final ClientLoad cl, final MinecraftServer s) {
		if (cl.caster == null || cl.gearStacks.isEmpty()) {
			return "";
		}
		if (cl.t % 15 == 0) {
			ItemStack st = cl.gearStacks.get(cl.gearNext++ % cl.gearStacks.size()).copy();
			cl.caster.setItemInHand(InteractionHand.MAIN_HAND, st);
			cl.caster.getCooldowns().removeCooldown(cl.caster.getCooldowns().getCooldownGroup(st));
			cl.caster.snapTo(cl.caster.getX(), cl.caster.getY(), cl.caster.getZ(), 0F, 10F);
			try {
				var res = cl.caster.gameMode.useItem(cl.caster, cl.lvl, st, InteractionHand.MAIN_HAND);
				String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
				if (cl.meter.on && !cl.gearUsed.contains(id + ":" + res)) {
					cl.gearUsed.add(id + ":" + res);
				}
			} catch (Throwable e) {
				QAServer.LOG.warn("[clientload] gear use threw: {}", e.toString());
			}
		} else if (cl.t % 15 == 10 && cl.caster.isUsingItem()) {
			cl.caster.releaseUsingItem();
		}
		return "";
	}

	static final String[] BOSSES = {"vorlagh", "neferkhet", "rimeback", "strawgrim", "gulpmother"};
	String bossId = "";

	static String startBoss(final ClientLoad cl, final MinecraftServer s) {
		for (String b : BOSSES) {
			Identifier key = Identifier.fromNamespaceAndPath("elsewhere", b);
			if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
				continue;
			}
			Entity e = cl.spawn(BuiltInRegistries.ENTITY_TYPE.getValue(key), cl.anchor.getX() + 0.5, cl.anchor.getZ() + 0.5);
			if (e != null) {
				if (e instanceof Mob m) {
					m.setTarget(cl.probe);
				}
				cl.bossId = b;
				return "elsewhere:" + b + " at 16 blocks, targeting the measuring player (survival, invulnerable)";
			}
		}
		return "no telegraphing boss type in this jar";
	}

	// ------------------------------------------------------------------ results
	JsonObject result(final Scn s, final int ticks) {
		double secs = ticks / 20.0;
		JsonObject o = new JsonObject();
		o.addProperty("id", s.id);
		o.addProperty("title", s.title);
		o.addProperty("staged", s.how);
		o.addProperty("viewDistanceChunks", VIEW);
		o.addProperty("watchFrom", s.dist);
		o.addProperty("ticks", ticks);
		o.addProperty("wallSeconds", (System.nanoTime() - t0) / 1e9);
		List<Long> ns = new ArrayList<>(tickNs);
		ns.sort(Long::compare);
		JsonObject ms = new JsonObject();
		ms.addProperty("avg", ns.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6);
		ms.addProperty("p95", ns.isEmpty() ? 0 : ns.get((int) (ns.size() * 0.95)) / 1e6);
		ms.addProperty("max", ns.isEmpty() ? 0 : ns.get(ns.size() - 1) / 1e6);
		o.add("mspt", ms);
		o.add("net", meter.net(secs));
		o.add("particles", meter.particleJson(secs));
		o.add("census", meter.census());
		o.add("blockEntities", blockEntities());
		if (s.id.equals("gear3")) {
			o.addProperty("gearUsed", String.join(", ", gearUsed));
		}
		if (s.id.equals("boss")) {
			o.addProperty("boss", bossId);
		}
		if (!LOG_DISCONNECT.isEmpty()) {
			o.addProperty("disconnects", String.join(" | ", LOG_DISCONNECT));
			LOG_DISCONNECT.clear();
		}
		JsonObject n = o.getAsJsonObject("net"), p = o.getAsJsonObject("particles"), c = o.getAsJsonObject("census");
		String line = QAServer.f("%s: %.0f packets/s, %.1f KB/s; particles %.0f/s server + %.0f/s client est; entities avg %.0f (soldiers %.0f, armored %.0f, mounts %.0f, "
			+ "projectiles %.0f), render passes %.0f; block entities %d; MSPT %.2f", s.id, n.get("packetsPerSec").getAsDouble(), n.get("kbPerSec").getAsDouble(),
			p.get("serverPerSec").getAsDouble(), p.get("clientEstPerSec").getAsDouble(), c.getAsJsonObject("avg").get("all").getAsDouble(),
			c.getAsJsonObject("avg").get("soldier").getAsDouble(), c.getAsJsonObject("avg").get("armoredSoldier").getAsDouble(),
			c.getAsJsonObject("avg").get("mount").getAsDouble(), c.getAsJsonObject("avg").get("projectile").getAsDouble(), c.get("passesAvg").getAsDouble(),
			o.getAsJsonObject("blockEntities").get("total").getAsInt(), ms.get("avg").getAsDouble());
		QAServer.LOG.info("[clientload] {}", line);
		boolean sawSomething = c.getAsJsonObject("max").get("all").getAsDouble() > 0 || s.id.equals("idle");
		QAServer.record(sawSomething ? "PASS" : "FAIL", "client load " + s.id, line + " | " + s.how);
		return o;
	}

	JsonObject blockEntities() {
		Map<String, Integer> all = new TreeMap<>(), near = new TreeMap<>();
		int total = 0, n64 = 0;
		if (eye != null) {
			int cx = ((int) Math.floor(eye.x)) >> 4, cz = ((int) Math.floor(eye.z)) >> 4;
			for (int dx = -VIEW; dx <= VIEW; dx++) {
				for (int dz = -VIEW; dz <= VIEW; dz++) {
					LevelChunk ch = lvl.getChunkSource().getChunkNow(cx + dx, cz + dz);
					if (ch == null) {
						continue;
					}
					for (BlockEntity be : ch.getBlockEntities().values()) {
						String k = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()));
						all.merge(k, 1, Integer::sum);
						total++;
						if (Vec3.atCenterOf(be.getBlockPos()).distanceTo(eye) <= 64) {
							near.merge(k, 1, Integer::sum);
							n64++;
						}
					}
				}
			}
		}
		JsonObject o = new JsonObject();
		o.addProperty("total", total);
		o.addProperty("within64", n64);
		JsonObject a = new JsonObject(), b = new JsonObject();
		all.forEach(a::addProperty);
		near.forEach(b::addProperty);
		o.add("byType", a);
		o.add("within64ByType", b);
		return o;
	}

	void write() {
		try {
			JsonObject root = new JsonObject();
			root.addProperty("suite", "clientload");
			root.addProperty("viewDistance", VIEW);
			root.add("scenarios", results);
			Files.writeString(Path.of("clientload.json"), new GsonBuilder().setPrettyPrinting().create().toJson(root));
		} catch (Exception e) {
			QAServer.LOG.error("[clientload] could not write clientload.json", e);
		}
	}

	// ------------------------------------------------------------------ the meter
	static final class Meter {
		static ProtocolInfo<ClientGamePacketListener> proto;
		static Class<?> soldierClass;
		static boolean soldierLooked;

		final ServerPlayer probe;
		final ByteBuf buf = Unpooled.buffer(1 << 16);
		final Int2ObjectOpenHashMap<Entity> tracked = new Int2ObjectOpenHashMap<>();
		boolean on;
		int chunks;
		final Map<String, long[]> buckets = new TreeMap<>(), classes = new HashMap<>(), parts = new TreeMap<>(), payloads = new TreeMap<>();
		long crits, poofs, encodeFail, packets, bytes;
		int samples;
		final Map<String, double[]> cat = new LinkedHashMap<>();
		final Map<String, Integer> types = new TreeMap<>();
		double passes, inView, flyingCrit;

		Meter(final ServerPlayer probe) {
			this.probe = probe;
			if (proto == null) {
				proto = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(probe.level().getServer().registryAccess()));
			}
			if (!soldierLooked) {
				soldierLooked = true;
				soldierClass = ReleaseSuite.cls(KP + "KingdomSoldier");
			}
		}

		void reset() {
			buckets.clear();
			classes.clear();
			parts.clear();
			payloads.clear();
			crits = poofs = encodeFail = packets = bytes = 0;
			samples = 0;
			cat.clear();
			types.clear();
			passes = inView = flyingCrit = 0;
		}

		void onPacket(final Packet<?> p) {
			if (p instanceof BundlePacket<?> b) {
				for (Packet<?> sub : b.subPackets()) {
					onPacket(sub);
				}
				if (on) {
					add(buckets, "entity-spawn", 2, 4);
				}
				return;
			}
			if (p instanceof ClientboundAddEntityPacket a) {
				tracked.put(a.getId(), probe.level().getEntity(a.getId()));
			} else if (p instanceof ClientboundRemoveEntitiesPacket r) {
				for (int id : r.entityIds()) {
					tracked.remove(id);
				}
			} else if (p instanceof ClientboundLevelChunkWithLightPacket) {
				chunks++;
			}
			if (!on) {
				return;
			}
			int n = size(p);
			packets++;
			bytes += n;
			String b = bucket(p);
			add(buckets, b, 1, n);
			add(classes, p.getClass().getName().replace("net.minecraft.network.protocol.game.", "").replace("net.minecraft.network.protocol.common.", ""), 1, n);
			if (p instanceof ClientboundLevelParticlesPacket lp) {
				String k = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(lp.particle().getType()));
				add(parts, k, 1, Math.max(1, lp.count()));
			} else if (p instanceof ClientboundCustomPayloadPacket cp) {
				add(payloads, String.valueOf(cp.payload().type().id()), 1, n);
			} else if (p instanceof ClientboundAnimatePacket ap) {
				if (ap.getAction() == ClientboundAnimatePacket.CRITICAL_HIT || ap.getAction() == ClientboundAnimatePacket.MAGIC_CRITICAL_HIT) {
					crits++;
				}
			} else if (p instanceof ClientboundEntityEventPacket ep) {
				if (ep.getEventId() == 60) {
					poofs++;
				}
			}
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		int size(final Packet<?> p) {
			try {
				buf.clear();
				((StreamCodec) proto.codec()).encode(buf, p);
				int n = buf.readableBytes();
				return n + (n < 128 ? 1 : n < 16384 ? 2 : 3);
			} catch (Throwable e) {
				encodeFail++;
				return 0;
			}
		}

		static String bucket(final Packet<?> p) {
			String n = p.getClass().getName();
			n = n.substring(n.lastIndexOf('.') + 1);
			if (n.contains("MoveEntityPacket") || n.contains("EntityPositionSync") || n.contains("TeleportEntity") || n.contains("RotateHead")
				|| n.contains("SetEntityMotion") || n.contains("MoveMinecart")) {
				return "entity-move";
			}
			if (n.contains("SetEntityData") || n.contains("SetEquipment") || n.contains("UpdateAttributes") || n.contains("EntityEvent") || n.contains("Animate")
				|| n.contains("HurtAnimation") || n.contains("DamageEvent") || n.contains("SetPassengers") || n.contains("SetEntityLink") || n.contains("MobEffect")
				|| n.contains("TakeItemEntity") || n.contains("ProjectilePower")) {
				return "entity-state";
			}
			if (n.contains("AddEntity") || n.contains("RemoveEntities")) {
				return "entity-spawn";
			}
			if (n.contains("LevelParticles")) {
				return "particles";
			}
			if (n.contains("Sound")) {
				return "sound";
			}
			if (n.contains("CustomPayload")) {
				return "payload";
			}
			if (n.contains("BlockUpdate") || n.contains("SectionBlocksUpdate") || n.contains("BlockEntityData") || n.contains("BlockEvent") || n.contains("BlockDestruction")
				|| n.contains("LevelEvent") || n.contains("Explode")) {
				return "block";
			}
			if (n.contains("Chunk") || n.contains("LightUpdate")) {
				return "chunk";
			}
			if (n.contains("BossEvent") || n.contains("Title") || n.contains("ActionBar") || n.contains("SystemChat")) {
				return "ui";
			}
			return "other";
		}

		static void add(final Map<String, long[]> m, final String k, final long a, final long b) {
			long[] v = m.computeIfAbsent(k, x -> new long[2]);
			v[0] += a;
			v[1] += b;
		}

		String classify(final Entity e) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
			String path = id.getPath(), cn = e.getClass().getName();
			if (soldierClass != null && soldierClass.isInstance(e)) {
				return "soldier";
			}
			if (cn.contains("Rank") || cn.contains("Crowd") || cn.contains("Impostor")) {
				return "rank";
			}
			if (e instanceof Projectile) {
				return "projectile";
			}
			if (e.isVehicle() || path.contains("horse") || path.contains("camel") || path.contains("mount") || path.contains("yak") || path.contains("steed")) {
				return "mount";
			}
			if (e instanceof net.minecraft.world.entity.item.ItemEntity) {
				return "item";
			}
			if (e instanceof net.minecraft.world.entity.ExperienceOrb) {
				return "xp";
			}
			if (e instanceof LivingEntity le) {
				if (cn.startsWith("dev.elsewhere.boss") || cn.contains("stub") || le.getMaxHealth() >= 150) {
					return "boss";
				}
				if (path.contains("villager") || path.contains("trader") || cn.contains("kingdom_court") || cn.contains("kingdom_villages") || cn.contains("Townsfolk")) {
					return "npc";
				}
				if (id.getNamespace().equals("elsewhere") && (cn.contains("summon") || cn.contains("Minion") || cn.contains("gear3"))) {
					return "summon";
				}
				return "mob";
			}
			return "other";
		}

		void sample(final Vec3 eye, final Vec3 look) {
			samples++;
			Map<String, Integer> c = new HashMap<>();
			int pass = 0, view = 0, crit = 0;
			int[] band = new int[4];
			for (Entity e : tracked.values()) {
				if (e == null || e.isRemoved()) {
					continue;
				}
				String k = classify(e);
				c.merge(k, 1, Integer::sum);
				c.merge("all", 1, Integer::sum);
				types.merge(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())), 1, Integer::sum);
				Vec3 d = e.position().subtract(eye);
				double dist = d.length();
				Vec3 h = new Vec3(d.x, 0, d.z).normalize(), lh = new Vec3(look.x, 0, look.z).normalize();
				if (dist < 2 || h.dot(lh) > Math.cos(Math.toRadians(58))) {
					view++;
				}
				if (e instanceof LivingEntity le) {
					int armor = 0, trims = 0, held = 0, body = 0;
					for (EquipmentSlot sl : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
						ItemStack st = le.getItemBySlot(sl);
						if (!st.isEmpty()) {
							armor++;
							if (st.get(DataComponents.TRIM) != null) {
								trims++;
							}
						}
					}
					if (!le.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
						held++;
					}
					if (!le.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
						held++;
					}
					if (!le.getItemBySlot(EquipmentSlot.BODY).isEmpty() || !le.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()) {
						body++;
					}
					pass += 1 + armor + trims + held + body;
					if (k.equals("soldier")) {
						if (armor > 0) {
							c.merge("armoredSoldier", 1, Integer::sum);
						}
						c.merge("soldierArmorPieces", armor, Integer::sum);
						c.merge("soldierTrims", trims, Integer::sum);
						c.merge("soldierHeld", held, Integer::sum);
						band[dist <= 24 ? 0 : dist <= 48 ? 1 : dist <= 96 ? 2 : 3]++;
					}
				} else {
					pass += 1;
				}
				if (e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow ar && ar.isCritArrow() && e.getDeltaMovement().lengthSqr() > 0.01) {
					crit++;
				}
			}
			c.put("soldier0to24", band[0]);
			c.put("soldier24to48", band[1]);
			c.put("soldier48to96", band[2]);
			c.put("soldierOver96", band[3]);
			for (String k : new String[] {"all", "soldier", "armoredSoldier", "mount", "projectile", "rank", "boss", "npc", "summon", "mob", "item", "xp", "other",
				"soldierArmorPieces", "soldierTrims", "soldierHeld", "soldier0to24", "soldier24to48", "soldier48to96", "soldierOver96"}) {
				double v = c.getOrDefault(k, 0);
				double[] a = cat.computeIfAbsent(k, x -> new double[2]);
				a[0] += v;
				a[1] = Math.max(a[1], v);
			}
			passes += pass;
			inView += view;
			flyingCrit += crit;
		}

		JsonObject census() {
			JsonObject o = new JsonObject(), avg = new JsonObject(), max = new JsonObject();
			int n = Math.max(1, samples);
			for (var e : cat.entrySet()) {
				avg.addProperty(e.getKey(), e.getValue()[0] / n);
				max.addProperty(e.getKey(), e.getValue()[1]);
			}
			for (String k : new String[] {"all", "soldier", "armoredSoldier", "mount", "projectile"}) {
				if (!avg.has(k)) {
					avg.addProperty(k, 0);
					max.addProperty(k, 0);
				}
			}
			o.add("avg", avg);
			o.add("max", max);
			o.addProperty("samples", samples);
			o.addProperty("passesAvg", passes / n);
			o.addProperty("inViewAvg", inView / n);
			o.addProperty("flyingCritArrowsAvg", flyingCrit / n);
			JsonObject ty = new JsonObject();
			types.forEach((k, v) -> ty.addProperty(k, v / (double) n));
			o.add("typesAvg", ty);
			return o;
		}

		JsonObject net(final double secs) {
			JsonObject o = new JsonObject();
			o.addProperty("packetsPerSec", packets / secs);
			o.addProperty("kbPerSec", bytes / 1024.0 / secs);
			o.addProperty("encodeFailures", encodeFail);
			JsonObject b = new JsonObject();
			for (var e : buckets.entrySet()) {
				JsonObject x = new JsonObject();
				x.addProperty("perSec", e.getValue()[0] / secs);
				x.addProperty("kbPerSec", e.getValue()[1] / 1024.0 / secs);
				b.add(e.getKey(), x);
			}
			o.add("buckets", b);
			JsonObject pl = new JsonObject();
			for (var e : payloads.entrySet()) {
				pl.addProperty(e.getKey(), e.getValue()[0] / secs);
			}
			o.add("payloadsPerSec", pl);
			JsonArray top = new JsonArray();
			classes.entrySet().stream().sorted((a, c) -> Long.compare(c.getValue()[1], a.getValue()[1])).limit(12).forEach(e -> {
				JsonObject x = new JsonObject();
				x.addProperty("packet", e.getKey());
				x.addProperty("perSec", e.getValue()[0] / secs);
				x.addProperty("kbPerSec", e.getValue()[1] / 1024.0 / secs);
				top.add(x);
			});
			o.add("topByBytes", top);
			return o;
		}

		JsonObject particleJson(final double secs) {
			JsonObject o = new JsonObject();
			long total = 0;
			JsonObject by = new JsonObject();
			for (var e : parts.entrySet()) {
				total += e.getValue()[1];
				by.addProperty(e.getKey(), e.getValue()[1] / secs);
			}
			o.addProperty("serverPerSec", total / secs);
			o.addProperty("serverPacketsPerSec", parts.values().stream().mapToLong(v -> v[0]).sum() / secs);
			o.add("byType", by);
			o.addProperty("critEventsPerSec", crits / secs);
			o.addProperty("poofEventsPerSec", poofs / secs);
			int n = Math.max(1, samples);
			// client-side particles the client makes on its own: crit emitter 16 x 3 ticks, death poof 20, crit-arrow trail 4 per tick of flight
			double est = (crits * 48 + poofs * 20) / secs + flyingCrit / n * 4 * 20;
			o.addProperty("clientEstPerSec", est);
			return o;
		}
	}
}
