package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.ArmiesBridge;
import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Distance rings for soldiers (ARMIES.md 3.2 / 9.5). R0: within 24 blocks of a player or fighting one; R1: 24-48; R2: 48-96;
 * R3/R4: beyond 96 (virtual squads, the existing {@code KingdomLod}). The active set itself is {@link KingdomPerf}'s (nearest
 * soldiers per player); this class adds the <b>player-first</b> rule on the same 10-tick cadence, after KingdomPerf: a soldier
 * fighting a player within 64 blocks keeps full AI even past the 48-block active radius, so archers engaged at range don't freeze.
 * Ring counts are computed on demand only (/kingdom armies, self-test), never per tick.
 */
public final class ArmyLod {
	private ArmyLod() {}

	public static final double R0 = 24, R1 = 48, R2 = 96, ENGAGED = 64;
	public static long keptForPlayer;

	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(ArmyLod::playerFirst);
	}

	static void playerFirst(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 10 != 0 || KingdomPerf.forceActive || level.players().isEmpty()) return;
		playerFirstPass(level, now);
		if (ArmiesConfig.tokens && !TEST_PINNED) attackTokens(level, now);
	}

	public static long heldByTokens;
	static final boolean TEST_PINNED = ArmiesConfig.otherSelfTest();

	/**
	 * Attack tokens (ARMIES.md 3.4): per player and kingdom, only the nearest {@code attack_tokens.melee} melee soldiers and
	 * {@code attack_tokens.ranged} shooters within 24 blocks fight at once; the rest of that kingdom's soldiers on the player hold
	 * (goals paused until the next 10-tick pass) and step in as token holders fall. Keeps a full squad beatable by one player.
	 * Soldiers of kingdoms without {@code army.scale} are unchanged.
	 */
	static void attackTokens(ServerLevel level, long now) {
		java.util.Map<Player, java.util.List<KingdomSoldier>> on = null;
		for (KingdomSoldier s : KingdomPerf.loaded(level)) {
			if (s.isRemoved() || !(s.getTarget() instanceof Player p) || p.isSpectator() || p.distanceToSqr(s) > R0 * R0) continue;
			if (on == null) on = new java.util.HashMap<>();
			on.computeIfAbsent(p, k -> new java.util.ArrayList<>()).add(s);
		}
		if (on == null) return;
		for (var e : on.entrySet()) {
			Player p = e.getKey();
			java.util.List<KingdomSoldier> l = e.getValue();
			l.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(p)));
			java.util.Map<String, Integer> used = new java.util.HashMap<>();
			for (KingdomSoldier s : l) {
				ArmyScale sc = ArmyScale.of(s.factionId());
				if (sc == null) continue;
				boolean ranged = s.unit().role().ranged();
				String k = s.factionId() + (ranged ? "|r" : "|m");
				int n = used.merge(k, 1, Integer::sum);
				if (n > (ranged ? sc.rangedTokens() : sc.meleeTokens())) {
					ArmiesBridge.keepActive(s, 0); // hold: goals paused until the next pass
					heldByTokens++;
				}
			}
		}
	}

	static void playerFirstPass(ServerLevel level, long now) {
		int active = KingdomPerf.activeCount(level);
		for (KingdomSoldier s : KingdomPerf.loaded(level)) {
			if (active >= KingdomConfig.maxRealSoldiersGlobal) return;
			if (s.isActive() || s.isRemoved()) continue;
			LivingEntity t = s.getTarget();
			if (t instanceof Player p && !p.isSpectator() && p.isAlive() && p.distanceToSqr(s) <= ENGAGED * ENGAGED) {
				ArmiesBridge.keepActive(s, now + 25);
				keptForPlayer++;
				active++;
			}
		}
	}

	/** Ring of a soldier: 0-2 by distance to the nearest player (or test observer), 3 beyond 96, 0 when fighting a player. */
	public static int ring(ServerLevel level, KingdomSoldier s) {
		if (s.getTarget() instanceof Player) return 0;
		double best = Double.MAX_VALUE;
		for (Player p : level.players()) if (!p.isSpectator()) best = Math.min(best, p.distanceToSqr(s));
		for (var o : KingdomPerf.testObservers) best = Math.min(best, o.distanceToSqr(s.position()));
		if (best <= R0 * R0) return 0;
		if (best <= R1 * R1) return 1;
		if (best <= R2 * R2) return 2;
		return 3;
	}

	/** Counts per ring [R0, R1, R2, R3+] for one level (on demand). */
	public static int[] rings(ServerLevel level) {
		int[] n = new int[4];
		for (KingdomSoldier s : KingdomPerf.loaded(level)) n[ring(level, s)]++;
		return n;
	}
}
