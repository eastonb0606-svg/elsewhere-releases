package dev.elsewhere.boss_music;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side boss music. While a boss from {@code boss_music.json} is within range and fighting (its boss bar is on
 * screen, or it has been hurt), its theme runs on a {@link Conductor}: the track's own intro once, then its loop; the first
 * phase threshold crossfades on the beat into the theme's surge section (or plays the phase stinger on the beat), the kill
 * brings in the track's own ending on the beat, and fleeing or dying fades out (flee on the beat, defeat slowly). Vanilla
 * music is paused and kingdom realm music is held off ({@link RealmGuard}). A second boss's theme waits until the first has
 * left. Everything plays in the MUSIC category, so the Music slider applies. Boss scan every 5 ticks; no packets.
 */
public final class BossMusicService {
	static final Logger LOGGER = LoggerFactory.getLogger("elsewhere_boss_music");

	private BossMusicService() {}

	private static BossMusicMap map;
	private static Theme theme;
	private static Identifier themeId;
	private static int trackedId = -1;
	private static boolean[] fired = new boolean[0];
	private static Field barsField;
	private static boolean barsLooked;

	/** True while a boss theme is playing or fading out: vanilla/other music stays paused. */
	public static boolean suppressVanilla() {
		return theme != null && !theme.isStopped();
	}

	public static BossMusicMap map() {
		Minecraft mc = Minecraft.getInstance();
		if (map == null) map = BossMusicMap.load(mc);
		return map;
	}

	/** Join (reload the map) or disconnect (cut everything at once). */
	static void reset(boolean join) {
		Minecraft mc = Minecraft.getInstance();
		if (theme != null) theme.stop(mc);
		theme = null;
		themeId = null;
		pendingId = null;
		trackedId = -1;
		RealmGuard.reset();
		map = null;
		if (join) map = BossMusicMap.load(mc);
	}

	private static Identifier pendingId;
	private static int scanClock;

	static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (mc.isPaused()) return; // the sound engine is paused too: the theme clock waits with it
		if (!BossMusicConfig.enabled()) {
			if (theme != null) { // switched off mid-fight: fade out on the beat, never cut
				theme.flee();
				theme.tick();
				if (theme.isStopped()) {
					theme = null;
					themeId = null;
				}
			}
			pendingId = null;
			trackedId = -1;
			return;
		}
		BossMusicMap m = map();
		RealmGuard.tick(mc);
		if (theme != null) {
			theme.tick();
			if (theme.isStopped()) {
				theme = null;
				themeId = null;
			}
		}
		if (mc.player.isDeadOrDying()) {
			if (theme != null) theme.defeat(); // slow defeat fade
			pendingId = null;
			trackedId = -1;
			return;
		}

