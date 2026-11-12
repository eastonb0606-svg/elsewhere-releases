package dev.elsewhere.gear2;

import net.minecraft.resources.Identifier;

/** Elsewhere 2.0 new gear (DECISIONS-2.0 "New gear: final picks", specs in Ideas/v2gear.json). Merge entry: {@link #init()}. */
public final class Gear2Content {
	private Gear2Content() {}

	private static boolean done;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("elsewhere", path);
	}

	public static void init() {
		if (done) return;
		done = true;
		Gear2Items.init();
		Gear2Loot.init();
		Gear2Hooks.init();
	}
}
