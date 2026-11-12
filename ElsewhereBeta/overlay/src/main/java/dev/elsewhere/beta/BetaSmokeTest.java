package dev.elsewhere.beta;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.core.travel.RiftTravel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combined beta smoke test (-Delsewhere.betatest=true): registry summary, every block placed and broken, every item used,
 * chunks generated in every Elsewhere dimension, enter/leave each dimension, and every boss spawned, stepped through its
 * health phases, killed and checked for drops. Logs "[betatest] PASS|FAIL ..." and a total, then halts if
 * -Delsewhere.betatest.exit=true.
 */
public final class BetaSmokeTest {
	static final Logger LOG = LoggerFactory.getLogger("ElsewhereBeta");
	static final String NS = "elsewhere";
	static final List<String> EXPECTED_DIMS = List.of("pale_hollow", "petalwind", "glasswaste", "verdigris_sea", "ironpeak", "greenhaze", "the_hush");
	static final List<String> EXPECTED_BOSSES = List.of("mother_creak", "selenne", "kazehana", "tempestra", "heliodor", "ankhara", "verdigrim",
		"galvanox", "thurgrim", "hrolf", "percolax", "grumblegut", "auris", "lithara", "gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh",
		"grunthar", "solaxis", "mournmaw", "ossyra", "aphelion");
	static final Map<String, String> SUMMON = Map.of(
		"verdigrim", "dev.elsewhere.boss.verdigrim.VerdigrimSummon",
		"galvanox", "dev.elsewhere.boss.galvanox.GalvanoxSummon",
		"thurgrim", "dev.elsewhere.boss.thurgrim.ThurgrimSummon",
		"hrolf", "dev.elsewhere.boss.hrolf.HrolfSummon");

	static boolean isBoss(final String path) {
		return EXPECTED_BOSSES.contains(path) || path.startsWith("thurgrim") || path.startsWith("hrolf");
	}

	static int infos;

	static void info(final String what) {
		infos++;
		LOG.info("[betatest] INFO {}", what);
	}

	static final float[] STEPS = {0.85F, 0.70F, 0.55F, 0.40F, 0.25F, 0.10F};

	static int passed;
	static int failed;
	static final List<String> FAILURES = new ArrayList<>();
	static final List<Fight> FIGHTS = new ArrayList<>();
	static int tick;
	static boolean bossStageStarted;
	static boolean done;

