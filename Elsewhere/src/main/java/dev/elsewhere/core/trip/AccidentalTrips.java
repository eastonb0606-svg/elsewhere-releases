package dev.elsewhere.core.trip;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.ElsewhereConfig;
import dev.elsewhere.core.boss.ElsewhereBoss;
import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.core.travel.RiftTravel;
import dev.elsewhere.registry.ModAttachments;
import dev.elsewhere.registry.ModItems;
import dev.elsewhere.registry.ModParticles;
import dev.elsewhere.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelData;

/**
 * The accidental-trip framework. Triggers ({@link TripTrigger}) report their base chance when their event happens via
 * {@link #fire}; this class applies the rules from CONTRACT.md: familiarity damping per 512x512 region, the home radius,
 * hourly budgets for activity triggers, the 2 h cooldown, the 60 min grace period, the pity timer, and the 3 s
 * cancellable "pull" before the trip. Arrivals get a Frayed Wayfinder.
 */
public final class AccidentalTrips {
	private static final Map<String, TripTrigger> TRIGGERS = new LinkedHashMap<>();
	private static final Map<UUID, Pull> PULLS = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	/** Test hook: when set, replaces the random roll (value in [0,1)). */
	public static java.util.function.DoubleSupplier rollOverride = null;

	/** Test hook: playtime source for fake players (whose stats never advance). */
	public static java.util.function.ToLongFunction<ServerPlayer> playTicksOverride = null;

	private record Pull(ServerPlayer player, TripTrigger trigger, int total, int[] left) {
	}

	private AccidentalTrips() {
	}

	public static void register(final TripTrigger trigger) {
		TRIGGERS.put(trigger.id(), trigger);
	}

