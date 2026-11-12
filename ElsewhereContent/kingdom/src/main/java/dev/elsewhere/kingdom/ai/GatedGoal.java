package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.function.Predicate;
import net.minecraft.world.entity.ai.goal.Goal;

/** Wraps a vanilla goal so it only runs for the right role, while active and not routed. */
public final class GatedGoal extends Goal {
	private final KingdomSoldier mob;
	private final Goal inner;
	private final Predicate<KingdomSoldier> gate;

	public GatedGoal(KingdomSoldier mob, Goal inner, Predicate<KingdomSoldier> gate) {
		this.mob = mob;
		this.inner = inner;
		this.gate = gate;
		this.setFlags(inner.getFlags());
	}

	private boolean ok() {
		return !mob.isRouted() && mob.isActive() && gate.test(mob);
	}

	@Override
	public boolean canUse() {
		return ok() && inner.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return ok() && inner.canContinueToUse();
	}

	@Override
	public boolean isInterruptable() {
		return inner.isInterruptable();
	}

	@Override
	public void start() {
		inner.start();
	}

	@Override
	public void stop() {
		inner.stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return inner.requiresUpdateEveryTick();
	}

	@Override
	public void tick() {
		inner.tick();
	}
}
