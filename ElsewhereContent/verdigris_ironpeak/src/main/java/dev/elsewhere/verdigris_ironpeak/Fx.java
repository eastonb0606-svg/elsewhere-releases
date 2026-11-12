package dev.elsewhere.verdigris_ironpeak;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Small shared effects: lightning, pushes, particle shapes. */
public final class Fx {
	public static final ResourceKey<Level> VERDIGRIS_SEA = ResourceKey.create(Registries.DIMENSION, Reg.id("verdigris_sea"));
	public static final ResourceKey<Level> IRONPEAK = ResourceKey.create(Registries.DIMENSION, Reg.id("ironpeak"));

	private Fx() {}

	public static @Nullable LightningBolt lightning(ServerLevel level, Vec3 at, boolean visualOnly, @Nullable ServerPlayer cause) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) return null;
		bolt.snapTo(at);
		bolt.setVisualOnly(visualOnly);
		if (cause != null) bolt.setCause(cause);
		level.addFreshEntity(bolt);
		return bolt;
	}

	/** Pushes an entity away from (or toward, with a negative strength) a point, horizontally, plus some lift. */
	public static void push(Entity e, Vec3 from, double strength, double lift) {
		Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
		if (d.lengthSqr() < 1.0E-4) d = new Vec3(1, 0, 0);
		d = d.normalize().scale(strength);
		e.setDeltaMovement(e.getDeltaMovement().add(d.x, lift, d.z));
		e.needsSync = true;
	}

	public static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double r, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	public static void disc(ServerLevel level, ParticleOptions p, Vec3 c, double r, int n) {
		for (int i = 0; i < n; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double rr = Math.sqrt(level.getRandom().nextDouble()) * r;
			level.sendParticles(p, c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
		}
	}

	public static void line(ServerLevel level, ParticleOptions p, Vec3 a, Vec3 b, double step) {
		Vec3 d = b.subtract(a);
		int n = Math.max(1, (int) (d.length() / step));
		for (int i = 0; i <= n; i++) {
			Vec3 q = a.add(d.scale(i / (double) n));
			level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
	}

	public static boolean in(Level level, ResourceKey<Level> dim) {
		return level.dimension() == dim;
	}

	public static BlockPos ground(ServerLevel level, BlockPos pos) {
		return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
	}
}
