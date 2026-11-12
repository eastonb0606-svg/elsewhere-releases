package dev.elsewhere.arsenal.client.anim.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** P6: registers the weapon-state condition properties ({@code elsewhere:lunge_ready}, {@code elsewhere:channeling}). */
@Mixin(ConditionalItemModelProperties.class)
public interface ConditionalPropsAccessor {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ConditionalItemModelProperty>> elsewhere$idMapper() {
		throw new AssertionError();
	}
}
