package dev.elsewhere.stub.petalwind_glasswaste;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** STUB: display names for arrival messages (the core owns the real list). */
public final class DimensionNames {
	private DimensionNames() {}

	public static String name(ResourceKey<Level> key) {
		return switch (key.identifier().getPath()) {
			case "petalwind" -> "the Petalwind Isles";
			case "glasswaste" -> "the Glasswaste";
			default -> key.identifier().toString();
		};
	}
}
