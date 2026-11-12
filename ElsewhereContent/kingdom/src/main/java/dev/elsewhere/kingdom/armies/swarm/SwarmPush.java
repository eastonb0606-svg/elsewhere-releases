package dev.elsewhere.kingdom.armies.swarm;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.world.entity.Entity;

/**
 * Collisions (ARMIES.md 3.2/3.3): soldiers of the same or an allied faction don't push each other in R1-R2, which removes most of the
 * O(n^2) jostling of dense formations; enemies, mounts, players and mobs still push. In R2 a soldier runs its push query every 2nd tick.
 * R0 is vanilla. Called from {@code KingdomSoldier.pushEntities()} and {@code doPush(Entity)}.
 */
public final class SwarmPush {
	private SwarmPush() {}

	/** Ally pushes skipped and R2 push queries skipped (self-test, bench, command). */
	public static long alliesSkipped, queriesSkipped;

	/** False on the R2 ticks a soldier skips its push query. */
	public static boolean mayPushNow(KingdomSoldier s) {
		if (!Swarm.on() || Swarm.ring(s) < 2 || ((s.tickCount + s.getId()) & 1) == 0) return true;
		queriesSkipped++;
		return false;
	}

	/** True when {@code s} must not push {@code other}: both soldiers, not hostile, and {@code s} is in R1 or beyond. */
	public static boolean skip(KingdomSoldier s, Entity other) {
		if (!Swarm.on() || !(other instanceof KingdomSoldier o) || Factions.hostile(s.factionId(), o.factionId()) || Swarm.ring(s) == 0) return false;
		alliesSkipped++;
		return true;
	}
}
