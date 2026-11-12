package dev.elsewhere.rivals.azmar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Faience fire pots (Naffatun, and players who throw the item). A pot is a server-side ledger entry, not an entity: it flies
 * on a gravity arc, bursts on a solid block or a living body (raw 6 impact), then leaves a 3x3 ground fire for 60 ticks
 * (2 damage per second). The fire is particles and damage only: it never ignites blocks, never spreads, never burns items.
 */
public final class FirePots {
	private FirePots() {}

	public static final float IMPACT = 6.0F, TICK_DAMAGE = 2.0F;
	public static final int PATCH_TICKS = 60;

	private record Pot(ServerLevel level, Vec3[] pos, Vec3[] vel, LivingEntity thrower, int[] age) {}
	private record Patch(ServerLevel level, BlockPos center, long until, LivingEntity thrower) {}
	private static final List<Pot> POTS = new ArrayList<>();
	private static final List<Patch> PATCHES = new ArrayList<>();
	/** Entity filter: true when the pot must not hurt this entity (the thrower's own side). Default: only the thrower. */
	public static java.util.function.BiPredicate<LivingEntity, LivingEntity> friendly = (thrower, other) -> thrower == other;
	/** Never throw/burst within this many blocks of a village block or villager is enforced by the caller (the field tactics). */
	public static int patchCount() { return PATCHES.size(); }

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(srv -> tick());
	}

	public static void throwPot(ServerLevel level, LivingEntity thrower, Vec3 from, Vec3 dir, double speed) {
		POTS.add(new Pot(level, new Vec3[] {from}, new Vec3[] {dir.normalize().scale(speed)}, thrower, new int[] {0}));
		level.playSound(null, BlockPos.containing(from), SoundEvents.SPLASH_POTION_THROW, SoundSource.NEUTRAL, 0.8F, 1.0F);
	}

	/** Throws toward a target position with a lobbed arc (used by the Naffatun). */
	public static void lob(ServerLevel level, LivingEntity thrower, Vec3 target) {
		Vec3 from = thrower.getEyePosition().add(0, -0.2, 0);
		Vec3 d = target.subtract(from);
		double h = Math.sqrt(d.x * d.x + d.z * d.z);
		double speed = Math.min(1.4, 0.45 + h * 0.06);
		Vec3 dir = new Vec3(d.x, d.y + h * 0.35 + 0.4, d.z);
		throwPot(level, thrower, from, dir, speed);
	}

	static void tick() {
		for (Iterator<Pot> it = POTS.iterator(); it.hasNext();) {
			Pot p = it.next();
			ServerLevel level = p.level;
			Vec3 a = p.pos[0], v = p.vel[0];
			Vec3 b = a.add(v);
			boolean burst = false;
			BlockPos hit = BlockPos.containing(b);
			if (!level.getBlockState(hit).getCollisionShape(level, hit).isEmpty()) { burst = true; b = a; }
			LivingEntity victim = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(0.6))) {
				if (e == p.thrower || friendly.test(p.thrower, e) || !e.isAlive() || (e instanceof Player pl && pl.isSpectator())) continue;
				victim = e; burst = true; break;
			}
			p.pos[0] = b;
			p.vel[0] = new Vec3(v.x * 0.99, v.y - 0.05, v.z * 0.99);
			p.age[0]++;
			level.sendParticles(ParticleTypes.FLAME, b.x, b.y, b.z, 1, 0.02, 0.02, 0.02, 0.0);
			if (burst || p.age[0] > 80 || b.y < level.getMinY()) {
				it.remove();
				burst(level, p.thrower, victim, victim != null ? victim.position() : a);
			}
		}
		for (Iterator<Patch> it = PATCHES.iterator(); it.hasNext();) {
			Patch pt = it.next();
			ServerLevel level = pt.level;
			long now = level.getGameTime();
			if (now >= pt.until) { it.remove(); continue; }
			Vec3 c = Vec3.atBottomCenterOf(pt.center);
			if (now % 4 == 0) {
				for (int i = 0; i < 6; i++)
					level.sendParticles(ParticleTypes.FLAME, c.x + (level.getRandom().nextDouble() * 3 - 1.5), c.y + 0.05, c.z + (level.getRandom().nextDouble() * 3 - 1.5), 1, 0.0, 0.04, 0.0, 0.01);
				level.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.3, c.z, 1, 0.6, 0.1, 0.6, 0.0);
			}
			if ((pt.until - now) % 20 == 0) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c.x - 1.5, c.y - 0.2, c.z - 1.5, c.x + 1.5, c.y + 1.5, c.z + 1.5))) {
					if (e == pt.thrower || friendly.test(pt.thrower, e) || !e.isAlive()) continue;
					e.hurtServer(level, level.damageSources().indirectMagic(pt.thrower, pt.thrower), TICK_DAMAGE);
				}
			}
		}
	}

	static void burst(ServerLevel level, LivingEntity thrower, LivingEntity victim, Vec3 at) {
		level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.9F, 0.9F);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 16, 0.5, 0.2, 0.5, 0.05);
		if (victim != null) victim.hurtServer(level, level.damageSources().indirectMagic(thrower, thrower), IMPACT);
		BlockPos c = BlockPos.containing(at);
		for (int dy = 0; dy < 3 && level.getBlockState(c).isAir(); dy++) c = c.below();
		while (!level.getBlockState(c.below()).isAir() && !level.getBlockState(c).isAir()) c = c.above();
		PATCHES.add(new Patch(level, c, level.getGameTime() + PATCH_TICKS, thrower));
	}

	public static void clear() {
		POTS.clear();
		PATCHES.clear();
	}
}
