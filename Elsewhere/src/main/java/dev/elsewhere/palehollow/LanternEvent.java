package dev.elsewhere.palehollow;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.trophy.Trophies;
import dev.elsewhere.palehollow.world.PHSites;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Lantern Procession (Pale Hollow): on some nights, lantern spirits walk the candle road and leave a resin chest at its end.
 * Triggered by a player standing within 96 blocks of a Lantern Procession road.
 */
public final class LanternEvent {
	private LanternEvent() {}

	public static final String TAG = "elsewhere_lantern_spirit";
	public static final int SPIRITS = 5;
	public static final double SPEED = 0.1;
	private static final TagKey<Structure> ROADS = TagKey.create(Registries.STRUCTURE, Elsewhere.id("lantern_road"));
	private static final Map<UUID, Long> NEXT_ROLL = new HashMap<>();
	private static final List<Walk> WALKS = new ArrayList<>();

	private static final class Walk {
		final ServerLevel level;
		final double y, z, endX;
		final BlockPos chest;
		final List<Entity> spirits = new ArrayList<>();
		final List<Double> offsets = new ArrayList<>();
		double lead;
		/** 3.0 contour roads: the walking points along the road and their cumulative distances (null for a 2.x straight road). */
		List<net.minecraft.world.phys.Vec3> path;
		double[] cum;

