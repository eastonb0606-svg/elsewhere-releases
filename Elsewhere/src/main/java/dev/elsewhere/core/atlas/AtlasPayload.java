package dev.elsewhere.core.atlas;

import dev.elsewhere.Elsewhere;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: open the Wayfinder Atlas (polish-atlas-a). {@code dims}: dimension ids the player has visited
 * (e.g. "elsewhere:pale_hollow"); {@code bosses}: boss entity id paths the player helped defeat (e.g. "mother_creak");
 * {@code found}: other discoveries as "kind:id" (e.g. "item:elsewhere:amber", "companion:hollow_stag", "event:lantern").
 */
public record AtlasPayload(List<String> dims, List<String> bosses, List<String> found) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AtlasPayload> TYPE = new CustomPacketPayload.Type<>(Elsewhere.id("atlas"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AtlasPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AtlasPayload::dims,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AtlasPayload::bosses,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AtlasPayload::found,
		AtlasPayload::new);

	@Override
	public CustomPacketPayload.Type<AtlasPayload> type() {
		return TYPE;
	}
}
