package dev.elsewhere.greenhaze_hush;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Geometry and targeting helpers shared by this module's bosses. */
public final class BossUtil {
	private BossUtil() {}

	/** Is p inside a horizontal cone from origin facing yawDeg (Minecraft yaw), half-angle and range? */
	public static boolean inCone(Vec3 origin, float yawDeg, float halfAngleDeg, double range, Vec3 p) {
		Vec3 d = p.subtract(origin);
		double dist = d.horizontalDistance();
		if (dist > range || dist < 0.01) return dist < 0.01;
		double facing = Math.toRadians(yawDeg);
		double fx = -Math.sin(facing), fz = Math.cos(facing);
		double cos = (d.x * fx + d.z * fz) / dist;
		return cos >= Math.cos(Math.toRadians(halfAngleDeg));
	}

	public static float yawTo(Vec3 from, Vec3 to) {
		return (float) (Math.atan2(-(to.x - from.x), to.z - from.z) * 180.0 / Math.PI);
	}

	/** True if the attacker stands behind the entity (outside its front 180 degrees). */
	public static boolean fromBehind(LivingEntity self, Entity attacker) {
		return !inCone(self.position(), self.getYRot(), 90, 1000, attacker.position());
	}

	public static List<ServerPlayer> sortedByDistance(List<ServerPlayer> players, Entity from) {
		List<ServerPlayer> out = new ArrayList<>(players);
		out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(from)));
		return out;
	}

	/** A sound only this player hears (fake paranoia sounds). */
	public static void privateSound(ServerPlayer p, Holder<SoundEvent> sound, Vec3 at, float volume, float pitch) {
		p.connection.send(new ClientboundSoundPacket(sound, SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch, p.getRandom().nextLong()));
	}

	/** Knock an entity away along (dx, dz) (from the source toward it is negative of these). */
	public static void shove(LivingEntity e, double power, double dx, double dz) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-4) return;
		e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(-dx / len * power, 0.35 * Math.min(1, power), -dz / len * power));
		e.needsSync = true;
	}

	public static Vec3 ground(Vec3 v, double y) {
		return new Vec3(v.x, y, v.z);
	}
}
