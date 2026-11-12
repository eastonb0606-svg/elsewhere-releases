package dev.elsewhere.core.travel;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.Progress;
import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.core.trophy.Trophies;
import dev.elsewhere.core.wayfinder.WayfinderItem;
import dev.elsewhere.registry.ModBlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Return-to-the-fight and travel QoL (2.0):
 * <ul>
 * <li>{@code new-rift-anchor}: an attuned Wayfinder lands next to your Rift Anchor in that dimension (one per player per
 * dimension; placing a new one pops the old one).</li>
 * <li>{@code new-boss-waystones}: a Boss Waystone appears where a boss falls; it is remembered for everyone credited (or
 * by using it). Sneak-use an attuned Wayfinder (outside its dimension) to warp to your latest waystone there; sneak-use
 * the base Wayfinder to warp to your latest waystone in the dimension you are in.</li>
 * <li>{@code qol-wayfinder-arrival}: during the arrival cooldown a Wayfinder use points to the nearest Return Rift
 * (direction + distance in the action bar) instead of doing nothing.</li>
 * <li>{@code qol-first-trip}: the first accidental trip lands 32-128 blocks from a Return Rift.</li>
 * </ul>
 * Position records always carry their dimension ("anchor:&lt;dim&gt;", "way:&lt;dim&gt;").
 */
public final class Anchors {
	private Anchors() {}

	public static final int FIRST_TRIP_MIN = 32;
	public static final int FIRST_TRIP_MAX = 128;
	/** Arrival times, keyed by player (cleared on stop). */
	private static final Map<UUID, Long> ARRIVED = new HashMap<>();

