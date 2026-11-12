package dev.elsewhere.greenhaze_hush.mixin;

import dev.elsewhere.stub.greenhaze_hush.BossDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Difficulty framework lethal-hit guard: no single boss hit takes more than 60% of max health after armor. */
@Mixin(Player.class)
public class PlayerLethalGuardMixin {
	@ModifyVariable(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F", ordinal = 0), argsOnly = true)
	private float elsewhere$lethalGuard(float dmg, ServerLevel level, DamageSource source) {
		return BossDamage.lethalGuard((Player) (Object) this, source, dmg);
	}
}
