package dev.elsewhere.kingdom.client.impostor;

import dev.elsewhere.kingdom.armies.impostor.Ranks;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client side of the rank impostors (ARMIES.md 3.6): the renderer. One call from {@code KingdomClient.initClient()}. */
public final class RankClient {
	private RankClient() {}

	public static void init() {
		// the entity type is registered whatever the kill switch says (a server with it on may send ranks); nothing else runs client-side
		if (Ranks.RANK != null) EntityRendererRegistry.register(Ranks.RANK, RankRenderer::new);
	}
}
