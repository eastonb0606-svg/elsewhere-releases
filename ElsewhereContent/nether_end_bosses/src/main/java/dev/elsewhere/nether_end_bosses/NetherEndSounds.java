package dev.elsewhere.nether_end_bosses;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * Music hooks. Each boss has its own track id; {@code sounds.json} points them at vanilla Nether/End music as
 * placeholders until the real tracks (Gilded Tyranny, Ninefold Pyre, Lament of the Basin, Inversion, Aphelion) exist.
 */
public final class NetherEndSounds {
	public static Holder<SoundEvent> MUSIC_GRUNTHAR;
	public static Holder<SoundEvent> MUSIC_SOLAXIS;
	public static Holder<SoundEvent> MUSIC_MOURNMAW;
	public static Holder<SoundEvent> MUSIC_OSSYRA;
	public static Holder<SoundEvent> MUSIC_APHELION;

	private NetherEndSounds() {}

	static void init() {
		BossSfx.init();
		Sfx3.init();
		MUSIC_GRUNTHAR = Reg.sound("music.boss.grunthar");
		MUSIC_SOLAXIS = Reg.sound("music.boss.solaxis");
		MUSIC_MOURNMAW = Reg.sound("music.boss.mournmaw");
		MUSIC_OSSYRA = Reg.sound("music.boss.ossyra");
		MUSIC_APHELION = Reg.sound("music.boss.aphelion");
	}
}
