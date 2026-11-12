package dev.elsewhere.stub.petalwind_glasswaste;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * STUB of the core's per-player boss loot: the core delivers drops to a personal loot chest; this stub puts them straight
 * into the player's inventory (or at their feet) and tracks first kills with a player tag.
 */
public final class BossLoot {
	private BossLoot() {}

	/** Marks the kill; returns true if this was the player's first kill of this boss. */
	public static boolean markKill(ServerPlayer player, String bossId) {
		String tag = "elsewhere.killed." + bossId;
		boolean first = !player.entityTags().contains(tag);
		if (first) player.addTag(tag);
		return first;
	}

	public static void deliver(ServerPlayer player, List<ItemStack> drops, Component bossName) {
		for (ItemStack stack : drops) {
			if (stack.isEmpty()) continue;
			player.sendSystemMessage(Component.literal("Loot from ").append(bossName).append(": ").append(stack.getHoverName())
				.append(stack.getCount() > 1 ? " x" + stack.getCount() : "").withStyle(ChatFormatting.GOLD));
			ItemStack copy = stack.copy();
			if (!player.getInventory().add(copy)) player.drop(copy, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}
}
