package dev.elsewhere.gear3;

import net.minecraft.resources.Identifier;

/**
 * Elsewhere 3.0 new items (GEAR.md catalogue). Everything is driven by one data file, {@code /elsewhere_gear3/catalogue.json},
 * which the Python generators in {@code gear3/tools} also read (models, lang, recipes, tags, JEI lines) and which the
 * self-test checks against. Merge entry: {@link #init()}. Kill switch: exclude the module "gear3" in modules.json.
 */
public final class Gear3Content {
	private Gear3Content() {}

	private static boolean done;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("elsewhere", path);
	}

	public static void init() {
		if (done) return;
		done = true;
		dev.elsewhere.arsenal.ArsenalAttributes.init(); // idempotent; our attrs refer to arsenal's attributes
		Gear3Catalogue.load();
		Gear3Items.init();
		Gear3Mechanics.init();
		Gear3LightBlocks.init(); // chunk attachment type + chunk-load hook (self-healing Glimmer Lantern lights)
		Gear3Loot.init();
		dev.elsewhere.gear3.s3.echoarrow.EchoArrows.init(); // s-echoarrow: entity type elsewhere:echo_arrow + dispenser behaviour (after Gear3Items)
	}
}
