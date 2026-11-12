package dev.elsewhere.hush;

import dev.elsewhere.greenhaze_hush.Reg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** The Hush ({@code elsewhere:the_hush}): the post-dragon sculk abyss where sound is what kills you. */
public final class Hush {
	private Hush() {}

	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Reg.id("the_hush"));
	public static final ResourceKey<Biome> THE_MURMUR = biome("the_murmur"), ECHO_SHAFTS = biome("echo_shafts"),
		OSSUARY_GALLERIES = biome("ossuary_galleries"), AMETHYST_CHOIR = biome("amethyst_choir"), DROWNED_SILENCE = biome("drowned_silence");

	private static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, Reg.id(name));
	}

	public static void init() {
		Reg.currentTab = Reg.HUSH_TAB;
		HushBlocks.init();
		HushItems.init();
		HushEntities.init();
		Resonance.init();
		HushNoise.init();
		HushSilence.init();
		HushStructures.init();
	}

	public static boolean isHush(Level level) {
		return level.dimension().equals(LEVEL);
	}
}
