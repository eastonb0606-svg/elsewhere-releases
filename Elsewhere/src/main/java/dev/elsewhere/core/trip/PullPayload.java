package dev.elsewhere.core.trip;

import dev.elsewhere.Elsewhere;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: start (ticks > 0) or cancel (ticks == 0) the "pull" vignette before an accidental trip. */
public record PullPayload(int ticks, int color) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PullPayload> TYPE = new CustomPacketPayload.Type<>(Elsewhere.id("pull"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PullPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, PullPayload::ticks, ByteBufCodecs.INT, PullPayload::color, PullPayload::new);

	@Override
	public CustomPacketPayload.Type<PullPayload> type() {
		return TYPE;
	}
}
