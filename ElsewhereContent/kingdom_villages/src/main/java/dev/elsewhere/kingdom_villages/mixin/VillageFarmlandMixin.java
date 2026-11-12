package dev.elsewhere.kingdom_villages.mixin;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom_villages.Serf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crop avoidance (FIELD-TACTICS 3.2): kingdom soldiers and their Serfs never trample farmland (jump-crits and falls land on fields). */
@Mixin(FarmlandBlock.class)
public abstract class VillageFarmlandMixin {
	private static final boolean SELFTEST = Boolean.getBoolean("villages.selftest");

	@Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
	private void elsewhereVillages$noTrample(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (entity instanceof KingdomSoldier || entity instanceof Serf) ci.cancel();
		else if (SELFTEST) System.out.println("[villages-selftest] farmland trampled by " + (entity == null ? "null" : entity.getType().toShortString()) + " at " + pos.toShortString());
	}
}
