package dev.elsewhere.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** Rift sparks (tinted), resin dust (falls, amber) and moon motes (rise, pale): all full-bright. */
public final class ElsewhereParticles {
	private ElsewhereParticles() {
	}

	public static class Glow extends SimpleAnimatedParticle {
		Glow(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy, final double vz,
			final SpriteSet sprites, final int rgb, final float gravity, final int life, final float size) {
			super(level, x, y, z, sprites, gravity);
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.lifetime = life;
			this.quadSize *= size;
			this.setColor(rgb);
			this.setFadeColor(0xFFFFFF);
			this.setSpriteFromAge(sprites);
		}

		@Override
		public int getLightCoords(final float a) {
			return 15728880;
		}
	}

	public static class SparkProvider implements ParticleProvider<ColorParticleOption> {
		private final SpriteSet sprites;

		public SparkProvider(final SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(final ColorParticleOption o, final ClientLevel level, final double x, final double y, final double z,
			final double vx, final double vy, final double vz, final RandomSource random) {
			int rgb = ((int)(o.getRed() * 255) << 16) | ((int)(o.getGreen() * 255) << 8) | (int)(o.getBlue() * 255);
			return new Glow(level, x, y, z, vx, vy, vz, this.sprites, rgb, 0.0F, 18 + random.nextInt(14), 0.75F);
		}
	}

	public static class ResinProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public ResinProvider(final SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(final SimpleParticleType o, final ClientLevel level, final double x, final double y, final double z,
			final double vx, final double vy, final double vz, final RandomSource random) {
			return new Glow(level, x, y, z, vx, vy, vz, this.sprites, 0xFFB347, 0.02F, 30 + random.nextInt(20), 0.6F);
		}
	}

	public static class MoonProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public MoonProvider(final SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(final SimpleParticleType o, final ClientLevel level, final double x, final double y, final double z,
			final double vx, final double vy, final double vz, final RandomSource random) {
			return new Glow(level, x, y, z, vx, vy + 0.01, vz, this.sprites, 0xDDE7FF, -0.005F, 25 + random.nextInt(20), 0.7F);
		}
	}
}