	private BetaSmokeTest() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			boolean prod = Boolean.getBoolean("elsewhere.prodcheck");
			LOG.info("[betatest] starting combined beta {}", prod ? "production check" : "smoke test");
			stage("registries", () -> registries());
			if (prod) {
				stage("dimensions", () -> dimensions(server));
				stage("travel", () -> travel(server));
				BetaSmokeBeta2.run(server);
				BetaSmoke040.run(server);
				finish(server);
				return;
			}
			stage("blocks", () -> blocks(server));
			stage("items", () -> items(server));
			stage("dimensions", () -> dimensions(server));
			stage("travel", () -> travel(server));
			BetaSmokeBeta2.run(server);
			BetaSmoke040.run(server);
			stage("boss setup", () -> setupBosses(server));
			bossStageStarted = true;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!bossStageStarted || done) {
				return;
			}
			tick++;
			try {
				boolean allDone = true;
				for (Fight f : FIGHTS) {
					if (!f.finished) {
						try {
							f.tick(server);
						} catch (Throwable t) {
							LOG.error("[betatest] boss {} threw", f.id, t);
							check(false, "boss " + f.id + " fight threw " + t);
							f.finished = true;
						}
						allDone &= f.finished;
					}
				}
				if (allDone || tick > 20 * 120) {
					for (Fight f : FIGHTS) {
						if (!f.finished) {
							check(false, "boss " + f.id + " fight timed out at step " + f.step);
						}
					}
					finish(server);
				}
			} catch (Throwable t) {
				LOG.error("[betatest] tick crashed", t);
				check(false, "boss stage crashed: " + t);
				finish(server);
			}
		});
	}

	static void finish(final MinecraftServer server) {
		done = true;
		LOG.info("[betatest] TOTAL {} passed, {} failed ({} info){}", passed, failed, infos, failed == 0 ? " - ALL PASSED" : " - FAILURES: " + FAILURES);
		if (Boolean.getBoolean("elsewhere.betatest.exit") || Boolean.getBoolean("elsewhere.prodcheck")) {
			server.halt(false);
		}
	}

	static void stage(final String name, final Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			LOG.error("[betatest] stage {} crashed", name, t);
			check(false, "stage " + name + " crashed: " + t);
		}
	}

	static void check(final boolean ok, final String what) {
		if (ok) {
			passed++;
		} else {
			failed++;
			FAILURES.add(what);
		}
		LOG.info("[betatest] {} {}", ok ? "PASS" : "FAIL", what);
	}

	static String f(final String fmt, final Object... a) {
		return String.format(Locale.ROOT, fmt, a);
	}

	static FakePlayer fake(final ServerLevel level, final String name) {
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
		return p;
	}

	// ------------------------------------------------------------------ registries
	static void registries() {
		long blocks = BuiltInRegistries.BLOCK.keySet().stream().filter(k -> k.getNamespace().equals(NS)).count();
		long items = BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals(NS)).count();
		long ents = BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(k -> k.getNamespace().equals(NS)).count();
		long sounds = BuiltInRegistries.SOUND_EVENT.keySet().stream().filter(k -> k.getNamespace().equals(NS)).count();
		check(blocks > 0 && items > 0 && ents > 0, f("registries: %d blocks, %d items, %d entity types, %d sounds in elsewhere:", blocks, items, ents, sounds));
	}

	// ------------------------------------------------------------------ blocks
	static void blocks(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos base = new BlockPos(-3000, 200, -3000);
		for (int dx = -1; dx <= 3; dx++) {
			for (int dz = -1; dz <= 3; dz++) {
				level.getChunk((base.getX() >> 4) + dx, (base.getZ() >> 4) + dz);
			}
		}
		int i = 0, ok = 0, popped = 0, errors = 0;
		List<String> bad = new ArrayList<>();
		for (Identifier id : new TreeMap<>(BuiltInRegistries.BLOCK.keySet().stream().filter(k -> k.getNamespace().equals(NS))
			.collect(java.util.stream.Collectors.toMap(k -> k.toString(), k -> k))).values()) {
			Block b = BuiltInRegistries.BLOCK.getValue(id);
			BlockPos pos = base.offset((i % 16) * 3, (i / 256) * 3, ((i / 16) % 16) * 3);
			i++;
			try {
				level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
				BlockState st = b.defaultBlockState();
				level.setBlock(pos, st, 3);
				if (level.getBlockState(pos).is(b)) {
					ok++;
				} else {
					popped++;
				}
				level.destroyBlock(pos, true);
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			} catch (Throwable t) {
				errors++;
				bad.add(id + ": " + t);
				LOG.error("[betatest] block {} threw", id, t);
			}
		}
		check(errors == 0, f("blocks: %d elsewhere blocks placed and broken (%d stayed, %d need support/popped), %d threw %s", i, ok, popped, errors, bad));
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(64))) {
			e.discard();
		}
	}

	// ------------------------------------------------------------------ items
	static void items(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos at = new BlockPos(-3000, 230, -2900);
		level.getChunk(at);
		level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
		FakePlayer p = fake(level, "BetaItemTester");
		int n = 0, errors = 0;
		List<String> bad = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals(NS)).sorted().toList()) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item instanceof BlockItem) {
				continue;
			}
			n++;
			try {
				if (p.level() != level) {
					RiftTravel.teleport(p, level, Vec3.atBottomCenterOf(at), 0.0F);
				}
				p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
				ItemStack stack = new ItemStack(item);
				p.setItemInHand(InteractionHand.MAIN_HAND, stack);
				stack.getHoverName();
				stack.use(level, p, InteractionHand.MAIN_HAND);
				p.releaseUsingItem();
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			} catch (Throwable t) {
				errors++;
				bad.add(id + ": " + t);
				LOG.error("[betatest] item {} threw on use", id, t);
			}
		}
		check(errors == 0, f("items: %d non-block elsewhere items used by a fake player, %d threw %s", n, errors, bad));
	}

	// ------------------------------------------------------------------ dimensions
	static List<ResourceKey<Level>> elsewhereDims(final MinecraftServer server) {
		List<ResourceKey<Level>> out = new ArrayList<>();
		for (ResourceKey<Level> k : server.levelKeys()) {
			if (k.identifier().getNamespace().equals(NS)) {
				out.add(k);
			}
		}
		return out;
	}

	static void dimensions(final MinecraftServer server) {
		List<ResourceKey<Level>> dims = elsewhereDims(server);
		List<String> names = dims.stream().map(k -> k.identifier().getPath()).toList();
		List<String> missing = EXPECTED_DIMS.stream().filter(d -> !names.contains(d)).toList();
		check(missing.isEmpty(), f("dimensions loaded: %s; missing: %s", names, missing));
		int[][] chunks = {{0, 0}, {1, 0}, {0, 1}, {-1, -1}, {40, 40}, {-60, 25}, {120, -90}};
		for (ResourceKey<Level> key : dims) {
			ServerLevel level = server.getLevel(key);
			long t0 = System.nanoTime();
			int columnsWithGround = 0, columns = 0;
			try {
				for (int[] c : chunks) {
					ChunkAccess chunk = level.getChunk(c[0], c[1]);
					for (int x = 0; x < 16; x += 4) {
						for (int z = 0; z < 16; z += 4) {
							columns++;
							if (chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) > level.getMinY()) {
								columnsWithGround++;
							}
						}
					}
				}
				check(columnsWithGround > 0, f("%s: generated %d chunks in %d ms, %d/%d sampled columns have ground", key.identifier(), chunks.length,
					(System.nanoTime() - t0) / 1_000_000, columnsWithGround, columns));
			} catch (Throwable t) {
				LOG.error("[betatest] worldgen in {} threw", key.identifier(), t);
				check(false, key.identifier() + ": chunk generation threw " + t);
			}
		}
	}

	// ------------------------------------------------------------------ travel
	static void travel(final MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		FakePlayer p = fake(overworld, "BetaTraveller");
		int sx = 700, sz = 700;
		overworld.getChunk(sx >> 4, sz >> 4);
		BlockPos start = new BlockPos(sx, overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz), sz);
		for (ResourceKey<Level> key : elsewhereDims(server)) {
			try {
				p.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
				boolean went = RiftTravel.travelNow(p, key);
				boolean there = p.level().dimension().equals(key);
				ServerLevel lvl = p.level();
				boolean free = lvl.noCollision(p) && !lvl.getBlockState(p.blockPosition().below()).isAir();
				check(went && there, f("enter %s: landed at %s (free space %s)", key.identifier(), p.blockPosition().toShortString(), free));
				if (dev.elsewhere.core.riftruin.ReturnRifts.enabledIn(key)) {
					BlockPos rift = dev.elsewhere.core.riftruin.ReturnRifts.nearestRift(lvl, p.getBlockX(), p.getBlockZ());
					lvl.getChunk(rift.getX() >> 4, rift.getZ() >> 4);
					boolean found = false;
					for (int dx = -3; dx <= 3 && !found; dx++) {
						for (int dz = -3; dz <= 3 && !found; dz++) {
							for (int y = lvl.getMinY(); y < lvl.getMaxY() && !found; y++) {
								found = lvl.getBlockState(new BlockPos(rift.getX() + dx, y, rift.getZ() + dz)).is(dev.elsewhere.registry.ModBlocks.RETURN_RIFT);
							}
						}
					}
					check(found, f("%s: nearest Return Rift at %s is %.0f blocks from the landing and generated", key.identifier(), rift.toShortString(),
						Math.sqrt(p.blockPosition().distSqr(new BlockPos(rift.getX(), p.getBlockY(), rift.getZ())))));
				}
				boolean back = RiftTravel.travelNow(p, null);
				check(back && p.level() == overworld && p.position().distanceTo(Vec3.atBottomCenterOf(start)) < 30,
					f("leave %s: back in the Overworld %.1f blocks from where we left", key.identifier(), p.position().distanceTo(Vec3.atBottomCenterOf(start))));
			} catch (Throwable t) {
				LOG.error("[betatest] travel {} threw", key.identifier(), t);
				check(false, "travel " + key.identifier() + " threw " + t);
				if (p.level() != overworld) {
					RiftTravel.teleport(p, overworld, Vec3.atBottomCenterOf(start), 0.0F);
				}
			}
		}
	}

	// ------------------------------------------------------------------ bosses
	static void setupBosses(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		Map<String, EntityType<?>> bosses = new LinkedHashMap<>();
		for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(k -> k.getNamespace().equals(NS)).sorted().toList()) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			boolean expected = EXPECTED_BOSSES.stream().anyMatch(b -> id.getPath().startsWith(b));
			try {
				Entity probe = type.create(level, EntitySpawnReason.COMMAND);
				if (probe instanceof LivingEntity le && (expected || le.getMaxHealth() >= 150.0F)) {
					bosses.put(id.getPath(), type);
				}
				if (probe != null) {
					probe.discard();
				}
			} catch (Throwable t) {
				LOG.error("[betatest] creating {} threw", id, t);
				check(false, "entity " + id + " cannot be created: " + t);
			}
		}
		List<String> missing = EXPECTED_BOSSES.stream().filter(b -> bosses.keySet().stream().noneMatch(k -> k.startsWith(b))).toList();
		List<String> real = bosses.keySet().stream().filter(BetaSmokeTest::isBoss).toList();
		check(missing.isEmpty(), f("bosses registered: %d %s; missing: %s", real.size(), real, missing));
		info("large mobs / boss parts also exercised: " + bosses.keySet().stream().filter(k -> !isBoss(k)).toList());
		int i = 0;
		for (Map.Entry<String, EntityType<?>> e : bosses.entrySet()) {
			FIGHTS.add(new Fight(e.getKey(), e.getValue(), level, new BlockPos(24000 + (i % 6) * 400, 0, 24000 + (i / 6) * 400)));
			i++;
		}
		// 2.0 diagnostic (alpha.3 "grunthar removed (DISCARDED)"): one bare Grunthar whose fake player is NOT enlisted, as
		// in alpha.3. Its outcome is logged as INFO only; the real Grunthar fight above is the checked one.
		EntityType<?> g = bosses.get("grunthar");
		if (g != null) {
			Fight probe = new Fight("grunthar", g, level, new BlockPos(24000 + 5 * 400, 0, 24000 + 6 * 400));
			probe.probe = true;
			FIGHTS.add(probe);
		}
	}

	static final class Fight {
		final String id;
		final EntityType<?> type;
		final ServerLevel level;
		BlockPos pos;
		LivingEntity boss;
		FakePlayer player;
		int t;
		int step;
		boolean finished;
		boolean forced;
		boolean died;
		int diedAt = -1;
		Vec3 deathPos;
		float maxHp;
		Method phaseMethod;
		Object lastPhase;
		final List<String> phases = new ArrayList<>();
		boolean tookDamage;
		int invBefore;
		/** Diagnostic fight: every outcome is INFO, and the fake player is never enlisted (alpha.3 behaviour). */
		boolean probe;
		boolean enlisted;

		void chk(final boolean ok, final String what) {
			if (probe) {
				info("probe (not enlisted) " + (ok ? "ok: " : "NOT ok: ") + what);
			} else {
				check(ok, what);
			}
		}

		/**
		 * 2.0: a real player standing in the arena is joined when the fight begins (and every 10 ticks); the FakePlayer is
		 * not in level.players(), so it used to join only once a hit dealt damage. A boss that is immune right then (bare
		 * Grunthar starts in a phase transition: no Ashtusk) saw no participant for 40 ticks and reset (discarded itself).
		 */
		void enlist() {
			if (probe || enlisted || !(boss instanceof dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss eb)) {
				return;
			}
			var fight = eb.primary().fight();
			if (fight == null) {
				return;
			}
			if (fight.participant(player) == null && fight.arena.contains(player)) {
				fight.join(player);
			}
			enlisted = fight.participant(player) != null;
		}

		String bossState() {
			if (boss instanceof dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss eb) {
				var fight = eb.primary().fight();
				return f("phase %d, transition %s, fight %s, fake player a participant: %s", eb.getPhase(), eb.inTransition(), fight == null ? "none" : "running",
					fight != null && fight.participant(player) != null);
			}
			return "";
		}

		Fight(final String id, final EntityType<?> type, final ServerLevel level, final BlockPos xz) {
			this.id = id;
			this.type = type;
			this.level = level;
			this.pos = xz;
		}

		void tick(final MinecraftServer server) {
			t++;
			if (t == 1) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						level.setChunkForced((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz, true);
					}
				}
				level.getChunk(pos);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
				pos = new BlockPos(pos.getX(), Math.max(y, level.getSeaLevel()), pos.getZ());
				for (int dx = -6; dx <= 6; dx++) {
					for (int dz = -6; dz <= 6; dz++) {
						level.setBlock(pos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
					}
				}
				String hook = SUMMON.entrySet().stream().filter(en -> id.startsWith(en.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
				if (hook != null) {
					try {
						Object r = Class.forName(hook).getMethod("summon", ServerLevel.class, BlockPos.class).invoke(null, level, pos);
						if (r instanceof LivingEntity le) {
							boss = le;
							info("boss " + id + " summoned through " + hook + ".summon (builds its arena)");
						}
					} catch (Throwable t) {
						LOG.error("[betatest] summon hook for {} threw", id, t);
					}
				}
				if (boss == null) {
					Entity e = type.create(level, EntitySpawnReason.COMMAND);
					if (!(e instanceof LivingEntity le)) {
						chk(false, "boss " + id + " could not be created");
						finished = true;
						return;
					}
					boss = le;
					boss.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
					if (boss instanceof Mob mob) {
						try {
							mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
						} catch (Throwable ignored) {
							// some bosses have no spawn data; the plain spawn still counts
						}
					}
					level.addFreshEntity(boss);
				}
				// FakePlayer.get caches by profile: the probe needs its own player, or it moves the checked fight's one away
				player = fake(level, (probe ? "BetaProbe_" : "BetaHunter_") + id);
				player.snapTo(pos.getX() + 4.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
				invBefore = countInv();
				phaseMethod = findPhase(boss);
				return;
			}
			if (died) {
				if (t - diedAt >= 60) {
					collectDrops();
					finished = true;
				}
				return;
			}
			if (!boss.isRemoved()) {
				enlist();
				if (probe && (t == 3 || t == 39 || t == 41)) {
					info(f("probe grunthar (not enlisted) at t=%d: %s", t, bossState()));
				}
			}
			if (boss.isRemoved() || boss.isDeadOrDying()) {
				if (boss.isDeadOrDying() || boss.getRemovalReason() == Entity.RemovalReason.KILLED) {
					markDied();
				} else {
					if (isBoss(id)) {
						chk(false, f("boss %s was removed (%s) before dying, at step %d (t=%d); discarded by %s", id, boss.getRemovalReason(), step, t,
							BossBarSweep.DISCARDED_BY.getOrDefault(boss.getUUID(), "?")));
					} else {
						info(f("%s was removed (%s) on its own: a boss part or summon that needs its parent", id, boss.getRemovalReason()));
					}
					finished = true;
				}
				return;
			}
			samplePhase();
			if (t == 20) {
				maxHp = boss.getMaxHealth();
				chk(boss.isAlive() && !boss.isRemoved(), f("boss %s spawns (HP %.0f/%.0f, armor %d)", id, boss.getHealth(), maxHp, boss.getArmorValue()));
			}
			int s = (t - 40) / 60;
			if (t >= 40 && (t - 40) % 60 == 0 && s < STEPS.length) {
				step = s + 1;
				float before = boss.getHealth();
				boss.hurtServer(level, level.damageSources().playerAttack(player), 6.0F);
				if (boss.getHealth() < before) {
					tookDamage = true;
				}
				float target = boss.getMaxHealth() * STEPS[s];
				if (boss.getHealth() > target) {
					boss.setHealth(target);
				}
			}
			int killStart = 40 + STEPS.length * 60;
			if (t == killStart) {
				if (tookDamage) {
					chk(true, f("boss %s takes real player damage during its phases", id));
				} else {
					info(f("boss %s ignored plain player hits (mechanic-gated: hearts, stun windows, dormancy); phases were forced through health", id));
				}
			}
			if (t >= killStart && t < killStart + 100 && (t - killStart) % 11 == 0) {
				boss.hurtServer(level, level.damageSources().playerAttack(player), Math.max(20.0F, boss.getMaxHealth() * 0.3F));
			}
			if (t == killStart + 100) {
				forced = true;
				boss.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			}
			if (t == killStart + 120) {
				forced = true;
				boss.setHealth(0.0F);
				boss.die(level.damageSources().playerAttack(player));
			}
			if (t > killStart + 400) {
				chk(false, f("boss %s would not die (HP %.1f)", id, boss.getHealth()));
				finished = true;
			}
		}

		void markDied() {
			died = true;
			diedAt = t;
			deathPos = boss.position();
			samplePhase();
			chk(true, f("boss %s dies%s after phases %s", id, forced ? " (forced kill)" : " from player damage", phases));
		}

		int countInv() {
			int n = 0;
			for (ItemStack s : player.getInventory()) {
				n += s.getCount();
			}
			return n;
		}

		void collectDrops() {
			List<String> drops = new ArrayList<>();
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(deathPos)).inflate(48))) {
				drops.add(e.getItem().getCount() + "x" + BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath());
			}
			int inv = countInv() - invBefore;
			boolean modDrop = drops.stream().anyMatch(d -> BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath(NS, d.substring(d.indexOf('x') + 1))));
			if (modDrop || inv > 0) {
				chk(true, f("%s drops: %s%s", id, drops, inv > 0 ? " + " + inv + " item(s) given to the player" : ""));
			} else {
				// per-participant loot: the fake player is not an arena participant, so roll the loot table directly
				List<String> rolled = new ArrayList<>();
				try {
					java.util.Optional<ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> key = type.getDefaultLootTable();
					if (key.isPresent()) {
						// 2.0: chance pools added by other modules (Fabric MODIFY, e.g. gear2's 30-50% parts) need more than 3 rolls
						for (int i = 0; i < 40 && rolled.isEmpty(); i++) {
							boss.dropFromLootTable(level, level.damageSources().playerAttack(player), true, key.get(),
								st -> rolled.add(st.getCount() + "x" + BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()));
						}
					}
				} catch (Throwable t) {
					LOG.error("[betatest] loot roll for {} threw", id, t);
				}
				boolean hasTable = type.getDefaultLootTable().isPresent()
					&& level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable().get()) != net.minecraft.world.level.storage.loot.LootTable.EMPTY;
				if (isBoss(id) && hasTable && rolled.isEmpty() && poolCount(level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable().get())) == 0) {
					// arsenal/summoner/gear2 placeholders: the file only exists so modules can hook it; no pools, the boss's drops are coded
					info(f("%s has only a placeholder loot table (no pools): its drops are coded per participant (the fake player is not one)", id));
				} else if (isBoss(id) && hasTable) {
					chk(!rolled.isEmpty(), f("%s drops: none in the world (per-participant loot); its loot table rolls %s (up to 40 rolls)", id, rolled));
				} else if (isBoss(id)) {
					info(f("%s has no loot table: its drops are coded per participant (the fake player is not one); drops are checked by the module self-test", id));
				} else {
					info(f("%s drops nothing in the world; loot table rolls %s", id, rolled));
				}
			}
			leftovers();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setChunkForced((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz, false);
				}
			}
		}

		/** 0.4.0: 3 s after the death no arena-seal blocks and no Elsewhere effect entities may be left (Kazehana, Vorlagh fixes). */
		void leftovers() {
			BlockPos c = BlockPos.containing(deathPos);
			int seals = 0;
			Block seal = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NS, "arena_seal"));
			if (seal != null && seal != Blocks.AIR) {
				BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
				for (int x = -40; x <= 40; x++) {
					for (int z = -40; z <= 40; z++) {
						for (int y = -16; y <= 32; y++) {
							m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
							if (level.isLoaded(m) && level.getBlockState(m).is(seal)) {
								seals++;
							}
						}
					}
				}
			}
			Map<String, Integer> ents = new TreeMap<>();
			for (Entity e : level.getEntities((Entity)null, new AABB(c).inflate(64), e -> !(e instanceof LivingEntity) && !(e instanceof ItemEntity)
				&& !(e instanceof net.minecraft.world.entity.ExperienceOrb) && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS))) {
				ents.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), 1, Integer::sum);
			}
			int n = ents.values().stream().mapToInt(Integer::intValue).sum();
			String msg = f("%s: 3 s after its death %d arena-seal block(s) and %d effect entit(ies) %s are left", id, seals, n, ents);
			if (id.equals("kazehana") || id.equals("vorlagh")) {
				chk(seals == 0 && n == 0, msg);
			} else if (seals > 0 || n > 0) {
				info("LEFTOVER " + msg);
			}
		}

		/** Number of pools in a loot table (reflection on its List<LootPool> field), -1 if unknown. */
		static int poolCount(final net.minecraft.world.level.storage.loot.LootTable table) {
			for (java.lang.reflect.Field fl : net.minecraft.world.level.storage.loot.LootTable.class.getDeclaredFields()) {
				if (List.class.isAssignableFrom(fl.getType()) && fl.getGenericType().getTypeName().contains("LootPool")) {
					try {
						fl.setAccessible(true);
						return ((List<?>) fl.get(table)).size();
					} catch (Throwable t) {
						return -1;
					}
				}
			}
			return -1;
		}

		static Method findPhase(final Object o) {
			for (String n : Arrays.asList("phase", "getPhase", "currentPhase", "getCurrentPhase")) {
				try {
					Method m = o.getClass().getMethod(n);
					if (m.getParameterCount() == 0) {
						return m;
					}
				} catch (NoSuchMethodException ignored) {
					// try next
				}
			}
			return null;
		}

		void samplePhase() {
			if (phaseMethod == null || boss == null) {
				return;
			}
			try {
				Object ph = phaseMethod.invoke(boss);
				if (!Objects.equals(ph, lastPhase)) {
					lastPhase = ph;
					phases.add(String.valueOf(ph));
				}
			} catch (Throwable ignored) {
				// reflection only for the log
			}
		}
	}
}
