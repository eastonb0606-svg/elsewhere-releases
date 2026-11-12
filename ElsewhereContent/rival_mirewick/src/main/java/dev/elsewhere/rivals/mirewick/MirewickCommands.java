package dev.elsewhere.rivals.mirewick;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import dev.elsewhere.rivals.mirewick.boss.MagnateBoss;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Op tools: {@code /mirewick boss <marrow|thwaite|sorrel|tolliver|ames>} summons a Magnate where you stand. */
public final class MirewickCommands {
	private MirewickCommands() {}

	static void init() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("mirewick").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("boss").then(Commands.argument("who", StringArgumentType.word()).executes(c -> boss(c.getSource(), StringArgumentType.getString(c, "who")))))));
	}

	static int boss(CommandSourceStack src, String who) {
		Magnate m = Magnate.byKey(who);
		if (m == null) {
			src.sendFailure(Component.literal("Unknown Magnate: " + who));
			return 0;
		}
		MagnateBoss b = MagnateBoss.spawn(src.getLevel(), m, src.getPosition());
		src.sendSuccess(() -> Component.literal(b == null ? "Could not spawn" : "Summoned " + m.key), true);
		return b == null ? 0 : 1;
	}
}
