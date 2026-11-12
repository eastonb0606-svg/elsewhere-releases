package dev.elsewhereqa.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Targeted checks for Elsewhere 0.4.0-beta (CONTRACT "UPDATE 0.4.0-beta"). Every result line is
 * "[qa-client] V04 PASS|FAIL|WARN|INFO <area>: detail". Only registries, commands and reflection: no compile
 * dependency on the beta jar.
 */
final class V04Checks {
	static final Logger LOG = LoggerFactory.getLogger("ElsewhereQA-client");
	static final String NS = "elsewhere";

	private V04Checks() {
	}

	static void res(final String status, final String area, final String detail) {
		LOG.info("[qa-client] V04 {} {}: {}", status, area, detail);
	}

	static ServerPlayer player(final MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	static Block block(final String id) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElse(Blocks.AIR);
	}

	static Item item(final String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
	}

	static int playerCmd(final MinecraftServer s, final String cmd) {
		ServerPlayer p = player(s);
		var src = s.createCommandSourceStack().withEntity(p).withPosition(p.position()).withLevel(p.level()).withSuppressedOutput();
		try {
			var parse = s.getCommands().getDispatcher().parse(cmd, src);
			if (parse.getReader().canRead()) {
				return -1;
			}
			return s.getCommands().getDispatcher().execute(parse);
		} catch (Throwable t) {
			LOG.info("[qa-client] command /{} threw {}", cmd, t.toString());
			return -2;
		}
	}

