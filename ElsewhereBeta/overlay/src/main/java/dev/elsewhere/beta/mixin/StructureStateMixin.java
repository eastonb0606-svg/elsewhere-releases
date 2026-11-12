package dev.elsewhere.beta.mixin;

import dev.elsewhere.beta.OverworldContent;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** overworldContent=false: drop the Overworld boss-site structure sets from every chunk generator's placement list. */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class StructureStateMixin {
	@Unique
	private List<Holder<StructureSet>> elsewhere_beta$filtered;

	@Inject(method = "possibleStructureSets", at = @At("RETURN"), cancellable = true)
	private void elsewhere_beta$filterSites(CallbackInfoReturnable<List<Holder<StructureSet>>> cir) {
		if (OverworldContent.enabled()) return;
		if (elsewhere_beta$filtered == null) elsewhere_beta$filtered = OverworldContent.filter(cir.getReturnValue());
		cir.setReturnValue(elsewhere_beta$filtered);
	}
}
