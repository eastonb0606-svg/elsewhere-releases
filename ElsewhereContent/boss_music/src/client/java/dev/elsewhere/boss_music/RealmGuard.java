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
 * Client-only, event-driven (no per-tick packets; the tick below only runs while a fade does). Off with the 3.0 kill switch
 * {@code bossMusic3=false}. Proven in game by {@link InGameTest} (the court's own packets through the client's listener).
 */
public final class RealmGuard {
	static final int FADE = 30;

	private RealmGuard() {}

	/** One realm track the guard knows of; {@code left} counts down while it fades (-1: not fading). */
	private static final class Realm {
		final SoundInstance s;
		float start = 1F;
		int left = -1;

		Realm(SoundInstance s) {
			this.s = s;
		}
	}

	private static final List<Realm> realms = new ArrayList<>();
	private static boolean fading;
	/** What the guard did (for the in-game self-test): realm starts held off, stop-alls ignored, fades started and finished. */
	static int heldPlays, keptStops, fadesStarted, fadesDone;

	static boolean isRealm(SoundInstance s) {
		Identifier id = s.getIdentifier();
		return s.getSource() == SoundSource.MUSIC && id != null && id.getPath().endsWith(".realm");
	}

	public static boolean blockPlay(SoundInstance s) {
		if (!BossMusicConfig.overhaul() || !isRealm(s)) return false;
		if (BossMusicService.suppressVanilla()) {
			heldPlays++;
			return true;
		}
		var sm = Minecraft.getInstance().getSoundManager();
		realms.removeIf(r -> r.left < 0 && !sm.isActive(r.s)); // forget finished tracks (a fading one is dropped by tick)
		realms.add(new Realm(s));
		return false;
	}

	public static boolean blockStopAll(Identifier id, SoundSource source) {
		boolean keep = BossMusicConfig.overhaul() && id == null && (source == null || source == SoundSource.MUSIC) && BossMusicService.suppressVanilla();
		if (keep) keptStops++;
		return keep;
	}

	/** Realm tracks the guard knows of (playing, or fading out). For the in-game self-test. */
	static List<SoundInstance> tracked() {
		List<SoundInstance> out = new ArrayList<>(realms.size());
		for (Realm r : realms) out.add(r.s);
		return out;
	}

	/** A theme just started: fade every realm track still playing. */
	static void themeStarted() {
		if (!BossMusicConfig.overhaul()) return;
		boolean any = false;
		for (Realm r : realms) {
			if (r.left >= 0) continue; // already fading
			r.start = r.s instanceof AbstractSoundInstance a ? ((SoundVolumeAccessor) a).elsewhere$getVolume() : 1F;
			r.left = FADE;
			any = true;
		}
		if (any) {
			fading = true;
			fadesStarted++;
		}
	}

	/** Called from the service tick; does nothing unless a fade is running. */
	static void tick(Minecraft mc) {
		if (!fading) return;
		boolean still = false;
		for (int i = realms.size() - 1; i >= 0; i--) {
			Realm r = realms.get(i);
			if (r.left < 0) continue;
			r.left--;
			if (r.left <= 0 || !(r.s instanceof AbstractSoundInstance a)) {
				mc.getSoundManager().stop(r.s);
				realms.remove(i);
				fadesDone++;
				continue;
			}
			((SoundVolumeAccessor) a).elsewhere$setVolume(Math.max(0.001F, r.start * r.left / FADE));
			still = true;
		}
		fading = still;
	}

	static void reset() {
		realms.clear();
		fading = false;
	}
}
