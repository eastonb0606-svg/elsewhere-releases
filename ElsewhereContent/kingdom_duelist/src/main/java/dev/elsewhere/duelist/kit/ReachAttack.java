package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.boss.HumanBoss;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** X8 {@code ReachAttack}: a melee hit with extra reach, half damage inside the minimum range, and an optional line pierce. */
public final class ReachAttack {
	private ReachAttack() {}

	/**
	 * Hits living entities along the boss's line to {@code target} out to {@code reach} blocks (beyond the normal 3). Inside
	 * {@code minRange} the hit does half damage. With {@code pierce} everything on the line is hit, else only the nearest.
	 * Returns the number hit. The caller owns the telegraph.
	 */
	public static int hit(ServerLevel level, HumanBoss boss, LivingEntity target, double reach, double minRange, float raw, boolean pierce, DamageCaps kind) {
		Vec3 from = boss.getEyePosition();
		Vec3 dir = target.getEyePosition().subtract(from).normalize();
		Vec3 to = from.add(dir.scale(reach));
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0))) {
			if (e == boss || e instanceof HumanBoss || (e instanceof Player p && (p.isCreative() || p.isSpectator()))) continue;
			var hit = e.getBoundingBox().inflate(0.4).clip(from, to);
			if (hit.isPresent()) hits.add(e);
		}
		hits.sort(Comparator.comparingDouble(boss::distanceToSqr));
		int n = 0;
		for (LivingEntity e : hits) {
			float amt = boss.distanceTo(e) < minRange ? raw * 0.5F : raw;
			if (boss.hurtRaw(level, e, amt, kind, level.damageSources().mobAttack(boss))) n++;
			if (!pierce) break;
		}
		return n;
	}
}
