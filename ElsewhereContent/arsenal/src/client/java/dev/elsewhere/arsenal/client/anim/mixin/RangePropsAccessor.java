package dev.elsewhere.arsenal.client.anim.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** P6: registers the weapon-state range properties ({@code elsewhere:attack_charge}, cast_cooldown, channel_time). */
@Mixin(RangeSelectItemModelProperties.class)
public interface RangePropsAccessor {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends RangeSelectItemModelProperty>> elsewhere$idMapper() {
		throw new AssertionError();
	}
}
