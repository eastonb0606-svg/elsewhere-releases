package dev.elsewhere.boss.kit;

import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Boss kit entry point. {@link #init()} is idempotent and cheap; call it once from the module's (or the core's) init:
 * it recovers fights cut short by a restart (the summon item comes back after {@link #RETRY_DELAY_TICKS}) and delivers
 * queued summon-item refunds once the delay has passed and the summoner is online.
 *
 * <p>Retry rule ({@code bal-boss-retry}): when everyone dies or leaves, the boss resets (arena restored) and the summon
 * item goes back to the summoner 30 s later, which is also when a retry becomes possible.
 */
public final class BossKit {
	private BossKit() {}

	public static final int RETRY_DELAY_TICKS = 600;
	private static boolean inited;

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> KitStore.of(server).recoverInterrupted());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> KitStore.forget());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) deliverRefunds(server);
		});
	}

	public static int deliverRefunds(MinecraftServer server) {
		KitStore store = KitStore.of(server);
		int n = 0;
		for (KitStore.Refund r : store.takeDueRefunds(id -> server.getPlayerList().getPlayer(id) != null)) {
			ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(r.player));
			ItemStack s = KitStore.stackOf(r.item, r.count);
			if (p == null || s == null) continue;
			give(p, s);
			p.sendSystemMessage(Component.literal("The fight reset. Your summoning item is back; you can try again."));
			n++;
		}
		return n;
	}

	public static void give(ServerPlayer p, ItemStack s) {
		if (!p.getInventory().add(s) && !s.isEmpty()) p.spawnAtLocation(p.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null, s);
	}
}
