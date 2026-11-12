package dev.elsewhere.arsenal.spell;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Instant line spells (Sunlance, Resonator, Stormcoil): walk the look ray in small steps. */
public final class SpellLines {
	private SpellLines() {}

	public record Line(Vec3 start, Vec3 end, List<LivingEntity> targets) {}

	public static Line trace(ServerLevel level, ServerPlayer p, double range, int maxTargets, boolean throughGlass) {
		Vec3 start = p.getEyePosition();
		Vec3 dir = p.getLookAngle().normalize();
		List<LivingEntity> out = new ArrayList<>();
		Vec3 end = start.add(dir.scale(range));
		for (double d = 0.5; d <= range; d += 0.25) {
			Vec3 pt = start.add(dir.scale(d));
			BlockPos bp = BlockPos.containing(pt);
			BlockState st = level.getBlockState(bp);
			if (!st.getCollisionShape(level, bp).isEmpty() && !(throughGlass && isGlass(st))) {
				end = pt;
				break;
			}
			for (Entity e : level.getEntities(p, new net.minecraft.world.phys.AABB(pt, pt).inflate(0.35))) {
				if (e instanceof LivingEntity le && !(e instanceof ArmorStand) && le.isAlive() && !out.contains(le) && e != p) {
					out.add(le);
					if (maxTargets > 0 && out.size() >= maxTargets) return new Line(start, pt, out);
				}
			}
		}
		return new Line(start, end, out);
	}

	public static boolean isGlass(BlockState st) {
		return st.is(BlockTags.IMPERMEABLE) || st.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
			|| st.getBlock().getDescriptionId().contains("glass");
	}

	/** Beam origin at the held-item hand (right and below the eye) so a first-person beam reads as a line, not a blob on the view axis. */
	public static Vec3 handStart(LivingEntity e) {
		Vec3 look = e.getLookAngle().normalize();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
		return e.getEyePosition().add(look.scale(0.7)).add(right.scale(0.45)).add(0, -0.4, 0);
	}

	public static void particles(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, double step) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1.0E-3) return;
		Vec3 n = d.scale(1 / len);
		for (double t = 0; t <= len; t += step) {
			Vec3 pt = a.add(n.scale(t));
			level.sendParticles(particle, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0);
		}
	}
}
