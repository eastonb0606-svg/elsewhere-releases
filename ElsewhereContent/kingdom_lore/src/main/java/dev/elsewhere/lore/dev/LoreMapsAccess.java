package dev.elsewhere.lore.dev;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Test-only bridge to the package-private cartographer hook (kept out of the public API). */
final class LoreMapsAccess {
	static void offer(ServerLevel l, ServerPlayer p, Villager v) {
		dev.elsewhere.lore.LoreMaps.offerForTest(l, p, v);
	}
}
