package dev.elsewhere.lore;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

/** Operator command {@code /aldermark locate} (permission level 2): the realm centre from the court module, clickable to teleport. */
public final class LoreCommands {
	private LoreCommands() {}

	static void init() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
			d.register(Commands.literal("aldermark").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("locate").executes(LoreCommands::locate)));
			for (LoreFactions.Lore f : LoreFactions.ALL.values()) {
				if (!f.rival()) continue;
				d.register(Commands.literal(f.key).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("locate").executes(c -> locateFaction(c, f))));
			}
		});
	}

	private static int locateFaction(CommandContext<CommandSourceStack> c, LoreFactions.Lore f) {
		CommandSourceStack src = c.getSource();
		BlockPos p = LoreMaps.courtRealmCenter(src.getServer(), f.id);
		if (p == null || LoreMaps.courtFallen(src.getServer(), f.id)) {
			src.sendFailure(Component.translatable("commands.elsewhere." + f.key + "_locate.failed"));
			return 0;
		}
		src.sendSuccess(() -> locateMessage(f, p, src.getPosition().x, src.getPosition().z), false);
		return 1;
	}

	/** Per-faction "The X realm centre is at [x, y, z] (N blocks away)". */
	public static Component locateMessage(LoreFactions.Lore f, BlockPos p, double fromX, double fromZ) {
		if (!f.rival()) return locateMessage(p, fromX, fromZ);
		return Component.translatable("commands.elsewhere." + f.key + "_locate.success", coords(p), (int) Math.round(Math.hypot(p.getX() - fromX, p.getZ() - fromZ)));
	}

	private static Component coords(BlockPos p) {
		return Component.translatable("chat.coordinates", p.getX(), p.getY(), p.getZ()).withStyle(Style.EMPTY
			.withColor(ChatFormatting.GREEN)
			.withClickEvent(new ClickEvent.RunCommand("/tp @s " + p.getX() + " " + p.getY() + " " + p.getZ()))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
	}

	private static int locate(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		BlockPos p = LoreMaps.courtRealmCenter(src.getServer());
		if (p == null) {
			src.sendFailure(Component.translatable("commands.elsewhere.aldermark_locate.failed"));
			return 0;
		}
		src.sendSuccess(() -> locateMessage(p, src.getPosition().x, src.getPosition().z), false);
		return 1;
	}

	/** "The Aldermark realm centre is at [x, y, z] (N blocks away)" with the coordinates clickable, like vanilla /locate. */
	public static Component locateMessage(BlockPos p, double fromX, double fromZ) {
		Component coords = Component.translatable("chat.coordinates", p.getX(), p.getY(), p.getZ()).withStyle(Style.EMPTY
			.withColor(ChatFormatting.GREEN)
			.withClickEvent(new ClickEvent.RunCommand("/tp @s " + p.getX() + " " + p.getY() + " " + p.getZ()))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
		int dist = (int) Math.round(Math.hypot(p.getX() - fromX, p.getZ() - fromZ));
		return Component.translatable("commands.elsewhere.aldermark_locate.success", coords, dist);
	}
}
