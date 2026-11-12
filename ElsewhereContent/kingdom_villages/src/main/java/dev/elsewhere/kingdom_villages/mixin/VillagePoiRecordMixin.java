package dev.elsewhere.kingdom_villages.mixin;

import dev.elsewhere.kingdom_villages.s3.brainlod.PoiCache;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** fx-siegeperf: the events that can make an empty POI search non-empty (see {@link PoiCache}): a record is created, or a ticket is freed. */
@Mixin(PoiRecord.class)
public abstract class VillagePoiRecordMixin {
	@Inject(method = "<init>*", at = @At("RETURN"))
	private void elsewhereVillages$created(CallbackInfo ci) {
		PoiCache.changed(((PoiRecord) (Object) this).getPos());
	}

	@Inject(method = "releaseTicket", at = @At("HEAD"))
	private void elsewhereVillages$freed(CallbackInfoReturnable<Boolean> cir) {
		PoiCache.changed(((PoiRecord) (Object) this).getPos());
	}
}
