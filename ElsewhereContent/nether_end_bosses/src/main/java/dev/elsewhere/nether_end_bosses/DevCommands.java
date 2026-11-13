package dev.elsewhere.nether_end_bosses;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only commands (op level 2), dropped at merge:
 * {@code /elsewhere_dev boss <id> [arena]} builds the boss's arena at your feet and summons it through its trigger;
 * {@code /elsewhere_dev tp nether|end|overworld}, {@code /elsewhere_dev home};
 * {@code /elsewhere_dev attack <id>} and {@code /elsewhere_dev hp <ratio>} drive the nearest boss.
 */
public final class DevCommands {
	/** Boss id -> summon at an origin (arena already built). Filled by each boss's content class. */
	public static final Map<String, BiFunction<ServerLevel, BlockPos, ElsewhereBoss>> SUMMONERS = new java.util.LinkedHashMap<>();
	/** Boss id -> the dimension its arena belongs in (the command moves you there first). */
	public static final Map<String, ResourceKey<Level>> HOME_DIMENSION = new java.util.LinkedHashMap<>();

	private DevCommands() {}

	static void register(CommandDispatcher<CommandSourceStack> d) {
		// Literal child per id (not a shared <id> argument): Brigadier merges same-named argument nodes across modules and only one executor survives.
		var bossNode = Commands.literal("boss");
		for (String id : new String[]{"grunthar", "solaxis", "mournmaw", "ossyra", "aphelion"})
			bossNode.then(Commands.literal(id).executes(c -> boss(c, id, true)).then(Commands.literal("arena").executes(c -> boss(c, id, false))));
		d.register(Commands.literal("elsewhere_dev").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(bossNode)
			.then(Commands.literal("tp")
				.then(Commands.literal("nether").executes(c -> tp(c, Level.NETHER)))
				.then(Commands.literal("end").executes(c -> tp(c, Level.END)))
				.then(Commands.literal("overworld").executes(c -> tp(c, Level.OVERWORLD))))
			.then(Commands.literal("home").executes(c -> tp(c, Level.OVERWORLD)))
			.then(Commands.literal("attack").then(Commands.argument("attack", StringArgumentType.word()).executes(DevCommands::attack)))
			.then(Commands.literal("hp").then(Commands.argument("ratio", FloatArgumentType.floatArg(0.01F, 1.0F)).executes(DevCommands::hp))));
	}

	private static int boss(CommandContext<CommandSourceStack> c, String id, boolean summon) {
		ArenaBuilder builder = ArenaBuilder.BY_BOSS.get(id);
		if (builder == null) {
			c.getSource().sendFailure(Component.literal("Unknown boss " + id));
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		BlockPos origin = BlockPos.containing(c.getSource().getPosition());
		builder.build(level, origin, level.getRandom());
		if (summon && SUMMONERS.containsKey(id)) {
			ElsewhereBoss boss = SUMMONERS.get(id).apply(level, origin);
			c.getSource().sendSuccess(() -> Component.literal("Built the " + id + " arena and summoned " + (boss == null ? "nothing" : boss.getName().getString())), true);
		} else {
			c.getSource().sendSuccess(() -> Component.literal("Built the " + id + " arena; anchor at " + builder.anchor(origin).toShortString()), true);
		}
		return 1;
	}

	private static int tp(CommandContext<CommandSourceStack> c, ResourceKey<Level> dim) {
		ServerPlayer player = c.getSource().getPlayer();
		if (player == null) return 0;
		ServerLevel level = c.getSource().getServer().getLevel(dim);
		BlockPos at;
		if (dim == Level.OVERWORLD) {
			at = level.getRespawnData().pos();
			at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		} else if (dim == Level.END) {
			at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(1200, 0, 0));
			if (at.getY() <= level.getMinY() + 1) {
				at = new BlockPos(1200, 70, 0);
				Build.box(level, at.offset(-2, -1, -2), at.offset(2, -1, 2), net.minecraft.world.level.block.Blocks.END_STONE.defaultBlockState());
			}
		} else {
			at = new BlockPos(0, 70, 0);
			Build.box(level, at.offset(-2, -1, -2), at.offset(2, -1, 2), net.minecraft.world.level.block.Blocks.NETHERRACK.defaultBlockState());
			Build.box(level, at.offset(-2, 0, -2), at.offset(2, 3, 2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}
		Vec3 pos = Vec3.atBottomCenterOf(at);
		player.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
		return 1;
	}

	private static ElsewhereBoss nearest(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		Vec3 at = c.getSource().getPosition();
		List<ElsewhereBoss> list = level.getEntitiesOfClass(ElsewhereBoss.class, new net.minecraft.world.phys.AABB(BlockPos.containing(at)).inflate(64));
		return list.stream().min(Comparator.comparingDouble(b -> b.distanceToSqr(at))).orElse(null);
	}

	private static int attack(CommandContext<CommandSourceStack> c) {
		ElsewhereBoss boss = nearest(c);
		String id = StringArgumentType.getString(c, "attack");
		if (boss == null || !boss.forceAttack(id)) {
			c.getSource().sendFailure(Component.literal("No boss nearby or no attack " + id));
			return 0;
		}
		return 1;
	}

	private static int hp(CommandContext<CommandSourceStack> c) {
		ElsewhereBoss boss = nearest(c);
		if (boss == null) return 0;
		boss.setHealth(boss.getMaxHealth() * FloatArgumentType.getFloat(c, "ratio"));
		return 1;
	}
}
