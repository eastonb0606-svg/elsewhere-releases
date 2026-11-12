package dev.elsewhere.greenhaze_hush.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elsewhere.boss.auris.Auris;
import dev.elsewhere.boss.grumblegut.Grumblegut;
import dev.elsewhere.boss.lithara.Lithara;
import dev.elsewhere.boss.percolax.Percolax;
import dev.elsewhere.greenhaze.Greenhaze;
import dev.elsewhere.greenhaze_hush.DimTravel;
import dev.elsewhere.hush.Hush;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Dev-only travel and boss commands: /elsewhere_dev tp greenhaze|the_hush, home, boss percolax|grumblegut|auris|lithara. */
public final class DevCommands {
	private DevCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, sel) -> dispatcher.register(Commands.literal("elsewhere_dev")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("tp").then(Commands.argument("dimension", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"greenhaze", "the_hush"}, b))
				.executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					String d = StringArgumentType.getString(c, "dimension");
					if (d.equals("greenhaze")) DimTravel.sendTo(p, Greenhaze.LEVEL);
					else if (d.equals("the_hush")) DimTravel.sendTo(p, Hush.LEVEL);
					else { c.getSource().sendFailure(Component.literal("This module has greenhaze and the_hush")); return 0; }
					return 1;
				})))
			.then(Commands.literal("silence").executes(c -> {
				ServerLevel l = c.getSource().getPlayerOrException().level();
				return dev.elsewhere.hush.HushSilence.start(l) ? 1 : 0;
			}))
			.then(Commands.literal("home").executes(c -> {
				DimTravel.home(c.getSource().getPlayerOrException());
				return 1;
			}))
			.then(Commands.literal("boss").then(Commands.argument("id", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"percolax", "grumblegut", "auris", "lithara"}, b))
				.executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					ServerLevel level = p.level();
					BlockPos at = p.blockPosition();
					String id = StringArgumentType.getString(c, "id");
					switch (id) {
						case "percolax" -> Percolax.buildAndSummon(level, at);
						case "grumblegut" -> Grumblegut.buildAndSummon(level, at);
						case "auris" -> Auris.buildAndSummon(level, at);
						case "lithara" -> Lithara.buildAndSummon(level, at);
						default -> { c.getSource().sendFailure(Component.literal("Unknown boss " + id)); return 0; }
					}
					return 1;
				})))));
	}
}
