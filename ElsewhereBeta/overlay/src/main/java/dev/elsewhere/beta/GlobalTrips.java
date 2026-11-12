package dev.elsewhere.beta;

import com.mojang.serialization.Codec;
import dev.elsewhere.ElsewhereConfig;
import dev.elsewhere.core.trip.AccidentalTrips;
import dev.elsewhere.core.trip.TripTrigger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Beta 2: one accidental-trip framework for the whole mod. Every module's trip stub (petalwind TripStub, verdigris Trips,
 * greenhaze TripSystem) is patched at merge to call {@link #fire}, which hands the event to the core's
 * {@link AccidentalTrips} (familiarity, home radius, hourly budgets, the global 2 h spacing, 60 min grace, pity, the 3 s
 * pull). Also the vanilla-friendly switches: per-player {@code /elsewhere trips on|off} (any player) and the server
 * config {@code overworldContent} (false = no accidental trips at all).
 */
public final class GlobalTrips {
	private GlobalTrips() {}

	/** Per player, saved, survives death. Absent = on. */
	/** 0.4.0: the core owns this attachment (same id) and the gate. */
	public static final AttachmentType<Boolean> TRIPS_ON = dev.elsewhere.registry.ModAttachments.TRIPS_ENABLED;

	private static final Map<String, TripTrigger> ADAPTERS = new LinkedHashMap<>();

	private record Adapter(String id, ResourceKey<Level> target, Kind kind, ToDoubleFunction<ServerPlayer> base) implements TripTrigger {
		@Override
		public double baseChance(ServerPlayer player) {
			return base.applyAsDouble(player);
		}
	}

	/** Registers a module trigger with the core framework (so /elsewhere trips lists it and the config can override it). */
	public static TripTrigger register(String id, ResourceKey<Level> target, boolean activity, ToDoubleFunction<ServerPlayer> base) {
		TripTrigger t = ADAPTERS.get(id);
		if (t != null) return t;
		TripTrigger existing = AccidentalTrips.triggers().get(id);
		t = existing != null ? existing : new Adapter(id, target, activity ? TripTrigger.Kind.ACTIVITY : TripTrigger.Kind.LOCATION, base);
		ADAPTERS.put(id, t);
		if (existing == null) AccidentalTrips.register(t);
		return t;
	}

	/** A module trigger's event happened: the core framework rolls it. True if a pull started. */
	public static boolean fire(ServerPlayer player, String id, ResourceKey<Level> target, boolean activity, ToDoubleFunction<ServerPlayer> base) {
		return AccidentalTrips.fire(player, register(id, target, activity, base));
	}

	public static boolean overworldContent() {
		return AccidentalTrips.overworldContent();
	}

	public static boolean playerEnabled(ServerPlayer player) {
		return AccidentalTrips.playerEnabled(player);
	}

	/** Gate checked first by AccidentalTrips.fire/startPull. */
	public static boolean allowed(ServerPlayer player) {
		return AccidentalTrips.allowed(player);
	}

	public static void setEnabled(ServerPlayer player, boolean on) {
		AccidentalTrips.setPlayerEnabled(player, on);
	}

	public static void init() {
		if (true) return; // 0.4.0: the core registers /elsewhere trips on|off itself
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> dispatcher.register(
			Commands.literal("elsewhere").then(Commands.literal("trips")
				.then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
				.then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false))))));
	}

	private static int set(CommandSourceStack src, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		setEnabled(p, on);
		Component msg = on
			? Component.translatable("message.elsewhere.trips.on").withStyle(ChatFormatting.LIGHT_PURPLE)
			: Component.translatable("message.elsewhere.trips.off").withStyle(ChatFormatting.GRAY);
		src.sendSuccess(() -> msg, false);
		if (on && !overworldContent()) {
			src.sendSuccess(() -> Component.translatable("message.elsewhere.trips.server_off").withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}
}
