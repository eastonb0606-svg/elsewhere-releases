package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalContent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the settled Primary / Secondary path ordinals (-1 = none). Sent only when they change, on join, respawn and level change. */
public record CallingPayload(int primary, int secondary) implements CustomPacketPayload {
	public static final Type<CallingPayload> TYPE = new Type<>(ArsenalContent.id("calling_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CallingPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CallingPayload::primary,
		ByteBufCodecs.VAR_INT, CallingPayload::secondary,
		CallingPayload::new);

	@Override
	public Type<CallingPayload> type() {
		return TYPE;
	}
}
