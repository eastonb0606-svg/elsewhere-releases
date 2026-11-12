package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.petalwind.PetalwindRules;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Custom payloads: the Heat meter (server -> client) and mid-air jumps (client -> server). */
public final class Net {
	private Net() {}

	/** Heat 0..100 for the HUD bar; {@code show} is false outside the Glasswaste. */
	public record HeatPayload(int heat, boolean show, boolean glare) implements CustomPacketPayload {
		public static final Type<HeatPayload> TYPE = new Type<>(Reg.id("glasswaste_heat"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HeatPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HeatPayload::heat, ByteBufCodecs.BOOL, HeatPayload::show, ByteBufCodecs.BOOL, HeatPayload::glare, HeatPayload::new);

		@Override
		public Type<HeatPayload> type() {
			return TYPE;
		}
	}

	/** The player pressed jump in mid-air (Galeweave double jump, Petalstep Sandals, Zephyr Wings boost). */
	public record AirJumpPayload(boolean sneaking) implements CustomPacketPayload {
		public static final Type<AirJumpPayload> TYPE = new Type<>(Reg.id("petalwind_air_jump"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AirJumpPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AirJumpPayload::sneaking, AirJumpPayload::new);

		@Override
		public Type<AirJumpPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(HeatPayload.TYPE, HeatPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AirJumpPayload.TYPE, AirJumpPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AirJumpPayload.TYPE, (payload, context) -> PetalwindRules.airJump(context.player(), payload.sneaking()));
	}
}
