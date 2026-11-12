package dev.elsewhere.boss_music;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

/** Client entry point of the boss_music module: tick service, join/leave hooks, the toggle command and the self-test. */
public final class BossMusicClient {
	private BossMusicClient() {}

	private static boolean done;

	public static void initClient() {
		if (done) return;
		done = true;
		BossMusicConfig.load();
		ClientTickEvents.END_CLIENT_TICK.register(BossMusicService::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> BossMusicService.reset(true));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> BossMusicService.reset(false));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
			ClientCommands.literal("elsewhere_bossmusic")
				.executes(c -> {
					c.getSource().sendFeedback(Component.literal("Elsewhere boss music: " + (BossMusicConfig.enabled() ? "on" : "off")
						+ " (/elsewhere_bossmusic true|false)"));
					return 1;
				})
				.then(ClientCommands.argument("enabled", BoolArgumentType.bool()).executes(c -> {
					boolean on = BoolArgumentType.getBool(c, "enabled");
					BossMusicConfig.setEnabled(on);
					c.getSource().sendFeedback(Component.literal("Elsewhere boss music: " + (on ? "on" : "off")));
					return 1;
				}))));
		if (Boolean.getBoolean("boss_music.selftest")) {
			ClientLifecycleEvents.CLIENT_STARTED.register(SelfTest::run);
		}
	}
}