	public static Map<String, TripTrigger> triggers() {
		return TRIGGERS;
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(PullPayload.TYPE, PullPayload.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F) {
				LAST_HURT.put(player.getUUID(), player.level().getGameTime());
				if (PULLS.remove(player.getUUID()) != null) {
					ServerPlayNetworking.send(player, new PullPayload(0, 0));
					player.sendOverlayMessage(Component.translatable("message.elsewhere.trip.cancelled").withStyle(ChatFormatting.GRAY));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(AccidentalTrips::tick);
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (!p.isSpectator()) {
					p.getAttachedOrCreate(ModAttachments.TRIP_STATE).addRegionTicks(regionKey(p), 20);
				}
			}
		}
		Iterator<Map.Entry<UUID, Pull>> it = PULLS.entrySet().iterator();
		List<Runnable> finish = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Pull> e = it.next();
			Pull pull = e.getValue();
			ServerPlayer p = pull.player();
			if (p == null || !p.isAlive()) {
				it.remove();
				continue;
			}
			int left = --pull.left()[0];
			int color = ElsewhereDimensions.colorOf(pull.trigger().target());
			ServerLevel level = p.level();
			double a = left * 0.4;
			level.sendParticles(ModParticles.spark(color), p.getX() + Math.cos(a) * 1.2, p.getY() + 0.2 + (pull.total() - left) * 0.03, p.getZ() + Math.sin(a) * 1.2,
				2, 0.05, 0.05, 0.05, 0.01);
			if (left % 20 == 0 && left > 0) {
				level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.TRIP_PULL.value(), SoundSource.PLAYERS, 0.8F, 0.8F + (pull.total() - left) * 0.004F);
			}
			if (left <= 0) {
				it.remove();
				finish.add(() -> arrive(p, pull.trigger()));
			}
		}
		finish.forEach(Runnable::run);
	}

	public static String regionKey(final ServerPlayer p) {
		int size = ElsewhereConfig.get().accidentalTrips.regionSize;
		return p.level().dimension().identifier() + "|" + Mth.floorDiv(p.getBlockX(), size) + "|" + Mth.floorDiv(p.getBlockZ(), size);
	}

	public static long playTicks(final ServerPlayer p) {
		if (playTicksOverride != null) {
			return playTicksOverride.applyAsLong(p);
		}
		return p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
	}

	/** Within the home radius of the respawn point (bed, anchor, or world spawn), in the same dimension. */
	public static boolean inHome(final ServerPlayer p) {
		ServerPlayer.RespawnConfig rc = p.getRespawnConfig();
		LevelData.RespawnData data = rc != null ? rc.respawnData() : p.level().getServer().getRespawnData();
		if (!data.dimension().equals(p.level().dimension())) {
			return false;
		}
		BlockPos home = data.pos();
		double r = ElsewhereConfig.get().accidentalTrips.homeRadius;
		return home.distToCenterSqr(p.getX(), home.getY() + 0.5, p.getZ()) <= r * r;
	}

	private static boolean blocked(final ServerPlayer p) {
		if (p.isPassenger() || p.isSpectator() || p.isCreative() || PULLS.containsKey(p.getUUID())) {
			return true;
		}
		if (!p.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
			return true; // BALANCE.md T1: triggers only roll in the Overworld
		}
		Long hurt = LAST_HURT.get(p.getUUID());
		if (hurt != null && p.level().getGameTime() - hurt < 200) {
			return true;
		}
		return !p.level().getEntitiesOfClass(ElsewhereBoss.class, p.getBoundingBox().inflate(64.0), ElsewhereBoss::isAwake).isEmpty();
	}

	public static double baseChance(final TripTrigger trigger, final ServerPlayer player) {
		Double configured = ElsewhereConfig.get().accidentalTrips.triggers.get(trigger.id());
		return configured != null ? configured : trigger.baseChance(player);
	}

	/** The chance this event would turn into a trip right now (does not spend the activity budget). */
	public static double currentChance(final ServerPlayer player, final TripTrigger trigger, final boolean budgetOk) {
		ElsewhereConfig.AccidentalTrips cfg = ElsewhereConfig.get().accidentalTrips;
		TripState state = player.getAttachedOrCreate(ModAttachments.TRIP_STATE);
		TripRules.Situation s = new TripRules.Situation(playTicks(player), state.lastTripPlayTicks, state.hoursIn(regionKey(player)),
			inHome(player), budgetOk, blocked(player));
		return TripRules.effectiveChance(baseChance(trigger, player), trigger.kind(), s, cfg);
	}

	/** A trigger's event happened. Rolls the damped chance and starts the pull on success. Returns true if a pull started. */
	public static boolean fire(final ServerPlayer player, final TripTrigger trigger) {
		if (!allowed(player)) {
			return false; // /elsewhere trips off, or server overworldContent=false
		}
		ElsewhereConfig.AccidentalTrips cfg = ElsewhereConfig.get().accidentalTrips;
		boolean budgetOk = true;
		if (trigger.kind() == TripTrigger.Kind.ACTIVITY) {
			int perHour = cfg.hourlyBudgets.getOrDefault(trigger.id(), 3);
			budgetOk = player.getAttachedOrCreate(ModAttachments.TRIP_STATE).spendBudget(trigger.id(), playTicks(player), perHour);
		}
		double chance = currentChance(player, trigger, budgetOk);
		double roll = rollOverride != null ? rollOverride.getAsDouble() : player.getRandom().nextDouble();
		if (chance <= 0.0 || roll >= chance) {
			return false;
		}
		startPull(player, trigger);
		return true;
	}

	/** Starts the 3 s warning; damage during it cancels the trip. */
	public static void startPull(final ServerPlayer player, final TripTrigger trigger) {
		if (!allowed(player)) {
			return;
		}
		int ticks = Math.max(1, (int)Math.round(ElsewhereConfig.get().accidentalTrips.pullSeconds * 20.0));
		PULLS.put(player.getUUID(), new Pull(player, trigger, ticks, new int[] {ticks}));
		ServerPlayNetworking.send(player, new PullPayload(ticks, ElsewhereDimensions.colorOf(trigger.target())));
		player.sendOverlayMessage(Component.translatable("message.elsewhere.trip.pull").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
	}

	/** Server switch {@code overworldContent} (config/elsewhere.json, default true). */
	public static boolean overworldContent() {
		return ElsewhereConfig.get().overworldContent;
	}

	/** The player's own {@code /elsewhere trips on|off} choice (default on). */
	public static boolean playerEnabled(final ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.TRIPS_ENABLED, Boolean.TRUE);
	}

	public static void setPlayerEnabled(final ServerPlayer player, final boolean on) {
		player.setAttached(ModAttachments.TRIPS_ENABLED, on);
		if (!on && PULLS.remove(player.getUUID()) != null) {
			ServerPlayNetworking.send(player, new PullPayload(0, 0));
		}
	}

	/** Gate checked first by {@link #fire} and {@link #startPull}: overworldContent on and the player has trips on. */
	public static boolean allowed(final ServerPlayer player) {
		return overworldContent() && playerEnabled(player);
	}

	public static boolean isPulling(final ServerPlayer player) {
		return PULLS.containsKey(player.getUUID());
	}

	private static void arrive(final ServerPlayer player, final TripTrigger trigger) {
		if (player.isSleeping()) {
			player.stopSleepInBed(true, true);
		}
		TripState state = player.getAttachedOrCreate(ModAttachments.TRIP_STATE);
		boolean first = state.lastTripPlayTicks < 0L;
		if (!(first ? RiftTravel.travelFirstTrip(player, trigger.target()) : RiftTravel.travelNow(player, trigger.target()))) {
			return;
		}
		if (first) {
			// qol-first-trip: one-time tip naming the switch, and the "Wrong Turn" advancement
			player.sendSystemMessage(Component.translatable("message.elsewhere.trip.first_tip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			dev.elsewhere.core.trophy.Trophies.grant(player, "wrong_turn");
		}
		state.lastTripPlayTicks = playTicks(player);
		ItemStack frayed = new ItemStack(ModItems.FRAYED_WAYFINDER);
		player.getInventory().placeItemBackInInventory(frayed, net.minecraft.util.Prediction.SERVER_ONLY);
		ElsewhereDimensions.Info info = ElsewhereDimensions.get(trigger.target());
		Component name = Component.translatable("dimension." + trigger.target().identifier().getNamespace() + "." + trigger.target().identifier().getPath());
		player.sendSystemMessage(Component.translatable("message.elsewhere.trip.arrived", name).withStyle(ChatFormatting.LIGHT_PURPLE));
		Elsewhere.LOG.info("Accidental trip: {} -> {} ({})", player.getName().getString(), trigger.target().identifier(), trigger.id());
		if (info == null) {
			Elsewhere.LOG.warn("Trip target {} is not a registered Elsewhere dimension", trigger.target().identifier());
		}
	}
}
