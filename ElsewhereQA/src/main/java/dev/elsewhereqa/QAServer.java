package dev.elsewhereqa;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elsewhere QA playthrough (-Delsewhereqa.play=true). Works on any build of the beta jar: only registries, commands
 * and reflection, never a compile dependency. Logs "[qa] PASS|FAIL|WARN area: detail", writes run/qa-results.tsv and
 * run/qa-registry.json, and halts when -Delsewhereqa.exit=true.
 */
public final class QAServer implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("ElsewhereQA");
	static final String NS = "elsewhere";
	static final List<String> EXPECTED_DIMS = List.of("pale_hollow", "petalwind", "glasswaste", "verdigris_sea", "ironpeak", "greenhaze", "the_hush");
	static final List<String> EXPECTED_BOSSES = List.of("mother_creak", "selenne", "kazehana", "tempestra", "heliodor", "ankhara", "verdigrim",
		"galvanox", "thurgrim", "hrolf", "percolax", "grumblegut", "auris", "lithara", "gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh",
		"grunthar", "solaxis", "mournmaw", "ossyra", "aphelion");

	static final List<String[]> RESULTS = new ArrayList<>();
	static int pass, fail, warn, skip;
	static final Deque<Step> STEPS = new ArrayDeque<>();
	static Step current;
	static long tickStart;
	static long stepNanos;
	static double lastTickMs;
	static boolean finished;
	static int globalTick;

	interface Step {
		/** @return true when finished */
		boolean tick(MinecraftServer server) throws Throwable;

		default String name() {
			return getClass().getSimpleName();
		}
	}

	@Override
	public void onInitialize() {
		// failsafe for unattended runs: this JVM halts itself after -Delsewhereqa.watchdog=<minutes> (we never kill processes from outside)
		String wd = System.getProperty("elsewhereqa.watchdog");
		if (wd != null && !wd.isBlank()) {
			long ms = (long)(Double.parseDouble(wd) * 60_000);
			Thread t = new Thread(() -> {
				try {
					Thread.sleep(ms);
				} catch (InterruptedException e) {
					return;
				}
				LOG.error("[qa] WATCHDOG: still running after {} minutes; halting this JVM", wd);
				try {
					Files.writeString(Path.of("qa-watchdog.txt"), "halted after " + wd + " minutes\n");
				} catch (IOException ignored) {
					// best effort
				}
				Runtime.getRuntime().halt(137);
			}, "elsewhereqa-watchdog");
			t.setDaemon(true);
			t.start();
		}
		if (!Boolean.getBoolean("elsewhereqa.play")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(QAServer::plan);
		ServerTickEvents.START_SERVER_TICK.register(s -> tickStart = System.nanoTime());
		ServerTickEvents.END_SERVER_TICK.register(QAServer::endTick);
	}

	// ------------------------------------------------------------------ plumbing
	static void record(final String status, final String area, final String detail) {
		switch (status) {
			case "PASS" -> pass++;
			case "FAIL" -> fail++;
			case "SKIP" -> skip++;
			default -> warn++;
		}
		RESULTS.add(new String[] {status, area, detail});
		LOG.info("[qa] {} {}: {}", status, area, detail);
	}

	static void check(final boolean ok, final String area, final String detail) {
		record(ok ? "PASS" : "FAIL", area, detail);
	}

	static String f(final String fmt, final Object... a) {
		return String.format(Locale.ROOT, fmt, a);
	}

	static void endTick(final MinecraftServer server) {
		if (finished) {
			return;
		}
		globalTick++;
		long before = System.nanoTime();
		lastTickMs = (before - tickStart - stepNanos) / 1e6;
		try {
			if (current == null) {
				current = STEPS.poll();
				if (current == null) {
					finish(server);
					return;
				}
				LOG.info("[qa] --- step {}", current.name());
			}
			if (current.tick(server)) {
				current = null;
			}
		} catch (Throwable t) {
			LOG.error("[qa] step {} crashed", current == null ? "?" : current.name(), t);
			record("FAIL", "harness", "step " + (current == null ? "?" : current.name()) + " threw " + t);
			current = null;
		}
		stepNanos = System.nanoTime() - before;
		if (stepNanos > 10_000_000_000L) {
			record("WARN", "perf", f("step %s blocked the server thread for %.1f s in one tick (synchronous chunk generation or a heavy call; max-tick-time is disabled for QA runs)",
				current == null ? "(finished)" : current.name(), stepNanos / 1e9));
		}
	}

	static void finish(final MinecraftServer server) {
		finished = true;
		LOG.info("[qa] TOTAL {} PASS, {} FAIL, {} WARN, {} SKIP", pass, fail, warn, skip);
		StringBuilder sb = new StringBuilder();
		for (String[] r : RESULTS) {
			sb.append(r[0]).append('\t').append(r[1]).append('\t').append(r[2].replace('\t', ' ').replace('\n', ' ')).append('\n');
		}
		try {
			Files.writeString(Path.of("selftest-qa-results.log"), sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("[qa] could not write results", e);
		}
		if (Boolean.getBoolean("elsewhereqa.exit")) {
			server.halt(false);
		}
	}

	static FakePlayer fake(final ServerLevel level, final String name) {
		return FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(("qa-" + name).getBytes(StandardCharsets.UTF_8)), name));
	}

	static List<Identifier> ids(final net.minecraft.core.Registry<?> reg) {
		return reg.keySet().stream().filter(k -> k.getNamespace().equals(NS)).sorted().toList();
	}

	static List<ResourceKey<Level>> dims(final MinecraftServer server) {
		List<ResourceKey<Level>> out = new ArrayList<>();
		for (ResourceKey<Level> k : server.levelKeys()) {
			if (k.identifier().getNamespace().equals(NS)) {
				out.add(k);
			}
		}
		out.sort((a, b) -> a.identifier().toString().compareTo(b.identifier().toString()));
		return out;
	}

	static void teleport(final ServerPlayer p, final ServerLevel level, final Vec3 pos) {
		Object r = reflectStatic("dev.elsewhere.core.travel.RiftTravel", "teleport", new Class<?>[] {ServerPlayer.class, ServerLevel.class, Vec3.class, float.class},
			p, level, pos, 0.0F);
		if (r == MISSING) {
			p.teleport(new TeleportTransition(level, pos, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		}
	}

	static final Object MISSING = new Object();

	static Object reflectStatic(final String cls, final String method, final Class<?>[] types, final Object... args) {
		try {
			Method m = Class.forName(cls).getMethod(method, types);
			return m.invoke(null, args);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			return MISSING;
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		} catch (IllegalAccessException e) {
			return MISSING;
		}
	}

	static Object staticField(final String cls, final String field) {
		try {
			Field fl = Class.forName(cls).getField(field);
			return fl.get(null);
		} catch (ReflectiveOperationException e) {
			return MISSING;
		}
	}

	static int runCommand(final MinecraftServer server, final CommandSourceStack src, final String cmd) {
		try {
			var parse = server.getCommands().getDispatcher().parse(cmd, src);
			if (parse.getReader().canRead()) {
				return -1; // does not parse fully: command does not exist in this shape
			}
			return server.getCommands().getDispatcher().execute(parse);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			return -1;
		} catch (Throwable t) {
			LOG.error("[qa] command '{}' threw", cmd, t);
			record("FAIL", "command", "/" + cmd + " threw " + t);
			return -2;
		}
	}

	// ------------------------------------------------------------------ plan
	static void plan(final MinecraftServer server) {
		LOG.info("[qa] Elsewhere QA playthrough starting; mods: {}", FabricLoader.getInstance().getAllMods().stream()
			.map(m -> m.getMetadata().getId() + "@" + m.getMetadata().getVersion().getFriendlyString())
			.filter(s -> !s.startsWith("fabric-") && !s.startsWith("java") && !s.startsWith("mixinextras")).toList());
		// -Delsewhereqa.only=bosses skips the world checks; -Delsewhereqa.shard=i/n fights every n-th boss from i (parallel runs)
		boolean bossesOnly = "bosses".equals(System.getProperty("elsewhereqa.only"));
		String suite = System.getProperty("elsewhereqa.suite", "");
		if (!suite.isEmpty()) {
			ReleaseSuite.plan(server, suite);
			return;
		}
		STEPS.add(new Inventory());
		if (!bossesOnly) {
			STEPS.add(new Blocks_());
			STEPS.add(new Items_());
			int i = 0;
			for (ResourceKey<Level> key : dims(server)) {
				STEPS.add(new DimVisit(key, i++));
			}
		}
		STEPS.add(new BossSetup());
	}

	// ------------------------------------------------------------------ inventory + names
	static final class Inventory implements Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Throwable {
			List<Identifier> items = ids(BuiltInRegistries.ITEM), blocks = ids(BuiltInRegistries.BLOCK), ents = ids(BuiltInRegistries.ENTITY_TYPE);
			List<String> dimNames = dims(server).stream().map(k -> k.identifier().getPath()).toList();
			StringBuilder js = new StringBuilder("{\n");
			js.append("\"items\": ").append(jsonList(items)).append(",\n");
			js.append("\"blocks\": ").append(jsonList(blocks)).append(",\n");
			js.append("\"entities\": ").append(jsonList(ents)).append(",\n");
			js.append("\"dimensions\": ").append(jsonList(dimNames)).append("\n}\n");
			Files.writeString(Path.of("selftest-qa-registry.log"), js.toString());
			record(items.isEmpty() ? "FAIL" : "PASS", "registry", f("%d items, %d blocks, %d entity types, %d dimensions in elsewhere:", items.size(),
				blocks.size(), ents.size(), dimNames.size()));
			List<String> missingDims = EXPECTED_DIMS.stream().filter(d -> !dimNames.contains(d)).toList();
			check(missingDims.isEmpty(), "registry", "all 7 dimensions registered; missing " + missingDims);
			List<String> missingBosses = EXPECTED_BOSSES.stream().filter(b -> ents.stream().noneMatch(e -> e.getPath().startsWith(b))).toList();
			check(missingBosses.isEmpty(), "registry", "all 24 boss entity types registered; missing " + missingBosses);
			// Lang (Fabric loads every mod's en_us on the server too)
			Language lang = Language.getInstance();
			List<String> raw = new ArrayList<>();
			for (Identifier id : items) {
				String key = BuiltInRegistries.ITEM.getValue(id).getDescriptionId();
				if (!lang.has(key)) {
					raw.add(key);
				}
			}
			for (Identifier id : blocks) {
				String key = BuiltInRegistries.BLOCK.getValue(id).getDescriptionId();
				if (!lang.has(key) && BuiltInRegistries.ITEM.containsKey(id)) {
					raw.add(key);
				}
			}
			for (Identifier id : ents) {
				String key = BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescriptionId();
				if (!lang.has(key)) {
					raw.add(key);
				}
			}
			check(raw.isEmpty(), "lang", f("every elsewhere item/block/entity has an English name; %d raw keys: %s", raw.size(), raw));
			return true;
		}
	}

	static String jsonList(final List<?> l) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < l.size(); i++) {
			sb.append(i == 0 ? "" : ", ").append('"').append(l.get(i)).append('"');
		}
		return sb.append(']').toString();
	}

	// ------------------------------------------------------------------ blocks: place as a player, break with the right tool
	static final class Blocks_ implements Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Throwable {
			ServerLevel level = server.overworld();
			BlockPos base = new BlockPos(-4000, 150, -4000);
			for (int dx = -1; dx <= 5; dx++) {
				for (int dz = -1; dz <= 5; dz++) {
					level.getChunk((base.getX() >> 4) + dx, (base.getZ() >> 4) + dz);
				}
			}
			FakePlayer p = fake(level, "QABuilder");
			int i = 0, placedAsItem = 0, noItem = 0, popped = 0, noDrop = 0;
			List<String> threw = new ArrayList<>(), noDrops = new ArrayList<>(), notPlaced = new ArrayList<>();
			for (Identifier id : ids(BuiltInRegistries.BLOCK)) {
				Block b = BuiltInRegistries.BLOCK.getValue(id);
				BlockPos pos = base.offset((i % 24) * 3, 0, (i / 24) * 3);
				i++;
				try {
					level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
					Item item = b.asItem();
					if (item != Items.AIR && item instanceof BlockItem) {
						p.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() - 1.5, 0.0F, 30.0F);
						ItemStack stack = new ItemStack(item);
						p.setItemInHand(InteractionHand.MAIN_HAND, stack);
						p.gameMode.useItemOn(p, level, stack, InteractionHand.MAIN_HAND,
							new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false));
						placedAsItem++;
					} else {
						noItem++;
						level.setBlock(pos, b.defaultBlockState(), 3);
					}
					BlockState st = level.getBlockState(pos);
					if (!st.is(b)) {
						// some blocks need support or a special surface; place the state directly to still test breaking
						notPlaced.add(id.getPath());
						level.setBlock(pos, b.defaultBlockState(), 3);
						st = level.getBlockState(pos);
						if (!st.is(b)) {
							popped++;
							continue;
						}
					}
					p.setItemInHand(InteractionHand.MAIN_HAND, toolFor(st));
					AABB box = new AABB(pos).inflate(1.5);
					for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
						e.discard();
					}
					p.gameMode.destroyBlock(pos);
					int drops = level.getEntitiesOfClass(ItemEntity.class, box).size();
					if (drops == 0 && item != Items.AIR && st.getDestroySpeed(level, pos) >= 0) {
						noDrop++;
						noDrops.add(id.getPath());
					}
					for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
						e.discard();
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				} catch (Throwable t) {
					threw.add(id.getPath() + ": " + t);
					LOG.error("[qa] block {} threw", id, t);
				}
			}
			check(threw.isEmpty(), "blocks", f("%d blocks placed by a player (%d have no item) and broken; %d threw %s", i, noItem, threw.size(), threw));
			record(notPlaced.isEmpty() ? "PASS" : "WARN", "blocks", f("%d/%d block items did not place on stone when used (may need special support): %s",
				notPlaced.size(), placedAsItem, notPlaced));
			record(noDrops.isEmpty() ? "PASS" : "WARN", "blocks", f("%d blocks dropped nothing when broken with the right tool (check loot tables): %s",
				noDrop, noDrops));
			return true;
		}

		static ItemStack toolFor(final BlockState st) {
			if (st.is(BlockTags.MINEABLE_WITH_AXE)) {
				return new ItemStack(Items.NETHERITE_AXE);
			}
			if (st.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
				return new ItemStack(Items.NETHERITE_SHOVEL);
			}
			if (st.is(BlockTags.MINEABLE_WITH_HOE)) {
				return new ItemStack(Items.NETHERITE_HOE);
			}
			if (st.is(Blocks.COBWEB) || st.is(BlockTags.LEAVES)) {
				return new ItemStack(Items.SHEARS);
			}
			return new ItemStack(Items.NETHERITE_PICKAXE);
		}
	}

	// ------------------------------------------------------------------ items: use in air, use on a block, swing at a zombie
	static final class Items_ implements Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Throwable {
			ServerLevel level = server.overworld();
			BlockPos at = new BlockPos(-4000, 150, -3800);
			level.getChunk(at);
			level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
			FakePlayer p = fake(level, "QAItemTester");
			int n = 0;
			List<String> threw = new ArrayList<>();
			for (Identifier id : ids(BuiltInRegistries.ITEM)) {
				Item item = BuiltInRegistries.ITEM.getValue(id);
				if (item instanceof BlockItem) {
					continue;
				}
				n++;
				String stage = "create";
				try {
					if (p.level() != level) {
						teleport(p, level, Vec3.atBottomCenterOf(at));
					}
					p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
					ItemStack stack = new ItemStack(item);
					stage = "name";
					stack.getHoverName().getString();
					stage = "use";
					p.setItemInHand(InteractionHand.MAIN_HAND, stack);
					p.gameMode.useItem(p, level, stack, InteractionHand.MAIN_HAND);
					stage = "release";
					p.releaseUsingItem();
					stage = "useOn";
					ItemStack s2 = new ItemStack(item);
					p.setItemInHand(InteractionHand.MAIN_HAND, s2);
					p.gameMode.useItemOn(p, level, s2, InteractionHand.MAIN_HAND,
						new BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), Direction.UP, at.below(), false));
					stage = "attack";
					Entity z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
					if (z != null) {
						z.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
						level.addFreshEntity(z);
						ItemStack s3 = new ItemStack(item);
						p.setItemInHand(InteractionHand.MAIN_HAND, s3);
						p.attack(z);
						z.discard();
					}
					p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					p.getInventory().clearContent();
				} catch (Throwable t) {
					threw.add(id.getPath() + " (" + stage + "): " + t);
					LOG.error("[qa] item {} threw at {}", id, stage, t);
				}
			}
			// clean up any rifts/projectiles the items made
			for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(48), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
			check(threw.isEmpty(), "items", f("%d non-block items used, used on a block and swung at a zombie by a player; %d threw %s", n, threw.size(), threw));
			return true;
		}
	}

	// ------------------------------------------------------------------ dimensions: travel in, check arrival, walk, return home
	static boolean solid(final BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	static String arrival(final ServerLevel lvl, final ServerPlayer p) {
		BlockPos feet = p.blockPosition();
		List<String> bad = new ArrayList<>();
		BlockState below = lvl.getBlockState(feet.below());
		if (!solid(below)) {
			// allow standing on the top of a partial block at feet level
			BlockState at = lvl.getBlockState(feet);
			if (!(solid(at) && p.getY() > feet.getY())) {
				bad.add("not standing on solid ground (below=" + BuiltInRegistries.BLOCK.getKey(below.getBlock()) + ")");
			}
		}
		if (p.getY() < lvl.getMinY() + 2) {
			bad.add("in the void (y=" + p.getY() + ")");
		}
		for (BlockPos q : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
			if (lvl.getBlockState(q).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				bad.add("lava next to the arrival at " + q.toShortString());
				break;
			}
		}
		if (!lvl.noCollision(p)) {
			bad.add("inside blocks (feet=" + BuiltInRegistries.BLOCK.getKey(lvl.getBlockState(feet).getBlock()) + ", head="
				+ BuiltInRegistries.BLOCK.getKey(lvl.getBlockState(feet.above()).getBlock()) + ")");
		}
		return bad.isEmpty() ? null : String.join("; ", bad);
	}

	static BlockPos findBlock(final ServerLevel lvl, final BlockPos c, final Block b) {
		for (int dx = -32; dx <= 32; dx++) {
			for (int dz = -32; dz <= 32; dz++) {
				int top = lvl.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX() + dx, c.getZ() + dz);
				for (int y = Math.min(top + 2, lvl.getMaxY()); y >= Math.max(lvl.getMinY(), top - 96); y--) {
					BlockPos q = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
					if (lvl.getBlockState(q).is(b)) {
						return q;
					}
				}
			}
		}
		return null;
	}

	static final class DimVisit implements Step {
		final ResourceKey<Level> key;
		final int index;
		final String dimName;
		FakePlayer p;
		ServerLevel overworld;
		BlockPos start;
		int t;
		int walked;
		double worstChunkMs;
		long genTotal;
		int voidCols, lavaCols, cols;
		Vec3 arrival;

		DimVisit(final ResourceKey<Level> key, final int index) {
			this.key = key;
			this.index = index;
			this.dimName = key.identifier().getPath();
		}

		@Override
		public String name() {
			return "dimension " + key.identifier();
		}

		String area() {
			return "dim " + dimName;
		}

		@Override
		public boolean tick(final MinecraftServer server) throws Throwable {
			t++;
			if (t == 1) {
				overworld = server.overworld();
				p = fake(overworld, "QATraveller" + index);
				int sx = 900 + index * 320, sz = 900;
				overworld.getChunk(sx >> 4, sz >> 4);
				start = new BlockPos(sx, overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz), sz);
				if (p.level() != overworld) {
					teleport(p, overworld, Vec3.atBottomCenterOf(start));
				}
				p.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
				// Real path: an attuned Wayfinder used in the Overworld opens a rift to the dimension.
				Item wf = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(NS, "wayfinder_" + dimName));
				boolean usedWayfinder = false;
				if (wf != Items.AIR && wf != null) {
					Object before = staticField("dev.elsewhere.core.travel.RiftTravel", "lastRift");
					ItemStack stack = new ItemStack(wf);
					p.setItemInHand(InteractionHand.MAIN_HAND, stack);
					var res = p.gameMode.useItem(p, overworld, stack, InteractionHand.MAIN_HAND);
					Object after = staticField("dev.elsewhere.core.travel.RiftTravel", "lastRift");
					usedWayfinder = after != MISSING && after != null && after != before;
					check(usedWayfinder, area(), f("attuned Wayfinder (elsewhere:wayfinder_%s) used in the Overworld opens a rift (%s)", dimName, res));
					if (after instanceof Entity rift) {
						Vec3 rp = rift.position();
						rift.discard();
						LOG.info("[qa] rift opened at {}", rp);
					}
				} else {
					record("WARN", area(), "no attuned Wayfinder item elsewhere:wayfinder_" + dimName + "; travelling by command");
				}
				// Step through: the rift's own destination logic (RiftTravel.travelNow) or a plain teleport.
				Object went = reflectStatic("dev.elsewhere.core.travel.RiftTravel", "travelNow", new Class<?>[] {ServerPlayer.class, ResourceKey.class}, p, key);
				if (went == MISSING) {
					ServerLevel lvl = server.getLevel(key);
					lvl.getChunk(0, 0);
					int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
					teleport(p, lvl, new Vec3(0.5, Math.max(y, lvl.getMinY() + 1), 0.5));
					record("WARN", area(), "core travel (RiftTravel.travelNow) not present; teleported by command to 0,0");
				} else {
					check(Boolean.TRUE.equals(went), area(), "travel into the dimension (RiftTravel.travelNow) returns true");
				}
				boolean there = p.level().dimension().equals(key);
				check(there, area(), "player is in " + key.identifier() + " after travel (now in " + p.level().dimension().identifier() + ")");
				if (!there) {
					return true;
				}
				arrival = p.position();
				ServerLevel lvl = p.level();
				String bad = arrival(lvl, p);
				check(bad == null, area(), f("arrival at %.1f %.1f %.1f is safe%s", arrival.x, arrival.y, arrival.z, bad == null ? "" : ": " + bad));
				return false;
			}
			ServerLevel lvl = server.getLevel(key);
			if (walked < 9) {
				// walk one chunk east per tick: generate it, step onto its surface, check the column
				int x = (int)Math.floor(arrival.x) + (walked + 1) * 16, z = (int)Math.floor(arrival.z);
				long g0 = System.nanoTime();
				lvl.getChunk(x >> 4, z >> 4);
				lvl.getChunk(x >> 4, (z >> 4) + 1);
				lvl.getChunk(x >> 4, (z >> 4) - 1);
				long g = System.nanoTime() - g0;
				genTotal += g;
				worstChunkMs = Math.max(worstChunkMs, g / 3e6);
				for (int dz = -8; dz <= 8; dz += 4) {
					cols++;
					int h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z + dz);
					if (h <= lvl.getMinY() + 1) {
						voidCols++;
					} else if (lvl.getBlockState(new BlockPos(x, h - 1, z + dz)).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
						lavaCols++;
					}
				}
				int h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				p.snapTo(x + 0.5, h, z + 0.5, 90.0F, 0.0F);
				walked++;
				if (walked == 9) {
					check(true, area(), f("walked 9 chunks east (27 generated) in %d ms, worst %.0f ms/chunk; %d/%d sampled columns void, %d lava-topped",
						genTotal / 1_000_000, worstChunkMs, voidCols, cols, lavaCols));
					if (worstChunkMs > 2000) {
						record("WARN", area(), f("slow chunk generation: worst %.0f ms per chunk", worstChunkMs));
					}
				}
				return false;
			}
			// Return home: find the nearest Return Rift and step into it.
			boolean riftsHere = !dimName.equals("the_hush");
			Object enabled = reflectStatic("dev.elsewhere.core.riftruin.ReturnRifts", "enabledIn", new Class<?>[] {ResourceKey.class}, key);
			if (enabled instanceof Boolean b) {
				riftsHere = b;
			}
			boolean home = false;
			Object anchorObj = reflectStatic("dev.elsewhere.core.riftruin.ReturnRifts", "nearestRift", new Class<?>[] {ServerLevel.class, int.class, int.class},
				lvl, (int)arrival.x, (int)arrival.z);
			if (riftsHere && anchorObj instanceof BlockPos anchor) {
				double dist = Math.sqrt(anchor.distToCenterSqr(arrival.x, anchor.getY(), arrival.z));
				record(dist <= 320 ? "PASS" : "WARN", area(), f("nearest Return Rift anchor %s is %.0f blocks from the arrival (target <= ~300)", anchor.toShortString(), dist));
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						lvl.getChunk((anchor.getX() >> 4) + dx, (anchor.getZ() >> 4) + dz);
					}
				}
				Block riftBlock = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NS, "return_rift"));
				BlockPos found = findBlock(lvl, anchor, riftBlock);
				check(found != null, area(), "a Return Rift ruin (elsewhere:return_rift block) generated at the core's anchor " + anchor.toShortString()
					+ " (where the Frayed Wayfinder needle points)");
				if (found == null) {
					// module structure sets may place their rift ruins on their own grid: locate them like /locate structure
					var reg = lvl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
					List<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> hs = new ArrayList<>();
					reg.listElements().forEach(h -> {
						if (h.key().identifier().getPath().contains("return_rift")) {
							hs.add(h);
						}
					});
					var pair = hs.isEmpty() ? null : lvl.getChunkSource().getGenerator().findNearestMapStructure(lvl,
						net.minecraft.core.HolderSet.direct(hs), BlockPos.containing(arrival), 40, false);
					if (pair == null) {
						check(false, area(), "no Return Rift structure can be located within ~640 blocks of the arrival (/locate finds none)");
					} else {
						BlockPos sp = pair.getFirst();
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								lvl.getChunk((sp.getX() >> 4) + dx, (sp.getZ() >> 4) + dz);
							}
						}
						found = findBlock(lvl, sp, riftBlock);
						double dd = Math.sqrt(sp.distSqr(new BlockPos(anchor.getX(), sp.getY(), anchor.getZ())));
						check(found != null, area(), f("located structure %s at %s (%.0f blocks from arrival, %.0f from the core anchor) has a return_rift block",
							pair.getSecond().unwrapKey().map(k -> k.identifier().toString()).orElse("?"), sp.toShortString(),
							Math.sqrt(sp.distSqr(BlockPos.containing(arrival.x, sp.getY(), arrival.z))), dd));
					}
				}
				if (found != null) {
					p.snapTo(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, 0.0F, 0.0F);
					BlockState rs = lvl.getBlockState(found);
					rs.entityInside(lvl, found, p, InsideBlockEffectApplier.NOOP, true);
					home = p.level() == overworld;
					check(home, area(), "stepping into the Return Rift takes the player to the Overworld (now in " + p.level().dimension().identifier() + ")");
					if (home) {
						String bad = arrival(overworld, p);
						check(bad == null, area(), f("Overworld arrival from the Return Rift at %s is safe%s", p.blockPosition().toShortString(), bad == null ? "" : ": " + bad));
					}
				}
			} else if (!riftsHere) {
				record("PASS", area(), "no Return Rifts here by design (The Hush)");
			}
			if (!home) {
				// The attuned Wayfinder used inside its own dimension opens a rift home.
				Item wf = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(NS, "wayfinder_" + dimName));
				if (wf != null && wf != Items.AIR) {
					Object before = staticField("dev.elsewhere.core.travel.RiftTravel", "lastRift");
					ItemStack stack = new ItemStack(wf);
					p.setItemInHand(InteractionHand.MAIN_HAND, stack);
					p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(stack));
					p.gameMode.useItem(p, lvl, stack, InteractionHand.MAIN_HAND);
					Object after = staticField("dev.elsewhere.core.travel.RiftTravel", "lastRift");
					boolean opened = after != MISSING && after != null && after != before;
					check(opened, area(), "attuned Wayfinder used inside its own dimension opens a rift home");
					if (after instanceof Entity rift) {
						rift.discard();
					}
				}
				Object back = reflectStatic("dev.elsewhere.core.travel.RiftTravel", "travelNow", new Class<?>[] {ServerPlayer.class, ResourceKey.class}, p, null);
				home = p.level() == overworld;
				check(home, area(), "home travel (RiftTravel.travelNow home) returns to the Overworld (" + back + ")");
				if (home) {
					double d = p.position().distanceTo(Vec3.atBottomCenterOf(start));
					check(d < 40, area(), f("home lands near where the player left (%.0f blocks)", d));
				}
			}
			if (!home) {
				teleport(p, overworld, Vec3.atBottomCenterOf(start));
			}
			return true;
		}
	}

	// ------------------------------------------------------------------ bosses
	/** Adds/removes a fake player to the server's online-player list (bosses drop participants not in PlayerList). */
	@SuppressWarnings("unchecked")
	static void listPlayer(final MinecraftServer server, final ServerPlayer p, final boolean add) {
		try {
			var pl = server.getPlayerList();
			Field byUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
			Field list = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			byUuid.setAccessible(true);
			list.setAccessible(true);
			Map<UUID, ServerPlayer> m = (Map<UUID, ServerPlayer>)byUuid.get(pl);
			List<ServerPlayer> l = (List<ServerPlayer>)list.get(pl);
			if (add) {
				m.put(p.getUUID(), p);
				if (!l.contains(p)) {
					l.add(p);
				}
			} else {
				m.remove(p.getUUID());
				l.remove(p);
			}
		} catch (Throwable t) {
			LOG.warn("[qa] could not {} the fake player in the player list", add ? "list" : "unlist", t);
		}
	}

	static final class BossSetup implements Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Throwable {
			ServerLevel level = server.overworld();
			Map<String, EntityType<?>> bosses = new LinkedHashMap<>();
			for (Identifier id : ids(BuiltInRegistries.ENTITY_TYPE)) {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
				if (id.getPath().endsWith("_head") || id.getPath().endsWith("_tentacle") || id.getPath().contains("minion")) {
					continue; // parts of a boss, not bosses
				}
				boolean expected = EXPECTED_BOSSES.stream().anyMatch(b -> id.getPath().equals(b) || id.getPath().startsWith(b + "_") && !id.getPath().contains("minion")) || ReleaseSuite.isKingdomBoss(id.getPath());
				try {
					Entity probe = type.create(level, EntitySpawnReason.COMMAND);
					if (probe instanceof LivingEntity le && (EXPECTED_BOSSES.contains(id.getPath()) || (expected || le.getMaxHealth() >= 200.0F))) {
						bosses.put(id.getPath(), type);
					}
					if (probe != null) {
						probe.discard();
					}
				} catch (Throwable t) {
					LOG.error("[qa] creating {} threw", id, t);
					record("FAIL", "entity " + id.getPath(), "cannot be created: " + t);
				}
			}
			LOG.info("[qa] bosses to fight: {}", bosses.keySet());
			int i = 0;
			String shard = System.getProperty("elsewhereqa.shard", "0/1");
			int si = Integer.parseInt(shard.split("/")[0]), sn = Integer.parseInt(shard.split("/")[1]);
			for (Map.Entry<String, EntityType<?>> e : bosses.entrySet()) {
				String filter = System.getProperty("elsewhereqa.bosses", "");
				boolean pick = filter.isEmpty() ? i % sn == si : List.of(filter.split(",")).contains(e.getKey());
				if (pick) {
					STEPS.add(new BossFight(e.getKey(), e.getValue(), new BlockPos(30000 + (i % 5) * 500, 0, 30000 + (i / 5) * 500)));
				}
				i++;
			}
			LOG.info("[qa] boss shard {}: {} fights", shard, STEPS.size());
			return true;
		}
	}

	static final class BossFight implements Step {
		final String id;
		final EntityType<?> type;
		BlockPos pos;
		ServerLevel level;
		LivingEntity boss;
		FakePlayer player;
		boolean playerAdded;
		int t;
		int deathT = -1;
		Vec3 deathPos;
		float maxHp;
		float dealt;
		int hits;
		String spawnedBy = "direct spawn";
		int sealsDuring;
		int xpStart;
		int slowTicks, worstRun, run;
		double worstTick, sumTick;
		int samples;
		double[] window = new double[60];
		double worstAvg;
		float damage = Float.parseFloat(System.getProperty("elsewhereqa.hit", "20"));
		int lethalBlocked;
		int addsHit;
		final List<String> healthMarks = new ArrayList<>();
		int nextMark = 1;
		int stuckSince;
		// mechanic triggers the harness performs like a player (Mother Creak hearts, Selenne reflections)
		int heartsBroken;
		int heartsSeen = -1;
		BlockPos reflectionAt;
		boolean reflectionUsed;
		float hpBeforeTrigger = -1;
		boolean triggerResumed;
		float lastHp;

		BossFight(final String id, final EntityType<?> type, final BlockPos xz) {
			this.id = id;
			this.type = type;
			this.pos = xz;
		}

		@Override
		public String name() {
			return "boss " + id;
		}

		String area() {
			return "boss " + id;
		}

		void forceChunks(final boolean on) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					level.setChunkForced((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz, on);
				}
			}
		}

		int countSeals() {
			Block seal = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NS, "arena_seal"));
			if (seal == null || seal == Blocks.AIR) {
				return 0;
			}
			int n = 0;
			BlockPos c = boss != null ? boss.blockPosition() : pos;
			for (BlockPos q : BlockPos.betweenClosed(c.offset(-40, -12, -40), c.offset(40, 24, 40))) {
				if (level.getBlockState(q).is(seal)) {
					n++;
				}
			}
			return n;
		}

		/** Mother Creak: break a resin heart (stuns her 6 s). Selenne phase 3: stand at a water cauldron of the Moonring. Returns true if it did something. */
		boolean performMechanic() {
			BlockPos c = boss.blockPosition();
			Block heart = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NS, "resin_heart"));
			if (id.equals("mother_creak") && heart != null) {
				for (BlockPos q : BlockPos.betweenClosed(c.offset(-24, -8, -24), c.offset(24, 14, 24))) {
					if (level.getBlockState(q).is(heart)) {
						if (heartsSeen < 0) {
							heartsSeen = 0;
							for (BlockPos r : BlockPos.betweenClosed(c.offset(-24, -8, -24), c.offset(24, 14, 24))) {
								if (level.getBlockState(r).is(heart)) {
									heartsSeen++;
								}
							}
							check(heartsSeen > 0, area(), f("trigger present: %d resin hearts in the arena walls", heartsSeen));
						}
						hpBeforeTrigger = boss.getHealth();
						triggerResumed = false;
						level.destroyBlock(q, true, player);
						heartsBroken++;
						return true;
					}
				}
				if (heartsSeen < 0) {
					heartsSeen = 0;
					check(false, area(), "no resin hearts found in the arena: the invulnerable phase has no trigger (GAME BUG)");
				}
				return false;
			}
			Block cauldron = Blocks.WATER_CAULDRON;
			if (id.equals("selenne") && reflectionAt == null && boss.getHealth() / Math.max(1, maxHp) <= 0.32F) {
				for (BlockPos q : BlockPos.betweenClosed(c.offset(-20, -6, -20), c.offset(20, 6, 20))) {
					if (level.getBlockState(q).is(cauldron)) {
						reflectionAt = q.immutable();
						break;
					}
				}
				if (reflectionAt == null) {
					check(false, area(), "no water cauldron (reflection) near Selenne: phase 3 has no trigger");
					return false;
				}
				check(true, area(), "trigger present: water cauldron reflections around the Moonring");
				hpBeforeTrigger = boss.getHealth();
				triggerResumed = false;
				reflectionUsed = true;
				player.snapTo(reflectionAt.getX() + 0.5, reflectionAt.getY() + 1.0, reflectionAt.getZ() + 1.0, 0.0F, 0.0F);
				return true;
			}
			return false;
		}

		LivingEntity findBoss(final MinecraftServer server) {
			LivingEntity best = null;
			for (ServerLevel l : server.getAllLevels()) {
				for (Entity e : l.getEntities(type, e -> e instanceof LivingEntity && e.isAlive())) {
					if (best == null || e.tickCount < best.tickCount) {
						best = (LivingEntity)e;
					}
				}
			}
			return best;
		}

		@Override
		public boolean tick(final MinecraftServer server) throws Throwable {
			t++;
			if (t == 1) {
				level = server.overworld();
				forceChunks(true);
				level.getChunk(pos);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
				pos = new BlockPos(pos.getX(), Math.max(y, level.getSeaLevel() + 1), pos.getZ());
				player = fake(level, "QAHunter_" + id);
				if (player.level() != level) {
					teleport(player, level, Vec3.atBottomCenterOf(pos));
				}
				player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
				// Try every dev boss command the beta kept (elsewhere_dev_<module> boss <id>, elsewhere arena <id>), as the player.
				CommandSourceStack src = server.createCommandSourceStack().withEntity(player).withLevel(level).withPosition(Vec3.atBottomCenterOf(pos))
					.withSuppressedOutput();
				List<String> roots = new ArrayList<>();
				for (var node : server.getCommands().getDispatcher().getRoot().getChildren()) {
					if (node.getName().startsWith("elsewhere")) {
						roots.add(node.getName());
					}
				}
				for (String r : roots) {
					for (String form : new String[] {r + " boss " + id, r + " arena " + id}) {
						int rc = runCommand(server, src, form);
						if (rc > 0) {
							boss = findBoss(server);
							if (boss != null) {
								spawnedBy = "/" + form;
								break;
							}
						}
					}
					if (boss != null) {
						break;
					}
				}
				if (boss == null && id.equals("selenne")) {
					// the real mechanic: the arena command built a Moonring 18 blocks south; right-click its Moon Bloom like a player
					Block bloom = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NS, "moon_bloom"));
					BlockPos ring = pos.offset(0, 0, 18);
					BlockPos found = null;
					for (BlockPos q : BlockPos.betweenClosed(ring.offset(-3, -3, -3), ring.offset(3, 3, 3))) {
						if (level.getBlockState(q).is(bloom)) {
							found = q.immutable();
							break;
						}
					}
					if (found == null) {
						record("WARN", area(), "no Moon Bloom found in the Moonring built by /elsewhere arena selenne; falling back to a direct spawn");
					} else {
						teleport(player, level, Vec3.atBottomCenterOf(found.south(2)));
						level.getBlockState(found).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(found), Direction.UP, found, false));
						boss = findBoss(server);
						if (boss != null) {
							spawnedBy = "Moon Bloom right-click in a built Moonring";
						} else {
							record("WARN", area(), "right-clicking the Moon Bloom summoned no Selenne; falling back to a direct spawn");
						}
					}
				}
				if (boss == null) {
					for (int dx = -8; dx <= 8; dx++) {
						for (int dz = -8; dz <= 8; dz++) {
							level.setBlock(pos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
						}
					}
					Entity e = type.create(level, EntitySpawnReason.COMMAND);
					if (!(e instanceof LivingEntity le)) {
						check(false, area(), "could not be created");
						forceChunks(false);
						return true;
					}
					boss = le;
					boss.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
					if (boss instanceof Mob mob) {
						try {
							mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
						} catch (Throwable ignored) {
							// plain spawn still counts
						}
					}
					level.addFreshEntity(boss);
					player.snapTo(boss.getX() + 6, boss.getY(), boss.getZ(), 90.0F, 0.0F);
				} else {
					// the dev command built the arena (maybe in the boss's home dimension) and moved the player there
					forceChunks(false);
					level = (ServerLevel)boss.level();
					pos = boss.blockPosition();
					forceChunks(true);
					if (player.level() != level) {
						teleport(player, level, boss.position().add(6, 0, 0));
					}
				}
				try {
					ServerLevel pl = player.level();
					if (!pl.players().contains(player)) {
						pl.addNewPlayer(player);
					}
					playerAdded = true;
					listPlayer(server, player, true);
				} catch (Throwable tt) {
					LOG.warn("[qa] could not add fake player to the level", tt);
				}
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
				lastHp = boss.getHealth();
				xpStart = player.totalExperience;
				return false;
			}
			// tick-time window (skip the spawn tick)
			if (t > 3 && deathT < 0) {
				double ms = lastTickMs;
				samples++;
				sumTick += ms;
				worstTick = Math.max(worstTick, ms);
				window[t % window.length] = ms;
				if (t > window.length + 3) {
					double avg = 0;
					for (double w : window) {
						avg += w;
					}
					worstAvg = Math.max(worstAvg, avg / window.length);
				}
				if (ms > 50) {
					slowTicks++;
					run++;
					worstRun = Math.max(worstRun, run);
				} else {
					run = 0;
				}
			}
			if (player != null && player.isAlive()) {
				player.setHealth(player.getMaxHealth());
				player.getFoodData().setFoodLevel(20);
			}
			if (deathT >= 0) {
				return afterDeath(server);
			}
			if (boss.isRemoved() || boss.isDeadOrDying()) {
				if (boss.isDeadOrDying() || boss.getRemovalReason() == Entity.RemovalReason.KILLED) {
					deathT = t;
					deathPos = boss.position();
					check(true, area(), f("dies from capped player hits after %.1f s (%d hits, %.0f damage dealt, max HP %.0f, %d hits on adds/parts); HP marks %s", (t - 20) / 20.0, hits,
						dealt, maxHp, addsHit, healthMarks));
					return false;
				}
				// a boss that "leaves" (phase teleport / replaced by another entity) - look for its replacement
				LivingEntity again = findBoss(level.getServer());
				if (again != null && again != boss) {
					boss = again;
					return false;
				}
				check(false, area(), f("was removed (%s) before dying, HP %.1f/%.0f", boss.getRemovalReason(), boss.getHealth(), maxHp));
				return cleanup();
			}
			if (t == 20) {
				maxHp = boss.getMaxHealth();
				check(boss.isAlive(), area(), f("spawns via %s (HP %.0f/%.0f, armor %d)", spawnedBy, boss.getHealth(), maxHp, boss.getArmorValue()));
				sealsDuring = countSeals();
			}
			if (t > 20 && t % 4 == 0) {
				// keep the hunter beside the boss like a melee player
				Vec3 bp = boss.position();
				if (reflectionAt == null && player.position().distanceTo(bp) > 8) {
					player.snapTo(bp.x + 4, bp.y, bp.z, 90.0F, 0.0F);
				}
				// stuck for 5 s: do what a player has to do to end the invulnerable phase
				if (t - Math.max(stuckSince, 20) > 100 && performMechanic()) {
					stuckSince = t;
				}
				float before = boss.getHealth();
				boss.setInvulnerableTime(0);
				boss.hurtServer(level, level.damageSources().playerAttack(player), damage);
				hits++;
				float d = before - boss.getHealth();
				if (d > 0) {
					dealt += d;
				}
				// like a player: when the boss stops taking damage for 5 s, also kill the adds/parts around it (heads, tentacles, shields)
				if (t - Math.max(stuckSince, 20) > 100) {
					for (LivingEntity add : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(40), e -> e != boss && e.isAlive()
						&& !(e instanceof ServerPlayer) && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS))) {
						add.setInvulnerableTime(0);
						add.hurtServer(level, level.damageSources().playerAttack(player), damage);
						addsHit++;
					}
				}
				float frac = boss.getHealth() / Math.max(1, maxHp);
				while (nextMark <= 9 && frac <= 1.0F - nextMark * 0.1F) {
					healthMarks.add(f("%d%%@%.0fs", 100 - nextMark * 10, (t - 20) / 20.0));
					nextMark++;
				}
				if (boss.getHealth() < lastHp - 0.01F && hpBeforeTrigger >= 0 && !triggerResumed) {
					triggerResumed = true;
					check(true, area(), f("damage resumed after the harness triggered the mechanic (%s, %d hearts broken)", reflectionUsed ? "stood at a reflection cauldron" : "broke a resin heart", heartsBroken));
				}
				if (boss.getHealth() < lastHp - 0.01F) {
					stuckSince = t;
					lastHp = boss.getHealth();
				}
				// guards or invulnerable phases: if nothing moved for 15 s, double the hit (and note it)
				if (t - Math.max(stuckSince, 20) > 300 && damage >= 80) {
					check(false, area(), f("takes no damage from player hits for 45 s (HP %.0f/%.0f, hit raised to %.0f) - invulnerable phase never ends without its arena/trigger?",
						boss.getHealth(), maxHp, damage));
					boss.discard();
					return cleanup();
				}
				if (t - Math.max(stuckSince, 20) > 300) {
					damage *= 2;
					stuckSince = t;
					record("WARN", area(), f("HP stuck at %.0f/%.0f for 15 s (invulnerable phase or guard?); raising hit to %.0f", boss.getHealth(), maxHp, damage));
				}
			}
			if (t == 20 * 120) {
				check(false, area(), f("did not die within 120 s of capped hits (HP %.0f/%.0f, %d hits, dealt %.0f)", boss.getHealth(), maxHp, hits, dealt));
				boss.discard();
				return cleanup();
			}
			return false;
		}

		boolean afterDeath(final MinecraftServer server) {
			if (t - deathT == 80) {
				List<String> drops = new ArrayList<>();
				for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(deathPos)).inflate(48))) {
					drops.add(e.getItem().getCount() + "x" + BuiltInRegistries.ITEM.getKey(e.getItem().getItem()));
				}
				int invItems = 0;
				for (ItemStack s : player.getInventory()) {
					if (!s.isEmpty() && s.getItem() != Items.NETHERITE_SWORD) {
						invItems += s.getCount();
						drops.add("inv:" + s.getCount() + "x" + BuiltInRegistries.ITEM.getKey(s.getItem()));
					}
				}
				int chests = 0;
				BlockPos c = BlockPos.containing(deathPos);
				for (BlockPos q : BlockPos.betweenClosed(c.offset(-16, -6, -16), c.offset(16, 10, 16))) {
					BlockEntity be = level.getBlockEntity(q);
					if (be instanceof net.minecraft.world.Container ct && !ct.isEmpty()) {
						chests++;
					}
				}
				ReleaseSuite.afterBossDeath(level, player, type, id, deathPos, drops, xpStart);
				boolean anyElsewhere = drops.stream().anyMatch(s -> s.contains("elsewhere:"));
				check(!drops.isEmpty() || chests > 0, area(), f("drops %s%s", drops, chests > 0 ? " + " + chests + " filled container(s) nearby" : ""));
				if (!anyElsewhere && chests == 0) {
					record("WARN", area(), "dropped no elsewhere: item (no signature/boss loot?)");
				}
			}
			if (t - deathT == 200) {
				int seals = countSeals();
				check(seals == 0, area(), f("arena unsealed after death (%d arena_seal blocks during the fight, %d remain 10 s after)", sealsDuring, seals));
				double avg = samples == 0 ? 0 : sumTick / samples;
				boolean lag = worstAvg > 50 || worstRun > 60;
				check(!lag, area(), f("tick time during the fight: avg %.1f ms, worst 3 s average %.1f ms, worst tick %.0f ms, %d ticks over 50 ms (longest run %d)",
					avg, worstAvg, worstTick, slowTicks, worstRun));
				// leftover minions or projectiles
				int left = level.getEntitiesOfClass(Entity.class, new AABB(BlockPos.containing(deathPos)).inflate(64),
					e -> !(e instanceof ServerPlayer) && !(e instanceof ItemEntity) && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS)).size();
				record(left == 0 ? "PASS" : "WARN", area(), left + " elsewhere entities (minions/projectiles) left 10 s after the boss died");
				return cleanup();
			}
			return false;
		}

		boolean cleanup() {
			try {
				if (playerAdded && player.level().players().contains(player)) {
					player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
				}
				listPlayer(level.getServer(), player, false);
			} catch (Throwable tt) {
				LOG.warn("[qa] could not remove fake player", tt);
			}
			for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(96, 64, 96), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
			forceChunks(false);
			return true;
		}
	}
}
