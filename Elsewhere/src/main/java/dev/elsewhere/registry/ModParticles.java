package dev.elsewhere.registry;

import dev.elsewhere.Elsewhere;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	/** Coloured spark used by rifts, the trip "pull" and Return Rifts. */
	public static final ParticleType<ColorParticleOption> RIFT_SPARK = Registry.register(
		BuiltInRegistries.PARTICLE_TYPE, Elsewhere.id("rift_spark"),
		FabricParticleTypes.complex(false, ColorParticleOption::codec, type -> ColorParticleOption.streamCodec(type))
	);
	/** Amber dust: Mother Creak's telegraph lines. */
	public static final SimpleParticleType RESIN_DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Elsewhere.id("resin_dust"), FabricParticleTypes.simple(true));
	/** Pale moonlight motes: Selenne's beam and the Moonring. */
	public static final SimpleParticleType MOON_MOTE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Elsewhere.id("moon_mote"), FabricParticleTypes.simple(true));

	private ModParticles() {
	}

	public static ColorParticleOption spark(final int rgb) {
		return ColorParticleOption.create(RIFT_SPARK, 0xFF000000 | rgb);
	}

	public static void init() {
	}
}
