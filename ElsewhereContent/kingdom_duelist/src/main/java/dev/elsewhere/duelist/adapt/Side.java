package dev.elsewhere.duelist.adapt;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Where a player is relative to the reader's facing: front within 60 degrees, rear beyond 120, else left or right. */
public enum Side {
	FRONT, LEFT, RIGHT, REAR;

	public static Side of(LivingEntity reader, LivingEntity player) {
		Vec3 look = Vec3.directionFromRotation(0, reader.getYRot()).multiply(1, 0, 1).normalize();
		Vec3 to = player.position().subtract(reader.position()).multiply(1, 0, 1);
		if (to.lengthSqr() < 1e-6) return FRONT;
		to = to.normalize();
		double ang = Math.toDegrees(Math.acos(Math.clamp(look.dot(to), -1.0, 1.0)));
		if (ang <= 60) return FRONT;
		if (ang >= 120) return REAR;
		double cross = look.x * to.z - look.z * to.x;
		return cross > 0 ? RIGHT : LEFT;
	}
}
