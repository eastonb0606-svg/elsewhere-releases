package dev.elsewhere.compat.jei.dev;

import net.fabricmc.api.ModInitializer;

/** Dev-run entry of the jei_compat module only (the merged jar hooks the self-test through modules.json post_init instead). */
public final class JeiDevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		if (Boolean.getBoolean("jei.selftest")) {
			JeiSelfTest.register();
		}
	}
}
