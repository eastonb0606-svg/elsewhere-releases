package dev.elsewhere.greenhaze_hush;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player screen-effect preferences (user decision: Elsewhere screen effects on/off + reduce motion, client settings in
 * config/elsewhere-client.json). The client sends its two flags on join and whenever they change; the server then skips
 * the purely visual effects it would apply in the Greenhaze/Hush (the Haze's Nausea pulses, the Toke Toad's Nausea lick).
 * Gameplay effects (Slowness, Darkness from boss mechanics or the Silence Draught) are not affected. Default: effects on.
 */
public final class FxPrefs {
	private FxPrefs() {}

	public record Payload(boolean effects, boolean reduceMotion) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = new Type<>(Reg.id("greenhaze_hush_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Payload::effects,
			ByteBufCodecs.BOOL, Payload::reduceMotion,
			Payload::new);

		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Payload> PREFS = new ConcurrentHashMap<>();
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, ctx) -> PREFS.put(ctx.player().getUUID(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PREFS.remove(handler.getPlayer().getUUID()));
	}

	public static void set(UUID player, boolean effects, boolean reduceMotion) {
		PREFS.put(player, new Payload(effects, reduceMotion));
	}

	/** Screen effects allowed at all for this player. */
	public static boolean effects(Player p) {
		Payload s = PREFS.get(p.getUUID());
		return s == null || s.effects();
	}

	/** Moving/warping screen effects (Nausea) allowed: effects on and reduce motion off. */
	public static boolean motion(Player p) {
		Payload s = PREFS.get(p.getUUID());
		return s == null || (s.effects() && !s.reduceMotion());
	}

	public static void forgetPlayer(UUID id) { PREFS.remove(id); }

	public static void clearAll() { PREFS.clear(); }
}
