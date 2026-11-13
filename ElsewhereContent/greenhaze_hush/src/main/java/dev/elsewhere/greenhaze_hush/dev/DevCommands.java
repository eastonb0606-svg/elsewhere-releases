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
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, sel) -> {
			// Literal children only: same-named argument nodes from other modules' /elsewhere_dev would merge and overwrite this executor.
			var boss = Commands.literal("boss");
			for (String id : new String[]{"percolax", "grumblegut", "auris", "lithara"}) boss.then(Commands.literal(id).executes(c -> summon(c.getSource().getPlayerOrException(), id)));
			dispatcher.register(Commands.literal("elsewhere_dev")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("tp")
					.then(Commands.literal("greenhaze").executes(c -> { DimTravel.sendTo(c.getSource().getPlayerOrException(), Greenhaze.LEVEL); return 1; }))
					.then(Commands.literal("the_hush").executes(c -> { DimTravel.sendTo(c.getSource().getPlayerOrException(), Hush.LEVEL); return 1; })))
				.then(Commands.literal("silence").executes(c -> {
					ServerLevel l = c.getSource().getPlayerOrException().level();
					return dev.elsewhere.hush.HushSilence.start(l) ? 1 : 0;
				}))
				.then(Commands.literal("home").executes(c -> {
					DimTravel.home(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(boss));
		});
	}

	private static int summon(ServerPlayer p, String id) {
		ServerLevel level = p.level();
		BlockPos at = p.blockPosition();
		switch (id) {
			case "percolax" -> Percolax.buildAndSummon(level, at);
			case "grumblegut" -> Grumblegut.buildAndSummon(level, at);
			case "auris" -> Auris.buildAndSummon(level, at);
			case "lithara" -> Lithara.buildAndSummon(level, at);
			default -> { return 0; }
		}
		return 1;
	}
}
