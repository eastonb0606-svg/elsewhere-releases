package dev.elsewhere.stub.greenhaze_hush;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.storage.LevelData;

/**
 * STUB of the core's accidental-trip framework, implementing the rules from CONTRACT.md "User decisions (2026-11-10)":
 * <ul>
 * <li>LOCATION triggers: familiarity damping max(0.03, 0.5^(H/0.5)) per 512x512 region per dimension (H = hours spent
 * there, saved with the player); always 0.03 within 128 blocks of the respawn point.</li>
 * <li>ACTIVITY triggers: only the first N events per playtime hour roll (budgets below).</li>
 * <li>2 h playtime cooldown after any trip, none in the first 60 min, pity x2 after 8 h without one (never at home).</li>
 * <li>A 3 s warning (whispers, particles, Darkness vignette) that damage cancels.</li>
 * </ul>
 * No trips while riding, in combat (hurt in the last 10 s) or inside a boss arena. At merge the core's version replaces this.
 */
public final class TripSystem {
	private TripSystem() {}

	public static final double MIN_MULT = 0.03;
	public static final double HALF_LIFE_HOURS = 0.5;
	public static final int HOME_RADIUS = 128;
	public static final long TICKS_PER_HOUR = 72000;
	public static final long COOLDOWN_TICKS = 2 * TICKS_PER_HOUR;
	public static final long MIN_PLAYTIME_TICKS = TICKS_PER_HOUR;
	public static final long PITY_TICKS = 8 * TICKS_PER_HOUR;
	public static final int WARNING_TICKS = 60;
	/** Hourly budgets for ACTIVITY triggers, by trigger id. */
	public static final Map<String, Integer> BUDGETS = Map.of(
		"petalwind.long_fall", 3, "petalwind.wind_launch", 4, "greenhaze.green_leaf_use", 12, "the_hush.warden", 2);
	public static double scale = 1.0;
	public static boolean enabled = true;

	/** Arrival handler: (player, trigger) -> teleport into the target dimension. Set by the content module. */
	public static BiConsumer<ServerPlayer, TripTrigger> arrival = (p, t) -> {};
	/** Is this player in a boss fight? Set by the boss framework. */
	public static java.util.function.Predicate<ServerPlayer> inBossFight = p -> false;

