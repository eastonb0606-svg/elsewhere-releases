package dev.elsewhere.lore;

import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.lore.dev.SelfTest;
import net.fabricmc.api.ModInitializer;

/** Dev mod initializer (elsewhere_kingdom_lore): the module content plus the self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		KingdomContent.init(); // the base jar's own DevEntry does this too; init() is idempotent
		LoreContent.init();
		if (Boolean.getBoolean("lore.selftest")) SelfTest.register();
	}
}
