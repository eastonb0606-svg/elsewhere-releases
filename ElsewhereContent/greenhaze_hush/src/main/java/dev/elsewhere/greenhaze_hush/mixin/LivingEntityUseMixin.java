package dev.elsewhere.greenhaze_hush.mixin;

import dev.elsewhere.greenhaze.GreenLeafBridge;
import dev.elsewhere.greenhaze_hush.GreenhazeHushContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detects completed Green Leaf item uses (puffs, rips, bites) by registry namespace: no compile dependency on Green Leaf. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUseMixin {
	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void elsewhere$greenLeafUse(CallbackInfo ci) {
		// a hit Green Leaf 2.1 refuses (coming down) is not a use: no trip roll, no Hush cough
		if ((Object) this instanceof ServerPlayer p && GreenLeafBridge.loaded() && GreenLeafBridge.isGreenLeafItem(p.getUseItem()) && !GreenLeafBridge.refusesHits(p)) {
			GreenhazeHushContent.onGreenLeafUse(p);
		}
	}
}
