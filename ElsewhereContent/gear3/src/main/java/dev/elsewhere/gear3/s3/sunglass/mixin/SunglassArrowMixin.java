package dev.elsewhere.gear3.s3.sunglass.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.elsewhere.gear3.s3.sunglass.SunglassArrow;
import dev.elsewhere.gear3.s3.sunglass.SunglassArrows;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Sunglass Arrow hooks inside the vanilla arrow code (owner s-sunglass). Each one is a no-op for every other arrow (one instanceof).
 * <ol>
 * <li>{@code tick}: the "inside a block, so stick" check ignores glass for a Sunglass Arrow (it may end a tick inside a glass block).</li>
 * <li>{@code tick}: the flight ray ({@code Level.clipIncludingBorder}) runs through glass ({@link SunglassArrow.GlassPassClip}).</li>
 * <li>{@code onHitEntity}: the damage passed to {@code hurtOrSimulate} is x1.25 against undead (after Power, crits and the speed
 * rounding, so the bonus is exactly +25% of the hit).</li>
 * </ol>
 */
@Mixin(AbstractArrow.class)
public abstract class SunglassArrowMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
	private VoxelShape elsewhere$sunglassInsideCheck(BlockState state, BlockGetter level, BlockPos pos, Operation<VoxelShape> original) {
		if ((Object) this instanceof SunglassArrow arrow && arrow.passes(state)) return Shapes.empty();
		return original.call(state, level, pos);
	}

	@ModifyArg(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;clipIncludingBorder(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
	private ClipContext elsewhere$sunglassFlightRay(ClipContext ray) {
		if ((Object) this instanceof SunglassArrow arrow && arrow.sunlit()) return new SunglassArrow.GlassPassClip(ray, arrow);
		return ray;
	}

	@WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean elsewhere$sunglassUndead(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
		if ((Object) this instanceof SunglassArrow arrow && arrow.sunlit()) amount = SunglassArrows.damageAgainst(target, amount);
		return original.call(target, source, amount);
	}
}
