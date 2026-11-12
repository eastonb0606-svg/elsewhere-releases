package dev.elsewhere.kingdom.tactics;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Glue between {@link Squad#tick} and the brains (FIELD-TACTICS 1.10): creates a {@link SquadBrain} for a squad whose faction has a non-pass-through profile, limits the number of
 * decisions per tick, doubles the interval when the server is slow, and answers the command. A faction without a profile or with {@code tactics.enabled=false} costs nothing.
 */
public final class TacticsRuntime {
	private TacticsRuntime() {}

	public static final int MAX_DECISIONS_PER_TICK = 2;
	static long budgetTick = -1;
	static int budgetUsed;
	public static long signalsEmitted, decisionsTotal;

	/** Called by the squad every few ticks for real, loaded squads. */
	public static void tick(ServerLevel level, Squad squad, net.minecraft.world.entity.@Nullable Mob lead, List<KingdomSoldier> mem, long now) {
		if (!TacticsRegistry.enabled) return;
		TacticsProfile prof = TacticsRegistry.of(squad.faction());
		if (prof.passThrough()) return;
		SquadBrain b = squad.brain;
		if (b == null || b.profile != prof) {
			b = new SquadBrain(squad, prof);
			squad.brain = b;
		}
		if (now < b.nextDecisionTick || now < b.disabledUntil) return;
		if (budgetTick != now) {
			budgetTick = now;
			budgetUsed = 0;
		}
		if (budgetUsed >= MAX_DECISIONS_PER_TICK) return;
		budgetUsed++;
		if (level.getServer().getAverageTickTimeNanos() > 40e6) b.nextDecisionTick = now + b.interval();
		List<KingdomSoldier> bodies = new ArrayList<>();
		if (lead instanceof KingdomSoldier ls && ls.isAlive()) bodies.add(ls);
		bodies.addAll(mem);
		long before = b.decisions;
		b.tick(level, now, bodies);
		decisionsTotal += b.decisions - before;
	}

	/** Squads of the same faction within {@code r} blocks of {@code b}'s centre that have a brain (for call_help). */
	static List<SquadBrain> alliesNear(SquadBrain b, double r) {
		List<SquadBrain> out = new ArrayList<>();
		if (b.last == null) return out;
		for (SquadBrain o : ALL) {
			if (o == b || !o.profile.faction().equals(b.profile.faction()) || o.last == null) continue;
			if (o.last.squadCentre().distanceToSqr(b.last.squadCentre()) <= r * r) out.add(o);
		}
		return out;
	}

	/** Brains seen this session (weak enough for the self-test: squads keep theirs, the list is pruned by {@link #prune}). */
	static final List<SquadBrain> ALL = new ArrayList<>();

	static void register(SquadBrain b) {
		if (!ALL.contains(b)) ALL.add(b);
	}

	public static void prune() {
		ALL.removeIf(b -> b.squad.brain != b);
	}

	/** The standoff a ranged soldier keeps from a melee contact (profile role spec, else 10). */
	static double standoff(KingdomSoldier s) {
		TacticsProfile p = TacticsRegistry.of(s.factionId());
		var spec = p.roles().get(s.role());
		var c = TacticsConfig.of(s.factionId());
		if (c != null) {
			double[] r = c.roles.get(s.role().name());
			if (r != null && r[0] > 0) return r[0];
		}
		return spec != null && spec.standoff() > 0 ? spec.standoff() : 10;
	}

	/** Every live brain of a faction (command and tests). */
	public static List<SquadBrain> brains(@Nullable ServerLevel level) {
		prune();
		return List.copyOf(ALL);
	}

	/** Average decision time over all brains of the faction, in ms. */
	public static double avgDecisionMs() {
		prune();
		double s = 0;
		int n = 0;
		for (SquadBrain b : ALL) if (b.decisions > 0) {
			s += b.avgDecisionMs();
			n++;
		}
		return n == 0 ? 0 : s / n;
	}

	public static BlockPos centreOf(SquadBrain b) {
		return b.last == null ? BlockPos.ZERO : BlockPos.containing(b.last.squadCentre());
	}
}