	public record State(long lastTrip, long budgetHour, Map<String, Integer> budgetUsed, Map<String, Double> regionHours) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("last_trip").forGetter(State::lastTrip),
			Codec.LONG.fieldOf("budget_hour").forGetter(State::budgetHour),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("budget_used").forGetter(State::budgetUsed),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("region_hours").forGetter(State::regionHours)
		).apply(i, State::new));

		static State fresh() {
			return new State(Long.MIN_VALUE / 4, -1, new HashMap<>(), new HashMap<>());
		}

		State copy() {
			return new State(lastTrip, budgetHour, new HashMap<>(budgetUsed), new HashMap<>(regionHours));
		}
	}

	/** Stub attachment id (prefixed so it cannot clash with the core's own). */
	public static final AttachmentType<State> STATE = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath("elsewhere", "greenhaze_hush_trip_state"),
		b -> b.persistent(State.CODEC).initializer(State::fresh).copyOnDeath());

	private record Pending(TripTrigger trigger, int ticksLeft) {}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static int tick;

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer p) {
				LAST_HURT.put(p.getUUID(), p.level().getGameTime());
				if (PENDING.remove(p.getUUID()) != null) {
					p.sendSystemMessage(Component.literal("The pull fades as you are hurt.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TripSystem::tick);
	}

	// ------------------------------------------------------------------------------------------------ pure math

	public static double familiarity(double hours) {
		return Math.max(MIN_MULT, Math.pow(0.5, hours / HALF_LIFE_HOURS));
	}

	public static String regionKey(String dim, int x, int z) {
		return dim + "|" + Math.floorDiv(x, 512) + "|" + Math.floorDiv(z, 512);
	}

	// ------------------------------------------------------------------------------------------------ live

	public static long playtime(ServerPlayer p) {
		return p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
	}

	static boolean atHome(ServerPlayer p) {
		ServerPlayer.RespawnConfig cfg = p.getRespawnConfig();
		LevelData.RespawnData data = cfg != null ? cfg.respawnData() : p.level().getServer().getRespawnData();
		if (!data.globalPos().dimension().equals(p.level().dimension())) return false;
		BlockPos home = data.globalPos().pos();
		return home.distSqr(p.blockPosition()) <= (double) HOME_RADIUS * HOME_RADIUS;
	}

	/** Why a trip can't happen now, or null if it may roll. */
	public static String blocked(ServerPlayer p, TripTrigger t) {
		if (!enabled || scale <= 0) return "disabled";
		if (p.level().dimension().equals(t.target())) return "already there";
		if (p.isPassenger()) return "riding";
		if (p.isSpectator() || p.isCreative()) return "spectator/creative";
		Long hurt = LAST_HURT.get(p.getUUID());
		if (hurt != null && p.level().getGameTime() - hurt < 200) return "combat";
		if (inBossFight.test(p)) return "boss fight";
		long play = playtime(p);
		if (play < MIN_PLAYTIME_TICKS) return "first hour";
		State s = p.getAttachedOrCreate(STATE);
		if (play - s.lastTrip() < COOLDOWN_TICKS) return "cooldown";
		if (PENDING.containsKey(p.getUUID())) return "pending";
		return null;
	}

	/**
	 * Fire a trigger's event. Returns the final chance that was rolled (0 if blocked or over budget).
	 * On success the 3 s warning starts.
	 */
	public static double fire(ServerPlayer p, TripTrigger t, RandomSource random) {
		if (blocked(p, t) != null) return 0;
		State s = p.getAttachedOrCreate(STATE).copy();
		long play = playtime(p);
		boolean home = atHome(p);
		double mult;
		if (t.kind() == TripTrigger.Kind.ACTIVITY) {
			long hour = play / TICKS_PER_HOUR;
			Map<String, Integer> used = hour == s.budgetHour() ? s.budgetUsed() : new HashMap<>();
			int n = used.getOrDefault(t.id(), 0);
			int budget = BUDGETS.getOrDefault(t.id(), 3);
			used.put(t.id(), n + 1);
			p.setAttached(STATE, new State(s.lastTrip(), hour, used, s.regionHours()));
			if (n >= budget) return 0;
		}
		// familiarity damping applies to LOCATION and ACTIVITY triggers alike (lead, 19:10)
		mult = home ? MIN_MULT : familiarity(s.regionHours().getOrDefault(regionKey(p.level().dimension().identifier().toString(),
			p.getBlockX(), p.getBlockZ()), 0.0));
		if (!home && play - Math.max(0, s.lastTrip()) > PITY_TICKS) mult *= 2;
		double chance = Math.min(1, t.baseChance(p) * scale * mult);
		if (random.nextDouble() < chance) startWarning(p, t);
		return chance;
	}

	public static void startWarning(ServerPlayer p, TripTrigger t) {
		PENDING.put(p.getUUID(), new Pending(t, WARNING_TICKS));
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, WARNING_TICKS + 20, 0, false, false));
		p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		p.sendSystemMessage(Component.literal("Something is pulling at you...").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}

	public static boolean isPending(ServerPlayer p) {
		return PENDING.containsKey(p.getUUID());
	}

	private static void tick(MinecraftServer server) {
		tick++;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Pending pending = PENDING.get(p.getUUID());
			if (pending != null) {
				ServerLevel level = p.level();
				if (pending.ticksLeft() % 2 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.6, 0.9, 0.6, 0.05); // P13: every 2nd tick
				if (pending.ticksLeft() % 20 == 0) {
					level.playSound(null, p.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
				}
				if (pending.ticksLeft() <= 0) {
					PENDING.remove(p.getUUID());
					State s = p.getAttachedOrCreate(STATE).copy();
					p.setAttached(STATE, new State(playtime(p), s.budgetHour(), s.budgetUsed(), s.regionHours()));
					arrival.accept(p, pending.trigger());
				} else {
					PENDING.put(p.getUUID(), new Pending(pending.trigger(), pending.ticksLeft() - 1));
				}
			}
			// Familiarity: one second of time in this region.
			if (Math.floorMod(tick + p.getId(), 20) == 0 && !p.isSpectator()) {
				State s = p.getAttachedOrCreate(STATE).copy();
				String key = regionKey(p.level().dimension().identifier().toString(), p.getBlockX(), p.getBlockZ());
				s.regionHours().merge(key, 1.0 / 3600.0, Double::sum);
				p.setAttached(STATE, s);
			}
		}
	}

	/** Testing hook: pretend this player last tripped long ago and has played for the given number of hours. */
	public static void resetForTest(ServerPlayer p) {
		PENDING.remove(p.getUUID());
		LAST_HURT.remove(p.getUUID());
		p.setAttached(STATE, State.fresh());
	}

	// ------------------------------------------------------------------------------------------------ simulation

	/**
	 * Monte-Carlo estimate of trips for a scripted player, using exactly the live rules. eventsPerHour events of one
	 * trigger fire, spread evenly; atHome selects the 0.03 home multiplier; for LOCATION triggers away from home the
	 * player stays in one region the whole time (the "built a house there" case when atHome is false).
	 */
	public static double simulate(TripTrigger.Kind kind, String id, double baseChance, double eventsPerHour, double hours,
								  boolean atHome, int runs, long seed) {
		RandomSource random = RandomSource.create(seed);
		long total = 0;
		int budget = BUDGETS.getOrDefault(id, 3);
		for (int run = 0; run < runs; run++) {
			double lastTrip = -1e9;
			double regionH = 0;
			int events = (int) Math.round(eventsPerHour * hours);
			for (int e = 0; e < events; e++) {
				double t = (e + random.nextDouble()) / eventsPerHour; // hours of playtime
				regionH = t;
				if (t < 1.0) continue;
				if (t - lastTrip < 2.0) continue;
				double mult;
				if (kind == TripTrigger.Kind.ACTIVITY) {
					int indexInHour = (int) Math.floor((t - Math.floor(t)) * eventsPerHour);
					if (indexInHour >= budget) continue;
				}
				mult = atHome ? MIN_MULT : familiarity(regionH);
				if (!atHome && t - Math.max(0, lastTrip) > 8) mult *= 2;
				if (random.nextDouble() < Math.min(1, baseChance * scale * mult)) {
					total++;
					lastTrip = t;
				}
			}
		}
		return (double) total / runs;
	}

	public static void forgetPlayer(UUID id) {
		PENDING.remove(id);
		LAST_HURT.remove(id);
	}

	public static void clearAll() {
		PENDING.clear();
		LAST_HURT.clear();
	}
}
