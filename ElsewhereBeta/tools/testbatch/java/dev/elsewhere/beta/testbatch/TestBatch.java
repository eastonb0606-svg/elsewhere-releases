package dev.elsewhere.beta.testbatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test batch (s-testbatch, 3.0.0): runs several module self-tests ONE AFTER ANOTHER in ONE server boot, instead of one
 * boot per module. Test tooling only: tools/testbatch.sh copies this package (plus three mixins and a generated
 * {@link TestBatchHooks}) into its own copy of the merged tree; the shipped jar never contains it.
 *
 * <p>{@code -Delsewhere.testbatch=a,b,c} (ids = the {@code -D<id>.selftest} prefixes, in run order). For each module:
 * <ol>
 * <li>{@code <id>.selftest=true}, {@code <id>.selftest.exit=true} and the module's own flags
 * ({@code -Delsewhere.testbatch.flags.<id>=k=v;k2=v2}) are set; everything is restored when the module ends.</li>
 * <li>Its BetaInit hook ({@link TestBatchHooks}, generated from BetaInit.java) runs while Fabric event registrations
 * are captured: SERVER_STARTING/STARTED listeners are called at once (the server is already up), server/level tick
 * listeners are registered behind a gate that is open only while this module runs. Other listeners register normally.</li>
 * <li>The module's own {@code server.halt(false)} (its "done" signal under {@code .exit}) is intercepted and ends the
 * module instead of the server. A module that throws (hook, start or tick) is marked CRASH; one that runs past
 * {@code elsewhere.testbatch.timeout} s (default 1200, per module {@code elsewhere.testbatch.timeout.<id>}) is TIMEOUT.
 * Either way the batch goes on with the next module.</li>
 * <li>Isolation between modules: chunks the module force-loaded are released, every non-player entity is discarded,
 * and with {@code elsewhere.testbatch.flatReset=<r>} (superflat worlds) the overworld chunks within r chunks of 0,0 are
 * put back to the world's flat layers. Each module starts at the fresh world's time of day, with the weather cycle reset.</li>
 * </ol>
 * Log markers: {@code [testbatch] BEGIN <id>}, {@code [testbatch] END <id> <DONE|CRASH|TIMEOUT> <s>s}, and
 * {@code [testbatch] FINISHED} at the end (tools/testbatch/summary.py splits the log on them). No per-tick cost when off:
 * nothing is registered without the property, and the mixins only read one static boolean.
 */
public final class TestBatch {
	private TestBatch() {}

	public static final Logger LOG = LoggerFactory.getLogger("testbatch");
	/** True only in a batch JVM ({@code -Delsewhere.testbatch} set and the capture mixin verified). */
	public static volatile boolean ENABLED;
	/** The module whose hook is being registered (event capture is on while this is set). */
	public static volatile Mod capturing;

	private static final Deque<String> QUEUE = new ArrayDeque<>();
	private static final List<String> RESULTS = new ArrayList<>();
	private static final int GAP_TICKS = 40;
	private static Mod current;
	private static boolean allowHalt, finished, serverUp;
	private static int gap = GAP_TICKS, total, index;
	private static long batchStart;
	private static int flatReset;
	private static long clockStart = -1;
	private static BlockState[] flatLayers;
	private static final Mod PROBE = new Mod("(probe)", 0);
	private static int probeHits;

	public static final class Mod {
		final String id;
		final int timeoutS;
		final long t0 = System.nanoTime();
		final List<ServerLifecycleEvents.ServerStarting> starting = new ArrayList<>();
		final List<ServerLifecycleEvents.ServerStarted> started = new ArrayList<>();
		final Map<String, String> savedProps = new LinkedHashMap<>();
		final List<ServerLevel> forcedLevels = new ArrayList<>();
		final List<long[]> forcedChunks = new ArrayList<>();
		volatile boolean active, done;
		String result = "DONE", why = "", haltFrom = "";
		int tickHooks, otherHooks;

		Mod(String id, int timeoutS) {
			this.id = id;
			this.timeoutS = timeoutS;
		}

		boolean live() {
			return active && !done && current == this;
		}

		void crash(String where, Throwable t) {
			LOG.error("[testbatch] {} CRASH in {}: {}", id, where, t.toString(), t);
			if (!"CRASH".equals(result)) why = " (" + where + ": " + t + ")";
			result = "CRASH";
			done = true;
		}

		double secs() {
			return (System.nanoTime() - t0) / 1e9;
		}
	}

