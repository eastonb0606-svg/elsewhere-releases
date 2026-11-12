package dev.elsewhere.nether_end_bosses;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * polish-boss-sounds: per-boss roar / big attack / death, each two layered, hand-picked and re-pitched VANILLA sounds
 * (events {@code boss.<id>.<kind>} and {@code boss.<id>.<kind>_2} in sounds.json point at vanilla events).
 */
public final class BossSfx {
	private BossSfx() {}

	public static final String[] BOSSES = {"grunthar", "solaxis", "mournmaw", "ossyra", "aphelion"};
	public static final String[] KINDS = {"roar", "big", "death"};
	private static final Map<String, SoundEvent[]> EVENTS = new HashMap<>();

	public static void init() {
		for (String b : BOSSES) for (String k : KINDS) {
			SoundEvent[] pair = new SoundEvent[2];
			for (int i = 0; i < 2; i++) {
				String n = "boss." + b + "." + k + (i == 0 ? "" : "_2");
				pair[i] = Reg.sound(n).value();
			}
			EVENTS.put(b + "." + k, pair);
		}
	}

	public static boolean has(String boss, String kind) {
		return EVENTS.containsKey(boss + "." + kind);
	}

	/** Plays both layers at the entity (volume 2 so a big boss is heard across the arena). */
	public static void play(Entity at, String boss, String kind) {
		if (!(at.level() instanceof ServerLevel level)) return;
		// sfx lane 3.0: the boss's own sound set replaces the 2.x pair (kill switch: Sfx3.CONFIG_KEY)
		if (kind.equals("roar") && Sfx3.roar(at, boss)) return;
		if (kind.equals("death") && Sfx3.death(at, boss)) return;
		if (kind.equals("big") && Sfx3.enabled() && Sfx3.has(boss, "big")) return; // played with the telegraph
		SoundEvent[] pair = EVENTS.get(boss + "." + kind);
		if (pair == null) return;
		for (SoundEvent e : pair) level.playSound(null, at.getX(), at.getY(), at.getZ(), e, SoundSource.HOSTILE, kind.equals("death") ? 3.0F : 2.0F, 1.0F);
	}
}
