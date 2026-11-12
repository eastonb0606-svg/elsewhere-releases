package dev.elsewhere.hush;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

/**
 * {@code new-hush-silence}: the Great Silence. Now and then (a few times per real hour while someone is in the Hush) the
 * dimension goes quiet for 2 minutes: Resonance gains are doubled and every Echo Crystal vein near a player glows, so it is
 * a risky window to mine in, never a free one. A countdown bar shows the time left. Cooldown 15 minutes after one ends.
 */
public final class HushSilence {
	private HushSilence() {}

	public static final int DURATION_TICKS = 2400;      // 2 minutes
	/** Tests shorten it. */
	public static int duration = DURATION_TICKS;
	public static final int COOLDOWN_TICKS = 18000;     // 15 minutes after the last one
	public static final int CHECK_INTERVAL = 1200;      // roll once a minute
	public static final double CHANCE_PER_CHECK = 0.08; // about one every 12 minutes of occupied Hush time once the cooldown is over
	public static final float RESONANCE_FACTOR = 2F;
	private static final int SAMPLES = 700, RADIUS = 28, MAX_GLOW = 300;

	private static final class State {
		long endsAt = -1, nextAllowed;
		ServerBossEvent bar;
		final Set<BlockPos> glow = new LinkedHashSet<>();
		long lastScan;
	}

	private static final Map<ServerLevel, State> STATES = new HashMap<>();
	public static int started;

	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (!Hush.isHush(level)) return;
			tick(level);
		});
	}

	private static State state(ServerLevel level) {
		return STATES.computeIfAbsent(level, l -> new State());
	}

	public static boolean active(ServerLevel level) {
		State s = STATES.get(level);
		return s != null && s.endsAt > level.getGameTime();
	}

	/** Resonance gain multiplier at this level right now. */
	public static float factor(ServerLevel level) {
		return Hush.isHush(level) && active(level) ? RESONANCE_FACTOR : 1F;
	}

	public static int glowingVeins(ServerLevel level) {
		State s = STATES.get(level);
		return s == null ? 0 : s.glow.size();
	}

	/** Starts the Great Silence now (the random roll, the dev command and the tests all come through here). */
	public static boolean start(ServerLevel level) {
		if (!Hush.isHush(level) || active(level)) return false;
		State s = state(level);
		long now = level.getGameTime();
		s.endsAt = now + duration;
		s.nextAllowed = s.endsAt + COOLDOWN_TICKS;
		s.lastScan = 0;
		if (s.bar == null) {
			s.bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal("The Great Silence"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
			s.bar.setDarkenScreen(false);
		}
		s.bar.setProgress(1F);
		started++;
		for (ServerPlayer p : level.players()) {
			s.bar.addPlayer(p);
			p.sendSystemMessage(Component.translatable("message.elsewhere.hush_silence.start").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
			level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 1.5F, 0.5F);
		}
		return true;
	}

	static void end(ServerLevel level, State s) {
		s.endsAt = -1;
		s.glow.clear();
		if (s.bar != null) s.bar.removeAllPlayers();
		for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.translatable("message.elsewhere.hush_silence.end").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
	}

	/** Remembers the Echo Crystal ore near {@code centre} now (the per-tick scan does this around each player). */
	public static int scanAround(ServerLevel level, BlockPos centre) {
		State s = state(level);
		for (int dx = -RADIUS / 2; dx <= RADIUS / 2; dx++) for (int dy = -RADIUS / 2; dy <= RADIUS / 2; dy++) for (int dz = -RADIUS / 2; dz <= RADIUS / 2; dz++) {
			BlockPos q = centre.offset(dx, dy, dz);
			if (s.glow.size() < MAX_GLOW && level.isLoaded(q) && level.getBlockState(q).is(HushBlocks.ECHO_CRYSTAL_ORE)) s.glow.add(q);
		}
		return s.glow.size();
	}

	private static void tick(ServerLevel level) {
		State s = state(level);
		long now = level.getGameTime();
		if (s.endsAt > 0) {
			if (now >= s.endsAt) { end(level, s); return; }
			if (s.bar != null) {
				s.bar.setProgress(Math.max(0F, (s.endsAt - now) / (float) duration));
				for (ServerPlayer p : level.players()) if (!s.bar.getPlayers().contains(p)) s.bar.addPlayer(p);
			}
			if (now % 10 == 0) glow(level, s, now);
			return;
		}
		if (now % CHECK_INTERVAL == 0 && now >= s.nextAllowed && occupied(level) && level.getRandom().nextDouble() < CHANCE_PER_CHECK) start(level);
	}

	private static boolean occupied(ServerLevel level) {
		for (ServerPlayer p : level.players()) if (!p.isSpectator()) return true;
		return false;
	}

	/** Finds Echo Crystal Ore near players by random sampling, remembers it, and makes every remembered vein sparkle. */
	private static void glow(ServerLevel level, State s, long now) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : level.players()) if (!p.isSpectator()) players.add(p);
		if (now - s.lastScan >= 10) {
			s.lastScan = now;
			for (ServerPlayer p : players) {
				BlockPos c = p.blockPosition();
				for (int i = 0; i < SAMPLES && s.glow.size() < MAX_GLOW; i++) {
					BlockPos q = c.offset(level.getRandom().nextIntBetweenInclusive(-RADIUS, RADIUS), level.getRandom().nextIntBetweenInclusive(-RADIUS / 2, RADIUS / 2),
						level.getRandom().nextIntBetweenInclusive(-RADIUS, RADIUS));
					if (level.isLoaded(q) && level.getBlockState(q).is(HushBlocks.ECHO_CRYSTAL_ORE)) s.glow.add(q);
				}
			}
		}
		for (java.util.Iterator<BlockPos> it = s.glow.iterator(); it.hasNext(); ) {
			BlockPos q = it.next();
			if (!level.getBlockState(q).is(HushBlocks.ECHO_CRYSTAL_ORE)) { it.remove(); continue; }
			level.sendParticles(ParticleTypes.END_ROD, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.01);
		}
	}

	public static void clearAll() {
		for (State s : STATES.values()) if (s.bar != null) s.bar.removeAllPlayers();
		STATES.clear();
		started = 0;
	}

	public static void forgetLevel(ServerLevel level) {
		State s = STATES.remove(level);
		if (s != null && s.bar != null) s.bar.removeAllPlayers();
	}
}
