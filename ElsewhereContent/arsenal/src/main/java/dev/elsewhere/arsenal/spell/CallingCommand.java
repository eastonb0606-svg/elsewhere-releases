package dev.elsewhere.arsenal.spell;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /calling}: read-only readout of the Callings your gear gives you (GEAR.md A1). Nothing to pick; runs only when typed. */
public final class CallingCommand {
	private CallingCommand() {}

	static void init() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("calling").executes(c -> show(c.getSource()))));
	}

	public static String readout(ServerPlayer p) {
		StringBuilder sb = new StringBuilder("Callings (from what you wear and hold):");
		for (Calling.Path path : Calling.Path.values()) {
			float m = Calling.mastery(p, path);
			sb.append("\n ").append(Calling.name(path)).append(": ").append(rankName(Calling.rank(p, path))).append(' ')
				.append(Math.round(m * 100)).append("% (x").append(String.format("%.2f", Calling.damageOf(m))).append(')');
		}
		sb.append(String.format("\n Spells: cost x%.2f, max Glimmer +%.0f, regen x%.2f", Calling.costMult(p, false), Calling.maxGlimmerBonus(p), Calling.regenMult(p)));
		float s = Calling.mastery(p, Calling.Path.SUMMONER);
		sb.append(String.format("\n Minions: up to %d, %d s, reserve %.1f each", Calling.slotCapOf(s), Calling.battleTimerTicks(p) / 20, Calling.minionReserve(p)));
		long wait = Calling.ticksUntilSettled(p);
		if (wait > 0) sb.append("\n Your new gear settles in ").append(wait / 20 + 1).append(" s.");
		return sb.toString();
	}

	static int show(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		if (p == null) return 0;
		String out = readout(p);
		src.sendSuccess(() -> Component.literal(out), false);
		return 1;
	}

	static String rankName(Calling.Rank r) {
		return switch (r) {
			case PRIMARY -> "Primary";
			case SECONDARY -> "Secondary";
			case OPEN -> "Open";
			case UNATTUNED -> "Unattuned";
		};
	}
}
