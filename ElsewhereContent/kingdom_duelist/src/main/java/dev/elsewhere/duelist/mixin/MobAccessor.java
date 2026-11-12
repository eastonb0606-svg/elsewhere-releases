package dev.elsewhere.duelist.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Self-test only: the goal selectors are protected. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("goalSelector")
	GoalSelector elsewhere$goalSelector();

	@Accessor("targetSelector")
	GoalSelector elsewhere$targetSelector();
}
