package dev.elsewhere.arsenal.glimmer;

import dev.elsewhere.arsenal.ArsenalContent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the owner's Glimmer for the HUD bar. failNeed > 0 means a cast just failed for lack of Glimmer. */
public record GlimmerPayload(float current, float max, int reserved, int minions, int failNeed) implements CustomPacketPayload {
	public static final Type<GlimmerPayload> TYPE = new Type<>(ArsenalContent.id("glimmer"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GlimmerPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, GlimmerPayload::current,
		ByteBufCodecs.FLOAT, GlimmerPayload::max,
		ByteBufCodecs.VAR_INT, GlimmerPayload::reserved,
		ByteBufCodecs.VAR_INT, GlimmerPayload::minions,
		ByteBufCodecs.VAR_INT, GlimmerPayload::failNeed,
		GlimmerPayload::new);

	@Override
	public Type<GlimmerPayload> type() {
		return TYPE;
	}
}
