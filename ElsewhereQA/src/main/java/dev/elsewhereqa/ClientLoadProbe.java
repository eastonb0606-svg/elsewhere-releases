package dev.elsewhereqa;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;

/**
 * s-clientload: the connection of a measuring "client" on the dedicated server. It is a real ServerPlayer in the level and the player list,
 * so the chunk map tracks entities for it, particles and sounds reach it and the kingdom LOD treats it as a watching player; every clientbound
 * packet the server would put on the wire is handed to {@link ClientLoad.Meter} instead (counted and encoded for its size). Chunk batches are
 * acknowledged on the next server tick, the way a fast client would. Like Fabric's FakePlayer listener it is an UntrackedPacketListener (no
 * play-init events for it); payloads that mods send without a canSend check still reach it and are counted.
 */
final class ClientLoadProbe extends ServerGamePacketListenerImpl implements net.fabricmc.fabric.impl.networking.UntrackedPacketListener {
	private ClientLoad.@Nullable Meter meter;
	boolean ackDue;

	ClientLoadProbe(final MinecraftServer server, final ServerPlayer player) {
		super(server, new Connection(PacketFlow.CLIENTBOUND), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
	}

	void meter(final ClientLoad.Meter m) {
		meter = m;
	}

	private void take(final Packet<?> packet) {
		if (packet instanceof ClientboundChunkBatchFinishedPacket) {
			ackDue = true;
		}
		ClientLoad.Meter m = meter;
		if (m != null) {
			m.onPacket(packet);
		}
	}

	@Override
	public void send(final Packet<?> packet) {
		take(packet);
	}

	@Override
	public void send(final Packet<?> packet, final @Nullable ChannelFutureListener listener) {
		take(packet);
	}

	@Override
	public void suspendFlushing() {
		// no channel
	}

	@Override
	public void resumeFlushing() {
		// no channel
	}

	@Override
	public void disconnect(final Component reason) {
		ClientLoad.LOG_DISCONNECT.add(reason.getString());
	}

	@Override
	public void disconnect(final DisconnectionDetails details) {
		ClientLoad.LOG_DISCONNECT.add(details.reason().getString());
	}
}
