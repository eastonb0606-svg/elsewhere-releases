package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.boss.kit.KitStore;
import dev.elsewhere.glasswaste.GlasswasteDimension;
import dev.elsewhere.glasswaste.GlasswasteItems;
import dev.elsewhere.glasswaste.Heat;
import dev.elsewhere.petalwind.PetalwindDimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * new-event-glass-storm (Glasswaste) and new-event-updrafts (Petalwind). Both are scheduled with persistent
 * {@link KitStore} cooldowns (a restart does not reset the timer; a storm or season cut short by a restart just ends).
 *
 * <p><b>Glass storm</b> (60 s, every 20-30 min): a wall of glass shards. Anyone under open sky without a helmet takes 1
 * damage every 2 s, and Heat builds twice as fast; cover or any helmet makes it harmless. Everyone who was caught
 * outside it and survived finds 1-3 Sunglass Shards at their feet when it ends (the reward for braving it).
 * <p><b>Updraft season</b> (3 min, every 25-40 min): rising wind columns (16 blocks, 20 s each) appear near every player
 * in Petalwind and lift whoever stands in them, so islands above can be reached without a Skyhook.
 */
public final class PGEvents {
	private PGEvents() {}

	public static final String NEXT_STORM = "pg_next_glass_storm", NEXT_SEASON = "pg_next_updraft_season";
	public static final int STORM_TICKS = 1200, SEASON_TICKS = 3600, COLUMN_TICKS = 400, COLUMN_HEIGHT = 16;
	private static long stormEnd = -1, seasonEnd = -1;
	private static final Set<UUID> EXPOSED = new HashSet<>();
	private record Column(BlockPos base, long until) {}
	private static final List<Column> COLUMNS = new ArrayList<>();

	public static boolean glassStorm(ServerLevel level) {
		return level.dimension() == GlasswasteDimension.LEVEL && level.getGameTime() < stormEnd;
	}

	public static boolean updraftSeason(ServerLevel level) {
		return level.dimension() == PetalwindDimension.LEVEL && level.getGameTime() < seasonEnd;
	}

	public static List<BlockPos> columns() {
		List<BlockPos> out = new ArrayList<>();
		for (Column c : COLUMNS) out.add(c.base());
		return out;
	}

	public static void startGlassStorm(ServerLevel level) {
		stormEnd = level.getGameTime() + STORM_TICKS;
		EXPOSED.clear();
		for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.translatable("event.elsewhere.glass_storm.start"));
		level.playSound(null, level.players().isEmpty() ? BlockPos.ZERO : level.players().get(0).blockPosition(), SoundEvents.SAND_BREAK, SoundSource.AMBIENT, 4.0F, 0.5F);
	}

	public static void startUpdrafts(ServerLevel level) {
		seasonEnd = level.getGameTime() + SEASON_TICKS;
		COLUMNS.clear();
		for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.translatable("event.elsewhere.updrafts.start"));
	}

	/** True when this player is hurt by the storm right now (under open sky, no helmet). */
	public static boolean stormExposed(ServerLevel level, ServerPlayer p) {
		return !p.isCreative() && !p.isSpectator() && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && level.canSeeSky(BlockPos.containing(p.getEyePosition()));
	}

	public static void tick(MinecraftServer server) {
		ServerLevel gw = server.getLevel(GlasswasteDimension.LEVEL), pw = server.getLevel(PetalwindDimension.LEVEL);
		if (gw != null) {
			schedule(gw, NEXT_STORM, 20 * 60 * 20, 30 * 60 * 20, () -> startGlassStorm(gw));
			tickStorm(gw);
		}
		if (pw != null) {
			schedule(pw, NEXT_SEASON, 25 * 60 * 20, 40 * 60 * 20, () -> startUpdrafts(pw));
			tickSeason(pw);
		}
	}

	private static void schedule(ServerLevel level, String key, int min, int max, Runnable start) {
		if (level.getServer().getTickCount() % 100 != 0 || level.players().isEmpty()) return;
		KitStore store = KitStore.of(level.getServer());
		int interval = min + level.getRandom().nextInt(max - min + 1);
		if (!store.data().cooldowns.containsKey(key)) {
			store.cooldownStart(key, interval / 2);
		} else if (store.cooldownReady(key)) {
			start.run();
			store.cooldownStart(key, interval);
		}
	}

	private static void tickStorm(ServerLevel level) {
		long now = level.getGameTime();
		if (stormEnd < 0) return;
		if (now >= stormEnd) {
			stormEnd = -1;
			for (ServerPlayer p : level.players()) {
				if (!EXPOSED.contains(p.getUUID()) || !p.isAlive()) continue;
				p.sendSystemMessage(Component.translatable("event.elsewhere.glass_storm.end"));
				ItemStack shards = new ItemStack(GlasswasteItems.SUNGLASS_SHARD, 1 + level.getRandom().nextInt(3));
				if (!p.getInventory().add(shards)) p.drop(shards, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			EXPOSED.clear();
			return;
		}
		if (now % 40 != 0) return;
		for (ServerPlayer p : level.players()) {
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.5, p.getZ(), 12, 6, 2, 6, 0.3);
			if (!stormExposed(level, p)) continue;
			EXPOSED.add(p.getUUID());
			p.hurtServer(level, level.damageSources().cactus(), 1.0F);
			Heat.boost(p, 60);
		}
	}

	private static void tickSeason(ServerLevel level) {
		long now = level.getGameTime();
		if (seasonEnd < 0) return;
		if (now >= seasonEnd) {
			seasonEnd = -1;
			COLUMNS.clear();
			return;
		}
		COLUMNS.removeIf(c -> now >= c.until());
		if (now % 100 == 0) {
			for (ServerPlayer p : level.players()) {
				int x = p.getBlockX() + level.getRandom().nextInt(25) - 12, z = p.getBlockZ() + level.getRandom().nextInt(25) - 12;
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
				if (y > level.getMinY()) COLUMNS.add(new Column(new BlockPos(x, y, z), now + COLUMN_TICKS));
			}
		}
		for (Column c : COLUMNS) {
			if (now % 4 == 0) level.sendParticles(ParticleTypes.CLOUD, c.base().getX() + 0.5, c.base().getY() + 1 + (now % 16), c.base().getZ() + 0.5, 2, 0.6, 0.5, 0.6, 0.02);
			lift(level, c);
		}
	}

	/** Lifts everything standing in a column (radius 2, up to 16 blocks above its base). */
	static void lift(ServerLevel level, Column c) {
		AABB box = new AABB(c.base().getX() - 1.5, c.base().getY(), c.base().getZ() - 1.5, c.base().getX() + 2.5, c.base().getY() + COLUMN_HEIGHT, c.base().getZ() + 2.5);
		for (Entity e : level.getEntities((Entity) null, box, x -> x.isAlive() && !x.isSpectator() && !x.isPassenger())) {
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x * 0.9, Math.min(0.45, v.y + 0.12), v.z * 0.9);
			e.resetFallDistance();
			if (e instanceof ServerPlayer sp) sp.needsSync = true;
		}
	}

	/** Self-test hook: applies one lift tick for a column at {@code base}. */
	public static void testLift(ServerLevel level, BlockPos base) {
		lift(level, new Column(base, level.getGameTime() + 10));
	}
}
