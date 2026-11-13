package dev.elsewhere.overworld_bosses.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elsewhere.overworld_bosses.Summons;
import dev.elsewhere.overworld_bosses.world.Blueprints;
import dev.elsewhere.overworld_bosses.world.OwbPiece;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Dev-only: {@code /elsewhere_dev boss <id>} builds the boss's arena next to you and summons it. Dropped at merge. */
public final class DevCommands {
	public static final String[] BOSSES = {"gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh"};

	private DevCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			// Literal child per id (not a shared <id> argument): Brigadier merges same-named argument nodes across modules and only one executor survives.
			var boss = Commands.literal("boss");
			for (String id : BOSSES) boss.then(Commands.literal(id).executes(c -> run(c.getSource(), id)));
			dispatcher.register(Commands.literal("elsewhere_dev").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(boss));
		});
	}

	private static int run(net.minecraft.commands.CommandSourceStack src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		ElsewhereBoss boss = spawnWithArena(src.getLevel(), id, p.blockPosition().offset(0, 0, 18));
		if (boss == null) {
			src.sendFailure(Component.literal("Unknown boss " + id));
			return 0;
		}
		src.sendSuccess(() -> Component.literal("Summoned " + id + " at " + boss.blockPosition().toShortString()), true);
		return 1;
	}

	/** Builds the arena with its centre at `center` and summons the boss through its real summon path. */
	public static ElsewhereBoss spawnWithArena(ServerLevel level, String id, BlockPos center) {
		var sink = Summons.levelSink(level);
		switch (id) {
			case "gulpmother" -> {
				OwbPiece.build(OwbPiece.CROAKING_SHRINE, sink, center, 0, true);
				return Summons.summonGulpmother(level, center.offset(Blueprints.SHRINE_CAULDRON));
			}
			case "strawgrim" -> {
				Blueprints.farmField(sink, center, true);
				return Summons.summonStrawgrim(level, center);
			}
			case "neferkhet" -> {
				OwbPiece.build(OwbPiece.SUN_CHAMBER, sink, center, center.getY() + 4, true);
				return Summons.summonNeferkhet(level, center.offset(Blueprints.SUN_ALTAR));
			}
			case "rimeback" -> {
				OwbPiece.build(OwbPiece.GLACIER_SHELF, sink, center, 0, true);
				return Summons.summonRimeback(level, center.offset(Blueprints.FROZEN_HEART));
			}
			case "vorlagh" -> {
				OwbPiece.build(OwbPiece.WRECK_RING, sink, center, 0, true);
				return Summons.summonVorlaghAt(level, center);
			}
			default -> {
				return null;
			}
		}
	}
}
