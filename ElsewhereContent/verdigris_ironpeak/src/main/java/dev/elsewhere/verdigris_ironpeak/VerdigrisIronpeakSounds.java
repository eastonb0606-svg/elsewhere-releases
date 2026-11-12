package dev.elsewhere.verdigris_ironpeak;

import static dev.elsewhere.verdigris_ironpeak.Reg.sound;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events. Their sounds.json entries point at vanilla sound files for now (pitched organ notes are note-block
 * harp/bell/flute samples; the music tracks reuse vanilla records), so everything plays without new audio assets.
 */
public final class VerdigrisIronpeakSounds {
	public static final Holder<SoundEvent> MUSIC_DISC_LOW_TIDE_ENGINE = sound("music_disc.low_tide_engine");
	public static final Holder<SoundEvent> MUSIC_DISC_THIN_AIR = sound("music_disc.thin_air");
	public static final Holder<SoundEvent> MUSIC_VERDIGRIM = sound("music.boss.verdigrim");
	public static final Holder<SoundEvent> MUSIC_GALVANOX = sound("music.boss.galvanox");
	public static final Holder<SoundEvent> MUSIC_THURGRIM = sound("music.boss.thurgrim");
	public static final Holder<SoundEvent> MUSIC_HROLF = sound("music.boss.hrolf");
	public static final Holder<SoundEvent> HYMNAL_HORN = sound("item.hymnal_horn.blow");
	public static final Holder<SoundEvent> AVALANCHE_HORN = sound("item.avalanche_horn.blow");
	public static final Holder<SoundEvent> ORGAN_NOTE = sound("entity.verdigrim.organ_note");
	public static final Holder<SoundEvent> VERDIGRIM_ROAR = sound("entity.verdigrim.roar");
	public static final Holder<SoundEvent> GALVANOX_HUM = sound("entity.galvanox.hum");
	public static final Holder<SoundEvent> GALVANOX_ROAR = sound("entity.galvanox.roar");
	public static final Holder<SoundEvent> THURGRIM_ROAR = sound("entity.thurgrim.roar");
	public static final Holder<SoundEvent> WAR_DRUM = sound("block.summit_war_drum.beat");
	public static final Holder<SoundEvent> HROLF_HORN = sound("entity.hrolf.horn");
	public static final Holder<SoundEvent> STORM_AMBIENT = sound("ambient.verdigris_sea.storm");
	public static final Holder<SoundEvent> WIND_AMBIENT = sound("ambient.ironpeak.wind");

	private VerdigrisIronpeakSounds() {}

	public static void init() {}
}
