package dev.elsewhere.kingdom.client.lod;

import dev.elsewhere.kingdom.armies.lod.RenderLod;
import dev.elsewhere.kingdom.client.KingdomClientConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.network.chat.Component;

/**
 * Client render LOD for armies (ARMIES.md 3.7): distance tiers and the full-detail cap for soldiers ({@link SoldierLod}), baked LOD skins
 * ({@link LodSkins}), culling past 96 blocks, and the battle particle and sound limits ({@link BattleFx}). One call from
 * {@code KingdomClient.initClient()}. Everything is event-driven: render-state extraction, one end-of-extraction callback per frame,
 * entity load/unload, and the particle/sound entry points (mixins in {@code elsewhere_kingdom_lod.mixins.json}).
 * {@code /kingdomlod} prints the live numbers. Kill switch: {@code "client_lod": false} in {@code config/elsewhere_armies.json}.
 */
public final class ArmyLodClient {
	private ArmyLodClient() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		RenderLod.load();
		LevelExtractionEvents.END_EXTRACTION.register(ctx -> SoldierLod.endFrame());
		ClientEntityEvents.ENTITY_LOAD.register(BattleFx::onLoad);
		ClientEntityEvents.ENTITY_UNLOAD.register(BattleFx::onUnload);
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("kingdomlod").executes(c -> {
			c.getSource().sendFeedback(Component.translatable("message.elsewhere.kingdom.lod", RenderLod.enabled() ? "on" : "off",
				KingdomClientConfig.detail.name().toLowerCase(java.util.Locale.ROOT), SoldierLod.BUDGET.cap(), SoldierLod.lastFrame[0], SoldierLod.lastFrame[1],
				SoldierLod.lastFrame[2], SoldierLod.lastFarFallback, SoldierLod.lastCulled, LodSkins.size(), BattleFx.trackedSoldiers(),
				BattleFx.battle() ? "yes" : "no", BattleFx.particlesThisSecond(), BattleFx.soundsPlaying(), BattleFx.DIN_ACC.played));
			return 1;
		})));
	}
}
