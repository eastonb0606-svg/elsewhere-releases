package dev.elsewhere.overworld_bosses;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Small geometry helpers shared by this module's bosses. */
public final class Geo {
	private Geo() {}

	/** Distance from p to the segment a..b. */
	public static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1.0E-6) return p.distanceTo(a);
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	/** Horizontal distance from p to the segment a..b (y ignored), plus the param t along it. */
	public static double flatDistToSegment(Vec3 p, Vec3 a, Vec3 b) {
		return distToSegment(new Vec3(p.x, 0, p.z), new Vec3(a.x, 0, a.z), new Vec3(b.x, 0, b.z));
	}

	public static Vec3 flatDir(Vec3 from, Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
		return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
	}

	/** Minecraft yaw (degrees) that looks along dir. */
	public static float yawOf(Vec3 dir) {
		return (float) (Math.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90.0F;
	}

	public static Vec3 dirOfYaw(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.sin(r), 0, Math.cos(r));
	}

	public static void face(LivingEntity e, Vec3 dir) {
		float yaw = yawOf(dir);
		e.setYRot(yaw);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		e.yRotO = yaw;
	}

	/** Top of the motion-blocking ground at x,z, as a Y to stand on. */
	public static double groundY(ServerLevel level, double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
	}

	/** Push an entity with a velocity and make sure players get it. */
	public static void push(Entity e, Vec3 v) {
		e.setDeltaMovement(v);
		e.needsSync = true;
		if (e instanceof ServerPlayer p) p.needsSync = true;
	}

	public static Vec3 bottom(BlockPos p) {
		return Vec3.atBottomCenterOf(p);
	}
}
