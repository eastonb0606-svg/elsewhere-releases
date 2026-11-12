package dev.elsewhere.core.travel;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.core.trip.TripState;
import dev.elsewhere.registry.ModAttachments;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModParticles;
import dev.elsewhere.registry.ModSounds;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Opening rifts, choosing where they lead, and moving players through them. */
public final class RiftTravel {
	private RiftTravel() {
	}

	public static void init() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> onArrive(player, destination));
	}

	/**
	 * Wayfinder use. Inside the attuned dimension it opens a rift home; anywhere else a rift to that dimension.
	 * The rift opens in front of the player.
	 */
	public static boolean openWayfinderRift(final ServerPlayer player, final ResourceKey<Level> attunedTo) {
		ServerLevel here = player.level();
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 at = player.position().add(look.scale(1.6));
		boolean home = here.dimension().equals(attunedTo);
		Destination dest = home ? homeDestination(player) : Anchors.anchorDestination(player, attunedTo);
		if (dest == null && !home) {
			dest = outboundDestination(player, attunedTo);
		}
		if (dest == null) {
			return false;
		}
		if (!home) {
			rememberReturnPoint(player);
		}
		spawnRift(here, at, player.getYRot() + 180.0F, dest, player, home ? 0xF2EBDD : ElsewhereDimensions.colorOf(attunedTo), false);
		return true;
	}

	/** Sneak-use fast travel: a rift to the Overworld at this dimension's X/Z times its coordinate scale. */
	public static boolean openScaledHomeRift(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		double scale = DimensionType.getTeleportationScale(player.level().dimensionType(), overworld.dimensionType());
		int x = (int)Math.floor(player.getX() * scale);
		int z = (int)Math.floor(player.getZ() * scale);
		Vec3 pos = SafeLanding.find(overworld, x, z, 48, overworld.getMaxY(), net.minecraft.world.level.block.Blocks.COBBLESTONE);
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		spawnRift(player.level(), player.position().add(look.scale(1.6)), player.getYRot() + 180.0F, new Destination(overworld, pos), player, 0xF2EBDD, false);
		return true;
	}

	/** A rift that opens on the player and takes them regardless of where they step (accidental trips, Frayed Wayfinder). */
	public static boolean openForcedRift(final ServerPlayer player, final @Nullable ResourceKey<Level> target) {
		Destination dest = target == null ? homeDestination(player) : outboundDestination(player, target);
		if (dest == null) {
			return false;
		}
		if (target != null) {
			rememberReturnPoint(player);
		}
		spawnRift(player.level(), player.position(), player.getYRot() + 180.0F, dest, player,
			target == null ? 0xF2EBDD : ElsewhereDimensions.colorOf(target), true);
		return true;
	}

	/** Immediate trip with no rift (commands and tests). */
	public static boolean travelNow(final ServerPlayer player, final @Nullable ResourceKey<Level> target) {
		Destination dest = target == null ? homeDestination(player) : outboundDestination(player, target);
		if (dest == null) {
			return false;
		}
		if (target != null) {
			rememberReturnPoint(player);
		}
		teleport(player, dest.level(), dest.pos(), player.getYRot());
		return true;
	}

	public record Destination(ServerLevel level, Vec3 pos) {
	}

	/** A rift in front of the player to an explicit destination (Boss Waystones). */
	public static void openRiftTo(final ServerPlayer player, final Destination dest, final int color) {
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		spawnRift(player.level(), player.position().add(look.scale(1.6)), player.getYRot() + 180.0F, dest, player, color, false);
	}

	/** First accidental trip (qol-first-trip): lands 32-128 blocks from a Return Rift. Falls back to the normal landing. */
	public static boolean travelFirstTrip(final ServerPlayer player, final ResourceKey<Level> target) {
		Destination dest = Anchors.firstTripDestination(player, target);
		if (dest == null) {
			return travelNow(player, target);
		}
		rememberReturnPoint(player);
		teleport(player, dest.level(), dest.pos(), player.getYRot());
		return true;
	}

	/** The most recently opened rift (self-test hook). */
	public static RiftEntity lastRift;

	private static void spawnRift(final ServerLevel level, final Vec3 at, final float yaw, final Destination dest, final ServerPlayer owner,
		final int color, final boolean forced) {
		RiftEntity rift = ModEntities.RIFT.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (rift == null) {
			return;
		}
		rift.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		rift.configure(dest.level().dimension(), dest.pos(), owner, color, forced);
		level.addFreshEntity(rift);
		lastRift = rift;
	}

	/** Scaled coordinates in the target dimension, nudged to within ~250 blocks of a Return Rift, then a safe spot. */
	public static @Nullable Destination outboundDestination(final ServerPlayer player, final ResourceKey<Level> target) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.getLevel(target);
		if (level == null) {
			player.sendSystemMessage(Component.translatable("message.elsewhere.no_dimension", target.identifier().toString()));
			return null;
		}
		double scale = DimensionType.getTeleportationScale(player.level().dimensionType(), level.dimensionType());
		int x = (int)Math.floor(player.getX() * scale);
		int z = (int)Math.floor(player.getZ() * scale);
		x = Math.max(-29_000_000, Math.min(29_000_000, x));
		z = Math.max(-29_000_000, Math.min(29_000_000, z));
		ElsewhereDimensions.Info info = ElsewhereDimensions.get(target);
		if (info != null && info.returnRifts()) {
			BlockPos rift = ReturnRifts.nearestRift(level, x, z);
			double dx = x - rift.getX();
			double dz = z - rift.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			double max = ReturnRifts.LANDING_RADIUS;
			if (d > max) {
				x = rift.getX() + (int)(dx / d * max);
				z = rift.getZ() + (int)(dz / d * max);
			}
		}
		int maxY = info == null ? level.getMaxY() : info.maxLandingY();
		Vec3 pos = SafeLanding.find(level, x, z, 64, maxY, ElsewhereDimensions.platformOf(target));
		return new Destination(level, pos);
	}

	/** The saved return point, or the player's respawn point, or world spawn; always a safe spot. */
	public static Destination homeDestination(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ReturnPoint rp = player.getAttached(ModAttachments.RETURN_POINT);
		if (rp != null) {
			ServerLevel level = server.getLevel(rp.dimension());
			if (level != null && !ElsewhereDimensions.isElsewhere(rp.dimension())) {
				return new Destination(level, SafeLanding.near(level, rp.pos(), 24, ElsewhereDimensions.platformOf(rp.dimension())));
			}
		}
		return respawnDestination(player);
	}

	/** The player's respawn point (bed/anchor) or world spawn, safe-spot searched. */
	public static Destination respawnDestination(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		LevelData.RespawnData data = respawn != null ? respawn.respawnData() : server.getRespawnData();
		ServerLevel level = server.getLevel(data.dimension());
		if (level == null) {
			level = server.overworld();
		}
		BlockPos p = data.pos();
		return new Destination(level, SafeLanding.find(level, p.getX(), p.getZ(), 32, level.getMaxY(), net.minecraft.world.level.block.Blocks.COBBLESTONE));
	}

	/** Saves where the player stands, unless they are already inside an Elsewhere dimension (keep the original way home). */
	public static void rememberReturnPoint(final ServerPlayer player) {
		if (!ElsewhereDimensions.isElsewhere(player.level().dimension())) {
			player.setAttached(ModAttachments.RETURN_POINT, new ReturnPoint(player.level().dimension(), player.position(), player.getYRot()));
		}
	}

	public static void teleport(final ServerPlayer player, final ServerLevel target, final Vec3 pos, final float yaw) {
		ServerLevel from = player.level();
		from.sendParticles(ModParticles.spark(ElsewhereDimensions.colorOf(target.dimension())), player.getX(), player.getY() + 1.0, player.getZ(),
			40, 0.4, 0.8, 0.4, 0.05);
		from.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RIFT_TRAVEL.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		player.stopRiding();
		player.teleport(new TeleportTransition(target, pos, Vec3.ZERO, yaw, player.getXRot(), TeleportTransition.DO_NOTHING));
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
		target.playSound(null, pos.x, pos.y, pos.z, ModSounds.RIFT_TRAVEL.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		target.sendParticles(ModParticles.spark(ElsewhereDimensions.colorOf(target.dimension())), pos.x, pos.y + 1.0, pos.z, 40, 0.5, 0.9, 0.5, 0.05);
	}

	/** First visit to an Elsewhere dimension unlocks its attunement recipe. */
	/** Last cooldown resync sent on arrival, in ticks (self-test hook). */
	public static int lastCooldownResync = -1;

	/**
	 * 0.4.0 fix for "the attuned Wayfinder used inside its dimension does nothing": the client rebuilds its player on a
	 * dimension change and forgets item cooldowns, while the server keeps them. So after a Wayfinder trip the client showed
	 * no cooldown, but the server still had ~57 s left and silently ignored every use. On every level change we now send
	 * the remaining Wayfinder cooldown again, so the hotbar shows it and the use works as soon as it ends.
	 */
	public static int resyncWayfinderCooldown(final ServerPlayer player) {
		net.minecraft.world.item.ItemStack probe = new net.minecraft.world.item.ItemStack(dev.elsewhere.registry.ModItems.WAYFINDER_PALE_HOLLOW);
		net.minecraft.world.item.ItemCooldowns cooldowns = player.getCooldowns();
		if (!cooldowns.isOnCooldown(probe)) {
			return 0;
		}
		net.minecraft.world.item.component.UseCooldown use = probe.get(net.minecraft.core.component.DataComponents.USE_COOLDOWN);
		int total = use != null ? use.ticks() : (int)Math.round(dev.elsewhere.ElsewhereConfig.get().wayfinders.cooldownSeconds * 20.0);
		int remaining = Math.max(1, (int)Math.ceil(cooldowns.getCooldownPercent(probe, 0.0F) * total));
		if (player.connection != null) {
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundCooldownPacket(cooldowns.getCooldownGroup(probe), remaining));
		}
		return remaining;
	}

	private static void onArrive(final ServerPlayer player, final ServerLevel destination) {
		lastCooldownResync = resyncWayfinderCooldown(player);
		ResourceKey<Level> key = destination.dimension();
		if (!ElsewhereDimensions.isElsewhere(key)) {
			return;
		}
		TripState state = player.getAttachedOrCreate(ModAttachments.TRIP_STATE);
		if (state.visited.add(key.identifier().toString())) {
			player.awardRecipesByKey(List.of(ResourceKey.create(Registries.RECIPE, Elsewhere.id("wayfinder_" + key.identifier().getPath()))));
		}
	}
}
