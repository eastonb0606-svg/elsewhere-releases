package dev.elsewhere.gear2;

import dev.elsewhere.stub.gear2.SetBonusStub;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Galvanic Coilmail set bonus "Capacitor": each hit you take stores a charge (max 5, each fades after 8 s, shown as
 * sparks). Your next melee hit discharges them into up to 3 other mobs within 4 blocks of the target, 2 damage per
 * charge, ignoring armor ({@code elsewhere:galvanic_discharge}, tagged {@code #minecraft:bypasses_armor}).
 */
public final class Capacitor {
	private Capacitor() {}

	public static final ResourceKey<DamageType> DISCHARGE = ResourceKey.create(Registries.DAMAGE_TYPE, Gear2Content.id("galvanic_discharge"));
	private static final Map<UUID, ArrayDeque<Long>> CHARGES = new HashMap<>();

	public static boolean wearing(Player p) {
		return SetBonusStub.active(p, Gear2Items.COILMAIL_SET);
	}

	public static int charges(Player p) {
		ArrayDeque<Long> q = CHARGES.get(p.getUUID());
		return q == null ? 0 : q.size();
	}

	/** A hit taken while wearing the full set stores a charge. */
	public static void onHurt(ServerPlayer p, DamageSource source) {
		if (!wearing(p) || source.getEntity() == null || source.getEntity() == p || source.is(DISCHARGE)) return;
		ArrayDeque<Long> q = CHARGES.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
		if (q.size() >= Gear2Stats.CAPACITOR_MAX) q.pollFirst();
		q.addLast(p.level().getGameTime() + Gear2Stats.CAPACITOR_FADE_TICKS);
	}

	public static void clear(Player p) {
		CHARGES.remove(p.getUUID());
	}

	static boolean otherMob(LivingEntity e, ServerPlayer p) {
		return e.isAlive() && !(e instanceof Player) && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p));
	}

	/** Discharges into up to 3 other mobs near {@code target}; returns the mobs hit. */
	public static List<LivingEntity> discharge(ServerPlayer p, LivingEntity target) {
		int n = charges(p);
		List<LivingEntity> hit = new ArrayList<>();
		if (n == 0 || !wearing(p)) return hit;
		clear(p);
		ServerLevel level = p.level();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(Gear2Stats.CAPACITOR_RADIUS),
			e -> e != target && e != p && otherMob(e, p) && e.distanceTo(target) <= Gear2Stats.CAPACITOR_RADIUS);
		near.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
		DamageSource src = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DISCHARGE), p, p);
		float dmg = Gear2Stats.CAPACITOR_DAMAGE_PER_CHARGE * n;
		for (LivingEntity e : near) {
			if (hit.size() >= Gear2Stats.CAPACITOR_TARGETS) break;
			e.hurtServer(level, src, dmg);
			hit.add(e);
			var a = target.position().add(0, target.getBbHeight() * 0.5, 0);
			var b = e.position().add(0, e.getBbHeight() * 0.5, 0);
			for (int i = 0; i <= 6; i++) {
				var at = a.lerp(b, i / 6.0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
			}
		}
		level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 1.6F);
		return hit;
	}

	static void tick(MinecraftServer server) {
		if (CHARGES.isEmpty()) return;
		CHARGES.entrySet().removeIf(e -> {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			long now = server.overworld().getGameTime();
			if (p != null) now = p.level().getGameTime();
			while (!e.getValue().isEmpty() && e.getValue().peekFirst() <= now) e.getValue().pollFirst();
			if (p != null && !e.getValue().isEmpty() && now % 10 == 0) {
				p.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), e.getValue().size(), 0.35, 0.5, 0.35, 0.02);
			}
			return e.getValue().isEmpty();
		});
	}
}
