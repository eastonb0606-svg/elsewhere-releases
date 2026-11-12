package dev.elsewhere.bossart.overworld_a;

import dev.elsewhere.bossart.gulpmother.GulpmotherArt;
import dev.elsewhere.bossart.neferkhet.NeferkhetArt;
import dev.elsewhere.bossart.strawgrim.StrawgrimArt;

/**
 * Boss art group overworld_a: custom models, textures and keyframe animations for Old Gulpmother, Strawgrim and
 * Neferkhet. Call after OverworldBossesClient.initClient(); it replaces those bosses' beta renderers.
 */
public final class BossArtOverworldAClient {
	private BossArtOverworldAClient() {}

	public static void initClient() {
		GulpmotherArt.register();
		StrawgrimArt.register();
		NeferkhetArt.register();
	}
}
