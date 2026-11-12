package dev.elsewhere.overworld_bosses;

import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events. {@code assets/elsewhere/sounds.json} maps them onto pitched vanilla sounds for now; the boss music
 * events ({@code music.boss.<id>}) point at vanilla tracks as placeholders until the real tracks exist.
 */
public final class OwbSounds {
	public static SoundEvent GULPMOTHER_CROAK, GULPMOTHER_GAPE, GULPMOTHER_TONGUE, GULPMOTHER_GULP, GULPMOTHER_SPLAT;
	public static SoundEvent STRAWGRIM_CAW, STRAWGRIM_CREAK, STRAWGRIM_SWEEP;
	public static SoundEvent NEFERKHET_CHANT, NEFERKHET_LANCE_CHARGE, NEFERKHET_LANCE_FIRE;
	public static SoundEvent RIMEBACK_GROAN;
	public static SoundEvent VORLAGH_GROAN;

	private OwbSounds() {}

	public static void init() {
		GULPMOTHER_CROAK = BossRegistry.sound("boss.gulpmother.croak");
		GULPMOTHER_GAPE = BossRegistry.sound("boss.gulpmother.gape");
		GULPMOTHER_TONGUE = BossRegistry.sound("boss.gulpmother.tongue");
		GULPMOTHER_GULP = BossRegistry.sound("boss.gulpmother.gulp");
		GULPMOTHER_SPLAT = BossRegistry.sound("boss.gulpmother.splat");
		STRAWGRIM_CAW = BossRegistry.sound("boss.strawgrim.caw");
		STRAWGRIM_CREAK = BossRegistry.sound("boss.strawgrim.creak");
		STRAWGRIM_SWEEP = BossRegistry.sound("boss.strawgrim.sweep");
		NEFERKHET_CHANT = BossRegistry.sound("boss.neferkhet.chant");
		NEFERKHET_LANCE_CHARGE = BossRegistry.sound("boss.neferkhet.lance_charge");
		NEFERKHET_LANCE_FIRE = BossRegistry.sound("boss.neferkhet.lance_fire");
		RIMEBACK_GROAN = BossRegistry.sound("boss.rimeback.groan");
		VORLAGH_GROAN = BossRegistry.sound("boss.vorlagh.groan");
		BossSfx.init();
		Sfx3.init();
		for (String b : new String[] {"gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh"}) BossRegistry.sound("music.boss." + b);
	}
}
