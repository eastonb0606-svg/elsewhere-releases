package dev.elsewhere.kingdom;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Op tools ({@code /kingdom}, permission level 2): {@code info}, {@code stress <n>} (spawns n soldiers in mixed squads
 * around you, caps applying, and reports MSPT after 5 s), {@code company}, {@code patrol <size>},
 * {@code aggression <player> <value>}, {@code hunt <player>}.
 */
public final class KingdomCommands {
	private KingdomCommands() {}

	private static CommandSourceStack stressSource;
	private static long stressReportTick = -1;

	static void init() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("kingdom").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("info").executes(c -> info(c.getSource())))
			.then(Commands.literal("stress").then(Commands.argument("n", IntegerArgumentType.integer(1, 200)).executes(c -> stress(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
			.then(Commands.literal("company").executes(c -> {
				List<Squad> q = KingdomApi.spawnCompany(c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), Factions.ALDERMARK);
				c.getSource().sendSuccess(() -> Component.literal("Company: " + q.size() + " squads, " + q.stream().mapToInt(Squad::size).sum() + " soldiers"), true);
				return q.size();
			}))
			.then(Commands.literal("patrol").then(Commands.argument("size", IntegerArgumentType.integer(1, 16)).executes(c -> {
				int n = KingdomApi.spawnPatrol(c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), Factions.ALDERMARK, IntegerArgumentType.getInteger(c, "size")).size();
				c.getSource().sendSuccess(() -> Component.literal("Patrol: " + n + " soldiers"), true);
				return n;
			})))
			.then(Commands.literal("aggression").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + " aggression: " + String.format("%.2f", KingdomApi.aggression(p))), false);
				return 1;
			}).then(Commands.argument("value", FloatArgumentType.floatArg(0F, KingdomAggression.MAX)).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				KingdomAggression.set(p.level(), p.getUUID(), FloatArgumentType.getFloat(c, "value"));
				c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + " aggression set to " + KingdomApi.aggression(p)), true);
				return 1;
			}))))
			.then(Commands.literal("factions").executes(c -> factions(c.getSource())))
			.then(Commands.literal("stance")
				.then(Commands.argument("a", com.mojang.brigadier.arguments.StringArgumentType.word())
					.then(Commands.argument("b", com.mojang.brigadier.arguments.StringArgumentType.word())
						.then(Commands.argument("stance", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> stance(c.getSource(),
							com.mojang.brigadier.arguments.StringArgumentType.getString(c, "a"), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "b"),
							com.mojang.brigadier.arguments.StringArgumentType.getString(c, "stance")))))))
			.then(Commands.literal("war")
				.then(Commands.literal("status").executes(c -> warStatus(c.getSource())))
				.then(Commands.literal("log").executes(c -> {
					for (String l : dev.elsewhere.kingdom.war.WarApi.log(c.getSource().getServer())) c.getSource().sendSuccess(() -> Component.literal(l), false);
					return 1;
				}))
				.then(Commands.literal("step").then(Commands.argument("days", IntegerArgumentType.integer(1, 400)).executes(c -> {
					dev.elsewhere.kingdom.war.WarApi.stepDays(c.getSource().getServer().overworld(), IntegerArgumentType.getInteger(c, "days"));
					return warStatus(c.getSource());
				}))))
			.then(Commands.literal("hunt").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				Squad q = KingdomAggression.startHunt(p.level(), p, Math.max(KingdomAggression.HUNT_THRESHOLD, KingdomApi.aggression(p)));
				c.getSource().sendSuccess(() -> Component.literal(q == null ? "No spot or no room for a hunt" : "Hunting squad of " + q.size() + " sent"), true);
				return q == null ? 0 : 1;
			})))));
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (stressReportTick >= 0 && srv.getTickCount() >= stressReportTick) {
				stressReportTick = -1;
				double mspt = srv.getAverageTickTimeNanos() / 1e6;
				CommandSourceStack s = stressSource;
				if (s != null) s.sendSuccess(() -> Component.literal(String.format("Kingdom stress: MSPT %.2f ms (avg of last 100 ticks), %d soldiers loaded, %d active",
					mspt, KingdomPerf.loadedCount(), KingdomPerf.activeCount(s.getLevel()))), false);
			}
		});
	}

	private static int warStatus(CommandSourceStack src) {
		var srv = src.getServer();
		var ids = dev.elsewhere.kingdom.war.WarLayer.participants(srv);
		if (ids.size() < 2) {
			src.sendSuccess(() -> Component.literal("War layer idle: " + ids.size() + " kingdom(s) with a realm"), false);
			return 0;
		}
		for (var f : ids) {
			var w = dev.elsewhere.kingdom.war.WarApi.class;
			src.sendSuccess(() -> Component.literal(String.format("%s: %d villages, %.0f heads, vet %.2f, treasury %.0f%s", f.getPath(), dev.elsewhere.kingdom.war.WarApi.villagesOf(srv, f),
				dev.elsewhere.kingdom.war.WarApi.heads(srv, f), dev.elsewhere.kingdom.war.WarApi.veterancy(srv, f), dev.elsewhere.kingdom.war.WarApi.treasury(srv, f),
				dev.elsewhere.kingdom.war.WarApi.brainDegraded(srv, f) ? " BRAIN DEGRADED" : "")), false);
		}
		var ct = dev.elsewhere.kingdom.war.WarApi.coalitionTarget(srv);
		src.sendSuccess(() -> Component.literal("coalition target: " + (ct == null ? "none" : ct.getPath()) + ", real battles active: " + dev.elsewhere.kingdom.war.WarBattles.active()), false);
		return ids.size();
	}

	private static int factions(CommandSourceStack src) {
		ServerLevel l = src.getLevel();
		KingdomState st = KingdomState.get(l.getServer().overworld());
		for (Factions.Faction f : Factions.all()) {
			long squads = KingdomState.get(l).squads().stream().filter(q -> q.faction().equals(f.id)).count();
			String agg = src.getEntity() instanceof ServerPlayer p ? String.format(", your aggression %.2f", KingdomApi.aggression(p, f.id)) : "";
			src.sendSuccess(() -> Component.literal(f.id + " (" + f.displayName().getString() + "): hostile to players " + f.hostileToPlayers() + ", " + squads + " squads here"
				+ (st.isFallen(f.id) ? ", FALLEN" : "") + agg), false);
		}
		src.sendSuccess(() -> Component.literal(st.clashes().size() + " abstract clashes recorded, default stance " + KingdomConfig.defaultStance()), false);
		return 1;
	}

	private static int stance(CommandSourceStack src, String a, String b, String s) {
		net.minecraft.resources.Identifier ia = net.minecraft.resources.Identifier.tryParse(a.contains(":") ? a : "elsewhere:" + a);
		net.minecraft.resources.Identifier ib = net.minecraft.resources.Identifier.tryParse(b.contains(":") ? b : "elsewhere:" + b);
		if (ia == null || ib == null || !Factions.exists(ia) || !Factions.exists(ib)) {
			src.sendFailure(Component.literal("Unknown faction (see /kingdom factions)"));
			return 0;
		}
		Factions.Stance st = Factions.Stance.parse(s, null);
		if (st == null) {
			src.sendFailure(Component.literal("Stance must be war, peace or allied"));
			return 0;
		}
		KingdomApi.setStance(src.getServer(), ia, ib, st);
		src.sendSuccess(() -> Component.literal(ia + " and " + ib + ": " + st), true);
		return 1;
	}

	private static int info(CommandSourceStack src) {
		ServerLevel l = src.getLevel();
		KingdomState st = KingdomState.get(l);
		long virt = st.squads().stream().filter(Squad::isVirtual).count();
		int vs = st.squads().stream().filter(Squad::isVirtual).mapToInt(q -> q.virtualMembers().size()).sum();
		String agg = src.getEntity() instanceof ServerPlayer p ? String.format(", your aggression %.2f", KingdomApi.aggression(p)) : "";
		src.sendSuccess(() -> Component.literal("Kingdom: " + KingdomPerf.loadedCount() + " soldiers loaded (" + KingdomPerf.activeCount(l) + " active), "
			+ st.squads().size() + " squads (" + virt + " virtual, " + vs + " stored soldiers)" + agg), false);
		return 1;
	}

	private static int stress(CommandSourceStack src, int n) {
		ServerLevel l = src.getLevel();
		BlockPos c = BlockPos.containing(src.getPosition());
		int made = 0;
		for (int i = 0; made < n && i < 40; i++) {
			BlockPos at = c.offset((i % 4) * 8 - 12, 0, (i / 4) * 8 - 12);
			at = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
			int got = KingdomApi.spawnPatrol(l, at, Factions.ALDERMARK, Math.min(8, n - made)).size();
			if (got == 0) break;
			made += got;
		}
		int fm = made;
		src.sendSuccess(() -> Component.literal("Kingdom stress: spawned " + fm + "/" + n + " soldiers (caps apply); MSPT report in 5 s"), true);
		stressSource = src;
		stressReportTick = l.getServer().getTickCount() + 100;
		return made;
	}
}
