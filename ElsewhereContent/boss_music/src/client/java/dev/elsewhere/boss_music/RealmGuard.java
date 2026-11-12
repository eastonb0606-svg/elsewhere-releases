package dev.elsewhere.boss_music;

import dev.elsewhere.boss_music.mixin.SoundVolumeAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * Keeps kingdom realm music (events ending in {@code .realm}, sent by the court every 240 s with a "stop all music" first)
 * from cutting or overlapping a boss theme: while a theme plays, a new realm track is not started and the stop-all is
 * ignored; a realm track already playing when a theme starts fades out over {@link #FADE} ticks instead of being cut.
 * Client-only, event-driven (no per-tick packets). Off with the 3.0 kill switch {@code bossMusic3=false}.
 */
public final class RealmGuard {
	static final int FADE = 30;

	private RealmGuard() {}

	private static final List<SoundInstance> realms = new ArrayList<>();
	private static final List<float[]> fades = new ArrayList<>(); // {startVolume, ticksLeft}, parallel to realms while fading
	private static boolean fading;

	static boolean isRealm(SoundInstance s) {
		Identifier id = s.getIdentifier();
		return s.getSource() == SoundSource.MUSIC && id != null && id.getPath().endsWith(".realm");
	}

	public static boolean blockPlay(SoundInstance s) {
		if (!BossMusicConfig.overhaul() || !isRealm(s)) return false;
		if (BossMusicService.suppressVanilla()) return true;
		realms.removeIf(r -> !Minecraft.getInstance().getSoundManager().isActive(r));
		realms.add(s);
		return false;
	}

	public static boolean blockStopAll(Identifier id, SoundSource source) {
		return BossMusicConfig.overhaul() && id == null && (source == null || source == SoundSource.MUSIC) && BossMusicService.suppressVanilla();
	}

	/** A theme just started: fade every realm track still playing. */
	static void themeStarted() {
		if (!BossMusicConfig.overhaul()) return;
		fades.clear();
		for (SoundInstance r : realms) {
			float v = r instanceof AbstractSoundInstance a ? ((SoundVolumeAccessor) a).elsewhere$getVolume() : 1F;
			fades.add(new float[] {v, FADE});
		}
		fading = !realms.isEmpty();
	}

	/** Called from the service tick only while a fade is running. */
	static void tick(Minecraft mc) {
		if (!fading) return;
		for (int i = realms.size() - 1; i >= 0; i--) {
			SoundInstance r = realms.get(i);
			float[] f = fades.get(i);
			f[1]--;
			if (f[1] <= 0 || !(r instanceof AbstractSoundInstance a)) {
				mc.getSoundManager().stop(r);
				realms.remove(i);
				fades.remove(i);
				continue;
			}
			((SoundVolumeAccessor) a).elsewhere$setVolume(Math.max(0.001F, f[0] * f[1] / FADE));
		}
		fading = !realms.isEmpty();
	}

	static void reset() {
		realms.clear();
		fades.clear();
		fading = false;
	}
}
