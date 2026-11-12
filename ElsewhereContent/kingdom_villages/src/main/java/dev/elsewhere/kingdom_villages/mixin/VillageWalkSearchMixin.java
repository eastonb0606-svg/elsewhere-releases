package dev.elsewhere.kingdom_villages.mixin;

import dev.elsewhere.kingdom_villages.s3.brainlod.BrainLod;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villager walk searches ({@link BrainLod}): a futile search is not repeated from the same block to the same target for a
 * short while, and at most a few villager walk searches start per tick. Villagers only; every other mob is vanilla.
 */
@Mixin(MoveToTargetSink.class)
public abstract class VillageWalkSearchMixin {
	@Shadow
	private int remainingCooldown;
	@Shadow
	private Path path;
	@Unique
	private long elsewhereVillages$futileUntil = Long.MIN_VALUE;
	@Unique
	private long elsewhereVillages$futileTarget, elsewhereVillages$futileFrom;

	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z", at = @At("HEAD"), cancellable = true)
	private void elsewhereVillages$walkSearch(ServerLevel level, Mob body, CallbackInfoReturnable<Boolean> cir) {
		if (!BrainLod.enabled || !(body instanceof Villager) || remainingCooldown > 0) return;
		Optional<WalkTarget> wt = body.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
		if (wt.isEmpty()) return;
		long now = level.getGameTime();
		if (now < elsewhereVillages$futileUntil && wt.get().getTarget().currentBlockPosition().asLong() == elsewhereVillages$futileTarget
			&& body.blockPosition().asLong() == elsewhereVillages$futileFrom) {
			BrainLod.walkSearchesSaved++;
			cir.setReturnValue(false); // same futile search from the same spot: stand, keep the target (the give-up timer runs on)
			return;
		}
		if (!BrainLod.tryWalkSearch(now)) cir.setReturnValue(false); // over this tick's budget: search on a later pass
	}

	@Inject(method = "tryComputePath", at = @At("RETURN"))
	private void elsewhereVillages$futile(Mob body, WalkTarget walkTarget, long timestamp, CallbackInfoReturnable<Boolean> cir) {
		if (!(body instanceof Villager)) return;
		Path p = path;
		boolean futile = body.onGround() && (p == null ? !cir.getReturnValueZ()
			: !p.canReach() && (p.getNodeCount() <= 1 || p.getEndNode().distanceManhattan(body.blockPosition()) <= 1F));
		if (futile) {
			elsewhereVillages$futileTarget = walkTarget.getTarget().currentBlockPosition().asLong();
			elsewhereVillages$futileFrom = body.blockPosition().asLong();
			elsewhereVillages$futileUntil = timestamp + BrainLod.FUTILE_MEMO;
		} else elsewhereVillages$futileUntil = Long.MIN_VALUE;
	}
}
