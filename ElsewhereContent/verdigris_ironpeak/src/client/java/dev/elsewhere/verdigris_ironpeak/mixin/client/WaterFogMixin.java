package dev.elsewhere.verdigris_ironpeak.mixin.client;

import dev.elsewhere.verdigris_sea.VerdigrisItems;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tidebrass helm (BALANCE N1): clear underwater view, water fog x0.5 (the fog reaches twice as far). Cheap: two float writes. */
@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void elsewhere$clearView(FogData data, Camera camera, ClientLevel level, float partialTick, DeltaTracker delta, CallbackInfo ci) {
		Entity e = camera.entity();
		if (e instanceof LivingEntity l && l.getItemBySlot(EquipmentSlot.HEAD).is(VerdigrisItems.DIVING_SUIT_HELMET)) {
			data.environmentalEnd *= 2.0F;
		}
	}
}
