package dev.elsewhere.petalwind_glasswaste;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elsewhere.glasswaste.GlasswasteDimension;
import dev.elsewhere.petalwind.PetalwindDimension;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import dev.elsewhere.stub.petalwind_glasswaste.SafeSpot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Dev-only travel and boss commands (dropped at merge): /elsewhere_dev tp|home|boss. Petalwind/Glasswaste only. */
public final class DevCommands {
	private DevCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		// Literal children only: same-named argument nodes from other modules' /elsewhere_dev would merge and overwrite this executor.
		var tp = Commands.literal("tp");
		for (String dim : new String[]{"petalwind", "glasswaste"}) tp.then(Commands.literal(dim).executes(c -> tp(c.getSource(), dim)));
		var boss = Commands.literal("boss");
		for (String id : Bosses.IDS) boss.then(Commands.literal(id).executes(c -> boss(c.getSource(), id)));
		d.register(Commands.literal("elsewhere_dev").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(tp)
			.then(Commands.literal("home").executes(c -> home(c.getSource())))
			.then(boss));
	}

	public static ResourceKey<Level> dimension(String name) {
		return switch (name) {
			case "petalwind" -> PetalwindDimension.LEVEL;
			case "glasswaste" -> GlasswasteDimension.LEVEL;
			default -> null;
		};
	}

	private static int tp(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		ResourceKey<Level> key = dimension(name);
		ServerLevel level = key == null ? null : src.getServer().getLevel(key);
		if (level == null) {
			src.sendFailure(Component.literal("Unknown dimension " + name + " (petalwind, glasswaste)"));
			return 0;
		}
		BlockPos spot = SafeSpot.find(level, BlockPos.containing(player.getX(), 0, player.getZ()), 320);
		if (spot == null) spot = SafeSpot.find(level, BlockPos.ZERO, 640);
		if (spot == null) {
			src.sendFailure(Component.literal("No safe spot found"));
			return 0;
		}
		player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(spot), Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		src.sendSuccess(() -> Component.literal("Welcome to " + name), false);
		return 1;
	}

	private static int home(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		ServerLevel overworld = src.getServer().overworld();
		BlockPos spawn = overworld.getRespawnData().globalPos().pos();
		BlockPos spot = SafeSpot.find(overworld, spawn, 64);
		Vec3 at = Vec3.atBottomCenterOf(spot != null ? spot : spawn.atY(overworld.getMaxY() - 10));
		player.teleport(new TeleportTransition(overworld, at, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		return 1;
	}

	private static int boss(CommandSourceStack src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		ResourceKey<Level> key = id.equals("kazehana") || id.equals("tempestra") ? PetalwindDimension.LEVEL : GlasswasteDimension.LEVEL;
		ServerLevel level = src.getServer().getLevel(key);
		if (level == null || !Bosses.IDS.contains(id)) {
			src.sendFailure(Component.literal("Unknown boss " + id));
			return 0;
		}
		BlockPos at;
		if (player.level() == level) at = player.blockPosition().offset(0, 0, 30);
		else at = new BlockPos(4000, 0, 0);
		level.getChunk(at.getX() >> 4, at.getZ() >> 4); // load first: an unloaded column reads as the minimum build height
		if (key == PetalwindDimension.LEVEL) at = at.atY(Math.max(at.getY(), 170));
		else at = at.atY(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		BossBase boss = Bosses.summon(level, id, at);
		if (boss == null) return 0;
		BlockPos c = boss.arenaCenter();
		Vec3 stand = Vec3.atBottomCenterOf(c).add(0, 1, -Math.min(6, boss.arenaRadius() - 2));
		if (id.equals("tempestra")) stand = Vec3.atBottomCenterOf(dev.elsewhere.boss.tempestra.EyeOfTheGale.platforms(c).get(0));
		player.teleport(new TeleportTransition(level, stand, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
		src.sendSuccess(() -> Component.literal("Summoned " + id + (id.equals("kazehana") ? " (sneak twice to bow back and start)" : "")), false);
		return 1;
	}
}
