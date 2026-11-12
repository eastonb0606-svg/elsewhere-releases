package dev.elsewhere.duelist.foresight;

import dev.elsewhere.duelist.DuelistContent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: whether this player gets boss attack hints (foresight gear). Sent on join and when it changes. */
public record ForesightPayload(boolean sees) implements CustomPacketPayload {
	public static final Type<ForesightPayload> TYPE = new Type<>(DuelistContent.id("foresight_sight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ForesightPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, ForesightPayload::sees,
		ForesightPayload::new);

	@Override
	public Type<ForesightPayload> type() {
		return TYPE;
	}
}