		Walk(ServerLevel level, double startX, double endX, double y, double z, BlockPos chest) {
			this.level = level;
			this.endX = endX;
			this.y = y;
			this.z = z;
			this.chest = chest;
			this.lead = startX;
		}
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e.entityTags().contains(TAG) && WALKS.stream().noneMatch(w -> w.spirits.contains(e))) e.discard();
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> !e.entityTags().contains(TAG));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickWalks();
			if (server.getTickCount() % 200 != 0) return;
			ServerLevel hollow = server.getLevel(PaleHollowDimension.LEVEL);
			if (hollow == null) return;
			long t = hollow.getGameTime();
			boolean night = Math.floorMod(t, 24000L) >= 13000L;
			if (!night) return;
			for (ServerPlayer p : hollow.players()) {
				if (p.isSpectator() || NEXT_ROLL.getOrDefault(p.getUUID(), 0L) > t) continue;
				NEXT_ROLL.put(p.getUUID(), t + 6000L);
				if (hollow.getRandom().nextFloat() < 0.4F) start(hollow, p);
			}
		});
	}

	/** Start a procession at the nearest road within range of {@code p}; returns whether one began. */
	public static boolean start(ServerLevel level, ServerPlayer p) {
		var found = level.findNearestMapStructure(ROADS, p.blockPosition(), 6, false);
		if (found == null) return false;
		BlockPos c = found;
		if (Math.abs(c.getX() - p.getX()) > 96 || Math.abs(c.getZ() - p.getZ()) > 96) return false;
		return begin(level, c.getX(), c.getZ(), p);
	}

	public static boolean begin(ServerLevel level, int cx, int cz, ServerPlayer notify) {
		dev.elsewhere.palehollow.fit3.FitPiece road = dev.elsewhere.palehollow.fit3.RoadKit.find(level, cx, cz);
		if (road != null) return beginPath(level, road, notify);
		int x0 = (cx >> 4) * 16 + 8, z0 = (cz >> 4) * 16 + 8;
		int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, x0, z0);
		BlockPos chest = new BlockPos(x0 + PHSites.ROAD_HALF + 2, gy, z0);
		Walk w = new Walk(level, x0 - PHSites.ROAD_HALF, x0 + PHSites.ROAD_HALF, gy + 1.2, z0 + 0.5, chest);
		for (int i = 0; i < SPIRITS; i++) {
			Entity a = dev.elsewhere.registry.ModEntities.EYEBLOSSOM_WISP.create(level, EntitySpawnReason.EVENT);
			if (a == null) continue;
			a.addTag(TAG);
			a.setNoGravity(true);
			a.setGlowingTag(true);
			a.setCustomName(Component.translatable("entity.elsewhere.lantern_spirit"));
			if (a instanceof net.minecraft.world.entity.Mob m) m.setNoAi(true);
			a.snapTo(w.lead - i * 3.0, w.y, w.z, -90F, 0F);
			level.addFreshEntity(a);
			w.spirits.add(a);
			w.offsets.add(i * 3.0);
		}
		WALKS.add(w);
		if (notify != null) notify.sendSystemMessage(Component.translatable("message.elsewhere.lantern.walk").withStyle(ChatFormatting.YELLOW));
		return !w.spirits.isEmpty();
	}

	/** A procession along a 3.0 contour road: the spirits follow the road's own cells. */
	private static boolean beginPath(ServerLevel level, dev.elsewhere.palehollow.fit3.FitPiece road, ServerPlayer notify) {
		List<net.minecraft.world.phys.Vec3> path = dev.elsewhere.palehollow.fit3.RoadKit.walk(road);
		if (path.size() < 2) return false;
		double[] cum = new double[path.size()];
		for (int i = 1; i < path.size(); i++) cum[i] = cum[i - 1] + path.get(i).distanceTo(path.get(i - 1));
		Walk w = new Walk(level, 0, cum[cum.length - 1], 0, 0, dev.elsewhere.palehollow.fit3.RoadKit.chest(road));
		w.path = path;
		w.cum = cum;
		for (int i = 0; i < SPIRITS; i++) {
			Entity a = dev.elsewhere.registry.ModEntities.EYEBLOSSOM_WISP.create(level, EntitySpawnReason.EVENT);
			if (a == null) continue;
			a.addTag(TAG);
			a.setNoGravity(true);
			a.setGlowingTag(true);
			a.setCustomName(Component.translatable("entity.elsewhere.lantern_spirit"));
			if (a instanceof net.minecraft.world.entity.Mob m) m.setNoAi(true);
			net.minecraft.world.phys.Vec3 p = along(w, -i * 3.0);
			a.snapTo(p.x, p.y, p.z, 0F, 0F);
			level.addFreshEntity(a);
			w.spirits.add(a);
			w.offsets.add(i * 3.0);
		}
		WALKS.add(w);
		if (notify != null) notify.sendSystemMessage(Component.translatable("message.elsewhere.lantern.walk").withStyle(ChatFormatting.YELLOW));
		return !w.spirits.isEmpty();
	}

	/** The point {@code d} blocks along a path walk (clamped to its ends). */
	private static net.minecraft.world.phys.Vec3 along(Walk w, double d) {
		double[] cum = w.cum;
		if (d <= 0) return w.path.get(0);
		if (d >= cum[cum.length - 1]) return w.path.get(cum.length - 1);
		int i = 1;
		while (i < cum.length - 1 && cum[i] < d) i++;
		double t = (d - cum[i - 1]) / Math.max(1e-6, cum[i] - cum[i - 1]);
		return w.path.get(i - 1).lerp(w.path.get(i), t);
	}

	public static int active() {
		return WALKS.size();
	}

	/** Advance every procession one tick; finish ones that reached the road's end. */
	public static void tickWalks() {
		for (int n = WALKS.size() - 1; n >= 0; n--) {
			Walk w = WALKS.get(n);
			w.lead += SPEED;
			for (int i = 0; i < w.spirits.size(); i++) {
				Entity a = w.spirits.get(i);
				if (a.isRemoved()) continue;
				if (w.path != null) {
					net.minecraft.world.phys.Vec3 p = along(w, w.lead - w.offsets.get(i)), q = along(w, w.lead - w.offsets.get(i) + 0.5);
					float yaw = (float) (Math.atan2(q.z - p.z, q.x - p.x) * 180.0 / Math.PI) - 90F;
					a.snapTo(p.x, p.y + Math.sin((w.lead + i) * 0.4) * 0.15, p.z, yaw, 0F);
				} else
				a.snapTo(w.lead - w.offsets.get(i), w.y + Math.sin((w.lead + i) * 0.4) * 0.15, w.z, -90F, 0F);
				if (w.level.getGameTime() % 4 == 0) w.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, a.getX(), a.getY() + 0.3, a.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (w.lead - w.offsets.get(w.offsets.size() - 1) >= w.endX) {
				finish(w);
				WALKS.remove(n);
			}
		}
	}

	private static void finish(Walk w) {
		for (Entity a : w.spirits) a.discard();
		ServerLevel l = w.level;
		BlockPos c = w.chest;
		// the road's end can sit against a slope: climb to the first free block (up to 8) instead of leaving no chest
		for (int i = 0; i < 8 && !(l.getBlockState(c).isAir() || l.getBlockState(c).canBeReplaced()); i++) c = c.above();
		if (l.getBlockState(c).isAir() || l.getBlockState(c).canBeReplaced()) {
			l.setBlockAndUpdate(c.below(), Blocks.PALE_OAK_PLANKS.defaultBlockState());
			l.setBlockAndUpdate(c, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST));
			RandomizableContainer.setBlockEntityLootTable(l, l.getRandom(), c, PHSites.loot("lantern_resin"));
			l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.02);
			for (ServerPlayer p : l.players()) {
				if (p.distanceToSqr(c.getX(), c.getY(), c.getZ()) < 96 * 96) {
					p.sendSystemMessage(Component.translatable("message.elsewhere.lantern.chest").withStyle(ChatFormatting.YELLOW));
					Trophies.grant(p, "lantern");
					dev.elsewhere.core.atlas.Atlas.discover(p, "event:lantern");
				}
			}
		}
	}

	public static BlockPos chestOf(ServerLevel l, int cx, int cz) {
		dev.elsewhere.palehollow.fit3.FitPiece road = dev.elsewhere.palehollow.fit3.RoadKit.find(l, cx, cz);
		if (road != null) {
			BlockPos b = dev.elsewhere.palehollow.fit3.RoadKit.chest(road);
			for (int i = 0; i <= 8; i++) if (l.getBlockState(b.above(i)).is(Blocks.CHEST)) return b.above(i);
			return b;
		}
		int x0 = (cx >> 4) * 16 + 8, z0 = (cz >> 4) * 16 + 8;
		BlockPos base = new BlockPos(x0 + PHSites.ROAD_HALF + 2, l.getHeight(Heightmap.Types.WORLD_SURFACE, x0, z0), z0);
		for (int i = 0; i <= 8; i++) if (l.getBlockState(base.above(i)).is(Blocks.CHEST)) return base.above(i);
		return base;
	}
}
