package dev.elsewhere.kingdom.armies.swarm;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /kingdom armies swarm} (operators): the swarm switch, rings changes and every counter. Merges into the existing {@code /kingdom armies} tree. */
public final class SwarmCommands {
	private SwarmCommands() {}

	static void init() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("kingdom").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("armies").then(Commands.literal("swarm").executes(c -> {
				var src = c.getSource();
				for (String line : report()) src.sendSuccess(() -> Component.literal(line), false);
				return Swarm.on() ? 1 : 0;
			})))));
	}

	/** The counters as text lines (command and self-test log). */
	public static java.util.List<String> report() {
		return java.util.List.of(
			"Swarm-as-one " + (Swarm.on() ? "on" : "off") + " (config key \"swarm\" " + Swarm.configured + "); ring changes " + Swarm.ringChanges,
			"Paths: march " + SwarmPaths.marchHits + " shared / " + SwarmPaths.marchMisses + " searched (" + SwarmPaths.marchStored + " stored, " + SwarmPaths.evicted
				+ " evicted), approach " + SwarmPaths.approachHits + " shared / " + SwarmPaths.approachMisses + " searched",
			"Followers: " + SlotSteer.decisions + " decisions, " + SlotSteer.ribbonSlots + " ribbon slots, recoveries " + SlotSteer.leaderPathJoins + " by the leader's path + "
				+ SlotSteer.realPaths + " real, " + SlotSteer.jumps + " jumps",
			"Targets: " + TargetAssigner.plans + " squad plans, " + TargetAssigner.rays + " rays, " + TargetAssigner.perceptionReuse + " perception reuses, assigned "
				+ TargetAssigner.assignedMelee + " melee + " + TargetAssigner.assignedRanged + " ranged, " + TargetAssigner.heldBack + " held back",
			"Pushing: " + SwarmPush.alliesSkipped + " ally pushes and " + SwarmPush.queriesSkipped + " R2 queries skipped");
	}
}
