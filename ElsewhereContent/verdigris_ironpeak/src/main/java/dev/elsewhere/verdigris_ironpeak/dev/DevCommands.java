package dev.elsewhere.verdigris_ironpeak.dev;

import dev.elsewhere.boss.galvanox.GalvanoxSummon;
import dev.elsewhere.boss.hrolf.HrolfSummon;
import dev.elsewhere.boss.thurgrim.ThurgrimSummon;
import dev.elsewhere.boss.verdigrim.VerdigrimSummon;
import dev.elsewhere.stub.verdigris_ironpeak.ReturnRiftStub;
import dev.elsewhere.stub.verdigris_ironpeak.Trips;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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

/** Dev-only travel and boss commands (dropped at merge): /elsewhere_dev tp|home|boss. */
public final class DevCommands {
	private DevCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(Commands.literal("elsewhere_dev")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("tp")
				.then(Commands.literal("verdigris_sea").executes(ctx -> tp(ctx.getSource(), Fx.VERDIGRIS_SEA)))
				.then(Commands.literal("ironpeak").executes(ctx -> tp(ctx.getSource(), Fx.IRONPEAK))))
			.then(Commands.literal("home").executes(ctx -> {
				ReturnRiftStub.sendHome(ctx.getSource().getPlayerOrException());
				return 1;
			}))
			.then(Commands.literal("boss")
				.then(Commands.literal("verdigrim").executes(ctx -> boss(ctx.getSource(), "drowned_cathedral", 0, (l, c) -> VerdigrimSummon.summon(l, c))))
				.then(Commands.literal("galvanox").executes(ctx -> boss(ctx.getSource(), "lighthouse", 0, (l, c) -> GalvanoxSummon.summon(l, c))))
				.then(Commands.literal("thurgrim").executes(ctx -> boss(ctx.getSource(), "summit_throne", 0, (l, c) -> ThurgrimSummon.summon(l, c))))
				.then(Commands.literal("hrolf").executes(ctx -> boss(ctx.getSource(), "proving_grounds", 0, (l, c) -> HrolfSummon.summon(l, c)))))
			.then(Commands.literal("build")
				.then(Commands.literal("clockwork_rig").executes(ctx -> build(ctx.getSource(), "clockwork_rig")))
				.then(Commands.literal("clan_hold").executes(ctx -> build(ctx.getSource(), "clan_hold")))
				.then(Commands.literal("return_rift").executes(ctx -> build(ctx.getSource(), "return_rift_ironpeak"))))));
	}

	private static int tp(CommandSourceStack src, ResourceKey<Level> dim) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		ServerLevel level = src.getServer().getLevel(dim);
		if (level == null) {
			src.sendFailure(Component.literal("Dimension not loaded: " + dim.identifier()));
			return 0;
		}
		BlockPos spot = Trips.safeSpot(level, new BlockPos(0, 0, 0));
		p.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(spot), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
		return 1;
	}

	/** Builds the arena 2 blocks above the player's feet (Thurgrim's plateau 12 above, for a real cliff) and summons the boss. */
	private static int boss(CommandSourceStack src, String blueprint, int dy, BiFunction<ServerLevel, BlockPos, ?> summon) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		ServerLevel level = p.level();
		BlockPos c = p.blockPosition().above(dy);
		Blueprint bp = Blueprint.ALL.get(blueprint);
		bp.build(new BlockSink(level, c, null, level.getRandom()), level.getRandom());
		p.teleportTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 8.5);
		summon.apply(level, c);
		return 1;
	}

	private static int build(CommandSourceStack src, String blueprint) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		Blueprint.ALL.get(blueprint).build(new BlockSink(p.level(), p.blockPosition(), null, p.getRandom()), p.getRandom());
		return 1;
	}
}
