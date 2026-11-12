package dev.elsewhere.arsenal.glimmer;

import com.mojang.serialization.Codec;
import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.ArsenalItems;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.stub.arsenal.SetBonus;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * Glimmer, the caster resource (ARSENAL.md §1). Public API for other modules:
 * {@link #trySpend(ServerPlayer, int)}, {@link #drain(ServerPlayer, float)}, {@link #refund(ServerPlayer, float)},
 * {@link #current(Player)}, {@link #max(Player)}, {@link #reserved(Player)}, {@link #cost(Player, int)}.
 */
public final class Glimmer {
	private Glimmer() {}

	/** Current Glimmer, saved with the player. Not copied on death (respawn sets half max). */
	public static final AttachmentType<Float> CURRENT = AttachmentRegistry.create(ArsenalContent.id("glimmer"), b -> b.persistent(Codec.FLOAT));

	private static final class State {
		long lastSpend = -10000;
		float sentCur = -99, sentMax = -99;
		int sentRes = -1, sentMinions = -1, keepAlive;
		long surgeUntil;
		boolean moonlitPrimed;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Last cast failure, for tests. */
	public static float lastFailNeed;

	private static State state(Player p) {
		return STATES.computeIfAbsent(p.getUUID(), u -> new State());
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(GlimmerPayload.TYPE, GlimmerPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Glimmer::tick);
		ServerPlayConnectionEvents.JOIN.register((h, s, server) -> forceSync(h.player));
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> STATES.remove(h.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
			if (!alive) newP.setAttached(CURRENT, (float) Math.floor(attrMax(newP) * ArsenalStats.RESPAWN_FRACTION));
			else newP.setAttached(CURRENT, current(oldP));
			forceSync(newP);
		});
	}

	// ---------------------------------------------------------------- getters

	public static float attrMax(Player p) {
		return (float) ArsenalAttributes.value(p, ArsenalAttributes.MAX_GLIMMER) + Calling.maxGlimmerBonus(p);
	}

	/** Minions out (soft link to the summoner module's {@code dev.elsewhere.minion.MinionApi.activeCount}). */
	public static int minions(Player p) {
		if (!(p instanceof ServerPlayer sp)) return ClientMirror.minions;
		try {
			if (activeCount == null && !lookedUp) {
				lookedUp = true;
				activeCount = Class.forName("dev.elsewhere.minion.MinionApi").getMethod("activeCount", ServerPlayer.class);
			}
			if (activeCount != null) return (Integer) activeCount.invoke(null, sp);
		} catch (ReflectiveOperationException | RuntimeException e) {
			activeCount = null;
		}
		return devMinions;
	}

	private static Method activeCount;
	private static boolean lookedUp;
	/** Dev/self-test stand-in for the minion count when the summoner module is absent. */
	public static int devMinions;

	/** Max Glimmer held back by active minions (ARSENAL §3b: 8/7/6/5/4 each by Summoner Calling). */
	public static int reserved(Player p) {
		if (!(p instanceof ServerPlayer sp)) return ClientMirror.reserved;
		int n = minions(p);
		if (n <= 0) return 0;
		try {
			if (reservedGlimmer == null && !lookedUpRes) {
				lookedUpRes = true;
				reservedGlimmer = Class.forName("dev.elsewhere.minion.MinionApi").getMethod("reservedGlimmer", ServerPlayer.class);
			}
			if (reservedGlimmer != null) return (Integer) reservedGlimmer.invoke(null, sp);
		} catch (ReflectiveOperationException | RuntimeException e) {
			reservedGlimmer = null;
		}
		return Math.round(n * Calling.minionReserve(p));
	}

	private static Method reservedGlimmer;
	private static boolean lookedUpRes;

	/** Usable max (attribute max minus the minion reserve). */
	public static float max(Player p) {
		return Math.max(0, attrMax(p) - reserved(p));
	}

	public static float current(Player p) {
		if (!(p instanceof ServerPlayer)) return ClientMirror.current;
		Float v = p.getAttached(CURRENT);
		if (v == null) {
			float m = max(p);
			p.setAttached(CURRENT, m);
			return m;
		}
		return v;
	}

	private static void set(ServerPlayer p, float v) {
		p.setAttached(CURRENT, Math.max(0, Math.min(max(p), v)));
	}

	/** Glimmer a raw cost really takes: × spell_cost (0.5–2) × the caster Calling cost, rounded to 0.1, minimum 1 (rev 4). */
	public static float cost(Player p, int raw) {
		return cost(p, raw, false);
	}

	public static float cost(Player p, int raw, boolean starter) {
		double m = Math.max(ArsenalStats.COST_MULT_MIN, Math.min(ArsenalStats.COST_MULT_MAX, ArsenalAttributes.value(p, ArsenalAttributes.SPELL_COST)));
		m *= Calling.costMult(p, starter);
		double r = Math.round(raw * m / ArsenalStats.COST_ROUNDING) * (double) ArsenalStats.COST_ROUNDING;
		return (float) Math.max(ArsenalStats.COST_MIN, Math.round(r * 10) / 10.0);
	}

	// ---------------------------------------------------------------- spending

	/** Spends a raw cost (scaled by {@link #cost}). False (and nothing spent; the bar flashes, a clink plays) if not enough. */
	public static boolean trySpend(ServerPlayer p, int raw) {
		return trySpendScaled(p, cost(p, raw));
	}

	public static boolean trySpend(ServerPlayer p, int raw, boolean starter) {
		return trySpendScaled(p, cost(p, raw, starter));
	}

	/** Spends an already-scaled amount. Creative players spend nothing. */
	public static boolean trySpendScaled(ServerPlayer p, float amount) {
		State s = state(p);
		float cur = current(p);
		s.moonlitPrimed = SetBonus.moonlit(p) && minions(p) == 0 && cur >= max(p) - 1.0E-3F && max(p) > 0;
		if (p.isCreative()) {
			s.lastSpend = p.level().getGameTime();
			return true;
		}
		if (cur + 1.0E-4F < amount) {
			fail(p, amount);
			return false;
		}
		set(p, cur - amount);
		s.lastSpend = p.level().getGameTime();
		sync(p, s, false, 0);
		return true;
	}

	/** True once per successful spend if the spend happened at full Glimmer with the Moonlit set bonus (no minions out). */
	public static boolean consumeMoonlit(ServerPlayer p) {
		State s = state(p);
		boolean m = s.moonlitPrimed;
		s.moonlitPrimed = false;
		return m;
	}

	/** Fractional upkeep drain (no spell_cost). False if the pool can't pay. */
	public static boolean drain(ServerPlayer p, float amount) {
		if (p.isCreative()) return true;
		float cur = current(p);
		if (cur < amount) return false;
		set(p, cur - amount);
		state(p).lastSpend = p.level().getGameTime();
		return true;
	}

	public static void refund(ServerPlayer p, float amount) {
		set(p, current(p) + amount);
	}

	/** Restores Glimmer (tonics). */
	public static void restore(ServerPlayer p, float amount) {
		refund(p, amount);
		sync(p, state(p), true, 0);
	}

	/** Regen ×2 for the given ticks (Glimmer Tonic). */
	public static void surge(ServerPlayer p, int ticks) {
		state(p).surgeUntil = p.level().getGameTime() + ticks;
	}

	public static void setForTest(ServerPlayer p, float v) {
		set(p, v);
	}

	public static void markSpentForTest(ServerPlayer p, long ago) {
		state(p).lastSpend = p.level().getGameTime() - ago;
	}

	private static void fail(ServerPlayer p, float need) {
		lastFailNeed = need;
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.6F, 1.7F);
		sync(p, state(p), true, (int) Math.ceil(need));
	}

	// ---------------------------------------------------------------- regen

	/** Glimmer regenerated per second right now (before the delay and food checks). */
	public static float regenPerSecond(ServerPlayer p) {
		State s = state(p);
		float r = ArsenalStats.REGEN_PER_SECOND * (float) ArsenalAttributes.value(p, ArsenalAttributes.GLIMMER_REGEN);
		r *= Calling.regenMult(p);
		if (p.level().getGameTime() < s.surgeUntil) r *= ArsenalStats.TONIC_SURGE_MULT;
		if (airborneMantle(p)) r *= ArsenalStats.MANTLE_AIR_REGEN_MULT;
		int n = minions(p);
		if (n > 0) r *= Math.max(0, 1 - ArsenalStats.REGEN_PER_MINION_PENALTY * n);
		return Math.max(0, r);
	}

	private static boolean airborneMantle(ServerPlayer p) {
		return !p.onGround() && !p.isInWater() && p.getItemBySlot(EquipmentSlot.CHEST).is(ArsenalItems.STORMSAGE_MANTLE);
	}

	private static int regenDelay(ServerPlayer p) {
		if (airborneMantle(p)) return 0;
		return SetBonus.moonlit(p) ? ArsenalStats.REGEN_DELAY_MOONLIT_TICKS : ArsenalStats.REGEN_DELAY_TICKS;
	}

	public static boolean canRegen(ServerPlayer p) {
		return p.isCreative() || p.getFoodData().getFoodLevel() >= ArsenalStats.REGEN_MIN_FOOD;
	}

	/** One player's tick (public for the self-test). */
	public static void tickPlayer(ServerPlayer p) {
		if (!p.isAlive()) return;
		State s = state(p);
		float max = max(p);
		float cur = current(p);
		if (cur > max) set(p, cur = max);
		if (cur < max && canRegen(p) && p.level().getGameTime() - s.lastSpend >= regenDelay(p)) {
			float add = Math.min(regenPerSecond(p) / 20F, max - cur);
			if (add > 0) {
				set(p, cur + add);
				if (!p.isCreative() && p.level().getDifficulty() != Difficulty.PEACEFUL) p.causeFoodExhaustion(add * ArsenalStats.EXHAUSTION_PER_GLIMMER);
			}
		}
		sync(p, s, false, 0);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p);
	}

	// ---------------------------------------------------------------- sync

	public static void forceSync(ServerPlayer p) {
		sync(p, state(p), true, 0);
	}

	private static void sync(ServerPlayer p, State s, boolean force, int failNeed) {
		float cur = current(p), max = attrMax(p);
		int res = reserved(p), mins = minions(p);
		s.keepAlive++;
		boolean changed = force || Math.abs(cur - s.sentCur) >= ArsenalStats.SYNC_STEP || max != s.sentMax || res != s.sentRes || mins != s.sentMinions
			|| (cur >= max(p) && s.sentCur < max(p)) || s.keepAlive >= 100;
		if (!changed) return;
		s.sentCur = cur;
		s.sentMax = max;
		s.sentRes = res;
		s.sentMinions = mins;
		s.keepAlive = 0;
		if (ServerPlayNetworking.canSend(p, GlimmerPayload.TYPE)) ServerPlayNetworking.send(p, new GlimmerPayload(cur, max, res, mins, failNeed));
	}

	/** Client-side copy of the last payload (plain fields so common code can read it on the client). */
	public static final class ClientMirror {
		public static float current, max = 20;
		public static int reserved, minions;
	}
}
