package dev.elsewhere.stub.petalwind_glasswaste;

import dev.elsewhere.petalwind_glasswaste.PetalwindGlasswasteContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * STUB of the core's accidental-trip framework so this module's triggers can be exercised before the core exists:
 * per-player {@link TripRules}, the 3 s cancellable "pull" warning, and a safe arrival. The core replaces all of it
 * (it also hands out the Frayed Wayfinder, unlocks recipes and saves the state with the player).
 */
public final class TripStub {
	private TripStub() {}

	public static final int WARNING_TICKS = 60;
	private static final Map<UUID, TripRules> RULES = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	public static boolean enabled = true;
	private static long tick;

	private record Pending(TripTrigger trigger, long start) {}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0) {
				LAST_HURT.put(p.getUUID(), tick);
				if (PENDING.remove(p.getUUID()) != null) {
					p.sendSystemMessage(Component.literal("The pull fades as you're hurt.").withStyle(ChatFormatting.GRAY));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TripStub::onTick);
	}

	public static TripRules rules(ServerPlayer player) {
		return RULES.computeIfAbsent(player.getUUID(), u -> new TripRules());
	}

	public static boolean atHome(ServerPlayer player) {
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		BlockPos home;
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim;
		if (config != null) {
			home = config.respawnData().globalPos().pos();
			dim = config.respawnData().globalPos().dimension();
		} else {
			home = player.level().getRespawnData().globalPos().pos();
			dim = player.level().getRespawnData().globalPos().dimension();
		}
		return player.level().dimension() == dim && player.blockPosition().closerThan(home, TripRules.HOME_RADIUS);
	}

	public static String region(ServerPlayer player) {
		return TripRules.regionKey(player.level().dimension().identifier().toString(), player.getX(), player.getZ());
	}

	/** Fire a trigger's event for a player (call from the trigger's hook). Returns true when a trip warning started. */
	public static boolean fire(TripTrigger trigger, ServerPlayer player) {
		if (!enabled || player.isSpectator() || PENDING.containsKey(player.getUUID())) return false;
		if (player.level().dimension() == trigger.target()) return false;
		boolean fallException = trigger.id().equals("petalwind.long_fall");
		if (!fallException && tick - LAST_HURT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < 200) return false;
		if (player.isPassenger() || BossFightRegistry.inFight(player)) return false;
		double base = trigger.baseChance(player);
		if (base <= 0) return false;
		double chance = rules(player).chance(trigger.id(), trigger.kind(), base, region(player), atHome(player));
		if (chance <= 0 || player.getRandom().nextDouble() >= chance) return false;
		PENDING.put(player.getUUID(), new Pending(trigger, tick));
		player.sendSystemMessage(Component.literal("Something is pulling at you...").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		return true;
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		if (tick % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) rules(p).tick(1.0, region(p));
		}
		if (PENDING.isEmpty()) return;
		for (var it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			long age = tick - e.getValue().start();
			ServerLevel level = p.level();
			if (age % 2 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.6, 0.9, 0.6, 0.02); // P13: every 2nd tick
			if (age % 20 == 0) level.playSound(null, p.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.6F);
			if (age >= WARNING_TICKS) {
				it.remove();
				arrive(p, e.getValue().trigger());
			}
		}
	}

	public static boolean arrive(ServerPlayer player, TripTrigger trigger) {
		ServerLevel target = player.level().getServer().getLevel(trigger.target());
		if (target == null) return false;
		BlockPos spot = SafeSpot.find(target, BlockPos.containing(player.getX(), 0, player.getZ()), 256);
		if (spot == null) spot = SafeSpot.find(target, BlockPos.ZERO, 512);
		if (spot == null) {
			PetalwindGlasswasteContent.LOGGER.warn("Accidental trip to {} found no safe spot", trigger.target().identifier());
			return false;
		}
		rules(player).recordTrip();
		player.teleport(new TeleportTransition(target, Vec3.atBottomCenterOf(spot), Vec3.ZERO, player.getYRot(), player.getXRot(),
			TeleportTransition.PLAY_PORTAL_SOUND));
		player.resetFallDistance();
		player.sendSystemMessage(Component.literal("You drifted into " + DimensionNames.name(trigger.target()) + ". (A Frayed Wayfinder will take you back once.)")
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	public static boolean pending(ServerPlayer player) {
		return PENDING.containsKey(player.getUUID());
	}
}
