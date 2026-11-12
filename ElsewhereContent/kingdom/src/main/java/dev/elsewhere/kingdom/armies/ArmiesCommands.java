package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomPerf;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * {@code /kingdom armies}: caps, rings, hygiene, switches and the loaded army scale per kingdom; {@code /kingdom armies battle <attacker> <defender>
 * <heads>} starts a wave battle at your position (paths in the elsewhere namespace, e.g. aldermark hrafnholt 150) and {@code battle stop} ends
 * it (operators).
 */
public final class ArmiesCommands {
	private ArmiesCommands() {}

	static void init() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("kingdom").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("armies").executes(c -> {
				var src = c.getSource();
				int[] r = ArmyLod.rings(src.getLevel());
				src.sendSuccess(() -> Component.literal("Armies 3.0 " + (ArmiesConfig.enabled ? "on" : "off") + ": " + ArmyCaps.state), false);
				src.sendSuccess(() -> Component.literal("Soldiers here: " + KingdomPerf.loaded(src.getLevel()).size() + " live, " + KingdomPerf.activeCount(src.getLevel())
					+ " full AI; rings R0 " + r[0] + ", R1 " + r[1] + ", R2 " + r[2] + ", R3+ " + r[3] + "; player-first keeps " + ArmyLod.keptForPlayer), false);
				src.sendSuccess(() -> Component.literal("Soldier arrows: " + ArrowHygiene.live() + " live (cap " + ArrowHygiene.PER_AREA + "/area), " + ArrowHygiene.retired
					+ " retired after resting, " + ArrowHygiene.retiredForCap + " for the cap; NPC-kill drops suppressed " + NpcKillDrops.suppressed), false);
				for (ArmyScale s : ArmyScale.all().values()) src.sendSuccess(() -> Component.literal(" " + s + (s.problems().isEmpty() ? "" : " PROBLEMS " + s.problems())), false);
				if (!ArmyScale.parseErrors().isEmpty()) src.sendSuccess(() -> Component.literal("Parse errors: " + ArmyScale.parseErrors()), false);
				src.sendSuccess(() -> Component.literal("Switches: waves " + ArmiesConfig.waves + ", routine " + ArmiesConfig.routine + ", attack_tokens " + ArmiesConfig.tokens
					+ ", caps " + (ArmiesConfig.fallbackCaps ? "fallback" : "epic") + " | waves: " + ArmyWaves.battles().size() + " running, " + ArmyWaves.started + " started, "
					+ ArmyWaves.wavesSpawned + " waves, max live " + ArmyWaves.maxLiveSeen + " | routine: " + ArmyRoutine.moved + " moves, " + ArmyRoutine.returned + " returns, "
					+ ArmyRoutine.teleported + " unseen | tokens held " + ArmyLod.heldByTokens), false);
				return KingdomConfig.maxRealSoldiersPerArea;
			})
				.then(Commands.literal("battle")
					.then(Commands.literal("stop").executes(c -> {
						int n = ArmyWaves.battles().size();
						ArmyWaves.stopAll(c.getSource().getServer(), true);
						c.getSource().sendSuccess(() -> Component.literal("Stopped " + n + " wave battle(s)"), true);
						return n;
					}))
					.then(Commands.argument("attacker", StringArgumentType.word()).then(Commands.argument("defender", StringArgumentType.word())
						.then(Commands.argument("heads", IntegerArgumentType.integer(4, 150)).executes(c -> {
							var src = c.getSource();
							Identifier a = Identifier.fromNamespaceAndPath("elsewhere", StringArgumentType.getString(c, "attacker"));
							Identifier df = Identifier.fromNamespaceAndPath("elsewhere", StringArgumentType.getString(c, "defender"));
							int h = IntegerArgumentType.getInteger(c, "heads");
							var b = ArmyWaves.start(src.getLevel(), BlockPos.containing(src.getPosition()), a, df, h, h, -1);
							src.sendSuccess(() -> Component.literal(b == null ? "No battle: nobody could be fielded (caps or unknown kingdom)"
								: "Wave battle " + a.getPath() + " v " + df.getPath() + ", " + h + " a side: " + (b.a.spawned + b.d.spawned) + " live now, the rest come in waves"), true);
							return b == null ? 0 : 1;
						}))))))));
	}
}
