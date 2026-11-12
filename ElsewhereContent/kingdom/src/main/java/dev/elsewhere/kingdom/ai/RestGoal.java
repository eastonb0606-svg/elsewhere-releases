package dev.elsewhere.kingdom.ai;

import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Night routine: a resting soldier stays crouched in place (no moving, no looking around) until woken. */
public final class RestGoal extends Goal {
	private final KingdomSoldier mob;

	public RestGoal(KingdomSoldier mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return mob.isResting() && mob.getTarget() == null && !mob.isRouted();
	}

	@Override
	public void start() {
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
	}
}
