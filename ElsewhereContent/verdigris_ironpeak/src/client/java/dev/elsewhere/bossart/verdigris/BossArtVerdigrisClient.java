package dev.elsewhere.bossart.verdigris;

import dev.elsewhere.boss.galvanox.GalvanoxEntities;
import dev.elsewhere.boss.verdigrim.VerdigrimEntities;
import dev.elsewhere.bossart.galvanox.GalvanoxArtModel;
import dev.elsewhere.bossart.galvanox.GalvanoxArtRenderer;
import dev.elsewhere.bossart.verdigrim.VerdigrimArtModel;
import dev.elsewhere.bossart.verdigrim.VerdigrimArtRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Boss art for the Verdigris Sea bosses (Archdeacon Verdigrim, Galvanox): registers the custom model layers and
 * replaces the module's placeholder renderers. Call after {@code VerdigrisIronpeakClient.initClient()}.
 */
public final class BossArtVerdigrisClient {
	private BossArtVerdigrisClient() {}

	public static void initClient() {
		ModelLayerRegistry.registerModelLayer(VerdigrimArtModel.LAYER, VerdigrimArtModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(GalvanoxArtModel.LAYER, GalvanoxArtModel::createBodyLayer);
		EntityRendererRegistry.register(VerdigrimEntities.VERDIGRIM, VerdigrimArtRenderer::new);
		EntityRendererRegistry.register(GalvanoxEntities.GALVANOX, GalvanoxArtRenderer::new);
	}
}
