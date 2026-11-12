package dev.elsewhere.lore;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kingdom lore module (lane K-D): royal patrols, the Royal Proclamation, the Survey Map, Seals, the Reeve and
 * Quartermaster, kingdom loot and the "Whispers of a Crown" advancements. Merge entry: {@link #init()}.
 * Needs the kingdom base module ({@code dev.elsewhere.kingdom}) on the classpath.
 */
public final class LoreContent {
	private LoreContent() {}

	public static final String NS = "elsewhere";
	public static final Logger LOG = LoggerFactory.getLogger("elsewhere-kingdom-lore");
	private static boolean done;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static void init() {
		if (done) return;
		done = true;
		LoreConfig.load();
		LoreItems.init();
		LoreLoot.init();
		LoreMaps.init();
		LoreCommands.init();
		Quartermasters.init();
		Patrols.init();
		LoreAdvancements.init();
		LOG.info("Elsewhere kingdom lore loaded: {} items", LoreItems.ALL.size());
	}
}