	public static void init() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
			ARRIVED.put(player.getUUID(), destination.getGameTime()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ARRIVED.clear());
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp) {
				ItemStack stack = sp.getItemInHand(hand);
				if (stack.getItem() instanceof WayfinderItem && sp.getCooldowns().isOnCooldown(stack) && pointToRift(sp)) {
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
	}

	private static String dimKey(final ResourceKey<Level> dim) {
		return dim.identifier().toString();
	}

	// ------------------------------------------------------------------ Rift Anchor
	public static void placeAnchor(final ServerPlayer p, final BlockPos pos) {
		ServerLevel level = p.level();
		String key = "anchor:" + dimKey(level.dimension());
		BlockPos old = anchorRecord(p, level.dimension());
		if (old != null && !old.equals(pos) && level.getBlockState(old).is(ModBlocks.RIFT_ANCHOR)) {
			level.destroyBlock(old, true);
		}
		Progress.set(p, key, Long.toString(pos.asLong()));
		boolean works = ElsewhereDimensions.isElsewhere(level.dimension());
		p.sendSystemMessage(Component.translatable(works ? "message.elsewhere.anchor.set" : "message.elsewhere.anchor.inert").withStyle(ChatFormatting.LIGHT_PURPLE));
		dev.elsewhere.core.trophy.Trophies.grant(p, "anchored");
	}

	public static @Nullable BlockPos anchorRecord(final ServerPlayer p, final ResourceKey<Level> dim) {
		long v = Progress.getLong(p, "anchor:" + dimKey(dim), Long.MIN_VALUE);
		return v == Long.MIN_VALUE ? null : BlockPos.of(v);
	}

	/** The player's anchor in {@code dim}, if the block is still there. */
	public static @Nullable BlockPos anchorFor(final ServerPlayer p, final ServerLevel level) {
		BlockPos pos = anchorRecord(p, level.dimension());
		if (pos == null) {
			return null;
		}
		if (!level.getBlockState(pos).is(ModBlocks.RIFT_ANCHOR)) {
			Progress.set(p, "anchor:" + dimKey(level.dimension()), null);
			return null;
		}
		return pos;
	}

	/** Landing next to the anchor, or null when the player has none in {@code target}. */
	public static RiftTravel.@Nullable Destination anchorDestination(final ServerPlayer p, final ResourceKey<Level> target) {
		ServerLevel level = p.level().getServer().getLevel(target);
		if (level == null) {
			return null;
		}
		BlockPos a = anchorFor(p, level);
		if (a == null) {
			return null;
		}
		Vec3 beside = nearestSafeAround(level, a, 5, 6);
		if (beside != null) {
			return new RiftTravel.Destination(level, beside);
		}
		return new RiftTravel.Destination(level, SafeLanding.near(level, Vec3.atBottomCenterOf(a.above()), 4, ElsewhereDimensions.platformOf(target)));
	}

	/** Closest safe standing spot within a box around the anchor (3D), so a cliff or tree never sends the player far away. */
	private static @Nullable Vec3 nearestSafeAround(final ServerLevel level, final BlockPos a, final int rh, final int rv) {
		level.getChunk(a.getX() >> 4, a.getZ() >> 4);
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos q : BlockPos.betweenClosed(a.offset(-rh, -rv, -rh), a.offset(rh, rv, rh))) {
			if (q.equals(a) || !level.hasChunkAt(q) || !SafeLanding.isSafe(level, q)) {
				continue;
			}
			double d = q.distSqr(a.above());
			if (d < bestD) {
				bestD = d;
				best = q.immutable();
			}
		}
		return best == null ? null : Vec3.atBottomCenterOf(best);
	}

	// ------------------------------------------------------------------ Boss Waystones
	/** Places a waystone near {@code at} unless one is already within 16 blocks. Returns the waystone position. */
	public static @Nullable BlockPos placeWaystone(final ServerLevel level, final BlockPos at) {
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16))) {
			if (level.getBlockState(q).is(ModBlocks.BOSS_WAYSTONE)) {
				return q.immutable();
			}
		}
		for (int r = 2; r <= 8; r++) {
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int x = at.getX() + (int)Math.round(Math.cos(a) * r);
				int z = at.getZ() + (int)Math.round(Math.sin(a) * r);
				for (int y = at.getY() + 4; y >= Math.max(level.getMinY() + 1, at.getY() - 12); y--) {
					BlockPos feet = new BlockPos(x, y, z);
					if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
						&& level.getBlockState(feet).canBeReplaced() && level.getBlockState(feet.above()).canBeReplaced()) {
						level.setBlock(feet, ModBlocks.BOSS_WAYSTONE.defaultBlockState(), Block.UPDATE_ALL);
						return feet;
					}
				}
			}
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		if (top.getY() > level.getMinY() && top.getY() < level.getMaxY() - 2) {
			level.setBlock(top, ModBlocks.BOSS_WAYSTONE.defaultBlockState(), Block.UPDATE_ALL);
			return top;
		}
		return null;
	}

	public static void attuneWaystone(final ServerPlayer p, final BlockPos pos) {
		rememberWaystone(p, p.level().dimension(), pos, true);
	}

	public static void rememberWaystone(final ServerPlayer p, final ResourceKey<Level> dim, final BlockPos pos, final boolean tell) {
		Progress.set(p, "way:" + dimKey(dim), Long.toString(pos.asLong()));
		if (tell) {
			p.sendSystemMessage(Component.translatable("message.elsewhere.waystone.set").withStyle(ChatFormatting.AQUA));
		}
	}

	public static @Nullable BlockPos waystoneRecord(final ServerPlayer p, final ResourceKey<Level> dim) {
		long v = Progress.getLong(p, "way:" + dimKey(dim), Long.MIN_VALUE);
		return v == Long.MIN_VALUE ? null : BlockPos.of(v);
	}

	public static RiftTravel.@Nullable Destination waystoneDestination(final ServerPlayer p, final ResourceKey<Level> dim) {
		ServerLevel level = p.level().getServer().getLevel(dim);
		BlockPos w = level == null ? null : waystoneRecord(p, dim);
		if (w == null) {
			return null;
		}
		return new RiftTravel.Destination(level, SafeLanding.near(level, Vec3.atBottomCenterOf(w.above()), 6, ElsewhereDimensions.platformOf(dim)));
	}

	/** Sneak-use: a rift to the player's latest waystone in {@code dim}. False (with a message) if none. */
	public static boolean warpToWaystone(final ServerPlayer p, final ResourceKey<Level> dim) {
		RiftTravel.Destination d = waystoneDestination(p, dim);
		if (d == null) {
			p.sendOverlayMessage(Component.translatable("message.elsewhere.waystone.none").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (!p.level().dimension().equals(dim)) {
			RiftTravel.rememberReturnPoint(p);
		}
		RiftTravel.openRiftTo(p, d, ElsewhereDimensions.colorOf(dim));
		p.sendOverlayMessage(Component.translatable("message.elsewhere.waystone.warp").withStyle(ChatFormatting.AQUA));
		return true;
	}

	// ------------------------------------------------------------------ arrival compass
	/** During the arrival cooldown in a dimension with Return Rifts: direction + distance to the nearest one. */
	public static boolean pointToRift(final ServerPlayer p) {
		ServerLevel level = p.level();
		if (!ReturnRifts.enabledIn(level.dimension())) {
			return false;
		}
		Long arrived = ARRIVED.get(p.getUUID());
		int cooldownTicks = (int)Math.round(dev.elsewhere.ElsewhereConfig.get().wayfinders.cooldownSeconds * 20.0);
		if (arrived == null || level.getGameTime() - arrived > cooldownTicks + 20) {
			return false;
		}
		BlockPos rift = ReturnRifts.nearestRift(level, p.getBlockX(), p.getBlockZ());
		p.sendOverlayMessage(compass(p.position(), rift));
		return true;
	}

	public static Component compass(final Vec3 from, final BlockPos to) {
		double dx = to.getX() + 0.5 - from.x;
		double dz = to.getZ() + 0.5 - from.z;
		int dist = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
		return Component.translatable("message.elsewhere.wayfinder.nearest_rift", Component.translatable("direction.elsewhere." + direction(dx, dz)), dist)
			.withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/** 8-way compass name: north = -Z. */
	public static String direction(final double dx, final double dz) {
		double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
		String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
		return names[(int)Math.round(deg / 45.0) % 8];
	}

	/** Self-test hook: pretend the player just arrived. */
	public static void markArrived(final ServerPlayer p) {
		ARRIVED.put(p.getUUID(), p.level().getGameTime());
	}

	// ------------------------------------------------------------------ first accidental trip
	/** A landing 32-128 blocks (horizontal) from the Return Rift nearest the scaled arrival point; null if the dimension has none. */
	public static RiftTravel.@Nullable Destination firstTripDestination(final ServerPlayer p, final ResourceKey<Level> target) {
		ServerLevel level = p.level().getServer().getLevel(target);
		if (level == null || !ReturnRifts.enabledIn(target)) {
			return null;
		}
		double scale = net.minecraft.world.level.dimension.DimensionType.getTeleportationScale(p.level().dimensionType(), level.dimensionType());
		int x = Mth.clamp((int)Math.floor(p.getX() * scale), -29_000_000, 29_000_000);
		int z = Mth.clamp((int)Math.floor(p.getZ() * scale), -29_000_000, 29_000_000);
		BlockPos rift = ReturnRifts.nearestRift(level, x, z);
		ElsewhereDimensions.Info info = ElsewhereDimensions.get(target);
		int maxY = info == null ? level.getMaxY() : info.maxLandingY();
		RandomSource r = p.getRandom();
		int fx = Integer.MIN_VALUE, fz = 0;
		for (int i = 0; i < 12; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			int d = 48 + r.nextInt(57); // 48-104, so SafeLanding's small search stays inside 32-128
			int lx = rift.getX() + (int)Math.round(Math.cos(a) * d);
			int lz = rift.getZ() + (int)Math.round(Math.sin(a) * d);
			Vec3 pos = SafeLanding.find(level, lx, lz, 12, maxY, ElsewhereDimensions.platformOf(target));
			double h = Math.sqrt(Math.pow(pos.x - rift.getX() - 0.5, 2) + Math.pow(pos.z - rift.getZ() - 0.5, 2));
			if (h >= FIRST_TRIP_MIN && h <= FIRST_TRIP_MAX) {
				return new RiftTravel.Destination(level, pos);
			}
			if (fx == Integer.MIN_VALUE) {
				fx = lx;
				fz = lz;
			}
		}
		return new RiftTravel.Destination(level, SafeLanding.platform(level, fx, fz, ElsewhereDimensions.platformOf(target)));
	}

	static {
		// Identifier/Registries referenced for dimension keys built from strings elsewhere in this class
		Identifier.class.getName();
		Registries.class.getName();
		Elsewhere.class.getName();
		Trophies.class.getName();
	}
}
