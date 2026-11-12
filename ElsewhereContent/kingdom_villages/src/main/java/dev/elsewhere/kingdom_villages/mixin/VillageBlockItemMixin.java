package dev.elsewhere.kingdom_villages.mixin;

import dev.elsewhere.kingdom_villages.PlayerMarks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block placed by a player marks its chunk; villages touching it are never changed by the kingdom. */
@Mixin(BlockItem.class)
public abstract class VillageBlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void elsewhereVillages$placed(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
		if (ctx.getPlayer() != null && cir.getReturnValue().consumesAction() && ctx.getLevel() instanceof ServerLevel level)
			PlayerMarks.mark(level, ctx.getClickedPos());
	}
}
