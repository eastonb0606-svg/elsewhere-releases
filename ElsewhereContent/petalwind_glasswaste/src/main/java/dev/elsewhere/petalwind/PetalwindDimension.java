package dev.elsewhere.petalwind;

import dev.elsewhere.petalwind_glasswaste.Reg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Keys for the Petalwind Isles dimension (`elsewhere:petalwind`), defined by data in data/elsewhere/dimension. */
public final class PetalwindDimension {
	private PetalwindDimension() {}

	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Reg.id("petalwind"));
	/** Below this Y you are dropped into the Overworld sky. */
	public static final int VOID_Y = -16;
	public static final int OVERWORLD_DROP_Y = 330;
	public static final int SLOW_FALL_TICKS = 500;
}
