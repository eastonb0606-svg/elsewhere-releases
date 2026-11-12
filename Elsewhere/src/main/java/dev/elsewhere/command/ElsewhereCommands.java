package dev.elsewhere.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elsewhere.boss.mother_creak.ElderTrunkArena;
import dev.elsewhere.boss.selenne.MoonringArena;
import dev.elsewhere.core.travel.RiftTravel;
import dev.elsewhere.core.trip.AccidentalTrips;
import dev.elsewhere.core.trip.TripTrigger;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** {@code /elsewhere ...}: op tools for travel, arenas and the trip framework. */
public final class ElsewhereCommands {
	private ElsewhereCommands() {
	}

	/** {@code /elsewhere trips on|off}: any player, saved with the player (survives death and restarts). */
	public static int setTrips(final CommandSourceStack src, final boolean on) throws CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		AccidentalTrips.setPlayerEnabled(p, on);
		src.sendSuccess(() -> Component.translatable(on ? "message.elsewhere.trips.on" : "message.elsewhere.trips.off")
			.withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), false);
		if (on && !AccidentalTrips.overworldContent()) {
			src.sendSuccess(() -> Component.translatable("message.elsewhere.trips.server_off").withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> dispatcher.register(
			Commands.literal("elsewhere")
				.then(Commands.literal("travel").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.argument("dimension", DimensionArgument.dimension()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					ServerLevel target = DimensionArgument.getDimension(ctx, "dimension");
					return RiftTravel.travelNow(p, target.dimension()) ? 1 : 0;
				})))
				.then(Commands.literal("rift").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.argument("dimension", DimensionArgument.dimension()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					ServerLevel target = DimensionArgument.getDimension(ctx, "dimension");
					return RiftTravel.openWayfinderRift(p, target.dimension()) ? 1 : 0;
				})))
				.then(Commands.literal("home").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> RiftTravel.travelNow(ctx.getSource().getPlayerOrException(), null) ? 1 : 0))
				.then(Commands.literal("arena").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("mother_creak").executes(ctx -> {
						ServerPlayer p = ctx.getSource().getPlayerOrException();
						ElderTrunkArena.buildAndSpawn(p.level(), p.blockPosition(), p);
						return 1;
					}))
					.then(Commands.literal("selenne").executes(ctx -> {
						ServerPlayer p = ctx.getSource().getPlayerOrException();
						MoonringArena.build(p.level(), p.blockPosition().offset(0, 0, 18));
						ctx.getSource().sendSuccess(() -> Component.literal("Moonring built 18 blocks south; pick the Moon Bloom at its centre."), false);
						return 1;
					})))
				// any player: /elsewhere trips (status + current chances), /elsewhere trips on|off (saved per player)
				.then(Commands.literal("trips")
					.then(Commands.literal("on").executes(ctx -> setTrips(ctx.getSource(), true)))
					.then(Commands.literal("off").executes(ctx -> setTrips(ctx.getSource(), false)))
					.executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					boolean on = AccidentalTrips.playerEnabled(p);
					ctx.getSource().sendSuccess(() -> Component.translatable(on ? "message.elsewhere.trips.on" : "message.elsewhere.trips.off")
						.withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), false);
					if (!AccidentalTrips.overworldContent()) {
						ctx.getSource().sendSuccess(() -> Component.translatable("message.elsewhere.trips.server_off").withStyle(ChatFormatting.GRAY), false);
					}
					ctx.getSource().sendSuccess(() -> Component.literal("Home radius: " + AccidentalTrips.inHome(p) + ", region "
						+ AccidentalTrips.regionKey(p)), false);
					for (TripTrigger t : AccidentalTrips.triggers().values()) {
						double c = AccidentalTrips.currentChance(p, t, true);
						ctx.getSource().sendSuccess(() -> Component.literal(String.format(" %s (%s): base %.3f%%, now %.4f%%", t.id(), t.kind(),
							AccidentalTrips.baseChance(t, p) * 100.0, c * 100.0)), false);
					}
					return 1;
				}))
				.then(Commands.literal("trip").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.argument("trigger", StringArgumentType.string()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					TripTrigger t = AccidentalTrips.triggers().get(StringArgumentType.getString(ctx, "trigger"));
					if (t == null) {
						ctx.getSource().sendFailure(Component.literal("Unknown trigger"));
						return 0;
					}
					AccidentalTrips.startPull(p, t);
					return 1;
				})))
		));
	}
}
