package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.Reg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Keys for the Glasswaste dimension (`elsewhere:glasswaste`), defined by data in data/elsewhere/dimension. */
public final class GlasswasteDimension {
	private GlasswasteDimension() {}

	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Reg.id("glasswaste"));
}
