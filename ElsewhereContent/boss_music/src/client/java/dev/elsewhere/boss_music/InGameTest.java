package dev.elsewhere.boss_music;

import dev.elsewhere.boss_music.mixin.SoundVolumeAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * In-game self-test of the realm guard and the phase layers (3.0), on a live client in a world: the court's real packets
 * go through the client's own packet listener ({@code handleStopSoundEvent}, {@code handleSoundEntityEvent}: exactly the
 * pair {@code CourtLife.realmMusicFor} sends on entering a realm and every 240 s, and its leave-stop), into the real
 * {@code SoundManager} mixins, {@link RealmGuard}, {@link BossMusicService}, {@link Theme} and the sound engine. A stand-in
 * boss (an invisible, silent giant added to the client level 16 blocks below the player and kept there, mapped to a real
 * theme for the test only) starts the theme, crosses every health threshold and leaves. Runs once after the first world
 * join with {@code -Dboss_music.selftest=true} (the integrator's client run), or on demand with
 * {@code /elsewhere_bossmusic selftest}. With {@code bossMusic3=false} it checks that the guard is off. If the world changes
 * or another boss's theme takes over mid-test, it starts again (at most 3 times). Prints
 * {@code [boss_music-ingame-selftest] PASS|FAIL <check>} lines and one TOTAL line, then sets the system property
 * {@code boss_music.ingame.done=true} (a client gametest can wait on it before leaving the world or summoning other
 * bosses). Nothing here runs unless started.
 */
final class InGameTest {
	private InGameTest() {}

	static final String TAG = "[boss_music-ingame-selftest] ";
	/** A realm event boss_music ships itself (the 240 s Hrafnholt track), so the test runs standalone and merged. */
	static final Identifier REALM = Identifier.fromNamespaceAndPath("elsewhere", "hrafnholt_music.realm");
	private static final int STANDIN_ID = -7_000_042, MAX_RESTARTS = 3;

	private static boolean running, ranOnJoin, ticking;
	private static int step, clock, mark, pass, fail, restarts;
	private static Consumer<String> echo;
	private static Identifier theme, standinKey;
	private static BossMusicMap testMap;
	private static ClientLevel testLevel;
	private static LivingEntity standin;
	private static SoundInstance realm;
	private static boolean engine, guardOn, volOk;
	private static float lastVol;
	private static int threshold, maxStreams, kept0, held0, fades0;
	private static final List<String> failed = new ArrayList<>();

	/** First world join with the self-test property: start once. */
	static void onJoin(Minecraft mc) {
		if (ranOnJoin || !Boolean.getBoolean("boss_music.selftest")) return;
		ranOnJoin = true;
		start(mc, null);
	}

	/** World closed: a test still running reports itself unfinished. */
	static void onDisconnect() {
		if (!running) return;
		check(false, "finished before the world closed (stopped at step " + step + ")");
		finish(null);
	}

	/** Starts the test (false while one runs). {@code chat} also gets the result lines (command use). */
	static boolean start(Minecraft mc, Consumer<String> chat) {
		if (running) return false;
		if (!ticking) {
			ticking = true;
			ClientTickEvents.END_CLIENT_TICK.register(InGameTest::tick); // after BossMusicService's own tick (registered first)
		}
		running = true;
		echo = chat;
		pass = fail = 0;
		restarts = 0;
		failed.clear();
		reset(mc);
		log("start (" + (BossMusicConfig.overhaul() ? "bossMusic3 on" : "bossMusic3=false: checking that the guard is off") + ")");
		return true;
	}

	private static void reset(Minecraft mc) {
		step = 0;
		clock = 0;
		mark = 0;
		maxStreams = 0;
		realm = null;
		theme = null;
		testLevel = mc.level;
	}

	static void tick(Minecraft mc) {
		if (!running || mc.level == null || mc.player == null || mc.getConnection() == null || mc.isPaused()) return;
		clock++;
		try {
			if (mc.level != testLevel) {
				restart(mc, "the world changed");
				return;
			}
			Identifier now = BossMusicService.themeId();
			if (step >= 3 && step <= 5 && now != null && !now.equals(theme)) {
				restart(mc, "another boss's theme took over (" + now + ")");
				return;
			}
			if (standin != null && standin.distanceTo(mc.player) > 20) { // the player was moved: keep the boss in range
				standin.setPos(mc.player.getX(), mc.player.getY() - 16, mc.player.getZ());
			}
			step(mc);
		} catch (RuntimeException e) {
			BossMusicService.LOGGER.error(TAG + "crashed at step " + step, e);
			check(false, "no exception (step " + step + ": " + e + ")");
			finish(mc);
		}
	}

	private static void restart(Minecraft mc, String why) {
		cleanup(mc);
		if (++restarts > MAX_RESTARTS) {
			check(false, "completes without interruption (" + why + ", " + MAX_RESTARTS + " restarts)");
			finish(mc);
			return;
		}
		log("INFO restarting: " + why);
		pass = fail = 0;
		failed.clear();
		reset(mc);
	}

	private static void step(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		var sm = mc.getSoundManager();
		BossMusicMap m = BossMusicService.map();
		switch (step) {
			case 0 -> { // let the world settle; a real boss theme playing now ends first
				if (clock < 40) return;
				if (BossMusicService.suppressVanilla()) {
					if (clock > 2400) {
						check(false, "setup: no other boss theme playing");
						finish(mc);
					}
					return;
				}
				if (!check(BossMusicConfig.enabled(), "setup: boss music is on (bossMusic)")) {
					finish(mc);
					return;
				}
				guardOn = BossMusicConfig.overhaul();
				if (!check(sm.getSoundEvent(REALM) != null, "setup: realm event " + REALM + " resolves")) {
					finish(mc);
					return;
				}
				theme = pickTheme(m);
				standinKey = BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.GIANT);
				if (!check(theme != null && !m.themes.containsKey(standinKey), "setup: a boss theme with a timing plan for the stand-in")) {
					finish(mc);
					return;
				}
				testMap = m;
				m.themes.put(standinKey, theme);
				log("theme " + theme + " (" + m.plans.get(theme).layers().size() + " phase layers)");
				// A: no theme yet. The court's enter/resend pair: stop all music, then the realm track on the player.
				kept0 = RealmGuard.keptStops;
				held0 = RealmGuard.heldPlays;
				conn.handleStopSoundEvent(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
				check(RealmGuard.keptStops == kept0, "court stop-all passes while no theme plays");
				conn.handleSoundEntityEvent(realmPacket(mc));
				List<SoundInstance> tr = RealmGuard.tracked();
				if (guardOn) {
					check(RealmGuard.heldPlays == held0 && !tr.isEmpty(), "realm track starts while no theme plays (the guard tracks it)");
					realm = tr.isEmpty() ? null : tr.get(tr.size() - 1);
				} else {
					check(RealmGuard.heldPlays == held0 && tr.isEmpty(), "bossMusic3=false: the guard leaves realm tracks alone");
				}
				next(1);
			}
			case 1 -> { // the realm track is live; a fighting boss comes in range
				if (clock - mark < 3) return;
				engine = realm != null && sm.isActive(realm);
				if (guardOn) {
					if (engine) check(true, "realm track plays in the sound engine");
					else log("INFO sound engine silent (music volume 0 or no audio device): stream-level checks skipped");
				}
				var g = EntityTypes.GIANT.create(mc.level, EntitySpawnReason.COMMAND);
				if (!check(g != null, "setup: stand-in boss created")) {
					finish(mc);
					return;
				}
				g.setId(STANDIN_ID);
				g.setPos(mc.player.getX(), mc.player.getY() - 16, mc.player.getZ());
				g.setInvisible(true);
				g.setSilent(true);
				g.setHealth(g.getMaxHealth() * 0.95F); // hurt = fighting
				mc.level.addEntity(g);
				standin = g;
				lastVol = realmVolume();
				volOk = true;
				fades0 = RealmGuard.fadesStarted;
				next(2);
			}
			case 2 -> { // the theme starts
				if (theme.equals(BossMusicService.themeId())) {
					check(true, "a fighting boss in range starts its theme (" + (clock - mark) + " ticks)");
					next(guardOn ? 3 : 5);
				} else if (clock - mark > 20) {
					check(false, "a fighting boss in range starts its theme within 20 ticks (now " + BossMusicService.themeId() + ")");
					next(7);
				}
			}
			case 3 -> { // B: the realm track fades out under the theme, never cut
				boolean gone = realm == null || !RealmGuard.tracked().contains(realm);
				if (!gone) {
					float v = realmVolume();
					if (v > lastVol + 1e-4F) volOk = false;
					lastVol = v;
				}
				if (gone || clock - mark > RealmGuard.FADE + 15) {
					check(RealmGuard.fadesStarted == fades0 + 1 && gone, "realm track fades out when the theme starts (gone after " + (clock - mark) + " ticks)");
					check(volOk && lastVol <= 0.15F, "realm fade only goes down and ends quiet (last level " + lastVol + ")");
					check(clock - mark <= RealmGuard.FADE + 3, "realm fade takes " + RealmGuard.FADE + " ticks, not longer");
					next(4);
				}
			}
			case 4 -> { // the engine has dropped the faded realm track
				if (clock - mark < 3) return;
				if (engine) check(!sm.isActive(realm), "realm track stopped in the engine after its fade");
				next(5);
			}
			case 5 -> { // C: the court's 240 s resend while the theme plays
				kept0 = RealmGuard.keptStops;
				held0 = RealmGuard.heldPlays;
				conn.handleStopSoundEvent(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
				Theme t = BossMusicService.theme();
				if (guardOn) {
					check(RealmGuard.keptStops == kept0 + 1, "court stop-all is ignored while a theme plays");
					if (engine) check(t != null && t.activeStreams(mc) >= 1, "theme still plays after the stop-all");
				} else {
					check(RealmGuard.keptStops == kept0, "bossMusic3=false: the stop-all is not held");
				}
				conn.handleSoundEntityEvent(realmPacket(mc));
				if (guardOn) check(RealmGuard.heldPlays == held0 + 1 && RealmGuard.tracked().isEmpty(), "realm resend is held off while a theme plays");
				else check(RealmGuard.heldPlays == held0, "bossMusic3=false: the realm resend is not held");
				threshold = 0;
				next(guardOn ? 6 : 7);
			}
			case 6 -> { // D: P12 phase layers in game, threshold by threshold
				Theme t = BossMusicService.theme();
				if (t == null || t.fadingOut()) {
					check(false, "theme still running for the threshold checks");
					next(7);
					return;
				}
				if (engine) maxStreams = Math.max(maxStreams, t.activeStreams(mc));
				if (threshold >= m.phases.length) {
					if (engine) check(maxStreams <= 2, "never more than 2 theme streams at once (max " + maxStreams + ")");
					next(7);
					return;
				}
				if (clock - mark == 1) standin.setHealth(standin.getMaxHealth() * Math.max(0.02F, m.phases[threshold] - 0.05F));
				int li = t.plan().layerIndex(threshold);
				if (li < 0) {
					if (clock - mark >= 10) { // a stinger threshold: nothing to swap
						threshold++;
						mark = clock;
					}
					return;
				}
				Conductor.Layer want = t.plan().layers().get(li);
				if (want.equals(t.layerNow())) {
					check(true, "threshold " + threshold + " crossfades into layer '" + want.name() + "' (" + (clock - mark) + " ticks, on the beat)");
					if (engine) check(t.activeStreams(mc) >= 1, "layer '" + want.name() + "' plays in the sound engine");
					threshold++;
					mark = clock;
				} else if (clock - mark > 200) {
					check(false, "threshold " + threshold + " brings in layer '" + want.name() + "' within 200 ticks");
					threshold++;
					mark = clock;
				}
			}
			case 7 -> { // E: the boss leaves: the theme fades out
				if (clock - mark == 1) {
					cleanupStandin(mc);
					return;
				}
				if (!BossMusicService.suppressVanilla()) {
					check(true, "theme fades out when the boss leaves (" + (clock - mark) + " ticks)");
					next(8);
				} else if (clock - mark > 400) {
					check(false, "theme ends within 400 ticks of the boss leaving");
					finish(mc);
				}
			}
			case 8 -> { // F: realm music plays again after the theme, and the court's leave-stop still works
				if (clock - mark == 1) {
					held0 = RealmGuard.heldPlays;
					conn.handleSoundEntityEvent(realmPacket(mc));
					List<SoundInstance> tr = RealmGuard.tracked();
					check(RealmGuard.heldPlays == held0, "realm resend plays again after the theme");
					realm = guardOn && !tr.isEmpty() ? tr.get(tr.size() - 1) : null;
				} else if (clock - mark == 4) {
					if (engine && realm != null) check(sm.isActive(realm), "realm track playing again in the engine");
					conn.handleStopSoundEvent(new ClientboundStopSoundPacket(REALM, SoundSource.MUSIC)); // the court's leave
				} else if (clock - mark == 40) { // SoundEngine keeps a stopped instance 'active' until 20 ticks after it started (soundDeleteTime), so wait past that
					if (engine && realm != null) check(!sm.isActive(realm), "court leave-stop still stops the realm track");
					finish(mc);
				}
			}
			default -> finish(mc);
		}
	}

	/** The used theme with the most phase layers (then the first id), so the threshold checks have something to swap. */
	private static Identifier pickTheme(BossMusicMap m) {
		Identifier best = null;
		int most = -1;
		for (Identifier id : new java.util.TreeSet<>(m.plans.keySet())) {
			int n = m.plans.get(id).layers().size();
			if (n > most && m.themes.containsValue(id)) {
				most = n;
				best = id;
			}
		}
		return best;
	}

	private static ClientboundSoundEntityPacket realmPacket(Minecraft mc) {
		return new ClientboundSoundEntityPacket(Holder.direct(SoundEvent.createVariableRangeEvent(REALM)), SoundSource.MUSIC, mc.player, 0.7F, 1.0F, 42L);
	}

	private static float realmVolume() {
		return realm instanceof AbstractSoundInstance a ? ((SoundVolumeAccessor) a).elsewhere$getVolume() : 0F;
	}

	private static void next(int s) {
		step = s;
		mark = clock;
	}

	private static boolean check(boolean ok, String what) {
		if (ok) pass++;
		else {
			fail++;
			failed.add(what);
		}
		log((ok ? "PASS " : "FAIL ") + what);
		return ok;
	}

	private static void log(String line) {
		System.out.println(TAG + line);
		if (echo != null) echo.accept(line);
	}

	private static void cleanupStandin(Minecraft mc) {
		if (mc != null && mc.level != null && mc.level.getEntity(STANDIN_ID) != null) mc.level.removeEntity(STANDIN_ID, Entity.RemovalReason.DISCARDED);
		standin = null;
	}

	/** Removes the stand-in, its map entry and any realm track the test started. */
	private static void cleanup(Minecraft mc) {
		cleanupStandin(mc);
		if (testMap != null && standinKey != null) testMap.themes.remove(standinKey);
		testMap = null;
		if (mc != null && mc.getSoundManager() != null) mc.getSoundManager().stop(REALM, SoundSource.MUSIC);
	}

	private static void finish(Minecraft mc) {
		cleanup(mc);
		log("TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL " + failed));
		running = false;
		System.setProperty("boss_music.ingame.done", "true"); // a client gametest waits on this (no compile dependency)
		echo = null;
	}
}
