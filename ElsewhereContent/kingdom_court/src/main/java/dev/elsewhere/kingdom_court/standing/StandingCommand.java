package dev.elsewhere.kingdom_court.standing;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Op-only {@code /diplomacy standing <kingdom> <-100..100>}: sets your own Standing so the SHOTS-3.0 P7 lines (dialogue per band, the
 * chip's Hostile warning) can be set up in a few seconds. Merged into the existing {@code /diplomacy} root by Brigadier; gamemasters only.
 */
public final class StandingCommand {
	private StandingCommand() {}

	static void register() {
		CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(Commands.literal("diplomacy")
			.then(Commands.literal("standing").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("kingdom", StringArgumentType.word()).then(Commands.argument("value", IntegerArgumentType.integer(-100, 100)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Identifier f = Identifier.tryBuild("elsewhere", StringArgumentType.getString(c, "kingdom"));
					if (f == null || !Factions.exists(f)) {
						c.getSource().sendFailure(Component.literal("Unknown kingdom (try aldermark, hrafnholt, azmar or mirewick)"));
						return 0;
					}
					int v = IntegerArgumentType.getInteger(c, "value");
					KingdomApi.setStanding(p, f, v);
					c.getSource().sendSuccess(() -> Component.literal("Standing in " + f.getPath() + " set to " + v), true);
					return 1;
				}))))));
	}
}
