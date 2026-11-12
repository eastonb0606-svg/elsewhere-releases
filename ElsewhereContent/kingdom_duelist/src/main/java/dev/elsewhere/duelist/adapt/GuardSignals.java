package dev.elsewhere.duelist.adapt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * X12 {@code GuardSignals}: a guard formation controller. The leader signals a {@link Formation} with a visible tell and
 * a sound; the guards turn to face the leader and obey after the delay (10 ticks for banner and blade, 40 for a flare).
 * One order at a time, per-formation durations and cooldowns from {@link Formation}. The controller moves the guards (a
 * path update every 10 ticks, never per tick) and never harms anything itself: a guard's attacks are its own AI.
 */
public final class GuardSignals {
	/** The visible tell for a signal. */
	public enum Tell {
		BANNER_UP, BLADE_UP, BLADE_DOWN, BLADE_SIDE, BANNER_LOWERED, SHIELD_TAPS, FLARE_AMBER, FLARE_BLUE, FLARE_RED, FLARE_WHITE;

		public boolean flare() {
			return this == FLARE_AMBER || this == FLARE_BLUE || this == FLARE_RED || this == FLARE_WHITE;
		}
	}

	public static final int OBEY_DELAY = 10, FLARE_DELAY = 40, TURN_TICKS = 10;

	private final Mob leader;
	private final List<Mob> guards = new ArrayList<>();
	private @Nullable Mob hookGuard;
	private final EnumMap<Formation, Long> readyAt = new EnumMap<>(Formation.class);
	private Formation current = Formation.WITHDRAW;
	private @Nullable Formation incoming;
	private long incomingAt, startedAt, endsAt;
	private @Nullable LivingEntity target;
	public int signalsSent;
	/** Divides Screen's cooldown (the Wazir alive shortens it from 400 to 240). */
	public int screenCooldownOverride = -1;

	public GuardSignals(Mob leader) {
		this.leader = leader;
	}

	public void setGuards(List<? extends Mob> g, @Nullable Mob hook) {
		guards.clear();
		guards.addAll(g);
		this.hookGuard = hook;
	}

	public List<Mob> guards() {
		guards.removeIf(g -> !g.isAlive());
		return guards;
	}

	public Formation current() {
		return current;
	}

	public boolean ordering() {
		return incoming != null;
	}

	public boolean ready(Formation f, long now) {
		return now >= readyAt.getOrDefault(f, 0L);
	}

	/** Plays the tell now and queues the order. Returns false when the formation is on cooldown. */
	public boolean signal(ServerLevel level, Formation f, Tell tell, @Nullable LivingEntity target) {
		long now = level.getGameTime();
		if (!ready(f, now) || guards().isEmpty()) return false;
		this.target = target;
		this.incoming = f;
		int delay = tell.flare() ? FLARE_DELAY : OBEY_DELAY;
		this.incomingAt = now + delay;
		int cd = f == Formation.SCREEN && screenCooldownOverride >= 0 ? screenCooldownOverride : f.cooldown;
		readyAt.put(f, now + cd);
		signalsSent++;
		tellEffects(level, tell);
		return true;
	}

	private void tellEffects(ServerLevel level, Tell tell) {
		double x = leader.getX(), y = leader.getY() + leader.getBbHeight() + 0.4, z = leader.getZ();
		switch (tell) {
			case BANNER_UP, BANNER_LOWERED -> {
				level.playSound(null, x, y, z, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.HOSTILE, 2F, tell == Tell.BANNER_UP ? 1.2F : 0.8F);
				level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 6, 0.1, 0.4, 0.1, 0.01);
			}
			case BLADE_UP, BLADE_DOWN, BLADE_SIDE -> {
				level.playSound(null, x, y, z, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value(), SoundSource.HOSTILE, 2F, 1F);
				level.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.2, 0.2, 0.2, 0.05);
			}
			case SHIELD_TAPS -> {
				level.playSound(null, x, y, z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2F, 1F);
				level.sendParticles(ParticleTypes.NOTE, x, y, z, 2, 0.2, 0.2, 0.2, 0.0);
			}
			default -> {
				level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2F, 1F);
				level.sendParticles(tell == Tell.FLARE_RED ? ParticleTypes.FLAME : ParticleTypes.CLOUD, x, y + 1, z, 12, 0.1, 1.0, 0.1, 0.05);
			}
		}
		// the guards turn to face the leader first
		for (Mob g : guards()) g.getLookControl().setLookAt(leader, 30F, 30F);
	}

	/** Call every AI tick of the leader; moves guards every 10 ticks. */
	public void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (incoming != null) {
			for (Mob g : guards()) g.getLookControl().setLookAt(leader, 30F, 30F);
			if (now >= incomingAt) {
				current = incoming;
				startedAt = now;
				endsAt = current.duration > 0 ? now + current.duration : Long.MAX_VALUE;
				incoming = null;
			}
			return;
		}
		if (current != Formation.WITHDRAW && now >= endsAt) {
			current = Formation.WITHDRAW;
			startedAt = now;
			endsAt = Long.MAX_VALUE;
		}
		if (now % 10 != 0) return;
		List<Mob> gs = guards();
		int n = gs.size();
		if (n == 0) return;
		LivingEntity t = target;
		for (int i = 0; i < n; i++) {
			Mob g = gs.get(i);
			Vec3 dest = destination(level, i, n, g, t, now);
			if (dest != null) g.getNavigation().moveTo(dest.x, dest.y, dest.z, current == Formation.SCREEN ? 0.8 : 1.0);
			if (current == Formation.SCREEN && g instanceof Guardable gd) gd.raiseShield(14);
		}
	}

	private @Nullable Vec3 destination(ServerLevel level, int i, int n, Mob g, @Nullable LivingEntity t, long now) {
		Vec3 lp = leader.position();
		switch (current) {
			case WITHDRAW -> {
				if (g.distanceToSqr(leader) <= 9) return null;
				return lp;
			}
			case SCREEN -> {
				if (t == null) return lp;
				Vec3 dir = t.position().subtract(lp).multiply(1, 0, 1).normalize();
				Vec3 side = new Vec3(-dir.z, 0, dir.x);
				double off = (i - (n - 1) / 2.0) * 1.3;
				return lp.add(dir.scale(3.0)).add(side.scale(off));
			}
			case RING -> {
				if (t == null) return lp;
				double secs = (now - startedAt) / 20.0;
				double r = Math.max(3.0, 6.0 - secs);
				double a = Math.PI * 2 * i / n + secs * 0.3;
				return t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
			}
			case OPEN -> {
				if (t == null) return lp;
				Vec3 dir = t.position().subtract(lp).multiply(1, 0, 1).normalize();
				Vec3 side = new Vec3(-dir.z, 0, dir.x);
				return t.position().add(side.scale((i % 2 == 0 ? 5.0 : -5.0)));
			}
			case HOOK -> {
				if (t == null) return null;
				if (g == hookGuard) return t.position();
				return t.position().add(lp.subtract(t.position()).normalize().scale(4.0));
			}
		}
		return null;
	}
}
