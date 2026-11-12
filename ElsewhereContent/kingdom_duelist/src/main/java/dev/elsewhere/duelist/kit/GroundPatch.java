package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.boss.HumanBoss;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * X5 {@code GroundPatch}: a timed damaging or slowing area, particles only. It never ignites, never edits a block. It
 * is armed after {@code armTicks} (the warning: an outline of particles and no effect), then pulses every
 * {@code pulseEvery} ticks for {@code lifetime} ticks. Damage is raw magic through {@code boss.hurtRaw(MAGIC)}.
 */
public final class GroundPatch {
	private final ServerLevel level;
	private final HumanBoss owner;
	private final Vec3 center;
	private final double radius;
	private final int armTicks, lifetime, pulseEvery;
	private final float raw;
	private final List<MobEffectInstance> effects;
	private final ParticleOptions particle;
	private int age;
	public int pulses;

	private static final List<GroundPatch> ACTIVE = new ArrayList<>();
	private static boolean inited;

	private GroundPatch(ServerLevel level, HumanBoss owner, Vec3 center, double radius, int armTicks, int lifetime, int pulseEvery, float raw, List<MobEffectInstance> effects, ParticleOptions particle) {
		this.level = level;
		this.owner = owner;
		this.center = center;
		this.radius = radius;
		this.armTicks = armTicks;
		this.lifetime = lifetime;
		this.pulseEvery = Math.max(1, pulseEvery);
		this.raw = raw;
		this.effects = effects;
		this.particle = particle;
	}

	public static GroundPatch spawn(ServerLevel level, HumanBoss owner, Vec3 center, double radius, int armTicks, int lifetime, int pulseEvery, float raw, List<MobEffectInstance> effects, ParticleOptions particle) {
		GroundPatch p = new GroundPatch(level, owner, center, radius, armTicks, lifetime, pulseEvery, raw, effects, particle);
		ACTIVE.add(p);
		return p;
	}

	public static int active() {
		return ACTIVE.size();
	}

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			for (Iterator<GroundPatch> it = ACTIVE.iterator(); it.hasNext();) {
				GroundPatch p = it.next();
				if (!p.tick()) it.remove();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> ACTIVE.clear());
	}

	private boolean tick() {
		age++;
		if (age > armTicks + lifetime || !owner.isAlive() || owner.level() != level) return false;
		boolean armed = age > armTicks;
		if (age % 4 == 0) { // P13: every 4th tick, was 3rd
			int n = armed ? 10 : 6;
			Runnable draw = () -> {
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n + age * 0.1;
					double r = armed ? radius * Math.sqrt(level.getRandom().nextDouble()) : radius;
					level.sendParticles(particle, center.x + Math.cos(a) * r, center.y + 0.15, center.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.0);
				}
			};
			if (armed) draw.run();   // the armed patch is the attack itself: everyone sees it
			else dev.elsewhere.duelist.foresight.Foresight.hint(draw);   // B5: the pre-arm ring (where it will land) is a foresight hint
		}
		if (armed && (age - armTicks) % pulseEvery == 0) {
			pulses++;
			for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 1.5, radius))) {
				if (v == owner || v instanceof HumanBoss || (v instanceof Player p && (p.isCreative() || p.isSpectator()))) continue;
				if (v.position().subtract(center).multiply(1, 0, 1).lengthSqr() > radius * radius) continue;
				for (MobEffectInstance e : effects) v.addEffect(new MobEffectInstance(e), owner);
				if (raw > 0F) owner.hurtRaw(level, v, raw, DamageCaps.MAGIC, level.damageSources().indirectMagic(owner, owner));
			}
		}
		return true;
	}
}
