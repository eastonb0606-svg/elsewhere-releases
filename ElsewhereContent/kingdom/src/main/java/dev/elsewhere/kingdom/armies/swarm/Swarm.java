package dev.elsewhere.kingdom.armies.swarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Swarm-as-one for soldiers (ARMIES.md 3.2-3.4): a squad moves and thinks as one unit, so a big fight costs the server far less
 * than the same soldiers each thinking alone.
 * <ul>
 * <li><b>Shared pathing</b> ({@link SwarmPaths}): one leader path per start section and 4x4x4 goal cell, reused for 100 ticks by every
 * squad marching there; squad mates closing on the same enemy share one approach path (10 ticks).</li>
 * <li><b>Followers steer</b> ({@link SlotSteer}): no A*; columns trail the leader's walked path (ribbon); a stuck follower first joins the
 * leader's path, then asks for one budgeted 400-node path.</li>
 * <li><b>Squad target selection</b> ({@link TargetAssigner}): one pass per squad per 10 ticks with shared perception; at most 3 melee per
 * enemy, the kingdom's tokens on a player, an approach angle per attacker; the rest hold formation.</li>
 * <li><b>No pushing between allies</b> ({@link SwarmPush}) in R1-R2; R2 pushes every 2nd tick.</li>
 * </ul>
 * Rings (3.2) are horizontal distance to the nearest player or test observer, with 8-block hysteresis; a ring is kept at least 100 ticks
 * before demotion, promotion is immediate, and a soldier fighting a player is always R0. <b>R0 keeps every per-soldier behaviour</b>
 * (own targets, own line-of-sight rays, own paths, pushing), so the player-facing fight is unchanged.
 * <p>Kill switch: {@code "swarm"} in {@code config/elsewhere_armies.json} (default {@code true}; system property
 * {@code elsewhere.armies.swarm}); it also follows {@code enabled}. Off, every hook returns at once and the 3.0 code paths run exactly.
 * Other modules' self-tests ({@code -D<x>.selftest}) run with it off, like the caps and waves.
 */
public final class Swarm {
	private Swarm() {}

	/** Whether the swarm runs now (config, the {@code enabled} switch and the test pin; tests may flip it with {@link #setActive}). */
	private static boolean active;
	/** The {@code swarm} key as read from the config (before the {@code enabled} switch and the test pin). */
	public static boolean configured = true;
	/** True once the key exists in the config file (written with its default when missing). */
	public static boolean keyPresent;
	private static boolean done;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static boolean on() {
		return active;
	}

	/** Called once from {@code Armies.init()} after {@code ArmiesConfig.load()}. */
	public static void init(boolean armiesEnabled, boolean otherSelfTest) {
		if (done) return;
		done = true;
		load();
		active = armiesEnabled && configured && !otherSelfTest;
		if (armiesEnabled) SwarmCommands.init();
		KingdomContent.LOG.info("Armies swarm-as-one is {} (config/elsewhere_armies.json key \"swarm\")", active ? "on" : "off");
	}

	/** Tests and the bench A/B: turns the swarm on or off at runtime and drops every cache. */
	public static void setActive(boolean on) {
		active = on;
		resetCaches();
	}

	public static void resetCaches() {
		SwarmPaths.clear();
		TargetAssigner.clear();
		SwarmSquads.clear();
		STATE.clear();
	}

	static void load() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere_armies.json");
		try {
			JsonObject o = Files.exists(p) ? GSON.fromJson(Files.readString(p), JsonObject.class) : null;
			if (o == null) o = new JsonObject();
			if (o.has("swarm")) configured = o.get("swarm").getAsBoolean();
			else {
				o.addProperty("swarm", true);
				Files.createDirectories(p.getParent());
				Files.writeString(p, GSON.toJson(o));
			}
			keyPresent = true;
		} catch (Exception e) {
			KingdomContent.LOG.warn("elsewhere_armies.json: swarm key unreadable ({}); swarm stays {}", e.toString(), configured ? "on" : "off");
		}
		if (System.getProperty("elsewhere.armies.swarm") != null) configured = Boolean.getBoolean("elsewhere.armies.swarm");
	}

	// ---- rings (ARMIES.md 3.2) -------------------------------------------------------------------------------------------------

	public static final double[] OUTER = {24, 48, 96};
	public static final int HYSTERESIS = 8, MIN_TICKS_IN_RING = 100, RECHECK = 10;

	static final class State {
		int ring = -1;
		long ringSince = Long.MIN_VALUE / 2, checked = Long.MIN_VALUE / 2;
	}

	private static final Map<KingdomSoldier, State> STATE = new WeakHashMap<>();
	public static long ringChanges;

	/**
	 * The soldier's ring, 0-3, re-evaluated at most every {@value #RECHECK} ticks. With no player or observer anywhere in its level it is
	 * R1 while {@code KingdomPerf.forceActive} (benchmarks fight at full AI) and R3 otherwise.
	 */
	public static int ring(KingdomSoldier s) {
		long now = s.level().getGameTime();
		State st = STATE.computeIfAbsent(s, k -> new State());
		if (st.ring >= 0 && now - st.checked < RECHECK && now >= st.checked) return st.ring;
		st.checked = now;
		int want = fightingPlayer(s) ? 0 : distanceRing(s, st.ring);
		if (st.ring < 0) {
			st.ring = want;
			st.ringSince = now;
		} else if (want < st.ring || (want > st.ring && now - st.ringSince >= MIN_TICKS_IN_RING)) {
			st.ring = want;
			st.ringSince = now;
			ringChanges++;
		}
		return st.ring;
	}

	/** Player-first: fighting a player, or hurt by one in the last 5 s. */
	static boolean fightingPlayer(KingdomSoldier s) {
		LivingEntity t = s.getTarget();
		if (t instanceof Player p && !p.isSpectator() && p.isAlive()) return true;
		return s.getLastHurtByMob() instanceof Player && s.tickCount - s.getLastHurtByMobTimestamp() < 100;
	}

	/** Ring by horizontal distance with hysteresis: promote 8 blocks inside the inner boundary, demote 8 blocks outside the outer one. */
	static int distanceRing(KingdomSoldier s, int cur) {
		double best = Double.MAX_VALUE;
		boolean any = false;
		if (s.level() instanceof ServerLevel sl) for (Player p : sl.players()) {
			if (p.isSpectator()) continue;
			any = true;
			best = Math.min(best, horiz2(p.position(), s.position()));
		}
		for (Vec3 o : KingdomPerf.testObservers) {
			any = true;
			best = Math.min(best, horiz2(o, s.position()));
		}
		if (!any) return KingdomPerf.forceActive ? 1 : 3;
		double d = Math.sqrt(best);
		return ringFor(d, cur);
	}

	/** Pure ring rule (self-test): {@code cur} -1 means no previous ring. */
	public static int ringFor(double d, int cur) {
		if (cur < 0) {
			for (int r = 0; r < OUTER.length; r++) if (d <= OUTER[r]) return r;
			return 3;
		}
		int r = cur;
		while (r > 0 && d <= OUTER[r - 1] - HYSTERESIS) r--;
		while (r < 3 && d > OUTER[r] + HYSTERESIS) r++;
		return r;
	}

	static double horiz2(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return dx * dx + dz * dz;
	}
}
