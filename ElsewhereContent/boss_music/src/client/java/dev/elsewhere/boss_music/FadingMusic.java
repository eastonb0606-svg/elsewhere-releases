package dev.elsewhere.boss_music;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A non-positional music instance in the MUSIC category (so the Music slider applies). Its level is set by the theme's
 * {@link Conductor} through {@link #gain}; the sound engine reads it on this instance's own tick. One instance per theme part
 * (intro, loop, surge, end); the loop and surge loop, the others are one-shots.
 */
final class FadingMusic extends AbstractTickableSoundInstance {
	private float gain = 0.001F;

	FadingMusic(SoundEvent event, boolean looping) {
		super(event, SoundSource.MUSIC, RandomSource.create());
		this.looping = looping;
		this.delay = 0;
		this.volume = 0.001F;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	void gain(float g) {
		gain = Mth.clamp(g, 0.001F, 1F);
		this.volume = gain;
	}

	void kill() {
		this.volume = 0F;
		stop();
	}

	@Override
	public void tick() {
		this.volume = gain;
	}
}
