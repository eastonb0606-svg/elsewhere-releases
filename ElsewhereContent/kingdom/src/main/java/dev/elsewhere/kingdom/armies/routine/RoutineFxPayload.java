package dev.elsewhere.kingdom.armies.routine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client, once per hour per nearby player while a festival is on: client-only festival lights around a place
 * ({@code kind} 0 = Mirewick's floating canal lanterns at the Lantern Fair, 1 = Azmar's lamp boats on the river at the Festival of
 * the Flood, 2 = the Hrafnholt Blót bonfire sparks). The client draws them itself (pooled, at most 120 particles, culled by distance).
 */
public record RoutineFxPayload(int kind, int x, int y, int z, int radius, int ticks) implements CustomPacketPayload {
	public static final Type<RoutineFxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("elsewhere", "routine_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RoutineFxPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RoutineFxPayload::kind,
		ByteBufCodecs.VAR_INT, RoutineFxPayload::x,
		ByteBufCodecs.VAR_INT, RoutineFxPayload::y,
		ByteBufCodecs.VAR_INT, RoutineFxPayload::z,
		ByteBufCodecs.VAR_INT, RoutineFxPayload::radius,
		ByteBufCodecs.VAR_INT, RoutineFxPayload::ticks,
		RoutineFxPayload::new);

	public static int kindOf(String fx) {
		return switch (fx) {
			case "lamp_boats" -> 1;
			case "bonfire" -> 2;
			default -> 0;
		};
	}

	@Override
	public Type<RoutineFxPayload> type() {
		return TYPE;
	}
}