		Entity tracked = trackedId >= 0 ? mc.level.getEntity(trackedId) : null;
		if (trackedId >= 0) {
			if (tracked instanceof LivingEntity le && le.isDeadOrDying()) { // the kill
				Identifier deadTheme = m.themes.get(BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()));
				LivingEntity other = findBoss(mc, m, null);
				if (other != null && deadTheme != null && deadTheme.equals(themeId)
					&& deadTheme.equals(m.themes.get(BuiltInRegistries.ENTITY_TYPE.getKey(other.getType())))) {
					trackedId = -1; // the other half of the same fight fights on (rider and goat, warlord and hoglin): no victory yet
					tracked = null;
				} else {
					if (theme != null) theme.victory();
					else sting(mc, m, "victory");
					pendingId = null;
					trackedId = -1;
					return;
				}
			} else if (tracked == null || tracked.isRemoved()) { // reset, despawn or out of tracking range
				if (theme != null) theme.flee();
				trackedId = -1;
			}
		}

		LivingEntity boss;
		if (tracked instanceof LivingEntity le && !le.isRemoved() && ++scanClock % 5 != 0 && le.distanceTo(mc.player) <= m.range + 8) {
			boss = le; // between scans (every 5 ticks) the tracked boss stays chosen
		} else {
			scanClock = 0;
			boss = findBoss(mc, m, tracked);
		}
		if (boss == null) {
			if (theme != null) theme.flee();
			pendingId = null;
			trackedId = -1;
			return;
		}
		Identifier want = m.themes.get(BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()));
		if (boss.getId() != trackedId) {
			trackedId = boss.getId();
			fired = new boolean[m.phases.length];
			float frac = boss.getHealth() / Math.max(1F, boss.getMaxHealth());
			for (int i = 0; i < fired.length; i++) fired[i] = frac <= m.phases[i]; // joined mid-fight: no stale stingers
		}
		if (want != null && (theme == null || theme.fadingOut() || !want.equals(themeId))) {
			if (theme != null && !theme.fadingOut()) theme.flee(); // the old theme leaves on its beat first: no overlap
			if (theme == null) {
				mc.getMusicManager().stopPlaying();
				themeId = want;
				theme = new Theme(mc, want, m);
				RealmGuard.themeStarted();
				pendingId = null;
				if (m.summonStinger || !BossMusicConfig.overhaul()) sting(mc, m, "summon");
				for (int i = 0; i < fired.length; i++) if (fired[i]) theme.phase(i); // joined below a threshold: start in the right section
			} else {
				pendingId = want; // starts once the old theme has finished leaving
			}
		}
		if (theme == null || theme.fadingOut()) return;
		float frac = boss.getHealth() / Math.max(1F, boss.getMaxHealth());
		for (int i = 0; i < fired.length; i++) {
			if (!fired[i] && frac <= m.phases[i]) {
				fired[i] = true;
				theme.phase(i);
			} else if (fired[i] && frac > m.phases[i] + 0.1F) {
				fired[i] = false; // healed back up: the boss re-arms (each section still plays once per theme)
			}
		}
	}

	/** The pending theme (if any) waiting for the old one to finish. Exposed for the self-test. */
	static Identifier pending() {
		return pendingId;
	}

	private static void sting(Minecraft mc, BossMusicMap m, String which) {
		Identifier id = m.stingers.get(which);
		if (id == null) return;
		mc.getSoundManager().play(SimpleSoundInstance.forMusic(SoundEvent.createVariableRangeEvent(id)));
	}

	private static LivingEntity findBoss(Minecraft mc, BossMusicMap m, Entity tracked) {
		var player = mc.player;
		double r = m.range + 8; // look a little wider; the tracked boss gets hysteresis below
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		List<String> bars = null;
		for (Entity e : mc.level.getEntities((Entity) null, player.getBoundingBox().inflate(r), x -> x instanceof LivingEntity)) {
			if (!(e instanceof LivingEntity le) || le.isDeadOrDying()) continue;
			Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
			if (!m.themes.containsKey(key)) continue;
			double d = le.distanceTo(player);
			if (d > (e == tracked ? m.range + 8 : m.range)) continue;
			if (bars == null) bars = barNames(mc);
			boolean fighting = le.getHealth() < le.getMaxHealth() - 0.01F
				|| bars.contains(le.getName().getString()) || bars.contains(le.getType().getDescription().getString());
			if (!fighting && e != tracked) continue;
			if (d < bestD) {
				bestD = d;
				best = le;
			}
		}
		return best;
	}

	/** Names of the boss bars currently on screen (the bar list is a private field of the overlay). */
	@SuppressWarnings("unchecked")
	private static List<String> barNames(Minecraft mc) {
		List<String> out = new ArrayList<>();
		try {
			if (!barsLooked) {
				barsLooked = true;
				barsField = BossHealthOverlay.class.getDeclaredField("events");
				barsField.setAccessible(true);
			}
			if (barsField == null || mc.gui == null || mc.gui.hud == null) return out;
			Map<UUID, LerpingBossEvent> ev = (Map<UUID, LerpingBossEvent>) barsField.get(mc.gui.hud.getBossOverlay());
			for (LerpingBossEvent e : ev.values()) out.add(e.getName().getString());
		} catch (ReflectiveOperationException | RuntimeException e) {
			barsField = null; // fall back to the "has been hurt" test
		}
		return out;
	}
}
