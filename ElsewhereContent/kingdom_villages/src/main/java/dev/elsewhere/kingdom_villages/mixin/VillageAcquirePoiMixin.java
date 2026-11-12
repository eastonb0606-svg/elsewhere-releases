package dev.elsewhere.kingdom_villages.mixin;

import dev.elsewhere.kingdom_villages.s3.brainlod.BrainLod;
import dev.elsewhere.kingdom_villages.s3.brainlod.PoiCache;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * fx-siegeperf: villager POI searches (AcquirePoi: bed, job site, meeting point) are staggered like the walk searches. The
 * siege bench sampler put AcquirePoi's trigger at about 10% of the full-siege tick, in bursts: the burn takes beds and job
 * sites from many villagers at once and they all search on the same pass. At most {@link BrainLod#POI_SEARCHES_PER_TICK}
 * villager POI searches start per server tick; a villager over the budget changes nothing (its own search schedule is not
 * advanced) and tries again on its next start pass. Only inside a villager's brain start pass; other mobs are untouched.
 */
@Mixin(AcquirePoi.class)
public abstract class VillageAcquirePoiMixin {
	@Inject(method = "lambda$create$3", at = @At("HEAD"), cancellable = true, require = 0)
	private static void elsewhereVillages$poiBudget(CallbackInfoReturnable<Boolean> cir) {
		if (!BrainLod.mayPoiSearch()) cir.setReturnValue(false);
	}

	/**
	 * The search itself: a section known to have no POI of this type with space within reach returns the empty result at
	 * once ({@link PoiCache}); otherwise the real search runs (and counts against the budget), and when it saw no candidate
	 * at all the section is probed and remembered. The stream is sorted (so fully evaluated) in vanilla as well; listing it
	 * here changes no result and no side effect.
	 */
	@WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findAllClosestFirstWithType(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"), require = 0)
	private static Stream<Pair<Holder<PoiType>, BlockPos>> elsewhereVillages$poiSearch(PoiManager pm, Predicate<Holder<PoiType>> type, Predicate<BlockPos> pos, BlockPos at, int radius,
		PoiManager.Occupancy occ, Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original) {
		if (!BrainLod.enabled || BrainLod.startPassTick == Long.MIN_VALUE || occ != PoiManager.Occupancy.HAS_SPACE)
			return original.call(pm, type, pos, at, radius, occ);
		if (PoiCache.knownEmpty(pm, type, at, radius)) return Stream.empty();
		BrainLod.poiSearched();
		boolean[] candidate = {false};
		Predicate<BlockPos> seen = p -> {
			candidate[0] = true;
			return pos.test(p);
		};
		List<Pair<Holder<PoiType>, BlockPos>> found = original.call(pm, type, seen, at, radius, occ).toList();
		if (!candidate[0]) PoiCache.searchedEmpty(pm, type, at, radius, occ);
		return found.stream();
	}
}
