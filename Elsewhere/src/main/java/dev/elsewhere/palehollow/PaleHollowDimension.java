package dev.elsewhere.palehollow;

import dev.elsewhere.Elsewhere;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class PaleHollowDimension {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Elsewhere.id("pale_hollow"));
	/** Rift / beam / particle colour: amber #E07B2A (BALANCE.md R). */
	public static final int COLOR = 0xE07B2A;
	public static final int MAX_LANDING_Y = 150;

	public static final ResourceKey<Biome> HOLLOW_THICKET = biome("hollow_thicket");
	public static final ResourceKey<Biome> RESIN_BOG = biome("resin_bog");
	public static final ResourceKey<Biome> WEEPING_DEEPS = biome("weeping_deeps");
	public static final ResourceKey<Biome> HEARTWOOD_GROVE = biome("heartwood_grove");
	public static final ResourceKey<Biome> SILENT_CLEARING = biome("silent_clearing");

	private PaleHollowDimension() {
	}

	private static ResourceKey<Biome> biome(final String name) {
		return ResourceKey.create(Registries.BIOME, Elsewhere.id(name));
	}
}