	/** Core RiftTravel.travelNow (arrival spot like a real trip); falls back to a plain teleport. */
	static String travel(final TestServerContext server, final String dim) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ResourceKey<Level> key = dim == null ? null : ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
			try {
				Class<?> rt = Class.forName("dev.elsewhere.core.travel.RiftTravel");
				rt.getMethod("travelNow", ServerPlayer.class, ResourceKey.class).invoke(null, p, key);
			} catch (ReflectiveOperationException e) {
				ServerLevel lvl = key == null ? s.overworld() : s.getLevel(key);
				p.teleportTo(lvl, 0.5, 100, 0.5, Set.of(), 0, 0, true);
			}
			return p.level().dimension().identifier() + " " + p.blockPosition().toShortString();
		});
	}

	static void lookAt(final ClientGameTestContext ctx, final Vec3 c) {
		ctx.runOnClient(mc -> {
			var eye = mc.player.getEyePosition();
			double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
			double h = Math.sqrt(dx * dx + dz * dz);
			float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float)(-Math.toDegrees(Math.atan2(dy, h)));
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.yRotO = yaw;
			mc.player.xRotO = pitch;
		});
	}

	static Object staticField(final String cls, final String field) {
		try {
			Field f = Class.forName(cls).getDeclaredField(field);
			f.setAccessible(true);
			return f.get(null);
		} catch (Throwable t) {
			return null;
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// 1. Nether / End boss arenas generate in the world, and their bosses can be summoned there.

	record Target(String boss, String dim, String marker, String anchorCls, String anchorField, String bossType) {
	}

	static final List<Target> NETHER = List.of(
		new Target("grunthar", "minecraft:the_nether", "elsewhere:gilded_throne", "dev.elsewhere.boss.grunthar.GruntharArena", "THRONE", "grunthar"),
		new Target("solaxis", "minecraft:the_nether", "elsewhere:pyre_cradle_core", null, null, "solaxis"),
		new Target("mournmaw", "minecraft:the_nether", "minecraft:soul_campfire", null, null, "mournmaw"));
	static final List<Target> END = List.of(
		new Target("ossyra", "minecraft:the_end", "elsewhere:matriarch_pedestal", "dev.elsewhere.boss.ossyra.OssyraArena", "PEDESTAL", "ossyra"),
		new Target("aphelion", "minecraft:the_end", "elsewhere:halo_altar", "dev.elsewhere.boss.aphelion.AphelionArena", "ALTAR", "aphelion"));

	static void arenaGen(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server, final Consumer<String> shot,
		final long netherBudgetMs, final long endBudgetMs) {
		server.runCommand("gamemode creative @a");
		Map<String, BlockPos> found = new LinkedHashMap<>();
		found.putAll(scan(ctx, server, "minecraft:the_nether", 0, 0, 0, NETHER, netherBudgetMs));
		found.putAll(scan(ctx, server, "minecraft:the_end", 75, 0, 66, END, endBudgetMs));
		for (Target t : concat(NETHER, END)) {
			BlockPos at = found.get(t.boss());
			if (at == null) {
				res("FAIL", "arenagen " + t.boss(), "no generated arena found in " + t.dim() + " within the scan budget (marker " + t.marker() + ")");
				continue;
			}
			res("PASS", "arenagen " + t.boss(), "generated arena found in " + t.dim() + ": marker " + t.marker() + " at " + at.toShortString());
			try {
				showAndSummon(ctx, sp, server, shot, t, at);
			} catch (Throwable e) {
				res("FAIL", "arenagen " + t.boss(), "summon/screenshot threw " + e);
			}
		}
		server.runCommand("gamemode creative @a");
		travel(server, null);
		ctx.waitTicks(20);
	}

	@SafeVarargs
	static <T> List<T> concat(final List<T>... ls) {
		List<T> out = new ArrayList<>();
		for (List<T> l : ls) {
			out.addAll(l);
		}
		return out;
	}

	/** Loads 17x17-chunk cells in a spiral (4 at a time, FORCED tickets) and looks for each target's marker block. */
	static Map<String, BlockPos> scan(final ClientGameTestContext ctx, final TestServerContext server, final String dim, final int cx0, final int cz0,
		final int minDistChunks, final List<Target> targets, final long budgetMs) {
		Map<String, BlockPos> found = new LinkedHashMap<>();
		Map<String, List<BlockPos>> campfires = new LinkedHashMap<>();
		List<int[]> cells = new ArrayList<>();
		for (int r = 0; r <= 8; r++) {
			for (int i = -r; i <= r; i++) {
				for (int j = -r; j <= r; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) {
						continue;
					}
					int cx = cx0 + i * 17, cz = cz0 + j * 17;
					if (Math.sqrt((double)cx * cx + (double)cz * cz) < minDistChunks) {
						continue;
					}
					cells.add(new int[] {cx, cz});
				}
			}
		}
		long t0 = System.currentTimeMillis();
		int chunks = 0, ci = 0;
		while (ci < cells.size() && found.size() < targets.size() && System.currentTimeMillis() - t0 < budgetMs) {
			final List<int[]> batch = cells.subList(ci, Math.min(cells.size(), ci + 4));
			ci += batch.size();
			List<CompletableFuture<?>> futs = server.computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
				List<CompletableFuture<?>> l = new ArrayList<>();
				for (int[] c : batch) {
					l.add(lvl.getChunkSource().addTicketAndLoadWithRadius(TicketType.FORCED, new ChunkPos(c[0], c[1]), 8));
				}
				return l;
			});
			for (int k = 0; k < 1800 && !futs.stream().allMatch(CompletableFuture::isDone); k++) {
				ctx.waitTick();
			}
			int n = server.computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
				int scanned = 0;
				for (int[] c : batch) {
					for (int dx = -8; dx <= 8; dx++) {
						for (int dz = -8; dz <= 8; dz++) {
							LevelChunk ch = lvl.getChunkSource().getChunkNow(c[0] + dx, c[1] + dz);
							if (ch == null) {
								continue;
							}
							scanned++;
							scanChunk(lvl, ch, targets, found, campfires);
						}
					}
					lvl.getChunkSource().removeTicketWithRadius(TicketType.FORCED, new ChunkPos(c[0], c[1]), 8);
				}
				return scanned;
			});
			chunks += n;
		}
		res("INFO", "arenagen scan " + dim, String.format(Locale.ROOT, "%d chunks scanned in %.1f s (%d cells); found %s", chunks,
			(System.currentTimeMillis() - t0) / 1000.0, ci, found));
		return found;
	}

	static void scanChunk(final ServerLevel lvl, final LevelChunk ch, final List<Target> targets, final Map<String, BlockPos> found,
		final Map<String, List<BlockPos>> campfires) {
		LevelChunkSection[] secs = ch.getSections();
		for (Target t : targets) {
			if (found.containsKey(t.boss())) {
				continue;
			}
			Block marker = block(t.marker());
			if (marker == Blocks.AIR) {
				continue;
			}
			for (int si = 0; si < secs.length; si++) {
				LevelChunkSection sec = secs[si];
				if (sec == null || sec.hasOnlyAir() || !sec.getStates().maybeHas(st -> st.is(marker))) {
					continue;
				}
				int baseY = lvl.getSectionYFromSectionIndex(si) << 4;
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							if (sec.getBlockState(x, y, z).is(marker)) {
								BlockPos pos = new BlockPos(ch.getPos().getMinBlockX() + x, baseY + y, ch.getPos().getMinBlockZ() + z);
								if (t.boss().equals("mournmaw")) {
									// a Weeping Basin has 4 soul campfires 4.5 blocks from its centre: wait for 3 of them close together
									List<BlockPos> l = campfires.computeIfAbsent("m", k -> new ArrayList<>());
									l.add(pos);
									List<BlockPos> near = l.stream().filter(q -> q.distManhattan(pos) <= 14 && Math.abs(q.getY() - pos.getY()) <= 1).toList();
									if (near.size() >= 3) {
										int sx = 0, sz = 0;
										for (BlockPos q : near) {
											sx += q.getX();
											sz += q.getZ();
										}
										found.put(t.boss(), new BlockPos(Math.round(sx / (float)near.size()), pos.getY(), Math.round(sz / (float)near.size())));
										return;
									}
								} else {
									found.put(t.boss(), pos);
									break;
								}
							}
						}
						if (found.containsKey(t.boss())) {
							break;
						}
					}
					if (found.containsKey(t.boss())) {
						break;
					}
				}
				if (found.containsKey(t.boss())) {
					break;
				}
			}
		}
	}

	/** Two views of the generated arena, the real summon trigger, and a view of the boss in it. */
	static void showAndSummon(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server, final Consumer<String> shot,
		final Target t, final BlockPos marker) {
		// arena origin (floor centre) from the builder's anchor offset when it has one
		BlockPos origin = marker;
		if (t.anchorCls() != null && staticField(t.anchorCls(), t.anchorField()) instanceof BlockPos off) {
			origin = marker.subtract(off);
		}
		final BlockPos o = origin;
		server.runCommand("gamemode spectator @a");
		for (int view = 0; view < 2; view++) {
			final int v = view;
			Vec3 cam = server.computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.dim())));
				lvl.getChunk(o.getX() >> 4, o.getZ() >> 4);
				// from 3 above the floor centre, walk outward until 1.5 blocks before a solid block (max 16), then up
				double ang = Math.toRadians(v == 0 ? 35 : 215);
				Vec3 start = Vec3.atBottomCenterOf(o).add(0, v == 0 ? 5 : 2.5, 0);
				Vec3 dir = new Vec3(Math.cos(ang), v == 0 ? 0.35 : 0.1, Math.sin(ang)).normalize();
				Vec3 best = start;
				for (double d = 1; d <= 18; d += 0.5) {
					Vec3 q = start.add(dir.scale(d));
					if (!lvl.getBlockState(BlockPos.containing(q)).isAir() || !lvl.getBlockState(BlockPos.containing(q).above()).isAir()) {
						break;
					}
					best = start.add(dir.scale(Math.max(1, d - 1.5)));
				}
				ServerPlayer p = player(s);
				p.teleportTo(lvl, best.x, best.y - p.getEyeHeight(), best.z, Set.of(), 0, 0, true);
				return best;
			});
			ctx.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			lookAt(ctx, Vec3.atCenterOf(o));
			ctx.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			shot.accept("v04_gen_" + t.boss() + (view == 0 ? "_a" : "_b"));
			LOG.info("[qa-client] arenagen {} view {} from {}", t.boss(), view, cam);
		}
		// real summon trigger
		server.runCommand("gamemode creative @a");
		String how = server.computeOnServer(s -> {
			ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.dim())));
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			p.teleportTo(lvl, o.getX() + 0.5, o.getY() + 1, o.getZ() + 6.5, Set.of(), 180, 0, true);
			if (t.dim().equals("minecraft:the_end")) {
				try {
					Object fight = lvl.getDragonFight();
					for (String fn : new String[] {"hasPreviouslyKilledDragon", "dragonKilled"}) {
						Field f = fight.getClass().getDeclaredField(fn);
						f.setAccessible(true);
						f.setBoolean(fight, true);
					}
				} catch (Throwable e) {
					LOG.info("[qa-client] could not mark the dragon killed: {}", e.toString());
				}
			}
			switch (t.boss()) {
				case "grunthar" -> {
					return "gold block on throne: " + useOn(p, lvl, "minecraft:gold_block", marker);
				}
				case "ossyra" -> {
					return "shulker shell on pedestal: " + useOn(p, lvl, "minecraft:shulker_shell", marker);
				}
				case "aphelion" -> {
					int have = lvl.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, new AABB(o).inflate(12)).size();
					for (int k = have; k < 4; k++) {
						var c = net.minecraft.world.entity.EntityTypes.END_CRYSTAL.create(lvl, EntitySpawnReason.COMMAND);
						double a = k * Math.PI / 2;
						c.snapTo(o.getX() + 0.5 + Math.cos(a) * 7, o.getY() + 1, o.getZ() + 0.5 + Math.sin(a) * 7, 0, 0);
						c.setShowBottom(false);
						lvl.addFreshEntity(c);
					}
					return "dragon egg on altar (" + have + " crystals there, topped up to 4): " + useOn(p, lvl, "minecraft:dragon_egg", marker);
				}
				case "solaxis" -> {
					List<BlockPos> sp2 = new ArrayList<>();
					for (BlockPos q : BlockPos.betweenClosed(o.offset(-12, -4, -12), o.offset(12, 8, 12))) {
						if (lvl.getBlockState(q).is(Blocks.SPAWNER)) {
							sp2.add(q.immutable());
						}
					}
					int broken = 0;
					for (BlockPos q : sp2) {
						if (p.gameMode.destroyBlock(q)) {
							broken++;
						}
					}
					return "broke " + broken + "/" + sp2.size() + " blaze spawners";
				}
				case "mournmaw" -> {
					Item dg = item("minecraft:dried_ghast");
					if (dg != null) {
						p.getInventory().add(new ItemStack(dg));
					}
					p.teleportTo(lvl, o.getX() + 0.5, o.getY() + 1, o.getZ() + 10.5, Set.of(), 180, 0, true);
					int dormant = lvl.getEntities(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, "mournmaw")),
						e -> e.distanceToSqr(Vec3.atCenterOf(o)) < 64 * 64).size();
					return "walked in with a dried ghast (" + dormant + " Mournmaw already there)";
				}
				default -> {
					return "?";
				}
			}
		});
		LOG.info("[qa-client] arenagen {} trigger: {}", t.boss(), how);
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, t.bossType()));
		String state = "no boss";
		for (int k = 0; k < 12 && state.equals("no boss"); k++) {
			ctx.waitTicks(10);
			state = server.computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.dim())));
				for (Entity e : lvl.getEntities(type, e -> e.isAlive() && e.distanceToSqr(Vec3.atCenterOf(o)) < 80 * 80)) {
					String bar = "";
					try {
						Method m = e.getClass().getMethod("isDormant");
						bar = " dormant=" + m.invoke(e);
					} catch (Throwable ignored) {
						// no dormant state
					}
					return "boss " + e.blockPosition().toShortString() + bar;
				}
				return "no boss";
			});
		}
		res(state.startsWith("boss") ? "PASS" : "FAIL", "arenagen " + t.boss() + " summon", how + " -> " + state);
		if (state.startsWith("boss")) {
			ctx.waitTicks(30);
			server.runOnServer(s -> {
				ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.dim())));
				ServerPlayer p = player(s);
				for (Entity e : lvl.getEntities(type, e -> e.isAlive() && e.distanceToSqr(Vec3.atCenterOf(o)) < 80 * 80)) {
					double d = Math.max(6.0, e.getBbWidth() * 1.6 + e.getBbHeight() * 1.0 + 2);
					Vec3 from = e.position().add(0, e.getBbHeight() * 0.4, 0).add(new Vec3(o.getX() + 0.5 - e.getX(), 0, o.getZ() + 6.5 - e.getZ()).normalize().scale(d));
					if (Double.isNaN(from.x)) {
						from = e.position().add(0, 2, d);
					}
					p.teleportTo(lvl, from.x, from.y, from.z, Set.of(), 0, 0, true);
					break;
				}
			});
			server.runCommand("gamemode spectator @a");
			ctx.waitTicks(10);
			sp.getConnection().waitForChunksRender();
			double[] c = server.computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.dim())));
				for (Entity e : lvl.getEntities(type, e -> e.isAlive())) {
					return new double[] {e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ()};
				}
				return null;
			});
			if (c != null) {
				lookAt(ctx, new Vec3(c[0], c[1], c[2]));
			}
			ctx.waitTicks(3);
			shot.accept("v04_gen_" + t.boss() + "_summoned");
		}
		server.runOnServer(s -> {
			ServerLevel lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.dim())));
			for (Entity e : lvl.getEntitiesOfClass(Entity.class, new AABB(o).inflate(80), e -> !(e instanceof ServerPlayer)
				&& (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS) || e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal))) {
				e.discard();
			}
		});
		server.runCommand("gamemode creative @a");
		ctx.waitTicks(10);
	}

	static String useOn(final ServerPlayer p, final ServerLevel lvl, final String itemId, final BlockPos pos) {
		Item it = item(itemId);
		if (it == null) {
			return "no item " + itemId;
		}
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(it));
		var r = p.gameMode.useItemOn(p, lvl, p.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
		return String.valueOf(r);
	}

	// ------------------------------------------------------------------------------------------------------------
	// 2. Ankhara's erupt / lunge / stun poses (event-driven, like the art team's shots), side view.

	static void ankhara(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server, final Consumer<String> shot) {
		server.runCommand("gamemode creative @a");
		Identifier aid = Identifier.fromNamespaceAndPath(NS, "ankhara");
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(aid);
		// like the art team's ArtShots: Bosses.summon on the Glasswaste surface (no arena), debugSurface, NoAI, side view
		String r = server.computeOnServer(s -> {
			ServerLevel gw = s.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NS, "glasswaste")));
			BlockPos at = new BlockPos(6100, 0, 6100);
			gw.getChunk(at.getX() >> 4, at.getZ() >> 4);
			at = at.atY(gw.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
			try {
				Class.forName("dev.elsewhere.petalwind_glasswaste.Bosses").getMethod("summon", ServerLevel.class, String.class, BlockPos.class).invoke(null, gw, "ankhara", at);
				return "Bosses.summon at " + at.toShortString();
			} catch (Throwable t) {
				return "Bosses.summon failed (" + t + "), dev command rc=" + playerCmd(s, "elsewhere_dev_petalwind_glasswaste boss ankhara");
			}
		});
		ctx.waitTicks(10);
		String placed = server.computeOnServer(s -> {
			for (ServerLevel l : s.getAllLevels()) {
				for (Entity e : l.getEntities(type, e -> e.isAlive())) {
					try {
						e.getClass().getMethod("debugSurface").invoke(e);
					} catch (Throwable t) {
						LOG.info("[qa-client] Ankhara.debugSurface unavailable: {}", t.toString());
					}
					if (e instanceof Mob m) {
						m.setNoAi(true);
					}
					float yaw = 200F;
					e.setYRot(yaw);
					e.setYBodyRot(yaw);
					e.setYHeadRot(yaw);
					Vec3 fwd = Vec3.directionFromRotation(0, yaw);
					Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
					Vec3 eye = e.position().add(fwd.scale(9)).add(right.scale(6)).add(0, 3.5, 0);
					for (int i = 0; i < 40 && !(l.getBlockState(BlockPos.containing(eye)).isAir() && l.getBlockState(BlockPos.containing(eye).above()).isAir()); i++) {
						eye = eye.add(0, 1, 0);
					}
					ServerPlayer p = player(s);
					p.teleportTo(l, eye.x, eye.y - p.getEyeHeight(), eye.z, Set.of(), 0, 0, true);
					p.getAbilities().flying = true;
					p.onUpdateAbilities();
					return "at " + e.blockPosition().toShortString() + " in " + l.dimension().identifier() + ", camera " + BlockPos.containing(eye).toShortString();
				}
			}
			return "no ankhara";
		});
		ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) { mc.gui.hud.toggle(); } });
		LOG.info("[qa-client] ankhara poses: dev command {} -> {}", r, placed);
		if (placed.startsWith("no")) {
			ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) { mc.gui.hud.toggle(); } });
			res("FAIL", "ankhara poses", "could not summon Ankhara (" + r + ")");
			return;
		}
		ctx.waitTicks(40);
		sp.getConnection().waitForChunksRender();
		Runnable aim = () -> {
			double[] c = server.computeOnServer(s -> {
				for (ServerLevel l : s.getAllLevels()) {
					for (Entity e : l.getEntities(type, e -> e.isAlive())) {
						return new double[] {e.getX(), e.getY() + 1.4, e.getZ()};
					}
				}
				return null;
			});
			if (c != null) {
				lookAt(ctx, new Vec3(c[0], c[1], c[2]));
			}
		};
		aim.run();
		ctx.waitTicks(5);
		shot.accept("v04_ankhara_idle");
		String cls = "dev.elsewhere.boss.ankhara.Ankhara";
		String[][] seq = {
			{"EV_ERUPT", "erupt", "3,8,14,24"},
			{"EV_WINDUP", "lunge_windup", "6,12,20"},
			{"EV_LUNGE", "lunge", "3,8,14"},
			{"EV_STUN", "stun", "6,14,28"}};
		for (String[] st : seq) {
			Object ev = staticField(cls, st[0]);
			if (!(ev instanceof Byte b)) {
				res("WARN", "ankhara poses", st[0] + " not found");
				continue;
			}
			if (st[0].equals("EV_STUN")) {
				setSynced(server, type, cls, "DATA_MODE", staticField(cls, "M_STUNNED"));
			}
			server.runOnServer(s -> {
				for (ServerLevel l : s.getAllLevels()) {
					for (Entity e : l.getEntities(type, e -> e.isAlive())) {
						l.broadcastEntityEvent(e, b);
					}
				}
			});
			int done = 0;
			for (String f : st[2].split(",")) {
				int at = Integer.parseInt(f);
				ctx.waitTicks(Math.max(1, at - done - 1));
				done = at;
				shot.accept("v04_ankhara_" + st[1] + "_" + at + "t");
			}
			ctx.waitTicks(40);
			if (st[0].equals("EV_STUN")) {
				setSynced(server, type, cls, "DATA_MODE", staticField(cls, "M_SURFACED"));
			}
		}
		ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) { mc.gui.hud.toggle(); } });
		res("INFO", "ankhara poses", "frames v04_ankhara_* taken (judge by eye)");
		server.runOnServer(s -> {
			for (ServerLevel l : s.getAllLevels()) {
				for (Entity e : l.getEntities(type, e -> true)) {
					e.discard();
				}
			}
		});
		travel(server, null);
		ctx.waitTicks(10);
	}

	@SuppressWarnings("unchecked")
	static void setSynced(final TestServerContext server, final EntityType<?> type, final String cls, final String field, final Object value) {
		if (!(value instanceof Integer v)) {
			return;
		}
		server.runOnServer(s -> {
			try {
				Field f = Class.forName(cls).getDeclaredField(field);
				f.setAccessible(true);
				var acc = (net.minecraft.network.syncher.EntityDataAccessor<Integer>)f.get(null);
				for (ServerLevel l : s.getAllLevels()) {
					for (Entity e : l.getEntities(type, e -> e.isAlive())) {
						e.getEntityData().set(acc, v);
					}
				}
			} catch (Throwable t) {
				LOG.info("[qa-client] set {} failed: {}", field, t.toString());
			}
		});
	}

	// ------------------------------------------------------------------------------------------------------------
	// 3. Herding Crook: Knockback, Unbreaking, Mending only.

	static void crook(final TestServerContext server) {
		String out = server.computeOnServer(s -> {
			Item it = item("elsewhere:herding_crook");
			if (it == null) {
				return "FAIL|herding_crook not registered";
			}
			ItemStack crook = new ItemStack(it);
			var reg = s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			Set<String> anvil = new TreeSet<>(), primary = new TreeSet<>(), table = new TreeSet<>();
			for (Holder.Reference<Enchantment> h : reg.listElements().toList()) {
				String id = h.key().identifier().getPath();
				if (h.value().canEnchant(crook)) {
					anvil.add(id);
				}
				if (h.value().isPrimaryItem(crook)) {
					primary.add(id);
				}
			}
			var inTable = reg.get(EnchantmentTags.IN_ENCHANTING_TABLE);
			for (int lvl = 1; lvl <= 40; lvl++) {
				if (inTable.isEmpty()) {
					break;
				}
				for (EnchantmentInstance ei : EnchantmentHelper.getAvailableEnchantmentResults(lvl, crook, inTable.get().stream())) {
					table.add(ei.enchantment().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
				}
			}
			Object ench = crook.get(DataComponents.ENCHANTABLE);
			Set<String> bad = new TreeSet<>();
			for (String b : List.of("sharpness", "smite", "bane_of_arthropods", "looting", "fire_aspect", "sweeping_edge")) {
				if (anvil.contains(b) || table.contains(b)) {
					bad.add(b);
				}
			}
			Set<String> extra = new TreeSet<>(anvil);
			extra.addAll(table);
			extra.removeAll(Set.of("knockback", "unbreaking", "mending", "vanishing_curse"));
			String st = bad.isEmpty() && extra.isEmpty() && anvil.contains("knockback") ? "PASS" : bad.isEmpty() ? "WARN" : "FAIL";
			return st + "|enchantable=" + ench + "; anvil/book accepts " + anvil + "; enchanting-table offers " + table + "; primary " + primary
				+ (bad.isEmpty() ? "" : "; FORBIDDEN " + bad) + (extra.isEmpty() ? "" : "; outside the allowed set: " + extra);
		});
		String[] p = out.split("\\|", 2);
		res(p[0], "herding crook enchantments", p[1]);
	}

	// ------------------------------------------------------------------------------------------------------------
	// 4. Pale Hollow extras: Moon-Eye trinket, Greatbow pierce, Echo-Forged heal-on-kill, drops (loot rolls).

	static void paleHollow(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server, final Consumer<String> shot) {
		// registry + components
		String reg = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (String id : List.of("moon_eye", "heartwood_greatbow", "echo_forged_blade", "rootcall_staff", "elder_bark_crown", "moonsilk_thread")) {
				Item it = item("elsewhere:" + id);
				if (it == null) {
					b.append(id).append("=MISSING; ");
					continue;
				}
				ItemStack st = new ItemStack(it);
				b.append(id).append(" attrs=").append(st.get(DataComponents.ATTRIBUTE_MODIFIERS)).append("; ");
			}
			Item me = item("elsewhere:moon_eye");
			if (me != null) {
				b.append("moon_eye in #elsewhere:caster_trinket=")
					.append(new ItemStack(me).is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NS, "caster_trinket"))));
			}
			return b.toString();
		});
		res(reg.contains("MISSING") ? "FAIL" : "INFO", "pale hollow items", reg);
		// loot rolls (300 each): which items each table gives and how often
		String loot = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			ServerLevel lvl = s.overworld();
			ServerPlayer p = player(s);
			for (String ent : List.of("selenne", "mother_creak", "eyeblossom_wisp")) {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath(NS, ent)).orElse(null);
				if (type == null) {
					b.append(ent).append(": no entity; ");
					continue;
				}
				Entity e = type.create(lvl, EntitySpawnReason.COMMAND);
				if (!(e instanceof LivingEntity le) || le.getLootTable().isEmpty()) {
					b.append(ent).append(": no loot table; ");
					continue;
				}
				e.snapTo(p.getX(), p.getY(), p.getZ(), 0, 0);
				LootTable table = s.reloadableRegistries().getLootTable(le.getLootTable().get());
				Map<String, Integer> hits = new TreeMap<>();
				int rolls = 300;
				for (int k = 0; k < rolls; k++) {
					var src = lvl.damageSources().playerAttack(p);
					LootParams params = new LootParams.Builder(lvl).withParameter(LootContextParams.THIS_ENTITY, e).withParameter(LootContextParams.ORIGIN, e.position())
						.withParameter(LootContextParams.DAMAGE_SOURCE, src).withOptionalParameter(LootContextParams.ATTACKING_ENTITY, p)
						.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, p).withParameter(LootContextParams.LAST_DAMAGE_PLAYER, p)
						.create(LootContextParamSets.ENTITY);
					Set<String> seen = new TreeSet<>();
					table.getRandomItems(params, st -> seen.add(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()));
					for (String x : seen) {
						hits.merge(x, 1, Integer::sum);
					}
				}
				e.discard();
				b.append(ent).append(" (").append(le.getLootTable().get().identifier()).append(") in ").append(rolls).append(" rolls: ");
				hits.forEach((k, v) -> b.append(k).append(' ').append(Math.round(100.0 * v / rolls)).append("%, "));
				b.append("; ");
			}
			return b.toString();
		});
		res("INFO", "pale hollow loot rolls", loot);
		res(loot.contains("rootcall_staff") ? "PASS" : "WARN", "selenne drops rootcall_staff", loot.contains("rootcall_staff") ? "in the loot table" : "not in Selenne's loot table (maybe rolled in code on the kill)");
		res(loot.contains("elder_bark_crown") ? "PASS" : "WARN", "mother creak drops elder_bark_crown", loot.contains("elder_bark_crown") ? "in the loot table" : "not in the loot table (maybe rolled in code)");
		res(loot.contains("moonsilk_thread") ? "PASS" : "WARN", "moonsilk_thread source", loot.contains("moonsilk_thread") ? "drops from " + loot.replaceAll(".*?(\\w+) \\([^)]*\\) in \\d+ rolls: [^;]*moonsilk_thread.*", "$1") : "no moonsilk_thread from Selenne / Eyeblossom Wisp tables");

		// live checks on a platform
		int bx = 52000, bz = 52000;
		server.runCommand("effect clear @a");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			ServerLevel lvl = s.overworld();
			lvl.getChunk(bx >> 4, bz >> 4);
			for (int dx = -10; dx <= 10; dx++) {
				for (int dz = -10; dz <= 22; dz++) {
					lvl.setBlock(new BlockPos(bx + dx, 119, bz + dz), Blocks.STONE.defaultBlockState(), 2);
					for (int dy = 0; dy < 8; dy++) {
						lvl.setBlock(new BlockPos(bx + dx, 120 + dy, bz + dz), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
			player(s).teleportTo(lvl, bx + 0.5, 120, bz + 0.5, Set.of(), 0, 0, true);
		});
		ctx.waitTicks(20);
		sp.getConnection().waitForChunksRender();
		// Echo-Forged Blade: kill a 1-HP zombie at 10 HP (food 17, so no natural regen)
		String heal = server.computeOnServer(s -> {
			ServerLevel lvl = s.overworld();
			ServerPlayer p = player(s);
			Item blade = item("elsewhere:echo_forged_blade");
			if (blade == null) {
				return "FAIL|no blade";
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
			p.getFoodData().setFoodLevel(17);
			p.getFoodData().setSaturation(0);
			p.setHealth(10);
			Mob z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(lvl, EntitySpawnReason.COMMAND);
			z.snapTo(bx + 0.5, 120, bz + 2.0, 180, 0);
			z.setNoAi(true);
			lvl.addFreshEntity(z);
			z.setHealth(1);
			float before = p.getHealth();
			p.resetAttackStrengthTicker();
			try {
				Field f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
				f.setAccessible(true);
				f.setInt(p, 100);
			} catch (Throwable ignored) {
				// fine
			}
			p.attack(z);
			return before + "|" + p.getHealth() + "|" + z.isDeadOrDying();
		});
		ctx.waitTicks(2);
		float after2 = server.computeOnServer(s -> player(s).getHealth());
		String[] hp = heal.split("\\|");
		if (hp.length == 3) {
			float before = Float.parseFloat(hp[0]), after = Math.max(Float.parseFloat(hp[1]), after2);
			res(after - before >= 1.9F ? "PASS" : "FAIL", "echo-forged blade heal on kill", String.format(Locale.ROOT,
				"health %.1f -> %.1f same tick, %.1f two ticks later (zombie killed=%s; expected +2)", before, Float.parseFloat(hp[1]), after2, hp[2]));
		} else {
			res("FAIL", "echo-forged blade heal on kill", heal);
		}
		server.runCommand("effect give @a saturation infinite 255 true");
		server.runCommand("effect give @a resistance infinite 255 true");
		// Heartwood Greatbow: two zombies in a line, shoot with a real right-click draw
		server.runOnServer(s -> {
			ServerLevel lvl = s.overworld();
			for (Entity e : lvl.getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
			for (int k = 0; k < 2; k++) {
				Mob z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(lvl, EntitySpawnReason.COMMAND);
				z.snapTo(bx + 0.5, 120, bz + 8.5 + k * 1.2, 180, 0);
				z.setNoAi(true);
				z.setPersistenceRequired();
				lvl.addFreshEntity(z);
			}
			ServerPlayer p = player(s);
			p.teleportTo(lvl, bx + 0.5, 120, bz + 0.5, Set.of(), 0, 0, true);
		});
		server.runCommand("item replace entity @a hotbar.0 with elsewhere:heartwood_greatbow");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:arrow 16");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(10);
		lookAt(ctx, new Vec3(bx + 0.5, 121.4, bz + 8.5));
		ctx.waitTicks(2);
		ctx.getInput().holdKeyFor(o -> o.keyUse, 30);
		ctx.waitTicks(3);
		String bow = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (Entity e : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
				new AABB(new BlockPos(bx, 120, bz)).inflate(40))) {
				b.append("arrow pierce=").append(((net.minecraft.world.entity.projectile.arrow.AbstractArrow)e).getPierceLevel()).append(' ');
			}
			return b.toString();
		});
		ctx.waitTicks(15);
		shot.accept("v04_greatbow_shot");
		String hurt = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (LivingEntity z : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30))) {
				b.append(String.format(Locale.ROOT, "%.0f/%.0f ", z.getHealth(), z.getMaxHealth()));
			}
			return b.toString().trim();
		});
		res(bow.contains("pierce=1") || bow.contains("pierce=2") || bow.contains("pierce=3") ? "PASS" : "FAIL", "heartwood greatbow pierce",
			"fired: [" + bow.trim() + "]; zombie HP after: [" + hurt + "] (expected pierce >= 1, both zombies hit)");
		// Moon-Eye: mark a zombie (3 Glimmer, Glowing 10 s)
		server.runCommand("item replace entity @a hotbar.0 with elsewhere:moon_eye");
		server.runCommand("effect give @a elsewhere:clarity 1 0 true");
		ctx.waitTicks(25);
		lookAt(ctx, new Vec3(bx + 0.5, 121.4, bz + 8.5));
		float gBefore = ctx.computeOnClient(mc -> glimmer());
		ctx.getInput().holdKeyFor(o -> o.keyUse, 25);
		ctx.waitTicks(5);
		float gAfter = ctx.computeOnClient(mc -> glimmer());
		String glow = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (LivingEntity z : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30))) {
				b.append(z.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) ? "glowing " : "not-glowing ");
			}
			return b.toString().trim();
		});
		shot.accept("v04_moon_eye_mark");
		res(glow.contains("glowing") && !glow.startsWith("not") || glow.contains(" glowing") ? "PASS" : "FAIL", "moon-eye mark",
			"zombies: [" + glow + "]; Glimmer " + gBefore + " -> " + gAfter + " (expected Glowing on the target, 3 Glimmer spent)");
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(40), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
		});
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
	}

	static float glimmer() {
		try {
			Class<?> c = Class.forName("dev.elsewhere.arsenal.glimmer.Glimmer$ClientMirror");
			return ((Number)c.getField("current").get(null)).floatValue();
		} catch (Throwable t) {
			return -1F;
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// 5. HUD: Haze / Resonance / Glimmer (and Heat) meters in Always mode at GUI scale 2 and 3; Heat follows toggles at once.

	static void setScale(final ClientGameTestContext ctx, final int scale) {
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		ctx.waitTicks(3);
		LOG.info("[qa-client] gui scale option {} -> {}", scale, ctx.computeOnClient(mc -> mc.getWindow().getGuiScale()));
	}

	static void hudScale(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server, final Consumer<String> shot) {
		for (String dim : List.of("elsewhere:greenhaze", "elsewhere:the_hush", "elsewhere:glasswaste")) {
			String name = Identifier.parse(dim).getPath();
			server.runCommand("gamemode creative @a");
			LOG.info("[qa-client] hudscale in {} at {}", name, travel(server, dim));
			server.runCommand("gamemode survival @a");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with elsewhere:amethyst_wand");
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 45 10");
			ctx.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			QAClientTest.clientCmd(ctx, "elsewhere_hud all always");
			ctx.waitTicks(60);
			for (int scale : new int[] {2, 3}) {
				setScale(ctx, scale);
				ctx.waitTicks(10);
				shot.accept("v04_hud_" + name + "_always_s" + scale);
			}
			setScale(ctx, 0);
			QAClientTest.clientCmd(ctx, "elsewhere_hud all auto");
			ctx.waitTicks(5);
		}
		server.runCommand("gamemode creative @a");
		travel(server, null);
		ctx.waitTicks(10);
	}

	static void heat(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server, final Consumer<String> shot) {
		server.runCommand("gamemode creative @a");
		LOG.info("[qa-client] heat check in glasswaste at {}", travel(server, "elsewhere:glasswaste"));
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 45 10");
		ctx.waitTicks(20);
		sp.getConnection().waitForChunksRender();
		QAClientTest.clientCmd(ctx, "elsewhere_hud heat always");
		ctx.waitTicks(40);
		shot.accept("v04_heat_always");
		LOG.info("[qa-client] heat mode {}", QAClientTest.hudMode(ctx, "heat"));
		QAClientTest.clientCmd(ctx, "elsewhere_hud heat hidden");
		shot.accept("v04_heat_hidden_now");
		ctx.waitTicks(20);
		shot.accept("v04_heat_hidden_1s");
		LOG.info("[qa-client] heat mode {}", QAClientTest.hudMode(ctx, "heat"));
		QAClientTest.clientCmd(ctx, "elsewhere_hud heat always");
		shot.accept("v04_heat_always_again_now");
		ctx.waitTicks(10);
		QAClientTest.hudAllHidden(ctx, true);
		ctx.waitTick();
		shot.accept("v04_heat_allhidden_now");
		QAClientTest.hudAllHidden(ctx, false);
		ctx.waitTick();
		shot.accept("v04_heat_allshown_now");
		QAClientTest.clientCmd(ctx, "elsewhere_hud heat auto");
		server.runCommand("gamemode creative @a");
		travel(server, null);
		ctx.waitTicks(10);
	}

	// ------------------------------------------------------------------------------------------------------------
	// 6. Wayfinder home from inside a dimension (Petalwind, entered with travelNow) opens a rift.

	static void wayHome(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server, final Consumer<String> shot) {
		for (String[] d : new String[][] {{"elsewhere:petalwind", "wayfinder_petalwind"}, {"elsewhere:glasswaste", "wayfinder_glasswaste"}}) {
			server.runCommand("gamemode creative @a");
			String where = travel(server, d[0]);
			server.runCommand("gamemode survival @a");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with elsewhere:" + d[1]);
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			ctx.waitTicks(30);
			sp.getConnection().waitForChunksRender();
			int cd = ctx.waitFor(mc -> !mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem()), 1400);
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(15);
			shot.accept("v04_wayhome_" + Identifier.parse(d[0]).getPath() + "_rift");
			String dimNow = "";
			for (int k = 0; k < 20; k++) {
				ctx.waitTicks(10);
				dimNow = server.computeOnServer(s -> player(s).level().dimension().identifier().toString());
				if (dimNow.equals("minecraft:overworld")) {
					break;
				}
			}
			sp.getConnection().waitForChunksRender();
			shot.accept("v04_wayhome_" + Identifier.parse(d[0]).getPath() + "_after");
			res(dimNow.equals("minecraft:overworld") ? "PASS" : "FAIL", "wayfinder home from " + d[0],
				"started at " + where + ", waited " + cd + " ticks for cooldown, right-click " + d[1] + " -> player in " + dimNow + " 10 s later");
			server.runCommand("gamemode creative @a");
			if (!dimNow.equals("minecraft:overworld")) {
				travel(server, null);
			}
			ctx.waitTicks(10);
		}
		server.runCommand("clear @a");
	}

	static void chat(final ClientGameTestContext ctx, final boolean on) {
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(on ? ChatVisiblity.FULL : ChatVisiblity.HIDDEN));
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
