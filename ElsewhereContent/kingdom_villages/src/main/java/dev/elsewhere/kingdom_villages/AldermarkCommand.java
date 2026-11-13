package dev.elsewhere.kingdom_villages;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * {@code /aldermark} (ops): {@code status}, {@code restore} (rebuild the nearest village from its original layout and make
 * it immune), {@code undo} (remove the nearest outpost / hamlet / road), {@code conquest on|off} (server switch),
 * {@code siege} and {@code liberate} (QA helpers that still obey every safety rule).
 */
public final class AldermarkCommand {
	private AldermarkCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("aldermark").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("status").executes(AldermarkCommand::status))
			.then(Commands.literal("restore").executes(AldermarkCommand::restore))
			.then(Commands.literal("undo").executes(AldermarkCommand::undo))
			.then(Commands.literal("conquest")
				.then(Commands.literal("on").executes(c -> conquest(c, true)))
				.then(Commands.literal("off").executes(c -> conquest(c, false))))
			.then(Commands.literal("siege").executes(AldermarkCommand::siege))
			.then(Commands.literal("liberate").executes(AldermarkCommand::liberate))
			.then(Commands.literal("rush")
				.then(Commands.literal("on").executes(c -> rush(c, true)))
				.then(Commands.literal("off").executes(c -> rush(c, false)))));
	}

	static @Nullable VillageRecord nearestVillage(ServerLevel level, BlockPos p, int max) {
		VillageRecord best = null;
		double bd = max;
		for (VillageRecord r : VillageLedger.get(level).villages.values()) {
			double d = RealmLocator.horizontal(r.center, p);
			if (d < bd) {
				bd = d;
				best = r;
			}
		}
		return best;
	}

	static @Nullable VillageRecord nearestSite(ServerLevel level, BlockPos p, int max) {
		VillageRecord best = null;
		double bd = max;
		for (VillageRecord r : VillageLedger.get(level).sites.values()) {
			double d = RealmLocator.horizontal(r.center, p);
			if (d < bd) {
				bd = d;
				best = r;
			}
		}
		return best;
	}

	static ServerLevel overworld(CommandContext<CommandSourceStack> c) {
		return c.getSource().getServer().overworld();
	}

	static int status(CommandContext<CommandSourceStack> c) {
		ServerLevel level = overworld(c);
		BlockPos p = BlockPos.containing(c.getSource().getPosition());
		VillageLedger l = VillageLedger.get(level);
		c.getSource().sendSuccess(() -> Component.literal("Aldermark conquest " + (l.conquestOn ? "ON" : "OFF") + ", villages known " + l.villages.size()
			+ ", outposts " + l.outpostsFounded + "/" + Conquest.MAX_OUTPOSTS + ", sites " + l.sites.size()), false);
		VillageRecord r = nearestVillage(level, p, 512);
		if (r != null) c.getSource().sendSuccess(() -> Component.literal("Nearest village " + r.center.toShortString() + ": " + r.state
			+ (r.reason.isEmpty() ? "" : " (" + r.reason + ")") + ", castle " + (r.castleDist < 0 ? "unknown" : (int) r.castleDist + " blocks")
			+ ", changed blocks " + r.journal.size() + ", plan " + r.planIndex + "/" + r.plan.size()), false);
		return 1;
	}

	static int restore(CommandContext<CommandSourceStack> c) {
		ServerLevel level = overworld(c);
		VillageRecord r = nearestVillage(level, BlockPos.containing(c.getSource().getPosition()), 256);
		if (r == null) {
			c.getSource().sendFailure(Component.literal("No known village within 256 blocks."));
			return 0;
		}
		int[] res = restoreVillage(level, r);
		c.getSource().sendSuccess(() -> Component.literal("Restored village at " + r.center.toShortString() + ": " + res[0] + " blocks back to the original layout, "
			+ res[1] + " left alone (changed by players). The village is now immune to the kingdom."), true);
		return 1;
	}

	/** Restores the original layout, removes the kingdom's soldiers and Serfs there, and makes the village immune. */
	public static int[] restoreVillage(ServerLevel level, VillageRecord r) {
		BlockWriter.extinguish(level, r);
		VillageManager.releaseSerfs(level, r, false);
		for (Entity e : VillageManager.soldiers(level, r)) e.discard();
		int[] res = BlockWriter.restore(level, r);
		r.reason = "restored by an operator";
		VillageManager.setState(level, r, VillageRecord.State.RESTORED);
		return res;
	}

	static int undo(CommandContext<CommandSourceStack> c) {
		ServerLevel level = overworld(c);
		VillageRecord s = nearestSite(level, BlockPos.containing(c.getSource().getPosition()), 256);
		if (s == null) {
			c.getSource().sendFailure(Component.literal("No kingdom outpost, hamlet or road within 256 blocks."));
			return 0;
		}
		for (Entity e : VillageManager.soldiers(level, s)) e.discard();
		int[] res = BlockWriter.restore(level, s);
		VillageLedger l = VillageLedger.get(level);
		l.sites.remove(s.id);
		l.dirty();
		c.getSource().sendSuccess(() -> Component.literal("Removed " + s.kind + " at " + s.center.toShortString() + ": " + res[0] + " blocks restored, " + res[1] + " left alone."), true);
		return 1;
	}

	static int conquest(CommandContext<CommandSourceStack> c, boolean on) {
		VillageLedger l = VillageLedger.get(overworld(c));
		l.conquestOn = on;
		l.dirty();
		c.getSource().sendSuccess(() -> Component.literal("Aldermark conquest (sieges, occupation, outposts) is now " + (on ? "ON" : "OFF") + "."), true);
		return 1;
	}

	/**
	 * QA helper (shot staging): rebuilds run 60 block ops per tick instead of 1 per second, the pace the lane's own gametest uses, so a
	 * full burn/rebuild fits the 72000-tick player-presence window. Every safety rule still applies (BlockWriter); reset on server stop.
	 */
	static int rush(CommandContext<CommandSourceStack> c, boolean on) {
		VillageManager.opInterval = on ? 1 : VillageManager.OP_INTERVAL;
		VillageManager.opsPerRun = on ? 60 : 1;
		VillageManager.siegeLength = on ? 300L : VillageManager.SIEGE_LENGTH;
		c.getSource().sendSuccess(() -> Component.literal("Village rebuild pace: " + (on ? "RUSH (60 ops per tick)" : "normal (1 op per second)") + "."), true);
		return 1;
	}

	static int siege(CommandContext<CommandSourceStack> c) {
		ServerLevel level = overworld(c);
		VillageRecord r = nearestVillage(level, BlockPos.containing(c.getSource().getPosition()), 256);
		if (r == null || !VillageManager.startSiege(level, r, null, true)) {
			c.getSource().sendFailure(Component.literal("No siege: " + (r == null ? "no known village nearby" : "village is " + r.state + (r.reason.isEmpty() ? "" : " (" + r.reason + ")"))));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Siege started at " + r.center.toShortString() + "."), true);
		return 1;
	}

	static int liberate(CommandContext<CommandSourceStack> c) {
		ServerLevel level = overworld(c);
		VillageRecord r = nearestVillage(level, BlockPos.containing(c.getSource().getPosition()), 256);
		if (r == null || !(r.state == VillageRecord.State.SIEGE || r.isOccupiedLike())) {
			c.getSource().sendFailure(Component.literal("No besieged or occupied village nearby."));
			return 0;
		}
		for (dev.elsewhere.kingdom.KingdomSoldier e : VillageManager.soldiers(level, r)) if (r.taxSquad == null || !r.taxSquad.equals(e.squadId())) e.kill(level);
		if (r.state != VillageRecord.State.LIBERATED) VillageManager.liberate(level, r);
		c.getSource().sendSuccess(() -> Component.literal("Liberated village at " + r.center.toShortString() + "."), true);
		return 1;
	}

	static boolean unused(Level l) {
		return l != null;
	}
}