	/** Called once from BetaHooks.afterModules() in the test copy. Does nothing without -Delsewhere.testbatch. */
	public static void init() {
		String spec = System.getProperty("elsewhere.testbatch", "").trim();
		if (spec.isEmpty()) return;
		// verify the capture mixin is applied before trusting the batch
		Event<Runnable> probe = EventFactory.createArrayBacked(Runnable.class, ls -> () -> {
			for (Runnable r : ls) r.run();
		});
		capturing = PROBE;
		try {
			probe.register(() -> {});
		} finally {
			capturing = null;
		}
		if (probeHits != 1) {
			LOG.error("[testbatch] event capture mixin is not applied (probe {}): batch disabled", probeHits);
			return;
		}
		for (String s : spec.split(",")) {
			s = s.trim();
			if (s.isEmpty()) continue;
			if (TestBatchHooks.has(s)) QUEUE.add(s);
			else LOG.error("[testbatch] unknown module '{}' (known: {})", s, String.join(",", TestBatchHooks.IDS));
		}
		total = QUEUE.size();
		flatReset = Integer.getInteger("elsewhere.testbatch.flatReset", 0);
		ENABLED = true;
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			serverUp = true;
			batchStart = System.nanoTime();
			if (flatReset > 0) flatLayers = sampleFlatLayers(srv.overworld());
			clockStart = srv.overworld().getOverworldClockTime();
			LOG.info("[testbatch] PLAN {} module(s): {}{}", total, String.join(",", QUEUE),
				flatReset > 0 ? " | flat reset r=" + flatReset + " layers=" + (flatLayers == null ? "none (not a flat world)" : flatLayers.length) : "");
		});
		ServerTickEvents.END_SERVER_TICK.register(TestBatch::endTick);
	}

	// ---- capture (called from TestBatchEventMixin) ------------------------------------------------------------------

	/** Returns true when the registration was taken over (the mixin then cancels the original one). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static boolean capture(Object event, Identifier phase, Object listener) {
		Mod m = capturing;
		if (m == null) return false;
		if (m == PROBE) {
			probeHits++;
			return true;
		}
		if (event == ServerLifecycleEvents.SERVER_STARTING) {
			m.starting.add((ServerLifecycleEvents.ServerStarting) listener);
			return true;
		}
		if (event == ServerLifecycleEvents.SERVER_STARTED) {
			m.started.add((ServerLifecycleEvents.ServerStarted) listener);
			return true;
		}
		Object gated;
		if (event == ServerTickEvents.START_SERVER_TICK) {
			ServerTickEvents.StartTick l = (ServerTickEvents.StartTick) listener;
			gated = (ServerTickEvents.StartTick) s -> {
				if (m.live()) try {
					l.onStartTick(s);
				} catch (Throwable t) {
					m.crash("start-of-tick hook", t);
				}
			};
		} else if (event == ServerTickEvents.END_SERVER_TICK) {
			ServerTickEvents.EndTick l = (ServerTickEvents.EndTick) listener;
			gated = (ServerTickEvents.EndTick) s -> {
				if (m.live()) try {
					l.onEndTick(s);
				} catch (Throwable t) {
					m.crash("end-of-tick hook", t);
				}
			};
		} else if (event == ServerTickEvents.START_LEVEL_TICK) {
			ServerTickEvents.StartLevelTick l = (ServerTickEvents.StartLevelTick) listener;
			gated = (ServerTickEvents.StartLevelTick) w -> {
				if (m.live()) try {
					l.onStartTick(w);
				} catch (Throwable t) {
					m.crash("start-of-level-tick hook", t);
				}
			};
		} else if (event == ServerTickEvents.END_LEVEL_TICK) {
			ServerTickEvents.EndLevelTick l = (ServerTickEvents.EndLevelTick) listener;
			gated = (ServerTickEvents.EndLevelTick) w -> {
				if (m.live()) try {
					l.onEndTick(w);
				} catch (Throwable t) {
					m.crash("end-of-level-tick hook", t);
				}
			};
		} else {
			m.otherHooks++;
			return false; // registered normally (and stays registered, as in a single-module boot)
		}
		capturing = null;
		try {
			((Event) event).register(phase, gated);
		} finally {
			capturing = m;
		}
		m.tickHooks++;
		return true;
	}

	// ---- halt and forced chunks (called from the server/level mixins) -----------------------------------------------

	/** True = swallow this halt: it is the running module's "done" signal. */
	public static boolean interceptHalt(MinecraftServer srv) {
		if (allowHalt || !serverUp) return false;
		Mod m = current;
		String from = caller();
		if (m == null) {
			LOG.warn("[testbatch] halt outside a running module (from {}): stopping; not run: {}", from, String.join(",", QUEUE));
			return false;
		}
		if (m.done) return true; // a second done signal from the module that is ending
		m.done = true;
		m.haltFrom = from;
		return true;
	}

	public static void onForced(ServerLevel level, int x, int z) {
		Mod m = current;
		if (m == null || !m.active) return;
		m.forcedLevels.add(level);
		m.forcedChunks.add(new long[] {x, z});
	}

	private static String caller() {
		return StackWalker.getInstance().walk(s -> s.map(f -> f.getClassName() + ":" + f.getLineNumber())
			.filter(c -> c.startsWith("dev.elsewhere.") && !c.startsWith("dev.elsewhere.beta.testbatch.") && !c.contains(".mixin."))
			.findFirst().orElse("?"));
	}

	// ---- the run loop ----------------------------------------------------------------------------------------------

	private static void endTick(MinecraftServer srv) {
		if (finished || !serverUp) return;
		Mod m = current;
		if (m != null) {
			if (!m.done) {
				if (m.secs() < m.timeoutS) return;
				LOG.error("[testbatch] {} TIMEOUT: no halt (done signal) after {} s", m.id, m.timeoutS);
				m.result = "TIMEOUT";
				m.why = " (no done signal within " + m.timeoutS + " s)";
				m.done = true;
			}
			end(srv, m);
			current = null;
			gap = GAP_TICKS;
			return;
		}
		if (gap-- > 0) return;
		if (QUEUE.isEmpty()) {
			finish(srv);
			return;
		}
		begin(srv, QUEUE.removeFirst());
	}

	private static void begin(MinecraftServer srv, String id) {
		int timeout = Integer.getInteger("elsewhere.testbatch.timeout." + id, Integer.getInteger("elsewhere.testbatch.timeout", 1200));
		Mod m = new Mod(id, timeout);
		current = m;
		index++;
		LOG.info("[testbatch] BEGIN {} ({}/{}, timeout {} s)", id, index, total, timeout);
		freshDay(srv);
		setProp(m, id + ".selftest", "true");
		setProp(m, id + ".selftest.exit", "true");
		String flags = System.getProperty("elsewhere.testbatch.flags." + id, "");
		for (String kv : flags.split(";")) {
			int eq = kv.indexOf('=');
			if (eq > 0) setProp(m, kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
		}
		capturing = m;
		try {
			TestBatchHooks.register(id);
		} catch (Throwable t) {
			m.crash("hook registration", t);
		} finally {
			capturing = null;
		}
		m.active = true;
		LOG.info("[testbatch] {} hooks: {} start, {} tick (gated), {} other{}", id, m.starting.size() + m.started.size(), m.tickHooks,
			m.otherHooks, m.savedProps.size() > 2 ? " | flags " + flags : "");
		if (m.done) return;
		if (m.starting.isEmpty() && m.started.isEmpty() && m.tickHooks == 0) {
			m.crash("hook registration", new IllegalStateException("the hook registered no start or tick listener"));
			return;
		}
		capturing = m; // listeners that the start hooks register belong to this module too
		try {
			for (int i = 0; i < m.starting.size() && !m.done; i++) m.starting.get(i).onServerStarting(srv);
			for (int i = 0; i < m.started.size() && !m.done; i++) m.started.get(i).onServerStarted(srv);
		} catch (Throwable t) {
			m.crash("server-started hook", t);
		} finally {
			capturing = null;
		}
	}

	private static void end(MinecraftServer srv, Mod m) {
		m.active = false;
		for (Map.Entry<String, String> e : m.savedProps.entrySet()) {
			if (e.getValue() == null) System.clearProperty(e.getKey());
			else System.setProperty(e.getKey(), e.getValue());
		}
		String line = String.format("%s %s %.1fs%s", m.id, m.result, m.secs(), m.why);
		LOG.info("[testbatch] END {}{}", line, m.haltFrom.isEmpty() ? "" : " (done signal from " + m.haltFrom + ")");
		RESULTS.add(line);
		cleanup(srv, m);
	}

	private static void finish(MinecraftServer srv) {
		finished = true;
		LOG.info("[testbatch] FINISHED {} module(s) in {} s", RESULTS.size(), Math.round((System.nanoTime() - batchStart) / 1e9));
		for (String r : RESULTS) LOG.info("[testbatch] RESULT {}", r);
		allowHalt = true;
		srv.halt(false);
	}

	private static void setProp(Mod m, String k, String v) {
		if (!m.savedProps.containsKey(k)) m.savedProps.put(k, System.getProperty(k));
		System.setProperty(k, v);
	}

	// ---- isolation between modules ---------------------------------------------------------------------------------

	private static void cleanup(MinecraftServer srv, Mod m) {
		long t0 = System.nanoTime();
		int unforced = 0, discarded = 0, reset = 0;
		try {
			for (int i = 0; i < m.forcedChunks.size(); i++) {
				long[] c = m.forcedChunks.get(i);
				if (m.forcedLevels.get(i).setChunkForced((int) c[0], (int) c[1], false)) unforced++;
			}
			if (!"false".equals(System.getProperty("elsewhere.testbatch.clean"))) {
				for (ServerLevel level : srv.getAllLevels()) {
					List<Entity> list = new ArrayList<>();
					for (Entity e : level.getAllEntities()) if (e != null && !(e instanceof Player) && !e.isPassenger()) list.add(e);
					for (Entity e : list) {
						if (e.isRemoved()) continue;
						e.ejectPassengers();
						e.discard();
						discarded++;
					}
				}
			}
			if (flatReset > 0 && flatLayers != null) reset = resetFlat(srv.overworld());
		} catch (Throwable t) {
			LOG.error("[testbatch] cleanup after {} failed: {}", m.id, t.toString(), t);
		}
		LOG.info("[testbatch] cleanup after {}: {} forced chunk(s) released, {} entities discarded, {} block(s) reset to flat ({} ms)", m.id,
			unforced, discarded, reset, (System.nanoTime() - t0) / 1_000_000);
	}

	/**
	 * Every module starts at the time of day and weather of a fresh world (as in its own boot): the overworld clock goes
	 * back to its value at server start and the weather cycle restarts clear. Tactics and spawns depend on day/night.
	 */
	private static void freshDay(MinecraftServer srv) {
		try {
			ServerLevel ow = srv.overworld();
			if (clockStart >= 0) srv.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD)
				.ifPresent(h -> ow.clockManager().setTotalTicks(h, clockStart));
			ow.resetWeatherCycle();
		} catch (Throwable t) {
			LOG.error("[testbatch] could not reset the clock/weather: {}", t.toString(), t);
		}
	}

	/** The flat world's layer column (bottom up) at 0,0 before any module ran; null if it is not a flat world. */
	private static BlockState[] sampleFlatLayers(ServerLevel level) {
		List<BlockState> out = new ArrayList<>();
		int min = level.getMinY();
		for (int y = min; y < min + 64; y++) {
			BlockState s = level.getBlockState(new BlockPos(0, y, 0));
			if (s.isAir()) break;
			out.add(s);
		}
		// a flat world: the same column 40 blocks away
		for (int i = 0; i < out.size(); i++) if (level.getBlockState(new BlockPos(40, min + i, 40)) != out.get(i)) return null;
		if (!level.getBlockState(new BlockPos(40, min + out.size(), 40)).isAir()) return null;
		return out.isEmpty() ? null : out.toArray(new BlockState[0]);
	}

	/**
	 * Puts every block back to the flat layers in the overworld chunks within flatReset chunks of 0,0 (loaded if needed)
	 * and in every other overworld chunk still loaded (the module's force-loaded chunks are only released, not yet
	 * unloaded, when this runs), so a module's roads, castles or arenas never reach the next module.
	 */
	private static int resetFlat(ServerLevel level) {
		int n = 0, min = level.getMinY();
		BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		java.util.LinkedHashSet<Long> todo = new java.util.LinkedHashSet<>();
		for (int cx = -flatReset; cx <= flatReset; cx++) for (int cz = -flatReset; cz <= flatReset; cz++) todo.add(net.minecraft.world.level.ChunkPos.pack(cx, cz));
		level.getChunkSource().chunkMap.forEachReadyToSendChunk(c -> todo.add(c.getPos().pack()));
		for (long key : todo) {
			int cx = net.minecraft.world.level.ChunkPos.getX(key), cz = net.minecraft.world.level.ChunkPos.getZ(key);
			LevelChunk c = level.getChunk(cx, cz);
			LevelChunkSection[] secs = c.getSections();
			for (int si = 0; si < secs.length; si++) {
				int baseY = min + si * 16;
				boolean ground = baseY < min + flatLayers.length;
				if (!ground && secs[si].hasOnlyAir()) continue;
				for (int ly = 0; ly < 16; ly++) {
					int y = baseY + ly, layer = y - min;
					BlockState want = layer < flatLayers.length ? flatLayers[layer] : air;
					for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
						if (secs[si].getBlockState(lx, ly, lz) == want) continue;
						p.set((cx << 4) + lx, y, (cz << 4) + lz);
						level.setBlock(p, want, flags);
						n++;
					}
				}
			}
		}
		return n;
	}
}
