package dev.elsewhere.kingdom_villages.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.elsewhere.kingdom_villages.s3.brainlod.BrainLod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Villager brain LOD/stagger ({@link BrainLod}): inside {@code Brain.tick}, only the start pass of a villager's brain is
 * gated; memory expiry, sensors and running behaviours keep ticking every tick. Other brain mobs are untouched.
 */
@Mixin(Brain.class)
public abstract class VillageBrainLodMixin {
	@Unique
	private int elsewhereVillages$every = 1;
	@Unique
	private long elsewhereVillages$retierAt = Long.MIN_VALUE;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;startEachNonRunningBehavior(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
	private void elsewhereVillages$staggerStarts(Brain<?> self, ServerLevel level, LivingEntity body, Operation<Void> original) {
		if (!BrainLod.enabled || !(body instanceof Villager v)) {
			original.call(self, level, body);
			return;
		}
		long now = level.getGameTime();
		if (now >= elsewhereVillages$retierAt || now < elsewhereVillages$retierAt - BrainLod.RETIER) {
			elsewhereVillages$every = BrainLod.computeInterval(level, v);
			elsewhereVillages$retierAt = now + BrainLod.RETIER;
		}
		if (BrainLod.onPhase(v, elsewhereVillages$every) || BrainLod.urgent(v, self)) {
			BrainLod.startsRun++;
			BrainLod.startPassTick = now;
			try {
				original.call(self, level, body);
			} finally {
				BrainLod.startPassTick = Long.MIN_VALUE;
			}
		} else BrainLod.startsSkipped++;
	}
}
